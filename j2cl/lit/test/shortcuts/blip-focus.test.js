// G-PORT-7 (#1116): tests for the blip-focus navigation helper.
import { fixture, expect, html } from "@open-wc/testing";
import "../../src/elements/wave-blip.js";
import { moveBlipFocus, clearBlipFocus, setFocusedBlip } from "../../src/shortcuts/blip-focus.js";

async function threeBlips() {
  const root = await fixture(html`
    <div>
      <wave-blip data-blip-id="b1" data-wave-id="w" author-name="A"></wave-blip>
      <wave-blip data-blip-id="b2" data-wave-id="w" author-name="B"></wave-blip>
      <wave-blip data-blip-id="b3" data-wave-id="w" author-name="C"></wave-blip>
    </div>
  `);
  return root;
}

describe("moveBlipFocus", () => {
  it("returns false when there are no blips", () => {
    const empty = document.createElement("div");
    expect(moveBlipFocus(1, empty)).to.equal(false);
  });

  it("on first j, focuses the first blip", async () => {
    const root = await threeBlips();
    expect(moveBlipFocus(1, root)).to.equal(true);
    const focused = root.querySelector("wave-blip[focused]");
    expect(focused).to.not.equal(null);
    expect(focused.getAttribute("data-blip-id")).to.equal("b1");
    expect(focused.getAttribute("data-blip-focused")).to.equal("true");
  });

  it("on first k, focuses the last blip", async () => {
    const root = await threeBlips();
    expect(moveBlipFocus(-1, root)).to.equal(true);
    const focused = root.querySelector("wave-blip[focused]");
    expect(focused.getAttribute("data-blip-id")).to.equal("b3");
  });

  it("j after b1 moves to b2", async () => {
    const root = await threeBlips();
    moveBlipFocus(1, root); // b1
    moveBlipFocus(1, root); // b2
    const focused = root.querySelector("wave-blip[focused]");
    expect(focused.getAttribute("data-blip-id")).to.equal("b2");
    // and only one focused at a time.
    expect(root.querySelectorAll("wave-blip[focused]").length).to.equal(1);
  });

  it("j wraps from last to first", async () => {
    const root = await threeBlips();
    moveBlipFocus(-1, root); // b3
    moveBlipFocus(1, root);  // wrap to b1
    expect(root.querySelector("wave-blip[focused]").getAttribute("data-blip-id")).to.equal("b1");
  });

  it("k wraps from first to last", async () => {
    const root = await threeBlips();
    moveBlipFocus(1, root);  // b1
    moveBlipFocus(-1, root); // wrap to b3
    expect(root.querySelector("wave-blip[focused]").getAttribute("data-blip-id")).to.equal("b3");
  });

  it("skips hidden blips", async () => {
    const root = await fixture(html`
      <div>
        <wave-blip data-blip-id="b1"></wave-blip>
        <wave-blip data-blip-id="b2" hidden></wave-blip>
        <wave-blip data-blip-id="b3"></wave-blip>
      </div>
    `);
    moveBlipFocus(1, root); // b1
    moveBlipFocus(1, root); // skip b2 -> b3
    expect(root.querySelector("wave-blip[focused]").getAttribute("data-blip-id")).to.equal("b3");
  });

  it("setFocusedBlip fires wave-blip-focus-changed", async () => {
    const root = await threeBlips();
    const target = root.querySelectorAll("wave-blip")[1];
    let fired = null;
    target.addEventListener("wave-blip-focus-changed", (e) => {
      fired = e.detail;
    });
    setFocusedBlip(target, Array.from(root.querySelectorAll("wave-blip")));
    expect(fired).to.deep.equal({ blipId: "b2", waveId: "w" });
  });
});

describe("clearBlipFocus", () => {
  it("returns false when nothing focused", async () => {
    const root = await threeBlips();
    expect(clearBlipFocus(root)).to.equal(false);
  });

  it("clears focused attr from every blip", async () => {
    const root = await threeBlips();
    moveBlipFocus(1, root);
    expect(clearBlipFocus(root)).to.equal(true);
    expect(root.querySelectorAll("wave-blip[focused]").length).to.equal(0);
    expect(root.querySelectorAll("wave-blip[data-blip-focused]").length).to.equal(0);
  });
});
