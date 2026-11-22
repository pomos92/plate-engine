package com.plateengine.sample

import android.content.Context
import android.graphics.Bitmap
import com.plateengine.core.Box
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * 다시 학습할 때 쓸 사진을 휴대폰 안(앱 전용 폴더)에 모아 두는 곳.
 * 사용자가 고친(또는 확인한) 번호를 파일 이름으로 저장한다. 예: plates/123가4567_1732251234.jpg
 *  - plates/   : 번호판 부분만 잘라낸 사진 -> 바로 `train_ocr.py --real plates` 에 넣을 수 있음
 *  - vehicles/ : 차 전체 사진 (번호판을 못 찾은 경우 포함, 나중에 번호판 위치 찾기 개선용)
 * 인터넷으로 보내지 않는다. 사용자가 '내보내기'를 눌렀을 때만 압축 파일로 공유한다.
 */
class TrainingStore(context: Context) {
    val root = File(context.filesDir, "training")
    private val plates = File(root, "plates")
    private val vehicles = File(root, "vehicles")
    private val exportDir = File(context.cacheDir, "export")

    /** 모아 둔 번호 수 (차 사진 기준). */
    fun count(): Int = vehicles.listFiles()?.count { it.isFile } ?: 0

    fun save(image: Bitmap, number: String, vehicleBox: Box, plateBox: Box?) {
        plates.mkdirs(); vehicles.mkdirs()
        val name = "${number.replace(" ", "")}_${System.currentTimeMillis()}.jpg"
        crop(image, vehicleBox, 0.05f, 0.05f)?.let { write(it, File(vehicles, name)) }
        // 번호판은 학습 데이터처럼 주변을 조금 남겨서 자른다
        plateBox?.let { b -> crop(image, b, 0.08f, 0.18f)?.let { write(it, File(plates, name)) } }
    }

    /** 모은 사진 전체를 zip 하나로 묶는다. */
    fun export(): File {
        exportDir.mkdirs()
        exportDir.listFiles()?.forEach { it.delete() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.KOREA).format(Date())
        val zip = File(exportDir, "plate_training_$stamp.zip")
        ZipOutputStream(FileOutputStream(zip)).use { out ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                out.putNextEntry(ZipEntry(f.relativeTo(root).path))
                f.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }

    fun clear() {
        root.deleteRecursively()
    }

    private fun write(bmp: Bitmap, f: File) = FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }

    private fun crop(img: Bitmap, b: Box, ex: Float, ey: Float): Bitmap? {
        val w = b.x1 - b.x0; val h = b.y1 - b.y0
        val x0 = max(0, (b.x0 - ex * w).toInt()); val x1 = min(img.width, (b.x1 + ex * w).toInt())
        val y0 = max(0, (b.y0 - ey * h).toInt()); val y1 = min(img.height, (b.y1 + ey * h).toInt())
        if (x1 - x0 < 4 || y1 - y0 < 4) return null
        return Bitmap.createBitmap(img, x0, y0, x1 - x0, y1 - y0)
    }
}
