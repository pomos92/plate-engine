plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.plateengine"
    compileSdk = 35
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // 모델 파일은 저장소 루트 models/ 를 그대로 assets 로 사용 (복사본을 두지 않음)
    sourceSets["main"].assets.srcDirs("../../models")
    androidResources { noCompress += listOf("tflite", "onnx") }
}

dependencies {
    // 차량 검출: MediaPipe Tasks (오프라인, 모델은 assets 에 포함)
    api("com.google.mediapipe:tasks-vision:0.10.14")
    // 번호판 OCR
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.20.0")
    // 번호판 위치·색상 판별 (OpenCV 공식 Android 패키지, Maven Central)
    api("org.opencv:opencv:4.10.0")
}
