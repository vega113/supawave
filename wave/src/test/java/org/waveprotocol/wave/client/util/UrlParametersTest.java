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

package org.waveprotocol.wave.client.util;

import junit.framework.TestCase;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class UrlParametersTest extends TestCase {

  public void testSomeQueries() {
    UrlParameters u = new UrlParameters("?act=new");
    assertEquals("new", u.getParameter("act"));
    assertNull(u.getParameter("cnv"));

    u = new UrlParameters("?act=new&cnv=123");
    assertEquals("new", u.getParameter("act"));
    assertEquals("123", u.getParameter("cnv"));
  }

  public void testNonExistentQuery() {
    UrlParameters u = new UrlParameters("?");
    assertNull(u.getParameter("act"));
    assertNull(u.getParameter("cnv"));

    u = new UrlParameters("");
    assertNull(u.getParameter("nonexistent_key"));
  }

  public void testSafeGetters() {
    UrlParameters u = new UrlParameters("?booleanValue=true&stringValue=hello");
    assertEquals(Boolean.TRUE, u.getBoolean("booleanValue"));
    assertEquals("hello", u.getString("stringValue"));
    assertNull(u.getDouble("booleanValue"));
  }

  public void testInvalidQueryStrings() {
    UrlParameters u = new UrlParameters("?act=");
    assertEquals("", u.getParameter("act"));

    u = new UrlParameters("?act==&");
    assertEquals("", u.getParameter("act"));

    u = new UrlParameters("?act=&cnv=3");
    assertEquals("", u.getParameter("act"));
    assertEquals("3", u.getParameter("cnv"));
  }

  public void testParamsDecoded() {
    UrlParameters u = new UrlParameters("?a+b=c+d");
    assertEquals("c d", u.getParameter("a b"));
  }

  public void testNonExistent() {
    UrlParameters u = new UrlParameters("?booleanValue=true&stringValue=hello");
    assertNull(u.getBoolean("nonexistent"));
  }

  public void testBuildQueryString() {
    assertEquals("", UrlParameters.buildQueryString(Collections.<String, String>emptyMap()));
    assertEquals("?item=1",
        UrlParameters.buildQueryString(Collections.<String, String>singletonMap("item", "1")));
    assertEquals("?one+one=one+two",
        UrlParameters.buildQueryString(
            Collections.<String, String>singletonMap("one one", "one two")));

    Map<String, String> queryMap = new HashMap<String, String>();
    queryMap.put("a", "b");
    queryMap.put("c", "d");
    queryMap.put("e", "f");
    String queryString = UrlParameters.buildQueryString(queryMap);

    UrlParameters u = new UrlParameters(queryString);
    assertEquals("b", u.getParameter("a"));
    assertEquals("d", u.getParameter("c"));
    assertEquals("f", u.getParameter("e"));
  }

  public void testBuildQueryStringPreservesEncodeComponentSafeCharacters() {
    assertEquals("?!~*'()=!~*'()",
        UrlParameters.buildQueryString(
            Collections.<String, String>singletonMap("!~*'()", "!~*'()")));
  }

  public void testUtf8RoundTripInJvmCodec() {
    String queryString =
        UrlParameters.buildQueryString(
            Collections.<String, String>singletonMap("こんにちは", "世界"));

    UrlParameters u = new UrlParameters(queryString);
    assertEquals("世界", u.getParameter("こんにちは"));
  }

  public void testTruncatedUtf8SequenceRejected() {
    try {
      new UrlParameters("?bad=%E2%82");
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("UTF-8"));
    }
  }

  public void testInvalidUtf8ContinuationRejected() {
    try {
      new UrlParameters("?bad=%E2%28%A1");
      fail("Expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("UTF-8"));
    }
  }
}
