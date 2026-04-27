import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-task-affordance.js";

/**
 * F-3.S2 (#1038, R-5.4) tests for the per-blip task affordance.
 * Verifies the toggle event contract, aria-checked reflection,
 * details-popover wiring, and the metadata-changed event.
 */
describe("<wavy-task-affordance>", () => {
  it("registers as a custom element", () => {
    expect(customElements.get("wavy-task-affordance")).to.exist;
  });

  it("renders toggle + details buttons with default state", async () => {
    const el = await fixture(html`
      <wavy-task-affordance data-blip-id="b1" data-wave-id="w1"></wavy-task-affordance>
    `);
    const toggle = el.renderRoot.querySelector('[data-task-toggle-trigger="true"]');
    const details = el.renderRoot.querySelector('[data-task-details-trigger="true"]');
    expect(toggle).to.exist;
    expect(details).to.exist;
    expect(toggle.getAttribute("aria-checked")).to.equal("false");
    expect(toggle.getAttribute("role")).to.equal("checkbox");
    expect(details.getAttribute("aria-haspopup")).to.equal("dialog");
    expect(details.getAttribute("aria-expanded")).to.equal("false");
  });

  it("emits wave-blip-task-toggled when the toggle button is clicked", async () => {
    const el = await fixture(html`
      <wavy-task-affordance data-blip-id="b42" data-wave-id="w7"></wavy-task-affordance>
    `);
    const toggle = el.renderRoot.querySelector('[data-task-toggle-trigger="true"]');
    const eventPromise = oneEvent(el, "wave-blip-task-toggled");
    toggle.click();
    const event = await eventPromise;
    expect(event.detail.blipId).to.equal("b42");
    expect(event.detail.waveId).to.equal("w7");
    expect(event.detail.completed).to.equal(true);
  });

  it("flips aria-checked and the data-task-completed attribute on toggle", async () => {
    const el = await fixture(html`
      <wavy-task-affordance data-blip-id="b1"></wavy-task-affordance>
    `);
    const toggle = el.renderRoot.querySelector('[data-task-toggle-trigger="true"]');
    expect(toggle.getAttribute("aria-checked")).to.equal("false");
    expect(el.hasAttribute("data-task-completed")).to.equal(false);

    toggle.click();
    await el.updateComplete;

    const updatedToggle = el.renderRoot.querySelector('[data-task-toggle-trigger="true"]');
    expect(updatedToggle.getAttribute("aria-checked")).to.equal("true");
    expect(el.hasAttribute("data-task-completed")).to.equal(true);
  });

  it("toggles back to open after a second click", async () => {
    const el = await fixture(html`
      <wavy-task-affordance data-blip-id="b1" data-task-completed></wavy-task-affordance>
    `);
    const toggle = el.renderRoot.querySelector('[data-task-toggle-trigger="true"]');
    expect(toggle.getAttribute("aria-checked")).to.equal("true");
    const eventPromise = oneEvent(el, "wave-blip-task-toggled");
    toggle.click();
    const event = await eventPromise;
    expect(event.detail.completed).to.equal(false);
  });

  it("announces the completion state via the aria-live region", async () => {
    const el = await fixture(html`
      <wavy-task-affordance data-blip-id="b1"></wavy-task-affordance>
    `);
    const announce = el.renderRoot.querySelector("[data-task-announce]");
    expect(announce).to.exist;
    expect(announce.getAttribute("aria-live")).to.equal("polite");
    expect(announce.textContent.trim()).to.equal("");

    const toggle = el.renderRoot.querySelector('[data-task-toggle-trigger="true"]');
    toggle.click();
    await el.updateComplete;
    const announceAfter = el.renderRoot.querySelector("[data-task-announce]");
    expect(announceAfter.textContent.trim()).to.equal("Task completed");

    toggle.click();
    await el.updateComplete;
    const announceAfter2 = el.renderRoot.querySelector("[data-task-announce]");
    expect(announceAfter2.textContent.trim()).to.equal("Task reopened");
  });

  it("opens the task-metadata-popover when the details button is clicked", async () => {
    const el = await fixture(html`
      <wavy-task-affordance data-blip-id="b1"></wavy-task-affordance>
    `);
    const details = el.renderRoot.querySelector('[data-task-details-trigger="true"]');
    expect(el.renderRoot.querySelector("task-metadata-popover")).to.equal(null);

    details.click();
    await el.updateComplete;

    const popover = el.renderRoot.querySelector("task-metadata-popover");
    expect(popover).to.exist;
    expect(popover.hasAttribute("open")).to.equal(true);
    expect(el.hasAttribute("data-popover-open")).to.equal(true);
    const detailsAfter = el.renderRoot.querySelector('[data-task-details-trigger="true"]');
    expect(detailsAfter.getAttribute("aria-expanded")).to.equal("true");
  });

  it("emits wave-blip-task-metadata-changed on popover submit", async () => {
    const el = await fixture(html`
      <wavy-task-affordance
        data-blip-id="b1"
        data-wave-id="w1"
        .participants=${[{ address: "alice@example.com", displayName: "Alice" }]}
      ></wavy-task-affordance>
    `);
    const details = el.renderRoot.querySelector('[data-task-details-trigger="true"]');
    details.click();
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("task-metadata-popover");
    expect(popover).to.exist;

    const eventPromise = oneEvent(el, "wave-blip-task-metadata-changed");
    popover.dispatchEvent(
      new CustomEvent("task-metadata-submit", {
        bubbles: true,
        composed: true,
        detail: { taskId: "b1", assigneeAddress: "alice@example.com", dueDate: "2026-05-01" }
      })
    );
    const event = await eventPromise;
    expect(event.detail.blipId).to.equal("b1");
    expect(event.detail.assigneeAddress).to.equal("alice@example.com");
    expect(event.detail.dueDate).to.equal("2026-05-01");
  });

  it("closes the popover and restores focus to the trigger on overlay-close", async () => {
    const el = await fixture(html`
      <wavy-task-affordance data-blip-id="b1"></wavy-task-affordance>
    `);
    const details = el.renderRoot.querySelector('[data-task-details-trigger="true"]');
    details.click();
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("task-metadata-popover");
    popover.dispatchEvent(
      new CustomEvent("overlay-close", {
        bubbles: true,
        composed: true,
        detail: { reason: "escape", focusTargetId: "task-details-trigger" }
      })
    );
    await el.updateComplete;
    expect(el.renderRoot.querySelector("task-metadata-popover")).to.equal(null);
    expect(el.hasAttribute("data-popover-open")).to.equal(false);
  });

  it("does not bubble overlay-close past the affordance host", async () => {
    const el = await fixture(html`
      <wavy-task-affordance data-blip-id="b1"></wavy-task-affordance>
    `);
    const details = el.renderRoot.querySelector('[data-task-details-trigger="true"]');
    details.click();
    await el.updateComplete;
    const popover = el.renderRoot.querySelector("task-metadata-popover");

    let outerSawClose = false;
    const parent = document.createElement("div");
    document.body.appendChild(parent);
    parent.appendChild(el);
    parent.addEventListener("overlay-close", () => {
      outerSawClose = true;
    });
    popover.dispatchEvent(
      new CustomEvent("overlay-close", {
        bubbles: true,
        composed: true,
        detail: { reason: "cancel" }
      })
    );
    await el.updateComplete;
    expect(outerSawClose).to.equal(false);
    parent.remove();
  });
});
