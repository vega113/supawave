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

package org.waveprotocol.box.server.robots.operations;

import static org.mockito.Mockito.when;

import junit.framework.TestCase;

import org.apache.commons.codec.digest.DigestUtils;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.wave.model.wave.ParticipantId;

public final class GravatarProfilesFetcherTest extends TestCase {

  private static final String ADDRESS = "alice@example.com";

  @Mock private AccountStore accountStore;
  @Mock private AccountData accountData;
  @Mock private HumanAccountData humanAccountData;

  private GravatarProfilesFetcher fetcher;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    MockitoAnnotations.initMocks(this);
    fetcher = new GravatarProfilesFetcher(accountStore);
  }

  public void testGetImageUrlUsesSha256HashOfResolvedEmail() throws Exception {
    when(accountStore.getAccount(ParticipantId.ofUnsafe(ADDRESS))).thenReturn(accountData);
    when(accountData.isHuman()).thenReturn(true);
    when(accountData.asHuman()).thenReturn(humanAccountData);
    when(humanAccountData.getEmail()).thenReturn("  Alice@Example.com ");

    String imageUrl = fetcher.getImageUrl(ADDRESS);

    assertEquals(
        "https://www.gravatar.com/avatar/"
            + DigestUtils.sha256Hex("alice@example.com")
            + "?d=identicon&s=40",
        imageUrl);
  }
}
