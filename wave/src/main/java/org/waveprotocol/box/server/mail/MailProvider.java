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
package org.waveprotocol.box.server.mail;

/**
 * Pluggable interface for sending outbound email from the Wave server.
 *
 * <p>Implementations must be thread-safe. The default implementation
 * ({@link LoggingMailProvider}) logs emails to the server log for development.
 * Production deployments should use {@link ResendMailProvider} or a custom
 * implementation.
 *
 * <p>To plug in a custom provider, set {@code core.mail_provider} in
 * {@code reference.conf} to the fully-qualified class name. The class must
 * have a public no-arg constructor or a constructor accepting
 * {@link com.typesafe.config.Config}.
 */
public interface MailProvider {

  /**
   * Sends an email.
   *
   * @param to        the recipient email address
   * @param subject   the email subject line
   * @param htmlBody  the email body in HTML format
   * @throws MailException if the email could not be sent
   */
  void sendEmail(String to, String subject, String htmlBody) throws MailException;

  /**
   * Sends an email with an optional Reply-To header so that mail clients route
   * replies to the submitter rather than to the admin inbox.
   *
   * @param to        the recipient email address
   * @param subject   the email subject line
   * @param htmlBody  the email body in HTML format
   * @param replyTo   the Reply-To address, or {@code null} to omit the header
   * @throws MailException if the email could not be sent
   */
  default void sendEmail(String to, String subject, String htmlBody, String replyTo)
      throws MailException {
    sendEmail(to, subject, htmlBody);
  }
}
