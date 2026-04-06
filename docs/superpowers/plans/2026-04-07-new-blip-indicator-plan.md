# Plan: Floating "N new messages" pill indicator

**Date:** 2026-04-07
**Branch:** `feat/new-blip-indicator`
**Feature Flag:** `new-blip-indicator` (server-side: `KnownFeatureFlags`, client-side: `ClientFlagsBase` flag `252`)

## Summary

When the user is scrolled up reading earlier blips and another participant adds a reply at the bottom, a floating pill appears at the bottom-center of the conversation scroll container showing "N new message(s) ↓". Clicking it scrolls to the first unseen blip and dismisses. The pill also auto-dismisses when the user manually scrolls near the bottom.

## Architecture Understanding

### Scroll Container
- `FixedConversationViewBuilder.outputHtml()` renders a scroll container div with id `{convId}T` and class `fixedThread` (overflow-y: scroll)
- `TopConversationDomImpl.getThreadContainer()` returns this element
- `DomScrollPanel.create(threadContainer)` wraps it for scroll queries via `getViewport()`, `getContent()`
- `ScrollPanel<View>` is available through `ProxyScrollPanel` → `AnimatedScrollPanel` → `DomScrollPanel`

### Blip Addition Events
- `LiveConversationViewRenderer.LiveConversationRenderer.onBlipAdded()` at line 129 handles new blip rendering
- It has access to the `conversation` and the `blip` being added
- The `blip.getAuthorId()` provides the blip author for filtering own blips

### Existing Patterns
- `ContinuationIndicatorViewBuilder` — uses `ClientBundle` + `CssResource` + `UiBuilder` pattern
- `WavePanelResourceLoader` — centralized CSS injection, one static field per resource bundle
- Feature flags: `FlagConstants` (string ID), `ClientFlagsBase` (field + constructor init + accessor), `ClientFlags.get()` usage
- Server-side flags: `KnownFeatureFlags.DEFAULTS` list

### Wiring
- `StageTwo.DefaultProvider.createBlipQueueRenderer()` (line 656) creates `LiveConversationViewRenderer` and calls `.init()`
- `StageTwo.DefaultProvider.installFeatures()` (line 1189) is where eager UI features are installed
- `stageOne.getWavePanel()` provides access to the wave panel
- `getSignedInUser()` provides current user identity
- The conversation's scroll container element is accessed via `TopConversationDomImpl.getThreadContainer()`

## Implementation

### Step 1: Add feature flag constants

**File: `wave/src/main/java/org/waveprotocol/wave/common/bootstrap/FlagConstants.java`**
```java
// New blip indicator pill
public static final String ENABLE_NEW_BLIP_INDICATOR = "252";
```
Add after ENABLE_SLIDE_NAVIGATION block (line 275). Also add to `__NAME_MAPPING__` array.

**File: `wave/src/main/java/org/waveprotocol/wave/client/util/ClientFlagsBase.java`**
- Add field: `private final Boolean enableNewBlipIndicator;`
- In constructor: `enableNewBlipIndicator = helper.getBoolean(FlagConstants.ENABLE_NEW_BLIP_INDICATOR, false);`
- Add accessor: `public Boolean enableNewBlipIndicator() { return enableNewBlipIndicator; }`

**File: `wave/src/main/java/org/waveprotocol/box/server/persistence/KnownFeatureFlags.java`**
- Add to DEFAULTS list: `new FeatureFlag("new-blip-indicator", "Show floating pill when new messages arrive below viewport", false, Collections.emptyMap())`

### Step 2: Create CSS

**File: `wave/src/main/resources/org/waveprotocol/wave/client/wavepanel/view/dom/full/NewBlipIndicator.css`**

```css
.pill {
  position: absolute;
  bottom: 12px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 10;
  background: rgba(66, 133, 244, 0.92);
  color: #fff;
  font-size: 13px;
  font-family: Arial, sans-serif;
  padding: 6px 16px;
  border-radius: 16px;
  cursor: pointer;
  box-shadow: 0 2px 6px rgba(0,0,0,0.18);
  opacity: 0;
  transition: opacity 0.2s ease-in;
  pointer-events: none;
  white-space: nowrap;
}

.pillVisible {
  opacity: 1;
  pointer-events: auto;
}

.pill:hover {
  background: rgba(66, 133, 244, 1);
}
```

### Step 3: Create NewBlipIndicatorBuilder (view builder)

**File: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/NewBlipIndicatorBuilder.java`**

```java
package org.waveprotocol.wave.client.wavepanel.view.dom.full;

import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.CssResource;

/**
 * Builder for the "N new messages" floating pill indicator.
 * Not a UiBuilder — this is a runtime-created DOM element managed by the presenter.
 */
public final class NewBlipIndicatorBuilder {

  public interface Resources extends ClientBundle {
    @Source("NewBlipIndicator.css")
    Css css();
  }

  public interface Css extends CssResource {
    String pill();
    String pillVisible();
  }

  private NewBlipIndicatorBuilder() {}
}
```

The builder is minimal — the presenter creates and manages the DOM element directly, as the pill is not part of the initial HTML rendering tree. This follows the pattern of runtime-created UI like toolbars.

### Step 4: Register CSS in WavePanelResourceLoader

**File: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/WavePanelResourceLoader.java`**

Add:
```java
private final static NewBlipIndicatorBuilder.Resources newBlipIndicator =
    GWT.create(NewBlipIndicatorBuilder.Resources.class);
```

In static block add:
```java
StyleInjector.inject(newBlipIndicator.css().getText(), isSynchronous);
```

Add accessor:
```java
public static NewBlipIndicatorBuilder.Resources getNewBlipIndicator() {
  return newBlipIndicator;
}
```

### Step 5: Create NewBlipIndicatorPresenter

**File: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/impl/NewBlipIndicatorPresenter.java`**

This is the core logic class. It:
1. Listens for new blip notifications from `LiveConversationViewRenderer`
2. Checks if the user is scrolled near the bottom (within 50px threshold)
3. Filters out the current user's own blips
4. Shows/updates the pill with a count
5. On click: scrolls to the first new blip position, dismisses
6. On scroll to bottom: auto-dismisses

```java
package org.waveprotocol.wave.client.wavepanel.impl;

import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.EventListener;
import org.waveprotocol.wave.client.scroll.DomScrollPanel;
import org.waveprotocol.wave.client.scroll.Extent;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.NewBlipIndicatorBuilder;
import org.waveprotocol.wave.client.wavepanel.view.dom.full.WavePanelResourceLoader;
import org.waveprotocol.wave.model.conversation.ObservableConversationBlip;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class NewBlipIndicatorPresenter {
  private static final int NEAR_BOTTOM_THRESHOLD_PX = 50;

  private final ParticipantId signedInUser;
  private final NewBlipIndicatorBuilder.Css css;
  private Element pillElement;
  private Element scrollContainer;
  private DomScrollPanel scrollPanel;
  private int newBlipCount;
  private double firstNewBlipScrollTop = -1;

  public NewBlipIndicatorPresenter(ParticipantId signedInUser) {
    this.signedInUser = signedInUser;
    this.css = WavePanelResourceLoader.getNewBlipIndicator().css();
  }

  /** Attach to a conversation's scroll container element. */
  public void attach(Element threadContainer) {
    this.scrollContainer = threadContainer;
    this.scrollPanel = DomScrollPanel.create(threadContainer);

    // Create pill element inside the scroll container's PARENT
    // (the fixedSelf div) so it floats above the scrollable content.
    pillElement = Document.get().createDivElement();
    pillElement.setClassName(css.pill());
    pillElement.setInnerText("");
    threadContainer.getParentElement().appendChild(pillElement);

    // Click handler: scroll to new content and dismiss
    Event.sinkEvents(pillElement, Event.ONCLICK);
    Event.setEventListener(pillElement, new EventListener() {
      @Override
      public void onBrowserEvent(Event event) {
        if (Event.ONCLICK == event.getTypeInt()) {
          scrollToNewBlips();
        }
      }
    });

    // Scroll listener on the thread container: dismiss when near bottom
    Event.sinkEvents(threadContainer, Event.ONSCROLL);
    final EventListener existingListener = Event.getEventListener(threadContainer);
    Event.setEventListener(threadContainer, new EventListener() {
      @Override
      public void onBrowserEvent(Event event) {
        if (existingListener != null) {
          existingListener.onBrowserEvent(event);
        }
        if (Event.ONSCROLL == event.getTypeInt()) {
          onScroll();
        }
      }
    });
  }

  /** Called by LiveConversationViewRenderer when a new blip is added. */
  public void onNewBlip(ObservableConversationBlip blip) {
    if (scrollContainer == null || scrollPanel == null) {
      return;
    }
    // Ignore the user's own blips
    if (blip.getAuthorId() != null && blip.getAuthorId().equals(signedInUser)) {
      return;
    }
    // If user is near bottom, do nothing (they'll see it)
    if (isNearBottom()) {
      return;
    }
    // Increment count and record scroll position of first new blip
    newBlipCount++;
    if (firstNewBlipScrollTop < 0) {
      Extent content = scrollPanel.getContent();
      firstNewBlipScrollTop = content.getEnd() - NEAR_BOTTOM_THRESHOLD_PX;
    }
    updatePill();
  }

  private boolean isNearBottom() {
    Extent viewport = scrollPanel.getViewport();
    Extent content = scrollPanel.getContent();
    return viewport.getEnd() >= content.getEnd() - NEAR_BOTTOM_THRESHOLD_PX;
  }

  private void updatePill() {
    if (pillElement == null) return;
    if (newBlipCount <= 0) {
      pillElement.removeClassName(css.pillVisible());
      return;
    }
    String text = newBlipCount == 1
        ? "1 new message \u2193"
        : newBlipCount + " new messages \u2193";
    pillElement.setInnerText(text);
    pillElement.addClassName(css.pillVisible());
  }

  private void scrollToNewBlips() {
    if (scrollPanel != null && firstNewBlipScrollTop >= 0) {
      scrollPanel.moveTo(firstNewBlipScrollTop);
    }
    dismiss();
  }

  private void onScroll() {
    if (newBlipCount > 0 && isNearBottom()) {
      dismiss();
    }
  }

  private void dismiss() {
    newBlipCount = 0;
    firstNewBlipScrollTop = -1;
    updatePill();
  }
}
```

### Step 6: Add notification hook in LiveConversationViewRenderer

**File: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/render/LiveConversationViewRenderer.java`**

Add a field and setter for the presenter:
```java
private NewBlipIndicatorPresenter newBlipIndicatorPresenter;

public void setNewBlipIndicatorPresenter(NewBlipIndicatorPresenter presenter) {
  this.newBlipIndicatorPresenter = presenter;
}
```

In `LiveConversationRenderer.onBlipAdded()` (line 129), after the existing rendering code (after `bubbleBlipCountUpdate(blip)` on line 138), add:
```java
if (newBlipIndicatorPresenter != null) {
  newBlipIndicatorPresenter.onNewBlip(blip);
}
```

Note: The presenter reference is on the outer class, accessed from the inner class. This works because inner class has access to enclosing class fields.

### Step 7: Wire up in StageTwo

**File: `wave/src/main/java/org/waveprotocol/wave/client/StageTwo.java`**

In `createBlipQueueRenderer()` (around line 670, after `live.init()`), add:
```java
// Install new blip indicator pill (feature-flagged)
if (Boolean.TRUE.equals(ClientFlags.get().enableNewBlipIndicator())) {
  NewBlipIndicatorPresenter newBlipPresenter =
      new NewBlipIndicatorPresenter(getSignedInUser());
  live.setNewBlipIndicatorPresenter(newBlipPresenter);
  // Defer attachment until DOM is ready (after rendering)
  SchedulerInstance.getMediumPriorityTimer().scheduleDelayed(new Task() {
    @Override
    public void execute() {
      // Find the conversation's thread container element
      Element threadContainer = findThreadContainer();
      if (threadContainer != null) {
        newBlipPresenter.attach(threadContainer);
      }
    }
  }, 100);
}
```

The `findThreadContainer()` helper:
```java
private Element findThreadContainer() {
  // The root conversation's thread container has the id pattern {convId}T
  // We can find it through the DOM since there's typically one root conversation
  ObservableConversationView convs = getConversations();
  if (convs != null) {
    for (org.waveprotocol.wave.model.conversation.Conversation c : convs.getConversations()) {
      if (c.getAnchor() == null) {
        // Root conversation
        org.waveprotocol.wave.client.wavepanel.view.ConversationView cv =
            getModelAsViewProvider().getConversationView(c);
        if (cv instanceof org.waveprotocol.wave.client.wavepanel.view.impl.TopConversationViewImpl) {
          // Get the DOM element through the view chain
          Object intrinsic = ((org.waveprotocol.wave.client.wavepanel.view.impl.TopConversationViewImpl<?>) cv).getIntrinsic();
          if (intrinsic instanceof org.waveprotocol.wave.client.wavepanel.view.dom.TopConversationDomImpl) {
            return ((org.waveprotocol.wave.client.wavepanel.view.dom.TopConversationDomImpl) intrinsic).getThreadContainer();
          }
        }
      }
    }
  }
  return null;
}
```

**Alternative (simpler):** Since the pill needs the fixedThread container, and we know its ID pattern, we can find it by iterating the DOM. However, the `TopConversationDomImpl` approach is type-safe. If the view chain proves too fragile, fall back to `Document.get().getElementsByTagName()` with class name matching.

**Simplest approach** — defer to `installFeatures()` where we have access to all stage components and the DOM is rendered:

In `installFeatures()` (line 1189), add after existing code:
```java
if (Boolean.TRUE.equals(ClientFlags.get().enableNewBlipIndicator())) {
  // newBlipPresenter was already attached to live renderer in createBlipQueueRenderer
  // Now attach the DOM: find the fixedThread container
  Element threadContainer = stageOne.getWavePanel().getElement()
      .querySelector("." + WavePanelResourceLoader.getConversation().css().fixedThread());
  if (threadContainer != null && newBlipPresenter != null) {
    newBlipPresenter.attach(threadContainer);
  }
}
```

This requires storing `newBlipPresenter` as a field in DefaultProvider. This is the cleanest approach since `installFeatures()` runs after `ensureRendered()` so the DOM is guaranteed to exist.

### Step 8: Adjust FixedConversationViewBuilder for positioning context

**File: `wave/src/main/java/org/waveprotocol/wave/client/wavepanel/view/dom/full/FixedConversationViewBuilder.java`**

The pill uses `position: absolute` and needs the `fixedSelf` parent to be the positioning context. Looking at `Conversation.css`, `.fixedSelf` already has `position: absolute`, so this is already correct. No changes needed here.

## Files Changed Summary

| File | Action | Description |
|------|--------|-------------|
| `FlagConstants.java` | Modify | Add `ENABLE_NEW_BLIP_INDICATOR = "252"` + name mapping |
| `ClientFlagsBase.java` | Modify | Add field, constructor init, accessor |
| `KnownFeatureFlags.java` | Modify | Add `new-blip-indicator` flag |
| `NewBlipIndicator.css` | Create | Pill styling |
| `NewBlipIndicatorBuilder.java` | Create | CSS resource bundle interface |
| `WavePanelResourceLoader.java` | Modify | Register + inject CSS, add accessor |
| `NewBlipIndicatorPresenter.java` | Create | Core logic: scroll detection, pill management, click/scroll handlers |
| `LiveConversationViewRenderer.java` | Modify | Add presenter field + setter, call `onNewBlip()` in `onBlipAdded()` |
| `StageTwo.java` | Modify | Wire presenter in `createBlipQueueRenderer()` + attach in `installFeatures()` |

## Risk Mitigation

1. **Scroll performance**: The scroll listener does minimal work (one viewport/content extent check). No DOM layout thrashing.
2. **Race condition**: Pill attachment is deferred to `installFeatures()` which runs after rendering. The presenter silently ignores `onNewBlip()` calls before attachment.
3. **Memory leaks**: The pill element and event listeners live for the lifetime of the conversation view, same as other features. No explicit cleanup needed since GWT handles it on page unload.
4. **GWT compatibility**: Uses only `com.google.gwt.dom.client.Element`, `Event.sinkEvents/setEventListener`, and `CssResource` — all standard GWT APIs.
5. **Existing behavior**: Feature-flagged off by default. When off, zero code paths are affected.
6. **Own blips**: Filtered by comparing `blip.getAuthorId()` with `signedInUser`.
