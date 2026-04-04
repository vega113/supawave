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

package org.waveprotocol.box.server.waveserver;

import org.waveprotocol.wave.model.document.DocumentConstants;
import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.DocInitialization;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.id.IdUtil;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;

/**
 * Utility for extracting plain text from wavelet documents for search indexing.
 *
 * <p>CRITICAL: characters() calls in DocInitialization can be split by
 * annotationBoundary() events, so we always use StringBuilder to accumulate
 * text rather than relying on individual characters() calls.
 */
public final class WaveletTextExtractor {

  private static final int MAX_CONTENT_CHARS = 32768;
  private static final int MAX_TITLE_CHARS = 256;

  private WaveletTextExtractor() {}

  /**
   * Extracts concatenated text from all blip documents in a wavelet.
   * Only processes conversational wavelets (checks wavelet ID).
   *
   * @param wavelet the wavelet to extract text from.
   * @return concatenated plain text content, or empty string if no text found.
   */
  public static String extractAllText(ReadableWaveletData wavelet) {
    if (!IdUtil.isConversationalId(wavelet.getWaveletId())) {
      return "";
    }
    StringBuilder allText = new StringBuilder(1024);
    for (String docId : wavelet.getDocumentIds()) {
      // Only index blip documents (b+xxx), skip manifest and metadata docs.
      if (!IdUtil.isBlipId(docId)) {
        continue;
      }
      ReadableBlipData blip = wavelet.getDocument(docId);
      if (blip == null) {
        continue;
      }
      String text = extractTextFromBlip(blip, MAX_CONTENT_CHARS - allText.length());
      if (!text.isEmpty()) {
        if (allText.length() > 0) {
          allText.append(' ');
        }
        allText.append(text);
      }
      if (allText.length() >= MAX_CONTENT_CHARS) {
        break;
      }
    }
    return allText.toString();
  }

  /**
   * Extracts the title text from a wavelet's root blip. The title is the text
   * content of the first line in the first blip referenced in the conversation
   * manifest.
   *
   * @param wavelet the conversation root wavelet.
   * @return the title text, or empty string if not found.
   */
  public static String extractTitle(ReadableWaveletData wavelet) {
    String rootBlipId = getRootBlipId(wavelet);
    if (rootBlipId == null) {
      return "";
    }
    ReadableBlipData rootBlip = wavelet.getDocument(rootBlipId);
    if (rootBlip == null) {
      return "";
    }
    return extractFirstLineFromBlip(rootBlip, MAX_TITLE_CHARS);
  }

  /**
   * Extracts plain text content from a blip document by walking its DocOp
   * representation using a DocInitializationCursor and StringBuilder.
   */
  static String extractTextFromBlip(ReadableBlipData blip, int maxChars) {
    if (maxChars <= 0) {
      return "";
    }
    DocInitialization docOp = blip.getContent().asOperation();

    final int limit = maxChars;
    final StringBuilder textBuilder = new StringBuilder(Math.min(limit, 1024));
    docOp.apply(new DocInitializationCursor() {
      @Override
      public void elementStart(String type, Attributes attrs) {
        if ("line".equals(type) && textBuilder.length() > 0 && textBuilder.length() < limit) {
          textBuilder.append(' ');
        }
      }

      @Override
      public void elementEnd() {
      }

      @Override
      public void characters(String chars) {
        if (chars != null && textBuilder.length() < limit) {
          int remaining = limit - textBuilder.length();
          if (chars.length() <= remaining) {
            textBuilder.append(chars);
          } else {
            textBuilder.append(chars, 0, remaining);
          }
        }
      }

      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
      }
    });
    return textBuilder.toString().trim();
  }

  /**
   * Extracts only the first line of text from a blip document.
   */
  private static String extractFirstLineFromBlip(ReadableBlipData blip, int maxChars) {
    if (maxChars <= 0) {
      return "";
    }
    DocInitialization docOp = blip.getContent().asOperation();

    final int limit = maxChars;
    final StringBuilder textBuilder = new StringBuilder(Math.min(limit, 1024));
    final boolean[] sawFirstLine = {false};
    final boolean[] capturing = {false};
    final boolean[] finished = {false};
    docOp.apply(new DocInitializationCursor() {
      @Override
      public void elementStart(String type, Attributes attrs) {
        if (finished[0]) {
          return;
        }
        if ("line".equals(type)) {
          if (!sawFirstLine[0]) {
            sawFirstLine[0] = true;
            capturing[0] = true;
          } else {
            finished[0] = true;
            capturing[0] = false;
          }
        }
      }

      @Override
      public void elementEnd() {
      }

      @Override
      public void characters(String chars) {
        if (finished[0] || !capturing[0] || chars == null || textBuilder.length() >= limit) {
          return;
        }
        int remaining = limit - textBuilder.length();
        if (chars.length() <= remaining) {
          textBuilder.append(chars);
        } else {
          textBuilder.append(chars, 0, remaining);
        }
      }

      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
      }
    });
    return textBuilder.toString().trim();
  }

  /**
   * Reads the root blip ID from a wavelet's conversation manifest document.
   */
  private static String getRootBlipId(ReadableWaveletData waveletData) {
    ReadableBlipData manifestDoc = waveletData.getDocument(
        DocumentConstants.MANIFEST_DOCUMENT_ID);
    if (manifestDoc == null) {
      return null;
    }

    DocInitialization docOp = manifestDoc.getContent().asOperation();

    final String[] rootBlipId = {null};
    docOp.apply(new DocInitializationCursor() {
      @Override
      public void elementStart(String type, Attributes attrs) {
        if (rootBlipId[0] == null
            && DocumentConstants.BLIP.equals(type)
            && attrs != null) {
          String id = attrs.get(DocumentConstants.BLIP_ID);
          if (id != null) {
            rootBlipId[0] = id;
          }
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
}
