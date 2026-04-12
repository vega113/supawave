# Robot Streaming Into Waves Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Support progressive robot replies into dedicated wave blips by documenting and proving a JSON-RPC streaming-write pattern built on existing robot operations.

**Architecture:** Keep the passive callback transport unchanged. Add a robot-client-side streaming path that creates one reply blip, stores its `newBlipId`, and serially applies throttled `document.modify` full-text replacements against that robot-owned blip. Use the passive bundle’s `rpcServerUrl` for follow-on writes and keep a default one-shot fallback for non-streaming LLM backends.

**Tech Stack:** Java, Wave robot API classes, Jakarta `/api-docs` servlet, JUnit/TestCase tests, changelog fragment workflow.

---

## Task 1: Add Streaming Capability To The gpt-bot LLM Client Layer

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/CodexClient.java`
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/OpenAiCodexClient.java`
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotReplyPlanner.java`
- Modify: `wave/src/test/java/org/waveprotocol/examples/robots/gptbot/GptBotRobotTest.java`
- Create: `wave/src/test/java/org/waveprotocol/examples/robots/gptbot/OpenAiCodexClientTest.java`

- [ ] **Step 1: Write the failing test shape into the existing robot test double usage**

Add a new `GptBotRobotTest` scenario that expects multiple streamed updates instead of a single final append when reply mode is streaming-active. The test should assert that the planner/client path can emit intermediate text snapshots like:

```java
apiClient.streamUpdates == Arrays.asList("Hel", "Hello", "Hello world");
```

- [ ] **Step 2: Run the focused test target to verify the new expectation fails**

Run:

```bash
sbt "testOnly org.waveprotocol.examples.robots.gptbot.OpenAiCodexClientTest"
```

Expected: FAIL because `CodexClient`, `GptBotReplyPlanner`, and the recording API client only support one-shot completion.

- [ ] **Step 3: Extend `CodexClient` with a streaming callback API and a default one-shot fallback**

Update `CodexClient` to add a small listener type plus a default streaming method that preserves current backends:

```java
public interface CodexClient {
  interface StreamingListener {
    void onText(String accumulatedText);
  }

  String complete(String prompt);

  default String completeMessagesStreaming(
      List<Map<String, String>> messages, StreamingListener listener) {
    String response = completeMessages(messages);
    if (listener != null && response != null && !response.isEmpty()) {
      listener.onText(response);
    }
    return response;
  }
}
```

Update `GptBotReplyPlanner` to add a sibling method that builds the same message list, calls `completeMessagesStreaming(...)`, and only commits the final assistant text to history once.

- [ ] **Step 4: Implement real incremental streaming in `OpenAiCodexClient`**

Add an override that uses OpenAI’s streaming HTTP response mode, accumulates text deltas, and invokes the listener with progressively larger text snapshots:

```java
@Override
public String completeMessagesStreaming(
    List<Map<String, String>> messages, StreamingListener listener) {
  StringBuilder accumulated = new StringBuilder();
  // POST with streaming enabled
  // parse SSE lines
  // on each text delta:
  accumulated.append(delta);
  if (listener != null) {
    listener.onText(accumulated.toString());
  }
  return accumulated.toString().trim();
}
```

Keep existing `completeMessages(...)` behavior intact for non-streaming callers and keep the default fallback behavior for `ProcessCodexClient` and `EchoCodexClient`.
If the current OpenAI path enters the existing one-shot tool-calling/web-search flow, explicitly fall back to that one-shot path instead of mixing streaming with a different tool semantics.

- [ ] **Step 5: Rerun the focused test**

Run:

```bash
sbt "testOnly org.waveprotocol.examples.robots.gptbot.GptBotRobotTest"
```

Expected: the new streaming-oriented assertions now pass, while legacy passive/active tests remain green.

- [ ] **Step 6: Commit**

```bash
git add \
  wave/src/main/java/org/waveprotocol/examples/robots/gptbot/CodexClient.java \
  wave/src/main/java/org/waveprotocol/examples/robots/gptbot/OpenAiCodexClient.java \
  wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotReplyPlanner.java \
  wave/src/test/java/org/waveprotocol/examples/robots/gptbot/GptBotRobotTest.java \
  wave/src/test/java/org/waveprotocol/examples/robots/gptbot/OpenAiCodexClientTest.java
git commit -m "feat(gptbot): add streaming LLM reply callbacks"
```

## Task 2: Add A Serialized Streaming Reply Writer On Top Of Robot RPC

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveClient.java`
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClient.java`
- Create: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/StreamingReplyWriter.java`
- Modify: `wave/src/test/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClientTest.java`

- [ ] **Step 1: Write the failing test for placeholder creation plus repeated replace updates**

Expand `GptBotRobotTest` with a streaming-active scenario that expects:

```java
assertEquals(1, apiClient.createReplyCalls);
assertEquals(Arrays.asList("Hel", "Hello", "Hello world"), apiClient.streamUpdates);
assertEquals("b+streamed", apiClient.lastCreatedReplyId);
```

Also add `SupaWaveApiClientTest` assertions for:

```java
createReply(..., "https://wave.example.com/robot/rpc") -> uses robot token
createReply(..., "https://wave.example.com/robot/dataapi/rpc") -> uses data token
createReply(...) parses result.newBlipId
```

- [ ] **Step 2: Run the focused test to capture the missing API surface**

Run:

```bash
sbt "testOnly org.waveprotocol.examples.robots.gptbot.GptBotRobotTest"
```

Expected: FAIL because `SupaWaveClient` only exposes `appendReply(...)` and there is no streaming reply session or update method.

- [ ] **Step 3: Extend `SupaWaveClient` with explicit create/update operations**

Change the interface to expose the reusable robot mutation pattern:

```java
Optional<String> createReply(String waveId, String waveletId, String parentBlipId,
    String initialContent, String rpcServerUrl);

boolean replaceReply(String waveId, String waveletId, String replyBlipId,
    String content, String rpcServerUrl);
```

Keep `appendReply(...)` as a convenience wrapper if it is still useful for the non-streaming active path.

- [ ] **Step 4: Implement `StreamingReplyWriter` and wire it through `SupaWaveApiClient`**

Add a focused helper that:

- creates one placeholder reply blip
- stores the returned `newBlipId`
- throttles update flushes
- sends full accumulated text via `document.modify` `REPLACE`
- guarantees one in-flight RPC at a time per reply blip

Skeleton:

```java
final class StreamingReplyWriter {
  private final SupaWaveClient apiClient;
  private final String waveId;
  private final String waveletId;
  private final String parentBlipId;
  private final String rpcServerUrl;
  private String replyBlipId;
  private String lastSent = "";

  boolean start() { ... }
  boolean update(String accumulatedText) { ... }
  boolean finish(String finalText) { ... }
}
```

In `SupaWaveApiClient`, parse `newBlipId` from the `blip.createChild` response result and add a raw `document.modify` request builder that replaces the full reply content. Choose the bearer token based on the actual `rpcServerUrl` instead of hardcoding a single endpoint.

- [ ] **Step 5: Rerun the focused test target**

Run:

```bash
sbt "testOnly org.waveprotocol.examples.robots.gptbot.GptBotRobotTest org.waveprotocol.examples.robots.gptbot.SupaWaveApiClientTest"
```

Expected: the streaming-active scenario now proves placeholder creation plus repeated replace updates, and existing one-shot active behavior still passes.

- [ ] **Step 6: Commit**

```bash
git add \
  wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveClient.java \
  wave/src/main/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClient.java \
  wave/src/main/java/org/waveprotocol/examples/robots/gptbot/StreamingReplyWriter.java \
  wave/src/test/java/org/waveprotocol/examples/robots/gptbot/GptBotRobotTest.java \
  wave/src/test/java/org/waveprotocol/examples/robots/gptbot/SupaWaveApiClientTest.java
git commit -m "feat(robot-api): stream replies through createChild and document.modify"
```

## Task 3: Wire The Example Robot To Use The Streaming Path Safely

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotConfig.java`
- Modify: `wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotRobot.java`
- Modify: `wave/src/test/java/org/waveprotocol/examples/robots/gptbot/GptBotRobotTest.java`

- [ ] **Step 1: Write the failing behavior expectation for the new reply mode**

Add/adjust tests so the robot distinguishes:

- `PASSIVE`: callback returns direct operations
- `ACTIVE`: one-shot createChild append
- `ACTIVE_STREAM`: placeholder + repeated replace updates

Expected assertions:

```java
assertFalse(response.contains("wavelet.appendBlip"));
assertEquals(1, apiClient.createReplyCalls);
assertTrue(apiClient.streamUpdates.size() >= 2);
```

- [ ] **Step 2: Run the focused robot test**

Run:

```bash
sbt "testOnly org.waveprotocol.examples.robots.gptbot.GptBotRobotTest"
```

Expected: FAIL because `ReplyMode` only supports `PASSIVE` and `ACTIVE`.

- [ ] **Step 3: Add `ACTIVE_STREAM` configuration and robot orchestration**

Update `GptBotConfig.ReplyMode`:

```java
public enum ReplyMode {
  PASSIVE,
  ACTIVE,
  ACTIVE_STREAM;
}
```

Update `GptBotRobot.handleBlip(...)` to:

- capture `bundle.getRpcServerUrl()` during request handling
- use `StreamingReplyWriter` in `ACTIVE_STREAM`
- keep active one-shot behavior unchanged
- fall back to one-shot active behavior if streaming setup fails before any partial text is published

- [ ] **Step 4: Preserve OT/concurrency safety in the orchestration**

Ensure the robot:

- only streams into a newly-created robot reply blip
- never overlaps writes for the same reply blip
- sends the final full accumulated text on completion
- does not duplicate a passive reply when active streaming is selected

The implementation should keep the callback handler synchronous from SupaWave’s point of view, but the streamed wave writes should happen through the follow-on RPC path, not through the passive response payload.

- [ ] **Step 5: Run the focused robot test again**

Run:

```bash
sbt "testOnly org.waveprotocol.examples.robots.gptbot.GptBotRobotTest"
```

Expected: PASS for passive, active, and active-stream reply mode scenarios.

- [ ] **Step 6: Commit**

```bash
git add \
  wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotConfig.java \
  wave/src/main/java/org/waveprotocol/examples/robots/gptbot/GptBotRobot.java \
  wave/src/test/java/org/waveprotocol/examples/robots/gptbot/GptBotRobotTest.java
git commit -m "feat(gptbot): add active streaming reply mode"
```

## Task 4: Document The Supported Streaming Pattern And Ship The Changelog

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java`
- Modify: `wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java`
- Modify: `docs/gpt-bot.md`
- Modify: `docs/robot-data-api-authentication.md`
- Create: `wave/config/changelog.d/2026-04-10-robot-streaming-replies.json`

- [ ] **Step 1: Add the failing docs assertions**

Extend `ApiDocsServletTest` so the rendered docs must mention:

```text
Streaming replies
newBlipId
document.modify
rpcServerUrl
```

- [ ] **Step 2: Run the docs test to verify the gap**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.ApiDocsServletTest"
```

Expected: FAIL because `/api-docs` does not yet describe the streaming reply flow.

- [ ] **Step 3: Update `/api-docs` and markdown docs**

Add a short “Streaming replies” section to `ApiDocsServlet` that explains:

- passive callbacks are single-response
- progressive output should use `blip.createChild` then `document.modify`
- `newBlipId` must be persisted
- updates should be serialized and full-text replace based
- robots should prefer the event bundle’s `rpcServerUrl`

Update `docs/gpt-bot.md` to document the new reply mode and the endpoint/token behavior. Update `docs/robot-data-api-authentication.md` so `rpcServerUrl` is described as the preferred endpoint hint for follow-on robot writes, regardless of whether it resolves to `/robot/rpc` or `/robot/dataapi/rpc`.

- [ ] **Step 4: Add the changelog fragment and rebuild generated output**

Create a fragment like:

```json
{
  "type": "feature",
  "area": "robot-api",
  "summary": "Document and demonstrate streamed robot replies via createChild + document.modify."
}
```

Then run the repo’s changelog assembly workflow and validation script.

- [ ] **Step 5: Run the focused verification set**

Run:

```bash
sbt "testOnly org.waveprotocol.examples.robots.gptbot.GptBotRobotTest org.waveprotocol.examples.robots.gptbot.SupaWaveApiClientTest org.waveprotocol.examples.robots.gptbot.OpenAiCodexClientTest org.waveprotocol.box.server.rpc.ApiDocsServletTest"
python3 scripts/validate-changelog.py
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add \
  wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ApiDocsServlet.java \
  wave/src/test/java/org/waveprotocol/box/server/rpc/ApiDocsServletTest.java \
  docs/gpt-bot.md \
  docs/robot-data-api-authentication.md \
  wave/config/changelog.d/2026-04-10-robot-streaming-replies.json \
  wave/config/changelog.json
git commit -m "docs(robot-api): document streaming replies into waves"
```

## Task 5: Final Verification, Review Evidence, And PR Prep

**Files:**
- Create: `journal/local-verification/2026-04-10-issue-811-robot-streaming.md`

- [ ] **Step 1: Run the targeted implementation test suite**

Run:

```bash
sbt "testOnly org.waveprotocol.examples.robots.gptbot.GptBotRobotTest org.waveprotocol.box.server.rpc.ApiDocsServletTest"
```

Expected: PASS.

- [ ] **Step 2: Run the local sanity verification appropriate to the changed area**

Run a narrow robot-path sanity check such as:

```bash
env GPTBOT_CODEX_ENGINE=echo GPTBOT_REPLY_MODE=active-stream GPTBOT_CONTEXT_MODE=none \
  sbt "runMain org.waveprotocol.examples.robots.gptbot.GptBotServer"
```

Then confirm the local `/healthz`, `/`, and `/_wave/robot/profile` endpoints respond.

- [ ] **Step 3: Record exact commands and results**

Write the verification record to:

```text
journal/local-verification/2026-04-10-issue-811-robot-streaming.md
```

Include:

- commands
- PASS/FAIL results
- any limitations

- [ ] **Step 4: Run the Claude Opus implementation review**

Run the repo’s `claude-review` workflow against the implementation diff and capture the findings summary plus rerun outcome if fixes are needed.

- [ ] **Step 5: Update the GitHub issue evidence**

Add an issue comment with:

- worktree path and branch
- spec path
- plan path
- Claude plan review result
- commit SHAs
- verification commands/results
- Claude/code review findings and resolutions

- [ ] **Step 6: Prepare the PR**

Create the branch PR against `main` after the above checks are green.
