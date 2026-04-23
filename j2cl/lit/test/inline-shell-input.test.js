import { expect } from "@open-wc/testing";
import { createInlineShellInput } from "../src/input/inline-shell-input.js";

describe("inline-shell-input", () => {
  beforeEach(() => {
    window.__session = undefined;
    window.__websocket_address = undefined;
  });

  it("returns a signed-out snapshot when no session is present", () => {
    const input = createInlineShellInput(window);
    const snap = input.read();
    expect(snap.signedIn).to.equal(false);
    expect(snap.address).to.equal("");
    expect(snap.role).to.equal("user");
    expect(snap.websocketAddress).to.equal("");
  });

  it("returns a signed-in snapshot with role normalization", () => {
    window.__session = {
      address: "a@b.c",
      role: "admin",
      domain: "b.c",
      idSeed: "s",
      features: []
    };
    window.__websocket_address = "ws.example:443";
    const input = createInlineShellInput(window);
    const snap = input.read();
    expect(snap.signedIn).to.equal(true);
    expect(snap.address).to.equal("a@b.c");
    expect(snap.role).to.equal("admin");
    expect(snap.websocketAddress).to.equal("ws.example:443");
  });

  it("returns signed-out when address is empty string", () => {
    window.__session = {
      address: "",
      role: "user",
      domain: "",
      idSeed: "",
      features: []
    };
    const input = createInlineShellInput(window);
    expect(input.read().signedIn).to.equal(false);
  });
});
