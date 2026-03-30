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

package org.waveprotocol.box.common;

import com.google.common.base.Function;

import org.waveprotocol.wave.model.document.operation.AnnotationBoundaryMap;
import org.waveprotocol.wave.model.document.operation.Attributes;
import org.waveprotocol.wave.model.document.operation.AttributesUpdate;
import org.waveprotocol.wave.model.document.operation.DocInitializationCursor;
import org.waveprotocol.wave.model.document.operation.DocOp;
import org.waveprotocol.wave.model.document.DocumentConstants;
import org.waveprotocol.wave.model.document.operation.DocOpCursor;
import org.waveprotocol.wave.model.document.operation.impl.InitializationCursorAdapter;
import org.waveprotocol.wave.model.wave.data.ReadableBlipData;
import org.waveprotocol.wave.model.wave.data.ReadableWaveletData;
import org.waveprotocol.wave.model.wave.data.WaveletData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Utility methods for rendering snippets.
 *
 * @author anorth@google.com (Alex North)
 */
public final class Snippets {

  private final static Function<StringBuilder, Void> DEFAULT_LINE_MODIFIER =
      new Function<StringBuilder, Void>() {

    @Override
    public Void apply(StringBuilder resultBuilder) {
      resultBuilder.append(" ");
      return null;
    }
  };
  public static final int DIGEST_SNIPPET_LENGTH = 140;

  /**
   * Concatenates all of the text for the given documents in
   * {@link WaveletData}.
   *
   * @param wavelet the wavelet for which to concatenate the documents.
   * @return A String containing the characters from all documents.
   */
  public static String collateTextForWavelet(ReadableWaveletData wavelet) {
    List<ReadableBlipData> documents = new ArrayList<ReadableBlipData>();
    for (String documentId : wavelet.getDocumentIds()) {
      documents.add(wavelet.getDocument(documentId));
    }
    return collateTextForDocuments(documents);
  }

  /**
   * Concatenates all of the text of the specified blips into a single String.
   *
   * @param documents the documents to concatenate.
   * @return A String containing the characters from all documents.
   */
  public static String collateTextForDocuments(Iterable<? extends ReadableBlipData> documents) {
    ArrayList<DocOp> docOps = new ArrayList<DocOp>();
    for (ReadableBlipData blipData : documents) {
      docOps.add(blipData.getContent().asOperation());
    }
    return collateTextForOps(docOps);
  }

  public static String collateTextForDocuments(Iterable<? extends ReadableBlipData> documents,
      int maxLength) {
    ArrayList<DocOp> docOps = new ArrayList<DocOp>();
    for (ReadableBlipData blipData : documents) {
      docOps.add(blipData.getContent().asOperation());
    }
    return collateTextForOps(docOps, DEFAULT_LINE_MODIFIER, maxLength);
  }

  /**
   * Concatenates all of the text of the specified docops into a single String.
   *
   * @param documentops the document operations to concatenate.
   * @param func the function that will be applied on result when
   *        DocumentConstants.LINE event is be encountered during parsing.
   * @return A String containing the characters from the operations.
   */
  public static String collateTextForOps(Iterable<? extends DocOp> documentops,
      final Function<StringBuilder, Void> func) {
    return collateTextForOps(documentops, func, Integer.MAX_VALUE);
  }

  private static String collateTextForOps(Iterable<? extends DocOp> documentops,
      final Function<StringBuilder, Void> func, final int maxLength) {
    final StringBuilder resultBuilder = new StringBuilder();
    int safeMaxLength = Math.max(0, maxLength);
    try {
      for (DocOp docOp : documentops) {
        docOp.apply(InitializationCursorAdapter.adapt(new DocOpCursor() {
          @Override
          public void characters(String s) {
            if (resultBuilder.length() >= safeMaxLength) {
              throw new SnippetLimitReachedException();
            }
            int remaining = safeMaxLength - resultBuilder.length();
            if (s.length() <= remaining) {
              resultBuilder.append(s);
            } else {
              resultBuilder.append(s.substring(0, remaining));
              throw new SnippetLimitReachedException();
            }
          }

          @Override
          public void annotationBoundary(AnnotationBoundaryMap map) {
          }

          @Override
          public void elementStart(String type, Attributes attrs) {
            if (type.equals(DocumentConstants.LINE)) {
              func.apply(resultBuilder);
            }
          }

          @Override
          public void elementEnd() {
          }

          @Override
          public void retain(int itemCount) {
          }

          @Override
          public void deleteCharacters(String chars) {
          }

          @Override
          public void deleteElementStart(String type, Attributes attrs) {
          }

          @Override
          public void deleteElementEnd() {
          }

          @Override
          public void replaceAttributes(Attributes oldAttrs, Attributes newAttrs) {
          }

          @Override
          public void updateAttributes(AttributesUpdate attrUpdate) {
          }
        }));
      }
    } catch (SnippetLimitReachedException ignored) {
    }
    return resultBuilder.toString().trim();
  }

  /**
   * Concatenates all of the text of the specified docops into a single String.
   *
   * @param documentops the document operations to concatenate.
   * @return A String containing the characters from the operations.
   */
  public static String collateTextForOps(Iterable<? extends DocOp> documentops) {
    return collateTextForOps(documentops, DEFAULT_LINE_MODIFIER);
  }

  /**
   * Returns a snippet or null.
   *
   * Renders blips in conversation manifest order so the root blip content
   * always comes first.  This ensures the wave title (derived from the root
   * blip) can be reliably stripped from the snippet prefix and that reply
   * blip content never displaces the root blip content at the start of the
   * snippet.
   */
  public static String renderSnippet(final ReadableWaveletData wavelet,
      final int maxSnippetLength) {
    final StringBuilder sb = new StringBuilder();
    Set<String> docsIds = wavelet.getDocumentIds();

    // Find the conversation manifest document.
    ReadableBlipData manifestDoc = null;
    if (docsIds.contains(DocumentConstants.MANIFEST_DOCUMENT_ID)) {
      manifestDoc = wavelet.getDocument(DocumentConstants.MANIFEST_DOCUMENT_ID);
    }

    if (manifestDoc != null) {
      // Walk the conversation manifest to render blips in their natural
      // (root-first) order instead of picking the most-recently-modified blip.
      DocOp docOp = manifestDoc.getContent().asOperation();
      docOp.apply(InitializationCursorAdapter.adapt(new DocInitializationCursor() {
        @Override
        public void annotationBoundary(AnnotationBoundaryMap map) {
        }

        @Override
        public void characters(String chars) {
          // No chars in the conversation manifest
        }

        @Override
        public void elementEnd() {
        }

        @Override
        public void elementStart(String type, Attributes attrs) {
          if (sb.length() >= maxSnippetLength) {
            return;
          }

          if (DocumentConstants.BLIP.equals(type)) {
            String blipId = attrs.get(DocumentConstants.BLIP_ID);
            if (blipId != null) {
              ReadableBlipData document = wavelet.getDocument(blipId);
              if (document == null) {
                // We see this when a blip has been deleted
                return;
              }
              sb.append(collateTextForDocuments(Arrays.asList(document)));
              sb.append(" ");
            }
          }
        }
      }));
    } else {
      // No conversation manifest found – fall back to collating all text.
      sb.append(collateTextForWavelet(wavelet));
    }
    if (sb.length() > maxSnippetLength) {
      return sb.substring(0, maxSnippetLength);
    }
    return sb.toString();
  }

  /**
   * Returns a snippet from the last (most recently added) blip in the
   * conversation manifest, representing the latest reply.  If the wave has
   * only one blip (the root), returns an empty string so the digest can
   * fall back to showing the root blip body text minus the title.
   *
   * @param wavelet the wavelet data
   * @param maxSnippetLength maximum character length of the returned snippet
   * @return the snippet text from the last reply blip, or empty string
   */
  public static String renderSnippetFromLastBlip(final ReadableWaveletData wavelet,
      final int maxSnippetLength) {
    Set<String> docsIds = wavelet.getDocumentIds();

    ReadableBlipData manifestDoc = null;
    if (docsIds.contains(DocumentConstants.MANIFEST_DOCUMENT_ID)) {
      manifestDoc = wavelet.getDocument(DocumentConstants.MANIFEST_DOCUMENT_ID);
    }

    if (manifestDoc == null) {
      return "";
    }

    // Collect all blip IDs from the manifest in order.
    final List<String> blipIds = new ArrayList<String>();
    DocOp docOp = manifestDoc.getContent().asOperation();
    docOp.apply(InitializationCursorAdapter.adapt(new DocInitializationCursor() {
      @Override
      public void annotationBoundary(AnnotationBoundaryMap map) {
      }

      @Override
      public void characters(String chars) {
      }

      @Override
      public void elementEnd() {
      }

      @Override
      public void elementStart(String type, Attributes attrs) {
        if (DocumentConstants.BLIP.equals(type)) {
          String blipId = attrs.get(DocumentConstants.BLIP_ID);
          if (blipId != null) {
            blipIds.add(blipId);
          }
        }
      }
    }));

    if (blipIds.size() <= 1) {
      // Only the root blip exists; no replies yet.
      return "";
    }

    // Use the last blip as the "latest reply" snippet source.
    String lastBlipId = blipIds.get(blipIds.size() - 1);
    ReadableBlipData document = wavelet.getDocument(lastBlipId);
    if (document == null) {
      return "";
    }
    String text = collateTextForDocuments(Arrays.asList(document), maxSnippetLength).trim();
    if (text.length() > maxSnippetLength) {
      return text.substring(0, maxSnippetLength);
    }
    return text;
  }

  private static final class SnippetLimitReachedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
  }

  private Snippets() {
  }
}
