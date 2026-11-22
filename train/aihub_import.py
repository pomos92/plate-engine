"""AI Hub 번호판 데이터(라벨 json + 사진)에서 번호판 부분만 잘라 '정답이름.jpg'로 저장.

사용법:
  python train/aihub_import.py <AI Hub 압축을 푼 폴더> train/data/aihub_plates [--limit 20000]
결과 폴더를 그대로 학습에 씁니다:
  python train/train_ocr.py --resume --epochs 4 --real train/data/aihub_plates ...

AI Hub 데이터마다 json 모양이 조금씩 달라서, json 안을 전부 훑으며
'번호판 번호처럼 생긴 글자'와 그 근처의 '상자 좌표'를 찾아 짝지어 씁니다.
"""
import argparse
import glob
import json
import os
import re
import sys

import cv2
import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from engine.plate_rules import parse  # noqa: E402

IMG_EXT = (".jpg", ".jpeg", ".png", ".bmp")


def _num(v):
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def _box(d):
    """dict 하나에서 상자 좌표(x0,y0,x1,y1)를 찾아본다. 여러 표기 방식 지원."""
    if not isinstance(d, dict):
        return None
    low = {k.lower(): v for k, v in d.items()}
    for k in ("bbox", "box", "plate_bbox", "points", "coordinate", "coordinates", "position", "polygon"):
        v = low.get(k)
        if isinstance(v, dict):
            b = _box(v)
            if b: return b
        if isinstance(v, list) and v:
            if all(_num(x) is not None for x in v) and len(v) == 4:
                x, y, c, r = [float(t) for t in v]
                # [x0,y0,x1,y1] 또는 [x,y,w,h]: 앞 방식이 말이 되면(오른쪽 아래가 더 크고 번호판처럼 가로로 길면) 그걸로
                if c > x and r > y and 1.2 <= (c - x) / (r - y) <= 9:
                    return (x, y, c, r)
                return (x, y, x + c, y + r)
            if all(_num(x) is not None for x in v) and len(v) >= 8 and len(v) % 2 == 0:
                xs, ys = v[0::2], v[1::2]
                return (min(xs), min(ys), max(xs), max(ys))
            if all(isinstance(p, (list, tuple)) and len(p) >= 2 for p in v):
                xs = [float(p[0]) for p in v]; ys = [float(p[1]) for p in v]
                return (min(xs), min(ys), max(xs), max(ys))
            if all(isinstance(p, dict) for p in v):
                xs = [_num(p.get("x", p.get("X"))) for p in v]; ys = [_num(p.get("y", p.get("Y"))) for p in v]
                if None not in xs and None not in ys:
                    return (min(xs), min(ys), max(xs), max(ys))
    keys = [("x", "y", "w", "h"), ("x", "y", "width", "height"), ("left", "top", "width", "height")]
    for kx, ky, kw, kh in keys:
        if all(_num(low.get(k)) is not None for k in (kx, ky, kw, kh)):
            x, y, w, h = (float(low[k]) for k in (kx, ky, kw, kh))
            return (x, y, x + w, y + h)
    for kx0, ky0, kx1, ky1 in [("x1", "y1", "x2", "y2"), ("xmin", "ymin", "xmax", "ymax"), ("x0", "y0", "x1", "y1")]:
        if all(_num(low.get(k)) is not None for k in (kx0, ky0, kx1, ky1)):
            return tuple(float(low[k]) for k in (kx0, ky0, kx1, ky1))
    return None


def _plate_text(v):
    if isinstance(v, str):
        t = re.sub(r"[\s\-·]", "", v)
        p = parse(t)
        return p["number"] if p else None
    return None


def find_pairs(node, out):
    """json 전체를 훑어 (번호, 상자) 짝을 모은다. 번호와 상자는 같은 dict 또는 부모 dict에 있다."""
    if isinstance(node, dict):
        texts = [t for t in (_plate_text(v) for v in node.values()) if t]
        if texts:
            b = _box(node)
            if b is None:
                for v in node.values():
                    b = _box(v) if isinstance(v, dict) else None
                    if b: break
            out.append((texts[0], b))
        for v in node.values():
            if isinstance(v, (dict, list)):
                find_pairs(v, out)
    elif isinstance(node, list):
        for v in node:
            find_pairs(v, out)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src"); ap.add_argument("out")
    ap.add_argument("--limit", type=int, default=0)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    images = {}
    for f in glob.glob(os.path.join(a.src, "**", "*"), recursive=True):
        if f.lower().endswith(IMG_EXT):
            images.setdefault(os.path.splitext(os.path.basename(f))[0], f)
    print("사진", len(images))
    saved = nobox = noimg = 0
    for jf in glob.glob(os.path.join(a.src, "**", "*.json"), recursive=True):
        try:
            with open(jf, encoding="utf-8-sig") as fh:
                data = json.load(fh)
        except Exception:
            continue
        pairs = []; find_pairs(data, pairs)
        if not pairs:
            continue
        stem = os.path.splitext(os.path.basename(jf))[0]
        img_path = images.get(stem)
        if img_path is None:  # json 안에 파일명이 적혀 있는 경우
            for m in re.findall(r'"([^"]+\.(?:jpg|jpeg|png|bmp))"', json.dumps(data, ensure_ascii=False), re.I):
                img_path = images.get(os.path.splitext(os.path.basename(m))[0])
                if img_path: break
        if img_path is None:
            noimg += 1; continue
        img = cv2.imdecode(np.fromfile(img_path, np.uint8), cv2.IMREAD_COLOR)
        if img is None:
            noimg += 1; continue
        H, W = img.shape[:2]
        for k, (text, b) in enumerate(pairs):
            if b is None:
                # 상자 정보가 없으면 사진 자체가 번호판만 찍힌 것으로 본다 (가로로 긴 사진만)
                if W / H < 1.8:
                    nobox += 1; continue
                crop = img
            else:
                x0, y0, x1, y1 = b
                if max(b) <= 1.5:  # 0~1 비율 좌표
                    x0, x1, y0, y1 = x0 * W, x1 * W, y0 * H, y1 * H
                w, h = x1 - x0, y1 - y0
                if w < 12 or h < 6:
                    nobox += 1; continue
                crop = img[max(0, int(y0 - 0.15 * h)):min(H, int(y1 + 0.15 * h)),
                           max(0, int(x0 - 0.06 * w)):min(W, int(x1 + 0.06 * w))]
            ok, enc = cv2.imencode(".jpg", crop, [cv2.IMWRITE_JPEG_QUALITY, 95])
            if ok:
                enc.tofile(os.path.join(a.out, f"{text}_{stem}_{k}.jpg")); saved += 1
        if a.limit and saved >= a.limit:
            break
    print(f"저장 {saved}장  (상자 없음 {nobox}, 사진 못 찾음 {noimg})  -> {a.out}")


if __name__ == "__main__":
    main()
