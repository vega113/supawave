/** Jakarta variant of RobotConnector. */
package org.waveprotocol.box.server.robots.passive;

import com.google.inject.Inject;
import com.google.wave.api.InvalidRequestException;
import com.google.wave.api.OperationRequest;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.RobotSerializer;
import com.google.wave.api.impl.EventMessageBundle;
import com.google.wave.api.robot.CapabilityFetchException;
import com.google.wave.api.robot.RobotCapabilitiesParser;
import com.google.wave.api.robot.RobotConnection;
import com.google.wave.api.robot.RobotConnectionException;
import org.waveprotocol.box.server.account.RobotAccountData;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import org.waveprotocol.box.server.robots.passive.Robot;
import org.waveprotocol.wave.util.logging.Log;

import java.net.URI;
import java.util.Collections;
import java.util.List;

public class RobotConnector implements RobotCapabilityFetcher {
  private static final Log LOG = Log.get(RobotConnector.class);

  private final RobotConnection connection;
  private final RobotSerializer serializer;

  @Inject
  public RobotConnector(RobotConnection connection, RobotSerializer serializer) {
    this.connection = connection;
    this.serializer = serializer;
  }

  public List<OperationRequest> sendMessageBundle(EventMessageBundle bundle, Robot robot,
                                                  ProtocolVersion version) {
    String serializedBundle = serializer.serialize(bundle, version);
    String storedUrl = robot.getAccount().getUrl();
    String robotUrl = robotRpcUrl(storedUrl);
    LOG.info("Sending: " + serializedBundle + " to " + robotUrl);
    try {
      String response = connection.postJson(robotUrl, serializedBundle);
      LOG.info("Received: " + response + " from " + robotUrl);
      return serializer.deserializeOperations(response);
    } catch (RobotConnectionException e) {
      LOG.info("Failed to receive a response from " + robotUrl, e);
    } catch (InvalidRequestException e) {
      LOG.info("Failed to deserialize passive API response", e);
    }
    return null;
  }

  @Override
  public RobotAccountData fetchCapabilities(RobotAccountData account, String activeApiUrl)
      throws CapabilityFetchException {
    RobotCapabilitiesParser parser = new RobotCapabilitiesParser(
        robotBaseUrl(account.getUrl()) + Robot.CAPABILITIES_URL, connection, activeApiUrl);
    RobotCapabilities capabilities = new RobotCapabilities(
        parser.getCapabilities(), parser.getCapabilitiesHash(), parser.getProtocolVersion(),
        parser.getRpcServerUrl(), true);
    long updatedAtMillis = Math.max(account.getUpdatedAtMillis() + 1L,
        System.currentTimeMillis());
    return new RobotAccountDataImpl(account.getId(), account.getUrl(), account.getConsumerSecret(),
        capabilities, account.isVerified(), account.getTokenExpirySeconds(),
        account.getOwnerAddress(), account.getDescription(), account.getCreatedAtMillis(),
        updatedAtMillis, account.isPaused(), account.getTokenVersion(),
        account.getLastActiveAtMillis());
  }

  /**
   * Extracts the scheme + authority (origin) from a callback URL so that well-known
   * paths like {@code /_wave/capabilities.xml} can be appended without duplicating
   * the callback path component.
   */
  static String robotBaseUrl(String callbackUrl) {
    if (callbackUrl == null || callbackUrl.isBlank()) {
      return "";
    }
    URI uri = parseCallbackUrl(callbackUrl);
    if (uri == null) {
      return callbackUrl;
    }
    String scheme = uri.getScheme();
    String authority = uri.getAuthority();
    if (scheme == null || authority == null) {
      return callbackUrl;
    }
    String path = uri.getPath();
    if (path == null || path.isEmpty() || "/".equals(path)) {
      return scheme + "://" + authority;
    }
    if (path.endsWith(Robot.RPC_URL)) {
      return scheme + "://" + authority
          + path.substring(0, path.length() - Robot.RPC_URL.length());
    }
    return scheme + "://" + authority + path;
  }

  /**
   * Returns the passive robot RPC endpoint for the stored robot URL, appending
   * {@link Robot#RPC_URL} only when the stored URL does not already point at it.
   */
  static String robotRpcUrl(String storedUrl) {
    if (storedUrl == null || storedUrl.isBlank()) {
      return "";
    }
    URI uri = parseCallbackUrl(storedUrl);
    if (uri != null) {
      String path = uri.getPath();
      if (path != null && path.endsWith(Robot.RPC_URL)) {
        return storedUrl;
      }
    }
    return storedUrl + Robot.RPC_URL;
  }

  private static URI parseCallbackUrl(String callbackUrl) {
    try {
      return URI.create(callbackUrl);
    } catch (IllegalArgumentException e) {
      LOG.warning("Unable to parse robot callback URL: " + sanitizeCallbackUrl(callbackUrl), e);
      return null;
    }
  }

  static String sanitizeCallbackUrl(String callbackUrl) {
    if (callbackUrl == null || callbackUrl.isBlank()) {
      return "<blank callback URL>";
    }
    try {
      URI uri = URI.create(callbackUrl);
      StringBuilder sanitized = new StringBuilder();
      String scheme = uri.getScheme();
      if (scheme != null) {
        sanitized.append(scheme).append("://");
      }
      String authority = sanitizeAuthority(uri);
      if (!authority.isEmpty()) {
        sanitized.append(authority);
      }
      String path = uri.getRawPath();
      if (path != null && !path.isEmpty()) {
        sanitized.append(path);
      }
      if (sanitized.length() > 0) {
        return sanitized.toString();
      }
    } catch (IllegalArgumentException ignored) {
      // Fall through to a generic placeholder.
    }
    return "<invalid callback URL>";
  }

  private static String sanitizeAuthority(URI uri) {
    String host = uri.getHost();
    if (host != null) {
      StringBuilder authority = new StringBuilder();
      if (host.contains(":") && !host.startsWith("[")) {
        authority.append('[').append(host).append(']');
      } else {
        authority.append(host);
      }
      int port = uri.getPort();
      if (port != -1) {
        authority.append(':').append(port);
      }
      return authority.toString();
    }
    String rawAuthority = uri.getRawAuthority();
    if (rawAuthority == null) {
      return "";
    }
    int userInfoSeparator = rawAuthority.lastIndexOf('@');
    return userInfoSeparator >= 0 ? rawAuthority.substring(userInfoSeparator + 1) : rawAuthority;
  }
}
