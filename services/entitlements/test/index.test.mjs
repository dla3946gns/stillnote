import test from "node:test";
import assert from "node:assert/strict";
import { createHandler } from "../dist/index.js";
import { InvalidIdToken, IdentityProviderUnavailable } from "../dist/firebase-auth.js";

const url = "https://api.example.invalid/v1/entitlement";
const get = (auth = "Bearer signed-valid-token", method = "GET") =>
  new Request(url, { method, headers: auth ? { authorization: auth } : {} });

function environment(row = null) {
  const queries = [];
  const env = {
    FIREBASE_PROJECT_ID: "stillnote-free-test",
    DB: {
      prepare(sql) {
        assert.match(sql, /verified_purchases/);
        return {
          bind(...args) {
            queries.push(args);
            return { first: async () => row };
          }
        };
      }
    }
  };
  return { env, queries };
}

test("health endpoint contains no purchases or user data", async () => {
  const handle = createHandler();
  const response = await handle(new Request("https://api.example.invalid/health"), {});
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { ok: true, service: "stillnote-entitlements" });
});

test("valid UID with no purchases keeps free access", async () => {
  const { env, queries } = environment(null);
  const handle = createHandler({ verifyToken: async () => "user-a" });
  const response = await handle(get(), env);
  assert.deepEqual(await response.json(), { entitlement: "free", hasPlus: false });
  assert.deepEqual(queries, [["user-a", "stillnote_plus"]]);
  assert.equal(response.headers.get("cache-control"), "no-store");
});

test("purchase from either platform enables Plus for same UID", async () => {
  const { env } = environment({ found: 1 });
  const handle = createHandler({ verifyToken: async () => "user-a" });
  const response = await handle(get(), env);
  assert.deepEqual(await response.json(), { entitlement: "plus", hasPlus: true });
});

test("no identity, unsupported methods and missing bindings fail closed", async () => {
  const handle = createHandler({ verifyToken: async () => "user-a" });
  const { env } = environment();
  assert.equal((await handle(get(null), env)).status, 401);
  assert.equal((await handle(get("Basic xyz"), env)).status, 401);
  assert.equal((await handle(get("Bearer abc", "POST"), env)).status, 405);
  assert.equal((await handle(get(), { FIREBASE_PROJECT_ID: "x" })).status, 503);
  assert.equal((await handle(new Request("https://api.example.invalid/v1/purchases/google"), env)).status, 404);
  assert.equal((await handle(new Request("https://api.example.invalid/v1/purchases/apple"), env)).status, 404);
});

test("invalid ID tokens never run DB queries", async () => {
  const { env, queries } = environment({ found: 1 });
  const handle = createHandler({ verifyToken: async () => { throw new InvalidIdToken(); } });
  const response = await handle(get(), env);
  assert.equal(response.status, 401);
  assert.deepEqual(queries, []);
});

test("Google certificate outage does not grant Plus or downgrade to free", async () => {
  const { env } = environment(null);
  const handle = createHandler({ verifyToken: async () => {
    throw new IdentityProviderUnavailable();
  } });
  const response = await handle(get(), env);
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: "identity_provider_unavailable" });
});

test("D1 outage returns an error, not a false free entitlement", async () => {
  const { env } = environment();
  env.DB.prepare = () => { throw new Error("DB offline"); };
  const handle = createHandler({ verifyToken: async () => "user-a" });
  const response = await handle(get(), env);
  assert.equal(response.status, 503);
  assert.deepEqual(await response.json(), { error: "entitlement_unavailable" });
});
