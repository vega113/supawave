package org.waveprotocol.box.j2cl.search;

import java.util.ArrayList;
import java.util.List;

public final class J2clSearchResultProjector {
  static final String DEFAULT_QUERY = "in:inbox";
  static final int DESKTOP_PAGE_SIZE = 30;
  static final int MOBILE_PAGE_SIZE = 15;
  static final int MOBILE_BREAKPOINT_PX = 768;

  private J2clSearchResultProjector() {
  }

  public static String normalizeQuery(String rawQuery) {
    if (rawQuery == null || rawQuery.trim().isEmpty()) {
      return DEFAULT_QUERY;
    }
    return rawQuery;
  }

  public static int getPageSizeForViewport(double viewportWidth) {
    if (viewportWidth <= MOBILE_BREAKPOINT_PX) {
      return MOBILE_PAGE_SIZE;
    }
    return DESKTOP_PAGE_SIZE;
  }

  public static J2clSearchResultModel project(SidecarSearchResponse response, int requestedSize) {
    if (response == null || response.getDigests().isEmpty()) {
      return J2clSearchResultModel.empty("No waves matched this query.");
    }

    List<J2clSearchDigestItem> items = new ArrayList<J2clSearchDigestItem>();
    int unreadWaveCount = 0;
    for (SidecarSearchResponse.Digest digest : response.getDigests()) {
      if (digest.getUnreadCount() > 0) {
        unreadWaveCount++;
      }
      items.add(
          new J2clSearchDigestItem(
              digest.getWaveId(),
              normalizeTitle(digest.getTitle()),
              defaultString(digest.getSnippet()),
              resolveAuthor(digest),
              digest.getUnreadCount(),
              digest.getBlipCount(),
              digest.getLastModified(),
              digest.isPinned()));
    }

    int total = response.getTotalResults();
    boolean totalKnown = total >= 0;
    int loaded = items.size();
    String waveCountText;
    if (!totalKnown) {
      waveCountText = loaded + " waves";
    } else if (loaded < total) {
      waveCountText = loaded + " of " + total + " waves";
    } else {
      waveCountText = total + " waves";
    }
    if (unreadWaveCount > 0) {
      waveCountText += " \u00b7 " + unreadWaveCount + " unread";
    }

    boolean showMoreVisible =
        totalKnown ? loaded < total : (requestedSize > 0 && loaded >= requestedSize);
    return new J2clSearchResultModel(items, waveCountText, showMoreVisible, "");
  }

  private static String normalizeTitle(String title) {
    return title == null || title.trim().isEmpty() ? "(untitled wave)" : title;
  }

  private static String defaultString(String value) {
    return value == null ? "" : value;
  }

  private static String resolveAuthor(SidecarSearchResponse.Digest digest) {
    String author = digest.getAuthor();
    if (author != null && !author.trim().isEmpty()) {
      return author;
    }
    List<String> participants = digest.getParticipants();
    return participants.isEmpty() ? "" : participants.get(0);
  }
}
