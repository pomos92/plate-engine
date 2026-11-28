package com.plateengine.sample.watcher

import android.content.Context
import android.net.Uri

/** 폴더 자동정리 설정 (휴대폰 안에 저장, 다음에 켤 때 그대로). */
data class WatchSettings(
    val watchUri: Uri? = null,
    val backupUri: Uri? = null,
    /** 사진 안의 촬영시각(EXIF)을 이름에 사용. 없거나 끄면 파일 시각 */
    val useExifTime: Boolean = true,
    /** 시간과 번호 사이에 _ 넣기 */
    val separator: Boolean = true,
    /** 번호를 못 읽은 사진도 원래 이름으로 백업 폴더로 옮기기 */
    val moveUnrecognized: Boolean = true,
    /** 시작할 때 이미 있던 사진도 처리 */
    val processExisting: Boolean = false,
    /** 휴대폰을 켜면 자동으로 감시 시작 */
    val startOnBoot: Boolean = false,
) {
    val ready get() = watchUri != null && backupUri != null

    fun save(context: Context) {
        prefs(context).edit()
            .putString("watch", watchUri?.toString())
            .putString("backup", backupUri?.toString())
            .putBoolean("exif", useExifTime)
            .putBoolean("sep", separator)
            .putBoolean("moveUnrecognized", moveUnrecognized)
            .putBoolean("existing", processExisting)
            .putBoolean("boot", startOnBoot)
            .apply()
    }

    companion object {
        private fun prefs(c: Context) = c.getSharedPreferences("folder_watch", Context.MODE_PRIVATE)

        fun load(context: Context): WatchSettings {
            val p = prefs(context)
            return WatchSettings(
                watchUri = p.getString("watch", null)?.let(Uri::parse),
                backupUri = p.getString("backup", null)?.let(Uri::parse),
                useExifTime = p.getBoolean("exif", true),
                separator = p.getBoolean("sep", true),
                moveUnrecognized = p.getBoolean("moveUnrecognized", true),
                processExisting = p.getBoolean("existing", false),
                startOnBoot = p.getBoolean("boot", false),
            )
        }

        /** 감시 중인지 (휴대폰을 다시 켰을 때 이어서 감시할지 판단용) */
        fun setRunning(context: Context, running: Boolean) = prefs(context).edit().putBoolean("running", running).apply()
        fun wasRunning(context: Context) = prefs(context).getBoolean("running", false)
    }
}
