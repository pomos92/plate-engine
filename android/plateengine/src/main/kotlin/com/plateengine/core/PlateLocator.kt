package com.plateengine.core

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** 정수 좌표 상자 (x0,y0)-(x1,y1), x1/y1 은 포함하지 않음. */
data class Box(val x0: Int, val y0: Int, val x1: Int, val y1: Int) {
    val width get() = x1 - x0
    val height get() = y1 - y0
    fun offset(dx: Int, dy: Int) = Box(x0 + dx, y0 + dy, x1 + dx, y1 + dy)
}

/**
 * 번호판 후보 영역 찾기 (Python engine/plate_locator.py 이식).
 * 후보를 넉넉히 뽑고, 최종 선택은 OCR 결과로 한다.
 */
object PlateLocator {
    private class Cand(val box: Box, val score: Double)

    fun findCandidates(bgr: Mat, maxCandidates: Int = 12): List<Box> {
        val H = bgr.rows(); val W = bgr.cols()
        val scale = if (max(H, W) > 640) 640.0 / max(H, W) else 1.0
        val img = Mat()
        if (scale != 1.0) Imgproc.resize(bgr, img, Size((W * scale).toInt().toDouble(), (H * scale).toInt().toDouble()))
        else bgr.copyTo(img)
        val h = img.rows(); val w = img.cols()
        val gray0 = Mat(); Imgproc.cvtColor(img, gray0, Imgproc.COLOR_BGR2GRAY)
        val gray = Mat(); Imgproc.createCLAHE(2.0, Size(8.0, 8.0)).apply(gray0, gray)

        val cands = ArrayList<Cand>()

        fun addFromMask(mask: Mat, srcBonus: Double) {
            val cnts = ArrayList<MatOfPoint>()
            Imgproc.findContours(mask, cnts, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
            for (c in cnts) {
                val r = Imgproc.boundingRect(c)
                val x = r.x; val y = r.y; val bw = r.width; val bh = r.height
                if (bw < 0.04 * w || bh < 8 || bw > 0.9 * w || bh > 0.5 * h) continue
                val ar = bw.toDouble() / bh
                if (ar < 1.4 || ar > 7.5) continue
                val fill = Imgproc.contourArea(c) / (bw * bh)
                if (fill < 0.45) continue
                val roi = gray.submat(y, y + bh, x, x + bw)
                val sob = Mat(); Imgproc.Sobel(roi, sob, CvType.CV_32F, 1, 0, 3)
                Core.absdiff(sob, Scalar(0.0), sob)
                val gx = Core.mean(sob).`val`[0]
                val mean = MatOfDouble(); val std = MatOfDouble()
                Core.meanStdDev(roi, mean, std)
                val contrast = std.toArray()[0]
                if (contrast < 18) continue
                val pos = 1.0 - abs((x + bw / 2.0) / w - 0.5) + 0.5 * ((y + bh / 2.0) / h)
                val score = gx / 40 + contrast / 60 + pos + srcBonus + (if (ar in 2.0..5.5) 0.5 else 0.0)
                cands.add(Cand(Box(x, y, x + bw, y + bh), score))
            }
        }

        // (1) 세로 경계 밀집 영역 (글자열)
        val blur = Mat(); Imgproc.GaussianBlur(gray, blur, Size(3.0, 3.0), 0.0)
        val sx = Mat(); Imgproc.Sobel(blur, sx, CvType.CV_8U, 1, 0, 3)
        val th = Mat(); Imgproc.threshold(sx, th, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        for (kw in intArrayOf(max(9, w / 40), max(15, w / 25))) {
            val m = Mat()
            Imgproc.morphologyEx(th, m, Imgproc.MORPH_CLOSE, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(kw.toDouble(), 3.0)))
            Imgproc.morphologyEx(m, m, Imgproc.MORPH_OPEN, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0)))
            addFromMask(m, 0.0)
        }

        // (2) 번호판 바탕색 영역 (흰/노랑/파랑/초록)
        val hsv = Mat(); Imgproc.cvtColor(img, hsv, Imgproc.COLOR_BGR2HSV)
        val ranges = listOf(
            Scalar(0.0, 0.0, 150.0) to Scalar(180.0, 60.0, 255.0),     // 흰색
            Scalar(15.0, 90.0, 110.0) to Scalar(38.0, 255.0, 255.0),   // 노랑
            Scalar(88.0, 70.0, 70.0) to Scalar(125.0, 255.0, 255.0),   // 파랑(전기차)
            Scalar(40.0, 60.0, 40.0) to Scalar(88.0, 255.0, 200.0),    // 초록(구형)
        )
        val k = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(max(5, w / 60).toDouble(), max(3, h / 120).toDouble()))
        for ((lo, hi) in ranges) {
            val mk = Mat(); Core.inRange(hsv, lo, hi, mk)
            Imgproc.morphologyEx(mk, mk, Imgproc.MORPH_CLOSE, k)
            addFromMask(mk, 0.3)
        }

        val kept = nms(cands, 0.5).sortedByDescending { it.score }
        return kept.take(maxCandidates).map { c ->
            val b = c.box
            val px = (0.04 * b.width).toInt(); val py = (0.12 * b.height).toInt()
            Box(max(0, ((b.x0 - px) / scale).toInt()), max(0, ((b.y0 - py) / scale).toInt()),
                min(W, ((b.x1 + px) / scale).toInt()), min(H, ((b.y1 + py) / scale).toInt()))
        }
    }

    private fun nms(c: List<Cand>, thr: Double): List<Cand> {
        val order = c.sortedByDescending { it.score }.toMutableList()
        val keep = ArrayList<Cand>()
        while (order.isNotEmpty()) {
            val a = order.removeAt(0); keep.add(a)
            order.removeAll { b ->
                val ix = max(0, min(a.box.x1, b.box.x1) - max(a.box.x0, b.box.x0))
                val iy = max(0, min(a.box.y1, b.box.y1) - max(a.box.y0, b.box.y0))
                val inter = (ix * iy).toDouble()
                val iou = inter / (a.box.width * a.box.height + b.box.width * b.box.height - inter + 1e-6)
                iou >= thr
            }
        }
        return keep
    }
}
