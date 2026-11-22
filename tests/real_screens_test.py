"""사용자가 보내준 실제 사진(앱 결과 화면 캡처에서 사진 부분만 잘라낸 것)으로 확인.

사용법: python tests/real_screens_test.py 폴더   (파일명 = 정답, 예: 154러7070.jpg)
"""
import glob
import os
import sys
import time

import cv2
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from engine.engine import VehicleEngine  # noqa: E402

eng = VehicleEngine(sys.argv[2]) if len(sys.argv) > 2 else VehicleEngine()
ok = n = 0
for f in sorted(glob.glob(os.path.join(sys.argv[1], "*.jpg"))):
    lab = os.path.splitext(os.path.basename(f))[0].split("_")[0]
    img = cv2.imdecode(np.fromfile(f, np.uint8), cv2.IMREAD_COLOR)
    t = time.time()
    r = eng.analyze(img)
    nums = [v["plate"]["number"] for v in r["vehicles"] if v["plate"]]
    hit = lab in nums
    n += 1; ok += hit
    print(("O" if hit else "X"), lab, nums, [v["plate"]["plate_color"] for v in r["vehicles"] if v["plate"]], f"{time.time() - t:.2f}s")
print(f"{ok}/{n}")
