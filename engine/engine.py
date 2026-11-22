"""차량 정보 인식 엔진: 사진 -> 차량별 (번호판, 차종, 색상, 전기차 여부)."""
import os

import cv2

from . import plate_rules
import numpy as np

from .plate_locator import char_group_candidates, deskew, find_plate_candidates
from .plate_ocr import PlateOCR
from .vehicle_color import classify_color, classify_plate_color
from .vehicle_detector import VehicleDetector, VEHICLE_LABELS

MODELS = os.path.join(os.path.dirname(__file__), "..", "models")

PLATE_COLOR_KO = {"white": "흰색", "ev": "파란색(전기차)", "yellow": "노란색(사업용)",
                  "green": "녹색(구형)", "unknown": "알 수 없음"}


class VehicleEngine:
    # (가로 확장 비율, 세로 확장 비율, 가로 이동 비율)
    VARIANTS = [(0, 0, 0), (0.12, 0.15, 0), (-0.06, -0.08, 0), (0.25, 0.3, 0), (0.05, 0.05, -0.08), (0.05, 0.05, 0.08)]

    def __init__(self, models_dir=MODELS):
        self.detector = VehicleDetector(os.path.join(models_dir, "efficientdet_lite2_int8.tflite"))
        self.ocr = PlateOCR(os.path.join(models_dir, "plate_ocr.onnx"))

    # ---- 번호판 ----
    def _try(self, crop, box, best, modes=None):
        """조각 하나를 읽어 지금까지 최고 결과와 비교."""
        h, w = crop.shape[:2]
        if crop.size == 0 or w < 10 or h < 5:
            return best
        ar = w / max(h, 1)
        if modes is None:
            modes = [None] if not (2.0 <= ar <= 3.6) else [False, True]  # 애매한 비율은 1줄/2줄 둘 다
        for two in modes:
            text, conf = self.ocr.read(crop, two)
            parsed = plate_rules.parse(text)
            # 형식이 맞으면 가산점, 글자가 잘려 짧아진 결과보다 온전한 결과를 우선
            score = conf + (1.0 + 0.03 * len(text) if parsed else 0.0)
            if best is None or score > best["score"]:
                best = {"text": text, "confidence": round(conf, 3), "valid_format": parsed is not None,
                        "parsed": parsed, "box": box, "score": score, "crop": crop}
        return best

    @staticmethod
    def _rect_crop(img, cx, cy, w, h, angle):
        """(cx,cy) 중심, angle(도)만큼 기운 w×h 영역을 똑바로 펴서 잘라낸다."""
        M = cv2.getRotationMatrix2D((cx, cy), angle, 1.0)
        M[0, 2] += w / 2 - cx; M[1, 2] += h / 2 - cy
        return cv2.warpAffine(img, M, (int(w), int(h)), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REPLICATE)

    def read_plate(self, bgr, region_box=None):
        """이미지(또는 차량 영역)에서 번호판을 찾아 읽는다. 좌표는 원본 기준."""
        ox, oy = 0, 0
        img = bgr
        if region_box is not None:
            x0, y0, x1, y1 = [int(v) for v in region_box]
            img = bgr[max(0, y0):y1, max(0, x0):x1]
            ox, oy = max(0, x0), max(0, y0)
        best = None
        ih, iw = img.shape[:2]
        # (1) 글자 모양으로 찾은 후보: 기울기를 바로잡아 똑바로 편 조각을 읽는다
        for (gx0, gy0, gx1, gy1, ang, hm) in char_group_candidates(img):
            cx, cy = (gx0 + gx1) / 2, (gy0 + gy1) / 2
            span = (gx1 - gx0) / max(np.cos(np.radians(ang)), 0.5)
            box = (gx0 + ox, gy0 + oy, gx1 + ox, gy1 + oy)
            # 1줄, 여유, 위로 넉넉히(2줄 번호판의 윗줄 포함). 글자 줄을 기준으로 잘랐으니 1줄로 읽고, 마지막만 2줄로도 읽는다
            for wmul, hmul, up, modes in ((1.0, 1.9, 0.0, [False]), (1.12, 2.4, 0.0, [False]), (1.05, 3.3, 0.55, [False, True])):
                w = span * wmul; h = hm * hmul
                ccx = cx - up * hm * np.sin(np.radians(ang)); ccy = cy - up * hm * np.cos(np.radians(ang))
                crop = self._rect_crop(img, ccx, ccy, w, h, ang)
                best = self._try(crop, box, best, modes)
        # (2) 경계·색으로 찾은 후보: 상자 오차를 감안해 크기/위치를 조금씩 바꿔 읽고, 기울어져 있으면 바로잡아서도 읽는다
        for (cx0, cy0, cx1, cy1) in find_plate_candidates(img):
            bw, bh = cx1 - cx0, cy1 - cy0
            for k, (ex, ey, dx) in enumerate(self.VARIANTS):
                x0 = int(max(0, cx0 - ex * bw + dx * bw)); x1 = int(min(iw, cx1 + ex * bw + dx * bw))
                y0 = int(max(0, cy0 - ey * bh)); y1 = int(min(ih, cy1 + ey * bh))
                crop = img[y0:y1, x0:x1]
                if crop.size == 0:
                    continue
                box = (x0 + ox, y0 + oy, x1 + ox, y1 + oy)
                best = self._try(crop, box, best)
                if k < 2:
                    d = deskew(crop)
                    if d is not crop:
                        best = self._try(d, box, best)
        # 형식도 안 맞고 글자 수도 적으면 번호판을 못 찾은 것으로 본다 (가려진/흐린 번호판 등)
        if best and not best["valid_format"] and len(best["text"]) < 5:
            return None
        return best

    # ---- 전체 ----
    def analyze(self, bgr):
        H, W = bgr.shape[:2]
        vehicles = self.detector.detect(bgr)
        if not vehicles:  # 번호판만 크게 찍은 사진 등: 전체를 하나의 차량 영역으로
            vehicles = [{"label": None, "score": 0.0, "box": (0, 0, W, H)}]
        results = []
        for v in vehicles:
            x0, y0, x1, y1 = [int(c) for c in v["box"]]
            x0, y0, x1, y1 = max(0, x0), max(0, y0), min(W, x1), min(H, y1)
            crop = bgr[y0:y1, x0:x1]
            plate = self.read_plate(bgr, (x0, y0, x1, y1))
            plate_box_local = None
            if plate:
                px0, py0, px1, py1 = plate["box"]
                plate_box_local = (px0 - x0, py0 - y0, px1 - x0, py1 - y0)
            color, color_conf, color_top = classify_color(crop, plate_box_local, bgr)
            plate_color = classify_plate_color(plate["crop"]) if plate else "unknown"
            results.append(self._compose(v, (x0, y0, x1, y1), plate, plate_color, color, color_conf, color_top))
        return {"image_size": [W, H], "vehicles": results}

    @staticmethod
    def _compose(v, box, plate, plate_color, color, color_conf, color_top):
        parsed = plate["parsed"] if plate and plate["valid_format"] else None
        # 차종: 번호판 규칙(가장 확실) > 검출 모델 라벨
        category = None; source = None
        if parsed:
            category = {"승용": "승용", "승합": "승합", "화물": "트럭", "특수": "특수", "긴급": "긴급"}[
                parsed["category_by_number"]]
            source = "번호판"
        elif v["label"] and v["score"] >= 0.5:  # 검출 점수가 낮으면 car/truck 구분을 믿기 어렵다
            category = VEHICLE_LABELS.get(v["label"], v["label"]); source = "차량 모양"
        is_ev = plate_color == "ev"
        # 경차: 번호판에 표시가 없다. 현재는 차량 박스 비율로 '추정'만 하고, 전용 모델 학습 전까지 낮은 신뢰도로 표시
        w, h = box[2] - box[0], box[3] - box[1]
        compact_guess = bool(category == "승용" and h > 0 and w / h < 1.25)
        return {
            "box": list(box),
            "detector": {"label": v["label"], "score": round(v["score"], 3)},
            "plate": None if not plate else {
                "number": plate["text"], "valid_format": plate["valid_format"],
                "confidence": plate["confidence"], "box": list(plate["box"]),
                "plate_color": PLATE_COLOR_KO[plate_color],
                "region": parsed["region"] if parsed else None,
                "usage": parsed["usage"] if parsed else None,
            },
            "vehicle_type": {"category": category, "source": source,
                             "electric": is_ev, "compact_guess": compact_guess},
            "color": {"name": color, "confidence": color_conf, "top3": color_top},
        }
