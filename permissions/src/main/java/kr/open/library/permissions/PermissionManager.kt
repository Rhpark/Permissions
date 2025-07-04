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

    private val specialPermissionActions = mapOf(
        Manifest.permission.SYSTEM_ALERT_WINDOW to Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Manifest.permission.WRITE_SETTINGS to Settings.ACTION_MANAGE_WRITE_SETTINGS,
        Manifest.permission.PACKAGE_USAGE_STATS to Settings.ACTION_USAGE_ACCESS_SETTINGS,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE to Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS to Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Manifest.permission.SCHEDULE_EXACT_ALARM to Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Manifest.permission.BIND_ACCESSIBILITY_SERVICE to Settings.ACTION_ACCESSIBILITY_SETTINGS,
        Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE to "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
    )

    private val permissionsRequiringPackageUri = setOf(
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
    )

    public fun getIntentForSpecialPermission(context: Context, permission: String): Intent? {
        // API 레벨 체크
        when (permission) {
            Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            }
            Manifest.permission.SCHEDULE_EXACT_ALARM -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
            }
        }
        
        val action = specialPermissionActions[permission] ?: return null
        
        return if (permissionsRequiringPackageUri.contains(permission)) {
            Intent(action, Uri.parse("package:${context.packageName}"))
        } else {
            Intent(action)
        }
    }


    public fun result(context: Context, permissions: Map<String, Boolean>, requestId: String? = null) {
        val deniedPermissions = permissions.filterNot { (permission, granted) ->
            granted || context.hasPermission(permission)
        }.keys.toList()
        
        handleRequestResult(requestId, deniedPermissions)
    }
    
    public fun resultSpecialPermission(context: Context, permission: String, requestId: String? = null) {
        val deniedPermissions = if (context.hasPermission(permission)) emptyList() else listOf(permission)
        handleRequestResult(requestId, deniedPermissions)
    }
    
    private fun handleRequestResult(requestId: String?, deniedPermissions: List<String>) {
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
        
        storeRequest(requestId, permissionRequest, context)

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
        
        storeRequest(requestId, permissionRequest, context)

        val intent = getIntentForSpecialPermission(context, permission)
        if (intent != null) {
            specialPermissionLauncher.launch(intent)
        } else {
            cleanupRequest(requestId)
            onResult(false)
        }
        
        return requestId
    }
    
    private fun storeRequest(requestId: String, permissionRequest: PermissionRequest, context: Context) {
        cleanupExpiredRequests()
        pendingRequests[requestId] = permissionRequest
        contextRef[requestId] = WeakReference(context)
    }
    
    private fun cleanupRequest(requestId: String) {
        pendingRequests.remove(requestId)
        contextRef.remove(requestId)
    }
    
    public fun cancelRequest(requestId: String) {
        cleanupRequest(requestId)
    }
    
    public fun getPendingRequestsCount(): Int = pendingRequests.size
}