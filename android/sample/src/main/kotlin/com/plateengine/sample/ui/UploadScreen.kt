package com.plateengine.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 사진 등록 화면: 차 그림 아래 [카메라로 촬영 | 갤러리에서 선택] 두 버튼.
 * @param ready 엔진(모델) 로딩이 끝났는지
 * @param analyzing 분석 중이면 화면 위에 진행 표시
 */
@Composable
fun UploadScreen(
    ready: Boolean,
    analyzing: Boolean,
    message: String?,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE8F0FF), AppColors.Bg, Color.White)))
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Text("차량번호 인식", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = AppColors.Navy)
            Spacer(Modifier.height(6.dp))
            Text("차 사진 한 장으로 번호·차종·색상을 확인해요", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = AppColors.TextSub, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))

            // 메인 카드: 파란 테두리 둥근 사각형 안에 차 그림 + 버튼 바
            Box(
                Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, RoundedCornerShape(36.dp), ambientColor = AppColors.Blue, spotColor = AppColors.Blue)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Brush.linearGradient(listOf(AppColors.BlueLight, AppColors.Blue, Color(0xFF0D47C9))))
                    .padding(7.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFF8FBFF), Color(0xFFE6EEFB))))
            ) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CarIllustration(Modifier.fillMaxWidth().aspectRatio(1.25f))
                    Spacer(Modifier.height(12.dp))
                    ActionBar(enabled = ready && !analyzing, onCamera = onCamera, onGallery = onGallery)
                }
            }

            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.WifiOff, null, tint = AppColors.TextSub, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("인터넷 없이 휴대폰 안에서 분석해요", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = AppColors.TextSub)
            }
            if (!ready) {
                Spacer(Modifier.height(10.dp))
                Text("인식 엔진 준비 중…", fontFamily = Pretendard, fontSize = 13.sp, color = AppColors.Blue)
            }
            if (message != null) {
                Spacer(Modifier.height(10.dp))
                Text(message, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                    color = AppColors.Warn, textAlign = TextAlign.Center)
            }
        }

        if (analyzing) {
            Box(Modifier.fillMaxSize().background(Color(0x99071C4A)), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(horizontal = 36.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = AppColors.Blue, strokeWidth = 4.dp, modifier = Modifier.size(44.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("차량 정보 분석 중…", fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = AppColors.Navy)
                }
            }
        }
    }
}

@Composable
private fun ActionBar(enabled: Boolean, onCamera: () -> Unit, onGallery: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(116.dp)
            .clip(RoundedCornerShape(58.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF1652C8), AppColors.Navy))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(Icons.Rounded.CameraAlt, "카메라로 촬영", Color(0xFF2F7BFF), enabled, onCamera, Modifier.weight(1f))
        Box(Modifier.width(1.5.dp).fillMaxHeight(0.55f).background(Color(0x66FFFFFF)))
        ActionButton(Icons.Rounded.PhotoLibrary, "갤러리에서 선택", Color(0xFF34B36B), enabled, onGallery, Modifier.weight(1f))
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, accent: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Column(
        modifier.fillMaxHeight().clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                .border(2.dp, accent.copy(alpha = .35f), RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (enabled) accent else Color.LightGray, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 15.sp,
            color = if (enabled) Color.White else Color(0x99FFFFFF))
    }
}
