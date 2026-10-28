import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { renderFreeConfig } from "../scripts/configure-free.mjs";

const template = await readFile(new URL("../wrangler.example.toml", import.meta.url), "utf8");
const example = {
  firebaseProjectId: "stillnote-auth-free",
  d1DatabaseId: "123e4567-e89b-42d3-a456-426614174000"
};

test("creates typed Worker config with sanitized project and database IDs", () => {
  const result = renderFreeConfig(template, example);
  assert.match(result, /main = "src\/index\.ts"/);
  assert.match(result, /binding = "DB"/);
  assert.match(result, /FIREBASE_PROJECT_ID = "stillnote-auth-free"/);
  assert.match(result, /database_id = "123e4567-e89b-42d3-a456-426614174000"/);
  assert.doesNotMatch(result, /REPLACE_WITH_YOUR_/);
});

test("rejects invalid project IDs, TOML injection and malformed D1 UUIDs", () => {
  for (const firebaseProjectId of ["x", "UPPERCASE-PROJECT", "stillnote\n[vars]", "bad_\"name", "", "a".repeat(31)]) {
    assert.throws(() => renderFreeConfig(template, { ...example, firebaseProjectId }), /Invalid Firebase project ID/);
  }
  for (const d1DatabaseId of ["not-a-uuid", "", '123e4567-e89b-42d3-a456-426614174000"\n[vars]']) {
    assert.throws(() => renderFreeConfig(template, { ...example, d1DatabaseId }), /Invalid D1 database UUID/);
  }
});

test("refuses altered templates rather than writing unsafe or incomplete config", () => {
  assert.throws(() => renderFreeConfig(template.replace('binding = "DB"', 'binding = "USER_NOTES"'), example), /Unexpected/);
  assert.throws(() => renderFreeConfig(template + "\n# REPLACE_WITH_YOUR_D1_DATABASE_ID", example), /placeholder count/);
});
