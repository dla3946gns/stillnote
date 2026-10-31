// Shared client-side presentation rules. Firebase is the authority on credentials.
export const normalizedEmail = value => value.trim();

export function validateInput(email, password, mode) {
  const candidate = normalizedEmail(email);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(candidate)) return "이메일 주소를 확인해 주세요.";
  if (mode !== "reset" && !password) return "비밀번호를 입력해 주세요.";
  return null;
}

export function authErrorMessage(error) {
  const code = error && error.code;
  switch (code) {
    case "auth/invalid-email": return "이메일 주소를 확인해 주세요.";
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
    case "auth/user-disabled": return "이메일과 비밀번호를 확인해 주세요.";
    case "auth/email-already-in-use": return "이 이메일은 이미 등록되어 있어요.";
    case "auth/weak-password": return "계정의 비밀번호 보안 조건을 충족하지 못했어요.";
    case "auth/too-many-requests": return "요청이 많아요. 잠시 후 다시 시도해 주세요.";
    case "auth/network-request-failed": return "네트워크 연결을 확인해 주세요.";
    case "auth/requires-recent-login": return "다시 로그인한 뒤 시도해 주세요.";
    case "auth/operation-not-allowed": return "이메일 인증이 비활성화되어 있어요. 관리자에게 문의해 주세요.";
    case "auth/unauthorized-domain": return "웹사이트 주소가 Firebase 인증에 등록되지 않았어요.";
    case "stillnote/session-changed": return "로그인 상태가 변경됐어요. 다시 확인해 주세요.";
    default: return "요청을 처리하지 못했어요. 잠시 후 다시 시도해 주세요.";
  }
}
