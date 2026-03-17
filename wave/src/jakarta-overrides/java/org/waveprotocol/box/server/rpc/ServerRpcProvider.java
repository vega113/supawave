/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.waveprotocol.box.server.rpc;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.protobuf.Message;
import com.google.protobuf.Service;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Descriptors.MethodDescriptor;
import com.google.protobuf.RpcCallback;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.RequestLogWriter;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ForwardedRequestCustomizer;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolAuthenticate;
import org.waveprotocol.box.common.comms.WaveClientRpc.ProtocolAuthenticationResult;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.authentication.WebSession;
import org.waveprotocol.box.server.rpc.MessageExpectingChannel;
import org.waveprotocol.box.server.rpc.ProtoCallback;
import org.waveprotocol.box.server.rpc.ServerRpcController;
import org.waveprotocol.box.server.rpc.ServerRpcControllerImpl;
import org.waveprotocol.box.server.rpc.Rpc;
import org.waveprotocol.box.server.rpc.WebSocketChannel;
import org.waveprotocol.box.server.rpc.WebSocketChannelImpl;
import org.waveprotocol.box.server.stat.RequestScopeFilter;
import org.waveprotocol.box.server.stat.TimingFilter;
import org.waveprotocol.box.stat.SessionContext;
import org.waveprotocol.box.stat.Timer;
import org.waveprotocol.box.stat.Timing;
import org.waveprotocol.wave.util.logging.Log;
import org.waveprotocol.wave.model.wave.ParticipantId;

import javax.annotation.Nullable;

import jakarta.servlet.Filter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpSession;
import jakarta.websocket.Session;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import com.typesafe.config.ConfigFactory;

// Jakarta security/caching filters
import org.waveprotocol.box.server.security.jakarta.SecurityHeadersFilter;
import org.waveprotocol.box.server.security.jakarta.StaticCacheFilter;
import org.waveprotocol.box.server.security.jakarta.NoCacheFilter;

/**
 * Jakarta-compatible ServerRpcProvider built on Jetty 12 / EE10 APIs. This
 * variant mirrors the legacy provider, wiring programmatic servlet and
 * WebSocket registration for the jakarta.* namespace.
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
    private Injector guiceInjector;
    private final java.util.List<PendingServlet> pendingServlets = new java.util.ArrayList<>();
    private final java.util.List<PendingFilter> pendingFilters = new java.util.ArrayList<>();
    private final String[] resourceBases;

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
        this.resourceBases = resourceBases != null ? resourceBases : new String[]{"./war"};
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
        this.resourceBases = resourceBases != null ? resourceBases : new String[]{"./war"};
    }

    @Inject
    public ServerRpcProvider(Config config,
                             SessionManager sessionManager, SessionHandler sessionHandler,
                             @org.waveprotocol.box.server.executor.ExecutorAnnotations.ClientServerExecutor Executor executorService) {
        this.config = config;
        this.sessionHandler = sessionHandler;
        this.sessionManager = sessionManager;
        this.threadPool = executorService;
        if (config != null && config.hasPath("core.resource_bases")) {
            java.util.List<String> bases = config.getStringList("core.resource_bases");
            this.resourceBases = bases.toArray(new String[0]);
        } else {
            this.resourceBases = new String[]{"./war"};
        }
    }

    static abstract class Connection implements ProtoCallback {
        private final Map<Integer, ServerRpcController> activeRpcs = new ConcurrentHashMap<>();
        private ParticipantId loggedInUser;
        private final ServerRpcProvider provider;

        Connection(ParticipantId loggedInUser, ServerRpcProvider provider) {
            this.loggedInUser = loggedInUser;
            this.provider = provider;
        }

        protected void expectMessages(MessageExpectingChannel channel) {
            for (RegisteredServiceMethod serviceMethod : provider.registeredServices.values()) {
                channel.expectMessage(serviceMethod.service.getRequestPrototype(serviceMethod.method));
                if (LOG.isFineLoggable()) {
                    LOG.fine("Expecting: " + serviceMethod.method.getFullName());
                }
            }
            channel.expectMessage(Rpc.CancelRpc.getDefaultInstance());
        }

        protected abstract void sendMessage(int sequenceNo, Message message);

        private ParticipantId authenticate(String token) {
            if (token == null || token.isEmpty()) {
                return null;
            }
            WebSession session = provider.sessionManager.getSessionFromToken(token);
            return provider.sessionManager.getLoggedInUser(session);
        }

        @Override
        public void message(final int sequenceNo, Message message) {
            final String messageName = "/" + message.getClass().getSimpleName();
            final Timer profilingTimer = Timing.startRequest(messageName);
            if (message instanceof Rpc.CancelRpc) {
                final ServerRpcController controller = activeRpcs.get(sequenceNo);
                if (controller == null) {
                    throw new IllegalStateException("Trying to cancel an RPC that is not active!");
                }
                LOG.info("Cancelling open RPC " + sequenceNo);
                controller.cancel();
            } else if (message instanceof ProtocolAuthenticate) {
                ProtocolAuthenticate authMessage = (ProtocolAuthenticate) message;
                ParticipantId authenticatedAs = authenticate(authMessage.getToken());

                Preconditions.checkArgument(authenticatedAs != null, "Auth token invalid");
                Preconditions.checkState(loggedInUser == null || loggedInUser.equals(authenticatedAs),
                        "Session already authenticated as a different user");

                loggedInUser = authenticatedAs;
                LOG.info("Session authenticated as " + loggedInUser);
                sendMessage(sequenceNo, ProtocolAuthenticationResult.getDefaultInstance());
                if (profilingTimer != null) {
                    Timing.stop(profilingTimer);
                }
            } else if (provider.registeredServices.containsKey(message.getDescriptorForType())) {
                if (activeRpcs.containsKey(sequenceNo)) {
                    throw new IllegalStateException(
                            "Can't invoke a new RPC with a sequence number already in use.");
                }
                final RegisteredServiceMethod serviceMethod =
                        provider.registeredServices.get(message.getDescriptorForType());

                final ServerRpcController controller =
                        new ServerRpcControllerImpl(message, serviceMethod.service, serviceMethod.method,
                                loggedInUser, new RpcCallback<Message>() {
                            @Override
                            public synchronized void run(Message response) {
                                boolean completed = response instanceof Rpc.RpcFinished ||
                                        !serviceMethod.method.getOptions().getExtension(Rpc.isStreamingRpc);
                                if (completed) {
                                    boolean failed = response instanceof Rpc.RpcFinished &&
                                            ((Rpc.RpcFinished) response).getFailed();
                                    if (LOG.isFineLoggable()) {
                                        LOG.fine("RPC " + sequenceNo + " is now finished, failed = " + failed);
                                    }
                                    if (failed) {
                                        LOG.info("error = " + ((Rpc.RpcFinished) response).getErrorText());
                                    }
                                    activeRpcs.remove(sequenceNo);
                                }
                                sendMessage(sequenceNo, response);
                                if (completed && profilingTimer != null) {
                                    Timing.stop(profilingTimer);
                                }
                            }
                        });

                activeRpcs.put(sequenceNo, controller);
                provider.threadPool.execute(controller);
            } else {
                throw new IllegalStateException(
                        "Got expected but unknown message (" + message + ") for sequence: " + sequenceNo);
            }
        }
    }

    public static class WebSocketConnection extends Connection {
        private final WebSocketChannel socketChannel;

        WebSocketConnection(ParticipantId loggedInUser, ServerRpcProvider provider) {
            super(loggedInUser, provider);
            socketChannel = new WebSocketChannelImpl(this);
            LOG.info("New websocket connection set up for user " + loggedInUser);
            expectMessages(socketChannel);
        }

        public void attachSession(Session session) {
            if (socketChannel instanceof WebSocketChannelImpl) {
                ((WebSocketChannelImpl) socketChannel).attach(session);
            }
        }

        public void handleText(String data) {
            socketChannel.handleMessageString(data);
        }

        @Override
        protected void sendMessage(int sequenceNo, Message message) {
            socketChannel.sendMessage(sequenceNo, message);
        }
    }

    public WebSocketConnection createWebSocketConnection(ParticipantId loggedInUser) {
        return new WebSocketConnection(loggedInUser, this);
    }

    public void startWebSocketServer(final Injector injector) {
        try {
            this.guiceInjector = injector;
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
            } catch (Exception t) {
                LOG.warning("Access logging initialization failed; continuing without request log", t);
            }

            // Forwarded headers toggle mirrors legacy flag: network.enable_forwarded_headers
            boolean enableFwd = false;
            boolean strictFwd = false;
            if (config == null) {
                LOG.fine("No Config provided; enable_forwarded_headers=false (forwarded headers disabled).");
            } else {
                try {
                    if (config.hasPath("network.enable_forwarded_headers")) {
                        enableFwd = config.getBoolean("network.enable_forwarded_headers");
                    }
                } catch (ConfigException e) {
                    LOG.warning("Invalid config for network.enable_forwarded_headers; disabling forwarded headers.", e);
                    enableFwd = false;
                }
                try {
                    if (enableFwd && config.hasPath("network.forwarded_headers.strict")) {
                        strictFwd = config.getBoolean("network.forwarded_headers.strict");
                    }
                } catch (ConfigException e) {
                    LOG.warning("Invalid config for network.forwarded_headers.strict; using default (false).", e);
                    strictFwd = false;
                }
            }
            final HttpConfiguration httpConfig = new HttpConfiguration();
            if (enableFwd) {
                if (strictFwd) {
                    // First, strip invalid forwarded headers, then apply standard processing
                    // Strict forwarded-header handling not available; fall back to Jetty defaults
                    httpConfig.addCustomizer(new ForwardedRequestCustomizer());
                    LOG.info("Enabled default forwarded-header handling (no strict pre-filter)");
                } else {
                    httpConfig.addCustomizer(new ForwardedRequestCustomizer());
                    LOG.info("Enabled default forwarded-header handling (Jetty behavior)");
                }
            } else {
                LOG.fine("Forwarded headers disabled");
            }
            // Build connectors for all configured addresses (compat with legacy)
            int added = 0;
            java.util.List<String> invalidAddrs = new java.util.ArrayList<>();
            List<String> addrs = (config != null && config.hasPath("core.http_frontend_addresses"))
                    ? config.getStringList("core.http_frontend_addresses")
                    : java.util.Collections.singletonList("127.0.0.1:9898");
            for (String a : addrs) {
                if (a == null || a.isBlank() || !a.contains(":")) {
                    invalidAddrs.add(String.valueOf(a));
                    continue;
                }
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
            Resource baseResource = null;
            if (resourceBases != null && resourceBases.length > 0) {
                try {
                    ResourceFactory factory = ResourceFactory.of(context);
                    List<Resource> bases = new ArrayList<>();
                    for (String base : resourceBases) {
                        Resource resource = resolveResource(factory, base);
                        if (resource != null) {
                            bases.add(resource);
                        }
                    }
                    if (!bases.isEmpty()) {
                        baseResource = bases.size() == 1 ? bases.get(0) : ResourceFactory.combine(bases);
                        context.setBaseResource(baseResource);
                        try {
                            Resource probe = baseResource.resolve("static/logo.png");
                            LOG.info("Resolved base resource -> static/logo.png exists=" + (probe != null && probe.exists()));
                        } catch (Exception ignore) {
                            // diagnostic only
                        }
                    }
                } catch (Exception e) {
                    LOG.warning("Failed to configure servlet base resources " + Arrays.toString(resourceBases), e);
                }
            }
            this.servletContextHandler = context;
            registerPendingServlets();
            registerPendingFilters();

            // Static resources base uses either configured entries or ./war fallback to serve legacy assets.

            // DefaultServlet mappings with caching semantics
            Resource staticResource = null;
            Resource webclientResource = null;
            try {
                if (baseResource != null) {
                    staticResource = baseResource.resolve("static/");
                    webclientResource = baseResource.resolve("webclient/");
                }
            } catch (Exception ignore) {}
            if ((staticResource == null || !staticResource.exists()) && resourceBases != null) {
                staticResource = resolveResource(ResourceFactory.of(context), "static");
            }
            if ((webclientResource == null || !webclientResource.exists()) && resourceBases != null) {
                webclientResource = resolveResource(ResourceFactory.of(context), "webclient");
            }

            ServletHolder staticHolder = new ServletHolder("jakarta-static", DefaultServlet.class);
            staticHolder.setInitParameter("etags", "true");
            staticHolder.setInitParameter("cacheControl", "public, max-age=31536000, immutable");
            if (resourceBases != null && resourceBases.length > 0) {
                staticHolder.setInitParameter("dirAllowed", "false");
            }
            if (staticResource != null && staticResource.exists()) {
                staticHolder.setInitParameter("baseResource", staticResource.getURI().toString());
                LOG.info("Serving /static from " + staticResource);
            } else if (baseResource != null) {
                staticHolder.setInitParameter("baseResource", baseResource.getURI().toString());
                staticHolder.setInitParameter("relativeResourceBase", "static/");
                LOG.warning("Falling back to relative static/ under base resource for /static");
            }
            context.addServlet(staticHolder, "/static/*");

            ServletHolder webclientHolder = new ServletHolder("jakarta-webclient", DefaultServlet.class);
            webclientHolder.setInitParameter("etags", "true");
            webclientHolder.setInitParameter("cacheControl", "no-cache, no-store, must-revalidate");
            if (webclientResource != null && webclientResource.exists()) {
                webclientHolder.setInitParameter("baseResource", webclientResource.getURI().toString());
                LOG.info("Serving /webclient from " + webclientResource);
            } else if (baseResource != null) {
                webclientHolder.setInitParameter("baseResource", baseResource.getURI().toString());
                webclientHolder.setInitParameter("relativeResourceBase", "webclient/");
                LOG.warning("Falling back to relative webclient/ under base resource for /webclient");
            }
            context.addServlet(webclientHolder, "/webclient/*");

            // Minimal Jakarta replacements for server-side GWT services
            addServlet("/webclient/remote_logging", org.waveprotocol.box.server.rpc.RemoteLoggingJakartaServlet.class);
            addServlet(org.waveprotocol.box.stat.StatService.STAT_URL, org.waveprotocol.box.server.stat.StatuszJakartaServlet.class);
            addServlet("/metrics", org.waveprotocol.box.server.stat.MetricsPrometheusServlet.class);

            // Register metrics, security and caching filters programmatically (Jakarta variants)
            try {
                // Micrometer timing filter first so it wraps the rest
                org.eclipse.jetty.ee10.servlet.FilterHolder metrics = new org.eclipse.jetty.ee10.servlet.FilterHolder(new org.waveprotocol.box.server.stat.MetricsHttpFilter());
                context.addFilter(metrics, "/*", java.util.EnumSet.allOf(DispatcherType.class));

                Config effectiveCfg = (this.config != null) ? this.config : ConfigFactory.empty();
                org.eclipse.jetty.ee10.servlet.FilterHolder sec = new org.eclipse.jetty.ee10.servlet.FilterHolder(new SecurityHeadersFilter(effectiveCfg));
                context.addFilter(sec, "/*", java.util.EnumSet.allOf(DispatcherType.class));

                org.eclipse.jetty.ee10.servlet.FilterHolder cacheStatic = new org.eclipse.jetty.ee10.servlet.FilterHolder(new StaticCacheFilter());
                context.addFilter(cacheStatic, "/static/*", java.util.EnumSet.allOf(DispatcherType.class));

                org.eclipse.jetty.ee10.servlet.FilterHolder cacheNo = new org.eclipse.jetty.ee10.servlet.FilterHolder(new NoCacheFilter());
                context.addFilter(cacheNo, "/webclient/*", java.util.EnumSet.allOf(DispatcherType.class));

                // Request scope captures session context for Timing/Statusz compatibility.
                org.eclipse.jetty.ee10.servlet.FilterHolder scopeHolder =
                        new org.eclipse.jetty.ee10.servlet.FilterHolder(new RequestScopeFilter(sessionManager));
                context.addFilter(scopeHolder, "/*", java.util.EnumSet.allOf(DispatcherType.class));

                boolean enableProfiling = false;
                try {
                    if (effectiveCfg.hasPath("core.enable_profiling")) {
                        enableProfiling = effectiveCfg.getBoolean("core.enable_profiling");
                    }
                } catch (ConfigException ce) {
                    LOG.warning("Invalid config for core.enable_profiling; defaulting to disabled.", ce);
                    enableProfiling = false;
                }
                if (enableProfiling) {
                    org.eclipse.jetty.ee10.servlet.FilterHolder timingHolder =
                            new org.eclipse.jetty.ee10.servlet.FilterHolder(new TimingFilter());
                    context.addFilter(timingHolder, "/*", java.util.EnumSet.allOf(DispatcherType.class));
                }
            } catch (Exception ex) {
                LOG.warning("Failed to register Jakarta security/profiling filters", ex);
            }

            // Register Jakarta WebSocket endpoint programmatically with DI configurator
            JakartaWebSocketServletContainerInitializer.configure(context, (ctx, container) -> {
                ServerEndpointConfig sec = ServerEndpointConfig.Builder
                        .create(org.waveprotocol.box.server.rpc.jakarta.WaveWebSocketEndpoint.class, "/socket")
                        .configurator(new ServerEndpointConfig.Configurator() {
                            @Override
                            public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response) {
                                HttpSession httpSession = (HttpSession) request.getHttpSession();
                                if (httpSession != null) {
                                    config.getUserProperties().put(HttpSession.class.getName(), httpSession);
                                }
                            }

                            @Override
                            public <T> T getEndpointInstance(Class<T> endpointClass) {
                                try {
                                    T ep = endpointClass.getDeclaredConstructor().newInstance();
                                    var method = endpointClass.getMethod("setDependencies", ServerRpcProvider.class);
                                    method.invoke(ep, ServerRpcProvider.this);
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
                                    table.put(e.getKey(), new Object[]{e.getValue().service, e.getValue().method});
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

    public ServletHolder addServlet(String urlPattern, Class<?> servlet,
                                    @Nullable Map<String, String> initParams) {
        try {
            if (!jakarta.servlet.http.HttpServlet.class.isAssignableFrom(servlet)) {
                LOG.warning("[Jakarta] Skipping registration of non‑Jakarta servlet: " + servlet.getName() + " at " + urlPattern);
                return new ServletHolder(DefaultServlet.class);
            }
            @SuppressWarnings("unchecked")
            Class<? extends HttpServlet> httpCls = (Class<? extends HttpServlet>) servlet;
            Map<String, String> paramsCopy = (initParams == null || initParams.isEmpty()) ? null : new java.util.HashMap<>(initParams);
            if (servletContextHandler == null || guiceInjector == null) {
                pendingServlets.add(new PendingServlet(urlPattern, httpCls, paramsCopy));
                if (LOG.isFineLoggable()) LOG.fine("[Jakarta] addServlet deferred (context not initialized): " + urlPattern + " -> " + servlet.getName());
                return new ServletHolder(httpCls);
            }
            try {
                HttpServlet instance = guiceInjector.getInstance(httpCls);
                ServletHolder holder = new ServletHolder(instance);
                if (paramsCopy != null) {
                    holder.setInitParameters(paramsCopy);
                }
                servletContextHandler.addServlet(holder, urlPattern);
                if (LOG.isFineLoggable()) LOG.fine("[Jakarta] addServlet " + urlPattern + " -> " + servlet.getName());
                return holder;
            } catch (Throwable t) {
                LOG.warning("Failed to instantiate servlet '" + servlet.getName() + "' via Guice; falling back to default constructor", t);
                ServletHolder holder = new ServletHolder(httpCls);
                if (paramsCopy != null) {
                    holder.setInitParameters(paramsCopy);
                }
                servletContextHandler.addServlet(holder, urlPattern);
                return holder;
            }
        } catch (Throwable t) {
            LOG.warning("Failed to add servlet '" + servlet + "' at '" + urlPattern + "'", t);
            return new ServletHolder(DefaultServlet.class);
        }
    }

    public ServletHolder addServlet(String urlPattern, Class<?> servlet) {
        return addServlet(urlPattern, servlet, null);
    }

    public void addFilter(String urlPattern, Class<?> filter) {
        try {
            if (!jakarta.servlet.Filter.class.isAssignableFrom(filter)) {
                LOG.warning("[Jakarta] Skipping registration of non‑Jakarta filter: " + filter.getName() + " at " + urlPattern);
                return;
            }
            @SuppressWarnings("unchecked")
            Class<? extends Filter> filt = (Class<? extends Filter>) filter;
            if (servletContextHandler == null || guiceInjector == null) {
                pendingFilters.add(new PendingFilter(urlPattern, filt));
                if (LOG.isFineLoggable()) LOG.fine("[Jakarta] addFilter deferred (context not initialized): " + urlPattern + " -> " + filter.getName());
                return;
            }
            try {
                Filter instance = guiceInjector.getInstance(filt);
                org.eclipse.jetty.ee10.servlet.FilterHolder holder = new org.eclipse.jetty.ee10.servlet.FilterHolder(instance);
                servletContextHandler.addFilter(holder, urlPattern, java.util.EnumSet.allOf(DispatcherType.class));
                if (LOG.isFineLoggable()) LOG.fine("[Jakarta] addFilter " + urlPattern + " -> " + filter.getName());
            } catch (Throwable t) {
                LOG.warning("Failed to instantiate filter '" + filter.getName() + "' via Guice; skipping", t);
            }
        } catch (Throwable t) {
            LOG.warning("Failed to add filter '" + filter + "' at '" + urlPattern + "'", t);
        }
    }

    /**
     * Transparent proxying is not implemented on the Jakarta path yet.
     * Throwing here avoids silent misconfiguration during the migration stage.
     */
    @Deprecated
    public void addTransparentProxy(String urlPattern, String proxyTo, String prefix) {
        String msg = "Transparent proxying is not supported on the Jakarta path yet: " +
                urlPattern + " -> " + proxyTo + " (prefix " + prefix + ")";
        LOG.warning(msg);
        throw new UnsupportedOperationException(msg);
    }

    /**
     * Returns the first bound address for the /socket endpoint. Throws if the
     * server is not started or no connectors are bound to avoid returning null.
     */
    public SocketAddress getWebSocketAddress() {
        if (httpServer == null) {
            throw new IllegalStateException("Jakarta server not started; no WebSocket address available");
        }
        for (var conn : httpServer.getConnectors()) {
            if (conn instanceof ServerConnector) {
                ServerConnector sc = (ServerConnector) conn;
                return new InetSocketAddress(sc.getHost(), sc.getLocalPort());
            }
        }
        throw new IllegalStateException("No connectors bound; WebSocket address unavailable");
    }

    public void stopServer() throws IOException {
        try {
            if (httpServer != null) httpServer.stop();
        } catch (Exception e) {
            LOG.warning("Error stopping Jakarta http server", e);
        }
    }

    /**
     * Returns bound ws addresses for testing/logging.
     */
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

    private void registerPendingServlets() {
        if (servletContextHandler == null || guiceInjector == null || pendingServlets.isEmpty()) {
            return;
        }
        for (PendingServlet pending : new ArrayList<>(pendingServlets)) {
            try {
                addServlet(pending.urlPattern, pending.servletClass, pending.initParams);
            } catch (Throwable t) {
                LOG.warning("Failed to register deferred servlet '" + pending.urlPattern + "'", t);
            }
        }
        pendingServlets.clear();
    }

    private void registerPendingFilters() {
        if (servletContextHandler == null || guiceInjector == null || pendingFilters.isEmpty()) {
            return;
        }
        for (PendingFilter pending : new ArrayList<>(pendingFilters)) {
            try {
                addFilter(pending.urlPattern, pending.filterClass);
            } catch (Throwable t) {
                LOG.warning("Failed to register deferred filter '" + pending.urlPattern + "'", t);
            }
        }
        pendingFilters.clear();
    }

    /**
     * Exposes the registered service map for per-connection dispatchers.
     */
    public Map<Descriptors.Descriptor, RegisteredServiceMethod> getRegisteredServices() {
        return registeredServices;
    }

    /**
     * Exposes the executor used for running RPC controllers.
     */
    public Executor getThreadPool() {
        return threadPool;
    }

    /**
     * Exposes the session manager for authentication.
     */
    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public void registerService(Service service) {
        for (MethodDescriptor methodDescriptor : service.getDescriptorForType().getMethods()) {
            registeredServices.put(methodDescriptor.getInputType(),
                    new RegisteredServiceMethod(service, methodDescriptor));
        }
    }

    /**
     * Internal container mapping input types to handlers, mirrored from legacy path.
     */
    static class RegisteredServiceMethod {
        final Service service;
        final MethodDescriptor method;

        RegisteredServiceMethod(Service service, MethodDescriptor method) {
            this.service = service;
            this.method = method;
        }
    }

    private Resource resolveResource(ResourceFactory factory, String base) {
        if (base == null || base.isBlank()) {
            return null;
        }
        Resource resource = null;
        try {
            resource = factory.newResource(base);
            if (resource != null && resource.exists()) {
                return resource;
            }
        } catch (Exception e) {
            if (LOG.isFineLoggable()) {
                LOG.fine("Failed to resolve resourceBase '" + base + "' via factory: " + e);
            }
        }
        try {
            Path candidate = Paths.get(base);
            if (!candidate.isAbsolute()) {
                candidate = candidate.normalize();
            }
            if (!Files.exists(candidate)) {
                Path waveRelative = Paths.get("wave").resolve(base.startsWith("./") ? base.substring(2) : base);
                if (Files.exists(waveRelative)) {
                    candidate = waveRelative;
                }
            }
            if (Files.exists(candidate)) {
                Resource fallback = factory.newResource(candidate.toUri());
                if (fallback != null && fallback.exists()) {
                    LOG.info("Resolved resource base '" + base + "' to " + candidate.toAbsolutePath());
                    return fallback;
                }
            }
        } catch (Exception ex) {
            if (LOG.isFineLoggable()) {
                LOG.fine("Fallback resolution failed for resource base '" + base + "': " + ex);
            }
        }
        LOG.warning("Skipping resource base '" + base + "' (not found)");
        return null;
    }

    private static final class PendingServlet {
        final String urlPattern;
        final Class<? extends HttpServlet> servletClass;
        final Map<String, String> initParams;

        PendingServlet(String urlPattern, Class<? extends HttpServlet> servletClass, Map<String, String> initParams) {
            this.urlPattern = urlPattern;
            this.servletClass = servletClass;
            this.initParams = initParams;
        }
    }

    private static final class PendingFilter {
        final String urlPattern;
        final Class<? extends Filter> filterClass;

        PendingFilter(String urlPattern, Class<? extends Filter> filterClass) {
            this.urlPattern = urlPattern;
            this.filterClass = filterClass;
        }
    }

    /**
     * Deprecated: Jakarta endpoint now dispatches directly per-connection.
     */
    @Deprecated
    public void receiveWebSocketMessage(int sequenceNo, com.google.protobuf.Message message) {
        LOG.info("receiveWebSocketMessage is deprecated on Jakarta path; no-op");
    }
}
