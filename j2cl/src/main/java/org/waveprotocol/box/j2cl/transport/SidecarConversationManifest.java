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

    public Entry(String blipId, String parentBlipId, String threadId, int depth, int siblingIndex) {
      this.blipId = blipId == null ? "" : blipId;
      this.parentBlipId = parentBlipId == null ? "" : parentBlipId;
      this.threadId = threadId == null ? "" : threadId;
      this.depth = depth;
      this.siblingIndex = siblingIndex;
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
  }

  private static final SidecarConversationManifest EMPTY =
      new SidecarConversationManifest(
          Collections.<Entry>emptyList(), Collections.<String, Entry>emptyMap());

  private final List<Entry> orderedEntries;
  private final Map<String, Entry> entriesByBlipId;
  private final Map<String, List<String>> childBlipIdsByParentBlipId;

  private SidecarConversationManifest(
      List<Entry> orderedEntries, Map<String, Entry> entriesByBlipId) {
    this.orderedEntries = Collections.unmodifiableList(new ArrayList<Entry>(orderedEntries));
    this.entriesByBlipId =
        Collections.unmodifiableMap(new LinkedHashMap<String, Entry>(entriesByBlipId));
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
    if (entriesInDfsOrder == null || entriesInDfsOrder.isEmpty()) {
      return EMPTY;
    }
    Map<String, Entry> byId = new LinkedHashMap<String, Entry>();
    for (Entry entry : entriesInDfsOrder) {
      if (entry == null || entry.getBlipId().isEmpty()) {
        continue;
      }
      // First occurrence wins so a malformed manifest with duplicate
      // blip-id references does not silently overwrite the original.
      if (!byId.containsKey(entry.getBlipId())) {
        byId.put(entry.getBlipId(), entry);
      }
    }
    return new SidecarConversationManifest(entriesInDfsOrder, byId);
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
}
