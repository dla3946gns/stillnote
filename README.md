# 간단 메모

메모를 작성하고 이 브라우저에 자동 저장하는 한국어 웹앱입니다.

## 실행

설치하거나 빌드할 의존성은 없습니다. 저장소 루트에서 실행하세요.

```bash
python3 -m http.server 8765 --directory dist --bind 0.0.0.0
```

브라우저에서 `http://localhost:8765`를 엽니다. Windows에서는 `python3` 대신 설치된 Python 실행 명령을 사용할 수 있습니다.

## Codex Cloud

1. GitHub 연결에서 이 비공개 저장소에 대한 접근을 허용합니다.
2. Cloud 환경 생성 시 이 저장소를 선택합니다. 별도 패키지 설치나 빌드 명령은 필요 없습니다.
3. `AGENTS.md`를 읽고 `dist/index.html`을 수정합니다. 위 명령으로 로컬 미리보기를 실행할 수 있습니다.

## 소스와 저장 방식

- `dist/index.html`: 실제 HTML, CSS, JavaScript 소스입니다. 생성된 빌드 파일이 아니므로 직접 수정합니다.
- `.openai/hosting.json`: 기존 Sites 프로젝트와 정적 배포 경로 설정입니다.
- 메모는 `localStorage`의 `simple-notes-v1` 키에 배열로 저장됩니다. 각 항목은 `id`, `title`, `body`, `createdAt`, `updatedAt`, 선택적 `pinned` 필드를 사용합니다.
- 메모 데이터는 저장소에 포함되지 않습니다. 로컬 미리보기와 배포 사이트는 서로 다른 origin이므로 각 브라우저 저장 데이터도 분리됩니다. 필요하면 앱의 JSON 백업 다운로드와 가져오기를 사용합니다.

운영 사이트: https://simple-notes.anna-johnny8517.chatgpt.site (소유자 전용)

GitHub 업로드는 사이트 배포나 기존 30분 개발 예약의 Cloud 이전을 자동으로 수행하지 않습니다.
