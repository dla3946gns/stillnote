import test from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import {
  InvalidIdToken,
  IdentityProviderUnavailable,
  verifyFirebaseIdToken
} from "../dist/firebase-auth.js";

const enc = new TextEncoder();
const projectId = "stillnote-free-test";
const nowSec = 1780000000;
const toB64Url = bytes => Buffer.from(bytes).toString("base64url");
function tlv(tag, body) {
  const content = Buffer.from(body);
  const length = content.length < 128
    ? Buffer.from([content.length])
    : content.length < 256
      ? Buffer.from([0x81, content.length])
      : Buffer.from([0x82, content.length >> 8, content.length & 255]);
  return Buffer.concat([Buffer.from([tag]), length, content]);
}
let keys;
let certPem;

async function setup() {
  if (keys) return;
  keys = await webcrypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true, ["sign", "verify"]
  );
  const spki = Buffer.from(await webcrypto.subtle.exportKey("spki", keys.publicKey));
  // Minimal DER fixture shaped like X.509 certificate; used only to test
  // retrieval of SPKI and signature verification, not certificate trust.
  const sequence = (...parts) => tlv(0x30, Buffer.concat(parts));
  const algorithm = sequence();
  const tbs = sequence(
    tlv(0xa0, tlv(0x02, [2])), // version
    tlv(0x02, [1]),            // serial
    algorithm,                 // signature
    sequence(),                // issuer
    sequence(),                // validity
    sequence(),                // subject
    spki                       // SPKI
  );
  const der = sequence(tbs, algorithm, tlv(0x03, [0]));
  const b64 = der.toString("base64").match(/.{1,64}/g).join("\n");
  certPem = `-----BEGIN CERTIFICATE-----\n${b64}\n-----END CERTIFICATE-----\n`;
}

async function makeToken(overrides = {}) {
  await setup();
  const header = { alg: "RS256", kid: "unit-test-key" };
  const payload = {
    aud: projectId,
    iss: `https://securetoken.google.com/${projectId}`,
    sub: "user-from-verified-firebase-jwt",
    exp: nowSec + 1800,
    iat: nowSec - 30,
    auth_time: nowSec - 300,
    ...overrides
  };
  const unsigned = `${toB64Url(JSON.stringify(header))}.${toB64Url(JSON.stringify(payload))}`;
  const signature = new Uint8Array(await webcrypto.subtle.sign(
    "RSASSA-PKCS1-v1_5", keys.privateKey, enc.encode(unsigned)
  ));
  return `${unsigned}.${toB64Url(signature)}`;
}

function goodFetcher() {
  return async url => {
    assert.match(url, /securetoken/);
    return new Response(JSON.stringify({ "unit-test-key": certPem }), {
      status: 200, headers: { "cache-control": "public, max-age=3600" }
    });
  };
}

test("accepts correctly signed Firebase token and returns only verified UID", async () => {
  const token = await makeToken();
  const user = await verifyFirebaseIdToken(token, projectId, {
    fetcher: goodFetcher(), now: () => nowSec * 1000
  });
  assert.equal(user, "user-from-verified-firebase-jwt");
});

test("rejects wrong project, issuer, expired and future-issued tokens", async () => {
  for (const overrides of [
    { aud: "another-project" },
    { iss: "https://securetoken.google.com/attacker" },
    { exp: nowSec - 1 },
    { iat: nowSec + 1 },
    { auth_time: nowSec + 1 },
    { sub: "" }
  ]) {
    await assert.rejects(
      verifyFirebaseIdToken(await makeToken(overrides), projectId, {
        fetcher: goodFetcher(), now: () => nowSec * 1000
      }),
      InvalidIdToken
    );
  }
});

test("rejects tampered signature even when claims look valid", async () => {
  const token = await makeToken();
  const parts = token.split(".");
  parts[1] = toB64Url(JSON.stringify({
    aud: projectId, iss: `https://securetoken.google.com/${projectId}`,
    sub: "attacker", exp: nowSec + 1800, iat: nowSec - 30,
    auth_time: nowSec - 300
  }));
  await assert.rejects(
    verifyFirebaseIdToken(parts.join("."), projectId, {
      fetcher: goodFetcher(), now: () => nowSec * 1000
    }), InvalidIdToken
  );
});

test("rejects unknown signing key and fails closed if Google certs unavailable", async () => {
  const token = await makeToken();
  await assert.rejects(
    verifyFirebaseIdToken(token, projectId, {
      fetcher: async () => new Response("{}", { status: 200 }),
      now: () => nowSec * 1000
    }), InvalidIdToken
  );
  await assert.rejects(
    verifyFirebaseIdToken(token, projectId, {
      fetcher: async () => new Response("", { status: 503 }),
      now: () => nowSec * 1000
    }), IdentityProviderUnavailable
  );
});

test("rejects malformed bearer payload before network access", async () => {
  let called = false;
  await assert.rejects(
    verifyFirebaseIdToken("not-a-firebase-token", projectId, {
      fetcher: async () => { called = true; return new Response("{}"); }
    }), InvalidIdToken
  );
  assert.equal(called, false);
});
