package com.plateengine.core

import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 글자 모양으로 번호판 찾기 (Python engine/plate_locator.py 의 char_group_candidates / deskew 이식).
 * 글자처럼 생긴 덩어리를 찾고, 크기가 비슷한 것끼리 한 줄(기울어도 됨)로 이어지면 번호판 후보.
 */
object CharGroups {
    private class Blob(val x: Int, val y: Int, val w: Int, val h: Int, val cx: Double, val cy: Double, val pol: Int)

    /** 후보: 원본 좌표 상자, 글자 줄 기울기(도), 글자 높이. */
    data class Cand(val box: Box, val angle: Double, val charH: Double)

    private fun blobs(gray: Mat): List<Blob> {
        val H = gray.rows(); val W = gray.cols()
        val bs = max(15, (min(H, W) / 20) or 1)
        val out = ArrayList<Blob>()
        for ((pol, inv) in listOf(0 to Imgproc.THRESH_BINARY_INV, 1 to Imgproc.THRESH_BINARY)) {
            val b = Mat()
            Imgproc.adaptiveThreshold(gray, b, 255.0, Imgproc.ADAPTIVE_THRESH_MEAN_C, inv, bs, 9.0)
            val labels = Mat(); val st = Mat(); val cen = Mat()
            val n = Imgproc.connectedComponentsWithStats(b, labels, st, cen, 8)
            for (i in 1 until n) {
                val x = st.get(i, 0)[0].toInt(); val y = st.get(i, 1)[0].toInt()
                val w = st.get(i, 2)[0].toInt(); val h = st.get(i, 3)[0].toInt(); val a = st.get(i, 4)[0]
                if (h < 9 || h > 0.4 * H || w < 2) continue
                val r = w.toDouble() / h
                if (r < 0.08 || r > 1.15) continue  // 숫자 1은 아주 가늘다
                val fill = a / (w * h)
                if (fill < 0.12 || fill > 0.92) continue
                out.add(Blob(x, y, w, h, x + w / 2.0, y + h / 2.0, pol))
            }
            b.release(); labels.release(); st.release(); cen.release()
        }
        return out
    }

    private fun groups(bl: List<Blob>, minN: Int = 4): List<List<Blob>> {
        val n = bl.size
        if (n < minN) return emptyList()
        val parent = IntArray(n) { it }
        fun find(i0: Int): Int { var i = i0; while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i] }; return i }
        val order = (0 until n).sortedBy { bl[it].cx }
        for ((ii, i) in order.withIndex()) {
            val a = bl[i]
            for (jj in ii + 1 until n) {
                val b = bl[order[jj]]
                val dx = b.cx - a.cx
                if (dx > 2.4 * max(a.h, b.h)) break  // 한글은 여러 조각으로 갈라져서 사이가 벌어질 수 있다
                if (a.pol != b.pol || dx <= 0.15 * min(a.h, b.h)) continue
                if (max(a.h, b.h).toDouble() / min(a.h, b.h) > 1.35) continue
                if (abs(b.cy - a.cy) > 0.25 * max(a.h, b.h) + 0.45 * dx) continue
                parent[find(order[jj])] = find(i)
            }
        }
        return (0 until n).groupBy { find(it) }.values.filter { it.size >= minN }.map { g -> g.map { bl[it] } }
    }

    /** 최소제곱 직선 기울기 -> 각도(도). */
    private fun angleOf(g: List<Blob>): Double {
        val mx = g.sumOf { it.cx } / g.size; val my = g.sumOf { it.cy } / g.size
        var sxy = 0.0; var sxx = 0.0
        for (b in g) { sxy += (b.cx - mx) * (b.cy - my); sxx += (b.cx - mx) * (b.cx - mx) }
        return if (sxx == 0.0) 0.0 else Math.toDegrees(atan(sxy / sxx))
    }

    fun candidates(bgr: Mat, maxCandidates: Int = 8): List<Cand> {
        val H = bgr.rows(); val W = bgr.cols()
        val scale = if (max(H, W) > 1000) 1000.0 / max(H, W) else 1.0
        val img = Mat()
        if (scale != 1.0) Imgproc.resize(bgr, img, Size((W * scale).toInt().toDouble(), (H * scale).toInt().toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
        else bgr.copyTo(img)
        val g0 = Mat(); Imgproc.cvtColor(img, g0, Imgproc.COLOR_BGR2GRAY)
        val gray = Mat(); Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(g0, gray)
        class R(val score: Double, val x0: Double, val y0: Double, val x1: Double, val y1: Double, val ang: Double, val hm: Double)
        val res = ArrayList<R>()
        for (g0s in groups(blobs(gray))) {
            val g = g0s.sortedBy { it.cx }
            val hs = g.map { it.h.toDouble() }.sorted()
            val hm = if (hs.size % 2 == 1) hs[hs.size / 2] else (hs[hs.size / 2 - 1] + hs[hs.size / 2]) / 2
            val span = g.last().cx - g.first().cx
            if (span < 2.0 * hm || span > 12 * hm) continue
            val ang = angleOf(g)
            if (abs(ang) > 28) continue
            val mean = hs.average(); val sd = sqrt(hs.sumOf { (it - mean) * (it - mean) } / hs.size)
            val x0 = g.minOf { it.x }.toDouble(); val x1 = g.maxOf { it.x + it.w }.toDouble()
            val y0 = g.minOf { it.y }.toDouble(); val y1 = g.maxOf { it.y + it.h }.toDouble()
            res.add(R(g.size - 2 * sd / hm, x0 - 0.5 * hm, y0 - 0.35 * hm, x1 + 0.5 * hm, y1 + 0.35 * hm, ang, hm))
        }
        img.release(); g0.release(); gray.release()
        return res.sortedByDescending { it.score }.take(maxCandidates).map {
            Cand(Box(max(0, (it.x0 / scale).toInt()), max(0, (it.y0 / scale).toInt()),
                min(W, (it.x1 / scale).toInt()), min(H, (it.y1 / scale).toInt())), it.ang, it.hm / scale)
        }
    }

    /** (cx,cy) 중심, angle(도)만큼 기운 w×h 영역을 똑바로 펴서 잘라낸다. */
    fun rectCrop(img: Mat, cx: Double, cy: Double, w: Double, h: Double, angle: Double): Mat {
        val m = Imgproc.getRotationMatrix2D(Point(cx, cy), angle, 1.0)
        m.put(0, 2, m.get(0, 2)[0] + w / 2 - cx); m.put(1, 2, m.get(1, 2)[0] + h / 2 - cy)
        val out = Mat()
        Imgproc.warpAffine(img, out, m, Size(w.toInt().toDouble(), h.toInt().toDouble()), Imgproc.INTER_LINEAR, Core.BORDER_REPLICATE)
        return out
    }

    /** 조각 안의 글자로 기울기를 추정해 수평으로 돌린다. 2도 미만이면 null (그대로 쓰면 됨). */
    fun deskew(crop: Mat): Mat? {
        val gray = Mat(); Imgproc.cvtColor(crop, gray, Imgproc.COLOR_BGR2GRAY)
        val gs = groups(blobs(gray), 3); gray.release()
        val g = gs.maxByOrNull { it.size } ?: return null
        val ang = angleOf(g)
        if (abs(ang) < 2) return null
        return rectCrop(crop, crop.cols() / 2.0, crop.rows() / 2.0, crop.cols().toDouble(), crop.rows().toDouble(), ang)
    }
}
