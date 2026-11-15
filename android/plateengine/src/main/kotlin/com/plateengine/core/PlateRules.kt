package com.plateengine.core

/** 한국 번호판 규칙: 형식 검사, 번호로 차종/용도 판별. (Python engine/plate_rules.py 와 동일) */
object PlateRules {
    val REGIONS = listOf("서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원",
        "충북", "충남", "전북", "전남", "경북", "경남", "제주")
    private const val COMMERCIAL = "바사아자배"
    private const val RENTAL = "하허호"

    private val PLATE_RE = Regex(
        "^(" + REGIONS.joinToString("|") + ")?(\\d{2,3})([가-힣])(\\d{4})$"
    )

    data class Parsed(
        val number: String,
        val region: String?,
        /** 승용 / 승합 / 화물 / 특수 / 긴급 */
        val categoryByNumber: String,
        /** 자가용 / 사업용 / 렌터카 */
        val usage: String,
    )

    fun parse(text: String): Parsed? {
        val t = text.replace(" ", "")
        val m = PLATE_RE.matchEntire(t) ?: return null
        val region = m.groupValues[1].ifEmpty { null }
        val type = m.groupValues[2]
        val n = type.toInt()
        val cls = if (type.length == 3) {
            when {
                n <= 699 -> "승용"; n <= 799 -> "승합"; n <= 979 -> "화물"; n <= 997 -> "특수"; else -> "긴급"
            }
        } else {
            when { n <= 69 -> "승용"; n <= 79 -> "승합"; n <= 97 -> "화물"; else -> "특수" }
        }
        val h = m.groupValues[3][0]
        val usage = when {
            COMMERCIAL.indexOf(h) >= 0 -> "사업용"
            RENTAL.indexOf(h) >= 0 -> "렌터카"
            else -> "자가용"
        }
        return Parsed(t, region, cls, usage)
    }
}
