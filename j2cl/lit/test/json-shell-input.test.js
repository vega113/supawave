import { expect } from "@open-wc/testing";
import { createJsonShellInput } from "../src/input/json-shell-input.js";

describe("json-shell-input", () => {
  beforeEach(() => {
    window.__bootstrap = undefined;
  });

  it("reads the bootstrap contract payload from window.__bootstrap", () => {
    window.__bootstrap = {
      session: {
        address: "a@b.c",
        role: "owner",
        domain: "b.c",
        id: "seed-1",
        features: []
      },
      socket: {
        address: "ws.example:443"
      }
    };
    const snap = createJsonShellInput(window).read();
    expect(snap.signedIn).to.equal(true);
    expect(snap.role).to.equal("owner");
    expect(snap.idSeed).to.equal("seed-1");
    expect(snap.websocketAddress).to.equal("ws.example:443");
  });
});
