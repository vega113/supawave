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
package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.wave.api.ParticipantProfile;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.waveprotocol.box.profile.ProfilesProto.ProfileResponse;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSessions;
import org.waveprotocol.box.server.robots.operations.FetchProfilesService.ProfilesFetcher;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

import java.io.IOException;

/**
 * A servlet that enables the client to fetch user profiles. Typically hosted on
 * {@code /profile}.
 *
 * <p>Valid request: {@code GET /profile/?addresses=user1@example.com,user2@example.com}
 * (URL-encoded). Returns JSON in the protobuf-JSON format used by the websocket
 * interface.
 */
@SuppressWarnings("serial")
@Singleton
public final class FetchProfilesServlet extends HttpServlet {
  private static final Log LOG = Log.get(FetchProfilesServlet.class);

  private final SessionManager sessionManager;
  private final ProfilesFetcher profilesFetcher;
  private final ProtoSerializer serializer;

  @Inject
  public FetchProfilesServlet(SessionManager sessionManager,
      ProfilesFetcher profilesFetcher, ProtoSerializer serializer) {
    this.sessionManager = sessionManager;
    this.profilesFetcher = profilesFetcher;
    this.serializer = serializer;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
    ParticipantId user = sessionManager.getLoggedInUser(WebSessions.from(req, false));
    if (user == null) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    String addressesParam = req.getParameter("addresses");
    if (addressesParam == null || addressesParam.isEmpty()) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing addresses parameter");
      return;
    }

    String[] addresses = addressesParam.split(",");

    ProfileResponse.Builder builder = ProfileResponse.newBuilder();
    boolean hasProfiles = false;
    for (String address : addresses) {
      String trimmed = address.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      ParticipantProfile profile = profilesFetcher.fetchProfile(trimmed);
      ProfileResponse.FetchedProfile.Builder profileBuilder =
          ProfileResponse.FetchedProfile.newBuilder()
              .setAddress(profile.getAddress())
              .setName(profile.getName())
              .setImageUrl(profile.getImageUrl())
              .setProfileUrl(profile.getProfileUrl());
      if (profile.getFirstName() != null) {
        profileBuilder.setFirstName(profile.getFirstName());
      }
      if (profile.getLastName() != null) {
        profileBuilder.setLastName(profile.getLastName());
      }
      if (profile.getBio() != null) {
        profileBuilder.setBio(profile.getBio());
      }
      if (profile.getLastSeenTime() != 0) {
        profileBuilder.setLastSeenTime(profile.getLastSeenTime());
      }
      builder.addProfiles(profileBuilder.build());
      hasProfiles = true;
    }

    if (!hasProfiles) {
      response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No valid addresses provided");
      return;
    }

    ProfileResponse profileResponse = builder.build();
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType("application/json");
    response.setHeader("Cache-Control", "no-store");
    try {
      response.getWriter().append(serializer.toJson(profileResponse).toString());
    } catch (ProtoSerializer.SerializationException e) {
      throw new IOException(e);
    }
  }
}
