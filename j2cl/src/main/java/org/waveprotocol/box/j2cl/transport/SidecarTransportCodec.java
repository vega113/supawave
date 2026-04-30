package org.waveprotocol.box.j2cl.transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.j2cl.search.SidecarSearchResponse;

public final class SidecarTransportCodec {
  private SidecarTransportCodec() {
  }

  public static String encodeOpenEnvelope(int sequenceNumber, SidecarOpenRequest request) {
    StringBuilder json = new StringBuilder(128);
    json.append("{\"sequenceNumber\":")
        .append(sequenceNumber)
        .append(",\"messageType\":\"ProtocolOpenRequest\",\"message\":{\"1\":\"")
        .append(escapeJson(request.getParticipantId()))
        .append("\",\"2\":\"")
        .append(escapeJson(request.getWaveId()))
        .append("\",\"3\":[");
    List<String> prefixes = request.getWaveletIdPrefixes();
    for (int i = 0; i < prefixes.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append('"').append(escapeJson(prefixes.get(i))).append('"');
    }
    json.append(']');
    SidecarViewportHints hints = request.getViewportHints();
    if (hints.getStartBlipId() != null) {
      json.append(",\"5\":\"").append(escapeJson(hints.getStartBlipId())).append('"');
    }
    if (hints.getDirection() != null) {
      json.append(",\"6\":\"").append(escapeJson(hints.getDirection())).append('"');
    }
    if (hints.getLimit() != null) {
      json.append(",\"7\":").append(hints.getLimit().intValue());
    }
    json.append("}}");
    return json.toString();
  }

  public static String encodeSubmitEnvelope(int sequenceNumber, SidecarSubmitRequest request) {
    StringBuilder json = new StringBuilder(request.getDeltaJson().length() + 96);
    json.append("{\"sequenceNumber\":")
        .append(sequenceNumber)
        .append(",\"messageType\":\"ProtocolSubmitRequest\",\"message\":{\"1\":\"")
        .append(escapeJson(request.getWaveletName()))
        .append("\",\"2\":")
        .append(request.getDeltaJson());
    if (request.getChannelId() != null) {
      json.append(",\"3\":\"").append(escapeJson(request.getChannelId())).append('"');
    }
    json.append("}}");
    return json.toString();
  }

  public static SidecarSearchResponse decodeSearchResponse(String json) {
    Map<String, Object> root = parseJsonObject(json);
    List<SidecarSearchResponse.Digest> digests = new ArrayList<>();
    Object digestValue = root.get("3");
    if (digestValue != null) {
      for (Object rawDigest : asList(digestValue)) {
        Map<String, Object> digest = asObject(rawDigest);
        digests.add(
            new SidecarSearchResponse.Digest(
                getString(digest, "1"),
                getString(digest, "2"),
                getString(digest, "3"),
                getLong(digest, "4"),
                getInt(digest, "5"),
                getInt(digest, "6"),
                getStringList(digest, "7"),
                getString(digest, "8"),
                getBoolean(digest, "9")));
      }
    }
    return new SidecarSearchResponse(getString(root, "1"), getInt(root, "2"), digests);
  }

  public static SidecarWaveletUpdateSummary decodeWaveletUpdate(String json) {
    Map<String, Object> envelope = parseJsonObject(json);
    Map<String, Object> payload = asObject(envelope.get("message"));
    return new SidecarWaveletUpdateSummary(
        getInt(envelope, "sequenceNumber"),
        getString(payload, "1"),
        getArrayLength(payload.get("2")),
        getBoolean(payload, "6"),
        getString(payload, "7"));
  }

  public static SidecarSelectedWaveUpdate decodeSelectedWaveUpdate(String json) {
    return decodeSelectedWaveUpdate(parseJsonObject(json));
  }

  public static SidecarSelectedWaveUpdate decodeSelectedWaveUpdate(Map<String, Object> envelope) {
    Map<String, Object> payload = asObject(envelope.get("message"));
    Map<String, Object> resultingVersion = getOptionalObject(payload, "4");
    Map<String, Object> snapshot = getOptionalObject(payload, "5");
    List<String> participantIds = getStringList(snapshot, "2");
    List<SidecarSelectedWaveDocument> documents = new ArrayList<SidecarSelectedWaveDocument>();
    SidecarConversationManifest conversationManifest = SidecarConversationManifest.empty();
    Object rawDocuments = snapshot.get("3");
    if (rawDocuments != null) {
      for (Object rawDocument : asList(rawDocuments)) {
        Map<String, Object> document = asObject(rawDocument);
        String documentId = getString(document, "1");
        Map<String, Object> documentOperation = getOptionalObject(document, "2");
        DocumentExtraction extraction = extractDocument(documentOperation, documentId);
        documents.add(
            new SidecarSelectedWaveDocument(
                documentId,
                getString(document, "3"),
                getLong(document, "5"),
                getLong(document, "6"),
                extraction.textContent,
                extraction.bodyItemCount,
                extraction.annotationRanges,
                extraction.reactionEntries,
                extraction.lockState));
        if ("conversation".equals(documentId) && conversationManifest.isEmpty()) {
          conversationManifest = extractConversationManifest(documentOperation);
        }
      }
    }

    Map<String, Object> fragments = getOptionalObject(payload, "8");
    List<SidecarSelectedWaveFragmentRange> ranges =
        new ArrayList<SidecarSelectedWaveFragmentRange>();
    Object rawRanges = fragments.get("4");
    if (rawRanges != null) {
      for (Object rawRange : asList(rawRanges)) {
        Map<String, Object> range = asObject(rawRange);
        ranges.add(
            new SidecarSelectedWaveFragmentRange(
                getString(range, "1"), getLong(range, "2"), getLong(range, "3")));
      }
    }

    List<SidecarSelectedWaveFragment> entries =
        new ArrayList<SidecarSelectedWaveFragment>();
    Object rawEntries = fragments.get("5");
    if (rawEntries != null) {
      for (Object rawEntry : asList(rawEntries)) {
        Map<String, Object> entry = asObject(rawEntry);
        Map<String, Object> entrySnapshot = getOptionalObject(entry, "2");
        entries.add(
            new SidecarSelectedWaveFragment(
                getString(entry, "1"),
                getString(entrySnapshot, "1"),
                getArrayLength(entry.get("3")),
                getArrayLength(entry.get("4"))));
      }
    }

    return new SidecarSelectedWaveUpdate(
        getInt(envelope, "sequenceNumber"),
        getString(payload, "1"),
        getBoolean(payload, "6"),
        getString(payload, "7"),
        getOptionalLong(resultingVersion, "1", -1L),
        getString(resultingVersion, "2"),
        participantIds,
        documents,
        new SidecarSelectedWaveFragments(
            getOptionalLong(fragments, "1", -1L),
            getOptionalLong(fragments, "2", 0L),
            getOptionalLong(fragments, "3", 0L),
            ranges,
            entries),
        conversationManifest);
  }

  /**
   * J-UI-4 (#1082, R-3.1) — parses the {@code conversation} manifest
   * document operation into a depth-first pre-order
   * {@link SidecarConversationManifest}.
   *
   * <p>The manifest XML is a tree of {@code <thread id="…">} and
   * {@code <blip id="b+…"/>} elements. The codec walks the document
   * operation components (element-start / element-end), maintaining
   * a stack of open threads + the most-recent blip on each thread,
   * and emits one {@link SidecarConversationManifest.Entry} per
   * {@code <blip>}.
   *
   * <p>Unknown tags are ignored. {@code <blip>} elements without an
   * {@code id} attribute or with an empty id are skipped silently —
   * the renderer simply has no manifest entry for them and falls
   * back to flat rendering for that blip.
   */
  static SidecarConversationManifest extractConversationManifest(
      Map<String, Object> documentOperation) {
    if (documentOperation == null || documentOperation.isEmpty()) {
      return SidecarConversationManifest.empty();
    }
    Object rawComponents = documentOperation.get("1");
    if (rawComponents == null) {
      return SidecarConversationManifest.empty();
    }
    List<SidecarConversationManifest.Entry> entries =
        new ArrayList<SidecarConversationManifest.Entry>();
    // Stack of currently-open <thread> ids (top = innermost). Empty
    // string is used when a <thread> element has no id attribute.
    List<String> threadStack = new ArrayList<String>();
    // Per-open-thread: most recently encountered <blip> in the same
    // thread. Used as the parent-blip for any reply <thread> that
    // opens before the next sibling blip on the same thread.
    List<String> mostRecentBlipPerThread = new ArrayList<String>();
    // Per-open-thread sibling counter (parallel to threadStack so
    // re-used thread ids in different subtrees do not collide —
    // review-1089 round-1: a `<thread id="t+a">` nested under one
    // blip and a sibling `<thread id="t+a">` under a different blip
    // each get their own counter).
    List<Integer> siblingCounterStack = new ArrayList<Integer>();
    // Stack of element types (lowercase). Used so we know which
    // mirror state to pop on element-end.
    List<String> elementStack = new ArrayList<String>();
    List<Integer> openBlipEntryIndexStack = new ArrayList<Integer>();
    int itemPosition = 0;

    for (Object rawComponent : asList(rawComponents)) {
      Map<String, Object> component = asObject(rawComponent);
      if (component.containsKey("3")) {
        Map<String, Object> elementStart = getOptionalObject(component, "3");
        String type = getString(elementStart, "1");
        String safeType = type == null ? "" : type;
        elementStack.add(safeType);
        if ("thread".equals(safeType)) {
          String threadId = getAttribute(elementStart, "id");
          threadStack.add(threadId == null ? "" : threadId);
          mostRecentBlipPerThread.add("");
          siblingCounterStack.add(Integer.valueOf(0));
        } else if ("blip".equals(safeType)) {
          String blipId = getAttribute(elementStart, "id");
          if (blipId == null || blipId.isEmpty()) {
            elementStack.set(elementStack.size() - 1, "ignored-blip");
            itemPosition++;
            continue;
          }
          String threadId = threadStack.isEmpty() ? "" : threadStack.get(threadStack.size() - 1);
          int depth = Math.max(0, threadStack.size() - 1);
          // The blip's parent is the most recent blip on the
          // *enclosing* thread (i.e. the blip whose reply <thread>
          // we are currently inside). For the outermost thread, this
          // is empty.
          String parentBlipId = "";
          if (threadStack.size() >= 2) {
            parentBlipId = mostRecentBlipPerThread.get(mostRecentBlipPerThread.size() - 2);
          }
          int siblingIndex = 0;
          if (!siblingCounterStack.isEmpty()) {
            siblingIndex = siblingCounterStack.get(siblingCounterStack.size() - 1).intValue();
            siblingCounterStack.set(
                siblingCounterStack.size() - 1, Integer.valueOf(siblingIndex + 1));
          }
          int entryIndex = entries.size();
          entries.add(
              new SidecarConversationManifest.Entry(
                  blipId, parentBlipId, threadId, depth, siblingIndex));
          openBlipEntryIndexStack.add(Integer.valueOf(entryIndex));
          if (!mostRecentBlipPerThread.isEmpty()) {
            mostRecentBlipPerThread.set(mostRecentBlipPerThread.size() - 1, blipId);
          }
        }
        itemPosition++;
      } else if (component.containsKey("4")) {
        if (elementStack.isEmpty()) {
          itemPosition++;
          continue;
        }
        String ended = elementStack.remove(elementStack.size() - 1);
        if ("thread".equals(ended)) {
          if (!threadStack.isEmpty()) {
            threadStack.remove(threadStack.size() - 1);
          }
          if (!mostRecentBlipPerThread.isEmpty()) {
            mostRecentBlipPerThread.remove(mostRecentBlipPerThread.size() - 1);
          }
          if (!siblingCounterStack.isEmpty()) {
            siblingCounterStack.remove(siblingCounterStack.size() - 1);
          }
        } else if ("blip".equals(ended)) {
          if (!openBlipEntryIndexStack.isEmpty()) {
            int entryIndex =
                openBlipEntryIndexStack.remove(openBlipEntryIndexStack.size() - 1).intValue();
            setReplyInsertPosition(entries, entryIndex, itemPosition);
          }
        }
        itemPosition++;
      } else if (component.containsKey("2")) {
        String chars = getString(component, "2");
        itemPosition += chars == null ? 0 : chars.length();
      } else if (component.containsKey("5")) {
        itemPosition += Math.max(0, getInt(component, "5"));
      }
    }
    return SidecarConversationManifest.of(entries, itemPosition);
  }

  private static void setReplyInsertPosition(
      List<SidecarConversationManifest.Entry> entries, int index, int position) {
    if (entries == null || index < 0 || index >= entries.size()) {
      return;
    }
    SidecarConversationManifest.Entry entry = entries.get(index);
    entries.set(
        index,
        new SidecarConversationManifest.Entry(
            entry.getBlipId(),
            entry.getParentBlipId(),
            entry.getThreadId(),
            entry.getDepth(),
            entry.getSiblingIndex(),
            position));
  }

  public static SidecarSelectedWaveReadState decodeSelectedWaveReadState(String json) {
    Map<String, Object> root = parseJsonObject(json);
    String waveId = getString(root, "waveId");
    int unreadCount = getInt(root, "unreadCount");
    boolean read = getBoolean(root, "isRead");
    return new SidecarSelectedWaveReadState(waveId, unreadCount, read);
  }

  public static boolean decodeRpcFinishedFailed(Map<String, Object> envelope) {
    Map<String, Object> payload = asObject(envelope.get("message"));
    return getBoolean(payload, "1");
  }

  public static String decodeRpcFinishedErrorText(Map<String, Object> envelope, String fallback) {
    Map<String, Object> payload = asObject(envelope.get("message"));
    String errorText = getString(payload, "2");
    return errorText == null || errorText.isEmpty() ? fallback : errorText;
  }

  public static SidecarSubmitResponse decodeSubmitResponse(Map<String, Object> envelope) {
    Map<String, Object> payload = asObject(envelope.get("message"));
    Map<String, Object> hashedVersion = getOptionalObject(payload, "3");
    long resultingVersion = getOptionalLong(hashedVersion, "1", -1L);
    return new SidecarSubmitResponse(
        getInt(payload, "1"), getString(payload, "2"), resultingVersion);
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
            escaped.append("\\u");
            appendHex4(escaped, c);
          } else {
            escaped.append(c);
          }
      }
    }
    return escaped.toString();
  }

  public static String decodeMessageType(String json) {
    return getString(parseJsonObject(json), "messageType");
  }

  public static Map<String, Object> parseJsonObject(String json) {
    JsonParser parser = new JsonParser(json);
    Map<String, Object> result = asObject(parser.parseValue());
    parser.ensureFullyConsumed();
    return result;
  }

  public static String parseJsonString(String json) {
    JsonParser parser = new JsonParser(json);
    Object value = parser.parseValue();
    parser.ensureFullyConsumed();
    if (!(value instanceof String)) {
      throw new IllegalArgumentException("Expected string but got " + value);
    }
    return (String) value;
  }

  private static Map<String, Object> asObject(Object value) {
    if (!(value instanceof Map)) {
      throw new IllegalArgumentException("Expected object but got " + value);
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> object = (Map<String, Object>) value;
    return object;
  }

  private static List<Object> asList(Object value) {
    if (!(value instanceof List)) {
      throw new IllegalArgumentException("Expected array but got " + value);
    }
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) value;
    return list;
  }

  private static String getString(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value == null ? null : String.valueOf(value);
  }

  private static Map<String, Object> getOptionalObject(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value == null ? new LinkedHashMap<String, Object>() : asObject(value);
  }

  private static int getInt(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value == null ? 0 : requireIntValue(value, key);
  }

  private static boolean getBoolean(Map<String, Object> object, String key) {
    Object value = object.get(key);
    return value != null && Boolean.TRUE.equals(value);
  }

  private static long getLong(Map<String, Object> object, String key) {
    Object value = object.get(key);
    if (value == null) {
      return 0L;
    }
    if (value instanceof Number) {
      return requireIntegralLong((Number) value, key);
    }
    List<Object> words = asList(value);
    int lowWord = requireIntValue(words.get(0), key + "[0]");
    int highWord = requireIntValue(words.get(1), key + "[1]");
    return toLong(highWord, lowWord);
  }

  private static long getOptionalLong(Map<String, Object> object, String key, long missingValue) {
    return object.containsKey(key) ? getLong(object, key) : missingValue;
  }

  private static int requireIntValue(Object value, String key) {
    if (!(value instanceof Number)) {
      throw new IllegalArgumentException("Expected numeric value for " + key + " but got " + value);
    }
    long integral = requireIntegralLong((Number) value, key);
    if (integral < Integer.MIN_VALUE || integral > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Expected int-range value for " + key + " but got " + value);
    }
    return (int) integral;
  }

  private static long requireIntegralLong(Number value, String key) {
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      return value.longValue();
    }
    double candidate = value.doubleValue();
    if (!Double.isFinite(candidate)) {
      throw new IllegalArgumentException("Expected finite numeric value for " + key + " but got " + value);
    }
    long integral = (long) candidate;
    if ((double) integral != candidate) {
      throw new IllegalArgumentException("Expected integral numeric value for " + key + " but got " + value);
    }
    return integral;
  }

  private static List<String> getStringList(Map<String, Object> object, String key) {
    List<String> values = new ArrayList<>();
    Object value = object.get(key);
    if (value == null) {
      return values;
    }
    for (Object rawValue : asList(value)) {
      values.add(String.valueOf(rawValue));
    }
    return values;
  }

  private static int getArrayLength(Object value) {
    if (value == null) {
      return 0;
    }
    return asList(value).size();
  }

  private static DocumentExtraction extractDocument(
      Map<String, Object> documentOperation, String documentId) {
    Object rawComponents = documentOperation.get("1");
    if (rawComponents == null) {
      return new DocumentExtraction(
          "",
          0,
          new ArrayList<SidecarAnnotationRange>(),
          new ArrayList<SidecarReactionEntry>(),
          SidecarSelectedWaveDocument.LOCK_STATE_UNLOCKED);
    }
    StringBuilder text = new StringBuilder();
    int bodyItemCount = 0;
    String lockState = SidecarSelectedWaveDocument.LOCK_STATE_UNLOCKED;
    Map<String, ActiveAnnotation> activeAnnotations =
        new LinkedHashMap<String, ActiveAnnotation>();
    List<SidecarAnnotationRange> annotationRanges = new ArrayList<SidecarAnnotationRange>();
    Map<String, List<String>> reactionAddressesByEmoji = new LinkedHashMap<String, List<String>>();
    List<String> elementStack = new ArrayList<String>();
    String currentReactionEmoji = null;

    for (Object rawComponent : asList(rawComponents)) {
      Map<String, Object> component = asObject(rawComponent);
      if (component.containsKey("1")) {
        processAnnotationBoundary(
            getOptionalObject(component, "1"),
            text.length(),
            activeAnnotations,
            annotationRanges);
        continue;
      }
      if (component.containsKey("2")) {
        String characters = getString(component, "2");
        if (characters != null) {
          text.append(characters);
          bodyItemCount += characters.length();
        }
        continue;
      }
      if (component.containsKey("3")) {
        bodyItemCount++;
        Map<String, Object> elementStart = getOptionalObject(component, "3");
        String type = getString(elementStart, "1");
        elementStack.add(type == null ? "" : type);
        if ("reaction".equals(type)) {
          currentReactionEmoji = getAttribute(elementStart, "emoji");
          if (currentReactionEmoji != null
              && !reactionAddressesByEmoji.containsKey(currentReactionEmoji)) {
            reactionAddressesByEmoji.put(currentReactionEmoji, new ArrayList<String>());
          }
        } else if ("user".equals(type) && currentReactionEmoji != null) {
          String address = getAttribute(elementStart, "address");
          List<String> reactionAddresses = reactionAddressesByEmoji.get(currentReactionEmoji);
          if (address != null
              && !address.isEmpty()
              && reactionAddresses != null
              && !reactionAddresses.contains(address)) {
            reactionAddresses.add(address);
          }
        } else if ("m/lock".equals(documentId) && "lock".equals(type)) {
          lockState =
              SidecarSelectedWaveDocument.normalizeLockState(getAttribute(elementStart, "mode"));
        }
        if ("line".equals(type) && text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
          text.append('\n');
        }
        continue;
      }
      if (component.containsKey("4")) {
        bodyItemCount++;
        if (!elementStack.isEmpty()) {
          String ended = elementStack.remove(elementStack.size() - 1);
          if ("reaction".equals(ended)) {
            currentReactionEmoji = null;
          }
        }
      }
    }
    closeAllAnnotations(text.length(), activeAnnotations, annotationRanges);
    return new DocumentExtraction(
        text.toString(),
        bodyItemCount,
        annotationRanges,
        extractReactionEntries(documentId, reactionAddressesByEmoji),
        lockState);
  }

  private static void processAnnotationBoundary(
      Map<String, Object> boundary,
      int offset,
      Map<String, ActiveAnnotation> activeAnnotations,
      List<SidecarAnnotationRange> annotationRanges) {
    Object rawEnds = boundary.get("2");
    if (rawEnds != null) {
      for (Object rawEnd : asList(rawEnds)) {
        closeAnnotation(String.valueOf(rawEnd), offset, activeAnnotations, annotationRanges);
      }
    }
    Object rawChanges = boundary.get("3");
    if (rawChanges == null) {
      return;
    }
    for (Object rawChange : asList(rawChanges)) {
      Map<String, Object> change = asObject(rawChange);
      String key = getString(change, "1");
      if (key == null || key.isEmpty()) {
        continue;
      }
      if (activeAnnotations.containsKey(key)) {
        closeAnnotation(key, offset, activeAnnotations, annotationRanges);
      }
      String newValue = getString(change, "3");
      if (newValue != null) {
        activeAnnotations.put(key, new ActiveAnnotation(newValue, offset));
      }
    }
  }

  private static void closeAllAnnotations(
      int offset,
      Map<String, ActiveAnnotation> activeAnnotations,
      List<SidecarAnnotationRange> annotationRanges) {
    List<String> activeKeys = new ArrayList<String>(activeAnnotations.keySet());
    for (String key : activeKeys) {
      closeAnnotation(key, offset, activeAnnotations, annotationRanges);
    }
  }

  private static void closeAnnotation(
      String key,
      int offset,
      Map<String, ActiveAnnotation> activeAnnotations,
      List<SidecarAnnotationRange> annotationRanges) {
    ActiveAnnotation active = activeAnnotations.remove(key);
    if (active == null || offset <= active.startOffset) {
      return;
    }
    annotationRanges.add(
        new SidecarAnnotationRange(key, active.value, active.startOffset, offset));
  }

  private static String getAttribute(Map<String, Object> elementStart, String name) {
    Object rawAttributes = elementStart.get("2");
    if (rawAttributes == null) {
      return null;
    }
    for (Object rawAttribute : asList(rawAttributes)) {
      Map<String, Object> attribute = asObject(rawAttribute);
      if (name.equals(getString(attribute, "1"))) {
        return getString(attribute, "2");
      }
    }
    return null;
  }

  private static List<SidecarReactionEntry> extractReactionEntries(
      String documentId, Map<String, List<String>> reactionAddressesByEmoji) {
    if (documentId == null || !documentId.startsWith("react+")) {
      return new ArrayList<SidecarReactionEntry>();
    }
    List<SidecarReactionEntry> entries = new ArrayList<SidecarReactionEntry>();
    for (Map.Entry<String, List<String>> entry : reactionAddressesByEmoji.entrySet()) {
      if (entry.getKey() == null
          || entry.getKey().isEmpty()
          || entry.getValue() == null
          || entry.getValue().isEmpty()) {
        continue;
      }
      entries.add(new SidecarReactionEntry(entry.getKey(), entry.getValue()));
    }
    return entries;
  }

  private static final class ActiveAnnotation {
    private final String value;
    private final int startOffset;

    ActiveAnnotation(String value, int startOffset) {
      this.value = value;
      this.startOffset = startOffset;
    }
  }

  private static final class DocumentExtraction {
    private final String textContent;
    private final int bodyItemCount;
    private final List<SidecarAnnotationRange> annotationRanges;
    private final List<SidecarReactionEntry> reactionEntries;
    private final String lockState;

    DocumentExtraction(
        String textContent,
        int bodyItemCount,
        List<SidecarAnnotationRange> annotationRanges,
        List<SidecarReactionEntry> reactionEntries,
        String lockState) {
      this.textContent = textContent;
      this.bodyItemCount = bodyItemCount;
      this.annotationRanges = annotationRanges;
      this.reactionEntries = reactionEntries;
      this.lockState = lockState;
    }
  }

  private static long toLong(int highWord, int lowWord) {
    long value = lowWord;
    if (!((highWord == 0 && lowWord > 0) || (highWord == -1 && lowWord < 0))) {
      value &= 0xFFFFFFFFL;
      value |= ((long) highWord) << 32;
    }
    return value;
  }

  private static void appendHex4(StringBuilder out, int value) {
    out.append(hexDigit((value >> 12) & 0xF));
    out.append(hexDigit((value >> 8) & 0xF));
    out.append(hexDigit((value >> 4) & 0xF));
    out.append(hexDigit(value & 0xF));
  }

  private static char hexDigit(int value) {
    return (char) (value < 10 ? ('0' + value) : ('a' + (value - 10)));
  }

  private static final class JsonParser {
    private final String json;
    private int index;

    JsonParser(String json) {
      this.json = json;
    }

    Object parseValue() {
      skipWhitespace();
      if (index >= json.length()) {
        throw new IllegalArgumentException("Unexpected end of JSON input");
      }
      char c = json.charAt(index);
      switch (c) {
        case '{':
          return parseObjectValue();
        case '[':
          return parseArrayValue();
        case '"':
          return parseStringValue();
        case 't':
        case 'f':
          return parseBooleanValue();
        case 'n':
          return parseNullValue();
        default:
          return parseNumberValue();
      }
    }

    private Map<String, Object> parseObjectValue() {
      expect('{');
      Map<String, Object> object = new LinkedHashMap<>();
      skipWhitespace();
      if (peek('}')) {
        index++;
        return object;
      }
      while (true) {
        String key = parseStringValue();
        skipWhitespace();
        expect(':');
        object.put(key, parseValue());
        skipWhitespace();
        if (peek('}')) {
          index++;
          return object;
        }
        expect(',');
        skipWhitespace();
      }
    }

    private List<Object> parseArrayValue() {
      expect('[');
      List<Object> values = new ArrayList<>();
      skipWhitespace();
      if (peek(']')) {
        index++;
        return values;
      }
      while (true) {
        values.add(parseValue());
        skipWhitespace();
        if (peek(']')) {
          index++;
          return values;
        }
        expect(',');
        skipWhitespace();
      }
    }

    private String parseStringValue() {
      expect('"');
      StringBuilder value = new StringBuilder();
      while (index < json.length()) {
        char c = json.charAt(index++);
        if (c == '"') {
          return value.toString();
        }
        if (c != '\\') {
          value.append(c);
          continue;
        }
        if (index >= json.length()) {
          throw new IllegalArgumentException("Invalid escape at end of string");
        }
        char escaped = json.charAt(index++);
        switch (escaped) {
          case '"':
          case '\\':
          case '/':
            value.append(escaped);
            break;
          case 'b':
            value.append('\b');
            break;
          case 'f':
            value.append('\f');
            break;
          case 'n':
            value.append('\n');
            break;
          case 'r':
            value.append('\r');
            break;
          case 't':
            value.append('\t');
            break;
          case 'u':
            value.append(parseUnicodeEscape());
            break;
          default:
            throw new IllegalArgumentException("Unsupported escape: \\" + escaped);
        }
      }
      throw new IllegalArgumentException("Unterminated string");
    }

    private char parseUnicodeEscape() {
      int escapeStart = index - 2;
      if (index + 4 > json.length()) {
        throw new IllegalArgumentException("Invalid unicode escape at index " + escapeStart);
      }
      int unicode = 0;
      for (int i = 0; i < 4; i++) {
        int digit = Character.digit(json.charAt(index + i), 16);
        if (digit < 0) {
          throw new IllegalArgumentException("Invalid unicode escape at index " + escapeStart);
        }
        unicode = (unicode << 4) + digit;
      }
      index += 4;
      return (char) unicode;
    }

    private Boolean parseBooleanValue() {
      if (json.startsWith("true", index)) {
        index += 4;
        return Boolean.TRUE;
      }
      if (json.startsWith("false", index)) {
        index += 5;
        return Boolean.FALSE;
      }
      throw new IllegalArgumentException("Invalid boolean at index " + index);
    }

    private Object parseNullValue() {
      if (!json.startsWith("null", index)) {
        throw new IllegalArgumentException("Invalid null at index " + index);
      }
      index += 4;
      return null;
    }

    private Number parseNumberValue() {
      int start = index;
      if (peek('-')) {
        index++;
      }
      while (index < json.length() && Character.isDigit(json.charAt(index))) {
        index++;
      }
      boolean isFractional = false;
      if (peek('.')) {
        isFractional = true;
        index++;
        while (index < json.length() && Character.isDigit(json.charAt(index))) {
          index++;
        }
      }
      if (peek('e') || peek('E')) {
        isFractional = true;
        index++;
        if (peek('+') || peek('-')) {
          index++;
        }
        while (index < json.length() && Character.isDigit(json.charAt(index))) {
          index++;
        }
      }
      String raw = json.substring(start, index);
      return isFractional ? Double.valueOf(raw) : Long.valueOf(raw);
    }

    private void skipWhitespace() {
      while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
        index++;
      }
    }

    private boolean peek(char c) {
      return index < json.length() && json.charAt(index) == c;
    }

    private void expect(char c) {
      skipWhitespace();
      if (!peek(c)) {
        throw new IllegalArgumentException("Expected '" + c + "' at index " + index);
      }
      index++;
    }

    void ensureFullyConsumed() {
      skipWhitespace();
      if (index != json.length()) {
        throw new IllegalArgumentException("Unexpected trailing content at index " + index);
      }
    }
  }
}
