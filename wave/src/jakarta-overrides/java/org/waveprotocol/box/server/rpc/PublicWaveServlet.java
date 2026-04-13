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
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.waveprotocol.box.common.Snippets;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.box.server.rpc.PublicWaveBlipRenderer.BlipInfo;
import org.waveprotocol.box.server.waveserver.AnalyticsRecorder;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Serves public wave pages at {@code /wave/{waveId}} with SEO-optimized
 * server-side rendered HTML including Open Graph, Twitter Card, and JSON-LD
 * structured data meta tags. Only waves that include the shared domain
 * participant (i.e. public waves) are accessible through this endpoint.
 *
 * <p>Authenticated users who visit this URL are also served the SSR page
 * (they can follow the link into the full client). Unauthenticated visitors
 * get the same SEO-friendly page which includes a call-to-action to sign in.
 */
@Singleton
public final class PublicWaveServlet extends HttpServlet {

  private static final Log LOG = Log.get(PublicWaveServlet.class);
  private static final int MAX_DESCRIPTION_LENGTH = 200;

  private final String siteUrl;
  private final String waveDomain;
  private final WaveletProvider waveletProvider;
  private final ParticipantId sharedDomainParticipant;
  private final AnalyticsRecorder analyticsRecorder;

  @Inject
  public PublicWaveServlet(
      Config config,
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain,
      WaveletProvider waveletProvider,
      AnalyticsRecorder analyticsRecorder) {
    this.siteUrl = RobotsServlet.resolveSiteUrl(config);
    this.waveDomain = waveDomain;
    this.waveletProvider = waveletProvider;
    this.sharedDomainParticipant =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
    this.analyticsRecorder = analyticsRecorder;
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    // Extract wave ID from path: /wave/{waveId}
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() <= 1) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Wave ID required");
      return;
    }
    String waveIdStr = pathInfo.substring(1); // strip leading /

    // Parse the wave ID
    WaveId waveId;
    try {
      waveId = WaveId.deserialise(waveIdStr);
    } catch (Exception e) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Invalid wave ID");
      return;
    }

    // Get the conv+root wavelet
    WaveletId rootWaveletId = WaveletId.of(waveId.getDomain(), "conv+root");
    WaveletName waveletName = WaveletName.of(waveId, rootWaveletId);

    // Check that the wave is public (shared domain participant has access)
    try {
      if (!waveletProvider.checkAccessPermission(waveletName, sharedDomainParticipant)) {
        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Wave not found");
        return;
      }
    } catch (WaveServerException e) {
      LOG.warning("Error checking access for public wave: " + waveIdStr, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    // Fetch the snapshot
    CommittedWaveletSnapshot committedSnapshot;
    try {
      committedSnapshot = waveletProvider.getSnapshot(waveletName);
    } catch (WaveServerException e) {
      LOG.warning("Error fetching snapshot for public wave: " + waveIdStr, e);
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      return;
    }

    if (committedSnapshot == null) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Wave not found");
      return;
    }

    ReadableWaveletData snapshot = committedSnapshot.snapshot;

    // Extract title and description from the wave content (for SEO meta tags)
    String snippet = Snippets.renderSnippet(snapshot, MAX_DESCRIPTION_LENGTH + 200).trim();
    String title;
    String description;

    if (snippet.isEmpty()) {
      title = "Wave";
      description = "";
    } else {
      // Title: first 80 chars or first sentence
      title = extractTitle(snippet);
      description = extractDescription(snippet, title);
    }

    // Get author and timestamps
    String author = snapshot.getCreator() != null
        ? snapshot.getCreator().getAddress() : "";
    long creationTime = snapshot.getCreationTime();
    long lastModifiedTime = snapshot.getLastModifiedTime();

    SimpleDateFormat iso8601 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    iso8601.setTimeZone(TimeZone.getTimeZone("UTC"));
    String datePublished = creationTime > 0 ? iso8601.format(new Date(creationTime)) : null;
    String dateModified = lastModifiedTime > 0 ? iso8601.format(new Date(lastModifiedTime)) : null;

    String canonicalUrl = siteUrl + "/wave/" + waveIdStr;

    // Render full blip content with author and thread structure
    List<BlipInfo> blips = PublicWaveBlipRenderer.renderBlips(snapshot);

    // Render the page
    String html = HtmlRenderer.renderPublicWavePageWithBlips(
        title, description, canonicalUrl, siteUrl, waveIdStr,
        author, datePublished, dateModified, blips, waveDomain);

    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/html; charset=UTF-8");
    // Use no-cache so browsers revalidate on every request. This ensures that
    // waves toggled to private immediately return 404 instead of serving stale
    // cached content for up to 5 minutes.
    resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
    resp.setHeader("Pragma", "no-cache");
    analyticsRecorder.incrementPageViews(waveIdStr, System.currentTimeMillis());
    resp.getWriter().write(html);
  }

  /**
   * Extracts a suitable title from the wave snippet. Uses the first line/sentence
   * up to 80 characters.
   */
  private static String extractTitle(String snippet) {
    if (snippet.length() <= 80) {
      return snippet;
    }
    // Try to find a sentence boundary within the first 80 chars
    int end = 80;
    for (int i = 0; i < 80 && i < snippet.length(); i++) {
      char c = snippet.charAt(i);
      if (c == '.' || c == '!' || c == '?') {
        end = i + 1;
        break;
      }
    }
    return snippet.substring(0, Math.min(end, snippet.length())).trim();
  }

  /**
   * Extracts a description from the snippet, removing the title prefix if present.
   * Truncates at {@link #MAX_DESCRIPTION_LENGTH} characters.
   */
  private static String extractDescription(String snippet, String title) {
    String desc = snippet;
    if (desc.startsWith(title)) {
      desc = desc.substring(title.length()).trim();
    }
    if (desc.length() > MAX_DESCRIPTION_LENGTH) {
      desc = desc.substring(0, MAX_DESCRIPTION_LENGTH).trim();
      // Avoid cutting in the middle of a word
      int lastSpace = desc.lastIndexOf(' ');
      if (lastSpace > MAX_DESCRIPTION_LENGTH / 2) {
        desc = desc.substring(0, lastSpace);
      }
      desc += "...";
    }
    return desc;
  }
}
