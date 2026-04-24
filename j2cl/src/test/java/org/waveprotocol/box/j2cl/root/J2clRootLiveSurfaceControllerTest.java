package org.waveprotocol.box.j2cl.root;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSearchDigestItem;
import org.waveprotocol.box.j2cl.search.J2clSearchPanelController;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteController;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteState;

@J2clTestInput(J2clRootLiveSurfaceControllerTest.class)
public class J2clRootLiveSurfaceControllerTest {
  @Test
  public void startPublishesInitialModelAndStartsExistingRouteController() {
    FakeShellSurface shell = new FakeShellSurface();
    FakeRouteStarter routeStarter = new FakeRouteStarter();
    J2clRootLiveSurfaceController controller =
        new J2clRootLiveSurfaceController(shell, routeStarter);

    controller.start();

    Assert.assertEquals(1, routeStarter.startCount);
    Assert.assertEquals(
        "Loading workspace.",
        shell.lastModel().getStatusText());
    Assert.assertNull(shell.lastModel().getSelectedWaveId());
  }

  @Test
  public void startKeepsSynchronousRouteStartupAsTerminalStatus() {
    FakeShellSurface shell = new FakeShellSurface();
    final J2clRootLiveSurfaceController[] controllerRef =
        new J2clRootLiveSurfaceController[1];
    controllerRef[0] =
        new J2clRootLiveSurfaceController(
            shell, () -> controllerRef[0].onRouteUrlChanged("?view=j2cl-root&q=in%3Ainbox"));

    controllerRef[0].start();

    Assert.assertEquals(1, shell.models.size());
    Assert.assertEquals("?view=j2cl-root&q=in%3Ainbox", shell.lastModel().getRouteUrl());
    Assert.assertEquals("in:inbox", shell.lastModel().getQuery());
    Assert.assertEquals("Showing search results for in:inbox.", shell.lastModel().getStatusText());
  }

  @Test
  public void startRollsBackActiveStateWhenRouteStarterFails() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller =
        new J2clRootLiveSurfaceController(
            shell,
            () -> {
              throw new RuntimeException("startup failed");
            });

    assertStartFailsAndSuppressesCallbacks(controller, shell, "startup failed");
  }

  @Test
  public void startRollsBackActiveStateWhenRouteStarterFailsWithError() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller =
        new J2clRootLiveSurfaceController(
            shell,
            () -> {
              throw new AssertionError("startup error");
            });

    assertStartFailsAndSuppressesCallbacks(controller, shell, "startup error");
  }

  @Test
  public void startCanRecoverAfterRouteStarterFailure() {
    FakeShellSurface shell = new FakeShellSurface();
    final int[] attempts = new int[1];
    J2clRootLiveSurfaceController controller =
        new J2clRootLiveSurfaceController(
            shell,
            () -> {
              attempts[0]++;
              if (attempts[0] == 1) {
                throw new RuntimeException("startup failed");
              }
            });

    assertStartFailsAndSuppressesCallbacks(controller, shell, "startup failed");
    shell.clear();

    controller.start();

    Assert.assertEquals(2, attempts[0]);
    Assert.assertEquals(1, shell.models.size());
    Assert.assertEquals("Loading workspace.", shell.lastModel().getStatusText());
  }

  @Test
  public void startCanRecoverAfterSynchronousPublishThenFailure() {
    FakeShellSurface shell = new FakeShellSurface();
    final int[] attempts = new int[1];
    final J2clRootLiveSurfaceController[] controllerRef =
        new J2clRootLiveSurfaceController[1];
    controllerRef[0] =
        new J2clRootLiveSurfaceController(
            shell,
            () -> {
              attempts[0]++;
              controllerRef[0].onRouteUrlChanged("?view=j2cl-root&q=partial");
              if (attempts[0] == 1) {
                throw new RuntimeException("startup failed");
              }
            });

    try {
      controllerRef[0].start();
      Assert.fail("Expected route starter failure.");
    } catch (RuntimeException expected) {
      Assert.assertEquals("startup failed", expected.getMessage());
    }
    Assert.assertEquals(1, shell.returnTargets.size());
    Assert.assertEquals(1, shell.models.size());
    controllerRef[0].onRouteUrlChanged("?view=j2cl-root&q=late");
    Assert.assertEquals(1, shell.returnTargets.size());
    Assert.assertEquals(1, shell.models.size());
    shell.clear();

    controllerRef[0].start();

    Assert.assertEquals(2, attempts[0]);
    Assert.assertEquals(1, shell.returnTargets.size());
    Assert.assertEquals(1, shell.models.size());
    Assert.assertEquals("partial", shell.lastModel().getQuery());
  }

  @Test
  public void routeUrlChangedSyncsReturnTargetAndPublishesModel() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);

    controller.onRouteUrlChanged("?view=j2cl-root&q=in%3Ainbox");

    Assert.assertEquals(
        Arrays.asList("?view=j2cl-root&q=in%3Ainbox"),
        shell.returnTargets);
    Assert.assertEquals("?view=j2cl-root&q=in%3Ainbox", shell.lastModel().getRouteUrl());
    Assert.assertEquals(
        "Showing search results for in:inbox.",
        shell.lastModel().getStatusText());
  }

  @Test
  public void routeStateHandlerPublishesSelectedWaveAndDelegates() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    FakeRouteStateHandler delegate = new FakeRouteStateHandler();
    J2clSearchPanelController.RouteStateHandler handler =
        controller.routeStateHandler(delegate);
    J2clSearchDigestItem digest = digest("example.com/w+123");

    handler.onRouteStateChanged(
        new J2clSidecarRouteState("in:inbox", "example.com/w+123"), digest, true);

    Assert.assertEquals(
        Arrays.asList("in:inbox|example.com/w+123|true|example.com/w+123"),
        delegate.events);
    Assert.assertEquals("in:inbox", shell.lastModel().getQuery());
    Assert.assertEquals("example.com/w+123", shell.lastModel().getSelectedWaveId());
    Assert.assertEquals(
        "Selected wave is active.",
        shell.lastModel().getStatusText());
  }

  @Test
  public void routeStateHandlerClearsSelectedWaveBackToQueryStatus() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    J2clSearchPanelController.RouteStateHandler handler =
        controller.routeStateHandler(null);

    handler.onRouteStateChanged(
        new J2clSidecarRouteState("in:inbox", "example.com/w+123"), null, true);
    handler.onRouteStateChanged(new J2clSidecarRouteState("in:inbox", null), null, false);

    Assert.assertEquals("in:inbox", shell.lastModel().getQuery());
    Assert.assertNull(shell.lastModel().getSelectedWaveId());
    Assert.assertEquals(
        "Showing search results for in:inbox.",
        shell.lastModel().getStatusText());
  }

  @Test
  public void routeStateHandlerIgnoresNullRouteState() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    J2clSearchPanelController.RouteStateHandler handler =
        controller.routeStateHandler(null);

    handler.onRouteStateChanged(null, null, false);

    Assert.assertEquals(0, shell.models.size());
  }

  @Test
  public void selectedWaveAdapterClearsToLastKnownQueryStatus() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    J2clSearchPanelController.RouteStateHandler handler =
        controller.routeStateHandler(null);
    J2clSidecarRouteController.SelectedWaveController selectedWaveController =
        controller.selectedWaveController(null);

    handler.onRouteStateChanged(
        new J2clSidecarRouteState("in:inbox", "example.com/w+123"), null, false);
    selectedWaveController.onWaveSelected(null, null);

    Assert.assertEquals("in:inbox", shell.lastModel().getQuery());
    Assert.assertNull(shell.lastModel().getSelectedWaveId());
    Assert.assertEquals(
        "Showing search results for in:inbox.",
        shell.lastModel().getStatusText());
  }

  @Test
  public void repeatedEquivalentStatusStillPublishesModelTransitions() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    J2clSearchPanelController.RouteStateHandler handler =
        controller.routeStateHandler(null);

    handler.onRouteStateChanged(
        new J2clSidecarRouteState("in:inbox", "example.com/w+123"), null, false);
    handler.onRouteStateChanged(
        new J2clSidecarRouteState("in:inbox", "example.com/w+456"), null, false);

    Assert.assertEquals(2, shell.models.size());
    Assert.assertEquals("example.com/w+456", shell.lastModel().getSelectedWaveId());
    Assert.assertEquals("Selected wave is active.", shell.lastModel().getStatusText());
  }

  @Test
  public void restartResetsModelToLoadingState() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller =
        new J2clRootLiveSurfaceController(shell, () -> {});
    J2clSearchPanelController.RouteStateHandler handler =
        controller.routeStateHandler(null);

    controller.start();
    handler.onRouteStateChanged(
        new J2clSidecarRouteState("in:inbox", "example.com/w+123"), null, false);
    controller.stop();
    shell.models.clear();

    controller.start();

    Assert.assertEquals("Loading workspace.", shell.lastModel().getStatusText());
    Assert.assertNull(shell.lastModel().getSelectedWaveId());
    Assert.assertEquals("", shell.lastModel().getQuery());
  }

  @Test
  public void stopSuppressesFutureLiveStatusPublication() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller =
        new J2clRootLiveSurfaceController(shell, () -> {});

    controller.stop();
    controller.onRouteUrlChanged("?view=j2cl-root&q=in%3Ainbox");

    Assert.assertEquals(0, shell.returnTargets.size());
    Assert.assertEquals(0, shell.models.size());
  }

  @Test
  public void interleavedRouteSelectionAndClearTransitionsRemainMonotonic() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    J2clSearchPanelController.RouteStateHandler handler =
        controller.routeStateHandler(null);

    controller.onRouteUrlChanged("?view=j2cl-root&q=a");
    handler.onRouteStateChanged(new J2clSidecarRouteState("a", "example.com/w+1"), null, false);
    handler.onRouteStateChanged(new J2clSidecarRouteState("a", null), null, false);
    controller.onRouteUrlChanged("?view=j2cl-root&q=b");

    Assert.assertEquals("?view=j2cl-root&q=b", shell.lastModel().getRouteUrl());
    Assert.assertEquals("b", shell.lastModel().getQuery());
    Assert.assertNull(shell.lastModel().getSelectedWaveId());
    Assert.assertEquals("Showing search results for b.", shell.lastModel().getStatusText());
  }

  @Test
  public void selectedWaveAdapterPublishesContinuityAndPreservesDelegateCall() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    FakeSelectedWaveController delegate = new FakeSelectedWaveController();
    J2clSidecarRouteController.SelectedWaveController selectedWaveController =
        controller.selectedWaveController(delegate);

    selectedWaveController.onWaveSelected("example.com/w+abc", digest("example.com/w+abc"));

    Assert.assertEquals(
        Arrays.asList("example.com/w+abc|example.com/w+abc"),
        delegate.events);
    Assert.assertEquals("example.com/w+abc", shell.lastModel().getSelectedWaveId());
    Assert.assertEquals(
        "Selected wave is active.",
        shell.lastModel().getStatusText());
  }

  @Test
  public void selectedWaveAdapterAllowsNullDelegateForObservationOnly() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    J2clSidecarRouteController.SelectedWaveController selectedWaveController =
        controller.selectedWaveController(null);

    selectedWaveController.onWaveSelected("example.com/w+abc", null);

    Assert.assertEquals("example.com/w+abc", shell.lastModel().getSelectedWaveId());
    Assert.assertEquals("Selected wave is active.", shell.lastModel().getStatusText());
  }

  @Test
  public void routeStateHandlerAllowsNullDelegateForObservationOnly() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    J2clSearchPanelController.RouteStateHandler handler =
        controller.routeStateHandler(null);

    handler.onRouteStateChanged(new J2clSidecarRouteState("in:inbox", null), null, false);

    Assert.assertEquals("in:inbox", shell.lastModel().getQuery());
    Assert.assertEquals("Showing search results for in:inbox.", shell.lastModel().getStatusText());
  }

  @Test
  public void callbacksBeforeStartDoNotPublishOrSyncReturnTarget() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller =
        new J2clRootLiveSurfaceController(shell, () -> {});
    J2clSearchPanelController.RouteStateHandler routeStateHandler =
        controller.routeStateHandler(null);
    J2clSidecarRouteController.SelectedWaveController selectedWaveController =
        controller.selectedWaveController(null);

    controller.onRouteUrlChanged("?view=j2cl-root&q=in%3Ainbox");
    routeStateHandler.onRouteStateChanged(
        new J2clSidecarRouteState("in:inbox", "example.com/w+123"), null, false);
    selectedWaveController.onWaveSelected("example.com/w+456", null);

    Assert.assertEquals(0, shell.returnTargets.size());
    Assert.assertEquals(0, shell.models.size());
  }

  @Test
  public void routeAndSelectionCallbacksAfterStopDoNotLeakIntoRestart() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    J2clSearchPanelController.RouteStateHandler routeStateHandler =
        controller.routeStateHandler(null);
    J2clSidecarRouteController.SelectedWaveController selectedWaveController =
        controller.selectedWaveController(null);

    routeStateHandler.onRouteStateChanged(
        new J2clSidecarRouteState("in:inbox", "example.com/w+123"), null, false);
    controller.stop();
    routeStateHandler.onRouteStateChanged(
        new J2clSidecarRouteState("after-stop", "example.com/w+456"), null, false);
    selectedWaveController.onWaveSelected("example.com/w+789", null);
    Assert.assertEquals(1, shell.models.size());
    shell.clear();

    controller.start();

    Assert.assertEquals("Loading workspace.", shell.lastModel().getStatusText());
    Assert.assertEquals("", shell.lastModel().getQuery());
    Assert.assertNull(shell.lastModel().getSelectedWaveId());
  }

  @Test
  public void callbacksAfterStopDoNotForwardToDelegates() {
    FakeShellSurface shell = new FakeShellSurface();
    J2clRootLiveSurfaceController controller = startedController(shell);
    FakeRouteStateHandler routeDelegate = new FakeRouteStateHandler();
    FakeSelectedWaveController selectedDelegate = new FakeSelectedWaveController();
    J2clSearchPanelController.RouteStateHandler routeStateHandler =
        controller.routeStateHandler(routeDelegate);
    J2clSidecarRouteController.SelectedWaveController selectedWaveController =
        controller.selectedWaveController(selectedDelegate);

    controller.stop();
    routeStateHandler.onRouteStateChanged(
        new J2clSidecarRouteState("in:inbox", "example.com/w+123"),
        digest("example.com/w+123"),
        true);
    selectedWaveController.onWaveSelected("example.com/w+456", digest("example.com/w+456"));

    Assert.assertEquals(0, routeDelegate.events.size());
    Assert.assertEquals(0, selectedDelegate.events.size());
    Assert.assertEquals(0, shell.models.size());
  }

  @Test
  public void constructorRejectsNullShellSurface() {
    try {
      new J2clRootLiveSurfaceController(null, () -> {});
      Assert.fail("Expected null shell surface to be rejected.");
    } catch (IllegalArgumentException expected) {
      Assert.assertEquals("shellSurface is required", expected.getMessage());
    }
  }

  @Test
  public void constructorRejectsNullRouteStarter() {
    try {
      new J2clRootLiveSurfaceController(new FakeShellSurface(), null);
      Assert.fail("Expected null route starter to be rejected.");
    } catch (IllegalArgumentException expected) {
      Assert.assertEquals("routeStarter is required", expected.getMessage());
    }
  }

  private static J2clSearchDigestItem digest(String waveId) {
    return new J2clSearchDigestItem(
        waveId,
        "Title",
        "Snippet",
        "Creator",
        1,
        2,
        7L,
        false);
  }

  private static void assertStartFailsAndSuppressesCallbacks(
      J2clRootLiveSurfaceController controller, FakeShellSurface shell, String expectedMessage) {
    try {
      controller.start();
      Assert.fail("Expected route starter failure.");
    } catch (RuntimeException | Error expected) {
      Assert.assertEquals(expectedMessage, expected.getMessage());
    }
    controller.onRouteUrlChanged("?view=j2cl-root&q=in%3Ainbox");
    controller
        .routeStateHandler(null)
        .onRouteStateChanged(
            new J2clSidecarRouteState("in:inbox", "example.com/w+123"), null, false);
    controller
        .selectedWaveController(null)
        .onWaveSelected("example.com/w+456", null);

    Assert.assertEquals(0, shell.returnTargets.size());
    Assert.assertEquals(0, shell.models.size());
  }

  private static J2clRootLiveSurfaceController startedController(FakeShellSurface shell) {
    J2clRootLiveSurfaceController controller =
        new J2clRootLiveSurfaceController(shell, () -> {});
    controller.start();
    shell.clear();
    return controller;
  }

  private static final class FakeShellSurface
      implements J2clRootLiveSurfaceController.ShellSurface {
    private final List<String> returnTargets = new ArrayList<String>();
    private final List<J2clRootLiveSurfaceModel> models =
        new ArrayList<J2clRootLiveSurfaceModel>();

    @Override
    public void syncReturnTarget(String routeUrl) {
      returnTargets.add(routeUrl);
    }

    @Override
    public void publishLiveStatus(J2clRootLiveSurfaceModel model) {
      models.add(model);
    }

    private J2clRootLiveSurfaceModel lastModel() {
      return models.get(models.size() - 1);
    }

    private void clear() {
      returnTargets.clear();
      models.clear();
    }
  }

  private static final class FakeRouteStarter
      implements J2clRootLiveSurfaceController.RouteStarter {
    private int startCount;

    @Override
    public void start() {
      startCount++;
    }
  }

  private static final class FakeRouteStateHandler
      implements J2clSearchPanelController.RouteStateHandler {
    private final List<String> events = new ArrayList<String>();

    @Override
    public void onRouteStateChanged(
        J2clSidecarRouteState state, J2clSearchDigestItem digestItem, boolean userNavigation) {
      events.add(
          state.getQuery()
              + "|"
              + state.getSelectedWaveId()
              + "|"
              + userNavigation
              + "|"
              + (digestItem == null ? "null" : digestItem.getWaveId()));
    }
  }

  private static final class FakeSelectedWaveController
      implements J2clSidecarRouteController.SelectedWaveController {
    private final List<String> events = new ArrayList<String>();

    @Override
    public void onWaveSelected(String waveId, J2clSearchDigestItem digestItem) {
      events.add(waveId + "|" + (digestItem == null ? "null" : digestItem.getWaveId()));
    }
  }
}
