# AJ Notes: Google Play Developer API 심사 제출

이 기능은 수동 GitHub Actions 실행으로 동작합니다. 코드 push/PR 또는 서명 AAB 빌드 성공만으로 자동 제출되지 않습니다.

## 출시 대상 사용자 비율: 100.0%

- API 릴리스 필드 `status: completed`를 사용하고 `userFraction`을 설정하지 않습니다.
- 이는 **프로덕션 배포 대상 사용자 비율 100%**를 의미합니다. 5%, 20% 단계적 출시는 사용하지 않습니다.
- 출시 **국가/지역**와는 다른 설정입니다. 이 워크플로는 스토어 출시 가능 국가 목록을 변경하지 않으며, Play Console에서 이미 선택한 국가/지역을 유지합니다.
- 심사 승인 및 관리형 게시 설정에 따라 실제 출시 시점은 달라질 수 있습니다.

## 계정 소유자가 최초 한 번 수행할 설정

1. Google Cloud 프로젝트에 Google Play Android Developer API를 활성화하고, AJ Notes 배포 전용 서비스 계정을 생성합니다.
2. Google Play Console의 '사용자 및 권한'에서 서비스 계정에 AJ Notes (`com.stillnotes.app`)만의 프로덕션 출시 권한을 부여합니다. 다른 앱, 결제 정보, 재무 권한은 부여하지 않습니다.
3. GitHub의 'Settings → Environments'에서 `google-play-production` 환경을 만듭니다. 가능한 경우 Required reviewers 설정을 활성화하고 승인 권한자를 제한합니다.
4. 해당 환경의 Secret 이름 `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`에 서비스 계정 JSON 키 내용을 저장합니다. 이 비밀값을 채팅, PR, 이슈, 로그에 절대 입력하지 마세요.
5. 앱의 첫 업로드는 Play Console에서 직접 수행해야 할 수 있습니다. 콘텐츠 정책, 앱 접근 권한, 데이터 보안 및 법적 동의 항목은 Console에서 완료합니다.
6. Play Console의 '게시 개요'에서 심사 중 또는 아직 심사 전송 전인 다른 변경사항을 확인합니다. API 커밋은 기존 미제출 변경사항도 함께 제출할 수 있습니다.

## AJ Notes 0.1.1 제출

[Actions → Submit AJ Notes to Google Play](https://github.com/dla3946gns/stillnote/actions/workflows/google-play-submit.yml) 에서 Run workflow → main을 선택합니다.

| 입력 | 값 |
|---|---|
| release_run_id | `43893360988` |
| expected_sha | `e2df98b66cb2905d7720d6244b4a246cb1f8cf10` |
| version_code | `2` |
| version_name | `0.1.1` |
| confirm_production | `SUBMIT_AJ_NOTES_TO_PRODUCTION` |

서명 AAB 아티팩트는 2026-11-24 00:13 KST경까지 유지됩니다. 만료되면 새로 Android signed release AAB 워크플로를 실행한 뒤 새 run ID를 입력합니다.

## 검증 및 안전장치

- 서명 빌드 워크플로 성공 상태, main 커밋 SHA, 빌드 아티팩트 체크섬, 패키지 ID 및 versionCode/versionName, 개인정보 확인용 manifest, jarsigner 서명을 검증합니다.
- 프로덕션에 `draft`, `inProgress`, `halted` 릴리스가 있으면 실패하고 중단합니다.
- 기존 변경사항이 이미 심사 중일 때 자동 취소하지 않도록 `changesInReviewBehavior=ERROR_IF_IN_REVIEW`를 지정합니다.
- 명시적인 제출 확인 문구와 환경 승인 절차를 사용합니다.
- 제출 성공은 **Google Play API edit 커밋 성공**이지, 심사 승인 및 실제 배포 완료를 뜻하지 않습니다.

## 오류 대응

- Missing GOOGLE_PLAY_SERVICE_ACCOUNT_JSON: GitHub 환경 Secret 설정이 필요합니다.
- API 403: 서비스 계정에 Play Console 앱 배포 권한이 있는지 확인합니다.
- CHANGES_ALREADY_IN_REVIEW: 기존 심사가 끝날 때까지 기다리거나 Console에서 상태를 조정한 뒤 재시도합니다.
- versionCode 중복: 기존 Play 버전 코드 사용 여부를 확인하고 버전을 다시 빌드합니다.
- 아티팩트 만료: 새로운 서명 빌드 run ID로 재시도합니다.

참고: https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks 및 https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit

## 스토어 등록정보 업데이트

[Update AJ Notes Google Play listing](https://github.com/dla3946gns/stillnote/actions/workflows/google-play-listing.yml) 워크플로로 `com.stillnotes.app`의 기본 스토어 등록정보를 업데이트합니다. AAB 빌드와 버전 선택 없이 실행할 수 있습니다.

`mobile/play/listing-ko.json`에서 앱 이름, 짧은 설명, 자세한 설명을 관리합니다. YouTube 홍보 영상은 `video`에 URL을 지정합니다. `website`, `contactEmail`, `contactPhone`은 앱 전체의 지원 연락처에 반영되므로 기본 한국어 파일에서 관리합니다. 번역을 추가할 때는 `mobile/play/listing-en-US.json`처럼 별도 파일을 만들고 `language`에 해당 Play 언어 코드를 지정합니다. 개인정보처리방침 URL, 개발자 이름, 카테고리, 데이터 보안과 콘텐츠 등급은 Play Console에서 관리합니다.

### 스토어 이미지 준비

검토한 PNG/JPEG를 `mobile/play/assets/`에 넣고 해당 언어 파일의 `images`에 `mobile/play/` 기준 경로를 지정합니다. 실제 Android 화면 캡처 후보는 기존 CI의 `stillnote-play-listing-assets` 아티팩트에서 받을 수 있습니다. [이미지 생성 안내](PLAY_ASSETS.md)

```json
{
  "images": {
    "icon": ["assets/ko-KR/icon.png"],
    "featureGraphic": ["assets/ko-KR/feature.jpg"],
    "phoneScreenshots": ["assets/ko-KR/phone-01.png", "assets/ko-KR/phone-02.png"]
  }
}
```

태블릿은 `sevenInchScreenshots`와 `tenInchScreenshots`를 지원합니다. 파일당 업로드 한도는 아이콘 1 MiB, 나머지 이미지 15 MiB입니다. 스크린샷 배열은 해당 기기 유형에 표시할 이미지의 전체 순서입니다. 기존 이미지 중 순서를 유지할 수 있는 파일은 SHA-256으로 비교해 재사용합니다. 배열에 포함하지 않은 기존 이미지는 해당 유형에서 제거되므로, 유지할 파일도 배열에 넣으세요. 갱신할 이미지 유형만 키로 지정합니다. 현재 한국어 JSON에는 이미지 경로가 따로 지정되어 있지 않습니다.

### 실행 및 승인

1. PR 또는 `Run workflow`의 기본 `validate` 모드에서 문구와 이미지 규격을 확인합니다.
2. Play Console **게시 개요**에서 심사 중인 항목과 전송 대기 항목을 확인합니다. API 커밋 시 전송 대기 중이던 다른 변경사항도 함께 심사에 제출될 수 있습니다.
3. `main`에서 `Run workflow`를 열고 `language`에 `ko-KR` 또는 등록한 언어를 선택합니다. `mode`는 `listing`(문구·연락처), `images`(이미지), `all`(둘 다) 중 선택하고 `confirm_submission`에 `SUBMIT_AJ_NOTES_STORE_LISTING`을 입력합니다. 이 확인은 선택한 등록정보의 공개 검토가 끝났다는 뜻입니다.
4. 기존 `google-play-production` 환경을 재사용합니다. 환경에 Required reviewers를 설정해 제출 작업을 승인합니다. `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`의 서비스 계정에 AJ Notes 앱의 **스토어 등록정보 관리(Manage store presence)** 권한을 부여합니다. [Play Console 권한 안내](https://support.google.com/googleplay/android-developer/answer/9844686)

워크플로는 적용할 변경사항이 있을 때 한 번의 Play edit을 검증하고 커밋합니다. 이미 심사 중인 변경사항이 있으면 커밋이 거부됩니다. 커밋에 성공하면 **게시 개요**에서 심사 및 관리형 게시 상태를 확인합니다. [Google Play edit 커밋](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit)
