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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Collections;
import junit.framework.TestCase;

public class AttachmentInfoRequestBuilderTest extends TestCase {

  private static final AttachmentInfoRequestBuilder.Encoder JAVA_QUERY_ENCODER =
      new AttachmentInfoRequestBuilder.Encoder() {
        @Override
        public String encode(String value) {
          try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
          } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
          }
        }
      };

  public void testBuildEncodesPlusInAttachmentId() {
    assertEquals(
        "/attachmentsInfo?attachmentIds=att%2B123",
        AttachmentInfoRequestBuilder.build(
            "/attachmentsInfo",
            Collections.singletonList("att+123"),
            JAVA_QUERY_ENCODER));
  }

  public void testBuildEncodesEachIdBeforeJoining() {
    assertEquals(
        "/attachmentsInfo?attachmentIds=att%2Bone,second%2Bid",
        AttachmentInfoRequestBuilder.build(
            "/attachmentsInfo",
            Arrays.asList("att+one", "second+id"),
            JAVA_QUERY_ENCODER));
  }
}
