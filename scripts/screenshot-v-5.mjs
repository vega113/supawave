#!/usr/bin/env node
// V-5 (#1103) headless screenshot harness for ?view=j2cl-root.
// Drives the running dev server via Playwright: registers a fresh QA
// user, signs in, navigates to /?view=j2cl-root, and captures the
// screenshot at 1440x900. The before/after diff comes from running
// this script before and after the V-5 changes (or against
// origin/main vs. this branch in sibling worktrees).
//
// Usage:
//   node scripts/screenshot-v-5.mjs <out.png> [--port 9905]
import path from "node:path";
import fs from "node:fs";
import url from "node:url";
import { createRequire } from "node:module";

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");
const litRoot = path.join(repoRoot, "j2cl", "lit");
const litRequire = createRequire(path.join(litRoot, "package.json"));
const { chromium } = litRequire("playwright");

const args = process.argv.slice(2);
if (args.length < 1) {
  console.error("usage: node scripts/screenshot-v-5.mjs <out.png> [--port 9905]");
  process.exit(2);
}
const outPath = path.resolve(args[0]);
let port = 9905;
for (let i = 1; i < args.length; i++) {
  if (args[i] === "--port" && args[i + 1]) {
    port = Number(args[++i]);
  }
}
fs.mkdirSync(path.dirname(outPath), { recursive: true });

const HOST = "127.0.0.1";
const BASE = `http://${HOST}:${port}`;

const stamp = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
const userAddress = `qav5${stamp}`;
const userEmail = `${userAddress}@local.net`;
const password = "Pass" + stamp + "!";

async function fillById(page, id, value) {
  await page.locator(`#${id}`).fill(value);
}

let browser;
let exitCode = 0;
try {
  browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1
  });
  const page = await context.newPage();

  await page.goto(`${BASE}/auth/register`, { waitUntil: "domcontentloaded" });
  await fillById(page, "address", userAddress);
  await fillById(page, "email", userEmail);
  await fillById(page, "password", password);
  await fillById(page, "verifypass", password);
  await Promise.all([
    page.waitForLoadState("domcontentloaded"),
    page.locator('input.btn-primary').click()
  ]);

  // Some registration flows redirect through /auth/signin; sign in if
  // we landed there with the flash error / reentry form.
  const u = page.url();
  if (/\/auth\/signin/.test(u) || /register/.test(u)) {
    await page.goto(`${BASE}/auth/signin`, { waitUntil: "domcontentloaded" });
    if (await page.locator("#address").count()) {
      await fillById(page, "address", userAddress);
      await fillById(page, "password", password);
      await Promise.all([
        page.waitForLoadState("domcontentloaded"),
        page.locator('input.btn-primary, button[type="submit"]').first().click()
      ]);
    }
  }

  await page.goto(`${BASE}/?view=j2cl-root`, { waitUntil: "networkidle" });
  await page.waitForTimeout(800);

  await page.screenshot({ path: outPath, fullPage: false });
  console.log("wrote", outPath);
  console.log("registered", userEmail);
} catch (e) {
  console.error(e);
  exitCode = 1;
} finally {
  if (browser) await browser.close();
}
process.exit(exitCode);
