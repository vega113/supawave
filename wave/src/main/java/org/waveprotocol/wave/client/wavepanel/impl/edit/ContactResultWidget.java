/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.wave.client.wavepanel.impl.edit;

import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseOutEvent;
import com.google.gwt.event.dom.client.MouseOutHandler;
import com.google.gwt.event.dom.client.MouseOverEvent;
import com.google.gwt.event.dom.client.MouseOverHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Image;
import com.google.gwt.user.client.ui.InlineLabel;

import org.waveprotocol.wave.client.wavepanel.impl.edit.i18n.ContactSearchMessages;

/**
 * A single contact search result row showing avatar, email address with
 * bold prefix matching, and relative time since last contact.
 *
 * <p>Clickable: fires a callback with the participant address.
 * Supports highlighted (keyboard-selected) state.
 */
public class ContactResultWidget extends Composite {

  /** Callback when this result is selected. */
  public interface Listener {
    void onSelect(String address);
  }

  private static final String BG_DEFAULT = "white";
  private static final String BG_HOVER = "#f1f3f4";
  private static final String BG_SELECTED = "#e8f0fe";

  private final FlowPanel row;
  private final String address;
  private boolean highlighted;

  /**
   * Creates a new contact result widget.
   *
   * @param address the contact's email address
   * @param displayName the human-readable display name, or null if unavailable
   * @param lastContactMs the timestamp (millis) of last contact, or 0 if unknown
   * @param prefix the search prefix for bold highlighting
   * @param messages i18n messages
   * @param listener callback for selection
   */
  public ContactResultWidget(String address, String displayName, long lastContactMs, String prefix,
      ContactSearchMessages messages, final Listener listener) {
    this.address = address;
    this.highlighted = false;

    row = new FlowPanel();
    Style rowStyle = row.getElement().getStyle();
    rowStyle.setProperty("display", "flex");
    rowStyle.setProperty("alignItems", "center");
    rowStyle.setProperty("padding", "6px 10px");
    rowStyle.setCursor(Style.Cursor.POINTER);
    rowStyle.setProperty("borderBottom", "1px solid #eee");
    rowStyle.setProperty("background", BG_DEFAULT);

    // Avatar (Gravatar identicon fallback -- use MD5 of email for identicons).
    String gravatarUrl = "https://www.gravatar.com/avatar/"
        + "?d=identicon&s=32&default=identicon";
    String emailHash = md5Hex(address.trim().toLowerCase());
    if (emailHash != null && !emailHash.isEmpty()) {
      gravatarUrl = "https://www.gravatar.com/avatar/"
          + emailHash + "?d=identicon&s=32";
    }
    Image avatar = new Image(gravatarUrl);
    avatar.setPixelSize(32, 32);
    Style avatarStyle = avatar.getElement().getStyle();
    avatarStyle.setProperty("borderRadius", "50%");
    avatarStyle.setProperty("marginRight", "10px");
    avatarStyle.setProperty("flexShrink", "0");
    avatar.getElement().setAttribute("loading", "lazy");
    row.add(avatar);

    // Center column: display name (primary) + email (secondary) + last contact
    FlowPanel centerCol = new FlowPanel();
    Style centerStyle = centerCol.getElement().getStyle();
    centerStyle.setProperty("flex", "1");
    centerStyle.setProperty("minWidth", "0");
    centerStyle.setProperty("overflow", "hidden");

    boolean hasDisplayName = displayName != null && !displayName.isEmpty();

    if (hasDisplayName) {
      // Display name as primary line with bold prefix match
      HTML nameHtml = new HTML(formatText(displayName, prefix));
      Style nameStyle = nameHtml.getElement().getStyle();
      nameStyle.setFontSize(13, Style.Unit.PX);
      nameStyle.setColor("#202124");
      nameStyle.setProperty("whiteSpace", "nowrap");
      nameStyle.setProperty("overflow", "hidden");
      nameStyle.setProperty("textOverflow", "ellipsis");
      nameStyle.setProperty("fontWeight", "500");
      centerCol.add(nameHtml);

      // Email as secondary line
      HTML emailHtml = new HTML(formatText(address, prefix));
      Style emailStyle = emailHtml.getElement().getStyle();
      emailStyle.setFontSize(11, Style.Unit.PX);
      emailStyle.setColor("#5f6368");
      emailStyle.setProperty("whiteSpace", "nowrap");
      emailStyle.setProperty("overflow", "hidden");
      emailStyle.setProperty("textOverflow", "ellipsis");
      centerCol.add(emailHtml);
    } else {
      // No display name: email is the primary line
      HTML emailHtml = new HTML(formatText(address, prefix));
      Style emailStyle = emailHtml.getElement().getStyle();
      emailStyle.setFontSize(13, Style.Unit.PX);
      emailStyle.setColor("#202124");
      emailStyle.setProperty("whiteSpace", "nowrap");
      emailStyle.setProperty("overflow", "hidden");
      emailStyle.setProperty("textOverflow", "ellipsis");
      centerCol.add(emailHtml);
    }

    // Last contact relative time
    if (lastContactMs > 0) {
      String relativeTime = formatRelativeTime(lastContactMs);
      InlineLabel timeLabel = new InlineLabel(messages.lastContact(relativeTime));
      Style timeStyle = timeLabel.getElement().getStyle();
      timeStyle.setFontSize(11, Style.Unit.PX);
      timeStyle.setColor("#5f6368");
      centerCol.add(timeLabel);
    }

    row.add(centerCol);

    // Hover effects
    row.addDomHandler(new MouseOverHandler() {
      @Override
      public void onMouseOver(MouseOverEvent event) {
        if (!highlighted) {
          row.getElement().getStyle().setProperty("background", BG_HOVER);
        }
      }
    }, MouseOverEvent.getType());

    row.addDomHandler(new MouseOutHandler() {
      @Override
      public void onMouseOut(MouseOutEvent event) {
        if (!highlighted) {
          row.getElement().getStyle().setProperty("background", BG_DEFAULT);
        }
      }
    }, MouseOutEvent.getType());

    // Click handler
    row.addDomHandler(new ClickHandler() {
      @Override
      public void onClick(ClickEvent event) {
        listener.onSelect(ContactResultWidget.this.address);
      }
    }, ClickEvent.getType());

    initWidget(row);
  }

  /** Returns the address represented by this widget. */
  public String getAddress() {
    return address;
  }

  /** Sets or clears the keyboard-highlight state. */
  public void setHighlighted(boolean highlighted) {
    this.highlighted = highlighted;
    row.getElement().getStyle().setProperty("background",
        highlighted ? BG_SELECTED : BG_DEFAULT);
  }

  /** Returns true if this widget is currently highlighted. */
  public boolean isHighlighted() {
    return highlighted;
  }

  /**
   * Formats text with the matching prefix substring in bold.
   */
  private static String formatText(String text, String prefix) {
    if (prefix == null || prefix.isEmpty()) {
      return escapeHtml(text);
    }
    String lowerText = text.toLowerCase();
    String lowerPrefix = prefix.toLowerCase();
    int idx = lowerText.indexOf(lowerPrefix);
    if (idx < 0) {
      return escapeHtml(text);
    }
    String before = text.substring(0, idx);
    String match = text.substring(idx, idx + prefix.length());
    String after = text.substring(idx + prefix.length());
    return escapeHtml(before) + "<b>" + escapeHtml(match) + "</b>" + escapeHtml(after);
  }

  /** Simple HTML escaping. */
  private static String escapeHtml(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * Formats a timestamp as a human-readable relative time string.
   *
   * @param timestampMs the timestamp in milliseconds
   * @return a relative time string like "2 hours ago", "3 days ago"
   */
  private static String formatRelativeTime(long timestampMs) {
    long nowMs = System.currentTimeMillis();
    long diffMs = nowMs - timestampMs;
    if (diffMs < 0) {
      return "just now";
    }

    long seconds = diffMs / 1000;
    long minutes = seconds / 60;
    long hours = minutes / 60;
    long days = hours / 24;

    if (days > 0) {
      return days == 1 ? "1 day ago" : days + " days ago";
    } else if (hours > 0) {
      return hours == 1 ? "1 hour ago" : hours + " hours ago";
    } else if (minutes > 0) {
      return minutes == 1 ? "1 minute ago" : minutes + " minutes ago";
    } else {
      return "just now";
    }
  }

  /**
   * Computes an MD5 hex digest of the given input string. This is a
   * lightweight pure-Java implementation compatible with GWT compilation.
   * Used for Gravatar URL generation per their spec (trimmed, lowercased
   * email -> MD5 hex).
   */
  private static String md5Hex(String input) {
    if (input == null) {
      return "";
    }
    byte[] message = toUTF8Bytes(input);
    // MD5 constants
    int[] s = {
        7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
        5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
        4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
        6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
    };
    int[] k = new int[64];
    for (int i = 0; i < 64; i++) {
      k[i] = (int) (long) ((long) Math.floor(Math.abs(Math.sin(i + 1)) * 4294967296.0));
    }

    // Pre-processing: add padding bits
    int origLen = message.length;
    int newLen = origLen + 1;
    while (newLen % 64 != 56) {
      newLen++;
    }
    byte[] padded = new byte[newLen + 8];
    for (int i = 0; i < origLen; i++) {
      padded[i] = message[i];
    }
    padded[origLen] = (byte) 0x80;
    long bitLen = (long) origLen * 8;
    for (int i = 0; i < 8; i++) {
      padded[newLen + i] = (byte) (bitLen >>> (8 * i));
    }

    // Initialize hash values
    int a0 = 0x67452301;
    int b0 = 0xefcdab89;
    int c0 = 0x98badcfe;
    int d0 = 0x10325476;

    // Process each 512-bit chunk
    for (int offset = 0; offset < padded.length; offset += 64) {
      int[] m = new int[16];
      for (int j = 0; j < 16; j++) {
        m[j] = (padded[offset + j * 4] & 0xFF)
            | ((padded[offset + j * 4 + 1] & 0xFF) << 8)
            | ((padded[offset + j * 4 + 2] & 0xFF) << 16)
            | ((padded[offset + j * 4 + 3] & 0xFF) << 24);
      }

      int a = a0;
      int b = b0;
      int c = c0;
      int d = d0;

      for (int i = 0; i < 64; i++) {
        int f;
        int g;
        if (i < 16) {
          f = (b & c) | (~b & d);
          g = i;
        } else if (i < 32) {
          f = (d & b) | (~d & c);
          g = (5 * i + 1) % 16;
        } else if (i < 48) {
          f = b ^ c ^ d;
          g = (3 * i + 5) % 16;
        } else {
          f = c ^ (b | ~d);
          g = (7 * i) % 16;
        }
        int temp = d;
        d = c;
        c = b;
        b = b + Integer.rotateLeft(a + f + k[i] + m[g], s[i]);
        a = temp;
      }

      a0 += a;
      b0 += b;
      c0 += c;
      d0 += d;
    }

    return intToHex(a0) + intToHex(b0) + intToHex(c0) + intToHex(d0);
  }

  /** Converts a 32-bit int to 8-char little-endian hex string. */
  private static String intToHex(int val) {
    StringBuilder sb = new StringBuilder(8);
    for (int i = 0; i < 4; i++) {
      int b = (val >>> (8 * i)) & 0xFF;
      String hex = Integer.toHexString(b);
      if (hex.length() == 1) {
        sb.append('0');
      }
      sb.append(hex);
    }
    return sb.toString();
  }

  /** Converts a string to UTF-8 bytes (GWT-compatible). */
  private static byte[] toUTF8Bytes(String str) {
    // Simple ASCII-safe implementation. For emails this is sufficient.
    byte[] bytes = new byte[str.length()];
    for (int i = 0; i < str.length(); i++) {
      bytes[i] = (byte) (str.charAt(i) & 0xFF);
    }
    return bytes;
  }
}
