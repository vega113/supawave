package org.waveprotocol.box.j2cl.search;

import elemental2.dom.DomGlobal;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;

public final class J2clSelectedWaveController {
  private static final int INITIAL_RECONNECT_DELAY_MS = 250;
  // Keep retries bounded, but leave enough budget for a local WIAB restart on the same port.
  private static final int MAX_RECONNECT_DELAY_MS = 2000;
  private static final int MAX_RECONNECT_ATTEMPTS = 8;

  public interface Gateway {
    void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError);

    Subscription openSelectedWave(
        SidecarSessionBootstrap bootstrap,
        String waveId,
        J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> onUpdate,
        J2clSearchPanelController.ErrorCallback onError,
        Runnable onDisconnect);
  }

  public interface View {
    void render(J2clSelectedWaveModel model);
  }

  public interface RetryScheduler {
    void scheduleRetry(int delayMs, Runnable action);
  }

  public interface Subscription {
    void close();
  }

  private final Gateway gateway;
  private final View view;
  private final RetryScheduler retryScheduler;
  private Subscription currentSubscription;
  private SidecarSessionBootstrap currentBootstrap;
  private SidecarSelectedWaveUpdate lastUpdate;
  private String selectedWaveId;
  private J2clSearchDigestItem selectedDigestItem;
  private J2clSelectedWaveModel currentModel;
  private int reconnectCount;
  private int requestGeneration;

  public J2clSelectedWaveController(Gateway gateway, View view) {
    this(
        gateway,
        view,
        (delayMs, action) -> DomGlobal.setTimeout(ignored -> action.run(), delayMs));
  }

  public J2clSelectedWaveController(Gateway gateway, View view, RetryScheduler retryScheduler) {
    this.gateway = gateway;
    this.view = view;
    this.retryScheduler = retryScheduler;
    this.currentModel = J2clSelectedWaveModel.empty();
    this.view.render(currentModel);
  }

  public void onWaveSelected(String waveId) {
    onWaveSelected(waveId, null);
  }

  public void onWaveSelected(String waveId, J2clSearchDigestItem digestItem) {
    if (waveId != null
        && waveId.equals(selectedWaveId)
        && currentSubscription != null
        && requestGeneration > 0) {
      selectedDigestItem = digestItem;
      if (lastUpdate != null) {
        currentModel =
            J2clSelectedWaveProjector.project(
                selectedWaveId, selectedDigestItem, lastUpdate, currentModel, reconnectCount);
        view.render(currentModel);
      }
      return;
    }

    int generation = ++requestGeneration;
    closeSubscription();

    if (waveId == null || waveId.isEmpty()) {
      selectedWaveId = null;
      selectedDigestItem = null;
      currentBootstrap = null;
      lastUpdate = null;
      reconnectCount = 0;
      currentModel = J2clSelectedWaveModel.empty();
      view.render(currentModel);
      return;
    }

    selectedWaveId = waveId;
    selectedDigestItem = digestItem;
    lastUpdate = null;
    reconnectCount = 0;
    fetchBootstrapAndOpenSelectedWave(generation, 0, false);
  }

  private void fetchBootstrapAndOpenSelectedWave(
      int generation, int reconnectCount, boolean retryOnFailure) {
    if (selectedWaveId == null) {
      return;
    }
    this.reconnectCount = reconnectCount;
    currentModel = J2clSelectedWaveModel.loading(selectedWaveId, selectedDigestItem, reconnectCount);
    view.render(currentModel);
    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          if (!isCurrentGeneration(generation)) {
            return;
          }
          currentBootstrap = bootstrap;
          openSelectedWave(generation, reconnectCount, retryOnFailure);
        },
        error -> {
          if (!isCurrentGeneration(generation)) {
            return;
          }
          clearActiveSubscription();
          currentBootstrap = null;
          if (retryOnFailure) {
            scheduleReconnectOrFail(generation, reconnectCount);
            return;
          }
          currentModel =
              J2clSelectedWaveModel.error(
                  selectedWaveId, selectedDigestItem, "Unable to open selected wave.", error);
          view.render(currentModel);
        });
  }

  private void openSelectedWave(int generation, int reconnectCount, boolean retryOnFailure) {
    if (selectedWaveId == null || currentBootstrap == null) {
      return;
    }
    final boolean[] terminalStateHandled = new boolean[] {false};
    // Mutable so successful updates reset the budget, keeping MAX_RECONNECT_ATTEMPTS per outage.
    final int[] activeReconnectCount = {reconnectCount};
    currentSubscription =
        gateway.openSelectedWave(
            currentBootstrap,
            selectedWaveId,
            update -> {
              if (!isCurrentGeneration(generation) || isChannelEstablishmentUpdate(update)) {
                return;
              }
              int projectedReconnectCount = activeReconnectCount[0];
              lastUpdate = update;
              currentModel =
                  J2clSelectedWaveProjector.project(
                      selectedWaveId,
                      selectedDigestItem,
                      update,
                      currentModel,
                      projectedReconnectCount);
              view.render(currentModel);
              activeReconnectCount[0] = 0;
              this.reconnectCount = projectedReconnectCount;
            },
            error -> {
              if (!isCurrentGeneration(generation)) {
                return;
              }
              if (terminalStateHandled[0]) {
                return;
              }
              terminalStateHandled[0] = true;
              closeSubscription();
              if (retryOnFailure || lastUpdate != null) {
                scheduleReconnectOrFail(generation, activeReconnectCount[0]);
                return;
              }
              currentModel =
                  J2clSelectedWaveModel.error(
                      selectedWaveId, selectedDigestItem, "Selected wave stream failed.", error);
              view.render(currentModel);
            },
            () -> {
              if (!isCurrentGeneration(generation) || selectedWaveId == null) {
                return;
              }
              if (terminalStateHandled[0]) {
                return;
              }
              terminalStateHandled[0] = true;
              clearActiveSubscription();
              scheduleReconnectOrFail(generation, activeReconnectCount[0]);
            });
  }

  private void scheduleReconnectOrFail(int generation, int reconnectCount) {
    if (reconnectCount >= MAX_RECONNECT_ATTEMPTS) {
      currentModel =
          J2clSelectedWaveModel.error(
              selectedWaveId,
              selectedDigestItem,
              "Selected wave disconnected.",
              "The selected-wave sidecar stopped retrying after "
                  + MAX_RECONNECT_ATTEMPTS
                  + " reconnect attempts.");
      view.render(currentModel);
      return;
    }
    int nextReconnectCount = reconnectCount + 1;
    retryScheduler.scheduleRetry(
        buildReconnectDelayMs(reconnectCount),
        () -> {
          if (!isCurrentGeneration(generation) || selectedWaveId == null) {
            return;
          }
          fetchBootstrapAndOpenSelectedWave(generation, nextReconnectCount, true);
        });
  }

  private boolean isCurrentGeneration(int generation) {
    return generation == requestGeneration;
  }

  static boolean isChannelEstablishmentUpdate(SidecarSelectedWaveUpdate update) {
    String waveletName = update.getWaveletName();
    // The socket open handshake reuses ProtocolWaveletUpdate to deliver the initial channel id
    // before any real wavelet data is streamed. Those synthetic frames target ~/dummy+root and
    // must not overwrite the selected-wave panel or they would flash a fake wavelet into view.
    return waveletName != null && waveletName.endsWith("/~/dummy+root");
  }

  private void closeSubscription() {
    if (currentSubscription != null) {
      currentSubscription.close();
      currentSubscription = null;
    }
  }

  private void clearActiveSubscription() {
    currentSubscription = null;
  }

  private static int buildReconnectDelayMs(int reconnectCount) {
    int delayMs = INITIAL_RECONNECT_DELAY_MS << reconnectCount;
    return Math.min(delayMs, MAX_RECONNECT_DELAY_MS);
  }
}
