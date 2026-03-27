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

package org.waveprotocol.wave.client.wavepanel.view.dom;

import java.util.List;
import junit.framework.TestCase;
import org.waveprotocol.wave.client.common.util.StringSequence;

public final class BlipMetaDomImplTest extends TestCase {

  public void testResolveInlineLocatorReferenceKeepsKnownReference() {
    StringSequence inlineLocators = StringSequence.of(List.of("inline-1", "inline-2"));

    assertEquals(
        "inline-2",
        BlipMetaDomImpl.resolveInlineLocatorReference(inlineLocators, "inline-2"));
  }

  public void testResolveInlineLocatorReferenceFallsBackWhenMissing() {
    StringSequence inlineLocators = StringSequence.of(List.of("inline-1", "inline-2"));

    assertNull(BlipMetaDomImpl.resolveInlineLocatorReference(inlineLocators, "missing-inline"));
  }

  public void testResolveInlineLocatorReferenceReturnsNullWhenInlineLocatorsNull() {
    assertNull(BlipMetaDomImpl.resolveInlineLocatorReference(null, "inline-2"));
  }

  public void testResolveInlineLocatorReferenceReturnsNullWhenReferenceNull() {
    StringSequence inlineLocators = StringSequence.of(List.of("inline-1", "inline-2"));

    assertNull(BlipMetaDomImpl.resolveInlineLocatorReference(inlineLocators, null));
  }

  public void testResolveInlineLocatorReferenceHandlesPrefixAndEncodedIds() {
    StringSequence inlineLocators = StringSequence.of(List.of("a", "ab", "a/b"));

    assertEquals("a", BlipMetaDomImpl.resolveInlineLocatorReference(inlineLocators, "a"));
    assertEquals("ab", BlipMetaDomImpl.resolveInlineLocatorReference(inlineLocators, "ab"));
    assertEquals("a/b", BlipMetaDomImpl.resolveInlineLocatorReference(inlineLocators, "a/b"));
    assertNull(BlipMetaDomImpl.resolveInlineLocatorReference(inlineLocators, "abc"));
  }
}
