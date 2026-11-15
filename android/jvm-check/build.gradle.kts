// Android 기기 없이 PC(JVM)에서 Kotlin 엔진 코어를 돌려 Python 결과와 비교하는 검증용 프로젝트.
plugins {
    kotlin("jvm") version "2.0.21"
    application
}

repositories { maven("https://repo1.maven.org/maven2/") }

dependencies {
    implementation("org.openpnp:opencv:4.9.0-0")            // 데스크톱용 OpenCV Java (Android 의 org.opencv 와 같은 API)
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")  // Android 의 onnxruntime-android 와 같은 API
}

sourceSets {
    main {
        kotlin.srcDir("../plateengine/src/main/kotlin/com/plateengine/core")
    }
}

kotlin { jvmToolchain(21) }

application { mainClass.set("CheckKt") }
