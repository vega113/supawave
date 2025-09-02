package org.waveprotocol.box.server.persistence.blocks;

/** Minimal interval placeholder for compat layer. */
public interface Interval {
  /** Returns an opaque snapshot view for the given version. */
  Object getSnapshot(long version);
}

