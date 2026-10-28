-- Only SERVER-VERIFIED Google/Apple purchases may be inserted.
-- Do not store raw purchase tokens, receipts, memo contents or email addresses.
-- The fingerprint is SHA-256 of a store-scoped receipt/transaction identifier;
-- it must be computed by the trusted purchase verification service.
CREATE TABLE IF NOT EXISTS verified_purchases (
  platform TEXT NOT NULL CHECK (platform IN ('google_play', 'app_store')),
  transaction_fingerprint TEXT NOT NULL,
  firebase_uid TEXT NOT NULL,
  entitlement_key TEXT NOT NULL CHECK (entitlement_key = 'stillnote_plus'),
  store_product_id TEXT NOT NULL,
  status TEXT NOT NULL CHECK (status IN ('active', 'revoked')),
  verified_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (platform, transaction_fingerprint)
);
CREATE INDEX IF NOT EXISTS idx_verified_purchases_entitlement
  ON verified_purchases (firebase_uid, entitlement_key, status);
