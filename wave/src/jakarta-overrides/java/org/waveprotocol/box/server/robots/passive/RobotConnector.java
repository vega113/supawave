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

import java.util.Collections;
import java.util.List;

public final class RobotConnector implements RobotCapabilityFetcher {
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
    String robotUrl = robot.getAccount().getUrl() + Robot.RPC_URL;
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
        account.getUrl() + Robot.CAPABILITIES_URL, connection, activeApiUrl);
    RobotCapabilities capabilities = new RobotCapabilities(
        parser.getCapabilities(), parser.getCapabilitiesHash(), parser.getProtocolVersion());
    return new RobotAccountDataImpl(account.getId(), account.getUrl(), account.getConsumerSecret(),
        capabilities, account.isVerified());
  }
}
