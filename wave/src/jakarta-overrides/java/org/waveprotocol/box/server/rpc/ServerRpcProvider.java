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
package org.waveprotocol.box.server.rpc;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.protobuf.Service;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.typesafe.config.Config;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.RequestLogWriter;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.session.SessionHandler;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceCollection;
import org.eclipse.jetty.servlet.ServletHolder;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.wave.util.logging.Log;

import javax.annotation.Nullable;
import jakarta.servlet.Filter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.websocket.server.ServerEndpointConfig;

/**
 * Minimal Jakarta-compatible stub of ServerRpcProvider to allow compiling the
 * main module against Jetty 12 while we migrate servlet APIs to jakarta.*.
 * This stub preserves the public surface used by ServerMain and friends.
 *
 * Build-time notes
 * - Included only when building with -PjettyFamily=jakarta.
 * - Default build (javax/Jetty 9.4) continues to use the real class in src/main/java.
 *
 * Migration checklist (P5‑T3)
 * - Servlet/Filter APIs: replace javax.servlet.* with jakarta.servlet.* across server code.
 * - Context/handlers: use ee10 variants (e.g., ServletContextHandler from org.eclipse.jetty.ee10.servlet).
 * - WebSockets: migrate org.eclipse.jetty.websocket.servlet.* to Jetty 12 websocket APIs
 *   (jetty-websocket-jetty12 / ee10 websocket modules) and update WaveWebSocketServlet wiring.
 * - GZip: prefer server-side GzipHandler compatible with Jetty 12 packages.
 * - Forwarded headers, access logs, HTTPS/ALPN/HTTP2: map existing setup to Jetty 12 equivalents.
 * - Static resources and proxies: validate DefaultServlet and ProxyServlet coordinates under Jetty 12.
 * - Sessions: confirm SessionHandler and SessionDataStore APIs under Jetty 12.
 * - Remove this stub once jakarta implementation is complete and tests pass.
 */
@Singleton
public class ServerRpcProvider {
  private static final Log LOG = Log.get(ServerRpcProvider.class);
  private final Config config;
  private final SessionHandler sessionHandler;
  final SessionManager sessionManager;
  final Executor threadPool;
  final Map<Descriptors.Descriptor, RegisteredServiceMethod> registeredServices = new ConcurrentHashMap<>();
  private Server httpServer;
  private ServletContextHandler servletContextHandler;

  public ServerRpcProvider(InetSocketAddress[] httpAddresses,
                           String[] resourceBases, Executor threadPool,
                           SessionManager sessionManager,
                           SessionHandler sessionHandler, String sessionStoreDir,
                           boolean sslEnabled, String sslKeystorePath, String sslKeystorePassword,
                           boolean enableForwardedHeaders) {
    // No-op: stub constructor
    this.config = null;
    this.threadPool = threadPool;
    this.sessionManager = sessionManager;
    this.sessionHandler = sessionHandler;
  }

  public ServerRpcProvider(InetSocketAddress[] httpAddresses,
                           String[] resourceBases, SessionManager sessionManager,
                           SessionHandler sessionHandler, String sessionStoreDir,
                           boolean sslEnabled, String sslKeystorePath, String sslKeystorePassword,
                           Executor executor) {
    // No-op: stub constructor
    this.config = null;
    this.threadPool = executor;
    this.sessionManager = sessionManager;
    this.sessionHandler = sessionHandler;
  }

  @Inject
  public ServerRpcProvider(Config config,
                           SessionManager sessionManager, SessionHandler sessionHandler,
                           @org.waveprotocol.box.server.executor.ExecutorAnnotations.ClientServerExecutor Executor executorService) {
    this.config = config;
    this.sessionHandler = sessionHandler;
    this.sessionManager = sessionManager;
    this.threadPool = executorService;
  }

  public void startWebSocketServer(final Injector injector) {
    try {
      httpServer = new Server();
      // Configure access logging (NCSA) similar to legacy defaults
      try {
        java.io.File logDir = new java.io.File("logs");
        if (!logDir.exists()) logDir.mkdirs();
        RequestLogWriter logWriter = new RequestLogWriter(new java.io.File(logDir, "access.yyyy_mm_dd.log").getPath());
        logWriter.setAppend(true);
        logWriter.setRetainDays(7);
        CustomRequestLog requestLog = new CustomRequestLog(logWriter, CustomRequestLog.NCSA_FORMAT);
        httpServer.setRequestLog(requestLog);
      } catch (Throwable t) {
        LOG.warning("Failed to initialize access logging", t);
      }

      // Forwarded headers toggle mirrors legacy flag: network.enable_forwarded_headers
      boolean enableFwd = false;
      try {
        enableFwd = (config != null && config.hasPath("network.enable_forwarded_headers")
            && config.getBoolean("network.enable_forwarded_headers"));
      } catch (Throwable t) {
        LOG.fine("network.enable_forwarded_headers read failed; using default false", t);
      }
      final HttpConfiguration httpConfig = new HttpConfiguration();
      if (enableFwd) {
        httpConfig.addCustomizer(new ForwardedRequestCustomizer());
      }
      // Build connectors for all configured addresses (compat with legacy)
      int added = 0;
      java.util.List<String> invalidAddrs = new java.util.ArrayList<>();
      List<String> addrs = (config != null && config.hasPath("core.http_frontend_addresses"))
          ? config.getStringList("core.http_frontend_addresses")
          : java.util.Collections.singletonList("127.0.0.1:9898");
      for (String a : addrs) {
        if (a == null || a.isBlank() || !a.contains(":")) { invalidAddrs.add(String.valueOf(a)); continue; }
        try {
          int idx = a.lastIndexOf(':');
          String host = a.substring(0, idx);
          int port = Integer.parseInt(a.substring(idx + 1));
          if (port <= 0 || port > 65535) throw new IllegalArgumentException("Port out of range");
          ServerConnector c = new ServerConnector(httpServer, new HttpConnectionFactory(httpConfig));
          c.setHost(host);
          c.setPort(port);
          httpServer.addConnector(c);
          added++;
        } catch (Exception ex) {
          invalidAddrs.add(a);
        }
      }
      if (!invalidAddrs.isEmpty()) {
        LOG.warning("Ignoring invalid core.http_frontend_addresses entries: " + invalidAddrs +
            "; expected format 'host:port' (e.g., 127.0.0.1:9898)");
      }
      if (added == 0) {
        String def = (config != null && config.hasPath("core.default_http_frontend_address"))
            ? config.getString("core.default_http_frontend_address") : "127.0.0.1:9898";
        try {
          int idx = def.lastIndexOf(':');
          String host = def.substring(0, idx);
          int port = Integer.parseInt(def.substring(idx + 1));
          ServerConnector c = new ServerConnector(httpServer, new HttpConnectionFactory(httpConfig));
          c.setHost(host);
          c.setPort(port);
          httpServer.addConnector(c);
          LOG.info("No valid addresses configured; using default " + host + ":" + port);
        } catch (Exception ex) {
          LOG.severe("Invalid core.default_http_frontend_address '" + def + "'; using 127.0.0.1:9898", ex);
          ServerConnector c = new ServerConnector(httpServer, new HttpConnectionFactory(httpConfig));
          c.setHost("127.0.0.1");
          c.setPort(9898);
          httpServer.addConnector(c);
        }
      }

      ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
      context.setContextPath("/");
      if (sessionHandler != null) {
        context.setSessionHandler(sessionHandler);
      }
      this.servletContextHandler = context;

      // Static resources (multiple bases) similar to legacy ResourceCollection
      try {
        if (config != null && config.hasPath("core.resource_bases")) {
          java.util.List<String> bases = config.getStringList("core.resource_bases");
          if (bases != null && !bases.isEmpty()) {
            java.util.List<Resource> resources = new java.util.ArrayList<>();
            for (String b : bases) {
              if (b != null && !b.isBlank()) resources.add(Resource.newResource(b));
            }
            if (!resources.isEmpty()) {
              context.setBaseResource(new ResourceCollection(resources.toArray(new Resource[0])));
            }
          }
        }
      } catch (Exception ex) {
        LOG.fine("Failed to configure base static resources; continuing without base resource", ex);
      }

      // DefaultServlet mappings with caching semantics
      java.util.Map<String,String> staticParams = new java.util.HashMap<>();
      staticParams.put("etags", "true");
      staticParams.put("cacheControl", "public, max-age=31536000, immutable");
      ServletHolder staticHolder = new ServletHolder(DefaultServlet.class);
      staticHolder.setInitParameters(staticParams);
      context.addServlet(staticHolder, "/static/*");

      java.util.Map<String,String> webclientParams = new java.util.HashMap<>();
      webclientParams.put("etags", "true");
      webclientParams.put("cacheControl", "no-cache, no-store, must-revalidate");
      ServletHolder webclientHolder = new ServletHolder(DefaultServlet.class);
      webclientHolder.setInitParameters(webclientParams);
      context.addServlet(webclientHolder, "/webclient/*");

      // Register Jakarta WebSocket endpoint programmatically with DI configurator
      JakartaWebSocketServletContainerInitializer.configure(context, (ctx, container) -> {
        ServerEndpointConfig sec = ServerEndpointConfig.Builder
            .create(org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint.class, "/socket")
            .configurator(new ServerEndpointConfig.Configurator() {
              @Override
              public <T> T getEndpointInstance(Class<T> endpointClass) {
                try {
                  T ep = endpointClass.getDeclaredConstructor().newInstance();
                  // Build immutable service table and validate entries
                  @SuppressWarnings("unchecked")
                  Map<Descriptors.Descriptor, Object[]> table = new java.util.HashMap<>();
      for (Map.Entry<Descriptors.Descriptor, RegisteredServiceMethod> e : registeredServices.entrySet()) {
        table.put(e.getKey(), new Object[] { e.getValue().service, e.getValue().method });
      }
                  if (threadPool == null || sessionManager == null) {
                    throw new IllegalStateException("Missing required dependencies (executor/sessionManager)");
                  }
                  // Validate table contents
                  for (Map.Entry<Descriptors.Descriptor, Object[]> e : table.entrySet()) {
                    if (e.getKey() == null || e.getValue() == null || e.getValue().length != 2) {
                      throw new IllegalStateException("Invalid service table entry for descriptor: " + e.getKey());
                    }
                    if (!(e.getValue()[0] instanceof com.google.protobuf.Service) ||
                        !(e.getValue()[1] instanceof Descriptors.MethodDescriptor)) {
                      throw new IllegalStateException("Service table entry types are invalid for: " + e.getKey());
                    }
                  }
                  var m = endpointClass.getMethod("setDependencies", java.util.concurrent.Executor.class,
                      org.waveprotocol.box.server.authentication.SessionManager.class,
                      java.util.Map.class);
                  Class<?>[] ptypes = m.getParameterTypes();
                  if (ptypes.length != 3 || !java.util.concurrent.Executor.class.isAssignableFrom(ptypes[0])
                      || !ptypes[1].getName().equals("org.waveprotocol.box.server.authentication.SessionManager")
                      || !java.util.Map.class.isAssignableFrom(ptypes[2])) {
                    throw new IllegalStateException("setDependencies signature mismatch on endpoint: " + m);
                  }
                  m.invoke(ep, threadPool, sessionManager, java.util.Collections.unmodifiableMap(table));
                  return ep;
                } catch (Throwable t) {
                  throw new RuntimeException("Failed to create endpoint instance", t);
                }
              }
            })
            .build();
        container.addEndpoint(sec);
      });

      // Wrap with GzipHandler (prefer server-side gzip over legacy filters)
      GzipHandler gzip = new GzipHandler();
      gzip.setMinGzipSize(1024);
      gzip.setIncludedMethods("GET", "POST");
      gzip.setInflateBufferSize(8 * 1024);
      gzip.setHandler(context);

      httpServer.setHandler(gzip);
      httpServer.start();
      // Log all bound addresses
      var connectors = httpServer.getConnectors();
      StringBuilder sb = new StringBuilder("Jakarta WebSocket server started at: ");
      for (int i = 0; i < connectors.length; i++) {
        if (connectors[i] instanceof ServerConnector) {
          ServerConnector sc = (ServerConnector) connectors[i];
          if (i > 0) sb.append(", ");
          sb.append("ws://").append(sc.getHost()).append(":").append(sc.getLocalPort()).append("/socket");
        }
      }
      LOG.info(sb.toString());
    } catch (Exception e) {
      LOG.severe("Fatal error starting Jakarta http server.", e);
    }
  }

  /**
   * Register the Jakarta WebSocket endpoint on the given ServletContext.
   * This uses the standard ServerContainer attribute to add the endpoint at
   * path "/socket".
   */
  public void registerWebSocketEndpoint(Object servletContext) {
    try {
      if (!(servletContext instanceof jakarta.servlet.ServletContext)) {
        LOG.info("registerWebSocketEndpoint called without a Jakarta ServletContext; skipping.");
        return;
      }
      jakarta.servlet.ServletContext ctx = (jakarta.servlet.ServletContext) servletContext;
      Object attr = ctx.getAttribute("jakarta.websocket.server.ServerContainer");
      if (attr == null) {
        LOG.info("No ServerContainer found on ServletContext; WebSocket endpoint not registered.");
        return;
      }
      jakarta.websocket.server.ServerContainer sc = (jakarta.websocket.server.ServerContainer) attr;
      ServerEndpointConfig sec = ServerEndpointConfig.Builder
          .create(org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint.class, "/socket")
          .configurator(new ServerEndpointConfig.Configurator() {
            @Override
            public <T> T getEndpointInstance(Class<T> endpointClass) {
              try {
                T ep = endpointClass.getDeclaredConstructor().newInstance();
                @SuppressWarnings("unchecked")
                Map<Descriptors.Descriptor, Object[]> table = new java.util.HashMap<>();
                  for (Map.Entry<Descriptors.Descriptor, RegisteredServiceMethod> e : registeredServices.entrySet()) {
                    table.put(e.getKey(), new Object[] { e.getValue().service, e.getValue().method });
                  }
                endpointClass.getMethod("setDependencies", java.util.concurrent.Executor.class,
                    org.waveprotocol.box.server.authentication.SessionManager.class,
                    java.util.Map.class)
                    .invoke(ep, threadPool, sessionManager, table);
                return ep;
              } catch (Throwable t) {
                throw new RuntimeException("Failed to create endpoint instance", t);
              }
            }
          })
          .build();
      sc.addEndpoint(sec);
      LOG.info("Registered Jakarta WebSocket endpoint at /socket");
    } catch (Throwable t) {
      LOG.warning("Failed to register Jakarta WebSocket endpoint", t);
    }
  }

  public ServletHolder addServlet(String urlPattern, Class<? extends HttpServlet> servlet,
                                  @Nullable Map<String, String> initParams) {
    try {
      if (servletContextHandler == null) {
        LOG.info("[Jakarta] addServlet deferred (context not initialized): {} -> {}", urlPattern, servlet.getName());
        return new ServletHolder(servlet);
      }
      ServletHolder holder = new ServletHolder(servlet);
      if (initParams != null && !initParams.isEmpty()) {
        holder.setInitParameters(initParams);
      }
      servletContextHandler.addServlet(holder, urlPattern);
      LOG.info("[Jakarta] addServlet {} -> {}", urlPattern, servlet.getName());
      return holder;
    } catch (Throwable t) {
      LOG.warning("Failed to add servlet '" + servlet + "' at '" + urlPattern + "'", t);
      return new ServletHolder(servlet);
    }
  }

  public ServletHolder addServlet(String urlPattern, Class<? extends HttpServlet> servlet) {
    return addServlet(urlPattern, servlet, null);
  }

  public void addFilter(String urlPattern, Class<? extends Filter> filter) {
    try {
      if (servletContextHandler == null) {
        LOG.info("[Jakarta] addFilter deferred (context not initialized): {} -> {}", urlPattern, filter.getName());
        return;
      }
      org.eclipse.jetty.ee10.servlet.FilterHolder holder = new org.eclipse.jetty.ee10.servlet.FilterHolder(filter);
      servletContextHandler.addFilter(holder, urlPattern, java.util.EnumSet.allOf(DispatcherType.class));
      LOG.info("[Jakarta] addFilter {} -> {}", urlPattern, filter.getName());
    } catch (Throwable t) {
      LOG.warning("Failed to add filter '" + filter + "' at '" + urlPattern + "'", t);
    }
  }

  public void addTransparentProxy(String urlPattern, String proxyTo, String prefix) {
    LOG.info("[Jakarta stub] addTransparentProxy {} -> {} (prefix {})", urlPattern, proxyTo, prefix);
  }

  public SocketAddress getWebSocketAddress() {
    return null;
  }

  public void stopServer() throws IOException {
    try {
      if (httpServer != null) httpServer.stop();
    } catch (Exception e) {
      LOG.warning("Error stopping Jakarta http server", e);
    }
  }

  /** Returns bound ws addresses for testing/logging. */
  public java.util.List<java.net.InetSocketAddress> getBoundAddresses() {
    java.util.List<java.net.InetSocketAddress> out = new java.util.ArrayList<>();
    if (httpServer != null) {
      for (var conn : httpServer.getConnectors()) {
        if (conn instanceof ServerConnector) {
          ServerConnector sc = (ServerConnector) conn;
          out.add(new java.net.InetSocketAddress(sc.getHost(), sc.getLocalPort()));
        }
      }
    }
    return out;
  }

  /** Exposes the registered service map for per-connection dispatchers. */
  public Map<Descriptors.Descriptor, RegisteredServiceMethod> getRegisteredServices() {
    return registeredServices;
  }

  /** Exposes the executor used for running RPC controllers. */
  public Executor getThreadPool() {
    return threadPool;
  }

  /** Exposes the session manager for authentication. */
  public SessionManager getSessionManager() {
    return sessionManager;
  }

  public void registerService(Service service) {
    for (MethodDescriptor methodDescriptor : service.getDescriptorForType().getMethods()) {
      registeredServices.put(methodDescriptor.getInputType(),
          new RegisteredServiceMethod(service, methodDescriptor));
    }
  }

  /** Internal container mapping input types to handlers, mirrored from legacy path. */
  static class RegisteredServiceMethod {
    final Service service;
    final MethodDescriptor method;
    RegisteredServiceMethod(Service service, MethodDescriptor method) {
      this.service = service;
      this.method = method;
    }
  }

  /** Deprecated: Jakarta endpoint now dispatches directly per-connection. */
  @Deprecated
  public void receiveWebSocketMessage(int sequenceNo, com.google.protobuf.Message message) {
    LOG.info("receiveWebSocketMessage is deprecated on Jakarta path; no-op");
  }
}
