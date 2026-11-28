package com.plateengine.sample.watcher

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.plateengine.PlateEngine
import com.plateengine.sample.MainActivity
import com.plateengine.sample.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 화면을 닫아도 계속 폴더를 감시하는 서비스. 알림줄에 "폴더 감시 중" 이 떠 있는 동안 동작한다.
 */
class WatchService : Service() {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private var job: Job? = null
    private var engine: PlateEngine? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            WatchSettings.setRunning(this, false)
            stopSelf(); return START_NOT_STICKY
        }
        startInForeground("감시 준비 중…")
        if (job?.isActive == true) return START_STICKY
        val settings = WatchSettings.load(this)
        if (!settings.ready) { stopSelf(); return START_NOT_STICKY }
        WatchSettings.setRunning(this, true)
        WatchState.setRunning(true)
        WatchState.reset()
        job = scope.launch {
            try {
                WatchState.setStatus("인식 엔진 준비 중…")
                val eng = engine ?: PlateEngine(applicationContext).also { engine = it }
                val watcher = FolderWatcher(applicationContext, settings, eng)
                watcher.start()
                WatchState.setStatus("새 사진을 기다리는 중")
                updateNotification("새 사진을 기다리는 중")
                var lastCount = 0
                while (isActive) {
                    try {
                        watcher.poll()
                        WatchState.setStatus("새 사진을 기다리는 중")
                    } catch (e: SecurityException) {
                        WatchState.setStatus("폴더 접근 권한이 없어요. 폴더를 다시 골라 주세요.")
                    } catch (e: Exception) {
                        WatchState.setStatus("확인 중 오류: ${e.message}")
                    }
                    val c = WatchState.counts.value
                    val total = c.read + c.unread + c.failed
                    if (total != lastCount) {
                        lastCount = total
                        updateNotification("정리 ${c.read}장 · 미인식 ${c.unread}장" + if (c.failed > 0) " · 오류 ${c.failed}장" else "")
                    }
                    delay(POLL_MS)
                }
            } catch (e: Exception) {
                WatchState.setStatus("감시를 시작하지 못했어요: ${e.message}")
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        engine?.close(); engine = null
        WatchState.setRunning(false)
        super.onDestroy()
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "폴더 자동정리", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_WATCH, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, WatchService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_watch)
            .setContentTitle("번호판 폴더 감시 중")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, "감시 중지", stop)
            .build()
    }

    private fun startInForeground(text: String) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, notification(text), type)
    }

    private fun updateNotification(text: String) {
        runCatching { getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification(text)) }
    }

    companion object {
        private const val CHANNEL = "folder_watch"
        private const val NOTIF_ID = 7
        private const val ACTION_STOP = "stop"
        const val POLL_MS = 3000L

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, WatchService::class.java))

        fun stop(context: Context) {
            WatchSettings.setRunning(context, false)
            context.stopService(Intent(context, WatchService::class.java))
        }
    }
}
