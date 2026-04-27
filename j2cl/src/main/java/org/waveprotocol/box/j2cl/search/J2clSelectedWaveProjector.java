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
        readStateKnown && readStateStale);
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
        readStateKnown && readStateStale);
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
              /* hasMention= */ documentHasMention(document)));
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
              documentHasMention(doc)));
    }
    return enriched;
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
