const assert = require("node:assert/strict");
const test = require("node:test");

const {
  evaluateCodexReviewGate,
  publishCodexReviewGateHeadStatus,
  shouldRequeueCodexReviewGate,
} = require("./codex-review-gate");

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
            committedDate: "2026-03-28T13:00:00Z",
            statusCheckRollup: { contexts: { nodes: [] } },
          },
        },
      ],
    },
    reviews: { nodes: [] },
    comments: { nodes: [] },
    reviewThreads: { nodes: [] },
    ...overrides,
  };
}

function codeRabbitStatus() {
  return [
    {
      __typename: "StatusContext",
      context: "CodeRabbit",
      state: "SUCCESS",
    },
  ];
}

test("fails when review threads are unresolved", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    pullRequest: buildPullRequest({
      reviewThreads: { nodes: [{ isResolved: false }] },
    }),
  });

  assert.equal(result.ok, false);
  assert.match(result.message, /unresolved review thread/);
});

test("rejects stacked PRs with stale Codex labels", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    pullRequest: buildPullRequest({
      baseRefName: "fix/tag-filter-regression",
      labels: { nodes: [{ name: "codex-reviewed" }] },
    }),
  });

  assert.equal(result.ok, false);
  assert.match(result.message, /explicit Codex coverage/);
});

test("passes main PRs with Codex label coverage", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    pullRequest: buildPullRequest({
      labels: { nodes: [{ name: "codex-reviewed" }] },
    }),
  });

  assert.equal(result.ok, true);
  assert.match(result.message, /Codex coverage/);
});

test("rejects stacked PRs when only CodeRabbit is green", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    pullRequest: buildPullRequest({
      baseRefName: "fix/tag-filter-regression",
      commits: {
        nodes: [
          {
            commit: {
              oid: "head-oid",
              committedDate: "2026-03-28T13:00:00Z",
              statusCheckRollup: { contexts: { nodes: codeRabbitStatus() } },
            },
          },
        ],
      },
    }),
  });

  assert.equal(result.ok, false);
  assert.match(result.message, /explicit Codex coverage/);
});

test("passes stacked PRs when Codex comments on the current head commit", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    pullRequest: buildPullRequest({
      baseRefName: "fix/tag-filter-regression",
      reviews: {
        nodes: [
          {
            author: { login: "chatgpt-codex-connector[bot]" },
            commit: { oid: "head-oid" },
            state: "APPROVED",
          },
        ],
      },
    }),
  });

  assert.equal(result.ok, true);
  assert.match(result.message, /Codex coverage/);
});

test("rejects main PRs when CodeRabbit skipped review", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    pullRequest: buildPullRequest({
      comments: {
        nodes: [
          {
            author: { login: "coderabbitai[bot]" },
            body: "Review skipped because the base branch is not the default branch",
          },
        ],
      },
      commits: {
        nodes: [
          {
            commit: {
              oid: "head-oid",
              committedDate: "2026-03-28T13:00:00Z",
              statusCheckRollup: { contexts: { nodes: codeRabbitStatus() } },
            },
          },
        ],
      },
    }),
  });

  assert.equal(result.ok, false);
  assert.match(result.message, /skipped review/);
});

test("passes main PRs after the CodeRabbit grace period", () => {
  const result = evaluateCodexReviewGate({
    defaultBranchName: "main",
    nowMs: Date.parse("2026-03-28T13:06:00Z"),
    pullRequest: buildPullRequest({
      commits: {
        nodes: [
          {
            commit: {
              oid: "head-oid",
              committedDate: "2026-03-28T13:00:00Z",
              statusCheckRollup: { contexts: { nodes: codeRabbitStatus() } },
            },
          },
        ],
      },
    }),
  });

  assert.equal(result.ok, true);
  assert.match(result.message, /5 minute Codex-review window/);
});

test("requeues a CodeRabbit-approved PR after the grace window", () => {
  const shouldRequeue = shouldRequeueCodexReviewGate({
    defaultBranchName: "main",
    nowMs: Date.parse("2026-03-28T13:06:00Z"),
    pullRequest: buildPullRequest({
      commits: {
        nodes: [
          {
            commit: {
              oid: "head-oid",
              committedDate: "2026-03-28T13:00:00Z",
              statusCheckRollup: { contexts: { nodes: codeRabbitStatus() } },
            },
          },
        ],
      },
    }),
  });

  assert.equal(shouldRequeue, true);
});

test("publishes Codex Review Gate success on the PR head", async () => {
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
    description: "Review gate passed after the 5 minute Codex-review window using CodeRabbit",
    owner: "vega113",
    repo: "incubator-wave",
    sha: "head-oid",
  });

  assert.deepEqual(calls, [
    {
      context: "Codex Review Gate",
      description: "Review gate passed after the 5 minute Codex-review window using CodeRabbit",
      owner: "vega113",
      repo: "incubator-wave",
      sha: "head-oid",
      state: "success",
    },
  ]);
});
