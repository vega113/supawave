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
import org.waveprotocol.wave.concurrencycontrol.ResponseStatus;
import org.waveprotocol.wave.diff.FetchDiffResponse.WaveletDiff;
import org.waveprotocol.wave.diff.WaveletDiffSnapshot;
import org.waveprotocol.wave.concurrencycontrol.jso.ResponseStatusJsoImpl;
import org.waveprotocol.wave.diff.jso.FetchDiffResponseJsoImpl.WaveletDiffJsoImpl;
import org.waveprotocol.wave.diff.jso.WaveletDiffSnapshotJsoImpl;

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
import org.waveprotocol.wave.diff.FetchDiffResponse;
import org.waveprotocol.wave.diff.FetchDiffResponseUtil;

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
import org.waveprotocol.wave.diff.FetchDiffResponse.WaveletDiff;
import org.waveprotocol.wave.diff.FetchDiffResponseUtil.WaveletDiffUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of FetchDiffResponse backed by a GWT JavaScriptObject.
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
public final class FetchDiffResponseJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements FetchDiffResponse {

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
  public static final class WaveletDiffJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
      implements WaveletDiff {
    private static final String keyWaveletId = "1";
    private static final String keySnapshot = "2";
    protected WaveletDiffJsoImpl() {
    }

    public static WaveletDiffJsoImpl create() {
      WaveletDiffJsoImpl instance = (WaveletDiffJsoImpl) JsonMessage.createJsonMessage();
      // Force all lists to start with an empty list rather than no property for
      // the list. This is so that the native JS equality works, since (obviously)
      // {} != {"foo": []} while in the context of messages they should be.
      return instance;
    }

    @Override
    public void copyFrom(WaveletDiff message) {
      super.copyFrom((WaveletDiffJsoImpl) message);
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
    public WaveletDiffSnapshot getSnapshot() {
      return hasProperty(this, keySnapshot) ? ((WaveletDiffSnapshotJsoImpl) getPropertyAsObject(this, keySnapshot)) : null;
    }

    @Override
    public void setSnapshot(WaveletDiffSnapshot model) {
      setPropertyAsObject(this, keySnapshot, (WaveletDiffSnapshotJsoImpl) model);
    }

    @Override
    public boolean isEqualTo(Object o) {
      if (o instanceof WaveletDiffJsoImpl) {
        return nativeIsEqualTo(o);
      } else if (o instanceof WaveletDiff) {
        return WaveletDiffUtil.isEqual(this, (WaveletDiff) o);
      } else {
        return false;
      }
    }

  }

  private static final String keyStatus = "1";
  private static final String keyWavelet = "2";
  protected FetchDiffResponseJsoImpl() {
  }

  public static FetchDiffResponseJsoImpl create() {
    FetchDiffResponseJsoImpl instance = (FetchDiffResponseJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearWavelet();
    return instance;
  }

  @Override
  public void copyFrom(FetchDiffResponse message) {
    super.copyFrom((FetchDiffResponseJsoImpl) message);
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
  public List<WaveletDiffJsoImpl> getWavelet() {
    initArray(this, keyWavelet);
    List<WaveletDiffJsoImpl> list = new ArrayList<WaveletDiffJsoImpl>();
    for (int i = 0; i < getWaveletSize(); i++) {
      list.add(getWavelet(i));
    }
    return list;
  }

  @Override
  public void addAllWavelet(List<? extends WaveletDiff> models) {
    for (WaveletDiff model : models) {
      addWavelet(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public WaveletDiffJsoImpl getWavelet(int n) {
    initArray(this, keyWavelet);
    JsArray<WaveletDiffJsoImpl> array = getPropertyAsObject(this, keyWavelet).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setWavelet(int n, WaveletDiff model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyWavelet);
    ((JsArray<WaveletDiffJsoImpl>) getPropertyAsObject(this, keyWavelet)).set(n, (WaveletDiffJsoImpl) model);
  }

  @Override
  public int getWaveletSize() {
    return hasProperty(this, keyWavelet) ? ((JsArray<?>) getPropertyAsObject(this, keyWavelet)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addWavelet(WaveletDiff model) {
    initArray(this, keyWavelet);
    ((JsArray<WaveletDiffJsoImpl>) getPropertyAsObject(this, keyWavelet)).push((WaveletDiffJsoImpl) model);
  }

  @Override
  public void clearWavelet() {
    clearArray(this, keyWavelet);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof FetchDiffResponseJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof FetchDiffResponse) {
      return FetchDiffResponseUtil.isEqual(this, (FetchDiffResponse) o);
    } else {
      return false;
    }
  }

}