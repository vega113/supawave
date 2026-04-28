package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.overlay.J2clInteractionBlipModel;
import org.waveprotocol.box.j2cl.read.J2clReadBlip;
import org.waveprotocol.box.j2cl.transport.SidecarConversationManifest;

public final class J2clSelectedWaveModel {
  public static final int UNKNOWN_UNREAD_COUNT = -1;

  private final boolean hasSelection;
  private final boolean loading;
  private final boolean error;
  private final String selectedWaveId;
  private final String titleText;
  private final String snippetText;
  private final String unreadText;
  private final String statusText;
  private final String detailText;
  private final int reconnectCount;
  private final List<String> participantIds;
  private final List<String> contentEntries;
  private final List<J2clReadBlip> readBlips;
  private final J2clSelectedWaveViewportState viewportState;
  private final List<J2clInteractionBlipModel> interactionBlips;
  private final J2clSidecarWriteSession writeSession;
  private final int unreadCount;
  private final boolean read;
  private final boolean readStateKnown;
  private final boolean readStateStale;
  // J-UI-4 (#1082, R-3.1): conversation manifest is plumbed through
  // the model so the view can publish it to the read renderer for the
  // viewport-windowed render path. Projector populates from
  // SidecarSelectedWaveUpdate.getConversationManifest().
  private SidecarConversationManifest conversationManifest = SidecarConversationManifest.empty();

  J2clSelectedWaveModel(
      boolean hasSelection,
      boolean loading,
      boolean error,
      String selectedWaveId,
      String titleText,
      String snippetText,
      String unreadText,
      String statusText,
      String detailText,
      int reconnectCount,
      List<String> participantIds,
      List<String> contentEntries,
      J2clSidecarWriteSession writeSession,
      int unreadCount,
      boolean read,
      boolean readStateKnown,
      boolean readStateStale) {
    this(
        hasSelection,
        loading,
        error,
        selectedWaveId,
        titleText,
        snippetText,
        unreadText,
        statusText,
        detailText,
        reconnectCount,
        participantIds,
        contentEntries,
        Collections.<J2clReadBlip>emptyList(),
        J2clSelectedWaveViewportState.empty(),
        writeSession,
        unreadCount,
        read,
        readStateKnown,
        readStateStale);
  }

  J2clSelectedWaveModel(
      boolean hasSelection,
      boolean loading,
      boolean error,
      String selectedWaveId,
      String titleText,
      String snippetText,
      String unreadText,
      String statusText,
      String detailText,
      int reconnectCount,
      List<String> participantIds,
      List<String> contentEntries,
      List<J2clReadBlip> readBlips,
      J2clSidecarWriteSession writeSession,
      int unreadCount,
      boolean read,
      boolean readStateKnown,
      boolean readStateStale) {
    this(
        hasSelection,
        loading,
        error,
        selectedWaveId,
        titleText,
        snippetText,
        unreadText,
        statusText,
        detailText,
        reconnectCount,
        participantIds,
        contentEntries,
        readBlips,
        J2clSelectedWaveViewportState.empty(),
        writeSession,
        unreadCount,
        read,
        readStateKnown,
        readStateStale);
  }

  J2clSelectedWaveModel(
      boolean hasSelection,
      boolean loading,
      boolean error,
      String selectedWaveId,
      String titleText,
      String snippetText,
      String unreadText,
      String statusText,
      String detailText,
      int reconnectCount,
      List<String> participantIds,
      List<String> contentEntries,
      List<J2clReadBlip> readBlips,
      J2clSelectedWaveViewportState viewportState,
      J2clSidecarWriteSession writeSession,
      int unreadCount,
      boolean read,
      boolean readStateKnown,
      boolean readStateStale) {
    this(
        hasSelection,
        loading,
        error,
        selectedWaveId,
        titleText,
        snippetText,
        unreadText,
        statusText,
        detailText,
        reconnectCount,
        participantIds,
        contentEntries,
        readBlips,
        viewportState,
        Collections.<J2clInteractionBlipModel>emptyList(),
        writeSession,
        unreadCount,
        read,
        readStateKnown,
        readStateStale);
  }

  J2clSelectedWaveModel(
      boolean hasSelection,
      boolean loading,
      boolean error,
      String selectedWaveId,
      String titleText,
      String snippetText,
      String unreadText,
      String statusText,
      String detailText,
      int reconnectCount,
      List<String> participantIds,
      List<String> contentEntries,
      List<J2clReadBlip> readBlips,
      J2clSelectedWaveViewportState viewportState,
      List<J2clInteractionBlipModel> interactionBlips,
      J2clSidecarWriteSession writeSession,
      int unreadCount,
      boolean read,
      boolean readStateKnown,
      boolean readStateStale) {
    this.hasSelection = hasSelection;
    this.loading = loading;
    this.error = error;
    this.selectedWaveId = selectedWaveId;
    this.titleText = titleText == null ? "" : titleText;
    this.snippetText = snippetText == null ? "" : snippetText;
    this.unreadText = unreadText == null ? "" : unreadText;
    this.statusText = statusText == null ? "" : statusText;
    this.detailText = detailText == null ? "" : detailText;
    this.reconnectCount = reconnectCount;
    this.participantIds =
        participantIds == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(participantIds));
    this.contentEntries =
        contentEntries == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(contentEntries));
    this.readBlips =
        readBlips == null
            ? Collections.<J2clReadBlip>emptyList()
            : Collections.unmodifiableList(new ArrayList<J2clReadBlip>(readBlips));
    this.viewportState =
        viewportState == null ? J2clSelectedWaveViewportState.empty() : viewportState;
    this.interactionBlips =
        interactionBlips == null
            ? Collections.<J2clInteractionBlipModel>emptyList()
            : Collections.unmodifiableList(new ArrayList<J2clInteractionBlipModel>(interactionBlips));
    this.writeSession = writeSession;
    this.unreadCount = unreadCount;
    this.read = read;
    this.readStateKnown = readStateKnown;
    this.readStateStale = readStateStale;
  }

  public static J2clSelectedWaveModel empty() {
    return emptyModel(null);
  }

  public static J2clSelectedWaveModel clearedSelection() {
    return emptyModel("");
  }

  private static J2clSelectedWaveModel emptyModel(String selectedWaveId) {
    return new J2clSelectedWaveModel(
        false,
        false,
        false,
        selectedWaveId,
        "Select a wave",
        "",
        "",
        "Choose a digest from the search results to open a read-only selected-wave panel.",
        "Copied URLs can restore the selected wave when the route includes it.",
        0,
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        null,
        UNKNOWN_UNREAD_COUNT,
        false,
        false,
        false);
  }

  private static boolean sameWave(String selectedWaveId, J2clSelectedWaveModel previous) {
    return previous != null && selectedWaveId != null
        && selectedWaveId.equals(previous.getSelectedWaveId());
  }

  public static J2clSelectedWaveModel loading(
      String selectedWaveId,
      J2clSearchDigestItem digestItem,
      int reconnectCount,
      J2clSelectedWaveModel previous) {
    boolean sameWave = sameWave(selectedWaveId, previous);
    int prevUnreadCount = sameWave ? previous.getUnreadCount() : UNKNOWN_UNREAD_COUNT;
    boolean prevRead = sameWave && previous.isRead();
    boolean prevKnown = sameWave && previous.isReadStateKnown();
    boolean prevStale = sameWave && previous.isReadStateStale();
    return new J2clSelectedWaveModel(
        true,
        true,
        false,
        selectedWaveId,
        resolveTitle(selectedWaveId, digestItem),
        resolveSnippet(digestItem),
        resolveUnreadText(digestItem, prevUnreadCount, prevRead, prevKnown),
        reconnectCount > 0 ? "Reconnecting selected wave." : "Opening selected wave.",
        reconnectCount > 0
            ? "Reusing the current session after a disconnect."
            : "Waiting for the first live selected-wave update.",
        reconnectCount,
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        null,
        prevUnreadCount,
        prevRead,
        prevKnown,
        prevStale);
  }

  public static J2clSelectedWaveModel error(
      String selectedWaveId,
      J2clSearchDigestItem digestItem,
      String statusText,
      String detailText,
      J2clSelectedWaveModel previous) {
    boolean sameWave = sameWave(selectedWaveId, previous);
    int prevUnreadCount = sameWave ? previous.getUnreadCount() : UNKNOWN_UNREAD_COUNT;
    boolean prevRead = sameWave && previous.isRead();
    boolean prevKnown = sameWave && previous.isReadStateKnown();
    boolean prevStale = sameWave && previous.isReadStateStale();
    return new J2clSelectedWaveModel(
        true,
        false,
        true,
        selectedWaveId,
        resolveTitle(selectedWaveId, digestItem),
        resolveSnippet(digestItem),
        resolveUnreadText(digestItem, prevUnreadCount, prevRead, prevKnown),
        statusText,
        detailText,
        0,
        Collections.<String>emptyList(),
        Collections.<String>emptyList(),
        null,
        prevUnreadCount,
        prevRead,
        prevKnown,
        prevStale);
  }

  private static String resolveTitle(String selectedWaveId, J2clSearchDigestItem digestItem) {
    if (digestItem != null && digestItem.getTitle() != null && !digestItem.getTitle().isEmpty()) {
      return digestItem.getTitle();
    }
    return selectedWaveId == null ? "Selected wave" : selectedWaveId;
  }

  private static String resolveSnippet(J2clSearchDigestItem digestItem) {
    return digestItem == null ? "" : digestItem.getSnippet();
  }

  private static String resolveUnreadText(
      J2clSearchDigestItem digestItem, int unreadCount, boolean read, boolean readStateKnown) {
    if (readStateKnown) {
      return formatUnreadText(unreadCount, read);
    }
    if (digestItem == null) {
      return "";
    }
    int digestUnreadCount = digestItem.getUnreadCount();
    return digestUnreadCount <= 0
        ? "Selected digest is read."
        : digestUnreadCount + " unread in the selected digest.";
  }

  static String formatUnreadText(int unreadCount, boolean read) {
    if (unreadCount <= 0 || read) {
      return "Read.";
    }
    return unreadCount + " unread.";
  }

  public boolean hasSelection() {
    return hasSelection;
  }

  public boolean isLoading() {
    return loading;
  }

  public boolean isError() {
    return error;
  }

  public String getSelectedWaveId() {
    return selectedWaveId;
  }

  public String getTitleText() {
    return titleText;
  }

  public String getSnippetText() {
    return snippetText;
  }

  public String getUnreadText() {
    return unreadText;
  }

  public String getStatusText() {
    return statusText;
  }

  public String getDetailText() {
    return detailText;
  }

  public int getReconnectCount() {
    return reconnectCount;
  }

  public List<String> getParticipantIds() {
    return participantIds;
  }

  public List<String> getContentEntries() {
    return contentEntries;
  }

  public List<J2clReadBlip> getReadBlips() {
    return readBlips;
  }

  /** J-UI-4 (#1082, R-3.1): parsed conversation manifest for the wave. Empty when unavailable. */
  public SidecarConversationManifest getConversationManifest() {
    return conversationManifest;
  }

  /** J-UI-4 (#1082, R-3.1): package-private setter used by the projector. */
  J2clSelectedWaveModel withConversationManifest(SidecarConversationManifest manifest) {
    this.conversationManifest = manifest == null ? SidecarConversationManifest.empty() : manifest;
    return this;
  }

  public J2clSelectedWaveViewportState getViewportState() {
    return viewportState;
  }

  public List<J2clInteractionBlipModel> getInteractionBlips() {
    return interactionBlips;
  }

  public J2clSidecarWriteSession getWriteSession() {
    return writeSession;
  }

  J2clSelectedWaveModel withViewportState(J2clSelectedWaveViewportState nextViewportState) {
    return new J2clSelectedWaveModel(
        hasSelection,
        loading,
        error,
        selectedWaveId,
        titleText,
        snippetText,
        unreadText,
        statusText,
        detailText,
        reconnectCount,
        participantIds,
        nextViewportState == null
            ? contentEntries
            : nextViewportState.getLoadedContentEntries(),
        nextViewportState == null
            ? readBlips
            : nextViewportState.getLoadedReadBlips(),
        nextViewportState == null ? viewportState : nextViewportState,
        interactionBlips,
        writeSession,
        unreadCount,
        read,
        readStateKnown,
        readStateStale)
        // J-UI-4 (#1082, R-3.1): preserve manifest across clones so
        // viewport-windowed renders keep nesting after fragment growth.
        .withConversationManifest(conversationManifest);
  }

  J2clSelectedWaveModel withReadBlips(List<J2clReadBlip> newReadBlips) {
    return new J2clSelectedWaveModel(
        hasSelection,
        loading,
        error,
        selectedWaveId,
        titleText,
        snippetText,
        unreadText,
        statusText,
        detailText,
        reconnectCount,
        participantIds,
        contentEntries,
        newReadBlips,
        viewportState,
        interactionBlips,
        writeSession,
        unreadCount,
        read,
        readStateKnown,
        readStateStale)
        .withConversationManifest(conversationManifest);
  }

  J2clSelectedWaveModel withStatus(String nextStatusText, String nextDetailText) {
    // Soft status updates keep the selected-wave card interactive and avoid the blocking error
    // presentation used for bootstrap/stream failures.
    return new J2clSelectedWaveModel(
        hasSelection,
        loading,
        false,
        selectedWaveId,
        titleText,
        snippetText,
        unreadText,
        nextStatusText,
        nextDetailText,
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
        readStateStale)
        .withConversationManifest(conversationManifest);
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public boolean isRead() {
    return read;
  }

  public boolean isReadStateKnown() {
    return readStateKnown;
  }

  public boolean isReadStateStale() {
    return readStateStale;
  }
}
