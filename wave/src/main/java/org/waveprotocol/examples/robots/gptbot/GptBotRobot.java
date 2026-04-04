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

package org.waveprotocol.examples.robots.gptbot;

import com.google.gson.Gson;
import com.google.wave.api.Blip;
import com.google.wave.api.OperationQueue;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ParticipantProfile;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.BlipSubmittedEvent;
import com.google.wave.api.event.Event;
import com.google.wave.api.event.WaveletBlipCreatedEvent;
import com.google.wave.api.impl.EventMessageBundle;
import com.google.wave.api.impl.GsonFactory;

import org.waveprotocol.wave.util.logging.Log;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Plain JSON-RPC robot engine for the gpt-bot example.
 */
public final class GptBotRobot {

  private static final Log LOG = Log.get(GptBotRobot.class);
  private static final String CAPABILITIES_PATH = "/_wave/capabilities.xml";
  private static final String PROFILE_PATH = "/_wave/robot/profile";
  private static final String RPC_PATH = "/_wave/robot/jsonrpc";
  private static final String XML_NS = "http://wave.google.com/extensions/robots/1.0";

  private final GptBotConfig config;
  private final GptBotReplyPlanner replyPlanner;
  private final SupaWaveClient apiClient;
  private final Gson gson;
  private final String capabilitiesXml;
  private final String profileJson;

  public GptBotRobot(GptBotConfig config, GptBotReplyPlanner replyPlanner,
      SupaWaveClient apiClient) {
    this.config = config;
    this.replyPlanner = replyPlanner;
    this.apiClient = apiClient;
    this.gson = new GsonFactory().create();
    this.capabilitiesXml = buildCapabilitiesXml();
    this.profileJson = gson.toJson(new ParticipantProfile(config.getParticipantId(),
        config.getRobotName(), config.getAvatarUrl(), config.getProfilePageUrl()));
  }

  public String getCapabilitiesPath() {
    return CAPABILITIES_PATH;
  }

  public String getProfilePath() {
    return PROFILE_PATH;
  }

  public String getRpcPath() {
    return RPC_PATH;
  }

  public String getCapabilitiesXml() {
    return capabilitiesXml;
  }

  public String getProfileJson() {
    return profileJson;
  }

  public String handleEventBundle(String jsonBody) {
    EventMessageBundle bundle;
    try {
      bundle = gson.fromJson(jsonBody, EventMessageBundle.class);
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("Invalid event bundle", e);
    }
    if (bundle == null || bundle.getWavelet() == null
        || bundle.getWavelet().getOperationQueue() == null) {
      throw new IllegalArgumentException("Invalid event bundle");
    }

    OperationQueue operationQueue = bundle.getWavelet().getOperationQueue();
    operationQueue.notifyRobotInformation(ProtocolVersion.DEFAULT, capabilitiesHash());
    processEvents(bundle);
    List<OperationRequest> operations = operationQueue.drainPendingOperations();
    return gson.toJson(operations, GsonFactory.OPERATION_REQUEST_LIST_TYPE);
  }

  private void processEvents(EventMessageBundle bundle) {
    List<Event> events = bundle.getEvents();
    if (events != null) {
      Set<String> handledBlipIds = new HashSet<String>();
      for (Event event : events) {
        switch (event.getType()) {
          case BLIP_SUBMITTED:
            handleBlip(BlipSubmittedEvent.as(event).getBlip(), event.getModifiedBy(),
                handledBlipIds);
            break;
          case WAVELET_BLIP_CREATED:
            handleBlip(WaveletBlipCreatedEvent.as(event).getNewBlip(), event.getModifiedBy(),
                handledBlipIds);
            break;
          default:
            break;
        }
      }
    }
  }

  private void handleBlip(Blip blip, String modifiedBy, Set<String> handledBlipIds) {
    if (blip != null && !shouldIgnore(modifiedBy) && shouldHandle(blip, handledBlipIds)) {
      String waveId = blip.getWaveId() == null ? "" : blip.getWaveId().toString();
      String waveletId = blip.getWaveletId() == null ? "" : blip.getWaveletId().toString();
      Optional<String> prompt = replyPlanner.extractPrompt(blip.getContent());
      if (prompt.isPresent()) {
        String waveContext = apiClient.fetchWaveContext(waveId, waveletId).orElse("");
        Optional<String> reply = replyPlanner.replyForPrompt(prompt.get(), waveContext);
        if (reply.isPresent()) {
          String replyText = reply.get();
          if (config.getReplyMode() == GptBotConfig.ReplyMode.ACTIVE) {
            if (!apiClient.appendReply(waveId, waveletId, blip.getBlipId(), replyText)) {
              LOG.warning("Active API reply delivery failed; not falling back to passive reply");
            }
          } else {
            blip.reply().append(replyText);
          }
        }
      }
    }
  }

  private boolean shouldHandle(Blip blip, Set<String> handledBlipIds) {
    String blipId = blip.getBlipId();
    boolean shouldHandle = true;
    if (blipId != null) {
      shouldHandle = handledBlipIds.add(blipId);
    }
    return shouldHandle;
  }

  private boolean shouldIgnore(String modifiedBy) {
    return modifiedBy != null && modifiedBy.equalsIgnoreCase(config.getParticipantId());
  }

  private String buildCapabilitiesXml() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<w:robot xmlns:w=\"").append(XML_NS).append("\">\n");
    xml.append("  <w:version>").append(capabilitiesHash()).append("</w:version>\n");
    xml.append("  <w:protocolversion>").append(ProtocolVersion.DEFAULT.getVersionString())
        .append("</w:protocolversion>\n");
    xml.append("  <w:capabilities>\n");
    xml.append(capabilityXml("BLIP_SUBMITTED", "SELF,SIBLINGS"));
    xml.append(capabilityXml("WAVELET_BLIP_CREATED", "SELF,SIBLINGS"));
    xml.append("  </w:capabilities>\n");
    xml.append("</w:robot>\n");
    return xml.toString();
  }

  private String capabilityXml(String name, String context) {
    return "    <w:capability name=\"" + name + "\" context=\"" + context + "\"/>\n";
  }

  private String capabilitiesHash() {
    String payload = String.join("|",
        config.getRobotName(),
        "BLIP_SUBMITTED:SELF,SIBLINGS",
        "WAVELET_BLIP_CREATED:SELF,SIBLINGS");
    String hash = "sha256:";
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      hash = hash + toHex(bytes);
    } catch (NoSuchAlgorithmException e) {
      LOG.warning("Unable to compute capability hash", e);
    }
    return hash;
  }

  private static String toHex(byte[] bytes) {
    StringBuilder hex = new StringBuilder();
    for (byte value : bytes) {
      hex.append(String.format(Locale.ROOT, "%02x", value));
    }
    return hex.toString();
  }

}
