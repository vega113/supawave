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

const resolveCodeRabbitCompletedAt = (checkRuns) => {
  const latestCodeRabbitRun = latestCodeRabbitCompletion(checkRuns);
  return latestCodeRabbitRun ? Date.parse(latestCodeRabbitRun.completed_at) : NaN;
};

module.exports = {
  codeRabbitNames,
  codexReactionLogins,
  isCodexReactionLogin,
  latestCodeRabbitCompletion,
  resolveCodeRabbitCompletedAt,
  reviewGracePeriodMs
};
