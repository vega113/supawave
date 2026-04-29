# J2CL ↔ GWT Parity E2E Harness

Playwright harness for the G-PORT roadmap (umbrella
[#1109](https://github.com/vega113/supawave/issues/1109), parent
[#904](https://github.com/vega113/supawave/issues/904)).

Every G-PORT slice from G-PORT-2 onwards extends this harness with a
parity test asserting equivalent user-visible behaviour on
`?view=j2cl-root` and `?view=gwt`.

G-PORT-1 itself ships only the harness skeleton plus a smoke test that
proves both views bootstrap.

## Layout

```
wave/src/e2e/j2cl-gwt-parity/
├── package.json              # @playwright/test + typescript
├── playwright.config.ts      # baseURL from $WAVE_E2E_BASE_URL
├── tsconfig.json
├── fixtures/
│   └── testUser.ts           # freshCredentials() + registerAndSignIn()
├── pages/
│   ├── WavePage.ts           # shared base (goto, viewQuery hook)
│   ├── J2clPage.ts           # ?view=j2cl-root, asserts <shell-root>
│   └── GwtPage.ts            # ?view=gwt,        asserts GWT bootstrap
└── tests/
    └── smoke.spec.ts         # G-PORT-1 acceptance gate
```

## Running locally

From the repo root:

```bash
# 1. Stage and start a server in this worktree.
bash scripts/worktree-boot.sh --port 9900
# (Run the printed wave-smoke.sh start command.)

# 2. Install harness deps.
cd wave/src/e2e/j2cl-gwt-parity
npm install
npx playwright install chromium

# 3. Run the smoke test.
WAVE_E2E_BASE_URL=http://127.0.0.1:9900 npx playwright test

# 4. Stop the server.
PORT=9900 bash ../../../../scripts/wave-smoke.sh stop
```

## Running in CI

The workflow `.github/workflows/j2cl-gwt-parity-e2e.yml` exposes a check
named `J2CL ↔ GWT Parity E2E` that stages the distribution, starts the
server on port 9898, runs `npx playwright test`, and uploads the HTML
report on failure.

## Authoring guidelines for later slices

- Add a new `*.spec.ts` per slice; do not bloat `smoke.spec.ts`.
- Drive both views in the same test where parity is the contract; assert
  equivalent observable behaviour, not identical DOM.
- Extend `J2clPage` / `GwtPage` with the helpers your slice needs
  (`findWave`, `openWave`, `clickReply`, `typeAndSend`, `mentionsList`,
  ...). Keep the API symmetric so a single test body can run against
  either page object.
- Never skip an assertion to make a flaky test pass — if the UI is
  genuinely broken, file a blocker on the slice issue.
