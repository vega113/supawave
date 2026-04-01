package org.waveprotocol.box.server.authentication.jwt;

import java.util.Set;

/**
 * Standard JWT scope constants for Wave robot API tokens.
 */
public final class JwtScopes {
  public static final String DATA_READ = "wave:data:read";
  public static final String DATA_WRITE = "wave:data:write";
  public static final String ROBOT_ACTIVE = "wave:robot:active";

  /** Default scopes for DATA_API_ACCESS tokens. */
  public static final Set<String> DATA_API_DEFAULT = Set.of(DATA_READ, DATA_WRITE);

  /** Default scopes for ROBOT_ACCESS tokens. */
  public static final Set<String> ROBOT_DEFAULT = Set.of(ROBOT_ACTIVE, DATA_READ, DATA_WRITE);

  private JwtScopes() {}
}
