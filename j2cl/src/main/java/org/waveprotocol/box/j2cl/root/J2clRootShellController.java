package org.waveprotocol.box.j2cl.root;

import elemental2.dom.HTMLElement;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceController;
import org.waveprotocol.box.j2cl.compose.J2clComposeSurfaceView;
import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSearchGateway;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelView;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteController;
import org.waveprotocol.box.j2cl.search.J2clSelectedWaveController;
import org.waveprotocol.box.j2cl.search.J2clSelectedWaveView;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceController;
import org.waveprotocol.box.j2cl.toolbar.J2clToolbarSurfaceView;

public final class J2clRootShellController {
  private final HTMLElement host;
  private boolean started;

  public J2clRootShellController(HTMLElement host) {
    this.host = host;
  }

  public void start() {
    if (started) {
      return;
    }
    started = true;
    J2clRootShellView shellView = new J2clRootShellView(host);
    J2clSearchGateway gateway = new J2clSearchGateway();
    final J2clSidecarRouteController[] routeControllerRef = new J2clSidecarRouteController[1];
    final J2clSelectedWaveController[] selectedWaveControllerRef =
        new J2clSelectedWaveController[1];
    final J2clToolbarSurfaceController[] toolbarControllerRef =
        new J2clToolbarSurfaceController[1];
    // The route controller is wired below; the starter runs only after that assignment.
    J2clRootLiveSurfaceController liveSurfaceController =
        new J2clRootLiveSurfaceController(shellView, () -> routeControllerRef[0].start());
    J2clSearchPanelView searchView =
        new J2clSearchPanelView(
            shellView.getWorkflowHost(), J2clSearchPanelView.ShellPresentation.ROOT_SHELL);
    J2clSelectedWaveView selectedWaveView =
        new J2clSelectedWaveView(searchView.getSelectedWaveHost());
    HTMLElement selectedWaveComposeHost = selectedWaveView.getComposeHost();
    HTMLElement selectedToolbarHost =
        createChildHost(selectedWaveComposeHost, "j2cl-root-toolbar-host");
    HTMLElement selectedReplyHost =
        createChildHost(selectedWaveComposeHost, "j2cl-root-reply-host");
    J2clComposeSurfaceController composeController =
        new J2clComposeSurfaceController(
            gateway,
            new J2clComposeSurfaceView(searchView.getComposeHost(), selectedReplyHost),
            new J2clPlainTextDeltaFactory(buildRootShellSessionSeed()),
            waveId -> routeControllerRef[0].selectWave(waveId),
            waveId -> {
              if (selectedWaveControllerRef[0] != null) {
                selectedWaveControllerRef[0].refreshSelectedWave();
              }
            });
    J2clToolbarSurfaceController toolbarController =
        new J2clToolbarSurfaceController(
            new J2clToolbarSurfaceView(selectedToolbarHost),
            action ->
                toolbarControllerRef[0].onActionUnavailable(
                    action, "This toolbar action is not wired in the J2CL root shell yet."));
    toolbarControllerRef[0] = toolbarController;
    J2clSelectedWaveController selectedWaveController =
        new J2clSelectedWaveController(
            gateway,
            selectedWaveView,
            writeSession -> {
              composeController.onWriteSessionChanged(writeSession);
              toolbarController.onWriteSessionChanged(writeSession);
              toolbarController.onEditStateChanged(
                  new J2clToolbarSurfaceController.EditState(writeSession != null));
            });
    selectedWaveControllerRef[0] = selectedWaveController;
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            searchView,
            liveSurfaceController.routeStateHandler(
                (state, digestItem, userNavigation) ->
                    routeControllerRef[0].onRouteStateChanged(state, digestItem, userNavigation)),
            resolveViewportWidth());
    J2clSidecarRouteController routeController =
        new J2clSidecarRouteController(
            new J2clSidecarRouteController.BrowserHistoryAdapter(),
            controller,
            liveSurfaceController.selectedWaveController(
                (waveId, digestItem) -> {
                  toolbarController.onSelectedWaveStateChanged(
                      // TODO(#971): publish real archive/pin/mention state from the root-live or
                      // selected-wave model before enabling folder-specific toolbar controls.
                      new J2clToolbarSurfaceController.SelectedWaveState(
                          waveId != null && !waveId.isEmpty(), false, false, true, false, false));
                  selectedWaveController.onWaveSelected(waveId, digestItem);
                }),
            "view=j2cl-root",
            liveSurfaceController::onRouteUrlChanged);
    routeControllerRef[0] = routeController;
    searchView.setSessionSummary("Mounted inside the J2CL root shell.");
    composeController.start();
    toolbarController.start();
    toolbarController.onEditStateChanged(new J2clToolbarSurfaceController.EditState(false));
    liveSurfaceController.start();
  }

  private static HTMLElement createChildHost(HTMLElement parent, String className) {
    HTMLElement child = (HTMLElement) elemental2.dom.DomGlobal.document.createElement("div");
    child.className = className;
    parent.appendChild(child);
    return child;
  }

  private static double resolveViewportWidth() {
    return Double.parseDouble(String.valueOf(elemental2.dom.DomGlobal.window.innerWidth));
  }

  private static String buildRootShellSessionSeed() {
    long timestampSeed = System.currentTimeMillis();
    long randomSeed = (long) Math.floor(Math.random() * 0x7fffffff);
    return "j2cl-root" + Long.toHexString(timestampSeed) + Long.toHexString(randomSeed);
  }
}
