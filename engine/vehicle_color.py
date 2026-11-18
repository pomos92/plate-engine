"""차량 색상 판별: 차체 영역의 대표 색을 정해진 색 이름 중 하나로 고른다."""
import cv2
import numpy as np

# 이름, (H 범위들), 채도/명도 조건은 아래 classify_pixel에서 처리
COLOR_NAMES = ["흰색", "검정", "회색", "은색", "빨강", "주황", "노랑", "초록", "파랑", "남색", "갈색", "베이지"]


def _body_mask(h, w, plate_box=None):
    """차체 페인트가 잘 보이는 영역: 보닛 띠 + 좌우 옆면 (유리창·그릴·바닥 회피)."""
    m = np.zeros((h, w), bool)
    m[int(h * 0.28):int(h * 0.62), int(w * 0.15):int(w * 0.85)] = True
    m[int(h * 0.60):int(h * 0.85), int(w * 0.05):int(w * 0.19)] = True
    m[int(h * 0.60):int(h * 0.85), int(w * 0.81):int(w * 0.95)] = True
    if plate_box is not None:
        x0, y0, x1, y1 = plate_box
        m[max(0, y0 - 3):y1 + 3, max(0, x0 - 3):x1 + 3] = False
    return m


def _name_from_lab(L, a, b, scene_L):
    """대표 색(Lab, OpenCV 0~255 스케일) -> 색 이름."""
    chroma = np.hypot(a - 128, b - 128)
    if chroma < 14:
        # 밝기는 장면 밝기(중앙값)에 대한 비율로 판단 -> 어두운 주차장에서도 흰 차를 흰색으로
        r = L / max(scene_L, 60)
        if L > 205 or r > 0.9: return "흰색"
        if r > 0.65: return "은색"
        if r > 0.45: return "회색"
        return "검정"
    hue = (np.degrees(np.arctan2(b - 128, a - 128)) + 360) % 360  # Lab 색상각
    if L < 70 and chroma < 30:
        return "남색" if 220 <= hue < 320 else ("갈색" if hue < 90 else "검정")
    if hue < 40 or hue >= 345: return "빨강"
    if hue < 70: return "주황" if L > 120 else "갈색"
    if hue < 100: return "노랑" if chroma > 40 else "베이지"
    if hue < 200: return "초록"
    if hue < 300: return "파랑" if L >= 70 else "남색"
    return "빨강"


def classify_color(vehicle_bgr, plate_box=None, scene_bgr=None):
    """(색 이름, 신뢰도, 상위 후보) 반환. 차체 영역 픽셀을 k-means로 묶어 가장 큰 덩어리의 색을 쓴다."""
    if vehicle_bgr is None or vehicle_bgr.size == 0:
        return None, 0.0, []
    H0, W0 = vehicle_bgr.shape[:2]
    img = cv2.resize(vehicle_bgr, (160, 120), interpolation=cv2.INTER_AREA)
    if plate_box is not None:
        sx, sy = 160 / W0, 120 / H0
        plate_box = (int(plate_box[0] * sx), int(plate_box[1] * sy), int(plate_box[2] * sx), int(plate_box[3] * sy))
    m = _body_mask(120, 160, plate_box)
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB).reshape(-1, 3)[m.reshape(-1)].astype(np.float32)
    if len(lab) < 50:
        return None, 0.0, []
    crit = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 1.0)
    _, labels, centers = cv2.kmeans(lab, 3, None, crit, 3, cv2.KMEANS_PP_CENTERS)
    counts = np.bincount(labels.ravel(), minlength=3)
    ref = scene_bgr if scene_bgr is not None else vehicle_bgr
    scene_L = float(np.median(cv2.cvtColor(cv2.resize(ref, (200, 150)), cv2.COLOR_BGR2LAB)[..., 0]))
    votes = {}
    for i in range(3):
        n = _name_from_lab(*centers[i], scene_L)
        votes[n] = votes.get(n, 0) + counts[i] / counts.sum()
    ranked = sorted(votes.items(), key=lambda x: -x[1])
    best = ranked[0]
    return best[0], round(float(best[1]), 3), [(n, round(float(p), 3)) for n, p in ranked[:3]]


def classify_plate_color(plate_bgr):
    """번호판 바탕색: white / ev(파랑) / yellow / green / unknown."""
    if plate_bgr is None or plate_bgr.size == 0:
        return "unknown"
    hsv = cv2.cvtColor(plate_bgr, cv2.COLOR_BGR2HSV)
    h, s, v = hsv[..., 0].astype(int) * 2, hsv[..., 1] / 255, hsv[..., 2] / 255
    # 글자(어두운 부분) 제외, 바탕만
    bg = v >= np.percentile(v, 40)
    if bg.sum() < 20:
        return "unknown"
    hh, ss = h[bg], s[bg]
    sat = ss > 0.28
    if sat.mean() < 0.35:
        return "white"
    hm = np.median(hh[sat])
    if 180 <= hm <= 250:
        return "ev"
    if 30 <= hm < 75:
        return "yellow"
    if 75 <= hm < 180:
        return "green"
    return "white"
