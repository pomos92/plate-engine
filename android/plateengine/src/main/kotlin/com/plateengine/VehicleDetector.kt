package com.plateengine

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.plateengine.core.Box
import com.plateengine.core.Detection

/** 차량 검출: MediaPipe EfficientDet-Lite2 int8 (assets/efficientdet_lite2_int8.tflite). */
internal class VehicleDetector(context: Context, scoreThreshold: Float = 0.3f) : AutoCloseable {
    private val detector: ObjectDetector = ObjectDetector.createFromOptions(
        context,
        ObjectDetector.ObjectDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
            .setRunningMode(RunningMode.IMAGE)
            .setScoreThreshold(scoreThreshold)
            .setMaxResults(20)
            .setCategoryAllowlist(listOf("car", "truck", "bus", "motorcycle"))
            .build()
    )

    fun detect(bitmap: Bitmap): List<Detection> {
        val result = detector.detect(BitmapImageBuilder(bitmap).build())
        return result.detections().map { d ->
            val b = d.boundingBox()
            val c = d.categories()[0]
            Detection(c.categoryName(), c.score(), Box(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt()))
        }
    }

    override fun close() = detector.close()

    companion object { const val MODEL = "efficientdet_lite2_int8.tflite" }
}
