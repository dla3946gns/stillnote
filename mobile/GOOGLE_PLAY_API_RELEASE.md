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
