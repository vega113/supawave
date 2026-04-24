import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/composer-shell.js";

describe("<composer-shell>", () => {
  it("exposes labeled create and reply sections plus a status slot", async () => {
    const el = await fixture(html`
      <composer-shell>
        <p slot="create">Create form</p>
        <p slot="reply">Reply form</p>
        <p slot="status">Ready</p>
      </composer-shell>
    `);

    const sections = el.renderRoot.querySelectorAll("section");
    expect(sections.length).to.equal(2);
    expect(el.renderRoot.querySelector("slot[name='create']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='reply']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='status']")).to.exist;
  });
});
