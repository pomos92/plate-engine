package com.plateengine.sample.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.plateengine.sample.watcher.WatchLog
import com.plateengine.sample.watcher.WatchSettings
import com.plateengine.sample.watcher.WatchState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 폴더 자동정리 화면 (PC 버전과 같은 기능).
 * 감시 폴더에 새 사진이 생기면 번호를 읽어 '년월일시분초_번호' 로 이름을 바꿔 백업 폴더로 옮긴다.
 */
@Composable
fun WatchScreen(
    settings: WatchSettings,
    watchLabel: String,
    backupLabel: String,
    running: Boolean,
    status: String,
    counts: WatchState.Counts,
    logs: List<WatchLog>,
    error: String?,
    onBack: () -> Unit,
    onPickWatch: () -> Unit,
    onPickBackup: () -> Unit,
    onChange: (WatchSettings) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(AppColors.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "뒤로", tint = AppColors.Navy)
            }
            Spacer(Modifier.width(4.dp))
            Text("폴더 자동정리", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = AppColors.Navy)
        }
        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Text("감시 폴더에 새 사진이 생기면 번호를 읽고, 이름을 '년월일시분초_번호'로 바꿔 백업 폴더의 같은 하위 폴더로 옮겨요.",
                fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = AppColors.TextSub)
            Spacer(Modifier.height(14.dp))

            FolderCard(Icons.Rounded.FolderOpen, "감시 폴더", watchLabel, settings.watchUri != null, !running, onPickWatch,
                hint = "예: DCIM/Camera 를 고르면 카메라로 찍는 즉시 정리돼요")
            Spacer(Modifier.height(10.dp))
            FolderCard(Icons.Rounded.Folder, "백업 폴더", backupLabel, settings.backupUri != null, !running, onPickBackup,
                hint = "감시 폴더 밖에 있는 폴더를 골라 주세요")

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                Text(error, fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Warn)
            }

            // 감시 중에는 설정을 접고 처리 내역을 위에 보여준다
            if (!running) {
            Spacer(Modifier.height(16.dp))
            Card {
                val sample = "20261123083700" + (if (settings.separator) "_" else "") + "67너7570.jpg"
                Text("새 이름 예시", fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = AppColors.TextSub)
                Text(sample, fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = AppColors.Blue)
                Spacer(Modifier.height(6.dp))
                Option("사진 촬영시각 사용", "없으면 파일이 만들어진 시각", settings.useExifTime, !running) { onChange(settings.copy(useExifTime = it)) }
                Option("시간과 번호 사이에 _ 넣기", null, settings.separator, !running) { onChange(settings.copy(separator = it)) }
                Option("번호를 못 읽은 사진도 옮기기", "원래 이름 그대로 백업 폴더로", settings.moveUnrecognized, !running) { onChange(settings.copy(moveUnrecognized = it)) }
                Option("시작할 때 이미 있던 사진도 처리", null, settings.processExisting, !running) { onChange(settings.copy(processExisting = it)) }
                Option("휴대폰을 켜면 감시 이어서 하기", null, settings.startOnBoot, true) { onChange(settings.copy(startOnBoot = it)) }
            }
            }

            Spacer(Modifier.height(16.dp))
            if (running || counts != WatchState.Counts()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("정리됨", counts.read, AppColors.Ok, Modifier.weight(1f))
                    Stat("번호 못 읽음", counts.unread, AppColors.TextSub, Modifier.weight(1f))
                    Stat("오류", counts.failed, AppColors.Warn, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
            if (logs.isNotEmpty()) {
                Text("최근 처리", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AppColors.Navy)
                Spacer(Modifier.height(6.dp))
                Card { logs.take(20).forEachIndexed { i, l -> if (i > 0) Divider(); LogRow(l) } }
                Spacer(Modifier.height(12.dp))
            }
        }

        // 아래 고정: 시작/중지 버튼과 상태
        Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 12.dp)) {
            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(AppColors.Ok))
                    Spacer(Modifier.width(6.dp))
                    Text(status.ifEmpty { "감시 중" }, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = AppColors.TextSub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
            }
            val enabled = running || settings.ready
            Row(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(29.dp))
                    .background(
                        when {
                            running -> Brush.horizontalGradient(listOf(Color(0xFFEF5A5F), AppColors.Warn))
                            enabled -> Brush.horizontalGradient(listOf(AppColors.BlueLight, AppColors.Blue))
                            else -> Brush.horizontalGradient(listOf(Color(0xFFC9D2E3), Color(0xFFC9D2E3)))
                        }
                    )
                    .clickable(enabled = enabled) { if (running) onStop() else onStart() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (running) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (running) "감시 중지" else "감시 시작", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp, color = Color.White)
            }
            if (running) {
                Spacer(Modifier.height(6.dp))
                Text("앱을 닫아도 알림줄에 '폴더 감시 중'이 떠 있는 동안 계속 정리해요", fontFamily = Pretendard,
                    fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = AppColors.TextSub)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(1.dp, AppColors.Line, RoundedCornerShape(20.dp)).padding(16.dp)
    ) { content() }
}

@Composable
private fun Divider() = Box(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(AppColors.Line))

@Composable
private fun FolderCard(icon: ImageVector, title: String, label: String, chosen: Boolean, enabled: Boolean, onClick: () -> Unit, hint: String) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White)
            .border(1.5.dp, if (chosen) AppColors.Blue.copy(alpha = .35f) else AppColors.Line, RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE8F0FF)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = AppColors.Blue, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = AppColors.TextSub)
            Text(if (chosen) label else "눌러서 폴더 고르기", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                color = if (chosen) AppColors.TextMain else AppColors.Blue, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!chosen) Text(hint, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = AppColors.TextSub)
        }
        if (chosen && enabled) Text("변경", fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AppColors.Blue)
    }
}

@Composable
private fun Option(title: String, sub: String?, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Pretendard, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                color = if (enabled) AppColors.TextMain else AppColors.TextSub)
            if (sub != null) Text(sub, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = AppColors.TextSub)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Blue))
    }
}

@Composable
private fun Stat(label: String, value: Int, color: Color, modifier: Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, AppColors.Line, RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$value", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color)
        Text(label, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = AppColors.TextSub)
    }
}

@Composable
private fun LogRow(l: WatchLog) {
    val ok = l.error == null && l.plate != null
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            when { l.error != null -> Icons.Rounded.Error; ok -> Icons.Rounded.CheckCircle; else -> Icons.Rounded.HelpOutline },
            null, tint = when { l.error != null -> AppColors.Warn; ok -> AppColors.Ok; else -> AppColors.TextSub },
            modifier = Modifier.size(20.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(l.plate ?: if (l.error != null) "처리 못 함" else "번호 못 읽음", fontFamily = Pretendard, fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp, color = AppColors.TextMain)
            Text(l.source, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = AppColors.TextSub,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            val second = l.error ?: l.dest?.let { "→ $it" } ?: "감시 폴더에 그대로 둠"
            Text(second, fontFamily = Pretendard, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = if (l.error != null) AppColors.Warn else AppColors.Blue, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Text(SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date(l.time)), fontFamily = Pretendard, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, color = AppColors.TextSub)
    }
}
