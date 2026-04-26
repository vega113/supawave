const REVIEW_WINDOW_MS = 10 * 60 * 1000;
const REVIEW_WINDOW_MINUTES = REVIEW_WINDOW_MS / 60000;

// Bot reviewers whose unresolved threads are skipped when Codex quota is
// exhausted.  These bots are not human reviewers and their threads are resolved
// as part of normal PR maintenance.
const BOT_REVIEWER_LOGINS = new Set([
  "coderabbitai",
  "coderabbitai[bot]",
  "copilot-pull-request-reviewer",
  "copilot-pull-request-reviewer[bot]",
  "github-advanced-security",
  "codex",
  "chatgpt-codex-connector",
  "chatgpt-codex-connector[bot]",
]);

function evaluateCodexReviewGate({
  pullRequest,
  nowMs = Date.now(),
  skipCodexCheck = false,
}) {
  return pullRequest
    ? evaluatePullRequestGate({ pullRequest, nowMs, skipCodexCheck })
    : failure("Pull request not found");
}

function evaluatePullRequestGate({ pullRequest, nowMs, skipCodexCheck = false }) {
  if (pullRequest.isDraft) {
    return failure("Draft PRs cannot pass the review gate");
  }

  const allUnresolved = getReviewThreadNodes(pullRequest).filter(
    (thread) => !thread.isResolved,
  );

  // When Codex quota is exhausted, only block on threads from human reviewers.
  // Bot-generated threads (CodeRabbit, Copilot, etc.) are skipped.
  const unresolvedThreads = skipCodexCheck
    ? allUnresolved.filter((thread) => {
        const login = thread.comments?.nodes?.[0]?.author?.login ?? "";
        return !BOT_REVIEWER_LOGINS.has(login);
      })
    : allUnresolved;

  if (unresolvedThreads.length > 0) {
    const skippedCount = allUnresolved.length - unresolvedThreads.length;
    const skippedNote = skippedCount > 0 ? ` (${skippedCount} bot thread(s) skipped — Codex quota exhausted)` : "";
    return failure(
      `Pull request has ${unresolvedThreads.length} unresolved review thread(s)${skippedNote}`,
    );
  }

  const latestCommit = getLatestCommit(pullRequest);
  const latestCommitAt = Date.parse(latestCommit?.committedDate ?? "");
  if (!Number.isFinite(latestCommitAt)) {
    return failure("Unable to determine last commit time");
  }
  const commitAgeMs = nowMs - latestCommitAt;

  if (commitAgeMs < REVIEW_WINDOW_MS) {
    const remainingMinutes = Math.ceil(
      (REVIEW_WINDOW_MS - commitAgeMs) / 60000,
    );
    return failure(
      `Waiting for ${REVIEW_WINDOW_MINUTES}-minute review window (${remainingMinutes} minute(s) remaining)`,
    );
  }

  const quotaNote = skipCodexCheck ? " (Codex quota exhausted — bot threads skipped)" : "";
  return success(
    `Review gate passed: ${REVIEW_WINDOW_MINUTES}-minute window elapsed and no unresolved human threads${quotaNote}`,
  );
}

function shouldRequeueCodexReviewGate({
  pullRequest,
  nowMs = Date.now(),
  skipCodexCheck = false,
}) {
  if (pullRequest.isDraft) return false;

  const allUnresolved = getReviewThreadNodes(pullRequest).filter(
    (thread) => !thread.isResolved,
  );

  const unresolvedThreads = skipCodexCheck
    ? allUnresolved.filter((thread) => {
        const login = thread.comments?.nodes?.[0]?.author?.login ?? "";
        return !BOT_REVIEWER_LOGINS.has(login);
      })
    : allUnresolved;

  if (unresolvedThreads.length > 0) return false;

  const latestCommit = getLatestCommit(pullRequest);
  const latestCommitAt = Date.parse(latestCommit?.committedDate ?? "");
  if (!Number.isFinite(latestCommitAt)) return false;
  const commitAgeMs = nowMs - latestCommitAt;

  const statusNodes = getStatusNodes(latestCommit);
  const alreadyPassed = hasSuccessfulCodexReviewGateStatus(statusNodes);

  return commitAgeMs >= REVIEW_WINDOW_MS && !alreadyPassed;
}

async function publishCodexReviewGateHeadStatus(github, {
  owner,
  repo,
  sha,
  state = "success",
  description,
}) {
  return github.rest.repos.createCommitStatus({
    owner,
    repo,
    sha,
    state,
    context: "Codex Review Gate",
    description,
  });
}

function getLatestCommit(pullRequest) {
  return pullRequest.commits?.nodes?.[0]?.commit ?? null;
}

function getStatusNodes(latestCommit) {
  return latestCommit?.statusCheckRollup?.contexts?.nodes ?? [];
}

function getReviewThreadNodes(pullRequest) {
  return pullRequest.reviewThreads?.nodes ?? [];
}

function hasSuccessfulCodexReviewGateStatus(statusNodes) {
  return statusNodes.some((node) => {
    return (
      (node.__typename === "StatusContext" &&
        node.context === "Codex Review Gate" &&
        node.state === "SUCCESS") ||
      (node.__typename === "CheckRun" &&
        node.name === "Codex Review Gate" &&
        node.conclusion === "SUCCESS")
    );
  });
}

function failure(message) {
  return { ok: false, message };
}

function success(message) {
  return { ok: true, message };
}

module.exports = {
  evaluateCodexReviewGate,
  evaluatePullRequestGate,
  hasSuccessfulCodexReviewGateStatus,
  publishCodexReviewGateHeadStatus,
  shouldRequeueCodexReviewGate,
};
