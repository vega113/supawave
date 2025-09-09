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

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;

/**
 * Strict variant of Jetty's ForwardedRequestCustomizer that ignores malformed
 * X-Forwarded-* values. If headers are invalid, does not apply any forwarded
 * adjustments, leaving the original connection scheme/remote intact.
 */
public class StrictForwardedRequestCustomizer implements HttpConfiguration.Customizer {

  @Override
  public void customize(Connector connector, HttpConfiguration channelConfig, Request request) {
    HttpFields headers = request.getHeaders();
    if (headers == null) return;

    String proto = headers.get("X-Forwarded-Proto");
    if (proto != null && !("http".equalsIgnoreCase(proto) || "https".equalsIgnoreCase(proto))) {
      headers.remove("X-Forwarded-Proto");
    }

    String xff = headers.get("X-Forwarded-For");
    if (xff != null) {
      String first = xff.split(",", 2)[0].trim();
      if (!isValidIp(first)) {
        headers.remove("X-Forwarded-For");
      }
    }
  }

  private static boolean isValidIp(String s) {
    if (s == null || s.isEmpty()) return false;
    try {
      java.net.InetAddress.getByName(s);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
