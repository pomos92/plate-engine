"""번호판 자동 정리 (PC용 폴더 감시 프로그램).

감시 폴더(하위 폴더 포함)에 새 사진이 생기면
  1) 번호판을 읽고
  2) 파일 이름을 '년월일시분초_번호' 로 바꿔서 (예: 20261123083700_67너7570.jpg)
  3) 백업 폴더의 같은 하위 폴더 위치로 옮깁니다.

실행:
  python pc/plate_watcher.py                       # 창(GUI)으로 실행
  python pc/plate_watcher.py --watch A --backup B  # 창 없이 실행 (Ctrl+C 로 종료)
  python pc/plate_watcher.py --selftest 폴더 결과.json   # 설치 확인용: 폴더의 사진을 한 번 처리
"""
import argparse
import csv
import datetime as dt
import json
import os
import queue
import shutil
import sys
import threading
import time

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
if ROOT not in sys.path:
    sys.path.insert(0, ROOT)

APP_NAME = "번호판 자동정리"
IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".bmp", ".webp", ".tif", ".tiff"}
LOG_NAME = "처리기록.csv"


# ---------------------------------------------------------------- 설정
def config_path():
    base = os.environ.get("APPDATA") or os.path.join(os.path.expanduser("~"), ".config")
    d = os.path.join(base, "PlateWatcher")
    os.makedirs(d, exist_ok=True)
    return os.path.join(d, "config.json")


DEFAULTS = {
    "watch_dir": "", "backup_dir": "",
    "separator": "_",            # 시간과 번호 사이 구분자 ("" 로 하면 2026112308370067너7570.jpg)
    "time_source": "exif",       # exif: 사진 촬영시각(없으면 파일 생성시각) / file: 항상 파일 생성시각
    "process_existing": False,   # 시작할 때 이미 폴더에 있던 사진도 처리할지
    "auto_start": False,         # 프로그램을 켜면 바로 감시 시작
    "unrecognized": "keep",      # 번호를 못 읽은 사진: keep=원래 이름으로 백업에 이동 / leave=감시 폴더에 그대로 둠
    "poll_seconds": 2.0,
}


def load_config():
    cfg = dict(DEFAULTS)
    try:
        with open(config_path(), encoding="utf-8") as f:
            cfg.update(json.load(f))
    except Exception:
        pass
    return cfg


def save_config(cfg):
    with open(config_path(), "w", encoding="utf-8") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)


# ---------------------------------------------------------------- 시간 / 이름
def photo_time(path, source="exif"):
    """사진 촬영시각(EXIF). 없으면 파일 생성시각(수정시각 중 빠른 쪽)."""
    if source == "exif":
        try:
            from PIL import Image
            with Image.open(path) as im:
                ex = im.getexif()
                v = ex.get_ifd(0x8769).get(36867) or ex.get(306)  # DateTimeOriginal, DateTime
            if v:
                return dt.datetime.strptime(str(v).strip()[:19], "%Y:%m:%d %H:%M:%S"), "촬영시각"
        except Exception:
            pass
    st = os.stat(path)
    t = min(getattr(st, "st_birthtime", st.st_mtime) or st.st_mtime, st.st_mtime)
    if os.name == "nt":
        t = min(t, st.st_ctime)  # 윈도우는 st_ctime 이 생성시각
    return dt.datetime.fromtimestamp(t), "파일시각"


def unique_path(path):
    """같은 이름이 있으면 _2, _3 ... 을 붙인다."""
    if not os.path.exists(path):
        return path
    stem, ext = os.path.splitext(path)
    n = 2
    while os.path.exists(f"{stem}_{n}{ext}"):
        n += 1
    return f"{stem}_{n}{ext}"


def pick_plate(result):
    """가장 크게 찍힌 차(검출 결과는 면적순)부터 형식이 맞는 번호판을 고른다."""
    for v in result.get("vehicles", []):
        p = v.get("plate")
        if p and p.get("valid_format"):
            return p
    return None


def is_inside(child, parent):
    try:
        child, parent = os.path.realpath(child), os.path.realpath(parent)
        return os.path.commonpath([child, parent]) == parent
    except ValueError:  # 다른 드라이브
        return False


# ---------------------------------------------------------------- 처리
class Processor:
    def __init__(self, cfg, log=print):
        self.cfg = cfg
        self.log = log
        self._engine = None

    @property
    def engine(self):
        if self._engine is None:
            self.log("인식 엔진을 불러오는 중...")
            from engine.engine import VehicleEngine
            self._engine = VehicleEngine()
            self.log("인식 엔진 준비 완료")
        return self._engine

    def analyze(self, path):
        import cv2
        import numpy as np
        data = np.fromfile(path, np.uint8)  # 한글 경로 지원
        bgr = cv2.imdecode(data, cv2.IMREAD_COLOR)
        if bgr is None:
            raise ValueError("이미지를 열 수 없음")
        return self.engine.analyze(bgr)

    def process(self, path):
        """사진 한 장 처리. 결과 dict 반환 (CSV 기록용)."""
        watch, backup = self.cfg["watch_dir"], self.cfg["backup_dir"]
        rel_dir = os.path.relpath(os.path.dirname(path), watch)
        dest_dir = os.path.normpath(os.path.join(backup, rel_dir))
        ext = os.path.splitext(path)[1].lower()
        when, tsrc = photo_time(path, self.cfg.get("time_source", "exif"))
        plate = pick_plate(self.analyze(path))
        rec = {"처리시각": dt.datetime.now().strftime("%Y-%m-%d %H:%M:%S"), "원본": path,
               "번호": plate["number"] if plate else "", "신뢰도": plate["confidence"] if plate else "",
               "시각": when.strftime("%Y-%m-%d %H:%M:%S"), "시각출처": tsrc}
        if plate:
            name = f"{when:%Y%m%d%H%M%S}{self.cfg.get('separator', '_')}{plate['number']}{ext}"
            rec["결과"] = "인식"
        else:
            rec["결과"] = "미인식"
            if self.cfg.get("unrecognized", "keep") == "leave":
                rec["이동위치"] = ""
                self.write_log(rec)
                return rec
            name = os.path.basename(path)
        os.makedirs(dest_dir, exist_ok=True)
        dest = unique_path(os.path.join(dest_dir, name))
        shutil.move(path, dest)
        rec["이동위치"] = dest
        self.write_log(rec)
        return rec

    def write_log(self, rec):
        p = os.path.join(self.cfg["backup_dir"], LOG_NAME)
        cols = ["처리시각", "결과", "번호", "신뢰도", "시각", "시각출처", "원본", "이동위치"]
        new = not os.path.exists(p)
        try:
            with open(p, "a", newline="", encoding="utf-8-sig") as f:
                w = csv.DictWriter(f, fieldnames=cols, extrasaction="ignore")
                if new:
                    w.writeheader()
                w.writerow(rec)
        except PermissionError:
            self.log("처리기록.csv 가 엑셀 등에서 열려 있어 기록을 못 했습니다 (파일 처리는 정상).")


def scan_images(root, skip=None):
    for d, dirs, files in os.walk(root):
        if skip:
            dirs[:] = [x for x in dirs if not is_inside(os.path.join(d, x), skip)]
        for f in files:
            if os.path.splitext(f)[1].lower() in IMAGE_EXTS and not f.startswith("~$"):
                yield os.path.join(d, f)


class Watcher(threading.Thread):
    """감시 폴더를 주기적으로 훑어서 새 사진을 찾는다 (네트워크 드라이브에서도 동작하도록 폴링 방식)."""

    STABLE_POLLS = 2      # 크기/수정시각이 이만큼 연속으로 같아야 '다 써진 파일'로 본다
    GIVE_UP_SEC = 120     # 열리지 않는 파일은 이 시간 동안만 다시 시도

    def __init__(self, cfg, log=print, on_result=None):
        super().__init__(daemon=True)
        self.cfg, self.log, self.on_result = cfg, log, on_result
        self.proc = Processor(cfg, log)
        self.stop_evt = threading.Event()
        self.pending = {}    # path -> (size, mtime, 같은값 횟수, 처음 본 시각)
        self.ignored = set()

    def stop(self):
        self.stop_evt.set()

    def run(self):
        watch, backup = self.cfg["watch_dir"], self.cfg["backup_dir"]
        try:
            self.proc.engine  # 미리 로드
        except Exception as e:
            self.log(f"엔진을 불러오지 못했습니다: {e}")
            return
        if not self.cfg.get("process_existing"):
            self.ignored = set(scan_images(watch, backup))
            if self.ignored:
                self.log(f"기존 사진 {len(self.ignored)}장은 그대로 두고, 지금부터 새로 생기는 사진만 처리합니다.")
        self.log(f"감시 시작: {watch}  →  백업: {backup}")
        while not self.stop_evt.is_set():
            try:
                self.tick(watch, backup)
            except Exception as e:
                self.log(f"폴더 확인 중 오류: {e}")
            self.stop_evt.wait(float(self.cfg.get("poll_seconds", 2.0)))
        self.log("감시를 멈췄습니다.")

    def tick(self, watch, backup):
        now = time.time()
        seen = set()
        for p in scan_images(watch, backup):
            seen.add(p)
            if p in self.ignored:
                continue
            try:
                st = os.stat(p)
            except OSError:
                continue
            key = (st.st_size, st.st_mtime)
            old = self.pending.get(p)
            if old is None or old[:2] != key:
                self.pending[p] = (*key, 0, old[3] if old else now)
                continue
            cnt = old[2] + 1
            self.pending[p] = (*key, cnt, old[3])
            if cnt < self.STABLE_POLLS or st.st_size == 0:
                continue
            if self.stop_evt.is_set():
                return
            try:
                rec = self.proc.process(p)
            except Exception as e:
                if isinstance(e, (PermissionError, ValueError, OSError)) and now - old[3] < self.GIVE_UP_SEC:
                    continue  # 아직 복사 중이거나 잠겨 있음: 다음에 다시
                self.log(f"처리 실패, 건너뜀: {p} ({e})")
                self.ignored.add(p)
                self.pending.pop(p, None)
                continue
            self.pending.pop(p, None)
            if rec["결과"] == "미인식" and not rec.get("이동위치"):
                self.ignored.add(p)
            self.report(rec)
        # 사라진 파일 정리
        for p in list(self.pending):
            if p not in seen:
                self.pending.pop(p)
        self.ignored &= seen

    def report(self, rec):
        if rec["결과"] == "인식":
            self.log(f"[인식] {rec['번호']}  {os.path.basename(rec['원본'])} → {rec['이동위치']}")
        elif rec.get("이동위치"):
            self.log(f"[미인식] {os.path.basename(rec['원본'])} → {rec['이동위치']} (원래 이름 유지)")
        else:
            self.log(f"[미인식] {rec['원본']} (감시 폴더에 그대로 둠)")
        if self.on_result:
            self.on_result(rec)


def check_folders(watch, backup):
    if not watch or not os.path.isdir(watch):
        return "감시할 폴더를 선택하세요."
    if not backup:
        return "백업 폴더를 선택하세요."
    if os.path.realpath(watch) == os.path.realpath(backup):
        return "감시 폴더와 백업 폴더가 같으면 안 됩니다."
    if is_inside(watch, backup):
        return "감시 폴더가 백업 폴더 안에 있으면 안 됩니다."
    return None


# ---------------------------------------------------------------- 윈도우 자동 실행
RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"


def set_windows_startup(enable):
    if os.name != "nt":
        return False
    import winreg
    with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as k:
        if enable:
            exe = sys.executable if getattr(sys, "frozen", False) else f'"{sys.executable}" "{os.path.abspath(__file__)}"'
            winreg.SetValueEx(k, "PlateWatcher", 0, winreg.REG_SZ, exe if exe.startswith('"') else f'"{exe}"')
        else:
            try:
                winreg.DeleteValue(k, "PlateWatcher")
            except FileNotFoundError:
                pass
    return True


def windows_startup_enabled():
    if os.name != "nt":
        return False
    import winreg
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as k:
            winreg.QueryValueEx(k, "PlateWatcher")
            return True
    except OSError:
        return False


# ---------------------------------------------------------------- 화면
def run_gui():
    import tkinter as tk
    from tkinter import filedialog, messagebox, ttk

    cfg = load_config()
    root = tk.Tk()
    root.title(APP_NAME)
    root.geometry("760x520")
    root.minsize(620, 420)
    msgs = queue.Queue()
    state = {"watcher": None, "ok": 0, "fail": 0}

    def log(t):
        msgs.put(f"{dt.datetime.now():%H:%M:%S}  {t}")

    frm = ttk.Frame(root, padding=12)
    frm.pack(fill="both", expand=True)
    frm.columnconfigure(1, weight=1)

    watch_v = tk.StringVar(value=cfg["watch_dir"])
    backup_v = tk.StringVar(value=cfg["backup_dir"])

    def row(r, label, var):
        ttk.Label(frm, text=label).grid(row=r, column=0, sticky="w", pady=4)
        ttk.Entry(frm, textvariable=var).grid(row=r, column=1, sticky="ew", padx=6)

        def pick():
            d = filedialog.askdirectory(initialdir=var.get() or os.path.expanduser("~"))
            if d:
                var.set(os.path.normpath(d))
        ttk.Button(frm, text="찾아보기", command=pick).grid(row=r, column=2)

    row(0, "감시할 폴더", watch_v)
    row(1, "백업(옮길) 폴더", backup_v)

    opts = ttk.Frame(frm)
    opts.grid(row=2, column=0, columnspan=3, sticky="w", pady=(6, 0))
    sep_v = tk.BooleanVar(value=cfg["separator"] == "_")
    exist_v = tk.BooleanVar(value=cfg["process_existing"])
    keep_v = tk.BooleanVar(value=cfg["unrecognized"] == "keep")
    auto_v = tk.BooleanVar(value=cfg["auto_start"])
    boot_v = tk.BooleanVar(value=windows_startup_enabled())
    exif_v = tk.BooleanVar(value=cfg["time_source"] == "exif")
    checks = [
        (exif_v, "사진 촬영시각 사용 (없으면 파일 생성시각)"),
        (sep_v, "시간과 번호 사이에 _ 넣기"),
        (keep_v, "번호를 못 읽은 사진도 원래 이름으로 백업에 옮기기"),
        (exist_v, "시작할 때 이미 있던 사진도 처리"),
        (auto_v, "프로그램을 켜면 바로 감시 시작"),
    ]
    if os.name == "nt":
        checks.append((boot_v, "윈도우 켤 때 자동 실행"))
    for i, (v, t) in enumerate(checks):
        ttk.Checkbutton(opts, text=t, variable=v).grid(row=i // 2, column=i % 2, sticky="w", padx=(0, 16))

    example_v = tk.StringVar()

    def update_example(*_):
        example_v.set(f"새 이름 예: {dt.datetime.now():%Y%m%d%H%M%S}{'_' if sep_v.get() else ''}12가3456.jpg")
    sep_v.trace_add("write", update_example)
    update_example()
    ttk.Label(frm, textvariable=example_v, foreground="#555").grid(row=3, column=0, columnspan=3, sticky="w", pady=(4, 0))

    bar = ttk.Frame(frm)
    bar.grid(row=4, column=0, columnspan=3, sticky="ew", pady=8)
    status_v = tk.StringVar(value="멈춤")
    start_b = ttk.Button(bar, text="▶ 감시 시작")
    stop_b = ttk.Button(bar, text="■ 멈춤", state="disabled")
    start_b.pack(side="left")
    stop_b.pack(side="left", padx=6)
    ttk.Button(bar, text="백업 폴더 열기", command=lambda: open_folder(backup_v.get())).pack(side="left", padx=6)
    ttk.Label(bar, textvariable=status_v).pack(side="right")

    txt = tk.Text(frm, height=14, wrap="none", state="disabled", font=("Malgun Gothic", 9) if os.name == "nt" else None)
    txt.grid(row=5, column=0, columnspan=3, sticky="nsew")
    sb = ttk.Scrollbar(frm, command=txt.yview)
    sb.grid(row=5, column=3, sticky="ns")
    txt["yscrollcommand"] = sb.set
    frm.rowconfigure(5, weight=1)

    def open_folder(p):
        if p and os.path.isdir(p):
            if os.name == "nt":
                os.startfile(p)

    def collect():
        cfg.update(watch_dir=watch_v.get().strip(), backup_dir=backup_v.get().strip(),
                   separator="_" if sep_v.get() else "", process_existing=exist_v.get(),
                   unrecognized="keep" if keep_v.get() else "leave", auto_start=auto_v.get(),
                   time_source="exif" if exif_v.get() else "file")
        save_config(cfg)
        if os.name == "nt":
            try:
                set_windows_startup(boot_v.get())
            except Exception as e:
                log(f"자동 실행 설정 실패: {e}")

    def on_result(rec):
        state["ok" if rec["결과"] == "인식" else "fail"] += 1

    def start(silent=False):
        collect()
        err = check_folders(cfg["watch_dir"], cfg["backup_dir"])
        if err:
            if not silent:
                messagebox.showwarning(APP_NAME, err)
            else:
                log(err)
            return
        os.makedirs(cfg["backup_dir"], exist_ok=True)
        w = Watcher(dict(cfg), log, on_result)
        state["watcher"] = w
        w.start()
        start_b["state"] = "disabled"
        stop_b["state"] = "normal"

    def stop():
        w = state["watcher"]
        if w:
            w.stop()
        state["watcher"] = None
        start_b["state"] = "normal"
        stop_b["state"] = "disabled"

    start_b["command"] = start
    stop_b["command"] = stop

    def pump():
        while True:
            try:
                m = msgs.get_nowait()
            except queue.Empty:
                break
            txt["state"] = "normal"
            txt.insert("end", m + "\n")
            if int(txt.index("end-1c").split(".")[0]) > 2000:
                txt.delete("1.0", "200.0")
            txt.see("end")
            txt["state"] = "disabled"
        w = state["watcher"]
        running = w is not None and w.is_alive()
        if w is not None and not running:
            stop()
        status_v.set(f"{'감시 중' if running else '멈춤'}  ·  인식 {state['ok']}장 / 미인식 {state['fail']}장")
        root.after(300, pump)

    def on_close():
        if state["watcher"] and not messagebox.askyesno(APP_NAME, "감시 중입니다. 종료할까요?"):
            return
        collect()
        stop()
        root.destroy()

    root.protocol("WM_DELETE_WINDOW", on_close)
    log("감시할 폴더와 백업 폴더를 고른 뒤 [감시 시작]을 누르세요.")
    if cfg.get("auto_start") and cfg["watch_dir"] and cfg["backup_dir"]:
        root.after(500, lambda: start(silent=True))
    pump()
    root.mainloop()


# ---------------------------------------------------------------- 진입점
def main():
    ap = argparse.ArgumentParser(description=APP_NAME)
    ap.add_argument("--watch")
    ap.add_argument("--backup")
    ap.add_argument("--existing", action="store_true", help="이미 있던 사진도 처리")
    ap.add_argument("--selftest", nargs=2, metavar=("폴더", "결과.json"))
    a = ap.parse_args()

    if a.selftest:
        # 설치 확인: 폴더의 사진을 임시 백업 폴더로 한 번 처리하고 결과를 JSON 으로 남긴다
        src, out = a.selftest
        backup = src.rstrip("\\/") + "_백업"
        cfg = dict(DEFAULTS, watch_dir=src, backup_dir=backup)
        lines = []
        p = Processor(cfg, lines.append)
        recs = []
        try:
            for f in list(scan_images(src, backup)):
                recs.append(p.process(f))
            res = {"ok": True, "results": recs, "log": lines}
        except Exception as e:
            import traceback
            res = {"ok": False, "error": repr(e), "trace": traceback.format_exc(), "log": lines}
        with open(out, "w", encoding="utf-8") as f:
            json.dump(res, f, ensure_ascii=False, indent=2)
        return

    if a.watch or a.backup:
        cfg = dict(load_config(), watch_dir=a.watch or "", backup_dir=a.backup or "", process_existing=a.existing)
        err = check_folders(cfg["watch_dir"], cfg["backup_dir"])
        if err:
            sys.exit(err)
        os.makedirs(cfg["backup_dir"], exist_ok=True)
        w = Watcher(cfg, lambda t: print(f"{dt.datetime.now():%H:%M:%S}  {t}", flush=True))
        w.start()
        try:
            while w.is_alive():
                w.join(0.5)
        except KeyboardInterrupt:
            w.stop()
            w.join()
        return

    run_gui()


if __name__ == "__main__":
    main()
