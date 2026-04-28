package org.waveprotocol.box.j2cl.richtext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable structured composer document used to build J2CL rich-content sidecar deltas. */
public final class J2clComposerDocument {
  enum ComponentType {
    TEXT,
    ANNOTATED_TEXT,
    IMAGE_ATTACHMENT
  }

  /** Package-private value object intentionally kept internal to the delta builder package. */
  static final class Component {
    final ComponentType type;
    final String text;
    final String annotationKey;
    final String annotationValue;
    final String attachmentId;
    final String displaySize;

    private Component(
        ComponentType type,
        String text,
        String annotationKey,
        String annotationValue,
        String attachmentId,
        String displaySize) {
      this.type = type;
      this.text = nullToEmpty(text);
      this.annotationKey = nullToEmpty(annotationKey);
      this.annotationValue = nullToEmpty(annotationValue);
      this.attachmentId = nullToEmpty(attachmentId);
      this.displaySize = nullToEmpty(displaySize);
    }
  }

  public static final class Builder {
    private final List<Component> components = new ArrayList<Component>();

    /** Appends literal text when non-empty; null or empty inputs are treated as no-ops. */
    public Builder text(String text) {
      if (text != null && !text.isEmpty()) {
        components.add(new Component(ComponentType.TEXT, text, "", "", "", ""));
      }
      return this;
    }

    /** Appends non-empty text bracketed by one annotation start and end boundary. */
    public Builder annotatedText(String annotationKey, String annotationValue, String text) {
      String key = requireNonEmpty(annotationKey, "Missing annotation key.");
      String value = requireNonEmpty(annotationValue, "Missing annotation value.");
      if (text == null || text.trim().isEmpty()) {
        throw new IllegalArgumentException("Missing annotated text.");
      }
      components.add(
          new Component(
              ComponentType.ANNOTATED_TEXT,
              text,
              key,
              value,
              "",
              ""));
      return this;
    }

    /**
     * J-UI-3 (#1081, R-5.1): appends the wave title as an `ANNOTATED_TEXT`
     * component using the canonical Wave title annotation
     * (`conv/title` with the AUTO_VALUE empty-string sentinel from
     * {@code TitleHelper.AUTO_VALUE}). The empty annotation value tells the
     * server-side digest path to use the annotated text itself as the title.
     *
     * <p>Bypasses {@link #annotatedText}'s non-empty-value check because the
     * AUTO_VALUE sentinel is exactly the empty string. No-op on null/blank
     * input so callers can pass through an unset title cleanly.
     */
    public Builder titleText(String text) {
      if (text == null || text.trim().isEmpty()) {
        return this;
      }
      components.add(
          new Component(
              ComponentType.ANNOTATED_TEXT,
              text,
              "conv/title",
              "",
              "",
              ""));
      return this;
    }

    /** Appends an image attachment element; caption text is preserved exactly when present. */
    public Builder imageAttachment(String attachmentId, String caption, String displaySize) {
      components.add(
          new Component(
              ComponentType.IMAGE_ATTACHMENT,
              caption,
              "",
              "",
              requireNonEmpty(attachmentId, "Missing attachment id."),
              normalizeDisplaySize(displaySize)));
      return this;
    }

    public J2clComposerDocument build() {
      return new J2clComposerDocument(components);
    }
  }

  private final List<Component> components;

  private J2clComposerDocument(List<Component> components) {
    this.components =
        Collections.unmodifiableList(new ArrayList<Component>(components));
  }

  public static Builder builder() {
    return new Builder();
  }

  List<Component> getComponents() {
    return components;
  }

  private static String normalizeDisplaySize(String displaySize) {
    String normalized = displaySize == null ? "" : displaySize.trim().toLowerCase(Locale.ROOT);
    if (normalized.isEmpty()) {
      return "medium";
    }
    if ("small".equals(normalized) || "medium".equals(normalized) || "large".equals(normalized)) {
      return normalized;
    }
    throw new IllegalArgumentException("Invalid attachment display size: " + displaySize);
  }

  private static String requireNonEmpty(String value, String message) {
    String normalized = value == null ? "" : value.trim();
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException(message);
    }
    return normalized;
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}
