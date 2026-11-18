package com.plateengine.sample.ui

import android.graphics.Bitmap
import com.plateengine.core.AnalyzeResult
import com.plateengine.core.Box
import com.plateengine.core.VehicleResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 결과 화면에 보여줄 한 대의 차량 정보 (엔진 결과에서 화면용으로 정리한 것). */
data class VehicleUi(
    val plateNumber: String?,       // 번호판을 못 읽으면 null
    val plateValid: Boolean,
    val plateKind: PlateKind,
    val category: String?,          // 승용 / 승합 / 트럭 ...
    val electric: Boolean,
    val compactGuess: Boolean,
    val colorName: String?,
    val usage: String?,             // 자가용 / 사업용 / 렌터카
    val confidence: Double?,        // 번호 인식 신뢰도 0~1
    val vehicleBox: Box,
    val plateBox: Box?,
)

enum class PlateKind { WHITE, EV, YELLOW, GREEN }

data class ResultUi(
    val image: Bitmap,
    val vehicles: List<VehicleUi>,
    val recognizedAt: String,
    val elapsedMs: Long,
)

private fun VehicleResult.toUi() = VehicleUi(
    plateNumber = plate?.number,
    plateValid = plate?.validFormat == true,
    plateKind = when {
        plate?.plateColor?.startsWith("파란") == true -> PlateKind.EV
        plate?.plateColor?.startsWith("노란") == true -> PlateKind.YELLOW
        plate?.plateColor?.startsWith("녹색") == true -> PlateKind.GREEN
        else -> PlateKind.WHITE
    },
    category = type.category,
    electric = type.electric,
    compactGuess = type.compactGuess,
    colorName = color.name,
    usage = plate?.usage,
    confidence = plate?.confidence,
    vehicleBox = box,
    plateBox = plate?.box,
)

/** 번호를 읽은 차를 앞에, 그중 형식이 맞고 신뢰도 높은 순으로. */
fun AnalyzeResult.toUi(image: Bitmap, elapsedMs: Long, now: Date = Date()): ResultUi {
    val list = vehicles.map { it.toUi() }.sortedWith(
        compareByDescending<VehicleUi> { it.plateNumber != null }
            .thenByDescending { it.plateValid }
            .thenByDescending { it.confidence ?: 0.0 }
    )
    val fmt = SimpleDateFormat("yyyy.MM.dd. (E) HH:mm:ss", Locale.KOREA)
    return ResultUi(image, list, fmt.format(now), elapsedMs)
}

/** 화면에 보일 차종 문구: "승용차", "트럭" 등 */
fun VehicleUi.categoryLabel(): String = when (category) {
    "승용" -> if (compactGuess) "승용차 (경차 추정)" else "승용차"
    "승합" -> "승합차"
    "특수" -> "특수차"
    "긴급" -> "긴급차"
    null -> "확인 불가"
    else -> category
}
