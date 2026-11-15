"""학습 체크포인트(.pt) -> ONNX 내보내기."""
import json, os, sys
import torch
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
from train.train_ocr import PlateOCR, CHARS, BLANK
from engine.ocr_prep import OCR_H, OCR_W
ckpt, out = sys.argv[1], sys.argv[2]
m = PlateOCR(len(CHARS) + 1); m.load_state_dict(torch.load(ckpt)); m.eval()
torch.onnx.export(m, torch.zeros(1, 1, OCR_H, OCR_W), out, input_names=["image"], output_names=["logits"],
                  dynamic_axes={"image": {0: "batch"}, "logits": {0: "batch"}}, opset_version=17, dynamo=False)
json.dump({"blank": BLANK, "chars": CHARS, "input": [1, OCR_H, OCR_W]},
          open(os.path.splitext(out)[0] + "_chars.json", "w", encoding="utf-8"), ensure_ascii=False)
print("exported", out, os.path.getsize(out))
