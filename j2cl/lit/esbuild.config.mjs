import { build } from "esbuild";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, "../../war/j2cl/assets");

await build({
  entryPoints: [
    { in: resolve(here, "src/index.js"), out: "shell" },
    { in: resolve(here, "src/tokens/shell-tokens.css"), out: "shell" }
  ],
  bundle: true,
  format: "esm",
  target: "es2020",
  minify: true,
  sourcemap: true,
  outdir: out,
  logLevel: "info"
});
