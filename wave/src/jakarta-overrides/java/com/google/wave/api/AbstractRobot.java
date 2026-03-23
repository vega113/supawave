/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.google.wave.api;

import com.google.wave.api.event.AnnotatedTextChangedEvent;
import com.google.wave.api.event.BlipContributorsChangedEvent;
import com.google.wave.api.event.BlipSubmittedEvent;
import com.google.wave.api.event.DocumentChangedEvent;
import com.google.wave.api.event.Event;
import com.google.wave.api.event.EventHandler;
import com.google.wave.api.event.EventType;
import com.google.wave.api.event.FormButtonClickedEvent;
import com.google.wave.api.event.GadgetStateChangedEvent;
import com.google.wave.api.event.OperationErrorEvent;
import com.google.wave.api.event.WaveletBlipCreatedEvent;
import com.google.wave.api.event.WaveletBlipRemovedEvent;
import com.google.wave.api.event.WaveletCreatedEvent;
import com.google.wave.api.event.WaveletFetchedEvent;
import com.google.wave.api.event.WaveletParticipantsChangedEvent;
import com.google.wave.api.event.WaveletSelfAddedEvent;
import com.google.wave.api.event.WaveletSelfRemovedEvent;
import com.google.wave.api.event.WaveletTagsChangedEvent;
import com.google.wave.api.event.WaveletTitleChangedEvent;
import com.google.wave.api.impl.EventMessageBundle;

import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Jakarta override of AbstractRobot for in-JVM robot agents.
 *
 * <p>This version removes all {@code javax.servlet.http.HttpServlet} inheritance,
 * {@code net.oauth} dependencies, and HTTP request/response handling. It retains
 * the event processing pipeline and delegates wave operations to
 * {@link LocalWaveService} (which submits directly via
 * {@link org.waveprotocol.box.server.robots.agent.LocalOperationSubmitter}).
 *
 * <p>Built-in robot agents (welcome, passwd, registration, etc.) extend this
 * class and never serve HTTP -- they are instantiated directly by the server
 * and process events pushed to them by the robots gateway.
 */
public abstract class AbstractRobot implements EventHandler, Serializable {

  private static final long serialVersionUID = 1L;

  /** Some mime types. */
  public static final String JSON_MIME_TYPE = "application/json; charset=utf-8";
  public static final String TEXT_MIME_TYPE = "text/plain";
  public static final String XML_MIME_TYPE = "application/xml";

  /** Some constants for encoding. */
  public static final String UTF_8 = "UTF-8";

  /** The query parameter to specify custom profile request. */
  public static final String NAME_QUERY_PARAMETER_KEY = "name";

  /** The query parameter for security token. */
  public static final String SECURITY_TOKEN_PARAMETER_KEY = "st";

  /** Various request path constants that the robot replies to. */
  public static final String RPC_PATH = "/_wave/robot/jsonrpc";
  public static final String PROFILE_PATH = "/_wave/robot/profile";
  public static final String CAPABILITIES_PATH = "/_wave/capabilities.xml";
  public static final String VERIFY_TOKEN_PATH = "/_wave/verify_token";
  public static final String DEFAULT_AVATAR =
      "https://wave.google.com/a/wavesandbox.com/static/images/profiles/rusty.png";

  private static final Logger LOG = Logger.getLogger(AbstractRobot.class.getName());

  /** A map of this robot's capabilities. */
  private Map<String, Capability> capabilityMap;

  /** A version number that is computed from this robot's capabilities. */
  private String version;

  /** The local wave service for in-JVM operation submission. */
  private LocalWaveService localWaveService;

  /**
   * Constructor.
   */
  protected AbstractRobot() {
    capabilityMap = computeCapabilityMap();
    version = computeHash();
  }

  /**
   * Sets the {@link LocalWaveService} to be used for operation submission.
   * Called during agent initialization after construction.
   */
  public void setLocalWaveService(LocalWaveService localWaveService) {
    this.localWaveService = localWaveService;
  }

  /**
   * Returns the local wave service, or null if not yet set.
   */
  protected LocalWaveService getLocalWaveService() {
    return localWaveService;
  }

  /**
   * Returns the version hash of this robot.
   */
  protected String getVersion() {
    return version;
  }

  /**
   * Submits the pending operations associated with this {@link Wavelet}.
   *
   * @param wavelet the bundle that contains the operations to be submitted.
   * @param rpcServerUrl ignored for local submission.
   * @return a list of {@link JsonRpcResponse} that represents the responses
   *     from the server for all operations that were submitted.
   * @throws IOException if there is a problem submitting the operations.
   */
  public List<JsonRpcResponse> submit(Wavelet wavelet, String rpcServerUrl) throws IOException {
    return localWaveService.submit(wavelet, rpcServerUrl);
  }

  /**
   * Returns an empty/blind stub of a wavelet with the given wave id and wavelet
   * id.
   *
   * @param waveId the id of the wave.
   * @param waveletId the id of the wavelet.
   * @return a stub of a wavelet.
   */
  public Wavelet blindWavelet(WaveId waveId, WaveletId waveletId) {
    return localWaveService.blindWavelet(waveId, waveletId);
  }

  public Wavelet blindWavelet(WaveId waveId, WaveletId waveletId, String proxyForId) {
    return localWaveService.blindWavelet(waveId, waveletId, proxyForId);
  }

  public Wavelet blindWavelet(WaveId waveId, WaveletId waveletId, String proxyForId,
      Map<String, Blip> blips) {
    return localWaveService.blindWavelet(waveId, waveletId, proxyForId, blips);
  }

  public Wavelet blindWavelet(WaveId waveId, WaveletId waveletId, String proxyForId,
      Map<String, Blip> blips, Map<String, BlipThread> threads) {
    return localWaveService.blindWavelet(waveId, waveletId, proxyForId, blips, threads);
  }

  /**
   * Creates a new wave with a list of participants on it.
   */
  public Wavelet newWave(String domain, Set<String> participants) {
    return localWaveService.newWave(domain, participants);
  }

  public Wavelet newWave(String domain, Set<String> participants, String proxyForId) {
    return localWaveService.newWave(domain, participants, proxyForId);
  }

  public Wavelet newWave(String domain, Set<String> participants, String msg, String proxyForId) {
    return localWaveService.newWave(domain, participants, msg, proxyForId);
  }

  public Wavelet newWave(String domain, Set<String> participants, String msg, String proxyForId,
      String rpcServerUrl) throws IOException, InvalidIdException {
    return localWaveService.newWave(domain, participants, msg, proxyForId, rpcServerUrl);
  }

  /**
   * Requests SearchResult for a query.
   */
  public SearchResult search(String query, Integer index, Integer numResults, String rpcServerUrl)
      throws IOException {
    return localWaveService.search(query, index, numResults, rpcServerUrl);
  }

  /**
   * Fetches a wavelet using the active API.
   */
  public Wavelet fetchWavelet(WaveId waveId, WaveletId waveletId, String rpcServerUrl)
      throws IOException {
    return localWaveService.fetchWavelet(waveId, waveletId, rpcServerUrl);
  }

  public Wavelet fetchWavelet(WaveId waveId, WaveletId waveletId, String proxyForId,
      String rpcServerUrl) throws IOException {
    return localWaveService.fetchWavelet(waveId, waveletId, proxyForId, rpcServerUrl);
  }

  /**
   * @return a custom profile based on "name" query parameter, or {@code null}
   *     if this robot doesn't support custom profile.
   */
  protected ParticipantProfile getCustomProfile(@SuppressWarnings("unused") String name) {
    return null;
  }

  /**
   * @return the URL of the robot's avatar image.
   */
  protected String getRobotAvatarUrl() {
    return DEFAULT_AVATAR;
  }

  /**
   * No-op for in-JVM bots. Verification tokens are not needed.
   */
  protected void setupVerificationToken(String verificationToken, String securityToken) {
    // No-op.
  }

  /**
   * No-op for in-JVM bots. OAuth is bypassed entirely.
   */
  protected void setupOAuth(String consumerKey, String consumerSecret, String rpcServerUrl) {
    // No-op: in-JVM bots do not use OAuth.
  }

  /**
   * No-op for in-JVM bots.
   */
  protected void setupOAuth(String consumerKey, String consumerSecret) {
    // No-op.
  }

  /**
   * No-op for in-JVM bots.
   */
  protected void setAllowUnsignedRequests(boolean allowUnsignedRequests) {
    // No-op.
  }

  /**
   * Always returns true for in-JVM bots.
   */
  protected boolean isUnsignedRequestsAllowed() {
    return true;
  }

  /**
   * Processes the incoming event bundle. This method iterates over the event
   * bundle and dispatch the individual event to its own handler, based on the
   * event type.
   *
   * @param events the incoming event bundle.
   */
  protected void processEvents(EventMessageBundle events) {
    for (Event event : events.getEvents()) {
      switch (event.getType()) {
        case ANNOTATED_TEXT_CHANGED:
          onAnnotatedTextChanged(AnnotatedTextChangedEvent.as(event));
          break;
        case BLIP_CONTRIBUTORS_CHANGED:
          onBlipContributorsChanged(BlipContributorsChangedEvent.as(event));
          break;
        case BLIP_SUBMITTED:
          onBlipSubmitted(BlipSubmittedEvent.as(event));
          break;
        case DOCUMENT_CHANGED:
          onDocumentChanged(DocumentChangedEvent.as(event));
          break;
        case FORM_BUTTON_CLICKED:
          onFormButtonClicked(FormButtonClickedEvent.as(event));
          break;
        case GADGET_STATE_CHANGED:
          onGadgetStateChanged(GadgetStateChangedEvent.as(event));
          break;
        case WAVELET_BLIP_CREATED:
          onWaveletBlipCreated(WaveletBlipCreatedEvent.as(event));
          break;
        case WAVELET_BLIP_REMOVED:
          onWaveletBlipRemoved(WaveletBlipRemovedEvent.as(event));
          break;
        case WAVELET_CREATED:
          onWaveletCreated(WaveletCreatedEvent.as(event));
          break;
        case WAVELET_FETCHED:
          onWaveletFetched(WaveletFetchedEvent.as(event));
          break;
        case WAVELET_PARTICIPANTS_CHANGED:
          onWaveletParticipantsChanged(WaveletParticipantsChangedEvent.as(event));
          break;
        case WAVELET_SELF_ADDED:
          onWaveletSelfAdded(WaveletSelfAddedEvent.as(event));
          break;
        case WAVELET_SELF_REMOVED:
          onWaveletSelfRemoved(WaveletSelfRemovedEvent.as(event));
          break;
        case WAVELET_TAGS_CHANGED:
          onWaveletTagsChanged(WaveletTagsChangedEvent.as(event));
          break;
        case WAVELET_TITLE_CHANGED:
          onWaveletTitleChanged(WaveletTitleChangedEvent.as(event));
          break;
        case OPERATION_ERROR:
          onOperationError(OperationErrorEvent.as(event));
          break;
      }
    }
  }

  /**
   * Computes this robot's capabilities, based on the overridden event handler
   * methods, and their {@link EventHandler.Capability} annotations.
   *
   * @return a map of event type string to capability.
   */
  protected Map<String, Capability> computeCapabilityMap() {
    Map<String, Capability> map = new HashMap<String, Capability>();
    for (Method baseMethod : EventHandler.class.getDeclaredMethods()) {
      Method overriddenMethod = null;
      try {
        overriddenMethod = this.getClass().getMethod(baseMethod.getName(),
            baseMethod.getParameterTypes());
      } catch (NoSuchMethodException e) {
        // Robot does not override this particular event handler. Continue.
        continue;
      }

      // Skip the method, if it's declared in AbstractRobot.
      if (AbstractRobot.class.equals(overriddenMethod.getDeclaringClass())) {
        continue;
      }

      // Get the event type.
      EventType eventType = EventType.fromClass(overriddenMethod.getParameterTypes()[0]);

      // Get the capability annotation.
      Capability capability = overriddenMethod.getAnnotation(Capability.class);

      map.put(eventType.toString(), capability);
    }
    return map;
  }

  /**
   * Computes this robot's hash, based on the capabilities.
   *
   * @return a hash of this robot, computed from its capabilities.
   */
  protected String computeHash() {
    long version = 0L;
    for (Entry<String, Capability> entry : capabilityMap.entrySet()) {
      long hash = entry.getKey().hashCode();
      Capability capability = entry.getValue();
      if (capability != null) {
        for (Context context : capability.contexts()) {
          hash = hash * 31 + context.name().hashCode();
        }
        hash = hash * 31 + capability.filter().hashCode();
      }
      version = version * 17 + hash;
    }
    return Long.toHexString(version);
  }

  @Override
  public void onAnnotatedTextChanged(AnnotatedTextChangedEvent event) {
    // No-op.
  }

  @Override
  public void onBlipContributorsChanged(BlipContributorsChangedEvent event) {
    // No-op.
  }

  @Override
  public void onBlipSubmitted(BlipSubmittedEvent event) {
    // No-op.
  }

  @Override
  public void onDocumentChanged(DocumentChangedEvent event) {
    // No-op.
  }

  @Override
  public void onFormButtonClicked(FormButtonClickedEvent event) {
    // No-op.
  }

  @Override
  public void onGadgetStateChanged(GadgetStateChangedEvent event) {
    // No-op.
  }

  @Override
  public void onWaveletBlipCreated(WaveletBlipCreatedEvent event) {
    // No-op.
  }

  @Override
  public void onWaveletBlipRemoved(WaveletBlipRemovedEvent event) {
    // No-op.
  }

  @Override
  public void onWaveletCreated(WaveletCreatedEvent event) {
    // No-op.
  }

  @Override
  public void onWaveletFetched(WaveletFetchedEvent event) {
    // No-op.
  }

  @Override
  public void onWaveletParticipantsChanged(WaveletParticipantsChangedEvent event) {
    // No-op.
  }

  @Override
  public void onWaveletSelfAdded(WaveletSelfAddedEvent event) {
    // No-op.
  }

  @Override
  public void onWaveletSelfRemoved(WaveletSelfRemovedEvent event) {
    // No-op.
  }

  @Override
  public void onWaveletTagsChanged(WaveletTagsChangedEvent event) {
    // No-op.
  }

  @Override
  public void onWaveletTitleChanged(WaveletTitleChangedEvent event) {
    // No-op.
  }

  @Override
  public void onOperationError(OperationErrorEvent event) {
    // No-op.
  }

  /**
   * @return the display name of the robot.
   */
  protected abstract String getRobotName();

  /**
   * @return the URL of the robot's profile page.
   */
  protected abstract String getRobotProfilePageUrl();
}
