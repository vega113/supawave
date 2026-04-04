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
    String robotUrl = storedUrl.contains("/_wave/robot/jsonrpc")
        ? storedUrl : storedUrl + Robot.RPC_URL;
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
    return Collections.emptyList();
  }

  @Override
  public RobotAccountData fetchCapabilities(RobotAccountData account, String activeApiUrl)
      throws CapabilityFetchException {
    RobotCapabilitiesParser parser = new RobotCapabilitiesParser(
        robotBaseUrl(account.getUrl()) + Robot.CAPABILITIES_URL, connection, activeApiUrl);
    RobotCapabilities capabilities = new RobotCapabilities(
        parser.getCapabilities(), parser.getCapabilitiesHash(), parser.getProtocolVersion());
    return new RobotAccountDataImpl(account.getId(), account.getUrl(), account.getConsumerSecret(),
        capabilities, account.isVerified(), account.getTokenExpirySeconds(),
        account.getOwnerAddress(), account.getDescription(), account.getCreatedAtMillis(),
        account.getUpdatedAtMillis(), account.isPaused(), account.getTokenVersion());
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
    try {
      URI uri = URI.create(callbackUrl);
      String scheme = uri.getScheme();
      String authority = uri.getAuthority();
      if (scheme != null && authority != null) {
        return scheme + "://" + authority;
      }
    } catch (IllegalArgumentException e) {
      LOG.warning("Unable to parse robot callback URL: " + callbackUrl, e);
    }
    return callbackUrl;
  }
}
