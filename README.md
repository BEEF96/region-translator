# RegionTranslator v1.3 (실사용용 APK 자동 생성)

✅ 기능
- 오버레이에서 **영역 이동 + 크기 조절(모서리 핸들)**
- 선택 영역만 OCR(ML Kit) → EN→KO 온디바이스 번역(ML Kit)
- 자동 모드 / 1회 번역
- OCR 전처리(확대/그레이/대비/샤픈)

## GitHub Actions로 APK 받기(폰만)
1) 이 폴더 내용(압축 풀린 상태)을 GitHub 저장소 루트에 그대로 업로드 후 Commit
2) 저장소 **Actions** → `Build APK (signed release)` 실행 확인
3) 실행 완료 후 **Artifacts**에서 다운로드
   - `app-release-apk` (서명된 release, 실사용 권장)
   - `app-debug-apk` (디버그)

## 중요: 서명(keystore)
- 편의를 위해 `keystore/release.keystore`가 **프로젝트에 포함**되어 있습니다.
- 비밀번호: `rt-changeit`
- 나중에 Play Store 배포를 하거나 보안을 강화하려면 keystore를 새로 만들고, GitHub Secrets로 바꾸는 것을 권장합니다.
