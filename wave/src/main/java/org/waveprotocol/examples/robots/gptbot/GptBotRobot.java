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
import com.google.wave.api.event.DocumentChangedEvent;
import com.google.wave.api.event.Event;
import com.google.wave.api.event.WaveletBlipCreatedEvent;
import com.google.wave.api.impl.EventMessageBundle;
import com.google.wave.api.impl.GsonFactory;

import org.waveprotocol.wave.util.logging.Log;

import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    EventMessageBundle bundle = gson.fromJson(jsonBody, EventMessageBundle.class);
    if (bundle == null || bundle.getWavelet() == null) {
      throw new IllegalArgumentException("Invalid event bundle");
    }

    OperationQueue operationQueue = operationQueue(bundle);
    operationQueue.notifyRobotInformation(ProtocolVersion.DEFAULT, capabilitiesHash());
    processEvents(bundle);
    List<OperationRequest> operations = operationQueue.getPendingOperations();
    return gson.toJson(operations, GsonFactory.OPERATION_REQUEST_LIST_TYPE);
  }

  private void processEvents(EventMessageBundle bundle) {
    List<Event> events = bundle.getEvents();
    if (events != null) {
      for (Event event : events) {
        switch (event.getType()) {
          case BLIP_SUBMITTED:
            handleBlip(BlipSubmittedEvent.as(event).getBlip(), event.getModifiedBy());
            break;
          case DOCUMENT_CHANGED:
            handleBlip(DocumentChangedEvent.as(event).getBlip(), event.getModifiedBy());
            break;
          case WAVELET_BLIP_CREATED:
            handleBlip(WaveletBlipCreatedEvent.as(event).getNewBlip(), event.getModifiedBy());
            break;
          default:
            break;
        }
      }
    }
  }

  private void handleBlip(Blip blip, String modifiedBy) {
    if (blip != null && !shouldIgnore(modifiedBy)) {
      String waveId = blip.getWaveId() == null ? "" : blip.getWaveId().toString();
      String waveletId = blip.getWaveletId() == null ? "" : blip.getWaveletId().toString();
      String waveContext = apiClient.fetchWaveContext(waveId, waveletId).orElse("");
      Optional<String> reply = replyPlanner.replyFor(blip.getContent(), waveContext);
      if (reply.isPresent()) {
        String replyText = reply.get();
        boolean appended = false;
        if (config.getReplyMode() == GptBotConfig.ReplyMode.ACTIVE) {
          appended = apiClient.appendReply(waveId, waveletId, blip.getBlipId(), replyText);
          if (!appended) {
            LOG.warning("Falling back to passive callback reply after active API delivery failed");
          }
        }
        if (!appended) {
          blip.reply().append(replyText);
        }
      }
    }
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
    xml.append(capabilityXml("DOCUMENT_CHANGED", "SELF,SIBLINGS"));
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
        "DOCUMENT_CHANGED:SELF,SIBLINGS",
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

  private static OperationQueue operationQueue(EventMessageBundle bundle) {
    try {
      Field field = bundle.getWavelet().getClass().getDeclaredField("operationQueue");
      field.setAccessible(true);
      return (OperationQueue) field.get(bundle.getWavelet());
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("Unable to access the operation queue", e);
    }
  }
}
