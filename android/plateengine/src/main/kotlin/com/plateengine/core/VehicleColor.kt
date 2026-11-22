package com.plateengine.core

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/** 차량 색상 / 번호판 바탕색 판별 (Python engine/vehicle_color.py 이식). */
object VehicleColor {
    data class ColorResult(val name: String?, val confidence: Double, val top3: List<Pair<String, Double>>)

    private fun bodyMask(h: Int, w: Int, plate: Box?): BooleanArray {
        val m = BooleanArray(h * w)
        fun fill(y0: Int, y1: Int, x0: Int, x1: Int, v: Boolean) {
            for (y in max(0, y0) until minOf(h, y1)) for (x in max(0, x0) until minOf(w, x1)) m[y * w + x] = v
        }
        fill((h * 0.28).toInt(), (h * 0.62).toInt(), (w * 0.15).toInt(), (w * 0.85).toInt(), true)
        fill((h * 0.60).toInt(), (h * 0.85).toInt(), (w * 0.05).toInt(), (w * 0.19).toInt(), true)
        fill((h * 0.60).toInt(), (h * 0.85).toInt(), (w * 0.81).toInt(), (w * 0.95).toInt(), true)
        if (plate != null) fill(plate.y0 - 3, plate.y1 + 3, plate.x0 - 3, plate.x1 + 3, false)
        return m
    }

    private fun nameFromLab(L: Double, a: Double, b: Double, sceneL: Double): String {
        val chroma = hypot(a - 128, b - 128)
        val hue0 = (Math.toDegrees(kotlin.math.atan2(b - 128, a - 128)) + 360) % 360
        // 회색·은색 차는 하늘이 비쳐 푸르스름하게 찍히기 쉬워서, 파란 쪽은 무채색 기준을 넉넉하게
        if (chroma < 14 || (hue0 >= 220 && hue0 < 300 && chroma < 22)) {
            val r = L / max(sceneL, 60.0)
            return when {
                L > 205 || r > 0.9 -> "흰색"; r > 0.65 -> "은색"; r > 0.45 -> "회색"; else -> "검정"
            }
        }
        val hue = (Math.toDegrees(atan2(b - 128, a - 128)) + 360) % 360
        if (L < 70 && chroma < 30) return if (hue >= 220 && hue < 320) "남색" else if (hue < 90) "갈색" else "검정"
        return when {
            hue < 40 || hue >= 345 -> "빨강"
            hue < 70 -> if (L > 120) "주황" else "갈색"
            hue < 100 -> if (chroma > 40) "노랑" else "베이지"
            hue < 200 -> "초록"
            hue < 300 -> if (L >= 70) "파랑" else "남색"
            else -> "빨강"
        }
    }

    /** 차체 영역 픽셀을 k-means(3)로 묶어 덩어리별 색 이름에 투표. */
    fun classify(vehicleBgr: Mat, plateBoxLocal: Box? = null, sceneBgr: Mat? = null): ColorResult {
        if (vehicleBgr.empty()) return ColorResult(null, 0.0, emptyList())
        val H0 = vehicleBgr.rows(); val W0 = vehicleBgr.cols()
        val img = Mat(); Imgproc.resize(vehicleBgr, img, Size(160.0, 120.0), 0.0, 0.0, Imgproc.INTER_AREA)
        val pb = plateBoxLocal?.let {
            val sx = 160.0 / W0; val sy = 120.0 / H0
            Box((it.x0 * sx).toInt(), (it.y0 * sy).toInt(), (it.x1 * sx).toInt(), (it.y1 * sy).toInt())
        }
        val mask = bodyMask(120, 160, pb)
        val lab = Mat(); Imgproc.cvtColor(img, lab, Imgproc.COLOR_BGR2Lab)
        val bytes = MatUtil.toBytes(lab)
        val n = mask.count { it }
        if (n < 50) return ColorResult(null, 0.0, emptyList())
        val samples = Mat(n, 3, CvType.CV_32F)
        val buf = FloatArray(n * 3); var k = 0
        for (i in mask.indices) if (mask[i]) {
            for (c in 0 until 3) buf[k * 3 + c] = (bytes[i * 3 + c].toInt() and 0xff).toFloat()
            k++
        }
        samples.put(0, 0, buf)
        val labels = Mat(); val centers = Mat()
        Core.kmeans(samples, 3, labels, TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 20, 1.0), 3, Core.KMEANS_PP_CENTERS, centers)
        val lab32 = IntArray(n); labels.get(0, 0, lab32)
        val counts = IntArray(3); for (l in lab32) counts[l]++
        val cen = FloatArray(9); centers.get(0, 0, cen)

        val ref = sceneBgr ?: vehicleBgr
        val small = Mat(); Imgproc.resize(ref, small, Size(200.0, 150.0))
        val sLab = Mat(); Imgproc.cvtColor(small, sLab, Imgproc.COLOR_BGR2Lab)
        val sb = MatUtil.toBytes(sLab)
        val Ls = FloatArray(sb.size / 3) { (sb[it * 3].toInt() and 0xff).toFloat() }
        val sceneL = MatUtil.median(Ls)

        val votes = LinkedHashMap<String, Double>()
        for (i in 0 until 3) {
            val name = nameFromLab(cen[i * 3].toDouble(), cen[i * 3 + 1].toDouble(), cen[i * 3 + 2].toDouble(), sceneL)
            votes[name] = (votes[name] ?: 0.0) + counts[i].toDouble() / n
        }
        val ranked = votes.entries.sortedByDescending { it.value }.map { it.key to Math.round(it.value * 1000) / 1000.0 }
        return ColorResult(ranked[0].first, ranked[0].second, ranked.take(3))
    }

    /** 번호판 바탕색: white / ev(파랑) / yellow / green / unknown */
    fun plateColor(plateBgr: Mat?): String {
        if (plateBgr == null || plateBgr.empty()) return "unknown"
        val hsv = Mat(); Imgproc.cvtColor(plateBgr, hsv, Imgproc.COLOR_BGR2HSV)
        val b = MatUtil.toBytes(hsv)
        val n = b.size / 3
        val v = FloatArray(n) { (b[it * 3 + 2].toInt() and 0xff) / 255f }
        val sorted = v.copyOf(); sorted.sort()
        val p40 = MatUtil.percentile(sorted, 40.0)
        val hh = ArrayList<Float>(); var satCount = 0; var bgCount = 0
        for (i in 0 until n) if (v[i] >= p40) {
            bgCount++
            val s = (b[i * 3 + 1].toInt() and 0xff) / 255.0
            if (s > 0.28) { satCount++; hh.add(((b[i * 3].toInt() and 0xff) * 2).toFloat()) }
        }
        if (bgCount < 20) return "unknown"
        if (satCount.toDouble() / bgCount < 0.35) return "white"
        val hm = MatUtil.median(hh.toFloatArray())
        return when {
            hm in 180.0..250.0 -> "ev"
            hm >= 30 && hm < 75 -> "yellow"
            hm >= 75 && hm < 180 -> "green"
            else -> "white"
        }
    }
}
