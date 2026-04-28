#!/usr/bin/env node
// Probe rail / wave-panel widths and pane-gutter padding so the V-5
// before/after diff has measurable numbers in the PR body.
import path from "node:path";
import url from "node:url";
import { createRequire } from "node:module";
const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");
const litRequire = createRequire(path.join(repoRoot, "j2cl/lit/package.json"));
const { chromium } = litRequire("playwright");

const args = process.argv.slice(2);
let port = 9905;
for (let i = 0; i < args.length; i++) {
  if (args[i] === "--port" && args[i + 1]) port = Number(args[++i]);
}
const HOST = "127.0.0.1";
const BASE = `http://${HOST}:${port}`;

const stamp = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
const userAddress = `qav5p${stamp}`;
const userEmail = `${userAddress}@local.net`;
const password = "Pass" + stamp + "!";

const browser = await chromium.launch();
try {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await ctx.newPage();
  // Defeat the in-browser disk cache so probes against a freshly
  // rebuilt war/ directory always pick up the latest CSS.
  await ctx.route('**/*', (route) => {
    const headers = { ...route.request().headers(), 'cache-control': 'no-cache' };
    route.continue({ headers });
  });
  await ctx.setExtraHTTPHeaders({ 'cache-control': 'no-cache' });
  await page.goto(`${BASE}/auth/register`, { waitUntil: "domcontentloaded" });
  await page.locator("#address").fill(userAddress);
  await page.locator("#email").fill(userEmail);
  await page.locator("#password").fill(password);
  await page.locator("#verifypass").fill(password);
  await Promise.all([
    page.waitForLoadState("domcontentloaded"),
    page.locator('input.btn-primary').click()
  ]);
  await page.goto(`${BASE}/?view=j2cl-root`, { waitUntil: "networkidle" });
  await page.waitForTimeout(400);

  const tags = await page.evaluate(() => {
    const all = new Set();
    document.querySelectorAll('*').forEach((el) => {
      if (el.tagName.includes('-')) all.add(el.tagName.toLowerCase());
    });
    return Array.from(all).sort();
  });
  console.error('CUSTOM ELEMENTS:', tags.join(', '));

  const probe = await page.evaluate(() => {
    const rail = document.querySelector('shell-nav-rail');
    const main = document.querySelector('shell-main-region');
    const card = document.querySelector('.sidecar-selected-card');
    const root = document.querySelector('shell-root');
    const railRect = rail ? rail.getBoundingClientRect() : null;
    const mainRect = main ? main.getBoundingClientRect() : null;
    const cardRect = card ? card.getBoundingClientRect() : null;
    const cardCs = card ? getComputedStyle(card) : null;
    const docCs = getComputedStyle(document.documentElement);
    const railWidthMin = docCs.getPropertyValue('--wavy-rail-width-min').trim();
    const railWidthMax = docCs.getPropertyValue('--wavy-rail-width-max').trim();
    const paneY = docCs.getPropertyValue('--wavy-pane-gutter-y').trim();
    const paneX = docCs.getPropertyValue('--wavy-pane-gutter-x').trim();
    const railColTemplate = root ? getComputedStyle(root).getPropertyValue('grid-template-columns') : '';
    const isSignedOut = !!document.querySelector('shell-root-signed-out');
    const hasShellRoot = !!document.querySelector('shell-root');
    const railEl = document.querySelector('wavy-search-rail');
    const railRect2 = railEl ? railEl.getBoundingClientRect() : null;
    return {
      railWidth: railRect ? railRect.width : null,
      mainWidth: mainRect ? mainRect.width : null,
      cardPadding: cardCs ? cardCs.padding : null,
      cardTop: cardRect ? cardRect.top : null,
      cardBottom: cardRect ? cardRect.bottom : null,
      cardHeight: cardRect ? cardRect.height : null,
      railWidthMin,
      railWidthMax,
      paneY,
      paneX,
      railColTemplate,
      isSignedOut,
      hasShellRoot,
      railElWidth: railRect2 ? railRect2.width : null,
      railElRight: railRect2 ? railRect2.right : null
    };
  });
  console.log(JSON.stringify(probe, null, 2));
} finally {
  await browser.close();
}
