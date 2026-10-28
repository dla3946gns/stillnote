/**
 * Verify Firebase Auth ID tokens in Workers without Firebase Admin credentials.
 * Uses Google's published Secure Token signing certificates; never trust the
 * user ID or email sent directly by the client.
 * https://firebase.google.com/docs/auth/admin/verify-id-tokens
 */
const CERTS_URL = "https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com";
let cachedCerts: Record<string, string> | undefined;
let cacheExpiresAt = 0;

export class InvalidIdToken extends Error {}
export class IdentityProviderUnavailable extends Error {}

function fromBase64Url(input: string): Uint8Array {
  if (!/^[A-Za-z0-9_-]+$/.test(input)) throw new InvalidIdToken("Invalid token encoding");
  const normalized = input.replace(/-/g, "+").replace(/_/g, "/");
  const bytes = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, "="));
  return Uint8Array.from(bytes, c => c.charCodeAt(0));
}

function decodeJson(input: string): unknown {
  try {
    return JSON.parse(new TextDecoder().decode(fromBase64Url(input)));
  } catch {
    throw new InvalidIdToken("Invalid token JSON");
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

async function getSigningCertificates(fetcher: typeof fetch): Promise<Record<string, string>> {
  const now = Date.now();
  // Cache only for the production fetcher, never across injected test sources.
  if (fetcher === fetch && cachedCerts && now < cacheExpiresAt) return cachedCerts;
  let response;
  try {
    response = await fetcher(CERTS_URL);
    if (!response.ok) throw new Error("Signing certificates unavailable");
    const certs: unknown = await response.json();
    if (!isRecord(certs) || !Object.values(certs).every(value => typeof value === "string")) {
      throw new Error("Invalid certificate data");
    }
    const cacheControl = response.headers.get("cache-control") || "";
    const maxAge = Number(/(?:^|,)\s*max-age=(\d+)/i.exec(cacheControl)?.[1] || 300);
    if (fetcher === fetch) {
      cachedCerts = certs as Record<string, string>;
      cacheExpiresAt = now + Math.max(60, Math.min(maxAge, 3600)) * 1000;
    }
    return certs as Record<string, string>;
  } catch {
    throw new IdentityProviderUnavailable("Unable to verify sign-in right now");
  }
}

export interface VerifyTokenOptions {
  fetcher?: typeof fetch;
  now?: () => number;
}

export async function verifyFirebaseIdToken(
  token: string,
  projectId: string,
  { fetcher = fetch, now = () => Date.now() }: VerifyTokenOptions = {}
): Promise<string> {
  if (typeof projectId !== "string" || !/^[a-zA-Z0-9-]{3,100}$/.test(projectId)) {
    throw new IdentityProviderUnavailable("Firebase project is not configured");
  }
  if (typeof token !== "string" || token.length > 8192 || token.length < 30) {
    throw new InvalidIdToken("Invalid token");
  }

  const pieces = token.split(".");
  if (pieces.length !== 3) throw new InvalidIdToken("Invalid token");
  const [headerPart, payloadPart, signaturePart] = pieces;
  const header = decodeJson(headerPart);
  const claims = decodeJson(payloadPart);

  if (!isRecord(header) || header.alg !== "RS256" || typeof header.kid !== "string" || !header.kid) {
    throw new InvalidIdToken("Invalid token header");
  }
  const seconds = Math.floor(now() / 1000);
  if (!isRecord(claims) || claims.aud !== projectId ||
      claims.iss !== `https://securetoken.google.com/${projectId}` ||
      typeof claims.sub !== "string" || !claims.sub || claims.sub.length > 128 ||
      typeof claims.exp !== "number" || !Number.isSafeInteger(claims.exp) || claims.exp <= seconds ||
      typeof claims.iat !== "number" || !Number.isSafeInteger(claims.iat) || claims.iat > seconds ||
      typeof claims.auth_time !== "number" || !Number.isSafeInteger(claims.auth_time) || claims.auth_time > seconds) {
    throw new InvalidIdToken("Invalid token claims");
  }

  const certs = await getSigningCertificates(fetcher);
  const pem = certs[header.kid];
  if (typeof pem !== "string") throw new InvalidIdToken("Unknown signing key");
  const publicKeyBytes = Uint8Array.from(
    atob(pem.replace(/-----BEGIN CERTIFICATE-----|-----END CERTIFICATE-----|\s/g, "")),
    c => c.charCodeAt(0)
  );
  // Firebase's x509 endpoint publishes X.509 certificates rather than bare
  // SPKI keys. crypto.subtle.importKey("spki") needs the SubjectPublicKeyInfo
  // portion, so parse ASN.1 certificate framing and extract the SPKI sequence.
  const spki = extractSubjectPublicKeyInfo(publicKeyBytes);
  let verified;
  try {
    const key = await crypto.subtle.importKey("spki", spki, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]);
    verified = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5", key, fromBase64Url(signaturePart),
      new TextEncoder().encode(`${headerPart}.${payloadPart}`)
    );
  } catch {
    throw new InvalidIdToken("Invalid signature");
  }
  if (!verified) throw new InvalidIdToken("Invalid signature");
  return claims.sub;
}

function derTlv(bytes: Uint8Array, at: number): { tag: number; start: number; end: number } {
  if (at >= bytes.length) throw new InvalidIdToken("Invalid certificate");
  const tag = bytes[at++];
  if (at >= bytes.length) throw new InvalidIdToken("Invalid certificate");
  let length = bytes[at++];
  if (length & 0x80) {
    const count = length & 0x7f;
    if (count === 0 || count > 4 || at + count > bytes.length) {
      throw new InvalidIdToken("Invalid certificate");
    }
    length = 0;
    for (let i = 0; i < count; i++) length = length * 256 + bytes[at++];
  }
  if (at + length > bytes.length) throw new InvalidIdToken("Invalid certificate");
  return { tag, start: at, end: at + length };
}

function extractSubjectPublicKeyInfo(cert: Uint8Array): Uint8Array {
  // Certificate ::= SEQUENCE { tbsCertificate, signatureAlgorithm, signatureValue }
  // TBSCertificate ::= SEQUENCE { [0] version OPTIONAL, serialNumber,
  // signature, issuer, validity, subject, subjectPublicKeyInfo, ... }
  const outer = derTlv(cert, 0);
  if (outer.tag !== 0x30 || outer.end !== cert.length) throw new InvalidIdToken("Invalid certificate");
  const tbs = derTlv(cert, outer.start);
  if (tbs.tag !== 0x30) throw new InvalidIdToken("Invalid certificate");
  let offset = tbs.start;
  if (cert[offset] === 0xa0) offset = derTlv(cert, offset).end;
  for (let i = 0; i < 5; i++) offset = derTlv(cert, offset).end;
  const spki = derTlv(cert, offset);
  if (spki.tag !== 0x30 || spki.end > tbs.end) throw new InvalidIdToken("Invalid certificate");
  return cert.slice(offset, spki.end);
}
