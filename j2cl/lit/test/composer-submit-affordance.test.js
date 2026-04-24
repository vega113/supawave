import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/composer-submit-affordance.js";

describe("<composer-submit-affordance>", () => {
  it("renders a real submit button with visible status and error text", async () => {
    const el = await fixture(html`
      <composer-submit-affordance
        label="Send reply"
        status="Submitting the reply"
        error="Network failed"
      ></composer-submit-affordance>
    `);

    const button = el.renderRoot.querySelector("button");
    expect(button).to.exist;
    expect(button.textContent.trim()).to.equal("Send reply");
    expect(button.getAttribute("aria-label")).to.equal("Send reply");
    expect(el.renderRoot.textContent).to.include("Network failed");
  });

  it("disables the button while busy", async () => {
    const el = await fixture(html`
      <composer-submit-affordance label="Create wave" busy></composer-submit-affordance>
    `);

    const button = el.renderRoot.querySelector("button");
    expect(button.disabled).to.equal(true);
    expect(button.getAttribute("aria-busy")).to.equal("true");
  });

  it("dispatches submit-affordance when clicked", async () => {
    const el = await fixture(html`
      <composer-submit-affordance label="Create wave"></composer-submit-affordance>
    `);
    const eventPromise = oneEvent(el, "submit-affordance");

    el.renderRoot.querySelector("button").click();

    expect(await eventPromise).to.exist;
  });
});
