import { fixture, expect, html, oneEvent } from "@open-wc/testing";
import "../src/elements/composer-inline-reply.js";

describe("<composer-inline-reply>", () => {
  it("shows disabled stale-basis state with the reply target label", async () => {
    const el = await fixture(html`
      <composer-inline-reply
        target-label="Root blip"
        status="Selection changed before submit completed. Review the draft and retry."
        stale-basis
      ></composer-inline-reply>
    `);

    const textarea = el.renderRoot.querySelector("textarea");
    const button = el.renderRoot.querySelector("composer-submit-affordance");
    expect(el.renderRoot.textContent).to.include("Root blip");
    expect(textarea.disabled).to.equal(true);
    expect(button.disabled).to.equal(true);
    expect(el.renderRoot.textContent).to.include("Selection changed");
  });

  it("emits draft-change and reply-submit events when available", async () => {
    const el = await fixture(html`
      <composer-inline-reply available target-label="Root blip"></composer-inline-reply>
    `);
    const textarea = el.renderRoot.querySelector("textarea");
    const draftEvent = oneEvent(el, "draft-change");

    textarea.value = "hello";
    textarea.dispatchEvent(new Event("input", { bubbles: true, composed: true }));

    expect((await draftEvent).detail.value).to.equal("hello");

    const submitEvent = oneEvent(el, "reply-submit");
    el.renderRoot.querySelector("composer-submit-affordance").dispatchEvent(
      new CustomEvent("submit-affordance", { bubbles: true, composed: true })
    );
    expect(await submitEvent).to.exist;
  });

  it("renders rich command status and errors as live regions", async () => {
    const el = await fixture(html`
      <composer-inline-reply
        available
        target-label="Root blip"
        active-command="bold"
        command-status="Bold applied to the current draft."
        command-error="Attachment upload failed."
      ></composer-inline-reply>
    `);

    const commandStatus = el.renderRoot.querySelector("[data-command-status]");
    const commandError = el.renderRoot.querySelector("[data-command-error]");

    expect(commandStatus.getAttribute("role")).to.equal("status");
    expect(commandStatus.getAttribute("aria-live")).to.equal("polite");
    expect(commandStatus.textContent).to.include("Bold applied");
    expect(commandError.getAttribute("role")).to.equal("alert");
    expect(commandError.getAttribute("aria-live")).to.equal("assertive");
    expect(commandError.textContent).to.include("Attachment upload failed");
  });

  it("emits pasted image files for attachment upload wiring", async () => {
    const el = await fixture(html`<composer-inline-reply available></composer-inline-reply>`);
    const textarea = el.renderRoot.querySelector("textarea");
    const imageFile = new File(["png"], "paste.png", { type: "image/png" });
    let pastedFile = null;
    el.addEventListener("attachment-paste-image", (event) => {
      pastedFile = event.detail.file;
    });
    const pasteEvent = new Event("paste", { bubbles: true, cancelable: true });
    Object.defineProperty(pasteEvent, "clipboardData", {
      value: {
        items: [
          {
            type: "image/png",
            getAsFile: () => imageFile
          }
        ]
      }
    });

    textarea.dispatchEvent(pasteEvent);

    expect(pasteEvent.defaultPrevented).to.equal(true);
    expect(pastedFile).to.equal(imageFile);
  });

  it("ignores non-image pasted clipboard items", async () => {
    const el = await fixture(html`<composer-inline-reply available></composer-inline-reply>`);
    const textarea = el.renderRoot.querySelector("textarea");
    let pasteEvents = 0;
    el.addEventListener("attachment-paste-image", () => {
      pasteEvents += 1;
    });
    const pasteEvent = new Event("paste", { bubbles: true, cancelable: true });
    Object.defineProperty(pasteEvent, "clipboardData", {
      value: {
        items: [
          {
            type: "text/plain",
            getAsFile: () => new File(["text"], "note.txt", { type: "text/plain" })
          }
        ]
      }
    });

    textarea.dispatchEvent(pasteEvent);

    expect(pasteEvent.defaultPrevented).to.equal(false);
    expect(pasteEvents).to.equal(0);
  });

  it("ignores paste events with no clipboard data", async () => {
    const el = await fixture(html`<composer-inline-reply available></composer-inline-reply>`);
    const textarea = el.renderRoot.querySelector("textarea");
    let pasteEvents = 0;
    el.addEventListener("attachment-paste-image", () => {
      pasteEvents += 1;
    });
    const pasteEvent = new Event("paste", { bubbles: true, cancelable: true });

    textarea.dispatchEvent(pasteEvent);

    expect(pasteEvent.defaultPrevented).to.equal(false);
    expect(pasteEvents).to.equal(0);
  });

  it("uses the first image when pasted clipboard data has mixed items", async () => {
    const el = await fixture(html`<composer-inline-reply available></composer-inline-reply>`);
    const textarea = el.renderRoot.querySelector("textarea");
    const firstImage = new File(["first"], "first.png", { type: "image/png" });
    const secondImage = new File(["second"], "second.jpg", { type: "image/jpeg" });
    let pastedFile = null;
    el.addEventListener("attachment-paste-image", (event) => {
      pastedFile = event.detail.file;
    });
    const pasteEvent = new Event("paste", { bubbles: true, cancelable: true });
    Object.defineProperty(pasteEvent, "clipboardData", {
      value: {
        items: [
          {
            type: "text/plain",
            getAsFile: () => new File(["text"], "note.txt", { type: "text/plain" })
          },
          {
            type: "image/png",
            getAsFile: () => firstImage
          },
          {
            type: "image/jpeg",
            getAsFile: () => secondImage
          }
        ]
      }
    });

    textarea.dispatchEvent(pasteEvent);

    expect(pasteEvent.defaultPrevented).to.equal(false);
    expect(pastedFile).to.equal(firstImage);
  });

  it("ignores image clipboard items whose file extraction throws", async () => {
    const el = await fixture(html`<composer-inline-reply available></composer-inline-reply>`);
    const textarea = el.renderRoot.querySelector("textarea");
    let pasteEvents = 0;
    el.addEventListener("attachment-paste-image", () => {
      pasteEvents += 1;
    });
    const pasteEvent = new Event("paste", { bubbles: true, cancelable: true });
    Object.defineProperty(pasteEvent, "clipboardData", {
      value: {
        items: [
          {
            type: "image/png",
            getAsFile: () => {
              throw new Error("clipboard is unavailable");
            }
          }
        ]
      }
    });

    textarea.dispatchEvent(pasteEvent);

    expect(pasteEvent.defaultPrevented).to.equal(false);
    expect(pasteEvents).to.equal(0);
  });

  it("emits pasted image via clipboardData.files fallback when items has no image", async () => {
    const el = await fixture(html`<composer-inline-reply available></composer-inline-reply>`);
    const textarea = el.renderRoot.querySelector("textarea");
    const imageFile = new File(["png"], "mobile-paste.png", { type: "image/png" });
    let pastedFile = null;
    el.addEventListener("attachment-paste-image", (event) => {
      pastedFile = event.detail.file;
    });
    const pasteEvent = new Event("paste", { bubbles: true, cancelable: true });
    Object.defineProperty(pasteEvent, "clipboardData", {
      value: {
        items: [],
        files: [imageFile]
      }
    });

    textarea.dispatchEvent(pasteEvent);

    expect(pasteEvent.defaultPrevented).to.equal(true);
    expect(pastedFile).to.equal(imageFile);
  });

  it("ignores focus requests when the textarea has not rendered", async () => {
    const el = await fixture(html`<composer-inline-reply></composer-inline-reply>`);
    el.renderRoot.querySelector("textarea").remove();

    el.dispatchEvent(new Event("composer-focus-request"));

    expect(el.renderRoot.activeElement).to.equal(null);
  });

  it("focuses the reply textarea when the host receives a focus request", async () => {
    const el = await fixture(html`<composer-inline-reply available></composer-inline-reply>`);
    const textarea = el.renderRoot.querySelector("textarea");

    el.dispatchEvent(new Event("composer-focus-request"));

    expect(el.renderRoot.activeElement).to.equal(textarea);
  });

  it("does not focus the disabled reply textarea", async () => {
    const el = await fixture(html`<composer-inline-reply></composer-inline-reply>`);

    el.dispatchEvent(new Event("composer-focus-request"));

    expect(el.renderRoot.activeElement).to.equal(null);
  });

  it("removes the host focus-request listener when disconnected", async () => {
    const el = await fixture(html`<composer-inline-reply available></composer-inline-reply>`);
    let focusCalls = 0;
    el.focusComposer = () => {
      focusCalls += 1;
    };

    el.remove();
    el.dispatchEvent(new Event("composer-focus-request"));

    expect(focusCalls).to.equal(0);
  });

  it("re-attaches the host focus-request listener when reconnected", async () => {
    const el = await fixture(html`<composer-inline-reply available></composer-inline-reply>`);
    let focusCalls = 0;
    el.focusComposer = () => {
      focusCalls += 1;
    };

    el.remove();
    el.dispatchEvent(new Event("composer-focus-request"));
    document.body.appendChild(el);
    el.dispatchEvent(new Event("composer-focus-request"));

    expect(focusCalls).to.equal(1);
    el.remove();
  });
});
