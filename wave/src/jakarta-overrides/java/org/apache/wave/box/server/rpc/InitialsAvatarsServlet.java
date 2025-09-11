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
package org.apache.wave.box.server.rpc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Jakarta variant of InitialsAvatarsServlet.
 * Falls back to a generated placeholder if default avatar resource is missing.
 */
@Singleton
public final class InitialsAvatarsServlet extends HttpServlet {
  private static final Logger LOG = Logger.getLogger(InitialsAvatarsServlet.class.getName());
  private BufferedImage DEFAULT;

  @Inject
  public InitialsAvatarsServlet() throws IOException {
    // Try classpath resources used by historic builds
    BufferedImage img = null;
    img = tryLoad("static/images/avatar/unknown.jpg");
    if (img == null) img = tryLoad("org/apache/wave/box/server/rpc/avatar/unknown.jpg");
    if (img == null) {
      LOG.warning("Default Avatar image not found on classpath; generating placeholder.");
      img = generatePlaceholder();
    }
    DEFAULT = img;
  }

  private static BufferedImage tryLoad(String path) {
    try {
      URL u = Resources.getResource(path);
      return ImageIO.read(u);
    } catch (Throwable t) {
      return null;
    }
  }

  private static BufferedImage generatePlaceholder() {
    int w = 100, h = 100;
    BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = img.createGraphics();
    try {
      g.setColor(new Color(230,230,230));
      g.fillRect(0,0,w,h);
      g.setColor(new Color(200,200,200));
      for (int y = 0; y < h; y += 10) g.fillRect(0, y, w, 5);
      g.setColor(new Color(150,150,150));
      g.drawString("?", w/2 - 3, h/2 + 4);
    } finally {
      g.dispose();
    }
    return img;
  }

  @Override
  @VisibleForTesting
  protected void doGet(HttpServletRequest req, HttpServletResponse response) throws IOException {
    response.setContentType("image/jpg");
    // Encode as JPEG
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try {
      ImageIO.write(DEFAULT, "JPG", baos);
      baos.flush();
      byte[] bytes = baos.toByteArray();
      response.setStatus(HttpServletResponse.SC_OK);
      response.setContentLength(bytes.length);
      response.getOutputStream().write(bytes);
    } catch (IOException e) {
      LOG.log(Level.WARNING, "Failed to write avatar image", e);
      response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
      try { baos.close(); } catch (IOException ignore) {}
    }
  }
}

