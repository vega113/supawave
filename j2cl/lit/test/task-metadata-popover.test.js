import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/task-metadata-popover.js";

describe("<task-metadata-popover>", () => {
  const participants = [
    { address: "alice@example.com", displayName: "Alice" },
    { address: "bob@example.com", displayName: "Bob" }
  ];

  it("renders a modal task dialog and focuses the assignee field when opened", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        task-id="task-1"
        assignee-address="alice@example.com"
        due-date="2026-04-30"
        .participants=${participants}
      ></task-metadata-popover>
    `);

    const dialog = el.renderRoot.querySelector("[role='dialog']");
    const assignee = el.renderRoot.querySelector("select[name='assignee']");
    expect(dialog.getAttribute("aria-modal")).to.equal("true");
    expect(dialog.getAttribute("aria-labelledby")).to.not.equal("");
    expect(assignee.value).to.equal("alice@example.com");
    expect(assignee).to.equal(el.shadowRoot.activeElement);
  });

  it("submits semantic task metadata on Enter", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        task-id="task-1"
        .participants=${participants}
      ></task-metadata-popover>
    `);
    el.renderRoot.querySelector("select[name='assignee']").value = "bob@example.com";
    el.renderRoot.querySelector("input[name='dueDate']").value = "2026-05-01";
    const eventPromise = oneEvent(el, "task-metadata-submit");

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));

    expect((await eventPromise).detail).to.deep.equal({
      taskId: "task-1",
      assigneeAddress: "bob@example.com",
      dueDate: "2026-05-01"
    });
  });

  it("does not submit when Enter belongs to the assignee select", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        task-id="task-1"
        .participants=${participants}
      ></task-metadata-popover>
    `);
    let submitCount = 0;
    el.addEventListener("task-metadata-submit", () => {
      submitCount += 1;
    });

    el.renderRoot
      .querySelector("select[name='assignee']")
      .dispatchEvent(
        new KeyboardEvent("keydown", { key: "Enter", bubbles: true, composed: true })
      );
    await el.updateComplete;

    expect(submitCount).to.equal(0);
  });

  it("traps Tab inside the modal task dialog", async () => {
    const el = await fixture(html`
      <task-metadata-popover open task-id="task-1"></task-metadata-popover>
    `);
    const controls = el.renderRoot.querySelectorAll("select, input, button");
    controls[controls.length - 1].focus();

    controls[controls.length - 1].dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Tab",
        bubbles: true,
        cancelable: true,
        composed: true
      })
    );

    expect(controls[0]).to.equal(el.shadowRoot.activeElement);

    controls[0].dispatchEvent(
      new KeyboardEvent("keydown", {
        key: "Tab",
        shiftKey: true,
        bubbles: true,
        cancelable: true,
        composed: true
      })
    );

    expect(controls[controls.length - 1]).to.equal(el.shadowRoot.activeElement);
  });

  it("emits cancel close events with focus restoration target", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        focus-target-id="task-trigger-1"
      ></task-metadata-popover>
    `);
    const closePromise = oneEvent(el, "overlay-close");

    el.renderRoot.querySelector("button[type='button']").click();

    expect((await closePromise).detail).to.deep.equal({
      reason: "cancel",
      focusTargetId: "task-trigger-1"
    });
  });

  it("keeps invalid due date visible and closes on Escape", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        task-id="task-1"
        focus-target-id="task-trigger-1"
      ></task-metadata-popover>
    `);
    const dueDate = el.renderRoot.querySelector("input[name='dueDate']");
    dueDate.value = "tomorrow";

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await el.updateComplete;

    expect(dueDate.value).to.equal("tomorrow");
    expect(el.renderRoot.querySelector("[role='alert']").textContent).to.include(
      "Use YYYY-MM-DD"
    );

    const closePromise = oneEvent(el, "overlay-close");
    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Escape", bubbles: true }));
    expect((await closePromise).detail).to.deep.equal({
      reason: "escape",
      focusTargetId: "task-trigger-1"
    });
  });

  it("rejects impossible calendar dates", async () => {
    const el = await fixture(html`
      <task-metadata-popover open task-id="task-1"></task-metadata-popover>
    `);
    el.renderRoot.querySelector("input[name='dueDate']").value = "2026-99-99";

    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await el.updateComplete;

    expect(el.renderRoot.querySelector("[role='alert']").textContent).to.include(
      "Use YYYY-MM-DD"
    );
    expect(el.renderRoot.querySelector("input[name='dueDate']").getAttribute("aria-invalid")).to.equal(
      "true"
    );
    expect(
      el.renderRoot.querySelector("input[name='dueDate']").getAttribute("aria-describedby")
    ).to.equal(el.renderRoot.querySelector("[role='alert']").id);
  });
});
