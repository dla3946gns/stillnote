import test from "node:test";
import assert from "node:assert/strict";
import { normalizedEmail, validateInput, authErrorMessage } from "../../dist/account/rules.js";

test("email normalization trims only surrounding whitespace", () => {
  assert.equal(normalizedEmail("  Person+TEST@example.com  "), "Person+TEST@example.com");
  assert.equal(normalizedEmail("UPPER@example.com"), "UPPER@example.com");
});
test("email syntax validation is shared by login, registration, and reset", () => {
  assert.match(validateInput("missing-at-sign", "secret", "login"), /이메일/);
  assert.equal(validateInput("name@example.com", "secret", "login"), null);
  assert.equal(validateInput("name@example.com", "", "reset"), null);
  assert.match(validateInput("name@example.com", "", "register", true), /비밀번호/);
});

test("registration requires explicit age confirmation while login and reset do not", () => {
  for (const value of [undefined, false, null, "true", 1]) {
    assert.match(validateInput("name@example.com", "secret", "register", value), /만 18세/);
  }
  assert.equal(validateInput("name@example.com", "secret", "register", true), null);
  assert.match(validateInput("invalid", "secret", "register", true), /이메일/);
  assert.equal(validateInput("name@example.com", "secret", "login", false), null);
  assert.equal(validateInput("name@example.com", "", "reset", false), null);
});
test("Firebase credential error messages do not echo secrets", () => {
  for (const code of ["auth/invalid-credential", "auth/user-not-found", "auth/wrong-password"]) {
    assert.equal(authErrorMessage({code}), "이메일과 비밀번호를 확인해 주세요.");
  }
  assert.match(authErrorMessage({code:"auth/network-request-failed"}), /네트워크/);
  assert.match(authErrorMessage({code:"stillnote/session-changed"}), /로그인/);
  assert.equal(authErrorMessage({code:"auth/unknown"}).includes("auth/unknown"), false);
});
