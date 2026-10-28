import {
  verifyFirebaseIdToken,
  InvalidIdToken,
  IdentityProviderUnavailable
} from "./firebase-auth.mjs";

const PLUS_ENTITLEMENT = "stillnote_plus";
const HEADERS = {
  "content-type": "application/json; charset=utf-8",
  "cache-control": "no-store",
  "x-content-type-options": "nosniff",
  "vary": "Authorization"
};

function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: HEADERS });
}

/**
 * Test-only dependency injection; production default always uses the real
 * cryptographic Firebase token verifier. No HTTP header can bypass verification.
 */
export function createHandler({ verifyToken = verifyFirebaseIdToken } = {}) {
  return async function handle(request, env) {
    const path = new URL(request.url).pathname;
    if (path === "/health" && request.method === "GET") {
      return json({ ok: true, service: "stillnote-entitlements" });
    }
    if (path !== "/v1/entitlement") return json({ error: "not_found" }, 404);
    if (request.method !== "GET") return json({ error: "method_not_allowed" }, 405);

    const authorization = request.headers.get("authorization") || "";
    if (!authorization.startsWith("Bearer ") || authorization.length > 8200) {
      return json({ error: "authentication_required" }, 401);
    }
    if (!env?.FIREBASE_PROJECT_ID || !env?.DB?.prepare) {
      return json({ error: "service_unavailable" }, 503);
    }

    let userId;
    try {
      userId = await verifyToken(authorization.slice(7), env.FIREBASE_PROJECT_ID);
    } catch (error) {
      if (error instanceof IdentityProviderUnavailable) return json({ error: "identity_provider_unavailable" }, 503);
      if (error instanceof InvalidIdToken) return json({ error: "invalid_identity_token" }, 401);
      // Fail closed. Do not surface token contents or internal details.
      return json({ error: "service_unavailable" }, 503);
    }
    if (typeof userId !== "string" || !userId) {
      return json({ error: "invalid_identity_token" }, 401);
    }

    try {
      const row = await env.DB.prepare(
        "SELECT 1 AS found FROM verified_purchases " +
        "WHERE firebase_uid = ? AND entitlement_key = ? AND status = 'active' LIMIT 1"
      ).bind(userId, PLUS_ENTITLEMENT).first();
      return json({ entitlement: row ? "plus" : "free", hasPlus: Boolean(row) });
    } catch {
      return json({ error: "entitlement_unavailable" }, 503);
    }
  };
}

const productionHandler = createHandler();
export default { fetch: (request, env) => productionHandler(request, env) };
