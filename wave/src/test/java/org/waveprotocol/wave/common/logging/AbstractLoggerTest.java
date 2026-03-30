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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.waveprotocol.wave.common.logging.AbstractLogger.Level;

import junit.framework.TestCase;

/**
 * Tests for {@link AbstractLogger}.
 */
public class AbstractLoggerTest extends TestCase {

  private LogSink mockSink;
  private TestLogger logger;
  private boolean shouldLogResult;

  private class TestLogger extends AbstractLogger {
    boolean handleClientErrorsCalled = false;

    public TestLogger(LogSink sink) {
      super(sink);
    }

    @Override
    protected boolean shouldLog(Level level) {
      return shouldLogResult;
    }

    @Override
    public boolean isModuleEnabled() {
      return true;
    }

    @Override
    protected void handleClientErrors(Level level, Throwable t, Object... messages) {
      handleClientErrorsCalled = true;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mockSink = mock(LogSink.class);
    logger = new TestLogger(mockSink);
  }

  public void testLogPlainTextInner() {
    shouldLogResult = true;
    String message = "hello < world";
    logger.logPlainTextInner(AbstractLogger.Level.ERROR, message);

    verify(mockSink).lazyLog(
        AbstractLogger.Level.ERROR,
        "<pre style='display:inline'>",
        "hello &lt; world",
        "</pre>");
  }

  public void testLogWhenShouldLogIsTrue() {
    shouldLogResult = true;
    Object[] messages = new Object[]{"test", "message"};

    logger.log(Level.ERROR, messages);

    assertTrue(logger.handleClientErrorsCalled);
    verify(mockSink).lazyLog(Level.ERROR, messages);
    verifyNoMoreInteractions(mockSink);
  }

  public void testLogWhenShouldLogIsFalse() {
    shouldLogResult = false;
    Object[] messages = new Object[]{"test", "message"};

    logger.log(Level.TRACE, messages);

    assertTrue(logger.handleClientErrorsCalled);
    verifyNoMoreInteractions(mockSink);
  }
}
