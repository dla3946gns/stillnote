# AJ Notes App Store 운영

App Store Connect 앱 ID는 `6835123279`, iOS 번들 ID는 `com.stillnotes.app`입니다. Actions에서 `main`과 대상 iOS 버전을 선택합니다.

## TestFlight 업로드와 앱 심사를 함께 실행할 때

1. App Store Connect에 필수 등록정보 및 심사 자료가 준비되어 있는지 확인합니다. 심사 제출 작업은 대상 버전을 조회하고, 없으면 수동 출시 방식으로 생성합니다. 기존 버전의 출시 방식은 변경하지 않습니다. 저장소의 해당 버전/로케일에 `whatsNew`가 설정된 경우 편집 가능한 버전에만 출시 노트를 적용합니다.
2. **AJ Notes TestFlight**에서 **Run workflow**를 열고 `submit_review`를 선택합니다. 프로젝트와 다른 마케팅 버전으로 빌드할 경우 `marketing_version`에 해당 버전을 입력합니다.
3. Xcode가 App Store 심사에 사용할 수 있는 빌드를 클라우드 서명해 한 번 업로드합니다. 아카이브에서 확인한 버전과 빌드 번호를 다음 작업으로 전달합니다. Linux 작업이 그 빌드가 조회되고 Apple 처리가 끝날 때까지 최대 40분 기다린 후 심사를 요청합니다. 성공 로그에 Apple 심사 제출 ID가 표시됩니다. Apple의 필수 등록정보·심사 자료·수출 규정 등의 검증에 실패하면 심사 요청이 중단됩니다. 새 버전을 생성한 경우 심사 승인 후 공개는 수동으로 진행합니다.

TestFlight에 이미 올린 빌드는 **AJ Notes App Store**에서 `submit-review`, iOS 버전, 정확한 빌드 번호를 선택해 제출할 수 있습니다. Apple 처리가 오래 걸렸거나 심사 단계만 다시 실행하려는 경우에도 이 경로를 사용합니다. `TestFlight Internal Only`로 올린 빌드는 내부 테스터용이므로, 심사에는 App Store 배포용 빌드를 선택합니다.

테스터에게 앱을 제공하려면 TestFlight 그룹에 빌드를 배정하거나 그룹의 자동 배포 설정을 사용합니다. 외부 테스터 배포에는 Apple이 별도의 베타 심사를 요구할 수 있습니다. 앱 심사 승인 후 공개 시점은 App Store Connect에서 해당 버전에 설정한 출시 방식을 따릅니다.

## 스토어 정보와 스크린샷

**AJ Notes App Store**의 기본 작업은 읽기 전용 `status`입니다. 앱 버전 상태, 연결된 빌드 번호, 선택한 언어의 스크린샷 그룹과 이미지 ID를 확인합니다. 한국어 App Store 로케일은 `ko`입니다.

| 작업 | Actions 입력 및 효과 |
| --- | --- |
| `metadata` | 선택한 버전과 언어의 `listing.json`에서 지정한 앱 이름, 부제, 개인정보 처리방침 URL, 설명, 키워드, 프로모션 문구, 지원·마케팅 URL, 업데이트 출시 노트(`whatsNew`)의 변경분을 적용합니다. |
| `append-screenshots` | `group`에 지정한 기기 그룹의 기존 순서를 유지하면서 목록의 새 이미지를 뒤에 추가합니다. |
| `replace-screenshots` | 선택한 언어·기기 그룹의 최종 이미지 목록과 순서를 파일에 맞춥니다. 한 장만 바꾸려면 나머지 장의 경로나 기존 이미지 ID를 목록에 유지합니다. |
| `submit-review` | 지정한 버전과 빌드 번호로 Apple에 심사를 요청합니다. |

설정 위치는 `mobile/app-store/<버전>/<로케일>/listing.json`입니다. `1.0/ko/listing.json`에는 기존 앱 이름만, `1.0.1/ko/listing.json`에는 아이콘 업데이트 출시 노트가 들어 있습니다. 스크린샷은 해당 언어 디렉터리에 iOS PNG 또는 JPEG를 추가하고, 파일에 표시할 순서대로 경로를 기록합니다. 기존 App Asset Library 이미지도 `status`에서 확인한 `{"assetId":"이미지-ID"}`로 재사용할 수 있습니다.

업데이트 버전의 설정 예시입니다. 그룹은 Apple이 해당 기기에 제공하는 식별자를 사용합니다.

```json
{
  "appInfo": { "name": "AJ Notes" },
  "versionInfo": { "whatsNew": "메모 검색을 개선했습니다." },
  "screenshots": {
    "IPHONE_DYNAMIC_ISLAND_LARGE_PROFILE": [
      "screenshots/01-list.png",
      "screenshots/02-editor.png"
    ]
  }
}
```

새 이미지가 Apple의 처리를 마치고 해당 기기 규격에 맞는지 확인한 다음 배치를 변경합니다. 재실행할 때 파일의 SHA-256으로 업로드한 이미지를 재사용하고 최종 표시 순서를 확인합니다. Apple에는 이미지 여러 장을 한꺼번에 바꾸는 트랜잭션이 없어 중단된 경우 같은 목록으로 다시 실행할 수 있습니다.

스크린샷은 제출 준비, 유효하지 않은 바이너리, 거절, 메타데이터 거절, 개발자 거절 상태에서 수정할 수 있습니다. 프로모션 문구는 출시 후에도 수정할 수 있습니다. 대부분의 다른 문구는 편집 가능한 버전에서 수정합니다. 출시 노트는 업데이트 버전에 사용합니다. Apple이 추가 설명이나 실기기 영상을 요청한 경우 App Store Connect에 해당 심사 자료를 먼저 보완합니다.

## 인증과 검증

기존 GitHub 환경 `ios-testflight`의 `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_PRIVATE_KEY`를 사용합니다. 빌드 업로드에는 Developer 역할도 사용할 수 있고, 심사 제출에는 App Manager 이상 권한이 필요합니다. 새 빌드를 만드는 단계는 기존 Xcode 러너와 클라우드 서명을 사용합니다. 등록정보·이미지·심사 API 단계는 Ubuntu에서 SHA-256으로 고정한 `asc` 7.4.0을 사용합니다.

PR에서는 Apple 인증 없이 설정과 단위 테스트를 검사합니다. 로컬에서 같은 검사를 실행할 수 있습니다.

```sh
python3 mobile/scripts/app_store.py check-files
python3 -m unittest discover -s mobile/scripts -p 'test_app_store*.py' -v
```

[Apple 빌드 업로드 API](https://developer.apple.com/documentation/appstoreconnectapi/build-uploads) · [빌드 상태와 TestFlight](https://developer.apple.com/help/app-store-connect/reference/app-uploads/app-build-statuses) · [앱 심사 제출](https://developer.apple.com/help/app-store-connect/manage-submissions-to-app-review/submit-an-app) · [스크린샷 배치](https://developer.apple.com/documentation/appstoreconnectapi/placing-assets-on-your-app-store-surfaces) · [버전별 앱 정보](https://developer.apple.com/documentation/appstoreconnectapi/app-store-version-localizations)
