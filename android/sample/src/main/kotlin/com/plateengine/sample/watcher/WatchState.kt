package com.plateengine.sample.watcher

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 처리 내역 한 줄 (화면 표시용). */
data class WatchLog(
    val time: Long,
    /** 감시 폴더 안 위치. 예: 1층/A구역/IMG_001.jpg */
    val source: String,
    /** 백업 폴더 안 새 위치. 옮기지 않았으면 null */
    val dest: String?,
    val plate: String?,
    val error: String? = null,
)

/** 감시 서비스와 화면이 같이 보는 상태. */
object WatchState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()
    private val _logs = MutableStateFlow<List<WatchLog>>(emptyList())
    val logs: StateFlow<List<WatchLog>> = _logs.asStateFlow()
    private val _counts = MutableStateFlow(Counts())
    val counts: StateFlow<Counts> = _counts.asStateFlow()

    data class Counts(val read: Int = 0, val unread: Int = 0, val failed: Int = 0)

    fun setRunning(v: Boolean) { _running.value = v }
    fun setStatus(s: String) { _status.value = s }
    fun reset() { _counts.value = Counts() }

    fun add(log: WatchLog) {
        _logs.update { (listOf(log) + it).take(50) }
        _counts.update {
            when {
                log.error != null -> it.copy(failed = it.failed + 1)
                log.plate != null -> it.copy(read = it.read + 1)
                else -> it.copy(unread = it.unread + 1)
            }
        }
    }
}
