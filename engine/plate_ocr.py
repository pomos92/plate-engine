"""번호판 OCR (ONNX Runtime)."""
import json
import os

import numpy as np
import onnxruntime as ort

from .ocr_prep import prep_plate


class PlateOCR:
    def __init__(self, model_path):
        self.sess = ort.InferenceSession(model_path, providers=["CPUExecutionProvider"])
        with open(os.path.splitext(model_path)[0] + "_chars.json", encoding="utf-8") as f:
            meta = json.load(f)
        self.chars, self.blank = meta["chars"], meta["blank"]

    def read(self, plate_bgr, two_line=None):
        """(문자열, 신뢰도 0~1) 반환."""
        x = prep_plate(plate_bgr, two_line)[None].astype(np.float32)
        logits = self.sess.run(None, {"image": x})[0][0]  # (C,T)
        e = np.exp(logits - logits.max(0, keepdims=True))
        p = e / e.sum(0, keepdims=True)
        ids, conf = p.argmax(0), p.max(0)
        text, confs, prev = [], [], -1
        for i, c in zip(ids, conf):
            if i != prev and i != self.blank:
                text.append(self.chars[i - 1]); confs.append(c)
            prev = i
        score = float(np.exp(np.mean(np.log(np.array(confs) + 1e-6)))) if confs else 0.0
        return "".join(text), score
