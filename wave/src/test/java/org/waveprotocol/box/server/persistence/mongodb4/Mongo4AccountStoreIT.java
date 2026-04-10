package org.waveprotocol.box.server.persistence.mongodb4;

import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.junit.Assume;
import org.junit.Test;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.account.RobotAccountDataImpl;
import org.waveprotocol.box.server.authentication.PasswordDigest;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.robots.RobotCapabilities;
import com.google.wave.api.ProtocolVersion;
import com.google.wave.api.event.EventType;
import com.google.wave.api.robot.Capability;
import com.google.wave.api.Context;
import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class Mongo4AccountStoreIT {
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(Mongo4AccountStoreIT.class);
  @Test
  public void humanAndRobotRoundTripIfDockerAvailable() throws Exception {
    // Normalize DOCKER_HOST for Colima before Testcontainers initializes.
    MongoItTestUtil.preferColimaIfDockerHostInvalid(LOG);

    DockerImageName image = DockerImageName.parse("mongo:6.0").asCompatibleSubstituteFor("mongo");
    try (MongoDBContainer mongo = new MongoDBContainer(image)) {
      MongoItTestUtil.startOrSkip(mongo, LOG);
      try (var client = MongoClients.create(mongo.getConnectionString())) {
        MongoDatabase db = client.getDatabase("wiab_it");
        AccountStore store = new Mongo4AccountStore(db);
        store.initializeAccountStore();

        // Human round-trip
        ParticipantId hid = ParticipantId.ofUnsafe("human@example.com");
        byte[] salt = new byte[PasswordDigest.MINIMUM_SALT_LENGTH];
        byte[] dig = new byte[] {4,5,6};
        PasswordDigest pd = PasswordDigest.from(salt, dig);
        AccountData human = new HumanAccountDataImpl(hid, pd);
        store.putAccount(human);
        AccountData loadedHuman = store.getAccount(hid);
        assertNotNull(loadedHuman);
        assertTrue(loadedHuman.isHuman());
        assertArrayEquals(salt, loadedHuman.asHuman().getPasswordDigest().getSalt());
        assertArrayEquals(dig, loadedHuman.asHuman().getPasswordDigest().getDigest());
        store.removeAccount(hid);
        assertNull(store.getAccount(hid));

        // Robot round-trip
        ParticipantId rid = ParticipantId.ofUnsafe("robot@example.com");
        Map<EventType, Capability> cmap = CollectionUtils.newHashMap();
        List<Context> ctx = Arrays.asList(Context.SELF, Context.ALL);
        cmap.put(EventType.DOCUMENT_CHANGED, new Capability(EventType.DOCUMENT_CHANGED, ctx, ""));
        RobotCapabilities caps = new RobotCapabilities(cmap, "hash123", ProtocolVersion.DEFAULT,
            "", true);
        AccountData robot = new RobotAccountDataImpl(rid, "http://bot.example.com/callback", "secret", caps, true);
        store.putAccount(robot);
        AccountData loadedRobot = store.getAccount(rid);
        assertNotNull(loadedRobot);
        assertTrue(loadedRobot.isRobot());
        assertEquals("http://bot.example.com/callback", loadedRobot.asRobot().getUrl());
        assertEquals("secret", loadedRobot.asRobot().getConsumerSecret());
        assertTrue(loadedRobot.asRobot().isVerified());
        assertTrue(loadedRobot.asRobot().getCapabilities().getCapabilitiesMap().containsKey(EventType.DOCUMENT_CHANGED));
        assertTrue(loadedRobot.asRobot().getCapabilities().isRpcServerUrlFetched());
        store.removeAccount(rid);
        assertNull(store.getAccount(rid));
      } finally {
        MongoItTestUtil.stopQuietly(mongo, LOG);
      }
    }
  }
}
