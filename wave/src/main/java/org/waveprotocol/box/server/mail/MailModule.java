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

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.typesafe.config.Config;
import org.waveprotocol.wave.util.logging.Log;

import java.lang.reflect.Constructor;

/**
 * Guice module that binds a {@link MailProvider} implementation based on the
 * {@code core.mail_provider} configuration value.
 *
 * <ul>
 *   <li>{@code "logging"} (default) -- {@link LoggingMailProvider}</li>
 *   <li>{@code "resend"} -- {@link ResendMailProvider}</li>
 *   <li>Any fully-qualified class name -- reflective instantiation</li>
 * </ul>
 */
public class MailModule extends AbstractModule {

  private static final Log LOG = Log.get(MailModule.class);

  @Inject
  public MailModule() {}

  @Override
  protected void configure() {
    // Binding happens via @Provides
  }

  @Provides
  @Singleton
  public MailProvider provideMailProvider(Config config) {
    String providerName = config.hasPath("core.mail_provider")
        ? config.getString("core.mail_provider") : "logging";

    switch (providerName) {
      case "logging":
        LOG.info("Using LoggingMailProvider (emails will be logged, not sent)");
        return new LoggingMailProvider();

      case "resend":
        try {
          LOG.info("Using ResendMailProvider");
          return new ResendMailProvider(config);
        } catch (Exception e) {
          LOG.severe("Failed to create ResendMailProvider (missing resend_api_key or "
              + "email_from_address?); falling back to LoggingMailProvider. "
              + "Registration and other servlets will still work, but emails "
              + "will NOT be sent. Error: " + e.getMessage());
          return new LoggingMailProvider();
        }

      default:
        // Treat as FQCN for a custom MailProvider implementation
        LOG.info("Loading custom MailProvider: " + providerName);
        try {
          return loadCustomProvider(providerName, config);
        } catch (Exception e) {
          LOG.severe("Failed to load custom MailProvider '" + providerName
              + "'; falling back to LoggingMailProvider. Error: " + e.getMessage());
          return new LoggingMailProvider();
        }
    }
  }

  private static MailProvider loadCustomProvider(String className, Config config) {
    try {
      Class<?> clazz = Class.forName(className);
      if (!MailProvider.class.isAssignableFrom(clazz)) {
        throw new IllegalStateException(
            className + " does not implement MailProvider");
      }
      // Try Config constructor first, then no-arg
      try {
        Constructor<?> configCtor = clazz.getConstructor(Config.class);
        return (MailProvider) configCtor.newInstance(config);
      } catch (NoSuchMethodException e) {
        return (MailProvider) clazz.getConstructor().newInstance();
      }
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to load custom MailProvider: " + className, e);
    }
  }
}
