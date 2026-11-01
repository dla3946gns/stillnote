import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { createContext, SourceTextModule, SyntheticModule } from "node:vm";

// Exercise the real controller with isolated DOM/SDK boundaries. No network or accounts.
async function accountPage() {
  const html = await readFile(new URL("../../dist/account/index.html", import.meta.url), "utf8");
  const elements = new Map([...html.matchAll(/id="([^"]+)"/g)].map(([, id]) => [id, {
    value: "", checked: false, hidden: false, disabled: false, dataset: {}, handlers: {},
    addEventListener(type, action) { this.handlers[type] = action; },
    setAttribute() {}
  }]));
  const buttons = [...html.matchAll(/<button\b[^>]*id="([^"]+)"/g)].map(([, id]) => elements.get(id));
  const window = { handlers: {}, addEventListener(type, action) { this.handlers[type] = action; } };
  const calls = { register: 0, login: 0, reset: 0 };
  const sdk = {
    getAuth: () => ({}),
    onAuthStateChanged: (_, render) => render(null),
    createUserWithEmailAndPassword: async () => { calls.register++; return { user: {} }; },
    sendEmailVerification: async () => {},
    signInWithEmailAndPassword: async () => { calls.login++; },
    sendPasswordResetEmail: async () => { calls.reset++; }
  };
  const context = createContext({
    document: { getElementById: id => elements.get(id), querySelectorAll: () => buttons },
    location: { search: "" }, URLSearchParams, window
  });
  async function synthetic(exports) {
    const module = new SyntheticModule(Object.keys(exports), function () {
      for (const [key, value] of Object.entries(exports)) this.setExport(key, value);
    }, { context });
    await module.link(() => {});
    await module.evaluate();
    return module;
  }
  const rules = new SourceTextModule(await readFile(new URL("../../dist/account/rules.js", import.meta.url), "utf8"), { context });
  const app = new SourceTextModule(await readFile(new URL("../../dist/account/app.js", import.meta.url), "utf8"), {
    context,
    importModuleDynamically: url => {
      if (url.endsWith("/firebase-app.js")) return synthetic({ initializeApp: () => ({}), getApps: () => [] });
      if (url.endsWith("/firebase-auth.js")) return synthetic(sdk);
      throw new Error("Unexpected SDK import");
    }
  });
  await app.link(url => {
    if (url === "./rules.js") return rules;
    if (url === "./config.js") return synthetic({ firebaseConfig: {
      apiKey: "synthetic", authDomain: "example.test", projectId: "demo-test", appId: "synthetic"
    } });
    throw new Error("Unexpected local import");
  });
  await app.evaluate();
  const element = id => elements.get(id);
  const click = id => element(id).handlers.click();
  async function submit() {
    element("loginForm").handlers.submit({ preventDefault() {} });
    await new Promise(resolve => setImmediate(resolve));
  }
  return { element, click, submit, calls, window };
}

test("unchecked registration blocks direct form submission before SDK call", async () => {
  const page = await accountPage();
  page.click("switchRegister");
  assert.equal(page.element("accountCredentialFields").hidden, true);
  assert.equal(page.element("accountCredentialFields").disabled, true);
  page.element("accountEmailInput").value = "synthetic@example.test";
  page.element("accountPasswordInput").value = "Synthetic-test-only";
  await page.submit();
  assert.equal(page.calls.register, 0);
  assert.match(page.element("accountStatus").textContent, /만 18세/);
  page.element("registrationAgeConfirmed").checked = true;
  page.element("registrationAgeConfirmed").handlers.change();
  assert.equal(page.element("accountCredentialFields").hidden, false);
  assert.equal(page.element("accountCredentialFields").disabled, false);
  page.element("accountPasswordInput").value = "Synthetic-test-only";
  await page.submit();
  assert.equal(page.calls.register, 1);
  assert.equal(page.element("accountPasswordInput").value, "");
});

test("mode changes and page exit reset age state without restricting login/reset", async () => {
  const page = await accountPage();
  page.click("switchRegister");
  page.element("registrationAgeConfirmed").checked = true;
  page.element("registrationAgeConfirmed").handlers.change();
  page.click("switchLogin");
  assert.equal(page.element("registrationAgeConfirmed").checked, false);
  page.element("accountEmailInput").value = "synthetic@example.test";
  page.element("accountPasswordInput").value = "Synthetic-test-only";
  await page.submit();
  assert.equal(page.calls.login, 1);
  page.click("switchReset");
  await page.submit();
  assert.equal(page.calls.reset, 1);
  page.click("switchRegister");
  assert.equal(page.element("accountCredentialFields").hidden, true);
  page.element("registrationAgeConfirmed").checked = true;
  page.element("registrationAgeConfirmed").handlers.change();
  page.window.handlers.pagehide();
  assert.equal(page.element("registrationAgeConfirmed").checked, false);
  assert.equal(page.element("accountCredentialFields").hidden, true);
});
