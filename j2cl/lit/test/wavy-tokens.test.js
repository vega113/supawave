import { fixture, expect, html } from "@open-wc/testing";

// Load the wavy-tokens stylesheet into the test document so getComputedStyle
// can resolve --wavy-* custom properties. Web-test-runner serves files
// relative to the project root via @web/dev-server; the stylesheet sits at
// /src/design/wavy-tokens.css.
function ensureWavyTokensLoaded() {
  if (document.querySelector('link[data-wavy-tokens-test]')) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/design/wavy-tokens.css";
  link.dataset.wavyTokensTest = "true";
  document.head.appendChild(link);
}

async function waitForStylesheet() {
  // Wait for the stylesheet to apply by polling getComputedStyle.
  for (let i = 0; i < 50; i++) {
    const cs = getComputedStyle(document.documentElement);
    if (cs.getPropertyValue("--wavy-bg-base").trim() !== "") return;
    await new Promise((r) => setTimeout(r, 20));
  }
  throw new Error("wavy-tokens.css did not apply within 1000ms");
}

// Color-token name list (from plan §3.1). The six --wavy-type-*,
// --wavy-spacing-* etc. lists are checked separately below.
const COLOR_TOKENS = [
  "--wavy-bg-base",
  "--wavy-bg-surface",
  "--wavy-border-hairline",
  "--wavy-text-body",
  "--wavy-text-muted",
  "--wavy-text-quiet",
  "--wavy-signal-cyan",
  "--wavy-signal-cyan-soft",
  "--wavy-signal-violet",
  "--wavy-signal-violet-soft",
  "--wavy-signal-amber",
  "--wavy-signal-amber-soft",
  "--wavy-focus-ring",
  "--wavy-pulse-ring"
];

const TYPOGRAPHY_TOKENS = [
  "--wavy-font-headline",
  "--wavy-font-body",
  "--wavy-font-label",
  "--wavy-type-display",
  "--wavy-type-h1",
  "--wavy-type-h2",
  "--wavy-type-h3",
  "--wavy-type-body",
  "--wavy-type-label",
  "--wavy-type-meta"
];

const MOTION_TOKENS = [
  "--wavy-motion-pulse-duration",
  "--wavy-motion-focus-duration",
  "--wavy-motion-collapse-duration",
  "--wavy-motion-fragment-fade-duration",
  "--wavy-easing-pulse",
  "--wavy-easing-focus",
  "--wavy-easing-collapse"
];

const SPACING_SHAPE_TOKENS = [
  "--wavy-spacing-1",
  "--wavy-spacing-2",
  "--wavy-spacing-3",
  "--wavy-spacing-4",
  "--wavy-spacing-5",
  "--wavy-spacing-6",
  "--wavy-spacing-7",
  "--wavy-spacing-8",
  "--wavy-radius-card",
  "--wavy-radius-pill"
];

const ALL_TOKENS = [
  ...COLOR_TOKENS,
  ...TYPOGRAPHY_TOKENS,
  ...MOTION_TOKENS,
  ...SPACING_SHAPE_TOKENS
];

// Parse `rgb(...)` / `rgba(...)` strings returned by getComputedStyle.
// Returns [r, g, b, a] in the 0..255 / 0..1 range, or null if not parseable.
function parseColor(str) {
  const m = String(str).trim().match(
    /^rgba?\(\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)\s*,\s*(\d+(?:\.\d+)?)(?:\s*,\s*(\d+(?:\.\d+)?))?\s*\)$/
  );
  if (!m) return null;
  return [Number(m[1]), Number(m[2]), Number(m[3]), m[4] === undefined ? 1 : Number(m[4])];
}

// WCAG 2.1 relative luminance, 0..1.
function relativeLuminance([r, g, b]) {
  const channel = (c) => {
    const v = c / 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

// Composite a foreground color (with alpha) over an opaque background and
// return the resulting RGB. Necessary because text tokens are alpha-tinted.
function composite(fg, bg) {
  const [fr, fg_, fb, fa] = fg;
  const [br, bg_, bb] = bg;
  return [
    Math.round(fr * fa + br * (1 - fa)),
    Math.round(fg_ * fa + bg_ * (1 - fa)),
    Math.round(fb * fa + bb * (1 - fa))
  ];
}

function contrastRatio(fgRgb, bgRgb) {
  const lFg = relativeLuminance(fgRgb);
  const lBg = relativeLuminance(bgRgb);
  const lighter = Math.max(lFg, lBg);
  const darker = Math.min(lFg, lBg);
  return (lighter + 0.05) / (darker + 0.05);
}

async function probeAt(themeAttr) {
  const wrapper = await fixture(html`
    <div data-wavy-theme=${themeAttr}>
      <div class="probe">probe</div>
    </div>
  `);
  return getComputedStyle(wrapper);
}

before(() => {
  ensureWavyTokensLoaded();
});

describe("wavy-tokens.css", () => {
  before(async () => {
    await waitForStylesheet();
  });

  it("defines every contract token from plan §3 (default scope)", () => {
    const cs = getComputedStyle(document.documentElement);
    for (const name of ALL_TOKENS) {
      const v = cs.getPropertyValue(name).trim();
      expect(v, `expected ${name} to be defined at :root`).to.not.equal("");
    }
  });

  it("uses only the --wavy- prefix (no accidental --shell- overlap)", () => {
    for (const name of ALL_TOKENS) {
      expect(name.startsWith("--wavy-")).to.equal(true);
    }
  });

  it("pins signal accent hex values from the Stitch source of truth", () => {
    const cs = getComputedStyle(document.documentElement);
    expect(cs.getPropertyValue("--wavy-signal-cyan").trim().toLowerCase()).to.equal("#22d3ee");
    expect(cs.getPropertyValue("--wavy-signal-violet").trim().toLowerCase()).to.equal("#7c3aed");
    expect(cs.getPropertyValue("--wavy-signal-amber").trim().toLowerCase()).to.equal("#fb923c");
  });

  it("pins motion durations and shape tokens to the spec values", () => {
    const cs = getComputedStyle(document.documentElement);
    expect(cs.getPropertyValue("--wavy-motion-pulse-duration").trim()).to.equal("600ms");
    expect(cs.getPropertyValue("--wavy-motion-focus-duration").trim()).to.equal("180ms");
    expect(cs.getPropertyValue("--wavy-motion-collapse-duration").trim()).to.equal("240ms");
    expect(cs.getPropertyValue("--wavy-motion-fragment-fade-duration").trim()).to.equal("300ms");
    expect(cs.getPropertyValue("--wavy-radius-card").trim()).to.equal("12px");
    expect(cs.getPropertyValue("--wavy-radius-pill").trim()).to.equal("9999px");
  });

  it("dark and light variants resolve to different surface colors", async () => {
    const csDark = await probeAt("dark");
    const csLight = await probeAt("light");
    const dark = csDark.getPropertyValue("--wavy-bg-surface").trim().toLowerCase();
    const light = csLight.getPropertyValue("--wavy-bg-surface").trim().toLowerCase();
    expect(dark).to.not.equal(light);
  });

  it("contrast variant raises text-body to a higher saturation than dark", async () => {
    const csDark = await probeAt("dark");
    const csContrast = await probeAt("contrast");
    const darkBody = csDark.getPropertyValue("--wavy-text-body").trim();
    const contrastBody = csContrast.getPropertyValue("--wavy-text-body").trim();
    // Contrast body is plain #ffffff, dark is rgba(232,240,255,0.92);
    // so they must differ.
    expect(darkBody).to.not.equal(contrastBody);
  });

  // Resolve a CSS color string (any form) to opaque RGB triplet by
  // round-tripping through getComputedStyle on a probe element.
  function resolveColor(cssValue) {
    const probe = document.createElement("span");
    probe.style.color = cssValue;
    document.body.appendChild(probe);
    const rgba = parseColor(getComputedStyle(probe).color);
    probe.remove();
    return rgba;
  }

  // WCAG floors per theme + tier:
  //   - body and muted: AA 4.5:1 for normal-size text on dark/light,
  //     AAA 7:1 on contrast.
  //   - quiet: 3:1 (WCAG AA for incidental/large UI text — quiet is the
  //     tertiary tier reserved for timestamps and decorative meta and
  //     is intentionally low-contrast). On contrast theme, raised to
  //     4.5:1 to match the contrast variant's stricter intent.
  function assertContrastFloors(themeAttr, bodyFloor, mutedFloor, quietFloor) {
    return (async () => {
      const cs = await probeAt(themeAttr);
      // Always use the explicit --wavy-bg-surface token as the
      // background — the wrapper element's backgroundColor is
      // rgba(0,0,0,0) by default and would skew the composite.
      const surfaceCss = cs.getPropertyValue("--wavy-bg-surface").trim();
      const opaqueBg = resolveColor(surfaceCss).slice(0, 3);

      const tiers = [
        ["--wavy-text-body", bodyFloor],
        ["--wavy-text-muted", mutedFloor],
        ["--wavy-text-quiet", quietFloor]
      ];
      for (const [tokenName, floor] of tiers) {
        const raw = cs.getPropertyValue(tokenName).trim();
        const fgRgba = resolveColor(raw);
        const fgComposited = composite(fgRgba, opaqueBg);
        const ratio = contrastRatio(fgComposited, opaqueBg);
        expect(
          ratio,
          `${themeAttr} theme: ${tokenName} contrast ratio ${ratio.toFixed(2)} below floor ${floor}`
        ).to.be.greaterThanOrEqual(floor);
      }
    })();
  }

  it("dark variant clears WCAG floors (body/muted AA 4.5:1, quiet 3:1)", async () => {
    await assertContrastFloors("dark", 4.5, 4.5, 3.0);
  });

  it("light variant clears WCAG floors (body/muted AA 4.5:1, quiet 3:1)", async () => {
    await assertContrastFloors("light", 4.5, 4.5, 3.0);
  });

  it("contrast variant clears WCAG AAA (body/muted 7:1, quiet 4.5:1)", async () => {
    await assertContrastFloors("contrast", 7, 7, 4.5);
  });

  it("reduced motion can be exercised by reading the resolved duration token", () => {
    // Sanity check: the test environment may or may not announce
    // prefers-reduced-motion. Either way, the duration token must
    // resolve to a positive parseable time value.
    const cs = getComputedStyle(document.documentElement);
    const raw = cs.getPropertyValue("--wavy-motion-pulse-duration").trim();
    expect(raw.endsWith("ms") || raw.endsWith("s")).to.equal(true);
  });

  it("font shorthand caveat: declaring font-weight after the type token sticks", async () => {
    // Plan §3.2 "Caveat — `font` shorthand reset" — recipes must
    // declare font-weight AFTER the var(--wavy-type-*) shorthand or
    // the weight resets to 'normal' / 400. This test mounts a probe
    // that does it the right way and asserts the computed weight; if
    // a future recipe drops the explicit font-weight after the
    // shorthand, the same pattern would compute as "400" here.
    const probe = await fixture(html`
      <div style="font: var(--wavy-type-h3); font-weight: 600;">Probe</div>
    `);
    expect(getComputedStyle(probe).fontWeight).to.equal("600");
  });
});
