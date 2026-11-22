"""실제 사진 폴더로 Python 엔진과 Kotlin 엔진 코어 결과 비교 (같은 차량 검출 결과를 넣음).
사용법: python tests/kotlin_parity_real.py 폴더 [models 폴더]
"""
import glob, json, os, subprocess, sys, tempfile
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
import cv2, numpy as np
from engine.engine import VehicleEngine
ROOT = os.path.join(os.path.dirname(__file__), "..")
BIN = os.path.join(ROOT, "android", "jvm-check", "build", "install", "plateengine-jvm-check", "bin", "plateengine-jvm-check")
models = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "models")
eng = VehicleEngine(models)
tmp = tempfile.mkdtemp(); same = n = 0
for f in sorted(glob.glob(os.path.join(sys.argv[1], "*.jpg"))):
    im = cv2.imdecode(np.fromfile(f, np.uint8), cv2.IMREAD_COLOR)
    ip = os.path.join(tmp, f"{n}.png"); cv2.imwrite(ip, im)
    dets = eng.detector.detect(im); dp = ip + ".det"
    with open(dp, "w") as fh:
        for d in dets:
            fh.write(f'{d["label"]} {d["score"]} {" ".join(str(int(v)) for v in d["box"])}\n')
    eng.detector.detect = lambda _im, _d=dets: _d
    py = eng.analyze(im)
    del eng.detector.detect
    kt = json.loads(subprocess.run([BIN, models, ip, dp], capture_output=True, text=True).stdout)
    pn = [(v["plate"] or {}).get("number") for v in py["vehicles"]]
    kn = [(v["plate"] or {}).get("number") for v in kt["vehicles"]]
    n += 1; same += pn == kn
    print("same" if pn == kn else "DIFF", os.path.basename(f), pn, kn)
print(f"{same}/{n} identical")
