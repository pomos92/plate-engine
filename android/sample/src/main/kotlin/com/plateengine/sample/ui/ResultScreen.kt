package com.plateengine.sample.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.max
import kotlin.math.min

/**
 * 결과 화면: 확인된 차량번호를 번호판 모양으로 가운데 크게, 아래에 차량 정보.
 */
@Composable
fun ResultScreen(
    result: ResultUi,
    onBack: () -> Unit,
    onSave: (Bitmap) -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    var showOriginal by remember { mutableStateOf(false) }
    val vehicle = result.vehicles.getOrNull(selected)
    val annotated = remember(result) { annotate(result.image, result.vehicles) }

    Column(Modifier.fillMaxSize().background(AppColors.Bg)) {
        // 상단 바
        Box(
            Modifier.fillMaxWidth().background(AppColors.NavyDeep).statusBarsPadding().height(60.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", tint = Color.White,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp).size(48.dp).clip(CircleShape)
                    .clickable(onClick = onBack).padding(12.dp))
            Text("차량번호 인식 결과", fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 21.sp, color = Color.White)
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // 사진 (선택한 차 주변 확대)
            Box(
                Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(AppColors.NavyDeep, AppColors.Navy, AppColors.Bg)))
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            ) {
                val hero = remember(result, selected) { heroCrop(annotated, vehicle) }
                Image(
                    hero.asImageBitmap(), contentDescription = "인식한 사진",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp))
                        .border(1.5.dp, AppColors.Glow.copy(alpha = .6f), RoundedCornerShape(20.dp)),
                )
            }

            if (result.vehicles.size > 1) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    result.vehicles.forEachIndexed { i, v ->
                        val on = i == selected
                        Text(
                            "차량 ${i + 1}" + (v.plateNumber?.let { " · $it" } ?: ""),
                            fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            color = if (on) Color.White else AppColors.Navy,
                            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                                .background(if (on) AppColors.Blue else Color.White)
                                .border(1.dp, if (on) AppColors.Blue else AppColors.Line, RoundedCornerShape(20.dp))
                                .clickable { selected = i }.padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                PlateCard(vehicle)
                Spacer(Modifier.height(16.dp))
                if (vehicle != null) InfoCard(vehicle)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFFE8EEF8)).padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.CalendarMonth, null, tint = AppColors.Blue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("인식일시", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = AppColors.TextSub)
                    Spacer(Modifier.weight(1f))
                    Text(result.recognizedAt, fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AppColors.TextMain)
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // 하단 버튼
        Row(
            Modifier.fillMaxWidth().background(AppColors.Bg).navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BottomButton(Icons.Rounded.Image, "원본 이미지", primary = false, Modifier.weight(1f)) { showOriginal = true }
            BottomButton(Icons.Rounded.SaveAlt, "결과 저장", primary = false, Modifier.weight(1f)) { onSave(annotated) }
            BottomButton(Icons.Rounded.CheckCircle, "확인", primary = true, Modifier.weight(1f), onClick = onBack)
        }
    }

    if (showOriginal) {
        Dialog(onDismissRequest = { showOriginal = false }) {
            Image(annotated.asImageBitmap(), "원본 이미지", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showOriginal = false })
        }
    }
}

@Composable
private fun PlateCard(v: VehicleUi?) {
    val found = v?.plateNumber != null
    Column(
        Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(24.dp), spotColor = AppColors.Blue)
            .clip(RoundedCornerShape(24.dp)).background(Brush.verticalGradient(listOf(Color.White, Color(0xFFF1F6FF))))
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (found) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline, null,
                tint = if (found) AppColors.Ok else AppColors.Warn, modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (found) "차량번호 인식 완료" else "번호판을 찾지 못했어요",
                fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = AppColors.TextMain,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (found) {
            PlateView(v!!.plateNumber!!, v.plateKind)
            if (!v.plateValid) {
                Spacer(Modifier.height(10.dp))
                Text("번호 형식이 맞지 않아요. 번호판이 잘 보이게 다시 찍어 주세요.", fontFamily = Pretendard,
                    fontSize = 14.sp, color = AppColors.Warn, textAlign = TextAlign.Center)
            }
        } else {
            Text("번호판이 정면으로 크게 보이도록 다시 찍어 주세요", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = AppColors.TextSub, textAlign = TextAlign.Center)
        }
    }
}

/** 실제 번호판처럼 보이는 큰 번호 표시. 전기차는 파란 판, 사업용은 노란 판. */
@Composable
fun PlateView(number: String, kind: PlateKind) {
    val (bg, fg) = when (kind) {
        PlateKind.EV -> Brush.horizontalGradient(listOf(Color(0xFF5AA9F0), Color(0xFF3C86D6))) to Color(0xFF0B1220)
        PlateKind.YELLOW -> Brush.horizontalGradient(listOf(Color(0xFFFFD43B), Color(0xFFF5BE12))) to Color(0xFF0B1220)
        PlateKind.GREEN -> Brush.horizontalGradient(listOf(Color(0xFF2E8B57), Color(0xFF237247))) to Color.White
        PlateKind.WHITE -> Brush.horizontalGradient(listOf(Color.White, Color(0xFFF4F6FA))) to Color(0xFF0B1220)
    }
    val text = formatPlate(number)
    val size = when {
        text.length <= 8 -> 50.sp; text.length <= 9 -> 46.sp; text.length <= 11 -> 38.sp; else -> 30.sp
    }
    Box(
        Modifier.fillMaxWidth()
            .shadow(14.dp, RoundedCornerShape(16.dp), ambientColor = AppColors.Glow, spotColor = AppColors.Glow)
            .clip(RoundedCornerShape(16.dp)).background(AppColors.Glow.copy(alpha = .35f)).padding(4.dp)
            .clip(RoundedCornerShape(13.dp)).background(Color(0xFF0B1220)).padding(4.dp)
            .clip(RoundedCornerShape(10.dp)).background(bg)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Bolt(); Bolt()
        }
        Text(text, fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = size, color = fg,
            maxLines = 1, overflow = TextOverflow.Visible, letterSpacing = (-0.5).sp)
    }
}

@Composable
private fun Bolt() {
    Box(Modifier.size(10.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color.White, Color(0xFF9AA3B2)))))
}

/** "123가4567" -> "123가 4567" */
fun formatPlate(n: String): String =
    if (n.length > 4 && n.takeLast(4).all { it.isDigit() } && !n[n.length - 5].isDigit()) n.dropLast(4) + " " + n.takeLast(4) else n

@Composable
private fun InfoCard(v: VehicleUi) {
    Column(
        Modifier.fillMaxWidth().shadow(6.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(Color.White)
    ) {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(AppColors.Navy, Color(0xFF1652C8))))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.DirectionsCar, null, tint = Color.White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text("차량 정보", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp, color = Color.White)
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
            InfoRow(Icons.Rounded.DirectionsCar, "차량종류", v.categoryLabel())
            Divider()
            InfoRow(Icons.Rounded.Bolt, "전기차", if (v.electric) "전기차 (파란 번호판)" else "아니요",
                valueColor = if (v.electric) AppColors.Blue else AppColors.TextMain)
            Divider()
            InfoRow(Icons.Rounded.Palette, "차량색상", v.colorName ?: "확인 불가", swatch = swatchOf(v.colorName))
            Divider()
            InfoRow(Icons.Rounded.Badge, "용도", v.usage ?: "확인 불가")
            if (v.confidence != null) {
                Divider()
                InfoRow(Icons.Rounded.Verified, "인식 신뢰도", "${(v.confidence * 100).toInt()}%")
            }
        }
    }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Line))

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String, swatch: Color? = null, valueColor: Color = AppColors.TextMain) {
    Row(Modifier.fillMaxWidth().padding(vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AppColors.Navy, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = AppColors.TextSub,
            modifier = Modifier.width(96.dp))
        if (swatch != null) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(swatch).border(1.dp, Color(0xFFB8C2D3), CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(value, fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = valueColor)
    }
}

private fun swatchOf(name: String?): Color? = when (name) {
    "흰색" -> Color.White; "검정" -> Color(0xFF111111); "회색" -> Color(0xFF7A7F87); "은색" -> Color(0xFFC4C9D0)
    "빨강" -> Color(0xFFD7263D); "주황" -> Color(0xFFF77F00); "노랑" -> Color(0xFFFFD000); "초록" -> Color(0xFF2E8B57)
    "파랑" -> Color(0xFF1E63E9); "남색" -> Color(0xFF1B2A6B); "갈색" -> Color(0xFF7B4B2A); "베이지" -> Color(0xFFE3D3B0)
    else -> null
}

@Composable
private fun BottomButton(icon: ImageVector, label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(84.dp)
            .shadow(if (primary) 8.dp else 2.dp, RoundedCornerShape(18.dp), spotColor = AppColors.Blue)
            .clip(RoundedCornerShape(18.dp))
            .background(if (primary) Brush.verticalGradient(listOf(Color(0xFF1E6BF0), Color(0xFF0B47C2))) else Brush.verticalGradient(listOf(Color.White, Color(0xFFF2F6FD))))
            .border(1.dp, if (primary) Color.Transparent else AppColors.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = if (primary) Color.White else AppColors.Navy, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 16.sp,
            color = if (primary) Color.White else AppColors.TextMain)
    }
}

/** 원본 사진 위에 차(하늘색)와 번호판(파란 형광) 상자를 그린 사본. */
fun annotate(src: Bitmap, vehicles: List<VehicleUi>): Bitmap {
    val out = src.copy(Bitmap.Config.ARGB_8888, true)
    val c = Canvas(out)
    val s = max(out.width, out.height) / 400f
    val car = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2.5f * s; color = 0xCC7FD3FF.toInt() }
    val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 7f * s; color = 0x6639B6FF }
    val plate = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f * s; color = 0xFF39B6FF.toInt() }
    for (v in vehicles) {
        val b = v.vehicleBox
        c.drawRoundRect(RectF(b.x0.toFloat(), b.y0.toFloat(), b.x1.toFloat(), b.y1.toFloat()), 8 * s, 8 * s, car)
        v.plateBox?.let { p ->
            val r = RectF(p.x0.toFloat(), p.y0.toFloat(), p.x1.toFloat(), p.y1.toFloat())
            c.drawRoundRect(r, 4 * s, 4 * s, glow); c.drawRoundRect(r, 4 * s, 4 * s, plate)
        }
    }
    return out
}

/** 결과 상단 사진: 선택한 차를 중심으로 2:1 비율로 잘라냄. */
fun heroCrop(img: Bitmap, v: VehicleUi?): Bitmap {
    if (v == null) return img
    val b = v.vehicleBox
    val cx = (b.x0 + b.x1) / 2f; val cy = (b.y0 + b.y1) / 2f
    var w = (b.x1 - b.x0) * 1.25f; var h = (b.y1 - b.y0) * 1.25f
    if (w / h < 2f) w = h * 2f else h = w / 2f
    w = min(w, img.width.toFloat()); h = min(h, img.height.toFloat())
    val x0 = (cx - w / 2).coerceIn(0f, img.width - w).toInt()
    val y0 = (cy - h / 2).coerceIn(0f, img.height - h).toInt()
    return Bitmap.createBitmap(img, x0, y0, max(1, w.toInt()), max(1, h.toInt()))
}
