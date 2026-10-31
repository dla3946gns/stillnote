# Stillnote 인증·개인정보 데이터 흐름 검토 (Android + iOS)

작성·갱신: 2026-11-01 / PR #25 검토 전용. 현재 Google Play 심사 중인 Android 0.1.0에는 적용하지 않음.

## 대상과 확인 수준

- 코드 기준: PR #25, 6b6093b76ef35f4e8374a8c25d693a0330382643. 실제 개발 중이며 main 미병합.
- Android: Firebase BoM 35.1.0, Firebase Authentication 25.0.0, 전이 의존성 reCAPTCHA 18.6.1. 프로젝트 stilenote, 패키지 com.stillnotes.app.
- iOS: Firebase Apple SDK 13.1.1 (Swift Package Manager, FirebaseAuth/FirebaseCore); SwiftUI 계정 UI 및 네이티브 어댑터. iOS 앱은 App Store 미출시.
- 인증 API는 AndroidEmailAuth.kt / AuthViewModel.kt와 AppleEmailAuth.swift / AuthModel.swift에 구현. 입력 검증은 KMP EmailAuthInput.
- 양 플랫폼 UI: 로그인 없이 메모 계속 사용, 계정 생성/로그인/이메일 확인/비밀번호 재설정/로그아웃/현재 비밀번호 재인증 후 Firebase 사용자 삭제. 앱 진입: 더보기 → 계정.
- Android 로컬 Firebase Auth emulator의 가상 계정 수명주기 및 iOS 시뮬레이터 컴파일 CI 통과. 실제 서비스 계정으로 양 플랫폼 실기기 전체 수명주기, 계정 삭제 사후 데이터 보존은 미검증.
- Firebase 첫 실행 신규 Android 에뮬레이터에서 앱 UID 관찰 구간 송수신 0B, 양성 대조 성공. 이것은 모든 기기·기간·인증 플로우에서 자동 수집이 없다는 증명이 아님.

## 데이터 흐름 인벤토리

| 정보 / 경로 | PR #25 코드·SDK상 상태 | 목적과 가능한 수신자 | 출시 전 판정 |
|---|---|---|---|
| 메모 제목·본문·메모 식별자·시간·고정 상태 | KMP NotesStore와 플랫폼 파일 저장소에 로컬 저장. 자동 서버 업로드·동기화 코드 없음 | 기기 내 편집·검색·백업·복원 | Firebase에 업로드된다고 신고하지 않음; 사용자 지정 외부 공유/백업은 별도 |
| 선택한 백업·공유·클립보드 | 시스템 문서 선택기, 공유 확장, 클립보드를 통해 사용자가 선택한 곳으로 전달 가능 | 지정한 문서 제공자·타 앱 | 사용자가 직접 개시한 전송. 제공자와 예외 요건 검토 |
| 이메일 주소 | 회원가입·로그인·비밀번호 재설정·확인 메일 요청에 사용 | Google Firebase Authentication | 로그인 기능 선택 시 처리됨. 앱 내부에 별도 계정 DB 구축하지 않음 |
| 비밀번호·재인증 비밀번호 | 인증 요청에 전달, 화면 입력 상태에 잠시 보관. 사용자 운영 메모/로그에 저장하는 코드 없음 | Firebase Authentication 인증 처리 | 비밀번호 재설정 및 삭제 전 재인증에도 사용. 서버의 실제 보관·삭제는 Google 자료 확인 |
| Firebase UID·계정 이메일 확인 상태 | SDK 계정 생성·인증·상태 조회 시 발생/사용 | Firebase Authentication, 기기 내 계정 UI | 계정 식별자. Cloudflare 서버로 아직 전송하지 않음 |
| 인증 세션·토큰 | Firebase SDK가 세션 유지/요청에 사용할 수 있음 | Firebase Authentication | SDK 보안·저장 세부 방식은 공급자 문서와 실기기 검증 필요 |
| IP 주소·Auth user agent·Firebase user agent·Firebase Android 앱 ID | Firebase 공식 Android 고지상 기본 처리 항목. IP는 특히 가입·인증 시 남용 방지 | Google Firebase | 앱 초기화 직후의 항상 수집으로 단정하지 않음; SDK 요청 시점과 처리 목적 구분 |
| reCAPTCHA 데이터 (IP, 앱·SDK 버전, 행동, 기기 무결성, 앱 상호작용, 일부 설치 앱, 기기 ID 등) | Android Firebase Auth의 전이 라이브러리에 포함; Google은 reCAPTCHA SDK 요청 수신 시 위 항목을 처리할 수 있다고 고지 | Google reCAPTCHA, 보안·남용방지 | 특정 인증 흐름·기기에서 언제 사용되는지/Play 각 유형을 확정하려면 추가 검증 |
| iOS Firebase 기술 정보 | FirebaseAuth/FirebaseCore 포함; Apple 개인정보 매니페스트와 실제 SDK·네트워크 흐름 별도 검토 필요 | Google Firebase | Android 고지 내용을 iOS 전체 동작의 실측 사실로 복사하지 않음 |
| 지원 이메일 문의 | 사용자가 지원 주소로 직접 보낼 때 이메일·문의 본문·첨부가 수신됨 | 운영자, 메일 제공자 | 앱 자동 수집과 별개. 보관·파기 기준 운영자 확정 필요 |
| Plus 구매·Cloudflare 서버 | 이 PR에서 실제 구매/검증/권한 API 호출 없음 | 현재 실행 흐름상 없음 | 미래 기능. 미구현 상태를 개인정보처리방침에 운영 중이라고 기재하지 않음 |

## 실제 계정 삭제 코드의 범위

1. AndroidEmailAuth.deleteAccount / AppleEmailAuth.deleteAccount: 현재 비밀번호를 검증하여 같은 Firebase UID로 재인증한 뒤 Firebase 사용자 삭제 요청을 실행.
2. 앱에서는 로컬 메모, 파일로 내보낸 백업, 다른 앱/클라우드 사본을 자동 삭제하지 않음. 계정 삭제와 앱 제거/로그아웃은 서로 다름.
3. Cloudflare D1/Plus 서버는 현재 이 PR과 연결되지 않음. 미래 서비스에 UID 연관 데이터가 추가되면 별도 삭제 정책·구현 필요.
4. Google의 서비스 로그·보안 보존 예외 및 운영자 문의 정보 삭제 범위는 개별 확인이 필요. Firebase 사용자 삭제 성공만으로 모든 원격 기록이 즉시 삭제됐다고 표현하지 않음.
5. Play 계정 삭제 관련 외부 웹 요청 경로는 아직 구현/게시되지 않음. 이메일 안내 초안만으로 웹 요건 충족이라고 주장하지 않음.

## 외부 근거

- Firebase Android Auth 데이터 고지: https://firebase.google.com/docs/android/play-data-disclosure#authentication
- Firebase 개인정보/보관 정보: https://firebase.google.com/support/privacy
- Google reCAPTCHA SDK 데이터 공개: https://docs.cloud.google.com/recaptcha/docs/disclosure-requirements-android
- Play 데이터 보안: https://support.google.com/googleplay/android-developer/answer/10787469
- Play 계정 삭제: https://support.google.com/googleplay/android-developer/answer/13327111
- Apple App Privacy: https://developer.apple.com/app-store/app-privacy-details/
- Apple 계정 삭제: https://developer.apple.com/support/offering-account-deletion-in-your-app/

## 출시 전 확인 게이트

- [ ] 최종 AAB/IPA의 SDK 및 전이 라이브러리 목록과 실제 수집/목적/수신자 확인.
- [ ] 실제 인증·비밀번호 재설정·삭제 경로의 데이터 흐름(실기기/테스트 계정) 검증. 실제 비밀번호·토큰·개인 메모는 증빙에 기록 금지.
- [ ] reCAPTCHA 실행 조건과 적용되는 Play 데이터 유형 확정. 필수/선택 처리 및 서비스 제공자 공유 예외를 실제 계약에 따라 판정.
- [ ] iOS Xcode Privacy Report/PrivacyManifest 및 Firebase iOS SDK 데이터 고지 확인.
- [ ] 외부 계정 삭제 요청 웹페이지의 본인확인·처리·결과 안내 실제 구현.
- [ ] 국외 처리/이전, 보관/파기, 문의 대응, 운영자 법적 명칭·고지 항목 운영자 및 필요시 법률 검토.
- [ ] 최종 개인정보처리방침·Android 앱 내/웹 문구·Google Play 데이터 보안·Apple App Privacy 일치 확인.
- [ ] 이 모든 게 끝나기 전 실제 출시 서명 AAB의 개인정보 보호 검사 우회/해제 금지.

이 문서는 공표용 고지가 아니라 출처와 미확인 항목을 구분한 검토 기록이다.
