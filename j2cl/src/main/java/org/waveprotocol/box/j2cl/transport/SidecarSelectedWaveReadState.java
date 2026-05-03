package org.waveprotocol.box.j2cl.transport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-user unread/read state for a single selected wave. Populated from the
 * server's {@code /read-state} endpoint (issue #931).
 */
public final class SidecarSelectedWaveReadState {
  private final String waveId;
  private final int unreadCount;
  private final boolean read;
  private final List<String> unreadBlipIds;

  public SidecarSelectedWaveReadState(String waveId, int unreadCount, boolean read) {
    this(waveId, unreadCount, read, Collections.<String>emptyList());
  }

  public SidecarSelectedWaveReadState(
      String waveId, int unreadCount, boolean read, List<String> unreadBlipIds) {
    this.waveId = waveId;
    this.unreadCount = unreadCount;
    this.read = read;
    this.unreadBlipIds =
        unreadBlipIds == null
            ? Collections.<String>emptyList()
            : Collections.unmodifiableList(new ArrayList<String>(unreadBlipIds));
  }

  public String getWaveId() {
    return waveId;
  }

  public int getUnreadCount() {
    return unreadCount;
  }

  public boolean isRead() {
    return read;
  }

  public List<String> getUnreadBlipIds() {
    return unreadBlipIds;
  }
}
