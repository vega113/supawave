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
import org.waveprotocol.wave.concurrencycontrol.CloseWaveletChannelRequest;
import org.waveprotocol.wave.concurrencycontrol.CloseWaveletChannelRequestUtil;
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
 * Server implementation of CloseWaveletChannelRequest.
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
public final class CloseWaveletChannelRequestProtoImpl
    implements CloseWaveletChannelRequest,
// Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
// An import of it is not resolved correctly from inner classes.
// This appears to be a javac bug. The Eclipse compiler handles it fine.
org.waveprotocol.wave.communication.gson.GsonSerializable,
org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest> {
  private org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest proto = null;
  private org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest.Builder protoBuilder = org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest.newBuilder();
  public CloseWaveletChannelRequestProtoImpl() {
  }

  public CloseWaveletChannelRequestProtoImpl(org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest proto) {
    this.proto = proto;
  }

  public CloseWaveletChannelRequestProtoImpl(CloseWaveletChannelRequest message) {
    copyFrom(message);
  }

  @Override
  public org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest getPB() {
    switchToProto();
    return proto;
  }

  @Override
  public void setPB(org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest proto) {
    this.proto = proto;
    this.protoBuilder = null;
  }

  @Override
  public void copyFrom(CloseWaveletChannelRequest message) {

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
    setChannelId(message.getChannelId());
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
  public String getChannelId() {
    switchToProto();
    return proto.getChannelId();
  }

  @Override
  public void setChannelId(String value) {
    switchToProtoBuilder();
    protoBuilder.setChannelId(value);
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
          ? org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest.newBuilder()
          : org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest.newBuilder(proto);
      proto = null;
    }
  }

  private void invalidateAll() {
    proto = null;
    protoBuilder = org.waveprotocol.wave.concurrencycontrol.ClientServer.CloseWaveletChannelRequest.newBuilder();
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
    json.add("1", new JsonPrimitive(getChannelId()));
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
      setChannelId(elem.getAsString());
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof CloseWaveletChannelRequestProtoImpl) {
      return getPB().equals(((CloseWaveletChannelRequestProtoImpl) o).getPB());
    } else {
      return false;
    }
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (equals(o)) {
      return true;
    } else if (o instanceof CloseWaveletChannelRequest) {
      return CloseWaveletChannelRequestUtil.isEqual(this, (CloseWaveletChannelRequest) o);
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