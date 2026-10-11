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

## iOS 실행

Apple silicon Mac에서 [iOS 실행 안내](iosApp/README.md)를 따라 `iosApp/Stillnote.xcodeproj`의 `Stillnote` 스킴을 실행합니다. Xcode가 공통 Kotlin 프레임워크를 먼저 빌드하도록 연결돼 있습니다. JDK 17이 필요하며 Xcode 빌드 환경에서 `JAVA_HOME`을 찾을 수 있어야 합니다.

Android applicationId와 iOS Bundle Identifier는 `com.stillnotes.app`입니다. 실제 기기 서명과 스토어 등록 전에 본인의 개발 팀을 설정해야 합니다. 이전 `com.stillnote.app`과는 다른 앱으로 설치되므로 이전 메모를 옮기려면 JSON 백업 내보내기·가져오기를 사용합니다.

## 현재 확인 범위

- 공통 Kotlin 메타데이터 컴파일: 성공.
- Android APK 빌드: 성공. 저장 처리 보완 후 재빌드도 성공.
- iOS Swift 컴파일·시뮬레이터·실기기 실행: Windows 환경이므로 미수행.
- 자동화 테스트·기기 동작 QA·스토어 배포: 미수행.

다음 단계는 Mac의 iOS 빌드, 두 플랫폼 실기기 입력·백업·저장 동작 확인, 앱 아이콘과 서명 준비입니다.
