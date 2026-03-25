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
import org.waveprotocol.box.common.comms.WaveletSnapshot;
import org.waveprotocol.box.common.comms.jso.WaveletSnapshotJsoImpl;

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
import org.waveprotocol.box.common.comms.WaveViewSnapshot;
import org.waveprotocol.box.common.comms.WaveViewSnapshotUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of WaveViewSnapshot backed by a GWT JavaScriptObject.
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
public final class WaveViewSnapshotJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements WaveViewSnapshot {
  private static final String keyWaveId = "1";
  private static final String keyWavelet = "2";
  protected WaveViewSnapshotJsoImpl() {
  }

  public static WaveViewSnapshotJsoImpl create() {
    WaveViewSnapshotJsoImpl instance = (WaveViewSnapshotJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearWavelet();
    return instance;
  }

  @Override
  public void copyFrom(WaveViewSnapshot message) {
    super.copyFrom((WaveViewSnapshotJsoImpl) message);
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
  public List<WaveletSnapshotJsoImpl> getWavelet() {
    initArray(this, keyWavelet);
    List<WaveletSnapshotJsoImpl> list = new ArrayList<WaveletSnapshotJsoImpl>();
    for (int i = 0; i < getWaveletSize(); i++) {
      list.add(getWavelet(i));
    }
    return list;
  }

  @Override
  public void addAllWavelet(List<? extends WaveletSnapshot> models) {
    for (WaveletSnapshot model : models) {
      addWavelet(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public WaveletSnapshotJsoImpl getWavelet(int n) {
    initArray(this, keyWavelet);
    JsArray<WaveletSnapshotJsoImpl> array = getPropertyAsObject(this, keyWavelet).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setWavelet(int n, WaveletSnapshot model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyWavelet);
    ((JsArray<WaveletSnapshotJsoImpl>) getPropertyAsObject(this, keyWavelet)).set(n, (WaveletSnapshotJsoImpl) model);
  }

  @Override
  public int getWaveletSize() {
    return hasProperty(this, keyWavelet) ? ((JsArray<?>) getPropertyAsObject(this, keyWavelet)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addWavelet(WaveletSnapshot model) {
    initArray(this, keyWavelet);
    ((JsArray<WaveletSnapshotJsoImpl>) getPropertyAsObject(this, keyWavelet)).push((WaveletSnapshotJsoImpl) model);
  }

  @Override
  public void clearWavelet() {
    clearArray(this, keyWavelet);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof WaveViewSnapshotJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof WaveViewSnapshot) {
      return WaveViewSnapshotUtil.isEqual(this, (WaveViewSnapshot) o);
    } else {
      return false;
    }
  }

}