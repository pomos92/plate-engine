package com.plateengine

import android.content.Context
import android.graphics.Bitmap
import com.plateengine.core.AnalyzeResult
import com.plateengine.core.PlateOcr
import com.plateengine.core.VehicleEngineCore
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * 오프라인 차량 정보 인식 엔진 (Android).
 *
 * ```
 * val engine = PlateEngine(context)           // 앱 시작 시 한 번 (모델 로딩 약 0.5초)
 * val result = engine.analyze(bitmap)          // 백그라운드 스레드에서 호출
 * result.vehicles.forEach { v -> v.plate?.number; v.type.category; v.type.electric; v.color.name }
 * engine.close()
 * ```
 * 인터넷 권한이 필요 없고, 모든 모델은 라이브러리 assets 에 들어 있다.
 * 스레드 안전하지 않으므로 한 인스턴스는 한 스레드에서만 사용한다.
 */
class PlateEngine(context: Context) : AutoCloseable {
    private val detector: VehicleDetector
    private val ocr: PlateOcr
    private val core: VehicleEngineCore

    init {
        check(OpenCVLoader.initLocal()) { "OpenCV 초기화 실패" }
        val assets = context.applicationContext.assets
        ocr = PlateOcr(
            assets.open("plate_ocr.onnx").use { it.readBytes() },
            assets.open("plate_ocr_chars.json").use { it.readBytes().toString(Charsets.UTF_8) },
        )
        detector = VehicleDetector(context.applicationContext)
        core = VehicleEngineCore(ocr)
    }

    /** 사진 한 장 분석. ARGB_8888 이 아니면 내부에서 변환한다. */
    fun analyze(bitmap: Bitmap): AnalyzeResult {
        val bmp = if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val rgba = Mat(); Utils.bitmapToMat(bmp, rgba)
        val bgr = Mat(); Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        val detections = detector.detect(bmp)
        return core.analyze(bgr, detections).also { rgba.release(); bgr.release() }
    }

    /** analyze() 결과를 Python 버전과 같은 구조의 JSON 문자열로. */
    fun analyzeJson(bitmap: Bitmap): String = analyze(bitmap).toJson()

    override fun close() {
        detector.close(); ocr.close()
    }
}
