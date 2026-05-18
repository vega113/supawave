# Issue 1266 GPT Bot Attachment Transcription

- Worktree: `/Users/vega/devroot/worktrees/codex/telegram-bot-attachments-openai`
- Branch: `codex/telegram-bot-attachments-openai`
- Plan: `docs/superpowers/plans/2026-05-18-gptbot-attachment-transcription.md`
- Issue: https://github.com/vega113/supawave/issues/1266

## Verification

- `python3 scripts/assemble-changelog.py`
  - Result: `assembled 381 entries -> wave/config/changelog.json`
- `python3 scripts/validate-changelog.py --changelog wave/config/changelog.json`
  - Result: `changelog validation passed`
- `git diff --check`
  - Result: exit 0
- `sbt "wave/testOnly org.waveprotocol.examples.robots.gptbot.GptBotRobotTest org.waveprotocol.examples.robots.gptbot.SupaWaveApiClientTest org.waveprotocol.examples.robots.gptbot.OpenAiCodexClientTest"`
  - Result: `Passed: Total 34, Failed 0, Errors 0, Passed 34`

## Self-Review

- Attachment context is limited to the triggering blip, so the bot does not scan unrelated wave history for files.
- Text-like attachment content is bounded before entering the model prompt.
- Audio/video transcription uses the OpenAI audio transcription endpoint and skips files larger than 25 MB.
- Failed attachment export or transcription degrades to normal bot handling instead of blocking replies.
