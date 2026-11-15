"""PC용 명령줄 실행기.

사용법:
  python recognize.py 사진.jpg                 # 결과 JSON 출력
  python recognize.py 사진.jpg --draw out.jpg  # 결과를 그린 이미지 저장
  python recognize.py --serve --port 8765      # 로컬 HTTP 서버 (같은 PC/사내망 앱 연동용, 인터넷 불필요)
"""
import argparse
import json
import sys

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

from engine.engine import VehicleEngine


def draw(bgr, result, path):
    img = Image.fromarray(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
    d = ImageDraw.Draw(img)
    try:
        import glob, os
        f = glob.glob(os.path.join(os.path.dirname(__file__), "train", "fonts", "*Bold.ttf"))[0]
        font = ImageFont.truetype(f, max(16, img.width // 50))
    except Exception:
        font = ImageFont.load_default()
    for v in result["vehicles"]:
        d.rectangle(v["box"], outline=(0, 200, 255), width=3)
        lines = []
        if v["plate"]:
            d.rectangle(v["plate"]["box"], outline=(255, 40, 40), width=3)
            lines.append(f'{v["plate"]["number"]} ({v["plate"]["confidence"]:.2f})')
        vt = v["vehicle_type"]
        lines.append(f'{vt["category"] or "?"}{" · 전기차" if vt["electric"] else ""} · {v["color"]["name"] or "?"}')
        x, y = v["box"][0], max(0, v["box"][1] - 2 * font.size - 8)
        for i, t in enumerate(lines):
            bb = d.textbbox((x, y + i * (font.size + 4)), t, font=font)
            d.rectangle(bb, fill=(0, 0, 0))
            d.text((x, y + i * (font.size + 4)), t, font=font, fill=(255, 255, 0))
    img.save(path)


def serve(port):
    from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
    eng = VehicleEngine()

    class H(BaseHTTPRequestHandler):
        def do_POST(self):
            if self.path != "/analyze":
                self.send_error(404); return
            data = self.rfile.read(int(self.headers.get("Content-Length", 0)))
            bgr = cv2.imdecode(np.frombuffer(data, np.uint8), cv2.IMREAD_COLOR)
            if bgr is None:
                self.send_error(400, "image decode failed"); return
            body = json.dumps(eng.analyze(bgr), ensure_ascii=False).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers(); self.wfile.write(body)

    print(f"POST 이미지 바이트 -> http://127.0.0.1:{port}/analyze")
    ThreadingHTTPServer(("0.0.0.0", port), H).serve_forever()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image", nargs="?")
    ap.add_argument("--draw")
    ap.add_argument("--serve", action="store_true")
    ap.add_argument("--port", type=int, default=8765)
    a = ap.parse_args()
    if a.serve:
        serve(a.port); return
    if not a.image:
        ap.print_help(); sys.exit(1)
    bgr = cv2.imdecode(np.fromfile(a.image, np.uint8), cv2.IMREAD_COLOR)  # 한글 경로 지원
    res = VehicleEngine().analyze(bgr)
    print(json.dumps(res, ensure_ascii=False, indent=2))
    if a.draw:
        draw(bgr, res, a.draw)


if __name__ == "__main__":
    main()
