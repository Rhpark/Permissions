# Android Permissions Library

🔐 **Android 권한 관리를 위한 통합 라이브러리**

일반 권한과 특수 권한을 하나의 API로 쉽게 관리할 수 있는 Android 라이브러리입니다.

## ✨ 주요 기능

- **통합 권한 관리**: 일반 권한과 특수 권한을 동일한 API로 처리
- **특수 권한 완벽 지원**: 8가지 특수 권한 자동 처리
- **안전한 메모리 관리**: WeakReference로 Context 누수 방지
- **비동기 처리**: ActivityResultLauncher 기반 안전한 권한 요청
- **자동 정리**: 만료된 권한 요청 자동 정리
- **하위 호환성**: API 28 이상 모든 Android 버전 지원

## 📱 지원 권한

### 일반 권한
- `READ_PHONE_STATE`
- `READ_PHONE_NUMBERS`
- `WRITE_EXTERNAL_STORAGE`
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- 기타 모든 위험 권한

### 특수 권한
- `SYSTEM_ALERT_WINDOW` - 다른 앱 위에 표시
- `WRITE_SETTINGS` - 시스템 설정 수정
- `PACKAGE_USAGE_STATS` - 앱 사용 통계
- `MANAGE_EXTERNAL_STORAGE` - 외부 저장소 관리
- `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` - 배터리 최적화 무시
- `SCHEDULE_EXACT_ALARM` - 정확한 알람 예약
- `POST_NOTIFICATIONS` - 알림 권한 (Android 13+)
- `BIND_ACCESSIBILITY_SERVICE` - 접근성 서비스
- `BIND_NOTIFICATION_LISTENER_SERVICE` - 알림 리스너 서비스

## 🚀 설치

### Gradle (모듈 수준)
```
dependencies {
    implementation ('com.github.rhpark:Permissions:0.9.0')
}
```

### 버전 정보
- **Min SDK**: 28 (Android 9.0)
- **Target SDK**: 35 (Android 15)
- **Kotlin**: 2.0.0
- **AGP**: 8.8.2

## 📖 사용법

### 1. 기본 설정

**Activity에서:**
```kotlin
class MainActivity : AppCompatActivity() {
    private val permissionManager = PermissionManager.getInstance()
    private var currentRequestId: String? = null

    // 일반 권한 요청 런처
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.result(this, permissions, currentRequestId)
    }

    // 특수 권한 요청 런처
    private val requestSpecialPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { 
        permissionManager.resultSpecialPermission(
            this, 
            Manifest.permission.SYSTEM_ALERT_WINDOW, 
            currentRequestId
        )
    }
}
```

**Fragment에서:**
```kotlin
class MyFragment : Fragment() {
    private val permissionManager = PermissionManager.getInstance()
    private var currentRequestId: String? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionManager.result(requireContext(), permissions, currentRequestId)
    }

    private val requestSpecialPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { 
        permissionManager.resultSpecialPermission(
            requireContext(), 
            Manifest.permission.SYSTEM_ALERT_WINDOW, 
            currentRequestId
        )
    }
}
```

### 2. 권한 요청

**여러 권한 동시 요청:**
```kotlin
private val permissionList = listOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.SYSTEM_ALERT_WINDOW,
    Manifest.permission.WRITE_SETTINGS,
    Manifest.permission.PACKAGE_USAGE_STATS,
    Manifest.permission.POST_NOTIFICATIONS
)

private fun requestPermissions() {
    currentRequestId = permissionManager.request(
        context = this,
        requestPermissionLauncher = requestPermissionLauncher,
        requestPermissionAlertWindowLauncher = requestSpecialPermissionLauncher,
        permissions = permissionList
    ) { deniedPermissions ->
        if (deniedPermissions.isNotEmpty()) {
            // 거부된 권한 처리
            handleDeniedPermissions(deniedPermissions)
        } else {
            // 모든 권한 허용
            onAllPermissionsGranted()
        }
    }
}
```

**특수 권한 개별 요청:**
```kotlin
private fun requestSpecialPermission() {
    currentRequestId = permissionManager.requestSpecialPermission(
        context = this,
        specialPermissionLauncher = requestSpecialPermissionLauncher,
        permission = Manifest.permission.SYSTEM_ALERT_WINDOW
    ) { granted ->
        if (granted) {
            // 권한 허용
        } else {
            // 권한 거부
        }
    }
}
```

### 3. 권한 상태 확인

```kotlin
// 개별 권한 확인
if (this.hasPermission(Manifest.permission.SYSTEM_ALERT_WINDOW)) {
    // 권한 있음
}

// 여러 권한 확인
if (this.hasPermissions(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)) {
    // 모든 권한 있음
}

// 남은 권한 확인
val remainingPermissions = this.remainPermissions(permissionList)
```

### 4. 생명주기 관리

```kotlin
override fun onDestroy() {
    super.onDestroy()
    // 현재 요청 취소
    currentRequestId?.let { requestId ->
        permissionManager.cancelRequest(requestId)
    }
}
```

## 🔧 고급 기능

### 특수 권한 Intent 생성
```kotlin
// 특수 권한 설정 화면으로 이동하는 Intent 생성
val intent = permissionManager.getIntentForSpecialPermission(
    context = this,
    permission = Manifest.permission.SYSTEM_ALERT_WINDOW
)
if (intent != null) {
    startActivity(intent)
}
```

### 요청 상태 관리
```kotlin
// 대기 중인 요청 수 확인
val pendingCount = permissionManager.getPendingRequestsCount()

// 만료된 요청 수동 정리 (기본 5분)
permissionManager.cleanupExpiredRequests()
```

## 📋 매니페스트 설정

```xml
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_NUMBERS" />
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.WRITE_SETTINGS" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" 
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" 
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" 
    tools:ignore="ProtectedPermissions" />
```

## 🏗️ 프로젝트 구조

```
permissions/
├── src/main/java/kr/open/library/permissions/
│   ├── PermissionManager.kt              # 메인 권한 관리자
│   └── extensions/
│       └── PermissionExtensions.kt       # 권한 확장 함수
├── build.gradle.kts                      # 모듈 빌드 설정
└── AndroidManifest.xml                   # 라이브러리 매니페스트
```

## 🔍 API 레퍼런스

### PermissionManager

| 메서드 | 설명 |
|--------|------|
| `getInstance()` | 싱글톤 인스턴스 반환 |
| `request()` | 여러 권한 요청 |
| `requestSpecialPermission()` | 특수 권한 개별 요청 |
| `result()` | 일반 권한 결과 처리 |
| `resultSpecialPermission()` | 특수 권한 결과 처리 |
| `getIntentForSpecialPermission()` | 특수 권한 Intent 생성 |
| `cancelRequest()` | 요청 취소 |
| `cleanupExpiredRequests()` | 만료된 요청 정리 |

### Extension Functions

| 함수 | 설명 |
|------|------|
| `Context.hasPermission()` | 개별 권한 확인 |
| `Context.hasPermissions()` | 여러 권한 확인 |
| `Context.remainPermissions()` | 남은 권한 목록 |
| `Context.isSpecialPermission()` | 특수 권한 여부 확인 |

## 🔒 보안 고려사항

- **최소 권한 원칙**: 필요한 권한만 요청
- **사용자 교육**: 권한 필요 이유를 명확히 설명
- **우아한 실패**: 권한 거부 시 앱 기능 제한적 제공
- **메모리 안전**: WeakReference로 Context 누수 방지
