import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/wavy-wave-header-actions.js";

function actionButton(el, action) {
  return el.renderRoot.querySelector(`button[data-action="${action}"]`);
}

async function createActions(properties = {}) {
  const defaults = {
    sourceWaveId: "example.com/w+header",
    participants: ["alice@example.com", "bob@example.com"],
    public: false,
    lockState: "unlocked"
  };
  const props = { ...defaults, ...properties };
  const el = await fixture(
    html`<wavy-wave-header-actions
      source-wave-id=${props.sourceWaveId}
      lock-state=${props.lockState}
      .participants=${props.participants}
      .public=${props.public}
      ?disabled=${props.disabled || false}
    ></wavy-wave-header-actions>`
  );
  await el.updateComplete;
  return el;
}

describe("<wavy-wave-header-actions>", () => {
  afterEach(() => {
    document.body.querySelectorAll("wavy-confirm-dialog").forEach((dialog) => dialog.remove());
  });

  it("renders Add participant, New wave, Public/private, and Lock buttons", async () => {
    const el = await createActions();

    const actions = Array.from(
      el.renderRoot.querySelectorAll("button[data-action]")
    ).map((button) => button.dataset.action);

    expect(actions).to.deep.equal([
      "add-participant",
      "new-with-participants",
      "publicity-toggle",
      "lock-toggle"
    ]);
    expect(actionButton(el, "add-participant").getAttribute("aria-label")).to.equal(
      "Add participant"
    );
    expect(
      actionButton(el, "new-with-participants").getAttribute("aria-label")
    ).to.equal("New wave with current participants");
    expect(actionButton(el, "publicity-toggle").getAttribute("aria-label")).to.equal(
      "Make wave public"
    );
    expect(actionButton(el, "lock-toggle").getAttribute("aria-label")).to.equal(
      "Lock root blip"
    );
  });

  it("disables write buttons when no source wave is selected", async () => {
    const el = await createActions({ sourceWaveId: "" });

    for (const button of el.renderRoot.querySelectorAll("button[data-action]")) {
      expect(button.disabled, `${button.dataset.action} should be disabled`).to.be.true;
    }
  });

  it("opens add-participant dialog and emits trimmed addresses", async () => {
    const el = await createActions();

    actionButton(el, "add-participant").click();
    await el.updateComplete;
    const input = el.renderRoot.querySelector('input[name="participant-addresses"]');
    expect(input).to.exist;
    input.value = " carol@example.com, dave@example.com ,, ";
    input.dispatchEvent(new Event("input", { bubbles: true, composed: true }));
    await el.updateComplete;

    const eventPromise = oneEvent(el, "wave-add-participant-requested");
    actionButton(el, "add-participant-submit").click();
    const event = await eventPromise;

    expect(event.detail).to.deep.equal({
      sourceWaveId: "example.com/w+header",
      addresses: ["carol@example.com", "dave@example.com"]
    });
    expect(event.bubbles).to.be.true;
    expect(event.composed).to.be.true;
  });

  it("starts a new wave with current participants excluding the shared-domain participant", async () => {
    const el = await createActions({
      participants: ["@example.com", "alice@example.com", "bob@example.com"]
    });

    const eventPromise = oneEvent(el, "wave-new-with-participants-requested");
    actionButton(el, "new-with-participants").click();
    const event = await eventPromise;

    expect(event.detail).to.deep.equal({
      sourceWaveId: "example.com/w+header",
      participants: ["alice@example.com", "bob@example.com"]
    });
  });

  it("confirms public/private changes before emitting the toggle event", async () => {
    const el = await createActions({ public: true });
    let emitted = false;
    el.addEventListener("wave-publicity-toggle-requested", () => {
      emitted = true;
    });

    const confirmPromise = oneEvent(document.body, "wavy-confirm-requested");
    actionButton(el, "publicity-toggle").click();
    const confirmEvent = await confirmPromise;

    expect(emitted).to.be.false;
    expect(confirmEvent.detail.requestId).to.match(/^wavy-wave-header-actions:publicity:/);
    expect(confirmEvent.detail.message).to.contain("Make this wave private");
    expect(confirmEvent.detail.tone).to.equal("destructive");

    const eventPromise = oneEvent(el, "wave-publicity-toggle-requested");
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-resolved", {
        bubbles: true,
        composed: true,
        detail: { requestId: confirmEvent.detail.requestId, confirmed: true }
      })
    );
    const event = await eventPromise;

    expect(event.detail).to.deep.equal({
      sourceWaveId: "example.com/w+header",
      currentlyPublic: true,
      nextPublic: false
    });
  });

  it("drops pending confirmations when the selected source wave changes", async () => {
    const el = await createActions({ lockState: "root" });
    let emitted = false;
    el.addEventListener("wave-root-lock-toggle-requested", () => {
      emitted = true;
    });

    const confirmPromise = oneEvent(document.body, "wavy-confirm-requested");
    actionButton(el, "lock-toggle").click();
    const confirmEvent = await confirmPromise;
    el.sourceWaveId = "example.com/w+other";
    await el.updateComplete;

    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-resolved", {
        bubbles: true,
        composed: true,
        detail: { requestId: confirmEvent.detail.requestId, confirmed: true }
      })
    );

    expect(emitted).to.be.false;
  });

  it("closes the add-participant draft when the selected source wave changes", async () => {
    const el = await createActions();
    actionButton(el, "add-participant").click();
    await el.updateComplete;
    const input = el.renderRoot.querySelector('input[name="participant-addresses"]');
    input.value = "carol@example.com";
    input.dispatchEvent(new Event("input", { bubbles: true, composed: true }));
    await el.updateComplete;

    el.sourceWaveId = "example.com/w+other";
    await el.updateComplete;

    expect(el.renderRoot.querySelector('input[name="participant-addresses"]')).to.not.exist;
  });

  it("mounts the shared confirm dialog before requesting public/private confirmation", async () => {
    const el = await createActions();
    expect(document.body.querySelector("wavy-confirm-dialog")).to.not.exist;

    const confirmPromise = oneEvent(document.body, "wavy-confirm-requested");
    actionButton(el, "publicity-toggle").click();
    const confirmEvent = await confirmPromise;

    const dialog = document.body.querySelector("wavy-confirm-dialog");
    expect(dialog).to.exist;
    await dialog.updateComplete;
    expect(dialog.open).to.be.true;
    expect(confirmEvent.detail.tone).to.equal("default");
  });

  it("confirms lock-state changes before emitting the lock toggle event", async () => {
    const el = await createActions({ lockState: "root" });

    const confirmPromise = oneEvent(document.body, "wavy-confirm-requested");
    actionButton(el, "lock-toggle").click();
    const confirmEvent = await confirmPromise;

    expect(confirmEvent.detail.requestId).to.match(/^wavy-wave-header-actions:lock:/);
    expect(confirmEvent.detail.message).to.contain("Lock the full wave");
    expect(confirmEvent.detail.tone).to.equal("destructive");

    const eventPromise = oneEvent(el, "wave-root-lock-toggle-requested");
    document.body.dispatchEvent(
      new CustomEvent("wavy-confirm-resolved", {
        bubbles: true,
        composed: true,
        detail: { requestId: confirmEvent.detail.requestId, confirmed: true }
      })
    );
    const event = await eventPromise;

    expect(event.detail).to.deep.equal({
      sourceWaveId: "example.com/w+header",
      currentLockState: "root",
      nextLockState: "all"
    });
  });
});
