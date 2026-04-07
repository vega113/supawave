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
 * KIND, either express or implied.  See the License for
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.server.rpc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.wave.api.ParticipantProfile;

import junit.framework.TestCase;

import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.memory.MemoryStore;
import org.waveprotocol.box.server.robots.operations.FetchProfilesService.ProfilesFetcher;
import org.waveprotocol.wave.model.wave.ParticipantId;

import java.lang.reflect.Method;

/**
 * Tests for ProfileServlet.
 */
public class ProfileServletTest extends TestCase {
  private AccountStore store;
  private ProfilesFetcher profilesFetcher;
  private ProfileServlet servlet;

  @Override
  protected void setUp() throws Exception {
    store = new MemoryStore();
    profilesFetcher = mock(ProfilesFetcher.class);
    servlet = new ProfileServlet(store, null, "example.com", profilesFetcher);
  }

  public void testResolveImageUrlWithCustomImage() throws Exception {
    ParticipantId pid = ParticipantId.ofUnsafe("user@example.com");
    HumanAccountData account = new HumanAccountDataImpl(pid, null);
    account.setProfileImageAttachmentId("data:image/png;base64,iVBORw0KGgoAAAANSUhEUg...");
    store.putAccount(account);

    String url = invokeResolveImageUrl(account);
    assertEquals("/userprofile/image/" + pid.getAddress(), url);
  }

  public void testResolveImageUrlWithEmptyCustomImage() throws Exception {
    ParticipantId pid = ParticipantId.ofUnsafe("user@example.com");
    HumanAccountData account = new HumanAccountDataImpl(pid, null);
    account.setProfileImageAttachmentId("");
    store.putAccount(account);

    ParticipantProfile mockProfile = mock(ParticipantProfile.class);
    when(mockProfile.getImageUrl()).thenReturn("http://gravatar.com/avatar");
    when(profilesFetcher.fetchProfile(pid.getAddress())).thenReturn(mockProfile);

    String url = invokeResolveImageUrl(account);
    assertEquals("http://gravatar.com/avatar", url);
  }

  public void testResolveImageUrlWithWhitespaceCustomImage() throws Exception {
    ParticipantId pid = ParticipantId.ofUnsafe("user@example.com");
    HumanAccountData account = new HumanAccountDataImpl(pid, null);
    account.setProfileImageAttachmentId("   ");
    store.putAccount(account);

    ParticipantProfile mockProfile = mock(ParticipantProfile.class);
    when(mockProfile.getImageUrl()).thenReturn("http://gravatar.com/avatar");
    when(profilesFetcher.fetchProfile(pid.getAddress())).thenReturn(mockProfile);

    String url = invokeResolveImageUrl(account);
    assertEquals("http://gravatar.com/avatar", url);
  }

  private String invokeResolveImageUrl(HumanAccountData account) throws Exception {
    Method method = ProfileServlet.class.getDeclaredMethod("resolveImageUrl", HumanAccountData.class);
    method.setAccessible(true);
    return (String) method.invoke(servlet, account);
  }
}
