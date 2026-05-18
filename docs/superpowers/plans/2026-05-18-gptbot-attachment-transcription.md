# GptBot Attachment Transcription Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the SupaWave GPT bot use attached files as prompt context, including OpenAI transcription for supported audio and video attachments.

**Architecture:** Keep attachment handling in the existing `org.waveprotocol.examples.robots.gptbot` robot lane. `GptBotRobot` extracts attachment/image elements from the triggering blip, `SupaWaveApiClient` exports attachment bytes through `robot.exportAttachment`, and `GptBotReplyPlanner` appends bounded attachment context to the user prompt. `OpenAiCodexClient` transcribes supported audio/video files through `/audio/transcriptions`; small textual attachments are converted to markdown-like fenced context, while unsupported binaries are represented by metadata only.

**Tech Stack:** Java 11 HTTP client, existing Wave Robot JSON-RPC/Data API, OpenAI Chat Completions, OpenAI Audio Transcriptions, JUnit 3 style SBT tests.

---

### Task 1: Add Attachment Data Contracts

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/CodexClient.java`
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveClient.java`
- Create: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/BotAttachmentContext.java`

- [ ] Add a `CodexClient.transcribeAttachment(String fileName, String mimeType, byte[] data)` default method returning `Optional.empty()`.
- [ ] Add a `SupaWaveClient.exportAttachment(String attachmentId, String rpcServerUrl)` method returning `Optional<BotAttachmentContext.RawAttachment>`.
- [ ] Define immutable `BotAttachmentContext` and nested `RawAttachment` value types with bounded text rendering helpers.

### Task 2: Test Attachment Context Flow

**Files:**
- Modify: `wave/src/test/java/org/waveprotocol/examples/robots/gptbot/GptBotRobotTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClientTest.java`
- Modify: `wave/src/test/java/org/waveprotocol/examples/robots/gptbot/OpenAiCodexClientTest.java`

- [ ] Add a failing robot test where a blip contains an attachment element with `mimeType=audio/mpeg`; assert the bot exports the attachment, calls transcription, and includes the transcript in the prompt sent to the model.
- [ ] Add a failing robot test where a blip contains a text attachment; assert exported bytes are included as fenced attachment context without transcription.
- [ ] Add a failing SupaWave API client test that verifies `robot.exportAttachment` is posted to the supplied trusted RPC endpoint and parsed from `attachmentData`.
- [ ] Add a failing OpenAI client test that verifies transcription uses multipart form upload against `/audio/transcriptions` with the configured transcription model.

### Task 3: Implement Export And Transcription

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClient.java`
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/OpenAiCodexClient.java`

- [ ] Implement `SupaWaveApiClient.exportAttachment` with `robot.exportAttachment`, trusted endpoint resolution, token selection, error handling, and decoded `attachmentData` parsing.
- [ ] Implement `OpenAiCodexClient.transcribeAttachment` with `multipart/form-data`, `model`, `response_format=json`, file part metadata, OpenAI error logging, and JSON `text` parsing.
- [ ] Use `GPTBOT_OPENAI_TRANSCRIPTION_MODEL`, defaulting to `gpt-4o-mini-transcribe`.

### Task 4: Wire Attachment Context Into Replies

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotRobot.java`
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotReplyPlanner.java`
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveClient.java`

- [ ] Extract attachment IDs, captions, mime types, and display names from `ATTACHMENT` and attachment-backed `IMAGE` elements on the triggering blip.
- [ ] Export each referenced attachment through `SupaWaveClient`.
- [ ] For supported audio/video attachments, request `CodexClient.transcribeAttachment` and attach the transcript to the prompt context.
- [ ] For text/markdown/json/csv attachments, decode UTF-8 text and include a bounded markdown fenced block.
- [ ] For unsupported or oversized attachments, include metadata and a clear skipped reason.

### Task 5: Verification And PR

**Files:**
- Create: `wave/config/changelog.d/2026-05-18-gptbot-attachment-transcription.json`
- Create: `journal/local-verification/2026-05-18-gptbot-attachment-transcription.md`

- [ ] Add a changelog fragment for the user-visible bot behavior change.
- [ ] Run focused tests: `sbt "wave/testOnly org.waveprotocol.examples.robots.gptbot.GptBotRobotTest org.waveprotocol.examples.robots.gptbot.SupaWaveApiClientTest org.waveprotocol.examples.robots.gptbot.OpenAiCodexClientTest"`.
- [ ] Run `git diff --check`.
- [ ] Record worktree, branch, plan path, commit SHA, and verification in the GitHub issue and journal.
- [ ] Commit, push, open a PR, monitor checks/review threads, address feedback, and merge when gates allow.

### Self-Review

- Spec coverage: attachments are exported, text files are converted to markdown-like context, audio/video use OpenAI transcription, and attachment context reaches reply generation.
- Scope control: no Telegram-specific server is invented because this repo surface is the existing GPT bot; the implementation extends the robot path already used for OpenAI.
- Risk checks: bounded attachment text avoids unbounded prompts; unsupported binaries are metadata-only; OpenAI transcription failure does not prevent normal text prompt replies.
