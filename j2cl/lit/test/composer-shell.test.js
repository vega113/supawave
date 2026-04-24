import { fixture, expect, html, nextFrame } from "@open-wc/testing";
import "../src/elements/composer-shell.js";

describe("<composer-shell>", () => {
  it("shows reply section when reply slot has content", async () => {
    const el = await fixture(html`
      <composer-shell>
        <p slot="create">Create form</p>
        <p slot="reply">Reply form</p>
        <p slot="status">Ready</p>
      </composer-shell>
    `);

    await nextFrame();

    const sections = el.renderRoot.querySelectorAll("section");
    expect(sections.length).to.equal(2);
    expect(el.renderRoot.querySelector("slot[name='create']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='reply']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='status']")).to.exist;
    expect(el.renderRoot.querySelector("section[hidden]")).to.not.exist;
  });

  it("hides reply section when reply slot is empty", async () => {
    const el = await fixture(html`
      <composer-shell>
        <p slot="create">Create form</p>
      </composer-shell>
    `);

    await nextFrame();

    const replySection = el.renderRoot.querySelector(
      "section[aria-labelledby='composer-reply-title']"
    );
    expect(replySection).to.exist;
    expect(replySection.hidden).to.equal(true);
  });
});
