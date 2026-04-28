// V-2 (#1100): capture before/after screenshots for the J2CL root shell.
// Run with: node scripts/v-2-screenshots.mjs <port> <outdir> [debug-overlay]
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";

const port = process.argv[2] || "9902";
const outDir = process.argv[3] || "docs/superpowers/screenshots/v-2";
const debugOverlay = process.argv[4] === "debug-overlay";
const tag = process.argv[5] || (debugOverlay ? "after-debug-on" : "after");

fs.mkdirSync(outDir, { recursive: true });

const baseUrl = `http://127.0.0.1:${port}`;
const stamp = Date.now();
const username = `v2qa${stamp}`;
const password = "passW0rd!";

const browser = await chromium.launch();
const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
const page = await context.newPage();
page.on("console", (msg) => {
  if (msg.type() === "error") console.log("[browser console error]", msg.text());
});

// 1. Register (form: address, email, password, verifypass; button is JS validate())
await page.goto(`${baseUrl}/auth/register`);
await page.fill('input[name="address"]', username);
await page.fill('input[name="email"]', `${username}@example.com`);
await page.fill('input[name="password"]', password);
await page.fill('input[name="verifypass"]', password);
await page.evaluate(() => {
  // The Create Account button is a typeless input wired to validate(); call it.
  if (typeof validate === "function") validate();
});
await page.waitForLoadState("networkidle").catch(() => {});

// 2. Sign in (the register form usually auto-signs-in, but be safe)
await page.goto(`${baseUrl}/auth/signin?r=/?view=j2cl-root`);
const signinUser = await page.$('input[name="address"]');
if (signinUser) {
  await page.fill('input[name="address"]', username);
  await page.fill('input[name="password"]', password);
  await page.click('input[type="submit"], button[type="submit"]');
  await page.waitForLoadState("networkidle").catch(() => {});
}

// 3. Navigate to j2cl-root and let live updates settle
await page.goto(`${baseUrl}/?view=j2cl-root`);
await page.waitForLoadState("networkidle");
await page.waitForTimeout(2500);

const empty = path.join(outDir, `${tag}-empty.png`);
await page.screenshot({ path: empty, fullPage: false });
console.log("saved", empty);

// 4. If the inbox is empty (fresh user), seed a wave by clicking New Wave.
const newWaveBtn = await page.$('button:has-text("New Wave"), [data-new-wave], wavy-new-wave-button');
if (newWaveBtn) {
  try {
    await newWaveBtn.click({ timeout: 5000 });
    await page.waitForTimeout(800);
    const titleInput = await page.$('input[placeholder*="title" i], input[name="title"], wavy-new-wave-input input');
    if (titleInput) {
      await titleInput.fill(`V-2 QA wave ${stamp}`);
      await titleInput.press("Enter");
    }
    await page.waitForTimeout(2500);
  } catch (e) {
    console.log("New Wave button not clickable:", e.message);
  }
}

// 5. Open the welcome wave (or whatever the first rail card is) so the
// selected-wave card mounts.
const firstCard = await page.$("wavy-search-rail-card, .sidecar-search-card .digest-card, [data-rail-card-id]");
if (firstCard) {
  await firstCard.click();
  await page.waitForTimeout(2500);
}
const opened = path.join(outDir, `${tag}.png`);
await page.screenshot({ path: opened, fullPage: false });
console.log("saved", opened);

// 6. Trigger a reply so composer-inline-reply / wavy-composer paint.
const replyTrigger = await page.$('button[data-action="reply"], wavy-wave-root-reply-trigger, button:has-text("Reply")');
if (replyTrigger) {
  try {
    await replyTrigger.click({ timeout: 5000 });
    await page.waitForTimeout(1500);
    const composer = path.join(outDir, `${tag}-composer.png`);
    await page.screenshot({ path: composer, fullPage: false });
    console.log("saved", composer);
  } catch (e) {
    console.log("reply trigger not clickable:", e.message);
  }
}

// 7. Dump the rendered HTML for grep verification (before any overlay toggle).
const htmlOut = path.join(outDir, `${tag}-page.html`);
fs.writeFileSync(htmlOut, await page.content());
console.log("saved", htmlOut);

// 8. Demonstrate the debug-overlay flag: toggle the body class directly
// to mirror what HtmlRenderer does when j2cl-debug-overlay is enabled.
// Only runs when the debug-overlay CLI arg is passed so flag-off runs
// stay faithful to the default (no dev strings) state.
if (debugOverlay) {
  await page.evaluate(() => {
    document.body.classList.add("j2cl-debug-overlay-on");
  });
  await page.waitForTimeout(500);
  const overlay = path.join(outDir, `${tag}-debug-on.png`);
  await page.screenshot({ path: overlay, fullPage: false });
  console.log("saved", overlay);
  const overlayHtml = path.join(outDir, `${tag}-debug-on-page.html`);
  fs.writeFileSync(overlayHtml, await page.content());
  console.log("saved", overlayHtml);
}

console.log(`signed in as ${username}; flag-on=${debugOverlay}`);
await browser.close();
