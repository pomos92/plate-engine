plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.plateengine.sample"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.plateengine.sample"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        // 대부분의 실제 폰(64비트 ARM)만 포함해서 APK 크기를 줄임. 에뮬레이터(x86_64)에서 돌리려면 "x86_64" 추가
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation(project(":plateengine"))
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
