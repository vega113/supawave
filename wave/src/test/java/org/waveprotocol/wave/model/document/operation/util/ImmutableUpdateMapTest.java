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

package org.waveprotocol.wave.model.document.operation.util;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.impl.AttributesUpdateImpl;
import org.waveprotocol.wave.model.document.operation.util.ImmutableUpdateMap.AttributeUpdate;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author ohler@google.com (Christian Ohler)
 */
public class ImmutableUpdateMapTest extends TestCase {

  public void testComposeWithNonImmutableUpdateMap() {
    AttributesUpdateImpl base = new AttributesUpdateImpl(
        "b", "oldB", "midB",
        "d", "oldD", "midD");

    AttributesUpdate result = base.composeWith(new NonImmutableAttributesUpdate(
        "c", null, "newC",
        "a", null, "newA",
        "b", "midB", "newB"));

    assertUpdate(result,
        "a", null, "newA",
        "b", "oldB", "newB",
        "c", null, "newC",
        "d", "oldD", "midD");
  }

  public void testCheckUpdatesSorted() {
    // see also the corresponding tests in ImmutableStateMapTest.
    ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(new AttributeUpdate[] {}));
    ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(new AttributeUpdate("a", null, "1")));
    ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
        new AttributeUpdate("aa", "0", "1"),
        new AttributeUpdate("ab", null, null)));
    ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
        new AttributeUpdate("a", "0", null),
        new AttributeUpdate("b", "p", "2"),
        new AttributeUpdate("c", "1", "1")));
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          new AttributeUpdate("asdfa", "a", "1"),
          new AttributeUpdate("asdfb", "2", null),
          new AttributeUpdate("asdfb", "2", "3")));
      fail();
    } catch (IllegalArgumentException e) {
      // ok
    }
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          new AttributeUpdate("rar", null, "1"),
          new AttributeUpdate("rar", "2", null),
          new AttributeUpdate("rbr", "1", "2")));
      fail();
    } catch (IllegalArgumentException e) {
      // ok
    }
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          null,
          new AttributeUpdate("a", "2", "1")));
      fail();
    } catch (NullPointerException e) {
      // ok
    }
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          new AttributeUpdate("a", "2", "a"),
          null));
      fail();
    } catch (NullPointerException e) {
      // ok
    }
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          new AttributeUpdate("a", "1", "j"),
          new AttributeUpdate("a", "1", "r")));
      fail();
    } catch (IllegalArgumentException e) {
      // ok
    }
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          new AttributeUpdate("a", "1", "f"),
          null,
          new AttributeUpdate("c", "a", "1")));
      fail();
    } catch (NullPointerException e) {
      // ok
    }
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          null,
          new AttributeUpdate("a", "1", "o"),
          new AttributeUpdate("c", "1", "l")));
      fail();
    } catch (NullPointerException e) {
      // ok
    }
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          new AttributeUpdate("a", "1", "y"),
          new AttributeUpdate("c", "1", ";"),
          null));
      fail();
    } catch (NullPointerException e) {
      // ok
    }
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          new AttributeUpdate("ard", "1", "3"),
          new AttributeUpdate("ard", "1", "2"),
          null));
      fail();
    } catch (IllegalArgumentException e) {
      // ok
    }
    try {
      ImmutableUpdateMap.checkUpdatesSorted(Arrays.asList(
          new AttributeUpdate("a", "1", null),
          new AttributeUpdate("c", "2", null),
          new AttributeUpdate("b", "3", null)));
      fail();
    } catch (IllegalArgumentException e) {
      // ok
    }
  }

  private static void assertUpdate(UpdateMap update, String ... triples) {
    assertEquals(triples.length / 3, update.changeSize());
    for (int i = 0; i < update.changeSize(); i++) {
      assertEquals(triples[3 * i], update.getChangeKey(i));
      assertEquals(triples[3 * i + 1], update.getOldValue(i));
      assertEquals(triples[3 * i + 2], update.getNewValue(i));
    }
  }

  private static final class NonImmutableAttributesUpdate implements AttributesUpdate {
    private final String[] triples;

    NonImmutableAttributesUpdate(String ... triples) {
      this.triples = triples;
    }

    @Override
    public int changeSize() {
      return triples.length / 3;
    }

    @Override
    public String getChangeKey(int changeIndex) {
      return triples[3 * changeIndex];
    }

    @Override
    public String getOldValue(int changeIndex) {
      return triples[3 * changeIndex + 1];
    }

    @Override
    public String getNewValue(int changeIndex) {
      return triples[3 * changeIndex + 2];
    }

    @Override
    public AttributesUpdate composeWith(AttributesUpdate mutation) {
      throw new UnsupportedOperationException();
    }

    @Override
    public AttributesUpdate exclude(Collection<String> keys) {
      throw new UnsupportedOperationException();
    }
  }

}
