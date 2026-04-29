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

  it("j clamps at last blip (no wrap)", async () => {
    const root = await threeBlips();
    moveBlipFocus(-1, root); // b3
    const result = moveBlipFocus(1, root); // at end, consume key but stay on b3
    expect(result).to.equal(true);
    expect(root.querySelector("wave-blip[focused]").getAttribute("data-blip-id")).to.equal("b3");
  });

  it("k clamps at first blip (no wrap)", async () => {
    const root = await threeBlips();
    moveBlipFocus(1, root);  // b1
    const result = moveBlipFocus(-1, root); // at start, consume key but stay on b1
    expect(result).to.equal(true);
    expect(root.querySelector("wave-blip[focused]").getAttribute("data-blip-id")).to.equal("b1");
  });

  it("skips blips inside collapsed thread containers", async () => {
    const root = await fixture(html`
      <div>
        <wave-blip data-blip-id="b1"></wave-blip>
        <div class="j2cl-read-thread-collapsed">
          <wave-blip data-blip-id="b2"></wave-blip>
        </div>
        <wave-blip data-blip-id="b3"></wave-blip>
      </div>
    `);
    moveBlipFocus(1, root); // b1
    moveBlipFocus(1, root); // skip b2 (collapsed) -> b3
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
    setFocusedBlip(target);
    expect(fired).to.deep.equal({ blipId: "b2", waveId: "w" });
  });

  it("setFocusedBlip adds j2cl-read-blip-focused class and removes from others", async () => {
    const root = await threeBlips();
    const blips = Array.from(root.querySelectorAll("wave-blip"));
    setFocusedBlip(blips[1]);
    expect(blips[1].classList.contains("j2cl-read-blip-focused")).to.equal(true);
    expect(blips[0].classList.contains("j2cl-read-blip-focused")).to.equal(false);
    expect(blips[2].classList.contains("j2cl-read-blip-focused")).to.equal(false);
    // Moving focus removes the class from the previous target.
    setFocusedBlip(blips[2]);
    expect(blips[2].classList.contains("j2cl-read-blip-focused")).to.equal(true);
    expect(blips[1].classList.contains("j2cl-read-blip-focused")).to.equal(false);
  });

  it("setFocusedBlip clears stale markers from hidden (parked) blips", async () => {
    const root = await fixture(html`
      <div>
        <wave-blip data-blip-id="b1" data-wave-id="w" author-name="A"></wave-blip>
        <wave-blip data-blip-id="b2" data-wave-id="w" author-name="B" hidden focused data-blip-focused="true"></wave-blip>
      </div>
    `);
    const blips = Array.from(root.querySelectorAll("wave-blip"));
    // b2 is hidden but has stale focus markers from the renderer
    setFocusedBlip(blips[0]);
    expect(blips[1].hasAttribute("focused")).to.equal(false);
    expect(blips[1].hasAttribute("data-blip-focused")).to.equal(false);
    expect(blips[1].classList.contains("j2cl-read-blip-focused")).to.equal(false);
  });

  it("j continues from renderer-established focus (j2cl-read-blip-focused class)", async () => {
    const root = await threeBlips();
    const blips = Array.from(root.querySelectorAll("wave-blip"));
    // Simulate the Java renderer setting focus on b2 without the Lit attribute.
    blips[1].classList.add("j2cl-read-blip-focused");
    moveBlipFocus(1, root); // should continue from b2 -> b3
    const focused = root.querySelector("wave-blip[focused]");
    expect(focused.getAttribute("data-blip-id")).to.equal("b3");
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
    expect(root.querySelectorAll("wave-blip.j2cl-read-blip-focused").length).to.equal(0);
  });

  it("clears renderer-established focus (j2cl-read-blip-focused only)", async () => {
    const root = await threeBlips();
    const blips = Array.from(root.querySelectorAll("wave-blip"));
    // Simulate Java renderer setting focus without the Lit `focused` attribute.
    blips[0].classList.add("j2cl-read-blip-focused");
    expect(clearBlipFocus(root)).to.equal(true);
    expect(blips[0].classList.contains("j2cl-read-blip-focused")).to.equal(false);
  });

  it("clears aria-current from previously renderer-focused blip on Esc", async () => {
    const root = await threeBlips();
    const blips = Array.from(root.querySelectorAll("wave-blip"));
    blips[1].setAttribute("aria-current", "true");
    blips[1].classList.add("j2cl-read-blip-focused");
    clearBlipFocus(root);
    expect(blips[1].hasAttribute("aria-current")).to.equal(false);
  });
});

describe("setFocusedBlip aria-current cleanup", () => {
  it("removes aria-current from blips that lose focus via j/k", async () => {
    const root = await threeBlips();
    const blips = Array.from(root.querySelectorAll("wave-blip"));
    blips[0].setAttribute("aria-current", "true");
    blips[0].classList.add("j2cl-read-blip-focused");
    setFocusedBlip(blips[1]);
    expect(blips[0].hasAttribute("aria-current")).to.equal(false);
    expect(blips[1].hasAttribute("focused")).to.equal(true);
  });
});
