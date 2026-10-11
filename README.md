# stillnotes.web

메모를 작성하고 저장 버튼으로 이 브라우저에 보관하는 한국어 웹앱입니다. 취소 버튼은 마지막 저장 내용으로 돌아가며, 새 메모는 저장 전 취소하면 목록에 추가되지 않습니다. 저장하지 않은 상태로 이동할 때는 저장·버리기·계속 편집을 선택합니다.

## 네이티브 모바일 앱

KMP 공통 로직과 Android Jetpack Compose·iOS SwiftUI 앱은 `mobile/`에 있습니다. 구조, 개발 버전과 실행 방법은 [모바일 안내](mobile/README.md)를 참고하세요.

## 실행

배포 파일이 포함되어 있어 정적 실행에는 패키지 설치나 빌드가 필요 없습니다. 저장소 루트에서 실행하세요.

```bash
python3 -m http.server 8765 --directory dist --bind 0.0.0.0
```

브라우저에서 `http://localhost:8765`를 엽니다. Windows에서는 `python3` 대신 설치된 Python 실행 명령을 사용할 수 있습니다.

## Shaders 디자인 수정

사이드바 상단은 MIT 라이선스의 `shaders@4.0.1` MeshGradient를 사용합니다. 효과 소스는 `src/sidebar-shader.js`이며 수정 후 Node.js 환경에서 번들을 갱신합니다.

```bash
npm ci
npm run build:shader
```

`dist/assets`의 생성 파일과 라이선스 안내도 함께 커밋합니다. HTML 및 메모 기능 수정에는 이 빌드가 필요 없습니다. 효과는 메모 기능 초기화 후 로컬 번들에서 불러오며, WebGPU 미지원·동작 줄이기·데이터 절약 설정에서는 정적 그라데이션을 표시합니다. SDK telemetry는 비활성화했습니다.

Codex 스킬은 [shader-effects-inc/shaders의 skills/shaders](https://github.com/shader-effects-inc/shaders/tree/main/skills/shaders)입니다. Shaders CLI의 `connect`는 현재 React·Vue·Svelte·Solid만 감지하므로 이 일반 JavaScript 앱은 SDK를 직접 사용합니다. 별도 Shaders 계정이나 유료 프리셋이 필요하지 않습니다.

## Codex Cloud

1. GitHub 연결에서 공개 저장소 [dla3946gns/stillnote](https://github.com/dla3946gns/stillnote)에 대한 접근을 허용합니다.
2. Cloud 환경 생성 시 `dla3946gns/stillnote` 저장소를 선택합니다. 별도 패키지 설치나 빌드 명령은 필요 없습니다.
3. `AGENTS.md`를 읽고 `dist/index.html`을 수정합니다. 위 명령으로 로컬 미리보기를 실행할 수 있습니다.

## 소스와 저장 방식

- `dist/index.html`: 실제 HTML, CSS, JavaScript 소스입니다. 생성된 빌드 파일이 아니므로 직접 수정합니다.
- `src/sidebar-shader.js`: 장식 효과와 표시·정지 제어 소스입니다.
- `scripts/build-shader.mjs`: 효과 번들과 라이선스 안내를 생성합니다.
- `.openai/hosting.json`: 기존 Sites 프로젝트와 정적 배포 경로 설정입니다.
- 메모는 `localStorage`의 `simple-notes-v1` 키에 배열로 저장됩니다. 각 항목은 `id`, `title`, `body`, `createdAt`, `updatedAt`, 선택적 `pinned` 필드를 사용합니다.
- 메모 데이터는 저장소에 포함되지 않습니다. 로컬 미리보기와 배포 사이트는 서로 다른 origin이므로 각 브라우저 저장 데이터도 분리됩니다. 필요하면 앱의 JSON 백업 다운로드와 가져오기를 사용합니다.

운영 사이트: https://simple-notes.anna-johnny8517.chatgpt.site (소유자 전용)

GitHub 업로드는 사이트 배포나 기존 30분 개발 예약의 Cloud 이전을 자동으로 수행하지 않습니다.
