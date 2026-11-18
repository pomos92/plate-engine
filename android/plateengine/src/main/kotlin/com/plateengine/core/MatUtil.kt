package com.plateengine.core

import org.opencv.core.Mat
import org.opencv.core.Rect

internal object MatUtil {
    /** numpy.percentile(linear) 과 같은 방식의 백분위수. */
    fun percentile(sorted: FloatArray, q: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val pos = (sorted.size - 1) * q / 100.0
        val lo = kotlin.math.floor(pos).toInt()
        val hi = kotlin.math.min(lo + 1, sorted.size - 1)
        val f = pos - lo
        return sorted[lo] + (sorted[hi] - sorted[lo]) * f
    }

    fun median(values: FloatArray): Double {
        val s = values.copyOf(); s.sort(); return percentile(s, 50.0)
    }

    /** Python 슬라이싱처럼 범위를 잘라낸 ROI (빈 영역이면 null). */
    fun crop(m: Mat, x0: Int, y0: Int, x1: Int, y1: Int): Mat? {
        val a = x0.coerceIn(0, m.cols()); val b = y0.coerceIn(0, m.rows())
        val c = x1.coerceIn(0, m.cols()); val d = y1.coerceIn(0, m.rows())
        if (c <= a || d <= b) return null
        return m.submat(Rect(a, b, c - a, d - b))
    }

    fun toBytes(m: Mat): ByteArray {
        val c = if (m.isContinuous) m else m.clone()
        val out = ByteArray((c.total() * c.channels()).toInt())
        c.get(0, 0, out)
        return out
    }
}
