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

package org.waveprotocol.wave.model.conversation;

/**
 * Shared helpers for task ownership and due-date metadata.
 *
 * <p>These helpers live in a non-client package so they can be exercised by the
 * default JVM test runner and reused by the GWT client task UI code.
 */
@SuppressWarnings("deprecation")
public final class TaskMetadataUtil {

  private static final String[] MONTH_ABBREVIATIONS = {
      "Jan", "Feb", "Mar", "Apr", "May", "Jun",
      "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
  };

  private TaskMetadataUtil() {
  }

  /**
   * Formats a task assignee pill label from a participant identifier.
   */
  public static String formatTaskAssigneeLabel(String assignee) {
    return formatParticipantDisplay(assignee);
  }

  /**
   * Formats a participant identifier for compact UI display.
   */
  public static String formatParticipantDisplay(String participantAddress) {
    String normalized = normalize(participantAddress);
    if (normalized.isEmpty()) {
      return "";
    }
    int at = normalized.indexOf('@');
    return at > 0 ? normalized.substring(0, at) : normalized;
  }

  /**
   * Parses a {@code yyyy-MM-dd} date input into UTC-midnight epoch millis.
   *
   * <p>Stores UTC midnight so the date is the same for all collaborators
   * regardless of their local timezone.
   *
   * @return epoch millis, or {@code -1} when the input is blank or invalid
   */
  public static long parseDateInputValue(String rawValue) {
    String value = normalize(rawValue);
    if (value.isEmpty() || value.length() != 10
        || value.charAt(4) != '-' || value.charAt(7) != '-') {
      return -1L;
    }

    int year = parseInt(value.substring(0, 4));
    int month = parseInt(value.substring(5, 7));
    int day = parseInt(value.substring(8, 10));
    if (year < 0 || month < 1 || month > 12) {
      return -1L;
    }
    int maxDay = daysInMonth(year, month);
    if (day < 1 || day > maxDay) {
      return -1L;
    }

    return toUtcMidnightMillis(year, month, day);
  }

  /**
   * Formats UTC-midnight epoch millis into a {@code yyyy-MM-dd} input value.
   */
  public static String formatDateInputValue(long dueTs) {
    if (dueTs < 0) {
      return "";
    }
    int[] ymd = fromUtcMidnightMillis(dueTs);
    return zeroPad(ymd[0], 4) + "-" + zeroPad(ymd[1], 2) + "-" + zeroPad(ymd[2], 2);
  }

  /**
   * Formats a due-date pill label from UTC-midnight epoch millis.
   */
  public static String formatTaskDueLabel(long dueTs) {
    if (dueTs < 0) {
      return "";
    }
    int[] ymd = fromUtcMidnightMillis(dueTs);
    String month = MONTH_ABBREVIATIONS[ymd[1] - 1];
    return "Due " + month + " " + ymd[2];
  }

  /**
   * Converts a calendar date (year/month/day) to UTC-midnight epoch millis.
   * Uses the Hinnant civil-from-days algorithm for timezone-independent arithmetic.
   */
  private static long toUtcMidnightMillis(int year, int month, int day) {
    long y = month <= 2 ? year - 1 : year;
    long m = month <= 2 ? month + 9 : month - 3;
    long era = (y >= 0 ? y : y - 399) / 400;
    long yoe = y - era * 400;
    long doy = (153 * m + 2) / 5 + day - 1;
    long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
    long days = era * 146097 + doe - 719468;
    return days * 86400000L;
  }

  /**
   * Converts UTC-midnight epoch millis to a [year, month, day] array.
   * Uses the Hinnant days-from-civil algorithm for timezone-independent arithmetic.
   */
  private static int[] fromUtcMidnightMillis(long millis) {
    long z = millis / 86400000L;
    if (millis < 0 && millis % 86400000L != 0) {
      z--;
    }
    z += 719468;
    long era = (z >= 0 ? z : z - 146096) / 146097;
    long doe = z - era * 146097;
    long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
    long y = yoe + era * 400;
    long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    long mp = (5 * doy + 2) / 153;
    long d = doy - (153 * mp + 2) / 5 + 1;
    long m = mp + (mp < 10 ? 3 : -9);
    y += (m <= 2 ? 1 : 0);
    return new int[]{(int) y, (int) m, (int) d};
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return -1;
    }
  }

  private static int daysInMonth(int year, int month) {
    switch (month) {
      case 1:
      case 3:
      case 5:
      case 7:
      case 8:
      case 10:
      case 12:
        return 31;
      case 4:
      case 6:
      case 9:
      case 11:
        return 30;
      case 2:
        return isLeapYear(year) ? 29 : 28;
      default:
        return 0;
    }
  }

  private static boolean isLeapYear(int year) {
    return (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
  }

  private static String zeroPad(int value, int width) {
    String stringValue = String.valueOf(value);
    if (stringValue.length() >= width) {
      return stringValue;
    }
    StringBuilder builder = new StringBuilder(width);
    for (int i = stringValue.length(); i < width; i++) {
      builder.append('0');
    }
    builder.append(stringValue);
    return builder.toString();
  }
}
