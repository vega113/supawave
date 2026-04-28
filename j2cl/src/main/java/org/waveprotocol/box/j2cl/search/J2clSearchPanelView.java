package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import elemental2.dom.Event;
import elemental2.dom.HTMLButtonElement;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import elemental2.dom.HTMLFormElement;
import elemental2.dom.HTMLInputElement;
import java.util.LinkedHashMap;
import java.util.Map;
import jsinterop.base.Js;
import org.waveprotocol.box.j2cl.root.J2clServerFirstRootShellDom;

public final class J2clSearchPanelView implements J2clSearchPanelController.View {
  public enum ShellPresentation {
    SIDE_CAR,
    ROOT_SHELL
  }

  static final class Copy {
    final String eyebrow;
    final String title;
    final String detail;

    private Copy(String eyebrow, String title, String detail) {
      this.eyebrow = eyebrow;
      this.title = title;
      this.detail = detail;
    }
  }

  private final HTMLElement host;
  private final HTMLElement sessionSummary;
  private final HTMLElement status;
  private final HTMLElement waveCount;
  private final HTMLInputElement queryInput;
  private final HTMLButtonElement submitButton;
  private final HTMLElement composeHost;
  private final HTMLDivElement digestList;
  private final HTMLElement emptyState;
  private final HTMLButtonElement showMoreButton;
  private final HTMLElement selectedWaveHost;
  private final Map<String, J2clDigestView> digestViews = new LinkedHashMap<String, J2clDigestView>();
  /**
   * J-UI-1 (#1079): per-digest views when the {@code j2cl-search-rail-cards}
   * flag is on; the legacy {@code digestViews} map stays in place for the
   * flag-off path so the two coexist while the flag bakes.
   */
  private final Map<String, J2clSearchRailCardView> railCardViews =
      new LinkedHashMap<String, J2clSearchRailCardView>();
  /** J-UI-1 (#1079): the {@code <wavy-search-rail>} host (when adopted). */
  private final HTMLElement searchRail;
  /**
   * J-UI-1 (#1079): when true, render digests as {@code <wavy-search-rail-card>}
   * children of the rail instead of plain-DOM {@code J2clDigestView}s.
   * Bound at construction from the SSR'd
   * {@code data-rail-cards-enabled="true"} attribute on the rail, written
   * by the server based on the {@code j2cl-search-rail-cards} flag value
   * for the current viewer.
   */
  private final boolean railCardsEnabled;
  private J2clSearchViewListener listener;
  /**
   * J-UI-2 (#1080 / R-4.5): aria-live region appended to the search rail
   * so saved-search and filter-chip clicks are announced to screen
   * readers. Created lazily on first {@link #announceNavigation(String)}
   * call.
   */
  private HTMLElement railLiveRegion;
  /**
   * J-UI-2 (#1080 / R-4.5): the rail emits {@code wavy-search-filter-toggled}
   * AND {@code wavy-search-submit} for a single chip click. When the
   * filter listener has already issued a search, we record the composed
   * query here so the submit listener can skip the duplicate. Reset to
   * {@code null} after the matching submit lands.
   */
  private String chipDrivenSubmitPending;

  public J2clSearchPanelView(HTMLElement host) {
    this(host, ShellPresentation.SIDE_CAR);
  }

  public J2clSearchPanelView(HTMLElement host, ShellPresentation shellPresentation) {
    Copy copy = copyFor(shellPresentation);
    this.host = host;
    host.className = "sidecar-root";

    HTMLElement adoptedShell =
        shellPresentation == ShellPresentation.ROOT_SHELL
            ? J2clServerFirstRootShellDom.findServerFirstWorkflow(host)
            : null;

    if (adoptedShell != null) {
      HTMLElement card = queryRequired(adoptedShell, ".sidecar-search-card");
      HTMLFormElement form = queryRequired(card, ".sidecar-search-toolbar");
      sessionSummary = queryRequired(card, ".sidecar-search-session");
      status = queryRequired(card, ".sidecar-search-status");
      waveCount = queryRequired(card, ".sidecar-wave-count");
      queryInput = queryRequired(card, ".sidecar-search-input");
      submitButton = queryRequired(card, ".sidecar-search-submit");
      composeHost = queryRequired(card, ".sidecar-search-compose");
      digestList = queryRequired(card, ".sidecar-digests");
      emptyState = queryRequired(card, ".sidecar-empty-state");
      showMoreButton = queryRequired(card, ".sidecar-show-more");
      selectedWaveHost = J2clServerFirstRootShellDom.findSelectedWaveHost(host);

      // J-UI-1 (#1079): the <wavy-search-rail> lives in the shell-root
      // nav slot, NOT inside the workflow host. Resolve it from the
      // document root.
      searchRail = findRail();
      // The flag value is emitted independently on <shell-root> as
      // data-j2cl-search-rail-cards. Reading it from <shell-root>
      // (rather than from the rail) means a missing-rail regression
      // surfaces as an error instead of a silent fallback to the
      // legacy digest list — matching the project rule "no legacy
      // fallbacks for flagged features". The data-rail-cards-enabled
      // attribute on the rail itself is preserved for SSR-side parity
      // tests but is no longer the source of truth.
      railCardsEnabled = readRailCardsFlag();

      form.onsubmit =
          event -> {
            event.preventDefault();
            if (listener != null) {
              listener.onQuerySubmitted(queryInput.value);
            }
            return null;
          };
      showMoreButton.onclick =
          event -> {
            if (listener != null) {
              listener.onShowMoreRequested();
            }
            return null;
          };
      // J-UI-1 (#1079): wire rail-emitted events into the panel listener
      // ONLY when the rail-cards path is the active rendering surface.
      // Gating keeps flag-off behavior identical to before this change.
      if (railCardsEnabled) {
        bindRailEventsToListener();
      }
      return;
    }

    host.innerHTML = "";

    HTMLElement shell = (HTMLElement) DomGlobal.document.createElement("section");
    shell.className = "sidecar-search-shell";
    host.appendChild(shell);

    HTMLElement layout = (HTMLElement) DomGlobal.document.createElement("div");
    layout.className = "sidecar-split-layout";
    shell.appendChild(layout);

    HTMLElement card = (HTMLElement) DomGlobal.document.createElement("div");
    card.className = "sidecar-search-card";
    layout.appendChild(card);

    HTMLElement eyebrow = (HTMLElement) DomGlobal.document.createElement("p");
    eyebrow.className = "sidecar-eyebrow";
    eyebrow.textContent = copy.eyebrow;
    card.appendChild(eyebrow);

    HTMLElement title = (HTMLElement) DomGlobal.document.createElement("h1");
    title.className = "sidecar-title";
    title.textContent = copy.title;
    card.appendChild(title);

    HTMLElement detail = (HTMLElement) DomGlobal.document.createElement("p");
    detail.className = "sidecar-detail";
    detail.textContent = copy.detail;
    card.appendChild(detail);

    sessionSummary = (HTMLElement) DomGlobal.document.createElement("p");
    sessionSummary.className = "sidecar-search-session";
    sessionSummary.textContent = "Inspecting the active root session.";
    card.appendChild(sessionSummary);

    HTMLFormElement form = (HTMLFormElement) DomGlobal.document.createElement("form");
    form.className = "sidecar-search-toolbar";
    card.appendChild(form);

    queryInput = (HTMLInputElement) DomGlobal.document.createElement("input");
    queryInput.className = "sidecar-search-input";
    queryInput.type = "search";
    queryInput.placeholder = "Search waves";
    queryInput.setAttribute("aria-label", "Search waves");
    queryInput.autocomplete = "off";
    form.appendChild(queryInput);

    submitButton = (HTMLButtonElement) DomGlobal.document.createElement("button");
    submitButton.className = "sidecar-search-submit";
    submitButton.type = "submit";
    submitButton.textContent = "Search";
    form.appendChild(submitButton);

    form.onsubmit =
        event -> {
          event.preventDefault();
          if (listener != null) {
            listener.onQuerySubmitted(queryInput.value);
          }
          return null;
        };

    composeHost = (HTMLElement) DomGlobal.document.createElement("div");
    composeHost.className = "sidecar-search-compose";
    card.appendChild(composeHost);

    status = (HTMLElement) DomGlobal.document.createElement("p");
    status.className = "sidecar-search-status";
    status.textContent = "Waiting for the first sidecar search response.";
    card.appendChild(status);

    waveCount = (HTMLElement) DomGlobal.document.createElement("p");
    waveCount.className = "sidecar-wave-count";
    card.appendChild(waveCount);

    digestList = (HTMLDivElement) DomGlobal.document.createElement("div");
    digestList.className = "sidecar-digests";
    card.appendChild(digestList);

    emptyState = (HTMLElement) DomGlobal.document.createElement("div");
    emptyState.className = "sidecar-empty-state";
    emptyState.textContent = "Search results will appear here.";
    card.appendChild(emptyState);

    showMoreButton = (HTMLButtonElement) DomGlobal.document.createElement("button");
    showMoreButton.className = "sidecar-show-more";
    showMoreButton.type = "button";
    showMoreButton.textContent = "Show more waves";
    showMoreButton.hidden = true;
    showMoreButton.onclick =
        event -> {
          if (listener != null) {
            listener.onShowMoreRequested();
          }
          return null;
        };
    card.appendChild(showMoreButton);

    selectedWaveHost = (HTMLElement) DomGlobal.document.createElement("div");
    selectedWaveHost.className = "sidecar-selected-host";
    selectedWaveHost.setAttribute("data-j2cl-selected-wave-host", "true");
    layout.appendChild(selectedWaveHost);

    // J-UI-1 (#1079): the legacy non-adopted (sidecar-only) constructor
    // does not mount a <wavy-search-rail>; cards path stays disabled.
    searchRail = null;
    railCardsEnabled = false;
  }

  @Override
  public void bind(J2clSearchViewListener listener) {
    this.listener = listener;
  }

  @Override
  public void setQuery(String query) {
    String safeValue = query == null ? "" : query;
    queryInput.value = safeValue;
    // J-UI-1 (#1079): mirror the controller's normalised query onto the
    // rail's `query` attribute so the rail's saved-folder aria-current
    // derivation and the input box stay in sync with the sidecar's view
    // of the active query. Gated by railCardsEnabled to avoid changing
    // flag-off behavior; the rail's `query` setter does not re-emit
    // wavy-search-submit so this is loop-safe.
    if (railCardsEnabled && searchRail != null) {
      searchRail.setAttribute("query", safeValue);
    }
  }

  @Override
  public void setLoading(boolean loading) {
    queryInput.disabled = loading;
    submitButton.disabled = loading;
    showMoreButton.disabled = loading;
    host.className = loading ? "sidecar-root sidecar-root-loading" : "sidecar-root";
  }

  @Override
  public void setSessionSummary(String summary) {
    sessionSummary.textContent = summary;
  }

  @Override
  public void setStatus(String statusText, boolean error) {
    status.className = error ? "sidecar-search-status sidecar-search-status-error" : "sidecar-search-status";
    status.textContent = statusText;
  }

  @Override
  public void render(J2clSearchResultModel model) {
    waveCount.textContent = model.getWaveCountText();
    if (railCardsEnabled) {
      renderRailCards(model);
    } else {
      renderLegacyDigests(model);
    }
    showMoreButton.hidden = !model.isShowMoreVisible();
  }

  /**
   * J-UI-1 (#1079): the new rendering path — projects each digest as a
   * {@code <wavy-search-rail-card>} child of the {@code <wavy-search-rail>}'s
   * {@code cards} slot. Per the project rule "no legacy fallbacks for
   * flagged features", a missing rail with the flag on raises a status
   * error; we do not silently fall back to the legacy plain-DOM digest list.
   */
  private void renderRailCards(J2clSearchResultModel model) {
    if (searchRail == null) {
      // The flag is on but the rail is unreachable — surface the error
      // through the existing aria-live status slot.
      setStatus(
          "Search rail is unavailable; cards cannot render. Reload the page.",
          true);
      return;
    }
    digestList.innerHTML = "";
    digestList.hidden = true;
    digestViews.clear();
    clearRailCards();
    railCardViews.clear();

    if (model.isEmpty()) {
      emptyState.hidden = false;
      emptyState.textContent = model.getEmptyMessage().isEmpty()
          ? "No waves matched this query."
          : model.getEmptyMessage();
      // Empty state copy lives in the workflow's status text via
      // setStatus() — do not mirror long-form copy into the rail's
      // numeric result-count slot.
      searchRail.setAttribute("result-count", "");
      return;
    }
    emptyState.hidden = true;
    for (J2clSearchDigestItem item : model.getDigestItems()) {
      if (item.getWaveId() == null) {
        continue;
      }
      J2clSearchRailCardView cardView =
          new J2clSearchRailCardView(
              item,
              waveId -> {
                if (listener != null) {
                  listener.onDigestSelected(waveId);
                }
              });
      railCardViews.put(item.getWaveId(), cardView);
      searchRail.appendChild(cardView.element());
    }
    searchRail.setAttribute("result-count", model.getWaveCountText());
  }

  private void renderLegacyDigests(J2clSearchResultModel model) {
    digestList.innerHTML = "";
    digestList.hidden = false;
    digestViews.clear();

    if (model.isEmpty()) {
      emptyState.hidden = false;
      emptyState.textContent = model.getEmptyMessage().isEmpty()
          ? "No waves matched this query."
          : model.getEmptyMessage();
      return;
    }
    emptyState.hidden = true;
    for (J2clSearchDigestItem item : model.getDigestItems()) {
      if (item.getWaveId() == null) {
        continue;
      }
      J2clDigestView digestView =
          new J2clDigestView(
              item,
              waveId -> {
                if (listener != null) {
                  listener.onDigestSelected(waveId);
                }
              });
      digestViews.put(item.getWaveId(), digestView);
      digestList.appendChild(digestView.element());
    }
  }

  @Override
  public void setSelectedWaveId(String waveId) {
    for (Map.Entry<String, J2clDigestView> entry : digestViews.entrySet()) {
      entry.getValue().setSelected(entry.getKey() != null && entry.getKey().equals(waveId));
    }
    for (Map.Entry<String, J2clSearchRailCardView> entry : railCardViews.entrySet()) {
      entry.getValue().setSelected(entry.getKey() != null && entry.getKey().equals(waveId));
    }
  }

  @Override
  public boolean updateDigestUnread(String waveId, int unreadCount) {
    if (waveId == null || waveId.isEmpty()) {
      return false;
    }
    boolean updated = false;
    J2clDigestView digest = digestViews.get(waveId);
    if (digest != null) {
      updated |= digest.setUnreadCount(unreadCount);
    }
    J2clSearchRailCardView card = railCardViews.get(waveId);
    if (card != null) {
      updated |= card.setUnreadCount(unreadCount);
    }
    return updated;
  }

  public HTMLElement getSelectedWaveHost() {
    return selectedWaveHost;
  }

  public HTMLElement getComposeHost() {
    return composeHost;
  }

  /**
   * J-UI-1 (#1079): looks up the {@code <wavy-search-rail>} element from
   * the document. The rail lives in the {@code shell-root} nav slot so it
   * is not a descendant of the workflow {@code host}. Returns {@code null}
   * when missing (e.g. legacy view contexts that have not mounted the
   * rail).
   */
  private static HTMLElement findRail() {
    Object element = DomGlobal.document.querySelector("wavy-search-rail");
    return element == null ? null : (HTMLElement) element;
  }

  /**
   * J-UI-1 (#1079): reads {@code data-j2cl-search-rail-cards} from the
   * {@code <shell-root>} element so the flag value is independent of
   * the rail element's lifecycle. SSR writes this attribute when the
   * {@code j2cl-search-rail-cards} feature flag is enabled for the
   * current viewer.
   */
  private static boolean readRailCardsFlag() {
    Object shell = DomGlobal.document.querySelector("shell-root[data-j2cl-search-rail-cards=\"true\"]");
    return shell != null;
  }

  /**
   * J-UI-1 (#1079): removes any previously-projected
   * {@code <wavy-search-rail-card>} children from the rail so a new render
   * does not stack duplicates on top of the prior result set.
   */
  private void clearRailCards() {
    if (searchRail == null) {
      return;
    }
    elemental2.dom.NodeList<elemental2.dom.Element> existing =
        searchRail.querySelectorAll(":scope > wavy-search-rail-card");
    for (int i = (int) existing.length - 1; i >= 0; i--) {
      elemental2.dom.Element child = existing.getAt(i);
      if (child != null && child.parentNode != null) {
        child.parentNode.removeChild(child);
      }
    }
  }

  /**
   * J-UI-1 (#1079): subscribes the search panel listener to the rail's
   * emitted events so user interaction with the rail's query box,
   * saved-search folders, filter chips, and refresh button drives the
   * sidecar search controller. The legacy hidden form binding stays in
   * place so SSR-driven smoke tests and re-entrancy via setQuery
   * continue to work.
   */
  private void bindRailEventsToListener() {
    if (searchRail == null) {
      return;
    }
    searchRail.addEventListener(
        "wavy-search-submit",
        (Event evt) -> {
          if (listener == null) {
            return;
          }
          String query = readDetailString(evt, "query");
          String resolved = query == null ? queryInput.value : query;
          // J-UI-2 (#1080 / R-4.5): chip toggles emit both
          // wavy-search-filter-toggled and wavy-search-submit; the
          // filter listener already drove a search via onFilterToggled.
          // Skip the redundant submit so the chip click does not
          // produce two identical backend requests. See
          // shouldSuppressChipDrivenSubmit for the predicate.
          if (shouldSuppressChipDrivenSubmit(chipDrivenSubmitPending, resolved)) {
            chipDrivenSubmitPending = null;
            return;
          }
          chipDrivenSubmitPending = null;
          listener.onQuerySubmitted(resolved);
        });
    searchRail.addEventListener(
        "wavy-saved-search-selected",
        (Event evt) -> {
          if (listener == null) {
            return;
          }
          String query = readDetailString(evt, "query");
          if (query == null || query.isEmpty()) {
            return;
          }
          // J-UI-2 (#1080 / R-4.5): route through onSavedSearchSelected
          // so the controller can announce the navigation and re-focus
          // the active folder button. The folderId/label come straight
          // from the rail's CustomEvent detail; missing fields fall back
          // to the query token for the announcement.
          String folderId = readDetailString(evt, "folderId");
          String label = readDetailString(evt, "label");
          if (label == null || label.isEmpty()) {
            label = labelForFolderId(folderId, query);
          }
          listener.onSavedSearchSelected(folderId, label, query);
        });
    // J-UI-2 (#1080 / R-4.5): chip toggles emit BOTH wavy-search-submit
    // and wavy-search-filter-toggled. Bind the more specific event so
    // the controller can announce the chip-active state. The submit
    // listener guard below ensures the chip-driven submit does not
    // double-fire onQuerySubmitted — readFromFilterToggle short-
    // circuits the next submit event so the same chip click does not
    // produce two backend requests.
    searchRail.addEventListener(
        "wavy-search-filter-toggled",
        (Event evt) -> {
          if (listener == null) {
            return;
          }
          String filterId = readDetailString(evt, "filterId");
          String label = readDetailString(evt, "label");
          if (label == null || label.isEmpty()) {
            label = labelForFilterId(filterId);
          }
          String composed = readDetailString(evt, "query");
          boolean active = readDetailBoolean(evt, "active");
          // Mark the next submit event as "consumed by the filter
          // pathway" so the submit listener skips it.
          chipDrivenSubmitPending = composed == null ? "" : composed;
          listener.onFilterToggled(filterId, label, active, composed == null ? "" : composed);
        });
    searchRail.addEventListener(
        "wavy-search-refresh-requested",
        (Event evt) -> {
          if (listener == null) {
            return;
          }
          // J-UI-1 (#1079) — Codex review: refresh must NOT clear the
          // selected wave or reset the page size; that's the contract
          // for `wavy-search-refresh-requested` (re-fetch the active
          // query in place). onRefreshRequested re-issues the search
          // without going through the new-query reset path.
          listener.onRefreshRequested();
        });
  }

  /**
   * J-UI-2 (#1080 / R-4.5): predicate for the wavy-search-submit
   * dedupe. Returns true when the pending chip-driven submit query
   * matches the resolved submit value — the contract relies on the
   * Lit rail dispatching {@code wavy-search-filter-toggled} BEFORE
   * {@code wavy-search-submit} on a chip toggle (verified by the
   * "filter-toggled is dispatched BEFORE wavy-search-submit"
   * regression test in {@code wavy-search-rail.test.js}). Extracted
   * for JVM testability — the live event-listener path uses
   * elemental2 DOM types which are not available under plain JUnit.
   */
  static boolean shouldSuppressChipDrivenSubmit(String pending, String resolved) {
    if (pending == null) {
      return false;
    }
    return pending.equals(resolved);
  }

  private static String readDetailString(Event evt, String key) {
    Object detail = Js.asPropertyMap(evt).get("detail");
    if (detail == null) {
      return null;
    }
    Object value = Js.asPropertyMap(detail).get(key);
    return value == null ? null : String.valueOf(value);
  }

  /**
   * J-UI-2 (#1080 / R-4.5): read a boolean detail from the rail's
   * CustomEvent. Truthy strings ({@code "true"}, {@code "1"}) and the
   * native {@code Boolean.TRUE} both return true; everything else is
   * false. Used by the filter-toggled listener to detect the chip's
   * pressed state.
   */
  private static boolean readDetailBoolean(Event evt, String key) {
    Object detail = Js.asPropertyMap(evt).get("detail");
    if (detail == null) {
      return false;
    }
    Object value = Js.asPropertyMap(detail).get(key);
    if (value == null) {
      return false;
    }
    if (value instanceof Boolean) {
      return ((Boolean) value).booleanValue();
    }
    String text = String.valueOf(value).toLowerCase();
    return text.equals("true") || text.equals("1");
  }

  /**
   * J-UI-2 (#1080 / R-4.5): folder labels for screen-reader announcement
   * when the rail does not include the label in the CustomEvent detail.
   * Mirrors the canonical FOLDERS list in
   * {@code j2cl/lit/src/elements/wavy-search-rail.js}; if a folderId is
   * unknown we fall back to the raw query token so the announcement is
   * still meaningful.
   */
  private static String labelForFolderId(String folderId, String fallbackQuery) {
    if (folderId == null) {
      return fallbackQuery == null ? "" : fallbackQuery;
    }
    switch (folderId) {
      case "inbox":
        return "Inbox";
      case "mentions":
        return "Mentions";
      case "tasks":
        return "Tasks";
      case "public":
        return "Public";
      case "archive":
        return "Archive";
      case "pinned":
        return "Pinned";
      default:
        return fallbackQuery == null ? folderId : fallbackQuery;
    }
  }

  /**
   * J-UI-2 (#1080 / R-4.5): chip labels for screen-reader announcement,
   * mirroring the FILTERS list in the rail.
   */
  private static String labelForFilterId(String filterId) {
    if (filterId == null) {
      return "";
    }
    switch (filterId) {
      case "unread":
        return "Unread only";
      case "attachments":
        return "With attachments";
      case "from-me":
        return "From me";
      default:
        return filterId;
    }
  }

  @Override
  public void announceNavigation(String label) {
    if (label == null || label.isEmpty()) {
      return;
    }
    HTMLElement region = ensureRailLiveRegion();
    if (region == null) {
      return;
    }
    // Toggle textContent: setting the same string twice in a row does
    // not always re-trigger an announcement, so we clear first.
    region.textContent = "";
    region.textContent = label;
  }

  @Override
  public void focusActiveFolder() {
    if (searchRail == null) {
      return;
    }
    // The rail keeps the active folder pinned via aria-current=page;
    // ask the Lit element to focus it through its public method so we
    // do not reach into shadow DOM from the J2CL side. Cast the host
    // through a structural interface and call the method directly;
    // when the rail predates this method (e.g. a stale SSR snapshot)
    // the call is a silent no-op via the optional-chain guard below.
    if (Js.asPropertyMap(searchRail).get("focusActiveFolder") == null) {
      return;
    }
    Js.<RailFocusBridge>cast(searchRail).focusActiveFolder();
  }

  /**
   * J-UI-2 (#1080 / R-4.5): structural type for invoking the Lit rail's
   * {@code focusActiveFolder()} method from J2CL without reaching into
   * the shadow DOM. The method is exposed by
   * {@code j2cl/lit/src/elements/wavy-search-rail.js}.
   */
  @jsinterop.annotations.JsType(isNative = true, namespace = jsinterop.annotations.JsPackage.GLOBAL, name = "Object")
  private interface RailFocusBridge {
    void focusActiveFolder();
  }

  private HTMLElement ensureRailLiveRegion() {
    if (railLiveRegion != null) {
      return railLiveRegion;
    }
    if (searchRail == null) {
      return null;
    }
    HTMLElement region =
        (HTMLElement) DomGlobal.document.createElement("div");
    region.className = "j2cl-rail-live-region";
    region.setAttribute("role", "status");
    region.setAttribute("aria-live", "polite");
    region.setAttribute("aria-atomic", "true");
    // Visually hidden, screen-reader announceable.
    region.setAttribute(
        "style",
        "position:absolute;width:1px;height:1px;margin:-1px;padding:0;"
            + "overflow:hidden;clip:rect(0 0 0 0);white-space:nowrap;border:0;");
    // Keep the live region OUTSIDE the rail's shadow root so screen
    // readers reliably pick up the assertive content. Append next to
    // the rail in the light DOM.
    if (searchRail.parentNode != null) {
      searchRail.parentNode.insertBefore(region, searchRail.nextSibling);
    } else {
      DomGlobal.document.body.appendChild(region);
    }
    railLiveRegion = region;
    return region;
  }

  static Copy copyFor(ShellPresentation shellPresentation) {
    if (shellPresentation == ShellPresentation.ROOT_SHELL) {
      return new Copy(
          "J2CL root shell",
          "Hosted workflow",
          "The signed-in root shell hosts the search, compose, and selected-wave surfaces inline.");
    }
    return new Copy(
        "Isolated J2CL search slice",
        "First real search/results vertical slice",
        "The root / route still runs the legacy GWT app. This sidecar route only mounts the first J2CL search panel slice.");
  }

  @SuppressWarnings("unchecked")
  private static <T> T queryRequired(HTMLElement root, String selector) {
    Object element = root.querySelector(selector);
    if (element == null) {
      throw new IllegalStateException(
          "Missing required server-rendered element for selector: " + selector);
    }
    return (T) element;
  }
}
