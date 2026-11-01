# Stillnote 웹 계정(회원가입·로그인·회원 탈퇴) 개발 안내

상태: **2026-11-01 Firebase Web 앱 등록과 설정 연결 완료. 실제 사용자 생명주기 전체 검증은 남아 있으며 공개·배포하지 않았다.** 기존 Android 0.1.0 Google Play 심사 버전은 변경하지 않는다.

## 구성 및 URL

- 기존 웹 메모 앱: dist/index.html. 계정 관리 링크가 dist/account/로 연결된다. 메모 저장 키 simple-notes-v1, 저장 데이터, JSON 백업, 브라우저 셰이더 동작은 기존과 동일하다.
- 모바일웹 계정 UI: dist/account/index.html. 회원가입, 로그인, 비밀번호 재설정, 이메일 확인, 로그아웃, 회원 탈퇴.
- GitHub Pages용 계정 UI: docs/account/index.html. 같은 화면과 동일한 Firebase 웹 인증 클라이언트. 실제 게시 시 사이트 경로 /stillnote/account/.
- Google Play 외부 계정 삭제 경로: docs/delete-account/index.html. 삭제 이유·데이터 범위를 보여주고 /stillnote/account/?mode=delete로 연결한다. 이 페이지는 현재 게시되지 않았으며 Play Console에 등록되지 않았다.
- 두 계정 페이지의 HTML, JS, 설정 템플릿은 정적 검사로 동일성을 확인한다. 브라우저가 **같은 origin**으로 열면 Firebase Auth 로그인 상태를 공유할 수 있으나, 다른 origin에서는 같은 Firebase 사용자여도 각각 별도 로그인이 필요하다.

## Firebase 웹 앱 등록과 연결

1. Firebase Console → 기존 프로젝트 stilenote → 프로젝트 설정 → 앱 추가 → **웹 앱** 등록. Android/iOS와 동일 Firebase Authentication 사용자 풀을 사용한다.
2. 등록 결과의 **apiKey, authDomain, projectId, appId**를 확인한다. 이 정보는 웹 앱의 공개 식별 설정이며 Admin SDK 비밀 키나 사용자의 비밀번호와 다르다.
3. docs/account/config.js 및 dist/account/config.js에 **같은 Web 앱 설정값**을 반영했다. Stillnote Web의 appId는 `1:218352371308:web:eb4af67b7577182268ba49`, projectId는 `stilenote`, authDomain은 `stilenote.firebaseapp.com`이다. Authentication만 초기화하며 Console 예제의 Analytics는 추가하지 않았다.
4. Firebase Console Authentication → Settings → Authorized domains에서 **실제 공개할 웹 호스트**를 허용/확인한다. API 키에 HTTP referrer/API 제한이 있다면 배포 도메인 및 Authentication API와 일치하는지 확인한다.
5. 이메일/비밀번호 로그인 제공업체 설정을 유지한다. 이메일 확인·재설정 메일의 발신자/도메인/템플릿을 점검한다.
6. 실계정에 영향을 주지 않는 **테스트 사용자**를 사용하여 웹 회원가입 → 확인 메일 → 로그아웃 → 로그인 → 비밀번호 재설정 → 현재 비밀번호 재인증 → 삭제 → 재로그인 실패를 양쪽 웹 호스트에서 확인한다. Android/iOS 계정과 실제 동일한 Firebase UID가 확인되는지 점검한다.
7. 가입/로그인 상태 및 비밀번호를 로그·저장소·CI 결과에 기록하지 않는다. Firebase Web SDK는 자체적으로 인증 지속성을 저장할 수 있다.

## 계정 삭제 동작

웹에서 로그인한 상태 → '계정 삭제(회원 탈퇴)' 선택 → 현재 비밀번호와 영구 삭제 동의 확인 → Firebase reauthenticateWithCredential → 인증 전후 동일 UID 확인 → Firebase deleteUser → 로그인 상태 해제. 로컬 메모 및 사용자가 별도 보관한 JSON 백업을 삭제하지 않는다.

웹에서 로그아웃 상태인 사용자는 **먼저 웹에서 로그인**한다. 네이티브 앱의 로그인 상태가 다른 웹 origin으로 자동 전달되지는 않는다. Firebase 프로젝트의 사용자 계정 자체는 하나이므로 웹에서 삭제하면 Android/iOS 앱도 그 계정으로 다시 정상 로그인할 수 없다.

사용자가 비밀번호를 모르는 경우 웹의 재설정 기능을 통해 복구를 시도할 수 있다. 등록 이메일에 접근할 수 없는 경우에는 지원 이메일의 **운영자 본인확인 및 수동 요청 절차**가 필요하며, 단순히 입력한 주소를 신뢰하여 Firebase 관리자 권한으로 계정을 삭제하면 안 된다. 이 절차는 아직 운영되지 않는다.

현재 코드에는 별도의 서버 회원 탈퇴 API, Firebase Admin SDK, Cloudflare 계정 연동, 메모 동기화·자동 업로드가 없다.

## 출시 전 승인·차단 사항

- 현재 Google Play 심사 중 Android 0.1.0과 main의 기존 개인정보처리방침은 변경하지 않는다.
- 후속 버전에 로그인 기능이 제공되기 전 웹·Android·iOS 개인정보처리방침, 국외 처리, Play 데이터 보안 신고 및 계정 삭제 URL, Apple App Privacy를 실제 데이터 흐름에 맞춰 확정한다.
- 웹 계정 생성이 가능한 서비스의 공개 시점이 모바일 인증 심사 시점과 달라지면, 사용자-facing 개인정보 고지부터 먼저 맞춘다.
- GitHub Pages는 main 반영 및 별도 승인 전까지 현재 공개 페이지를 변경하지 않는다.
- 실서비스 인증·계정 삭제 전 과정 및 외부 삭제 요청의 실효성 검증 없이 운영 준비 완료라고 표현하지 않는다.

## 테스트

2026-11-01 Console 확인: Spark 무료 유지. 웹 앱 등록 시 Hosting 설정 미선택. 이메일/비밀번호 제공업체 활성화 상태를 유지했다. Authorized domains는 `localhost`, `stilenote.firebaseapp.com`, `stilenote.web.app`이며 추가 변경하지 않았다. 향후 Pages 공개 호스트 `dla3946gns.github.io`는 공개 승인 후 별도 확인·등록한다.

로컬 Chrome 390×844 모바일 뷰에서 Firebase Web SDK 13.1.0 초기화와 로그인 화면 표시, 존재하지 않는 테스트 주소의 로그인 거부 및 비밀번호 필드 초기화를 확인했다. 이는 가입·메일 수신·비밀번호 변경·재인증 삭제의 성공을 뜻하지 않는다. 수신 가능한 테스트 이메일과 사용자 직접 비밀번호 변경을 통한 전체 검증이 남아 있다. Android/iOS/Web 설정의 프로젝트 ID와 프로젝트 번호 일치는 자동 검사하며, 세 플랫폼의 실기기 동일 UID 로그인은 별도 검증이 필요하다.

- node --check dist/account/app.js
- node --test web/tests/account-rules.test.mjs
- python3 web/tests/verify-account-pages.py
- CI: .github/workflows/web-auth-validation.yml
