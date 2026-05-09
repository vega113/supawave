import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-header.js";

describe("<wavy-header>", () => {
  it("registers the F-2.S3 wavy-header element", () => {
    expect(customElements.get("wavy-header")).to.exist;
  });

  it("renders the brand link with cyan signal-dot accent (A.1)", async () => {
    const el = await fixture(html`<wavy-header></wavy-header>`);
    await el.updateComplete;
    const brand = el.renderRoot.querySelector("a.brand");
    expect(brand).to.exist;
    expect(brand.getAttribute("href")).to.equal("/");
    expect(brand.querySelector(".brand-dot")).to.exist;
    expect(brand.querySelector(".brand-text").textContent.trim()).to.equal("SupaWave");
  });

  it("brand-dot CSS uses --wavy-signal-cyan token (A.1 contract)", () => {
    const cssText = WavyHeader_styleText();
    expect(cssText).to.include("var(--wavy-signal-cyan");
    // Sanity: ensure the dot rule itself uses the cyan token (not just
    // present elsewhere in the stylesheet for unrelated rules).
    expect(cssText).to.match(/\.brand-dot\s*\{[^}]*var\(--wavy-signal-cyan/);
  });

  it("locale picker emits 7 options with the canonical codes (A.2)", async () => {
    const el = await fixture(html`<wavy-header></wavy-header>`);
    await el.updateComplete;
    const select = el.renderRoot.querySelector("select.locale");
    expect(select).to.exist;
    expect(select.getAttribute("aria-label")).to.equal("Language");
    const options = Array.from(select.querySelectorAll("option")).map((o) => o.value);
    expect(options).to.deep.equal(["en", "de", "es", "fr", "ru", "sl", "zh_TW"]);
  });

  it("compact GWT topbar mode keeps locale labels short", async () => {
    const el = await fixture(html`<wavy-header compact-gwt-topbar></wavy-header>`);
    await el.updateComplete;
    const labels = Array.from(el.renderRoot.querySelectorAll("select.locale option"))
      .map((option) => option.textContent.trim());

    expect(labels).to.deep.equal(["EN", "DE", "ES", "FR", "RU", "SL", "ZH-TW"]);
  });

  it("compact GWT topbar mode preserves selected locale", async () => {
    const el = await fixture(html`<wavy-header compact-gwt-topbar locale="zh_TW"></wavy-header>`);
    await el.updateComplete;
    const selected = el.renderRoot.querySelector("select.locale option:checked");

    expect(selected.value).to.equal("zh_TW");
    expect(selected.textContent.trim()).to.equal("ZH-TW");
  });

  it("compact GWT topbar mode uses compact action controls", async () => {
    const el = await fixture(html`<wavy-header compact-gwt-topbar signed-in></wavy-header>`);
    await el.updateComplete;
    const bell = el.renderRoot.querySelector("button.bell");

    expect(getComputedStyle(bell).width).to.equal("32px");
    expect(getComputedStyle(bell).height).to.equal("32px");
  });

  it("change on locale picker emits wavy-locale-changed (A.2)", async () => {
    const el = await fixture(html`<wavy-header></wavy-header>`);
    await el.updateComplete;
    const select = el.renderRoot.querySelector("select.locale");
    select.value = "de";
    setTimeout(() => select.dispatchEvent(new Event("change", { bubbles: true })), 0);
    const evt = await oneEvent(el, "wavy-locale-changed");
    expect(evt.detail.locale).to.equal("de");
    expect(el.locale).to.equal("de");
  });

  it("notifications bell renders only when signed-in (A.5)", async () => {
    const elOut = await fixture(html`<wavy-header></wavy-header>`);
    await elOut.updateComplete;
    expect(elOut.renderRoot.querySelector(".bell")).to.be.null;

    const elIn = await fixture(
      html`<wavy-header signed-in data-address="alice@example.com"></wavy-header>`
    );
    await elIn.updateComplete;
    expect(elIn.renderRoot.querySelector(".bell")).to.exist;
  });

  it("bell unread dot toggles on unread-count > 0 (A.5)", async () => {
    const el = await fixture(
      html`<wavy-header signed-in data-address="alice@example.com"></wavy-header>`
    );
    await el.updateComplete;
    const dot = el.renderRoot.querySelector(".bell .dot.violet");
    expect(dot).to.exist;
    expect(dot.hasAttribute("hidden")).to.be.true;
    el.unreadCount = 4;
    await el.updateComplete;
    expect(dot.hasAttribute("hidden")).to.be.false;
  });

  it("bell unread dot uses --wavy-signal-violet, NOT cyan (A.5)", () => {
    const cssText = WavyHeader_styleText();
    expect(cssText).to.match(/\.dot\.violet\s*\{[^}]*var\(--wavy-signal-violet/);
  });

  it("mail icon links to /?view=j2cl-root&q=in:inbox (A.6)", async () => {
    const el = await fixture(
      html`<wavy-header signed-in data-address="alice@example.com"></wavy-header>`
    );
    await el.updateComplete;
    const mail = el.renderRoot.querySelector("a.mail");
    expect(mail).to.exist;
    // F-2 slice 6 (#1058): the mail icon must keep the J2CL view selector
    // so signed-in users stay on the wavy chrome rather than being kicked
    // back to the legacy GWT route. The source-of-truth href is
    // `${base}?view=j2cl-root&q=in:inbox` in wavy-header.js.
    expect(mail.getAttribute("href")).to.equal("/?view=j2cl-root&q=in:inbox");
    expect(mail.getAttribute("aria-label")).to.equal("Inbox");
  });

  it("user-menu trigger renders avatar with initials AND visible email span (A.7)", async () => {
    const el = await fixture(
      html`<wavy-header signed-in data-address="alice.smith@example.com"></wavy-header>`
    );
    await el.updateComplete;
    const userMenu = el.renderRoot.querySelector("button.user-menu");
    expect(userMenu).to.exist;
    expect(userMenu.getAttribute("aria-haspopup")).to.equal("menu");
    const avatar = userMenu.querySelector(".avatar");
    expect(avatar).to.exist;
    expect(avatar.textContent.trim()).to.equal("AS"); // alice.smith → A + S
    const email = userMenu.querySelector(".user-email");
    expect(email).to.exist;
    expect(email.textContent.trim()).to.equal("alice.smith@example.com");
  });

  it("compact GWT topbar mode hides long email while keeping avatar initials", async () => {
    const el = await fixture(
      html`<wavy-header compact-gwt-topbar signed-in data-address="alice.smith@example.com"></wavy-header>`
    );
    await el.updateComplete;
    const userMenu = el.renderRoot.querySelector("button.user-menu");
    const avatar = userMenu.querySelector(".avatar");
    const email = userMenu.querySelector(".user-email");

    expect(avatar.textContent.trim()).to.equal("AS");
    expect(getComputedStyle(email).display).to.equal("none");
  });

  it("user-menu click emits wavy-user-menu-requested with the address (A.7)", async () => {
    const el = await fixture(
      html`<wavy-header signed-in data-address="alice@example.com"></wavy-header>`
    );
    await el.updateComplete;
    const userMenu = el.renderRoot.querySelector("button.user-menu");
    setTimeout(() => userMenu.click(), 0);
    const evt = await oneEvent(el, "wavy-user-menu-requested");
    expect(evt.detail.address).to.equal("alice@example.com");
  });

  it("avatar initials handle single-name emails", async () => {
    const el = await fixture(
      html`<wavy-header signed-in data-address="bob@example.com"></wavy-header>`
    );
    await el.updateComplete;
    const avatar = el.renderRoot.querySelector(".avatar");
    expect(avatar.textContent.trim()).to.equal("BO");
  });

  it("missing address falls back to ? for initials and empty email span", async () => {
    const el = await fixture(html`<wavy-header signed-in></wavy-header>`);
    await el.updateComplete;
    const avatar = el.renderRoot.querySelector(".avatar");
    expect(avatar.textContent.trim()).to.equal("?");
    const email = el.renderRoot.querySelector(".user-email");
    expect(email).to.exist;
    expect(email.textContent.trim()).to.equal("");
  });

  it("avatar initials use first + last segment for multi-dot addresses (SSR parity)", async () => {
    const el = await fixture(
      html`<wavy-header signed-in data-address="john.q.public@x.com"></wavy-header>`
    );
    await el.updateComplete;
    const avatar = el.renderRoot.querySelector(".avatar");
    // first="john" → J, last="public" → P (not second segment "q")
    expect(avatar.textContent.trim()).to.equal("JP");
  });

  it("compact GWT topbar emits savestatus + netstatus chips with default state", async () => {
    const el = await fixture(html`<wavy-header signed-in compact-gwt-topbar></wavy-header>`);
    await el.updateComplete;
    const save = el.renderRoot.querySelector(".savestatus");
    const net = el.renderRoot.querySelector(".netstatus");
    expect(save).to.exist;
    expect(net).to.exist;
    expect(save.getAttribute("data-state")).to.equal("saved");
    expect(net.getAttribute("data-state")).to.equal("online");
    expect(save.querySelector("svg")).to.exist;
    expect(net.querySelector("svg")).to.exist;
  });

  it("non-compact wavy-header omits savestatus + netstatus", async () => {
    const el = await fixture(html`<wavy-header signed-in></wavy-header>`);
    await el.updateComplete;
    expect(el.renderRoot.querySelector(".savestatus")).to.be.null;
    expect(el.renderRoot.querySelector(".netstatus")).to.be.null;
  });

  it("compact savestatus reflects data-save-state attribute", async () => {
    const el = await fixture(
      html`<wavy-header signed-in compact-gwt-topbar data-save-state="saving"></wavy-header>`
    );
    await el.updateComplete;
    const save = el.renderRoot.querySelector(".savestatus");
    expect(save.getAttribute("data-state")).to.equal("saving");
  });

  it("compact netstatus reflects data-connection-state and swaps SVG glyph for offline", async () => {
    const el = await fixture(
      html`<wavy-header signed-in compact-gwt-topbar data-connection-state="offline"></wavy-header>`
    );
    await el.updateComplete;
    const net = el.renderRoot.querySelector(".netstatus");
    expect(net.getAttribute("data-state")).to.equal("offline");
    // Offline glyph contains the slashed-line element distinguishing wifi-off from wifi.
    const svgHtml = net.querySelector("svg").outerHTML;
    expect(svgHtml).to.include('x1="1"');
  });
});

function WavyHeader_styleText() {
  const cls = customElements.get("wavy-header");
  const styles = cls.styles;
  const arr = Array.isArray(styles) ? styles : [styles];
  return arr.map((s) => (s && s.cssText) || "").join("\n");
}
