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
package org.waveprotocol.box.server;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;
import com.typesafe.config.Config;
import java.time.Clock;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;
import javax.security.auth.login.Configuration;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.session.DefaultSessionCache;
import org.eclipse.jetty.session.FileSessionDataStore;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.SessionManagerImpl;
import org.waveprotocol.box.server.authentication.jwt.JwtKeyRingPersistence;
import org.waveprotocol.box.server.authentication.jwt.JwtKeyRing;
import org.waveprotocol.box.server.authentication.oauth.DefaultSocialAuthHttpClient;
import org.waveprotocol.box.server.authentication.oauth.SocialAuthHttpClient;
import org.waveprotocol.box.server.jakarta.ServerRpcProviderJakartaProvider;
import org.waveprotocol.box.server.rpc.ChangelogProvider;
import org.waveprotocol.box.server.rpc.ChangelogServlet;
import org.waveprotocol.box.server.rpc.ProtoSerializer;
import org.waveprotocol.box.server.rpc.ServerRpcProvider;
// RobotRegistrar binding is in JakartaRobotApiBindingsModule
import org.waveprotocol.box.server.waveserver.WaveServerImpl;
import org.waveprotocol.box.server.waveserver.WaveServerModule;
import org.waveprotocol.wave.federation.FederationHostBridge;
import org.waveprotocol.wave.federation.FederationRemoteBridge;
import org.waveprotocol.wave.federation.WaveletFederationListener;
import org.waveprotocol.wave.federation.WaveletFederationProvider;
import org.waveprotocol.wave.model.id.IdGenerator;
import org.waveprotocol.wave.model.id.IdGeneratorImpl;
import org.waveprotocol.wave.model.id.IdGeneratorImpl.Seed;
import org.waveprotocol.wave.model.id.TokenGenerator;
import org.waveprotocol.wave.model.id.TokenGeneratorImpl;

@SuppressWarnings("ALL")
public class ServerModule extends AbstractModule {
  private final WaveServerModule waveServerModule;

  @Inject
  public ServerModule(WaveServerModule waveServerModule) {
    this.waveServerModule = waveServerModule;
  }

  @Override
  protected void configure() {
    bind(WaveServerImpl.class).in(Singleton.class);
    bind(WaveletFederationListener.Factory.class).annotatedWith(FederationRemoteBridge.class)
        .to(WaveServerImpl.class);
    bind(WaveletFederationProvider.class).annotatedWith(FederationHostBridge.class)
        .to(WaveServerImpl.class);

    install(waveServerModule);
    TypeLiteral<List<String>> certs = new TypeLiteral<List<String>>() {};
    bind(certs).annotatedWith(Names.named("certs")).toInstance(Arrays.<String>asList());
    bind(ProtoSerializer.class).in(Singleton.class);
    bind(ChangelogProvider.class).in(Singleton.class);
    bind(ChangelogServlet.class).in(Singleton.class);
    bind(Configuration.class).toInstance(Configuration.getConfiguration());
    bind(SessionManager.class).to(SessionManagerImpl.class).in(Singleton.class);
    bind(SocialAuthHttpClient.class).to(DefaultSocialAuthHttpClient.class).in(Singleton.class);
    // Bind via Provider to avoid Guice eagerly reflecting over ServerRpcProvider's methods
    // (which reference EE10 types) during injector creation.
    bind(ServerRpcProvider.class).toProvider(ServerRpcProviderJakartaProvider.class).in(Singleton.class);
    // RobotRegistrar is bound by JakartaRobotApiBindingsModule; do NOT duplicate here
    // or Guice will reject the child-injector with ChildBindingAlreadySet.

    // Explicit binding for WelcomeWaveCreator so that Guice can inject it
    // into UserRegistrationServlet.  Without this, JIT binding may fail when
    // the injector child-chain hasn't been fully wired yet.
    bind(org.waveprotocol.box.server.rpc.WelcomeWaveCreator.class).in(Singleton.class);
    bind(org.waveprotocol.box.server.robots.util.ConversationUtil.class).in(Singleton.class);
  }

  @Provides @Singleton
  public IdGenerator provideIdGenerator(@com.google.inject.name.Named(CoreSettingsNames.WAVE_SERVER_DOMAIN) String domain, Seed seed) {
    return new IdGeneratorImpl(domain, seed);
  }

  @Provides @Singleton
  public SecureRandom provideSecureRandom() { return new SecureRandom(); }

  @Provides @Singleton
  public Clock provideClock() { return Clock.systemUTC(); }

  @Provides @Singleton
  public JwtKeyRing provideJwtKeyRing(Config config) {
    Path path = Paths.get(config.getString("security.jwt_signing_key_path"));
    return JwtKeyRingPersistence.loadOrCreate(path, "wave-session");
  }

  @Provides @Singleton
  public TokenGenerator provideTokenGenerator(SecureRandom random) { return new TokenGeneratorImpl(random); }

  @Provides @Singleton
  public Seed provideSeed(final SecureRandom random) {
    return () -> Long.toString(Math.abs(random.nextLong()), 36);
  }

  @Provides @Singleton
  public SessionHandler provideSessionHandler(Config config) {
    SessionHandler sessionHandler = new SessionHandler();
    sessionHandler.setSessionIdPathParameterName(null);

    // Configure cookie attributes to match the javax variant
    try {
      sessionHandler.getSessionCookieConfig()
          .setMaxAge(config.getInt("network.session_cookie_max_age"));
    } catch (Exception ignore) {}
    try {
      boolean httpOnly = true;
      try {
        if (config.hasPath("network.session_cookie_http_only")) {
          httpOnly = config.getBoolean("network.session_cookie_http_only");
        }
      } catch (Exception ignored) {}
      sessionHandler.getSessionCookieConfig().setHttpOnly(httpOnly);

      boolean enableSsl = false;
      try { enableSsl = config.getBoolean("security.enable_ssl"); } catch (Exception ignored) {}
      if (enableSsl) {
        sessionHandler.getSessionCookieConfig().setSecure(true);
      }
      // Set SameSite=LAX if supported by the runtime Jetty version
      try {
        Class<?> sameSiteClass = Class.forName("org.eclipse.jetty.http.HttpCookie$SameSite");
        if (sameSiteClass.isEnum()) {
          Object lax = null;
          Object[] consts = sameSiteClass.getEnumConstants();
          if (consts != null) {
            for (Object c : consts) {
              if ("LAX".equals(String.valueOf(c))) { lax = c; break; }
            }
          }
          if (lax != null) {
            SessionHandler.class.getMethod("setSameSite", sameSiteClass)
                .invoke(sessionHandler, lax);
          }
        }
      } catch (Throwable ignored) {}
    } catch (Exception ignore) {}

    // File-backed session data store for persistence across restarts.
    // Validate the directory thoroughly BEFORE wiring the cache: once the
    // cache is set on the SessionHandler, Jetty will attempt to start the
    // FileSessionDataStore during httpServer.start().  If the store
    // directory is missing or unwritable at that point, Jetty throws
    // IllegalStateException which kills the entire server startup.
    try {
      java.io.File storeDir =
          new java.io.File(config.getString("core.sessions_store_directory"));
      if (!storeDir.exists()) {
        storeDir.mkdirs();
      }
      if (storeDir.isDirectory() && storeDir.canRead() && storeDir.canWrite()) {
        DefaultSessionCache cache = new DefaultSessionCache(sessionHandler);
        FileSessionDataStore dataStore = new FileSessionDataStore();
        dataStore.setStoreDir(storeDir);
        cache.setSessionDataStore(dataStore);
        sessionHandler.setSessionCache(cache);
      } else {
        org.waveprotocol.wave.util.logging.Log.get(ServerModule.class)
            .warning("Session store directory '" + storeDir.getAbsolutePath()
                + "' is not a readable/writable directory; "
                + "sessions will not persist across restarts");
      }
    } catch (Exception e) {
      org.waveprotocol.wave.util.logging.Log.get(ServerModule.class)
          .warning("Failed to configure file-backed session store; sessions will not persist across restarts", e);
    }

    return sessionHandler;
  }
}
