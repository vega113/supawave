package org.waveprotocol.box.j2cl.search;

import com.google.j2cl.junit.apt.J2clTestInput;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.waveprotocol.box.j2cl.transport.SidecarSessionBootstrap;
import org.junit.Assert;
import org.junit.Test;

@J2clTestInput(J2clSearchPanelControllerTest.class)
public class J2clSearchPanelControllerTest {
  @Test
  public void startLoadsDefaultQueryAndRendersInitialResponse() {
    FakeGateway gateway =
        new FakeGateway(
            new SidecarSearchResponse(
                "in:inbox",
                1,
                Collections.singletonList(
                    new SidecarSearchResponse.Digest(
                        "Inbox wave",
                        "Snippet",
                        "example.com/w+abc123",
                        12345L,
                        1,
                        3,
                        Collections.singletonList("teammate@example.com"),
                        "user@example.com",
                        false))));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> { }, 1200);

    controller.start("   ", null);

    Assert.assertEquals("in:inbox", gateway.lastQuery);
    Assert.assertEquals(0, gateway.lastIndex);
    Assert.assertEquals(30, gateway.lastNumResults);
    Assert.assertEquals("in:inbox", view.query);
    Assert.assertEquals("user@example.com", view.sessionSummary);
    Assert.assertEquals("1 waves · 1 unread", view.lastModel.getWaveCountText());
    Assert.assertFalse(view.loading);
  }

  @Test
  public void submittingQueryResetsWindowAndShowMoreUsesNextPage() {
    FakeGateway gateway =
        new FakeGateway(
            new SidecarSearchResponse(
                "with:@",
                45,
                Arrays.asList(
                    new SidecarSearchResponse.Digest(
                        "Wave 1",
                        "Snippet",
                        "example.com/w+1",
                        1L,
                        0,
                        2,
                        Collections.singletonList("person@example.com"),
                        "person@example.com",
                        false),
                    new SidecarSearchResponse.Digest(
                        "Wave 2",
                        "Snippet",
                        "example.com/w+2",
                        2L,
                        0,
                        2,
                        Collections.singletonList("person@example.com"),
                        "person@example.com",
                        false))));
    FakeView view = new FakeView();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(gateway, view, (state, digestItem, userNavigation) -> { }, 1200);

    controller.start(null, null);
    controller.onQuerySubmitted("with:@");
    Assert.assertEquals("with:@", gateway.lastQuery);
    Assert.assertEquals(30, gateway.lastNumResults);

    controller.onShowMoreRequested();
    Assert.assertEquals("with:@", gateway.lastQuery);
    Assert.assertEquals(60, gateway.lastNumResults);
  }

  @Test
  public void submittingQueryClearsSelectionAndNotifiesHandler() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Wave 1",
                    "Snippet",
                    "example.com/w+1",
                    1L,
                    0,
                    2,
                    Collections.singletonList("person@example.com"),
                    "person@example.com",
                    false)));
    FakeView view = new FakeView();
    List<String> routeEvents = new ArrayList<String>();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            view,
            (state, digestItem, userNavigation) ->
                routeEvents.add(state.getQuery() + "|" + state.getSelectedWaveId() + "|" + userNavigation),
            1200);

    controller.start(null, null);
    controller.onDigestSelected("example.com/w+1");
    controller.onQuerySubmitted("with:@");

    Assert.assertNull(view.selectedWaveId);
    Assert.assertEquals(
        Arrays.asList("in:inbox|example.com/w+1|true", "with:@|null|true"),
        routeEvents);
  }

  @Test
  public void missingSelectedWaveDoesNotClearRestoredRouteSelection() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Wave 1",
                    "Snippet",
                    "example.com/w+1",
                    1L,
                    0,
                    2,
                    Collections.singletonList("person@example.com"),
                    "person@example.com",
                    false)));
    FakeView view = new FakeView();
    List<String> routeEvents = new ArrayList<String>();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            view,
            (state, digestItem, userNavigation) ->
                routeEvents.add(state.getQuery() + "|" + state.getSelectedWaveId() + "|" + userNavigation),
            1200);

    controller.start("with:@", "example.com/w+1");
    gateway.response =
        responseWithDigests(
            new SidecarSearchResponse.Digest(
                "Wave 2",
                "Snippet",
                "example.com/w+2",
                2L,
                0,
                2,
                Collections.singletonList("person@example.com"),
                "person@example.com",
                false));

    controller.onShowMoreRequested();

    Assert.assertEquals("example.com/w+1", view.selectedWaveId);
    Assert.assertEquals(
        Arrays.asList("with:@|example.com/w+1|false"),
        routeEvents);
  }

  @Test
  public void searchErrorKeepsCurrentSelectionRouteState() {
    FakeGateway gateway =
        new FakeGateway(
            responseWithDigests(
                new SidecarSearchResponse.Digest(
                    "Wave 1",
                    "Snippet",
                    "example.com/w+1",
                    1L,
                    0,
                    2,
                    Collections.singletonList("person@example.com"),
                    "person@example.com",
                    false)));
    FakeView view = new FakeView();
    List<String> routeEvents = new ArrayList<String>();
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            view,
            (state, digestItem, userNavigation) ->
                routeEvents.add(state.getQuery() + "|" + state.getSelectedWaveId() + "|" + userNavigation),
            1200);

    controller.start(null, null);
    controller.onDigestSelected("example.com/w+1");
    gateway.error = "boom";

    controller.onShowMoreRequested();

    Assert.assertEquals("example.com/w+1", view.selectedWaveId);
    Assert.assertEquals("Search request failed: boom", view.status);
    Assert.assertTrue(view.error);
    Assert.assertEquals(Arrays.asList("in:inbox|example.com/w+1|true"), routeEvents);
  }

  @Test
  public void digestSelectionPublishesRouteStateAsUserNavigation() {
    FakeGateway gateway = new FakeGateway(new SidecarSearchResponse("in:inbox", 0, null));
    FakeView view = new FakeView();
    J2clSidecarRouteState[] selectedState = new J2clSidecarRouteState[1];
    boolean[] userNavigation = new boolean[1];
    J2clSearchPanelController controller =
        new J2clSearchPanelController(
            gateway,
            view,
            (state, digestItem, isUserNavigation) -> {
              selectedState[0] = state;
              userNavigation[0] = isUserNavigation;
            },
            1200);

    controller.onDigestSelected("example.com/w+abc123");

    Assert.assertEquals("example.com/w+abc123", view.selectedWaveId);
    Assert.assertEquals("example.com/w+abc123", selectedState[0].getSelectedWaveId());
    Assert.assertTrue(userNavigation[0]);
  }

  private static final class FakeGateway implements J2clSearchPanelController.SearchGateway {
    private SidecarSearchResponse response;
    private String error;
    private String lastQuery;
    private int lastIndex = -1;
    private int lastNumResults = -1;

    private FakeGateway(SidecarSearchResponse response) {
      this.response = response;
    }

    @Override
    public void fetchRootSessionBootstrap(
        J2clSearchPanelController.SuccessCallback<SidecarSessionBootstrap> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      onSuccess.accept(new SidecarSessionBootstrap("user@example.com", "socket.example.test"));
    }

    @Override
    public void search(
        String query,
        int index,
        int numResults,
        J2clSearchPanelController.SuccessCallback<SidecarSearchResponse> onSuccess,
        J2clSearchPanelController.ErrorCallback onError) {
      lastQuery = query;
      lastIndex = index;
      lastNumResults = numResults;
      if (error != null) {
        onError.accept(error);
        return;
      }
      onSuccess.accept(response);
    }
  }

  private static final class FakeView implements J2clSearchPanelController.View {
    private J2clSearchViewListener listener;
    private String query;
    private boolean loading;
    private String sessionSummary;
    private String status;
    private boolean error;
    private J2clSearchResultModel lastModel = J2clSearchResultModel.empty("");
    private String selectedWaveId;

    @Override
    public void bind(J2clSearchViewListener listener) {
      this.listener = listener;
    }

    @Override
    public void setQuery(String query) {
      this.query = query;
    }

    @Override
    public void setLoading(boolean loading) {
      this.loading = loading;
    }

    @Override
    public void setSessionSummary(String summary) {
      this.sessionSummary = summary;
    }

    @Override
    public void setStatus(String status, boolean error) {
      this.status = status;
      this.error = error;
    }

    @Override
    public void render(J2clSearchResultModel model) {
      this.lastModel = model;
    }

    @Override
    public void setSelectedWaveId(String waveId) {
      this.selectedWaveId = waveId;
    }
  }

  private static SidecarSearchResponse responseWithDigests(SidecarSearchResponse.Digest... digests) {
    return new SidecarSearchResponse("in:inbox", digests.length, Arrays.asList(digests));
  }
}
