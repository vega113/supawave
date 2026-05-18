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

package org.waveprotocol.examples.robots.gptbot;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * Bounded text context derived from a Wave attachment for the GPT bot prompt.
 */
public final class BotAttachmentContext {

  static final int MAX_TEXT_BYTES = 64 * 1024;
  static final int MAX_TRANSCRIPTION_BYTES = 25 * 1024 * 1024;
  private static final int MAX_RENDERED_CHARS = 12000;

  private final String attachmentId;
  private final String fileName;
  private final String mimeType;
  private final String renderedContext;

  private BotAttachmentContext(String attachmentId, String fileName, String mimeType,
      String renderedContext) {
    this.attachmentId = clean(attachmentId);
    this.fileName = clean(fileName);
    this.mimeType = clean(mimeType);
    this.renderedContext = clean(renderedContext);
  }

  public String render() {
    return renderedContext;
  }

  static BotAttachmentContext fromRaw(RawAttachment raw, CodexClient codexClient) {
    String mimeType = normalizedMime(raw.getMimeType());
    String fileName = fallback(raw.getFileName(), raw.getAttachmentId());
    String header = "Attachment: " + fileName + " (" + mimeType + ")";
    if (isTranscribable(mimeType, fileName)) {
      if (raw.getData().length > MAX_TRANSCRIPTION_BYTES) {
        return new BotAttachmentContext(raw.getAttachmentId(), fileName, mimeType,
            header + "\nSkipped: audio/video attachment is larger than 25 MB.");
      }
      Optional<String> transcript = codexClient.transcribeAttachment(fileName, mimeType, raw.getData());
      if (transcript.isPresent() && !transcript.get().isBlank()) {
        return new BotAttachmentContext(raw.getAttachmentId(), fileName, mimeType,
            header + "\nTranscript:\n" + clamp(transcript.get().strip()));
      }
      return new BotAttachmentContext(raw.getAttachmentId(), fileName, mimeType,
          header + "\nSkipped: audio/video transcription failed.");
    }
    if (isTextLike(mimeType, fileName)) {
      Optional<String> text = decodeText(raw.getData());
      if (text.isPresent()) {
        String fence = fenceLanguage(mimeType, fileName);
        return new BotAttachmentContext(raw.getAttachmentId(), fileName, mimeType,
            header + "\n```" + fence + "\n" + clamp(text.get().strip()) + "\n```");
      }
      return new BotAttachmentContext(raw.getAttachmentId(), fileName, mimeType,
          header + "\nSkipped: text attachment could not be decoded as UTF-8.");
    }
    return new BotAttachmentContext(raw.getAttachmentId(), fileName, mimeType,
        header + "\nSkipped: unsupported binary attachment.");
  }

  private static boolean isTranscribable(String mimeType, String fileName) {
    String lowerName = fileName.toLowerCase(Locale.ROOT);
    return mimeType.startsWith("audio/")
        || mimeType.startsWith("video/")
        || lowerName.endsWith(".mp3")
        || lowerName.endsWith(".mp4")
        || lowerName.endsWith(".mpeg")
        || lowerName.endsWith(".mpga")
        || lowerName.endsWith(".m4a")
        || lowerName.endsWith(".wav")
        || lowerName.endsWith(".webm");
  }

  private static boolean isTextLike(String mimeType, String fileName) {
    String lowerName = fileName.toLowerCase(Locale.ROOT);
    return mimeType.startsWith("text/")
        || mimeType.contains("json")
        || mimeType.contains("xml")
        || lowerName.endsWith(".md")
        || lowerName.endsWith(".markdown")
        || lowerName.endsWith(".txt")
        || lowerName.endsWith(".json")
        || lowerName.endsWith(".csv")
        || lowerName.endsWith(".xml");
  }

  private static Optional<String> decodeText(byte[] data) {
    byte[] bytes = data == null ? new byte[0] : data;
    if (bytes.length > MAX_TEXT_BYTES) {
      return Optional.of(new String(bytes, 0, MAX_TEXT_BYTES, StandardCharsets.UTF_8)
          + "\n[truncated after " + MAX_TEXT_BYTES + " bytes]");
    }
    CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT);
    try {
      return Optional.of(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString());
    } catch (CharacterCodingException e) {
      return Optional.empty();
    }
  }

  private static String fenceLanguage(String mimeType, String fileName) {
    String lowerName = fileName.toLowerCase(Locale.ROOT);
    if (mimeType.contains("markdown") || lowerName.endsWith(".md")
        || lowerName.endsWith(".markdown")) {
      return "markdown";
    }
    if (mimeType.contains("json") || lowerName.endsWith(".json")) {
      return "json";
    }
    if (lowerName.endsWith(".csv")) {
      return "csv";
    }
    if (mimeType.contains("xml") || lowerName.endsWith(".xml")) {
      return "xml";
    }
    return "text";
  }

  private static String normalizedMime(String mimeType) {
    String normalized = clean(mimeType).toLowerCase(Locale.ROOT);
    return normalized.isEmpty() ? "application/octet-stream" : normalized;
  }

  private static String fallback(String value, String fallback) {
    String cleaned = clean(value);
    return cleaned.isEmpty() ? clean(fallback) : cleaned;
  }

  private static String clamp(String value) {
    String text = clean(value);
    return text.length() > MAX_RENDERED_CHARS
        ? text.substring(0, MAX_RENDERED_CHARS).strip() + "\n[truncated]"
        : text;
  }

  private static String clean(String value) {
    return value == null ? "" : value.trim();
  }

  public static final class RawAttachment {
    private final String attachmentId;
    private final String fileName;
    private final String mimeType;
    private final byte[] data;

    public RawAttachment(String attachmentId, String fileName, String mimeType, byte[] data) {
      this.attachmentId = clean(attachmentId);
      this.fileName = clean(fileName);
      this.mimeType = clean(mimeType);
      this.data = data == null ? new byte[0] : data.clone();
    }

    public String getAttachmentId() {
      return attachmentId;
    }

    public String getFileName() {
      return fileName;
    }

    public String getMimeType() {
      return mimeType;
    }

    public byte[] getData() {
      return data.clone();
    }

    RawAttachment withFallbackMetadata(String fileName, String mimeType) {
      String effectiveFileName = this.fileName.isEmpty() ? fileName : this.fileName;
      String effectiveMimeType = this.mimeType.isEmpty()
          || "application/octet-stream".equals(this.mimeType)
          ? mimeType : this.mimeType;
      return new RawAttachment(attachmentId, effectiveFileName, effectiveMimeType, data);
    }
  }
}
