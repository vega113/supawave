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
package org.waveprotocol.box.server.waveserver.lucene9;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.waveprotocol.box.server.waveserver.IndexException;

@Singleton
public class Lucene9VectorSearcher {

  private final Lucene9WaveIndexerImpl indexer;

  @Inject
  public Lucene9VectorSearcher(Lucene9WaveIndexerImpl indexer) {
    this.indexer = indexer;
  }

  public TopDocs searchSimilar(float[] embedding, int k, Query preFilter) {
    IndexSearcher searcher = null;
    try {
      searcher = indexer.acquireSearcher();
      Query query = preFilter == null
          ? new KnnFloatVectorQuery(Lucene9FieldNames.EMBEDDING, embedding, k)
          : new KnnFloatVectorQuery(Lucene9FieldNames.EMBEDDING, embedding, k, preFilter);
      return searcher.search(query, k);
    } catch (IOException e) {
      throw new IndexException(e);
    } finally {
      indexer.release(searcher);
    }
  }
}
