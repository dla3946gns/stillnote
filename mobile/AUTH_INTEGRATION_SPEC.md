# StillNote 인증 연동 스펙 및 결정·검증 기록

최종 정리: 2026-10-31. 대화의 결정과 실제 검증을 구분해 기록하는 기준 문서다.
과거에는 여러 보고서에 분산 기록했고, 이 문서에서 현재까지의 관련 내용을 소급 정리했다.
새 결정·테스트 시 이 문서를 함께 갱신한다. 대화 원문, 자격 증명, 토큰, 실제 메모는 기록하지 않는다.

## 확정 범위

- Firebase 프로젝트 `stilenote`, Spark 무료 유지. 유료 업그레이드·결제수단 등록 금지.
- Email/Password 활성화, 비밀번호 없는 이메일 링크 비활성화. 다른 제공업체 변경 없음.
- Android 등록: `com.stillnotes.app`, 닉네임 Stillnote Android.
- Google 서비스 플러그인 4.5.0, Firebase BoM 35.1.0, Auth 25.0.0 및 reCAPTCHA 18.6.1 포함.
- 기존 메모는 기기 내 저장. 자동 업로드·동기화·Cloudflare 배포·구매 연동은 이번 작업 범위 밖.
- 로그인하지 않아도 기존 메모 사용을 보존하는 방향. SDK 자동 처리까지 선택 수집이라고 단정하지 않는다.
- 로그인 UI 구현을 진행 중이며 아직 검증·출시 완료 상태가 아니다. 실제 변경 전 대상 파일·영향 범위 승인 규칙을 따른다.
- 사용자 승인(2026-10-31): Android MainActivity/StillnoteApp 및 신규 AuthScreen/AuthViewModel에서 더보기→계정, 로그인·가입·재설정·계정 상태·로그아웃·재인증 후 삭제를 구현한다. 같은 범위를 iOS에도 적용한다.
- 앞으로 모바일 기능은 Android/iOS를 함께 구현한다. 공통 도메인·검증은 KMP, Android UI는 Compose, iOS UI는 SwiftUI, Firebase SDK 호출은 각각의 얇은 네이티브 어댑터로 분리한다. 특정 타사 앱과 내부 스택이 같거나 성능이 보장된다고 주장하지 않는다.
- 공개 정책·Play 신고는 검토 초안만 준비. 실제 게시·제출·CI 허용 목록 변경은 미수행.

## 화면·도구 결정

- 토스를 디자인 참고 대상으로 선택. 크림·코코아 톤, 이메일/비밀번호 한 화면, 회원가입·재설정 경로 제안.
- 간소화 시안은 사용자가 원래 시안으로 되돌리기로 결정. 원래 설명·인사 문구를 복원하고 화면 전환·입력 포커스·버튼 눌림 애니메이션을 초안에 적용. 동작 줄이기 설정 존중.
- 설정→계정 진입은 제안 상태이며 실제 앱 탐색 변경을 승인·구현한 것으로 간주하지 않는다.
- REA 스킬 reverse-engineer-anything 설치. MCP/분석 엔진은 미연결. 토스 APK·실행 동작의 실제 REA 분석 없음. 초안은 공개 라이팅 자료를 참고한 자체 설계.
- 브라우저 초안은 실제 Firebase 인증·이메일 발송을 하지 않는다.

## 결정·검증 이력

| 날짜 | 작업/결정 | 상태 및 한계 |
|---|---|---|
| 2026-10-31 | Firebase 로그인 제공업체 설정 | 저장 후 재열람·새로고침으로 켜짐/꺼짐 확인, Spark 무료 확인 |
| 2026-10-31 | Android 앱 등록 | Play Console 및 저장소 applicationId 일치 확인 |
| 2026-10-31 | Firebase 구성 파일 확보 | Work 다운로드 경로 미확인 후 Chrome에서 확보, 식별자·JSON·TOML 검증 |
| 2026-10-31 | 로그인 초안 | 입력 버튼 활성화·로딩·오류 예시·재설정 전환 확인. 이후 텍스트 간소화→원안 복원·애니메이션 적용. 마지막 변경은 JS 구문 검사 확인, 실제 Android 검증 아님 |
| 2026-10-31 | SDK 빌드 로컬 실행 | Java 미설치로 Gradle 실행 차단. 컴파일 오류나 성공으로 판단하지 않음 |
| 2026-10-31 | CI #73, PR #25 | processDebugGoogleServices, 공통/Android 단위 테스트, assembleDebug 성공. 폐기용 키 bundleRelease 및 서명 검증 성공 |
| 2026-10-31 | CI 릴리스 개인정보 검사 | INTERNET, ACCESS_NETWORK_STATE, READ_GSERVICES 추가로 실패. 에뮬레이터 UI/업데이트 보존 작업은 선행 실패로 건너뜀 |
| 2026-10-31 | CI #74 재검증(통신 테스트 코드 추가 시 자동 실행) | 공통/Android 단위 테스트·APK·폐기용 키 AAB 성공. 동일한 권한 3개로 릴리스 개인정보 검사 실패, 기존 UI/업데이트 작업 건너뜀. 실행 41201860488 |
| 2026-10-31 | 권한 출처 조사(사용자 지정 1번) | 공식 AAR·POM 및 실제 APK 버전 대조. Auth와 reCAPTCHA가 네트워크 두 권한, reCAPTCHA가 READ_GSERVICES 선언. 실제 사용·제거 안전성 미검증 |
| 2026-10-31 | 정책 검토(사용자 승인 후 2번) | 데이터 흐름표·정책 수정안·Play 응답 초안 작성. 현재 SDK 상태와 미래 로그인 도입을 구분. SDK 초기화 통신·계정 삭제 미검증으로 게시 보류 |
| 2026-10-31 | 첫 실행 통신 조사·통합 기록 승인 | CI 전용 관찰 성공. 양성 대조 전 앱 UID 송수신 0B, 대조 요청 TX 1,218B/RX 4,070B. 아래 한계 포함 |

## 첫 실행 통신 검증 계획

- 전용 CI 에뮬레이터(API 35 google_apis x86_64), 실제 Google/Firebase 계정·개인 메모 없음.
- SDK 연결 APK 신규 설치 후 앱 실행 전 30초, 첫 실행 90초, 네트워크 차단 후 30초 관찰.
- 에뮬레이터 PCAP은 시스템 전체 통신이므로 그 자체를 앱 통신으로 단정하지 않는다. 앱 UID 소켓 스냅샷, netstats, 동일 UID의 TrafficStats 카운터를 대조한다.
- 관찰 후 계측 테스트가 자체 UID 카운터를 읽고 공개 204 응답 엔드포인트에 테스트용 HEAD 요청을 한 번 보내 탐지의 양성 대조로 쓴다. 이 구간을 일반 첫 실행과 분리한다.
- 카운터 미지원·초기화 증거 누락·양성 대조 실패를 ‘통신 없음’으로 해석하지 않는다.
- TLS 복호화·인증서 우회·실계정 로그인·유료 기능 활성화 없음. 캡처는 실제 개인정보 없는 테스트 VM에서만 생성하고 아티팩트 7일 보관.
- 관찰 창에서 0바이트여도 다른 기기·시간·인증 후 동작 및 다른 UID를 통한 처리를 배제할 수 없다. 데이터 수집 없음 신고의 단독 근거로 사용하지 않는다.

## 근거와 산출물

- [초안 PR #25](https://github.com/dla3946gns/stillnote/pull/25), SDK 커밋 c9c6ebcff3842b731e48a0d0b6e8b709b68e4467.
- [CI #73](https://github.com/dla3946gns/stillnote/actions/runs/41200396728).
- [권한 출처 보고서](review/auth/firebase-permission-audit.md), [정적 근거](review/auth/firebase-permission-evidence.json),
  [컴파일 결과](review/auth/firebase-compile-result.md), [데이터 흐름표](review/auth/stillnote-auth-data-flow-review.md),
  [정책 초안](review/auth/stillnote-privacy-policy-auth-draft.md),
  [Play 응답 초안](review/auth/stillnote-play-data-safety-auth-draft.md).
  기존 로컬 보고서의 검토용 사본이며 공개 정책·제출 답변이 아니다.
- 비밀번호·API 키 값·로그인 토큰·개인 메모·개인 로컬 경로는 이 스펙에 포함하지 않는다.

## 남은 승인·검증

첫 실행 실제 통신 관찰 → 정책 초안 보완 → 검토한 권한 기준 변경 → CI 재실행.
로그인 UI/계정 삭제·외부 요청 경로 구현과 실기기 인증 검증은 별도 후속 범위다.
PR은 초안이며 병합·Play 업로드·출시하지 않았다.

## 양 플랫폼 인증 구현 — 진행 중

- Android: MainActivity/StillnoteApp의 더보기→계정 진입, AuthScreen/AuthViewModel과 AndroidEmailAuth 추가. 기존 미저장 메모 확인을 통과한 뒤 진입하고 닫기·뒤로가기로 복귀한다.
- iOS: NotesView의 더보기→계정, AuthView/AuthModel 및 AppleEmailAuth 추가. 기존 미저장 메모 확인과 로컬 저장을 유지한다. Firebase Apple SDK 13.1.1을 Swift Package Manager로 고정한다.
- Firebase Apple 앱 등록 완료: 번들 com.stillnotes.app, 닉네임 Stillnote iOS, 앱 ID 1:218352371308:ios:cd042dca3de0094b68ba49. 구성 파일의 프로젝트·번들·앱 ID를 확인했다. App Store ID·팀 ID·배포 서명은 추가하지 않았다.
- KMP: AuthAccount/AuthProblem과 EmailAuthInput 입력 규칙. 비밀번호를 trim하거나 현재 신규가입 정책으로 기존 로그인 비밀번호를 차단하지 않는다. 비밀번호 보안 정책 최종 판정은 Firebase가 담당한다.
- 가입 완료와 이메일 확인 메일 발송 실패를 구분한다. 가입 성공 후 메일만 실패했을 때 계정 생성 실패로 안내하지 않는다. 재설정 응답은 계정 존재 여부를 드러내지 않는 문구를 사용한다.
- 계정 삭제는 현재 비밀번호로 재인증하고 동일 UID인지 확인한 뒤 실행한다. 기기 메모·백업·Plus 이용권을 삭제·변경하거나 권한을 부여하지 않는다.
- 비밀번호는 화면의 일시적 상태로만 취급하고 제출·화면 전환·닫기 시 지운다. 로그나 저장 파일·saved state에 남기지 않는다.
- 검증 계획: KMP 입력 규칙 테스트, Android 로컬 Auth emulator 가상 계정 수명주기와 메모 보존·화면 탐색 테스트, macOS에서 SwiftUI/Firebase/KMP 시뮬레이터 컴파일. 아직 결과 대기.
- 외부 계정 삭제 요청 페이지, 실제 메일 수신·실서비스 인증, 양 플랫폼 개인정보 고지·스토어 신고 확정은 미완료. 라이브 공개 정책이나 가상의 삭제 URL을 추가하지 않는다. 현 단계는 개발 PR이며 출시 보류.

## 첫 실행 관찰 결과 — 2026-10-31

- [전용 CI 실행 #1](https://github.com/dla3946gns/stillnote/actions/runs/41201860594), 테스트 커밋 `ab0fca28e393a726350530fa74c1de8e1b04cce0`.
- API 35 google_apis x86_64, 실제 계정·개인 메모 없는 신규 에뮬레이터. 앱·계측 테스트 컴파일 성공, 계측 1개 통과. FirebaseInitProvider 초기화 성공, 앱 cold launch 확인.
- 한국시간 21:40:44 실행 전 관찰, 21:41:17 첫 실행, 21:42:54 Wi-Fi/모바일 비활성화 명령, 21:43:30 양성 대조 시작. 완전한 네트워크 단절은 별도 검증하지 않았으므로 이 구간을 완전한 오프라인 테스트 통과로 주장하지 않는다.
- 앱 UID 10209, 대조 요청 전 TX/RX 모두 0B. UID 소켓 스냅샷 연결 행 0개. 앱 UID로 분류된 통신은 해당 관찰 구간에서 발견하지 못했다.
- 공개 204 엔드포인트 HEAD 양성 대조 후 TX 1,218B, RX 4,070B, HTTP 204. 측정이 양성 트래픽을 식별함을 확인했다.
- 전체 VM PCAP에는 시스템 트래픽이 존재했다. 해당 패킷·DNS 이름을 앱 또는 Firebase 인증 데이터 전송으로 귀속시키지 않았다.
- 소스상 명시적 FirebaseAuth 인스턴스 요청·인증 호출은 실행하지 않았다. TLS 내용·실기기·후속 시간대·다른 UID 위임·인증 경로는 미검증. ‘앱이 데이터를 전혀 수집하지 않는다’로 일반화하지 않는다.
- 로컬 YAML 파싱 시도는 PyYAML 미설치로 실행하지 못했다. GitHub Actions에서 워크플로 로드·컴파일·실행은 성공했다. 정적 패킷 요약은 작업 폴더의 dpkt 1.9.8로 수행했다.
- [실행 결과 보고서](review/auth/firebase-startup-network-result.md), [영구 요약 근거](review/auth/firebase-startup-summary.json). 원본 PCAP·스크린샷·UID 기록은 CI 아티팩트 13043102714에서 7일 보관하며 저장소에 올리지 않는다.

다음 작업: 로그인 구현 시 실제 인증 요청·계정 삭제를 검증하고 정책 초안을 확정한다. 이번 관찰만으로 기존 CI 권한 보호를 해제하거나 공개 정책·Play 신고를 변경하지 않는다.

결과 기록 커밋은 문서·JSON 요약만 변경하므로 `[skip ci]`로 같은 테스트를 반복하지 않는다. 실제 테스트 근거는 위 커밋과 실행에 고정하며 문서 갱신을 새 런타임 검증으로 표현하지 않는다.

## 실패한 네이티브 인증 검증 복구 — 2026-10-31

- 대상 PR: #25, 기존 검증 https://github.com/dla3946gns/stillnote/actions/runs/41204684107.
- iOS: macos-15의 Xcode가 Firebase Apple SDK 13.1.1의 Swift tools 6.2 요구를 충족하지 못해 `macos-26`의 Xcode 26.2로 고정했다. 그 후 실제 Swift 컴파일에서 `NotesModel.swift`의 `read() throws -> String?`가 nullable Objective-C 프로토콜 계약을 구현하지 못한다는 오류를 확인했다. 재검증 결과 nonthrowing 구현 역시 Objective-C의 throwing 요구사항을 충족하지 못했다. **최종 해결:** KMP `NotesStorage.read(): String`을 non-null throwing 계약으로 통일하고, **파일 없음은 항상 빈 JSON 배열 `[]`**로 나타낸다. Android·iOS 저장소 및 KMP 테스트 가짜 저장소도 같은 계약을 사용한다. 실제 파일 읽기 오류는 양쪽에서 계속 예외로 전달되어 `NotesStore.loadFailed`가 기록된 메모의 무단 덮어쓰기를 막는다. 기존 JSON 백업 형식과 저장된 메모 파일은 변경하지 않는다. Android의 파일 없음 회귀 단위 테스트를 추가했고, iOS 저장소 인터롭의 실제 시뮬레이터 재컴파일이 필요하다.
- Android: Firebase Auth 로컬 에뮬레이터 요청이 `UNAVAILABLE`을 반환했다. 원인 가설은 Android 9+의 비암호화(HTTP) 트래픽 기본 차단이며, 실제 자격 증명 없는 디버그 빌드에서만 `10.0.2.2`에 대한 cleartext 접근을 제한적으로 허용한다. **릴리스 빌드 매니페스트·네트워크 정책에는 예외를 추가하지 않는다.**
- Android UI: 작은 화면/키보드 노출 시 검증 메시지가 보이지 않는 계측 실패를 해결하기 위해 제출 시 포커스·키보드를 해제하고, 검증 메시지가 표시되면 스크롤 끝으로 이동한다.
- CI: 실패 시 민감한 인증 로그 대신 계측 결과/테스트 스택 위치만 요약하도록 진단 출력을 수정한다.
- 2차 검증: Android 인증 계정 수명주기 및 네이티브 계정 화면 테스트 성공(https://github.com/dla3946gns/stillnote/actions/runs/41205455442). iOS 빌드는 nullable+throws 충돌로 실패해 위의 non-null 계약으로 재수정했으며 **새 CI 검증 대기**다. 사용자가 입력한 실계정·토큰·메모를 테스트에 사용하지 않는다.
- Android 릴리스 개인정보 보호 검사의 신규 권한 3개 차단은 유지한다. 정책/Play 데이터 안전성 확정 및 별도 승인 전에는 해당 게이트를 완화하거나 출시하지 않는다.

## Swift 6 엄격 동시성 오류 후속 검증 — 2026-10-31

- CI https://github.com/dla3946gns/stillnote/actions/runs/41206065356에서 Android 인증 에뮬레이터 테스트는 재통과했으며, iOS는 기존 저장소 브리지 오류를 넘어 **Swift 6 동시성 오류** 6건으로 멈췄다.
- Firebase `User`는 Sendable로 입증되지 않은 SDK 참조이므로 `await user.sendEmailVerification/reload/reauthenticate/delete`로 비격리 async API에 전달하지 않는다. 같은 @MainActor에서 콜백형 Firebase SDK 메서드를 직접 호출하고 `CheckedContinuation<Void, Error>`로 완료 결과만 전달하여 기존 에러·삭제 전 재인증 검사를 유지한다. continuation에서 사용자 객체·비밀번호·토큰을 출력하거나 저장하지 않는다.
- KMP `NotesObserver`의 동기 콜백은 기존 문서상 UI 스레드 전용이다. iOS `StoreObserver`를 `@MainActor`, `@preconcurrency NotesObserver`로 선언하고 메인 스레드에서만 `NotesState`를 직접 UI 모델로 전달한다. 다른 스레드에서 호출되면 사전조건이 실패하여 데이터 경합을 방치하지 않는다.
- 이 변경에 대한 **iOS 실제 컴파일/런타임 검증은 새 CI 결과 대기**. 프로덕션 Firebase 계정이나 Play 배포는 사용하지 않았다.

## iOS Xcode 26.2 Swift IRGen 컴파일러 충돌 후속 — 2026-10-31

- [최신 인증 CI 41206631020](https://github.com/dla3946gns/stillnote/actions/runs/41206631020): Android 로컬 Firebase 인증·계정 화면과 공유 테스트 **성공**. iOS Swift 6 동시성 타입 오류를 수정한 뒤, `NotesView.swift`의 컴파일러 생성 함수에 대한 IR 생성에서 **Apple Swift 6.2.3 swift-frontend가 내부 오류/스택 덤프로 중단**됐다. 일반 소스 타입 오류는 해당 로그에 보고되지 않았다.
- Firebase iOS SDK 13.1.1이 요구하는 최소 Xcode 26.2는 유지하되, CI의 Xcode를 26.6으로 교체해 더 최신 Swift 컴파일러에서 같은 SwiftUI·Firebase·KMP 코드를 재검증한다. 동시성 타입 검사를 비활성화하거나 메모/구매 로직을 우회하지 않는다.
- 실제 결과는 후속 CI에서 검증할 것이며, **컴파일러 버전 변경만으로 빌드 성공을 미리 단정하지 않는다.** Android 개인정보 릴리스 권한 3개에 대한 별도 정책 검토 차단은 유지한다.
