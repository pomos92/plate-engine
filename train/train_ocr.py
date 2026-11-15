"""번호판 OCR 모델(CNN + CTC) 학습 후 ONNX로 내보내기.

모바일 호환을 위해 LSTM 없이 합성곱만 사용 (ONNX / TFLite / CoreML 변환이 쉬움).
사용법: python train/gen_dataset.py 120000 train/data/train.npz && python train/gen_dataset.py 4000 train/data/val.npz 999
      python train/train_ocr.py --epochs 8 --out models/plate_ocr.onnx
실제 번호판 사진이 있으면 --real 폴더(파일명 = 정답, 예: 123가4567.jpg)로 섞어서 학습.
"""
import argparse
import glob
import json
import os
import random
import sys
import time

import cv2
import numpy as np
import torch
import torch.nn as nn

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from engine.ocr_prep import prep_plate, OCR_H, OCR_W  # noqa: E402
from engine.plate_rules import charset  # noqa: E402

CHARS = charset()
BLANK = 0
C2I = {c: i + 1 for i, c in enumerate(CHARS)}


def conv_bn(i, o, s=1):
    return nn.Sequential(nn.Conv2d(i, o, 3, s, 1, bias=False), nn.BatchNorm2d(o), nn.ReLU(inplace=True))


class PlateOCR(nn.Module):
    def __init__(self, n_classes):
        super().__init__()
        self.body = nn.Sequential(
            conv_bn(1, 24), nn.MaxPool2d(2),              # 20 x 96
            conv_bn(24, 48), nn.MaxPool2d(2),             # 10 x 48
            conv_bn(48, 80), nn.MaxPool2d((2, 1)),        # 5 x 48
            conv_bn(80, 112), conv_bn(112, 112),
            nn.MaxPool2d((2, 1)),                         # 2 x 48
            conv_bn(112, 160),
        )
        self.collapse = nn.Conv2d(160, 192, (2, 1))       # 1 x 48
        self.seq = nn.Sequential(
            nn.Conv1d(192, 192, 5, padding=2), nn.BatchNorm1d(192), nn.ReLU(inplace=True),
            nn.Conv1d(192, 192, 5, padding=2), nn.BatchNorm1d(192), nn.ReLU(inplace=True),
            nn.Dropout(0.1),
            nn.Conv1d(192, n_classes, 1),
        )

    def forward(self, x):  # x: (N,1,40,192)
        f = self.collapse(self.body(x)).squeeze(2)  # (N,192,T)
        return self.seq(f)  # (N,C,T) logits


def greedy_decode(logits):
    ids = logits.argmax(1).cpu().numpy()  # (N,T)
    out = []
    for row in ids:
        s, prev = [], -1
        for i in row:
            if i != prev and i != BLANK:
                s.append(CHARS[i - 1])
            prev = i
        out.append("".join(s))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--epochs", type=int, default=8)
    ap.add_argument("--data", default="train/data/train.npz")
    ap.add_argument("--val", default="train/data/val.npz")
    ap.add_argument("--bs", type=int, default=64)
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--real", default=None)
    ap.add_argument("--out", default="models/plate_ocr.onnx")
    ap.add_argument("--ckpt", default="train/plate_ocr.pt")
    ap.add_argument("--resume", action="store_true")
    a = ap.parse_args()

    torch.set_num_threads(os.cpu_count())
    model = PlateOCR(len(CHARS) + 1)
    if a.resume and os.path.exists(a.ckpt):
        model.load_state_dict(torch.load(a.ckpt))
        print("resumed", a.ckpt)
    d = np.load(a.data)
    X, L = d["X"], list(d["L"])
    if a.real:  # 실제 번호판 이미지 (파일명 = 정답) 를 3배 가중으로 추가
        RX, RL = [], []
        for f in glob.glob(os.path.join(a.real, "*")):
            lab = os.path.splitext(os.path.basename(f))[0].split("_")[0]
            img = cv2.imread(f)
            if img is not None and all(c in C2I for c in lab):
                RX.append((prep_plate(img)[0] * 255).astype(np.uint8)); RL.append(lab)
        if RX:
            X = np.concatenate([X] + [np.stack(RX)] * 3); L += RL * 3
            print("real samples", len(RX))
    v = np.load(a.val)
    VX = torch.from_numpy(v["X"].astype(np.float32)[:, None] / 255); VL = list(v["L"])
    steps_per_epoch = len(X) // a.bs
    total = steps_per_epoch * a.epochs
    opt = torch.optim.AdamW(model.parameters(), lr=a.lr, weight_decay=1e-4)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, a.lr, total_steps=total, pct_start=0.08)
    ctc = nn.CTCLoss(blank=BLANK, zero_infinity=True)
    t0 = time.time(); step = 0
    for ep in range(a.epochs):
        model.train()
        perm = np.random.permutation(len(X))
        for bi in range(steps_per_epoch):
            idx = perm[bi * a.bs:(bi + 1) * a.bs]
            x = torch.from_numpy(X[idx].astype(np.float32)[:, None] / 255)
            # 가벼운 온라인 증강: 밝기/대비 반전 일부
            if random.random() < 0.1:
                x = 1 - x
            ys = [torch.tensor([C2I[c] for c in L[i]]) for i in idx]
            y = torch.cat(ys); yl = torch.tensor([len(t) for t in ys])
            logits = model(x)
            lp = logits.permute(2, 0, 1).log_softmax(2)
            loss = ctc(lp, y, torch.full((x.size(0),), lp.size(0), dtype=torch.long), yl)
            opt.zero_grad(); loss.backward()
            nn.utils.clip_grad_norm_(model.parameters(), 5.0)
            opt.step(); sched.step(); step += 1
            if step % 200 == 0:
                print(f"ep {ep} step {step}/{total} loss {loss.item():.3f} {time.time() - t0:.0f}s", flush=True)
        model.eval()
        with torch.no_grad():
            pred = []
            for k in range(0, len(VX), 256):
                pred += greedy_decode(model(VX[k:k + 256]))
        acc = np.mean([p == t for p, t in zip(pred, VL)])
        print(f"== epoch {ep} val_plate_acc {acc:.4f}", flush=True)
        torch.save(model.state_dict(), a.ckpt)

    model.eval()
    os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
    dummy = torch.zeros(1, 1, OCR_H, OCR_W)
    torch.onnx.export(model, dummy, a.out, input_names=["image"], output_names=["logits"],
                      dynamic_axes={"image": {0: "batch"}, "logits": {0: "batch"}}, opset_version=17,
                      dynamo=False)
    with open(os.path.splitext(a.out)[0] + "_chars.json", "w", encoding="utf-8") as f:
        json.dump({"blank": BLANK, "chars": CHARS, "input": [1, OCR_H, OCR_W]}, f, ensure_ascii=False)
    print("exported", a.out)


if __name__ == "__main__":
    main()
