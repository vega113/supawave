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

package org.waveprotocol.box.search.query;

import java.util.Collections;
import java.util.List;

/**
 * A structured search query consisting of a list of conditions.
 *
 * Ported from Wiab.pro.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public class SearchQuery {

  private final List<QueryCondition> conditions;

  public SearchQuery() {
    this.conditions = Collections.emptyList();
  }

  public SearchQuery(List<QueryCondition> conditions) {
    this.conditions = conditions;
  }

  public List<QueryCondition> getConditions() {
    return conditions;
  }

  public boolean isInbox() {
    for (QueryCondition condition : conditions) {
      if (condition.isInbox()) {
        return true;
      }
    }
    return false;
  }

  public boolean isArchive() {
    for (QueryCondition condition : conditions) {
      if (condition.isArchive()) {
        return true;
      }
    }
    return false;
  }

  public boolean isPublic() {
    for (QueryCondition condition : conditions) {
      if (condition.isPublic()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (QueryCondition condition : conditions) {
      if (sb.length() != 0) {
        sb.append(" ");
      }
      sb.append(condition.toString());
    }
    return sb.toString();
  }
}
