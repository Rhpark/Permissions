package kr.open.library.permissions.extensions

import android.Manifest
import android.app.AppOpsManager
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat


public inline fun Context.hasPermission(permission: String): Boolean =
    when (permission) {
        Manifest.permission.SYSTEM_ALERT_WINDOW -> Settings.canDrawOverlays(this)

        Manifest.permission.WRITE_SETTINGS -> Settings.System.canWrite(this)

        Manifest.permission.PACKAGE_USAGE_STATS -> hasUsageStatsPermission()

        Manifest.permission.MANAGE_EXTERNAL_STORAGE -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
        }

        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            powerManager?.isIgnoringBatteryOptimizations(packageName) ?: false
        }

        Manifest.permission.SCHEDULE_EXACT_ALARM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.canScheduleExactAlarms() ?: false
            } else {
                true
            }
        }

        Manifest.permission.POST_NOTIFICATIONS -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                NotificationManagerCompat.from(this).areNotificationsEnabled()
            } else {
                true
            }
        }

        Manifest.permission.BIND_ACCESSIBILITY_SERVICE -> hasAccessibilityServicePermission()

        Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE -> hasNotificationListenerPermission()

        else -> {
            if(getPermissionProtectionLevel(permission) == PermissionInfo.PROTECTION_DANGEROUS) {
                ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }
    }

public inline fun Context.hasPermissions(vararg permissions: String): Boolean =
    permissions.all { permission -> hasPermission(permission) }

public inline fun Context.hasPermissions(vararg permissions: String, doWork: () -> Unit): Boolean =
    if (permissions.all { permission -> hasPermission(permission) }) {
        doWork()
        true
    } else {
        false
    }


public inline fun Context.remainPermissions(permissions: List<String>): List<String> =
    permissions.filterNot { hasPermission(it) }

public inline fun Context.hasUsageStatsPermission(): Boolean = try {
    val appOps = getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
    appOps?.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        packageName
    ) == AppOpsManager.MODE_ALLOWED
} catch (e: Exception) {
    false
}

public inline fun Context.hasAccessibilityServicePermission(): Boolean = try {
    val enabledServices = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    )
    enabledServices?.contains(packageName) == true
} catch (e: Exception) {
    false
}

public inline fun Context.hasNotificationListenerPermission(): Boolean = try {
    val enabledListeners = Settings.Secure.getString(
        contentResolver,
        "enabled_notification_listeners"
    )
    enabledListeners?.contains(packageName) == true
} catch (e: Exception) {
    false
}

public inline fun Context.isSpecialPermission(permission: String): Boolean =
    when (permission) {
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.PACKAGE_USAGE_STATS,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Manifest.permission.SCHEDULE_EXACT_ALARM,
        Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
        Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE -> true
        else -> false
    }

public inline fun Context.getPermissionProtectionLevel(permission: String): Int = try {
    packageManager.getPermissionInfo(permission, 0).protection
} catch (e: PackageManager.NameNotFoundException) {
    PermissionInfo.PROTECTION_DANGEROUS
}
