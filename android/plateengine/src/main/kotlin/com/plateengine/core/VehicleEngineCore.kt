package com.plateengine.core

import org.opencv.core.Mat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin

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

    /** 조각 하나를 읽어 지금까지 최고 결과와 비교. 애매한 비율은 1줄/2줄 둘 다 읽어 본다. */
    private fun tryCrop(crop: Mat, box: Box, best0: PlateHit?, modes0: List<Boolean?>? = null): PlateHit? {
        var best = best0
        if (crop.cols() < 10 || crop.rows() < 5) return best
        val ar = crop.cols().toDouble() / max(crop.rows(), 1)
        val modes: List<Boolean?> = modes0 ?: if (ar in 2.0..3.6) listOf(false, true) else listOf(null)
        for (two in modes) {
            val r = ocr.read(crop, two)
            val parsed = PlateRules.parse(r.text)
            // 형식이 맞으면 가산점, 글자가 잘려 짧아진 결과보다 온전한 결과를 우선
            val score = r.confidence + if (parsed != null) 1.0 + 0.03 * r.text.length else 0.0
            if (best == null || score > best.score) best = PlateHit(r.text, r.confidence, parsed, box, score, crop)
        }
        return best
    }

    private fun readPlate(bgr: Mat, region: Box): PlateHit? {
        val img = MatUtil.crop(bgr, region.x0, region.y0, region.x1, region.y1) ?: return null
        val ox = max(0, region.x0); val oy = max(0, region.y0)
        val ih = img.rows(); val iw = img.cols()
        var best: PlateHit? = null
        // (1) 글자 모양으로 찾은 후보: 기울기를 바로잡아 똑바로 편 조각을 읽는다
        for (c in CharGroups.candidates(img)) {
            val cx = (c.box.x0 + c.box.x1) / 2.0; val cy = (c.box.y0 + c.box.y1) / 2.0
            val rad = Math.toRadians(c.angle)
            val span = c.box.width / max(cos(rad), 0.5)
            val box = c.box.offset(ox, oy)
            // 글자 줄을 기준으로 잘랐으니 1줄로 읽고, 마지막(윗줄까지 넉넉히)만 2줄로도 읽는다
            for ((i, v) in GROUP_VARIANTS.withIndex()) {
                val (wmul, hmul, up) = v
                val ccx = cx - up * c.charH * sin(rad); val ccy = cy - up * c.charH * cos(rad)
                val modes: List<Boolean?> = if (i == GROUP_VARIANTS.lastIndex) listOf(false, true) else listOf(false)
                best = tryCrop(CharGroups.rectCrop(img, ccx, ccy, span * wmul, c.charH * hmul, c.angle), box, best, modes)
            }
        }
        // (2) 경계·색으로 찾은 후보: 상자 오차를 감안해 크기/위치를 조금씩 바꿔 읽고, 기울어져 있으면 바로잡아서도 읽는다
        for (c in PlateLocator.findCandidates(img)) {
            val bw = c.width; val bh = c.height
            for ((k, v) in VARIANTS.withIndex()) {
                val (ex, ey, dx) = v
                val x0 = max(0.0, c.x0 - ex * bw + dx * bw).toInt()
                val x1 = min(iw.toDouble(), c.x1 + ex * bw + dx * bw).toInt()
                val y0 = max(0.0, c.y0 - ey * bh).toInt()
                val y1 = min(ih.toDouble(), c.y1 + ey * bh).toInt()
                if (x1 - x0 < 10 || y1 - y0 < 5) continue
                val crop = MatUtil.crop(img, x0, y0, x1, y1) ?: continue
                val box = Box(x0 + ox, y0 + oy, x1 + ox, y1 + oy)
                best = tryCrop(crop, box, best)
                if (k < 2) {
                    val d = CharGroups.deskew(crop)
                    if (d != null) best = tryCrop(d, box, best)
                }
            }
        }
        // 형식도 안 맞고 글자 수도 적으면 번호판을 못 찾은 것으로 본다 (가려진/흐린 번호판 등)
        val b = best
        if (b != null && b.parsed == null && b.text.length < 5) return null
        return b
    }

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
        } else if (v.label != null && v.score >= 0.5f) {  // 검출 점수가 낮으면 car/truck 구분을 믿기 어렵다
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
        /** 글자 줄 후보 조각: (가로 배율, 세로=글자높이×배율, 위로 이동=글자높이×배율) — 1줄, 여유, 2줄(윗줄 포함) */
        val GROUP_VARIANTS = listOf(Triple(1.0, 1.9, 0.0), Triple(1.12, 2.4, 0.0), Triple(1.05, 3.3, 0.55))
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
