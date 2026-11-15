"""번호판 이미지 전처리 (학습과 추론에서 똑같이 사용)."""
import cv2
import numpy as np

OCR_H, OCR_W = 40, 192
TWO_LINE_ASPECT = 3.0  # 가로/세로 비율이 이보다 작으면 2줄 번호판으로 본다


def is_two_line(plate_bgr) -> bool:
    h, w = plate_bgr.shape[:2]
    return (w / max(h, 1)) < TWO_LINE_ASPECT


def prep_plate(plate_bgr, two_line=None):
    """번호판 컬러 이미지 -> (1, OCR_H, OCR_W) float32 [0,1].

    2줄 번호판은 위/아래 줄을 잘라 가로로 이어 붙여 1줄로 만든다.
    """
    if two_line is None:
        two_line = is_two_line(plate_bgr)
    g = cv2.cvtColor(plate_bgr, cv2.COLOR_BGR2GRAY) if plate_bgr.ndim == 3 else plate_bgr
    if two_line:
        h = g.shape[0]
        top = g[: int(h * 0.45)]
        bot = g[int(h * 0.38):]
        th = OCR_H
        top = cv2.resize(top, (max(1, int(top.shape[1] * th / max(top.shape[0], 1))), th))
        bot = cv2.resize(bot, (max(1, int(bot.shape[1] * th / max(bot.shape[0], 1))), th))
        g = np.concatenate([top, bot], axis=1)
    g = cv2.resize(g, (OCR_W, OCR_H), interpolation=cv2.INTER_AREA)
    g = g.astype(np.float32) / 255.0
    # 대비 정규화: 밝은 글자/어두운 배경(구형 녹색판)도 같은 분포로
    lo, hi = np.percentile(g, 2), np.percentile(g, 98)
    g = np.clip((g - lo) / max(hi - lo, 1e-3), 0, 1)
    return g[None]
