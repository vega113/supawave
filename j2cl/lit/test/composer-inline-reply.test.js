import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/composer-inline-reply.js";

describe("<composer-inline-reply>", () => {
  it("shows disabled stale-basis state with the reply target label", async () => {
    const el = await fixture(html`
      <composer-inline-reply
        target-label="Root blip"
        status="Selection changed before submit completed. Review the draft and retry."
        stale-basis
      ></composer-inline-reply>
    `);

    const textarea = el.renderRoot.querySelector("textarea");
    const button = el.renderRoot.querySelector("composer-submit-affordance");
    expect(el.renderRoot.textContent).to.include("Root blip");
    expect(textarea.disabled).to.equal(true);
    expect(button.disabled).to.equal(true);
    expect(el.renderRoot.textContent).to.include("Selection changed");
  });

  it("emits draft-change and reply-submit events when available", async () => {
    const el = await fixture(html`
      <composer-inline-reply available target-label="Root blip"></composer-inline-reply>
    `);
    const textarea = el.renderRoot.querySelector("textarea");
    const draftEvent = oneEvent(el, "draft-change");

    textarea.value = "hello";
    textarea.dispatchEvent(new Event("input", { bubbles: true, composed: true }));

    expect((await draftEvent).detail.value).to.equal("hello");

    const submitEvent = oneEvent(el, "reply-submit");
    el.renderRoot.querySelector("composer-submit-affordance").dispatchEvent(
      new CustomEvent("submit-affordance", { bubbles: true, composed: true })
    );
    expect(await submitEvent).to.exist;
  });
});
