# Firebase SDK 컴파일 확인 결과

2026-10-31 / GitHub Actions Ubuntu / Temurin Java 17 / 기존 Android SDK 설치 단계 사용

## 결론

컴파일과 단위 테스트는 성공했다. 전체 CI는 릴리스 개인정보 권한 검사에서 실패했다.
빌드 실패와 릴리스 정책 실패를 구분해야 한다.

## 확인 대상

- PR: https://github.com/dla3946gns/stillnote/pull/25 (초안, 미병합)
- 브랜치: codex/firebase-android-sdk
- 커밋: c9c6ebcff3842b731e48a0d0b6e8b709b68e4467
- 실행: https://github.com/dla3946gns/stillnote/actions/runs/41200396728 (Mobile PR validation #73)
- 작업: https://github.com/dla3946gns/stillnote/actions/runs/41200396728/job/124534899224
- SDK 설정: Firebase BoM 35.1.0 / Google 서비스 Gradle 플러그인 4.5.0 / firebase-auth 기본 모듈
- 변경 범위: 구성 파일·Gradle·SDK 연결 안내. 로그인 UI 및 메모 코드는 변경하지 않음.

## 실제 확인 결과

- `:androidApp:processDebugGoogleServices`: 실행됨. 이를 포함한 빌드 성공.
- `:shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug`: 성공, BUILD SUCCESSFUL (2분 33초).
- 테스트 보고서 및 `stillnote-android-debug-apk` 아티팩트 업로드: 성공.
- `:androidApp:bundleRelease`: 폐기용 CI 키로 성공, BUILD SUCCESSFUL (1분 5초). 후속 서명 검증 성공. 이 AAB는 CI에서 삭제하며 Play 제출용이 아님.
- 릴리스 개인정보 사전 검사: 실패. 새로 발견한 권한은 아래와 같음.
  - android.permission.ACCESS_NETWORK_STATE
  - android.permission.INTERNET
  - com.google.android.providers.gsf.permission.READ_GSERVICES
- Android 에뮬레이터 UI·앱 업데이트 시 메모 보존 검사: 선행 작업 실패로 건너뜀.

## 배포 상태와 다음 단계

기존 CI 및 릴리스의 오프라인 권한 보호 규칙을 완화하지 않았다. 유료 리소스·결제 설정·워크플로는 변경하지 않았다. PR은 초안이며 병합·Play 업로드·출시는 하지 않았다.

다음은 의존성별 권한 출처와 실제 데이터 흐름을 검토하고 개인정보처리방침·Play 데이터 보안 신고의 변경 필요성을 결정하는 일이다. 런타임 Firebase 초기화, 실제 인증·재설정 메일·실기기 동작은 아직 검증하지 않았다.

이 결과가 이전 firebase-sdk-result.md의 빌드 미확인·PR 미생성 상태를 갱신한다.
