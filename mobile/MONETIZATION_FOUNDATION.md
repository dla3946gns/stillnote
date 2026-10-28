# Stillnote Android 수익화 기반 (첫 번째 PR)

이 PR은 **메모 무료 기능을 그대로 유지**하면서 추후 일회성 Plus 구매와
AdMob 광고를 안전하게 붙이기 위한 설계·UI 미리보기·회귀 테스트입니다.
**실제 결제, 광고 로딩, 상품 판매 또는 개인정보 전송은 하지 않습니다.**

## 현재 구현

- Android 전용 `MonetizationAccess`에서 Plus 상태를 `FREE`,
  `PURCHASE_PENDING`, `VERIFIED`로 구분합니다.
- 오직 `VERIFIED`에서만 Plus 혜택을 허용합니다. 구매 버튼이나 로컬 설정값으로
  `VERIFIED`를 임의 부여하면 안 됩니다.
- 광고 후보 위치는 **무료 사용자 메모 목록**으로 제한합니다.
  광고 SDK가 준비되고 UMP가 광고 요청을 허용할 때만 후보가 되며,
  편집 화면·키보드 표시 중·Plus 사용자에게는 광고를 표시하지 않습니다.
- Plus 혜택 설명용 Compose 다이얼로그를 추가했습니다.
  `SHOW_PLUS_PREVIEW = false`이므로 실제 메뉴에는 노출되지 않습니다.
  결제 구현 전에 이를 `true`로 바꾸거나 상품을 판매하지 마세요.
- 호스트 JVM 테스트가 기본 무료 상태·결제 대기·구매 완료·광고 노출 제한을 검증합니다.
- KMP `NotesStore`, 로컬 JSON 저장소, JSON 백업 형식, 기존 무료 UX,
  Android 권한·매니페스트·`versionCode=1`은 변경하지 않았습니다.

## 다음 PR: Google Play Billing (일회성 Plus)

1. Play Console 결제 프로필과 관리형 일회성 상품
   `stillnote_plus_lifetime` 설정. 제안 가격 ₩4,900은 정책·세금 검토 후 확정.
2. 공식 Google Play Billing Library를 도입하고 Google Play의 상품 조회,
   구매·구매 취소·결제 대기·구매 확인(acknowledgement), 복원, 재연결,
   환불·취소 시 권한 갱신을 구현.
3. 결제 소유권을 검증한 뒤에만 `VERIFIED` 상태로 전환.
   가능한 한 서버 검증을 이용하고, 서버가 없는 초기 구현의 위협 모델을 문서화.
4. Plus에서만 제공하는 **새** 기능(색상 테마·메모 템플릿)을 추가.
   기존 메모 작성·검색·고정·공유·JSON 백업/복원은 계속 무료.
5. 라이선스 테스터/내부 테스트에서 구매 성공·대기·취소·복원·재설치 검증.
6. `versionCode`는 출시된 버전보다 높게 올리고 정식 서명 AAB로 배포.

## 후속 PR: Google AdMob

1. AdMob 앱/광고 단위 생성, Google Mobile Ads SDK 및 테스트 광고 설정.
2. 필요한 UMP 동의·개인정보 설정 UI와 `canRequestAds` 연동.
3. 광고가 실제로 준비되고 정책 조건을 만족할 때만 목록 하단에 표시.
   편집 중·키보드 표시 중에는 절대 보이지 않게 계측 테스트 추가.
4. Plus 구매/복원 시 즉시 광고 제거, 구매 상태 확인 실패 시 안전한 상태 처리.
5. 개인정보처리방침과 Play Console 데이터 보안 신고 갱신,
   `app-ads.txt` 공개 도메인 루트 설정 및 실제 기기 QA 수행.
6. 광고 심사/인증이 늦어져도 결제 업데이트를 독립 배포할 수 있도록
   광고 기능 플래그 및 SDK 초기화 분리.

## 검증 방법

```sh
cd mobile
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug
```

GitHub PR CI의 에뮬레이터 UI 회귀 테스트도 확인합니다. 이 PR을
`main`에 병합해도 즉시 광고가 표시되거나 구매가 가능해지는 것은 아닙니다.
