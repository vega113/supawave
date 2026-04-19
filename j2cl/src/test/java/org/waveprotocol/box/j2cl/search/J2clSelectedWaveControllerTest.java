package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
  public void selectingWaveRendersLiveUpdateAndReconnectsAfterDisconnect() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController();

    controller.getClass().getMethod("onWaveSelected", String.class).invoke(controller, "example.com/w+1");

    Assert.assertEquals("example.com/w+1", harness.modelValue("getSelectedWaveId"));
    Assert.assertEquals(Boolean.TRUE, harness.modelValue("isLoading"));
    Assert.assertEquals(1, harness.openCount);

    harness.deliverUpdate("Hello from the sidecar");

    Assert.assertEquals(Boolean.FALSE, harness.modelValue("isLoading"));
    Assert.assertEquals(Boolean.FALSE, harness.modelValue("isError"));
    Assert.assertEquals(1, ((List<?>) harness.modelValue("getContentEntries")).size());
    Assert.assertEquals("Hello from the sidecar", ((List<?>) harness.modelValue("getContentEntries")).get(0));

    harness.fireDisconnect();
    Assert.assertEquals(2, harness.openCount);

    harness.deliverUpdate("After reconnect");

    Assert.assertEquals(1, harness.modelValue("getReconnectCount"));
    Assert.assertEquals("After reconnect", ((List<?>) harness.modelValue("getContentEntries")).get(0));
  }

  @Test
  public void clearingSelectionClosesSubscriptionAndReturnsToEmptyState() throws Exception {
    Harness harness = new Harness();
    Object controller = harness.createController();

    controller.getClass().getMethod("onWaveSelected", String.class).invoke(controller, "example.com/w+1");
    harness.deliverUpdate("Hello from the sidecar");

    controller.getClass().getMethod("onWaveSelected", String.class).invoke(controller, new Object[] {null});

    Assert.assertEquals(1, harness.closedCount);
    Assert.assertEquals(Boolean.FALSE, harness.modelValue("hasSelection"));
    Assert.assertEquals(Boolean.FALSE, harness.modelValue("isLoading"));
  }

  private static final class Harness {
    private int openCount;
    private int closedCount;
    private J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> updateCallback;
    private Runnable disconnectCallback;
    private Object lastModel;

    private Object createController() throws Exception {
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
                  success.accept(
                      new SidecarSessionBootstrap("user@example.com", "socket.example.test"));
                  return null;
                }
                if ("openSelectedWave".equals(method.getName())) {
                  openCount++;
                  @SuppressWarnings("unchecked")
                  J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate> success =
                      (J2clSearchPanelController.SuccessCallback<SidecarSelectedWaveUpdate>) args[2];
                  updateCallback = success;
                  disconnectCallback = (Runnable) args[4];
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

      Constructor<?> constructor = controllerClass.getConstructor(gatewayClass, viewClass);
      return constructor.newInstance(gateway, view);
    }

    private void deliverUpdate(String rawSnapshot) {
      updateCallback.accept(
          new SidecarSelectedWaveUpdate(
              1,
              "example.com!w+1/example.com!conv+root",
              true,
              "chan-1",
              java.util.Arrays.asList("user@example.com", "teammate@example.com"),
              java.util.Collections.singletonList(
                  new SidecarSelectedWaveDocument("b+root", "user@example.com", 33L, 44L)),
              new SidecarSelectedWaveFragments(
                  44L,
                  40L,
                  44L,
                  java.util.Arrays.asList(
                      new SidecarSelectedWaveFragmentRange("manifest", 40L, 44L),
                      new SidecarSelectedWaveFragmentRange("blip:b+root", 41L, 44L)),
                  java.util.Arrays.asList(
                      new SidecarSelectedWaveFragment("manifest", "conversation: Inbox wave", 0, 0),
                      new SidecarSelectedWaveFragment("blip:b+root", rawSnapshot, 0, 0)))));
    }

    private void fireDisconnect() {
      disconnectCallback.run();
    }

    private Object modelValue(String methodName) throws Exception {
      Method method = lastModel.getClass().getMethod(methodName);
      return method.invoke(lastModel);
    }
  }
}
