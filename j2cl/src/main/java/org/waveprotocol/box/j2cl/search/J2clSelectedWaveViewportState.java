package org.waveprotocol.box.j2cl.search;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentMetadata;
import org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.read.J2clReadBlipContent;
import org.waveprotocol.box.j2cl.read.J2clReadWindowEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

public final class J2clSelectedWaveViewportState {
  static final String BLIP_SEGMENT_PREFIX = "blip:";
  private static final String BLIP_DOCUMENT_PREFIX = "b+";

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
      if (textContent == null) {
        textContent = "";
      }
      String documentId = document.getDocumentId();
      if (documentId == null || documentId.isEmpty()) {
        continue;
      }
      // Documents do not carry fragment segment ids. Generated conversational blip ids use
      // IdGenerator's "b+" token; other document ids remain non-read metadata entries until the
      // sidecar transport can send explicit segment ids for documents too.
      String segment =
          documentId.startsWith(BLIP_DOCUMENT_PREFIX)
              ? BLIP_SEGMENT_PREFIX + documentId
              : documentId;
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
        Entry existing = merged.get(existingIndex);
        if (existing.isLoaded() && !fragmentEntry.isLoaded()) {
          merged.set(
              existingIndex,
              Entry.loaded(
                  existing.getSegment(),
                  minKnown(existing.getFromVersion(), fragmentEntry.getFromVersion()),
                  Math.max(existing.getToVersion(), fragmentEntry.getToVersion()),
                  existing.getRawSnapshot(),
                  existing.getAdjustOperationCount(),
                  existing.getDiffOperationCount(),
                  existing.shouldParseAttachmentElements(),
                  existing.attachmentOverrides,
                  existing.parsedContent));
        } else {
          merged.set(existingIndex, fragmentEntry.withCachedParsedContentFrom(existing));
        }
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

  String edgePlaceholderBlipId(String direction) {
    if (J2clViewportGrowthDirection.isBackward(direction)) {
      for (Entry entry : entries) {
        if (!entry.isLoaded() && entry.isBlip()) {
          return entry.getBlipId();
        }
        if (entry.isLoaded() && entry.isBlip()) {
          return "";
        }
      }
      return "";
    }
    for (int i = entries.size() - 1; i >= 0; i--) {
      Entry entry = entries.get(i);
      if (!entry.isLoaded() && entry.isBlip()) {
        return entry.getBlipId();
      }
      if (entry.isLoaded() && entry.isBlip()) {
        return "";
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
        // Fragment fetches can expand the window through mergeFragments/minKnown; document-only
        // updates refresh content inside the current viewport and must not widen a known fragment
        // window backward just because a document has an older modified version.
        long mergedFromVersion =
            knownOrFallback(existing.getFromVersion(), documentEntry.getFromVersion());
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
        knownOrFallback(startVersion, documentState.getStartVersion()),
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

  /**
   * Returns true only for fragment payloads that can define a selected-wave read window.
   *
   * <p>Metadata/index-only fragment deltas are state updates; the projector must route them to the
   * previous viewport or document fallback path instead of replacing visible blips.
   */
  boolean hasBlipEntries() {
    for (Entry entry : entries) {
      if (entry.isBlip()) {
        return true;
      }
    }
    return false;
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

  List<String> getPendingAttachmentIds() {
    Set<String> pendingIds = new LinkedHashSet<String>();
    for (Entry entry : entries) {
      if (!entry.isLoaded() || !entry.shouldParseAttachmentElements()) {
        continue;
      }
      J2clReadBlipContent content = entry.getParsedContent();
      for (J2clAttachmentRenderModel attachment : resolveAttachments(entry, content)) {
        if (attachment.isMetadataPending() && !attachment.getAttachmentId().isEmpty()) {
          pendingIds.add(attachment.getAttachmentId());
        }
      }
    }
    return new ArrayList<String>(pendingIds);
  }

  J2clSelectedWaveViewportState withAttachmentMetadata(
      List<J2clAttachmentMetadata> metadata,
      List<String> missingAttachmentIds) {
    Map<String, J2clAttachmentMetadata> metadataById =
        new HashMap<String, J2clAttachmentMetadata>();
    if (metadata != null) {
      for (J2clAttachmentMetadata item : metadata) {
        if (item != null && item.getAttachmentId() != null && !item.getAttachmentId().isEmpty()) {
          metadataById.put(item.getAttachmentId(), item);
        }
      }
    }
    Set<String> missingIds = new HashSet<String>();
    if (missingAttachmentIds != null) {
      missingIds.addAll(missingAttachmentIds);
    }
    return withAttachmentResolution(metadataById, missingIds, Collections.<String>emptySet(), "");
  }

  J2clSelectedWaveViewportState withAttachmentMetadataFailure(
      List<String> attachmentIds, String reason) {
    Set<String> failedIds = new HashSet<String>();
    if (attachmentIds != null) {
      failedIds.addAll(attachmentIds);
    }
    return withAttachmentResolution(
        Collections.<String, J2clAttachmentMetadata>emptyMap(),
        Collections.<String>emptySet(),
        failedIds,
        reason);
  }

  public List<J2clReadBlip> getLoadedReadBlips() {
    List<J2clReadBlip> readBlips = new ArrayList<J2clReadBlip>();
    for (Entry entry : entries) {
      if (!entry.isLoaded() || !entry.isBlip()) {
        continue;
      }
      // parseAttachmentElements is true only for fragment entries (fromFragments); document
      // entries (fromDocuments) leave it false, so plain text like "2 < 3" is never mangled.
      if (entry.shouldParseAttachmentElements()) {
        J2clReadBlipContent content = entry.getParsedContent();
        readBlips.add(
            new J2clReadBlip(
                entry.getBlipId(),
                content.getText(),
                resolveAttachments(entry, content)));
      } else {
        readBlips.add(new J2clReadBlip(entry.getBlipId(), entry.getRawSnapshot()));
      }
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
        if (entry.shouldParseAttachmentElements()) {
          J2clReadBlipContent content = entry.getParsedContent();
          windowEntries.add(
              J2clReadWindowEntry.loaded(
                  entry.getSegment(),
                  entry.getFromVersion(),
                  entry.getToVersion(),
                  entry.getBlipId(),
                  content.getText(),
                  resolveAttachments(entry, content)));
        } else {
          windowEntries.add(
              J2clReadWindowEntry.loaded(
                  entry.getSegment(),
                  entry.getFromVersion(),
                  entry.getToVersion(),
                  entry.getBlipId(),
                  entry.getRawSnapshot()));
        }
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

  private J2clSelectedWaveViewportState withAttachmentResolution(
      Map<String, J2clAttachmentMetadata> metadataById,
      Set<String> missingIds,
      Set<String> failedIds,
      String failureReason) {
    if ((metadataById == null || metadataById.isEmpty())
        && (missingIds == null || missingIds.isEmpty())
        && (failedIds == null || failedIds.isEmpty())) {
      return this;
    }
    List<Entry> resolved = new ArrayList<Entry>();
    boolean changed = false;
    for (Entry entry : entries) {
      Entry next =
          entry.withAttachmentResolution(metadataById, missingIds, failedIds, failureReason);
      resolved.add(next);
      changed = changed || next != entry;
    }
    if (!changed) {
      return this;
    }
    return new J2clSelectedWaveViewportState(
        snapshotVersion, startVersion, endVersion, resolved);
  }

  private static List<J2clAttachmentRenderModel> resolveAttachments(
      Entry entry, J2clReadBlipContent content) {
    return entry.getAttachmentOverrides().isEmpty()
        ? content.getAttachments()
        : entry.getAttachmentOverrides();
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

  private static long knownOrFallback(long value, long fallback) {
    return value < 0 ? fallback : value;
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
    private final boolean parseAttachmentElements;
    private final List<J2clAttachmentRenderModel> attachmentOverrides;
    // Cache only: never include this mutable field in equality/hash semantics if Entry later gains
    // value-style comparison. A loaded fragment can be projected into content, read blips, and
    // attachment metadata overrides during the same viewport lifetime.
    private J2clReadBlipContent parsedContent;

    private Entry(
        String segment,
        long fromVersion,
        long toVersion,
        String rawSnapshot,
        int adjustOperationCount,
        int diffOperationCount,
        boolean loaded,
        boolean parseAttachmentElements,
        List<J2clAttachmentRenderModel> attachmentOverrides,
        J2clReadBlipContent parsedContent) {
      this.segment = segment == null ? "" : segment;
      this.fromVersion = fromVersion;
      this.toVersion = toVersion;
      this.rawSnapshot = rawSnapshot == null ? "" : rawSnapshot;
      this.adjustOperationCount = adjustOperationCount;
      this.diffOperationCount = diffOperationCount;
      this.loaded = loaded;
      this.parseAttachmentElements = parseAttachmentElements;
      this.attachmentOverrides =
          attachmentOverrides == null
              ? Collections.<J2clAttachmentRenderModel>emptyList()
              : Collections.unmodifiableList(
                  new ArrayList<J2clAttachmentRenderModel>(attachmentOverrides));
      this.parsedContent = parsedContent;
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
          true,
          true,
          Collections.<J2clAttachmentRenderModel>emptyList(),
          null);
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
          true,
          true,
          Collections.<J2clAttachmentRenderModel>emptyList(),
          null);
    }

    static Entry loaded(
        String segment,
        long fromVersion,
        long toVersion,
        String rawSnapshot,
        int adjustOperationCount,
        int diffOperationCount) {
      // Default loaded entries are document text snapshots; fragment debug XML opts in below.
      return loaded(
          segment,
          fromVersion,
          toVersion,
          rawSnapshot,
          adjustOperationCount,
          diffOperationCount,
          false);
    }

    static Entry loaded(
        String segment,
        long fromVersion,
        long toVersion,
        String rawSnapshot,
        int adjustOperationCount,
        int diffOperationCount,
        boolean parseAttachmentElements) {
      return loaded(
          segment,
          fromVersion,
          toVersion,
          rawSnapshot,
          adjustOperationCount,
          diffOperationCount,
          parseAttachmentElements,
          Collections.<J2clAttachmentRenderModel>emptyList());
    }

    static Entry loaded(
        String segment,
        long fromVersion,
        long toVersion,
        String rawSnapshot,
        int adjustOperationCount,
        int diffOperationCount,
        boolean parseAttachmentElements,
        List<J2clAttachmentRenderModel> attachmentOverrides) {
      return loaded(
          segment,
          fromVersion,
          toVersion,
          rawSnapshot,
          adjustOperationCount,
          diffOperationCount,
          parseAttachmentElements,
          attachmentOverrides,
          null);
    }

    static Entry loaded(
        String segment,
        long fromVersion,
        long toVersion,
        String rawSnapshot,
        int adjustOperationCount,
        int diffOperationCount,
        boolean parseAttachmentElements,
        List<J2clAttachmentRenderModel> attachmentOverrides,
        J2clReadBlipContent parsedContent) {
      return new Entry(
          segment,
          fromVersion,
          toVersion,
          rawSnapshot,
          adjustOperationCount,
          diffOperationCount,
          true,
          parseAttachmentElements,
          attachmentOverrides,
          parsedContent);
    }

    static Entry placeholder(String segment, long fromVersion, long toVersion) {
      return new Entry(
          segment,
          fromVersion,
          toVersion,
          "",
          0,
          0,
          false,
          false,
          Collections.<J2clAttachmentRenderModel>emptyList(),
          null);
    }

    private Entry withAttachmentResolution(
        Map<String, J2clAttachmentMetadata> metadataById,
        Set<String> missingIds,
        Set<String> failedIds,
        String failureReason) {
      if (!loaded || !parseAttachmentElements) {
        return this;
      }
      J2clReadBlipContent content = getParsedContent();
      if (content.getAttachments().isEmpty()) {
        return attachmentOverrides.isEmpty()
            ? this
            : loaded(
                segment,
                fromVersion,
                toVersion,
                rawSnapshot,
                adjustOperationCount,
                diffOperationCount,
                parseAttachmentElements,
                Collections.<J2clAttachmentRenderModel>emptyList(),
                content);
      }
      Map<String, Deque<J2clAttachmentRenderModel>> currentQueuesById =
          new HashMap<String, Deque<J2clAttachmentRenderModel>>();
      for (J2clAttachmentRenderModel current : attachmentOverrides) {
        if (!current.getAttachmentId().isEmpty()) {
          Deque<J2clAttachmentRenderModel> queue = currentQueuesById.get(current.getAttachmentId());
          if (queue == null) {
            queue = new ArrayDeque<J2clAttachmentRenderModel>();
            currentQueuesById.put(current.getAttachmentId(), queue);
          }
          queue.add(current);
        }
      }
      List<J2clAttachmentRenderModel> nextAttachments =
          new ArrayList<J2clAttachmentRenderModel>();
      for (J2clAttachmentRenderModel parsed : content.getAttachments()) {
        String attachmentId = parsed.getAttachmentId();
        J2clAttachmentMetadata metadata = metadataById.get(attachmentId);
        if (metadata != null) {
          nextAttachments.add(
              J2clAttachmentRenderModel.fromMetadata(
                  attachmentId, parsed.getCaption(), parsed.getDisplaySize(), metadata));
        } else if (missingIds.contains(attachmentId)) {
          nextAttachments.add(
              J2clAttachmentRenderModel.metadataFailure(
                  attachmentId,
                  parsed.getCaption(),
                  parsed.getDisplaySize(),
                  "Attachment metadata unavailable."));
        } else if (failedIds.contains(attachmentId)) {
          nextAttachments.add(
              J2clAttachmentRenderModel.metadataFailure(
                  attachmentId, parsed.getCaption(), parsed.getDisplaySize(), failureReason));
        } else {
          Deque<J2clAttachmentRenderModel> queue = currentQueuesById.get(attachmentId);
          J2clAttachmentRenderModel current = queue != null ? queue.poll() : null;
          nextAttachments.add(current == null ? parsed : current);
        }
      }
      if (nextAttachments.equals(attachmentOverrides)) {
        return this;
      }
      return loaded(
          segment,
          fromVersion,
          toVersion,
          rawSnapshot,
          adjustOperationCount,
          diffOperationCount,
          parseAttachmentElements,
          nextAttachments,
          content);
    }

    private Entry withCachedParsedContentFrom(Entry existing) {
      if (existing == null || !rawSnapshot.equals(existing.rawSnapshot)) {
        return this;
      }
      // J2clReadBlipContent is a pure parse of rawSnapshot, so exact raw equality is the only
      // safe cache/override reuse key across fragment replacements.
      return loaded(
          segment,
          fromVersion,
          toVersion,
          rawSnapshot,
          adjustOperationCount,
          diffOperationCount,
          parseAttachmentElements,
          existing.attachmentOverrides,
          existing.parsedContent);
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

    public boolean shouldParseAttachmentElements() {
      return parseAttachmentElements;
    }

    List<J2clAttachmentRenderModel> getAttachmentOverrides() {
      return attachmentOverrides;
    }

    J2clReadBlipContent getParsedContent() {
      if (parsedContent == null) {
        parsedContent = J2clReadBlipContent.parseRawSnapshot(rawSnapshot);
      }
      return parsedContent;
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
