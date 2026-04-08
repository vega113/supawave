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

package org.waveprotocol.wave.model.schema.conversation;

import static org.waveprotocol.wave.model.schema.conversation.ConversationSchemas.BLIP_SCHEMA_CONSTRAINTS;

import junit.framework.TestCase;

public class ImageDisplaySizeSchemaTest extends TestCase {

  public void testDisplaySizeValuesArePermittedForImageElements() {
    assertTrue(BLIP_SCHEMA_CONSTRAINTS.permitsAttribute("image", "display-size", "small"));
    assertTrue(BLIP_SCHEMA_CONSTRAINTS.permitsAttribute("image", "display-size", "medium"));
    assertTrue(BLIP_SCHEMA_CONSTRAINTS.permitsAttribute("image", "display-size", "large"));
    assertFalse(BLIP_SCHEMA_CONSTRAINTS.permitsAttribute("image", "display-size", "giant"));
  }
}
