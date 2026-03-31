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
  public void testPrintObjectAsHtmlWithNull() {
    assertEquals("<span class='object'>null</span>", LogUtils.printObjectAsHtml(null));
  }

  @Test
  public void testPrintObjectAsHtmlWithSimpleString() {
    assertEquals("<span class='object'>hello world</span>", LogUtils.printObjectAsHtml("hello world"));
  }

  @Test
  public void testPrintObjectAsHtmlWithEscapedCharacters() {
    String input = "A & B < C > D \" E ' F";
    String expected = "<span class='object'>A &amp; B &lt; C &gt; D &quot; E &#39; F</span>";
    assertEquals(expected, LogUtils.printObjectAsHtml(input));
  }

  @Test
  public void testPrintObjectAsHtmlWithCustomObject() {
    Object customObj = new Object() {
      @Override
      public String toString() {
        return "custom <object>";
      }
    };
    String expected = "<span class='object'>custom &lt;object&gt;</span>";
    assertEquals(expected, LogUtils.printObjectAsHtml(customObj));
  }

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

  @Test
  public void testStringifyLogObjectWithNullHandledSafely() {
    assertEquals("null", LogUtils.stringifyLogObject(null));
  }

  @Test
  public void testStringifyLogObjectWithArrayContainingNullsHandledSafely() {
    Object[] arrayWithNull = new Object[]{"hello", null, "world"};
    assertEquals("hellonullworld", LogUtils.stringifyLogObject(arrayWithNull));
  }
}
