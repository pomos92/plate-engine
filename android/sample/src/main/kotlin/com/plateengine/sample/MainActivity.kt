package com.plateengine.sample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.plateengine.PlateEngine
import com.plateengine.core.AnalyzeResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 갤러리에서 사진을 고르면 기기 안에서 인식하고 결과를 그려 보여주는 예제. */
class MainActivity : ComponentActivity() {
    private var engine: PlateEngine? = null
    private lateinit var image: ImageView
    private lateinit var text: TextView

    private val pick = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) analyze(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        val button = Button(this).apply {
            text = "사진 고르기"
            setOnClickListener { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
        }
        image = ImageView(this).apply { adjustViewBounds = true }
        text = TextView(this).apply { textSize = 16f; text = "모델 불러오는 중…" }
        root.addView(button, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(image, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        root.addView(text, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        setContentView(ScrollView(this).apply { addView(root) })

        lifecycleScope.launch {
            engine = withContext(Dispatchers.Default) { PlateEngine(applicationContext) }
            text.text = "사진을 골라 주세요"
        }
    }

    private fun analyze(uri: Uri) {
        val eng = engine ?: return
        text.text = "분석 중…"
        lifecycleScope.launch {
            val (bmp, result, ms) = withContext(Dispatchers.Default) {
                val bmp = decode(uri)
                val t = System.currentTimeMillis()
                val r = eng.analyze(bmp)
                Triple(bmp, r, System.currentTimeMillis() - t)
            }
            image.setImageBitmap(draw(bmp, result))
            text.text = describe(result) + "\n(${ms}ms)"
        }
    }

    /** 긴 변 1600px 로 줄여서 읽기 (정확도에 충분하고 메모리 절약). */
    private fun decode(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        return contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts)!! }
    }

    private fun describe(r: AnalyzeResult): String = buildString {
        if (r.vehicles.isEmpty()) append("차량을 찾지 못했습니다")
        r.vehicles.forEachIndexed { i, v ->
            append("${i + 1}. ")
            append(v.plate?.number ?: "번호판 없음")
            v.type.category?.let { append(" · $it") }
            if (v.type.electric) append(" · 전기차")
            if (v.type.compactGuess) append(" · 경차(추정)")
            v.color.name?.let { append(" · $it") }
            append('\n')
        }
    }

    private fun draw(src: Bitmap, r: AnalyzeResult): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(out)
        val stroke = maxOf(3f, out.width / 300f)
        val car = Paint().apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = Color.CYAN }
        val plate = Paint().apply { style = Paint.Style.STROKE; strokeWidth = stroke; color = Color.RED }
        val label = Paint().apply { color = Color.YELLOW; textSize = out.width / 30f; isFakeBoldText = true }
        val bg = Paint().apply { color = Color.argb(180, 0, 0, 0) }
        for (v in r.vehicles) {
            c.drawRect(v.box.x0.toFloat(), v.box.y0.toFloat(), v.box.x1.toFloat(), v.box.y1.toFloat(), car)
            v.plate?.let { p -> c.drawRect(p.box.x0.toFloat(), p.box.y0.toFloat(), p.box.x1.toFloat(), p.box.y1.toFloat(), plate) }
            val t = listOfNotNull(v.plate?.number, v.type.category, v.color.name).joinToString(" · ")
            val y = maxOf(label.textSize, v.box.y0 - 8f)
            c.drawRect(v.box.x0.toFloat(), y - label.textSize, v.box.x0 + label.measureText(t), y + 6, bg)
            c.drawText(t, v.box.x0.toFloat(), y, label)
        }
        return out
    }

    override fun onDestroy() {
        engine?.close()
        super.onDestroy()
    }
}
