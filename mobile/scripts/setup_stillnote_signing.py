#!/usr/bin/env python3
"""Local-only Stillnote upload signing setup. Never send credentials to ChatGPT."""

import base64
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

REPOSITORY = "dla3946gns/stillnote"
ENVIRONMENT = "android-release"
KEY_ALIAS_DEFAULT = "stillnote-upload"
SECRETS = (
    "STILLNOTE_UPLOAD_KEYSTORE_BASE64",
    "STILLNOTE_UPLOAD_STORE_PASSWORD",
    "STILLNOTE_UPLOAD_KEY_ALIAS",
    "STILLNOTE_UPLOAD_KEY_PASSWORD",
)


def run(cmd, *, stdin=None, env=None, allow_failure=False):
    """Keep secret values out of command arguments and terminal output."""
    result = subprocess.run(
        cmd, input=stdin, text=True, capture_output=True, env=env, check=False
    )
    if result.returncode and not allow_failure:
        details = (result.stderr or result.stdout).strip()
        raise RuntimeError("Command failed: " + " ".join(cmd[:3]) +
                           ("\n" + details[:1200] if details else ""))
    return result


def ask_choice(question, allowed):
    while True:
        reply = input(question).strip().lower()
        if reply in allowed:
            return reply
        print("가능한 선택:", ", ".join(sorted(allowed)))


def confirm_phrase(phrase, message):
    print(message)
    return input("계속하려면 " + repr(phrase) + " 입력: ").strip() == phrase


def password_prompt(label, confirm=False):
    value = getpass.getpass(label + ": ")
    if not value:
        raise ValueError("비밀번호는 비어 있을 수 없습니다.")
    if confirm and getpass.getpass(label + " 다시 입력: ") != value:
        raise ValueError("비밀번호 확인이 일치하지 않습니다.")
    if confirm and len(value) < 12:
        raise ValueError("새 업로드 키 비밀번호는 12자 이상이어야 합니다.")
    return value


def keytool_env(store_password, key_password):
    env = os.environ.copy()
    env["STILLNOTE_LOCAL_STORE_PASSWORD"] = store_password
    env["STILLNOTE_LOCAL_KEY_PASSWORD"] = key_password
    return env


def public_certificate_fingerprint(path, alias, store_password, key_password):
    env = keytool_env(store_password, key_password)
    output = run(
        ["keytool", "-list", "-v", "-keystore", str(path), "-alias", alias,
         "-storepass:env", "STILLNOTE_LOCAL_STORE_PASSWORD"], env=env
    ).stdout
    if "PrivateKeyEntry" not in output:
        raise RuntimeError("선택한 별칭은 개인키(PrivateKeyEntry)가 아닙니다.")
    match = re.search(r"SHA\s*256\s*:\s*([0-9A-Fa-f:]{60,})",
                      output, re.IGNORECASE)
    if not match:
        raise RuntimeError("SHA-256 인증서 지문 추출 실패: keytool 설정을 확인하세요.")
    # Listing a certificate doesn't verify the private key password.
    # A temporary CSR demonstrates the key can actually be unlocked.
    with tempfile.TemporaryDirectory(prefix="stillnote-key-check-") as directory:
        csr = Path(directory) / "verify.csr"
        run(
            ["keytool", "-certreq", "-keystore", str(path), "-alias", alias,
             "-storepass:env", "STILLNOTE_LOCAL_STORE_PASSWORD",
             "-keypass:env", "STILLNOTE_LOCAL_KEY_PASSWORD",
             "-file", str(csr)], env=env
        )
        if not csr.is_file() or csr.stat().st_size == 0:
            raise RuntimeError("개인키 비밀번호 검증 실패")
    return match.group(1).upper()


def create_new_key(path):
    if path.exists():
        raise RuntimeError("기존 파일을 덮어쓰지 않습니다: " + str(path))
    if not confirm_phrase(
        "CREATE NEW",
        "Play Console의 기존 업로드 인증서와 키를 먼저 확인하세요.\n"
        "기존 서명키가 있다면 신규 생성하면 안 됩니다.",
    ):
        raise RuntimeError("새 키 생성이 취소되었습니다.")
    password = password_prompt(
        "신규 키 저장소/별칭 비밀번호 (비밀번호 관리자에 보관)", confirm=True)
    # Don't change permissions of a directory that already exists.
    new_parent = not path.parent.exists()
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    if new_parent:
        try:
            os.chmod(path.parent, 0o700)
        except OSError:
            pass
    run(
        ["keytool", "-genkeypair", "-v", "-keystore", str(path),
         "-alias", KEY_ALIAS_DEFAULT, "-keyalg", "RSA", "-keysize", "3072",
         "-validity", "10000",
         "-dname", "CN=Stillnote Upload, O=Anna Johnny Works",
         "-storetype", "JKS",
         "-storepass:env", "STILLNOTE_LOCAL_STORE_PASSWORD",
         "-keypass:env", "STILLNOTE_LOCAL_KEY_PASSWORD", "-noprompt"],
        env=keytool_env(password, password),
    )
    if not path.is_file() or path.stat().st_size == 0:
        raise RuntimeError("새 키 파일 확인 실패")
    try:
        os.chmod(path, 0o600)
    except OSError:
        pass
    return KEY_ALIAS_DEFAULT, password, password


def use_existing_key(path):
    if not path.is_file():
        raise RuntimeError("키 파일이 없습니다: " + str(path))
    alias = input("기존 키 별칭 [stillnote-upload]: ").strip() or KEY_ALIAS_DEFAULT
    store_password = password_prompt("기존 키 저장소 비밀번호")
    key_password = getpass.getpass(
        "기존 키 별칭 비밀번호 [Enter=저장소 비밀번호 사용]: ") or store_password
    return alias, store_password, key_password


def verified_backup(path):
    print("\n원본과 별도의 안전한 장소에 키 사본을 보관하고 비밀번호 관리자에 기록하세요.")
    directory = input("백업 폴더 전체 경로 (Enter=취소): ").strip().strip('"')
    if not directory:
        raise RuntimeError("백업 위치가 없어 Secrets 등록을 중단했습니다.")
    folder = Path(directory).expanduser().resolve()
    if not folder.is_dir():
        raise RuntimeError("백업 폴더가 존재하지 않습니다: " + str(folder))
    target = folder / path.name
    if target.resolve() == path.resolve():
        raise RuntimeError("원본과 같은 파일은 백업으로 인정하지 않습니다.")
    if target.exists():
        if target.read_bytes() != path.read_bytes():
            raise RuntimeError("서로 다른 키가 있어 덮어쓰지 않습니다: " + str(target))
        print("기존 키와 동일한 백업 확인:", target)
    else:
        shutil.copy2(path, target)
        try:
            os.chmod(target, 0o600)
        except OSError:
            pass
        if target.read_bytes() != path.read_bytes():
            raise RuntimeError("백업 내용 검증 실패")
        print("백업 생성 및 바이트 일치 확인:", target)
    if not confirm_phrase(
        "BACKED UP",
        "백업이 다른 안전한 장소에 있으며 비밀번호도 별도로 보관했나요?",
    ):
        raise RuntimeError("백업 확인을 완료하지 않아 중단했습니다.")


def ensure_github_environment():
    run(["gh", "auth", "status"])
    response = run(
        ["gh", "api", "repos/" + REPOSITORY + "/environments/" + ENVIRONMENT],
        allow_failure=True,
    )
    if response.returncode:
        # A 403 or another access failure is not proof the environment is absent.
        if "404" not in response.stderr and "404" not in response.stdout:
            raise RuntimeError("GitHub 환경 조회 권한 또는 연결 상태를 확인하세요.")
        if not confirm_phrase(
            "CREATE ENV", "GitHub android-release 환경이 없습니다. 생성하시겠습니까?"
        ):
            raise RuntimeError("GitHub 환경 미설정으로 중단합니다.")
        run(["gh", "api", "--method", "PUT",
             "repos/" + REPOSITORY + "/environments/" + ENVIRONMENT])
    response = run(
        ["gh", "secret", "list", "--env", ENVIRONMENT, "--repo", REPOSITORY,
         "--json", "name"])
    existing = {x.get("name") for x in json.loads(response.stdout or "[]")}
    overlapping = sorted(existing.intersection(SECRETS))
    if overlapping:
        print("기존 Secret 이름:", ", ".join(overlapping))
        if not confirm_phrase(
            "REPLACE",
            "Secrets의 기존 값은 조회할 수 없습니다. 등록 시 기존 값이 교체됩니다."
        ):
            raise RuntimeError("기존 Secret을 보호하기 위해 중단합니다.")


def register_secrets(path, alias, store_password, key_password):
    data = {
        "STILLNOTE_UPLOAD_KEYSTORE_BASE64": base64.b64encode(
            path.read_bytes()).decode("ascii"),
        "STILLNOTE_UPLOAD_STORE_PASSWORD": store_password,
        "STILLNOTE_UPLOAD_KEY_ALIAS": alias,
        "STILLNOTE_UPLOAD_KEY_PASSWORD": key_password,
    }
    for name, value in data.items():
        # gh encrypts the value locally before the secret is stored on GitHub.
        run(["gh", "secret", "set", name, "--env", ENVIRONMENT,
             "--repo", REPOSITORY], stdin=value)
        print("Secret 등록:", name, "(값 표시 안 함)")
    current = run(
        ["gh", "secret", "list", "--env", ENVIRONMENT, "--repo", REPOSITORY,
         "--json", "name"])
    names = {x.get("name") for x in json.loads(current.stdout or "[]")}
    missing = set(SECRETS) - names
    if missing:
        raise RuntimeError("등록되지 않은 Secret: " + ", ".join(sorted(missing)))
    print("Secrets 네 개의 이름 등록을 확인했습니다. 값은 읽지 않았습니다.")


def main():
    print("Stillnote Google Play 업로드 서명키 - 로컬 전용 설정\n")
    for binary in ("keytool", "gh"):
        if not shutil.which(binary):
            raise RuntimeError(binary + " 실행파일이 없습니다. 릴리스 가이드를 확인하세요.")
    print("저장소:", REPOSITORY, "/ 보호 환경:", ENVIRONMENT)
    choice = ask_choice("기존 키(e), 신규 키(n), 종료(q): ", {"e", "n", "q"})
    if choice == "q":
        return
    if choice == "n":
        default = Path.home() / "StillnoteSigning" / "stillnote-upload.jks"
        typed = input("새 키 저장 경로 [" + str(default) + "]: ").strip().strip('"')
        path = Path(typed).expanduser().resolve() if typed else default
        alias, store_password, key_password = create_new_key(path)
    else:
        typed = input("기존 업로드 키(.jks 등) 전체 경로: ").strip().strip('"')
        if not typed:
            raise RuntimeError("기존 키 경로를 입력해야 합니다.")
        path = Path(typed).expanduser().resolve()
        alias, store_password, key_password = use_existing_key(path)

    fingerprint = public_certificate_fingerprint(
        path, alias, store_password, key_password)
    print("\n업로드 인증서 SHA-256:", fingerprint)
    print("Play Console의 '업로드 키 인증서' 지문과 비교하세요.")
    if not confirm_phrase(
        "CERT OK",
        "기존 등록된 업로드 인증서와 일치하거나 최초 등록임을 확인했나요?"
    ):
        raise RuntimeError("인증서 미확인: GitHub 비밀값을 변경하지 않습니다.")
    verified_backup(path)
    ensure_github_environment()
    if not confirm_phrase("UPLOAD", "실제 업로드 키와 암호를 GitHub 비공개 Secrets에 등록할까요?"):
        raise RuntimeError("비밀값 등록을 취소했습니다.")
    register_secrets(path, alias, store_password, key_password)
    print("\nhttps://github.com/" + REPOSITORY +
          "/actions/workflows/android-release.yml")
    if confirm_phrase("BUILD", "정식 서명 AAB 빌드를 요청할까요? Play 업로드는 없습니다."):
        run(["gh", "workflow", "run", "android-release.yml", "--ref", "main",
             "--repo", REPOSITORY])
        print("GitHub Actions 정식 AAB 빌드를 요청했습니다.")
    else:
        print("Secrets 등록까지만 완료했습니다. 빌드는 요청하지 않았습니다.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyboardInterrupt) as exc:
        print("\n안전하게 중단:", str(exc), file=sys.stderr)
        sys.exit(1)
