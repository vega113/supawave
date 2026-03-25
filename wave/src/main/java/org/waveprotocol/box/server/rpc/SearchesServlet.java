/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.waveprotocol.box.server.rpc;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.waveprotocol.box.searches.SearchesItem;
import org.waveprotocol.box.server.account.AccountData;
import org.waveprotocol.box.server.account.HumanAccountData;
import org.waveprotocol.box.server.account.HumanAccountDataImpl;
import org.waveprotocol.box.server.authentication.SessionManager;
import org.waveprotocol.box.server.persistence.AccountStore;
import org.waveprotocol.box.server.persistence.PersistenceException;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.util.logging.Log;

/**
 * Servlet allowing users to get and set their saved search patterns.
 *
 * GET returns the user's saved searches as JSON.
 * POST stores the provided searches for the user.
 *
 * Ported from Wiab.pro, simplified to use Gson instead of PST proto serialization.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
@SuppressWarnings("serial")
@Singleton
public final class SearchesServlet extends HttpServlet {

  // Default saved searches are empty — the icon buttons in the search widget
  // already provide Inbox, Public, and Archive filters.  Users can add custom
  // saved searches via the editor.
  private static final List<SearchesItem> DEFAULT_SEARCHES = new ArrayList<>();

  private static final Log LOG = Log.get(SearchesServlet.class);
  private static final Gson GSON = new Gson();
  private static final Type SEARCHES_LIST_TYPE = new TypeToken<List<SearchesItem>>(){}.getType();

  private final SessionManager sessionManager;
  private final AccountStore accountStore;

  @Inject
  public SearchesServlet(SessionManager sessionManager, AccountStore accountStore) {
    this.sessionManager = sessionManager;
    this.accountStore = accountStore;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    try {
      ParticipantId participant = sessionManager.getLoggedInUser(req.getSession(false));
      if (participant == null) {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return;
      }
      String request = req.getReader().lines().collect(Collectors.joining("\n"));
      List<SearchesItem> searches = GSON.fromJson(request, SEARCHES_LIST_TYPE);
      AccountData account = accountStore.getAccount(participant);
      HumanAccountData humanAccount;
      if (account != null) {
        humanAccount = account.asHuman();
      } else {
        humanAccount = new HumanAccountDataImpl(participant);
      }
      humanAccount.setSearches(searches);
      accountStore.putAccount(humanAccount);
      resp.setStatus(HttpServletResponse.SC_OK);
    } catch (JsonParseException ex) {
      resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid searches payload");
    } catch (PersistenceException ex) {
      throw new IOException(ex);
    }
  }

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    ParticipantId participant = sessionManager.getLoggedInUser(req.getSession(false));
    if (participant == null) {
      resp.setStatus(HttpServletResponse.SC_FORBIDDEN);
      return;
    }
    try {
      AccountData account = accountStore.getAccount(participant);
      List<SearchesItem> searches = null;
      if (account != null) {
        searches = account.asHuman().getSearches();
      }
      if (searches == null) {
        searches = DEFAULT_SEARCHES;
      }
      resp.setStatus(HttpServletResponse.SC_OK);
      resp.setContentType("application/json; charset=utf-8");
      resp.setHeader("Cache-Control", "no-store");
      resp.getWriter().append(GSON.toJson(searches));
    } catch (PersistenceException ex) {
      throw new IOException(ex);
    }
  }
}
