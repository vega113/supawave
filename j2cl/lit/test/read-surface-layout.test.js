import { fixture, expect, html } from "@open-wc/testing";

function ensureReadSurfaceStylesLoaded() {
  if (document.querySelector("link[data-read-surface-layout-test]")) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/design/wavy-thread-collapse.css";
  link.dataset.readSurfaceLayoutTest = "true";
  document.head.appendChild(link);
}

async function waitForReadSurfaceStyles() {
  for (let i = 0; i < 50; i++) {
    const probe = await fixture(html`
      <div
        class="j2cl-read-attachment"
        data-attachment-state="pending"
        data-display-size="medium"
      ></div>
    `);
    const minHeight = getComputedStyle(probe).minHeight;
    probe.remove();
    if (minHeight && minHeight !== "0px") return;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error("wavy-thread-collapse.css did not apply within 1000ms");
}

before(async () => {
  ensureReadSurfaceStylesLoaded();
  await waitForReadSurfaceStyles();
});

describe("J2CL read-surface loading layout", () => {
  it("reserves display-size-specific space for pending attachment metadata", async () => {
    const medium = await fixture(html`
      <div
        class="j2cl-read-attachment"
        data-attachment-state="pending"
        data-display-size="medium"
      ></div>
    `);
    const large = await fixture(html`
      <div
        class="j2cl-read-attachment"
        data-attachment-state="pending"
        data-display-size="large"
      ></div>
    `);

    expect(getComputedStyle(medium).minHeight).to.equal("180px");
    expect(getComputedStyle(large).minHeight).to.equal("260px");
  });
});
