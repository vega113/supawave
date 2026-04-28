package org.waveprotocol.box.j2cl.richtext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Immutable structured composer document used to build J2CL rich-content sidecar deltas. */
public final class J2clComposerDocument {
  // J-UI-3 (#1081, R-5.1): the wave-title annotation key. Mirrors
  // org.waveprotocol.wave.model.conversation.TitleHelper.TITLE_KEY which
  // is built from Annotations.join(Blips.ANNOTATION_PREFIX, "title") and
  // resolves to the literal "conv/title". Kept as a private constant
  // here because TitleHelper lives in the wave model module which is not
  // visible to J2CL-compiled code.
  static final String TITLE_ANNOTATION_KEY = "conv/title";
  // TitleHelper.AUTO_VALUE = "" — empty value tells the server to use
  // the annotated text run as the title.
  static final String TITLE_ANNOTATION_AUTO_VALUE = "";

  /**
   * J-UI-5 (#1083): public DTO used by callers of
   * {@link Builder#annotatedTextMulti(List, String)} so they can pass
   * an ordered list of annotation pairs without depending on the
   * package-private {@link Annotation} record.
   */
  public static final class KeyValuePair {
    private final String key;
    private final String value;

    public KeyValuePair(String key, String value) {
      this.key = key;
      this.value = value;
    }

    public String getKey() {
      return key;
    }

    public String getValue() {
      return value;
    }
  }

  enum ComponentType {
    TEXT,
    ANNOTATED_TEXT,
    IMAGE_ATTACHMENT
  }

  /**
   * J-UI-5 (#1083, codex review #1095 thread PRRT_kwDOBwxLXs5-C84a):
   * a single annotation pair (key, value). Used by ANNOTATED_TEXT
   * components to support combined wraps (e.g. bold+italic) without
   * forcing multiple components on the same text span.
   */
  static final class Annotation {
    final String key;
    final String value;

    Annotation(String key, String value) {
      this.key = nullToEmpty(key);
      this.value = nullToEmpty(value);
    }
  }

  /** Package-private value object intentionally kept internal to the delta builder package. */
  static final class Component {
    final ComponentType type;
    final String text;
    final String annotationKey;
    final String annotationValue;
    final List<Annotation> annotations;
    final String attachmentId;
    final String displaySize;

    private Component(
        ComponentType type,
        String text,
        String annotationKey,
        String annotationValue,
        List<Annotation> annotations,
        String attachmentId,
        String displaySize) {
      this.type = type;
      this.text = nullToEmpty(text);
      this.annotationKey = nullToEmpty(annotationKey);
      this.annotationValue = nullToEmpty(annotationValue);
      this.annotations =
          annotations == null
              ? Collections.<Annotation>emptyList()
              : Collections.unmodifiableList(new ArrayList<Annotation>(annotations));
      this.attachmentId = nullToEmpty(attachmentId);
      this.displaySize = nullToEmpty(displaySize);
    }
  }

  public static final class Builder {
    private final List<Component> components = new ArrayList<Component>();

    /** Appends literal text when non-empty; null or empty inputs are treated as no-ops. */
    public Builder text(String text) {
      if (text != null && !text.isEmpty()) {
        components.add(
            new Component(
                ComponentType.TEXT, text, "", "", Collections.<Annotation>emptyList(), "", ""));
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
      List<Annotation> single = new ArrayList<Annotation>(1);
      single.add(new Annotation(key, value));
      components.add(
          new Component(ComponentType.ANNOTATED_TEXT, text, key, value, single, "", ""));
      return this;
    }

    /**
     * J-UI-5 (#1083): appends non-empty text bracketed by one OR more
     * annotation boundaries. Annotation starts open in declaration
     * order and close in reverse (so the wave-doc op stream stays
     * well-nested). Empty annotation list throws — callers should
     * route through {@link #text(String)} instead.
     *
     * <p>Coderabbit review #1095 thread PRRT_kwDOBwxLXs5-NWW2:
     * duplicate annotation keys are collapsed last-wins BEFORE the
     * Component is constructed, so the builder's stored representation
     * matches what the delta writer emits. Without this, callers that
     * inspect a built {@link J2clComposerDocument} would see two
     * `textDecoration` entries while the on-the-wire delta only
     * carries one. Last-wins matches the wave-doc reader's resolution
     * of overlapping annotation starts on the same key.
     */
    public Builder annotatedTextMulti(List<KeyValuePair> annotations, String text) {
      if (annotations == null || annotations.isEmpty()) {
        throw new IllegalArgumentException("Missing annotations.");
      }
      if (text == null || text.trim().isEmpty()) {
        throw new IllegalArgumentException("Missing annotated text.");
      }
      java.util.LinkedHashMap<String, Annotation> dedup =
          new java.util.LinkedHashMap<String, Annotation>();
      for (KeyValuePair pair : annotations) {
        if (pair == null) {
          throw new IllegalArgumentException("Null annotation entry.");
        }
        String key = requireNonEmpty(pair.getKey(), "Missing annotation key.");
        String value = requireNonEmpty(pair.getValue(), "Missing annotation value.");
        // last-wins removal-then-insert keeps insertion order stable
        // for keys whose value did not change while letting later
        // duplicates overwrite earlier entries (the wave-doc reader
        // applies the same resolution).
        dedup.remove(key);
        dedup.put(key, new Annotation(key, value));
      }
      List<Annotation> resolved = new ArrayList<Annotation>(dedup.values());
      Annotation first = resolved.get(0);
      components.add(
          new Component(
              ComponentType.ANNOTATED_TEXT,
              text,
              first.key,
              first.value,
              resolved,
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
      // J-UI-5 (#1083): Component now carries a `List<Annotation>` so
      // multi-annotation runs (e.g. bold+italic) can serialise without
      // duplicating chars. Title runs only ever carry the single
      // `conv/title` annotation, so the list has exactly one entry.
      List<Annotation> single = new ArrayList<Annotation>(1);
      single.add(new Annotation(TITLE_ANNOTATION_KEY, TITLE_ANNOTATION_AUTO_VALUE));
      components.add(
          new Component(
              ComponentType.ANNOTATED_TEXT,
              text,
              TITLE_ANNOTATION_KEY,
              TITLE_ANNOTATION_AUTO_VALUE,
              single,
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
              Collections.<Annotation>emptyList(),
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
