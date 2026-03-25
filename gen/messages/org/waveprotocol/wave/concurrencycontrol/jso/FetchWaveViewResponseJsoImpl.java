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

package org.waveprotocol.wave.concurrencycontrol.jso;

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
import org.waveprotocol.wave.concurrencycontrol.ResponseStatus;
import org.waveprotocol.wave.concurrencycontrol.FetchWaveViewResponse.Wavelet;
import org.waveprotocol.wave.concurrencycontrol.WaveletSnapshot;
import org.waveprotocol.wave.concurrencycontrol.jso.ResponseStatusJsoImpl;
import org.waveprotocol.wave.concurrencycontrol.jso.FetchWaveViewResponseJsoImpl.WaveletJsoImpl;
import org.waveprotocol.wave.concurrencycontrol.jso.WaveletSnapshotJsoImpl;

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
import org.waveprotocol.wave.concurrencycontrol.FetchWaveViewResponse;
import org.waveprotocol.wave.concurrencycontrol.FetchWaveViewResponseUtil;

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
import org.waveprotocol.wave.concurrencycontrol.FetchWaveViewResponse.Wavelet;
import org.waveprotocol.wave.concurrencycontrol.FetchWaveViewResponseUtil.WaveletUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of FetchWaveViewResponse backed by a GWT JavaScriptObject.
 *
 * Generated from org/waveprotocol/wave/concurrencycontrol/clientserver.proto. Do not edit.
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
public final class FetchWaveViewResponseJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements FetchWaveViewResponse {

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
  public static final class WaveletJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
      implements Wavelet {
    private static final String keyWaveletId = "1";
    private static final String keySnapshot = "2";
    protected WaveletJsoImpl() {
    }

    public static WaveletJsoImpl create() {
      WaveletJsoImpl instance = (WaveletJsoImpl) JsonMessage.createJsonMessage();
      // Force all lists to start with an empty list rather than no property for
      // the list. This is so that the native JS equality works, since (obviously)
      // {} != {"foo": []} while in the context of messages they should be.
      return instance;
    }

    @Override
    public void copyFrom(Wavelet message) {
      super.copyFrom((WaveletJsoImpl) message);
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
    public String getWaveletId() {
      return hasProperty(this, keyWaveletId) ? getPropertyAsString(this, keyWaveletId) : null;
    }

    @Override
    public void setWaveletId(String value) {
      setPropertyAsString(this, keyWaveletId, value);
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

    @Override
    public boolean isEqualTo(Object o) {
      if (o instanceof WaveletJsoImpl) {
        return nativeIsEqualTo(o);
      } else if (o instanceof Wavelet) {
        return WaveletUtil.isEqual(this, (Wavelet) o);
      } else {
        return false;
      }
    }

  }

  private static final String keyStatus = "1";
  private static final String keyWavelet = "2";
  protected FetchWaveViewResponseJsoImpl() {
  }

  public static FetchWaveViewResponseJsoImpl create() {
    FetchWaveViewResponseJsoImpl instance = (FetchWaveViewResponseJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearWavelet();
    return instance;
  }

  @Override
  public void copyFrom(FetchWaveViewResponse message) {
    super.copyFrom((FetchWaveViewResponseJsoImpl) message);
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
  public ResponseStatus getStatus() {
    return hasProperty(this, keyStatus) ? ((ResponseStatusJsoImpl) getPropertyAsObject(this, keyStatus)) : null;
  }

  @Override
  public void setStatus(ResponseStatus model) {
    setPropertyAsObject(this, keyStatus, (ResponseStatusJsoImpl) model);
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
  public List<WaveletJsoImpl> getWavelet() {
    initArray(this, keyWavelet);
    List<WaveletJsoImpl> list = new ArrayList<WaveletJsoImpl>();
    for (int i = 0; i < getWaveletSize(); i++) {
      list.add(getWavelet(i));
    }
    return list;
  }

  @Override
  public void addAllWavelet(List<? extends Wavelet> models) {
    for (Wavelet model : models) {
      addWavelet(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public WaveletJsoImpl getWavelet(int n) {
    initArray(this, keyWavelet);
    JsArray<WaveletJsoImpl> array = getPropertyAsObject(this, keyWavelet).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setWavelet(int n, Wavelet model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyWavelet);
    ((JsArray<WaveletJsoImpl>) getPropertyAsObject(this, keyWavelet)).set(n, (WaveletJsoImpl) model);
  }

  @Override
  public int getWaveletSize() {
    return hasProperty(this, keyWavelet) ? ((JsArray<?>) getPropertyAsObject(this, keyWavelet)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addWavelet(Wavelet model) {
    initArray(this, keyWavelet);
    ((JsArray<WaveletJsoImpl>) getPropertyAsObject(this, keyWavelet)).push((WaveletJsoImpl) model);
  }

  @Override
  public void clearWavelet() {
    clearArray(this, keyWavelet);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof FetchWaveViewResponseJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof FetchWaveViewResponse) {
      return FetchWaveViewResponseUtil.isEqual(this, (FetchWaveViewResponse) o);
    } else {
      return false;
    }
  }

}