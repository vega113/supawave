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

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.wave.api.impl.DocumentModifyAction.BundledAnnotation;

import junit.framework.TestCase;

import java.util.List;

/**
 * Tests for {@link BlipContentRefs}.
 */
public class BlipContentRefsTest extends TestCase {

  public void testInsertBlipContentDelegatesToNullAnnotations() {
    Blip blip = mock(Blip.class);
    when(blip.length()).thenReturn(10);
    when(blip.getContent()).thenReturn("1234567890");

    BlipContentRefs refs = BlipContentRefs.range(blip, 1, 2);
    BlipContentRefs spiedRefs = spy(refs);

    BlipContent arg1 = Plaintext.of("hello");
    BlipContent arg2 = Plaintext.of("world");
    BlipContent[] args = new BlipContent[]{arg1, arg2};

    // Prevent actual execution on the deeper `insert` method by mocking the delegation target
    doReturn(spiedRefs).when(spiedRefs).insert((List<BundledAnnotation>) null, args);

    // This is the target method we are testing
    spiedRefs.insert(args);

    // Verify it delegates correctly to the method taking a List<BundledAnnotation>
    verify(spiedRefs).insert((List<BundledAnnotation>) null, args);
  }
}
