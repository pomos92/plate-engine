# 차량 정보 인식 엔진 (오프라인 프로토타입)

사진 한 장에서 차량마다 **번호판 번호, 차종(승용/승합/트럭/특수), 전기차 여부, 차량 색상**을 뽑아 JSON으로 돌려줍니다.
인터넷 연결 없이 기기 안에서만 동작합니다.

## 구성

| 단계 | 방식 | 파일 | 크기 |
|---|---|---|---|
| 차량 찾기 | MediaPipe EfficientDet-Lite2 (COCO 학습, Apache-2.0) | `models/efficientdet_lite2_int8.tflite` | 7.5MB |
| 번호판 위치 | OpenCV 영상처리로 후보 추출 → OCR 결과로 최종 선택 | `engine/plate_locator.py` | - |
| 번호판 읽기 | 직접 학습한 CNN+CTC 모델 (합성 번호판으로 학습) | `models/plate_ocr.onnx` | 약 3MB |
| 차종 | 번호 앞자리 규칙(1순위) → 차량 모양(2순위) | `engine/plate_rules.py` | - |
| 전기차 | 파란색 번호판 판별 | `engine/vehicle_color.py` | - |
| 색상 | 차체 영역 픽셀의 색 분포 투표 (12색) | `engine/vehicle_color.py` | - |

차종 규칙 (2019년 개정 기준, 앞 3자리): 100~699 승용, 700~799 승합, 800~979 화물(트럭), 980~997 특수.
구형 2자리도 같은 구간(01~69 승용, 70~79 승합, 80~97 화물, 98~99 특수)으로 처리합니다.

## PC에서 실행

```bash
python -m venv venv && source venv/bin/activate      # Windows: venv\Scripts\activate
pip install -r requirements.txt
python recognize.py 사진.jpg --draw 결과.jpg
```

결과 예시:

```json
{
  "vehicles": [{
    "box": [66, 285, 517, 578],
    "plate": {"number": "123가4567", "valid_format": true, "confidence": 0.97,
              "plate_color": "흰색", "region": null, "usage": "자가용"},
    "vehicle_type": {"category": "승용", "source": "번호판", "electric": false, "compact_guess": false},
    "color": {"name": "검정", "confidence": 0.71}
  }]
}
```

다른 PC 프로그램에서 쓰려면 로컬 서버로 띄우고 이미지 바이트를 보내면 됩니다 (인터넷 불필요):

```bash
python recognize.py --serve --port 8765
curl --data-binary @사진.jpg http://127.0.0.1:8765/analyze
```

## Android 앱

`android/` 폴더에 오프라인 Android 라이브러리와 예제 앱이 있습니다. 사용법은 [android/README.md](android/README.md)를 보세요.

## 모바일(iOS 등)로 옮기는 방법

모델 두 개는 그대로 앱에 넣어 쓸 수 있습니다.
- 차량 검출 `.tflite`: Android/iOS 모두 **MediaPipe Tasks ObjectDetector**에 그대로 넣으면 됩니다.
- 번호판 OCR `.onnx`: **ONNX Runtime Mobile**(Android `onnxruntime-android`, iOS `onnxruntime-objc`)로 실행합니다.
  입력은 `[1,1,40,192]` 흑백 0~1, 출력은 CTC 로짓이며 글자표는 `models/plate_ocr_chars.json`에 있습니다.
- 번호판 위치 찾기와 색상 판별은 OpenCV 코드라서 앱에서는 OpenCV 모바일 SDK로 옮기거나,
  다음 단계에서 번호판 검출 모델을 학습해 모델 하나로 바꾸는 것을 권장합니다.

## 한계와 다음 단계

- 번호판 OCR은 **합성 이미지로만 학습**했습니다. 실제 사진 정확도를 올리려면 실제 번호판 사진이 필요합니다.
  `train/train_ocr.py --real 폴더`로 넣을 수 있습니다 (파일명이 정답, 예: `123가4567.jpg`).
  AI Hub의 공개 차량/번호판 데이터(무료, 회원가입 필요)를 쓰면 좋습니다.
- **경차**는 번호판에 표시가 없어 지금은 차량 박스 비율로만 약하게 추정합니다 (`compact_guess`).
  정확히 하려면 차종(모델명) 분류 모델 학습이 필요합니다.
- 차량 검출 모델의 car/truck 구분은 부정확할 수 있어서, 번호판이 읽히면 번호 규칙을 우선합니다.

## 다시 학습하기

```bash
pip install torch koreanize-matplotlib
python train/gen_dataset.py 120000 train/data/train.npz
python train/gen_dataset.py 4000 train/data/val.npz 999
python train/train_ocr.py --epochs 10 --data train/data/train.npz --val train/data/val.npz
```

## 라이선스 메모

- EfficientDet-Lite 모델: Apache-2.0 (Google MediaPipe)
- 학습용 글꼴: Pretendard (SIL OFL 1.1), 나눔고딕 (SIL OFL 1.1, koreanize-matplotlib 패키지 경유)

## 현재 성능 (합성 기준)

- 합성 번호판 검증셋 4,000장: 번호 전체 일치 99.9%
- 실제 주차장 사진의 번호판 자리에 합성 번호판을 합성한 테스트(`tests/composite_test.py`) 48건:
  번호 전체 일치 46건(96%), 번호판 색(흰/파랑/노랑) 48건 모두 일치
- 처리 속도: 1920×1080 사진 한 장(차량 3대) 약 0.23초 (CPU 4코어)
- **실제 번호판 사진으로는 아직 검증하지 못했습니다.** 실제 사진 몇 장을 주시면 바로 확인할 수 있습니다.
