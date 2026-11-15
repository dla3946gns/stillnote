# AJ Notes iOS TestFlight 준비

## 목표 및 현황

- App Store Connect 앱 이름: **AJ Notes** (사용자 등록 확인)
- App Store Connect App ID: `6835123279`
- iOS Bundle ID: `com.stillnotes.app` (**변경하지 않음**)
- App Store Connect 앱 버전: `1.0`
- iOS 홈 화면 이름 / 메모 목록 제목: `AJ Notes` (이 PR)
- iOS 빌드 번호: `1`
- Xcode 프로젝트/스킴/실행 파일/공통 프레임워크 이름은 기존 `Stillnote` 유지. 이들은 사용자에게 표시되는 앱 이름과 별개이며, 이름 변경으로 기존 앱 데이터를 잃지 않도록 불필요하게 변경하지 않음.

현재 GitHub Actions의 `.github/workflows/ios-ci.yml`은 Xcode 27에서 앱 컴파일과 시뮬레이터 실행을 검사하며, `Stillnote-iPhone-unsigned.ipa`를 아티팩트로 만듭니다. **이 IPA는 서명되지 않았으므로 TestFlight에 업로드할 수 없습니다.**

## 업로드 전 준비

1. **아이콘**은 기존 Stillnote 감자 아이콘을 사용합니다. 1024×1024 RGB PNG를 iOS AppIcon에 포함합니다.
2. App Store Connect → 사용자 및 액세스 → 통합에서 클라우드 배포 서명 권한이 있는 **팀 API 키**를 준비합니다.
3. GitHub Actions의 `ios-testflight` 환경에 `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_PRIVATE_KEY`를 Secrets로 등록합니다. `ASC_PRIVATE_KEY`에는 `.p8` 파일의 전체 내용을 저장합니다.
4. `main`에서 **AJ Notes TestFlight** 워크플로를 수동 실행합니다. 기본 실행은 iPhone용 아카이브의 번들 ID, 버전, 아이콘을 확인합니다. `upload`를 선택하면 Xcode가 Apple 클라우드 서명을 요청하고 내부 테스트 전용 빌드를 업로드합니다.

## TestFlight 내부 테스트

1. 정식 서명 빌드가 업로드된 후 Apple 처리 완료를 확인.
2. App Store Connect → **AJ Notes → TestFlight → 내부 테스트(Internal Testing)**에 테스트 그룹을 만들고 빌드를 할당.
3. 본인의 App Store Connect 사용자 계정을 내부 테스터로 추가하고 초대 수락.
4. iPhone에서 TestFlight를 열고 AJ Notes를 설치.
5. 실기기에서 저장·취소·재실행 후 메모 유지, 검색, 삭제·복원, JSON 백업·가져오기 및 다크 모드 확인.

내부 테스트는 App Store 정식 공개를 뜻하지 않음. 외부 테스트는 별도 베타 앱 심사가 필요할 수 있음.

## 참고

- [Apple — Upload builds](https://developer.apple.com/help/app-store-connect/manage-builds/upload-builds)
- [Apple — Create an App Store Connect provisioning profile](https://developer.apple.com/help/account/provisioning-profiles/create-an-app-store-provisioning-profile)
- [Apple — Add internal testers](https://developer.apple.com/help/app-store-connect/test-a-beta-version/add-internal-testers)
