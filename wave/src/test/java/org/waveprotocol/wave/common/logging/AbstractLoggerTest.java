package org.waveprotocol.wave.common.logging;

import junit.framework.TestCase;
import org.mockito.Mockito;

public class AbstractLoggerTest extends TestCase {

  private AbstractLogger logger;
  private LogSink mockSink;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mockSink = Mockito.mock(LogSink.class);
    logger = new AbstractLogger(mockSink) {
      @Override
      protected boolean shouldLog(Level level) {
        return true;
      }

      @Override
      public boolean isModuleEnabled() {
        return true;
      }
    };
  }

  public void testLogPlainTextInner() {
    String message = "hello < world";
    logger.logPlainTextInner(AbstractLogger.Level.ERROR, message);

    Mockito.verify(mockSink).lazyLog(
        AbstractLogger.Level.ERROR,
        "<pre style='display:inline'>",
        "hello &lt; world",
        "</pre>");
  }
}
