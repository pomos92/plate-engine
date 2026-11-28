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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material.icons.rounded.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    /** 번호를 고쳐서 모아 둔 학습용 사진 수 */
    trainingCount: Int = 0,
    onExportTraining: () -> Unit = {},
    onClearTraining: () -> Unit = {},
    /** 폴더 자동정리 화면 열기 (PC 버전과 같은 기능) */
    onFolderWatch: () -> Unit = {},
    /** 폴더 자동정리가 지금 돌고 있는지 */
    watching: Boolean = false,
) {
    var trainingMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE8F0FF), AppColors.Bg, Color.White)))
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
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
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White)
                    .border(1.dp, AppColors.Line, RoundedCornerShape(22.dp))
                    .clickable(onClick = onFolderWatch).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFE8F0FF)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DriveFileMove, null, tint = AppColors.Blue, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("폴더 자동정리", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AppColors.Navy)
                    Text(if (watching) "감시 중 · 새 사진을 이름 바꿔 백업 폴더로" else "새 사진을 번호로 이름 바꿔 백업 폴더로 옮겨요",
                        fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = if (watching) AppColors.Ok else AppColors.TextSub)
                }
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = AppColors.TextSub)
            }
            if (trainingCount > 0) {
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White)
                        .border(1.dp, AppColors.Line, RoundedCornerShape(20.dp))
                        .clickable { trainingMenu = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.School, null, tint = AppColors.Blue, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("학습용 사진 ${trainingCount}장 모음 · 내보내기", fontFamily = Pretendard, fontWeight = FontWeight.Bold,
                        fontSize = 14.sp, color = AppColors.Blue)
                }
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

        if (trainingMenu) {
            AlertDialog(
                onDismissRequest = { trainingMenu = false },
                title = { Text("학습용 사진 ${trainingCount}장", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold) },
                text = {
                    Text("번호를 고친 사진이 휴대폰 안에만 모여 있어요. 압축 파일로 내보내서 전달해 주시면 이 사진들로 인식 엔진을 다시 학습시킬 수 있어요.",
                        fontFamily = Pretendard, fontSize = 15.sp, color = AppColors.TextSub)
                },
                confirmButton = {
                    TextButton(onClick = { trainingMenu = false; onExportTraining() }) {
                        Text("내보내기", fontFamily = Pretendard, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { trainingMenu = false; confirmClear = true }) {
                        Text("모두 지우기", fontFamily = Pretendard, color = AppColors.Warn)
                    }
                },
                containerColor = Color.White,
            )
        }
        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("모은 사진을 지울까요?", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold) },
                text = { Text("지운 사진은 되돌릴 수 없어요. 내보내기를 먼저 했는지 확인해 주세요.", fontFamily = Pretendard, fontSize = 15.sp) },
                confirmButton = {
                    TextButton(onClick = { confirmClear = false; onClearTraining() }) {
                        Text("지우기", fontFamily = Pretendard, fontWeight = FontWeight.Bold, color = AppColors.Warn)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소", fontFamily = Pretendard) } },
                containerColor = Color.White,
            )
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
