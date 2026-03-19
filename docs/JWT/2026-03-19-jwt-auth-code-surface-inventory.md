# JWT Auth Code Surface Inventory

This inventory lists the concrete repository surfaces that a JWT redesign would touch. It is grouped by runtime responsibility so implementation lanes can scope their work cleanly.

## 1. Browser session and WebSocket auth

### Effective runtime path

- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/SessionManager.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/SessionManager.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/WebSession.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/WebSession.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/WebSessions.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/authentication/WebSessions.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java)

### Legacy mirror and tests still relevant during migration

- [`wave/src/main/java/org/waveprotocol/box/server/authentication/SessionManager.java`](../../wave/src/main/java/org/waveprotocol/box/server/authentication/SessionManager.java)
- [`wave/src/main/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java`](../../wave/src/main/java/org/waveprotocol/box/server/authentication/SessionManagerImpl.java)
- [`wave/src/main/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java`](../../wave/src/main/java/org/waveprotocol/box/server/rpc/AuthenticationServlet.java)
- [`wave/src/main/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java`](../../wave/src/main/java/org/waveprotocol/box/server/rpc/ServerRpcProvider.java)
- [`wave/src/main/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java`](../../wave/src/main/java/org/waveprotocol/box/server/rpc/WaveClientServlet.java)
- [`wave/src/test/java/org/waveprotocol/box/server/rpc/AuthenticationServletTest.java`](../../wave/src/test/java/org/waveprotocol/box/server/rpc/AuthenticationServletTest.java)
- [`wave/src/test/java/org/waveprotocol/box/server/authentication/SessionManagerTest.java`](../../wave/src/test/java/org/waveprotocol/box/server/authentication/SessionManagerTest.java)
- [`wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/AuthenticationServletJakartaIT.java`](../../wave/src/jakarta-test/java/org/waveprotocol/box/server/jakarta/AuthenticationServletJakartaIT.java)

### Browser client surfaces

- [`wave/src/main/java/org/waveprotocol/box/webclient/client/WaveWebSocketClient.java`](../../wave/src/main/java/org/waveprotocol/box/webclient/client/WaveWebSocketClient.java)
- [`wave/src/main/java/org/waveprotocol/box/webclient/client/Session.java`](../../wave/src/main/java/org/waveprotocol/box/webclient/client/Session.java)
- [`wave/src/main/java/org/waveprotocol/box/common/SessionConstants.java`](../../wave/src/main/java/org/waveprotocol/box/common/SessionConstants.java)

Why these matter:

- This area owns the move away from JS-readable `JSESSIONID`.
- This is where cookie, session bootstrap, and WebSocket authentication have to converge on one JWT story.

## 2. Robot active API, passive robots, and registration

### Effective runtime path

- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotApiModule.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotApiModule.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java)

### Shared and legacy surfaces

- [`wave/src/main/java/org/waveprotocol/box/server/robots/RobotApiModule.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/RobotApiModule.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/RobotRegistrationServlet.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/active/ActiveApiServlet.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotConnector.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotsGateway.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/passive/RobotsGateway.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/passive/Robot.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/passive/Robot.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrar.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/register/RobotRegistrarImpl.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/operations/NotifyOperationService.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/operations/NotifyOperationService.java)

### Robot client library surfaces

- [`wave/src/main/java/com/google/wave/api/AbstractRobot.java`](../../wave/src/main/java/com/google/wave/api/AbstractRobot.java)
- [`wave/src/jakarta-overrides/java/com/google/wave/api/AbstractRobot.java`](../../wave/src/jakarta-overrides/java/com/google/wave/api/AbstractRobot.java)
- [`wave/src/main/java/com/google/wave/api/WaveService.java`](../../wave/src/main/java/com/google/wave/api/WaveService.java)
- [`wave/src/main/java/org/waveprotocol/box/server/util/OAuthUtil.java`](../../wave/src/main/java/org/waveprotocol/box/server/util/OAuthUtil.java)
- [`wave/src/main/java/com/google/wave/api/robot/HttpRobotConnection.java`](../../wave/src/main/java/com/google/wave/api/robot/HttpRobotConnection.java)
- [`wave/src/main/java/org/waveprotocol/box/expimp/OAuth.java`](../../wave/src/main/java/org/waveprotocol/box/expimp/OAuth.java)
- [`wave/src/main/java/org/waveprotocol/box/expimp/WaveImport.java`](../../wave/src/main/java/org/waveprotocol/box/expimp/WaveImport.java)
- [`wave/src/main/java/org/waveprotocol/box/expimp/WaveExport.java`](../../wave/src/main/java/org/waveprotocol/box/expimp/WaveExport.java)

Why these matter:

- Active and passive robot auth both currently assume shared secrets and OAuth 1.0 signatures.
- The robot client library itself exposes `setupOAuth`, so any server-side redesign has an SDK and tooling tail.

## 3. Data API auth runtime

### Effective runtime path

- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java)

### Shared and legacy surfaces

- [`wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/BaseApiServlet.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiServlet.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServlet.java)
- [`wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java`](../../wave/src/main/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainer.java)
- [`wave/src/main/java/com/google/wave/api/oauth/OAuthService.java`](../../wave/src/main/java/com/google/wave/api/oauth/OAuthService.java)
- [`wave/src/main/java/com/google/wave/api/oauth/impl/OAuthServiceImpl.java`](../../wave/src/main/java/com/google/wave/api/oauth/impl/OAuthServiceImpl.java)
- [`wave/src/main/java/com/google/wave/api/oauth/impl/OAuthUser.java`](../../wave/src/main/java/com/google/wave/api/oauth/impl/OAuthUser.java)

### Tests

- [`wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServletTest.java`](../../wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServletTest.java)
- [`wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiServletTest.java`](../../wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiServletTest.java)
- [`wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainerTest.java`](../../wave/src/test/java/org/waveprotocol/box/server/robots/dataapi/DataApiTokenContainerTest.java)
- [`wave/src/jakarta-test/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServletJakartaIT.java`](../../wave/src/jakarta-test/java/org/waveprotocol/box/server/robots/dataapi/DataApiOAuthServletJakartaIT.java)
- [`wave/src/test/java/com/google/wave/api/oauth/impl/OAuthServiceImplRobotTest.java`](../../wave/src/test/java/com/google/wave/api/oauth/impl/OAuthServiceImplRobotTest.java)

Why these matter:

- This is the cleanest place to remove an in-memory token container and replace it with signed bearer-token validation.
- The Data API path is the largest concentration of server-owned OAuth 1.0 flow logic.

## 4. Robot identity persistence and schema

- [`wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java`](../../wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountData.java)
- [`wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java`](../../wave/src/main/java/org/waveprotocol/box/server/account/RobotAccountDataImpl.java)
- [`wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java`](../../wave/src/main/java/org/waveprotocol/box/server/persistence/file/FileAccountStore.java)
- [`wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java`](../../wave/src/main/java/org/waveprotocol/box/server/persistence/protos/ProtoAccountDataSerializer.java)
- [`wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto`](../../wave/src/proto/proto/org/waveprotocol/box/server/persistence/protos/account-store.proto)
- [`wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java`](../../wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb/MongoDbStore.java)
- [`wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java`](../../wave/src/main/java/org/waveprotocol/box/server/persistence/mongodb4/Mongo4AccountStore.java)

Why these matter:

- Robot identity is currently persisted as a shared secret named `consumerSecret` or `secret`.
- A JWT redesign needs new persisted fields for public key material, key ids, rotation state, and possibly token-version metadata.

## 5. Gadget and OpenSocial-adjacent surfaces

- [`wave/src/main/java/org/waveprotocol/box/server/rpc/GadgetProviderServlet.java`](../../wave/src/main/java/org/waveprotocol/box/server/rpc/GadgetProviderServlet.java)
- [`wave/src/main/java/org/waveprotocol/box/server/ServerMain.java`](../../wave/src/main/java/org/waveprotocol/box/server/ServerMain.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerMain.java)
- [`wave/config/reference.conf`](../../wave/config/reference.conf)
- [`wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetDataStore.java`](../../wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetDataStore.java)
- [`wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetDataStoreImpl.java`](../../wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetDataStoreImpl.java)
- [`wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetRenderer.java`](../../wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetRenderer.java)
- [`wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetWidget.java`](../../wave/src/main/java/org/waveprotocol/wave/client/gadget/renderer/GadgetWidget.java)
- [`wave/src/main/java/com/google/wave/api/oauth/impl/OAuthServiceImpl.java`](../../wave/src/main/java/com/google/wave/api/oauth/impl/OAuthServiceImpl.java)
- [`wave/src/main/java/com/google/wave/api/oauth/impl/PopupLoginFormHandler.java`](../../wave/src/main/java/com/google/wave/api/oauth/impl/PopupLoginFormHandler.java)
- [`wave/src/main/java/com/google/wave/api/oauth/impl/SimpleLoginFormHandler.java`](../../wave/src/main/java/com/google/wave/api/oauth/impl/SimpleLoginFormHandler.java)
- [`wave/src/main/java/com/google/wave/api/oauth/impl/OpenSocialHttpMessage.java`](../../wave/src/main/java/com/google/wave/api/oauth/impl/OpenSocialHttpMessage.java)
- [`wave/src/main/java/com/google/wave/api/oauth/impl/OpenSocialHttpResponseMessage.java`](../../wave/src/main/java/com/google/wave/api/oauth/impl/OpenSocialHttpResponseMessage.java)
- [`wave/src/test/java/org/waveprotocol/wave/client/gadget/renderer/GadgetNonEditorGwtTest.java`](../../wave/src/test/java/org/waveprotocol/wave/client/gadget/renderer/GadgetNonEditorGwtTest.java)

Why these matter:

- Gadget auth is partly client-side and partly externalized through the proxied gadget server.
- This area must either gain a JWT-backed `st` definition or be deliberately retired from the supported auth surface.

## 6. Build, dependency, and runtime wiring

- [`wave/build.gradle`](../../wave/build.gradle)
- [`wave/src/main/java/org/waveprotocol/box/server/ServerModule.java`](../../wave/src/main/java/org/waveprotocol/box/server/ServerModule.java)
- [`wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerModule.java`](../../wave/src/jakarta-overrides/java/org/waveprotocol/box/server/ServerModule.java)
- [`wave/config/reference.conf`](../../wave/config/reference.conf)
- [`wave/config/application.conf`](../../wave/config/application.conf)

Why these matter:

- `wave/build.gradle` still carries the `net.oauth.core` dependency line and the `excludeLegacyOAuth` knob.
- Session cookie flags and gadget server wiring are configuration-level concerns, not only Java concerns.

## 7. Legacy docs that should be updated when implementation starts

- [`docs/modernization-plan.md`](../../docs/modernization-plan.md)
- [`docs/jetty-migration.md`](../../docs/jetty-migration.md)
- [`docs/current-state.md`](../../docs/current-state.md)
- [`docs/blocks-adoption-plan.md`](../../docs/blocks-adoption-plan.md)
- [`docs/superpowers/plans/2026-03-18-library-upgrades-plan.md`](../../docs/superpowers/plans/2026-03-18-library-upgrades-plan.md)

Why these matter:

- The repo already tracks legacy OAuth retirement as modernization work.
- The JWT redesign should become the canonical successor plan before code implementation starts.

## Suggested execution slices

If this inventory is converted into implementation work, the cleanest slices are:

1. Shared issuer and validator foundation
2. Browser plus WebSocket auth migration
3. Robot registration plus active API migration
4. Data API token issuance plus bearer validation
5. Gadget boundary decision and any resulting token work
6. Persistence and schema cleanup
7. Dependency and legacy endpoint removal
