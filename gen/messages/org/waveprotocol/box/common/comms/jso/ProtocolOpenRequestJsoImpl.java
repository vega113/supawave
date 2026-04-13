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

package org.waveprotocol.box.common.comms.jso;

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
import org.waveprotocol.box.common.comms.WaveletVersion;
import org.waveprotocol.box.common.comms.jso.WaveletVersionJsoImpl;

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
import org.waveprotocol.box.common.comms.ProtocolOpenRequest;
import org.waveprotocol.box.common.comms.ProtocolOpenRequestUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProtocolOpenRequest backed by a GWT JavaScriptObject.
 *
 * Generated from org/waveprotocol/box/common/comms/waveclient-rpc.proto. Do not edit.
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
public final class ProtocolOpenRequestJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProtocolOpenRequest {
  private static final String keyParticipantId = "1";
  private static final String keyWaveId = "2";
  private static final String keyWaveletIdPrefix = "3";
  private static final String keyKnownWavelet = "4";
  private static final String keyViewportStartBlipId = "5";
  private static final String keyViewportDirection = "6";
  private static final String keyViewportLimit = "7";
  private static final String keySearchQuery = "8";
  protected ProtocolOpenRequestJsoImpl() {
  }

  public static ProtocolOpenRequestJsoImpl create() {
    ProtocolOpenRequestJsoImpl instance = (ProtocolOpenRequestJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearWaveletIdPrefix();
    instance.clearKnownWavelet();
    return instance;
  }

  @Override
  public void copyFrom(ProtocolOpenRequest message) {
    super.copyFrom((ProtocolOpenRequestJsoImpl) message);
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
  public String getParticipantId() {
    return hasProperty(this, keyParticipantId) ? getPropertyAsString(this, keyParticipantId) : null;
  }

  @Override
  public void setParticipantId(String value) {
    setPropertyAsString(this, keyParticipantId, value);
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
  public List<String> getWaveletIdPrefix() {
    initArray(this, keyWaveletIdPrefix);
    List<String> list = new ArrayList<String>();
    for (int i = 0; i < getWaveletIdPrefixSize(); i++) {
      list.add(getWaveletIdPrefix(i));
    }
    return list;
  }

  @Override
  public void addAllWaveletIdPrefix(List<String> values) {
    for (String value : values) {
      addWaveletIdPrefix(value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public String getWaveletIdPrefix(int n) {
    initArray(this, keyWaveletIdPrefix);
    JsArrayString array = getPropertyAsObject(this, keyWaveletIdPrefix).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  public void setWaveletIdPrefix(int n, String value) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyWaveletIdPrefix);
    ((JsArrayString) getPropertyAsObject(this, keyWaveletIdPrefix)).set(n, value);
  }

  @Override
  public int getWaveletIdPrefixSize() {
    return hasProperty(this, keyWaveletIdPrefix) ? ((JsArray<?>) getPropertyAsObject(this, keyWaveletIdPrefix)).length() : 0;
  }

  @Override
  public void addWaveletIdPrefix(String value) {
    initArray(this, keyWaveletIdPrefix);
    ((JsArrayString) getPropertyAsObject(this, keyWaveletIdPrefix)).push(value);
  }

  @Override
  public void clearWaveletIdPrefix() {
    clearArray(this, keyWaveletIdPrefix);
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
  public List<WaveletVersionJsoImpl> getKnownWavelet() {
    initArray(this, keyKnownWavelet);
    List<WaveletVersionJsoImpl> list = new ArrayList<WaveletVersionJsoImpl>();
    for (int i = 0; i < getKnownWaveletSize(); i++) {
      list.add(getKnownWavelet(i));
    }
    return list;
  }

  @Override
  public void addAllKnownWavelet(List<? extends WaveletVersion> models) {
    for (WaveletVersion model : models) {
      addKnownWavelet(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public WaveletVersionJsoImpl getKnownWavelet(int n) {
    initArray(this, keyKnownWavelet);
    JsArray<WaveletVersionJsoImpl> array = getPropertyAsObject(this, keyKnownWavelet).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setKnownWavelet(int n, WaveletVersion model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyKnownWavelet);
    ((JsArray<WaveletVersionJsoImpl>) getPropertyAsObject(this, keyKnownWavelet)).set(n, (WaveletVersionJsoImpl) model);
  }

  @Override
  public int getKnownWaveletSize() {
    return hasProperty(this, keyKnownWavelet) ? ((JsArray<?>) getPropertyAsObject(this, keyKnownWavelet)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addKnownWavelet(WaveletVersion model) {
    initArray(this, keyKnownWavelet);
    ((JsArray<WaveletVersionJsoImpl>) getPropertyAsObject(this, keyKnownWavelet)).push((WaveletVersionJsoImpl) model);
  }

  @Override
  public void clearKnownWavelet() {
    clearArray(this, keyKnownWavelet);
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
  public boolean hasViewportStartBlipId() {
    return hasProperty(this, keyViewportStartBlipId);
  }

  @Override
  public void clearViewportStartBlipId() {
    if (hasProperty(this, keyViewportStartBlipId)) {
      deleteProperty(this, keyViewportStartBlipId);
    }
  }

  @Override
  public String getViewportStartBlipId() {
    return hasProperty(this, keyViewportStartBlipId) ? getPropertyAsString(this, keyViewportStartBlipId) : null;
  }

  @Override
  public void setViewportStartBlipId(String value) {
    setPropertyAsString(this, keyViewportStartBlipId, value);
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
  public boolean hasViewportDirection() {
    return hasProperty(this, keyViewportDirection);
  }

  @Override
  public void clearViewportDirection() {
    if (hasProperty(this, keyViewportDirection)) {
      deleteProperty(this, keyViewportDirection);
    }
  }

  @Override
  public String getViewportDirection() {
    return hasProperty(this, keyViewportDirection) ? getPropertyAsString(this, keyViewportDirection) : null;
  }

  @Override
  public void setViewportDirection(String value) {
    setPropertyAsString(this, keyViewportDirection, value);
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
  public boolean hasViewportLimit() {
    return hasProperty(this, keyViewportLimit);
  }

  @Override
  public void clearViewportLimit() {
    if (hasProperty(this, keyViewportLimit)) {
      deleteProperty(this, keyViewportLimit);
    }
  }

  @Override
  public int getViewportLimit() {
    return hasProperty(this, keyViewportLimit) ? getPropertyAsInteger(this, keyViewportLimit) : 0;
  }

  @Override
  public void setViewportLimit(int value) {
    setPropertyAsInteger(this, keyViewportLimit, value);
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
  public boolean hasSearchQuery() {
    return hasProperty(this, keySearchQuery);
  }

  @Override
  public void clearSearchQuery() {
    if (hasProperty(this, keySearchQuery)) {
      deleteProperty(this, keySearchQuery);
    }
  }

  @Override
  public String getSearchQuery() {
    return hasProperty(this, keySearchQuery) ? getPropertyAsString(this, keySearchQuery) : null;
  }

  @Override
  public void setSearchQuery(String value) {
    setPropertyAsString(this, keySearchQuery, value);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProtocolOpenRequestJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProtocolOpenRequest) {
      return ProtocolOpenRequestUtil.isEqual(this, (ProtocolOpenRequest) o);
    } else {
      return false;
    }
  }

}