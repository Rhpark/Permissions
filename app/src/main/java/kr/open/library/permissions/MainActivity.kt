package kr.open.library.permissions

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kr.open.library.logcat.Logx

class MainActivity : AppCompatActivity() {

    /************************
     *   Permission Check   *
     ************************/
    private val permission = PermissionManager.getInstance()
    private var currentRequestId: String? = null


    /**
     * SystemAlertPermission 처리를 위함.
     */
    private val requestPermissionAlertWindowLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Logx.d("requestPermissionAlertWindowLauncher ${Settings.canDrawOverlays(this)}")
            // 특수 권한 결과 처리 - 현재는 SYSTEM_ALERT_WINDOW만 처리
            permission.resultSpecialPermission(this, Manifest.permission.SYSTEM_ALERT_WINDOW, currentRequestId)
        }

    /**
     * SystemAlertPermission 제외한 권한 처리를 위함.
     */
    private val requestPermissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            Logx.d("requestPermissionLauncher ${permissions}")
            permission.result(this, permissions, currentRequestId)
        }

    /**
     * 권한 요청 & 결과 확인 메서드
     */
    protected fun requestPermissions(
        permissions: List<String>,
        onResult: ((deniedPermissions: List<String>) -> Unit)
    ) {
        currentRequestId = permission.request(
            this,
            requestPermissionLauncher,
            requestPermissionAlertWindowLauncher,
            permissions,
            onResult
        )
    }

    private val permissionList = listOf(
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_PHONE_NUMBERS,
        Manifest.permission.SYSTEM_ALERT_WINDOW,
        Manifest.permission.WRITE_SETTINGS,
        Manifest.permission.PACKAGE_USAGE_STATS,
        Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Manifest.permission.SCHEDULE_EXACT_ALARM,
        Manifest.permission.POST_NOTIFICATIONS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        requestPermissions(permissionList) { deniedPermissions ->
            Logx.d("deniedPermissions: $deniedPermissions")
            if (deniedPermissions.isNotEmpty()) {
                // Handle denied permissions
            } else {
                // All permissions granted
            }
        }

    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 현재 요청이 있다면 취소
        currentRequestId?.let { requestId ->
            permission.cancelRequest(requestId)
        }
    }
}