const assert = require("node:assert/strict");
const test = require("node:test");

const {
  evaluateCodexReviewGate,
  publishCodexReviewGateHeadStatus,
  shouldRequeueCodexReviewGate,
} = require("./codex-review-gate");

const BASE_TIME = Date.parse("2026-03-28T13:00:00Z");
const AFTER_WINDOW = Date.parse("2026-03-28T13:11:00Z");
const WITHIN_WINDOW = Date.parse("2026-03-28T13:07:00Z");
const EXACT_BOUNDARY = BASE_TIME + 10 * 60 * 1000;

function buildPullRequest(overrides = {}) {
  return {
    isDraft: false,
    baseRefName: "main",
    headRefOid: "head-oid",
    labels: { nodes: [] },
    commits: {
      nodes: [
        {
          commit: {
            oid: "head-oid",
            committedDate: new Date(BASE_TIME).toISOString(),
            statusCheckRollup: { contexts: { nodes: [] } },
          },
        },
      ],
    },
    reviewThreads: { nodes: [] },
    ...overrides,
  };
}

function withGateSuccess() {
  return {
    commits: {
      nodes: [
        {
          commit: {
            oid: "head-oid",
            committedDate: new Date(BASE_TIME).toISOString(),
            statusCheckRollup: {
              contexts: {
                nodes: [
                  {
                    __typename: "StatusContext",
                    context: "Codex Review Gate",
                    state: "SUCCESS",
                  },
                ],
              },
            },
          },
        },
      ],
    },
  };
}

// --- evaluateCodexReviewGate ---

test("fails for draft PRs", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: AFTER_WINDOW,
    pullRequest: buildPullRequest({ isDraft: true }),
  });
  assert.equal(result.ok, false);
  assert.match(result.message, /Draft/);
});

test("fails when there are unresolved review threads", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: AFTER_WINDOW,
    pullRequest: buildPullRequest({
      reviewThreads: { nodes: [{ isResolved: false }] },
    }),
  });
  assert.equal(result.ok, false);
  assert.match(result.message, /unresolved review thread/);
});

test("fails when inside the 10-minute window", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: WITHIN_WINDOW,
    pullRequest: buildPullRequest(),
  });
  assert.equal(result.ok, false);
  assert.match(result.message, /10-minute review window/);
});

test("reports remaining minutes when inside window", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: WITHIN_WINDOW,
    pullRequest: buildPullRequest(),
  });
  assert.equal(result.ok, false);
  assert.match(result.message, /\d+ minute/);
});

test("passes at exactly the 10-minute boundary", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: EXACT_BOUNDARY,
    pullRequest: buildPullRequest(),
  });
  assert.equal(result.ok, true);
  assert.match(result.message, /10-minute window elapsed/);
});

test("passes after the 10-minute window with no unresolved threads", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: AFTER_WINDOW,
    pullRequest: buildPullRequest(),
  });
  assert.equal(result.ok, true);
  assert.match(result.message, /10-minute window elapsed/);
});

test("passes for stacked PRs after the window (no special requirement)", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: AFTER_WINDOW,
    pullRequest: buildPullRequest({ baseRefName: "fix/some-feature" }),
  });
  assert.equal(result.ok, true);
});

test("passes when all threads are resolved", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: AFTER_WINDOW,
    pullRequest: buildPullRequest({
      reviewThreads: { nodes: [{ isResolved: true }, { isResolved: true }] },
    }),
  });
  assert.equal(result.ok, true);
});

test("fails with unresolved threads even after window", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: AFTER_WINDOW,
    pullRequest: buildPullRequest({
      reviewThreads: {
        nodes: [{ isResolved: true }, { isResolved: false }],
      },
    }),
  });
  assert.equal(result.ok, false);
  assert.match(result.message, /unresolved review thread/);
});

// --- shouldRequeueCodexReviewGate ---

test("shouldRequeue: true after window, no unresolved threads, not yet passed", () => {
  assert.equal(
    shouldRequeueCodexReviewGate({
      defaultBranchName: "main",
      nowMs: AFTER_WINDOW,
      pullRequest: buildPullRequest(),
    }),
    true,
  );
});

test("shouldRequeue: false when still inside the 10-minute window", () => {
  assert.equal(
    shouldRequeueCodexReviewGate({
      defaultBranchName: "main",
      nowMs: WITHIN_WINDOW,
      pullRequest: buildPullRequest(),
    }),
    false,
  );
});

test("shouldRequeue: false when gate already passed", () => {
  assert.equal(
    shouldRequeueCodexReviewGate({
      defaultBranchName: "main",
      nowMs: AFTER_WINDOW,
      pullRequest: buildPullRequest(withGateSuccess()),
    }),
    false,
  );
});

test("shouldRequeue: false when there are unresolved threads", () => {
  assert.equal(
    shouldRequeueCodexReviewGate({
      defaultBranchName: "main",
      nowMs: AFTER_WINDOW,
      pullRequest: buildPullRequest({
        reviewThreads: { nodes: [{ isResolved: false }] },
      }),
    }),
    false,
  );
});

test("shouldRequeue: false for draft PRs", () => {
  assert.equal(
    shouldRequeueCodexReviewGate({
      defaultBranchName: "main",
      nowMs: AFTER_WINDOW,
      pullRequest: buildPullRequest({ isDraft: true }),
    }),
    false,
  );
});

// --- publishCodexReviewGateHeadStatus ---

test("publishes success status on the PR head", async () => {
  const calls = [];
  const github = {
    rest: {
      repos: {
        createCommitStatus: async (payload) => {
          calls.push(payload);
          return payload;
        },
      },
    },
  };

  await publishCodexReviewGateHeadStatus(github, {
    description: "Review gate passed: 10-minute window elapsed and no unresolved threads",
    owner: "vega113",
    repo: "supawave",
    sha: "head-oid",
  });

  assert.deepEqual(calls, [
    {
      context: "Codex Review Gate",
      description: "Review gate passed: 10-minute window elapsed and no unresolved threads",
      owner: "vega113",
      repo: "supawave",
      sha: "head-oid",
      state: "success",
    },
  ]);
});

test("publishes failure status on the PR head", async () => {
  const calls = [];
  const github = {
    rest: {
      repos: {
        createCommitStatus: async (payload) => {
          calls.push(payload);
          return payload;
        },
      },
    },
  };

  await publishCodexReviewGateHeadStatus(github, {
    description: "Pull request has 1 unresolved review thread(s)",
    owner: "vega113",
    repo: "supawave",
    sha: "head-oid",
    state: "failure",
  });

  assert.deepEqual(calls, [
    {
      context: "Codex Review Gate",
      description: "Pull request has 1 unresolved review thread(s)",
      owner: "vega113",
      repo: "supawave",
      sha: "head-oid",
      state: "failure",
    },
  ]);
});
