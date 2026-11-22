package com.plateengine.sample.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/** 정면 자동차 + 번호판 스캔 틀 그림 (이미지 파일 없이 코드로 그림). */
@Composable
fun CarIllustration(modifier: Modifier = Modifier, plateText: String? = null) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val w = size.width; val h = size.height
        fun x(f: Float) = w * f
        fun y(f: Float) = h * f

        // 바닥 그림자
        drawOval(
            Brush.radialGradient(listOf(Color(0x552B6BE0), Color.Transparent), center = Offset(x(.5f), y(.93f)), radius = x(.48f)),
            topLeft = Offset(x(.04f), y(.86f)), size = Size(x(.92f), y(.13f)),
        )
        // 바퀴
        for (cx in listOf(.17f, .83f)) {
            drawRoundRect(Color(0xFF1C2433), topLeft = Offset(x(cx - .065f), y(.74f)), size = Size(x(.13f), y(.19f)), cornerRadius = CornerRadius(x(.03f)))
        }
        // 사이드미러
        for (s in listOf(-1f, 1f)) {
            val mx = if (s < 0) .055f else .835f
            drawRoundRect(Brush.verticalGradient(listOf(Color.White, Color(0xFFCBD5E4)), startY = y(.30f), endY = y(.38f)),
                topLeft = Offset(x(mx), y(.30f)), size = Size(x(.11f), y(.075f)), cornerRadius = CornerRadius(x(.03f)))
        }
        // 차체 (아래 몸통)
        val body = Path().apply {
            moveTo(x(.13f), y(.40f))
            cubicTo(x(.20f), y(.35f), x(.80f), y(.35f), x(.87f), y(.40f))
            lineTo(x(.93f), y(.55f))
            lineTo(x(.93f), y(.80f))
            quadraticBezierTo(x(.93f), y(.85f), x(.88f), y(.85f))
            lineTo(x(.12f), y(.85f))
            quadraticBezierTo(x(.07f), y(.85f), x(.07f), y(.80f))
            lineTo(x(.07f), y(.55f))
            close()
        }
        drawPath(body, Brush.verticalGradient(listOf(Color.White, Color(0xFFE6ECF5), Color(0xFFC9D3E3)), startY = y(.35f), endY = y(.85f)))
        // 지붕 + 앞유리
        val cabin = Path().apply {
            moveTo(x(.25f), y(.13f))
            cubicTo(x(.35f), y(.09f), x(.65f), y(.09f), x(.75f), y(.13f))
            lineTo(x(.86f), y(.39f))
            cubicTo(x(.70f), y(.36f), x(.30f), y(.36f), x(.14f), y(.39f))
            close()
        }
        drawPath(cabin, Brush.verticalGradient(listOf(Color(0xFFF7F9FC), Color(0xFFDCE3EE)), startY = y(.09f), endY = y(.39f)))
        val glass = Path().apply {
            moveTo(x(.28f), y(.16f))
            cubicTo(x(.38f), y(.135f), x(.62f), y(.135f), x(.72f), y(.16f))
            lineTo(x(.80f), y(.355f))
            cubicTo(x(.66f), y(.335f), x(.34f), y(.335f), x(.20f), y(.355f))
            close()
        }
        drawPath(glass, Brush.verticalGradient(listOf(Color(0xFF3B4C6E), Color(0xFF16213A)), startY = y(.14f), endY = y(.36f)))
        // 유리 반사
        drawLine(Color(0x33FFFFFF), Offset(x(.40f), y(.17f)), Offset(x(.33f), y(.33f)), strokeWidth = x(.03f), cap = StrokeCap.Round)
        // 전조등
        for (s in listOf(-1f, 1f)) {
            val p = Path().apply {
                if (s < 0) {
                    moveTo(x(.11f), y(.47f)); lineTo(x(.33f), y(.52f)); lineTo(x(.30f), y(.56f)); lineTo(x(.13f), y(.54f))
                } else {
                    moveTo(x(.89f), y(.47f)); lineTo(x(.67f), y(.52f)); lineTo(x(.70f), y(.56f)); lineTo(x(.87f), y(.54f))
                }
                close()
            }
            drawPath(p, Color(0xFF1B2A44))
            drawPath(p, AppColors.Glow, style = Stroke(width = x(.008f)))
            val cx = if (s < 0) .22f else .78f
            drawCircle(Brush.radialGradient(listOf(Color.White, AppColors.Glow.copy(alpha = .6f), Color.Transparent), center = Offset(x(cx), y(.52f)), radius = x(.05f)),
                radius = x(.05f), center = Offset(x(cx), y(.52f)))
        }
        // 그릴
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF2A3346), Color(0xFF111827)), startY = y(.53f), endY = y(.66f)),
            topLeft = Offset(x(.31f), y(.535f)), size = Size(x(.38f), y(.12f)), cornerRadius = CornerRadius(x(.03f)))
        for (i in 1..3) drawLine(Color(0xFF3A465E), Offset(x(.33f), y(.535f + i * .03f)), Offset(x(.67f), y(.535f + i * .03f)), strokeWidth = 2f)
        // 하단 공기흡입구
        drawRoundRect(Color(0xFF1C2433), topLeft = Offset(x(.20f), y(.76f)), size = Size(x(.60f), y(.05f)), cornerRadius = CornerRadius(x(.02f)))

        // 번호판 + 스캔 틀
        val pl = Offset(x(.335f), y(.655f)); val ps = Size(x(.33f), y(.09f))
        drawRoundRect(AppColors.Glow.copy(alpha = .25f), topLeft = pl - Offset(8f, 8f), size = Size(ps.width + 16f, ps.height + 16f), cornerRadius = CornerRadius(x(.025f)))
        drawRoundRect(Color.White, topLeft = pl, size = ps, cornerRadius = CornerRadius(x(.015f)))
        drawRoundRect(AppColors.Glow, topLeft = pl, size = ps, cornerRadius = CornerRadius(x(.015f)), style = Stroke(width = x(.008f)))
        if (plateText != null) {
            val style = TextStyle(fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, color = Color(0xFF111827),
                fontSize = (ps.height * .62f / density).sp)
            val layout = measurer.measure(plateText, style)
            drawText(layout, topLeft = Offset(pl.x + (ps.width - layout.size.width) / 2, pl.y + (ps.height - layout.size.height) / 2))
        }
        scanCorners(Offset(x(.30f), y(.615f)), Size(x(.40f), y(.17f)), x(.05f), x(.012f))
    }
}

private fun DrawScope.scanCorners(tl: Offset, s: Size, len: Float, stroke: Float) {
    val c = AppColors.Glow
    val glow = c.copy(alpha = .35f)
    val corners = listOf(
        Triple(tl, 1f, 1f), Triple(Offset(tl.x + s.width, tl.y), -1f, 1f),
        Triple(Offset(tl.x, tl.y + s.height), 1f, -1f), Triple(Offset(tl.x + s.width, tl.y + s.height), -1f, -1f),
    )
    for ((p, dx, dy) in corners) {
        for ((col, sw) in listOf(glow to stroke * 2.6f, c to stroke)) {
            drawLine(col, p, Offset(p.x + dx * len, p.y), strokeWidth = sw, cap = StrokeCap.Round)
            drawLine(col, p, Offset(p.x, p.y + dy * len), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}
