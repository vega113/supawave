package org.waveprotocol.box.j2cl.root;

import com.google.j2cl.junit.apt.J2clTestInput;
import elemental2.dom.DomGlobal;
import elemental2.dom.HTMLDivElement;
import elemental2.dom.HTMLElement;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.waveprotocol.box.j2cl.search.J2clSidecarRouteState;

@J2clTestInput(J2clRootShellViewTest.class)
public class J2clRootShellViewTest {
  private HTMLDivElement host;

  @After
  public void tearDown() {
    if (host != null && host.parentElement != null) {
      host.parentElement.removeChild(host);
    }
    host = null;
  }

  @Test
  public void publishLiveStatusCreatesSingleAriaHiddenSeparatorAndLiveText() {
    assumeBrowserDom();
    J2clRootShellView view = createViewWithStatusStrip();
    HTMLElement statusStrip = statusStrip();

    view.publishLiveStatus(queryStatusModel("in:inbox"));
    view.publishLiveStatus(J2clRootLiveSurfaceModel.starting().withSelectedWaveId("example/w+1"));

    Assert.assertEquals(1, host.querySelectorAll("#j2cl-root-live-status-separator").length);
    HTMLElement separator =
        (HTMLElement) host.querySelector("#j2cl-root-live-status-separator");
    Assert.assertEquals("true", separator.getAttribute("aria-hidden"));
    Assert.assertEquals("", separator.style.display);
    HTMLElement liveStatus = (HTMLElement) host.querySelector("#j2cl-root-live-status-text");
    Assert.assertTrue(statusStrip.contains(separator));
    Assert.assertTrue(statusStrip.contains(liveStatus));
    Assert.assertNull(liveStatus.getAttribute("role"));
    Assert.assertEquals("polite", liveStatus.getAttribute("aria-live"));
    Assert.assertEquals("j2cl-status-live-text", liveStatus.className);
    Assert.assertEquals("j2cl-status-live-separator", separator.className);
    Assert.assertEquals(liveStatus, separator.nextSibling);
    Assert.assertEquals("Selected wave is active.", liveStatus.textContent);
  }

  @Test
  public void publishLiveStatusNullClearsTextAndHidesSeparator() {
    assumeBrowserDom();
    J2clRootShellView view = createViewWithStatusStrip();

    view.publishLiveStatus(queryStatusModel("in:inbox"));
    view.publishLiveStatus(null);

    HTMLElement separator =
        (HTMLElement) host.querySelector("#j2cl-root-live-status-separator");
    HTMLElement liveStatus = (HTMLElement) host.querySelector("#j2cl-root-live-status-text");
    Assert.assertEquals("", liveStatus.textContent);
    Assert.assertEquals("none", separator.style.display);
  }

  @Test
  public void publishLiveStatusScopesInsertionToOwningRootShell() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement firstRoot = createRootShell();
    HTMLElement firstStatusStrip = appendStatusStrip(firstRoot);
    HTMLElement secondRoot = createRootShell();
    HTMLElement secondStatusStrip = appendStatusStrip(secondRoot);
    HTMLElement secondWorkflowHost = appendWorkflowHost(secondRoot);
    J2clRootShellView view = new J2clRootShellView(secondWorkflowHost);

    view.publishLiveStatus(queryStatusModel("in:inbox"));

    Assert.assertNull(firstStatusStrip.querySelector("#j2cl-root-live-status-text"));
    Assert.assertNotNull(secondStatusStrip.querySelector("#j2cl-root-live-status-text"));
  }

  @Test
  public void syncReturnTargetScopesUpdatesToOwningRootShell() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement firstRoot = createRootShell();
    appendReturnTargetNodes(firstRoot);
    HTMLElement secondRoot = createRootShell();
    appendReturnTargetNodes(secondRoot);
    HTMLElement secondWorkflowHost = appendWorkflowHost(secondRoot);
    J2clRootShellView view = new J2clRootShellView(secondWorkflowHost);

    view.syncReturnTarget("?view=j2cl-root&q=in%3Ainbox");

    Assert.assertNull(firstRoot.getAttribute("data-j2cl-root-return-target"));
    Assert.assertEquals(
        "/?view=j2cl-root&q=in%3Ainbox",
        secondRoot.getAttribute("data-j2cl-root-return-target"));
    Assert.assertEquals(
        "/?view=j2cl-root&q=in%3Ainbox",
        ((HTMLElement) secondRoot.querySelector("#j2cl-root-brand-link")).getAttribute("href"));
    Assert.assertEquals(
        "/auth/signin?r=/%3Fview%3Dj2cl-root%26q%3Din%253Ainbox",
        ((HTMLElement) secondRoot.querySelector("[data-j2cl-root-signin='true']"))
            .getAttribute("href"));
    Assert.assertEquals(
        "/auth/signout?r=/%3Fview%3Dj2cl-root%26q%3Din%253Ainbox",
        ((HTMLElement) secondRoot.querySelector("[data-j2cl-root-signout='true']"))
            .getAttribute("href"));
    Assert.assertNull(
        ((HTMLElement) firstRoot.querySelector("[data-j2cl-root-signin='true']"))
            .getAttribute("href"));
    Assert.assertNull(
        ((HTMLElement) firstRoot.querySelector("[data-j2cl-root-signout='true']"))
            .getAttribute("href"));
    Assert.assertEquals(
        "Return target: /?view=j2cl-root&q=in%3Ainbox",
        ((HTMLElement) secondRoot.querySelector("#j2cl-root-return-target-text")).textContent);
  }

  @Test
  public void syncReturnTargetDoesNotUpdateNestedRootShellNodes() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement outerRoot = createRootShell();
    appendReturnTargetNodes(outerRoot);
    HTMLElement outerWorkflowHost = appendWorkflowHost(outerRoot);
    HTMLElement innerRoot = (HTMLElement) DomGlobal.document.createElement("shell-root");
    innerRoot.setAttribute("data-j2cl-root-shell", "true");
    outerRoot.appendChild(innerRoot);
    appendReturnTargetNodes(innerRoot);
    J2clRootShellView view = new J2clRootShellView(outerWorkflowHost);

    view.syncReturnTarget("?view=j2cl-root&q=in%3Ainbox");

    Assert.assertEquals(
        "/?view=j2cl-root&q=in%3Ainbox",
        ((HTMLElement) outerRoot.querySelector("#j2cl-root-brand-link")).getAttribute("href"));
    Assert.assertEquals(
        "/auth/signin?r=/%3Fview%3Dj2cl-root%26q%3Din%253Ainbox",
        ((HTMLElement) outerRoot.querySelector("[data-j2cl-root-signin='true']"))
            .getAttribute("href"));
    Assert.assertEquals(
        "/auth/signout?r=/%3Fview%3Dj2cl-root%26q%3Din%253Ainbox",
        ((HTMLElement) outerRoot.querySelector("[data-j2cl-root-signout='true']"))
            .getAttribute("href"));
    Assert.assertNull(
        ((HTMLElement) innerRoot.querySelector("#j2cl-root-brand-link")).getAttribute("href"));
    Assert.assertNull(
        ((HTMLElement) innerRoot.querySelector("[data-j2cl-root-signin='true']"))
            .getAttribute("href"));
    Assert.assertNull(
        ((HTMLElement) innerRoot.querySelector("[data-j2cl-root-signout='true']"))
            .getAttribute("href"));
    Assert.assertEquals(
        "Return target: /?view=j2cl-root&q=in%3Ainbox",
        ((HTMLElement) outerRoot.querySelector("#j2cl-root-return-target-text")).textContent);
    Assert.assertEquals(
        "",
        ((HTMLElement) innerRoot.querySelector("#j2cl-root-return-target-text")).textContent);
  }

  @Test
  public void publishLiveStatusScopesInsertionToNearestNestedRootShell() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement outerRoot = createRootShell();
    HTMLElement outerStatusStrip = appendStatusStrip(outerRoot);
    HTMLElement innerRoot = (HTMLElement) DomGlobal.document.createElement("shell-root");
    innerRoot.setAttribute("data-j2cl-root-shell", "true");
    outerRoot.appendChild(innerRoot);
    HTMLElement innerStatusStrip = appendStatusStrip(innerRoot);
    HTMLElement innerWorkflowHost = appendWorkflowHost(innerRoot);
    J2clRootShellView view = new J2clRootShellView(innerWorkflowHost);

    view.publishLiveStatus(queryStatusModel("in:inbox"));

    Assert.assertNull(outerStatusStrip.querySelector("#j2cl-root-live-status-text"));
    Assert.assertNotNull(innerStatusStrip.querySelector("#j2cl-root-live-status-text"));
  }

  @Test
  public void publishLiveStatusUsesServerFirstWorkflowHostAncestor() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement rootShell = createRootShell();
    HTMLElement statusStrip = appendStatusStrip(rootShell);
    HTMLElement workflowHost = appendWorkflowHost(rootShell);
    HTMLElement serverFirstWorkflow =
        (HTMLElement) DomGlobal.document.createElement("div");
    serverFirstWorkflow.className = "sidecar-search-shell";
    serverFirstWorkflow.setAttribute("data-j2cl-server-first-workflow", "true");
    workflowHost.appendChild(serverFirstWorkflow);
    J2clRootShellView view = new J2clRootShellView(workflowHost);

    view.publishLiveStatus(queryStatusModel("in:inbox"));

    Assert.assertNotNull(statusStrip.querySelector("#j2cl-root-live-status-text"));
  }

  @Test
  public void publishLiveStatusDoesNotUseUnrelatedStatusStripOutsideRootShell() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement unrelatedStatusStrip = appendStatusStrip(host);
    HTMLElement workflowHost = (HTMLElement) DomGlobal.document.createElement("div");
    host.appendChild(workflowHost);
    J2clRootShellView view = new J2clRootShellView(workflowHost);

    view.publishLiveStatus(queryStatusModel("in:inbox"));

    Assert.assertNull(unrelatedStatusStrip.querySelector("#j2cl-root-live-status-text"));
    Assert.assertNull(host.querySelector("#j2cl-root-live-status-text"));
  }

  @Test
  public void publishLiveStatusDoesNothingWithoutStatusStrip() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement rootShell = createRootShell();
    HTMLElement workflowHost = appendWorkflowHost(rootShell);
    J2clRootShellView view = new J2clRootShellView(workflowHost);

    view.publishLiveStatus(queryStatusModel("in:inbox"));

    Assert.assertNull(host.querySelector("#j2cl-root-live-status-separator"));
    Assert.assertNull(host.querySelector("#j2cl-root-live-status-text"));
  }

  @Test
  public void publishLiveStatusCanAttachAfterStatusStripArrives() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement rootShell = createRootShell();
    HTMLElement workflowHost = appendWorkflowHost(rootShell);
    J2clRootShellView view = new J2clRootShellView(workflowHost);

    view.publishLiveStatus(queryStatusModel("in:inbox"));
    HTMLElement statusStrip = appendStatusStrip(rootShell);
    view.publishLiveStatus(queryStatusModel("in:inbox"));

    Assert.assertNotNull(statusStrip.querySelector("#j2cl-root-live-status-text"));
  }

  @Test
  public void publishLiveStatusCanReturnToStartingStatus() {
    assumeBrowserDom();
    J2clRootShellView view = createViewWithStatusStrip();

    view.publishLiveStatus(queryStatusModel("in:inbox"));
    view.publishLiveStatus(J2clRootLiveSurfaceModel.starting());

    HTMLElement liveStatus = (HTMLElement) host.querySelector("#j2cl-root-live-status-text");
    Assert.assertEquals("Loading workspace.", liveStatus.textContent);
  }

  @Test
  public void publishLiveStatusStampsIconChromeAttributes() {
    assumeBrowserDom();
    J2clRootShellView view = createViewWithStatusStrip();
    HTMLElement statusStrip = statusStrip();

    view.publishLiveStatus(J2clRootLiveSurfaceModel.starting().withSelectedWaveId("example/w+1"));

    Assert.assertNull(statusStrip.getAttribute("data-connection-state"));
    Assert.assertNull(statusStrip.getAttribute("data-save-state"));
    Assert.assertEquals("selected-wave", statusStrip.getAttribute("data-route-state"));
    Assert.assertEquals(
        "Selected wave is active.", statusStrip.getAttribute("data-live-status-text"));
  }

  @Test
  public void publishLiveStatusRepublishesWhenSeparatorDisplayMutatedExternally() {
    assumeBrowserDom();
    J2clRootShellView view = createViewWithStatusStrip();
    HTMLElement statusStrip = statusStrip();

    view.publishLiveStatus(queryStatusModel("in:inbox"));
    HTMLElement separator =
        (HTMLElement) statusStrip.querySelector("#j2cl-root-live-status-separator");
    // Simulate an external mutation of the separator display while status text is unchanged.
    separator.style.display = "none";
    view.publishLiveStatus(queryStatusModel("in:inbox"));

    Assert.assertEquals("", separator.style.display);
  }

  @Test
  public void syncReturnTargetPreservesBasePathForQueryOnlyRoute() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement rootShell = createRootShell();
    rootShell.setAttribute("data-j2cl-root-base-path", "/app/");
    appendReturnTargetNodes(rootShell);
    HTMLElement workflowHost = appendWorkflowHost(rootShell);
    J2clRootShellView view = new J2clRootShellView(workflowHost);

    view.syncReturnTarget("?view=j2cl-root&q=in%3Ainbox");

    Assert.assertEquals(
        "/app/?view=j2cl-root&q=in%3Ainbox",
        rootShell.getAttribute("data-j2cl-root-return-target"));
    Assert.assertEquals(
        "/app/?view=j2cl-root&q=in%3Ainbox",
        ((HTMLElement) rootShell.querySelector("#j2cl-root-brand-link")).getAttribute("href"));
  }

  @Test
  public void syncReturnTargetPreservesBasePathWithoutTrailingSlash() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement rootShell = createRootShell();
    rootShell.setAttribute("data-j2cl-root-base-path", "/app");
    appendReturnTargetNodes(rootShell);
    HTMLElement workflowHost = appendWorkflowHost(rootShell);
    J2clRootShellView view = new J2clRootShellView(workflowHost);

    view.syncReturnTarget("?view=j2cl-root&q=in%3Ainbox");

    Assert.assertEquals(
        "/app?view=j2cl-root&q=in%3Ainbox",
        rootShell.getAttribute("data-j2cl-root-return-target"));
    Assert.assertEquals(
        "/app?view=j2cl-root&q=in%3Ainbox",
        ((HTMLElement) rootShell.querySelector("#j2cl-root-brand-link")).getAttribute("href"));
  }

  @Test
  public void syncReturnTargetIgnoresUnsafeBasePathForQueryOnlyRoute() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement rootShell = createRootShell();
    rootShell.setAttribute("data-j2cl-root-base-path", "//evil.example");
    appendReturnTargetNodes(rootShell);
    HTMLElement workflowHost = appendWorkflowHost(rootShell);
    J2clRootShellView view = new J2clRootShellView(workflowHost);

    view.syncReturnTarget("?view=j2cl-root&q=in%3Ainbox");

    Assert.assertEquals(
        "/?view=j2cl-root&q=in%3Ainbox",
        rootShell.getAttribute("data-j2cl-root-return-target"));
    Assert.assertEquals(
        "/?view=j2cl-root&q=in%3Ainbox",
        ((HTMLElement) rootShell.querySelector("#j2cl-root-brand-link")).getAttribute("href"));
  }

  @Test
  public void publishLiveStatusRepublishesWhenSeparatorRemovedWithSameStatusText() {
    assumeBrowserDom();
    J2clRootShellView view = createViewWithStatusStrip();
    HTMLElement statusStrip = statusStrip();

    view.publishLiveStatus(queryStatusModel("in:inbox"));
    HTMLElement separator =
        (HTMLElement) statusStrip.querySelector("#j2cl-root-live-status-separator");
    statusStrip.removeChild(separator);
    view.publishLiveStatus(queryStatusModel("in:inbox"));

    HTMLElement restoredSeparator =
        (HTMLElement) statusStrip.querySelector("#j2cl-root-live-status-separator");
    Assert.assertNotNull(restoredSeparator);
    Assert.assertEquals("", restoredSeparator.style.display);
  }

  @Test
  public void publishLiveStatusReusesExistingNodesForRecreatedView() {
    assumeBrowserDom();
    J2clRootShellView firstView = createViewWithStatusStrip();
    HTMLElement workflowHost = firstView.getWorkflowHost();
    HTMLElement statusStrip = statusStrip();

    firstView.publishLiveStatus(queryStatusModel("in:inbox"));
    J2clRootShellView secondView = new J2clRootShellView(workflowHost);
    secondView.publishLiveStatus(J2clRootLiveSurfaceModel.starting());

    Assert.assertEquals(
        1, statusStrip.querySelectorAll("#j2cl-root-live-status-separator").length);
    Assert.assertEquals(1, statusStrip.querySelectorAll("#j2cl-root-live-status-text").length);
    Assert.assertEquals(
        "Loading workspace.",
        ((HTMLElement) statusStrip.querySelector("#j2cl-root-live-status-text")).textContent);
  }

  @Test
  public void publishLiveStatusRestoresMissingSeparatorBeforeExistingText() {
    assumeBrowserDom();
    J2clRootShellView view = createViewWithStatusStrip();
    HTMLElement statusStrip = statusStrip();

    view.publishLiveStatus(queryStatusModel("in:inbox"));
    HTMLElement separator =
        (HTMLElement) statusStrip.querySelector("#j2cl-root-live-status-separator");
    statusStrip.removeChild(separator);
    view.publishLiveStatus(J2clRootLiveSurfaceModel.starting());

    HTMLElement restoredSeparator =
        (HTMLElement) statusStrip.querySelector("#j2cl-root-live-status-separator");
    HTMLElement liveStatus =
        (HTMLElement) statusStrip.querySelector("#j2cl-root-live-status-text");
    Assert.assertEquals(1, statusStrip.querySelectorAll("#j2cl-root-live-status-separator").length);
    Assert.assertEquals(restoredSeparator.nextSibling, liveStatus);
    Assert.assertEquals("Loading workspace.", liveStatus.textContent);
  }

  @Test
  public void publishLiveStatusUpdatesRediscoveredNodeWhenStatusStripIsReplaced() {
    assumeBrowserDom();
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement rootShell = createRootShell();
    HTMLElement oldStatusStrip = appendStatusStrip(rootShell);
    HTMLElement workflowHost = appendWorkflowHost(rootShell);
    J2clRootShellView view = new J2clRootShellView(workflowHost);
    J2clRootLiveSurfaceModel inboxModel = queryStatusModel("in:inbox");

    view.publishLiveStatus(inboxModel);
    rootShell.removeChild(oldStatusStrip);
    HTMLElement newStatusStrip = appendStatusStrip(rootShell);
    HTMLElement existingLiveStatus = (HTMLElement) DomGlobal.document.createElement("span");
    existingLiveStatus.id = "j2cl-root-live-status-text";
    newStatusStrip.appendChild(existingLiveStatus);
    view.publishLiveStatus(inboxModel);

    Assert.assertEquals("Showing search results for in:inbox.", existingLiveStatus.textContent);
    Assert.assertEquals(1, newStatusStrip.querySelectorAll("#j2cl-root-live-status-text").length);
  }

  private J2clRootShellView createViewWithStatusStrip() {
    host = (HTMLDivElement) DomGlobal.document.createElement("div");
    DomGlobal.document.body.appendChild(host);
    HTMLElement rootShell = createRootShell();
    appendStatusStrip(rootShell);
    HTMLElement workflowHost = appendWorkflowHost(rootShell);
    return new J2clRootShellView(workflowHost);
  }

  private HTMLElement createRootShell() {
    HTMLElement rootShell = (HTMLElement) DomGlobal.document.createElement("shell-root");
    rootShell.setAttribute("data-j2cl-root-shell", "true");
    host.appendChild(rootShell);
    return rootShell;
  }

  private static HTMLElement appendStatusStrip(HTMLElement rootShell) {
    HTMLElement statusStrip = (HTMLElement) DomGlobal.document.createElement("shell-status-strip");
    statusStrip.setAttribute("slot", "status");
    rootShell.appendChild(statusStrip);
    return statusStrip;
  }

  private static HTMLElement appendWorkflowHost(HTMLElement rootShell) {
    HTMLElement workflowHost = (HTMLElement) DomGlobal.document.createElement("div");
    rootShell.appendChild(workflowHost);
    return workflowHost;
  }

  private static void appendReturnTargetNodes(HTMLElement rootShell) {
    HTMLElement brandLink = (HTMLElement) DomGlobal.document.createElement("a");
    brandLink.id = "j2cl-root-brand-link";
    rootShell.appendChild(brandLink);
    HTMLElement signIn = (HTMLElement) DomGlobal.document.createElement("a");
    signIn.setAttribute("data-j2cl-root-signin", "true");
    rootShell.appendChild(signIn);
    HTMLElement signOut = (HTMLElement) DomGlobal.document.createElement("a");
    signOut.setAttribute("data-j2cl-root-signout", "true");
    rootShell.appendChild(signOut);
    HTMLElement returnTargetText = (HTMLElement) DomGlobal.document.createElement("span");
    returnTargetText.id = "j2cl-root-return-target-text";
    rootShell.appendChild(returnTargetText);
  }

  private HTMLElement statusStrip() {
    return (HTMLElement) host.querySelector("shell-status-strip[slot='status']");
  }

  private static J2clRootLiveSurfaceModel queryStatusModel(String query) {
    return J2clRootLiveSurfaceModel.starting()
        .withRouteState(new J2clSidecarRouteState(query, null));
  }

  private static void assumeBrowserDom() {
    Assume.assumeTrue(DomGlobal.document != null && DomGlobal.document.body != null);
  }
}
