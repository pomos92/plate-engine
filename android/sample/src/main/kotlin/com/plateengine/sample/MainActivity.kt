package com.plateengine.sample

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.plateengine.PlateEngine
import com.plateengine.sample.ui.PlateTheme
import com.plateengine.sample.ui.ResultScreen
import com.plateengine.sample.ui.ResultUi
import com.plateengine.sample.ui.UploadScreen
import com.plateengine.sample.ui.toUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** 등록 화면(카메라 촬영 / 갤러리 선택) -> 기기 안에서 인식 -> 결과 화면. */
class MainActivity : ComponentActivity() {
    private var engine: PlateEngine? = null
    private var ready by mutableStateOf(false)
    private var analyzing by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var result by mutableStateOf<ResultUi?>(null)
    private var cameraUri: Uri? = null

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) analyze(uri)
    }
    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = cameraUri
        if (ok && uri != null) analyze(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PlateTheme {
                val r = result
                if (r == null) {
                    UploadScreen(
                        ready = ready, analyzing = analyzing, message = message,
                        onCamera = ::openCamera,
                        onGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    )
                } else {
                    BackHandler { result = null }
                    ResultScreen(r, onBack = { result = null }, onSave = ::save)
                }
            }
        }
        lifecycleScope.launch {
            engine = withContext(Dispatchers.Default) { PlateEngine(applicationContext) }
            ready = true
        }
    }

    private fun openCamera() {
        val dir = File(cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "photo.jpg")
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        cameraUri = uri
        takePhoto.launch(uri)
    }

    private fun analyze(uri: Uri) {
        val eng = engine ?: return
        analyzing = true; message = null
        lifecycleScope.launch {
            try {
                val ui = withContext(Dispatchers.Default) {
                    val bmp = decode(uri)
                    val t = System.currentTimeMillis()
                    val r = eng.analyze(bmp)
                    r.toUi(bmp, System.currentTimeMillis() - t)
                }
                if (ui.vehicles.isEmpty()) message = "사진에서 차를 찾지 못했어요. 차가 크게 나오게 다시 찍어 주세요."
                else result = ui
            } catch (e: Exception) {
                message = "사진을 분석하지 못했어요: ${e.message}"
            } finally {
                analyzing = false
            }
        }
    }

    /** 긴 변 1600px 근처로 줄여 읽고, 카메라 사진의 회전(EXIF)을 바로잡는다. */
    private fun decode(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, opts)!! }
        val rotation = contentResolver.openInputStream(uri)!!.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
        if (rotation == 0f) return bmp
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation) }, true)
    }

    /** 결과 사진을 갤러리(사진/PlateEngine 폴더)에 저장. */
    private fun save(bitmap: Bitmap) {
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "plate_${System.currentTimeMillis()}.jpg")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PlateEngine")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                    contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                }.isSuccess
            }
            Toast.makeText(this@MainActivity, if (ok) "갤러리에 저장했어요" else "저장하지 못했어요", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        engine?.close()
        super.onDestroy()
    }
}
