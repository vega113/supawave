import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-main-region.js";

describe("<shell-main-region>", () => {
  it("uses the main landmark", async () => {
    const el = await fixture(html`<shell-main-region></shell-main-region>`);
    expect(el.renderRoot.querySelector("main")).to.exist;
  });

  it("slots light-DOM children so the workflow mount host stays queryable from document", async () => {
    const el = await fixture(html`
      <shell-main-region>
        <section
          id="j2cl-root-shell-workflow"
          data-j2cl-root-shell-workflow="true"
        ></section>
      </shell-main-region>
    `);
    expect(el.renderRoot.querySelector("slot")).to.exist;
    expect(el.renderRoot.querySelector("#j2cl-root-shell-workflow")).to.not.exist;
    expect(el.querySelector("#j2cl-root-shell-workflow")).to.exist;
    expect(document.getElementById("j2cl-root-shell-workflow")).to.exist;
  });
});
