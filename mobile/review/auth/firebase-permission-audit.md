# Firebase SDK 권한 출처 검토 — 1번 결과

2026-10-31. 결론: 세 권한은 앱 소스의 수동 추가가 아니라 Firebase Auth 및 그 직접 의존성 reCAPTCHA의 매니페스트 선언으로 유입된다. 개인정보 안내 수정안 준비(2번)로 진행할 근거는 확보했으나, 실제 데이터 수집·전송 및 권한 제거 안전성은 아직 검증하지 않았다.

## 확인 대상과 방법

- PR #25의 최신 커밋 `c9c6ebcff3842b731e48a0d0b6e8b709b68e4467`.
- [CI #73](https://github.com/dla3946gns/stillnote/actions/runs/41200396728) 릴리스 사전 검사 로그가 발견한 권한 3개를 기준으로 조사했다.
- 동일 실행의 `stillnote-android-debug-apk`(아티팩트 13042441873)를 다운로드해 APK 안의 SDK 버전 속성 파일을 확인했다. firebase-auth 25.0.0, recaptcha 18.6.1, play-services-basement 18.9.0이 일치한다.
- Google Maven의 해당 버전 POM·AAR을 다운로드해 의존성 및 AndroidManifest.xml을 정적으로 검사했다. 해시와 원본 선언은 `firebase-permission-evidence.json`에 기록했다.
- Firebase Auth 25.0.0 POM은 reCAPTCHA 18.6.1을 직접 의존한다. 따라서 확인된 경로는 `androidApp → firebase-auth:25.0.0 → recaptcha:18.6.1`이다.

## 권한별 결과

| 권한 | 확인된 선언 주체 | 역할 및 판단 |
|---|---|---|
| android.permission.INTERNET | firebase-auth 25.0.0 및 recaptcha 18.6.1 | Android의 네트워크 소켓 접근 권한. 온라인 인증 요청에 필요하므로 유지 권고. |
| android.permission.ACCESS_NETWORK_STATE | firebase-auth 25.0.0 및 recaptcha 18.6.1 | 네트워크 상태 정보를 읽는 권한. SDK가 요구하는 선언이므로 유지 권고. 제거해도 인증이 안전하게 동작하는지는 시험하지 않음. |
| com.google.android.providers.gsf.permission.READ_GSERVICES | recaptcha 18.6.1 | Google Services Framework 관련 사용자 지정 권한 선언. 앱 자체 코드의 직접 사용은 발견하지 못함. 이 버전이 실제로 읽는 데이터·호출 조건·기기별 허용 수준과 제거 영향은 미확인. 필요 없다고 단정하거나 임의로 제거하지 않음. |

INTERNET과 ACCESS_NETWORK_STATE는 Android 공식 문서에서 보호 수준 normal로 명시한다. 권한 선언은 가능 범위를 나타내며 실제 전송 내용·시점·수집 여부를 증명하지 않는다. READ_GSERVICES를 전화번호·주소록·메모 접근 권한이라고 해석할 근거는 이번 조사에 없다.

firebase-common 23.0.0 및 play-services-basement 18.9.0의 매니페스트에는 이 세 권한 선언이 없었다. 다만 모든 전이 의존성의 매니페스트 병합 출처 보고서를 확보한 것은 아니므로, 다른 선언 주체가 전혀 없다는 완전성 주장은 하지 않는다. 릴리스 병합 출처 보고서는 기존 CI에 보관되지 않았고, 릴리스 권한 목록은 CI 로그로 확인했다.

## 실제 앱 동작과 구분

Android/KMP 소스에서 `FirebaseAuth`, `Recaptcha`, `initializeRecaptcha`, `signInWith`, `createUserWith`, `sendPasswordReset` 문자열을 검색했으며 일치가 없었다. 이는 앱의 명시적 인증 호출을 이 범위에서 발견하지 못했다는 뜻이다. SDK 자동 초기화·내부 호출·외부 통신이 전혀 없다는 증명은 아니다. 실기기 통신 캡처, reCAPTCHA 실행, Firebase 계정 생성·로그인은 수행하지 않았다.

reCAPTCHA SDK가 포함됐다는 사실만으로 유료 reCAPTCHA 서비스가 활성화되거나 결제가 시작됐다고 볼 수 없다. 이번 조사에서 Firebase 설정·제공업체·요금제·유료 리소스는 변경하지 않았다.

## 권고 및 2번 승인 범위

2번인 개인정보 안내와 Play 데이터 보안 **수정안 준비는 진행 권고**한다. SDK 공식 공개 자료와 실제 앱 구현을 대조하고, 자동 처리·인증 시 처리·미확인 항목을 분리해야 한다. 현재 권한 존재만으로 수집 여부를 확정하거나 ‘전혀 수집하지 않는다’고 단정하지 않아야 한다.

승인 후 준비할 내용:

1. Firebase Auth/reCAPTCHA의 공식 자료와 실제 사용 범위를 대조한 데이터 흐름표.
2. 오프라인 메모와 선택적 계정 기능을 구분한 개인정보처리방침 수정안.
3. 확인 근거·미확인 항목을 붙인 Play 데이터 보안 응답 초안.

이번 1번에서는 정책 문서·Play Console 신고·매니페스트·CI 허용 목록을 변경하지 않았다. 새 커밋·PR 수정·병합·배포도 없다. 2번은 사용자 승인 전까지 진행하지 않는다. 권한 제거 또는 CI 허용 목록 변경은 이 보고서의 권고가 아닌 별도 구현 판단 사항이다.

## 원본 및 참고 자료

- [Firebase Auth 25.0.0 POM](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-auth/25.0.0/firebase-auth-25.0.0.pom)
- [Firebase Auth 25.0.0 AAR](https://dl.google.com/dl/android/maven2/com/google/firebase/firebase-auth/25.0.0/firebase-auth-25.0.0.aar)
- [reCAPTCHA 18.6.1 AAR](https://dl.google.com/dl/android/maven2/com/google/android/recaptcha/recaptcha/18.6.1/recaptcha-18.6.1.aar)
- [Android INTERNET 권한](https://developer.android.com/reference/android/Manifest.permission#INTERNET)
- [Android ACCESS_NETWORK_STATE 권한](https://developer.android.com/reference/android/Manifest.permission#ACCESS_NETWORK_STATE)
- [Firebase 데이터 공개 안내](https://firebase.google.com/docs/android/play-data-disclosure)
- [reCAPTCHA 데이터 공개 안내](https://docs.cloud.google.com/recaptcha/docs/disclosure-requirements-android)

위 공개 안내는 2번 검토의 기준 자료다. 이번 단계에서 정책 수정안이나 신고 답변을 작성·적용한 것은 아니다.
