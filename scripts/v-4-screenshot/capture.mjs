// V-4 (#1102) headless screenshot capture for the per-blip chrome
// fixture. Boots a static-file server out of the repo root so the
// fixture's relative <link> + import map paths resolve, then uses
// Playwright (already installed for the lit web-test-runner) to
// render the fixture and write a 1440x900 PNG.
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import url from "node:url";
import { chromium } from "../../j2cl/lit/node_modules/playwright/index.mjs";

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "..", "..");

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".mjs": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png",
  ".json": "application/json",
  ".map": "application/json"
};

function startServer(rootDir) {
  return new Promise((resolve) => {
    const server = http.createServer((req, res) => {
      let urlPath = decodeURIComponent(req.url.split("?")[0]);
      if (urlPath === "/") urlPath = "/index.html";
      const filePath = path.join(rootDir, urlPath);
      if (!filePath.startsWith(rootDir)) {
        res.writeHead(403); res.end("forbidden"); return;
      }
      fs.readFile(filePath, (err, buf) => {
        if (err) {
          res.writeHead(404, { "content-type": "text/plain" });
          res.end("not found: " + filePath);
          return;
        }
        const ext = path.extname(filePath);
        res.writeHead(200, { "content-type": MIME[ext] || "application/octet-stream" });
        res.end(buf);
      });
    });
    server.listen(0, "127.0.0.1", () => {
      const { port } = server.address();
      resolve({ server, port });
    });
  });
}

async function capture({ outPath, label }) {
  const { server, port } = await startServer(REPO_ROOT);
  const fixtureUrl = `http://127.0.0.1:${port}/scripts/v-4-screenshot/fixture.html`;
  const browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 2,
    colorScheme: "light"
  });
  const page = await context.newPage();
  page.on("console", (m) => console.log(`[${label}] console`, m.type(), m.text()));
  page.on("pageerror", (e) => console.log(`[${label}] pageerror`, e.message));
  await page.goto(fixtureUrl, { waitUntil: "networkidle" });
  await page.waitForTimeout(300);
  await page.screenshot({ path: outPath, fullPage: false });
  await browser.close();
  await new Promise((r) => server.close(r));
  console.log(`wrote ${outPath}`);
}

const out = process.argv[2];
if (!out) {
  console.error("usage: node capture.mjs <output-path>");
  process.exit(1);
}
capture({ outPath: out, label: path.basename(out) }).catch((err) => {
  console.error(err);
  process.exit(1);
});
