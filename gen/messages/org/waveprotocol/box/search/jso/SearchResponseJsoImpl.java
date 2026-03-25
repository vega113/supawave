/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.search.jso;

import static org.waveprotocol.wave.communication.gwt.JsonHelper.*;
import com.google.gwt.core.client.*;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.waveprotocol.box.search.SearchResponse.Digest;
import org.waveprotocol.box.search.jso.SearchResponseJsoImpl.DigestJsoImpl;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.waveprotocol.box.search.SearchResponse;
import org.waveprotocol.box.search.SearchResponseUtil;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.waveprotocol.box.search.SearchResponse.Digest;
import org.waveprotocol.box.search.SearchResponseUtil.DigestUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of SearchResponse backed by a GWT JavaScriptObject.
 *
 * Generated from org/waveprotocol/box/search/search.proto. Do not edit.
 */

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/** We have to use fully-qualified name of the GsonSerializable class here in order to make it
 * visible in case of nested classes.
 */
public final class SearchResponseJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements SearchResponse {

  /**
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the
   * "License"); you may not use this file except in compliance
   * with the License. You may obtain a copy of the License at
   *
   * http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing,
   * software distributed under the License is distributed on an
   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   * KIND, either express or implied. See the License for the
   * specific language governing permissions and limitations
   * under the License.
   */

  /** We have to use fully-qualified name of the GsonSerializable class here in order to make it
   * visible in case of nested classes.
   */
  public static final class DigestJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
      implements Digest {
    private static final String keyTitle = "1";
    private static final String keySnippet = "2";
    private static final String keyWaveId = "3";
    private static final String keyLastModified = "4";
    private static final String keyUnreadCount = "5";
    private static final String keyBlipCount = "6";
    private static final String keyParticipants = "7";
    private static final String keyAuthor = "8";
    protected DigestJsoImpl() {
    }

    public static DigestJsoImpl create() {
      DigestJsoImpl instance = (DigestJsoImpl) JsonMessage.createJsonMessage();
      // Force all lists to start with an empty list rather than no property for
      // the list. This is so that the native JS equality works, since (obviously)
      // {} != {"foo": []} while in the context of messages they should be.
      instance.clearParticipants();
      return instance;
    }

    @Override
    public void copyFrom(Digest message) {
      super.copyFrom((DigestJsoImpl) message);
    }

    /**
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements. See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership. The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing,
     * software distributed under the License is distributed on an
     * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
     * KIND, either express or implied. See the License for the
     * specific language governing permissions and limitations
     * under the License.
     */

    @Override
    public String getTitle() {
      return hasProperty(this, keyTitle) ? getPropertyAsString(this, keyTitle) : null;
    }

    @Override
    public void setTitle(String value) {
      setPropertyAsString(this, keyTitle, value);
    }

    /**
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements. See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership. The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing,
     * software distributed under the License is distributed on an
     * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
     * KIND, either express or implied. See the License for the
     * specific language governing permissions and limitations
     * under the License.
     */

    @Override
    public String getSnippet() {
      return hasProperty(this, keySnippet) ? getPropertyAsString(this, keySnippet) : null;
    }

    @Override
    public void setSnippet(String value) {
      setPropertyAsString(this, keySnippet, value);
    }

    /**
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements. See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership. The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing,
     * software distributed under the License is distributed on an
     * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
     * KIND, either express or implied. See the License for the
     * specific language governing permissions and limitations
     * under the License.
     */

    @Override
    public String getWaveId() {
      return hasProperty(this, keyWaveId) ? getPropertyAsString(this, keyWaveId) : null;
    }

    @Override
    public void setWaveId(String value) {
      setPropertyAsString(this, keyWaveId, value);
    }

    /**
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements. See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership. The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing,
     * software distributed under the License is distributed on an
     * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
     * KIND, either express or implied. See the License for the
     * specific language governing permissions and limitations
     * under the License.
     */

    @Override
    public long getLastModified() {
      return hasProperty(this, keyLastModified) ? getPropertyAsLong(this, keyLastModified) : 0L;
    }

    @Override
    public void setLastModified(long value) {
      setPropertyAsLong(this, keyLastModified, value);
    }

    /**
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements. See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership. The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing,
     * software distributed under the License is distributed on an
     * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
     * KIND, either express or implied. See the License for the
     * specific language governing permissions and limitations
     * under the License.
     */

    @Override
    public int getUnreadCount() {
      return hasProperty(this, keyUnreadCount) ? getPropertyAsInteger(this, keyUnreadCount) : 0;
    }

    @Override
    public void setUnreadCount(int value) {
      setPropertyAsInteger(this, keyUnreadCount, value);
    }

    /**
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements. See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership. The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing,
     * software distributed under the License is distributed on an
     * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
     * KIND, either express or implied. See the License for the
     * specific language governing permissions and limitations
     * under the License.
     */

    @Override
    public int getBlipCount() {
      return hasProperty(this, keyBlipCount) ? getPropertyAsInteger(this, keyBlipCount) : 0;
    }

    @Override
    public void setBlipCount(int value) {
      setPropertyAsInteger(this, keyBlipCount, value);
    }

    /**
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements. See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership. The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing,
     * software distributed under the License is distributed on an
     * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
     * KIND, either express or implied. See the License for the
     * specific language governing permissions and limitations
     * under the License.
     */

    @Override
    public List<String> getParticipants() {
      initArray(this, keyParticipants);
      List<String> list = new ArrayList<String>();
      for (int i = 0; i < getParticipantsSize(); i++) {
        list.add(getParticipants(i));
      }
      return list;
    }

    @Override
    public void addAllParticipants(List<String> values) {
      for (String value : values) {
        addParticipants(value);
      }
    }

    @Override
    @SuppressWarnings("unchecked")
        public String getParticipants(int n) {
      initArray(this, keyParticipants);
      JsArrayString array = getPropertyAsObject(this, keyParticipants).cast();
      if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
      if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
      return array.get(n);
    }

    @Override
    public void setParticipants(int n, String value) {
      if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
      initArray(this, keyParticipants);
      ((JsArrayString) getPropertyAsObject(this, keyParticipants)).set(n, value);
    }

    @Override
    public int getParticipantsSize() {
      return hasProperty(this, keyParticipants) ? ((JsArray<?>) getPropertyAsObject(this, keyParticipants)).length() : 0;
    }

    @Override
    public void addParticipants(String value) {
      initArray(this, keyParticipants);
      ((JsArrayString) getPropertyAsObject(this, keyParticipants)).push(value);
    }

    @Override
    public void clearParticipants() {
      clearArray(this, keyParticipants);
    }

    /**
     * Licensed to the Apache Software Foundation (ASF) under one
     * or more contributor license agreements. See the NOTICE file
     * distributed with this work for additional information
     * regarding copyright ownership. The ASF licenses this file
     * to you under the Apache License, Version 2.0 (the
     * "License"); you may not use this file except in compliance
     * with the License. You may obtain a copy of the License at
     *
     * http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing,
     * software distributed under the License is distributed on an
     * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
     * KIND, either express or implied. See the License for the
     * specific language governing permissions and limitations
     * under the License.
     */

    @Override
    public String getAuthor() {
      return hasProperty(this, keyAuthor) ? getPropertyAsString(this, keyAuthor) : null;
    }

    @Override
    public void setAuthor(String value) {
      setPropertyAsString(this, keyAuthor, value);
    }

    @Override
    public boolean isEqualTo(Object o) {
      if (o instanceof DigestJsoImpl) {
        return nativeIsEqualTo(o);
      } else if (o instanceof Digest) {
        return DigestUtil.isEqual(this, (Digest) o);
      } else {
        return false;
      }
    }

  }

  private static final String keyQuery = "1";
  private static final String keyTotalResults = "2";
  private static final String keyDigests = "3";
  protected SearchResponseJsoImpl() {
  }

  public static SearchResponseJsoImpl create() {
    SearchResponseJsoImpl instance = (SearchResponseJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearDigests();
    return instance;
  }

  @Override
  public void copyFrom(SearchResponse message) {
    super.copyFrom((SearchResponseJsoImpl) message);
  }

  /**
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the
   * "License"); you may not use this file except in compliance
   * with the License. You may obtain a copy of the License at
   *
   * http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing,
   * software distributed under the License is distributed on an
   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   * KIND, either express or implied. See the License for the
   * specific language governing permissions and limitations
   * under the License.
   */

  @Override
  public String getQuery() {
    return hasProperty(this, keyQuery) ? getPropertyAsString(this, keyQuery) : null;
  }

  @Override
  public void setQuery(String value) {
    setPropertyAsString(this, keyQuery, value);
  }

  /**
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the
   * "License"); you may not use this file except in compliance
   * with the License. You may obtain a copy of the License at
   *
   * http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing,
   * software distributed under the License is distributed on an
   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   * KIND, either express or implied. See the License for the
   * specific language governing permissions and limitations
   * under the License.
   */

  @Override
  public int getTotalResults() {
    return hasProperty(this, keyTotalResults) ? getPropertyAsInteger(this, keyTotalResults) : 0;
  }

  @Override
  public void setTotalResults(int value) {
    setPropertyAsInteger(this, keyTotalResults, value);
  }

  /**
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the
   * "License"); you may not use this file except in compliance
   * with the License. You may obtain a copy of the License at
   *
   * http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing,
   * software distributed under the License is distributed on an
   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
   * KIND, either express or implied. See the License for the
   * specific language governing permissions and limitations
   * under the License.
   */

  @Override
  public List<DigestJsoImpl> getDigests() {
    initArray(this, keyDigests);
    List<DigestJsoImpl> list = new ArrayList<DigestJsoImpl>();
    for (int i = 0; i < getDigestsSize(); i++) {
      list.add(getDigests(i));
    }
    return list;
  }

  @Override
  public void addAllDigests(List<? extends Digest> models) {
    for (Digest model : models) {
      addDigests(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public DigestJsoImpl getDigests(int n) {
    initArray(this, keyDigests);
    JsArray<DigestJsoImpl> array = getPropertyAsObject(this, keyDigests).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setDigests(int n, Digest model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyDigests);
    ((JsArray<DigestJsoImpl>) getPropertyAsObject(this, keyDigests)).set(n, (DigestJsoImpl) model);
  }

  @Override
  public int getDigestsSize() {
    return hasProperty(this, keyDigests) ? ((JsArray<?>) getPropertyAsObject(this, keyDigests)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addDigests(Digest model) {
    initArray(this, keyDigests);
    ((JsArray<DigestJsoImpl>) getPropertyAsObject(this, keyDigests)).push((DigestJsoImpl) model);
  }

  @Override
  public void clearDigests() {
    clearArray(this, keyDigests);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof SearchResponseJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof SearchResponse) {
      return SearchResponseUtil.isEqual(this, (SearchResponse) o);
    } else {
      return false;
    }
  }

}