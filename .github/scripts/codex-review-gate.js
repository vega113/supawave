const reviewGracePeriodMs = 5 * 60 * 1000;
const codeRabbitNames = new Set(["CodeRabbit", "CodeRabbit - Review completed"]);
const codexReactionLogins = new Set([
  "chatgpt-codex-connector",
  "chatgpt-codex-connector[bot]",
  "openai-codex",
  "openai-codex[bot]"
]);

const isCodexReactionLogin = (login) => {
  const normalized = (login ?? "").toLowerCase();
  return codexReactionLogins.has(normalized);
};

const latestCodeRabbitCompletion = (checkRuns) => {
  return checkRuns
    .filter((run) => {
      return run != null &&
        typeof run === "object" &&
        codeRabbitNames.has(run.name) &&
        run.conclusion === "success" &&
        run.completed_at;
    })
    .sort((left, right) => Date.parse(right.completed_at) - Date.parse(left.completed_at))[0] ?? null;
};

const latestCodeRabbitStatusCompletion = (statusNodes) => {
  return statusNodes
    .filter((node) => {
      return node != null && typeof node === "object";
    })
    .flatMap((node) => {
      if (node.__typename === "CheckRun" &&
        codeRabbitNames.has(node.name) &&
        node.conclusion === "SUCCESS" &&
        node.completedAt) {
        return [Date.parse(node.completedAt)];
      }

      if (node.__typename === "StatusContext" &&
        node.context === "CodeRabbit" &&
        node.state === "SUCCESS" &&
        node.createdAt) {
        return [Date.parse(node.createdAt)];
      }

      return [];
    })
    .filter((completedAt) => Number.isFinite(completedAt))
    .sort((left, right) => right - left)[0] ?? NaN;
};

const resolveCodeRabbitCompletedAt = (checkRuns, statusNodes = []) => {
  const latestCodeRabbitRun = latestCodeRabbitCompletion(checkRuns);
  const latestCodeRabbitCompletedAt = latestCodeRabbitRun
    ? Date.parse(latestCodeRabbitRun.completed_at)
    : NaN;

  if (Number.isFinite(latestCodeRabbitCompletedAt)) {
    return latestCodeRabbitCompletedAt;
  }

  return latestCodeRabbitStatusCompletion(statusNodes);
};

const publishCodexReviewGateHeadStatus = async (github, options) => {
  return github.rest.repos.createCommitStatus({
    owner: options.owner,
    repo: options.repo,
    sha: options.sha,
    state: options.state,
    context: "Codex Review Gate",
    description: options.description
  });
};

module.exports = {
  codeRabbitNames,
  codexReactionLogins,
  isCodexReactionLogin,
  latestCodeRabbitCompletion,
  publishCodexReviewGateHeadStatus,
  resolveCodeRabbitCompletedAt,
  reviewGracePeriodMs
};
