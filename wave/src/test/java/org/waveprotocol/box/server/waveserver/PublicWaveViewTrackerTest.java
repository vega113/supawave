package org.waveprotocol.box.server.waveserver;

import static org.junit.Assert.assertSame;

import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.Test;

public final class PublicWaveViewTrackerTest {
  @Test
  public void guiceReusesSingleTrackerInstance() {
    Injector injector = Guice.createInjector();

    PublicWaveViewTracker first = injector.getInstance(PublicWaveViewTracker.class);
    PublicWaveViewTracker second = injector.getInstance(PublicWaveViewTracker.class);

    assertSame(first, second);
  }
}
