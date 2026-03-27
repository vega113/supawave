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

public final class Lucene9FieldNames {

  public static final String DOC_ID = "doc_id";
  public static final String WAVE_ID = "wave_id";
  public static final String ROOT_WAVELET_ID = "root_wavelet_id";
  public static final String PARTICIPANT = "participant";
  public static final String CREATOR_FILTER = "creator_filter";
  public static final String CREATOR_SORT = "creator_sort";
  public static final String TAG = "tag";
  public static final String TITLE_TEXT = "title_text";
  public static final String CONTENT_TEXT = "content_text";
  public static final String ALL_TEXT = "all_text";
  public static final String CREATED_SORT = "created_sort";
  public static final String LAST_MODIFIED_SORT = "last_modified_sort";
  public static final String EMBEDDING = "embedding";
  public static final String EMBEDDING_MODEL = "embedding_model";

  private Lucene9FieldNames() {
  }
}
