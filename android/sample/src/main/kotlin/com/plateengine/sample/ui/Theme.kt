package com.plateengine.sample.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.plateengine.sample.R

object AppColors {
    val Navy = Color(0xFF0B2A6B)
    val NavyDeep = Color(0xFF071C4A)
    val Blue = Color(0xFF1E63E9)
    val BlueLight = Color(0xFF4FA3FF)
    val Glow = Color(0xFF39B6FF)
    val Bg = Color(0xFFF1F5FC)
    val Card = Color.White
    val Line = Color(0xFFE3E9F4)
    val TextMain = Color(0xFF111827)
    val TextSub = Color(0xFF5B6475)
    val Ok = Color(0xFF22B455)
    val Warn = Color(0xFFE5484D)
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

@Composable
fun PlateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = AppColors.Blue, onPrimary = Color.White,
            background = AppColors.Bg, surface = AppColors.Card,
        ),
        content = content,
    )
}
