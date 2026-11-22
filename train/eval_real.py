"""실제 번호판 사진으로 지금 OCR 모델 정확도 확인.

사용법:
  python train/eval_real.py 폴더_또는_앱에서_내보낸.zip [--model models/plate_ocr.onnx] [--show-wrong]
파일명이 정답이어야 합니다 (예: 123가4567_아무거나.jpg). 학습 전/후 비교에 씁니다.
"""
import argparse
import os
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from engine.plate_ocr import PlateOCR  # noqa: E402
from train.train_ocr import real_files  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("paths", nargs="+")
    ap.add_argument("--model", default=os.path.join(os.path.dirname(__file__), "..", "models", "plate_ocr.onnx"))
    ap.add_argument("--show-wrong", action="store_true")
    a = ap.parse_args()
    ocr = PlateOCR(a.model)
    n = ok = 0
    for f in real_files(a.paths):
        lab = os.path.splitext(os.path.basename(f))[0].split("_")[0]
        img = cv2.imdecode(np.fromfile(f, np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            continue
        text, conf = ocr.read(img)
        n += 1; ok += text == lab
        if a.show_wrong and text != lab:
            print(f"틀림  정답 {lab}  인식 {text}  ({conf:.2f})  {os.path.basename(f)}")
    print(f"실제 사진 {n}장 중 {ok}장 정확 ({100 * ok / max(n, 1):.1f}%)")


if __name__ == "__main__":
    main()
