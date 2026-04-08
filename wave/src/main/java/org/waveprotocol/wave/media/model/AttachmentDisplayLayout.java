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

package org.waveprotocol.wave.media.model;

import java.util.Objects;

/**
 * Pure layout decisions for attachment-backed image rendering.
 */
public final class AttachmentDisplayLayout {
  private static final String DISPLAY_SIZE_MEDIUM = "medium";
  private static final String DISPLAY_SIZE_LARGE = "large";

  private AttachmentDisplayLayout() {}

  public enum SourceKind {
    THUMBNAIL,
    ATTACHMENT
  }

  public static final class Decision {
    private final SourceKind sourceKind;
    private final boolean hideChrome;

    Decision(SourceKind sourceKind, boolean hideChrome) {
      this.sourceKind = sourceKind;
      this.hideChrome = hideChrome;
    }

    public SourceKind getSourceKind() {
      return sourceKind;
    }

    public boolean hideChrome() {
      return hideChrome;
    }
  }

  public static final class Size {
    private final int width;
    private final int height;

    public Size(int width, int height) {
      this.width = width;
      this.height = height;
    }

    public int getWidth() {
      return width;
    }

    public int getHeight() {
      return height;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Size)) {
        return false;
      }
      Size that = (Size) other;
      return width == that.width && height == that.height;
    }

    @Override
    public int hashCode() {
      return Objects.hash(width, height);
    }

    @Override
    public String toString() {
      return "Size{" + width + "x" + height + "}";
    }
  }

  public static Decision decide(String displaySize, boolean fullMode, boolean contentImage) {
    if (fullMode) {
      return new Decision(SourceKind.ATTACHMENT, true);
    }
    if (!contentImage) {
      return new Decision(SourceKind.THUMBNAIL, false);
    }
    String normalizedDisplaySize = normalizeDisplaySize(displaySize);
    boolean inlineImage = DISPLAY_SIZE_MEDIUM.equals(normalizedDisplaySize)
        || DISPLAY_SIZE_LARGE.equals(normalizedDisplaySize);
    return new Decision(inlineImage ? SourceKind.ATTACHMENT : SourceKind.THUMBNAIL, inlineImage);
  }

  public static Size scaleToFit(int sourceWidth, int sourceHeight, String displaySize,
      boolean fullMode) {
    if (fullMode) {
      if (sourceWidth > 0 && sourceHeight > 0) {
        return new Size(sourceWidth, sourceHeight);
      }
      return getDisplayBox(DISPLAY_SIZE_LARGE);
    }

    Size box = getDisplayBox(displaySize);
    if (sourceWidth <= 0 || sourceHeight <= 0) {
      return box;
    }

    double widthScale = (double) box.getWidth() / sourceWidth;
    double heightScale = (double) box.getHeight() / sourceHeight;
    double scale = Math.min(widthScale, heightScale);
    int scaledWidth = Math.max(1, (int) Math.round(sourceWidth * scale));
    int scaledHeight = Math.max(1, (int) Math.round(sourceHeight * scale));
    return new Size(scaledWidth, scaledHeight);
  }

  private static Size getDisplayBox(String displaySize) {
    switch (normalizeDisplaySize(displaySize)) {
      case DISPLAY_SIZE_MEDIUM:
        return new Size(300, 200);
      case DISPLAY_SIZE_LARGE:
        return new Size(600, 400);
      default:
        return new Size(120, 80);
    }
  }

  private static String normalizeDisplaySize(String displaySize) {
    if (DISPLAY_SIZE_MEDIUM.equals(displaySize) || DISPLAY_SIZE_LARGE.equals(displaySize)) {
      return displaySize;
    }
    return "small";
  }
}
