/**
 * Generate a local-only Wrangler config from existing Firebase / Cloudflare IDs.
 * Does NOT authenticate, create cloud resources, deploy, or change billing.
 * This script intentionally does not handle credentials or access tokens.
 */
import { readFile, writeFile } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(dirname(fileURLToPath(import.meta.url)));
const FIREBASE_PLACEHOLDER = "REPLACE_WITH_YOUR_FIREBASE_SPARK_PROJECT_ID";
const D1_PLACEHOLDER = "REPLACE_WITH_YOUR_D1_DATABASE_ID";
const PROJECT_ID = /^[a-z][a-z0-9-]{4,28}[a-z0-9]$/;
const D1_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function replaceExactlyOnce(template, marker, value) {
  if (template.split(marker).length !== 2) {
    throw new Error("Wrangler example configuration has an unexpected placeholder count");
  }
  return template.replace(marker, value);
}

export function renderFreeConfig(template, { firebaseProjectId, d1DatabaseId }) {
  if (typeof template !== "string" || !template.includes('main = "src/index.ts"') ||
      !template.includes('binding = "DB"')) {
    throw new Error("Unexpected Wrangler configuration template");
  }
  if (typeof firebaseProjectId !== "string" || !PROJECT_ID.test(firebaseProjectId)) {
    throw new Error("Invalid Firebase project ID (expected 6-30 lowercase letters, digits, hyphens)");
  }
  if (typeof d1DatabaseId !== "string" || !D1_ID.test(d1DatabaseId)) {
    throw new Error("Invalid D1 database UUID");
  }
  return replaceExactlyOnce(
    replaceExactlyOnce(template, FIREBASE_PLACEHOLDER, firebaseProjectId),
    D1_PLACEHOLDER, d1DatabaseId
  );
}

function parseArguments(args) {
  if (args.length !== 4) {
    throw new Error("Usage: node scripts/configure-free.mjs --firebase-project-id PROJECT_ID --d1-database-id UUID");
  }
  const values = new Map();
  for (let i = 0; i < args.length; i += 2) {
    const key = args[i];
    if (!["--firebase-project-id", "--d1-database-id"].includes(key) ||
        values.has(key) || !args[i + 1]?.trim()) {
      throw new Error("Missing, duplicate, or unsupported configuration argument");
    }
    values.set(key, args[i + 1]);
  }
  return {
    firebaseProjectId: values.get("--firebase-project-id"),
    d1DatabaseId: values.get("--d1-database-id")
  };
}

async function main() {
  const values = parseArguments(process.argv.slice(2));
  const template = await readFile(join(root, "wrangler.example.toml"), "utf8");
  const config = renderFreeConfig(template, values);
  const destination = join(root, "wrangler.toml");
  // Exclusive create: never overwrite an existing deployment config.
  await writeFile(destination, config, { flag: "wx", mode: 0o600 });
  console.log("Generated git-ignored services/entitlements/wrangler.toml (no deployment performed).");
  console.log("Confirm Cloudflare Workers Free + D1 Free and Firebase Spark before running Wrangler commands.");
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch(error => {
    console.error(error instanceof Error ? error.message : "Configuration failed");
    process.exitCode = 1;
  });
}
