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

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects explicit mentions of the robot in plain text blips.
 */
public final class MentionDetector {

  private static final Pattern LEADING_PROMPT_SEPARATORS = Pattern.compile("^[\\s,;:!?-]+");

  private final Pattern mentionPattern;

  public MentionDetector(String robotName) {
    if (robotName == null || robotName.isBlank()) {
      throw new IllegalArgumentException("robotName must not be blank");
    }
    this.mentionPattern = Pattern.compile(buildMentionRegex(robotName), Pattern.CASE_INSENSITIVE);
  }

  public Optional<String> extractPrompt(String text) {
    Optional<String> prompt = Optional.empty();
    if (text != null) {
      Matcher matcher = mentionPattern.matcher(text);
      if (matcher.find()) {
        String remainder = text.substring(matcher.end());
        remainder = trimPromptPrefix(remainder);
        prompt = Optional.of(remainder);
      }
    }
    return prompt;
  }

  private static String buildMentionRegex(String robotName) {
    String normalized = robotName == null ? "" : robotName.trim().toLowerCase(Locale.ROOT);
    if (normalized.isBlank()) {
      return "(?!)";
    }
    String[] tokens = normalized.split("[\\s_-]+");
    StringBuilder pattern = new StringBuilder();
    for (String token : tokens) {
      if (!token.isBlank()) {
        if (pattern.length() > 0) {
          pattern.append("[-_\\s]*");
        }
        pattern.append(Pattern.quote(token));
      }
    }
    if (pattern.length() == 0) {
      pattern.append(Pattern.quote(normalized));
    }
    return "(?<![\\p{Alnum}])@?" + pattern + "(?=$|[^\\p{Alnum}])";
  }

  private static String trimPromptPrefix(String text) {
    String trimmed = text == null ? "" : text.stripLeading();
    boolean changed = true;
    while (changed && !trimmed.isEmpty()) {
      Matcher matcher = LEADING_PROMPT_SEPARATORS.matcher(trimmed);
      changed = matcher.find();
      if (changed) {
        trimmed = trimmed.substring(matcher.end()).stripLeading();
      }
    }
    return trimmed;
  }
}
