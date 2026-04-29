package org.waveprotocol.box.j2cl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * J-UI-4 (#1082, R-3.1) — parsed view of the {@code conversation}
 * manifest document for the J2CL read surface. The manifest XML is
 * shipped as one of the {@code SidecarSelectedWaveDocument}s on every
 * selected-wave update; this type is the parsed tree the projector
 * uses to attach parent-blip / thread / depth metadata to each
 * {@link org.waveprotocol.box.j2cl.read.J2clReadBlip}.
 *
 * <p>The manifest is a tree of {@code <thread>} and {@code <blip>}
 * elements. The root is {@code <conversation>} which contains a
 * single root-level thread (id often {@code root}); blips inside a
 * thread may themselves carry zero or more reply {@code <thread>}
 * children. The renderer uses the parsed tree to nest reply threads
 * visually under their parent blip.
 *
 * <p>Empty manifest = "no conversation document on this update", in
 * which case the renderer falls back to flat rendering (the current
 * F-2 behaviour).
 */
public final class SidecarConversationManifest {

  /** Per-blip entry in the manifest. */
  public static final class Entry {
    private final String blipId;
    private final String parentBlipId;
    private final String threadId;
    private final int depth;
    private final int siblingIndex;
    private final int replyInsertPosition;

    public Entry(String blipId, String parentBlipId, String threadId, int depth, int siblingIndex) {
      this(blipId, parentBlipId, threadId, depth, siblingIndex, -1);
    }

    public Entry(
        String blipId,
        String parentBlipId,
        String threadId,
        int depth,
        int siblingIndex,
        int replyInsertPosition) {
      this.blipId = blipId == null ? "" : blipId;
      this.parentBlipId = parentBlipId == null ? "" : parentBlipId;
      this.threadId = threadId == null ? "" : threadId;
      this.depth = depth;
      this.siblingIndex = siblingIndex;
      this.replyInsertPosition = Math.max(-1, replyInsertPosition);
    }

    public String getBlipId() {
      return blipId;
    }

    /** Empty when this blip is in the root thread. */
    public String getParentBlipId() {
      return parentBlipId;
    }

    /** Empty when the manifest didn't tag the thread with an id. */
    public String getThreadId() {
      return threadId;
    }

    /** 0 = root thread; +1 per nested {@code <thread>} ancestor. */
    public int getDepth() {
      return depth;
    }

    /** Position among siblings inside the same {@code <thread>}. */
    public int getSiblingIndex() {
      return siblingIndex;
    }

    /**
     * Item position immediately before this blip's closing element in the
     * conversation manifest. Reply submit deltas retain to this position and
     * insert a new {@code <thread><blip/></thread>} child there.
     */
    public int getReplyInsertPosition() {
      return replyInsertPosition;
    }
  }

  private static final SidecarConversationManifest EMPTY =
      new SidecarConversationManifest(
          Collections.<Entry>emptyList(), Collections.<String, Entry>emptyMap(), 0);

  private final List<Entry> orderedEntries;
  private final Map<String, Entry> entriesByBlipId;
  private final Map<String, List<String>> childBlipIdsByParentBlipId;
  private final int itemCount;

  private SidecarConversationManifest(
      List<Entry> orderedEntries, Map<String, Entry> entriesByBlipId, int itemCount) {
    this.orderedEntries = Collections.unmodifiableList(new ArrayList<Entry>(orderedEntries));
    this.entriesByBlipId =
        Collections.unmodifiableMap(new LinkedHashMap<String, Entry>(entriesByBlipId));
    this.itemCount = Math.max(0, itemCount);
    Map<String, List<String>> children = new LinkedHashMap<String, List<String>>();
    for (Entry entry : this.orderedEntries) {
      String parent = entry.getParentBlipId();
      List<String> bucket = children.get(parent);
      if (bucket == null) {
        bucket = new ArrayList<String>();
        children.put(parent, bucket);
      }
      bucket.add(entry.getBlipId());
    }
    Map<String, List<String>> readonlyChildren = new LinkedHashMap<String, List<String>>();
    for (Map.Entry<String, List<String>> kv : children.entrySet()) {
      readonlyChildren.put(kv.getKey(), Collections.unmodifiableList(kv.getValue()));
    }
    this.childBlipIdsByParentBlipId = Collections.unmodifiableMap(readonlyChildren);
  }

  /** Empty manifest — used when no {@code conversation} document was streamed. */
  public static SidecarConversationManifest empty() {
    return EMPTY;
  }

  /**
   * Builds a manifest from an ordered list of entries already in
   * depth-first pre-order traversal order. Callers (the codec) must
   * have already validated the parent chain.
   */
  public static SidecarConversationManifest of(List<Entry> entriesInDfsOrder) {
    return of(entriesInDfsOrder, -1);
  }

  public static SidecarConversationManifest of(List<Entry> entriesInDfsOrder, int itemCount) {
    if (entriesInDfsOrder == null || entriesInDfsOrder.isEmpty()) {
      return EMPTY;
    }
    // Filter null / empty-id entries AND dedupe by blipId so the
    // ordered list and the by-id lookup map agree on what's present
    // (review-1089 round-1: previously the ordered list could carry
    // duplicates that the renderer would then render twice).
    Map<String, Entry> byId = new LinkedHashMap<String, Entry>();
    List<Entry> filtered = new ArrayList<Entry>(entriesInDfsOrder.size());
    for (Entry entry : entriesInDfsOrder) {
      if (entry == null || entry.getBlipId().isEmpty()) {
        continue;
      }
      // First occurrence wins so a malformed manifest with duplicate
      // blip-id references does not silently overwrite the original.
      if (byId.containsKey(entry.getBlipId())) {
        continue;
      }
      byId.put(entry.getBlipId(), entry);
      filtered.add(entry);
    }
    if (filtered.isEmpty()) {
      return EMPTY;
    }
    return new SidecarConversationManifest(filtered, byId, resolveItemCount(filtered, itemCount));
  }

  /**
   * Extracts the conversation manifest from viewport-style fragments.
   *
   * <p>The selected-wave stream can deliver the manifest as a full document
   * operation (decoded by {@code SidecarTransportCodec}) or as the raw XML
   * snapshot carried by the viewport {@code manifest} segment. Fragment growth
   * uses the latter shape, so the J2CL renderer must be able to recover the
   * same parent/thread tree from raw XML as it does from document operations.
   */
  public static SidecarConversationManifest fromFragments(SidecarSelectedWaveFragments fragments) {
    if (fragments == null || fragments.getEntries().isEmpty()) {
      return EMPTY;
    }
    for (SidecarSelectedWaveFragment fragment : fragments.getEntries()) {
      if (fragment == null || !"manifest".equals(fragment.getSegment())) {
        continue;
      }
      SidecarConversationManifest parsed = fromXml(fragment.getRawSnapshot());
      if (!parsed.isEmpty()) {
        return parsed;
      }
    }
    return EMPTY;
  }

  /**
   * Parses the compact raw XML snapshot used by the viewport manifest segment.
   * This intentionally recognizes only {@code conversation}, {@code thread},
   * and {@code blip} structure; unknown tags and malformed tails are ignored so
   * a corrupt manifest cannot suppress otherwise visible blip content.
   */
  public static SidecarConversationManifest fromXml(String rawXml) {
    if (rawXml == null || rawXml.isEmpty() || rawXml.indexOf("<") < 0) {
      return EMPTY;
    }
    List<Entry> entries = new ArrayList<Entry>();
    List<String> threadStack = new ArrayList<String>();
    List<String> threadParentBlipStack = new ArrayList<String>();
    List<Integer> siblingCounterStack = new ArrayList<Integer>();
    List<String> openBlipStack = new ArrayList<String>();
    List<Integer> openBlipEntryIndexStack = new ArrayList<Integer>();
    int rootSiblingCounter = 0;
    int itemPosition = 0;
    int cursor = 0;
    while (cursor < rawXml.length()) {
      int tagStart = rawXml.indexOf('<', cursor);
      if (tagStart < 0) {
        break;
      }
      int tagEnd = findTagEnd(rawXml, tagStart + 1);
      if (tagEnd < 0) {
        break;
      }
      String tag = rawXml.substring(tagStart + 1, tagEnd).trim();
      cursor = tagEnd + 1;
      if (tag.isEmpty() || tag.startsWith("?") || tag.startsWith("!")) {
        continue;
      }
      boolean closing = tag.startsWith("/");
      if (closing) {
        String name = tagName(tag.substring(1).trim());
        if ("thread".equals(name)) {
          popLast(threadStack);
          popLast(threadParentBlipStack);
          popLast(siblingCounterStack);
        } else if ("blip".equals(name)) {
          popLast(openBlipStack);
          Integer entryIndex = removeLast(openBlipEntryIndexStack);
          if (entryIndex != null && entryIndex.intValue() >= 0) {
            setReplyInsertPosition(entries, entryIndex.intValue(), itemPosition);
          }
        }
        if ("conversation".equals(name) || "thread".equals(name) || "blip".equals(name)) {
          itemPosition++;
        }
        continue;
      }
      boolean selfClosing = tag.endsWith("/");
      if (selfClosing) {
        tag = tag.substring(0, tag.length() - 1).trim();
      }
      String name = tagName(tag);
      if ("thread".equals(name)) {
        itemPosition++;
        String threadId = attributeValue(tag, "id");
        threadStack.add(threadId == null ? "" : threadId);
        String parentBlipId =
            openBlipStack.isEmpty() ? "" : openBlipStack.get(openBlipStack.size() - 1);
        threadParentBlipStack.add(parentBlipId);
        siblingCounterStack.add(Integer.valueOf(0));
        if (selfClosing) {
          popLast(threadStack);
          popLast(threadParentBlipStack);
          popLast(siblingCounterStack);
          itemPosition++;
        }
      } else if ("blip".equals(name)) {
        itemPosition++;
        String blipId = attributeValue(tag, "id");
        if (blipId == null || blipId.isEmpty()) {
          if (selfClosing) {
            itemPosition++;
          } else {
            openBlipStack.add("");
            openBlipEntryIndexStack.add(Integer.valueOf(-1));
          }
          continue;
        }
        String parentBlipId =
            threadParentBlipStack.isEmpty()
                ? ""
                : threadParentBlipStack.get(threadParentBlipStack.size() - 1);
        String threadId =
            threadStack.isEmpty() ? "" : threadStack.get(threadStack.size() - 1);
        int siblingIndex;
        if (siblingCounterStack.isEmpty()) {
          siblingIndex = rootSiblingCounter++;
        } else {
          int last = siblingCounterStack.size() - 1;
          siblingIndex = siblingCounterStack.get(last).intValue();
          siblingCounterStack.set(last, Integer.valueOf(siblingIndex + 1));
        }
        int entryIndex = entries.size();
        entries.add(
            new Entry(
                blipId,
                parentBlipId,
                threadId,
                depthFor(threadParentBlipStack),
                siblingIndex));
        if (selfClosing) {
          setReplyInsertPosition(entries, entryIndex, itemPosition);
          itemPosition++;
        } else {
          openBlipStack.add(blipId);
          openBlipEntryIndexStack.add(Integer.valueOf(entryIndex));
        }
      } else if ("conversation".equals(name)) {
        itemPosition++;
        if (selfClosing) {
          itemPosition++;
        }
      }
    }
    return of(entries, itemPosition);
  }

  public boolean isEmpty() {
    return orderedEntries.isEmpty();
  }

  /** Entries in depth-first pre-order traversal order. */
  public List<Entry> getOrderedEntries() {
    return orderedEntries;
  }

  public Entry findByBlipId(String blipId) {
    return blipId == null ? null : entriesByBlipId.get(blipId);
  }

  /**
   * Direct-child blip ids for a given parent. Empty list when the
   * parent has no replies. {@code parentBlipId == ""} returns the
   * root-thread blips.
   */
  public List<String> getChildBlipIds(String parentBlipId) {
    String key = parentBlipId == null ? "" : parentBlipId;
    List<String> bucket = childBlipIdsByParentBlipId.get(key);
    return bucket == null ? Collections.<String>emptyList() : bucket;
  }

  /** Total manifest document item count, used to build complete insert DocOps. */
  public int getItemCount() {
    return itemCount;
  }

  private static String tagName(String tag) {
    if (tag == null || tag.isEmpty()) {
      return "";
    }
    int end = 0;
    while (end < tag.length()) {
      char c = tag.charAt(end);
      if (Character.isWhitespace(c) || c == '/') {
        break;
      }
      end++;
    }
    return end <= 0 ? "" : tag.substring(0, end);
  }

  private static int findTagEnd(String rawXml, int cursor) {
    char quote = 0;
    for (int i = cursor; i < rawXml.length(); i++) {
      char c = rawXml.charAt(i);
      if (quote != 0) {
        if (c == quote) {
          quote = 0;
        }
        continue;
      }
      if (c == '"' || c == '\'') {
        quote = c;
      } else if (c == '>') {
        return i;
      }
    }
    return -1;
  }

  private static String attributeValue(String tag, String name) {
    if (tag == null || name == null || name.isEmpty()) {
      return null;
    }
    int cursor = 0;
    while (cursor < tag.length()) {
      int idx = tag.indexOf(name, cursor);
      if (idx < 0) {
        return null;
      }
      int before = idx - 1;
      int afterName = idx + name.length();
      if ((before >= 0 && isNameChar(tag.charAt(before)))
          || (afterName < tag.length() && isNameChar(tag.charAt(afterName)))) {
        cursor = afterName;
        continue;
      }
      int pos = afterName;
      while (pos < tag.length() && Character.isWhitespace(tag.charAt(pos))) {
        pos++;
      }
      if (pos >= tag.length() || tag.charAt(pos) != '=') {
        cursor = afterName;
        continue;
      }
      pos++;
      while (pos < tag.length() && Character.isWhitespace(tag.charAt(pos))) {
        pos++;
      }
      if (pos >= tag.length()) {
        return "";
      }
      char quote = tag.charAt(pos);
      if (quote == '"' || quote == '\'') {
        int valueStart = pos + 1;
        int valueEnd = tag.indexOf(quote, valueStart);
        return valueEnd < 0 ? tag.substring(valueStart) : tag.substring(valueStart, valueEnd);
      }
      int valueStart = pos;
      while (pos < tag.length()
          && !Character.isWhitespace(tag.charAt(pos))
          && tag.charAt(pos) != '/') {
        pos++;
      }
      return tag.substring(valueStart, pos);
    }
    return null;
  }

  private static boolean isNameChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':';
  }

  private static int depthFor(List<String> threadParentBlipStack) {
    int depth = 0;
    for (String parent : threadParentBlipStack) {
      if (parent != null && !parent.isEmpty()) {
        depth++;
      }
    }
    return depth;
  }

  private static <T> void popLast(List<T> values) {
    if (values != null && !values.isEmpty()) {
      values.remove(values.size() - 1);
    }
  }

  private static <T> T removeLast(List<T> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    return values.remove(values.size() - 1);
  }

  private static int resolveItemCount(List<Entry> entries, int explicitItemCount) {
    int inferredMin = 0;
    for (Entry entry : entries) {
      if (entry != null) {
        inferredMin = Math.max(inferredMin, entry.getReplyInsertPosition() + 1);
      }
    }
    if (explicitItemCount >= 0) {
      return Math.max(explicitItemCount, inferredMin);
    }
    return inferredMin;
  }

  private static void setReplyInsertPosition(List<Entry> entries, int index, int position) {
    if (entries == null || index < 0 || index >= entries.size()) {
      return;
    }
    Entry entry = entries.get(index);
    entries.set(
        index,
        new Entry(
            entry.getBlipId(),
            entry.getParentBlipId(),
            entry.getThreadId(),
            entry.getDepth(),
            entry.getSiblingIndex(),
            position));
  }
}
