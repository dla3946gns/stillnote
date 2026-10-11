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

## 현재 확인 범위

Windows에서 소스와 Xcode 프로젝트를 작성했습니다. Mac의 Swift 컴파일·시뮬레이터·실기기 실행과 App Store 배포는 아직 수행하지 않았습니다. 출시 전에 앱 아이콘, 서명, 개인정보 안내와 실제 기기 동작을 준비해야 합니다.
