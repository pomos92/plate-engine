package com.plateengine.sample

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.plateengine.PlateEngine
import com.plateengine.sample.ui.PlateTheme
import com.plateengine.sample.ui.ResultScreen
import com.plateengine.sample.ui.ResultUi
import com.plateengine.sample.ui.UploadScreen
import com.plateengine.sample.ui.WatchScreen
import com.plateengine.sample.watcher.Saf
import com.plateengine.sample.watcher.WatchService
import com.plateengine.sample.watcher.WatchSettings
import com.plateengine.sample.watcher.WatchState
import com.plateengine.sample.ui.corrected
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
    private lateinit var training: TrainingStore
    private var trainingCount by mutableStateOf(0)
    private var showWatch by mutableStateOf(false)
    private var watchSettings by mutableStateOf(WatchSettings())
    private var watchError by mutableStateOf<String?>(null)
    /** 폴더 선택 창을 감시 폴더용으로 열었는지(아니면 백업 폴더용) */
    private var pickingWatch = true

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolderPicked(uri)
    }
    private val askNotify = registerForActivityResult(ActivityResultContracts.RequestPermission()) { startWatch(force = true) }

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
        training = TrainingStore(applicationContext)
        trainingCount = training.count()
        watchSettings = WatchSettings.load(this)
        showWatch = intent?.getBooleanExtra(EXTRA_OPEN_WATCH, false) == true
        setContent {
            PlateTheme {
                val r = result
                val watching by WatchState.running.collectAsState()
                if (showWatch) {
                    BackHandler { showWatch = false }
                    val status by WatchState.status.collectAsState()
                    val counts by WatchState.counts.collectAsState()
                    val logs by WatchState.logs.collectAsState()
                    WatchScreen(
                        settings = watchSettings,
                        watchLabel = Saf.label(watchSettings.watchUri),
                        backupLabel = Saf.label(watchSettings.backupUri),
                        running = watching, status = status, counts = counts, logs = logs, error = watchError,
                        onBack = { showWatch = false },
                        onPickWatch = { pickingWatch = true; pickFolder.launch(watchSettings.watchUri) },
                        onPickBackup = { pickingWatch = false; pickFolder.launch(watchSettings.backupUri) },
                        onChange = { watchSettings = it; it.save(this) },
                        onStart = { startWatch() },
                        onStop = { WatchService.stop(this) },
                    )
                } else if (r == null) {
                    UploadScreen(
                        ready = ready, analyzing = analyzing, message = message,
                        onCamera = ::openCamera,
                        onGallery = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        trainingCount = trainingCount,
                        onExportTraining = ::exportTraining,
                        onClearTraining = { training.clear(); trainingCount = 0 },
                        onFolderWatch = { showWatch = true },
                        watching = watching,
                    )
                } else {
                    BackHandler { result = null }
                    ResultScreen(r, onBack = { result = null }, onSave = ::save, onCorrect = ::correct)
                }
            }
        }
        lifecycleScope.launch {
            engine = withContext(Dispatchers.Default) { PlateEngine(applicationContext) }
            ready = true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_WATCH, false)) { result = null; showWatch = true }
    }

    private fun onFolderPicked(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { contentResolver.takePersistableUriPermission(uri, flags) }
        val old = if (pickingWatch) watchSettings.watchUri else watchSettings.backupUri
        val next = if (pickingWatch) watchSettings.copy(watchUri = uri) else watchSettings.copy(backupUri = uri)
        // 다른 쪽에서 쓰지 않는 이전 폴더 권한은 돌려준다
        if (old != null && old != uri && old != next.watchUri && old != next.backupUri) {
            runCatching { contentResolver.releasePersistableUriPermission(old, flags) }
        }
        watchSettings = next; next.save(this)
        watchError = folderProblem(next)
    }

    private fun folderProblem(s: WatchSettings): String? {
        val w = s.watchUri ?: return null
        val b = s.backupUri ?: return null
        return when {
            Saf.isSameOrInside(w, b) -> "백업 폴더가 감시 폴더 안에 있어요. 감시 폴더 밖의 폴더를 골라 주세요."
            Saf.isSameOrInside(b, w) -> "감시 폴더가 백업 폴더 안에 있어요. 서로 다른 폴더를 골라 주세요."
            else -> null
        }
    }

    private fun startWatch(force: Boolean = false) {
        val s = watchSettings
        if (!s.ready) return
        folderProblem(s)?.let { watchError = it; return }
        watchError = null
        if (!force && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            askNotify.launch(Manifest.permission.POST_NOTIFICATIONS); return
        }
        runCatching { WatchService.start(this) }.onFailure { watchError = "감시를 시작하지 못했어요: ${it.message}" }
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

    private fun decode(uri: Uri): Bitmap = decodeForEngine(contentResolver, uri)

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

    /** 사용자가 고친 번호: 화면을 바꾸고, 사진을 학습용으로 휴대폰에 저장. */
    private fun correct(index: Int, number: String) {
        val r = result ?: return
        val v = r.vehicles.getOrNull(index) ?: return
        result = r.copy(vehicles = r.vehicles.toMutableList().also { it[index] = v.corrected(number) })
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { training.save(r.image, number, v.vehicleBox, v.plateBox); training.count() }
            }
            ok.onSuccess { trainingCount = it }
            Toast.makeText(this@MainActivity,
                if (ok.isSuccess) "학습용 사진으로 저장했어요 (${trainingCount}장)" else "저장하지 못했어요", Toast.LENGTH_SHORT).show()
        }
    }

    /** 모은 사진을 zip으로 묶어 공유(드라이브, 카카오톡, 메일 등)로 내보낸다. */
    private fun exportTraining() {
        lifecycleScope.launch {
            val zip = withContext(Dispatchers.IO) { runCatching { training.export() }.getOrNull() }
            if (zip == null) {
                Toast.makeText(this@MainActivity, "내보내지 못했어요", Toast.LENGTH_SHORT).show(); return@launch
            }
            val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", zip)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "번호판 학습용 사진 ${trainingCount}장")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(send, "학습용 사진 내보내기"))
        }
    }

    companion object {
        const val EXTRA_OPEN_WATCH = "open_watch"
    }

    override fun onDestroy() {
        engine?.close()
        super.onDestroy()
    }
}
