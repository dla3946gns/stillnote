# Stillnote 개인정보처리방침 웹 공개 절차 (GitHub Pages)

현재 공개 URL은 **아직 활성화 확인 전**입니다. 이 문서는 `docs/privacy/index.html`을 공개 웹페이지로 게시하는 정확한 절차입니다.

## 정책 내용

- Android 앱 내부: 메모 목록 → `더보기` → `개인정보처리방침` (인터넷 연결 없이 전체 본문 확인)
- 동일한 앱 내 정책 원문: `mobile/androidApp/src/main/assets/privacy-policy-ko.txt`
- 공개 웹 페이지 원본: `docs/privacy/index.html`
- 운영 주체: **Anna Johnny Works**
- 공개 개인정보 문의 이메일: **dla3946gns@gmail.com**
- GitHub Actions는 앱 원문과 웹 문서의 내용이 같은지 `mobile/scripts/verify-privacy-policy.py`로 검사합니다.

## 1. PR 병합 후 GitHub Pages 활성화

GitHub Pages 설정은 저장소 소유자 권한으로 다음을 진행해야 합니다.

1. 저장소 [Settings → Pages](https://github.com/dla3946gns/stillnote/settings/pages)로 이동합니다.
2. `Build and deployment` → `Source`에서 **Deploy from a branch**를 선택합니다.
3. `Branch: main`, 폴더 **/docs**를 선택하고 **Save**합니다.
4. GitHub Pages 배포가 끝날 때까지 기다립니다.
5. 다음 **예정 URL**에 접속하여 문서가 실제로 보이는지 확인합니다.

`https://dla3946gns.github.io/stillnote/privacy/`

**중요:** 위 주소는 GitHub Pages 설정을 완료했을 때 사용하도록 준비한 경로로, 현재 공개·정상 접속을 보장하는 URL이 아닙니다. HTTPS 접속과 모바일 브라우저 표시, 발신 이메일 링크, 정책 본문 및 날짜가 모두 정상인지 확인하기 전에는 Google Play Console에 입력하지 마세요.

## 2. 게시 전 운영 주체 검토

- [ ] 실제 Play Console에 등록할 개발자/사업자 이름이 Anna Johnny Works와 일치하는지 확인
- [ ] 지원 이메일 `dla3946gns@gmail.com`으로 문의를 받을 수 있는지 확인
- [ ] 공개 개인정보처리방침의 사용 데이터·공유·보관 설명이 실제 최종 출시 AAB와 일치하는지 확인
- [ ] 운영 주체의 사업자 지위, 필수 공개사항 및 현지 개인정보 보호법에 따른 추가 고지가 필요한지 검토
- [ ] GitHub Pages 사이트에 개인정보처리방침의 최신본이 게시되고 공개 읽기 가능함을 확인
- [ ] 앱 내부 개인정보처리방침(오프라인) 동작을 실제 설치 기기에서 확인
- [ ] Google Play Console의 정책 URL과 Data safety 신고 내용 검토

## 3. 정책 수정 시 유지보수

정책 본문을 수정할 때는 **앱의 원문과 웹페이지를 같이 수정**하고 다음을 실행하세요.

```bash
python3 mobile/scripts/verify-privacy-policy.py
```

CI에서 둘이 다르면 실패합니다. 앱 버전을 출시한 뒤 새로운 정책을 적용할 경우 앱 업데이트/공개 페이지 갱신 및 변경 고지의 필요 여부를 검토해야 합니다.

## 계정 및 배포 범위

Google Play Console 개발자 계정이 없어도 GitHub Pages 정책 웹페이지는 준비할 수 있습니다. 다만 **실제 Pages 호스팅 설정, Play Console 등록, 법률상 사업자 확인 및 Google Play 게시가 완료됐다는 의미는 아닙니다.**
