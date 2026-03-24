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
import org.waveprotocol.wave.util.logging.Log;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Jakarta-compatible variant of {@link InitialsAvatarsServlet}. Serves the
 * default avatar image using the Jakarta servlet APIs.
 */
@Singleton
public final class InitialsAvatarsServlet extends HttpServlet {
  private static final Log LOG = Log.get(InitialsAvatarsServlet.class);
  private final BufferedImage defaultAvatar;

  @Inject
  public InitialsAvatarsServlet() throws IOException {
    this.defaultAvatar = loadDefault();
  }

  private static BufferedImage loadDefault() throws IOException {
    try {
      return ImageIO.read(Resources.getResource(
          "org/apache/wave/box/server/rpc/avatar/unknown.jpg"));
    } catch (Exception primary) {
      LOG.warning("Default avatar image missing from classpath path; trying WAR fallback.", primary);
      return ImageIO.read(Resources.getResource(
          "static/images/unknown.jpg"));
    }
  }

  @Override
  @VisibleForTesting
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("image/jpg");
    ImageIO.write(defaultAvatar, "JPG", resp.getOutputStream());
  }
}
