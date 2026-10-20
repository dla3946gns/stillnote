# Stillnote Android 릴리스 빌드 가이드

> 이 저장소에서는 **서명키를 생성·공개·커밋하지 않습니다.** 정식 Google Play 업로드용 AAB는 소유자가 직접 관리하는 업로드 키를 사용합니다. GitHub Actions에서 빌드하는 디버그 APK는 정식 배포용이 아닙니다.

## 현재 기본값

| 항목 | 현재 설정 |
|---|---|
| applicationId | `com.stillnotes.app` (변경하지 않음) |
| versionCode | `1` |
| versionName | `0.1.0` |
| Android minSdk | 26 |
| Android targetSdk | 37 |
| 최종 산출물 | `androidApp-release.aab` |
| Google Play 배포 | 아직 수동 승인·등록 필요 |
| iOS 출시 | Mac 사용 가능 시점까지 보류 |

버전은 [`mobile/gradle.properties`](gradle.properties)의 `stillnoteVersionCode` 및 `stillnoteVersionName`에서 관리합니다. **Google Play에 이미 사용한 versionCode는 재사용하지 마세요.** 최초 배포 이후 업데이트마다 `versionCode`를 증가시키고 리뷰 가능한 PR로 변경하세요. Google Play 최대 versionCode는 2,100,000,000입니다.

## 1. 업로드 서명키 생성 및 백업 (저장소 밖에서 수행)

Google Play 앱 서명(Play App Signing)을 사용하는 것을 권장합니다. Google Play가 최종 배포 앱을 서명하는 키와 업로드에 사용하는 키는 다릅니다. 업로드 키를 **오프라인에서 생성**해 안전한 장소에 백업하고, 키 저장소 파일·비밀번호는 채팅이나 GitHub 코드/이슈에 올리지 마세요.

예시(명령이 동작하는 컴퓨터에서 키 파일 위치를 안전한 곳으로 바꿔 사용):

```sh
keytool -genkeypair -v \
  -keystore stillnote-upload.jks \
  -alias stillnote-upload \
  -keyalg RSA -keysize 3072 -validity 10000 \
  -dname "CN=Stillnote Upload"
```

명령이 저장소 아래 파일을 생성하지 않도록 주의하세요. 비밀번호는 대화형 프롬프트로 설정하고 비밀번호 관리자에 기록합니다. 키 파일은 별도의 안전한 장소 두 곳 이상에 백업합니다. **테스트용 CI 임시 키를 Google Play에 절대 등록하면 안 됩니다.**

Play Console에서 앱 생성 후 Play App Signing 등록 시 이 **업로드 인증서**를 사용하는 순서를 확인합니다. 기존 앱을 Google Play에 올린 적이 있다면 새 키를 만드는 대신 등록된 업로드 키와 기존 서명 구성을 먼저 확인해야 합니다.

## 2. 로컬 서명 빌드

Android Studio/Gradle JDK 17과 Android SDK Platform 37.0, Build Tools 36.0.0이 설치된 환경에서, 아래 네 환경 변수를 설정합니다.

| 환경 변수 | 의미 |
|---|---|
| `STILLNOTE_UPLOAD_KEYSTORE` | 로컬 업로드 키 파일 경로 (가능하면 절대 경로) |
| `STILLNOTE_UPLOAD_STORE_PASSWORD` | 키 저장소 비밀번호 |
| `STILLNOTE_UPLOAD_KEY_ALIAS` | 업로드 키 별칭 |
| `STILLNOTE_UPLOAD_KEY_PASSWORD` | 키 별칭에 해당하는 비밀번호 |

저장소의 `mobile` 디렉터리에서 실행합니다.

```sh
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:bundleRelease
```

서명키 환경 변수가 하나라도 빠지면 릴리스 빌드는 실패하게 구성했습니다. 유효한 입력이 있을 때만 릴리스 서명 설정이 활성화됩니다. AAB 출력 위치는 `mobile/androidApp/build/outputs/bundle/release/androidApp-release.aab`입니다.

```sh
jarsigner -verify -verbose -certs androidApp/build/outputs/bundle/release/androidApp-release.aab
```

실제 키의 인증서 SHA-256 지문을 확인해 Play Console에 등록된 **업로드 인증서**와 일치하는지 검증하세요. AAB는 APK처럼 휴대전화에 직접 설치하는 파일이 아닙니다. 실제 Google Play 배포 후에는 Play App Signing 인증서와 업로드 키 인증서가 다를 수 있습니다.

## 3. GitHub Actions 수동 릴리스 빌드

[Android signed release AAB](../.github/workflows/android-release.yml) 워크플로는 **`main` 브랜치의 수동 실행(`workflow_dispatch`)에만 동작**합니다. Google Play에 자동 게시하지 않습니다.

GitHub 저장소의 **Settings → Environments**에서 `android-release` 환경을 만들고 배포 전 승인자(필요하다면 Required reviewers)를 설정하세요. 그 환경의 **Environment secrets**에 아래 네 개를 등록합니다.

| Secret | 값 |
|---|---|
| `STILLNOTE_UPLOAD_KEYSTORE_BASE64` | 업로드 키 파일 바이트를 Base64 인코딩한 단일 줄 |
| `STILLNOTE_UPLOAD_STORE_PASSWORD` | 키 저장소 비밀번호 |
| `STILLNOTE_UPLOAD_KEY_ALIAS` | 키 별칭 |
| `STILLNOTE_UPLOAD_KEY_PASSWORD` | 키 비밀번호 |

Base64는 암호화가 아닙니다. 키 원본만큼 비밀로 취급하세요. 예를 들어 Linux에서 `base64 -w 0 /safe/path/stillnote-upload.jks`, macOS에서 `base64 < /safe/path/stillnote-upload.jks | tr -d '\n'`으로 한 줄을 만들 수 있습니다. 해당 결과를 로그, 메신저, PR에 붙여넣지 마세요.

Secrets 구성을 마치면 Actions → **Android signed release AAB** → **Run workflow** → `main`을 선택합니다. 워크플로는 빌드와 테스트가 성공했을 때만 서명된 `androidApp-release.aab`와 `androidApp-release.aab.sha256`을 **`stillnote-signed-release-aab` 아티팩트**로 7일 동안 보관합니다. 사용 후 CI 실행 권한과 아티팩트 접근 권한을 확인하세요.

### PR CI의 서명 테스트와 차이

`mobile-ci.yml`은 PR마다 폐기용 임시 키를 만들어 AAB 컴파일과 서명 연결만 검증하고 **그 AAB를 업로드하지 않습니다**. 폐기용 인증서는 릴리스 키가 아닙니다. 수동 릴리스 워크플로는 별도로 등록한 소유자의 업로드 키를 사용합니다.

## 기존 설치 앱 업데이트 검증

[자동 업데이트 보존 테스트](scripts/run-upgrade-preservation-test.sh)는 GitHub PR CI에서 다음 절차를 거칩니다.

1. 이전 `main` 소스에서 `versionCode=1` 디버그 APK와 계측 테스트 APK를 빌드합니다.
2. 테스트가 실제 앱 UI에서 메모를 생성·저장하고 앱을 재실행해 확인합니다.
3. 같은 GitHub runner의 **동일한 디버그 서명키**로 새 PR의 `versionCode=2` 디버그 APK를 빌드합니다. 이 숫자 증가는 **테스트 빌드에만 적용하며 저장소 릴리스 버전은 변경하지 않습니다.**
4. Android 에뮬레이터에서 `adb install -r`로 앱을 제거하지 않고 덮어씁니다.
5. 업그레이드된 앱을 실행한 후, 메모 내부 저장 파일의 내용이 업데이트 전과 **바이트 단위로 동일한지** 비교합니다.

이것은 실제 APK 교체를 포함하는 데이터 보존 회귀 테스트지만 **Play App Signing 키로 서명한 실제 Play 스토어 업데이트 경로를 완전히 대체하지 않습니다.** 서명이 다른 기존 디버그 앱을 Play 앱으로 덮어쓰는 것도 허용되지 않습니다. 실기기 전환과 Play 내부 테스트에서 별도 확인해야 합니다.

## 스토어 등록정보 및 개인정보 고지 준비

- [한국어 스토어 등록정보 초안](PLAY_STORE_LISTING_DRAFT.md)
- [개인정보처리방침 및 운영 주체 확인·배포 전 검토](PRIVACY_POLICY_DRAFT.md)
- [앱 내부 개인정보처리방침 원문](androidApp/src/main/assets/privacy-policy-ko.txt)
- [GitHub Pages 공개용 정책 페이지](../docs/privacy/index.html)
- [웹사이트 게시와 공개 URL 검증 절차](PRIVACY_POLICY_PUBLISHING.md)

운영 주체는 **Anna Johnny Works**, 사용자 문의 이메일은 **dla3946gns@gmail.com**으로 확인되어 앱과 정책 페이지에 반영했습니다. Android 앱의 `더보기 → 개인정보처리방침`에서 **인터넷 없이 전체 정책을 확인**할 수 있도록 구현했습니다. CI는 앱 내부 정책과 웹페이지 정책의 내용 일치를 자동 검사합니다. 정책 페이지의 **실제 HTTPS 공개와 본문 일치는 GitHub Pages 배포 성공으로 확인**했습니다(2026-10-20). 이후 정책을 수정하면 동일한 검증을 다시 수행해야 합니다. Google Play Data safety 설문, 운영 주체 사업자 관련 필수 정보 및 최종 공개 정책의 법적 검토도 별도로 필요합니다.

## Google Play 등록정보·데이터 보안·이미지

- [한국어 스토어 입력값 JSON](play/listing-ko.json) — 글자 수/운영자 정보 자동 검증
- [Google Play 디자인·아이콘·실제 앱 스크린샷 생성 방법](PLAY_ASSETS.md)
- [Data safety 데이터 수집·공유·삭제 검토표](PLAY_DATA_SAFETY_REVIEW.md)

앱 런처 아이콘과 스토어 이미지는 **포실이(새싹 감자)**를 이용한 통일된 따뜻한 파스텔 디자인입니다. 메모 UI를 광고 이미지에 합성하지 않고 Android 에뮬레이터의 실제 UI에서 예시 데이터만 캡처합니다. 성공한 GitHub Actions에서는 `stillnote-play-listing-assets` 아티팩트로 이미지를 받습니다.

공개 웹사이트: https://dla3946gns.github.io/stillnote/
공개 개인정보처리방침: https://dla3946gns.github.io/stillnote/privacy/
웹 URL의 HTTPS·본문은 [Pages 실행](https://github.com/dla3946gns/stillnote/actions/runs/38173211408)에서 확인했습니다. Play Console 등록과 최종 Data safety 응답은 운영자 확인이 필요합니다.

## 2026-10-20 재개 점검 기록

- 공식 웹사이트와 개인정보처리방침의 GitHub Pages 배포가 성공했습니다: https://github.com/dla3946gns/stillnote/actions/runs/39376529660
- Google Search Console 소유권 확인 안내를 받았습니다. Play Console의 조직 전환에 필요한 D-U-N-S **9자리 번호 발급은 별개**입니다.
- Play Console에는 Stillnote 앱이 생성된 상태이지만, **정식 업로드 키 인증서·소유자 서명 AAB·내부 테스트 배포는 미확인/미완료**입니다. 실제 Play 제출·심사/공개는 하지 않았습니다.
- PR #10의 폐기용 키 AAB 사전 검증은 본문 권한·백업 기준에 합격했지만, Play 제출용 서명 결과를 대체하지 않습니다. PR #11은 정식 서명 워크플로에 동일한 정적 검증을 추가합니다.

## 2026-10-20 업로드 서명키 존재 여부 점검

- `main` Git 기록의 추적 파일 목록에서 `.jks`, `.keystore`, `.p12`, `.pfx`, `.pkcs12` 업로드 키 파일을 발견하지 못했습니다. **파일이 저장소에 없다는 사실은 로컬 또는 GitHub Secrets에 키가 없다는 증거가 아닙니다.**
- 점검 당시 GitHub Actions 전체 실행 78건 중 실제 `Android signed release AAB` 워크플로 실행 기록은 **0건**입니다. PR CI의 폐기용 서명키 및 시험 빌드는 제외합니다.
- 이 대화의 연결된 GitHub 도구로는 `android-release` Environment의 Secret **등록 이름이나 값**, 개인 컴퓨터의 키 파일, Play Console의 업로드 인증서를 직접 확인할 수 없습니다. 따라서 **기존 업로드 키 상태는 미확인**이고 신규 생성이나 교체를 진행하지 않았습니다.
- 정식 빌드 전에는 기존 키 파일·암호·별칭을 소유자의 보안 저장소에서 확인하고, Play Console의 **업로드 키 인증서** SHA-256과 실제 업로드 키의 공개 인증서 지문을 비교합니다. Google Play의 **앱 서명 키** 지문과 혼동하지 마세요.
- 값이 확인된 뒤에만 GitHub `android-release` Environment Secrets 4개를 안전하게 등록하고 수동 릴리스 워크플로를 실행합니다. 비밀값/키 파일/Base64는 이슈, PR, 채팅, 로그에 붙여넣지 마세요.
- **추가 실측 결과:** 2026-10-20에 [일회성 보호 환경 검사](https://github.com/dla3946gns/stillnote/actions/runs/39391470951)를 실행했습니다. 작업은 정상 종료했지만 필요한 업로드 서명 Secret 4개 중 **최소 하나가 없거나 해당 환경에서 접근 불가**하여 정식 서명 AAB 빌드는 요청되지 않았습니다. 어떤 키 값도 출력하지 않았습니다. 이 검사만을 위해 추가했던 자동 트리거 파일은 결과 확인 후 제거했습니다. **폐기용 CI 서명 AAB는 Play 업로드 용도로 사용하면 안 됩니다.**

## 4. Google Play 내부 테스트 전 체크

- [ ] `PLAY_STORE_LISTING_DRAFT.md`에서 설명을 검토하고 실제 스토어 등록정보로 확정
- [ ] `PRIVACY_POLICY_DRAFT.md`의 개발자 연락처·정책 내용 확정 및 공개 URL 준비
- [x] Android 앱 내 정책 전문 오프라인 열람 기능 구현 및 자동 UI 테스트 추가
- [ ] 정책 메뉴가 실제 삼성 휴대전화에서 정상 작동하는지 확인
- [x] GitHub Pages 자동 배포 워크플로 준비 및 정책 본문 일치 검증
- [x] 소유자가 Settings → Pages → Source에서 **GitHub Actions**를 선택하고 Pages 배포 성공 확인
- [x] 개인정보처리방침 공개 URL의 HTTPS와 본문 확인
- [ ] 포실이 앱 아이콘·대표 이미지·실제 화면 스크린샷 최종 검수 및 Play Console 등록정보 승인
- [ ] 개인정보처리방침 및 Play Console 데이터 보안(Data safety) 신고 항목 확인
- [ ] 콘텐츠 등급, 타깃 고객층, 개인정보 및 필수 정책 질문 완료
- [ ] 이전에 배포된 버전보다 높은 `versionCode`, 동일한 `applicationId` 확인
- [ ] `jarsigner` 검증과 업로드 인증서 지문 확인
- [ ] AAB를 **내부 테스트 트랙**에 먼저 등록한 뒤 설치 및 업데이트 동작 확인
- [ ] 시스템 파일 선택기, 백업·복원, 화면 회전, 큰 글씨 및 다수 Android 버전 QA
- [ ] 프로덕션 출시를 위한 Play Console 정책·심사/테스터 요건 확인

**데이터 보호 주의:** 이미 기기에서 사용 중인 디버그 APK는 새 업로드 키로 서명한 APK 또는 Play 앱과 인증서가 달라 덮어쓰기 설치가 불가능할 수 있습니다. **기존 앱을 삭제하기 전에 JSON 백업을 외부에 저장하고 그 파일을 복원 가능한지 확인하세요.** 앱 삭제 시 기기 내부 저장소의 메모가 사라질 수 있습니다. 릴리스 설치/업데이트 전략은 실제 Play 내부 테스트에서 따로 검증합니다.

## 범위

이 단계에서 Google Play Developer 계정 생성, 키 생성, Secret 실제 등록, AAB 실제 스토어 제출, 결제, 앱 심사나 자동 게시를 실행하지 않습니다. iOS 빌드/출시도 진행하지 않습니다.
