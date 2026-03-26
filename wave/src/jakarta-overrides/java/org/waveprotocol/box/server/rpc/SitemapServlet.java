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

import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import com.typesafe.config.Config;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.TimeZone;
import org.waveprotocol.box.server.CoreSettingsNames;
import org.waveprotocol.box.server.waveserver.PerUserWaveViewProvider;
import org.waveprotocol.box.server.waveserver.WaveletProvider;
import org.waveprotocol.box.server.waveserver.WaveServerException;
import org.waveprotocol.box.server.frontend.CommittedWaveletSnapshot;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.id.WaveletName;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.ParticipantIdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Generates a sitemap.xml listing all public waves. Public waves are those
 * whose participant list includes the shared domain participant (@domain).
 * The sitemap is generated on each request; for large deployments consider
 * caching the result.
 */
@Singleton
public final class SitemapServlet extends HttpServlet {

  private static final Log LOG = Log.get(SitemapServlet.class);
  /** Cache the generated sitemap for 10 minutes to reduce load. */
  private static final long CACHE_TTL_MS = 10 * 60 * 1000;

  private final String siteUrl;
  private final String waveDomain;
  private final PerUserWaveViewProvider waveViewProvider;
  private final WaveletProvider waveletProvider;
  private final ParticipantId sharedDomainParticipant;

  // Simple in-memory cache
  private volatile String cachedSitemap;
  private volatile long cachedAt;

  @Inject
  public SitemapServlet(
      Config config,
      @Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String waveDomain,
      PerUserWaveViewProvider waveViewProvider,
      WaveletProvider waveletProvider) {
    this.siteUrl = RobotsServlet.resolveSiteUrl(config);
    this.waveDomain = waveDomain;
    this.waveViewProvider = waveViewProvider;
    this.waveletProvider = waveletProvider;
    this.sharedDomainParticipant =
        ParticipantIdUtil.makeUnsafeSharedDomainParticipantId(waveDomain);
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    String sitemap = getCachedOrGenerate();
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("application/xml; charset=UTF-8");
    resp.setHeader("Cache-Control", "public, max-age=600");
    resp.getWriter().write(sitemap);
  }

  private String getCachedOrGenerate() {
    long now = System.currentTimeMillis();
    if (cachedSitemap != null && (now - cachedAt) < CACHE_TTL_MS) {
      return cachedSitemap;
    }
    String result = generateSitemap();
    cachedSitemap = result;
    cachedAt = now;
    return result;
  }

  private String generateSitemap() {
    StringBuilder sb = new StringBuilder(4096);
    sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    sb.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

    // Add static pages
    appendUrl(sb, siteUrl + "/", null, "daily", "1.0");
    appendUrl(sb, siteUrl + "/terms", null, "monthly", "0.3");
    appendUrl(sb, siteUrl + "/privacy", null, "monthly", "0.3");
    appendUrl(sb, siteUrl + "/contact", null, "monthly", "0.3");

    // Enumerate public waves (those visible to the shared domain participant)
    try {
      Multimap<WaveId, WaveletId> publicWaves =
          waveViewProvider.retrievePerUserWaveView(sharedDomainParticipant);

      SimpleDateFormat w3cDate = new SimpleDateFormat("yyyy-MM-dd");
      w3cDate.setTimeZone(TimeZone.getTimeZone("UTC"));

      for (WaveId waveId : publicWaves.keySet()) {
        Collection<WaveletId> waveletIds = publicWaves.get(waveId);
        // Find the conv+root wavelet to get the last modification time
        WaveletId rootWaveletId = null;
        for (WaveletId wid : waveletIds) {
          if (wid.getId().equals("conv+root")) {
            rootWaveletId = wid;
            break;
          }
        }
        if (rootWaveletId == null) {
          continue;
        }

        String lastMod = null;
        try {
          WaveletName waveletName = WaveletName.of(waveId, rootWaveletId);
          CommittedWaveletSnapshot snapshot = waveletProvider.getSnapshot(waveletName);
          if (snapshot != null) {
            ReadableWaveletData data = snapshot.snapshot;
            long modTime = data.getLastModifiedTime();
            if (modTime > 0) {
              lastMod = w3cDate.format(new Date(modTime));
            }
          }
        } catch (WaveServerException e) {
          LOG.fine("Failed to get snapshot for sitemap entry: " + waveId.serialise());
        }

        String waveIdStr = waveId.serialise();
        appendUrl(sb, siteUrl + "/wave/" + escapeXml(waveIdStr),
            lastMod, "weekly", "0.6");
      }
    } catch (Exception e) {
      LOG.warning("Failed to enumerate public waves for sitemap", e);
    }

    sb.append("</urlset>\n");
    return sb.toString();
  }

  private static void appendUrl(StringBuilder sb, String loc, String lastmod,
      String changefreq, String priority) {
    sb.append("  <url>\n");
    sb.append("    <loc>").append(escapeXml(loc)).append("</loc>\n");
    if (lastmod != null) {
      sb.append("    <lastmod>").append(lastmod).append("</lastmod>\n");
    }
    if (changefreq != null) {
      sb.append("    <changefreq>").append(changefreq).append("</changefreq>\n");
    }
    if (priority != null) {
      sb.append("    <priority>").append(priority).append("</priority>\n");
    }
    sb.append("  </url>\n");
  }

  private static String escapeXml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;");
  }
}
