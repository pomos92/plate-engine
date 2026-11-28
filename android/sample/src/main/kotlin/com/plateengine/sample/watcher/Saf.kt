package com.plateengine.sample.watcher

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * 사용자가 고른 폴더(SAF 트리) 안의 파일을 다루는 도우미.
 * 안드로이드는 앱이 아무 폴더나 직접 열 수 없어서, 사용자가 "폴더 선택"으로 허락한 폴더만 이 방식으로 다룬다.
 */
class Saf(private val resolver: ContentResolver, val tree: Uri) {
    val rootId: String = DocumentsContract.getTreeDocumentId(tree)

    data class Entry(val id: String, val name: String, val mime: String, val size: Long, val modified: Long) {
        val isDir get() = mime == Document.MIME_TYPE_DIR
    }

    fun uri(id: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)

    fun children(dirId: String): List<Entry> {
        val out = ArrayList<Entry>()
        val cols = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, dirId), cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out += Entry(
                    id = c.getString(0), name = c.getString(1) ?: "", mime = c.getString(2) ?: "",
                    size = if (c.isNull(3)) -1 else c.getLong(3),
                    modified = if (c.isNull(4)) 0 else c.getLong(4),
                )
            }
        }
        return out
    }

    /** 하위 폴더 이름(경로 순서대로)을 따라가며 없으면 만든다. 폴더 id 를 돌려준다. */
    fun ensureDirs(path: List<String>, cache: MutableMap<String, String>): String {
        var cur = rootId
        val key = StringBuilder()
        for (name in path) {
            key.append('/').append(name)
            cur = cache.getOrPut(key.toString()) {
                children(cur).firstOrNull { it.isDir && it.name == name }?.id
                    ?: DocumentsContract.createDocument(resolver, uri(cur), Document.MIME_TYPE_DIR, name)
                        ?.let(DocumentsContract::getDocumentId)
                    ?: error("폴더를 만들 수 없음: $name")
            }
        }
        return cur
    }

    fun createFile(dirId: String, mime: String, name: String): Uri =
        DocumentsContract.createDocument(resolver, uri(dirId), mime, name) ?: error("파일을 만들 수 없음: $name")

    fun delete(id: String): Boolean = DocumentsContract.deleteDocument(resolver, uri(id))

    companion object {
        /** "primary:DCIM/Camera" -> "내장 메모리/DCIM/Camera" 처럼 사람이 읽는 위치 */
        fun label(tree: Uri?): String {
            if (tree == null) return "선택 안 됨"
            val id = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return tree.toString()
            val vol = id.substringBefore(':')
            val path = id.substringAfter(':', "")
            val volName = if (vol == "primary") "내장 메모리" else "SD카드"
            return if (path.isEmpty()) volName else "$volName/$path"
        }

        /** b 가 a 와 같거나 a 안에 있는 폴더인지 (같은 저장소일 때만 판단 가능) */
        fun isSameOrInside(a: Uri, b: Uri): Boolean {
            if (a.authority != b.authority) return false
            val ia = DocumentsContract.getTreeDocumentId(a)
            val ib = DocumentsContract.getTreeDocumentId(b)
            if (ia == ib) return true
            val base = if (ia.endsWith(":") || ia.endsWith("/")) ia else "$ia/"
            return ib.startsWith(base)
        }
    }
}
