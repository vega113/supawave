import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/index.js";

describe("<compose-attachment-card>", () => {
  it("is registered by the Lit shell bundle", () => {
    expect(customElements.get("compose-attachment-card")).to.be.a("function");
  });

  it("renders keyboard-reachable open and download controls", async () => {
    const el = await fixture(html`
      <compose-attachment-card
        attachment-id="att-1"
        filename="diagram.png"
        mime-type="image/png"
        size-label="42 KB"
        display-size="large"
        open-url="/attachment/att-1"
        download-url="/attachment/att-1?download=1"
      ></compose-attachment-card>
    `);

    const article = el.renderRoot.querySelector("article");
    const open = el.renderRoot.querySelector("[data-action='attachment-open']");
    const download = el.renderRoot.querySelector("[data-action='attachment-download']");

    expect(article.classList.contains("size-large")).to.equal(true);
    expect(open.getAttribute("aria-label")).to.equal("Open attachment diagram.png");
    expect(download.getAttribute("aria-label")).to.equal("Download attachment diagram.png");
    expect(open.tabIndex).to.equal(0);
    expect(download.tabIndex).to.equal(0);
    open.focus();
    expect(open).to.equal(el.shadowRoot.activeElement);

    const openEvent = oneEvent(el, "attachment-open");
    open.click();
    expect((await openEvent).detail).to.deep.equal({
      attachmentId: "att-1",
      filename: "diagram.png",
      url: "/attachment/att-1"
    });

    const downloadEvent = oneEvent(el, "attachment-download");
    download.click();
    expect((await downloadEvent).detail).to.deep.equal({
      attachmentId: "att-1",
      filename: "diagram.png",
      url: "/attachment/att-1?download=1"
    });
  });

  it("renders blocked and error states as alerts without openable controls", async () => {
    const el = await fixture(html`
      <compose-attachment-card
        attachment-id="att-2"
        filename="blocked.exe"
        mime-type="application/octet-stream"
        malware-blocked
        error="This attachment is blocked by server policy."
      ></compose-attachment-card>
    `);

    const alert = el.renderRoot.querySelector("[role='alert']");
    const open = el.renderRoot.querySelector("[data-action='attachment-open']");
    const download = el.renderRoot.querySelector("[data-action='attachment-download']");

    expect(alert.textContent).to.include("blocked by server policy");
    expect(open.disabled).to.equal(true);
    expect(download.disabled).to.equal(true);
  });

  it("does not emit open or download events when controls are disabled", async () => {
    const el = await fixture(html`
      <compose-attachment-card
        attachment-id="att-3"
        filename="missing-metadata.bin"
        mime-type="application/octet-stream"
      ></compose-attachment-card>
    `);
    let openEvents = 0;
    let downloadEvents = 0;
    el.addEventListener("attachment-open", () => {
      openEvents += 1;
    });
    el.addEventListener("attachment-download", () => {
      downloadEvents += 1;
    });

    el.renderRoot.querySelector("[data-action='attachment-open']").click();
    el.renderRoot.querySelector("[data-action='attachment-download']").click();

    expect(openEvents).to.equal(0);
    expect(downloadEvents).to.equal(0);
  });
});
