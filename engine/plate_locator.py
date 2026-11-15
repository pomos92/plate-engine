"""번호판 후보 영역 찾기 (학습 데이터 없이 동작하는 영상처리 방식).

후보를 넉넉히 뽑고, 최종 선택은 OCR 결과(형식이 맞는지 + 신뢰도)로 한다.
나중에 번호판 검출 모델을 학습하면 이 모듈만 교체하면 된다.
"""
import cv2
import numpy as np


def _nms(boxes, scores, thr=0.4):
    if not boxes:
        return []
    b = np.array(boxes, dtype=np.float32); s = np.array(scores)
    order = s.argsort()[::-1]; keep = []
    while len(order):
        i = order[0]; keep.append(i)
        xx0 = np.maximum(b[i, 0], b[order[1:], 0]); yy0 = np.maximum(b[i, 1], b[order[1:], 1])
        xx1 = np.minimum(b[i, 2], b[order[1:], 2]); yy1 = np.minimum(b[i, 3], b[order[1:], 3])
        inter = np.clip(xx1 - xx0, 0, None) * np.clip(yy1 - yy0, 0, None)
        area = (b[:, 2] - b[:, 0]) * (b[:, 3] - b[:, 1])
        iou = inter / (area[i] + area[order[1:]] - inter + 1e-6)
        order = order[1:][iou < thr]
    return [boxes[i] for i in keep]


def find_plate_candidates(bgr, max_candidates=12):
    """bgr 이미지에서 번호판일 법한 사각형 (x0,y0,x1,y1) 목록."""
    H, W = bgr.shape[:2]
    scale = 640 / max(H, W) if max(H, W) > 640 else 1.0
    img = cv2.resize(bgr, (int(W * scale), int(H * scale))) if scale != 1 else bgr
    h, w = img.shape[:2]
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    gray = cv2.createCLAHE(2.0, (8, 8)).apply(gray)

    cands, scores = [], []

    def add_from_mask(mask, src_bonus):
        cnts, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for c in cnts:
            x, y, bw, bh = cv2.boundingRect(c)
            if bw < 0.04 * w or bh < 8 or bw > 0.9 * w or bh > 0.5 * h:
                continue
            ar = bw / bh
            if not (1.4 <= ar <= 7.5):
                continue
            fill = cv2.contourArea(c) / (bw * bh)
            if fill < 0.45:
                continue
            roi = gray[y:y + bh, x:x + bw]
            # 글자가 있으면 세로 경계가 많고 명암 대비가 크다
            gx = np.abs(cv2.Sobel(roi, cv2.CV_32F, 1, 0, ksize=3)).mean()
            contrast = roi.std()
            if contrast < 18:
                continue
            # 이미지 아래쪽(범퍼 위치)·가로 중앙 가중
            pos = 1.0 - abs((x + bw / 2) / w - 0.5) + 0.5 * ((y + bh / 2) / h)
            score = gx / 40 + contrast / 60 + pos + src_bonus + (0.5 if 2.0 <= ar <= 5.5 else 0)
            cands.append((x, y, x + bw, y + bh)); scores.append(score)

    # (1) 세로 경계 밀집 영역 (글자열)
    sx = cv2.Sobel(cv2.GaussianBlur(gray, (3, 3), 0), cv2.CV_8U, 1, 0, ksize=3)
    _, th = cv2.threshold(sx, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    for kw in (max(9, w // 40), max(15, w // 25)):
        m = cv2.morphologyEx(th, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (kw, 3)))
        m = cv2.morphologyEx(m, cv2.MORPH_OPEN, cv2.getStructuringElement(cv2.MORPH_RECT, (3, 3)))
        add_from_mask(m, 0.0)

    # (2) 번호판 바탕색 영역 (흰/노랑/파랑/초록) + 밝은 영역
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
    masks = [
        cv2.inRange(hsv, (0, 0, 150), (180, 60, 255)),        # 흰색
        cv2.inRange(hsv, (15, 90, 110), (38, 255, 255)),      # 노랑
        cv2.inRange(hsv, (88, 70, 70), (125, 255, 255)),      # 파랑(전기차)
        cv2.inRange(hsv, (40, 60, 40), (88, 255, 200)),       # 초록(구형)
    ]
    k = cv2.getStructuringElement(cv2.MORPH_RECT, (max(5, w // 60), max(3, h // 120)))
    for mk in masks:
        mk = cv2.morphologyEx(mk, cv2.MORPH_CLOSE, k)
        add_from_mask(mk, 0.3)

    boxes = _nms(cands, scores, 0.5)
    order = sorted(range(len(boxes)), key=lambda i: -scores[cands.index(boxes[i])])
    out = []
    for i in order[:max_candidates]:
        x0, y0, x1, y1 = boxes[i]
        # 약간 여유를 두고 원본 좌표로
        px, py = int(0.04 * (x1 - x0)), int(0.12 * (y1 - y0))
        out.append((max(0, int((x0 - px) / scale)), max(0, int((y0 - py) / scale)),
                    min(W, int((x1 + px) / scale)), min(H, int((y1 + py) / scale))))
    return out
