package org.waveprotocol.box.j2cl.richtext;

import java.util.List;
import java.util.Locale;
import org.waveprotocol.box.j2cl.search.J2clSidecarWriteSession;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSubmitRequest;

/**
 * Builds sidecar submit deltas from structured composer content without wiring UI call sites.
 *
 * <p>The token counter is shared across create-wave and reply requests for one browser session. A
 * create delta prepends the required AddParticipant op before the root blip document operation.
 */
public final class J2clRichContentDeltaFactory {
  public static final class CreateWaveRequest {
    private final String createdWaveId;
    private final SidecarSubmitRequest submitRequest;

    public CreateWaveRequest(String createdWaveId, SidecarSubmitRequest submitRequest) {
      this.createdWaveId = createdWaveId;
      this.submitRequest = submitRequest;
    }

    public String getCreatedWaveId() {
      return createdWaveId;
    }

    public SidecarSubmitRequest getSubmitRequest() {
      return submitRequest;
    }
  }

  private static final char[] WEB64_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toCharArray();

  private final String sessionSeed;
  private int counter;

  public J2clRichContentDeltaFactory(String sessionSeed) {
    this.sessionSeed = sanitizeSeed(sessionSeed);
  }

  public CreateWaveRequest createWaveRequest(String address, J2clComposerDocument document) {
    requirePresent(document, "Missing composer document.");
    String normalizedAddress = normalizeAddress(address);
    String domain = extractDomain(normalizedAddress);
    String waveToken = nextToken("w+");
    String createdWaveId = domain + "/" + waveToken;
    String versionZeroHistoryHash = buildVersionZeroHistoryHash(domain, waveToken);
    String deltaJson =
        buildDeltaJson(
            0L,
            versionZeroHistoryHash,
            normalizedAddress,
            buildAddParticipantOperation(normalizedAddress)
                + ","
                + buildDocumentOperation("b+root", document));
    return new CreateWaveRequest(
        createdWaveId,
        new SidecarSubmitRequest(buildWaveletName(createdWaveId), deltaJson, null));
  }

  /**
   * F-3.S2 (#1038, R-5.3 step 4): sugar wrapper that appends a mention
   * chip annotation to the supplied builder. Encodes a `link/manual`
   * annotated-text component whose value is the participant address
   * and whose display text is `@displayName`.
   *
   * <p>Usage from the composer surface:
   * <pre>
   *   J2clComposerDocument.Builder b = J2clComposerDocument.builder();
   *   factory.appendMentionInsert(b, "alice@example.com", "Alice Adams");
   *   J2clComposerDocument doc = b.build();
   * </pre>
   *
   * <p>Address normalisation: the wrapper applies {@link #normalizeAddress}
   * (trim + lowercase) and validates the address shape via
   * {@link #extractDomain} so the surface gets a single error path on
   * malformed input rather than discovering the issue at submit time.
   */
  public J2clComposerDocument.Builder appendMentionInsert(
      J2clComposerDocument.Builder builder, String participantAddress, String displayName) {
    requirePresent(builder, "Missing composer document builder.");
    String normalizedAddress = normalizeAddress(participantAddress);
    extractDomain(normalizedAddress);
    String label = displayName == null || displayName.trim().isEmpty()
        ? normalizedAddress
        : displayName.trim();
    builder.annotatedText("link/manual", normalizedAddress, "@" + label);
    return builder;
  }

  /**
   * F-3.S2 (#1038, R-5.4 step 3): build a stand-alone toggle delta that
   * sets the `task/done` annotation on the entire blip body. The op
   * opens the annotation at offset 0 (with the new boolean value) and
   * closes it at the end of the document so the supplement live-update
   * on the GWT path mirrors the existing `task/done` writer shape.
   *
   * <p>The returned request is independent of any reply draft so a
   * task toggle on blip B does not clobber an in-flight reply on
   * blip A.
   */
  public SidecarSubmitRequest taskToggleRequest(
      String address, J2clSidecarWriteSession session, String blipId, boolean completed) {
    return buildBlipAnnotationRequest(
        address,
        session,
        blipId,
        new String[] {"task/done"},
        new String[] {completed ? "true" : "false"});
  }

  /**
   * F-3.S2 (#1038, R-5.4 step 5): build a stand-alone delta that
   * writes the `task/assignee` and `task/dueTs` annotations on the blip.
   * Either value may be empty, in which case the annotation start
   * still serialises with an empty-string value field
   * (`{"1":"task/assignee","3":""}`) and the GWT reader treats the
   * empty string as the "unset" sentinel.
   *
   * <p>The reader path for `task/dueTs` ({@code
   * J2clInteractionBlipModel#parseLong}) treats the annotation value
   * as a millisecond timestamp. The composer surface, however,
   * receives the raw `YYYY-MM-DD` string from a native HTML date
   * input. To make metadata round-trip after submit/refresh we
   * convert that ISO date to UTC-midnight millis here. Numeric
   * values pass through unchanged so legacy callers and explicit
   * timestamp UIs keep working. Unparseable values are coerced to
   * the empty-string "unset" sentinel rather than written as a
   * non-numeric annotation that the reader silently drops.
   * (PR #1066 review thread PRRT_kwDOBwxLXs593gTP.)
   */
  public SidecarSubmitRequest taskMetadataRequest(
      String address,
      J2clSidecarWriteSession session,
      String blipId,
      String assigneeAddress,
      String dueDate) {
    String assignee = assigneeAddress == null ? "" : assigneeAddress.trim();
    String due = normalizeDueTimestamp(dueDate);
    return buildBlipAnnotationRequest(
        address,
        session,
        blipId,
        new String[] {"task/assignee", "task/dueTs"},
        new String[] {assignee, due});
  }

  /**
   * F-3.S3 (#1038, R-5.5): build a stand-alone toggle delta against the
   * `react+<blipId>` data document. Adds (when the user is not already
   * a reactor under {@code emoji}) or removes (when they are) the
   * user's `<user address=Y/>` element. When the toggled emoji had only
   * one reactor and that reactor is being removed, also drop the empty
   * `<reaction emoji=X>` wrapper so the reader doesn't render an empty
   * chip.
   *
   * <p>The {@code currentSnapshot} list MUST be the most recent
   * per-blip {@link SidecarReactionEntry} aggregate the J2CL client has
   * (drawn from {@link J2clInteractionBlipModel#getReactionEntries()}).
   * The factory uses it to compute element-tree retain offsets without
   * round-tripping the live document.
   *
   * <p>Snapshot semantics:
   * <ul>
   *   <li>{@code null} or empty → the document has no `<reactions>`
   *       root yet. The factory emits the full envelope insert
   *       ({@code <reactions><reaction emoji=X><user address=Y/>}…)
   *       which the server applies against an empty document at base
   *       version. Only valid for {@code adding=true}.
   *   <li>Non-empty list → the root exists; the factory emits a
   *       retain over the existing element-tree items and either
   *       appends a sibling `<reaction>` (toggle on for an emoji not
   *       yet owned by this user) or deletes the user's existing
   *       `<user>` element (toggle off).
   * </ul>
   */
  public SidecarSubmitRequest reactionToggleRequest(
      String address,
      J2clSidecarWriteSession session,
      String blipId,
      String emoji,
      List<SidecarReactionEntry> currentSnapshot,
      boolean adding) {
    requirePresent(session, "Missing write session.");
    String normalizedAddress = normalizeAddress(address);
    extractDomain(normalizedAddress);
    String selectedWaveId =
        requireNonEmpty(session.getSelectedWaveId(), "Missing selected wave id.");
    String historyHash =
        requireNonEmpty(session.getHistoryHash(), "Missing write-session history hash.");
    String channelId = requireNonEmpty(session.getChannelId(), "Missing write-session channel id.");
    String trimmedBlipId = requireNonEmpty(blipId, "Missing blip id.");
    String trimmedEmoji = requireNonEmpty(emoji, "Missing reaction emoji.");
    long baseVersion = session.getBaseVersion();
    if (baseVersion < 0) {
      throw new IllegalArgumentException("Invalid write-session base version.");
    }

    String reactionDocumentId = "react+" + trimmedBlipId;
    StringBuilder components = new StringBuilder();
    if (adding) {
      buildAddingComponents(components, currentSnapshot, trimmedEmoji, normalizedAddress);
    } else {
      buildRemovingComponents(components, currentSnapshot, trimmedEmoji, normalizedAddress);
    }
    StringBuilder operation = new StringBuilder(components.length() + reactionDocumentId.length() + 32);
    operation
        .append("{\"3\":{\"1\":\"")
        .append(escapeJson(reactionDocumentId))
        .append("\",\"2\":{\"1\":[")
        .append(components)
        .append("]}}}");
    String deltaJson =
        buildDeltaJson(baseVersion, historyHash, normalizedAddress, operation.toString());
    return new SidecarSubmitRequest(buildWaveletName(selectedWaveId), deltaJson, channelId);
  }

  /**
   * Compute the document item count (count of element-start, element-end,
   * character, and annotation-boundary positions) that a `<reactions>`
   * data document occupies given the supplied per-emoji snapshot.
   *
   * <p>An empty snapshot returns 0 (no `<reactions>` root yet); a
   * single emoji with one user returns 6 (root start + reaction start +
   * user start + 3 ends). Each additional user adds 2 items
   * (start + end); each additional emoji adds 2 + 2*users items.
   * Package-private for the unit-test offset assertions.
   */
  static int reactionsRootItemCount(List<SidecarReactionEntry> snapshot) {
    if (snapshot == null || snapshot.isEmpty()) {
      return 0;
    }
    int count = 0;
    for (SidecarReactionEntry entry : snapshot) {
      if (entry == null) continue;
      if (count == 0) {
        count = 2; // <reactions> open + close — only once a real entry exists
      }
      List<String> users = entry.getAddresses();
      int userCount = users == null ? 0 : users.size();
      count += 2 + (2 * userCount); // <reaction> open + close + 2 per <user/>
    }
    return count;
  }

  /**
   * F-3.S3: append toggle-ON ops to {@code components}. When the
   * snapshot is empty, emit the full root envelope (the server creates
   * the data document on demand). Otherwise emit a retain spanning the
   * existing element tree minus the closing `</reactions>` tag, then a
   * new `<reaction emoji=X><user address=Y/></reaction>` sibling. The
   * reader's `LinkedHashMap`-based decoder merges duplicate emoji
   * elements, so re-toggling an emoji owned by another user simply adds
   * another `<reaction>` sibling that the read codec collapses.
   */
  private static void buildAddingComponents(
      StringBuilder components,
      List<SidecarReactionEntry> currentSnapshot,
      String emoji,
      String normalizedAddress) {
    int rootItemCount = reactionsRootItemCount(currentSnapshot);
    if (rootItemCount == 0) {
      // No <reactions> root yet — emit the full envelope.
      appendElementStartReactions(components);
      appendComponentSeparator(components);
      appendElementStartReaction(components, emoji);
      appendComponentSeparator(components);
      appendElementStartUser(components, normalizedAddress);
      appendComponentSeparator(components);
      appendElementEnd(components);
      appendComponentSeparator(components);
      appendElementEnd(components);
      appendComponentSeparator(components);
      appendElementEnd(components);
      return;
    }
    // Retain the existing root open + all reactions, but stop just
    // before the closing </reactions> tag so the new <reaction> lands
    // inside the root.
    appendRetain(components, rootItemCount - 1);
    appendComponentSeparator(components);
    appendElementStartReaction(components, emoji);
    appendComponentSeparator(components);
    appendElementStartUser(components, normalizedAddress);
    appendComponentSeparator(components);
    appendElementEnd(components);
    appendComponentSeparator(components);
    appendElementEnd(components);
    appendComponentSeparator(components);
    // Trailing retain over the closing </reactions> end tag (1 item).
    appendRetain(components, 1);
  }

  /**
   * F-3.S3: append toggle-OFF ops to {@code components}. Walks
   * {@code currentSnapshot} to compute the item offset of the user's
   * `<user address=Y/>` element under the matching `<reaction emoji=X>`
   * element. When the user was the only reactor under that emoji, the
   * factory ALSO drops the now-empty `<reaction>` wrapper so the read
   * side doesn't render an empty chip. Throws when the snapshot does
   * not list the address under the emoji (the controller MUST gate this
   * with an active-for-current-user check; an unguarded call indicates a
   * stale snapshot bug).
   */
  private static void buildRemovingComponents(
      StringBuilder components,
      List<SidecarReactionEntry> currentSnapshot,
      String emoji,
      String normalizedAddress) {
    if (currentSnapshot == null || currentSnapshot.isEmpty()) {
      throw new IllegalArgumentException(
          "Cannot remove reaction: snapshot empty for emoji " + emoji);
    }
    // Walk emojis until we find the matching one; track running offset.
    // Offset starts at 1 (skip the <reactions> root open).
    int offset = 1;
    SidecarReactionEntry matched = null;
    int userIndexInMatch = -1;
    for (SidecarReactionEntry entry : currentSnapshot) {
      if (entry == null) continue;
      List<String> users = entry.getAddresses();
      int userCount = users == null ? 0 : users.size();
      if (emoji.equals(entry.getEmoji())) {
        // Check whether this user is in this entry.
        if (users != null) {
          for (int i = 0; i < users.size(); i++) {
            if (normalizedAddress.equalsIgnoreCase(users.get(i))) {
              matched = entry;
              userIndexInMatch = i;
              break;
            }
          }
        }
        if (matched != null) {
          break;
        }
      }
      // Skip past this <reaction>...</reaction>.
      offset += 2 + (2 * userCount);
    }
    if (matched == null) {
      throw new IllegalArgumentException(
          "Cannot remove reaction: snapshot does not list "
              + normalizedAddress
              + " under "
              + emoji);
    }
    int rootItemCount = reactionsRootItemCount(currentSnapshot);
    boolean isLastUser = matched.getAddresses().size() == 1;
    int reactionEntryCount = 0;
    for (SidecarReactionEntry entry : currentSnapshot) {
      if (entry != null) {
        reactionEntryCount++;
      }
    }
    boolean isLastReaction = reactionEntryCount == 1;
    if (isLastUser && isLastReaction) {
      // Last user of the only remaining reaction: delete the entire
      // <reactions> document (root open + reaction + user + 3 ends).
      // No retains — the document becomes empty on the server.
      appendDeleteElementStartNoAttrs(components, "reactions");
      appendComponentSeparator(components);
      appendDeleteElementStart(components, "reaction", "emoji", emoji);
      appendComponentSeparator(components);
      appendDeleteElementStart(components, "user", "address", normalizedAddress);
      appendComponentSeparator(components);
      appendDeleteElementEnd(components);
      appendComponentSeparator(components);
      appendDeleteElementEnd(components);
      appendComponentSeparator(components);
      appendDeleteElementEnd(components);
      return;
    }
    if (isLastUser) {
      // Delete the entire <reaction emoji=X></reaction> wrapper plus
      // its single <user/> child; other reactions remain so we retain
      // the surrounding root items. offset currently points at the
      // <reaction> element start; the wrapper occupies 4 items
      // (reaction start + user start + user end + reaction end).
      if (offset > 0) {
        appendRetain(components, offset);
        appendComponentSeparator(components);
      }
      appendDeleteElementStart(components, "reaction", "emoji", emoji);
      appendComponentSeparator(components);
      appendDeleteElementStart(components, "user", "address", normalizedAddress);
      appendComponentSeparator(components);
      appendDeleteElementEnd(components);
      appendComponentSeparator(components);
      appendDeleteElementEnd(components);
      int deleted = 4;
      int trailing = rootItemCount - offset - deleted;
      if (trailing > 0) {
        appendComponentSeparator(components);
        appendRetain(components, trailing);
      }
      return;
    }
    // Single-user delete: skip past the <reaction emoji=X> open and
    // any prior <user/> siblings, then delete just the user pair.
    int userOffset = offset + 1 + (2 * userIndexInMatch);
    if (userOffset > 0) {
      appendRetain(components, userOffset);
      appendComponentSeparator(components);
    }
    appendDeleteElementStart(components, "user", "address", normalizedAddress);
    appendComponentSeparator(components);
    appendDeleteElementEnd(components);
    int trailing = rootItemCount - userOffset - 2;
    if (trailing > 0) {
      appendComponentSeparator(components);
      appendRetain(components, trailing);
    }
  }

  private static void appendElementStartReactions(StringBuilder builder) {
    builder.append("{\"3\":{\"1\":\"reactions\",\"2\":[]}}");
  }

  private static void appendElementStartReaction(StringBuilder builder, String emoji) {
    builder
        .append("{\"3\":{\"1\":\"reaction\",\"2\":[{\"1\":\"emoji\",\"2\":\"")
        .append(escapeJson(emoji))
        .append("\"}]}}");
  }

  private static void appendElementStartUser(StringBuilder builder, String address) {
    builder
        .append("{\"3\":{\"1\":\"user\",\"2\":[{\"1\":\"address\",\"2\":\"")
        .append(escapeJson(address))
        .append("\"}]}}");
  }

  private static void appendDeleteElementStartNoAttrs(StringBuilder builder, String type) {
    builder
        .append("{\"7\":{\"1\":\"")
        .append(escapeJson(type))
        .append("\",\"2\":[]}}");
  }

  private static void appendDeleteElementStart(
      StringBuilder builder, String type, String attrKey, String attrValue) {
    builder
        .append("{\"7\":{\"1\":\"")
        .append(escapeJson(type))
        .append("\",\"2\":[{\"1\":\"")
        .append(escapeJson(attrKey))
        .append("\",\"2\":\"")
        .append(escapeJson(attrValue))
        .append("\"}]}}");
  }

  private static void appendDeleteElementEnd(StringBuilder builder) {
    builder.append("{\"8\":true}");
  }

  private static void appendRetain(StringBuilder builder, int itemCount) {
    if (itemCount <= 0) {
      throw new IllegalArgumentException("retain item count must be positive: " + itemCount);
    }
    builder.append("{\"5\":").append(itemCount).append("}");
  }

  /**
   * Convert a task due-date input to the millisecond-timestamp string
   * format the GWT/J2CL reader path expects for `task/dueTs`.
   *
   * <ul>
   *   <li>{@code null} or blank → empty string (unset sentinel).
   *   <li>All-digit string → returned as-is (already an epoch-millis
   *       value from a legacy or explicit-timestamp caller).
   *   <li>{@code YYYY-MM-DD} (with optional trailing time component
   *       separated by `T`) → parsed as UTC midnight on that calendar
   *       date and serialised as decimal millis since 1970-01-01Z.
   *   <li>Anything else → empty string (the reader treats
   *       unparseable annotation values as "unknown due date" today,
   *       so coercing here matches the existing graceful-degradation
   *       contract instead of writing a value that always reads as
   *       unknown after refresh).
   * </ul>
   */
  static String normalizeDueTimestamp(String rawDue) {
    if (rawDue == null) {
      return "";
    }
    String trimmed = rawDue.trim();
    if (trimmed.isEmpty()) {
      return "";
    }
    if (isAllDigits(trimmed)) {
      return trimmed;
    }
    String datePart = trimmed;
    int tIndex = datePart.indexOf('T');
    if (tIndex >= 0) {
      datePart = datePart.substring(0, tIndex);
    }
    if (datePart.length() != 10
        || datePart.charAt(4) != '-'
        || datePart.charAt(7) != '-') {
      return "";
    }
    int year = parsePositiveInt(datePart.substring(0, 4));
    int month = parsePositiveInt(datePart.substring(5, 7));
    int day = parsePositiveInt(datePart.substring(8, 10));
    if (year < 0 || month < 1 || month > 12 || day < 1 || day > 31) {
      return "";
    }
    long epochDays = computeEpochDays(year, month, day);
    if (epochDays == Long.MIN_VALUE) {
      return "";
    }
    long millis = epochDays * 86_400_000L;
    return Long.toString(millis);
  }

  private static boolean isAllDigits(String value) {
    if (value.isEmpty()) {
      return false;
    }
    int start = value.charAt(0) == '-' ? 1 : 0;
    if (start == value.length()) {
      return false;
    }
    for (int i = start; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  private static int parsePositiveInt(String value) {
    int result = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        return -1;
      }
      result = result * 10 + (c - '0');
    }
    return result;
  }

  /**
   * Days from 1970-01-01 (UTC) to the given proleptic-Gregorian date
   * using Howard Hinnant's `days_from_civil` algorithm. Returns
   * {@link Long#MIN_VALUE} when the day number is invalid for the
   * supplied month (e.g. day=31 in April, day=29 in a non-leap
   * February). The algorithm itself accepts any
   * (year, month, day-in-1..31) triple but does not validate the
   * day-in-month bound; we re-derive the calendar date and reject
   * mismatches to avoid writing a silently-shifted timestamp.
   */
  private static long computeEpochDays(int year, int month, int day) {
    int adjustedYear = month <= 2 ? year - 1 : year;
    int era = (adjustedYear >= 0 ? adjustedYear : adjustedYear - 399) / 400;
    int yoe = adjustedYear - era * 400;
    int monthOffset = month + (month > 2 ? -3 : 9);
    int doy = (153 * monthOffset + 2) / 5 + day - 1;
    int doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    long epochDays = (long) era * 146097L + doe - 719468L;
    if (!matchesCivilDate(epochDays, year, month, day)) {
      return Long.MIN_VALUE;
    }
    return epochDays;
  }

  private static boolean matchesCivilDate(long epochDays, int year, int month, int day) {
    // Inverse of computeEpochDays — derive (y,m,d) from the day number
    // and compare to detect day-of-month overflow (April 31, Feb 29 in
    // non-leap years, etc.).
    long z = epochDays + 719468L;
    long era = (z >= 0 ? z : z - 146096L) / 146097L;
    long doe = z - era * 146097L;
    long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    long y = yoe + era * 400L;
    long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    long mp = (5 * doy + 2) / 153;
    long d = doy - (153 * mp + 2) / 5 + 1;
    long m = mp + (mp < 10 ? 3 : -9);
    long civilYear = y + (m <= 2 ? 1 : 0);
    return civilYear == year && m == month && d == day;
  }

  /**
   * Shared helper that builds a delta whose ops set 1+ annotations on
   * the entire body of the named blip. Used by both task-toggle and
   * task-metadata writes; future S* slices can reuse the helper for
   * other blip-level annotation flows (e.g. reactions, read-state)
   * without re-deriving the annotation shape.
   */
  private SidecarSubmitRequest buildBlipAnnotationRequest(
      String address,
      J2clSidecarWriteSession session,
      String blipId,
      String[] keys,
      String[] values) {
    requirePresent(session, "Missing write session.");
    if (keys == null || values == null || keys.length != values.length || keys.length == 0) {
      throw new IllegalArgumentException("Mismatched annotation keys/values.");
    }
    String normalizedAddress = normalizeAddress(address);
    extractDomain(normalizedAddress);
    String selectedWaveId =
        requireNonEmpty(session.getSelectedWaveId(), "Missing selected wave id.");
    String historyHash =
        requireNonEmpty(session.getHistoryHash(), "Missing write-session history hash.");
    String channelId = requireNonEmpty(session.getChannelId(), "Missing write-session channel id.");
    String documentId = requireNonEmpty(blipId, "Missing blip id.");
    long baseVersion = session.getBaseVersion();
    if (baseVersion < 0) {
      throw new IllegalArgumentException("Invalid write-session base version.");
    }
    StringBuilder components = new StringBuilder();
    components.append("{\"1\":{\"3\":[");
    for (int i = 0; i < keys.length; i++) {
      if (i > 0) components.append(",");
      components
          .append("{\"1\":\"")
          .append(escapeJson(keys[i]))
          .append("\",\"3\":\"")
          .append(escapeJson(values[i]))
          .append("\"}");
    }
    components.append("]}}");
    appendComponentSeparator(components);
    // No characters in between — the annotation brackets the empty
    // body span. The supplement live-update interprets a no-text
    // boundary as "apply this annotation to the whole body". Mirror
    // the GWT supplement-writer shape here so the read-side parity
    // assertion holds.
    components.append("{\"1\":{\"2\":[");
    for (int i = 0; i < keys.length; i++) {
      if (i > 0) components.append(",");
      components.append("\"").append(escapeJson(keys[i])).append("\"");
    }
    components.append("]}}");
    StringBuilder operation = new StringBuilder(components.length() + documentId.length() + 32);
    operation
        .append("{\"3\":{\"1\":\"")
        .append(escapeJson(documentId))
        .append("\",\"2\":{\"1\":[")
        .append(components)
        .append("]}}}");
    String deltaJson =
        buildDeltaJson(baseVersion, historyHash, normalizedAddress, operation.toString());
    return new SidecarSubmitRequest(buildWaveletName(selectedWaveId), deltaJson, channelId);
  }

  public SidecarSubmitRequest createReplyRequest(
      String address, J2clSidecarWriteSession session, J2clComposerDocument document) {
    requirePresent(session, "Missing write session.");
    requirePresent(document, "Missing composer document.");
    String normalizedAddress = normalizeAddress(address);
    extractDomain(normalizedAddress);
    String selectedWaveId =
        requireNonEmpty(session.getSelectedWaveId(), "Missing selected wave id.");
    String historyHash =
        requireNonEmpty(session.getHistoryHash(), "Missing write-session history hash.");
    String channelId = requireNonEmpty(session.getChannelId(), "Missing write-session channel id.");
    long baseVersion = session.getBaseVersion();
    if (baseVersion < 0) {
      throw new IllegalArgumentException("Invalid write-session base version.");
    }
    String replyBlipId = nextToken("b+");
    String deltaJson =
        buildDeltaJson(
            baseVersion,
            historyHash,
            normalizedAddress,
            buildDocumentOperation(replyBlipId, document));
    return new SidecarSubmitRequest(
        buildWaveletName(selectedWaveId), deltaJson, channelId);
  }

  private String buildDocumentOperation(String documentId, J2clComposerDocument document) {
    StringBuilder components = new StringBuilder();
    for (J2clComposerDocument.Component component : document.getComponents()) {
      switch (component.type) {
        case TEXT:
          appendComponentSeparator(components);
          appendCharacters(components, component.text);
          break;
        case ANNOTATED_TEXT:
          appendComponentSeparator(components);
          appendAnnotationStart(components, component.annotationKey, component.annotationValue);
          appendComponentSeparator(components);
          appendCharacters(components, component.text);
          appendComponentSeparator(components);
          appendAnnotationEnd(components, component.annotationKey);
          break;
        case IMAGE_ATTACHMENT:
          appendComponentSeparator(components);
          appendImageAttachment(components, component);
          break;
      }
    }
    StringBuilder operation = new StringBuilder(components.length() + documentId.length() + 32);
    operation
        .append("{\"3\":{\"1\":\"")
        .append(escapeJson(documentId))
        .append("\",\"2\":{\"1\":[")
        .append(components)
        .append("]}}}");
    return operation.toString();
  }

  private void appendImageAttachment(
      StringBuilder components, J2clComposerDocument.Component component) {
    components
        .append("{\"3\":{\"1\":\"image\",\"2\":[{\"1\":\"attachment\",\"2\":\"")
        .append(escapeJson(component.attachmentId))
        .append("\"},{\"1\":\"display-size\",\"2\":\"")
        .append(escapeJson(component.displaySize))
        .append("\"}]}}");
    appendComponentSeparator(components);
    components
        .append("{\"3\":{\"1\":\"caption\"}}");
    if (!component.text.isEmpty()) {
      appendComponentSeparator(components);
      appendCharacters(components, component.text);
    }
    appendComponentSeparator(components);
    appendElementEnd(components);
    appendComponentSeparator(components);
    appendElementEnd(components);
  }

  private static String buildAddParticipantOperation(String address) {
    return "{\"1\":\"" + escapeJson(address) + "\"}";
  }

  private static void appendComponentSeparator(StringBuilder builder) {
    if (builder.length() > 0) {
      builder.append(",");
    }
  }

  private static void appendCharacters(StringBuilder builder, String text) {
    builder.append("{\"2\":\"").append(escapeJson(text)).append("\"}");
  }

  private static void appendAnnotationStart(StringBuilder builder, String key, String value) {
    builder
        .append("{\"1\":{\"3\":[{\"1\":\"")
        .append(escapeJson(key))
        .append("\",\"3\":\"")
        .append(escapeJson(value))
        .append("\"}]}}");
  }

  private static void appendAnnotationEnd(StringBuilder builder, String key) {
    builder.append("{\"1\":{\"2\":[\"").append(escapeJson(key)).append("\"]}}");
  }

  private static void appendElementEnd(StringBuilder builder) {
    builder.append("{\"4\":true}");
  }

  private String buildDeltaJson(
      long baseVersion, String historyHash, String address, String operationsJson) {
    return "{\"1\":{\"1\":"
        + baseVersion
        + ",\"2\":\""
        + escapeJson(historyHash)
        + "\"},\"2\":\""
        + escapeJson(address)
        + "\",\"3\":["
        + operationsJson
        + "]}";
  }

  private String buildWaveletName(String waveId) {
    int separator = waveId.indexOf('/');
    if (separator <= 0 || separator >= waveId.length() - 1) {
      throw new IllegalArgumentException("Invalid wave id: " + waveId);
    }
    return waveId.substring(0, separator)
        + "/"
        + waveId.substring(separator + 1)
        + "/~/conv+root";
  }

  private static <T> T requirePresent(T value, String message) {
    if (value == null) {
      throw new IllegalArgumentException(message);
    }
    return value;
  }

  private static String requireNonEmpty(String value, String message) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return value.trim();
  }

  private static String normalizeAddress(String address) {
    if (address == null) {
      throw new IllegalArgumentException("Missing session address for sidecar submit.");
    }
    String trimmed = address.trim().toLowerCase(Locale.ROOT);
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("Missing session address for sidecar submit.");
    }
    return trimmed;
  }

  private static String extractDomain(String address) {
    int at = address.indexOf('@');
    if (at < 1 || at == address.length() - 1 || address.indexOf('@', at + 1) >= 0) {
      throw new IllegalArgumentException("Invalid participant address: " + address);
    }
    String domain = address.substring(at + 1);
    if (domain.indexOf('/') >= 0) {
      throw new IllegalArgumentException("Invalid participant address: " + address);
    }
    return domain;
  }

  private static String buildVersionZeroHistoryHash(String domain, String waveToken) {
    return encodeHex("wave://" + domain + "/" + waveToken + "/conv+root");
  }

  private static String encodeHex(String value) {
    // Version-zero wave URIs are ASCII-only; this mirrors the existing plain-text factory.
    StringBuilder encoded = new StringBuilder(value.length() * 2);
    for (int i = 0; i < value.length(); i++) {
      int ch = value.charAt(i);
      encoded.append(toHexDigit((ch >> 4) & 0xF));
      encoded.append(toHexDigit(ch & 0xF));
    }
    return encoded.toString();
  }

  private static char toHexDigit(int value) {
    return (char) (value < 10 ? ('0' + value) : ('A' + (value - 10)));
  }

  private String nextToken(String prefix) {
    return prefix + sessionSeed + base64Encode(counter++);
  }

  private static String sanitizeSeed(String rawSeed) {
    if (rawSeed == null || rawSeed.isEmpty()) {
      return "j2cl";
    }
    StringBuilder sanitized = new StringBuilder(rawSeed.length());
    for (int i = 0; i < rawSeed.length(); i++) {
      char c = rawSeed.charAt(i);
      if ((c >= 'A' && c <= 'Z')
          || (c >= 'a' && c <= 'z')
          || (c >= '0' && c <= '9')
          || c == '-'
          || c == '_') {
        sanitized.append(c);
      }
    }
    return sanitized.length() == 0 ? "j2cl" : sanitized.toString();
  }

  private static String base64Encode(int intValue) {
    if (intValue == 0) {
      return "A";
    }
    int numEncodedBytes = (int) Math.ceil((32 - Integer.numberOfLeadingZeros(intValue)) / 6.0);
    StringBuilder encoded = new StringBuilder(numEncodedBytes);
    // Encode the highest non-zero 6-bit group first, then fall through for lower groups.
    switch (numEncodedBytes) {
      case 6:
        encoded.append(WEB64_ALPHABET[(intValue >> 30) & 0x3F]);
        // fall through
      case 5:
        encoded.append(WEB64_ALPHABET[(intValue >> 24) & 0x3F]);
        // fall through
      case 4:
        encoded.append(WEB64_ALPHABET[(intValue >> 18) & 0x3F]);
        // fall through
      case 3:
        encoded.append(WEB64_ALPHABET[(intValue >> 12) & 0x3F]);
        // fall through
      case 2:
        encoded.append(WEB64_ALPHABET[(intValue >> 6) & 0x3F]);
        // fall through
      default:
        encoded.append(WEB64_ALPHABET[intValue & 0x3F]);
    }
    return encoded.toString();
  }

  private static String escapeJson(String value) {
    StringBuilder escaped = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '"':
          escaped.append("\\\"");
          break;
        case '\\':
          escaped.append("\\\\");
          break;
        case '\b':
          escaped.append("\\b");
          break;
        case '\f':
          escaped.append("\\f");
          break;
        case '\n':
          escaped.append("\\n");
          break;
        case '\r':
          escaped.append("\\r");
          break;
        case '\t':
          escaped.append("\\t");
          break;
        default:
          if (c < 0x20) {
            escaped.append("\\u00");
            String hex = Integer.toHexString(c);
            if (hex.length() == 1) {
              escaped.append('0');
            }
            escaped.append(hex);
          } else {
            escaped.append(c);
          }
      }
    }
    return escaped.toString();
  }
}
