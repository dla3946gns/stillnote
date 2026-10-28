# Stillnote Plus 공유 권한 서버 — 월 0원 목표

Android에서 Google Play로 구매한 Plus를 iPhone에서도 사용하고, 반대 방향도
지원하기 위한 **배포 전 초기 기반**입니다. 무료 메모는 로그인 없이 기기 내부
저장소를 계속 사용합니다. 로그인은 Plus 구매·복원 시에만 필요합니다.

## 선택한 무료 인프라

- **Cloudflare Workers Free:** 사용자 인증 및 구매 권한 조회 API.
  무료 플랜은 하루 100,000 요청 및 호출당 10ms CPU 제한.
- **Cloudflare D1 Free:** **검증된** Google/Apple 구매 기록만 저장.
  하루 읽기 5,000,000행·쓰기 100,000행·총 5GB 무료 할당량.
- **Firebase Authentication Spark:** Google/Apple 기기에서 공통 Firebase
  UID를 가진 **이메일+비밀번호 계정**. Spark는 비밀번호 기반 로그인으로 시작.
  이메일 링크 로그인은 Spark에서 하루 발송 5회로 제한되므로 초기 수단에서 제외.
  SMS·전화번호 인증은 사용하지 않습니다.
- 실제 메모, JSON 백업, 메모 본문/제목은 **서버로 전송하지 않음**.

Workers/D1 무료 한도 초과 시 해당 요청이 실패할 수 있으며, 결제 수단을
등록하거나 유료 플랜으로 업그레이드하지 않는 한 이 설계에 서버 월 기본료는
없습니다. **수익 서비스의 고가용성이나 0원 영구 보장을 의미하지는 않습니다.**
Apple 개발자 등록비·Google/Apple 인앱 결제 수수료는 별도입니다.

공식 근거:
- https://developers.cloudflare.com/workers/platform/limits/
- https://developers.cloudflare.com/d1/platform/pricing/
- https://firebase.google.com/docs/auth/limits
- https://firebase.google.com/docs/auth/admin/verify-id-tokens
- https://developer.apple.com/app-store/review/guidelines/

## TypeScript 서버 스펙

- Cloudflare Workers 진입 파일 `src/index.ts`와 인증 모듈 `src/firebase-auth.ts`를
  TypeScript **5.8.3**의 `strict: true` 타입 검사로 관리합니다.
- `npm ci` 및 `npm test` 실행 시 타입 검사, 컴파일, 기존 Node 보안 테스트를 순서대로 실행합니다.
- Wrangler는 `src/index.ts`를 번들링하며, `dist/`는 테스트용 생성물입니다.
- API 응답 및 D1 스키마는 기존과 동일합니다. TypeScript는 런타임 인증/입력 검증을 대체하지 않습니다.

## 이번 PR에 포함된 기능

- `GET /health`: 배포 진단, 구매·계정 정보 노출 없음.
- `GET /v1/entitlement`: `Authorization: Bearer <Firebase ID token>` 필수.
  Google의 Secure Token 인증서로 **JWT RSA 서명을 검사**하고, 발급자·
  대상 프로젝트·유효기간·인증시각·UID를 검증합니다. 클라이언트가 제공한
  UID를 그대로 신뢰하지 않습니다.
- 권한은 D1의 서버 검증 완료된 `verified_purchases` 행에서만 조회합니다.
  동일한 Firebase UID로 로그인하면 Android와 iOS에서 같은 권한 결과를 받습니다.
- 구매 검증·구매 기록 변경 API는 **아직 없음**. 검증되지 않은 구매로 Plus를
  부여할 수 없습니다. 모든 실제 구매 연결은 후속 PR에서 구현합니다.
- 서버 및 JWT 검증 단위 테스트. 실제 Firebase/Cloudflare 계정은 미생성,
  **실제 배포·유료 전환 없음**.

## 실제 배포 전에 필요한 계정 작업 (사용자 승인/콘솔 설정)

1. Firebase 프로젝트를 **Spark 무료**로 만들고 Authentication에서
   이메일/비밀번호 제공업체를 활성화. Android와 iOS 앱을 **같은 Firebase
   프로젝트**에 등록. 계정 생성 및 복원 화면, 개인정보처리방침·회원 탈퇴
   화면은 후속 클라이언트 PR에서 구현합니다.
2. Cloudflare 계정 **Workers Free**에서 D1 데이터베이스 하나 생성.
   `wrangler.example.toml`을 로컬 `wrangler.toml`로 복사해 D1 ID와
   Firebase 프로젝트 ID 입력. 관리용 자격 증명은 GitHub에 커밋 금지.
3. `wrangler d1 execute stillnote-entitlements --file=migrations/0001_verified_purchases.sql --remote`
   로 실제 D1 스키마 생성 후 `wrangler deploy`. 무료 플랜을 유지하고
   유료 플랜·결제 수단 등록은 사용자 승인 전 진행하지 않습니다.
4. 기기 앱에서는 로그인 시 Firebase ID 토큰을 안전하게 받고
   `GET /v1/entitlement`를 호출. 서버 장애/오프라인 시 기존 무료 메모
   기능은 영향 없이 유지. Plus 캐시는 재검증 정책과 환불 대응을 설계한 후 추가.
5. **구매 검증 및 계정 연동을 완성하기 전에는 실제 판매 금지.**

## 다음 보안 구현 (이번 PR에서 완료되지 않음)

- **Google Play Developer API** `purchases.productsv2.getproductpurchasev2`에서
  소유권·상품·구매 상태 확인 후에만 DB에 기록. 구매 토큰은 기기에서 전달되지만
  보관 시 원본이 아니라 비가역 지문만 기록. 스토어 구매 토큰/거래 ID를
  **서로 다른 Firebase UID에 중복 등록할 수 없도록** `PRIMARY KEY` 적용.
- **Apple App Store Server API**의 JWS 서명·체인·상품·앱·환경 검증.
  Apple의 `appAccountToken`으로 거래-계정 귀속 검증.
- 서버 간 알림(RTDN / App Store Server Notifications)으로 환불/취소 반영,
  중복 이벤트·재시도·네트워크 오류 테스트.
- 사용자 계정 탈퇴/삭제, 구매 이전 정책, 복원 및 오프라인 권한 캐시,
  인증 오류·권한 남용 방지(속도 제한 및 모니터링).
- iOS에서 동일한 Plus를 StoreKit 인앱 구매로도 제공해야 함.
  Apple 멀티플랫폼 정책 3.1.3(b) 참조.
- 유료 판매 전에 실제 Google/Apple 테스트 환경에서 엔드투엔드 검증.

## 테스트

```sh
cd services/entitlements
npm ci
npm test
```

이 PR은 기존 Android 메모 저장 코드·버전·정식 심사 중인 AAB와
독립적이며, CI는 테스트만 실행하고 Cloudflare에 자동 배포하지 않습니다.
