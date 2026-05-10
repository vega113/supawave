// audit-buttons.mjs — fail the build if any <button> in
// j2cl/lit/src/elements/*.js renders without both an aria-label AND a
// title/tooltip source. This locks in the J2CL/GWT parity that issue
// #1234+ keeps regressing on (see PR description for "objective-dewdney").
//
// Heuristics (intentionally simple — Lit elements ship as html`` template
// literals, so we walk the source text rather than parsing JS):
//
// - Find every "<button" opener.
// - Pair it with the first ">" that closes the opening tag (skipping any
//   nested template-literal expressions ${...} that would otherwise look
//   like ">" inside JS code).
// - Within that opener, require BOTH `aria-label=` and `title=` (or
//   confirm the button is rendered through a helper that we know provides
//   both: <toolbar-button>, <composer-submit-affordance>, <wavy-task-affordance>).
//
// If a violation is found, the audit prints the file + offending line and
// exits non-zero so `npm prebuild` (and CI) refuses to ship.

import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const elementsDir = resolve(here, "..", "src", "elements");

function listJs(dir) {
  return readdirSync(dir)
    .filter((name) => name.endsWith(".js"))
    .map((name) => join(dir, name))
    .filter((path) => statSync(path).isFile());
}

// Replace `//` line comments and `/* … */` block comments with whitespace
// of the same length so subsequent regex/index lookups still report the
// correct line numbers but never match content inside comments. We do NOT
// touch backtick template literals (Lit `html\`\`` blocks); those are the
// real targets we want to lint.
function stripJsComments(src) {
  let out = "";
  let i = 0;
  let inString = null; // ', ", or `
  while (i < src.length) {
    const ch = src[i];
    const next = src[i + 1];
    if (inString) {
      if (ch === "\\" && i + 1 < src.length) {
        out += ch + next;
        i += 2;
        continue;
      }
      if (ch === inString) {
        inString = null;
      }
      out += ch;
      i += 1;
      continue;
    }
    if (ch === "'" || ch === '"' || ch === "`") {
      inString = ch;
      out += ch;
      i += 1;
      continue;
    }
    if (ch === "/" && next === "/") {
      // Skip until newline.
      while (i < src.length && src[i] !== "\n") {
        out += src[i] === "\n" ? "\n" : " ";
        i += 1;
      }
      continue;
    }
    if (ch === "/" && next === "*") {
      out += "  ";
      i += 2;
      while (i < src.length && !(src[i] === "*" && src[i + 1] === "/")) {
        out += src[i] === "\n" ? "\n" : " ";
        i += 1;
      }
      if (i < src.length) {
        out += "  ";
        i += 2;
      }
      continue;
    }
    out += ch;
    i += 1;
  }
  return out;
}

// Find the matching `>` that closes a `<button` opener, skipping `${...}`
// expression blocks (Lit interpolation may contain `>` characters that are
// not part of HTML).
function findCloseAngle(src, startIndex) {
  let i = startIndex;
  while (i < src.length) {
    const ch = src[i];
    if (ch === "$" && src[i + 1] === "{") {
      // Skip balanced ${...} block.
      let depth = 1;
      i += 2;
      while (i < src.length && depth > 0) {
        const c2 = src[i];
        if (c2 === "{") depth += 1;
        else if (c2 === "}") depth -= 1;
        i += 1;
      }
      continue;
    }
    if (ch === ">") return i;
    i += 1;
  }
  return -1;
}

function lineNumberAt(src, index) {
  return src.slice(0, index).split("\n").length;
}

const violations = [];

for (const file of listJs(elementsDir)) {
  const raw = readFileSync(file, "utf8");
  const src = stripJsComments(raw);
  let cursor = 0;
  while (true) {
    const open = src.indexOf("<button", cursor);
    if (open === -1) break;
    cursor = open + 1;
    // Skip e.g. "<button-like" custom names (the `<` followed by "button"
    // with a hyphen) — only real <button> matches must end with whitespace,
    // newline, "/", or ">".
    const next = src[open + 7];
    if (!/[\s/>]/.test(next || "")) continue;
    const close = findCloseAngle(src, open);
    if (close === -1) break;
    const opener = src.slice(open, close + 1);
    const hasAria = /(?:^|[\s<])aria-label\s*=/.test(opener);
    const hasTitle = /(?:^|[\s<])title\s*=/.test(opener);
    if (hasAria && hasTitle) continue;
    violations.push({
      file,
      line: lineNumberAt(src, open),
      opener: opener.replace(/\s+/g, " ").slice(0, 160)
    });
  }
}

if (violations.length === 0) {
  console.log(`audit-buttons: OK (${listJs(elementsDir).length} files scanned)`);
  process.exit(0);
}

console.error(`audit-buttons: ${violations.length} violation(s):`);
for (const v of violations) {
  console.error(`  ${v.file}:${v.line}`);
  console.error(`    ${v.opener}`);
}
process.exit(1);
