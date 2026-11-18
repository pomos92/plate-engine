package com.plateengine.core

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * 번호판 OCR (ONNX Runtime). 입력 [1,1,40,192] 흑백 0~1, 출력 CTC 로짓 [1,C,T].
 * 전처리는 Python engine/ocr_prep.py 와 똑같이 맞춰야 정확도가 유지된다.
 */
class PlateOcr(modelBytes: ByteArray, charsJson: String) : AutoCloseable {
    private val env = OrtEnvironment.getEnvironment()
    private val session: OrtSession = env.createSession(modelBytes, OrtSession.SessionOptions())
    private val chars: List<String>
    private val blank: Int

    init {
        // {"blank": 0, "chars": ["0", ...], "input": [...]} 를 의존성 없이 파싱
        blank = Regex("\"blank\"\\s*:\\s*(\\d+)").find(charsJson)!!.groupValues[1].toInt()
        val arr = Regex("\"chars\"\\s*:\\s*\\[(.*?)]", RegexOption.DOT_MATCHES_ALL).find(charsJson)!!.groupValues[1]
        chars = Regex("\"((?:\\\\.|[^\"\\\\])*)\"").findAll(arr).map { unescape(it.groupValues[1]) }.toList()
    }

    data class Result(val text: String, val confidence: Double)

    fun read(plateBgr: Mat, twoLine: Boolean? = null): Result {
        val input = prep(plateBgr, twoLine)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(input), longArrayOf(1, 1, OCR_H.toLong(), OCR_W.toLong())).use { t ->
            session.run(mapOf("image" to t)).use { out ->
                @Suppress("UNCHECKED_CAST")
                val logits = (out[0].value as Array<Array<FloatArray>>)[0] // [C][T]
                return decode(logits)
            }
        }
    }

    private fun decode(logits: Array<FloatArray>): Result {
        val C = logits.size; val T = logits[0].size
        val sb = StringBuilder(); val confs = ArrayList<Double>()
        var prev = -1
        for (t in 0 until T) {
            var mx = Float.NEGATIVE_INFINITY; var arg = 0
            for (c in 0 until C) if (logits[c][t] > mx) { mx = logits[c][t]; arg = c }
            var sum = 0.0
            for (c in 0 until C) sum += exp((logits[c][t] - mx).toDouble())
            val p = 1.0 / sum
            if (arg != prev && arg != blank) { sb.append(chars[arg - 1]); confs.add(p) }
            prev = arg
        }
        val score = if (confs.isEmpty()) 0.0 else exp(confs.map { ln(it + 1e-6) }.average())
        return Result(sb.toString(), score)
    }

    override fun close() { session.close() }

    companion object {
        const val OCR_H = 40
        const val OCR_W = 192
        const val TWO_LINE_ASPECT = 3.0

        private fun unescape(s: String): String =
            Regex("\\\\u([0-9a-fA-F]{4})").replace(s.replace("\\\"", "\"")) { it.groupValues[1].toInt(16).toChar().toString() }

        fun isTwoLine(m: Mat) = m.cols().toDouble() / max(m.rows(), 1) < TWO_LINE_ASPECT

        /** 번호판 BGR 이미지 -> 길이 40*192 float 배열 (Python prep_plate 와 동일). */
        fun prep(plateBgr: Mat, twoLine: Boolean? = null): FloatArray {
            val two = twoLine ?: isTwoLine(plateBgr)
            var g = Mat()
            if (plateBgr.channels() == 3) Imgproc.cvtColor(plateBgr, g, Imgproc.COLOR_BGR2GRAY) else plateBgr.copyTo(g)
            if (two) {
                val h = g.rows()
                val top = g.rowRange(0, (h * 0.45).toInt())
                val bot = g.rowRange((h * 0.38).toInt(), h)
                val th = OCR_H
                val tr = Mat(); val br = Mat()
                Imgproc.resize(top, tr, Size(max(1, (top.cols() * th.toDouble() / max(top.rows(), 1)).toInt()).toDouble(), th.toDouble()))
                Imgproc.resize(bot, br, Size(max(1, (bot.cols() * th.toDouble() / max(bot.rows(), 1)).toInt()).toDouble(), th.toDouble()))
                val cat = Mat(); Core.hconcat(listOf(tr, br), cat); g = cat
            }
            val r = Mat()
            Imgproc.resize(g, r, Size(OCR_W.toDouble(), OCR_H.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
            val f = Mat(); r.convertTo(f, CvType.CV_32F, 1.0 / 255.0)
            val data = FloatArray(OCR_H * OCR_W); f.get(0, 0, data)
            val sorted = data.copyOf(); sorted.sort()
            val lo = MatUtil.percentile(sorted, 2.0); val hi = MatUtil.percentile(sorted, 98.0)
            val den = max(hi - lo, 1e-3)
            for (i in data.indices) data[i] = ((data[i] - lo) / den).coerceIn(0.0, 1.0).toFloat()
            return data
        }
    }
}
