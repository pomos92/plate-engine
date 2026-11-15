"""실제 차량 사진의 번호판 자리에 합성 번호판을 덮어씌워 전체 파이프라인(검출→위치→OCR→판별)을 점검.

테스트용 차량 사진(번호판이 모자이크된 공개 예시)은 저작권 때문에 저장소에 넣지 않았습니다. 받는 방법:
  mkdir -p samples && for n in 2 3; do curl -L -o samples/$n.png \
    https://raw.githubusercontent.com/RoadoneP/Korean_license_plate_recognition/main/images/$n.png; done

실제 번호판 사진이 생기면 tests/real/ 에 '정답.jpg' 로 넣고 real_test.py 를 쓰는 것이 정확하다.
"""
import os, random, sys
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import cv2, numpy as np
from train.synth_plates import render_plate
from engine.engine import VehicleEngine

SPOTS = {"2.png": [(160, 742, 316, 772), (796, 738, 936, 768), (1340, 726, 1470, 754)],
         "3.png": [(160, 512, 316, 542), (796, 508, 936, 538), (1340, 496, 1470, 524)]}
COLOR_KO = {"white": "흰색", "ev": "파란색(전기차)", "yellow": "노란색(사업용)"}

def main(trials=8, seed=3):
    random.seed(seed); np.random.seed(seed)
    eng = VehicleEngine()
    ok = col_ok = tot = 0
    for _ in range(trials):
        for f, boxes in SPOTS.items():
            im = cv2.imread(os.path.join(os.path.dirname(__file__), "..", "samples", f))
            truth = []
            for (x0, y0, x1, y1) in boxes:
                rgb, lab, col, two = render_plate()
                while two:
                    rgb, lab, col, two = render_plate()
                p = cv2.resize(cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR), (x1 - x0, y1 - y0), interpolation=cv2.INTER_AREA)
                im[y0:y1, x0:x1] = cv2.GaussianBlur(p, (3, 3), 0)
                truth.append((x0, lab, col))
            got = {v["plate"]["box"][0]: v["plate"] for v in eng.analyze(im)["vehicles"] if v["plate"]}
            for x0, lab, col in truth:
                m = [g for k, g in got.items() if abs(k - x0) < 60]
                hit = bool(m) and m[0]["number"] == lab
                ok += hit; tot += 1
                col_ok += bool(m) and m[0]["plate_color"] == COLOR_KO[col]
                if not hit:
                    print("miss", lab, col, "->", m[0]["number"] if m else None)
    print(f"번호 전체 일치 {ok}/{tot} ({ok / tot:.0%}), 번호판 색 일치 {col_ok}/{tot}")

if __name__ == "__main__":
    main()
