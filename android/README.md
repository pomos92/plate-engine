# Android 연동 (오프라인)

인터넷 없이 폰 안에서 차량 번호판·차종·전기차 여부·색상을 인식하는 Android 라이브러리와 예제 앱입니다.
PC용 Python 엔진과 같은 모델, 같은 처리 순서를 Kotlin으로 옮겼습니다.

## 구성

```
android/
├─ plateengine/   라이브러리 모듈 (앱에 붙이는 부분)
│  ├─ core/       플랫폼 공통 로직: 번호판 찾기, OCR, 차종 규칙, 색상 (OpenCV + ONNX Runtime)
│  ├─ PlateEngine.kt       공개 API: Bitmap -> 결과
│  └─ VehicleDetector.kt   차량 검출 (MediaPipe)
├─ sample/        예제 앱: 갤러리에서 사진 고르기 -> 결과 그려서 표시
└─ jvm-check/     폰 없이 PC에서 core 를 돌려 Python 결과와 비교하는 검증용
```

모델 파일은 저장소 루트의 `models/`를 그대로 assets로 씁니다 (`plateengine/build.gradle.kts`의 `assets.srcDirs`).

## 빌드

Android Studio(Ladybug 이상)에서 `android/` 폴더를 열고 `sample` 을 실행하면 됩니다.
명령줄: `cd android && ./gradlew :sample:installDebug`

- minSdk 24 (Android 7.0), compileSdk 35, AGP 8.7, Kotlin 2.0
- 의존성: MediaPipe tasks-vision 0.10.14, ONNX Runtime Android 1.20.0, OpenCV 4.10.0 (Maven Central)
- 앱 권한: 없음 (인터넷 권한도 필요 없음)

## 내 앱에 붙이기

1. `android/plateengine` 폴더와 `models/` 폴더를 프로젝트에 복사하고 `settings.gradle.kts`에 `include(":plateengine")`
   (`models/` 위치가 다르면 `plateengine/build.gradle.kts`의 `assets.srcDirs` 경로를 맞춰 주세요)
2. 앱 모듈에 `implementation(project(":plateengine"))`
3. 사용:

```kotlin
val engine = PlateEngine(context)        // 한 번만 생성 (백그라운드 스레드 권장)

// 백그라운드 스레드에서
val result = engine.analyze(bitmap)
for (v in result.vehicles) {
    v.plate?.number        // "123가4567" (못 읽으면 plate == null)
    v.plate?.validFormat   // 번호 형식이 맞는지
    v.plate?.plateColor    // 흰색 / 파란색(전기차) / 노란색(사업용) / 녹색(구형)
    v.type.category        // 승용 / 승합 / 트럭 / 특수 / 버스 / 이륜차
    v.type.electric        // 전기차 여부 (파란 번호판)
    v.type.compactGuess    // 경차 추정 (낮은 신뢰도)
    v.color.name           // 흰색, 검정, 은색, 회색, 빨강, 파랑 ...
}
val json = result.toJson()  // PC 버전과 같은 구조의 JSON

engine.close()
```

- 한 `PlateEngine` 인스턴스는 한 스레드에서만 쓰세요.
- 사진은 긴 변 1,600px 정도로 줄여서 넣으면 충분합니다 (예제 앱 `decode()` 참고).

## APK 크기

OpenCV AAR에 모든 CPU 종류가 들어 있어서 그대로 빌드하면 큽니다.
예제 앱은 `abiFilters += listOf("arm64-v8a")`로 실제 폰용만 넣습니다. 에뮬레이터에서 돌리려면 `"x86_64"`를 추가하세요.
모델은 합쳐서 약 11MB입니다.

## 검증 상태

- **core (Kotlin)**: PC(JVM)에서 실제 OpenCV·ONNX Runtime으로 실행해 Python 엔진과 비교했습니다.
  같은 차량 검출 결과를 넣었을 때 26대 중 번호 26대, 번호판색 26대, 차종 26대, 차량색 25대 일치
  (`python tests/kotlin_parity.py`, 차량색 1대는 k-means 초기값 차이로 은색/흰색 경계에서 갈림).
- **PlateEngine.kt**: 실제 Android·OpenCV·ONNX Runtime API로 컴파일 확인.
- **VehicleDetector.kt(MediaPipe)와 예제 앱**: 작업 환경에서 Google Maven 저장소가 막혀 있어 **아직 빌드해 보지 못했습니다.**
  Android Studio에서 처음 빌드할 때 오류가 나면 알려 주세요.
