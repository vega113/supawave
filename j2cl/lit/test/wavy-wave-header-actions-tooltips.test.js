import { expect, fixture, html } from "@open-wc/testing";
import "../src/elements/wavy-wave-header-actions.js";

describe("wavy-wave-header-actions / tooltip parity", () => {
  it("every header action button carries both aria-label and title", async () => {
    const el = await fixture(html`
      <wavy-wave-header-actions
        source-wave-id="w+abc"
      ></wavy-wave-header-actions>
    `);
    await el.updateComplete;

    const buttons = el.shadowRoot.querySelectorAll("button[data-action]");
    expect(buttons.length).to.equal(4);
    for (const button of buttons) {
      const aria = button.getAttribute("aria-label");
      const title = button.getAttribute("title");
      expect(aria, `${button.dataset.action} aria-label`).to.be.a("string").and.to.have.length.greaterThan(0);
      expect(title, `${button.dataset.action} title`).to.be.a("string").and.to.have.length.greaterThan(0);
      expect(title, `${button.dataset.action} title === aria-label`).to.equal(aria);
    }
  });
});
