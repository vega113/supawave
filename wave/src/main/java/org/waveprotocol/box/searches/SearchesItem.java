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
 */

package org.waveprotocol.box.searches;

/**
 * A stored search item consisting of a display name and a search query string.
 *
 * Ported from Wiab.pro (originally PST-generated from searches.proto).
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class SearchesItem {

  private String name;
  private String query;
  private boolean pinned;

  public SearchesItem() {
  }

  public SearchesItem(String name, String query) {
    this(name, query, false);
  }

  public SearchesItem(String name, String query, boolean pinned) {
    this.name = name;
    this.query = query;
    this.pinned = pinned;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getQuery() {
    return query;
  }

  public void setQuery(String query) {
    this.query = query;
  }

  public boolean isPinned() {
    return pinned;
  }

  public void setPinned(boolean pinned) {
    this.pinned = pinned;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SearchesItem)) return false;
    SearchesItem that = (SearchesItem) o;
    if (pinned != that.pinned) return false;
    if (name != null ? !name.equals(that.name) : that.name != null) return false;
    return query != null ? query.equals(that.query) : that.query == null;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (query != null ? query.hashCode() : 0);
    result = 31 * result + (pinned ? 1 : 0);
    return result;
  }
}
