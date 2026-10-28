# Android·iOS 공통 Stillnote Plus 권한 상태 모델

이 문서는 무료 메모 기능을 그대로 보존하면서 **한 번 구매한 Plus 이용권을
Android와 iPhone의 동일한 Stillnote 계정에서 공유**하는 클라이언트 설계입니다.
이 PR에서는 실제 로그인·서버 연결·결제를 구현하지 않습니다.

## 코드 스펙

- Kotlin Multiplatform 2.4.21 `commonMain`: `PlusAccessSession`,
  `PlusAccessStatus`, `PlusEntitlementResponse`.
- `SIGNED_OUT`은 로그인하지 않은 상태, `CHECKING`은 권한 확인 중,
  `FREE`와 `PLUS`는 **서버의 유효한 응답으로 구분한 결과**,
  `TEMPORARILY_UNAVAILABLE`은 서버 오류·응답 형식 오류입니다.
- 로그인 시 Firebase 인증 SDK가 획득한 UID만 `beginCheck`에 전달하고,
  인증된 토큰으로 `GET /v1/entitlement`에 요청합니다.
  응답 상태가 200이고 `entitlement`와 `hasPlus`가 서로 일치할 때만
  `PlusEntitlementResponse.parse`가 `PLUS` 또는 `FREE`를 반환합니다.
- 로그아웃·계정 변경·중복 요청 이후 도착한 **오래된 응답은 무시**합니다.
- 스토어별 구매 기록 자체는 앱에서 신뢰하지 않습니다. 향후 백엔드 구매
  검증/귀속이 끝났을 때만 공통 UID가 Plus 권한을 얻습니다.
- 메모 저장소, JSON 백업 형식, 기존 무료 메모 UI와 관련 권한은 변경하지 않습니다.

## 후속 구현

1. Firebase Spark 프로젝트를 구성하고 Android·iOS 앱을 같은 프로젝트에 등록.
   앱 자체 로그인은 Plus 구매·복원에서만 요청. 무료 메모는 로그인 없이 사용 가능.
2. Android Firebase Authentication과 iOS Firebase Authentication 로그인/로그아웃 UI 구현.
   이메일/비밀번호 정책 및 인증 실패·재설정·회원 탈퇴도 지원.
3. 앱의 인증된 ID 토큰을 TLS로 서버에 보내고 응답을 `PlusAccessSession`에 반영.
   기기 로그인 전환·인증 만료·인터넷 장애의 UI 및 네트워크 테스트.
4. Play Billing 및 StoreKit 2 구매를 서버에서 검증·귀속·취소 반영한 뒤 실제 판매.
   나중에 별도 정책을 설계할 때까지 **로컬 Plus 오프라인 캐시 없음**.
5. 매출·기기 변경·환불 테스트 완료 전에는 상품 판매/프로덕션 배포하지 않음.

## 검증

```sh
cd mobile
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug
```

주의: 이 PR은 서버에 배포하지 않고, Firebase/Cloudflare 프로젝트를
생성하지 않으며, 로그인 SDK나 INTERNET 권한도 추가하지 않습니다.
서버 비용 0원 목표 및 첫 Android 릴리스의 개인정보 데이터 범위를 유지합니다.
