# Stillnote iOS

SwiftUI 화면과 KMP `StillnoteShared` 프레임워크를 연결한 iPhone·iPad 앱입니다. 최소 지원 버전은 iOS 17이며, Swift 6 언어 모드와 설치된 Xcode의 iOS SDK를 사용합니다. Xcode 27을 개발 환경으로 권장하되 Kotlin/Native와의 조합은 Mac에서 빌드 확인이 필요합니다. 최신 OS에서는 기본 탐색·도구 모음이 시스템 디자인을 사용하고, 메모 작성 영역에는 크림·아이보리·코코아 팔레트를 적용합니다.

## Mac에서 실행

1. JDK 17을 설치하고 `JAVA_HOME`을 설정합니다.
2. `mobile/gradlew`에 실행 권한이 없다면 `chmod +x mobile/gradlew`를 실행합니다.
3. `mobile/iosApp/Stillnote.xcodeproj`를 엽니다.
4. `Stillnote` 스킴과 Apple silicon Mac의 iPhone/iPad 시뮬레이터를 선택합니다.
5. 실행하면 첫 빌드 단계가 `:shared:embedAndSignAppleFrameworkForXcode`로 공통 프레임워크를 만들고 Swift 소스를 컴파일합니다. 첫 실행에는 Gradle·Kotlin 의존성 다운로드가 필요합니다.
6. 실제 기기로 실행하거나 배포할 때는 Signing & Capabilities에서 본인의 개발 팀을 설정합니다. Bundle Identifier는 `com.stillnotes.app`입니다.

## 저장과 백업

- 메모는 앱 샌드박스의 `Application Support/Stillnote/stillnote-notes.json`에 원자적으로 저장합니다. 클라우드 동기화나 외부 서버를 사용하지 않습니다.
- 제목과 본문을 편집한 뒤 저장 버튼을 눌러 반영합니다. 취소하면 기존 내용으로 돌아가고, 새 메모는 저장하기 전까지 목록에 추가되지 않습니다. 편집 중 메모를 바꾸거나 목록으로 돌아갈 때는 저장·버리기·계속 편집을 선택할 수 있습니다.
- 읽기·쓰기 실패는 공통 메모 상태에 전달하고 화면의 재시도 버튼으로 다시 시도합니다.
- JSON 백업은 시스템 파일 선택기와 내보내기로 처리합니다. 웹과 동일한 메모 배열 형식을 사용하며 가져오기 검증·같은 ID 건너뛰기·저장 실패 처리는 공통 모듈이 담당합니다.
- 개별 메모는 공유하거나 UTF-8 텍스트 파일로 내보낼 수 있습니다. 제목 입력은 조합 중인 한글을 유지하고 조합이 끝난 뒤 120자로 제한합니다. 가져온 기존 제목은 편집하기 전까지 그대로 보존합니다.
- 기존 브라우저 메모를 자동으로 읽거나 이전하지 않습니다. 사용자가 웹에서 만든 백업을 직접 선택해 가져올 수 있습니다.
- SwiftUI 상태는 `NotesObserver`의 동기 콜백으로 갱신합니다. 모든 공통 메모 작업은 메인 액터에서 호출합니다. 관찰자는 약한 참조로 모델에 연결해 순환 참조를 피합니다.

## Mac 없이 클라우드에서 빌드 검증

GitHub Actions의 `iOS build validation`은 Apple silicon macOS 실행기와 JDK 17에서 공통 Kotlin 프레임워크 및 SwiftUI 앱을 빌드합니다. iOS 관련 PR에서 자동 실행되며, 기본 브랜치에 반영한 뒤에는 Actions에서 수동 실행할 수도 있습니다. Apple 계정이나 배포 인증서는 사용하지 않습니다.

- 빌드 및 기동 명령은 `.github/scripts/check-ios-simulator.sh`에 있습니다.
- 사용 가능한 iOS 17 이상 iPhone 시뮬레이터에 앱을 설치하고, 빈 저장소·합성 예시 메모·읽을 수 없는 UTF-8 파일 상태에서 기동을 검사합니다. 기존 파일을 덮어쓰지 않는지도 확인합니다.
- `stillnote-ios-simulator-qa` 아티팩트에 시뮬레이터용 `.app` ZIP, Xcode 결과 번들, 빌드 환경·로그, 실행 화면을 14일 동안 보관합니다. ZIP은 실제 iPhone 설치용 IPA가 아닙니다.
- Swift 파일 I/O 결과는 `IosNotesFiles`와 iOS 전용 Kotlin 어댑터로 전달합니다. 읽기·쓰기 실패는 Kotlin 예외로 변환하여 기존 `NotesStore`의 저장 실패 롤백과 손상 파일 보호를 유지합니다.

## iPhone 설치 준비용 IPA

같은 워크플로의 `Unsigned iPhone IPA for local signing` 작업은 `.github/scripts/build-ios-device-ipa.sh`로 Release `arm64` iPhoneOS 앱을 빌드하고 `Payload/Stillnote.app` 구조의 `Stillnote-iPhone-unsigned.ipa`를 만듭니다. 바이너리의 실기기 플랫폼, 패키지명과 ZIP 무결성을 검사합니다.

- `stillnote-ios-device-ipa` 아티팩트에 IPA, SHA-256, 패키지 정보와 빌드 로그를 14일 동안 보관합니다.
- 이 IPA는 Apple 계정 서명 전 파일입니다. Windows에서 Sideloadly 등의 도구와 본인의 Apple 계정을 사용해 서명·설치할 수 있습니다. 무료 계정은 7일마다 서명을 갱신해야 합니다.
- IPA 파일을 iPhone의 파일 앱에서 여는 것만으로 설치되지는 않습니다. 실제 설치와 실행은 별도 확인이 필요하며, 이번 작업은 인증서 생성이나 TestFlight·App Store 업로드를 수행하지 않습니다.

## 현재 확인 범위

초기 소스와 Xcode 프로젝트는 Windows에서 작성했습니다. Swift 컴파일 및 시뮬레이터 기동 결과는 위 GitHub Actions 실행에서 확인합니다. 실제 iPhone 설치·TestFlight·App Store 배포는 별도 서명 및 계정 설정이 필요합니다. 출시 전에 앱 아이콘, 개인정보 안내와 실제 기기 동작을 준비해야 합니다.
