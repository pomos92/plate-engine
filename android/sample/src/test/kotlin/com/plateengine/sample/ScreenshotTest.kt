package com.plateengine.sample

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.plateengine.sample.ui.corrected
import com.plateengine.core.Box
import com.plateengine.sample.ui.PlateKind
import com.plateengine.sample.ui.PlateTheme
import com.plateengine.sample.ui.ResultScreen
import com.plateengine.sample.ui.ResultUi
import com.plateengine.sample.ui.UploadScreen
import com.plateengine.sample.ui.VehicleUi
import com.plateengine.sample.ui.WatchScreen
import com.plateengine.sample.watcher.WatchLog
import com.plateengine.sample.watcher.WatchSettings
import com.plateengine.sample.watcher.WatchState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 화면 디자인 확인용 캡처: ./gradlew :sample:recordRoborazziDebug -> sample/build/outputs/roborazzi/ */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun upload() {
        compose.setContent { PlateTheme { UploadScreen(ready = true, analyzing = false, message = null, onCamera = {}, onGallery = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/1_upload.png")
    }

    @Test fun uploadAnalyzing() {
        compose.setContent { PlateTheme { UploadScreen(ready = true, analyzing = true, message = null, onCamera = {}, onGallery = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/2_upload_analyzing.png")
    }

    @Test fun result() {
        compose.setContent { PlateTheme { ResultScreen(fakeResult(PlateKind.WHITE, "123가4567", false), onBack = {}, onSave = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/3_result.png")
    }

    @Test fun resultEv() {
        compose.setContent { PlateTheme { ResultScreen(fakeResult(PlateKind.EV, "321허7788", true), onBack = {}, onSave = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/4_result_ev.png")
    }

    @Test fun resultNotFound() {
        compose.setContent { PlateTheme { ResultScreen(fakeResult(PlateKind.WHITE, null, false), onBack = {}, onSave = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/5_result_not_found.png")
    }

    @Test fun uploadWithTraining() {
        compose.setContent { PlateTheme { UploadScreen(ready = true, analyzing = false, message = null, onCamera = {}, onGallery = {}, trainingCount = 12) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/6_upload_training.png")
    }

    @Test fun resultCorrected() {
        val r = fakeResult(PlateKind.WHITE, "123가4561", false)
        val fixed = r.copy(vehicles = listOf(r.vehicles[0].corrected("123가4567")) + r.vehicles.drop(1))
        compose.setContent { PlateTheme { ResultScreen(fixed, onBack = {}, onSave = {}) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/8_result_corrected.png")
    }

    @Test fun uploadWatching() {
        compose.setContent { PlateTheme { UploadScreen(ready = true, analyzing = false, message = null, onCamera = {}, onGallery = {}, watching = true) } }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/9_upload_watching.png")
    }

    @Test fun watchSetup() {
        compose.setContent {
            PlateTheme {
                WatchScreen(
                    settings = WatchSettings(), watchLabel = "", backupLabel = "", running = false, status = "",
                    counts = WatchState.Counts(), logs = emptyList(), error = null,
                    onBack = {}, onPickWatch = {}, onPickBackup = {}, onChange = {}, onStart = {}, onStop = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/10_watch_setup.png")
    }

    @Test fun watchRunning() {
        val now = 1_795_900_000_000L
        val logs = listOf(
            WatchLog(now, "1층/A구역/IMG_0012.jpg", "1층/A구역/20261128235101_67너7570.jpg", "67너7570"),
            WatchLog(now - 9000, "1층/A구역/IMG_0011.jpg", "1층/A구역/IMG_0011.jpg", null),
            WatchLog(now - 30000, "2층/IMG_0010.jpg", "2층/20261128234930_123가4567.jpg", "123가4567"),
        )
        compose.setContent {
            PlateTheme {
                WatchScreen(
                    settings = WatchSettings(watchUri = android.net.Uri.parse("content://x/tree/primary%3ADCIM%2FCamera"),
                        backupUri = android.net.Uri.parse("content://x/tree/primary%3APictures%2F백업")),
                    watchLabel = "내장 메모리/DCIM/Camera", backupLabel = "내장 메모리/Pictures/백업", running = true,
                    status = "새 사진을 기다리는 중", counts = WatchState.Counts(read = 2, unread = 1), logs = logs, error = null,
                    onBack = {}, onPickWatch = {}, onPickBackup = {}, onChange = {}, onStart = {}, onStop = {},
                )
            }
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/11_watch_running.png")
    }

    private fun fakeResult(kind: PlateKind, number: String?, ev: Boolean): ResultUi {
        val bmp = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 800f, 0xFF3A4660.toInt(), 0xFF9AA6BA.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 1200f, 800f, p); p.shader = null
        p.color = 0xFFE9EDF3.toInt(); c.drawRoundRect(RectF(330f, 260f, 870f, 640f), 60f, 60f, p)
        p.color = 0xFF1E2636.toInt(); c.drawRoundRect(RectF(400f, 280f, 800f, 420f), 30f, 30f, p)
        p.color = 0xFFFFFFFF.toInt(); c.drawRect(RectF(520f, 540f, 680f, 580f), p)
        val v = VehicleUi(
            plateNumber = number, plateValid = number != null, plateKind = kind,
            category = "승용", electric = ev, compactGuess = false, colorName = if (ev) "흰색" else "은색",
            usage = if (number != null) "자가용" else null, confidence = if (number != null) 0.97 else null,
            vehicleBox = Box(330, 250, 870, 650), plateBox = if (number != null) Box(515, 535, 685, 585) else null,
        )
        val v2 = v.copy(plateNumber = "45나6789", plateKind = PlateKind.WHITE, colorName = "검정", electric = false)
        return ResultUi(bmp, if (number != null) listOf(v, v2) else listOf(v), "2026.11.18. (수) 21:30:12", 210)
    }
}
