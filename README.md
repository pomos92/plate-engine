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

## PC 폴더 자동정리 (Windows)

감시 폴더(하위 폴더 포함)에 새 사진이 생기면 번호판을 읽어 `년월일시분초_번호.jpg`로 이름을 바꾸고
백업 폴더의 같은 하위 폴더로 옮기는 창 프로그램입니다. 코드는 `pc/plate_watcher.py`, 사용법은 `pc/사용법.txt`.
Windows용 실행 파일은 GitHub Actions(`.github/workflows/pc-watcher.yml`)가 빌드해서
pre-release `pc-watcher-N`에 `PlateWatcher-win64.zip`으로 올립니다.

```bash
python pc/plate_watcher.py                          # 창으로 실행
python pc/plate_watcher.py --watch 감시폴더 --backup 백업폴더   # 창 없이 실행
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
  아래 "실제 사진으로 계속 학습시키기"를 참고하세요 (파일명이 정답, 예: `123가4567.jpg`).
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

## 실제 사진으로 계속 학습시키기

**1) 앱에서 모으기**: 결과 화면의 "번호가 틀렸나요? 고치기"로 바른 번호를 넣으면 번호판 사진이 휴대폰 안에만 저장됩니다
(첫 화면에 "학습용 사진 N장 모음" 표시). "내보내기"를 누르면 `plate_training_날짜.zip`을 드라이브·메일 등으로 공유할 수 있습니다.
zip 안의 `plates/`는 번호판만 잘라낸 사진, `vehicles/`는 차 전체 사진이고, 파일명이 정답입니다.

**2) AI Hub 데이터 넣기**: 받은 데이터 압축을 풀고 번호판 부분만 잘라냅니다.

```bash
python train/aihub_import.py AI허브_압축푼_폴더 train/data/aihub_plates
```

**3) 지금 모델 정확도 확인 → 이어서 학습 → 다시 확인**

```bash
python train/eval_real.py train/data/aihub_plates plate_training_20261122.zip
python train/train_ocr.py --resume --epochs 4 --data train/data/train.npz --val train/data/val.npz \
       --real train/data/aihub_plates plate_training_20261122.zip
python train/eval_real.py train/data/aihub_plates --show-wrong
```

실제 사진의 10%는 학습에 쓰지 않고 `real_holdout_acc`(실제 사진 정확도) 확인용으로 남겨 둡니다.
학습이 끝나면 `models/plate_ocr.onnx`만 바뀌고, 앱은 새로 빌드하면 그대로 반영됩니다.

## 라이선스 메모

- EfficientDet-Lite 모델: Apache-2.0 (Google MediaPipe)
- 학습용 글꼴: Pretendard (SIL OFL 1.1), 나눔고딕 (SIL OFL 1.1, koreanize-matplotlib 패키지 경유)

## 현재 성능

- 합성 번호판 검증셋 4,000장(기울기·원근·번호판 테두리 포함): 번호 전체 일치 99.4%
- 실제 주차장 사진에 합성 번호판을 넣은 테스트(`tests/composite_test.py`) 48건: 번호 전체 일치 47건(98%)
- **실제 번호판 사진 9장**(사용자가 앱으로 찍은 사진, 비스듬한 각도 포함): 7장 정답.
  이전 버전은 2장이었음. 틀린 2장은 화면 캡처에 앱이 그린 테두리가 번호판 글자와 겹친 사진.
  `python tests/real_screens_test.py 사진폴더` (파일명 = 정답)
- 비스듬한 번호판: 글자처럼 생긴 덩어리를 한 줄로 이어 찾고, 기울기를 바로잡아 읽음 (`char_group_candidates`)
- Android(Kotlin) 엔진도 같은 사진에서 Python과 결과가 똑같음 (`tests/kotlin_parity_real.py`)
- 처리 속도: 사진 한 장 약 0.2~0.5초 (PC CPU 4코어)
