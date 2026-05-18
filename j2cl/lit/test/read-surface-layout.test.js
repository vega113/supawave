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
  it("uses the compact pending attachment placeholder for all display sizes", async () => {
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

    expect(getComputedStyle(medium).minHeight).to.equal("72px");
    expect(getComputedStyle(large).minHeight).to.equal("72px");
  });

  it("keeps client-created pending-upgrade blips close to a one-line card height", async () => {
    const blip = await fixture(html`
      <wave-blip data-j2cl-pending-upgrade>
        <span>Pending formatted card</span>
      </wave-blip>
    `);

    expect(getComputedStyle(blip).minHeight).to.equal("40px");
  });
});
