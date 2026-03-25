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

package org.waveprotocol.wave.concurrencycontrol.proto;

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
import org.waveprotocol.wave.concurrencycontrol.proto.ResponseStatusProtoImpl;
import org.waveprotocol.wave.concurrencycontrol.proto.FetchWaveViewResponseProtoImpl.WaveletProtoImpl;
import org.waveprotocol.wave.concurrencycontrol.proto.WaveletSnapshotProtoImpl;

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
import org.waveprotocol.wave.communication.Codec;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.proto.Int52;
import org.waveprotocol.wave.communication.proto.ProtoWrapper;
import org.waveprotocol.wave.communication.gson.GsonException;
import org.waveprotocol.wave.communication.gson.GsonSerializable;
import org.waveprotocol.wave.communication.gson.GsonUtil;
import org.waveprotocol.wave.communication.json.RawStringData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server implementation of FetchWaveViewResponse.
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
// NOTE(kalman): It would be nicer to add a proto serialisation
// utility rather than having this class at all.
public final class FetchWaveViewResponseProtoImpl
    implements FetchWaveViewResponse,
// Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
// An import of it is not resolved correctly from inner classes.
// This appears to be a javac bug. The Eclipse compiler handles it fine.
org.waveprotocol.wave.communication.gson.GsonSerializable,
org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse> {

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
  // NOTE(kalman): It would be nicer to add a proto serialisation
  // utility rather than having this class at all.
  public static final class WaveletProtoImpl
      implements Wavelet,
  // Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
  // An import of it is not resolved correctly from inner classes.
  // This appears to be a javac bug. The Eclipse compiler handles it fine.
  org.waveprotocol.wave.communication.gson.GsonSerializable,
  org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet> {
    private org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet proto = null;
    private org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet.Builder protoBuilder = org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet.newBuilder();
    public WaveletProtoImpl() {
    }

    public WaveletProtoImpl(org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet proto) {
      this.proto = proto;
    }

    public WaveletProtoImpl(Wavelet message) {
      copyFrom(message);
    }

    @Override
    public org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet getPB() {
      switchToProto();
      return proto;
    }

    @Override
    public void setPB(org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet proto) {
      this.proto = proto;
      this.protoBuilder = null;
    }

    @Override
    public void copyFrom(Wavelet message) {

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
      setWaveletId(message.getWaveletId());

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
      if (message.hasSnapshot()) {
        setSnapshot(new WaveletSnapshotProtoImpl(message.getSnapshot()));
      } else {
        clearSnapshot();
      }
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
      switchToProto();
      return proto.getWaveletId();
    }

    @Override
    public void setWaveletId(String value) {
      switchToProtoBuilder();
      protoBuilder.setWaveletId(value);
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
      switchToProto();
      return proto.hasSnapshot();
    }

    @Override
    public void clearSnapshot() {
      switchToProtoBuilder();
      protoBuilder.clearSnapshot();
    }

    @Override
    public WaveletSnapshotProtoImpl getSnapshot() {
      switchToProto();
      return new WaveletSnapshotProtoImpl(proto.getSnapshot());
    }

    @Override
    public void setSnapshot(WaveletSnapshot value) {
      switchToProtoBuilder();
      protoBuilder.clearSnapshot();
      protoBuilder.setSnapshot(getOrCreateWaveletSnapshotProtoImpl(value).getPB());
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

    /** Get or create a WaveletSnapshotProtoImpl from a WaveletSnapshot. */
    private WaveletSnapshotProtoImpl getOrCreateWaveletSnapshotProtoImpl(WaveletSnapshot message) {
      if (message instanceof WaveletSnapshotProtoImpl) {
        return (WaveletSnapshotProtoImpl) message;
      } else {
        WaveletSnapshotProtoImpl messageImpl = new WaveletSnapshotProtoImpl();
        messageImpl.copyFrom(message);
        return messageImpl;
      }
    }

    private void switchToProto() {
      if (proto == null) {
        proto = protoBuilder.build();
        protoBuilder = null;
      }
    }

    private void switchToProtoBuilder() {
      if (protoBuilder == null) {
        protoBuilder = (proto == null)
            ? org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet.newBuilder()
            : org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet.newBuilder(proto);
        proto = null;
      }
    }

    private void invalidateAll() {
      proto = null;
      protoBuilder = org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Wavelet.newBuilder();
    }

    @Override
    public JsonElement toGson(RawStringData raw, Gson gson) {
      JsonObject json = new JsonObject();

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
      json.add("1", new JsonPrimitive(getWaveletId()));

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
      if (hasSnapshot()) {
        {
          JsonElement elem = ((GsonSerializable) getSnapshot()).toGson(raw, gson);
          json.add("2", elem);
        }
      }
      return json;
    }

    @Override
    public void fromGson(JsonElement json, Gson gson, RawStringData raw) throws GsonException {
      JsonObject jsonObject = json.getAsJsonObject();
      // NOTE: always check with has(...) as the json might not have all required
      // fields set; however these (obviously) will need to be set by other means
      // before accessing this object.
      invalidateAll();

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
      if (jsonObject.has("1")) {
        JsonElement elem = jsonObject.get("1");
        setWaveletId(elem.getAsString());
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
      if (jsonObject.has("2")) {
        JsonElement elem = jsonObject.get("2");
        if (!elem.isJsonNull()) {
          {
            WaveletSnapshotProtoImpl payload = new WaveletSnapshotProtoImpl();
            GsonUtil.extractJsonObject(payload, elem, gson, raw);
            setSnapshot(payload);
          }
        }
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (o instanceof WaveletProtoImpl) {
        return getPB().equals(((WaveletProtoImpl) o).getPB());
      } else {
        return false;
      }
    }

    @Override
    public boolean isEqualTo(Object o) {
      if (equals(o)) {
        return true;
      } else if (o instanceof Wavelet) {
        return WaveletUtil.isEqual(this, (Wavelet) o);
      } else {
        return false;
      }
    }

    @Override
    public int hashCode() {
      return getPB().hashCode();
    }

    @Override
    public String toString() {
      return getPB().toString();
    }

  }

  private org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse proto = null;
  private org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.Builder protoBuilder = org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.newBuilder();
  public FetchWaveViewResponseProtoImpl() {
  }

  public FetchWaveViewResponseProtoImpl(org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse proto) {
    this.proto = proto;
  }

  public FetchWaveViewResponseProtoImpl(FetchWaveViewResponse message) {
    copyFrom(message);
  }

  @Override
  public org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse getPB() {
    switchToProto();
    return proto;
  }

  @Override
  public void setPB(org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse proto) {
    this.proto = proto;
    this.protoBuilder = null;
  }

  @Override
  public void copyFrom(FetchWaveViewResponse message) {

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
    setStatus(new ResponseStatusProtoImpl(message.getStatus()));

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
    clearWavelet();
    for (Wavelet field : message.getWavelet()) {
      addWavelet(new WaveletProtoImpl(field));
    }
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
  public ResponseStatusProtoImpl getStatus() {
    switchToProto();
    return new ResponseStatusProtoImpl(proto.getStatus());
  }

  @Override
  public void setStatus(ResponseStatus value) {
    switchToProtoBuilder();
    protoBuilder.clearStatus();
    protoBuilder.setStatus(getOrCreateResponseStatusProtoImpl(value).getPB());
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
  public List<WaveletProtoImpl> getWavelet() {
    switchToProto();
    List<WaveletProtoImpl> list = new ArrayList<WaveletProtoImpl>();
    for (int i = 0; i < getWaveletSize(); i++) {
      WaveletProtoImpl message = new WaveletProtoImpl(proto.getWavelet(i));
      list.add(message);
    }
    return list;
  }

  @Override
  public void addAllWavelet(List<? extends Wavelet> values) {
    for (Wavelet message : values) {
      addWavelet(message);
    }
  }

  @Override
  public WaveletProtoImpl getWavelet(int n) {
    switchToProto();
    return new WaveletProtoImpl(proto.getWavelet(n));
  }

  @Override
  public void setWavelet(int n, Wavelet value) {
    switchToProtoBuilder();
    protoBuilder.setWavelet(n, getOrCreateWaveletProtoImpl(value).getPB());
  }

  @Override
  public int getWaveletSize() {
    switchToProto();
    return proto.getWaveletCount();
  }

  @Override
  public void addWavelet(Wavelet value) {
    switchToProtoBuilder();
    protoBuilder.addWavelet(getOrCreateWaveletProtoImpl(value).getPB());
  }

  @Override
  public void clearWavelet() {
    switchToProtoBuilder();
    protoBuilder.clearWavelet();
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

  /** Get or create a ResponseStatusProtoImpl from a ResponseStatus. */
  private ResponseStatusProtoImpl getOrCreateResponseStatusProtoImpl(ResponseStatus message) {
    if (message instanceof ResponseStatusProtoImpl) {
      return (ResponseStatusProtoImpl) message;
    } else {
      ResponseStatusProtoImpl messageImpl = new ResponseStatusProtoImpl();
      messageImpl.copyFrom(message);
      return messageImpl;
    }
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

  /** Get or create a WaveletProtoImpl from a Wavelet. */
  private WaveletProtoImpl getOrCreateWaveletProtoImpl(Wavelet message) {
    if (message instanceof WaveletProtoImpl) {
      return (WaveletProtoImpl) message;
    } else {
      WaveletProtoImpl messageImpl = new WaveletProtoImpl();
      messageImpl.copyFrom(message);
      return messageImpl;
    }
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

  /** Get or create a WaveletSnapshotProtoImpl from a WaveletSnapshot. */
  private WaveletSnapshotProtoImpl getOrCreateWaveletSnapshotProtoImpl(WaveletSnapshot message) {
    if (message instanceof WaveletSnapshotProtoImpl) {
      return (WaveletSnapshotProtoImpl) message;
    } else {
      WaveletSnapshotProtoImpl messageImpl = new WaveletSnapshotProtoImpl();
      messageImpl.copyFrom(message);
      return messageImpl;
    }
  }

  private void switchToProto() {
    if (proto == null) {
      proto = protoBuilder.build();
      protoBuilder = null;
    }
  }

  private void switchToProtoBuilder() {
    if (protoBuilder == null) {
      protoBuilder = (proto == null)
          ? org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.newBuilder()
          : org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.newBuilder(proto);
      proto = null;
    }
  }

  private void invalidateAll() {
    proto = null;
    protoBuilder = org.waveprotocol.wave.concurrencycontrol.ClientServer.FetchWaveViewResponse.newBuilder();
  }

  @Override
  public JsonElement toGson(RawStringData raw, Gson gson) {
    JsonObject json = new JsonObject();

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
    {
      JsonElement elem = ((GsonSerializable) getStatus()).toGson(raw, gson);
      json.add("1", elem);
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
    {
      JsonArray array = new JsonArray();
      for (int i = 0; i < getWaveletSize(); i++) {
        JsonElement elem = ((GsonSerializable) getWavelet(i)).toGson(raw, gson);
        // NOTE(kalman): if multistage parsing worked, split point would go here.
        array.add(elem);
      }
      json.add("2", array);
    }
    return json;
  }

  @Override
  public void fromGson(JsonElement json, Gson gson, RawStringData raw) throws GsonException {
    JsonObject jsonObject = json.getAsJsonObject();
    // NOTE: always check with has(...) as the json might not have all required
    // fields set; however these (obviously) will need to be set by other means
    // before accessing this object.
    invalidateAll();

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
    if (jsonObject.has("1")) {
      JsonElement elem = jsonObject.get("1");
      {
        ResponseStatusProtoImpl payload = new ResponseStatusProtoImpl();
        GsonUtil.extractJsonObject(payload, elem, gson, raw);
        setStatus(payload);
      }
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
    if (jsonObject.has("2")) {
      JsonElement elem = jsonObject.get("2");
      {
        JsonArray array = elem.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
          WaveletProtoImpl payload = new WaveletProtoImpl();
          GsonUtil.extractJsonObject(payload, array.get(i), gson, raw);
          addWavelet(payload);
        }
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof FetchWaveViewResponseProtoImpl) {
      return getPB().equals(((FetchWaveViewResponseProtoImpl) o).getPB());
    } else {
      return false;
    }
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (equals(o)) {
      return true;
    } else if (o instanceof FetchWaveViewResponse) {
      return FetchWaveViewResponseUtil.isEqual(this, (FetchWaveViewResponse) o);
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return getPB().hashCode();
  }

  @Override
  public String toString() {
    return getPB().toString();
  }

}