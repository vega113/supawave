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

package org.waveprotocol.wave.diff.proto;

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
import org.waveprotocol.wave.concurrencycontrol.proto.ResponseStatusProtoImpl;
import org.waveprotocol.wave.diff.proto.FetchDiffResponseProtoImpl.WaveletDiffProtoImpl;
import org.waveprotocol.wave.diff.proto.WaveletDiffSnapshotProtoImpl;

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
 * Server implementation of FetchDiffResponse.
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
// NOTE(kalman): It would be nicer to add a proto serialisation
// utility rather than having this class at all.
public final class FetchDiffResponseProtoImpl
    implements FetchDiffResponse,
// Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
// An import of it is not resolved correctly from inner classes.
// This appears to be a javac bug. The Eclipse compiler handles it fine.
org.waveprotocol.wave.communication.gson.GsonSerializable,
org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.diff.Diff.FetchDiffResponse> {

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
  public static final class WaveletDiffProtoImpl
      implements WaveletDiff,
  // Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
  // An import of it is not resolved correctly from inner classes.
  // This appears to be a javac bug. The Eclipse compiler handles it fine.
  org.waveprotocol.wave.communication.gson.GsonSerializable,
  org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff> {
    private org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff proto = null;
    private org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff.Builder protoBuilder = org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff.newBuilder();
    public WaveletDiffProtoImpl() {
    }

    public WaveletDiffProtoImpl(org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff proto) {
      this.proto = proto;
    }

    public WaveletDiffProtoImpl(WaveletDiff message) {
      copyFrom(message);
    }

    @Override
    public org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff getPB() {
      switchToProto();
      return proto;
    }

    @Override
    public void setPB(org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff proto) {
      this.proto = proto;
      this.protoBuilder = null;
    }

    @Override
    public void copyFrom(WaveletDiff message) {

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
        setSnapshot(new WaveletDiffSnapshotProtoImpl(message.getSnapshot()));
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
    public WaveletDiffSnapshotProtoImpl getSnapshot() {
      switchToProto();
      return new WaveletDiffSnapshotProtoImpl(proto.getSnapshot());
    }

    @Override
    public void setSnapshot(WaveletDiffSnapshot value) {
      switchToProtoBuilder();
      protoBuilder.clearSnapshot();
      protoBuilder.setSnapshot(getOrCreateWaveletDiffSnapshotProtoImpl(value).getPB());
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

    /** Get or create a WaveletDiffSnapshotProtoImpl from a WaveletDiffSnapshot. */
    private WaveletDiffSnapshotProtoImpl getOrCreateWaveletDiffSnapshotProtoImpl(WaveletDiffSnapshot message) {
      if (message instanceof WaveletDiffSnapshotProtoImpl) {
        return (WaveletDiffSnapshotProtoImpl) message;
      } else {
        WaveletDiffSnapshotProtoImpl messageImpl = new WaveletDiffSnapshotProtoImpl();
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
            ? org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff.newBuilder()
            : org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff.newBuilder(proto);
        proto = null;
      }
    }

    private void invalidateAll() {
      proto = null;
      protoBuilder = org.waveprotocol.wave.diff.Diff.FetchDiffResponse.WaveletDiff.newBuilder();
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
            WaveletDiffSnapshotProtoImpl payload = new WaveletDiffSnapshotProtoImpl();
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
      } else if (o instanceof WaveletDiffProtoImpl) {
        return getPB().equals(((WaveletDiffProtoImpl) o).getPB());
      } else {
        return false;
      }
    }

    @Override
    public boolean isEqualTo(Object o) {
      if (equals(o)) {
        return true;
      } else if (o instanceof WaveletDiff) {
        return WaveletDiffUtil.isEqual(this, (WaveletDiff) o);
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

  private org.waveprotocol.wave.diff.Diff.FetchDiffResponse proto = null;
  private org.waveprotocol.wave.diff.Diff.FetchDiffResponse.Builder protoBuilder = org.waveprotocol.wave.diff.Diff.FetchDiffResponse.newBuilder();
  public FetchDiffResponseProtoImpl() {
  }

  public FetchDiffResponseProtoImpl(org.waveprotocol.wave.diff.Diff.FetchDiffResponse proto) {
    this.proto = proto;
  }

  public FetchDiffResponseProtoImpl(FetchDiffResponse message) {
    copyFrom(message);
  }

  @Override
  public org.waveprotocol.wave.diff.Diff.FetchDiffResponse getPB() {
    switchToProto();
    return proto;
  }

  @Override
  public void setPB(org.waveprotocol.wave.diff.Diff.FetchDiffResponse proto) {
    this.proto = proto;
    this.protoBuilder = null;
  }

  @Override
  public void copyFrom(FetchDiffResponse message) {

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
    for (WaveletDiff field : message.getWavelet()) {
      addWavelet(new WaveletDiffProtoImpl(field));
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
  public List<WaveletDiffProtoImpl> getWavelet() {
    switchToProto();
    List<WaveletDiffProtoImpl> list = new ArrayList<WaveletDiffProtoImpl>();
    for (int i = 0; i < getWaveletSize(); i++) {
      WaveletDiffProtoImpl message = new WaveletDiffProtoImpl(proto.getWavelet(i));
      list.add(message);
    }
    return list;
  }

  @Override
  public void addAllWavelet(List<? extends WaveletDiff> values) {
    for (WaveletDiff message : values) {
      addWavelet(message);
    }
  }

  @Override
  public WaveletDiffProtoImpl getWavelet(int n) {
    switchToProto();
    return new WaveletDiffProtoImpl(proto.getWavelet(n));
  }

  @Override
  public void setWavelet(int n, WaveletDiff value) {
    switchToProtoBuilder();
    protoBuilder.setWavelet(n, getOrCreateWaveletDiffProtoImpl(value).getPB());
  }

  @Override
  public int getWaveletSize() {
    switchToProto();
    return proto.getWaveletCount();
  }

  @Override
  public void addWavelet(WaveletDiff value) {
    switchToProtoBuilder();
    protoBuilder.addWavelet(getOrCreateWaveletDiffProtoImpl(value).getPB());
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

  /** Get or create a WaveletDiffProtoImpl from a WaveletDiff. */
  private WaveletDiffProtoImpl getOrCreateWaveletDiffProtoImpl(WaveletDiff message) {
    if (message instanceof WaveletDiffProtoImpl) {
      return (WaveletDiffProtoImpl) message;
    } else {
      WaveletDiffProtoImpl messageImpl = new WaveletDiffProtoImpl();
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

  /** Get or create a WaveletDiffSnapshotProtoImpl from a WaveletDiffSnapshot. */
  private WaveletDiffSnapshotProtoImpl getOrCreateWaveletDiffSnapshotProtoImpl(WaveletDiffSnapshot message) {
    if (message instanceof WaveletDiffSnapshotProtoImpl) {
      return (WaveletDiffSnapshotProtoImpl) message;
    } else {
      WaveletDiffSnapshotProtoImpl messageImpl = new WaveletDiffSnapshotProtoImpl();
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
          ? org.waveprotocol.wave.diff.Diff.FetchDiffResponse.newBuilder()
          : org.waveprotocol.wave.diff.Diff.FetchDiffResponse.newBuilder(proto);
      proto = null;
    }
  }

  private void invalidateAll() {
    proto = null;
    protoBuilder = org.waveprotocol.wave.diff.Diff.FetchDiffResponse.newBuilder();
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
          WaveletDiffProtoImpl payload = new WaveletDiffProtoImpl();
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
    } else if (o instanceof FetchDiffResponseProtoImpl) {
      return getPB().equals(((FetchDiffResponseProtoImpl) o).getPB());
    } else {
      return false;
    }
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (equals(o)) {
      return true;
    } else if (o instanceof FetchDiffResponse) {
      return FetchDiffResponseUtil.isEqual(this, (FetchDiffResponse) o);
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