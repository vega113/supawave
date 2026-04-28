package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;

public final class J2clSearchPanelController
    implements J2clSearchViewListener, J2clSidecarRouteController.SearchPanelController {
  @FunctionalInterface
  public interface ErrorCallback {
    void accept(String error);
  }

  @FunctionalInterface
  public interface SuccessCallback<T> {
    void accept(T value);
  }

  public interface SearchGateway {
    void fetchRootSessionBootstrap(
        SuccessCallback<SidecarSessionBootstrap> onSuccess, ErrorCallback onError);

    void search(
        String query,
        int index,
        int numResults,
        SuccessCallback<SidecarSearchResponse> onSuccess,
        ErrorCallback onError);
  }

  public interface View {
    void bind(J2clSearchViewListener listener);

    void setQuery(String query);

    void setLoading(boolean loading);

    void setSessionSummary(String summary);

    void setStatus(String status, boolean error);

    void render(J2clSearchResultModel model);

    void setSelectedWaveId(String waveId);

    /**
     * F-4 (#1039 / R-4.4): updates the unread badge / stats text on the digest
     * card matching {@code waveId} without re-rendering the whole list.
     * Returns {@code true} when a matching card was found and updated.
     * Default no-op for legacy presentations that do not surface live unread.
     */
    default boolean updateDigestUnread(String waveId, int unreadCount) {
      return false;
    }

    /**
     * J-UI-2 (#1080 / R-4.5): announce a folder/chip navigation through an
     * aria-live region and move keyboard focus to the active folder
     * button. The default is a no-op so legacy view contexts (the
     * non-rail sidecar) keep their pre-existing behaviour.
     */
    default void announceNavigation(String label) {
    }

    /**
     * J-UI-2 (#1080 / R-4.5): move focus to the rail control representing
     * the currently active folder so keyboard users land predictably
     * after a saved-search button has driven a query change. No-op when
     * the view does not own a focusable folder list.
     */
    default void focusActiveFolder() {
    }
  }

  public interface RouteStateHandler {
    void onRouteStateChanged(
        J2clSidecarRouteState state, J2clSearchDigestItem digestItem, boolean userNavigation);
  }

  private final SearchGateway gateway;
  private final View view;
  private final RouteStateHandler routeStateHandler;
  private final int pageIncrement;
  private String currentQuery;
  private String selectedWaveId;
  private int currentPageSize;
  private int requestGeneration;
  private J2clSearchResultModel lastModel = J2clSearchResultModel.empty("Search results will appear here.");
  // J-UI-3 (#1081, R-5.1): optimistic-prepend bookkeeping. After a successful
  // create the controller stores a stub digest here so the rail shows the new
  // wave immediately while the server search index catches up. Each stub is
  // dropped once a refresh response includes the matching waveId, or after
  // OPTIMISTIC_TIMEOUT_MS to bound a stuck stub if the index never returns
  // it. The map is keyed by waveId so back-to-back creates each get their own
  // entry (no overwrite), and each stub is scoped to the query active at
  // submit time so it only renders into the rail it was created for.
  private final Map<String, PendingStub> pendingStubs = new LinkedHashMap<>();
  // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-CyWx: capture the
  // active query at the moment the user clicks submit, not when the
  // server response lands. Without this, a user who submits on query A
  // and switches to query B before the server confirms ends up with the
  // optimistic stub scoped to B (because onOptimisticDigest reads the
  // currentQuery field at success-callback time). The compose controller
  // calls {@link #markCreateSubmitted()} synchronously at the start of
  // submitCreate; the queue is consumed in submission order when each
  // onOptimisticDigest fires.
  private final Deque<String> pendingSubmitQueries = new ArrayDeque<>();
  private final OptimisticScheduler optimisticScheduler;

  /**
   * J-UI-3 (#1081, R-5.1): one per outstanding optimistic create. Holds the
   * stub digest, the query active when the create was submitted (so the
   * stub does not leak into unrelated rails), and the cancellable timeout
   * handle that retires the stub if the server never indexes the wave.
   */
  private static final class PendingStub {
    final J2clSearchDigestItem digest;
    final String query;
    Object timeoutHandle;

    PendingStub(J2clSearchDigestItem digest, String query) {
      this.digest = digest;
      this.query = query;
    }
  }
  // J-UI-3 (#1081): bound on a stuck optimistic stub. Set to 30s so the
  // race "timeout fires before slow gateway response arrives" is rare in
  // practice (Lucene refresh interval is typically <2s; 30s is well past
  // every observed lag). When the server response confirms the wave the
  // stub is dropped immediately and the timeout is cancelled, so this
  // bound only matters when the wave never gets indexed.
  private static final int OPTIMISTIC_TIMEOUT_MS = 30_000;

  /**
   * J-UI-3 (#1081, R-5.1): pluggable timer seam for the optimistic-prepend
   * timeout. Production wires {@link #defaultOptimisticScheduler}, which
   * delegates to {@code DomGlobal.setTimeout}. JVM tests pass a fake that
   * captures the runnable so they can drive the timeout deterministically.
   * The handle returned by {@link #scheduleTimeout} can be passed to
   * {@link #cancel} so the controller can retire the timer when the server
   * confirms the wave is indexed (avoids the timeout-fires-before-response
   * race).
   */
  public interface OptimisticScheduler {
    Object scheduleTimeout(int delayMs, Runnable action);

    void cancel(Object handle);
  }

  public J2clSearchPanelController(
      SearchGateway gateway,
      View view,
      RouteStateHandler routeStateHandler,
      double viewportWidth) {
    this(gateway, view, routeStateHandler, viewportWidth, defaultOptimisticScheduler());
  }

  public J2clSearchPanelController(
      SearchGateway gateway,
      View view,
      RouteStateHandler routeStateHandler,
      double viewportWidth,
      OptimisticScheduler optimisticScheduler) {
    this.gateway = gateway;
    this.view = view;
    this.routeStateHandler = routeStateHandler;
    this.pageIncrement = J2clSearchResultProjector.getPageSizeForViewport(viewportWidth);
    this.optimisticScheduler = optimisticScheduler;
  }

  private static OptimisticScheduler defaultOptimisticScheduler() {
    return new OptimisticScheduler() {
      @Override
      public Object scheduleTimeout(int delayMs, Runnable action) {
        return DomGlobal.setTimeout(ignored -> action.run(), delayMs);
      }

      @Override
      public void cancel(Object handle) {
        if (handle instanceof Double) {
          DomGlobal.clearTimeout(((Double) handle).doubleValue());
        }
      }
    };
  }

  @Override
  public void start(String initialQuery, String initialSelectedWaveId) {
    view.bind(this);
    currentQuery = J2clSearchResultProjector.normalizeQuery(initialQuery);
    currentPageSize = pageIncrement;
    selectedWaveId = normalizeSelectedWaveId(initialSelectedWaveId);
    view.setQuery(currentQuery);
    view.setSelectedWaveId(selectedWaveId);
    view.setLoading(true);
    view.setStatus("Bootstrapping the root session.", false);
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          view.setSessionSummary(bootstrap.getAddress());
          requestSearch();
        },
        error -> {
          view.setSessionSummary("Using the current browser session.");
          view.setStatus(
              "Continuing with the current browser session.",
              false);
          requestSearch();
        });
  }

  @Override
  public void restoreRoute(String query, String selectedWaveId) {
    currentQuery = J2clSearchResultProjector.normalizeQuery(query);
    this.selectedWaveId = normalizeSelectedWaveId(selectedWaveId);
    currentPageSize = pageIncrement;
    view.setQuery(currentQuery);
    view.setSelectedWaveId(this.selectedWaveId);
    requestSearch();
  }

  @Override
  public void syncSelection(String selectedWaveId) {
    this.selectedWaveId = normalizeSelectedWaveId(selectedWaveId);
    view.setSelectedWaveId(this.selectedWaveId);
  }

  @Override
  public void onQuerySubmitted(String query) {
    currentQuery = J2clSearchResultProjector.normalizeQuery(query);
    currentPageSize = pageIncrement;
    clearSelection(true);
    requestSearch();
  }

  @Override
  public void onShowMoreRequested() {
    currentPageSize += pageIncrement;
    requestSearch();
  }

  /**
   * J-UI-2 (#1080 / R-4.5): saved-search folder click — drive the same
   * query/route flow as {@link #onQuerySubmitted(String)} but also
   * announce the navigation and move focus so keyboard users land on
   * the active folder button. The view's hooks default to no-ops so
   * legacy non-rail surfaces keep their pre-existing behaviour.
   */
  @Override
  public void onSavedSearchSelected(String folderId, String label, String query) {
    onQuerySubmitted(query);
    if (label != null && !label.isEmpty()) {
      view.announceNavigation(label);
    }
    view.focusActiveFolder();
  }

  /**
   * J-UI-2 (#1080 / R-4.5): filter chip toggle — drive the search with
   * the composed query and announce the chip's new pressed state so
   * screen-reader users hear the active filter set after each toggle.
   */
  @Override
  public void onFilterToggled(
      String filterId, String label, boolean active, String composedQuery) {
    onQuerySubmitted(composedQuery);
    if (label != null && !label.isEmpty()) {
      String announcement =
          active ? label + " filter on" : label + " filter off";
      view.announceNavigation(announcement);
    }
  }

  /**
   * J-UI-1 (#1079): re-issues the current search WITHOUT touching
   * selection or page size. Used by the rail's refresh icon so
   * clicking refresh keeps the open wave on screen and the URL stable.
   */
  @Override
  public void onRefreshRequested() {
    requestSearch();
  }

  @Override
  public void onDigestSelected(String waveId) {
    selectedWaveId = normalizeSelectedWaveId(waveId);
    view.setSelectedWaveId(selectedWaveId);
    publishRouteState(true);
  }

  private void requestSearch() {
    final int generation = ++requestGeneration;
    final String query = currentQuery;
    final int numResults = currentPageSize;
    view.setQuery(query);
    view.setLoading(true);
    view.setStatus("Loading results for " + query + ".", false);
    gateway.search(
        query,
        0,
        numResults,
        response -> {
          if (generation != requestGeneration) {
            return;
          }
          J2clSearchResultModel projected = J2clSearchResultProjector.project(response, numResults);
          // J-UI-3 (#1081, R-5.1): if any just-created wave is still missing
          // from the server response, keep its optimistic stub at the top of
          // the rail; otherwise drop the stub now that the real digest is
          // present.
          lastModel = applyOptimisticStubs(projected);
          view.render(lastModel);
          view.setSelectedWaveId(selectedWaveId);
          if (selectedWaveId != null) {
            publishRouteState(false);
          }
          view.setStatus(buildStatusText(query, lastModel), false);
          view.setLoading(false);
        },
        error -> {
          if (generation != requestGeneration) {
            return;
          }
          // J-UI-3 (#1081, R-5.1): preserve outstanding optimistic stubs on
          // a transient gateway failure so a wave the user just created does
          // not vanish from the rail when the search index is briefly
          // unreachable. The empty model is what we'd render without stubs;
          // re-applying stubs prepends any waves we know are pending.
          J2clSearchResultModel base = J2clSearchResultModel.empty("Unable to load search results.");
          lastModel = applyOptimisticStubs(base);
          view.render(lastModel);
          view.setSelectedWaveId(selectedWaveId);
          view.setStatus("Search request failed: " + error, true);
          view.setLoading(false);
        });
  }

  /**
   * J-UI-3 (#1081, R-5.1): re-issues the current search query without
   * resetting selection or page size. Public entrypoint used by the
   * create-success path so the rail picks up the new digest after a wave
   * is created. Idempotent — safe to call repeatedly.
   */
  public void refreshSearch() {
    requestSearch();
  }

  /**
   * J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-CyWx: stamp the
   * active query as the next pending optimistic stub's submit-time
   * scope. Called synchronously by the compose controller at the start
   * of submitCreate (BEFORE the bootstrap fetch + server round-trip),
   * so a query change between submit and success cannot rebind the
   * stub to the wrong rail. The corresponding {@link
   * #onOptimisticDigest} call consumes the queue head in submission
   * order. Idempotent if called multiple times — each call enqueues
   * one entry and one onOptimisticDigest pops one entry.
   */
  public void markCreateSubmitted() {
    pendingSubmitQueries.addLast(currentQuery == null ? "" : currentQuery);
  }

  /**
   * J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DA7T: drop the
   * oldest entry from the pendingSubmitQueries deque without scoping a
   * stub. Called by the compose controller's failure hook so a create
   * that errors before the success callback (bootstrap or submit error)
   * does not leave a stale stamp in the queue. Without this, the next
   * successful create would consume the failed attempt's submit query
   * and scope its optimistic stub to the wrong rail. Idempotent:
   * silently no-ops when the queue is empty.
   */
  public void discardOldestSubmitStamp() {
    pendingSubmitQueries.pollFirst();
  }

  /**
   * J-UI-3 (#1090, thread PRRT_kwDOBwxLXs5-DA7T): pop the pending submit-query stamp when a
   * create attempt fails before reaching the success callback. Prevents stale stamps from
   * being consumed by a subsequent successful create under a different query.
   */
  public void cancelPendingCreateSubmit() {
    pendingSubmitQueries.pollFirst();
  }

  /**
   * J-UI-3 (#1081, R-5.1): record an optimistic digest for the just-created
   * wave and prepend it to the rail immediately. The stub is dropped when
   * the next search response includes the matching waveId or after
   * {@link #OPTIMISTIC_TIMEOUT_MS}, whichever comes first. {@code title}
   * is what the user typed; the stub author is left empty since we do not
   * yet know the bootstrap address at this site.
   *
   * <p>Multiple back-to-back creates each get their own pending entry so a
   * second submit before indexing catches up does not erase the first
   * (codex review thread on PR #1090). Each stub is scoped to the query
   * stamped by {@link #markCreateSubmitted} at the moment the user
   * clicked submit, so changing the rail query between submit and
   * server-success cannot leak the stub into an unrelated rail. When no
   * such stamp is available (legacy callers that did not call
   * markCreateSubmitted) the stub falls back to the active query, which
   * preserves prior behaviour.
   */
  public void onOptimisticDigest(String waveId, String title) {
    if (waveId == null || waveId.isEmpty()) {
      return;
    }
    String safeTitle = (title == null || title.isEmpty()) ? "(untitled wave)" : title;
    String safeSnippet = "";
    long now = System.currentTimeMillis();
    J2clSearchDigestItem stub =
        new J2clSearchDigestItem(waveId, safeTitle, safeSnippet, "", 0, 1, now, false);
    PendingStub previous = pendingStubs.remove(waveId);
    if (previous != null && previous.timeoutHandle != null) {
      optimisticScheduler.cancel(previous.timeoutHandle);
    }
    String submitQuery = pendingSubmitQueries.pollFirst();
    if (submitQuery == null) {
      submitQuery = currentQuery == null ? "" : currentQuery;
    }
    final PendingStub pending = new PendingStub(stub, submitQuery);
    pendingStubs.put(waveId, pending);
    // J-UI-3 (#1081, R-5.1) — codex P2 PRRT_kwDOBwxLXs5-DA7V: only
    // prepend immediately when the stamped submit query matches the
    // currently visible rail. If the user navigated to a different
    // query between submit and success, the stub stays in pendingStubs
    // (so it surfaces when they return) but is not painted into the
    // unrelated rail. This makes the immediate prepend match the
    // long-term scope semantics enforced by applyOptimisticStubs.
    String activeQuery = currentQuery == null ? "" : currentQuery;
    if (submitQuery.equals(activeQuery)) {
      lastModel = withActivePendingLabel(lastModel.withPrependedDigest(stub));
      view.render(lastModel);
    }
    // Schedule the stuck-stub bound BEFORE issuing requestSearch so a
    // synchronous response callback (in tests, or a hot-cache server) can
    // cancel the timeout via applyOptimisticStubs instead of stranding it.
    pending.timeoutHandle =
        optimisticScheduler.scheduleTimeout(
            OPTIMISTIC_TIMEOUT_MS,
            () -> {
              PendingStub stillPending = pendingStubs.get(waveId);
              if (stillPending == pending) {
                pendingStubs.remove(waveId);
                lastModel = withActivePendingLabel(lastModel.withoutDigest(waveId));
                view.render(lastModel);
              }
            });
    requestSearch();
  }

  /**
   * Returns {@code projected} with all outstanding optimistic stubs
   * reconciled: stubs whose waveId is now present in the server response
   * are dropped and their timeouts cancelled (the real digest wins);
   * stubs whose waveId is still missing are kept prepended on the rail
   * IF they were submitted under the active query; stubs from a different
   * query are skipped here but remain in the pending map so they
   * reappear when the user navigates back to that query.
   */
  private J2clSearchResultModel applyOptimisticStubs(J2clSearchResultModel projected) {
    if (pendingStubs.isEmpty()) {
      return projected;
    }
    Iterator<Map.Entry<String, PendingStub>> iter = pendingStubs.entrySet().iterator();
    while (iter.hasNext()) {
      Map.Entry<String, PendingStub> entry = iter.next();
      PendingStub pending = entry.getValue();
      if (projected.containsWave(pending.digest.getWaveId())) {
        if (pending.timeoutHandle != null) {
          optimisticScheduler.cancel(pending.timeoutHandle);
        }
        iter.remove();
      }
    }
    if (pendingStubs.isEmpty()) {
      return projected;
    }
    // Walk the LinkedHashMap in insertion order and prepend each stub. Each
    // prepend pushes the previously-prepended item down by one row, so the
    // last (most-recent) submission ends up at the top of the rail.
    J2clSearchResultModel result = projected;
    int prependedCount = 0;
    for (PendingStub pending : pendingStubs.values()) {
      if (pending.query == null
          ? currentQuery == null
          : pending.query.equals(currentQuery)) {
        result = result.withPrependedDigest(pending.digest);
        prependedCount++;
      }
    }
    if (prependedCount > 0) {
      result = withActivePendingLabel(result);
    }
    return result;
  }

  /**
   * Returns {@code model} with a "(+N pending)" suffix on the wave-count text
   * reflecting the number of optimistic stubs currently active for the current
   * query. Any existing pending suffix is stripped before the new one is
   * appended so back-to-back creates do not accumulate multiple suffixes.
   */
  private J2clSearchResultModel withActivePendingLabel(J2clSearchResultModel model) {
    int count = 0;
    for (PendingStub ps : pendingStubs.values()) {
      if (ps.query == null ? currentQuery == null : ps.query.equals(currentQuery)) {
        count++;
      }
    }
    String base = stripPendingSuffix(model.getWaveCountText());
    String updated =
        count > 0
            ? (base.isEmpty() ? "(+" + count + " pending)" : base + " (+" + count + " pending)")
            : base;
    if (updated.equals(model.getWaveCountText())) {
      return model;
    }
    return new J2clSearchResultModel(
        model.getDigestItems(), updated, model.isShowMoreVisible(), model.getEmptyMessage());
  }

  private static String stripPendingSuffix(String text) {
    if (text == null || !text.endsWith(" pending)")) {
      return text == null ? "" : text;
    }
    int i = text.lastIndexOf("(+");
    if (i < 0) {
      return text;
    }
    return text.substring(0, i).trim();
  }

  private void clearSelection(boolean userNavigation) {
    selectedWaveId = null;
    view.setSelectedWaveId(null);
    publishRouteState(userNavigation);
  }

  private static String buildStatusText(String query, J2clSearchResultModel model) {
    if (model.isEmpty()) {
      return "No results for " + query + ".";
    }
    return "Showing " + model.getDigestItems().size() + " result(s) for " + query + ".";
  }

  public J2clSearchDigestItem findDigestItem(String waveId) {
    return lastModel.findDigestItem(waveId);
  }

  /**
   * F-4 (#1039 / R-4.4): bridges the
   * {@link J2clSelectedWaveController.ReadStateListener} signal into the
   * search panel so the matching digest card decrements its unread badge
   * live without a full re-render. Idempotent — repeated calls with the
   * same count are a cheap no-op at the view layer.
   *
   * <p>The {@code stale} flag is passed through to the model in case the
   * view wants to dim the badge while the count is provisional; the
   * default view ignores it for now.
   */
  public void onReadStateChanged(String waveId, int unreadCount, boolean stale) {
    if (waveId == null || waveId.isEmpty()) {
      return;
    }
    int safeUnread = Math.max(0, unreadCount);
    boolean updated = view.updateDigestUnread(waveId, safeUnread);
    if (!updated) {
      return;
    }
    // Also patch the cached model so a re-render (e.g. on the next search
    // refresh) starts from the live count rather than the stale snapshot.
    lastModel = lastModel.withUpdatedUnreadCount(waveId, safeUnread);
  }

  private void publishRouteState(boolean userNavigation) {
    if (routeStateHandler == null) {
      return;
    }
    J2clSearchDigestItem digestItem = lastModel.findDigestItem(selectedWaveId);
    if (!userNavigation && selectedWaveId != null && digestItem == null) {
      return;
    }
    routeStateHandler.onRouteStateChanged(
        new J2clSidecarRouteState(currentQuery, selectedWaveId),
        digestItem,
        userNavigation);
  }

  private static String normalizeSelectedWaveId(String waveId) {
    return waveId == null || waveId.isEmpty() ? null : waveId;
  }
}
