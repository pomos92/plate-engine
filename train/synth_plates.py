"""합성 한국 번호판 이미지 생성기.

실제 번호판 사진 데이터가 없을 때 OCR 모델을 학습시키기 위한 용도.
번호판 종류: 신형 흰색(8자리/7자리), 전기차 파란색, 사업용 노란색(1줄/2줄),
구형 녹색 2줄, 흰색 2줄.
"""
import glob
import os
import random
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from engine.plate_rules import (REGIONS, PRIVATE_HANGUL, COMMERCIAL_HANGUL,  # noqa: E402
                                RENTAL_HANGUL)

FONT_DIRS = [os.path.join(os.path.dirname(__file__), "fonts")]


def find_fonts():
    fonts = []
    for d in FONT_DIRS:
        fonts += glob.glob(os.path.join(d, "*.ttf")) + glob.glob(os.path.join(d, "*.otf"))
    try:
        import koreanize_matplotlib
        fonts += glob.glob(os.path.join(os.path.dirname(koreanize_matplotlib.__file__), "**", "*.ttf"),
                           recursive=True)
    except ImportError:
        pass
    # 굵은 글꼴 위주
    bold = [f for f in fonts if any(k in os.path.basename(f).lower() for k in ("bold", "extra", "heavy", "black"))]
    return bold or fonts


FONTS = None


def _font(size):
    global FONTS
    if FONTS is None:
        FONTS = find_fonts()
        if not FONTS:
            raise RuntimeError("한글 글꼴(.ttf)이 필요합니다: train/fonts/ 에 넣어주세요")
    return ImageFont.truetype(random.choice(FONTS), size)


def rand_hangul(kind):
    if kind == "commercial":
        return random.choice(COMMERCIAL_HANGUL)
    r = random.random()
    if r < 0.85:
        return random.choice(PRIVATE_HANGUL)
    return random.choice(RENTAL_HANGUL)


def rand_digits(n):
    return "".join(random.choice("0123456789") for _ in range(n))


def rand_type_code(three):
    if three:
        r = random.random()
        if r < 0.7: return str(random.randint(100, 699))
        if r < 0.8: return str(random.randint(700, 799))
        if r < 0.97: return str(random.randint(800, 979))
        return str(random.randint(980, 997))
    r = random.random()
    if r < 0.65: return f"{random.randint(1, 69):02d}"
    if r < 0.75: return str(random.randint(70, 79))
    return str(random.randint(80, 99))


def _draw_text_fit(draw, box, text, font_size, fill, spacing=0.0):
    """box(x0,y0,x1,y1) 안에 text를 가로로 꽉 차게 그림 (글자별 간격 균등)."""
    x0, y0, x1, y1 = box
    font = _font(font_size)
    widths = [draw.textbbox((0, 0), c, font=font)[2] for c in text]
    total = sum(widths)
    gap = max(0.0, (x1 - x0 - total) / max(len(text) - 1, 1)) if len(text) > 1 else 0
    if total > (x1 - x0):  # 넘치면 글꼴 크기 줄임
        return _draw_text_fit(draw, box, text, int(font_size * (x1 - x0) / total * 0.98), fill)
    if len(text) == 1:
        x = (x0 + x1 - total) / 2
    else:
        x = x0
    for c, w in zip(text, widths):
        bb = draw.textbbox((0, 0), c, font=font)
        cy = (y0 + y1) / 2 - (bb[1] + bb[3]) / 2
        draw.text((x, cy), c, font=font, fill=fill)
        x += w + gap


COLORS = {
    "white": ((250, 250, 248), (15, 15, 15)),
    "ev": ((70, 150, 220), (10, 10, 10)),
    "yellow": ((245, 200, 30), (15, 15, 15)),
    "green": ((40, 120, 70), (245, 245, 240)),
}


def render_plate():
    """(RGB numpy 이미지, 정답 문자열, 번호판색, 2줄여부) 반환."""
    r = random.random()
    if r < 0.40:
        style, color, two = "new8", "white", False
    elif r < 0.55:
        style, color, two = "new7", "white", False
    elif r < 0.70:
        style, color, two = "new8", "ev", False
    elif r < 0.78:
        style, color, two = "new8", "yellow", False
    elif r < 0.88:
        style, color, two = "two_region", "yellow", True
    elif r < 0.94:
        style, color, two = "two_region", "green", True
    else:
        style, color, two = "two_plain", "white", True

    kind = "commercial" if color == "yellow" else "private"
    bg, fg = COLORS[color]
    bg = tuple(int(np.clip(c + random.randint(-25, 25), 0, 255)) for c in bg)
    hangul = rand_hangul(kind)
    serial = rand_digits(4)

    if not two:
        W, H = 520, 110
        img = Image.new("RGB", (W, H), bg)
        d = ImageDraw.Draw(img)
        code = rand_type_code(style == "new8")
        label = code + hangul + serial
        left = 30
        if color == "ev":
            # 그라데이션 + 왼쪽 EV 마크 자리
            arr = np.array(img).astype(np.float32)
            grad = np.linspace(1.05, 0.85, W)[None, :, None]
            img = Image.fromarray(np.clip(arr * grad, 0, 255).astype(np.uint8))
            d = ImageDraw.Draw(img)
            d.rectangle([8, 20, 40, 90], fill=(30, 60, 140))
            left = 50
        d.rectangle([2, 2, W - 3, H - 3], outline=(40, 40, 40), width=3)
        _draw_text_fit(d, (left, 10, W - 25, H - 10), label, 96, fg)
    else:
        W, H = 335, 170
        img = Image.new("RGB", (W, H), bg)
        d = ImageDraw.Draw(img)
        d.rectangle([2, 2, W - 3, H - 3], outline=(40, 40, 40), width=3)
        code = rand_type_code(False)
        region = random.choice(REGIONS) if style == "two_region" else ""
        top = region + code
        bottom = hangul + serial
        label = region + code + hangul + serial
        tw = 46 * len(top) + 30
        _draw_text_fit(d, ((W - tw) / 2, 8, (W + tw) / 2, 62), top, 52, fg)
        _draw_text_fit(d, (18, 64, W - 18, H - 8), bottom, 100, fg)
        # 볼트 구멍
        for bx in (40, W - 40):
            d.ellipse([bx - 5, 30, bx + 5, 40], fill=tuple(int(c * 0.7) for c in bg))
    return np.array(img), label, color, two


def augment(img):
    """원근 왜곡, 흐림, 노이즈, 밝기, 그림자, JPEG 압축."""
    h, w = img.shape[:2]
    # 주변 배경(범퍼·차체)을 넉넉히 붙여서, 검출 오차로 생기는 여유 공간까지 학습
    px, py = int(0.5 * h), int(0.35 * h)
    H, W = h + 2 * py, w + 2 * px
    base = np.array([random.randint(0, 255) for _ in range(3)], np.float32)
    canvas = np.empty((H, W, 3), np.float32)
    canvas[:] = base
    canvas += np.random.normal(0, random.uniform(2, 30), (H, W, 1))
    if random.random() < 0.5:  # 범퍼 경계선/그릴 같은 가로·세로 줄
        for _ in range(random.randint(1, 4)):
            c = random.randint(0, 255)
            if random.random() < 0.5:
                yy = random.randint(0, H - 1); canvas[yy:yy + random.randint(2, 8)] = c
            else:
                xx = random.choice([random.randint(0, px), random.randint(W - px, W - 1)])
                canvas[:, xx:xx + random.randint(2, 8)] = c
    canvas = np.clip(canvas, 0, 255).astype(np.uint8)
    canvas[py:py + h, px:px + w] = img
    img = canvas
    j = 0.06
    src = np.float32([[px, py], [W - px, py], [W - px, H - py], [px, H - py]])
    dst = src + np.float32([[random.uniform(-0.025, 0.025) * w, random.uniform(-0.1, 0.1) * h] for _ in range(4)])
    M = cv2.getPerspectiveTransform(src, dst)
    img = cv2.warpPerspective(img, M, (W, H), borderMode=cv2.BORDER_REPLICATE)
    # 잘라내기: 대부분은 번호판 근처, 가끔 여유 많이 (글자는 자르지 않음)
    loose = random.random() < 0.3
    mx, my = (0.9 * px, 0.9 * py) if loose else (0.3 * px, 0.35 * py)
    x0 = int(px - random.uniform(0, mx) + random.uniform(0, 0.02 * w))
    x1 = int(W - px + random.uniform(0, mx) - random.uniform(0, 0.02 * w))
    y0 = int(py - random.uniform(0, my) + random.uniform(0, 0.06 * h))
    y1 = int(H - py + random.uniform(0, my) - random.uniform(0, 0.06 * h))
    img = img[max(0, y0):min(H, y1), max(0, x0):min(W, x1)]
    # 해상도 저하
    s = random.uniform(0.15, 1.0)
    small = cv2.resize(img, (max(8, int(img.shape[1] * s)), max(4, int(img.shape[0] * s))),
                       interpolation=cv2.INTER_AREA)
    img = cv2.resize(small, (img.shape[1], img.shape[0]))
    if random.random() < 0.5:
        k = random.choice([3, 5])
        img = cv2.GaussianBlur(img, (k, k), 0)
    if random.random() < 0.3:  # 모션 블러
        k = random.randint(3, 9)
        ker = np.zeros((k, k)); ker[k // 2] = 1.0 / k
        img = cv2.filter2D(img, -1, ker)
    img = img.astype(np.float32)
    img = img * random.uniform(0.4, 1.3) + random.uniform(-40, 40)
    if random.random() < 0.4:  # 그림자/빛 번짐
        g = np.linspace(random.uniform(0.5, 1.2), random.uniform(0.5, 1.2), img.shape[1])[None, :, None]
        img = img * g
    img += np.random.normal(0, random.uniform(0, 12), img.shape)
    img = np.clip(img, 0, 255).astype(np.uint8)
    if random.random() < 0.6:
        q = random.randint(20, 90)
        _, enc = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, q])
        img = cv2.imdecode(enc, cv2.IMREAD_COLOR)
    return img


def sample():
    rgb, label, color, two = render_plate()
    bgr = cv2.cvtColor(rgb, cv2.COLOR_RGB2BGR)
    return augment(bgr), label, color, two


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "synth_preview"
    os.makedirs(out, exist_ok=True)
    for i in range(16):
        im, lab, col, two = sample()
        cv2.imwrite(os.path.join(out, f"{i:02d}_{col}_{lab}.png"), im)
        print(lab, col, two)
