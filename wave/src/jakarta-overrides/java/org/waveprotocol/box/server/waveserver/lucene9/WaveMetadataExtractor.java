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

import com.google.inject.Singleton;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.waveprotocol.box.common.DocumentConstants;
import org.waveprotocol.box.common.Snippets;
import org.waveprotocol.wave.model.conversation.TitleHelper;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.id.IdConstants;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.id.WaveId;
import org.waveprotocol.wave.model.id.WaveletId;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.ObservableWaveletData;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.WaveViewData;
import org.waveprotocol.wave.model.wave.opbased.OpBasedWavelet;

@Singleton
public class WaveMetadataExtractor {

  private static final String UNKNOWN_CREATOR = "unknown@example.com";

  public WaveMetadata extract(WaveViewData wave) {
    Set<String> participants = new LinkedHashSet<>();
    Set<String> creatorFilters = new LinkedHashSet<>();
    Set<String> tags = new LinkedHashSet<>();
    String title = "";
    String rootWaveletId = "";
    String creatorSort = UNKNOWN_CREATOR;
    StringBuilder content = new StringBuilder();
    long createdSort = -1L;
    long lastModifiedSort = -1L;

    for (ObservableWaveletData wavelet : wave.getWavelets()) {
      createdSort = Math.max(createdSort, wavelet.getCreationTime());
      for (ParticipantId participant : wavelet.getParticipants()) {
        participants.add(participant.getAddress());
      }
      if (wavelet.getCreator() != null) {
        creatorFilters.add(wavelet.getCreator().getAddress());
      }
      if (IdUtil.isConversationalId(wavelet.getWaveletId())) {
        lastModifiedSort = Math.max(lastModifiedSort, wavelet.getLastModifiedTime());
        appendContent(content, Snippets.collateTextForWavelet(wavelet));
      }
      if (IdUtil.isConversationRootWaveletId(wavelet.getWaveletId())) {
        rootWaveletId = wavelet.getWaveletId().serialise();
        creatorSort = wavelet.getCreator() == null ? UNKNOWN_CREATOR
            : wavelet.getCreator().getAddress();
        title = extractTitle(wavelet);
        tags.addAll(extractTags(wavelet));
      }
    }

    String contentText = content.toString().trim();
    String allText = (title + " " + contentText).trim();
    return new WaveMetadata(wave.getWaveId(), rootWaveletId, participants, creatorFilters,
        creatorSort, tags, title, contentText, allText, createdSort, lastModifiedSort);
  }

  private static void appendContent(StringBuilder builder, String text) {
    if (text == null || text.isEmpty()) {
      return;
    }
    if (builder.length() > 0) {
      builder.append('\n');
    }
    builder.append(text.trim());
  }

  private static String extractTitle(ObservableWaveletData wavelet) {
    String rootBlipId = findRootBlipId(wavelet);
    if (rootBlipId == null) {
      return "";
    }
    ReadableBlipData rootBlip = wavelet.getDocument(rootBlipId);
    if (rootBlip == null) {
      return "";
    }
    OpBasedWavelet opBasedWavelet = OpBasedWavelet.createReadOnly(wavelet);
    return TitleHelper.extractTitle(opBasedWavelet.getBlip(rootBlipId).getContent()).trim();
  }

  private static String findRootBlipId(ObservableWaveletData wavelet) {
    ReadableBlipData manifestDoc = wavelet.getDocument(DocumentConstants.MANIFEST_DOCUMENT_ID);
    if (manifestDoc == null) {
      return null;
    }
    DocInitialization docOp = manifestDoc.getContent().asOperation();
    String[] rootBlipId = { null };
    docOp.apply(new DocInitializationCursor() {
      @Override
      public void elementStart(String type, Attributes attrs) {
        if (rootBlipId[0] == null && DocumentConstants.BLIP.equals(type) && attrs != null) {
          rootBlipId[0] = attrs.get(DocumentConstants.BLIP_ID);
        }
      }

      @Override
      public void elementEnd() {
      }

      @Override
      public void characters(String chars) {
      }

      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
      }
    });
    return rootBlipId[0];
  }

  private static Set<String> extractTags(ObservableWaveletData wavelet) {
    ReadableBlipData tagsBlip = wavelet.getDocument(IdConstants.TAGS_DOC_ID);
    if (tagsBlip == null) {
      return java.util.Collections.emptySet();
    }
    Set<String> tags = new LinkedHashSet<>();
    DocInitialization docOp = tagsBlip.getContent().asOperation();
    StringBuilder currentTag = new StringBuilder();
    boolean[] insideTag = { false };
    docOp.apply(new DocInitializationCursor() {
      @Override
      public void elementStart(String type, Attributes attrs) {
        if ("tag".equals(type)) {
          insideTag[0] = true;
          currentTag.setLength(0);
        }
      }

      @Override
      public void elementEnd() {
        if (insideTag[0]) {
          String tag = currentTag.toString().trim().toLowerCase(Locale.ROOT);
          if (!tag.isEmpty()) {
            tags.add(tag);
          }
          insideTag[0] = false;
        }
      }

      @Override
      public void characters(String chars) {
        if (insideTag[0] && chars != null) {
          currentTag.append(chars);
        }
      }

      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
      }
    });
    return tags;
  }

  public static final class WaveMetadata {
    private final WaveId waveId;
    private final String rootWaveletId;
    private final Set<String> participants;
    private final Set<String> creatorFilters;
    private final String creatorSort;
    private final Set<String> tags;
    private final String title;
    private final String content;
    private final String allText;
    private final long createdSort;
    private final long lastModifiedSort;

    WaveMetadata(WaveId waveId, String rootWaveletId, Set<String> participants,
        Set<String> creatorFilters, String creatorSort, Set<String> tags, String title,
        String content, String allText, long createdSort, long lastModifiedSort) {
      this.waveId = waveId;
      this.rootWaveletId = rootWaveletId;
      this.participants = participants;
      this.creatorFilters = creatorFilters;
      this.creatorSort = creatorSort;
      this.tags = tags;
      this.title = title;
      this.content = content;
      this.allText = allText;
      this.createdSort = createdSort;
      this.lastModifiedSort = lastModifiedSort;
    }

    public WaveId getWaveId() {
      return waveId;
    }

    public String getRootWaveletId() {
      return rootWaveletId;
    }

    public Set<String> getParticipants() {
      return participants;
    }

    public Set<String> getCreatorFilters() {
      return creatorFilters;
    }

    public String getCreatorSort() {
      return creatorSort;
    }

    public Set<String> getTags() {
      return tags;
    }

    public String getTitle() {
      return title;
    }

    public String getContent() {
      return content;
    }

    public String getAllText() {
      return allText;
    }

    public long getCreatedSort() {
      return createdSort;
    }

    public long getLastModifiedSort() {
      return lastModifiedSort;
    }
  }
}
