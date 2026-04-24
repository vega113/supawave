package org.waveprotocol.box.j2cl.root;

import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteController;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteState;

public final class J2clRootLiveSurfaceController {
  public interface ShellSurface {
    void syncReturnTarget(String routeUrl);

    void publishLiveStatus(J2clRootLiveSurfaceModel model);
  }

  @FunctionalInterface
  public interface RouteStarter {
    void start();
  }

  private final ShellSurface shellSurface;
  private final RouteStarter routeStarter;
  private J2clRootLiveSurfaceModel model;
  private boolean active;
  private boolean publishedDuringStart;

  public J2clRootLiveSurfaceController(ShellSurface shellSurface, RouteStarter routeStarter) {
    if (shellSurface == null) {
      throw new IllegalArgumentException("shellSurface is required");
    }
    if (routeStarter == null) {
      throw new IllegalArgumentException("routeStarter is required");
    }
    this.shellSurface = shellSurface;
    this.routeStarter = routeStarter;
    this.model = J2clRootLiveSurfaceModel.starting();
  }

  public void start() {
    model = J2clRootLiveSurfaceModel.starting();
    active = true;
    publishedDuringStart = false;
    try {
      routeStarter.start();
    } catch (RuntimeException e) {
      active = false;
      throw e;
    }
    if (!publishedDuringStart) {
      publish();
    }
  }

  public void stop() {
    active = false;
  }

  public void onRouteUrlChanged(String routeUrl) {
    if (!active) {
      return;
    }
    shellSurface.syncReturnTarget(routeUrl);
    model = model.withRouteUrl(routeUrl);
    publish();
  }

  public J2clSearchPanelController.RouteStateHandler routeStateHandler(
      J2clSearchPanelController.RouteStateHandler delegate) {
    return (state, digestItem, userNavigation) -> {
      onRouteStateChanged(state);
      if (delegate != null) {
        delegate.onRouteStateChanged(state, digestItem, userNavigation);
      }
    };
  }

  public J2clSidecarRouteController.SelectedWaveController selectedWaveController(
      J2clSidecarRouteController.SelectedWaveController delegate) {
    return (waveId, digestItem) -> {
      onSelectedWaveChanged(waveId);
      if (delegate != null) {
        delegate.onWaveSelected(waveId, digestItem);
      }
    };
  }

  private void onRouteStateChanged(J2clSidecarRouteState state) {
    if (!active || state == null) {
      return;
    }
    model = model.withRouteState(state);
    publish();
  }

  private void onSelectedWaveChanged(String waveId) {
    if (!active) {
      return;
    }
    model = model.withSelectedWaveId(waveId);
    publish();
  }

  private void publish() {
    if (!active) {
      return;
    }
    shellSurface.publishLiveStatus(model);
    publishedDuringStart = true;
  }
}
