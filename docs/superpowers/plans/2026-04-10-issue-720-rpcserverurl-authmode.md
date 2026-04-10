# Issue 720 RpcServerUrl Auth Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make passive robot event bundles advertise the JSON-RPC endpoint that matches the robot's configured OAuth mode: `/robot/rpc` for 2-legged robots and `/robot/dataapi/rpc` for 3-legged robots.

**Architecture:** Keep the fix on the passive robot path only. Preserve the robot-advertised RPC endpoint when capabilities are fetched, then choose the bundle `rpcServerUrl` at event-generation time from the current robot account instead of freezing one URL at gateway construction. Treat the parsed endpoint as a runtime transport hint, not as part of the persisted capability identity, so the change stays narrow and restart-safe by forcing a capability refresh when the hint is missing.

**Tech Stack:** Java, existing Wave robot capability parsing, JUnit 3 style unit tests, Mockito, SBT `wave/testOnly`.

---

### Task 1: Preserve The Robot-Advertised RPC Endpoint During Capability Fetch

**Files:**
- Modify: `wave/src/main/java/com/google/wave/api/robot/RobotCapabilitiesParser.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/RobotCapabilities.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/passive/RobotConnectorTest.java`

- [ ] **Step 1: Write the failing fetch-capabilities regression tests**

```java
public void testFetchCapabilitiesPrefersRobotRpcWhenCapabilitiesAdvertiseTwoLeggedOauth()
    throws Exception {
  String capabilitiesXml =
      "<w:robot xmlns:w=\"http://wave.google.com/extensions/robots/1.0\">"
          + "<w:version>hash</w:version>"
          + "<w:protocolversion>0.22</w:protocolversion>"
          + "<w:capabilities><w:capability name=\"WAVELET_SELF_ADDED\"/></w:capabilities>"
          + "<w:consumer_keys>"
          + "<w:consumer_key for=\"https://wave.example.com/robot/rpc\">robot@example.com</w:consumer_key>"
          + "</w:consumer_keys>"
          + "</w:robot>";
  when(connection.get(TEST_CAPABILITIES_ENDPOINT)).thenReturn(capabilitiesXml);

  RobotAccountData accountData =
      connector.fetchCapabilities(ROBOT_ACCOUNT, "https://wave.example.com/robot/rpc");

  assertEquals("https://wave.example.com/robot/rpc",
      accountData.getCapabilities().getRpcServerUrl());
}

public void testFetchCapabilitiesFallsBackToDataApiRpcWhenOnlyThreeLeggedOauthIsAdvertised()
    throws Exception {
  String capabilitiesXml =
      "<w:robot xmlns:w=\"http://wave.google.com/extensions/robots/1.0\">"
          + "<w:version>hash</w:version>"
          + "<w:protocolversion>0.22</w:protocolversion>"
          + "<w:capabilities><w:capability name=\"WAVELET_SELF_ADDED\"/></w:capabilities>"
          + "<w:consumer_keys>"
          + "<w:consumer_key for=\"https://wave.example.com/robot/dataapi/rpc\">robot@example.com</w:consumer_key>"
          + "</w:consumer_keys>"
          + "</w:robot>";
  when(connection.get(TEST_CAPABILITIES_ENDPOINT)).thenReturn(capabilitiesXml);

  RobotAccountData accountData =
      connector.fetchCapabilities(ROBOT_ACCOUNT, "https://wave.example.com/robot/rpc");

  assertEquals("https://wave.example.com/robot/dataapi/rpc",
      accountData.getCapabilities().getRpcServerUrl());
}
```

- [ ] **Step 2: Run the focused connector test to verify red**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotConnectorTest"`
Expected: FAIL because `RobotCapabilities` does not yet expose a parsed `rpcServerUrl`.

- [ ] **Step 3: Add the runtime transport hint to parsed capabilities**

```java
// RobotCapabilities.java
private final String rpcServerUrl;

public RobotCapabilities(Map<EventType, Capability> capabilitiesMap, String capabilitiesHash,
    ProtocolVersion version) {
  this(capabilitiesMap, capabilitiesHash, version, "");
}

public RobotCapabilities(Map<EventType, Capability> capabilitiesMap, String capabilitiesHash,
    ProtocolVersion version, String rpcServerUrl) {
  ...
  this.rpcServerUrl = rpcServerUrl == null ? "" : rpcServerUrl;
}

public String getRpcServerUrl() {
  return rpcServerUrl;
}

// RobotCapabilitiesParser.java
private static final String ACTIVE_RPC_PATH = "/robot/rpc";
private static final String DATA_API_RPC_PATH = "/robot/dataapi/rpc";
private String rpcServerUrl = "";

public String getRpcServerUrl() {
  return rpcServerUrl;
}

for (Element consumerKeyElement : getElements(document, CONSUMER_KEYS_TAG, CONSUMER_KEY_TAG, XML_NS)) {
  String forUrl = consumerKeyElement.getAttributeValue(CONSUMER_KEY_FOR_ATTRIBUTE);
  if (forUrl == null || forUrl.isEmpty()) {
    continue;
  }
  if (forUrl.equals(activeRobotApiUrl)) {
    consumerKey = consumerKeyElement.getText();
  }
  if (forUrl.endsWith(ACTIVE_RPC_PATH)) {
    rpcServerUrl = forUrl;
  } else if (rpcServerUrl.isEmpty() && forUrl.endsWith(DATA_API_RPC_PATH)) {
    rpcServerUrl = forUrl;
  }
}

// RobotConnector.java
RobotCapabilities capabilities = new RobotCapabilities(
    parser.getCapabilities(), parser.getCapabilitiesHash(), parser.getProtocolVersion(),
    parser.getRpcServerUrl());
```

- [ ] **Step 4: Re-run the focused connector test to verify green**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotConnectorTest"`
Expected: PASS, including the new 2-legged and 3-legged assertions.

- [ ] **Step 5: Commit the capability-fetch seam**

```bash
git add \
  wave/src/main/java/com/google/wave/api/robot/RobotCapabilitiesParser.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/RobotCapabilities.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/passive/RobotConnectorTest.java
git commit -m "fix(robots): retain advertised rpc endpoint from capabilities"
```

### Task 2: Choose RpcServerUrl Per Passive Robot Event Dispatch

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/passive/EventGenerator.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/passive/Robot.java`
- Modify: `wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotsGateway.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/passive/RobotTest.java`
- Test: `wave/src/test/java/org/waveprotocol/box/server/robots/passive/EventGeneratorTest.java`

- [ ] **Step 1: Write the failing passive-dispatch regression tests**

```java
public void testProcessAdvertisesRobotRpcForTwoLeggedRobot() throws Exception {
  RobotAccountData twoLeggedAccount =
      new RobotAccountDataImpl(ROBOT, "www.example.com", "secret", new RobotCapabilities(
          Maps.<EventType, Capability>newHashMap(), "fake", ProtocolVersion.DEFAULT,
          "https://wave.example.com/robot/rpc"), true, 0L, null, "", 0L, 20L, false);
  doAnswer(invocation -> {
    robot.setAccount(twoLeggedAccount);
    return null;
  }).when(gateway).updateRobotAccount(robot);

  EventMessageBundle messages = new EventMessageBundle(ROBOT_NAME.toEmailAddress(), "");
  messages.addEvent(new DocumentChangedEvent(null, null, ALEX.getAddress(), 0L, "b+1234"));
  when(eventGenerator.generateEvents(any(), anyMap(), any(), eq("https://wave.example.com/robot/rpc")))
      .thenReturn(messages);
  when(connector.sendMessageBundle(any(EventMessageBundle.class), eq(robot), any(ProtocolVersion.class)))
      .thenReturn(Collections.<OperationRequest>emptyList());

  enqueueEmptyWavelet();
  robot.run();

  verify(eventGenerator).generateEvents(any(), anyMap(), any(),
      eq("https://wave.example.com/robot/rpc"));
}

public void testProcessAdvertisesDataApiRpcForThreeLeggedRobot() throws Exception {
  RobotAccountData threeLeggedAccount =
      new RobotAccountDataImpl(ROBOT, "www.example.com", "secret", new RobotCapabilities(
          Maps.<EventType, Capability>newHashMap(), "fake", ProtocolVersion.DEFAULT,
          "https://wave.example.com/robot/dataapi/rpc"), true, 0L, null, "", 0L, 20L, false);
  doAnswer(invocation -> {
    robot.setAccount(threeLeggedAccount);
    return null;
  }).when(gateway).updateRobotAccount(robot);

  EventMessageBundle messages = new EventMessageBundle(ROBOT_NAME.toEmailAddress(), "");
  messages.addEvent(new DocumentChangedEvent(null, null, ALEX.getAddress(), 0L, "b+1234"));
  when(eventGenerator.generateEvents(any(), anyMap(), any(),
      eq("https://wave.example.com/robot/dataapi/rpc"))).thenReturn(messages);
  when(connector.sendMessageBundle(any(EventMessageBundle.class), eq(robot), any(ProtocolVersion.class)))
      .thenReturn(Collections.<OperationRequest>emptyList());

  enqueueEmptyWavelet();
  robot.run();

  verify(eventGenerator).generateEvents(any(), anyMap(), any(),
      eq("https://wave.example.com/robot/dataapi/rpc"));
}
```

- [ ] **Step 2: Run the passive robot tests to verify red**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotTest" "wave/testOnly org.waveprotocol.box.server.robots.passive.EventGeneratorTest"`
Expected: FAIL because the passive path still freezes a single `rpcServerUrl` before account capabilities are considered.

- [ ] **Step 3: Move rpcServerUrl selection to event-generation time**

```java
// EventGenerator.java
public EventMessageBundle generateEvents(WaveletAndDeltas waveletAndDeltas,
    Map<EventType, Capability> capabilities, EventDataConverter converter,
    String rpcServerUrl) {
  EventMessageBundle messages = new EventMessageBundle(robotName.toEmailAddress(), rpcServerUrl);
  ...
}

// Robot.java
private static final String DEFAULT_DATA_API_RPC_PATH = "/robot/dataapi/rpc";

private String resolveRpcServerUrl(RobotAccountData currentAccount) {
  RobotCapabilities capabilities = currentAccount.getCapabilities();
  if (capabilities != null && !capabilities.getRpcServerUrl().isEmpty()) {
    return capabilities.getRpcServerUrl();
  }
  return gateway.getDefaultRpcServerUrl();
}

if (currentAccount.getCapabilities() == null
    || currentAccount.getCapabilities().getRpcServerUrl().isEmpty()) {
  gateway.updateRobotAccount(this);
  currentAccount = account;
}

EventMessageBundle messages = eventGenerator.generateEvents(
    wavelet, capabilities.getCapabilitiesMap(),
    converterManager.getEventDataConverter(capabilities.getProtocolVersion()),
    resolveRpcServerUrl(currentAccount));

// RobotsGateway.java
public static final String DATA_API_RPC_PATH = "/robot/dataapi/rpc";
private final String defaultRpcServerUrl;

this.defaultRpcServerUrl = PublicBaseUrlResolver.resolve(config) + DATA_API_RPC_PATH;

String getDefaultRpcServerUrl() {
  return defaultRpcServerUrl;
}
```

- [ ] **Step 4: Re-run the passive robot tests to verify green**

Run: `sbt "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotTest" "wave/testOnly org.waveprotocol.box.server.robots.passive.EventGeneratorTest"`
Expected: PASS, with passive dispatch verifying `/robot/rpc` for 2-legged and `/robot/dataapi/rpc` for 3-legged.

- [ ] **Step 5: Commit the passive-dispatch fix**

```bash
git add \
  wave/src/main/java/org/waveprotocol/box/server/robots/passive/EventGenerator.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/passive/Robot.java \
  wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotsGateway.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/passive/RobotTest.java \
  wave/src/test/java/org/waveprotocol/box/server/robots/passive/EventGeneratorTest.java
git commit -m "fix(robots): advertise rpc endpoint by oauth mode"
```

### Task 3: Verification, Review, And PR Evidence

**Files:**
- Modify: `journal/local-verification/2026-04-10-issue-720-rpcserverurl-authmode.md`
- Modify: GitHub issue `#720` comments

- [ ] **Step 1: Run the targeted verification suite**

Run:

```bash
sbt \
  "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotConnectorTest" \
  "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotTest" \
  "wave/testOnly org.waveprotocol.box.server.robots.passive.EventGeneratorTest"
```

Expected: PASS for all targeted passive-robot regression tests.

- [ ] **Step 2: Record the verification evidence locally**

```md
# Issue 720 Local Verification

- Command: `sbt "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotConnectorTest" "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotTest" "wave/testOnly org.waveprotocol.box.server.robots.passive.EventGeneratorTest"`
- Result: PASS
- Notes: Verified 2-legged robots advertise `/robot/rpc`; 3-legged robots advertise `/robot/dataapi/rpc`.
```

- [ ] **Step 3: Run direct review plus Claude review on the implementation diff**

Run:

```bash
export REVIEW_TASK="issue-720 rpcServerUrl auth mode"
export REVIEW_GOAL="Ensure passive robot event bundles advertise the JSON-RPC endpoint that matches 2-legged versus 3-legged OAuth configuration."
export REVIEW_ACCEPTANCE=$'- 2-legged passive robots advertise /robot/rpc\n- 3-legged passive robots advertise /robot/dataapi/rpc\n- Fix stays on the passive robot path\n- Focused regression tests pass'
export REVIEW_RUNTIME="Java + Wave robot server"
export REVIEW_RISKY="Passive robot capability refresh, rpcServerUrl selection, event bundle generation"
export REVIEW_TEST_COMMANDS='sbt "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotConnectorTest" "wave/testOnly org.waveprotocol.box.server.robots.passive.RobotTest" "wave/testOnly org.waveprotocol.box.server.robots.passive.EventGeneratorTest"'
export REVIEW_TEST_RESULTS="pass"
export REVIEW_DIFF_SPEC="origin/main...HEAD"
/Users/vega/.codex/skills/public/claude-review/scripts/review_task.sh
```

Expected: review output in `/tmp/claude-review.out`; address any findings before PR.

- [ ] **Step 4: Update issue evidence and open the PR**

```bash
git status --short
git log --oneline --decorate origin/main..HEAD
git push -u origin issue-720-rpcserverurl-authmode-20260410
```

Then add a GitHub issue comment containing:
- worktree path `/Users/vega/devroot/worktrees/issue-720-rpcserverurl-authmode-20260410`
- branch `issue-720-rpcserverurl-authmode-20260410`
- this plan path
- commit SHAs and one-line summaries
- verification command/result
- review summary and resolution notes

- [ ] **Step 5: Create the PR**

Create a PR against `main` titled:

```text
fix(robots): advertise rpcServerUrl by robot auth mode
```

PR body must link `#720` and include the same focused verification command/result.
