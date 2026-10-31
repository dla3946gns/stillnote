import { firebaseConfig } from "./config.js";
import { authErrorMessage, normalizedEmail, validateInput } from "./rules.js";

const element = id => document.getElementById(id);
const loginPanel = element("loginPanel");
const userPanel = element("userPanel");
const loginForm = element("loginForm");
const deleteForm = element("deleteForm");
const email = element("accountEmailInput");
const password = element("accountPasswordInput");
const deletePassword = element("deletePassword");
const deletionConsent = element("deletionConsent");
const status = element("accountStatus");
const accountTitle = element("accountTitle");
const passwordRow = element("passwordRow");
const loginSubmit = element("loginSubmit");
const switchLogin = element("switchLogin");
const switchRegister = element("switchRegister");
const switchReset = element("switchReset");
const signOutButton = element("signOutButton");
const verificationButton = element("verificationButton");
const userEmail = element("userEmail");
const deleteToggle = element("deleteToggle");
const allButtons = [...document.querySelectorAll("button")];

const pageIntentDelete = new URLSearchParams(location.search).get("mode") === "delete";
let mode = "login";
let busy = false;
let auth;
let sdk;

function report(message, isError = false) {
  status.textContent = message;
  status.dataset.error = String(isError);
}

function setBusy(value) {
  busy = value;
  for (const button of allButtons) button.disabled = value;
  loginSubmit.textContent = value ? "확인 중…" : ({login:"로그인",register:"계정 만들기",reset:"재설정 메일 요청"})[mode];
}

function selectMode(next) {
  if (busy) return;
  mode = next;
  password.value = "";
  deletePassword.value = "";
  deletionConsent.checked = false;
  deleteForm.hidden = true;
  passwordRow.hidden = next === "reset";
  password.required = next !== "reset";
  password.autocomplete = next === "register" ? "new-password" : "current-password";
  accountTitle.textContent = ({login:"로그인",register:"회원가입",reset:"비밀번호 재설정"})[next];
  switchLogin.setAttribute("aria-pressed", String(next === "login"));
  switchRegister.setAttribute("aria-pressed", String(next === "register"));
  switchReset.setAttribute("aria-pressed", String(next === "reset"));
  loginSubmit.textContent = ({login:"로그인",register:"계정 만들기",reset:"재설정 메일 요청"})[next];
  report(pageIntentDelete && next === "login"
    ? "회원 탈퇴를 요청하려면 먼저 Stillnote 계정으로 로그인해 주세요."
    : "계정은 메모를 자동으로 동기화하지 않습니다.");
}

function renderUser(user) {
  if (user) {
    loginPanel.hidden = true;
    userPanel.hidden = false;
    userEmail.textContent = user.email || "이메일이 없는 계정";
    verificationButton.hidden = !!user.emailVerified;
    deleteForm.hidden = !pageIntentDelete;
  } else {
    userPanel.hidden = true;
    loginPanel.hidden = false;
    deleteForm.hidden = true;
    password.value = "";
    deletePassword.value = "";
    deletionConsent.checked = false;
  }
}

async function execute(action) {
  if (busy || !sdk) return;
  setBusy(true);
  try {
    await action();
  } catch (error) {
    report(authErrorMessage(error), true);
  } finally {
    password.value = "";
    deletePassword.value = "";
    setBusy(false);
  }
}

switchLogin.addEventListener("click", () => selectMode("login"));
switchRegister.addEventListener("click", () => selectMode("register"));
switchReset.addEventListener("click", () => selectMode("reset"));
deleteToggle.addEventListener("click", () => {
  if (busy) return;
  deleteForm.hidden = !deleteForm.hidden;
  deletePassword.value = "";
  deletionConsent.checked = false;
});
loginForm.addEventListener("submit", event => {
  event.preventDefault();
  const typedEmail = normalizedEmail(email.value);
  const typedPassword = password.value;
  const problem = validateInput(typedEmail, typedPassword, mode);
  if (problem) return report(problem, true);
  execute(async () => {
    if (mode === "login") {
      await sdk.signInWithEmailAndPassword(auth, typedEmail, typedPassword);
      report("로그인했어요. 이 브라우저의 메모는 그대로 유지돼요.");
    } else if (mode === "register") {
      const credential = await sdk.createUserWithEmailAndPassword(auth, typedEmail, typedPassword);
      try {
        await sdk.sendEmailVerification(credential.user);
        report("계정을 만들었어요. 이메일 확인 메일을 확인해 주세요.");
      } catch {
        report("계정은 만들었지만 확인 메일을 보내지 못했어요. 아래에서 다시 요청할 수 있어요.");
      }
    } else {
      try {
        await sdk.sendPasswordResetEmail(auth, typedEmail);
      } catch (error) {
        // Do not disclose whether this email is registered.
        if (error.code !== "auth/user-not-found") throw error;
      }
      report("등록된 주소라면 비밀번호 재설정 메일을 받을 수 있어요.");
    }
  });
});
signOutButton.addEventListener("click", () => execute(async () => {
  await sdk.signOut(auth);
  selectMode("login");
  report("로그아웃했어요. 이 브라우저의 메모는 남아 있어요.");
}));
verificationButton.addEventListener("click", () => execute(async () => {
  const user = auth.currentUser;
  if (!user) throw { code: "stillnote/session-changed" };
  await sdk.sendEmailVerification(user);
  report("이메일 확인 메일을 보냈어요.");
}));
deleteForm.addEventListener("submit", event => {
  event.preventDefault();
  const typedPassword = deletePassword.value;
  const user = auth.currentUser;
  if (!user || !user.email) return report("로그인 상태를 다시 확인해 주세요.", true);
  if (!deletionConsent.checked || !typedPassword) return report("현재 비밀번호와 삭제 동의를 확인해 주세요.", true);
  const uid = user.uid;
  execute(async () => {
    await sdk.reauthenticateWithCredential(user, sdk.EmailAuthProvider.credential(user.email, typedPassword));
    if (!auth.currentUser || auth.currentUser.uid !== uid) throw { code: "stillnote/session-changed" };
    await sdk.deleteUser(user);
    selectMode("login");
    report("Stillnote 계정을 삭제했어요. 이 브라우저의 메모와 별도 백업은 삭제하지 않았어요.");
  });
});

selectMode("login");
if (!firebaseConfig || !["apiKey", "authDomain", "projectId", "appId"]
    .every(key => typeof firebaseConfig[key] === "string" && firebaseConfig[key].trim())) {
  loginPanel.hidden = true;
  userPanel.hidden = true;
  report("아직 Firebase 웹 앱 설정이 연결되지 않았어요. 계정 기능은 준비 중입니다.", true);
} else {
  try {
    const [{ initializeApp, getApps }, firebaseAuth] = await Promise.all([
      import("https://www.gstatic.com/firebasejs/13.1.0/firebase-app.js"),
      import("https://www.gstatic.com/firebasejs/13.1.0/firebase-auth.js")
    ]);
    sdk = firebaseAuth;
    const app = getApps()[0] || initializeApp(firebaseConfig);
    auth = sdk.getAuth(app);
    sdk.onAuthStateChanged(auth, renderUser, error => report(authErrorMessage(error), true));
  } catch {
    loginPanel.hidden = true;
    userPanel.hidden = true;
    report("계정 기능을 불러오지 못했어요. 네트워크 연결 또는 웹 설정을 확인해 주세요.", true);
  }
}
