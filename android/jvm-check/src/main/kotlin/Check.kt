import com.plateengine.core.Box
import com.plateengine.core.Detection
import com.plateengine.core.PlateOcr
import com.plateengine.core.VehicleEngineCore
import nu.pattern.OpenCV
import org.opencv.imgcodecs.Imgcodecs
import java.io.File

/**
 * 사용법: check <models 폴더> <이미지> [검출 파일]
 * 검출 파일: 한 줄에 "label score x0 y0 x1 y1" (Python MediaPipe 검출 결과를 그대로 넘겨 비교용)
 */
fun main(args: Array<String>) {
    OpenCV.loadLocally()
    val models = File(args[0])
    val ocr = PlateOcr(File(models, "plate_ocr.onnx").readBytes(), File(models, "plate_ocr_chars.json").readText())
    val engine = VehicleEngineCore(ocr)
    val img = Imgcodecs.imread(args[1])
    val dets = if (args.size > 2) File(args[2]).readLines().filter { it.isNotBlank() }.map {
        val p = it.trim().split(" ")
        Detection(p[0], p[1].toFloat(), Box(p[2].toInt(), p[3].toInt(), p[4].toInt(), p[5].toInt()))
    } else emptyList()
    val t = System.nanoTime()
    val r = engine.analyze(img, dets)
    System.err.println("ms=" + (System.nanoTime() - t) / 1_000_000)
    java.io.PrintStream(java.io.FileOutputStream(java.io.FileDescriptor.out), true, "UTF-8").println(r.toJson())
}
