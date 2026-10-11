# Stillnote Google Play 스토어 이미지

실제 Android 앱과 동일한 **포실이(새싹 감자) / 따뜻한 파스텔** 디자인의 스토어 아이콘·대표 이미지 및 **에뮬레이터에서 직접 촬영한 예시 메모 화면**을 준비합니다.

## 디자인 규칙

- 마스코트: 포실이, 초록 새싹 2잎, 둥근 감자 실루엣, 미소/분홍 볼/진갈색 테두리
- 공식 색상: 감자 `#F7D58B`, 포인트 `#F4B94A`, 새싹 `#7DBB4F`, 볼 `#FFB3C1`, 글자 `#5B4A32`, 배경 `#FFF9EF`
- **앱 화면이 주인공**이며 포실이는 앱 아이콘과 대표 이미지 등 일부에만 사용합니다.
- 앱 내 Android 벡터 아이콘: `androidApp/src/main/res/drawable/ic_stillnote.xml`
- Play 업로드용 벡터 소스: `play/assets/stillnote-icon.svg`, `play/assets/stillnote-feature.svg`

## Play Console 이미지 규격

| 자료 | 준비 형식 | 현재 상태 |
|---|---|---|
| 스토어 앱 아이콘 | **512×512**, 32비트 PNG(RGBA), 최대 1MB | SVG 소스 및 PNG 생성 자동화 |
| 대표 그래픽 | **1024×500**, 24비트 PNG(RGB), 투명도 없음 | SVG 소스 및 PNG 생성 자동화 |
| 전화기 스크린샷 | PNG RGB, 최소 2장, 짧은 변 ≥320px, 긴 변 ≤3840px, 긴 변/짧은 변 ≤2 | 실제 Android 에뮬레이터 UI 테스트에서 4장 생성 |

스크린샷은 **실제 Android Compose UI**에서 자동 생성하는 예시 메모('오늘의 생각', '이번 주 할 일')만 사용하며, 실제 사용자의 메모는 포함하지 않습니다. 필요하면 긴 화면의 좌우 가장자리만 배경색으로 패딩하며 **가짜 앱 UI를 합성하지 않습니다**.

## 생성·확인

```sh
cd mobile
python3 -m pip install pillow==11.3.0 cairosvg==2.8.2
python3 scripts/validate-play-listing.py
python3 scripts/build-play-assets.py --output play/output
# 실기기·에뮬레이터 캡처 파일이 있으면
python3 scripts/build-play-assets.py --output play/output --screenshots androidApp/build/qa-screenshots
```

GitHub Actions **Mobile PR validation** → `Android emulator UI (light and dark)` 작업에서 이미지 생성과 업로드까지 수행합니다. 성공한 실행의 `stillnote-play-listing-assets`에서 완성된 PNG를 받습니다.

- `stillnote-play-icon-512.png`
- `stillnote-feature-1024x500.png`
- `screenshots/store-list-light.png`
- `screenshots/store-editor-light.png`
- `screenshots/store-list-dark.png`
- `screenshots/store-editor-dark.png`

## 업로드 전 최종 검토

- [ ] 실제 Android 앱 아이콘이 Play 아이콘과 맞는지 삼성 휴대전화에서 확인
- [ ] 대표 이미지의 캐릭터·텍스트와 앱 기능이 오해를 일으키지 않는지 확인
- [ ] 스크린샷이 스토어에 제출할 **최신 앱**의 실제 화면과 일치하는지 확인
- [ ] 스크린샷에 개인 데이터·테스트용 비밀번호·미공개 문서가 없는지 확인
- [ ] 운영 주체 **Anna Johnny Works**가 최종 이미지 및 상표 사용을 승인
- [ ] Google Play Console에서 최종 규격 및 현지 언어 등록정보를 확인

공식 기준: [Google Play 앱 미리보기 이미지 등록 규격](https://support.google.com/googleplay/android-developer/answer/9866151).
