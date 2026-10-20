plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// 테스트 전용 앱. 실제 광고 SDK를 쓰지 않고, SDK가 호스트와 같은 패키지에서
// 전체 화면 Activity를 띄우는 상황을 흉내 낸다.
android {
    namespace = "com.personal.autoskip.mockhost"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.personal.autoskip.mockhost"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
