package com.plateengine.core

import org.opencv.core.Mat
import kotlin.math.max
import kotlin.math.min

/** 차량 검출 결과 (검출기는 플랫폼별로 따로: Android 는 MediaPipe). label: car/truck/bus/motorcycle 또는 null */
data class Detection(val label: String?, val score: Float, val box: Box)

data class PlateInfo(
    val number: String,
    val validFormat: Boolean,
    val confidence: Double,
    val box: Box,
    /** 흰색 / 파란색(전기차) / 노란색(사업용) / 녹색(구형) / 알 수 없음 */
    val plateColor: String,
    val region: String?,
    /** 자가용 / 사업용 / 렌터카 (형식이 맞을 때만) */
    val usage: String?,
)

data class VehicleType(
    /** 승용 / 승합 / 트럭 / 특수 / 긴급 / 버스 / 이륜차 / null */
    val category: String?,
    /** "번호판" (번호 규칙) 또는 "차량 모양" (검출 모델) */
    val source: String?,
    val electric: Boolean,
    /** 경차 추정 (낮은 신뢰도: 번호판에 표시가 없어 차량 박스 비율로만 추정) */
    val compactGuess: Boolean,
)

data class VehicleResult(
    val box: Box,
    val detectorLabel: String?,
    val detectorScore: Float,
    val plate: PlateInfo?,
    val type: VehicleType,
    val color: VehicleColor.ColorResult,
)

data class AnalyzeResult(val width: Int, val height: Int, val vehicles: List<VehicleResult>) {
    fun toJson(): String = Json.result(this)
}

/**
 * 플랫폼 공통 엔진: 차량 검출 결과를 받아 번호판·차종·색상을 판별한다.
 * (Python engine/engine.py 의 VehicleEngine 과 같은 동작)
 */
class VehicleEngineCore(private val ocr: PlateOcr) {

    private class PlateHit(val text: String, val conf: Double, val parsed: PlateRules.Parsed?, val box: Box,
                           val score: Double, val crop: Mat)

    private fun readPlate(bgr: Mat, region: Box): PlateHit? {
        val img = MatUtil.crop(bgr, region.x0, region.y0, region.x1, region.y1) ?: return null
        val ox = max(0, region.x0); val oy = max(0, region.y0)
        val ih = img.rows(); val iw = img.cols()
        var best: PlateHit? = null
        for (c in PlateLocator.findCandidates(img)) {
            val bw = c.width; val bh = c.height
            for ((ex, ey, dx) in VARIANTS) {
                val x0 = max(0.0, c.x0 - ex * bw + dx * bw).toInt()
                val x1 = min(iw.toDouble(), c.x1 + ex * bw + dx * bw).toInt()
                val y0 = max(0.0, c.y0 - ey * bh).toInt()
                val y1 = min(ih.toDouble(), c.y1 + ey * bh).toInt()
                if (x1 - x0 < 10 || y1 - y0 < 5) continue
                val crop = MatUtil.crop(img, x0, y0, x1, y1) ?: continue
                val r = ocr.read(crop)
                val parsed = PlateRules.parse(r.text)
                // 형식이 맞으면 가산점, 글자가 잘려 짧아진 결과보다 온전한 결과를 우선
                val score = r.confidence + if (parsed != null) 1.0 + 0.03 * r.text.length else 0.0
                if (best == null || score > best.score) {
                    best = PlateHit(r.text, r.confidence, parsed, Box(x0 + ox, y0 + oy, x1 + ox, y1 + oy), score, crop)
                }
            }
        }
        // 형식도 안 맞고 글자 수도 적으면 번호판을 못 찾은 것으로 본다 (가려진/흐린 번호판 등)
        if (best != null && best.parsed == null && best.text.length < 5) return null
        return best
    }

    /** @param bgr 8UC3 BGR 이미지, @param detections 차량 검출 결과 (비어 있으면 사진 전체를 한 대로 본다) */
    fun analyze(bgr: Mat, detections: List<Detection>): AnalyzeResult {
        val H = bgr.rows(); val W = bgr.cols()
        val vs = detections.ifEmpty { listOf(Detection(null, 0f, Box(0, 0, W, H))) }
            .sortedByDescending { it.box.width.toLong() * it.box.height }
        val out = vs.map { v ->
            val box = Box(max(0, v.box.x0), max(0, v.box.y0), min(W, v.box.x1), min(H, v.box.y1))
            val crop = MatUtil.crop(bgr, box.x0, box.y0, box.x1, box.y1) ?: Mat()
            val plate = readPlate(bgr, box)
            val local = plate?.box?.offset(-box.x0, -box.y0)
            val color = VehicleColor.classify(crop, local, bgr)
            val plateColor = if (plate != null) VehicleColor.plateColor(plate.crop) else "unknown"
            compose(v, box, plate, plateColor, color)
        }
        return AnalyzeResult(W, H, out)
    }

    private fun compose(v: Detection, box: Box, plate: PlateHit?, plateColor: String,
                        color: VehicleColor.ColorResult): VehicleResult {
        val parsed = plate?.parsed
        var category: String? = null; var source: String? = null
        if (parsed != null) {
            category = if (parsed.categoryByNumber == "화물") "트럭" else parsed.categoryByNumber; source = "번호판"
        } else if (v.label != null) {
            category = VEHICLE_LABELS[v.label] ?: v.label; source = "차량 모양"
        }
        val compact = category == "승용" && box.height > 0 && box.width.toDouble() / box.height < 1.25
        return VehicleResult(
            box, v.label, v.score,
            plate?.let {
                PlateInfo(it.text, it.parsed != null, Math.round(it.conf * 1000) / 1000.0, it.box,
                    PLATE_COLOR_KO[plateColor] ?: "알 수 없음", parsed?.region, parsed?.usage)
            },
            VehicleType(category, source, plateColor == "ev", compact),
            color,
        )
    }

    companion object {
        /** (가로 확장 비율, 세로 확장 비율, 가로 이동 비율) */
        val VARIANTS = listOf(
            Triple(0.0, 0.0, 0.0), Triple(0.12, 0.15, 0.0), Triple(-0.06, -0.08, 0.0),
            Triple(0.25, 0.3, 0.0), Triple(0.05, 0.05, -0.08), Triple(0.05, 0.05, 0.08),
        )
        val VEHICLE_LABELS = mapOf("car" to "승용", "truck" to "트럭", "bus" to "버스", "motorcycle" to "이륜차")
        val PLATE_COLOR_KO = mapOf("white" to "흰색", "ev" to "파란색(전기차)", "yellow" to "노란색(사업용)",
            "green" to "녹색(구형)", "unknown" to "알 수 없음")
    }
}

/** 의존성 없는 JSON 출력 (Python recognize.py 출력과 같은 구조). */
internal object Json {
    private fun s(v: String?) = if (v == null) "null" else "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun box(b: Box) = "[${b.x0},${b.y0},${b.x1},${b.y1}]"

    fun result(r: AnalyzeResult): String = buildString {
        append("{\"image_size\":[${r.width},${r.height}],\"vehicles\":[")
        r.vehicles.forEachIndexed { i, v ->
            if (i > 0) append(',')
            append("{\"box\":${box(v.box)},")
            append("\"detector\":{\"label\":${s(v.detectorLabel)},\"score\":${Math.round(v.detectorScore * 1000) / 1000.0}},")
            val p = v.plate
            if (p == null) append("\"plate\":null,") else append(
                "\"plate\":{\"number\":${s(p.number)},\"valid_format\":${p.validFormat},\"confidence\":${p.confidence}," +
                    "\"box\":${box(p.box)},\"plate_color\":${s(p.plateColor)},\"region\":${s(p.region)},\"usage\":${s(p.usage)}},")
            append("\"vehicle_type\":{\"category\":${s(v.type.category)},\"source\":${s(v.type.source)}," +
                "\"electric\":${v.type.electric},\"compact_guess\":${v.type.compactGuess}},")
            append("\"color\":{\"name\":${s(v.color.name)},\"confidence\":${v.color.confidence},\"top3\":[" +
                v.color.top3.joinToString(",") { "[${s(it.first)},${it.second}]" } + "]}}")
        }
        append("]}")
    }
}
