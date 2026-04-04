const REVIEW_WINDOW_MS = 5 * 60 * 1000;
const PASSED_REVIEW_GATE_DESCRIPTION =
  "Review gate passed: 5-minute window elapsed and no unresolved threads";

function evaluateCodexReviewGate({
  pullRequest,
  nowMs = Date.now(),
}) {
  return pullRequest
    ? evaluatePullRequestGate({ pullRequest, nowMs })
    : failure("Pull request not found");
}

function evaluatePullRequestGate({ pullRequest, nowMs }) {
  if (pullRequest.isDraft) {
    return failure("Draft PRs cannot pass the review gate");
  }

  const unresolvedThreads = getReviewThreadNodes(pullRequest).filter(
    (thread) => !thread.isResolved,
  );
  if (unresolvedThreads.length > 0) {
    return failure(
      `Pull request has ${unresolvedThreads.length} unresolved review thread(s)`,
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
      `Waiting for 5-minute review window (${remainingMinutes} minute(s) remaining)`,
    );
  }

  return success(
    PASSED_REVIEW_GATE_DESCRIPTION,
  );
}

function shouldRequeueCodexReviewGate({
  pullRequest,
  nowMs = Date.now(),
}) {
  if (pullRequest.isDraft) return false;

  const unresolvedThreads = getReviewThreadNodes(pullRequest).filter(
    (thread) => !thread.isResolved,
  );
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

async function requeueEligibleCodexReviewGate(github, {
  owner,
  nowMs = Date.now(),
  pullRequest,
  repo,
}) {
  if (!shouldRequeueCodexReviewGate({ pullRequest, nowMs })) {
    return false;
  }

  if (!pullRequest.headRefOid) {
    return false;
  }

  await publishCodexReviewGateHeadStatus(github, {
    description: PASSED_REVIEW_GATE_DESCRIPTION,
    owner,
    repo,
    sha: pullRequest.headRefOid,
    state: "success",
  });

  return true;
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
  requeueEligibleCodexReviewGate,
  shouldRequeueCodexReviewGate,
};
