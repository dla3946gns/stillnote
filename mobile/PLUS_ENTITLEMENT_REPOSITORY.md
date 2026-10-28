# KMP 공통 Plus 권한 조회 연동 — 네트워크 경계 분리

## 결론
`PlusEntitlementRepository`는 서버의 `GET /v1/entitlement` 결과를
**Android·iOS 공통 비즈니스 로직(KMP)** 에서 `PlusAccessSession`에 적용합니다.
UI·로그인 SDK·네트워크 구현은 추가하지 않았으며, 이 코드만으로 실제
서버에 접속하거나 결제 권한을 획득할 수는 없습니다.

## 코드 스펙 및 구현 흐름

- 경로: `mobile/shared/src/commonMain/kotlin/com/stillnote/shared/PlusEntitlementRepository.kt`.
- Kotlin Multiplatform 2.4.21. 기존 표준 라이브러리와 kotlinx.serialization 사용,
  **신규 Gradle 의존성 없음**.
- `PlusEntitlementTransport`는 플랫폼 전용 HTTP 클라이언트에 대한 포트입니다.
  Android와 iOS 어댑터가 **Firebase Authentication SDK에서 확보한**
  현재 사용자의 UID와 ID 토큰을 함께 전달해야 합니다.
- `refresh(firebaseUid, firebaseIdToken)`는 `CHECKING`으로 전환한 뒤
  네트워크 응답을 기존 `PlusEntitlementResponse.parse`로 검증하고,
  같은 UID·요청 번호일 때만 `FREE` 또는 `PLUS`를 반영합니다.
- 401/403/429/5xx, 연결 실패, 파싱 실패는 `TEMPORARILY_UNAVAILABLE`로 처리.
  작업 취소(`CancellationException`)는 재전파하고, 사용자가 로그아웃하면
  보유 권한을 즉시 초기화합니다.
- 사용자 메모와 JSON 백업 데이터에는 접근하지 않으며 로컬 Plus 권한을
  보관하지 않습니다. 검증 실패를 `FREE`로 잘못 처리하지 않습니다.

## 플랫폼 전용 구현 전제 조건 (후속 승인 및 작업)

1. Firebase Spark 프로젝트 생성 및 Android/iOS 인증 SDK 연결.
2. 유효한 ID 토큰 자동 갱신 및 계정 전환/로그아웃 시
   `PlusEntitlementRepository.signOut()` 호출.
3. HTTPS/TLS 고정 API 도메인, Bearer 토큰 설정, **리다이렉트 제한**,
   응답 크기 제한, 타임아웃·네트워크 예외 처리 등 플랫폼별 통신 어댑터.
4. 인증 토큰·사용자 메모를 로그·분석·URL에 포함하지 않고
   필요 최소한의 네트워크 접근 권한과 개인정보처리방침을 검토.
5. 결제 복원/환불 시 서버가 Google/Apple의 거래를 확인한 이후에만
   같은 Stillnote 계정으로 Plus 권한 공유.
6. 로그인·Plus 화면 등 UI 개발 또는 수정이 필요해지면
   AGENTS.md에 따라 **반드시 먼저 사용자 승인**을 받습니다.

현재 이 작업은 신규 UI, Android Manifest 네트워크 권한,
Firebase/Cloudflare 유료 리소스 및 실제 서버 배포를 포함하지 않습니다.

## 검증
`cd mobile && ./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug`

`PlusEntitlementRepositoryTest`는 성공·무료·401/503·네트워크 예외·
취소·로그아웃 중 늦은 응답·계정 전환·빈 인증값 등을 테스트합니다.
실제 Google/Apple 구매 검증, iOS 빌드, E2E 및 운영 서버 검증은 미실행입니다.
