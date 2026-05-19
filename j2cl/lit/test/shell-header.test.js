import { fixture, expect, html } from "@open-wc/testing";
import "../src/elements/shell-header.js";

function ensureShellTokensLoaded() {
  if (document.querySelector("link[data-shell-tokens-test]")) return;
  const link = document.createElement("link");
  link.rel = "stylesheet";
  link.href = "/src/tokens/shell-tokens.css";
  link.dataset.shellTokensTest = "true";
  document.head.appendChild(link);
}

async function waitForShellTokens() {
  for (let i = 0; i < 50; i++) {
    const probe = document.createElement("shell-header");
    probe.setAttribute("compact-gwt-topbar", "");
    probe.style.width = "640px";
    document.body.appendChild(probe);
    const applied = getComputedStyle(probe).display === "block";
    probe.remove();
    if (applied) return;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  throw new Error("shell-tokens.css did not apply within 1000ms");
}

describe("<shell-header>", () => {
  it("uses the banner landmark", async () => {
    const el = await fixture(html`<shell-header></shell-header>`);
    expect(el.renderRoot.querySelector("header[role='banner']")).to.exist;
  });

  it("renders the signed-in action slot when signed-in", async () => {
    const el = await fixture(html`<shell-header signed-in></shell-header>`);
    expect(el.renderRoot.querySelector("slot[name='actions-signed-in']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='actions-signed-out']")).to.not.exist;
  });

  it("renders the signed-out action slot by default", async () => {
    const el = await fixture(html`<shell-header></shell-header>`);
    expect(el.renderRoot.querySelector("slot[name='actions-signed-out']")).to.exist;
    expect(el.renderRoot.querySelector("slot[name='actions-signed-in']")).to.not.exist;
  });

  it("uses compact GWT topbar layout classes", async () => {
    const el = await fixture(html`<shell-header signed-in compact-gwt-topbar></shell-header>`);
    const header = el.renderRoot.querySelector("header");

    expect(header.classList.contains("gwt-topbar-panel")).to.be.true;
    expect(header.classList.contains("is-signed-in")).to.be.true;
    expect(getComputedStyle(header).minHeight).to.equal("40px");
  });

  it("right-aligns compact signed-in actions like the GWT topbar info cluster", async () => {
    ensureShellTokensLoaded();
    await waitForShellTokens();
    const el = await fixture(html`
      <shell-header signed-in compact-gwt-topbar style="width:640px">
        <a slot="brand">SupaWave</a>
        <span slot="actions-signed-in">EN</span>
        <button slot="actions-signed-in">Save</button>
        <button slot="actions-signed-in">User</button>
      </shell-header>
    `);
    const header = el.renderRoot.querySelector("header");
    const actions = el.renderRoot.querySelector(".actions");
    const brand = el.renderRoot.querySelector(".brand");

    expect(getComputedStyle(el).display).to.equal("block");
    expect(Math.round(el.getBoundingClientRect().width)).to.equal(640);
    expect(getComputedStyle(actions).justifyContent).to.equal("flex-end");
    expect(actions.getBoundingClientRect().left).to.be.greaterThan(
      brand.getBoundingClientRect().right
    );
    expect(Math.round(actions.getBoundingClientRect().right)).to.equal(
      Math.round(header.getBoundingClientRect().right)
    );
  });
});
