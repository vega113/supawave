package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.waveprotocol.box.j2cl.overlay.J2clInteractionBlipModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarConversationManifest;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;

public final class J2clSelectedWaveProjector {
  private J2clSelectedWaveProjector() {
  }

  public static J2clSelectedWaveModel project(
      String selectedWaveId,
      J2clSearchDigestItem digestItem,
      SidecarSelectedWaveUpdate update,
      J2clSelectedWaveModel previous,
      int reconnectCount) {
    return project(selectedWaveId, digestItem, update, previous, reconnectCount, null, false);
  }

  /**
   * Projects a selected-wave update, preferring the supplied per-user read
   * state over digest metadata. {@code readState} may be {@code null} when the
   * server has not yet responded; in that case the projector falls back to any
   * prior read state carried on {@code previous}, and finally to digest
   * metadata. {@code readStateStale} indicates that the last fetch failed — the
   * numeric count stays, but callers can surface the staleness separately.
   */
  public static J2clSelectedWaveModel project(
      String selectedWaveId,
      J2clSearchDigestItem digestItem,
      SidecarSelectedWaveUpdate update,
      J2clSelectedWaveModel previous,
      int reconnectCount,
      SidecarSelectedWaveReadState readState,
      boolean readStateStale) {
    boolean previousMatchesWave =
        previous != null
            && selectedWaveId != null
            && selectedWaveId.equals(previous.getSelectedWaveId());
    List<String> participantIds = update.getParticipantIds();
    if (participantIds.isEmpty() && previousMatchesWave) {
      participantIds = previous.getParticipantIds();
    }
    J2clSelectedWaveViewportState viewportState =
        projectViewportState(update, previousMatchesWave, previous);
    boolean hasViewportWindow = !viewportState.isEmpty();
    List<String> contentEntries =
        hasViewportWindow
            ? viewportState.getLoadedContentEntries()
            : extractDocumentEntries(update.getDocuments());
    // TODO(#904): keep the legacy document projection fallback for non-viewport updates until all
    // selected-wave surfaces consume J2clSelectedWaveViewportState directly.
    if (contentEntries.isEmpty()
        && !hasViewportWindow
        && previousMatchesWave
        && !previous.getContentEntries().isEmpty()) {
      contentEntries = previous.getContentEntries();
    }
    List<J2clReadBlip> readBlips =
        hasViewportWindow
            ? viewportState.getLoadedReadBlips()
            : extractDocumentReadBlips(update.getDocuments());
    if (readBlips.isEmpty()
        && !hasViewportWindow
        && previousMatchesWave
        && !previous.getReadBlips().isEmpty()) {
      readBlips = previous.getReadBlips();
    }
    // F-2 (#1037, R-3.1) — enrich the viewport-derived read blips with the
    // per-blip metadata (author, timestamp, mention flag) sourced from the
    // documents list when the same wire payload carries both shapes. The
    // document path is still authoritative when it exists.
    readBlips = enrichReadBlipMetadata(readBlips, update.getDocuments());
    // J-UI-4 (#1082, R-3.1) — apply the parsed conversation manifest:
    // graft parent-blip-id + thread-id onto each read blip and reorder
    // the list into manifest depth-first pre-order traversal so reply
    // ordering matches the conversation model. Empty manifest = no
    // change (fall back to enrichment-derived ordering).
    readBlips = applyConversationManifest(readBlips, update.getConversationManifest());
    J2clSidecarWriteSession writeSession = buildWriteSession(selectedWaveId, update, previous, participantIds);
    boolean interactionEditable = writeSession != null;
    List<J2clInteractionBlipModel> interactionBlips =
        extractInteractionBlips(update.getDocuments(), participantIds, interactionEditable);
    if (previousMatchesWave && previous != null && !previous.getInteractionBlips().isEmpty()) {
      interactionBlips =
          mergePreviousInteractionBlips(
              interactionBlips,
              update.getDocuments(),
              previous.getInteractionBlips(),
              participantIds,
              interactionEditable);
    }

    String detailText = buildDetailText(update);
    String baseStatusText =
        reconnectCount > 0 ? "Live updates reconnected." : "Live updates connected.";

    int unreadCount;
    boolean read;
    boolean readStateKnown;
    boolean readStateMatchesWave = readState != null
        && selectedWaveId != null
        && selectedWaveId.equals(readState.getWaveId());
    if (readStateMatchesWave) {
      unreadCount = Math.max(0, readState.getUnreadCount());
      read = readState.isRead() || unreadCount <= 0;
      readStateKnown = true;
    } else if (previousMatchesWave && previous.isReadStateKnown()) {
      unreadCount = previous.getUnreadCount();
      read = previous.isRead();
      readStateKnown = true;
    } else {
      unreadCount = J2clSelectedWaveModel.UNKNOWN_UNREAD_COUNT;
      read = false;
      readStateKnown = false;
    }
    // Only surface the "stale" banner when we actually have a known read-state
    // to be stale about — an initial fetch failure with no prior snapshot would
    // otherwise claim the unread count is stale even though no count exists.
    String statusText =
        (readStateKnown && readStateStale)
            ? appendStatus(baseStatusText, "Unread count may be stale.")
            : baseStatusText;

    return new J2clSelectedWaveModel(
        true,
        false,
        false,
        selectedWaveId,
        resolveTitle(selectedWaveId, digestItem),
        resolveSnippet(digestItem, contentEntries),
        resolveUnreadText(digestItem, unreadCount, read, readStateKnown),
        statusText,
        detailText,
        reconnectCount,
        participantIds,
        contentEntries,
        readBlips,
        viewportState,
        interactionBlips,
        writeSession,
        unreadCount,
        read,
        readStateKnown,
        readStateKnown && readStateStale)
        // J-UI-4 (#1082, R-3.1): plumb the parsed manifest through so
        // J2clSelectedWaveView can publish it to the renderer ahead of
        // each render pass. review-1089 round-3 (codex P1 +
        // coderabbitai): if the current update omits the conversation
        // document (live/fragment-only updates do this), preserve the
        // previous wave's manifest so the next renderWindow() does not
        // fall back to flat threading until a full snapshot resends.
        .withConversationManifest(
            chooseManifest(update.getConversationManifest(), previousMatchesWave, previous));
  }

  /**
   * J-UI-4 (#1082, R-3.1): if the new update has no conversation
   * document (manifest is empty), keep the previous same-wave model's
   * manifest. A non-empty new manifest always wins (so manifest edits
   * do propagate). Empty + no previous-same-wave = empty.
   */
  private static SidecarConversationManifest chooseManifest(
      SidecarConversationManifest fromUpdate,
      boolean previousMatchesWave,
      J2clSelectedWaveModel previous) {
    if (fromUpdate != null && !fromUpdate.isEmpty()) {
      return fromUpdate;
    }
    if (previousMatchesWave && previous != null) {
      SidecarConversationManifest previousManifest = previous.getConversationManifest();
      if (previousManifest != null && !previousManifest.isEmpty()) {
        return previousManifest;
      }
    }
    return SidecarConversationManifest.empty();
  }

  /**
   * Re-projects the previous model with a new read-state snapshot. Used when a
   * read-state fetch completes between selected-wave updates — there is no new
   * {@link SidecarSelectedWaveUpdate} to feed into
   * {@link #project(String, J2clSearchDigestItem, SidecarSelectedWaveUpdate,
   * J2clSelectedWaveModel, int, SidecarSelectedWaveReadState, boolean)}.
   */
  public static J2clSelectedWaveModel reprojectReadState(
      J2clSelectedWaveModel previous,
      J2clSearchDigestItem digestItem,
      SidecarSelectedWaveReadState readState,
      boolean readStateStale) {
    if (previous == null || !previous.hasSelection()) {
      return previous;
    }
    int unreadCount;
    boolean read;
    boolean readStateKnown;
    boolean readStateMatchesWave = readState != null
        && previous.getSelectedWaveId() != null
        && previous.getSelectedWaveId().equals(readState.getWaveId());
    if (readStateMatchesWave) {
      unreadCount = Math.max(0, readState.getUnreadCount());
      read = readState.isRead() || unreadCount <= 0;
      readStateKnown = true;
    } else {
      unreadCount = previous.getUnreadCount();
      read = previous.isRead();
      readStateKnown = previous.isReadStateKnown();
    }
    String staleSuffix = " Unread count may be stale.";
    String baseStatus = previous.getStatusText();
    if (baseStatus.endsWith(staleSuffix)) {
      baseStatus = baseStatus.substring(0, baseStatus.length() - staleSuffix.length());
    }
    String statusText = (readStateKnown && readStateStale)
        ? appendStatus(baseStatus, "Unread count may be stale.")
        : baseStatus;
    return new J2clSelectedWaveModel(
        previous.hasSelection(),
        previous.isLoading(),
        previous.isError(),
        previous.getSelectedWaveId(),
        previous.getTitleText(),
        previous.getSnippetText(),
        resolveUnreadText(digestItem, unreadCount, read, readStateKnown),
        statusText,
        previous.getDetailText(),
        previous.getReconnectCount(),
        previous.getParticipantIds(),
        previous.getContentEntries(),
        previous.getReadBlips(),
        previous.getViewportState(),
        previous.getInteractionBlips(),
        previous.getWriteSession(),
        unreadCount,
        read,
        readStateKnown,
        readStateKnown && readStateStale)
        // J-UI-4 (#1082, R-3.1): preserve manifest across read-state
        // re-projections — read-state updates carry no conversation
        // document and must not flatten threading.
        .withConversationManifest(previous.getConversationManifest());
  }

  private static String appendStatus(String base, String suffix) {
    if (base == null || base.isEmpty()) {
      return suffix;
    }
    return base + " " + suffix;
  }

  private static J2clSidecarWriteSession buildWriteSession(
      String selectedWaveId,
      SidecarSelectedWaveUpdate update,
      J2clSelectedWaveModel previous,
      List<String> participantIds) {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return null;
    }
    String channelId = update.getChannelId();
    if ((channelId == null || channelId.isEmpty())
        && previous != null
        && previous.getWriteSession() != null) {
      channelId = previous.getWriteSession().getChannelId();
    }
    String replyTargetBlipId = resolveReplyTargetBlipId(update);
    if ((replyTargetBlipId == null || replyTargetBlipId.isEmpty())
        && previous != null
        && previous.getWriteSession() != null) {
      replyTargetBlipId = previous.getWriteSession().getReplyTargetBlipId();
    }
    long baseVersion;
    String historyHash;
    long updateVersion = update.getResultingVersion();
    String updateHash = update.getResultingVersionHistoryHash();
    boolean updateHasCoupledPair =
        updateVersion >= 0 && updateHash != null && !updateHash.isEmpty();
    if (updateHasCoupledPair) {
      baseVersion = updateVersion;
      historyHash = updateHash;
    } else if (previous != null && previous.getWriteSession() != null) {
      baseVersion = previous.getWriteSession().getBaseVersion();
      historyHash = previous.getWriteSession().getHistoryHash();
    } else {
      return null;
    }
    if (channelId == null || channelId.isEmpty() || replyTargetBlipId == null || replyTargetBlipId.isEmpty()) {
      return null;
    }
    return new J2clSidecarWriteSession(
        selectedWaveId, channelId, baseVersion, historyHash, replyTargetBlipId, participantIds);
  }

  private static J2clSelectedWaveViewportState projectViewportState(
      SidecarSelectedWaveUpdate update,
      boolean previousMatchesWave,
      J2clSelectedWaveModel previous) {
    J2clSelectedWaveViewportState fragmentState =
        J2clSelectedWaveViewportState.fromFragments(update.getFragments());
    if (fragmentState.hasBlipEntries()) {
      return fragmentState.appendMissingDocuments(update.getDocuments());
    }
    if (previousMatchesWave && previous != null && !previous.getViewportState().isEmpty()) {
      J2clSelectedWaveViewportState mergedState =
          previous.getViewportState().mergeDocuments(update.getDocuments());
      if (!mergedState.isEmpty()) {
        return mergedState;
      }
    }
    J2clSelectedWaveViewportState documentState =
        J2clSelectedWaveViewportState.fromDocuments(update.getDocuments());
    if (!documentState.isEmpty()) {
      return documentState;
    }
    return J2clSelectedWaveViewportState.empty();
  }

  private static String resolveReplyTargetBlipId(SidecarSelectedWaveUpdate update) {
    String preferred = findPreferredDocumentId(update.getDocuments());
    if (preferred != null) {
      return preferred;
    }
    SidecarSelectedWaveFragments fragments = update.getFragments();
    if (fragments == null) {
      return null;
    }
    String fallback = null;
    for (SidecarSelectedWaveFragment fragment : fragments.getEntries()) {
      String blipId = J2clSelectedWaveViewportState.blipIdOrNull(fragment.getSegment());
      if (blipId == null) {
        continue;
      }
      if ("b+root".equals(blipId)) {
        return blipId;
      }
      if (fallback == null && blipId.startsWith("b+")) {
        fallback = blipId;
      }
    }
    return fallback;
  }

  private static String findPreferredDocumentId(List<SidecarSelectedWaveDocument> documents) {
    if (documents == null) {
      return null;
    }
    String fallback = null;
    for (SidecarSelectedWaveDocument document : documents) {
      String documentId = document.getDocumentId();
      if (documentId == null || !documentId.startsWith("b+")) {
        continue;
      }
      if ("b+root".equals(documentId)) {
        return documentId;
      }
      if (fallback == null) {
        fallback = documentId;
      }
    }
    return fallback;
  }

  private static List<String> extractDocumentEntries(List<SidecarSelectedWaveDocument> documents) {
    if (documents == null || documents.isEmpty()) {
      return Collections.emptyList();
    }
    List<String> blipEntries = new ArrayList<String>();
    List<String> fallbackEntries = new ArrayList<String>();
    for (SidecarSelectedWaveDocument document : documents) {
      String textContent = document.getTextContent();
      if (textContent == null || textContent.isEmpty()) {
        continue;
      }
      if (document.getDocumentId() != null && document.getDocumentId().startsWith("b+")) {
        blipEntries.add(textContent);
      } else {
        fallbackEntries.add(textContent);
      }
    }
    return blipEntries.isEmpty() ? fallbackEntries : blipEntries;
  }

  private static List<J2clReadBlip> extractDocumentReadBlips(
      List<SidecarSelectedWaveDocument> documents) {
    if (documents == null || documents.isEmpty()) {
      return Collections.emptyList();
    }
    List<J2clReadBlip> blips = new ArrayList<J2clReadBlip>();
    for (SidecarSelectedWaveDocument document : documents) {
      String documentId = document.getDocumentId();
      String textContent = document.getTextContent();
      if (documentId == null || !documentId.startsWith("b+")
          || textContent == null || textContent.isEmpty()) {
        continue;
      }
      blips.add(
          new J2clReadBlip(
              documentId,
              textContent,
              Collections.<org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel>emptyList(),
              /* authorId= */ document.getAuthor() == null ? "" : document.getAuthor(),
              /* authorDisplayName= */ document.getAuthor() == null ? "" : document.getAuthor(),
              /* lastModifiedTimeMillis= */ document.getLastModifiedTime(),
              /* parentBlipId= */ "",
              /* threadId= */ "",
              /* unread= */ false,
              /* hasMention= */ documentHasMention(document),
              /* deleted= */ documentIsDeleted(document)));
    }
    return blips;
  }

  /**
   * F-2 (#1037, R-3.1) — when the viewport-derived read-blip list carries no
   * per-blip metadata (because {@link J2clSelectedWaveViewportState} only
   * knows the segment + raw text), enrich each entry by looking up the
   * matching {@link SidecarSelectedWaveDocument} from the same update. The
   * document carries author, timestamp, and annotation ranges. Blips that do
   * not have a matching document (e.g. placeholder-replaced fragments fetched
   * via {@code /fragments}) are returned unchanged.
   */
  static List<J2clReadBlip> enrichReadBlipMetadata(
      List<J2clReadBlip> readBlips, List<SidecarSelectedWaveDocument> documents) {
    if (readBlips == null || readBlips.isEmpty()
        || documents == null || documents.isEmpty()) {
      return readBlips;
    }
    Map<String, SidecarSelectedWaveDocument> docsByBlipId =
        new LinkedHashMap<String, SidecarSelectedWaveDocument>();
    for (SidecarSelectedWaveDocument doc : documents) {
      if (doc == null || doc.getDocumentId() == null
          || !doc.getDocumentId().startsWith("b+")) {
        continue;
      }
      docsByBlipId.put(doc.getDocumentId(), doc);
    }
    if (docsByBlipId.isEmpty()) {
      return readBlips;
    }
    List<J2clReadBlip> enriched = new ArrayList<J2clReadBlip>(readBlips.size());
    for (J2clReadBlip blip : readBlips) {
      SidecarSelectedWaveDocument doc = docsByBlipId.get(blip.getBlipId());
      if (doc == null) {
        enriched.add(blip);
        continue;
      }
      // Preserve the viewport-decoded text and attachments; only graft on the
      // per-blip metadata that the document carries authoritatively.
      String author = doc.getAuthor() == null ? "" : doc.getAuthor();
      enriched.add(
          new J2clReadBlip(
              blip.getBlipId(),
              blip.getText(),
              blip.getAttachments(),
              author,
              author,
              doc.getLastModifiedTime(),
              /* parentBlipId= */ blip.getParentBlipId(),
              /* threadId= */ blip.getThreadId(),
              blip.isUnread(),
              documentHasMention(doc),
              /* deleted= */ documentIsDeleted(doc) || blip.isDeleted()));
    }
    return enriched;
  }

  /**
   * J-UI-4 (#1082, R-3.1) — applies the parsed conversation manifest
   * to a list of read blips. For each manifest entry in DFS pre-order:
   *
   * <ul>
   *   <li>If a matching read blip exists, emit it with the manifest's
   *       {@code parentBlipId} / {@code threadId} grafted on (other
   *       fields preserved from enrichment).
   *   <li>If no matching read blip exists (manifest references a blip
   *       whose document hasn't arrived in this update yet), emit a
   *       placeholder {@link J2clReadBlip} so the user sees the
   *       conversation skeleton; the next update will fill in the
   *       text once the fragment is loaded.
   * </ul>
   *
   * <p>Blips that exist in {@code readBlips} but are not in the
   * manifest (e.g. data documents, stray blips with no conversation
   * entry) are appended at the end in their original order so no
   * content is dropped silently.
   *
   * <p>An empty manifest is a no-op — the projector falls back to
   * the document/viewport ordering already in {@code readBlips}.
   */
  static List<J2clReadBlip> applyConversationManifest(
      List<J2clReadBlip> readBlips, SidecarConversationManifest manifest) {
    if (manifest == null || manifest.isEmpty()) {
      return readBlips;
    }
    if (readBlips == null) {
      readBlips = Collections.emptyList();
    }
    Map<String, J2clReadBlip> blipsByBlipId = new LinkedHashMap<String, J2clReadBlip>();
    for (J2clReadBlip blip : readBlips) {
      if (blip != null && blip.getBlipId() != null && !blip.getBlipId().isEmpty()) {
        blipsByBlipId.put(blip.getBlipId(), blip);
      }
    }
    List<J2clReadBlip> ordered = new ArrayList<J2clReadBlip>(readBlips.size());
    Set<String> seenBlipIds = new LinkedHashSet<String>();
    for (SidecarConversationManifest.Entry entry : manifest.getOrderedEntries()) {
      if (entry == null || entry.getBlipId().isEmpty()) {
        continue;
      }
      String blipId = entry.getBlipId();
      // review-1089 round-2 (Copilot): a malformed manifest with the
      // same blip id repeated would otherwise emit the blip twice.
      // Manifest.of() already dedupes its by-id map, but the ordered
      // list must also be deduped here so the read surface stays
      // single-instance even if a future manifest source skips the
      // de-dup.
      if (!seenBlipIds.add(blipId)) {
        continue;
      }
      J2clReadBlip existing = blipsByBlipId.get(blipId);
      if (existing == null) {
        ordered.add(
            new J2clReadBlip(
                blipId,
                /* text= */ "",
                java.util.Collections.<org.waveprotocol.box.j2cl.attachment.J2clAttachmentRenderModel>emptyList(),
                /* authorId= */ "",
                /* authorDisplayName= */ "",
                /* lastModifiedTimeMillis= */ 0L,
                entry.getParentBlipId(),
                entry.getThreadId(),
                /* unread= */ false,
                /* hasMention= */ false,
                /* deleted= */ false));
        continue;
      }
      ordered.add(
          new J2clReadBlip(
              existing.getBlipId(),
              existing.getText(),
              existing.getAttachments(),
              existing.getAuthorId(),
              existing.getAuthorDisplayName(),
              existing.getLastModifiedTimeMillis(),
              entry.getParentBlipId(),
              entry.getThreadId(),
              existing.isUnread(),
              existing.hasMention(),
              existing.isDeleted()));
    }
    // Append any blip that the manifest didn't reference so we don't
    // silently drop content from non-conversational data documents.
    for (J2clReadBlip blip : readBlips) {
      if (blip != null
          && blip.getBlipId() != null
          && !blip.getBlipId().isEmpty()
          && seenBlipIds.add(blip.getBlipId())) {
        ordered.add(blip);
      }
    }
    return Collections.unmodifiableList(ordered);
  }

  /**
   * F-2 (#1037, R-3.4 E.6 / E.7) — a blip "has a mention" when any annotation
   * carries the {@code mention/} key prefix. The annotation ranges are already
   * shipped on the wire today via {@link SidecarSelectedWaveDocument} for the
   * interaction-blip projection; reading them here keeps the read-surface
   * mention navigation in lockstep with the interaction surface.
   */
  static boolean documentHasMention(SidecarSelectedWaveDocument document) {
    if (document == null || document.getAnnotationRanges() == null) {
      return false;
    }
    for (SidecarAnnotationRange range : document.getAnnotationRanges()) {
      if (range != null && range.isMention()) {
        return true;
      }
    }
    return false;
  }

  /**
   * F-3.S4 (#1038, R-5.6 F.6 — review-1077 Bug 1): a blip is "deleted"
   * when its annotation ranges include a {@code tombstone/deleted=true}
   * span (the F.6 delete annotation written by
   * {@link org.waveprotocol.box.j2cl.richtext.J2clRichContentDeltaFactory#blipDeleteRequest}).
   * The read renderer filters tombstoned blips out of the surface so
   * deletes propagate visually as soon as the delta replays. Falsy /
   * empty annotation values count as not-deleted to keep the predicate
   * conservative; only an explicit {@code "true"} value tombstones.
   */
  static boolean documentIsDeleted(SidecarSelectedWaveDocument document) {
    if (document == null || document.getAnnotationRanges() == null) {
      return false;
    }
    for (SidecarAnnotationRange range : document.getAnnotationRanges()) {
      if (range == null) {
        continue;
      }
      if ("tombstone/deleted".equals(range.getKey()) && "true".equals(range.getValue())) {
        return true;
      }
    }
    return false;
  }

  private static List<J2clInteractionBlipModel> extractInteractionBlips(
      List<SidecarSelectedWaveDocument> documents,
      List<String> participantIds,
      boolean editable) {
    if (documents == null || documents.isEmpty()) {
      return Collections.emptyList();
    }
    Map<String, List<SidecarReactionEntry>> reactionsByBlip =
        extractReactionsByBlip(documents);
    List<J2clInteractionBlipModel> blips = new ArrayList<J2clInteractionBlipModel>();
    for (SidecarSelectedWaveDocument document : documents) {
      if (document == null) {
        continue;
      }
      String documentId = document.getDocumentId();
      if (documentId == null || !documentId.startsWith("b+")) {
        continue;
      }
      List<SidecarReactionEntry> reactions = reactionsByBlip.get(documentId);
      blips.add(
          new J2clInteractionBlipModel(
              documentId,
              documentId,
              document.getAuthor(),
              document.getTextContent(),
              participantIds,
              editable,
              document.getAnnotationRanges(),
              reactions == null ? Collections.<SidecarReactionEntry>emptyList() : reactions));
    }
    return blips;
  }

  private static List<J2clInteractionBlipModel> mergePreviousInteractionBlips(
      List<J2clInteractionBlipModel> projectedBlips,
      List<SidecarSelectedWaveDocument> documents,
      List<J2clInteractionBlipModel> previousBlips,
      List<String> participantIds,
      boolean editable) {
    // Rebuild carried-forward blips so read-only state and participant context follow the
    // current selected-wave snapshot instead of stale per-blip state.
    Map<String, List<SidecarReactionEntry>> reactionsByBlip = extractReactionsByBlip(documents);
    Map<String, J2clInteractionBlipModel> previousByBlip = indexInteractionBlips(previousBlips);
    List<J2clInteractionBlipModel> merged = new ArrayList<J2clInteractionBlipModel>();
    Set<String> seenBlipIds = new LinkedHashSet<String>();
    for (J2clInteractionBlipModel projected : projectedBlips) {
      J2clInteractionBlipModel previous = previousByBlip.get(projected.getBlipId());
      List<SidecarReactionEntry> reactions = projected.getReactionEntries();
      if (reactions.isEmpty()
          && previous != null
          && !reactionsByBlip.containsKey(projected.getBlipId())) {
        reactions = previous.getReactionEntries();
      }
      merged.add(
          rebuildInteractionBlip(projected, participantIds, editable, reactions));
      seenBlipIds.add(projected.getBlipId());
    }
    for (J2clInteractionBlipModel previous : previousBlips) {
      if (seenBlipIds.contains(previous.getBlipId())) {
        continue;
      }
      List<SidecarReactionEntry> reactions =
          reactionsByBlip.containsKey(previous.getBlipId())
              ? reactionsByBlip.get(previous.getBlipId())
              : previous.getReactionEntries();
      merged.add(
          rebuildInteractionBlip(previous, participantIds, editable, reactions));
    }
    return merged;
  }

  private static J2clInteractionBlipModel rebuildInteractionBlip(
      J2clInteractionBlipModel source,
      List<String> participantIds,
      boolean editable,
      List<SidecarReactionEntry> reactions) {
    return new J2clInteractionBlipModel(
        source.getBlipId(),
        source.getDocumentId(),
        source.getAuthor(),
        source.getText(),
        participantIds,
        editable,
        source.getAnnotationRanges(),
        reactions);
  }

  private static Map<String, List<SidecarReactionEntry>> extractReactionsByBlip(
      List<SidecarSelectedWaveDocument> documents) {
    Map<String, List<SidecarReactionEntry>> reactionsByBlip =
        new LinkedHashMap<String, List<SidecarReactionEntry>>();
    if (documents == null) {
      return reactionsByBlip;
    }
    for (SidecarSelectedWaveDocument document : documents) {
      if (document == null || !document.isReactionDataDocument()) {
        continue;
      }
      reactionsByBlip.put(document.getReactionTargetBlipId(), document.getReactionEntries());
    }
    return reactionsByBlip;
  }

  private static Map<String, J2clInteractionBlipModel> indexInteractionBlips(
      List<J2clInteractionBlipModel> blips) {
    Map<String, J2clInteractionBlipModel> indexed =
        new LinkedHashMap<String, J2clInteractionBlipModel>();
    if (blips == null) {
      return indexed;
    }
    for (J2clInteractionBlipModel blip : blips) {
      if (blip == null || blip.getBlipId() == null) {
        continue;
      }
      indexed.put(blip.getBlipId(), blip);
    }
    return indexed;
  }

  private static String buildDetailText(SidecarSelectedWaveUpdate update) {
    StringBuilder detail = new StringBuilder();
    if (update.getWaveletName() != null && !update.getWaveletName().isEmpty()) {
      detail.append(update.getWaveletName());
    }
    if (update.getChannelId() != null && !update.getChannelId().isEmpty()) {
      if (detail.length() > 0) {
        detail.append(" · ");
      }
      detail.append("channel ").append(update.getChannelId());
    }
    if (update.getFragments() != null && update.getFragments().getSnapshotVersion() > 0) {
      if (detail.length() > 0) {
        detail.append(" · ");
      }
      detail.append("snapshot v").append(update.getFragments().getSnapshotVersion());
    }
    return detail.toString();
  }

  private static String resolveTitle(String selectedWaveId, J2clSearchDigestItem digestItem) {
    if (digestItem != null && digestItem.getTitle() != null && !digestItem.getTitle().isEmpty()) {
      return digestItem.getTitle();
    }
    return selectedWaveId == null ? "Selected wave" : selectedWaveId;
  }

  private static String resolveSnippet(J2clSearchDigestItem digestItem, List<String> contentEntries) {
    if (digestItem != null && digestItem.getSnippet() != null && !digestItem.getSnippet().isEmpty()) {
      return digestItem.getSnippet();
    }
    return contentEntries.isEmpty() ? "" : contentEntries.get(0);
  }

  private static String resolveUnreadText(
      J2clSearchDigestItem digestItem, int unreadCount, boolean read, boolean readStateKnown) {
    if (readStateKnown) {
      return J2clSelectedWaveModel.formatUnreadText(unreadCount, read);
    }
    // Digest fallback for the pre-fetch window only. Once the server responds,
    // the authoritative copy above overwrites this.
    if (digestItem == null) {
      return "";
    }
    int digestUnreadCount = digestItem.getUnreadCount();
    return digestUnreadCount <= 0
        ? "Selected digest is read."
        : digestUnreadCount + " unread in the selected digest.";
  }
}
