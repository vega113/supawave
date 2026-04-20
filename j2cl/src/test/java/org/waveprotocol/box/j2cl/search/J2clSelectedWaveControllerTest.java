package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveDocument;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragment;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragmentRange;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveFragments;
import org.waveprotocol.box.j2cl.transport.SidecarSelectedWaveUpdate;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;

@J2clTestInput(J2clSelectedWaveControllerTest.class)
public class J2clSelectedWaveControllerTest {
  @Test
  public void selectingWaveRetriesLongEnoughToRecoverAfterServerRestart() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(true);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    harness.fireDisconnect(0);
    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals(Arrays.asList(250), harness.scheduledDelays);

    harness.runScheduledRetry(0);
    Assert.assertEquals(2, harness.bootstrapAttempts.size());
    Assert.assertEquals(1, harness.openCount);
    harness.rejectBootstrap(1, "Network failure for /");
    Assert.assertEquals(Arrays.asList(250, 500), harness.scheduledDelays);

    harness.runScheduledRetry(1);
    Assert.assertEquals(3, harness.bootstrapAttempts.size());
    Assert.assertEquals(1, harness.openCount);
    harness.rejectBootstrap(2, "Network failure for /");
    Assert.assertEquals(Arrays.asList(250, 500, 1000), harness.scheduledDelays);

    harness.runScheduledRetry(2);
    Assert.assertEquals(4, harness.bootstrapAttempts.size());
    Assert.assertEquals(1, harness.openCount);
    harness.rejectBootstrap(3, "Network failure for /");
    Assert.assertEquals(Arrays.asList(250, 500, 1000, 2000), harness.scheduledDelays);

    harness.runScheduledRetry(3);
    Assert.assertEquals(5, harness.bootstrapAttempts.size());
    Assert.assertEquals(1, harness.openCount);
    harness.rejectBootstrap(4, "Network failure for /");
    Assert.assertEquals(Arrays.asList(250, 500, 1000, 2000, 2000), harness.scheduledDelays);

    harness.runScheduledRetry(4);
    Assert.assertEquals(6, harness.bootstrapAttempts.size());
    harness.resolveBootstrap(5);
    Assert.assertEquals(2, harness.openCount);
    harness.deliverUpdate(1, "Recovered after restart");

    Assert.assertFalse((Boolean) harness.modelValue("isError"));
    Assert.assertEquals("Live updates reconnected.", harness.modelValue("getStatusText"));
    Assert.assertEquals(Arrays.asList("Recovered after restart"), harness.modelValue("getContentEntries"));
  }

  @Test
  public void selectingWaveStillStopsAfterBoundedReconnectBudget() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(true);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    harness.fireDisconnect(0);

    for (int attempt = 1; attempt <= 8; attempt++) {
      Assert.assertFalse((Boolean) harness.modelValue("isError"));
      harness.runScheduledRetry(attempt - 1);
      Assert.assertEquals(attempt + 1, harness.bootstrapAttempts.size());
      Assert.assertEquals(1, harness.openCount);
      harness.rejectBootstrap(attempt, "Network failure for /");
    }

    Assert.assertTrue((Boolean) harness.modelValue("isError"));
    Assert.assertEquals(
        Arrays.asList(250, 500, 1000, 2000, 2000, 2000, 2000, 2000), harness.scheduledDelays);
    Assert.assertTrue(
        String.valueOf(harness.modelValue("getStatusText")).contains("Selected wave disconnected"));
    Assert.assertTrue(
        String.valueOf(harness.modelValue("getDetailText")).contains("8 reconnect attempts"));
  }

  @Test
  public void bootstrapErrorRendersSelectedWaveError() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.rejectBootstrap(0, "bootstrap boom");

    Assert.assertTrue((Boolean) harness.modelValue("isError"));
    Assert.assertEquals("Unable to open selected wave.", harness.modelValue("getStatusText"));
    Assert.assertEquals("bootstrap boom", harness.modelValue("getDetailText"));
  }

  @Test
  public void transportOpenErrorRendersSelectedWaveError() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.failOpen(0, "socket boom");

    Assert.assertTrue((Boolean) harness.modelValue("isError"));
    Assert.assertEquals("Selected wave stream failed.", harness.modelValue("getStatusText"));
    Assert.assertEquals("socket boom", harness.modelValue("getDetailText"));
    Assert.assertEquals(1, harness.closedCount);
  }

  @Test
  public void transportStreamErrorRetriesSelectedWave() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(true);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");
    harness.failOpen(0, "socket boom");

    Assert.assertFalse((Boolean) harness.modelValue("isError"));
    Assert.assertEquals(Arrays.asList(250), harness.scheduledDelays);
    Assert.assertEquals(1, harness.closedCount);

    harness.runScheduledRetry(0);
    harness.resolveBootstrap(1);
    harness.deliverUpdate(1, "Recovered after socket error");

    Assert.assertEquals("Live updates reconnected.", harness.modelValue("getStatusText"));
    Assert.assertEquals(
        Arrays.asList("Recovered after socket error"), harness.modelValue("getContentEntries"));
  }

  @Test
  public void staleBootstrapSuccessIsIgnoredAfterRapidReselection() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+a", null);
    harness.selectWave(controller, "example.com/w+b", null);

    harness.resolveBootstrap(0);
    Assert.assertEquals(0, harness.openCount);

    harness.resolveBootstrap(1);
    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals("example.com/w+b", harness.openAttempts.get(0).waveId);
  }

  @Test
  public void staleOpenUpdateIsIgnoredAfterSwitchingToDifferentWave() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+a", null);
    harness.resolveBootstrap(0);
    Assert.assertEquals(1, harness.openCount);

    harness.selectWave(controller, "example.com/w+b", null);
    harness.resolveBootstrap(1);
    Assert.assertEquals(2, harness.openCount);

    harness.deliverUpdate(0, "stale A");
    Assert.assertEquals("Opening selected wave.", harness.modelValue("getStatusText"));

    harness.deliverUpdate(1, "fresh B");
    Assert.assertEquals(Arrays.asList("fresh B"), harness.modelValue("getContentEntries"));
    Assert.assertEquals("example.com/w+b", harness.modelValue("getSelectedWaveId"));
  }

  @Test
  public void sameWaveReselectRefreshesDigestMetadataWithoutReopeningSocket() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", digest("Wave A", "Old snippet", 3));
    harness.resolveBootstrap(0);
    harness.deliverUpdate(0, "Hello from the sidecar");

    harness.selectWave(controller, "example.com/w+1", digest("Wave A updated", "New snippet", 0));

    Assert.assertEquals(1, harness.openCount);
    Assert.assertEquals("Wave A updated", harness.modelValue("getTitleText"));
    Assert.assertEquals("Selected digest is read.", harness.modelValue("getUnreadText"));
    Assert.assertEquals("New snippet", harness.modelValue("getSnippetText"));
  }

  @Test
  public void channelEstablishmentUpdateIsIgnoredUntilRealWaveletArrives() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", null);
    harness.resolveBootstrap(0);

    harness.deliverRawUpdate(
        0,
        new SidecarSelectedWaveUpdate(
            1,
            "example.com!w+1/~/dummy+root",
            true,
            "chan-1",
            Arrays.asList("user@example.com"),
            new ArrayList<SidecarSelectedWaveDocument>(),
            null));

    Assert.assertTrue((Boolean) harness.modelValue("isLoading"));

    harness.deliverUpdate(0, "real content");
    Assert.assertEquals(Arrays.asList("real content"), harness.modelValue("getContentEntries"));
  }

  @Test
  public void snapshotOnlyUpdateRendersDocumentTextWhenFragmentsAreMissing() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController(false);

    harness.selectWave(controller, "example.com/w+1", digest("Wave A", "Digest snippet", 3));
    harness.resolveBootstrap(0);
    harness.deliverSnapshotOnlyUpdate(0, "Welcome to SupaWave");

    Assert.assertEquals(Arrays.asList("Welcome to SupaWave"), harness.modelValue("getContentEntries"));
    Assert.assertEquals("Digest snippet", harness.modelValue("getSnippetText"));
    Assert.assertFalse((Boolean) harness.modelValue("isLoading"));
  }

  private static J2clSearchDigestItem digest(String title, String snippet, int unreadCount) {
    return new J2clSearchDigestItem(
        "example.com/w+1", title, snippet, "user@example.com", unreadCount, 2, 1234L, false);
  }

  private static final class Harness {
    private int openCount;
    private int closedCount;
    private final List<Integer> scheduledDelays = new ArrayList<Integer>();
    private final List<Runnable> scheduledRetries = new ArrayList<Runnable>();
    private final List<BootstrapAttempt> bootstrapAttempts = new ArrayList<BootstrapAttempt>();
    private final List<OpenAttempt> openAttempts = new ArrayList<OpenAttempt>();
    private Object lastModel;
    private Method onWaveSelectedMethod;
    private Method onWaveSelectedWithDigestMethod;

    private Object createController(boolean withScheduler) throws Exception {
      Class<?> controllerClass =
          Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController");
      Class<?> gatewayClass =
          Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$Gateway");
      Class<?> viewClass =
          Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$View");
      Class<?> subscriptionClass =
          Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$Subscription");

      Object gateway =
          Proxy.newProxyInstance(
              gatewayClass.getClassLoader(),
              new Class<?>[] {gatewayClass},
              (proxy, method, args) -> {
                if ("fetchRootSessionBootstrap".equals(method.getName())) {
                  @SuppressWarnings("unchecked")
                  J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> success =
                      (J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap>) args[0];
                  J2clSearchPanelController.ErrorCallback error =
                      (J2clSearchPanelController.ErrorCallback) args[1];
                  bootstrapAttempts.add(new BootstrapAttempt(success, error));
                  return null;
                }
                if ("openSelectedWave".equals(method.getName())) {
                  openCount++;
                  @SuppressWarnings("unchecked")
                  J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> success =
                      (J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate>) args[2];
                  J2clSearchPanelController.ErrorCallback error =
                      (J2clSearchPanelController.ErrorCallback) args[3];
                  Runnable disconnect = (Runnable) args[4];
                  OpenAttempt attempt = new OpenAttempt((String) args[1], success, error, disconnect);
                  openAttempts.add(attempt);
                  return Proxy.newProxyInstance(
                      subscriptionClass.getClassLoader(),
                      new Class<?>[] {subscriptionClass},
                      (subscriptionProxy, subscriptionMethod, subscriptionArgs) -> {
                        if ("close".equals(subscriptionMethod.getName())) {
                          closedCount++;
                        }
                        return null;
                      });
                }
                return null;
              });

      Object view =
          Proxy.newProxyInstance(
              viewClass.getClassLoader(),
              new Class<?>[] {viewClass},
              (proxy, method, args) -> {
                if ("render".equals(method.getName())) {
                  lastModel = args[0];
                }
                return null;
              });

      Object controller;
      if (withScheduler) {
        Class<?> schedulerClass =
            Class.forName("org.waveprotocol.box.j2cl.search.J2clSelectedWaveController$RetryScheduler");
        Object scheduler =
            Proxy.newProxyInstance(
                schedulerClass.getClassLoader(),
                new Class<?>[] {schedulerClass},
                (proxy, method, args) -> {
                  scheduledDelays.add(((Number) args[0]).intValue());
                  scheduledRetries.add((Runnable) args[1]);
                  return null;
                });
        Constructor<?> constructor = controllerClass.getConstructor(gatewayClass, viewClass, schedulerClass);
        controller = constructor.newInstance(gateway, view, scheduler);
      } else {
        Constructor<?> constructor = controllerClass.getConstructor(gatewayClass, viewClass);
        controller = constructor.newInstance(gateway, view);
      }
      onWaveSelectedMethod = controllerClass.getMethod("onWaveSelected", String.class);
      onWaveSelectedWithDigestMethod =
          controllerClass.getMethod("onWaveSelected", String.class, J2clSearchDigestItem.class);
      return controller;
    }

    private void selectWave(Object controller, String waveId, J2clSearchDigestItem digestItem) throws Exception {
      if (digestItem == null) {
        onWaveSelectedMethod.invoke(controller, waveId);
      } else {
        onWaveSelectedWithDigestMethod.invoke(controller, waveId, digestItem);
      }
    }

    private void resolveBootstrap(int index) {
      bootstrapAttempts.get(index)
          .success
          .accept(new SidecarSessionBootstrap("user@example.com", "socket.example.test"));
    }

    private void rejectBootstrap(int index, String message) {
      bootstrapAttempts.get(index).error.accept(message);
    }

    private void failOpen(int index, String message) {
      openAttempts.get(index).error.accept(message);
    }

    private void fireDisconnect(int index) {
      openAttempts.get(index).disconnect.run();
    }

    private void runScheduledRetry(int index) {
      scheduledRetries.get(index).run();
    }

    private void deliverUpdate(int index, String rawSnapshot) {
      deliverRawUpdate(index, update("example.com!w+1/example.com!conv+root", rawSnapshot));
    }

    private void deliverSnapshotOnlyUpdate(int index, String textContent) {
      deliverRawUpdate(index, snapshotOnlyUpdate(textContent));
    }

    private void deliverRawUpdate(int index, SidecarSelectedWaveUpdate update) {
      openAttempts.get(index).success.accept(update);
    }

    private Object modelValue(String methodName) throws Exception {
      Method method = lastModel.getClass().getMethod(methodName);
      return method.invoke(lastModel);
    }
  }

  private static SidecarSelectedWaveUpdate update(String waveletName, String rawSnapshot) {
    return new SidecarSelectedWaveUpdate(
        1,
        waveletName,
        true,
        "chan-1",
        Arrays.asList("user@example.com", "teammate@example.com"),
        Arrays.asList(
            new SidecarSelectedWaveDocument(
                "b+root", "user@example.com", 33L, 44L, rawSnapshot)),
        new SidecarSelectedWaveFragments(
            44L,
            40L,
            44L,
            Arrays.asList(
                new SidecarSelectedWaveFragmentRange("manifest", 40L, 44L),
                new SidecarSelectedWaveFragmentRange("blip:b+root", 41L, 44L)),
            Arrays.asList(
                new SidecarSelectedWaveFragment("manifest", "conversation: Inbox wave", 0, 0),
                new SidecarSelectedWaveFragment("blip:b+root", rawSnapshot, 0, 0))));
  }

  private static SidecarSelectedWaveUpdate snapshotOnlyUpdate(String textContent) {
    return new SidecarSelectedWaveUpdate(
        1,
        "local.net!w+s4635670bfbwA/~/conv+root",
        true,
        "ch3",
        Arrays.asList("user@example.com"),
        Arrays.asList(
            new SidecarSelectedWaveDocument("b+abc123", "user@example.com", 1L, 2L, textContent)),
        new SidecarSelectedWaveFragments(
            0L,
            0L,
            0L,
            new ArrayList<SidecarSelectedWaveFragmentRange>(),
            new ArrayList<SidecarSelectedWaveFragment>()));
  }

  private static final class BootstrapAttempt {
    private final J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> success;
    private final J2clSearchPanelController.ErrorCallback error;

    private BootstrapAttempt(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> success,
        J2clSearchPanelController.ErrorCallback error) {
      this.success = success;
      this.error = error;
    }
  }

  private static final class OpenAttempt {
    private final String waveId;
    private final J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> success;
    private final J2clSearchPanelController.ErrorCallback error;
    private final Runnable disconnect;

    private OpenAttempt(
        String waveId,
        J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> success,
        J2clSearchPanelController.ErrorCallback error,
        Runnable disconnect) {
      this.waveId = waveId;
      this.success = success;
      this.error = error;
      this.disconnect = disconnect;
    }
  }
}
