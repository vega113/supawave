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

package org.waveprotocol.wave.media.model;

/**
 * Builds attachment metadata lookup requests while leaving the encoding strategy to the caller.
 */
public final class AttachmentInfoRequestBuilder {
  public interface Encoder {
    String encode(String value);
  }

  private AttachmentInfoRequestBuilder() {}

  public static String build(String baseUrl, Iterable<String> attachmentIds, Encoder encoder) {
    StringBuilder request = new StringBuilder(baseUrl).append("?attachmentIds=");
    boolean first = true;
    for (String attachmentId : attachmentIds) {
      if (!first) {
        request.append(',');
      }
      request.append(encoder.encode(attachmentId));
      first = false;
    }
    return request.toString();
  }
}
