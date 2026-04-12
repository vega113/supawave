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
import com.google.wave.api.Annotation;
import com.google.wave.api.Annotations;
import com.google.wave.api.Blip;
import com.google.wave.api.BlipThread;
import com.google.wave.api.OperationQueue;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ParticipantProfile;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.BlipEditingDoneEvent;
import com.google.wave.api.event.DocumentChangedEvent;
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
    String hash = capabilitiesHash();
    LOG.info("handleEventBundle: sending capabilities hash=" + hash);
    operationQueue.notifyRobotInformation(ProtocolVersion.DEFAULT, hash);
    processEvents(bundle, bundle.getRpcServerUrl());
    List<OperationRequest> operations = operationQueue.drainPendingOperations();
    LOG.info("handleEventBundle: returning " + operations.size() + " operation(s)");
    return gson.toJson(operations, GsonFactory.OPERATION_REQUEST_LIST_TYPE);
  }

  private void processEvents(EventMessageBundle bundle, String rpcServerUrl) {
    List<Event> events = bundle.getEvents();
    if (events == null) {
      LOG.info("processEvents: events list is null — nothing to process");
      return;
    }
    LOG.info("processEvents: received " + events.size() + " event(s)");
    Set<String> handledBlipIds = new HashSet<String>();
    for (Event event : events) {
      if (LOG.isFineLoggable()) {
        LOG.fine("processEvents: event type=" + event.getType() + " modifiedBy=" + event.getModifiedBy());
      }
      switch (event.getType()) {
        case BLIP_EDITING_DONE:
          // Primary trigger: fired when all user/d/ editing sessions are closed.
          handleBlip(BlipEditingDoneEvent.as(event).getBlip(), event.getModifiedBy(),
              handledBlipIds, rpcServerUrl);
          break;
        case DOCUMENT_CHANGED: {
          // Fallback for servers that haven't registered BLIP_EDITING_DONE yet.
          Blip changedBlip = DocumentChangedEvent.as(event).getBlip();
          if (changedBlip != null) {
            if (isBlipBeingEdited(changedBlip)) {
              LOG.fine("processEvents: DOCUMENT_CHANGED blipId=" + changedBlip.getBlipId()
                  + " still being edited, skipping");
            } else {
              handleBlip(changedBlip, event.getModifiedBy(), handledBlipIds, rpcServerUrl);
            }
          } else {
            LOG.info("processEvents: DOCUMENT_CHANGED blip is null — skipping");
          }
          break;
        }
        case WAVELET_BLIP_CREATED:
          handleBlip(WaveletBlipCreatedEvent.as(event).getNewBlip(), event.getModifiedBy(),
              handledBlipIds, rpcServerUrl);
          break;
        default:
          LOG.info("processEvents: unhandled event type=" + event.getType());
          break;
      }
    }
  }

  private void handleBlip(Blip blip, String modifiedBy, Set<String> handledBlipIds,
      String rpcServerUrl) {
    if (blip == null) {
      LOG.info("handleBlip: blip is null — skipping");
      return;
    }
    String blipId = blip.getBlipId();
    String content = blip.getContent();
    LOG.info("handleBlip: blipId=" + blipId + " modifiedBy=" + modifiedBy
        + " contentLen=" + (content == null ? 0 : content.length()));
    if (LOG.isFineLoggable()) {
      LOG.fine("handleBlip: blipId=" + blipId + " contentLen=" + (content == null ? 0 : content.length()));
    }

    if (shouldIgnore(modifiedBy)) {
      LOG.info("handleBlip: ignoring — modifiedBy=" + modifiedBy
          + " matches bot participantId=" + config.getParticipantId());
      return;
    }
    if (!shouldHandle(blip, handledBlipIds)) {
      LOG.info("handleBlip: already handled blipId=" + blipId + " — skipping duplicate");
      return;
    }
    if (isBlipBeingEdited(blip)) {
      return;
    }

    String waveId = blip.getWaveId() == null ? "" : blip.getWaveId().toString();
    String waveletId = blip.getWaveletId() == null ? "" : blip.getWaveletId().toString();
    LOG.info("handleBlip: waveId=" + waveId + " waveletId=" + waveletId);

    Optional<String> prompt = replyPlanner.extractPrompt(content);
    if (!prompt.isPresent() && isBotThreadReply(blip)) {
      // No @mention but this is a reply in a bot thread — use the full content as the prompt.
      String stripped = content == null ? "" : content.strip();
      if (!stripped.isEmpty()) {
        prompt = Optional.of(stripped);
        LOG.info("handleBlip: bot-thread reply fallback triggered, promptLen=" + stripped.length());
      }
    }
    if (!prompt.isPresent()) {
      LOG.info("handleBlip: no bot mention and no parent-blip fallback — no reply generated");
      return;
    }
    LOG.info("handleBlip: mention detected blipId=" + blipId
        + " promptLen=" + prompt.get().length());
    if (LOG.isFineLoggable()) {
      LOG.fine("handleBlip: blipId=" + blipId + " promptLen=" + prompt.get().length());
    }

    try {
      String waveContext = apiClient.fetchWaveContext(waveId, waveletId).orElse("");
      LOG.info("handleBlip: waveContextLen=" + waveContext.length());

      if (config.getReplyMode() == GptBotConfig.ReplyMode.ACTIVE_STREAM) {
        LOG.info("handleBlip: ACTIVE_STREAM mode — starting streamed reply for blipId=" + blipId);
        StreamingReplyWriter writer = new StreamingReplyWriter(apiClient, waveId, waveletId,
            blipId, rpcServerUrl);
        final boolean[] started = {false};
        Optional<String> reply = replyPlanner.replyForPromptStreaming(prompt.get(), waveContext, waveId,
            accumulatedText -> {
              if (!started[0] && accumulatedText != null && !accumulatedText.isEmpty()) {
                started[0] = writer.start(accumulatedText);
                if (!started[0]) {
                  LOG.warning("handleBlip: ACTIVE_STREAM createReply failed for blipId=" + blipId);
                }
                return;
              }
              if (!started[0]) {
                return;
              }
              if (!writer.update(accumulatedText)) {
                LOG.warning("handleBlip: ACTIVE_STREAM update failed for blipId=" + blipId);
              }
            });
        if (!reply.isPresent()) {
          LOG.warning("handleBlip: ACTIVE_STREAM reply generation returned empty for blipId=" + blipId);
          return;
        }
        String replyText = reply.get();
        if (!started[0] && !replyText.isEmpty() && !writer.start(replyText)) {
          LOG.warning("handleBlip: ACTIVE_STREAM createReply failed for blipId=" + blipId);
          return;
        }
        if (!writer.finish(replyText)) {
          LOG.warning("handleBlip: ACTIVE_STREAM final update failed for blipId=" + blipId);
        }
        LOG.info("handleBlip: ACTIVE_STREAM reply completed for blipId=" + blipId);
        return;
      }

      Optional<String> reply = replyPlanner.replyForPrompt(prompt.get(), waveContext, waveId);
      if (!reply.isPresent()) {
        LOG.warning("handleBlip: replyForPrompt returned empty — LLM may have failed");
        return;
      }
      String replyText = reply.get();
      LOG.info("handleBlip: reply generated, replyLen=" + replyText.length() + " for blipId=" + blipId);
      if (LOG.isFineLoggable()) {
        LOG.fine("handleBlip: blipId=" + blipId + " replyLen=" + replyText.length());
      }

      if (config.getReplyMode() == GptBotConfig.ReplyMode.ACTIVE) {
        LOG.info("handleBlip: ACTIVE mode — calling appendReply for blipId=" + blipId);
        if (!apiClient.appendReply(waveId, waveletId, blipId, replyText, rpcServerUrl)) {
          LOG.warning("handleBlip: ACTIVE appendReply failed for blipId=" + blipId
              + " waveId=" + waveId + " waveletId=" + waveletId);
        } else {
          LOG.info("handleBlip: ACTIVE appendReply succeeded for blipId=" + blipId);
        }
      } else {
        blip.getWavelet().reply("\n" + replyText);
        LOG.info("handleBlip: PASSIVE reply appended for blipId=" + blipId);
      }
    } catch (Exception e) {
      LOG.warning("handleBlip: exception during reply generation for blipId=" + blipId, e);
    }
  }

  /**
   * Returns true if {@code blip} is a reply in a thread where the bot previously participated,
   * so that a non-@mention follow-up message should be treated as a prompt.
   */
  private boolean isBotThreadReply(Blip blip) {
    // Check if the immediate parent blip has the bot as a contributor (direct reply or inline thread).
    Blip parent = blip.getParentBlip();
    if (parent != null && hasBotContributor(parent)) {
      return true;
    }
    // Check if any sibling blip in the same non-root thread has the bot as a contributor (inline
    // reply threads where the bot's reply and the user's follow-up share the same containing
    // thread). The root thread (id="" or null) is excluded: the bot now posts there too, and
    // treating every root-thread blip as a follow-up would over-trigger the bot.
    BlipThread thread = blip.getThread();
    if (thread != null && thread.getId() != null && !thread.getId().isEmpty()) {
      for (String siblingId : thread.getBlipIds()) {
        if (siblingId != null && !siblingId.equals(blip.getBlipId())) {
          Blip sibling = blip.getWavelet() != null ? blip.getWavelet().getBlip(siblingId) : null;
          if (sibling != null && hasBotContributor(sibling)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Returns true if the bot is listed as a contributor to the blip.
   * This covers cases where the bot authored the blip or contributed to edits,
   * ensuring that follow-up replies in bot-threaded conversations are treated as prompts.
   */
  private boolean hasBotContributor(Blip blip) {
    List<String> contributors = blip.getContributors();
    return contributors != null && contributors.stream()
        .anyMatch(c -> c != null && c.equalsIgnoreCase(config.getParticipantId()));
  }


  /**
   * Editing sessions open for longer than this are considered stale (client crashed/disconnected
   * without closing the session). Stale sessions are treated as closed so bots are not blocked.
   */
  static final long STALE_EDITING_THRESHOLD_MS = 30 * 60 * 1000L; // 30 minutes

  /**
   * Returns true if any participant is CURRENTLY actively editing this blip.
   *
   * In Wave's document model, when a user is editing a blip, a "user/d/{sessionId}"
   * annotation is added whose VALUE is "{userId},{startTimeMs},{endTimeMs}".
   * While editing is in progress, {endTimeMs} is EMPTY (trailing comma).
   * When editing ends (user submits or clicks away), {endTimeMs} is set to a non-empty timestamp.
   *
   * Note: "user/d/" annotations are PERMANENT — they remain on the blip after editing ends.
   * The presence of the annotation alone does NOT indicate ongoing editing.
   * Only annotations with an empty end timestamp indicate active editing.
   *
   * Sessions whose startTimeMs is older than {@link #STALE_EDITING_THRESHOLD_MS} are treated as
   * closed even if endTimeMs is empty. This handles the case where the client crashed or the
   * browser tab was force-closed mid-edit without sending a cleanup delta.
   */
  private boolean isBlipBeingEdited(Blip blip) {
    if (blip == null) return false;
    Annotations annotations = blip.getAnnotations();
    if (annotations == null) return false;
    long now = System.currentTimeMillis();
    for (Annotation annotation : annotations) {
      String name = annotation.getName();
      if (name == null || !name.startsWith("user/d/")) continue;
      // Value format: "{userId},{startTimeMs},{endTimeMs}"
      // Editing in progress = endTimeMs is empty (value ends with comma)
      String value = annotation.getValue();
      if (value == null) continue;
      String[] parts = value.split(",", -1);  // -1 keeps trailing empty strings
      if (parts.length >= 3 && parts[parts.length - 1].isEmpty()) {
        // endTimeMs is empty — check if session is stale (crashed client)
        boolean stale = false;
        if (parts.length >= 2 && !parts[1].isEmpty()) {
          try {
            double parsed = Double.parseDouble(parts[1]);
            if (!Double.isFinite(parsed)) throw new NumberFormatException("Non-finite timestamp");
            long startTimeMs = (long) parsed;
            stale = (now - startTimeMs > STALE_EDITING_THRESHOLD_MS);
          } catch (NumberFormatException e) {
            // Cannot parse startTimeMs — treat as active (safe default)
          }
        }
        if (stale) {
          if (LOG.isFineLoggable()) {
            LOG.fine("isBlipBeingEdited: blipId=" + blip.getBlipId()
                + " annotation=" + name + " session is stale (exceeded threshold), ignoring");
          }
          continue; // Skip stale session
        }
        if (LOG.isFineLoggable()) {
          LOG.fine("isBlipBeingEdited: blipId=" + blip.getBlipId()
              + " annotation=" + name + " active edit session (no endTimeMs)");
        }
        return true;
      }
    }
    return false;
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

  // To force the server to pick up new capabilities without restart:
  // use the Admin panel → Robots → Test button, or POST /api/robots/gpt-bot@supawave.ai/verify
  // The verify endpoint fetches /_wave/capabilities.xml and updates MongoDB.
  private String buildCapabilitiesXml() {
    StringBuilder xml = new StringBuilder();
    xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    xml.append("<w:robot xmlns:w=\"").append(XML_NS).append("\">\n");
    xml.append("  <w:version>").append(capabilitiesHash()).append("</w:version>\n");
    xml.append("  <w:protocolversion>").append(ProtocolVersion.DEFAULT.getVersionString())
        .append("</w:protocolversion>\n");
    xml.append("  <w:capabilities>\n");
    xml.append(capabilityXml("BLIP_EDITING_DONE", "SELF,SIBLINGS,PARENT"));
    xml.append(capabilityXml("DOCUMENT_CHANGED", "SELF,SIBLINGS,PARENT"));
    xml.append(capabilityXml("WAVELET_BLIP_CREATED", "SELF,SIBLINGS,PARENT"));
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
        "BLIP_EDITING_DONE:SELF,SIBLINGS,PARENT",
        "DOCUMENT_CHANGED:SELF,SIBLINGS,PARENT",
        "WAVELET_BLIP_CREATED:SELF,SIBLINGS,PARENT");
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
