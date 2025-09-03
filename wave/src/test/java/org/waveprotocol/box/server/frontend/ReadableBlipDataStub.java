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
package org.waveprotocol.box.server.frontend;

import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.DocumentOperationSink;
import org.waveprotocol.wave.model.document.Document;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.impl.DocInitializationBuffer;
import org.waveprotocol.wave.model.operation.OperationException;
import org.waveprotocol.wave.model.operation.SilentOperationSink;
import org.waveprotocol.wave.model.document.operation.DocOp;

/** Minimal ReadableBlipData stub for tests. */
final class ReadableBlipDataStub implements ReadableBlipData {
  private final ParticipantId author; private final long lmt;
  ReadableBlipDataStub(ParticipantId a, long t) { this.author=a; this.lmt=t; }
  @Override public ReadableWaveletData getWavelet() { return null; }
  @Override public ParticipantId getAuthor() { return author; }
  @Override public java.util.Set<ParticipantId> getContributors() { return java.util.Collections.emptySet(); }
  @Override public long getLastModifiedTime() { return lmt; }
  @Override public long getLastModifiedVersion() { return lmt; }
  @Override public DocumentOperationSink getContent() {
    return new DocumentOperationSink() {
      @Override public DocInitialization asOperation() { return new DocInitializationBuffer().finish(); }
      @Override public void consume(DocOp op) throws OperationException { }
      @Override public Document getMutableDocument() { return null; }
      @Override public void init(SilentOperationSink<? super DocOp> outputSink) { }
    };
  }
  @Override public String getId() { return "b+stub"; }
}
