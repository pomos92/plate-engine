"""차량 검출: MediaPipe EfficientDet-Lite0 (COCO, Apache-2.0).

같은 .tflite 파일을 Android / iOS / PC 에서 그대로 쓸 수 있다.
"""
import cv2
import mediapipe as mp
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

VEHICLE_LABELS = {"car": "승용", "truck": "트럭", "bus": "버스", "motorcycle": "이륜차"}


class VehicleDetector:
    def __init__(self, model_path, score_threshold=0.3):
        opts = vision.ObjectDetectorOptions(
            base_options=mp_python.BaseOptions(model_asset_path=model_path),
            score_threshold=score_threshold, max_results=20,
            category_allowlist=list(VEHICLE_LABELS))
        self.det = vision.ObjectDetector.create_from_options(opts)

    def detect(self, bgr):
        rgb = cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB)
        res = self.det.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
        out = []
        for d in res.detections:
            b = d.bounding_box
            c = d.categories[0]
            out.append({"label": c.category_name, "score": float(c.score),
                        "box": (b.origin_x, b.origin_y, b.origin_x + b.width, b.origin_y + b.height)})
        return sorted(out, key=lambda d: -(d["box"][2] - d["box"][0]) * (d["box"][3] - d["box"][1]))
