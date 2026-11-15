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

1. **아이콘**: 현재 iOS Xcode 프로젝트에 AppIcon asset catalog가 없음. 승인된 AJ Notes 아이콘을 1024×1024 픽셀 원본 이미지로 확정하고 Xcode asset catalog에 등록할 것. Android용 기존 감자 캐릭터 SVG는 최종 AJ Notes 아이콘으로 자동 간주하지 않음.
2. **Apple Distribution 인증서**: `Certificates, Identifiers & Profiles`에서 기존 유효한 배포 인증서와 해당 **개인키** 보유 여부부터 확인. 존재한다면 재사용. 새 인증서가 필요할 때는 소유자 명시 승인 후 생성. GitHub Actions에 임포트할 때는 비밀번호로 보호된 `.p12` 형식이 필요함.
3. **App Store Connect 프로비저닝 프로파일**: `Profiles → + → Distribution → App Store Connect`에서 `com.stillnotes.app` App ID 및 위 배포 인증서를 선택해 생성·다운로드. 유효한 기존 프로파일이 있으면 재사용.
4. **App Store Connect API 키**: 계정의 `Users and Access → Integrations → App Store Connect API`에서 기존 유효한 권한과 키가 있는지 확인. 필요하다면 최소 권한 키 생성 후 Key ID, Issuer ID와 `.p8` 키를 안전하게 보관.
5. **GitHub 보호 환경**: `ios-testflight` 환경의 승인자 설정 및 서명 인증서/프로파일/API 키를 환경 Secret으로 보관. 비밀키·비밀번호·`.p12`·`.p8`·프로파일은 코드, PR, CI 아티팩트, 이슈, 채팅에 첨부하지 않음.
6. **정식 서명 및 업로드 워크플로**: 준비 완료 후 수동 실행 전용 workflow를 별도 검토해 생성. `xcodebuild archive` → Apple Distribution 서명 → 검증/내보내기 → App Store Connect 업로드. **자동 업로드나 심사 제출은 지금 구성하지 않음.** 첫 업로드 및 서명 Secret 변경 전에는 소유자 승인을 받을 것.

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
