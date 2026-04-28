// V-4 (#1102) headless screenshot capture for the per-blip chrome
// fixture. Boots a static-file server out of the repo root so the
// fixture's relative <link> + import map paths resolve, then uses
// Playwright (already installed for the lit web-test-runner) to
// render the fixture and write a 1440x900 PNG.
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import url from "node:url";
import { createRequire } from "node:module";

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, "..", "..");

// The repo has no top-level package.json — Playwright lives in the
// j2cl/lit web-test-runner stack. Resolve it from there so the script
// runs from any cwd as long as `npm install` has been run in
// j2cl/lit/ (the same prerequisite the lit test suite already
// requires).
const litRequire = createRequire(
  new URL("../../j2cl/lit/package.json", import.meta.url)
);
const { chromium } = litRequire("playwright");

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
      // Strip traversal segments so taint-analysis can see the sanitisation
      // before the path reaches fs.readFile (defence-in-depth vs the startsWith guard).
      const safeParts = urlPath.split("/").filter(seg => seg !== ".." && seg !== ".");
      const safePath = "/" + safeParts.filter(Boolean).join("/");
      const filePath = path.resolve(rootDir, safePath.slice(1));
      if (!filePath.startsWith(rootDir + path.sep) && filePath !== rootDir) {
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
