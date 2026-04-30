package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.waveprotocol.box.j2cl.overlay.J2clInteractionBlipModel;
import org.waveprotocol.box.j2cl.overlay.J2clTaskItemModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.read.J2clReadWindowEntry;
import org.waveprotocol.box.j2cl.transport.SidecarAnnotationRange;
import org.waveprotocol.box.j2cl.transport.SidecarConversationManifest;
import org.waveprotocol.box.j2cl.transport.SidecarReactionEntry;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveReadState;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.viewport.J2clViewportGrowthDirection;

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
    // G-PORT-3 (#1112): waves authored in the same browser session can arrive
    // through the viewport channel before every per-blip document field is
    // populated. The selected digest already carries wave-author and
    // last-modified metadata from the search rail; use it as a conservative
    // display/parity fallback without overwriting richer per-blip metadata.
    readBlips = applyDigestMetadataFallback(readBlips, digestItem);
    // J-UI-4 (#1082, R-3.1) — apply the parsed conversation manifest:
    // graft parent-blip-id + thread-id onto each read blip and reorder
    // the list into manifest depth-first pre-order traversal so reply
    // ordering matches the conversation model. Empty manifest = no
    // change (fall back to enrichment-derived ordering).
    // review-1089 round-4 (codex P1 + coderabbitai major): use the
    // *effective* manifest (chooseManifest) here too — same-wave
    // fragment-only updates omit the conversation document, and
    // passing an empty manifest would leave readBlips flat until a
    // full snapshot resends. The same effective manifest is then
    // stored on the model below so ordering and stored state agree.
    // review-1089 round-5 (coderabbit P2): skip manifest expansion on
    // the viewport-window path — renderWindow() reads threadId/parentBlipId
    // directly from the stored conversationManifest rather than from
    // model.getReadBlips(), so allocating full-conversation placeholder
    // blips here is wasted work that grows linearly with wave size.
    SidecarConversationManifest manifestFromUpdate = updateManifest(update);
    SidecarConversationManifest effectiveManifest =
        chooseManifest(manifestFromUpdate, previousMatchesWave, previous);
    String lockState = chooseLockState(update, previousMatchesWave, previous);
    if (!hasViewportWindow) {
      readBlips = applyConversationManifest(readBlips, effectiveManifest);
    }
    J2clSidecarWriteSession writeSession =
        buildWriteSession(
            selectedWaveId, update, previous, participantIds, manifestFromUpdate);
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
        .withConversationManifest(effectiveManifest)
        .withLockState(lockState);
  }

  private static String chooseLockState(
      SidecarSelectedWaveUpdate update,
      boolean previousMatchesWave,
      J2clSelectedWaveModel previous) {
    String fromUpdate = lockStateFromDocuments(update.getDocuments());
    if (fromUpdate != null) {
      return fromUpdate;
    }
    if (previousMatchesWave && previous != null) {
      return previous.getLockState();
    }
    return SidecarSelectedWaveDocument.LOCK_STATE_UNLOCKED;
  }

  private static String lockStateFromDocuments(List<SidecarSelectedWaveDocument> documents) {
    if (documents == null) {
      return null;
    }
    for (SidecarSelectedWaveDocument document : documents) {
      if (document != null && "m/lock".equals(document.getDocumentId())) {
        return document.getLockState();
      }
    }
    return null;
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

  private static SidecarConversationManifest updateManifest(SidecarSelectedWaveUpdate update) {
    if (update == null) {
      return SidecarConversationManifest.empty();
    }
    SidecarConversationManifest fromDocuments = update.getConversationManifest();
    if (fromDocuments != null && !fromDocuments.isEmpty()) {
      return fromDocuments;
    }
    return SidecarConversationManifest.fromFragments(update.getFragments());
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
        .withConversationManifest(previous.getConversationManifest())
        .withLockState(previous.getLockState());
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
      List<String> participantIds,
      SidecarConversationManifest manifestFromUpdate) {
    if (selectedWaveId == null || selectedWaveId.isEmpty()) {
      return null;
    }
    J2clSidecarWriteSession previousWriteSession =
        previous == null ? null : previous.getWriteSession();
    String channelId = update.getChannelId();
    if ((channelId == null || channelId.isEmpty())
        && previousWriteSession != null) {
      channelId = previousWriteSession.getChannelId();
    }
    String replyTargetBlipId = resolveReplyTargetBlipId(update);
    if ((replyTargetBlipId == null || replyTargetBlipId.isEmpty())
        && previousWriteSession != null) {
      replyTargetBlipId = previousWriteSession.getReplyTargetBlipId();
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
    } else if (previousWriteSession != null) {
      baseVersion = previousWriteSession.getBaseVersion();
      historyHash = previousWriteSession.getHistoryHash();
    } else {
      return null;
    }
    if (channelId == null || channelId.isEmpty() || replyTargetBlipId == null || replyTargetBlipId.isEmpty()) {
      return null;
    }
    int replyManifestInsertPosition = -1;
    int replyManifestItemCount = -1;
    // Rendering may reuse a cached manifest on live blip-only updates, but submit offsets must
    // only come from a manifest coupled to the same base version/hash as the write session.
    SidecarConversationManifest writeManifest =
        updateHasCoupledPair && manifestFromUpdate != null && !manifestFromUpdate.isEmpty()
            ? manifestFromUpdate
            : SidecarConversationManifest.empty();
    if (!writeManifest.isEmpty()) {
      SidecarConversationManifest.Entry replyTarget = writeManifest.findByBlipId(replyTargetBlipId);
      if (replyTarget != null) {
        replyManifestInsertPosition = replyTarget.getReplyInsertPosition();
        replyManifestItemCount = writeManifest.getItemCount();
      }
    } else if (!updateHasCoupledPair
        && previousWriteSession != null
        && replyTargetBlipId.equals(previousWriteSession.getReplyTargetBlipId())) {
      replyManifestInsertPosition = previousWriteSession.getReplyManifestInsertPosition();
      replyManifestItemCount = previousWriteSession.getReplyManifestItemCount();
    }
    return new J2clSidecarWriteSession(
        selectedWaveId,
        channelId,
        baseVersion,
        historyHash,
        replyTargetBlipId,
        participantIds,
        replyManifestInsertPosition,
        replyManifestItemCount);
  }

  private static J2clSelectedWaveViewportState projectViewportState(
      SidecarSelectedWaveUpdate update,
      boolean previousMatchesWave,
      J2clSelectedWaveModel previous) {
    J2clSelectedWaveViewportState fragmentState =
        J2clSelectedWaveViewportState.fromFragments(update.getFragments());
    if (fragmentState.hasBlipEntries()) {
      if (canMergeAsLiveBlipFragments(update.getFragments(), fragmentState, previousMatchesWave, previous)) {
        return previous
            .getViewportState()
            .mergeFragments(update.getFragments(), J2clViewportGrowthDirection.FORWARD)
            .appendMissingDocuments(update.getDocuments());
      }
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

  /**
   * Returns true when the incoming fragment payload is a pure incremental live-blip delta that
   * should extend the previous viewport window via {@code mergeFragments}.
   *
   * <p>Two conditions must both hold:
   * <ol>
   *   <li>All entries in the incoming fragment state are blip entries (no index / manifest
   *       ranges). Full-window snapshots include metadata ranges and are authoritative — they
   *       must replace, not extend, the prior viewport.
   *   <li>The fragment's {@code snapshotVersion} is negative (typically {@code -1}, the default
   *       the transport codec injects when the server omits the field). A non-negative snapshot
   *       version signals a bounded full-window snapshot (e.g. on open or reconnect);
   *       even if it is blip-only, such a payload defines an authoritative new window and must
   *       replace the old one so stale blips from the previous window do not remain visible.
   * </ol>
   */
  private static boolean canMergeAsLiveBlipFragments(
      SidecarSelectedWaveFragments fragments,
      J2clSelectedWaveViewportState fragmentState,
      boolean previousMatchesWave,
      J2clSelectedWaveModel previous) {
    if (!previousMatchesWave || previous == null || previous.getViewportState().isEmpty()) {
      return false;
    }
    // Full-window snapshots carry a non-negative snapshotVersion; they are authoritative and must
    // replace the previous viewport rather than merge into it.
    if (fragments != null && fragments.getSnapshotVersion() >= 0) {
      return false;
    }
    // Full selected-wave viewport windows include metadata/index ranges and are authoritative.
    // Pure blip payloads are incremental live fragments and should extend the prior window.
    boolean sawBlip = false;
    for (J2clSelectedWaveViewportState.Entry entry : fragmentState.getEntries()) {
      if (!entry.isBlip()) {
        return false;
      }
      sawBlip = true;
    }
    return sawBlip;
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
              /* deleted= */ documentIsDeleted(document),
              /* taskDone= */ documentTaskDone(document),
              /* taskAssignee= */ documentTaskAssignee(document),
              /* taskDueTimestamp= */ documentTaskDueTimestamp(document),
              /* bodyItemCount= */ document.getBodyItemCount()));
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
              /* deleted= */ documentIsDeleted(doc) || blip.isDeleted(),
              /* taskDone= */ documentTaskDone(doc),
              /* taskAssignee= */ documentTaskAssignee(doc),
              /* taskDueTimestamp= */ documentTaskDueTimestamp(doc),
              /* bodyItemCount= */ doc.getBodyItemCount()));
    }
    return enriched;
  }

  static List<J2clReadBlip> applyDigestMetadataFallback(
      List<J2clReadBlip> readBlips, J2clSearchDigestItem digestItem) {
    if (readBlips == null || readBlips.isEmpty() || digestItem == null) {
      return readBlips;
    }
    String fallbackAuthor = digestItem.getAuthor() == null ? "" : digestItem.getAuthor();
    long fallbackModified = Math.max(0L, digestItem.getLastModified());
    if (fallbackAuthor.isEmpty() && fallbackModified <= 0L) {
      return readBlips;
    }
    List<J2clReadBlip> patched = new ArrayList<J2clReadBlip>(readBlips.size());
    boolean changed = false;
    for (J2clReadBlip blip : readBlips) {
      if (blip == null) {
        patched.add(blip);
        continue;
      }
      String authorId = blip.getAuthorId();
      String authorDisplayName = blip.getAuthorDisplayName();
      long lastModified = blip.getLastModifiedTimeMillis();
      if ((authorId == null || authorId.isEmpty()) && !fallbackAuthor.isEmpty()) {
        authorId = fallbackAuthor;
      }
      if ((authorDisplayName == null || authorDisplayName.isEmpty()) && !fallbackAuthor.isEmpty()) {
        authorDisplayName = fallbackAuthor;
      }
      if (lastModified <= 0L && fallbackModified > 0L) {
        lastModified = fallbackModified;
      }
      if (!Objects.equals(authorId, blip.getAuthorId())
          || !Objects.equals(authorDisplayName, blip.getAuthorDisplayName())
          || lastModified != blip.getLastModifiedTimeMillis()) {
        changed = true;
        patched.add(
            new J2clReadBlip(
                blip.getBlipId(),
                blip.getText(),
                blip.getAttachments(),
                authorId,
                authorDisplayName,
                lastModified,
                blip.getParentBlipId(),
                blip.getThreadId(),
                blip.isUnread(),
                blip.hasMention(),
                blip.isDeleted(),
                blip.isTaskDone(),
                blip.getTaskAssignee(),
                blip.getTaskDueTimestamp(),
                blip.getBodyItemCount()));
      } else {
        patched.add(blip);
      }
    }
    return changed ? patched : readBlips;
  }

  static List<J2clReadBlip> applyViewportMetadataFallbacks(
      List<J2clReadBlip> viewportReadBlips,
      List<J2clReadBlip> previousReadBlips,
      J2clSearchDigestItem digestItem) {
    return applyViewportMetadataFallbacks(
        viewportReadBlips,
        previousReadBlips,
        digestItem,
        /* preserveFallbackBooleans= */ false);
  }

  static List<J2clReadBlip> applyViewportMetadataFallbacks(
      List<J2clReadBlip> viewportReadBlips,
      List<J2clReadBlip> previousReadBlips,
      J2clSearchDigestItem digestItem,
      boolean preserveFallbackBooleans) {
    List<J2clReadBlip> patched =
        applyPreviousReadBlipMetadataFallback(
            viewportReadBlips, previousReadBlips, preserveFallbackBooleans);
    return applyDigestMetadataFallback(patched, digestItem);
  }

  private static List<J2clReadBlip> applyPreviousReadBlipMetadataFallback(
      List<J2clReadBlip> readBlips,
      List<J2clReadBlip> previousReadBlips,
      boolean preserveFallbackBooleans) {
    if (readBlips == null || readBlips.isEmpty()
        || previousReadBlips == null || previousReadBlips.isEmpty()) {
      return readBlips;
    }
    Map<String, J2clReadBlip> previousById = new LinkedHashMap<String, J2clReadBlip>();
    for (J2clReadBlip previous : previousReadBlips) {
      if (previous == null || previous.getBlipId() == null || previous.getBlipId().isEmpty()) {
        continue;
      }
      previousById.put(previous.getBlipId(), previous);
    }
    if (previousById.isEmpty()) {
      return readBlips;
    }
    List<J2clReadBlip> patched = new ArrayList<J2clReadBlip>(readBlips.size());
    boolean changed = false;
    for (J2clReadBlip blip : readBlips) {
      if (blip == null || blip.getBlipId() == null || blip.getBlipId().isEmpty()) {
        patched.add(blip);
        continue;
      }
      J2clReadBlip previous = previousById.get(blip.getBlipId());
      if (previous == null) {
        patched.add(blip);
        continue;
      }
      J2clReadBlip merged = copyMissingMetadata(blip, previous, preserveFallbackBooleans);
      patched.add(merged);
      changed = changed || merged != blip;
    }
    return changed ? patched : readBlips;
  }

  private static J2clReadBlip copyMissingMetadata(
      J2clReadBlip blip, J2clReadBlip fallback, boolean preserveFallbackBooleans) {
    String authorId = blip.getAuthorId();
    if ((authorId == null || authorId.isEmpty()) && !fallback.getAuthorId().isEmpty()) {
      authorId = fallback.getAuthorId();
    }
    String authorDisplayName = blip.getAuthorDisplayName();
    if ((authorDisplayName == null || authorDisplayName.isEmpty())
        && !fallback.getAuthorDisplayName().isEmpty()) {
      authorDisplayName = fallback.getAuthorDisplayName();
    }
    long lastModified = blip.getLastModifiedTimeMillis();
    if (lastModified <= 0L && fallback.getLastModifiedTimeMillis() > 0L) {
      lastModified = fallback.getLastModifiedTimeMillis();
    }
    String parentBlipId = blip.getParentBlipId();
    if ((parentBlipId == null || parentBlipId.isEmpty())
        && !fallback.getParentBlipId().isEmpty()) {
      parentBlipId = fallback.getParentBlipId();
    }
    String threadId = blip.getThreadId();
    if ((threadId == null || threadId.isEmpty()) && !fallback.getThreadId().isEmpty()) {
      threadId = fallback.getThreadId();
    }
    String taskAssignee = blip.getTaskAssignee();
    if ((taskAssignee == null || taskAssignee.isEmpty()) && !fallback.getTaskAssignee().isEmpty()) {
      taskAssignee = fallback.getTaskAssignee();
    }
    long taskDueTimestamp = blip.getTaskDueTimestamp();
    if (taskDueTimestamp == J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP
        && fallback.getTaskDueTimestamp() != J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP) {
      taskDueTimestamp = fallback.getTaskDueTimestamp();
    }
    // Boolean flags have no "missing" sentinel in J2clReadBlip. The default
    // contract treats the incoming blip as authoritative; fragment-growth
    // callers opt in to preserving the current model's booleans because raw
    // viewport fragments carry text/metadata but not read/task/delete state.
    boolean unread = preserveFallbackBooleans ? fallback.isUnread() : blip.isUnread();
    boolean hasMention = preserveFallbackBooleans ? fallback.hasMention() : blip.hasMention();
    boolean deleted = preserveFallbackBooleans ? fallback.isDeleted() : blip.isDeleted();
    boolean taskDone = preserveFallbackBooleans ? fallback.isTaskDone() : blip.isTaskDone();
    if (Objects.equals(authorId, blip.getAuthorId())
        && Objects.equals(authorDisplayName, blip.getAuthorDisplayName())
        && lastModified == blip.getLastModifiedTimeMillis()
        && Objects.equals(parentBlipId, blip.getParentBlipId())
        && Objects.equals(threadId, blip.getThreadId())
        && unread == blip.isUnread()
        && hasMention == blip.hasMention()
        && deleted == blip.isDeleted()
        && taskDone == blip.isTaskDone()
        && Objects.equals(taskAssignee, blip.getTaskAssignee())
        && taskDueTimestamp == blip.getTaskDueTimestamp()) {
      return blip;
    }
    return new J2clReadBlip(
        blip.getBlipId(),
        blip.getText(),
        blip.getAttachments(),
        authorId,
        authorDisplayName,
        lastModified,
        parentBlipId,
        threadId,
        unread,
        hasMention,
        deleted,
        taskDone,
        taskAssignee,
        taskDueTimestamp,
        blip.getBodyItemCount());
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
              existing.isDeleted(),
              existing.isTaskDone(),
              existing.getTaskAssignee(),
              existing.getTaskDueTimestamp(),
              existing.getBodyItemCount()));
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
   * J-UI-6 (#1084, R-5.4) — projects the per-blip metadata held on the
   * already-enriched read-blip list onto matching {@link J2clReadWindowEntry}
   * instances. {@link J2clSelectedWaveView} prefers the window-render path
   * over the flat-render path whenever the viewport state carries any
   * window entries, so the renderer's done-state attribute write only
   * appears in production output if the window entries themselves carry
   * the metadata. Entries with no matching read blip pass through
   * unchanged so placeholder windows / pre-fragment-fetch entries keep
   * working as before.
   *
   * <p>Implementation note — keeping the read-blip list as the source of
   * truth (rather than re-reading the SidecarSelectedWaveDocument list)
   * keeps the window-entry view of metadata consistent with whatever the
   * read-blip enrichment already decided about late-arriving fragments,
   * tombstones, and same-wave merges.
   */
  public static List<J2clReadWindowEntry> enrichWindowEntriesFromReadBlips(
      List<J2clReadWindowEntry> windowEntries, List<J2clReadBlip> readBlips) {
    if (windowEntries == null || windowEntries.isEmpty()) {
      return windowEntries;
    }
    if (readBlips == null || readBlips.isEmpty()) {
      return windowEntries;
    }
    Map<String, J2clReadBlip> blipsById = new LinkedHashMap<String, J2clReadBlip>();
    for (J2clReadBlip blip : readBlips) {
      if (blip == null || blip.getBlipId().isEmpty()) {
        continue;
      }
      blipsById.put(blip.getBlipId(), blip);
    }
    if (blipsById.isEmpty()) {
      return windowEntries;
    }
    List<J2clReadWindowEntry> enriched = new ArrayList<J2clReadWindowEntry>(windowEntries.size());
    boolean changed = false;
    for (J2clReadWindowEntry entry : windowEntries) {
      if (entry == null || !entry.isLoaded()) {
        enriched.add(entry);
        continue;
      }
      J2clReadBlip blip = blipsById.get(entry.getBlipId());
      if (blip == null) {
        enriched.add(entry);
        continue;
      }
      J2clReadWindowEntry next =
          J2clReadWindowEntry.loadedWithTaskMetadata(
              entry.getSegment(),
              entry.getFromVersion(),
              entry.getToVersion(),
              entry.getBlipId(),
              entry.getText(),
              entry.getAttachments(),
              blip.getAuthorId(),
              blip.getAuthorDisplayName(),
              blip.getLastModifiedTimeMillis(),
              blip.getParentBlipId(),
              blip.getThreadId(),
              blip.isUnread(),
              blip.hasMention(),
              blip.isTaskDone(),
              blip.getTaskAssignee(),
              blip.getTaskDueTimestamp(),
              blip.getBodyItemCount());
      enriched.add(next);
      changed = true;
    }
    return changed ? enriched : windowEntries;
  }

  /**
   * F-2 (#1037, R-3.4 E.6 / E.7) plus G-PORT-5 (#1114): a blip "has a mention"
   * only when an annotation carries the {@code mention/} key prefix. Keeping this
   * predicate aligned with GWT's {@code mention/user} annotation avoids false
   * positives from ordinary {@code link/manual} hyperlinks whose visible text
   * happens to start with {@code @}.
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

  /**
   * J-UI-6 (#1084, R-5.4): a blip is "task-done" iff its annotation ranges
   * carry a {@code task/done=true} span. The toggle delta written by
   * {@link org.waveprotocol.box.j2cl.richtext.J2clRichContentDeltaFactory#taskToggleRequest}
   * spans the entire blip body so the predicate is "any range with
   * {@code key == \"task/done\"} and {@code value.equals(\"true\")}". Any other
   * value (including the explicit {@code "false"} that an open-state toggle
   * writes) returns false. Reads directly from the wavelet DocOp so live
   * updates from other clients flip the visual state without a conversation-
   * model round-trip (per project-memory feedback_search_no_conversation_model).
   */
  static boolean documentTaskDone(SidecarSelectedWaveDocument document) {
    if (document == null || document.getAnnotationRanges() == null) {
      return false;
    }
    boolean done = false;
    for (SidecarAnnotationRange range : document.getAnnotationRanges()) {
      if (range == null || !"task/done".equals(range.getKey())) {
        continue;
      }
      // The toggle path writes a single annotation that spans the whole blip;
      // last writer wins if multiple ranges land in one document so the loop
      // tracks the most recent value rather than short-circuiting on the
      // first hit.
      done = "true".equals(range.getValue());
    }
    return done;
  }

  /**
   * J-UI-6 (#1084, R-5.4): persisted task assignee read from the
   * {@code task/assignee} annotation. Returns the empty string when no
   * range carries the key, when the value is null, or when every range
   * carries an empty value (the "unset" sentinel the metadata-write path
   * uses to clear an assignee).
   */
  static String documentTaskAssignee(SidecarSelectedWaveDocument document) {
    if (document == null || document.getAnnotationRanges() == null) {
      return "";
    }
    String assignee = "";
    for (SidecarAnnotationRange range : document.getAnnotationRanges()) {
      if (range == null || !"task/assignee".equals(range.getKey())) {
        continue;
      }
      String value = range.getValue();
      assignee = value == null ? "" : value;
    }
    return assignee;
  }

  /**
   * J-UI-6 (#1084, R-5.4): persisted task due-date timestamp read from the
   * {@code task/dueTs} annotation as ms since epoch. Empty / non-numeric
   * values stay {@link J2clTaskItemModel#UNKNOWN_DUE_TIMESTAMP} so the
   * metadata overlay falls back to the "no due date" UI rather than
   * rendering an epoch-zero placeholder.
   */
  static long documentTaskDueTimestamp(SidecarSelectedWaveDocument document) {
    if (document == null || document.getAnnotationRanges() == null) {
      return J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP;
    }
    long due = J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP;
    for (SidecarAnnotationRange range : document.getAnnotationRanges()) {
      if (range == null || !"task/dueTs".equals(range.getKey())) {
        continue;
      }
      String value = range.getValue();
      if (value == null) {
        continue;
      }
      String trimmed = value.trim();
      if (trimmed.isEmpty()) {
        // Explicit empty-string sentinel resets the due date.
        due = J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP;
        continue;
      }
      try {
        due = Long.parseLong(trimmed);
      } catch (NumberFormatException ignored) {
        // Unparseable values reset to "unset" rather than rendering a
        // corrupt epoch placeholder. The reader is conservative — a
        // single garbage range overrides any prior successfully-parsed
        // value because we cannot tell which one the writer intended.
        due = J2clTaskItemModel.UNKNOWN_DUE_TIMESTAMP;
      }
    }
    return due;
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
