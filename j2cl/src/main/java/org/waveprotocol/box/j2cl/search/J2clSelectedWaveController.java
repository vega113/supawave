package org.waveprotocol.box.j2cl.search;

import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;

public final class J2clSelectedWaveController {
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

  public interface Subscription {
    void close();
  }

  private final Gateway gateway;
  private final View view;
  private Subscription currentSubscription;
  private SidecarSessionBootstrap currentBootstrap;
  private SidecarSelectedWaveUpdate lastUpdate;
  private String selectedWaveId;
  private J2clSearchDigestItem selectedDigestItem;
  private J2clSelectedWaveModel currentModel;
  private int reconnectCount;
  private int requestGeneration;

  public J2clSelectedWaveController(Gateway gateway, View view) {
    this.gateway = gateway;
    this.view = view;
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
    currentModel = J2clSelectedWaveModel.loading(waveId, digestItem, reconnectCount);
    view.render(currentModel);

    gateway.fetchRootSessionBootstrap(
        bootstrap -> {
          if (!isCurrentGeneration(generation)) {
            return;
          }
          currentBootstrap = bootstrap;
          openSelectedWave(generation, 0);
        },
        error -> {
          if (!isCurrentGeneration(generation)) {
            return;
          }
          currentModel =
              J2clSelectedWaveModel.error(
                  selectedWaveId, selectedDigestItem, "Unable to open selected wave.", error);
          view.render(currentModel);
        });
  }

  private void openSelectedWave(int generation, int reconnectCount) {
    if (selectedWaveId == null || currentBootstrap == null) {
      return;
    }
    this.reconnectCount = reconnectCount;
    currentModel = J2clSelectedWaveModel.loading(selectedWaveId, selectedDigestItem, reconnectCount);
    view.render(currentModel);
    currentSubscription =
        gateway.openSelectedWave(
            currentBootstrap,
            selectedWaveId,
            update -> {
              if (!isCurrentGeneration(generation) || isChannelEstablishmentUpdate(update)) {
                return;
              }
              lastUpdate = update;
              currentModel =
                  J2clSelectedWaveProjector.project(
                      selectedWaveId, selectedDigestItem, update, currentModel, reconnectCount);
              view.render(currentModel);
            },
            error -> {
              if (!isCurrentGeneration(generation)) {
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
              openSelectedWave(generation, reconnectCount + 1);
            });
  }

  private boolean isCurrentGeneration(int generation) {
    return generation == requestGeneration;
  }

  private static boolean isChannelEstablishmentUpdate(SidecarSelectedWaveUpdate update) {
    String waveletName = update.getWaveletName();
    return waveletName != null && waveletName.contains("/~/dummy+root");
  }

  private void closeSubscription() {
    if (currentSubscription != null) {
      currentSubscription.close();
      currentSubscription = null;
    }
  }
}
