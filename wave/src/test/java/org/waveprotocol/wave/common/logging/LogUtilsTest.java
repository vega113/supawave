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

package org.waveprotocol.wave.common.logging;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Tests for {@link LogUtils}.
 */
public class LogUtilsTest {

  @Test
  public void testStringifyLogObjectWithSingleObject() {
    String singleObject = "single object";
    assertEquals("single object", LogUtils.stringifyLogObject(singleObject));

    Integer number = 42;
    assertEquals("42", LogUtils.stringifyLogObject(number));
  }

  @Test
  public void testStringifyLogObjectWithArray() {
    Object[] array = new Object[]{"hello", " ", "world"};
    assertEquals("hello world", LogUtils.stringifyLogObject(array));

    Object[] emptyArray = new Object[]{};
    assertEquals("", LogUtils.stringifyLogObject(emptyArray));

    Object[] mixedArray = new Object[]{"Number: ", 42, ", Boolean: ", true};
    assertEquals("Number: 42, Boolean: true", LogUtils.stringifyLogObject(mixedArray));
  }

  @Test(expected = NullPointerException.class)
  public void testStringifyLogObjectWithNullThrowsNPE() {
    LogUtils.stringifyLogObject(null);
  }

  @Test(expected = NullPointerException.class)
  public void testStringifyLogObjectWithArrayContainingNullsThrowsNPE() {
    Object[] arrayWithNull = new Object[]{"hello", null, "world"};
    LogUtils.stringifyLogObject(arrayWithNull);
  }
}
