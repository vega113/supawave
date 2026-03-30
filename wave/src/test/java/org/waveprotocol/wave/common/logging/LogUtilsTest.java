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

import junit.framework.TestCase;

/**
 * Tests for {@link LogUtils}.
 */
public class LogUtilsTest extends TestCase {

  public void testPrintObjectAsHtmlWithNull() {
    assertEquals("<span class='object'>null</span>", LogUtils.printObjectAsHtml(null));
  }

  public void testPrintObjectAsHtmlWithSimpleString() {
    assertEquals("<span class='object'>hello world</span>", LogUtils.printObjectAsHtml("hello world"));
  }

  public void testPrintObjectAsHtmlWithEscapedCharacters() {
    String input = "A & B < C > D \" E ' F";
    String expected = "<span class='object'>A &amp; B &lt; C &gt; D &quot; E &#39; F</span>";
    assertEquals(expected, LogUtils.printObjectAsHtml(input));
  }

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
}
