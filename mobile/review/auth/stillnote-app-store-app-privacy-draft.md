# Stillnote iOS 후속 출시 — App Store Connect App Privacy 검토 초안

2026-11-01 / 미제출·미승인. Apple Developer Program 미가입, iOS App Store 출시/심사 미진행.
본 문서는 Google Play 신고 초안과 별도. 공통 개인정보처리방침은 stillnote-privacy-policy-auth-draft.md.

## iOS 구현 기준

- SwiftUI 계정 UI + Firebase Apple SDK 13.1.1 (FirebaseAuth, FirebaseCore); 이메일/비밀번호 가입/로그인/비밀번호 재설정/현재 비밀번호 재인증 후 Firebase 계정 삭제.
- 메모: KMP NotesStore와 iOS 로컬 저장소. 자동 서버 백업/계정 간 메모 동기화 코드 없음.
- 로컬 메모는 계정 삭제와 별도. 앱 내 계정 삭제를 시작할 수 있는 UI 포함. iOS 시뮬레이터 컴파일 성공은 사용자 데이터 수집 및 삭제 경로 실기기 검증과 다름.

## App Privacy 응답 준비 후보

| 정보 | 검토 내용 | 미확정 |
|---|---|---|
| 연락처 정보 > 이메일 주소 | Firebase 이메일 가입/인증 요청 및 계정 관리 | 사용자와 연결 여부, 선택·필수 범위 최종 검증 |
| 식별자 > 사용자 ID | Firebase Auth 계정 UID·세션 처리 | 공급자 계약·연계 상태, Firebase Apple 데이터 고지 확인 |
| 기타 정보/사용 데이터 | Firebase Apple SDK의 user agent, IP, 기기/앱 기술 정보 처리 가능성 | 최종 SDK 개인정보 매니페스트 및 실제 요청/보관 확인 |
| 사용자 콘텐츠 > 메모 | 앱이 로컬에 보관하고 자동으로 원격 전송하지 않음 | 사용자 주도 공유/파일 내보내기 예외 검토 |
| 데이터 사용 목적 | 앱 기능, 계정 생성·관리, 계정 보안 및 부정 사용 방지 | 공급자 고지 및 실사용 검증 |
| 추적 (Tracking) | 광고·교차 앱 추적 SDK를 이번 인증 구현에 추가하지 않음 | 최종 포함 전체 SDK 기준으로 판단. '추적 없음'을 지금 확정하지 않음 |

Apple은 앱과 포함된 타사 SDK가 처리하는 데이터 종류, 사용자 연결 여부, 추적 여부를 개발자가 App Store Connect에 신고하도록 요구한다. Firebase Android Data safety 안내를 그대로 iOS 답변으로 사용하지 않는다.

## iOS 출시 전 필수 확인

1. Apple Developer Program 가입 후 최종 빌드의 privacy manifest 및 Xcode Privacy Report 확인.
2. Firebase iOS SDK 13.1.1의 해당 버전 데이터 수집/보관 자료와 앱 실제 동작 대조.
3. 개인정보처리방침에 Apple 심사 대상 앱과 일치하는 인증·삭제·국외 처리 안내 작성.
4. 앱 내 계정 삭제와 실제 Firebase 계정 삭제/잔여 데이터 처리 검증.
5. Apple App Privacy 답변(수집 유형, 연결, 추적, 목적, 필수 여부)을 직접 검토 후 제출.

참고:
- https://developer.apple.com/app-store/app-privacy-details/
- https://developer.apple.com/support/offering-account-deletion-in-your-app/
- https://firebase.google.com/docs/ios/app-store-data-collection

현재 Android 0.1.0의 Google Play 심사 상태와 공개 개인정보처리방침은 변경하지 않는다.
