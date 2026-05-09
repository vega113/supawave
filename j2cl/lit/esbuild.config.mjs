import { build } from "esbuild";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(here, "../../war/j2cl/assets");

await build({
  entryPoints: [
    { in: resolve(here, "src/index.js"), out: "shell" },
    { in: resolve(here, "src/tokens/shell-tokens.css"), out: "shell" },
    // F-0 (#1035): wavy design tokens emit as a sibling asset so server
    // templates load them with their own <link>. Recipe .js files do
    // NOT import this CSS (avoids double-emission into shell.css).
    { in: resolve(here, "src/design/wavy-tokens.css"), out: "wavy-tokens" },
    // F-2 slice 2 (#1046): thread collapse motion + .j2cl-read-surface
    // positioning context for the focus frame. Sibling stylesheet so the
    // server template can <link> it next to wavy-tokens.css.
    { in: resolve(here, "src/design/wavy-thread-collapse.css"), out: "wavy-thread-collapse" },
    // Round 3 (#1235): the J2CL shell template references
    // /j2cl/assets/sidecar.css but the source lives under
    // j2cl/src/main/webapp/assets/sidecar.css and was not copied into
    // war/j2cl/assets/, leaving the wave-panel chrome (blue title
    // strip, empty-state recipe, etc.) un-styled in production. Re-emit
    // it via esbuild so it ships next to shell.css.
    {
      in: resolve(here, "../src/main/webapp/assets/sidecar.css"),
      out: "sidecar"
    }
  ],
  bundle: true,
  format: "esm",
  target: "es2020",
  minify: true,
  sourcemap: true,
  outdir: out,
  logLevel: "info"
});
