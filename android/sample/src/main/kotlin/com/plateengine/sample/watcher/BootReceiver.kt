package com.plateengine.sample.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 휴대폰을 켜면, 설정에서 켜 두었고 꺼지기 전에 감시 중이었으면 감시를 다시 시작한다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val s = WatchSettings.load(context)
        if (s.ready && s.startOnBoot && WatchSettings.wasRunning(context)) runCatching { WatchService.start(context) }
    }
}
