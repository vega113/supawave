# @Mention Feature Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Telegram-style @mention support with annotation-based inline rendering, Lucene-indexed search, and a "Mentions" toolbar button.

**Architecture:** New `mention/user` annotation (same pattern as `link/manual`) stores participant address as value. Server-side indexing extracts mentions into a `mentioned` Lucene field. New `mentions:` query token filters by that field. Client-side `@` keypress triggers autocomplete popup, selecting a user inserts annotated text and highlights the mention.

**Tech Stack:** Java (server-side Lucene 9 indexing, query parsing), GWT (client-side editor annotations, popup widget, search panel)

---

### Task 1: Add `MENTIONS` to `TokenQueryType`

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/TokenQueryType.java`

- [ ] **Step 1: Add MENTIONS enum value**

In `TokenQueryType.java`, add `MENTIONS` to the enum. Current values end with `TITLE("title")`. Add after it:

```java
// In the enum declaration, after TITLE("title"):
MENTIONS("mentions");
```

The full enum line should read:
```java
IN("in"), ORDERBY("orderby"), WITH("with"), CREATOR("creator"), ID("id"), TAG("tag"), UNREAD("unread"), CONTENT("content"), TITLE("title"), MENTIONS("mentions");
```

- [ ] **Step 2: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`
Expected: compilation succeeds (the reverse-lookup map is built dynamically from `values()`, so no other changes needed)

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/TokenQueryType.java
git commit -m "feat(search): add MENTIONS token to TokenQueryType"
```

---

### Task 2: Add `mentioned` Lucene field name

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9FieldNames.java`

- [ ] **Step 1: Add MENTIONED constant**

After the `EMBEDDING_MODEL` field (line 36), add:

```java
public static final String MENTIONED = "mentioned";
```

- [ ] **Step 2: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9FieldNames.java
git commit -m "feat(search): add MENTIONED field to Lucene9FieldNames"
```

---

### Task 3: Extract mentions in `WaveMetadataExtractor`

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/WaveMetadataExtractor.java`

- [ ] **Step 1: Add `mentions` set to extraction loop**

In the `extract()` method, add a `Set<String> mentions = new LinkedHashSet<>()` alongside the existing `participants`, `tags`, etc. sets (after line 50).

Inside the loop over wavelets (line 58), after `appendContent(content, Snippets.collateTextForWavelet(wavelet));` (line 68), add mention extraction for conversational wavelets:

```java
if (IdUtil.isConversationalId(wavelet.getWaveletId())) {
  lastModifiedSort = Math.max(lastModifiedSort, wavelet.getLastModifiedTime());
  appendContent(content, Snippets.collateTextForWavelet(wavelet));
  extractMentions(wavelet, mentions);
}
```

Replace the existing block at lines 66-69 with the above (which adds the `extractMentions` call).

- [ ] **Step 2: Add `extractMentions` method**

Add a new static method after `extractTags` (after line 184):

```java
private static void extractMentions(ObservableWaveletData wavelet, Set<String> mentions) {
  for (String docId : wavelet.getDocumentIds()) {
    ReadableBlipData blip = wavelet.getDocument(docId);
    if (blip == null) {
      continue;
    }
    DocInitialization docOp = blip.getContent().asOperation();
    docOp.apply(new DocInitializationCursor() {
      private static final String MENTION_PREFIX = "mention/";

      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
        for (int i = 0; i < map.changeSize(); i++) {
          String key = map.getChangeKey(i);
          String newValue = map.getNewValue(i);
          if (key.startsWith(MENTION_PREFIX) && newValue != null && !newValue.isEmpty()) {
            mentions.add(newValue.toLowerCase(Locale.ROOT));
          }
        }
      }

      @Override
      public void characters(String chars) {
      }

      @Override
      public void elementStart(String type, Attributes attrs) {
      }

      @Override
      public void elementEnd() {
      }
    });
  }
}
```

- [ ] **Step 3: Add `mentions` to `WaveMetadata` class**

Add a `mentions` field to the `WaveMetadata` inner class:

In the constructor (line 199), add `Set<String> mentions` parameter after `tags`:
```java
WaveMetadata(WaveId waveId, String rootWaveletId, Set<String> participants,
    Set<String> creatorFilters, String creatorSort, Set<String> tags, Set<String> mentions,
    String title, String content, String allText, long createdSort, long lastModifiedSort) {
```

Add field: `private final Set<String> mentions;`
Add assignment: `this.mentions = mentions;`
Add getter:
```java
public Set<String> getMentions() {
  return mentions;
}
```

Update the `return new WaveMetadata(...)` call in `extract()` (line 84) to pass `mentions` after `tags`:
```java
return new WaveMetadata(wave.getWaveId(), rootWaveletId, participants, creatorFilters,
    creatorSort, tags, mentions, title, contentText, allText, createdSort, lastModifiedSort);
```

- [ ] **Step 4: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 5: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/WaveMetadataExtractor.java
git commit -m "feat(search): extract mention annotations in WaveMetadataExtractor"
```

---

### Task 4: Index mentions in `WaveDocumentBuilder`

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/WaveDocumentBuilder.java`

- [ ] **Step 1: Add mention indexing**

In the `build()` method, after the tag indexing loop (after line 72), add:

```java
for (String mentioned : metadata.getMentions()) {
  document.add(new StringField(Lucene9FieldNames.MENTIONED, mentioned, Store.YES));
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 3: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/WaveDocumentBuilder.java
git commit -m "feat(search): index mentioned users in Lucene documents"
```

---

### Task 5: Parse `mentions:` in `QueryHelper`

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/QueryHelper.java`

- [ ] **Step 1: Add `me` resolution for mentions**

The `parseQuery` method already handles token parsing generically — since `MENTIONS` is now a valid `TokenQueryType`, queries like `mentions:vega@example.com` will parse automatically.

However, we need to resolve `mentions:me` to the current user's address. This resolution happens at the search provider level, not in `QueryHelper`. No changes needed in `QueryHelper` itself.

Verify by checking that `TokenQueryType.hasToken("mentions")` returns `true`:

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && grep -n "MENTIONS" wave/src/main/java/org/waveprotocol/box/server/waveserver/TokenQueryType.java`
Expected: Shows the `MENTIONS("mentions")` entry.

---

### Task 6: Add mention filter to `SimpleSearchProviderImpl`

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java`

- [ ] **Step 1: Extract mention filter values in `search()` method**

After the content filter extraction (after line 301 — `final Set<String> contentValues = ...`), add:

```java
// Extract mentions filter values (e.g., "mentions:me" or "mentions:vega@example.com").
final Set<String> mentionValues;
if (queryParams.containsKey(TokenQueryType.MENTIONS)) {
  mentionValues = new HashSet<String>();
  for (String raw : queryParams.get(TokenQueryType.MENTIONS)) {
    if ("me".equalsIgnoreCase(raw)) {
      mentionValues.add(user.getAddress().toLowerCase(Locale.ROOT));
    } else {
      String normalized = raw.contains("@") ? raw : raw + "@" + user.getDomain();
      mentionValues.add(normalized.toLowerCase(Locale.ROOT));
    }
  }
} else {
  mentionValues = Collections.<String>emptySet();
}
```

- [ ] **Step 2: Add filter call in the filter pipeline**

After the content filter block (after line 318 — `LOG.info("After content filter: ...`), add:

```java
// Filter by mentions when the query specifies mentions: filters.
if (!mentionValues.isEmpty()) {
  LOG.info("Mentions filter active: required mentions = " + mentionValues
      + ", candidates before filter = " + results.size());
  filterByMentions(results, mentionValues);
  LOG.info("After mentions filter: " + results.size() + " results remain");
}
```

- [ ] **Step 3: Add `filterByMentions` method**

Add after the `filterByContent` method (after line 822):

```java
/**
 * Filters wave results by mention annotations. Only waves whose blip content
 * contains mention annotations referencing all of the requested addresses are kept.
 *
 * @param results the mutable list of wave views to filter in place.
 * @param requiredMentions the set of participant addresses that must be mentioned.
 */
private void filterByMentions(List<WaveViewData> results, Set<String> requiredMentions) {
  Iterator<WaveViewData> it = results.iterator();
  while (it.hasNext()) {
    WaveViewData wave = it.next();
    try {
      Set<String> foundMentions = new HashSet<String>();

      for (ObservableWaveletData wd : wave.getWavelets()) {
        if (!IdUtil.isConversationalId(wd.getWaveletId())) {
          continue;
        }

        for (String docId : wd.getDocumentIds()) {
          ReadableBlipData blip = wd.getDocument(docId);
          if (blip == null) {
            continue;
          }

          DocInitialization docOp = blip.getContent().asOperation();
          docOp.apply(new DocInitializationCursor() {
            @Override
            public void annotationBoundary(AnnotationBoundaryMap map) {
              for (int i = 0; i < map.changeSize(); i++) {
                String key = map.getChangeKey(i);
                String newValue = map.getNewValue(i);
                if (key.startsWith("mention/") && newValue != null && !newValue.isEmpty()) {
                  foundMentions.add(newValue.toLowerCase(Locale.ROOT));
                }
              }
            }

            @Override
            public void characters(String chars) {
            }

            @Override
            public void elementStart(String type, Attributes attrs) {
            }

            @Override
            public void elementEnd() {
            }
          });
        }
      }

      if (!foundMentions.containsAll(requiredMentions)) {
        it.remove();
      }
    } catch (Exception e) {
      LOG.warning("Failed to check mentions for wave " + wave.getWaveId(), e);
      it.remove();
    }
  }
}
```

- [ ] **Step 4: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 5: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/server/waveserver/SimpleSearchProviderImpl.java
git commit -m "feat(search): add mentions: filter to SimpleSearchProviderImpl"
```

---

### Task 7: Add mention query to `Lucene9QueryCompiler`

**Files:**
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9QueryCompiler.java`
- Modify: `wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9QueryModel.java`

- [ ] **Step 1: Add mention clause to `Lucene9QueryCompiler.compile()`**

In the `compile()` method, after the tag query loop (after line 66), add:

```java
for (String mentionValue : model.values(TokenQueryType.MENTIONS)) {
  String address = resolveMentionAddress(mentionValue, user);
  builder.add(new TermQuery(new Term(Lucene9FieldNames.MENTIONED,
      address.toLowerCase(Locale.ROOT))), Occur.MUST);
}
```

Add the `resolveMentionAddress` helper method after `normalizeParticipants` (after line 175):

```java
private String resolveMentionAddress(String raw, ParticipantId user) {
  if ("me".equalsIgnoreCase(raw)) {
    return user.getAddress();
  }
  if (!raw.contains("@")) {
    return raw + "@" + user.getDomain();
  }
  return raw;
}
```

- [ ] **Step 2: Update `Lucene9QueryModel.hasTextQuery()` to include MENTIONS**

In `Lucene9QueryModel.java`, the `hasTextQuery()` method (line 76) determines whether Lucene full-text search is needed. Mentions are indexed as StringFields (exact match), not TextField, so they need the Lucene path. Update:

```java
public boolean hasTextQuery() {
  return hasToken(TokenQueryType.TITLE) || hasToken(TokenQueryType.CONTENT)
      || hasToken(TokenQueryType.MENTIONS);
}
```

- [ ] **Step 3: Update `Lucene9QueryModel.toLegacyQuery()` to exclude MENTIONS**

In `toLegacyQuery()` (line 80), mentions should be excluded from the legacy query since the legacy provider handles them separately. Update the skip condition:

```java
if (token.getType() == TokenQueryType.TITLE || token.getType() == TokenQueryType.CONTENT
    || token.getType() == TokenQueryType.MENTIONS) {
  continue;
}
```

- [ ] **Step 4: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 5: Commit**

```bash
git add wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9QueryCompiler.java \
       wave/src/jakarta-overrides/java/org/waveprotocol/box/server/waveserver/lucene9/Lucene9QueryModel.java
git commit -m "feat(search): compile mentions: queries in Lucene9QueryCompiler"
```

---

### Task 8: Add mention annotation constants

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/model/conversation/AnnotationConstants.java`

- [ ] **Step 1: Add mention constants**

After the link constants block (after line 97, before `// Other`), add:

```java
// Mentions

/** Prefix for mention annotations. */
public static final String MENTION_PREFIX = "mention";

/** Denotes a user mention with value = participant address. */
public static final String MENTION_USER = MENTION_PREFIX + "/user";
```

- [ ] **Step 2: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/model/conversation/AnnotationConstants.java
git commit -m "feat(editor): add mention annotation constants"
```

---

### Task 9: Create `MentionAnnotationHandler`

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionAnnotationHandler.java`

- [ ] **Step 1: Create the handler**

```java
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 */
package org.waveprotocol.wave.client.doodad.mention;

import org.waveprotocol.wave.client.editor.content.AnnotationPainter;
import org.waveprotocol.wave.client.editor.content.AnnotationPainter.PaintFunction;
import org.waveprotocol.wave.client.editor.content.PainterRegistry;
import org.waveprotocol.wave.client.editor.content.Registries;
import org.waveprotocol.wave.client.editor.content.misc.AnnotationPaint;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.AnnotationBehaviour.DefaultAnnotationBehaviour;
import org.waveprotocol.wave.model.document.AnnotationBehaviour.BiasDirection;
import org.waveprotocol.wave.model.document.AnnotationMutationHandler;
import org.waveprotocol.wave.model.document.util.DocumentContext;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.ReadableStringSet;

import java.util.Collections;
import java.util.Map;

/**
 * Annotation handler for @mention annotations. Renders mentioned usernames
 * with a highlight background, similar to how link annotations render links.
 */
public class MentionAnnotationHandler implements AnnotationMutationHandler<Object> {

  private static final String MENTION_COLOUR = "#D1E8FF";

  private static final ReadableStringSet MENTION_KEYS = CollectionUtils.newStringSet(
      AnnotationConstants.MENTION_USER);

  private static final PaintFunction MENTION_PAINT_FUNC = new PaintFunction() {
    @Override
    public Map<String, String> apply(Map<String, Object> from, boolean isEditing) {
      Object value = from.get(AnnotationConstants.MENTION_USER);
      if (value != null && !value.toString().isEmpty()) {
        return Collections.singletonMap(AnnotationPaint.BG_COLOUR_ATTR, MENTION_COLOUR);
      }
      return Collections.emptyMap();
    }
  };

  private MentionAnnotationHandler() {
  }

  public static void register(Registries registries) {
    PainterRegistry painterRegistry = registries.getPaintRegistry();
    MentionAnnotationHandler handler = new MentionAnnotationHandler();
    registries.getAnnotationHandlerRegistry().registerHandler(
        AnnotationConstants.MENTION_PREFIX, handler);
    painterRegistry.registerPaintFunction(MENTION_KEYS, MENTION_PAINT_FUNC);
    registries.getAnnotationHandlerRegistry().registerBehaviour(
        AnnotationConstants.MENTION_PREFIX,
        new DefaultAnnotationBehaviour() {
          @Override
          public BiasDirection getBias(StringMap<Object> left, StringMap<Object> right,
              CursorDirection cursorDirection) {
            return BiasDirection.LEFT;
          }
        });
  }

  @Override
  public <N, E extends N, T extends N> void handleAnnotationChange(
      DocumentContext<N, E, T> bundle, int start, int end, String key, Object newValue) {
    // Paint update is handled by the PaintFunction registered above.
  }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionAnnotationHandler.java
git commit -m "feat(editor): create MentionAnnotationHandler with highlight paint"
```

---

### Task 10: Register mention handler in `StageTwo`

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`

- [ ] **Step 1: Add import**

Add import at the top of the file (among other doodad imports):

```java
import org.waveprotocol.wave.client.doodad.mention.MentionAnnotationHandler;
```

- [ ] **Step 2: Register in `installDoodads`**

In the `installDoodads` method (line 812), inside the `install(Registries r)` method, add after the `LinkAnnotationHandler.register(r, createLinkAttributeAugmenter());` line (line 821):

```java
MentionAnnotationHandler.register(r);
```

- [ ] **Step 3: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java
git commit -m "feat(editor): register MentionAnnotationHandler in StageTwo"
```

---

### Task 11: Create mention autocomplete popup

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionPopupWidget.java`

- [ ] **Step 1: Create the popup widget**

```java
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 */
package org.waveprotocol.wave.client.doodad.mention;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;

import org.waveprotocol.wave.client.widget.popup.PopupChrome;
import org.waveprotocol.wave.client.widget.popup.PopupChromeFactory;
import org.waveprotocol.wave.client.widget.popup.PopupFactory;
import org.waveprotocol.wave.client.widget.popup.RelativePopupPositioner;
import org.waveprotocol.wave.client.widget.popup.UniversalPopup;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.List;

/**
 * Popup widget that displays a filtered list of participants for @mention
 * autocomplete. Shows when the user types '@' in the editor.
 */
public class MentionPopupWidget extends Composite {

  /** Listener for mention selection events. */
  public interface Listener {
    void onMentionSelected(ParticipantId participant);
    void onDismissed();
  }

  private final FlowPanel container;
  private final UniversalPopup popup;
  private Listener listener;
  private int selectedIndex = -1;

  public MentionPopupWidget(Element anchor) {
    container = new FlowPanel();
    container.getElement().getStyle().setProperty("maxHeight", "200px");
    container.getElement().getStyle().setOverflow(Style.Overflow.AUTO);
    container.getElement().getStyle().setProperty("minWidth", "180px");
    initWidget(container);

    PopupChrome chrome = PopupChromeFactory.createPopupChrome();
    popup = PopupFactory.createPopup(anchor, RelativePopupPositioner.BELOW_LEFT, chrome, true);
    popup.add(this);
  }

  public void setListener(Listener listener) {
    this.listener = listener;
  }

  public void update(List<ParticipantId> participants) {
    container.clear();
    selectedIndex = participants.isEmpty() ? -1 : 0;
    for (int i = 0; i < participants.size(); i++) {
      final ParticipantId participant = participants.get(i);
      final int index = i;
      Label item = new Label(formatDisplay(participant));
      item.getElement().getStyle().setPadding(6, Style.Unit.PX);
      item.getElement().getStyle().setCursor(Style.Cursor.POINTER);
      item.getElement().getStyle().setProperty("whiteSpace", "nowrap");
      if (i == selectedIndex) {
        item.getElement().getStyle().setBackgroundColor("#E8F0FE");
      }
      item.addClickHandler(new ClickHandler() {
        @Override
        public void onClick(ClickEvent event) {
          if (listener != null) {
            listener.onMentionSelected(participant);
          }
        }
      });
      container.add(item);
    }
  }

  public void moveSelectionUp() {
    if (selectedIndex > 0) {
      setSelectedIndex(selectedIndex - 1);
    }
  }

  public void moveSelectionDown() {
    int count = container.getWidgetCount();
    if (selectedIndex < count - 1) {
      setSelectedIndex(selectedIndex + 1);
    }
  }

  public ParticipantId getSelectedParticipant(List<ParticipantId> participants) {
    if (selectedIndex >= 0 && selectedIndex < participants.size()) {
      return participants.get(selectedIndex);
    }
    return null;
  }

  private void setSelectedIndex(int newIndex) {
    if (selectedIndex >= 0 && selectedIndex < container.getWidgetCount()) {
      container.getWidget(selectedIndex).getElement().getStyle().clearBackgroundColor();
    }
    selectedIndex = newIndex;
    if (selectedIndex >= 0 && selectedIndex < container.getWidgetCount()) {
      container.getWidget(selectedIndex).getElement().getStyle().setBackgroundColor("#E8F0FE");
    }
  }

  public void show() {
    popup.show();
  }

  public void hide() {
    popup.hide();
    if (listener != null) {
      listener.onDismissed();
    }
  }

  public boolean isShowing() {
    return popup.isShowing();
  }

  private static String formatDisplay(ParticipantId participant) {
    String name = participant.getName();
    return "@" + (name != null && !name.isEmpty() ? name : participant.getAddress());
  }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionPopupWidget.java
git commit -m "feat(editor): create MentionPopupWidget for autocomplete"
```

---

### Task 12: Create mention trigger key handler

**Files:**
- Create: `wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionTriggerHandler.java`

- [ ] **Step 1: Create the key handler**

This handler listens for `@` keypresses in the editor, opens the autocomplete popup, handles arrow key navigation, Enter to select, and Escape to dismiss.

```java
/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.
 */
package org.waveprotocol.wave.client.doodad.mention;

import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.Timer;

import org.waveprotocol.wave.client.common.util.KeySignalListener;
import org.waveprotocol.wave.client.common.util.SignalEvent;
import org.waveprotocol.wave.client.editor.Editor;
import org.waveprotocol.wave.client.editor.content.CMutableDocument;
import org.waveprotocol.wave.model.conversation.AnnotationConstants;
import org.waveprotocol.wave.model.document.MutableDocument;
import org.waveprotocol.wave.model.document.util.FocusedRange;
import org.waveprotocol.wave.model.document.util.Point;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.Wavelet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Keyboard handler that triggers @mention autocomplete when the user types '@'
 * in the editor. Manages the lifecycle of the mention popup.
 */
public class MentionTriggerHandler implements KeySignalListener, MentionPopupWidget.Listener {

  private final Wavelet wavelet;
  private MentionPopupWidget popup;
  private Editor activeEditor;
  private boolean mentionMode = false;
  private int mentionStartPos = -1;
  private StringBuilder filterBuffer = new StringBuilder();
  private List<ParticipantId> currentMatches = new ArrayList<ParticipantId>();
  private Timer filterTimer;

  public MentionTriggerHandler(Wavelet wavelet) {
    this.wavelet = wavelet;
  }

  @Override
  public boolean onKeySignal(Editor editor, SignalEvent signal) {
    if (mentionMode) {
      return handleMentionModeKey(editor, signal);
    }

    // Detect '@' typed
    if (signal.isTypingEvent()) {
      String typed = signal.getTypedCharacter();
      if ("@".equals(typed)) {
        startMentionMode(editor);
        return false; // Let the '@' character be inserted normally
      }
    }
    return false;
  }

  private void startMentionMode(Editor editor) {
    mentionMode = true;
    activeEditor = editor;
    filterBuffer.setLength(0);

    FocusedRange selection = editor.getSelectionHelper().getSelectionRange();
    if (selection != null) {
      // Position after the '@' that is about to be inserted
      mentionStartPos = selection.getFocus();
    }

    // Create popup anchored to editor element
    Element editorElement = editor.getWidget().getElement();
    popup = new MentionPopupWidget(editorElement);
    popup.setListener(this);
    updateMatches("");
    popup.show();
  }

  private boolean handleMentionModeKey(Editor editor, SignalEvent signal) {
    if (signal.getKeySignalType() == SignalEvent.KeySignalType.INPUT) {
      String typed = signal.getTypedCharacter();
      if (typed != null && !typed.isEmpty()) {
        char c = typed.charAt(0);
        if (c == ' ' || c == '\n') {
          dismissPopup();
          return false;
        }
        filterBuffer.append(c);
        scheduleFilterUpdate();
        return false;
      }
    }

    int keyCode = signal.getKeyCode();

    // Escape
    if (keyCode == 27) {
      dismissPopup();
      return true;
    }
    // Up arrow
    if (keyCode == 38) {
      popup.moveSelectionUp();
      return true;
    }
    // Down arrow
    if (keyCode == 40) {
      popup.moveSelectionDown();
      return true;
    }
    // Enter or Tab
    if (keyCode == 13 || keyCode == 9) {
      ParticipantId selected = popup.getSelectedParticipant(currentMatches);
      if (selected != null) {
        onMentionSelected(selected);
        return true;
      }
      dismissPopup();
      return false;
    }
    // Backspace
    if (keyCode == 8) {
      if (filterBuffer.length() > 0) {
        filterBuffer.deleteCharAt(filterBuffer.length() - 1);
        scheduleFilterUpdate();
      } else {
        // Backspaced past '@', dismiss
        dismissPopup();
      }
      return false;
    }

    return false;
  }

  private void scheduleFilterUpdate() {
    if (filterTimer != null) {
      filterTimer.cancel();
    }
    filterTimer = new Timer() {
      @Override
      public void run() {
        updateMatches(filterBuffer.toString());
      }
    };
    filterTimer.schedule(100);
  }

  private void updateMatches(String filter) {
    String lowerFilter = filter.toLowerCase();
    Set<ParticipantId> seen = new LinkedHashSet<ParticipantId>();
    currentMatches.clear();

    // First: current wave participants
    for (ParticipantId p : wavelet.getParticipantIds()) {
      if (matchesFilter(p, lowerFilter)) {
        seen.add(p);
        currentMatches.add(p);
      }
    }

    // TODO: In a future iteration, fetch all server users via contacts service
    // and append non-duplicate matches after participants.

    if (popup != null) {
      popup.update(currentMatches);
    }
  }

  private boolean matchesFilter(ParticipantId participant, String lowerFilter) {
    if (lowerFilter.isEmpty()) {
      return true;
    }
    String name = participant.getName();
    String address = participant.getAddress();
    return (name != null && name.toLowerCase().contains(lowerFilter))
        || address.toLowerCase().contains(lowerFilter);
  }

  @Override
  public void onMentionSelected(ParticipantId participant) {
    if (activeEditor == null) {
      dismissPopup();
      return;
    }

    CMutableDocument doc = activeEditor.getDocument();
    if (doc == null) {
      dismissPopup();
      return;
    }

    String displayName = participant.getName();
    if (displayName == null || displayName.isEmpty()) {
      displayName = participant.getAddress().split("@")[0];
    }
    String mentionText = displayName;

    // The '@' is already in the document at mentionStartPos.
    // Characters typed as filter are also in the document after '@'.
    // We need to: delete from mentionStartPos to current cursor, then insert @name with annotation.
    FocusedRange selection = activeEditor.getSelectionHelper().getSelectionRange();
    if (selection == null) {
      dismissPopup();
      return;
    }

    int currentPos = selection.getFocus();
    int deleteFrom = mentionStartPos;
    int deleteTo = currentPos;

    // Perform the replacement inside a single document operation
    doc.beginMutationGroup();
    try {
      // Delete the '@' + filter text
      if (deleteTo > deleteFrom) {
        doc.deleteRange(doc.locate(deleteFrom), doc.locate(deleteTo));
      }

      // Insert @displayName
      int insertAt = deleteFrom;
      String fullText = "@" + mentionText;
      doc.insertText(doc.locate(insertAt), fullText);

      // Apply mention annotation over the inserted text
      int annoStart = insertAt;
      int annoEnd = insertAt + fullText.length();
      doc.setAnnotation(annoStart, annoEnd, AnnotationConstants.MENTION_USER,
          participant.getAddress());

      // Insert a trailing space so the cursor exits the annotation
      doc.insertText(doc.locate(annoEnd), " ");
    } finally {
      doc.endMutationGroup();
    }

    // Auto-add participant to wave if not already present
    if (!wavelet.getParticipantIds().contains(participant)) {
      wavelet.addParticipant(participant);
    }

    dismissPopup();
  }

  @Override
  public void onDismissed() {
    mentionMode = false;
    mentionStartPos = -1;
    filterBuffer.setLength(0);
    activeEditor = null;
  }

  private void dismissPopup() {
    if (popup != null && popup.isShowing()) {
      popup.hide();
    }
    mentionMode = false;
    mentionStartPos = -1;
    filterBuffer.setLength(0);
    activeEditor = null;
  }
}
```

- [ ] **Step 2: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/doodad/mention/MentionTriggerHandler.java
git commit -m "feat(editor): create MentionTriggerHandler for @ autocomplete"
```

---

### Task 13: Wire mention handler into editor lifecycle

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`

- [ ] **Step 1: Add import**

```java
import org.waveprotocol.wave.client.doodad.mention.MentionTriggerHandler;
```

- [ ] **Step 2: Register key listener**

Find where the editor is initialized and key listeners are added. In `StageTwo`, after the editor is created and available, add the mention trigger handler. The best place is in the `installFeatures` or similar setup method where the editor is accessible.

Look for where `editor.addKeySignalListener` is called or where the editor is first made available, and add:

```java
MentionTriggerHandler mentionHandler = new MentionTriggerHandler(getWavelet());
editor.addKeySignalListener(mentionHandler);
```

The exact insertion point depends on where the editor instance becomes available — likely in StageThree or the wave panel editor setup. Search for `addKeySignalListener` in the codebase to find the pattern.

- [ ] **Step 3: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java
git commit -m "feat(editor): wire MentionTriggerHandler into editor lifecycle"
```

---

### Task 14: Add "Mentions" toolbar button to search panel

**Files:**
- Modify: `wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java`

- [ ] **Step 1: Add mentions SVG icon constant**

Add a new SVG icon constant near the other icon constants (around line 80-100, where `ICON_COMPOSE`, `ICON_SETTINGS`, etc. are defined):

```java
private static final String ICON_MENTIONS = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><circle cx='12' cy='12' r='4'/><path d='M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3.92 7.94'/></svg>";
```

This is the standard "at sign" (`@`) icon from Lucide/Feather icons.

- [ ] **Step 2: Add Mentions button in `initToolbarMenu`**

In the `initToolbarMenu` method, after the Inbox button and before the Public button (the Inbox button sets query to `"in:inbox"`), add:

```java
new ToolbarButtonViewBuilder()
    .setTooltip("Mentions")
    .applyTo(filterGroup.addClickButton(), new ToolbarClickButton.Listener() {
      @Override
      public void onClicked() {
        searchUi.getSearch().setQuery("mentions:me");
        onQueryEntered();
      }
    }).setVisualElement(createSvgIcon(ICON_MENTIONS));
```

- [ ] **Step 3: Verify build compiles**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -5`

- [ ] **Step 4: Commit**

```bash
git add wave/src/main/java/org/waveprotocol/box/webclient/search/SearchPresenter.java
git commit -m "feat(search): add Mentions toolbar button to search panel"
```

---

### Task 15: Update search help panel

**Files:**
- Modify: `wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchWidget.ui.xml`

- [ ] **Step 1: Add mentions: to filter list**

In the help panel section of `SearchWidget.ui.xml`, find where the filter descriptions are listed (e.g., `in:`, `with:`, `creator:`, `tag:`, `unread:`, `title:`, `content:`). Add a new entry for `mentions:`:

```xml
<tr>
  <td class="{style.searchFilterName}">mentions:</td>
  <td>Waves where you are @mentioned</td>
</tr>
```

- [ ] **Step 2: Add clickable example**

In the examples section of the help panel, add:

```xml
<div class="{style.searchExample}" data-query="mentions:me">mentions:me</div>
```

- [ ] **Step 3: Commit**

```bash
git add wave/src/main/resources/org/waveprotocol/box/webclient/search/SearchWidget.ui.xml
git commit -m "feat(search): add mentions: filter to search help panel"
```

---

### Task 16: Verify full build and local test

- [ ] **Step 1: Full compilation check**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt compile 2>&1 | tail -20`
Expected: BUILD SUCCESS with no errors

- [ ] **Step 2: Run existing tests to check for regressions**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt test 2>&1 | tail -30`
Expected: All existing tests pass

- [ ] **Step 3: Start local server and verify**

Run: `cd /Users/vega/devroot/incubator-wave/.claude/worktrees/zealous-noyce && sbt run`

Manual verification checklist:
1. Open wave in browser
2. Type `@` in a blip — autocomplete popup appears
3. Select a participant — `@name` text appears with blue highlight
4. Mentioned user is added as participant if not already present
5. Click "Mentions" button in toolbar — shows waves where you're mentioned
6. Search `mentions:me` in search box — returns correct results

- [ ] **Step 4: Final commit if any fixes needed**

```bash
git add -A
git commit -m "fix: address compilation and integration issues for mention feature"
```
