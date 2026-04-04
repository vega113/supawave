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
import com.typesafe.config.Config;
import java.util.Optional;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.util.BytesRef;
import org.waveprotocol.box.server.waveserver.lucene9.WaveMetadataExtractor.WaveMetadata;
import org.waveprotocol.wave.model.wave.data.WaveViewData;

@Singleton
public class WaveDocumentBuilder {

  private final WaveEmbeddingProvider embeddingProvider;
  private final int vectorDimensions;
  private final VectorSimilarityFunction similarityFunction;

  @Inject
  public WaveDocumentBuilder(Config config, WaveEmbeddingProvider embeddingProvider) {
    this.embeddingProvider = embeddingProvider;
    this.vectorDimensions = config.getInt("core.lucene9_vector_dimensions");
    this.similarityFunction = toSimilarity(config.getString("core.lucene9_vector_similarity"));
  }

  public Document build(WaveMetadata metadata, WaveViewData wave) {
    Document document = new Document();
    String waveId = metadata.getWaveId().serialise();
    document.add(new StringField(Lucene9FieldNames.DOC_ID, waveId, Store.YES));
    document.add(new StringField(Lucene9FieldNames.WAVE_ID, waveId, Store.YES));
    if (!metadata.getRootWaveletId().isEmpty()) {
      document.add(new StringField(Lucene9FieldNames.ROOT_WAVELET_ID,
          metadata.getRootWaveletId(), Store.YES));
    }
    for (String participant : metadata.getParticipants()) {
      document.add(new StringField(Lucene9FieldNames.PARTICIPANT, participant, Store.YES));
    }
    for (String creator : metadata.getCreatorFilters()) {
      document.add(new StringField(Lucene9FieldNames.CREATOR_FILTER, creator, Store.YES));
    }
    document.add(new SortedDocValuesField(Lucene9FieldNames.CREATOR_SORT,
        new BytesRef(metadata.getCreatorSort())));
    for (String tag : metadata.getTags()) {
      document.add(new StringField(Lucene9FieldNames.TAG, tag, Store.YES));
    }
    for (String mentioned : metadata.getMentions()) {
      document.add(new StringField(Lucene9FieldNames.MENTIONED, mentioned, Store.YES));
    }
    if (!metadata.getTitle().isEmpty()) {
      document.add(new TextField(Lucene9FieldNames.TITLE_TEXT, metadata.getTitle(), Store.NO));
    }
    if (!metadata.getContent().isEmpty()) {
      document.add(new TextField(Lucene9FieldNames.CONTENT_TEXT, metadata.getContent(), Store.NO));
    }
    if (!metadata.getAllText().isEmpty()) {
      document.add(new TextField(Lucene9FieldNames.ALL_TEXT, metadata.getAllText(), Store.NO));
    }
    addLongField(document, Lucene9FieldNames.CREATED_SORT, metadata.getCreatedSort());
    addLongField(document, Lucene9FieldNames.LAST_MODIFIED_SORT, metadata.getLastModifiedSort());
    addEmbedding(document, metadata, wave);
    return document;
  }

  private void addLongField(Document document, String fieldName, long value) {
    document.add(new LongPoint(fieldName, value));
    document.add(new NumericDocValuesField(fieldName, value));
    document.add(new StoredField(fieldName, value));
  }

  private void addEmbedding(Document document, WaveMetadata metadata, WaveViewData wave) {
    if (vectorDimensions <= 0) {
      return;
    }
    Optional<float[]> embedding = embeddingProvider.embeddingForWave(metadata.getWaveId(), wave);
    if (!embedding.isPresent()) {
      return;
    }
    float[] vector = embedding.get();
    if (vector.length != vectorDimensions) {
      throw new IllegalArgumentException(
          "Embedding dimension " + vector.length
              + " does not match configured dimension " + vectorDimensions);
    }
    document.add(new KnnFloatVectorField(Lucene9FieldNames.EMBEDDING, vector,
        similarityFunction));
    document.add(new StringField(Lucene9FieldNames.EMBEDDING_MODEL,
        embeddingProvider.modelName(), Store.YES));
  }

  private static VectorSimilarityFunction toSimilarity(String similarity) {
    if ("dot_product".equalsIgnoreCase(similarity)) {
      return VectorSimilarityFunction.DOT_PRODUCT;
    }
    if ("cosine".equalsIgnoreCase(similarity)) {
      return VectorSimilarityFunction.COSINE;
    }
    throw new IllegalArgumentException(
        "Unsupported core.lucene9_vector_similarity: " + similarity);
  }
}
