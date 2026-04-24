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

  it("generates unique heading ids for multiple task dialogs", async () => {
    const first = await fixture(html`<task-metadata-popover open></task-metadata-popover>`);
    const second = await fixture(html`<task-metadata-popover open></task-metadata-popover>`);

    expect(first.renderRoot.querySelector("h2").id).to.not.equal(
      second.renderRoot.querySelector("h2").id
    );
    expect(first.renderRoot.querySelector("[role='dialog']").getAttribute("aria-labelledby")).to.equal(
      first.renderRoot.querySelector("h2").id
    );
  });

  it("filters malformed participant entries before rendering options", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        .participants=${[null, "bad", { address: "valid@example.com", displayName: "Valid" }]}
      ></task-metadata-popover>
    `);

    const options = el.renderRoot.querySelectorAll("select[name='assignee'] option");
    expect(options.length).to.equal(2);
    expect(options[1].value).to.equal("valid@example.com");
  });

  it("trims participant addresses before rendering and submitting options", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        task-id="task-1"
        assignee-address=" padded@example.com "
        .participants=${[{ address: " padded@example.com ", displayName: "Padded" }]}
      ></task-metadata-popover>
    `);
    const eventPromise = oneEvent(el, "task-metadata-submit");

    expect(el.renderRoot.querySelector("select[name='assignee']").value).to.equal(
      "padded@example.com"
    );

    el.renderRoot.querySelector("button[type='submit']").click();

    expect((await eventPromise).detail.assigneeAddress).to.equal("padded@example.com");
  });

  it("preserves a current assignee that is no longer in the participant list", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        task-id="task-1"
        assignee-address=" carol@example.com "
        .participants=${participants}
      ></task-metadata-popover>
    `);
    const options = Array.from(
      el.renderRoot.querySelectorAll("select[name='assignee'] option")
    );
    const eventPromise = oneEvent(el, "task-metadata-submit");

    expect(options.map(option => option.value)).to.include("carol@example.com");
    expect(el.renderRoot.querySelector("select[name='assignee']").value).to.equal(
      "carol@example.com"
    );

    el.renderRoot.querySelector("input[name='dueDate']").value = "2026-05-02";
    el.renderRoot.querySelector("button[type='submit']").click();

    expect((await eventPromise).detail).to.deep.equal({
      taskId: "task-1",
      assigneeAddress: "carol@example.com",
      dueDate: "2026-05-02"
    });
  });

  it("does not duplicate a current assignee that only differs by whitespace or case", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        task-id="task-1"
        assignee-address=" ALICE@example.com "
        .participants=${participants}
      ></task-metadata-popover>
    `);
    const options = Array.from(
      el.renderRoot.querySelectorAll("select[name='assignee'] option")
    );

    expect(options.length).to.equal(3);
    expect(options.filter(option => option.value === "alice@example.com").length).to.equal(1);
    expect(el.renderRoot.querySelector("select[name='assignee']").value).to.equal(
      "alice@example.com"
    );

    const eventPromise = oneEvent(el, "task-metadata-submit");
    el.renderRoot.querySelector("button[type='submit']").click();

    expect((await eventPromise).detail.assigneeAddress).to.equal("alice@example.com");
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
    const dueDateInput = el.renderRoot.querySelector("input[name='dueDate']");
    dueDateInput.value = "2026-05-01";
    const eventPromise = oneEvent(el, "task-metadata-submit");

    dueDateInput.dispatchEvent(
      new KeyboardEvent("keydown", { key: "Enter", bubbles: true, composed: true })
    );

    expect((await eventPromise).detail).to.deep.equal({
      taskId: "task-1",
      assigneeAddress: "bob@example.com",
      dueDate: "2026-05-01"
    });
  });

  it("trims due date input before validation and submit", async () => {
    const el = await fixture(html`
      <task-metadata-popover open task-id="task-1"></task-metadata-popover>
    `);
    let detail;
    el.addEventListener("task-metadata-submit", event => {
      detail = event.detail;
    });

    el.renderRoot.querySelector("input[name='dueDate']").value = " 2026-05-01 ";
    el.renderRoot.querySelector("button[type='submit']").click();
    await el.updateComplete;

    expect(el.renderRoot.querySelector("[role='alert']")).to.equal(null);
    expect(detail).to.deep.equal({
      taskId: "task-1",
      assigneeAddress: "",
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

    const event = await closePromise;
    expect(event.bubbles).to.equal(true);
    expect(event.composed).to.equal(true);
    expect(event.detail).to.deep.equal({
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
    const escapeEvent = await closePromise;
    expect(escapeEvent.bubbles).to.equal(true);
    expect(escapeEvent.composed).to.equal(true);
    expect(escapeEvent.detail).to.deep.equal({
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

  it("preserves the current assignee when they are absent from participants", async () => {
    const el = await fixture(html`
      <task-metadata-popover
        open
        task-id="task-1"
        assignee-address="carol@example.com"
        .participants=${participants}
      ></task-metadata-popover>
    `);

    const select = el.renderRoot.querySelector("select[name='assignee']");
    expect(select.value).to.equal("carol@example.com");

    const eventPromise = oneEvent(el, "task-metadata-submit");
    el.renderRoot.querySelector("button[type='submit']").click();
    const { detail } = await eventPromise;
    expect(detail.assigneeAddress).to.equal("carol@example.com");
  });

  it("clears stale validation errors across close and reopen", async () => {
    const el = await fixture(html`
      <task-metadata-popover open task-id="task-1"></task-metadata-popover>
    `);
    el.renderRoot.querySelector("input[name='dueDate']").value = "tomorrow";
    el.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true }));
    await el.updateComplete;

    el.open = false;
    await el.updateComplete;
    el.open = true;
    await el.updateComplete;

    expect(el.renderRoot.querySelector("[role='alert']")).to.equal(null);
    expect(el.renderRoot.querySelector("input[name='dueDate']").getAttribute("aria-invalid")).to.equal(
      "false"
    );
  });
});
