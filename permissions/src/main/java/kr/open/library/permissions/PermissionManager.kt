package kr.open.library.permissions

import android.Manifest
import android.Manifest.permission.SYSTEM_ALERT_WINDOW
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import kr.open.library.permissions.extensions.remainPermissions
import kr.open.library.permissions.extensions.isSpecialPermission

public open class PermissionManager() {

    private var onResult: ((deniedPermissions: List<String>) -> Unit)? = null

    public fun getIntentForSystemAlertWindow(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    
    public fun getIntentForWriteSettings(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    
    public fun getIntentForUsageStats(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
    
    public fun getIntentForManageExternalStorage(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    
    public fun getIntentForBatteryOptimization(context: Context): Intent =
        Intent(
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
        permissions.contains(SYSTEM_ALERT_WINDOW)

    public fun isRequestPermissionSystemAlertWindow(
        context: Context,
        permissions: List<String>
    ): Boolean =
        isRemainPermissionSystemAlertWindow(permissions) && !Settings.canDrawOverlays(context)

    public fun result(context: Context, permissions: Map<String, Boolean>) {
        val deniedPermissions = mutableListOf<String>()
        permissions.forEach { (permission, granted) ->
            if (!granted) {
                if (permission == SYSTEM_ALERT_WINDOW) {
                    if (!Settings.canDrawOverlays(context)) {
                        deniedPermissions.add(permission)
                    }
                } else {
                    deniedPermissions.add(permission)
                }
            }
        }
        onResult?.invoke(deniedPermissions)
        onResult = null
    }

    public fun request(
        context: Context,
        requestPermissionLauncher: ActivityResultLauncher<Array<String>>,
        requestPermissionAlertWindowLauncher: ActivityResultLauncher<Intent>,
        permissions: List<String>,
        onResult: ((deniedPermissions: List<String>) -> Unit)
    ) {
        val remainingPermissions = context.remainPermissions(permissions)
        if (remainingPermissions.isEmpty()) {
            onResult(emptyList())
            return
        }

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

        this.onResult = onResult
    }
    
    public fun requestSpecialPermission(
        context: Context,
        specialPermissionLauncher: ActivityResultLauncher<Intent>,
        permission: String,
        onResult: ((granted: Boolean) -> Unit)
    ) {
        if (!context.isSpecialPermission(permission)) {
            onResult(false)
            return
        }
        
        val intent = getIntentForSpecialPermission(context, permission)
        if (intent != null) {
            specialPermissionLauncher.launch(intent)
        } else {
            onResult(false)
        }
    }
}