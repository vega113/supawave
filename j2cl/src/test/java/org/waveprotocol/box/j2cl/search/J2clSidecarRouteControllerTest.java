package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clSidecarRouteControllerTest.class)
public class J2clSidecarRouteControllerTest {
  @Test
  public void startNormalizesUrlAndRestoresQueryAndSelectedWave() {
    FakeHistoryAdapter history =
        new FakeHistoryAdapter("?wave=example.com%2Fw%2B1&q=with%3A%40");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(history, searchController, selectedWaveController);

    controller.start();

    Assert.assertEquals(
        Arrays.asList("?q=with%3A%40&wave=example.com%2Fw%2B1#example.com/w+1"),
        history.replacedUrls);
    Assert.assertEquals(
        Arrays.asList("start:with:@:example.com/w+1"),
        searchController.events);
    Assert.assertEquals(
        Arrays.asList("example.com/w+1:null"),
        selectedWaveController.events);
    Assert.assertEquals(0, history.pushedUrls.size());
  }

  @Test
  public void userQueryChangePushesQueryOnlyRouteAndClearsSelectedWave() {
    FakeHistoryAdapter history = new FakeHistoryAdapter("");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(history, searchController, selectedWaveController);

    controller.start();
    controller.onRouteStateChanged(new J2clSidecarRouteState("with:@", null), null, true);

    Assert.assertEquals(
        Arrays.asList("?q=with%3A%40"),
        history.pushedUrls);
    Assert.assertEquals(Arrays.asList("null:null"), selectedWaveController.tailEvents(1));
  }

  @Test
  public void userSelectionPushesRouteWithWave() {
    FakeHistoryAdapter history = new FakeHistoryAdapter("?q=with%3A%40");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(history, searchController, selectedWaveController);

    controller.start();
    controller.onRouteStateChanged(
        new J2clSidecarRouteState("with:@", "example.com/w+1"), digest("example.com/w+1"), true);

    Assert.assertEquals(
        Arrays.asList("?q=with%3A%40&wave=example.com%2Fw%2B1#example.com/w+1"),
        history.pushedUrls);
    Assert.assertEquals(
        Arrays.asList("example.com/w+1:example.com/w+1"),
        selectedWaveController.tailEvents(1));
  }

  @Test
  public void popStateRestoresEarlierRouteWithoutEchoingPushState() {
    FakeHistoryAdapter history = new FakeHistoryAdapter("?q=with%3A%40&wave=example.com%2Fw%2B1");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(history, searchController, selectedWaveController);

    controller.start();
    history.setSearch("?q=in%3Ainbox");
    history.firePopState();

    Assert.assertEquals(
        Arrays.asList("start:with:@:example.com/w+1", "restore:in:inbox:null"),
        searchController.events);
    Assert.assertEquals(0, history.pushedUrls.size());
    Assert.assertEquals(Arrays.asList("null:null"), selectedWaveController.tailEvents(1));
  }

  @Test
  public void internalMetadataRefreshDoesNotPushDuplicateHistoryEntry() {
    FakeHistoryAdapter history = new FakeHistoryAdapter("?q=with%3A%40&wave=example.com%2Fw%2B1");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(history, searchController, selectedWaveController);

    controller.start();
    controller.onRouteStateChanged(
        new J2clSidecarRouteState("with:@", "example.com/w+1"), digest("example.com/w+1"), false);

    Assert.assertEquals(0, history.pushedUrls.size());
    Assert.assertEquals(
        Arrays.asList("example.com/w+1:example.com/w+1"),
        selectedWaveController.tailEvents(1));
  }

  @Test
  public void selectWavePushesRouteWhilePreservingCurrentQuery() {
    FakeHistoryAdapter history = new FakeHistoryAdapter("?q=with%3A%40");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(history, searchController, selectedWaveController);

    controller.start();
    controller.selectWave("example.com/w+2");

    Assert.assertEquals(
        Arrays.asList("?q=with%3A%40&wave=example.com%2Fw%2B2#example.com/w+2"),
        history.pushedUrls);
    Assert.assertEquals(
        Arrays.asList("sync:example.com/w+2"),
        searchController.tailEvents(1));
    Assert.assertEquals(
        Arrays.asList("example.com/w+2:null"),
        selectedWaveController.tailEvents(1));
  }

  @Test
  public void rootShellStartPreservesExplicitSelectorAndState() {
    FakeHistoryAdapter history =
        new FakeHistoryAdapter("?view=j2cl-root&q=with%3A%40&wave=example.com%2Fw%2B1");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(
            history, searchController, selectedWaveController, "view=j2cl-root");

    controller.start();

    Assert.assertEquals(
        Arrays.asList("?view=j2cl-root&q=with%3A%40&wave=example.com%2Fw%2B1#example.com/w+1"),
        history.replacedUrls);
    Assert.assertEquals(
        Arrays.asList("start:with:@:example.com/w+1"),
        searchController.events);
    Assert.assertEquals(
        Arrays.asList("example.com/w+1:null"),
        selectedWaveController.events);
  }

  @Test
  public void rootShellNavigationKeepsExplicitSelectorOnPush() {
    FakeHistoryAdapter history = new FakeHistoryAdapter("?view=j2cl-root&q=with%3A%40");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(
            history, searchController, selectedWaveController, "view=j2cl-root");

    controller.start();
    controller.selectWave("example.com/w+2");

    Assert.assertEquals(
        Arrays.asList("?view=j2cl-root&q=with%3A%40&wave=example.com%2Fw%2B2#example.com/w+2"),
        history.pushedUrls);
    Assert.assertEquals(
        Arrays.asList("sync:example.com/w+2"),
        searchController.tailEvents(1));
  }

  @Test
  public void rootShellPopStateRestoresSearchWithoutDroppingSelector() {
    FakeHistoryAdapter history =
        new FakeHistoryAdapter("?view=j2cl-root&q=with%3A%40&wave=example.com%2Fw%2B1");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(
            history, searchController, selectedWaveController, "view=j2cl-root");

    controller.start();
    history.setSearch("?view=j2cl-root&q=in%3Ainbox");
    history.firePopState();

    Assert.assertEquals(
        Arrays.asList("start:with:@:example.com/w+1", "restore:in:inbox:null"),
        searchController.events);
    Assert.assertEquals(1, history.replacedUrls.size());
    Assert.assertTrue(history.pushedUrls.isEmpty());
    Assert.assertEquals(
        Arrays.asList("example.com/w+1:null", "null:null"),
        selectedWaveController.events);
  }

  @Test
  public void rootShellRouteObserverSeesStartPushAndPopUrls() {
    FakeHistoryAdapter history =
        new FakeHistoryAdapter("?view=j2cl-root&q=with%3A%40&wave=example.com%2Fw%2B1");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    FakeRouteUrlObserver routeUrlObserver = new FakeRouteUrlObserver();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(
            history,
            searchController,
            selectedWaveController,
            "view=j2cl-root",
            routeUrlObserver);

    controller.start();
    controller.selectWave("example.com/w+2");
    history.setSearch("?view=j2cl-root&q=in%3Ainbox");
    history.firePopState();

    Assert.assertEquals(
        Arrays.asList(
            "?view=j2cl-root&q=with%3A%40&wave=example.com%2Fw%2B1#example.com/w+1",
            "?view=j2cl-root&q=with%3A%40&wave=example.com%2Fw%2B2#example.com/w+2",
            "?view=j2cl-root&q=in%3Ainbox"),
        routeUrlObserver.urls);
  }

  @Test
  public void rootShellStartNormalizesLegacyHashWaveIntoQueryRoute() {
    FakeHistoryAdapter history = new FakeHistoryAdapter("", "#example.com/w+1");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(
            history, searchController, selectedWaveController, "view=j2cl-root");

    controller.start();

    Assert.assertEquals(
        Arrays.asList("?view=j2cl-root&q=in%3Ainbox&wave=example.com%2Fw%2B1#example.com/w+1"),
        history.replacedUrls);
    Assert.assertEquals(
        Arrays.asList("start:in:inbox:example.com/w+1"),
        searchController.events);
    Assert.assertEquals(
        Arrays.asList("example.com/w+1:null"),
        selectedWaveController.events);
  }

  @Test
  public void explicitWaveQueryTakesPrecedenceOverLegacyHashRoute() {
    FakeHistoryAdapter history =
        new FakeHistoryAdapter(
            "?view=j2cl-root&q=with%3A%40&wave=example.com%2Fw%2B1",
            "#example.com/w+2");
    FakeSearchPanelController searchController = new FakeSearchPanelController();
    FakeSelectedWaveController selectedWaveController = new FakeSelectedWaveController();
    J2clSidecarRouteController controller =
        new J2clSidecarRouteController(
            history, searchController, selectedWaveController, "view=j2cl-root");

    controller.start();

    Assert.assertEquals(
        Arrays.asList("start:with:@:example.com/w+1"),
        searchController.events);
    Assert.assertEquals(
        Arrays.asList("example.com/w+1:null"),
        selectedWaveController.events);
  }

  private static J2clSearchDigestItem digest(String waveId) {
    return new J2clSearchDigestItem(
        waveId, "Wave", "Snippet", "user@example.com", 1, 2, 3L, false);
  }

  private static final class FakeHistoryAdapter
      implements J2clSidecarRouteController.HistoryAdapter {
    private final List<String> pushedUrls = new ArrayList<String>();
    private final List<String> replacedUrls = new ArrayList<String>();
    private String search;
    private String hash;
    private Runnable popStateListener;

    private FakeHistoryAdapter(String search) {
      this(search, "");
    }

    private FakeHistoryAdapter(String search, String hash) {
      this.search = search;
      this.hash = hash;
    }

    @Override
    public String getSearch() {
      return search;
    }

    @Override
    public String getHash() {
      return hash;
    }

    @Override
    public void pushUrl(String url) {
      pushedUrls.add(url);
    }

    @Override
    public void replaceUrl(String url) {
      replacedUrls.add(url);
    }

    @Override
    public void setPopStateListener(Runnable listener) {
      this.popStateListener = listener;
    }

    private void setSearch(String search) {
      this.search = search;
    }

    private void setHash(String hash) {
      this.hash = hash;
    }

    private void firePopState() {
      popStateListener.run();
    }
  }

  private static final class FakeSearchPanelController
      implements J2clSidecarRouteController.SearchPanelController {
    private final List<String> events = new ArrayList<String>();

    @Override
    public void start(String initialQuery, String initialSelectedWaveId) {
      events.add("start:" + initialQuery + ":" + initialSelectedWaveId);
    }

    @Override
    public void restoreRoute(String query, String selectedWaveId) {
      events.add("restore:" + query + ":" + selectedWaveId);
    }

    @Override
    public void syncSelection(String selectedWaveId) {
      events.add("sync:" + selectedWaveId);
    }

    private List<String> tailEvents(int count) {
      return events.subList(events.size() - count, events.size());
    }
  }

  private static final class FakeRouteUrlObserver
      implements J2clSidecarRouteController.RouteUrlObserver {
    private final List<String> urls = new ArrayList<String>();

    @Override
    public void onUrlChanged(String url) {
      urls.add(url);
    }
  }

  private static final class FakeSelectedWaveController
      implements J2clSidecarRouteController.SelectedWaveController {
    private final List<String> events = new ArrayList<String>();

    @Override
    public void onWaveSelected(String waveId, J2clSearchDigestItem digestItem) {
      events.add(waveId + ":" + (digestItem == null ? null : digestItem.getWaveId()));
    }

    private List<String> tailEvents(int count) {
      return events.subList(events.size() - count, events.size());
    }
  }
}
