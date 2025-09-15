package org.waveprotocol.box.server.authentication;

import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.wave.model.wave.ParticipantId;

/**
 * Jakarta-path SessionManager signature without javax.servlet dependency.
 */
public interface SessionManager {
  String USER_FIELD = "user";
  String SIGN_IN_URL = "/auth/signin";

  ParticipantId getLoggedInUser(WebSession session);
  AccountData getLoggedInAccount(WebSession session);
  void setLoggedInUser(WebSession session, ParticipantId id);
  void logout(WebSession session);
  String getLoginUrl(String redirect);
  WebSession getSessionFromToken(String token);
}

