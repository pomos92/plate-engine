"""Python 엔진과 Android(Kotlin) 엔진 코어가 같은 결과를 내는지 비교.

준비: (cd android/jvm-check && gradle installDist)
실행: python tests/kotlin_parity.py
같은 차량 검출 결과를 양쪽에 넣고 번호/번호판색/차종/색상을 비교한다.
"""
import json, os, random, subprocess, sys, tempfile
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import cv2, numpy as np
from tests.composite_test import SPOTS
from train.synth_plates import render_plate
from engine.engine import VehicleEngine

ROOT = os.path.join(os.path.dirname(__file__), "..")
BIN = os.path.join(ROOT, "android", "jvm-check", "build", "install", "plateengine-jvm-check", "bin", "plateengine-jvm-check")

def main(trials=4, seed=11):
    random.seed(seed); np.random.seed(seed)
    eng = VehicleEngine()
    tmp = tempfile.mkdtemp()
    stats = {"number": 0, "plate_color": 0, "category": 0, "color": 0, "truth": 0, "kotlin_truth": 0}
    n = 0
    for t in range(trials):
        for f, boxes in SPOTS.items():
            im = cv2.imread(os.path.join(ROOT, "samples", f))
            truth = {}
            for (x0, y0, x1, y1) in boxes:
                rgb, lab, col, two = render_plate()
                while two:
                    rgb, lab, col, two = render_plate()
                p = cv2.resize(cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR), (x1 - x0, y1 - y0), interpolation=cv2.INTER_AREA)
                im[y0:y1, x0:x1] = cv2.GaussianBlur(p, (3, 3), 0)
                truth[x0] = lab
            ip = os.path.join(tmp, f"{t}_{f}"); cv2.imwrite(ip, im)
            dets = eng.detector.detect(im)
            dp = ip + ".det"
            with open(dp, "w") as fh:
                for d in dets:
                    fh.write(f'{d["label"]} {d["score"]} {" ".join(str(int(v)) for v in d["box"])}\n')
            py = eng.analyze(im)
            kt = json.loads(subprocess.run([BIN, os.path.join(ROOT, "models"), ip, dp], capture_output=True, text=True, check=True).stdout)
            for a, b in zip(py["vehicles"], kt["vehicles"]):
                n += 1
                pa, pb = a["plate"] or {}, b["plate"] or {}
                stats["number"] += pa.get("number") == pb.get("number")
                stats["plate_color"] += pa.get("plate_color") == pb.get("plate_color")
                stats["category"] += a["vehicle_type"]["category"] == b["vehicle_type"]["category"]
                stats["color"] += a["color"]["name"] == b["color"]["name"]
                tx = [v for k, v in truth.items() if a["box"][0] <= k <= a["box"][2]]
                if tx:
                    stats["truth"] += pa.get("number") == tx[0]
                    stats["kotlin_truth"] += pb.get("number") == tx[0]
                if pa.get("number") != pb.get("number") or a["color"]["name"] != b["color"]["name"]:
                    print("diff", f, "py:", pa.get("number"), a["color"]["name"], "| kt:", pb.get("number"), b["color"]["name"])
    print(f"차량 {n}대 비교: 번호 일치 {stats['number']}, 번호판색 일치 {stats['plate_color']}, "
          f"차종 일치 {stats['category']}, 차량색 일치 {stats['color']}")
    print(f"정답 대비: Python {stats['truth']}/{n}, Kotlin {stats['kotlin_truth']}/{n}")

if __name__ == "__main__":
    main()
