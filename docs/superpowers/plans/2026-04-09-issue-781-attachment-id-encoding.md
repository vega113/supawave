# Issue #781 Attachment ID Encoding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `/attachmentsInfo` requests preserve attachment ids containing `+` by URL-encoding each id before the client joins them into the `attachmentIds` query parameter.

**Architecture:** Keep the production change local to `AttachmentManagerImpl`, but extract the query-string assembly into a tiny shared helper that can be exercised by the SBT test toolchain. Use a JVM test to pin the broken `+` case and the per-id join behavior, then switch the client call site to use `URL.encodeQueryString(...)` through that helper.

**Tech Stack:** Java 17, GWT client request code, SBT/JUnit, GitHub issue workflow

---

## Investigation Summary

- Client seam: `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/AttachmentManagerImpl.java`
- Server endpoint: `wave/src/main/java/org/waveprotocol/box/server/rpc/AttachmentInfoServlet.java`
- Existing SBT lane constraint: default `Test` excludes `org/waveprotocol/wave/client/**`, so direct client tests in `wave/src/test/java/org/waveprotocol/wave/client/...` do not run in this workflow.
- Plan choice: add a tiny shared request-builder helper under a non-excluded package, test that helper with JUnit, and keep the runtime client edit limited to calling the helper with a GWT query encoder.

## Files

- Create: `wave/src/main/java/org/waveprotocol/wave/media/model/AttachmentInfoRequestBuilder.java`
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/AttachmentManagerImpl.java`
- Test: `wave/src/test/java/org/waveprotocol/wave/media/model/AttachmentInfoRequestBuilderTest.java`

### Task 1: Add the failing regression test

- [ ] **Step 1: Write the failing test**

```java
public void testBuildEncodesPlusInAttachmentId() {
  assertEquals(
      "/attachmentsInfo?attachmentIds=att%2B123",
      AttachmentInfoRequestBuilder.build(
          "/attachmentsInfo",
          Collections.singletonList("att+123"),
          JAVA_QUERY_ENCODER));
}

public void testBuildEncodesEachIdBeforeJoining() {
  assertEquals(
      "/attachmentsInfo?attachmentIds=att%2Bone,second%2Bid",
      AttachmentInfoRequestBuilder.build(
          "/attachmentsInfo",
          Arrays.asList("att+one", "second+id"),
          JAVA_QUERY_ENCODER));
}
```

- [ ] **Step 2: Run the focused test and confirm it fails for the expected reason**

Run:

```bash
sbt "testOnly org.waveprotocol.wave.media.model.AttachmentInfoRequestBuilderTest"
```

Expected: FAIL because `AttachmentInfoRequestBuilder` does not exist yet.

### Task 2: Add the minimal request builder and client fix

- [ ] **Step 3: Implement the shared request builder**

Create `wave/src/main/java/org/waveprotocol/wave/media/model/AttachmentInfoRequestBuilder.java` with:

```java
public final class AttachmentInfoRequestBuilder {
  public interface Encoder {
    String encode(String value);
  }

  private AttachmentInfoRequestBuilder() {}

  public static String build(String baseUrl, Iterable<String> attachmentIds, Encoder encoder) {
    StringBuilder request = new StringBuilder(baseUrl).append("?attachmentIds=");
    boolean first = true;
    for (String attachmentId : attachmentIds) {
      if (!first) {
        request.append(',');
      }
      request.append(encoder.encode(attachmentId));
      first = false;
    }
    return request.toString();
  }
}
```

- [ ] **Step 4: Switch `AttachmentManagerImpl` to use the helper**

Use:

```java
private static final AttachmentInfoRequestBuilder.Encoder ATTACHMENT_ID_QUERY_ENCODER =
    new AttachmentInfoRequestBuilder.Encoder() {
      @Override
      public String encode(String value) {
        return URL.encodeQueryString(value);
      }
    };
```

and replace the current manual `request += attacmentId;` loop with:

```java
String request = AttachmentInfoRequestBuilder.build(
    ATTACHMENTS_INFO_URL_BASE, pendingQueue, ATTACHMENT_ID_QUERY_ENCODER);
```

- [ ] **Step 5: Run the focused regression test and confirm it passes**

Run:

```bash
sbt "testOnly org.waveprotocol.wave.media.model.AttachmentInfoRequestBuilderTest"
```

Expected: PASS

### Task 3: Verify the scoped change and record evidence

- [ ] **Step 6: Run the narrowest additional verification for this area**

Run:

```bash
sbt "testOnly org.waveprotocol.box.server.rpc.AttachmentInfoServletValidationTest"
```

Expected: PASS

- [ ] **Step 7: Review the diff**

Run direct review plus Claude review against the scoped diff before PR creation.

- [ ] **Step 8: Commit**

```bash
git add \
  docs/superpowers/plans/2026-04-09-issue-781-attachment-id-encoding.md \
  wave/src/main/java/org/waveprotocol/wave/media/model/AttachmentInfoRequestBuilder.java \
  wave/src/main/java/org/waveprotocol/wave/client/doodad/attachment/AttachmentManagerImpl.java \
  wave/src/test/java/org/waveprotocol/wave/media/model/AttachmentInfoRequestBuilderTest.java
git commit -m "fix(attachment): encode metadata lookup ids"
```
