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
package com.google.wave.api;

import static org.mockito.Mockito.mock;

import com.google.wave.api.JsonRpcConstant.ParamsProperty;
import com.google.wave.api.OperationRequest.Parameter;
import com.google.wave.api.impl.DocumentModifyQuery;

import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.util.List;

public class BlipContentRefsTest extends TestCase {

  public void testAllWithTextTarget() throws Exception {
    Blip blip = mock(Blip.class);
    String target = "hello";
    int maxResult = 5;

    BlipContentRefs refs = BlipContentRefs.all(blip, target, maxResult);

    assertNotNull(refs);

    // Verify iterator is created properly
    Field iteratorField = BlipContentRefs.class.getDeclaredField("iterator");
    iteratorField.setAccessible(true);
    BlipIterator<?> iterator = (BlipIterator<?>) iteratorField.get(refs);
    assertTrue(iterator instanceof BlipIterator.TextIterator);

    // Verify parameters are created properly
    Field parametersField = BlipContentRefs.class.getDeclaredField("parameters");
    parametersField.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<Parameter> parameters = (List<Parameter>) parametersField.get(refs);
    assertEquals(1, parameters.size());

    Parameter param = parameters.get(0);
    assertEquals(ParamsProperty.MODIFY_QUERY, param.getKey());
    assertTrue(param.getValue() instanceof DocumentModifyQuery);

    DocumentModifyQuery query = (DocumentModifyQuery) param.getValue();
    assertEquals(target, query.getTextMatch());
    assertEquals(maxResult, query.getMaxRes());
  }
}
