package com.plateengine.sample.watcher

import android.content.Context
import android.provider.DocumentsContract
import androidx.exifinterface.media.ExifInterface
import com.plateengine.PlateEngine
import com.plateengine.sample.decodeForEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 감시 폴더(하위 폴더 포함)를 주기적으로 훑어서 새 사진을 찾고,
 * 번호를 읽어 '년월일시분초_번호' 로 이름을 바꿔 백업 폴더의 같은 하위 폴더 위치로 옮긴다.
 * 휴대폰 저장소는 변경 알림이 폴더마다 오지 않아서 PC 버전처럼 몇 초마다 확인하는 방식.
 */
class FolderWatcher(private val context: Context, private val settings: WatchSettings, private val engine: PlateEngine) {
    private val resolver = context.contentResolver
    private val watch = Saf(resolver, settings.watchUri!!)
    private val backup = Saf(resolver, settings.backupUri!!)

    private class Pending(var size: Long, var modified: Long, var stable: Int, val firstSeen: Long)

    /** 처리하지 않을 사진 (시작 전부터 있던 사진, 못 읽어서 그대로 둔 사진 등) */
    private val ignored = HashSet<String>()
    private val pending = HashMap<String, Pending>()
    private val dirCache = HashMap<String, String>()

    private data class Found(val entry: Saf.Entry, val path: List<String>)

    fun start() {
        if (!settings.processExisting) scan().forEach { ignored += it.entry.id }
    }

    /** 한 번 훑기. 다 써진(크기·시각이 두 번 연속 같은) 사진만 처리한다. */
    fun poll() {
        val now = System.currentTimeMillis()
        val found = scan()
        val alive = HashSet<String>()
        for (f in found) {
            val e = f.entry
            alive += e.id
            if (e.id in ignored) continue
            val p = pending[e.id]
            if (p == null) {
                pending[e.id] = Pending(e.size, e.modified, 0, now); continue
            }
            if (p.size == e.size && p.modified == e.modified && e.size > 0) p.stable++ else {
                p.size = e.size; p.modified = e.modified; p.stable = 0
            }
            if (p.stable >= STABLE_POLLS) {
                pending.remove(e.id)
                process(f)
            } else if (now - p.firstSeen > GIVE_UP_MS) {
                pending.remove(e.id); ignored += e.id
                WatchState.add(WatchLog(now, f.display(), null, null, "파일이 계속 바뀌고 있어 건너뜀"))
            }
        }
        pending.keys.retainAll(alive)
        ignored.retainAll(alive)
    }

    private fun Found.display() = (path + entry.name).joinToString("/")

    private fun scan(): List<Found> {
        val out = ArrayList<Found>()
        fun walk(dirId: String, path: List<String>, depth: Int) {
            if (depth > 12) return
            for (e in watch.children(dirId)) {
                if (e.isDir) {
                    if (!e.name.startsWith(".")) walk(e.id, path + e.name, depth + 1)
                } else if (NameRules.isImage(e.name, e.mime)) out += Found(e, path)
            }
        }
        walk(watch.rootId, emptyList(), 0)
        return out
    }

    private fun process(f: Found) {
        val e = f.entry
        val src = watch.uri(e.id)
        val now = System.currentTimeMillis()
        try {
            val plate = NameRules.pickPlate(engine.analyze(decodeForEngine(resolver, src)))
            val (time, timeSource) = photoTime(f)
            if (plate == null && !settings.moveUnrecognized) {
                ignored += e.id
                log(f, null, null, time, timeSource, "미인식(그대로 둠)")
                WatchState.add(WatchLog(now, f.display(), null, null))
                return
            }
            val wanted = if (plate != null) NameRules.newName(time, plate, settings.separator, e.name) else e.name
            val destDir = backup.ensureDirs(f.path, dirCache)
            val name = NameRules.unique(wanted, backup.children(destDir).map { it.name }.toSet())
            val mime = e.mime.ifEmpty { "image/jpeg" }
            val dest = backup.createFile(destDir, mime, name)
            try {
                resolver.openInputStream(src)!!.use { input -> resolver.openOutputStream(dest)!!.use { input.copyTo(it) } }
            } catch (ex: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, dest) }
                throw ex
            }
            // 실제로 만들어진 이름 (저장소가 이름을 바꿨을 수 있음)
            val finalName = runCatching { queryName(dest) }.getOrNull() ?: name
            if (!watch.delete(e.id)) ignored += e.id
            val destPath = (f.path + finalName).joinToString("/")
            log(f, plate, destPath, time, timeSource, if (plate != null) "인식" else "미인식")
            WatchState.add(WatchLog(now, f.display(), destPath, plate))
        } catch (ex: Exception) {
            ignored += e.id
            log(f, null, null, null, "", "오류: ${ex.message}")
            WatchState.add(WatchLog(now, f.display(), null, null, ex.message ?: "처리 실패"))
        }
    }

    private fun queryName(uri: android.net.Uri): String? =
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }

    /** 사진 촬영시각(EXIF). 없거나 끄면 파일 시각. */
    private fun photoTime(f: Found): Pair<Long, String> {
        if (settings.useExifTime) {
            val t = runCatching {
                resolver.openInputStream(watch.uri(f.entry.id))!!.use {
                    val ex = ExifInterface(it)
                    NameRules.parseExif(ex.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                        ?: NameRules.parseExif(ex.getAttribute(ExifInterface.TAG_DATETIME))
                }
            }.getOrNull()
            if (t != null) return t to "촬영시각"
        }
        val m = f.entry.modified
        return (if (m > 0) m else System.currentTimeMillis()) to "파일시각"
    }

    /** 백업 폴더의 처리기록.csv 에 한 줄 추가 (엑셀로 열림). */
    private fun log(f: Found, plate: String?, dest: String?, time: Long?, timeSource: String, result: String) {
        runCatching {
            val existing = backup.children(backup.rootId).firstOrNull { !it.isDir && it.name == NameRules.LOG_NAME }
            val uri = existing?.let { backup.uri(it.id) } ?: backup.createFile(backup.rootId, "text/csv", NameRules.LOG_NAME)
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA)
            val sb = StringBuilder()
            if (existing == null) sb.append('﻿').append(NameRules.csvRow(listOf("처리시각", "결과", "번호", "시각", "시각출처", "원본", "이동위치")))
            sb.append(NameRules.csvRow(listOf(fmt.format(Date()), result, plate ?: "", time?.let { fmt.format(Date(it)) } ?: "",
                timeSource, f.display(), dest ?: "")))
            val bytes = sb.toString().toByteArray(Charsets.UTF_8)
            try {
                resolver.openOutputStream(uri, "wa")!!.use { it.write(bytes) }
            } catch (_: Exception) {
                // 이어쓰기를 지원하지 않는 저장소: 읽어서 통째로 다시 쓴다
                val old = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
                resolver.openOutputStream(uri, "wt")!!.use { it.write(old + bytes) }
            }
        }
    }

    companion object {
        const val STABLE_POLLS = 1   // 한 번 더 확인했을 때 크기/시각이 같으면 다 써진 것으로 본다
        const val GIVE_UP_MS = 120_000L
    }
}
