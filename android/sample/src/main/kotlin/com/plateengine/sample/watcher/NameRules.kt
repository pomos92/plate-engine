package com.plateengine.sample.watcher

import com.plateengine.core.AnalyzeResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 파일 이름 규칙 (PC 버전과 같음). 예: 20261123083700_67너7570.jpg */
object NameRules {
    val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "bmp", "webp", "tif", "tiff", "heic", "heif")
    const val LOG_NAME = "처리기록.csv"

    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    fun isImage(name: String, mime: String) =
        !name.startsWith(".") && (ext(name) in IMAGE_EXTS || (mime.startsWith("image/") && ext(name).isNotEmpty()))

    /** 가장 크게 찍힌 차(결과는 면적순)부터 형식이 맞는 번호판. */
    fun pickPlate(r: AnalyzeResult): String? =
        r.vehicles.firstNotNullOfOrNull { v -> v.plate?.takeIf { it.validFormat }?.number?.replace(" ", "") }

    fun stamp(time: Long): String = SimpleDateFormat("yyyyMMddHHmmss", Locale.KOREA).format(Date(time))

    fun newName(time: Long, plate: String, separator: Boolean, originalName: String): String {
        val e = ext(originalName)
        return stamp(time) + (if (separator) "_" else "") + plate + (if (e.isEmpty()) "" else ".$e")
    }

    /** 같은 이름이 있으면 _2, _3 ... 을 붙인다 (덮어쓰지 않음). */
    fun unique(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 2
        while ("${stem}_$n$ext" in taken) n++
        return "${stem}_$n$ext"
    }

    /** EXIF "2026:11:23 08:37:00" -> 시각(ms). 읽을 수 없으면 null */
    fun parseExif(v: String?): Long? {
        if (v.isNullOrBlank()) return null
        return runCatching { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.KOREA).parse(v.trim().take(19))?.time }.getOrNull()
    }

    fun csvRow(cols: List<String>): String =
        cols.joinToString(",") { c -> if (c.any { it == ',' || it == '"' || it == '\n' }) "\"" + c.replace("\"", "\"\"") + "\"" else c } + "\r\n"
}
