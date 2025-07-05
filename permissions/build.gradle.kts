plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    id ("maven-publish")
}

publishing {
    publications {
        register("release", MavenPublication::class) { // MavenPublication::class 사용 가능
            groupId = "com.github.Rhpark"
            artifactId = "Permissions"
            version = libs.versions.library.get()

            afterEvaluate {
                from(components.findByName("release"))
            }
        }

        register("debug", MavenPublication::class) { // MavenPublication::class 사용 가능
            groupId = "com.github.Rhpark"
            artifactId = "Permissions"
            version = libs.versions.library.get() // 동일 버전 사용 시 주의 (이전 답변 참고)

            afterEvaluate {
                from(components.findByName("debug"))
            }
        }
    }
}
android {
    namespace = "kr.open.library.permissions"
    compileSdk = 35

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/java")
        }
        getByName("debug") {
            java.srcDirs("src/debug/java")
        }
    }

    defaultConfig {
        minSdk = 28
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") { // 또는 release { ... } 로도 사용 가능
            isMinifyEnabled = false // setMinifyEnabled(false) 대신 isMinifyEnabled 사용 가능
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // consumerProguardFiles("consumer-rules.pro") // 라이브러리라면 이것도 중요
        }
        getByName("debug") { // 또는 debug { ... } 로도 사용 가능
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}