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

package org.waveprotocol.wave.diff.jso;

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
import org.waveprotocol.wave.concurrencycontrol.WaveletVersion;
import org.waveprotocol.wave.concurrencycontrol.jso.WaveletVersionJsoImpl;

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
import org.waveprotocol.wave.diff.FetchDiffRequest;
import org.waveprotocol.wave.diff.FetchDiffRequestUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of FetchDiffRequest backed by a GWT JavaScriptObject.
 *
 * Generated from org/waveprotocol/wave/diff/diff.proto. Do not edit.
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
public final class FetchDiffRequestJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements FetchDiffRequest {
  private static final String keyWaveId = "1";
  private static final String keyKnownWavelet = "2";
  protected FetchDiffRequestJsoImpl() {
  }

  public static FetchDiffRequestJsoImpl create() {
    FetchDiffRequestJsoImpl instance = (FetchDiffRequestJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearKnownWavelet();
    return instance;
  }

  @Override
  public void copyFrom(FetchDiffRequest message) {
    super.copyFrom((FetchDiffRequestJsoImpl) message);
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

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof FetchDiffRequestJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof FetchDiffRequest) {
      return FetchDiffRequestUtil.isEqual(this, (FetchDiffRequest) o);
    } else {
      return false;
    }
  }

}