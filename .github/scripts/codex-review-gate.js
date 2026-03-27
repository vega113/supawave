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
    .filter((run) => codeRabbitNames.has(run.name) && run.conclusion === "success" && run.completed_at)
    .sort((left, right) => Date.parse(right.completed_at) - Date.parse(left.completed_at))[0] ?? null;
};

const resolveCodeRabbitCompletedAt = (checkRuns, latestCommit) => {
  const latestCodeRabbitRun = latestCodeRabbitCompletion(checkRuns);
  if (latestCodeRabbitRun) {
    return Date.parse(latestCodeRabbitRun.completed_at);
  }

  const statusNodes = latestCommit?.statusCheckRollup?.contexts?.nodes ?? [];
  const graphQlCodeRabbitCompletedAt = statusNodes
    .flatMap((node) => {
      if (node.__typename !== "CheckRun") {
        return [];
      }

      if (!codeRabbitNames.has(node.name) || node.conclusion !== "SUCCESS" || !node.completedAt) {
        return [];
      }

      return [Date.parse(node.completedAt)];
    })
    .sort((left, right) => right - left)[0] ?? NaN;

  if (!Number.isNaN(graphQlCodeRabbitCompletedAt)) {
    return graphQlCodeRabbitCompletedAt;
  }

  return latestCommit ? Date.parse(latestCommit.committedDate) : NaN;
};

module.exports = {
  codeRabbitNames,
  codexReactionLogins,
  isCodexReactionLogin,
  latestCodeRabbitCompletion,
  resolveCodeRabbitCompletedAt,
  reviewGracePeriodMs
};
