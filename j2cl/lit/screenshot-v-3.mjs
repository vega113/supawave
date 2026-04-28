#!/usr/bin/env node
// V-3 (#1101) headless screenshot harness for the format toolbar.
// Renders <wavy-format-toolbar> against a fixed selection descriptor in
// a Chromium page driven by playwright, and writes the captured PNG to
// the requested output path. Run with the lit deps installed under
// j2cl/lit/node_modules.
//
// Usage:
//   node j2cl/lit/screenshot-v-3.mjs <out.png>
//
// The harness re-uses the lit modules from j2cl/lit/src so it picks up
// whatever toolbar shape is present on the working tree (before vs.
// after is captured by running the script after a `git checkout` of
// the toolbar files).
import { chromium } from "playwright";
import path from "node:path";
import http from "node:http";
import fs from "node:fs";
import url from "node:url";

const __dirname = path.dirname(url.fileURLToPath(import.meta.url));
const litRoot = __dirname;
const repoRoot = path.resolve(litRoot, "..", "..");

if (process.argv.length < 3) {
  console.error("usage: node j2cl/lit/screenshot-v-3.mjs <out.png>");
  process.exit(2);
}
const outPath = path.resolve(process.argv[2]);
fs.mkdirSync(path.dirname(outPath), { recursive: true });

const HOST = "127.0.0.1";

function contentTypeFor(p) {
  if (p.endsWith(".js") || p.endsWith(".mjs")) return "application/javascript";
  if (p.endsWith(".css")) return "text/css";
  if (p.endsWith(".json")) return "application/json";
  if (p.endsWith(".html")) return "text/html";
  if (p.endsWith(".svg")) return "image/svg+xml";
  return "application/octet-stream";
}

function splitBareSpecifier(spec) {
  // "@scope/pkg/subpath/x.js" -> { pkg: "@scope/pkg", sub: "subpath/x.js" }
  // "pkg/subpath/x.js" -> { pkg: "pkg", sub: "subpath/x.js" }
  if (spec.startsWith("@")) {
    const parts = spec.split("/");
    if (parts.length < 2) return { pkg: spec, sub: "" };
    return { pkg: parts.slice(0, 2).join("/"), sub: parts.slice(2).join("/") };
  }
  const idx = spec.indexOf("/");
  if (idx < 0) return { pkg: spec, sub: "" };
  return { pkg: spec.slice(0, idx), sub: spec.slice(idx + 1) };
}

function resolveImport(specifier, fromDir) {
  if (specifier.startsWith(".") || specifier.startsWith("/")) {
    return null; // handled by static path resolution
  }
  const { pkg, sub } = splitBareSpecifier(specifier);
  let dir = fromDir;
  while (true) {
    const pkgRoot = path.join(dir, "node_modules", pkg);
    if (fs.existsSync(pkgRoot)) {
      if (sub) {
        // Try the subpath as-is, then with .js suffix.
        const direct = path.join(pkgRoot, sub);
        if (fs.existsSync(direct) && fs.statSync(direct).isFile()) return direct;
        if (fs.existsSync(direct + ".js")) return direct + ".js";
        if (fs.existsSync(path.join(direct, "index.js"))) return path.join(direct, "index.js");
        return direct;
      }
      const pkgJson = path.join(pkgRoot, "package.json");
      if (fs.existsSync(pkgJson)) {
        const meta = JSON.parse(fs.readFileSync(pkgJson, "utf8"));
        let entry = null;
        if (typeof meta.exports === "string") entry = meta.exports;
        else if (meta.exports && typeof meta.exports === "object") {
          const root = meta.exports["."] || meta.exports;
          entry = (root && (root.import || root.default || root.module)) || null;
          if (entry && typeof entry === "object") entry = entry.default || entry.import;
        }
        if (!entry) entry = meta.module || meta.main || "index.js";
        return path.join(pkgRoot, entry);
      }
      return path.join(pkgRoot, "index.js");
    }
    const parent = path.dirname(dir);
    if (parent === dir) return null;
    dir = parent;
  }
}

function rewriteImports(source, fromFile) {
  const fromDir = path.dirname(fromFile);
  return source.replace(
    /(\bfrom\s*["']|\bimport\s*\(\s*["']|\bimport\s*["'])([^"']+)(["'])/g,
    (match, prefix, spec, suffix) => {
      if (spec.startsWith(".") || spec.startsWith("/") || spec.startsWith("http")) {
        return match;
      }
      const resolved = resolveImport(spec, fromDir);
      if (!resolved) return match;
      const rel = path.relative(litRoot, resolved);
      const url = "/_pkg/" + rel.split(path.sep).join("/");
      return `${prefix}${url}${suffix}`;
    }
  );
}

const HTML_FIXTURE = `<!doctype html>
<html lang="en" data-wavy-theme="dark">
<head>
  <meta charset="utf-8" />
  <title>V-3 toolbar capture</title>
  <link rel="stylesheet" href="/src/design/wavy-tokens.css" />
  <style>
    :root { color-scheme: dark; }
    html, body {
      margin: 0;
      padding: 0;
      background: #fafbfd;
      font-family: Inter, -apple-system, "Segoe UI", sans-serif;
    }
    .stage {
      width: 1100px;
      padding: 80px 40px;
      box-sizing: border-box;
    }
    .ctx {
      font: 11px/1.4 "Geist", Inter, sans-serif;
      color: rgba(11,19,32,0.55);
      letter-spacing: 0.08em;
      margin-bottom: 8px;
      text-transform: uppercase;
    }
    .editor {
      width: 900px;
      min-height: 200px;
      border: 2px solid #22d3ee;
      border-radius: 16px;
      background: #ffffff;
      box-shadow: 0 4px 12px rgba(11,19,32,0.06);
      padding: 24px 28px 28px;
      font: 14px/1.55 Inter, sans-serif;
      color: rgba(11,19,32,0.92);
      position: relative;
    }
    .editor .selected {
      background: rgba(34,211,238,0.22);
      border-radius: 3px;
      padding: 1px 0;
    }
    .toolbar-anchor {
      margin-top: 20px;
    }
    wavy-format-toolbar {
      position: relative !important;
      transform: none !important;
      display: inline-flex !important;
    }
  </style>
</head>
<body>
  <div class="stage">
    <div class="ctx">Reply composer · selection-driven format toolbar</div>
    <div class="editor">
      <div>request-id is <strong>6f1a-7cc4</strong>. The outer fetch retried after the 502 hit, then</div>
      <div><span class="selected"><strong><em>the handler retried 3 more times on top</em></strong></span>. Net</div>
      <div>5 retries against the same upstream. I think the fix is in the outer fetch.</div>
      <div class="toolbar-anchor">
        <wavy-format-toolbar id="tb"></wavy-format-toolbar>
      </div>
    </div>
  </div>

  <script type="module">
    import "/src/elements/wavy-format-toolbar.js";
    const tb = document.getElementById("tb");
    tb.removeAttribute("hidden");
    tb.selectionDescriptor = {
      collapsed: false,
      boundingRect: { top: 0, left: 0, width: 200, height: 18 },
      activeAnnotations: ["strong", "em"]
    };
    await tb.updateComplete;
    requestAnimationFrame(() => {
      tb.style.transform = "none";
      tb.removeAttribute("hidden");
      window.__ready = true;
    });
  </script>
</body>
</html>
`;

const server = http.createServer((req, res) => {
  const u = new URL(req.url, "http://x");
  let p = u.pathname;
  try {
    if (p === "/" || p === "/index.html") {
      res.writeHead(200, { "content-type": "text/html" });
      res.end(HTML_FIXTURE);
      return;
    }
    let abs;
    if (p.startsWith("/_pkg/")) {
      abs = path.join(litRoot, p.replace(/^\/_pkg\//, ""));
    } else if (p.startsWith("/src/") || p.startsWith("/node_modules/")) {
      abs = path.join(litRoot, p.replace(/^\//, ""));
    } else {
      res.writeHead(404).end("not found");
      return;
    }
    if (!fs.existsSync(abs)) {
      res.writeHead(404).end("missing " + p);
      return;
    }
    const ct = contentTypeFor(abs);
    if (ct === "application/javascript") {
      const src = fs.readFileSync(abs, "utf8");
      const rewritten = rewriteImports(src, abs);
      res.writeHead(200, { "content-type": ct });
      res.end(rewritten);
      return;
    }
    res.writeHead(200, { "content-type": ct });
    res.end(fs.readFileSync(abs));
  } catch (e) {
    res.writeHead(500).end(String(e));
  }
});
server.on("request", (req) => {
  if (process.env.SCREENSHOT_DEBUG) console.log("[req]", req.url);
});

const port = await new Promise((resolve) => {
  server.listen(0, HOST, () => resolve(server.address().port));
});

let exitCode = 0;
let browser;
try {
  browser = await chromium.launch();
  const context = await browser.newContext({
    viewport: { width: 1200, height: 600 },
    deviceScaleFactor: 2
  });
  const page = await context.newPage();
  page.on("console", (m) => console.log("[page]", m.type(), m.text()));
  page.on("pageerror", (e) => console.log("[pageerror]", e.message));
  page.on("requestfailed", (r) => console.log("[reqfail]", r.url(), r.failure()?.errorText));
  page.on("response", (r) => { if (r.status() >= 400) console.log("[resp404]", r.status(), r.url()); });
  await page.goto(`http://${HOST}:${port}/`, { waitUntil: "networkidle" });
  await page.waitForFunction(() => window.__ready === true, null, { timeout: 15000 });
  // Allow paint to settle.
  await page.waitForTimeout(200);
  const editor = await page.locator(".editor").first();
  await editor.screenshot({ path: outPath });
  console.log("wrote", outPath);
} catch (e) {
  console.error(e);
  exitCode = 1;
} finally {
  if (browser) await browser.close();
  server.close();
}
process.exit(exitCode);
