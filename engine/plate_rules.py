"""한국 번호판 규칙: 문자셋, 형식 검사, 번호로 차종/용도 판별."""
import re

REGIONS = ["서울", "부산", "대구", "인천", "광주", "대전", "울산", "세종", "경기", "강원",
           "충북", "충남", "전북", "전남", "경북", "경남", "제주"]

# 자가용(비사업용)
PRIVATE_HANGUL = list("가나다라마거너더러머버서어저고노도로모보소오조구누두루무부수우주")
# 사업용(택시·버스·화물) / 렌터카 / 택배
COMMERCIAL_HANGUL = list("바사아자배")
RENTAL_HANGUL = list("하허호")
SPECIAL_HANGUL = ["육", "해", "공", "국", "합", "외교", "영사", "준외", "준영", "대표", "협정"]

PLATE_HANGUL = PRIVATE_HANGUL + COMMERCIAL_HANGUL + RENTAL_HANGUL

def charset():
    chars = set("0123456789")
    for w in PLATE_HANGUL + REGIONS + SPECIAL_HANGUL:
        chars.update(w)
    return sorted(chars)

PLATE_RE = re.compile(
    r"^(?P<region>" + "|".join(REGIONS) + r")?"
    r"(?P<type>\d{2,3})(?P<hangul>[가-힣])(?P<serial>\d{4})$"
)


def parse(text: str):
    """번호 문자열을 해석. 형식이 맞지 않으면 None."""
    m = PLATE_RE.match(text.replace(" ", ""))
    if not m:
        return None
    d = m.groupdict()
    t = int(d["type"])
    # 차종 기호(앞 2~3자리). 3자리는 앞 2자리 기준으로도 동일 구간 적용
    # 2019년 개정: 100~699 승용, 700~799 승합, 800~979 화물, 980~997 특수, 998~999 긴급
    if len(d["type"]) == 3:
        if t <= 699: cls = "승용"
        elif t <= 799: cls = "승합"
        elif t <= 979: cls = "화물"
        elif t <= 997: cls = "특수"
        else: cls = "긴급"
    else:
        if t <= 69: cls = "승용"
        elif t <= 79: cls = "승합"
        elif t <= 97: cls = "화물"
        else: cls = "특수"
    h = d["hangul"]
    if h in COMMERCIAL_HANGUL: usage = "사업용"
    elif h in RENTAL_HANGUL: usage = "렌터카"
    else: usage = "자가용"
    return {
        "number": text.replace(" ", ""),
        "region": d["region"],
        "category_by_number": cls,   # 승용/승합/화물/특수
        "usage": usage,
    }
