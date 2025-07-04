package kr.open.library.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import kr.open.library.permissions.extensions.remainPermissions
import kr.open.library.permissions.extensions.isSpecialPermission
import kr.open.library.permissions.extensions.hasPermission
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.WeakReference

public class PermissionManager private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: PermissionManager? = null
        
        fun getInstance(): PermissionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PermissionManager().also { INSTANCE = it }
            }
        }
    }

    private val pendingRequests = ConcurrentHashMap<String, PermissionRequest>()
    private val contextRef = ConcurrentHashMap<String, WeakReference<Context>>()
    
    private data class PermissionRequest(
        val requestId: String,
        val permissions: List<String>,
        val onResult: (deniedPermissions: List<String>) -> Unit,
        val timestamp: Long = System.currentTimeMillis()
    )

    public fun getIntentForSystemAlertWindow(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    public fun getIntentForWriteSettings(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )

    public fun getIntentForUsageStats(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    public fun getIntentForManageExternalStorage(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )

    public fun getIntentForBatteryOptimization(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )

    public fun getIntentForScheduleExactAlarm(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)

    public fun getIntentForAccessibilityService(context: Context): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    public fun getIntentForNotificationListener(context: Context): Intent =
        Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")

    public fun getIntentForSpecialPermission(context: Context, permission: String): Intent? =
        when (permission) {
            Manifest.permission.SYSTEM_ALERT_WINDOW -> getIntentForSystemAlertWindow(context)
            Manifest.permission.WRITE_SETTINGS -> getIntentForWriteSettings(context)
            Manifest.permission.PACKAGE_USAGE_STATS -> getIntentForUsageStats(context)
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    getIntentForManageExternalStorage(context)
                } else {
                    null
                }
            }
            Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> getIntentForBatteryOptimization(context)
            Manifest.permission.SCHEDULE_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    getIntentForScheduleExactAlarm(context)
                } else {
                    null
                }
            }
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE -> getIntentForAccessibilityService(context)
            Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE -> getIntentForNotificationListener(context)
            else -> null
        }

    private fun isRemainPermissionSystemAlertWindow(permissions: List<String>): Boolean =
        permissions.contains(Manifest.permission.SYSTEM_ALERT_WINDOW)

    public fun isRequestPermissionSystemAlertWindow(
        context: Context,
        permissions: List<String>
    ): Boolean =
        isRemainPermissionSystemAlertWindow(permissions) && !Settings.canDrawOverlays(context)

    public fun result(context: Context, permissions: Map<String, Boolean>, requestId: String? = null) {
        val deniedPermissions = mutableListOf<String>()
        permissions.forEach { (permission, granted) ->
            if (!granted) {
                if (permission == Manifest.permission.SYSTEM_ALERT_WINDOW) {
                    if (!Settings.canDrawOverlays(context)) {
                        deniedPermissions.add(permission)
                    }
                } else {
                    deniedPermissions.add(permission)
                }
            }
        }
        
        if (requestId != null) {
            val request = pendingRequests.remove(requestId)
            contextRef.remove(requestId)
            request?.onResult?.invoke(deniedPermissions)
        } else {
            // 레거시 지원 - 가장 오래된 요청 처리
            val oldestRequest = pendingRequests.values.minByOrNull { it.timestamp }
            if (oldestRequest != null) {
                pendingRequests.remove(oldestRequest.requestId)
                contextRef.remove(oldestRequest.requestId)
                oldestRequest.onResult.invoke(deniedPermissions)
            }
        }
    }
    
    public fun resultSpecialPermission(context: Context, permission: String, requestId: String? = null) {
        val isGranted = context.hasPermission(permission)
        val deniedPermissions = if (isGranted) emptyList() else listOf(permission)
        
        if (requestId != null) {
            val request = pendingRequests.remove(requestId)
            contextRef.remove(requestId)
            request?.onResult?.invoke(deniedPermissions)
        }
    }
    
    public fun cleanupExpiredRequests(maxAgeMs: Long = 300_000) { // 5분
        val currentTime = System.currentTimeMillis()
        val expiredRequests = pendingRequests.filter { (_, request) ->
            currentTime - request.timestamp > maxAgeMs
        }
        
        expiredRequests.forEach { (requestId, _) ->
            pendingRequests.remove(requestId)
            contextRef.remove(requestId)
        }
    }

    public fun request(
        context: Context,
        requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
        requestPermissionAlertWindowLauncher: ActivityResultLauncher<Intent>,
        permissions: List<String>,
        onResult: ((deniedPermissions: List<String>) -> Unit)
    ): String {
        val remainingPermissions = context.remainPermissions(permissions)
        if (remainingPermissions.isEmpty()) {
            onResult(emptyList())
            return ""
        }

        val requestId = UUID.randomUUID().toString()
        val permissionRequest = PermissionRequest(
            requestId = requestId,
            permissions = remainingPermissions,
            onResult = onResult
        )
        
        // 만료된 요청들 정리
        cleanupExpiredRequests()
        
        // 새 요청 저장
        pendingRequests[requestId] = permissionRequest
        contextRef[requestId] = WeakReference(context)

        val (specialPermissions, normalPermissions) = remainingPermissions.partition {
            context.isSpecialPermission(it)
        }

        if (specialPermissions.isNotEmpty()) {
            specialPermissions.forEach { permission ->
                val intent = getIntentForSpecialPermission(context, permission)
                if (intent != null) {
                    requestPermissionAlertWindowLauncher.launch(intent)
                }
            }
        }

        if (normalPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(normalPermissions.toTypedArray())
        }

        return requestId
    }

    public fun requestSpecialPermission(
        context: Context,
        specialPermissionLauncher: ActivityResultLauncher<Intent>,
        permission: String,
        onResult: ((granted: Boolean) -> Unit)
    ): String {
        if (!context.isSpecialPermission(permission)) {
            onResult(false)
            return ""
        }
        
        val requestId = UUID.randomUUID().toString()
        val permissionRequest = PermissionRequest(
            requestId = requestId,
            permissions = listOf(permission),
            onResult = { deniedPermissions ->
                onResult(deniedPermissions.isEmpty())
            }
        )
        
        // 만료된 요청들 정리
        cleanupExpiredRequests()
        
        // 새 요청 저장
        pendingRequests[requestId] = permissionRequest
        contextRef[requestId] = WeakReference(context)

        val intent = getIntentForSpecialPermission(context, permission)
        if (intent != null) {
            specialPermissionLauncher.launch(intent)
        } else {
            // 실패 시 즉시 제거
            pendingRequests.remove(requestId)
            contextRef.remove(requestId)
            onResult(false)
        }
        
        return requestId
    }
    
    public fun cancelRequest(requestId: String) {
        pendingRequests.remove(requestId)
        contextRef.remove(requestId)
    }
    
    public fun getPendingRequestsCount(): Int = pendingRequests.size
}