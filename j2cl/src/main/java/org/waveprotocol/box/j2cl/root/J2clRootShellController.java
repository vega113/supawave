package org.waveprotocol.box.j2cl.root;

import elemental2.dom.HTMLElement;
import org.waveprotocol.box.j2cl.search.J2clPlainTextDeltaFactory;
import org.waveprotocol.box.j2cl.search.J2clSearchGateway;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelView;
import org.waveprotocol.box.j2cl.search.J2clSidecarComposeController;
import org.waveprotocol.box.j2cl.search.J2clSidecarComposeView;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteController;
import org.waveprotocol.box.j2cl.search.J2clSelectedWaveController;
import org.waveprotocol.box.j2cl.search.J2clSelectedWaveView;

public final class J2clRootShellController {
  private final HTMLElement host;

  public J2clRootShellController(HTMLElement host) {
    this.host = host;
  }

  public void start() {
    J2clRootShellView shellView = new J2clRootShellView(host);
    J2clSearchGateway gateway = new J2clSearchGateway();
    final J2clSidecarRouteController[] routeControllerRef = new J2clSidecarRouteController[1];
    final J2clSelectedWaveController[] selectedWaveControllerRef =
        new J2clSelectedWaveController[1];
    J2clSearchPanelView searchView =
        new J2clSearchPanelView(
            shellView.getWorkflowHost(), J2clSearchPanelView.ShellPresentation.ROOT_SHELL);
    J2clSelectedWaveView selectedWaveView =
        new J2clSelectedWaveView(searchView.getSelectedWaveHost());
    J2clSidecarComposeController composeController =
        new J2clSidecarComposeController(
            gateway,
            new J2clSidecarComposeView(
                searchView.getComposeHost(),
                selectedWaveView.getComposeHost(),
                J2clSearchPanelView.ShellPresentation.ROOT_SHELL),
            new J2clPlainTextDeltaFactory(buildRootShellSessionSeed()),
            waveId -> routeControllerRef[0].selectWave(waveId),
            waveId -> {
              if (selectedWaveControllerRef[0] != null) {
                selectedWaveControllerRef[0].refreshSelectedWave();
              }
            },
            J2clSearchPanelView.ShellPresentation.ROOT_SHELL);
    J2clSelectedWaveController selectedWaveController =
        new J2clSelectedWaveController(
            gateway, selectedWaveView, composeController::onWriteSessionChanged);
    selectedWaveControllerRef[0] = selectedWaveController;
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            searchView,
            (state, digestItem, userNavigation) ->
                routeControllerRef[0].onRouteStateChanged(state, digestItem, userNavigation),
            resolveViewportWidth());
    J2clSidecarRouteController routeController =
        new J2clSidecarRouteController(
            new J2clSidecarRouteController.BrowserHistoryAdapter(),
            controller,
            selectedWaveController,
            "view=j2cl-root");
    routeControllerRef[0] = routeController;
    searchView.setSessionSummary("Mounted inside the J2CL root shell.");
    composeController.start();
    routeController.start();
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
