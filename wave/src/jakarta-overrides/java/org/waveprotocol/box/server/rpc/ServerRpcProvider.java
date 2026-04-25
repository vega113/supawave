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
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ResourceServlet;
import org.eclipse.jetty.ee10.servlet.ServletContextHandler;
import org.eclipse.jetty.ee10.websocket.jakarta.server.config.JakartaWebSocketServletContainerInitializer;
import org.eclipse.jetty.server.handler.gzip.GzipHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
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
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.annotation.MultipartConfig;
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
import jakarta.websocket.server.ServerContainer;
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
    private static final long DEFAULT_WEBSOCKET_MAX_IDLE_TIME_MS = 0L;
    private static final int DEFAULT_WEBSOCKET_MAX_MESSAGE_SIZE_MB = 2;
    private final Config config;
    private final SessionHandler sessionHandler;
    final SessionManager sessionManager;
    final Executor threadPool;
    private final boolean sslEnabled;
    private final String sslKeystorePath;
    private final String sslKeystorePassword;
    final Map<Descriptors.Descriptor, RegisteredServiceMethod> registeredServices = new ConcurrentHashMap<>();
    private Server httpServer;
    private ServletContextHandler servletContextHandler;
    private Injector guiceInjector;
    private final java.util.List<PendingServlet> pendingServlets = new java.util.ArrayList<>();
    private final java.util.List<PendingFilter> pendingFilters = new java.util.ArrayList<>();
    private final String[] resourceBases;
    /** Addresses supplied via constructor; used as fallback when config has no http_frontend_addresses. */
    private final InetSocketAddress[] constructorHttpAddresses;

    public ServerRpcProvider(InetSocketAddress[] httpAddresses,
                             String[] resourceBases, Executor threadPool,
                             SessionManager sessionManager,
                             SessionHandler sessionHandler, String sessionStoreDir,
                             boolean sslEnabled, String sslKeystorePath, String sslKeystorePassword,
                             boolean enableForwardedHeaders) {
        this.config = null;
        this.constructorHttpAddresses = httpAddresses != null ? java.util.Arrays.copyOf(httpAddresses, httpAddresses.length) : null;
        this.threadPool = threadPool;
        this.sessionManager = sessionManager;
        this.sessionHandler = sessionHandler;
        this.sslEnabled = sslEnabled;
        this.sslKeystorePath = sslKeystorePath;
        this.sslKeystorePassword = sslKeystorePassword;
        this.resourceBases = resourceBases != null ? resourceBases : new String[]{"./war"};
    }

    public ServerRpcProvider(InetSocketAddress[] httpAddresses,
                             String[] resourceBases, SessionManager sessionManager,
                             SessionHandler sessionHandler, String sessionStoreDir,
                             boolean sslEnabled, String sslKeystorePath, String sslKeystorePassword,
                             Executor executor) {
        this.config = null;
        this.constructorHttpAddresses = httpAddresses != null ? java.util.Arrays.copyOf(httpAddresses, httpAddresses.length) : null;
        this.threadPool = executor;
        this.sessionManager = sessionManager;
        this.sessionHandler = sessionHandler;
        this.sslEnabled = sslEnabled;
        this.sslKeystorePath = sslKeystorePath;
        this.sslKeystorePassword = sslKeystorePassword;
        this.resourceBases = resourceBases != null ? resourceBases : new String[]{"./war"};
    }

    @Inject
    public ServerRpcProvider(Config config,
                             SessionManager sessionManager, SessionHandler sessionHandler,
                             @org.waveprotocol.box.server.executor.ExecutorAnnotations.ClientServerExecutor Executor executorService) {
        this.config = config;
        this.constructorHttpAddresses = null;
        this.sessionHandler = sessionHandler;
        this.sessionManager = sessionManager;
        this.threadPool = executorService;
        this.sslEnabled = config.getBoolean("security.enable_ssl");
        this.sslKeystorePath = config.getString("security.ssl_keystore_path");
        this.sslKeystorePassword = config.getString("security.ssl_keystore_password");
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

                // Prefer the user established during the HTTP upgrade handshake to
                // avoid Jetty creating a duplicate session when getManagedSession is
                // called outside an HTTP request context (causes HTTP 400 on the next
                // HTTP request).  The Jakarta WebSocket endpoint already verified the
                // HTTP session during upgrade, so loggedInUser is trusted here.
                ParticipantId authenticatedAs = (loggedInUser != null)
                        ? loggedInUser
                        : authenticate(authMessage.getToken());

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

        public void detachSession() {
            if (socketChannel instanceof WebSocketChannelImpl) {
                ((WebSocketChannelImpl) socketChannel).detach();
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

    static void configureWebSocketContainer(ServerContainer container, @Nullable Config config) {
        long idleTimeoutMs = DEFAULT_WEBSOCKET_MAX_IDLE_TIME_MS;
        int maxMessageSizeMb = DEFAULT_WEBSOCKET_MAX_MESSAGE_SIZE_MB;

        if (config != null) {
            if (config.hasPath("network.websocket_max_idle_time")) {
                idleTimeoutMs = config.getLong("network.websocket_max_idle_time");
            }
            if (config.hasPath("network.websocket_max_message_size")) {
                maxMessageSizeMb = config.getInt("network.websocket_max_message_size");
            }
        }

        container.setDefaultMaxSessionIdleTimeout(idleTimeoutMs);
        container.setDefaultMaxTextMessageBufferSize(maxMessageSizeMb * 1024 * 1024);
    }

    static List<Connector> buildConnectors(Server httpServer,
                                           InetSocketAddress[] httpAddresses,
                                           boolean sslEnabled,
                                           String sslKeystorePath,
                                           String sslKeystorePassword,
                                           boolean enableForwardedHeaders) {
        List<Connector> connectors = new ArrayList<>();
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        if (enableForwardedHeaders) {
            httpConfig.addCustomizer(new ForwardedRequestCustomizer());
        }

        SslContextFactory.Server sslContextFactory = null;
        HttpConfiguration httpsConfig = null;
        if (sslEnabled) {
            Preconditions.checkState(sslKeystorePath != null && !sslKeystorePath.isEmpty(),
                    "SSL Keystore path left blank");
            Preconditions.checkState(sslKeystorePassword != null && !sslKeystorePassword.isEmpty(),
                    "SSL Keystore password left blank");

            String[] excludeCiphers = {"SSL_RSA_EXPORT_WITH_RC4_40_MD5", "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                    "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA", "SSL_RSA_WITH_DES_CBC_SHA",
                    "SSL_DHE_RSA_WITH_DES_CBC_SHA", "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
                    "SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA", "TLS_DHE_RSA_WITH_AES_256_CBC_SHA"};

            sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(sslKeystorePath);
            sslContextFactory.setKeyStorePassword(sslKeystorePassword);
            sslContextFactory.setRenegotiationAllowed(false);
            sslContextFactory.setExcludeCipherSuites(excludeCiphers);
            sslContextFactory.setIncludeProtocols("TLSv1.2", "TLSv1.3");
            sslContextFactory.setWantClientAuth(true);

            httpsConfig = new HttpConfiguration(httpConfig);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());
            if (enableForwardedHeaders) {
                httpsConfig.addCustomizer(new ForwardedRequestCustomizer());
            }
        }

        for (InetSocketAddress address : httpAddresses) {
            ServerConnector connector;
            if (sslEnabled) {
                connector = new ServerConnector(
                        httpServer,
                        new SslConnectionFactory(sslContextFactory, "http/1.1"),
                        new HttpConnectionFactory(httpsConfig));
            } else {
                connector = new ServerConnector(httpServer, new HttpConnectionFactory(httpConfig));
            }
            connector.setHost(address.getAddress().getHostAddress());
            connector.setPort(address.getPort());
            connector.setIdleTimeout(0);
            connectors.add(connector);
        }
        return connectors;
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
            }
            java.util.List<String> invalidAddrs = new java.util.ArrayList<>();
            java.util.List<InetSocketAddress> validAddrs = new java.util.ArrayList<>();
            // Prefer constructor-supplied addresses when config has no http_frontend_addresses.
            if (constructorHttpAddresses != null && constructorHttpAddresses.length > 0
                    && (config == null || !config.hasPath("core.http_frontend_addresses"))) {
                for (InetSocketAddress addr : constructorHttpAddresses) {
                    if (addr != null) validAddrs.add(addr);
                }
            }
            if (validAddrs.isEmpty()) {
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
                    validAddrs.add(new InetSocketAddress(host, port));
                } catch (Exception ex) {
                    invalidAddrs.add(a);
                }
            }
            if (!invalidAddrs.isEmpty()) {
                LOG.warning("Ignoring invalid core.http_frontend_addresses entries: " + invalidAddrs +
                        "; expected format 'host:port' (e.g., 127.0.0.1:9898)");
            }
            if (validAddrs.isEmpty()) {
                String def = (config != null && config.hasPath("core.default_http_frontend_address"))
                        ? config.getString("core.default_http_frontend_address") : "127.0.0.1:9898";
                try {
                    int idx = def.lastIndexOf(':');
                    String host = def.substring(0, idx);
                    int port = Integer.parseInt(def.substring(idx + 1));
                    validAddrs.add(new InetSocketAddress(host, port));
                    LOG.info("No valid addresses configured; using default " + host + ":" + port);
                } catch (Exception ex) {
                    LOG.severe("Invalid core.default_http_frontend_address '" + def + "'; using 127.0.0.1:9898", ex);
                    validAddrs.add(new InetSocketAddress("127.0.0.1", 9898));
                }
            }
            } // end if (validAddrs.isEmpty()) for config-based parsing

            List<Connector> boundConnectors = buildConnectors(httpServer,
                    validAddrs.toArray(new InetSocketAddress[0]),
                    sslEnabled,
                    sslKeystorePath,
                    sslKeystorePassword,
                    enableFwd);
            for (Connector connector : boundConnectors) {
                httpServer.addConnector(connector);
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

            // Static resources: resolve sub-directories for /static, /webclient, and
            // maintained J2CL assets.
            Resource staticResource = null;
            Resource webclientResource = null;
            Resource j2clSearchResource = null;
            Resource j2clDebugResource = null;
            Resource j2clResource = null;
            try {
                if (baseResource != null) {
                    staticResource = baseResource.resolve("static/");
                    webclientResource = baseResource.resolve("webclient/");
                    j2clSearchResource = baseResource.resolve("j2cl-search/");
                    j2clDebugResource = baseResource.resolve("j2cl-debug/");
                    j2clResource = baseResource.resolve("j2cl/");
                }
            } catch (Exception ignore) {}
            if ((staticResource == null || !staticResource.exists()) && resourceBases != null) {
                staticResource = resolveResource(ResourceFactory.of(context), "static");
            }
            if ((webclientResource == null || !webclientResource.exists()) && resourceBases != null) {
                // Constrain fallback to configured resourceBases only — process-relative
                // resolution via ResourceFactory.of(context) can accidentally match unrelated
                // directories (e.g. the source webclient/ tree at repo root).
                ResourceFactory rf = ResourceFactory.of(context);
                for (String base : resourceBases) {
                    Resource baseRes = resolveResource(rf, base);
                    if (baseRes == null || !baseRes.exists()) continue;
                    Resource candidate = baseRes.resolve("webclient/");
                    if (candidate != null && candidate.exists()) {
                        webclientResource = candidate;
                        break;
                    }
                }
            }
            // J2CL sidecar resources are opt-in build artifacts: only serve them if they
            // were explicitly built under war/j2cl-*.  No resolveResource fallback here —
            // that fallback does relative-path resolution which would incorrectly match the
            // j2cl/ Maven source directory at repo root when artifacts are absent.

            // Jetty 12 EE10: use ResourceServlet (not DefaultServlet) for non-root
            // path mappings.  DefaultServlet is reserved for the "/" mapping and will
            // log warnings + mis-resolve paths when mounted on /static/* or /webclient/*.
            // ResourceServlet defaults pathInfoOnly=true, which strips the servlet-path
            // prefix before resolving against the baseResource, preventing double-path
            // lookups that caused buffer-release errors in the previous DefaultServlet
            // configuration.
            ServletHolder staticHolder =
                new ServletHolder("jakarta-static", ResourceServlet.class);
            staticHolder.setInitParameter("etags", "true");
            staticHolder.setInitParameter("cacheControl",
                "public, max-age=31536000, immutable");
            staticHolder.setInitParameter("dirAllowed", "false");
            if (staticResource != null && staticResource.exists()) {
                staticHolder.setInitParameter("baseResource",
                    staticResource.getURI().toString());
                LOG.info("Serving /static from " + staticResource);
            } else if (baseResource != null) {
                Resource fallback = baseResource.resolve("static/");
                if (fallback != null && fallback.exists()) {
                    staticHolder.setInitParameter("baseResource",
                        fallback.getURI().toString());
                } else {
                    staticHolder.setInitParameter("baseResource",
                        baseResource.getURI().toString());
                }
                LOG.warning(
                    "Falling back to base resource for /static: "
                        + staticHolder.getInitParameter("baseResource"));
            }
            context.addServlet(staticHolder, "/static/*");

            Resource resolvedWebclientResource = null;
            if (webclientResource != null && webclientResource.exists()) {
                resolvedWebclientResource = webclientResource;
            } else if (baseResource != null) {
                Resource fallback = baseResource.resolve("webclient/");
                if (fallback != null && fallback.exists()) {
                    resolvedWebclientResource = fallback;
                    LOG.warning("Falling back to constrained webclient resource for /webclient: "
                        + fallback.getURI().toString());
                }
            }

            if (resolvedWebclientResource != null) {
                ServletHolder webclientHolder =
                    new ServletHolder("jakarta-webclient", ResourceServlet.class);
                webclientHolder.setInitParameter("etags", "true");
                webclientHolder.setInitParameter("cacheControl",
                    "no-cache, no-store, must-revalidate");
                webclientHolder.setInitParameter("dirAllowed", "false");
                webclientHolder.setInitParameter("baseResource",
                    resolvedWebclientResource.getURI().toString());
                LOG.info("Serving /webclient from " + resolvedWebclientResource);
                context.addServlet(webclientHolder, "/webclient/*");
            } else {
                LOG.warning("No webclient resource found; /webclient will return 404");
                ServletHolder missingWebclientHolder =
                    new ServletHolder("jakarta-webclient-missing", new HttpServlet() {
                        @Override
                        protected void service(
                            jakarta.servlet.http.HttpServletRequest req,
                            jakarta.servlet.http.HttpServletResponse resp) throws IOException {
                            resp.sendError(
                                jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND,
                                "Webclient resources are not available on this server.");
                        }
                    });
                context.addServlet(missingWebclientHolder, "/webclient/*");
            }

            addJ2clServlet(context, "jakarta-j2cl-search", j2clSearchResource, "/j2cl-search", "/j2cl-search/*");
            addJ2clServlet(context, "jakarta-j2cl-debug", j2clDebugResource, "/j2cl-debug", "/j2cl-debug/*");
            addJ2clServlet(context, "jakarta-j2cl", j2clResource, "/j2cl", "/j2cl/*");

            addServlet(RemoteLoggingJakartaServlet.REMOTE_LOGGING_URL, org.waveprotocol.box.server.rpc.RemoteLoggingJakartaServlet.class);
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
                context.addFilter(cacheNo, "/j2cl-search/*", java.util.EnumSet.allOf(DispatcherType.class));
                context.addFilter(cacheNo, "/j2cl-debug/*", java.util.EnumSet.allOf(DispatcherType.class));
                context.addFilter(cacheNo, "/j2cl/*", java.util.EnumSet.allOf(DispatcherType.class));

                // JWT session restoration: when the HTTP session is lost (e.g. after a deploy)
                // but the browser still has a valid wave-session-jwt cookie, this filter
                // re-establishes the HTTP session so the user stays logged in.
                try {
                    org.waveprotocol.box.server.authentication.jwt.JwtKeyRing keyRing =
                            injector.getInstance(org.waveprotocol.box.server.authentication.jwt.JwtKeyRing.class);
                    org.eclipse.jetty.ee10.servlet.FilterHolder jwtRestore =
                            new org.eclipse.jetty.ee10.servlet.FilterHolder(
                                    new org.waveprotocol.box.server.authentication.jwt.JwtSessionRestorationFilter(
                                            sessionManager, keyRing));
                    context.addFilter(jwtRestore, "/*", java.util.EnumSet.allOf(DispatcherType.class));
                } catch (Exception jwtEx) {
                    LOG.warning("Failed to register JWT session restoration filter; "
                            + "sessions will not auto-restore after deploys", jwtEx);
                }

                // MDC logging: populates participantId/sessionId on every request for Grafana/Loki.
                org.eclipse.jetty.ee10.servlet.FilterHolder mdcHolder =
                        new org.eclipse.jetty.ee10.servlet.FilterHolder(new MdcLoggingFilter());
                context.addFilter(mdcHolder, "/*", java.util.EnumSet.allOf(DispatcherType.class));

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
                configureWebSocketContainer(container, config);
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
                    sb.append(sslEnabled ? "wss://" : "ws://")
                            .append(sc.getHost()).append(":").append(sc.getLocalPort()).append("/socket");
                }
            }
            LOG.info(sb.toString());
        } catch (Exception e) {
            LOG.severe("Fatal error starting Jakarta http server.", e);
            throw new RuntimeException("Failed to start Jakarta http server", e);
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
            configureWebSocketContainer(sc, config);
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
                configureMultipartIfNeeded(holder, httpCls);
                if (paramsCopy != null) {
                    holder.setInitParameters(paramsCopy);
                }
                servletContextHandler.addServlet(holder, urlPattern);
                if (LOG.isFineLoggable()) LOG.fine("[Jakarta] addServlet " + urlPattern + " -> " + servlet.getName());
                return holder;
            } catch (Throwable t) {
                // Only fall back to Jetty's default-constructor instantiation when
                // the servlet actually has a no-arg constructor.  Servlets that rely
                // on @Inject (like UserRegistrationServlet) have no default
                // constructor; silently registering a Class-based ServletHolder for
                // them causes a NoSuchMethodException at request time.
                boolean hasDefaultCtor = false;
                try {
                    httpCls.getConstructor();
                    hasDefaultCtor = true;
                } catch (NoSuchMethodException ignored) {}
                if (hasDefaultCtor) {
                    LOG.warning("Failed to instantiate servlet '" + servlet.getName()
                        + "' via Guice; falling back to default constructor", t);
                    ServletHolder holder = new ServletHolder(httpCls);
                    configureMultipartIfNeeded(holder, httpCls);
                    if (paramsCopy != null) {
                        holder.setInitParameters(paramsCopy);
                    }
                    servletContextHandler.addServlet(holder, urlPattern);
                    return holder;
                } else {
                    LOG.severe("Failed to instantiate servlet '" + servlet.getName()
                        + "' via Guice and no default constructor available; "
                        + "servlet will NOT be registered at " + urlPattern, t);
                    return new ServletHolder(DefaultServlet.class);
                }
            }
        } catch (Throwable t) {
            LOG.warning("Failed to add servlet '" + servlet + "' at '" + urlPattern + "'", t);
            return new ServletHolder(DefaultServlet.class);
        }
    }

    public ServletHolder addServlet(String urlPattern, Class<?> servlet) {
        return addServlet(urlPattern, servlet, null);
    }

    private static void addJ2clServlet(
            ServletContextHandler context,
            String holderName,
            @Nullable Resource resource,
            String displayPath,
            String mapping) {
        if (resource == null || !resource.exists()) {
            // Return explicit 404 so requests to this path fail clearly rather than
            // falling through to the root app and returning 200 HTML.
            String msg = displayPath + " artifacts not built — run the sidecar build first";
            ServletHolder notFound = new ServletHolder(holderName + "-404", new HttpServlet() {
                @Override
                protected void service(
                        jakarta.servlet.http.HttpServletRequest req,
                        jakarta.servlet.http.HttpServletResponse resp)
                        throws java.io.IOException {
                    resp.sendError(jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND, msg);
                }
            });
            context.addServlet(notFound, mapping);
            LOG.info(displayPath + " artifacts absent — serving 404 for " + mapping);
            return;
        }
        ServletHolder holder = new ServletHolder(holderName, ResourceServlet.class);
        holder.setInitParameter("etags", "true");
        holder.setInitParameter("cacheControl", "no-cache, no-store, must-revalidate");
        holder.setInitParameter("dirAllowed", "false");
        holder.setInitParameter("baseResource", resource.getURI().toString());
        context.addServlet(holder, mapping);
        LOG.info("Serving " + displayPath + " from " + resource);
    }

    private void configureMultipartIfNeeded(ServletHolder holder, Class<? extends HttpServlet> servletClass) {
        MultipartConfig multipartConfig = servletClass.getAnnotation(MultipartConfig.class);
        if (multipartConfig == null) {
            return;
        }
        MultipartConfigElement multipartConfigElement =
            new MultipartConfigElement(
                multipartConfig.location(),
                multipartConfig.maxFileSize(),
                multipartConfig.maxRequestSize(),
                multipartConfig.fileSizeThreshold());
        holder.getRegistration().setMultipartConfig(multipartConfigElement);
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

    public void stopServer() {
        try {
            if (httpServer != null) httpServer.stop();
        } catch (Exception e) {
            LOG.warning("Error stopping Jakarta http server", e);
        }
    }

    /**
     * Blocks the launcher thread until the Jakarta HTTP server is stopped.
     *
     * <p>The staged native-packager launcher expects the main Java process to
     * stay alive after Jetty readiness. Without an explicit join, lifecycle
     * ownership depends on Jetty thread details rather than the launcher
     * contract used by worktree smoke and browser verification flows.</p>
     */
    public void joinServer() throws InterruptedException {
        if (httpServer == null) {
            throw new IllegalStateException("Jakarta server not started; cannot join");
        }
        httpServer.join();
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
