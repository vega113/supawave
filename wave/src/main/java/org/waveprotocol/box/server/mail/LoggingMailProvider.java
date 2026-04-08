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

import org.waveprotocol.wave.util.logging.Log;

/**
 * Development {@link MailProvider} that logs emails to the server log instead
 * of sending them. This is the default provider and is useful for development
 * and testing.
 */
public class LoggingMailProvider implements MailProvider {

  private static final Log LOG = Log.get(LoggingMailProvider.class);

  public LoggingMailProvider() {}

  @Override
  public void sendEmail(String to, String subject, String htmlBody) {
    sendEmail(to, subject, htmlBody, null);
  }

  @Override
  public void sendEmail(String to, String subject, String htmlBody, String replyTo) {
    LOG.info("========== EMAIL ==========");
    LOG.info("To: " + to);
    if (replyTo != null && !replyTo.isEmpty()) {
      LOG.info("Reply-To: " + replyTo);
    }
    LOG.info("Subject: " + subject);
    LOG.info("Body: " + htmlBody);
    LOG.info("===========================");
  }
}
