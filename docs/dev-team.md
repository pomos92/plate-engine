# 개발팀 에이전트

이 저장소에는 역할별로 나눈 Claude 개발팀 에이전트가 `.claude/agents/`에 들어 있습니다.
Claude Code로 이 저장소를 열면 자동으로 불러오며, 일의 종류에 맞는 팀원에게 Claude가 알아서 일을 맡깁니다.
이름을 직접 불러서 시킬 수도 있습니다. 예: "ml-engineer 에이전트로 OCR 재학습해줘"

| 에이전트 | 역할 | 맡는 일 | 파일 수정 |
|---|---|---|---|
| `tech-lead` | 개발팀장 | 요청을 계획으로 정리하고 팀원에게 나눠 줌 | 안 함 |
| `android-dev` | 앱 개발자 | `android/` 앱 화면, 카메라·갤러리, Android 라이브러리 | 함 |
| `ml-engineer` | 인식·학습 담당 | `engine/`, `train/`, `models/`, OCR 재학습, AI Hub 데이터 | 함 |
| `qa-tester` | 테스트 담당 | 정확도 측정, 테스트 사진 결과표, Python·Kotlin 결과 비교 | 테스트만 |
| `code-reviewer` | 리뷰어 | 합치기 전 버그·오프라인 원칙·두 구현 일치 검토 | 안 함 |
| `release-manager` | 배포 담당 | GitHub Actions로 APK 빌드, 다운로드 링크·스크린샷 확인 | 거의 안 함 |

## 보통의 흐름

1. `tech-lead`가 요청을 단계로 나눔
2. `android-dev` / `ml-engineer`가 만들기
3. `qa-tester`가 확인, `code-reviewer`가 검토
4. `release-manager`가 새 APK 배포

에이전트 내용을 바꾸고 싶으면 `.claude/agents/` 안의 해당 `.md` 파일을 고치면 됩니다.
