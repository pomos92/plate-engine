"""합성 번호판 데이터셋을 미리 만들어 npz로 저장 (학습 속도용)."""
import os, sys
from multiprocessing import Pool
import numpy as np
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from train.synth_plates import sample
from engine.ocr_prep import prep_plate

def work(seed_n):
    seed, n = seed_n
    import random; random.seed(seed); np.random.seed(seed)
    X, L = [], []
    for _ in range(n):
        img, lab, _, two = sample()
        X.append((prep_plate(img, two)[0] * 255).astype(np.uint8)); L.append(lab)
    return np.stack(X), L

if __name__ == "__main__":
    n, out, seed0 = int(sys.argv[1]), sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 0
    jobs = [(seed0 + i, n // 40) for i in range(40)]
    with Pool(4) as p:
        res = p.map(work, jobs)
    X = np.concatenate([r[0] for r in res]); L = sum([r[1] for r in res], [])
    np.savez_compressed(out, X=X, L=np.array(L))
    print(out, X.shape)
