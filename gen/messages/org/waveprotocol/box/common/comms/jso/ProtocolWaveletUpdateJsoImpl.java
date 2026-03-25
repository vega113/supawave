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
import org.waveprotocol.wave.federation.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.box.common.comms.WaveletSnapshot;
import org.waveprotocol.box.common.comms.ProtocolFragments;
import org.waveprotocol.wave.federation.jso.ProtocolWaveletDeltaJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolHashedVersionJsoImpl;
import org.waveprotocol.box.common.comms.jso.WaveletSnapshotJsoImpl;
import org.waveprotocol.box.common.comms.jso.ProtocolFragmentsJsoImpl;

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
import org.waveprotocol.box.common.comms.ProtocolWaveletUpdate;
import org.waveprotocol.box.common.comms.ProtocolWaveletUpdateUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProtocolWaveletUpdate backed by a GWT JavaScriptObject.
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
public final class ProtocolWaveletUpdateJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProtocolWaveletUpdate {
  private static final String keyWaveletName = "1";
  private static final String keyAppliedDelta = "2";
  private static final String keyCommitNotice = "3";
  private static final String keyResultingVersion = "4";
  private static final String keySnapshot = "5";
  private static final String keyMarker = "6";
  private static final String keyChannelId = "7";
  private static final String keyFragments = "8";
  protected ProtocolWaveletUpdateJsoImpl() {
  }

  public static ProtocolWaveletUpdateJsoImpl create() {
    ProtocolWaveletUpdateJsoImpl instance = (ProtocolWaveletUpdateJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearAppliedDelta();
    return instance;
  }

  @Override
  public void copyFrom(ProtocolWaveletUpdate message) {
    super.copyFrom((ProtocolWaveletUpdateJsoImpl) message);
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
  public String getWaveletName() {
    return hasProperty(this, keyWaveletName) ? getPropertyAsString(this, keyWaveletName) : null;
  }

  @Override
  public void setWaveletName(String value) {
    setPropertyAsString(this, keyWaveletName, value);
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
  public List<ProtocolWaveletDeltaJsoImpl> getAppliedDelta() {
    initArray(this, keyAppliedDelta);
    List<ProtocolWaveletDeltaJsoImpl> list = new ArrayList<ProtocolWaveletDeltaJsoImpl>();
    for (int i = 0; i < getAppliedDeltaSize(); i++) {
      list.add(getAppliedDelta(i));
    }
    return list;
  }

  @Override
  public void addAllAppliedDelta(List<? extends ProtocolWaveletDelta> models) {
    for (ProtocolWaveletDelta model : models) {
      addAppliedDelta(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public ProtocolWaveletDeltaJsoImpl getAppliedDelta(int n) {
    initArray(this, keyAppliedDelta);
    JsArray<ProtocolWaveletDeltaJsoImpl> array = getPropertyAsObject(this, keyAppliedDelta).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setAppliedDelta(int n, ProtocolWaveletDelta model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyAppliedDelta);
    ((JsArray<ProtocolWaveletDeltaJsoImpl>) getPropertyAsObject(this, keyAppliedDelta)).set(n, (ProtocolWaveletDeltaJsoImpl) model);
  }

  @Override
  public int getAppliedDeltaSize() {
    return hasProperty(this, keyAppliedDelta) ? ((JsArray<?>) getPropertyAsObject(this, keyAppliedDelta)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addAppliedDelta(ProtocolWaveletDelta model) {
    initArray(this, keyAppliedDelta);
    ((JsArray<ProtocolWaveletDeltaJsoImpl>) getPropertyAsObject(this, keyAppliedDelta)).push((ProtocolWaveletDeltaJsoImpl) model);
  }

  @Override
  public void clearAppliedDelta() {
    clearArray(this, keyAppliedDelta);
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
  public boolean hasCommitNotice() {
    return hasProperty(this, keyCommitNotice);
  }

  @Override
  public void clearCommitNotice() {
    if (hasProperty(this, keyCommitNotice)) {
      deleteProperty(this, keyCommitNotice);
    }
  }

  @Override
  public ProtocolHashedVersion getCommitNotice() {
    return hasProperty(this, keyCommitNotice) ? ((ProtocolHashedVersionJsoImpl) getPropertyAsObject(this, keyCommitNotice)) : null;
  }

  @Override
  public void setCommitNotice(ProtocolHashedVersion model) {
    setPropertyAsObject(this, keyCommitNotice, (ProtocolHashedVersionJsoImpl) model);
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
  public boolean hasResultingVersion() {
    return hasProperty(this, keyResultingVersion);
  }

  @Override
  public void clearResultingVersion() {
    if (hasProperty(this, keyResultingVersion)) {
      deleteProperty(this, keyResultingVersion);
    }
  }

  @Override
  public ProtocolHashedVersion getResultingVersion() {
    return hasProperty(this, keyResultingVersion) ? ((ProtocolHashedVersionJsoImpl) getPropertyAsObject(this, keyResultingVersion)) : null;
  }

  @Override
  public void setResultingVersion(ProtocolHashedVersion model) {
    setPropertyAsObject(this, keyResultingVersion, (ProtocolHashedVersionJsoImpl) model);
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
  public boolean hasSnapshot() {
    return hasProperty(this, keySnapshot);
  }

  @Override
  public void clearSnapshot() {
    if (hasProperty(this, keySnapshot)) {
      deleteProperty(this, keySnapshot);
    }
  }

  @Override
  public WaveletSnapshot getSnapshot() {
    return hasProperty(this, keySnapshot) ? ((WaveletSnapshotJsoImpl) getPropertyAsObject(this, keySnapshot)) : null;
  }

  @Override
  public void setSnapshot(WaveletSnapshot model) {
    setPropertyAsObject(this, keySnapshot, (WaveletSnapshotJsoImpl) model);
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
  public boolean hasMarker() {
    return hasProperty(this, keyMarker);
  }

  @Override
  public void clearMarker() {
    if (hasProperty(this, keyMarker)) {
      deleteProperty(this, keyMarker);
    }
  }

  @Override
  public boolean getMarker() {
    return hasProperty(this, keyMarker) ? getPropertyAsBoolean(this, keyMarker) : false;
  }

  @Override
  public void setMarker(boolean value) {
    setPropertyAsBoolean(this, keyMarker, value);
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
  public boolean hasChannelId() {
    return hasProperty(this, keyChannelId);
  }

  @Override
  public void clearChannelId() {
    if (hasProperty(this, keyChannelId)) {
      deleteProperty(this, keyChannelId);
    }
  }

  @Override
  public String getChannelId() {
    return hasProperty(this, keyChannelId) ? getPropertyAsString(this, keyChannelId) : null;
  }

  @Override
  public void setChannelId(String value) {
    setPropertyAsString(this, keyChannelId, value);
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
  public boolean hasFragments() {
    return hasProperty(this, keyFragments);
  }

  @Override
  public void clearFragments() {
    if (hasProperty(this, keyFragments)) {
      deleteProperty(this, keyFragments);
    }
  }

  @Override
  public ProtocolFragments getFragments() {
    return hasProperty(this, keyFragments) ? ((ProtocolFragmentsJsoImpl) getPropertyAsObject(this, keyFragments)) : null;
  }

  @Override
  public void setFragments(ProtocolFragments model) {
    setPropertyAsObject(this, keyFragments, (ProtocolFragmentsJsoImpl) model);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProtocolWaveletUpdateJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProtocolWaveletUpdate) {
      return ProtocolWaveletUpdateUtil.isEqual(this, (ProtocolWaveletUpdate) o);
    } else {
      return false;
    }
  }

}