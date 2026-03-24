/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.attachment.AttachmentProto.AttachmentsResponse;
import org.waveprotocol.box.attachment.proto.AttachmentMetadataProtoImpl;
import org.waveprotocol.box.server.attachment.AttachmentService;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.persistence.AttachmentUtil;
import org.waveprotocol.box.server.rpc.ProtoSerializer.SerializationException;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.wave.media.model.AttachmentId;
import org.waveprotocol.wave.model.id.InvalidIdException;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

@SuppressWarnings("serial")
public class AttachmentInfoServlet extends HttpServlet {
    public static final String ATTACHMENTS_INFO_URL = "/attachmentsInfo";
    private static final Log LOG = Log.get(AttachmentInfoServlet.class);
    private final AttachmentService service;
    private final WaveletProvider waveletProvider;
    private final SessionManager sessionManager;
    private final ProtoSerializer serializer;

    @Inject
    private AttachmentInfoServlet(AttachmentService service, WaveletProvider waveletProvider,
                                  SessionManager sessionManager, ProtoSerializer serializer) {
        this.service = service;
        this.waveletProvider = waveletProvider;
        this.sessionManager = sessionManager;
        this.serializer = serializer;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        List<AttachmentId> attachmentIds = getIdsFromRequest(request);
        if (attachmentIds == null) { // param missing
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        if (attachmentIds.isEmpty()) { // param present but invalid or yielded no valid ids
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(request, false));
        if (user == null) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        AttachmentsResponse.Builder attachmentsResponse = AttachmentsResponse.newBuilder();
        boolean anyAuthorized = processIds(user, attachmentIds, attachmentsResponse);
        if (!anyAuthorized) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        String info;
        try {
            info = serializer.toJson(attachmentsResponse.build()).toString();
        }
        catch (SerializationException ex) {
            LOG.log(Level.SEVERE, "Attachments info serialize", ex);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            return;
        }
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json; charset=utf8");
        response.setHeader("Cache-Control", "no-store");
      try (var w = response.getWriter()) { w.append(info); w.flush(); }
        LOG.info("Fetched info for " + attachmentIds.size() + " attachments");
    }

    private static List<AttachmentId> getIdsFromRequest(HttpServletRequest request) {
        String par = request.getParameter("attachmentIds");
        if (par == null) {
            return null;
        }
        // Basic input hardening: size and composition limits
        if (par.length() > 4096) {
            LOG.warning("attachmentIds parameter too long; rejecting");
            return new ArrayList<>();
        }
        String[] raw = par.split(",", -1);
        if (raw.length == 0 || raw.length > 100) {
            LOG.warning("attachmentIds count invalid: " + raw.length);
            return new ArrayList<>();
        }
        java.util.regex.Pattern SAFE =
                java.util.regex.Pattern.compile("^[A-Za-z0-9._:+\\-=/]{1," + "256}$");
        List<AttachmentId> ids = new ArrayList<>();
        for (String r : raw) {
            String trimmed = r == null ? "" : r.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > 256 || !SAFE.matcher(trimmed).matches()) {
                LOG.fine("Skipping invalid attachmentId token: " + mask(trimmed));
                continue;
            }
            try {
                ids.add(AttachmentId.deserialise(trimmed));
            }
            catch (InvalidIdException ex) {
                LOG.log(Level.FINE, "Deserialize attachment Id (masked) " + mask(trimmed), ex);
            }
        }
        return ids;
    }

    // Visible for tests within package
    boolean processIds(ParticipantId user, List<AttachmentId> attachmentIds,
                       AttachmentsResponse.Builder out) {
        boolean anyAuthorized = false;
        for (AttachmentId id : attachmentIds) {
            if (id == null) {
                LOG.fine("Null attachmentId token after parsing; skipping");
                continue;
            }
            AttachmentMetadata metadata = null;
            try {
                metadata = service.getMetadata(id);
            }
            catch (Exception ex) {
                LOG.log(Level.WARNING,
                        "Failed to fetch metadata for attachmentId=" + maskAttachmentId(id), ex);
            }
            if (metadata != null) {
                boolean isAuthorized = false;
                WaveletName waveletName = AttachmentUtil.waveRef2WaveletName(metadata.getWaveRef());
                try {
                    isAuthorized = waveletProvider.checkAccessPermission(waveletName, user);
                }
                catch (WaveServerException e) {
                    LOG.warning("Problem authorizing user=" + maskParticipant(user) + " for " +
                            "resource", e);
                    isAuthorized = false;
                }
                if (isAuthorized) {
                    out.addAttachment(new AttachmentMetadataProtoImpl(metadata).getPB());
                    anyAuthorized = true;
                } else {
                    String reason = (user == null) ? "unauthenticated" : "providerDenied";
                    if (LOG.isFineLoggable()) {
                        LOG.fine("Attachment access denied: reason=" + reason +
                                ", attachmentId=" + maskAttachmentId(id) +
                                ", user=" + maskParticipant(user));
                    }
                }
            }
            else {
                LOG.fine("No metadata for attachmentId=" + maskAttachmentId(id) + "; skipping");
            }
        }
        return anyAuthorized;
    }

    private static String maskAttachmentId(AttachmentId id) {
        return (id == null) ? "null" : mask(id.toString());
    }

    private static String maskParticipant(ParticipantId p) {
        if (p == null) {
            return "null";
        }
        String addr = p.getAddress();
        int at = (addr != null) ? addr.indexOf('@') : -1;
        String local = (at > 0) ? addr.substring(0, at) : (addr == null ? "" : addr);
        String domain = (at > 0) ? addr.substring(at) : ""; // include '@'
        return mask(local) + domain;
    }

    private static String mask(String s) {
        if (s == null) {
            return "null";
        }
        int len = s.length();
        if (len <= 6) {
            return "***"; // short ids fully masked
        }
        return s.substring(0, 3) + "***" + s.substring(len - 2);
    }
}
