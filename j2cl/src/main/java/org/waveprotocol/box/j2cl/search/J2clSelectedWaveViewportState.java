package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.read.J2clReadWindowEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

public final class J2clSelectedWaveViewportState {
  static final String BLIP_SEGMENT_PREFIX = "blip:";

  private static final long UNKNOWN_VERSION = -1L;
  private static final J2clSelectedWaveViewportState EMPTY =
      new J2clSelectedWaveViewportState(
          UNKNOWN_VERSION,
          UNKNOWN_VERSION,
          UNKNOWN_VERSION,
          Collections.<Entry>emptyList());

  private final long snapshotVersion;
  private final long startVersion;
  private final long endVersion;
  private final List<Entry> entries;

  private J2clSelectedWaveViewportState(
      long snapshotVersion, long startVersion, long endVersion, List<Entry> entries) {
    this.snapshotVersion = snapshotVersion;
    this.startVersion = startVersion;
    this.endVersion = endVersion;
    this.entries =
        entries == null
            ? Collections.<Entry>emptyList()
            : Collections.unmodifiableList(new ArrayList<Entry>(entries));
  }

  public static J2clSelectedWaveViewportState empty() {
    return EMPTY;
  }

  static J2clSelectedWaveViewportState fromFragments(SidecarSelectedWaveFragments fragments) {
    if (fragments == null) {
      return empty();
    }
    List<Entry> entries = new ArrayList<Entry>();
    Set<String> seenSegments = new HashSet<String>();
    for (SidecarSelectedWaveFragmentRange range : fragments.getRanges()) {
      if (range == null) {
        continue;
      }
      SidecarSelectedWaveFragment fragment =
          findFragment(fragments.getEntries(), range.getSegment());
      entries.add(Entry.fromRange(range, fragment));
      seenSegments.add(range.getSegment());
    }
    for (SidecarSelectedWaveFragment fragment : fragments.getEntries()) {
      if (fragment == null || seenSegments.contains(fragment.getSegment())) {
        continue;
      }
      entries.add(Entry.fromFragment(fragment));
    }
    if (entries.isEmpty()) {
      return empty();
    }
    return new J2clSelectedWaveViewportState(
        fragments.getSnapshotVersion(),
        fragments.getStartVersion(),
        fragments.getEndVersion(),
        entries);
  }

  static J2clSelectedWaveViewportState fromDocuments(
      List<SidecarSelectedWaveDocument> documents) {
    if (documents == null || documents.isEmpty()) {
      return empty();
    }
    List<Entry> entries = new ArrayList<Entry>();
    long minVersion = Long.MAX_VALUE;
    long maxVersion = UNKNOWN_VERSION;
    for (SidecarSelectedWaveDocument document : documents) {
      if (document == null) {
        continue;
      }
      String textContent = document.getTextContent();
      if (textContent == null || textContent.isEmpty()) {
        continue;
      }
      String documentId = document.getDocumentId();
      if (documentId == null || documentId.isEmpty()) {
        continue;
      }
      // Documents do not carry fragment segment ids; blip documents use the
      // same segment convention emitted by SidecarSelectedWaveFragments.
      String segment = documentId.startsWith("b+") ? BLIP_SEGMENT_PREFIX + documentId : documentId;
      long version = document.getLastModifiedVersion();
      minVersion = Math.min(minVersion, version);
      maxVersion = Math.max(maxVersion, version);
      entries.add(Entry.loaded(segment, version, version, textContent, 0, 0));
    }
    if (entries.isEmpty()) {
      return empty();
    }
    long start = minVersion == Long.MAX_VALUE ? UNKNOWN_VERSION : minVersion;
    return new J2clSelectedWaveViewportState(maxVersion, start, maxVersion, entries);
  }

  J2clSelectedWaveViewportState mergeDocuments(
      List<SidecarSelectedWaveDocument> documents) {
    return mergeDocuments(documents, true);
  }

  J2clSelectedWaveViewportState appendMissingDocuments(
      List<SidecarSelectedWaveDocument> documents) {
    return mergeDocuments(documents, false);
  }

  J2clSelectedWaveViewportState mergeFragments(
      SidecarSelectedWaveFragments fragments, String direction) {
    J2clSelectedWaveViewportState fragmentState = fromFragments(fragments);
    if (fragmentState.isEmpty()) {
      return this;
    }
    if (isEmpty()) {
      return fragmentState;
    }
    List<Entry> merged = new ArrayList<Entry>(entries);
    List<Entry> missing = new ArrayList<Entry>();
    for (Entry fragmentEntry : fragmentState.getEntries()) {
      int existingIndex = indexOfSegment(merged, fragmentEntry.getSegment());
      if (existingIndex >= 0) {
        merged.set(existingIndex, fragmentEntry);
      } else {
        missing.add(fragmentEntry);
      }
    }
    if (J2clViewportGrowthDirection.isBackward(direction)) {
      merged.addAll(0, missing);
    } else {
      merged.addAll(missing);
    }
    return new J2clSelectedWaveViewportState(
        Math.max(snapshotVersion, fragmentState.getSnapshotVersion()),
        minKnown(startVersion, fragmentState.getStartVersion()),
        Math.max(endVersion, fragmentState.getEndVersion()),
        merged);
  }

  String edgeBlipId(String direction) {
    if (J2clViewportGrowthDirection.isBackward(direction)) {
      for (Entry entry : entries) {
        if (entry.isLoaded() && entry.isBlip()) {
          return entry.getBlipId();
        }
      }
      return "";
    }
    for (int i = entries.size() - 1; i >= 0; i--) {
      Entry entry = entries.get(i);
      if (entry.isLoaded() && entry.isBlip()) {
        return entry.getBlipId();
      }
    }
    return "";
  }

  private J2clSelectedWaveViewportState mergeDocuments(
      List<SidecarSelectedWaveDocument> documents, boolean replaceExisting) {
    J2clSelectedWaveViewportState documentState = fromDocuments(documents);
    if (documentState.isEmpty()) {
      return this;
    }
    List<Entry> merged = new ArrayList<Entry>(entries);
    for (Entry documentEntry : documentState.getEntries()) {
      int existingIndex = indexOfSegment(merged, documentEntry.getSegment());
      if (existingIndex >= 0) {
        Entry existing = merged.get(existingIndex);
        if (!replaceExisting && existing.isLoaded()) {
          continue;
        }
        long mergedToVersion =
            Math.max(existing.getToVersion(), documentEntry.getToVersion());
        long mergedFromVersion =
            minKnown(existing.getFromVersion(), documentEntry.getFromVersion());
        merged.set(
            existingIndex,
            Entry.loaded(
                existing.getSegment(),
                mergedFromVersion,
                mergedToVersion,
                documentEntry.getRawSnapshot(),
                existing.getAdjustOperationCount(),
                existing.getDiffOperationCount()));
      } else {
        merged.add(documentEntry);
      }
    }
    return new J2clSelectedWaveViewportState(
        Math.max(snapshotVersion, documentState.getSnapshotVersion()),
        minKnown(startVersion, documentState.getStartVersion()),
        Math.max(endVersion, documentState.getEndVersion()),
        merged);
  }

  public long getSnapshotVersion() {
    return snapshotVersion;
  }

  public long getStartVersion() {
    return startVersion;
  }

  public long getEndVersion() {
    return endVersion;
  }

  public List<Entry> getEntries() {
    return entries;
  }

  public boolean isEmpty() {
    return entries.isEmpty();
  }

  public List<String> getLoadedContentEntries() {
    List<String> contentEntries = new ArrayList<String>();
    List<String> fallbackEntries = new ArrayList<String>();
    for (Entry entry : entries) {
      if (!entry.isLoaded()) {
        continue;
      }
      if (entry.isBlip()) {
        contentEntries.add(entry.getRawSnapshot());
      } else {
        fallbackEntries.add(entry.getRawSnapshot());
      }
    }
    return contentEntries.isEmpty() ? fallbackEntries : contentEntries;
  }

  public List<J2clReadBlip> getLoadedReadBlips() {
    List<J2clReadBlip> readBlips = new ArrayList<J2clReadBlip>();
    for (Entry entry : entries) {
      if (!entry.isLoaded() || !entry.isBlip()) {
        continue;
      }
      readBlips.add(new J2clReadBlip(entry.getBlipId(), entry.getRawSnapshot()));
    }
    return readBlips;
  }

  public List<J2clReadWindowEntry> getReadWindowEntries() {
    List<J2clReadWindowEntry> windowEntries = new ArrayList<J2clReadWindowEntry>();
    for (Entry entry : entries) {
      if (!entry.isBlip()) {
        continue;
      }
      if (entry.isLoaded()) {
        windowEntries.add(
            J2clReadWindowEntry.loaded(
                entry.getSegment(),
                entry.getFromVersion(),
                entry.getToVersion(),
                entry.getBlipId(),
                entry.getRawSnapshot()));
      } else {
        windowEntries.add(
            J2clReadWindowEntry.placeholder(
                entry.getSegment(),
                entry.getFromVersion(),
                entry.getToVersion(),
                entry.getBlipId()));
      }
    }
    return windowEntries;
  }

  private static int indexOfSegment(List<Entry> entries, String segment) {
    for (int i = 0; i < entries.size(); i++) {
      if (equals(segment, entries.get(i).getSegment())) {
        return i;
      }
    }
    return -1;
  }

  private static long minKnown(long left, long right) {
    if (left < 0) {
      return right;
    }
    if (right < 0) {
      return left;
    }
    return Math.min(left, right);
  }

  private static SidecarSelectedWaveFragment findFragment(
      List<SidecarSelectedWaveFragment> fragments, String segment) {
    if (fragments == null) {
      return null;
    }
    for (SidecarSelectedWaveFragment fragment : fragments) {
      if (fragment != null && equals(segment, fragment.getSegment())) {
        return fragment;
      }
    }
    return null;
  }

  private static boolean equals(String left, String right) {
    return left == null ? right == null : left.equals(right);
  }

  static String blipIdOrNull(String segment) {
    if (segment == null || !segment.startsWith(BLIP_SEGMENT_PREFIX)) {
      return null;
    }
    String blipId = segment.substring(BLIP_SEGMENT_PREFIX.length());
    return blipId.isEmpty() ? null : blipId;
  }

  public static final class Entry {
    private final String segment;
    private final long fromVersion;
    private final long toVersion;
    private final String rawSnapshot;
    private final int adjustOperationCount;
    private final int diffOperationCount;
    private final boolean loaded;

    private Entry(
        String segment,
        long fromVersion,
        long toVersion,
        String rawSnapshot,
        int adjustOperationCount,
        int diffOperationCount,
        boolean loaded) {
      this.segment = segment == null ? "" : segment;
      this.fromVersion = fromVersion;
      this.toVersion = toVersion;
      this.rawSnapshot = rawSnapshot == null ? "" : rawSnapshot;
      this.adjustOperationCount = adjustOperationCount;
      this.diffOperationCount = diffOperationCount;
      this.loaded = loaded;
    }

    static Entry fromRange(
        SidecarSelectedWaveFragmentRange range, SidecarSelectedWaveFragment fragment) {
      if (fragment == null || fragment.getRawSnapshot() == null) {
        return placeholder(range.getSegment(), range.getFromVersion(), range.getToVersion());
      }
      return new Entry(
          range.getSegment(),
          range.getFromVersion(),
          range.getToVersion(),
          fragment.getRawSnapshot(),
          fragment.getAdjustOperationCount(),
          fragment.getDiffOperationCount(),
          true);
    }

    static Entry fromFragment(SidecarSelectedWaveFragment fragment) {
      if (fragment.getRawSnapshot() == null) {
        return placeholder(fragment.getSegment(), UNKNOWN_VERSION, UNKNOWN_VERSION);
      }
      return new Entry(
          fragment.getSegment(),
          UNKNOWN_VERSION,
          UNKNOWN_VERSION,
          fragment.getRawSnapshot(),
          fragment.getAdjustOperationCount(),
          fragment.getDiffOperationCount(),
          true);
    }

    static Entry loaded(
        String segment,
        long fromVersion,
        long toVersion,
        String rawSnapshot,
        int adjustOperationCount,
        int diffOperationCount) {
      return new Entry(
          segment,
          fromVersion,
          toVersion,
          rawSnapshot,
          adjustOperationCount,
          diffOperationCount,
          true);
    }

    static Entry placeholder(String segment, long fromVersion, long toVersion) {
      return new Entry(segment, fromVersion, toVersion, "", 0, 0, false);
    }

    public String getSegment() {
      return segment;
    }

    public long getFromVersion() {
      return fromVersion;
    }

    public long getToVersion() {
      return toVersion;
    }

    public String getRawSnapshot() {
      return rawSnapshot;
    }

    public int getAdjustOperationCount() {
      return adjustOperationCount;
    }

    public int getDiffOperationCount() {
      return diffOperationCount;
    }

    public boolean isLoaded() {
      return loaded;
    }

    public boolean isBlip() {
      return !getBlipId().isEmpty();
    }

    public String getBlipId() {
      String blipId = blipIdOrNull(segment);
      return blipId == null ? "" : blipId;
    }
  }
}
