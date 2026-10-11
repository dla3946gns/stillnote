# Stillnote 모바일

메모의 비즈니스 로직은 Kotlin Multiplatform으로 공유하고 Android 화면은 Kotlin·Jetpack Compose, iOS 화면은 Swift·SwiftUI로 구현한 첫 네이티브 앱입니다.

## 구조

```text
mobile/
  shared/       메모 모델, 검색·정렬, 편집·저장 상태, JSON 백업
  androidApp/   Compose 화면, Android 파일 저장·선택·공유
  iosApp/       SwiftUI 화면, iOS 파일 저장·선택·공유, Xcode 프로젝트
```

공통 `NotesStore`가 상태를 만들고 `NotesObserver`로 각 화면에 전달합니다. Android는 수명 주기를 갖는 `ViewModel`, iOS는 Swift Observation 모델을 사용합니다. 추가 Swift 브리지 라이브러리나 Alpha 상태의 Swift export는 사용하지 않습니다.

## 구현한 기능

- 메모 생성·편집, 저장·취소, 미저장 변경 확인, 제목·본문 검색, 최신 수정 순 정렬
- 메모 고정·고정 필터, 복제, 삭제 확인·실행 취소
- 내용 복사·시스템 공유, 현재 메모 텍스트 파일 내보내기
- 전체 메모 JSON 백업 내보내기·가져오기
- 저장 실패 안내와 재시도, 읽을 수 없는 저장 파일의 덮어쓰기 방지
- 크림·아이보리·코코아 색상, 따뜻한 다크 테마, 휴대폰·태블릿 화면

저장에 성공하면 홈의 메모 목록으로 돌아가 최신 내용을 보여줍니다. 취소는 편집 내용을 버린 뒤 홈으로 돌아갑니다. 저장에 실패하면 편집 화면과 입력 내용을 유지합니다.

앱은 기기 내 전용 파일에 저장합니다. 웹의 `localStorage`와 자동으로 연결하거나 기기 간 동기화하지 않습니다. 기존 웹 메모가 필요하면 사용자가 웹에서 JSON 백업을 내보내고 앱에서 파일을 선택해 가져옵니다. 같은 ID의 메모는 건너뛰며 기존 내용을 덮어쓰지 않습니다.

백업은 웹과 같은 배열 형식입니다. `id`, `title`, `body`, `createdAt`, `updatedAt`, 선택적 `pinned`를 사용하며 시각은 Unix epoch 밀리초 숫자입니다. 사용자 메모와 백업은 소스 저장소에 포함하지 않습니다.

## 고정한 개발 버전

2026-10-11에 확인한 안정판과 KMP 호환 범위를 기준으로 선택했습니다.

| 항목 | 버전 |
|---|---|
| Kotlin / KMP / Compose compiler | 2.4.21 |
| Android Gradle Plugin | 9.3.1 |
| Gradle wrapper | 9.7.0 |
| Compose BOM | 2026.09.00 |
| Compose UI / Foundation | 1.12.1 |
| Material 3 | 1.4.0 |
| Activity Compose | 1.13.0 |
| Lifecycle ViewModel | 2.11.0 |
| kotlinx.serialization | 1.11.0 |
| Android 최소 지원 / compile·target SDK | Android 8.0(API 26) / API 37 |
| iOS 최소 지원 / Swift 언어 모드 | iOS 17 / Swift 6 |

버전은 `gradle/libs.versions.toml`에서 관리합니다. Material 3 Expressive의 최신 라이브러리는 아직 베타이므로 이 첫 구현에는 안정판 Material 3를 사용합니다. 최신 iOS에서 기본 SwiftUI 탐색·도구 모음이 시스템 디자인을 사용하며, 메모 작성 영역에는 불투명한 아이보리 배경을 적용합니다.

AGP의 최신 버전과 별개로 KMP 문서의 명시된 호환 범위인 9.3.1을 선택했습니다. Kotlin 문서의 Xcode 표는 26.4를 명시하므로 Xcode 27 조합은 Mac에서 빌드 확인이 필요합니다.

공식 자료: [KMP 호환성](https://kotlinlang.org/docs/multiplatform/multiplatform-compatibility-guide.html), [Android KMP 플러그인](https://developer.android.com/kotlin/multiplatform/plugin), [Compose 릴리스](https://developer.android.com/jetpack/androidx/releases/compose), [Material 3 릴리스](https://developer.android.com/jetpack/androidx/releases/compose-material3), [SwiftUI 최신 기능](https://developer.apple.com/swiftui/whats-new/), [Xcode 직접 연결](https://kotlinlang.org/docs/multiplatform/multiplatform-direct-integration.html).

## Android 실행

1. Android Studio에서 이 `mobile` 폴더를 엽니다.
2. Gradle JDK는 JDK 17로 설정합니다.
3. SDK Manager에서 Android SDK Platform 37.0, Build Tools 36.0.0, Platform Tools를 설치합니다.
4. Gradle 동기화 후 `androidApp`을 기기나 에뮬레이터에서 실행합니다.

명령줄에서는 `JAVA_HOME`과 `ANDROID_HOME`을 설정하거나 SDK 경로를 로컬 `local.properties`의 `sdk.dir`에 지정한 뒤 실행합니다.

```powershell
./gradlew.bat :androidApp:assembleDebug
```

macOS/Linux:

```sh
chmod +x gradlew
./gradlew :androidApp:assembleDebug
```

APK 위치: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. 디버그 서명이며 스토어 제출용 빌드가 아닙니다.

GitHub Actions가 성공하면 `stillnote-android-debug-apk` 아티팩트에 개발용 APK도 보관합니다. 실제 Android 기기의 설치·JSON 백업·복원은 [실기기 QA 체크리스트](ANDROID_DEVICE_QA.md)를 따르세요. **디버그 APK의 서명이 기존 설치 앱과 다를 수 있으므로, 기기 메모를 백업하기 전에 앱을 삭제하거나 재설치하지 마세요.**

## Android 자동 테스트

KMP 공통 `NotesStore`에 대해 Android 호스트 JVM 테스트 14개를 추가했습니다. 작성·명시적 저장·취소, 제목·본문 검색, 고정, 삭제·복원, JSON 백업·가져오기, 저장/읽기 실패 시 데이터 보호를 확인합니다. 추가 Android 파일 저장 테스트 3개는 실제 임시 디렉터리에 파일을 작성하고, 스토어 재생성 이후 복원·쓰기 실패 시 이전 데이터 보존·손상된 UTF-8 파일 보호를 검증합니다. 총 17개의 JVM 테스트입니다.

```sh
./gradlew :shared:testAndroidHostTest :androidApp:testDebugUnitTest :androidApp:assembleDebug
```

GitHub Actions는 PR의 코드가 변경될 때 이 명령을 자동 실행하고 테스트 결과 XML을 아티팩트로 업로드합니다. **위의 17개 테스트는 Android 호스트 JVM에서 실행됩니다.** 실제 Android 기기 환경과 Compose 화면을 검증하기 위해 별도의 `androidTest` 계측 테스트도 추가했습니다. GitHub Actions에서 API 35 x86_64 에뮬레이터를 실행하고 라이트·다크 모드 각각 메모 작성·저장·취소, 미저장 확인창, 검색·고정, 삭제·복원, Activity 재실행 후 데이터 유지와 테마 배경색을 검사합니다. 테스트 보고서와 테마 캡처는 `android-emulator-qa` 아티팩트로 보관합니다. 전체 계측 테스트는 다음 명령으로 수동 실행할 수 있습니다(에뮬레이터 또는 Android 기기가 연결돼 있어야 함).

```sh
./gradlew :androidApp:connectedDebugAndroidTest
```

에뮬레이터 검증은 실제 물리 기기의 성능·키보드 호환성, 제조사별 동작, Android 문서 선택기를 통한 백업 입출력을 완전히 대체하지는 못합니다. 이를 보완하기 위해 Android Downloads 공급자의 `ContentResolver`를 사용하는 JSON 백업 입출력 계측 테스트 5개도 추가했습니다. 이 테스트는 실제 파일 스트림과 복원 로직을 확인하지만 **시스템 파일 선택기 UI를 직접 누르는 테스트는 아니므로 실기기 QA가 여전히 필요합니다.**

## Android 정식 릴리스 빌드 준비

Google Play 업로드용 AAB는 소유자만 보유한 **업로드 서명키**를 사용하며, 서명키·비밀번호는 저장소에 포함하지 않습니다. `mobile/gradle.properties`의 `stillnoteVersionCode`, `stillnoteVersionName`으로 버전을 관리합니다. PR CI는 **폐기용 임시 키로 AAB 빌드만 확인**하고 실제 AAB는 업로드하지 않습니다. 소유자 서명 AAB는 GitHub Actions의 `Android signed release AAB` 워크플로를 `main`에서 수동 실행하도록 구성했습니다. 서명 Secret을 등록하지 않으면 정식 빌드가 실패합니다.

**서명키 생성·백업, GitHub 환경 Secret 등록, 수동 릴리스 빌드와 Play Console 내부 테스트 절차는 [Android 릴리스 가이드](ANDROID_RELEASE.md)를 참고하세요.** 현재 자동 Play Store 게시 기능은 없고, CI에서 업로드 키를 생성하거나 보관하지 않습니다.

## APK 업데이트·Play 등록정보 준비

PR CI에 **이전 main 빌드에서 실제 메모를 생성하고 새 APK를 `adb install -r`로 덮어쓴 뒤 내부 저장 파일이 유지되는지** 확인하는 별도 에뮬레이터 회귀 테스트를 추가했습니다. 이 테스트는 테스트용 디버그 서명키로 검증하는 것으로, 나중에 진행할 **Google Play 서명 앱의 실제 업데이트 테스트를 대신하지 않습니다**.

개발자 계정 없이 작성 가능한 [Play 등록정보 문구 초안](PLAY_STORE_LISTING_DRAFT.md)과 [개인정보처리방침 초안](PRIVACY_POLICY_DRAFT.md)도 추가했습니다. **소유자 정보·정책 URL·앱 내부 개인정보 고지는 아직 미확정**이므로 Play Console 제출 전 반드시 확인해야 합니다. 현재 앱은 사용자 계정·자동 동기화 기능을 제공하지 않습니다.

## 개인정보처리방침 및 Play 스토어 공개 준비

사용자가 지정한 운영 주체 **Anna Johnny Works**와 공개 문의 이메일 **dla3946gns@gmail.com**을 정책 문서와 Android 앱에 반영했습니다. 앱 내 `더보기 → 개인정보처리방침`을 누르면 인터넷 없이 정책 전문을 확인할 수 있습니다.

- [오프라인 앱 정책 원문](androidApp/src/main/assets/privacy-policy-ko.txt)과 [웹 공개용 정책 HTML](../docs/privacy/index.html)은 CI에서 내용 일치를 검사합니다.
- [GitHub Pages 자동 배포](../.github/workflows/privacy-pages.yml)를 구성했습니다. [게시 안내](PRIVACY_POLICY_PUBLISHING.md)에 따라 소유자가 저장소 Pages 설정에서 **Source → GitHub Actions**를 한 번 선택해야 합니다. 이후 정책이 `main`에 반영되면 자동 배포하며, **현재 공개 웹 URL의 활성화는 확인되지 않았습니다.**
- [Play 스토어 등록정보 초안](PLAY_STORE_LISTING_DRAFT.md)에 운영 주체/지원 이메일을 반영했지만, 실제 사업자 정보·정책 URL·스크린샷·개인정보 관련 Play Console 신고는 최종 검토가 필요합니다.

## Google Play 등록정보 및 스토어 이미지

[Google Play 등록정보 JSON](play/listing-ko.json), [데이터 보안 사전 점검](PLAY_DATA_SAFETY_REVIEW.md), [스토어 아이콘·대표 그래픽·실제 화면 캡처 안내](PLAY_ASSETS.md)를 준비합니다. Android 런처 아이콘도 브랜드 가이드의 포실이 캐릭터로 맞췄습니다. Google Play 게시용 PNG는 CI에서 자동 생성하고 검사하며, 개인 메모 대신 화면에 표시하는 **합성 예시 메모를 실제 앱 UI로 촬영**합니다.

GitHub Pages 개인정보처리방침은 https://dla3946gns.github.io/stillnote/privacy/ 에 게시했으며, HTTPS로 실제 본문이 열린 것을 Actions에서 확인했습니다. Play Console은 아직 설정/제출하지 않았습니다.

## iOS 실행

Apple silicon Mac에서 [iOS 실행 안내](iosApp/README.md)를 따라 `iosApp/Stillnote.xcodeproj`의 `Stillnote` 스킴을 실행합니다. Xcode가 공통 Kotlin 프레임워크를 먼저 빌드하도록 연결돼 있습니다. JDK 17이 필요하며 Xcode 빌드 환경에서 `JAVA_HOME`을 찾을 수 있어야 합니다.

Android applicationId와 iOS Bundle Identifier는 `com.stillnotes.app`입니다. 실제 기기 서명과 스토어 등록 전에 본인의 개발 팀을 설정해야 합니다. 이전 `com.stillnote.app`과는 다른 앱으로 설치되므로 이전 메모를 옮기려면 JSON 백업 내보내기·가져오기를 사용합니다.

## 현재 확인 범위

- 공통 Kotlin 메타데이터 컴파일: 성공.
- Android APK 빌드: 성공. 저장 처리 보완 후 재빌드도 성공.
- iOS Swift 컴파일·시뮬레이터·실기기 실행: Windows 환경이므로 미수행.
- 공통 메모 로직 14개, Android 파일 저장 호스트 테스트 3개: GitHub Actions #43에서 통과.
- API 35 에뮬레이터 Compose UI 8개 + Android JSON 백업/문서 공급자 계측 테스트 5개를 라이트·다크 모드에서 각각 실행: 총 26회 GitHub Actions #43 통과.
- 사용자 보고: 삼성 Android 실기기에서 키보드 표시, 작성·저장, 재시작 후 데이터 유지, JSON 백업·복원, 취소·검색·삭제 5개 항목 통과.
- 회전·큰 글씨, 타사 키보드·다른 제조사, 클라우드 문서 공급자 등 추가 기기 호환성 QA: 미완료. [체크리스트](ANDROID_DEVICE_QA.md) 참조.
- Android Google Play 릴리스: 업로드키·환경 Secret·서명 AAB·Play 내부 테스트는 별도 준비 작업 중. 스토어 공개 전 확인 필요.
- iOS Swift 컴파일·시뮬레이터·실기기 실행 및 App Store 배포: Mac 환경을 사용할 수 있을 때까지 보류.

Android 앱과 실기기 QA 개선은 PR #1·#2를 통해 `main`에 병합됐습니다. Android 정식 릴리스 구성은 새로운 PR로 검증하며, Google Play 자동 배포는 하지 않습니다.
