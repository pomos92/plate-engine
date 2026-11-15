"""차량 정보 인식 엔진: 사진 -> 차량별 (번호판, 차종, 색상, 전기차 여부)."""
import os

import cv2

from . import plate_rules
from .plate_locator import find_plate_candidates
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
        for (cx0, cy0, cx1, cy1) in find_plate_candidates(img):
            bw, bh = cx1 - cx0, cy1 - cy0
            # 후보 상자 오차를 감안해 크기/위치를 조금씩 바꿔가며 읽고 가장 그럴듯한 결과를 고른다
            for ex, ey, dx in self.VARIANTS:
                x0 = int(max(0, cx0 - ex * bw + dx * bw)); x1 = int(min(iw, cx1 + ex * bw + dx * bw))
                y0 = int(max(0, cy0 - ey * bh)); y1 = int(min(ih, cy1 + ey * bh))
                crop = img[y0:y1, x0:x1]
                if crop.size == 0 or x1 - x0 < 10 or y1 - y0 < 5:
                    continue
                text, conf = self.ocr.read(crop)
                parsed = plate_rules.parse(text)
                # 형식이 맞으면 가산점, 글자가 잘려 짧아진 결과보다 온전한 결과를 우선
                score = conf + (1.0 + 0.03 * len(text) if parsed else 0.0)
                if best is None or score > best["score"]:
                    best = {"text": text, "confidence": round(conf, 3), "valid_format": parsed is not None,
                            "parsed": parsed, "box": (x0 + ox, y0 + oy, x1 + ox, y1 + oy),
                            "score": score, "crop": crop}
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
        elif v["label"]:
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
