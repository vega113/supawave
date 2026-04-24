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

package org.waveprotocol.wave.model.document.operation.algorithm;

import junit.framework.TestCase;

import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.operation.impl.AttributesUpdateImpl;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuffer;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.operation.OpComparators;

import java.util.Collection;

public class DocOpInverterTest extends TestCase {

  public void testUpdateAttributesInversionReversesValues() {
    DocOpInverter<DocOp> inverter = new DocOpInverter<DocOp>(new DocOpBuffer());

    inverter.updateAttributes(new NonImmutableAttributesUpdate(
        "b", "oldB", null,
        "a", null, "newA"));

    DocOp expected = new DocOpBuilder()
        .updateAttributes(new AttributesUpdateImpl(
            "a", "newA", null,
            "b", null, "oldB"))
        .build();
    assertTrue(OpComparators.SYNTACTIC_IDENTITY.equal(expected, inverter.finish()));
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
