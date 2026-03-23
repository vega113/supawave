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

import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.impl.WaveletData;
import org.waveprotocol.box.server.robots.agent.LocalOperationSubmitter;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * In-JVM replacement for {@link WaveService} that submits operations directly
 * to {@link LocalOperationSubmitter} instead of making HTTP/OAuth round-trips.
 *
 * <p>This class maintains API compatibility with the methods that
 * {@link AbstractRobot} and the built-in agents use: {@code newWave},
 * {@code submit}, {@code fetchWavelet}, {@code blindWavelet}, {@code search}.
 *
 * <p>OAuth-related methods ({@code setupOAuth}, {@code getConsumerDataMap},
 * {@code hasConsumerData}, {@code validateOAuthRequest}) are no-ops or return
 * empty results since in-JVM bots have no need for OAuth.
 */
public class LocalWaveService {

  /** The wire protocol version. */
  public static final ProtocolVersion PROTOCOL_VERSION = ProtocolVersion.DEFAULT;

  private static final Logger LOG = Logger.getLogger(LocalWaveService.class.getName());

  private final LocalOperationSubmitter submitter;
  private final ParticipantId robotId;
  private final String version;

  /**
   * Creates a new LocalWaveService.
   *
   * @param submitter the in-JVM operation submitter.
   * @param robotId the participant identity of the robot using this service.
   * @param version the robot capabilities version hash.
   */
  public LocalWaveService(LocalOperationSubmitter submitter, ParticipantId robotId, String version) {
    this.submitter = submitter;
    this.robotId = robotId;
    this.version = version;
  }

  // ---- OAuth no-ops ----

  /**
   * No-op for in-JVM bots.
   */
  public void setupOAuth(String consumerKey, String consumerSecret, String rpcServerUrl) {
    // No-op: in-JVM bots do not need OAuth.
  }

  /**
   * Returns an empty map (no OAuth consumer data for in-JVM bots).
   */
  public Map<String, Object> getConsumerDataMap() {
    return Collections.emptyMap();
  }

  /**
   * Always returns false (no OAuth consumer data for in-JVM bots).
   */
  public boolean hasConsumerData(String rpcServerUrl) {
    return false;
  }

  // ---- Core API methods ----

  /**
   * Submits the pending operations associated with this {@link Wavelet}.
   *
   * @param wavelet the bundle that contains the operations to be submitted.
   * @param rpcServerUrl ignored for local submission.
   * @return a list of {@link JsonRpcResponse} for the submitted operations.
   * @throws IOException if operation execution fails.
   */
  public List<JsonRpcResponse> submit(Wavelet wavelet, String rpcServerUrl) throws IOException {
    OperationQueue opQueue = wavelet.getOperationQueue();
    opQueue.notifyRobotInformation(PROTOCOL_VERSION, version);
    List<OperationRequest> operations = new ArrayList<>(opQueue.getPendingOperations());
    opQueue.clear();

    List<JsonRpcResponse> responses = submitter.submitOperations(operations, robotId);
    // Remove the response for the prepended robot.notify operation, matching WaveService behavior.
    if (!responses.isEmpty()) {
      responses.remove(0);
    }
    return responses;
  }

  /**
   * Returns an empty/blind stub of a wavelet.
   */
  public Wavelet blindWavelet(WaveId waveId, WaveletId waveletId) {
    return blindWavelet(waveId, waveletId, null);
  }

  public Wavelet blindWavelet(WaveId waveId, WaveletId waveletId, String proxyForId) {
    return blindWavelet(waveId, waveletId, proxyForId, new HashMap<String, Blip>());
  }

  public Wavelet blindWavelet(WaveId waveId, WaveletId waveletId, String proxyForId,
      Map<String, Blip> blips) {
    return blindWavelet(waveId, waveletId, proxyForId, blips, new HashMap<String, BlipThread>());
  }

  public Wavelet blindWavelet(WaveId waveId, WaveletId waveletId, String proxyForId,
      Map<String, Blip> blips, Map<String, BlipThread> threads) {
    Util.checkIsValidProxyForId(proxyForId);
    Map<String, String> roles = new HashMap<String, String>();
    return new Wavelet(waveId, waveletId, null,
        new BlipThread("", -1, new ArrayList<String>(), blips), Collections.<String>emptySet(),
        roles, blips, threads, new OperationQueue(proxyForId));
  }

  /**
   * Creates a new wave.
   */
  public Wavelet newWave(String domain, Set<String> participants) {
    return newWave(domain, participants, null);
  }

  public Wavelet newWave(String domain, Set<String> participants, String proxyForId) {
    return newWave(domain, participants, "", proxyForId);
  }

  public Wavelet newWave(String domain, Set<String> participants, String msg, String proxyForId) {
    Util.checkIsValidProxyForId(proxyForId);
    return new OperationQueue(proxyForId).createWavelet(domain, participants, msg);
  }

  public Wavelet newWave(String domain, Set<String> participants, String msg, String proxyForId,
      String rpcServerUrl) throws IOException, InvalidIdException {
    Util.checkIsValidProxyForId(proxyForId);
    OperationQueue opQueue = new OperationQueue(proxyForId);
    Wavelet newWavelet = opQueue.createWavelet(domain, participants, msg);

    if (rpcServerUrl != null && !rpcServerUrl.isEmpty()) {
      // submit() already strips the prepended robot.notify response, so the
      // wavelet.create result is at index 0 in the returned list.
      List<JsonRpcResponse> responses = this.submit(newWavelet, rpcServerUrl);
      if (responses.isEmpty()) {
        throw new IOException("No response received for wavelet creation");
      }
      JsonRpcResponse response = responses.get(0);
      if (response.isError()) {
        throw new IOException(response.getErrorMessage());
      }
      WaveId waveId = ApiIdSerializer.instance().deserialiseWaveId(
          (String) response.getData().get(ParamsProperty.WAVE_ID));
      WaveletId waveletId = ApiIdSerializer.instance().deserialiseWaveletId(
          (String) response.getData().get(ParamsProperty.WAVELET_ID));
      String rootBlipId = (String) response.getData().get(ParamsProperty.BLIP_ID);

      Map<String, Blip> blips = new HashMap<String, Blip>();
      Map<String, BlipThread> threads = new HashMap<String, BlipThread>();
      Map<String, String> roles = new HashMap<String, String>();

      List<String> blipIds = new ArrayList<String>();
      blipIds.add(rootBlipId);
      BlipThread rootThread = new BlipThread("", -1, blipIds, blips);

      newWavelet = new Wavelet(waveId, waveletId, rootBlipId, rootThread, participants,
          roles, blips, threads, opQueue);
      blips.put(rootBlipId, new Blip(rootBlipId, "", null, "", newWavelet));
    }
    return newWavelet;
  }

  /**
   * Fetches a wavelet.
   */
  public Wavelet fetchWavelet(WaveId waveId, WaveletId waveletId, String rpcServerUrl)
      throws IOException {
    return fetchWavelet(waveId, waveletId, null, rpcServerUrl);
  }

  @SuppressWarnings("unchecked")
  public Wavelet fetchWavelet(WaveId waveId, WaveletId waveletId, String proxyForId,
      String rpcServerUrl) throws IOException {
    Util.checkIsValidProxyForId(proxyForId);
    OperationQueue opQueue = new OperationQueue(proxyForId);
    opQueue.fetchWavelet(waveId, waveletId);

    Map<ParamsProperty, Object> responseData = makeSingleOperationRpc(opQueue, rpcServerUrl);

    // Deserialize wavelet.
    opQueue.clear();
    WaveletData waveletData = (WaveletData) responseData.get(ParamsProperty.WAVELET_DATA);
    Map<String, Blip> blips = new HashMap<String, Blip>();
    Map<String, BlipThread> threads = new HashMap<String, BlipThread>();
    Wavelet wavelet = Wavelet.deserialize(opQueue, blips, threads, waveletData);

    // Deserialize threads.
    Map<String, BlipThread> tempThreads =
        (Map<String, BlipThread>) responseData.get(ParamsProperty.THREADS);
    if (tempThreads != null) {
      for (Map.Entry<String, BlipThread> entry : tempThreads.entrySet()) {
        BlipThread thread = entry.getValue();
        threads.put(entry.getKey(),
            new BlipThread(thread.getId(), thread.getLocation(), thread.getBlipIds(), blips));
      }
    }

    // Deserialize blips.
    Map<String, BlipData> blipDatas =
        (Map<String, BlipData>) responseData.get(ParamsProperty.BLIPS);
    if (blipDatas != null) {
      for (Map.Entry<String, BlipData> entry : blipDatas.entrySet()) {
        blips.put(entry.getKey(), Blip.deserialize(opQueue, wavelet, entry.getValue()));
      }
    }

    return wavelet;
  }

  /**
   * Searches for waves.
   */
  public SearchResult search(String query, Integer index, Integer numResults, String rpcServerUrl)
      throws IOException {
    OperationQueue opQueue = new OperationQueue();
    opQueue.search(query, index, numResults);
    Map<ParamsProperty, Object> response = makeSingleOperationRpc(opQueue, rpcServerUrl);
    return (SearchResult) response.get(ParamsProperty.SEARCH_RESULTS);
  }

  /**
   * Submits a single-operation queue and returns the first response's data.
   */
  private Map<ParamsProperty, Object> makeSingleOperationRpc(
      OperationQueue opQueue, String rpcServerUrl) throws IOException {
    opQueue.notifyRobotInformation(PROTOCOL_VERSION, version);
    List<OperationRequest> operations = new ArrayList<>(opQueue.getPendingOperations());
    opQueue.clear();

    List<JsonRpcResponse> responses = submitter.submitOperations(operations, robotId);
    // Skip the robot.notify response (first element).
    if (responses.size() < 2) {
      throw new IOException("Expected at least 2 responses (notify + operation), got " +
          responses.size());
    }
    JsonRpcResponse response = responses.get(1);
    if (response.isError()) {
      throw new IOException(response.getErrorMessage());
    }
    return response.getData();
  }
}
