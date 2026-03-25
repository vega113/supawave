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

package org.waveprotocol.wave.federation.proto;

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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.AnnotationBoundary;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ElementStart;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ReplaceAttributes;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.UpdateAttributes;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValuePair;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValueUpdate;
import org.waveprotocol.wave.federation.proto.ProtocolDocumentOperationProtoImpl.ComponentProtoImpl;
import org.waveprotocol.wave.federation.proto.ProtocolDocumentOperationProtoImpl.ComponentProtoImpl.AnnotationBoundaryProtoImpl;
import org.waveprotocol.wave.federation.proto.ProtocolDocumentOperationProtoImpl.ComponentProtoImpl.ElementStartProtoImpl;
import org.waveprotocol.wave.federation.proto.ProtocolDocumentOperationProtoImpl.ComponentProtoImpl.ReplaceAttributesProtoImpl;
import org.waveprotocol.wave.federation.proto.ProtocolDocumentOperationProtoImpl.ComponentProtoImpl.UpdateAttributesProtoImpl;
import org.waveprotocol.wave.federation.proto.ProtocolDocumentOperationProtoImpl.ComponentProtoImpl.KeyValuePairProtoImpl;
import org.waveprotocol.wave.federation.proto.ProtocolDocumentOperationProtoImpl.ComponentProtoImpl.KeyValueUpdateProtoImpl;

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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation;
import org.waveprotocol.wave.federation.ProtocolDocumentOperationUtil;

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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component;
import org.waveprotocol.wave.federation.ProtocolDocumentOperationUtil.ComponentUtil;

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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValuePair;
import org.waveprotocol.wave.federation.ProtocolDocumentOperationUtil.ComponentUtil.KeyValuePairUtil;

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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValueUpdate;
import org.waveprotocol.wave.federation.ProtocolDocumentOperationUtil.ComponentUtil.KeyValueUpdateUtil;

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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ElementStart;
import org.waveprotocol.wave.federation.ProtocolDocumentOperationUtil.ComponentUtil.ElementStartUtil;

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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ReplaceAttributes;
import org.waveprotocol.wave.federation.ProtocolDocumentOperationUtil.ComponentUtil.ReplaceAttributesUtil;

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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.UpdateAttributes;
import org.waveprotocol.wave.federation.ProtocolDocumentOperationUtil.ComponentUtil.UpdateAttributesUtil;

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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.AnnotationBoundary;
import org.waveprotocol.wave.federation.ProtocolDocumentOperationUtil.ComponentUtil.AnnotationBoundaryUtil;
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
 * Server implementation of ProtocolDocumentOperation.
 *
 * Generated from org/waveprotocol/wave/federation/federation.proto. Do not edit.
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
public final class ProtocolDocumentOperationProtoImpl
    implements ProtocolDocumentOperation,
// Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
// An import of it is not resolved correctly from inner classes.
// This appears to be a javac bug. The Eclipse compiler handles it fine.
org.waveprotocol.wave.communication.gson.GsonSerializable,
org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation> {

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
  public static final class ComponentProtoImpl
      implements Component,
  // Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
  // An import of it is not resolved correctly from inner classes.
  // This appears to be a javac bug. The Eclipse compiler handles it fine.
  org.waveprotocol.wave.communication.gson.GsonSerializable,
  org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component> {

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
    public static final class KeyValuePairProtoImpl
        implements KeyValuePair,
    // Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
    // An import of it is not resolved correctly from inner classes.
    // This appears to be a javac bug. The Eclipse compiler handles it fine.
    org.waveprotocol.wave.communication.gson.GsonSerializable,
    org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair> {
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair proto = null;
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair.Builder protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair.newBuilder();
      public KeyValuePairProtoImpl() {
      }

      public KeyValuePairProtoImpl(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair proto) {
        this.proto = proto;
      }

      public KeyValuePairProtoImpl(KeyValuePair message) {
        copyFrom(message);
      }

      @Override
      public org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair getPB() {
        switchToProto();
        return proto;
      }

      @Override
      public void setPB(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair proto) {
        this.proto = proto;
        this.protoBuilder = null;
      }

      @Override
      public void copyFrom(KeyValuePair message) {

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
        setKey(message.getKey());

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
        setValue(message.getValue());
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
      public String getKey() {
        switchToProto();
        return proto.getKey();
      }

      @Override
      public void setKey(String value) {
        switchToProtoBuilder();
        protoBuilder.setKey(value);
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
      public String getValue() {
        switchToProto();
        return proto.getValue();
      }

      @Override
      public void setValue(String value) {
        switchToProtoBuilder();
        protoBuilder.setValue(value);
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
              ? org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair.newBuilder()
              : org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair.newBuilder(proto);
          proto = null;
        }
      }

      private void invalidateAll() {
        proto = null;
        protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValuePair.newBuilder();
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
        json.add("1", new JsonPrimitive(getKey()));

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
        json.add("2", new JsonPrimitive(getValue()));
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
          setKey(elem.getAsString());
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
          setValue(elem.getAsString());
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        } else if (o instanceof KeyValuePairProtoImpl) {
          return getPB().equals(((KeyValuePairProtoImpl) o).getPB());
        } else {
          return false;
        }
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (equals(o)) {
          return true;
        } else if (o instanceof KeyValuePair) {
          return KeyValuePairUtil.isEqual(this, (KeyValuePair) o);
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
    public static final class KeyValueUpdateProtoImpl
        implements KeyValueUpdate,
    // Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
    // An import of it is not resolved correctly from inner classes.
    // This appears to be a javac bug. The Eclipse compiler handles it fine.
    org.waveprotocol.wave.communication.gson.GsonSerializable,
    org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate> {
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate proto = null;
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate.Builder protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate.newBuilder();
      public KeyValueUpdateProtoImpl() {
      }

      public KeyValueUpdateProtoImpl(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate proto) {
        this.proto = proto;
      }

      public KeyValueUpdateProtoImpl(KeyValueUpdate message) {
        copyFrom(message);
      }

      @Override
      public org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate getPB() {
        switchToProto();
        return proto;
      }

      @Override
      public void setPB(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate proto) {
        this.proto = proto;
        this.protoBuilder = null;
      }

      @Override
      public void copyFrom(KeyValueUpdate message) {

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
        setKey(message.getKey());

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
        if (message.hasOldValue()) {
          setOldValue(message.getOldValue());
        } else {
          clearOldValue();
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
        if (message.hasNewValue()) {
          setNewValue(message.getNewValue());
        } else {
          clearNewValue();
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
      public String getKey() {
        switchToProto();
        return proto.getKey();
      }

      @Override
      public void setKey(String value) {
        switchToProtoBuilder();
        protoBuilder.setKey(value);
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
      public boolean hasOldValue() {
        switchToProto();
        return proto.hasOldValue();
      }

      @Override
      public void clearOldValue() {
        switchToProtoBuilder();
        protoBuilder.clearOldValue();
      }

      @Override
      public String getOldValue() {
        switchToProto();
        return proto.getOldValue();
      }

      @Override
      public void setOldValue(String value) {
        switchToProtoBuilder();
        protoBuilder.setOldValue(value);
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
      public boolean hasNewValue() {
        switchToProto();
        return proto.hasNewValue();
      }

      @Override
      public void clearNewValue() {
        switchToProtoBuilder();
        protoBuilder.clearNewValue();
      }

      @Override
      public String getNewValue() {
        switchToProto();
        return proto.getNewValue();
      }

      @Override
      public void setNewValue(String value) {
        switchToProtoBuilder();
        protoBuilder.setNewValue(value);
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
              ? org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate.newBuilder()
              : org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate.newBuilder(proto);
          proto = null;
        }
      }

      private void invalidateAll() {
        proto = null;
        protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.KeyValueUpdate.newBuilder();
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
        json.add("1", new JsonPrimitive(getKey()));

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
        if (hasOldValue()) {
          json.add("2", new JsonPrimitive(getOldValue()));
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
        if (hasNewValue()) {
          json.add("3", new JsonPrimitive(getNewValue()));
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
          setKey(elem.getAsString());
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
            setOldValue(elem.getAsString());
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
        if (jsonObject.has("3")) {
          JsonElement elem = jsonObject.get("3");
          if (!elem.isJsonNull()) {
            setNewValue(elem.getAsString());
          }
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        } else if (o instanceof KeyValueUpdateProtoImpl) {
          return getPB().equals(((KeyValueUpdateProtoImpl) o).getPB());
        } else {
          return false;
        }
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (equals(o)) {
          return true;
        } else if (o instanceof KeyValueUpdate) {
          return KeyValueUpdateUtil.isEqual(this, (KeyValueUpdate) o);
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
    public static final class ElementStartProtoImpl
        implements ElementStart,
    // Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
    // An import of it is not resolved correctly from inner classes.
    // This appears to be a javac bug. The Eclipse compiler handles it fine.
    org.waveprotocol.wave.communication.gson.GsonSerializable,
    org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart> {
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart proto = null;
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart.Builder protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart.newBuilder();
      public ElementStartProtoImpl() {
      }

      public ElementStartProtoImpl(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart proto) {
        this.proto = proto;
      }

      public ElementStartProtoImpl(ElementStart message) {
        copyFrom(message);
      }

      @Override
      public org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart getPB() {
        switchToProto();
        return proto;
      }

      @Override
      public void setPB(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart proto) {
        this.proto = proto;
        this.protoBuilder = null;
      }

      @Override
      public void copyFrom(ElementStart message) {

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
        setType(message.getType());

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
        clearAttribute();
        for (KeyValuePair field : message.getAttribute()) {
          addAttribute(new KeyValuePairProtoImpl(field));
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
      public String getType() {
        switchToProto();
        return proto.getType();
      }

      @Override
      public void setType(String value) {
        switchToProtoBuilder();
        protoBuilder.setType(value);
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
      public List<KeyValuePairProtoImpl> getAttribute() {
        switchToProto();
        List<KeyValuePairProtoImpl> list = new ArrayList<KeyValuePairProtoImpl>();
        for (int i = 0; i < getAttributeSize(); i++) {
          KeyValuePairProtoImpl message = new KeyValuePairProtoImpl(proto.getAttribute(i));
          list.add(message);
        }
        return list;
      }

      @Override
      public void addAllAttribute(List<? extends KeyValuePair> values) {
        for (KeyValuePair message : values) {
          addAttribute(message);
        }
      }

      @Override
      public KeyValuePairProtoImpl getAttribute(int n) {
        switchToProto();
        return new KeyValuePairProtoImpl(proto.getAttribute(n));
      }

      @Override
      public void setAttribute(int n, KeyValuePair value) {
        switchToProtoBuilder();
        protoBuilder.setAttribute(n, getOrCreateKeyValuePairProtoImpl(value).getPB());
      }

      @Override
      public int getAttributeSize() {
        switchToProto();
        return proto.getAttributeCount();
      }

      @Override
      public void addAttribute(KeyValuePair value) {
        switchToProtoBuilder();
        protoBuilder.addAttribute(getOrCreateKeyValuePairProtoImpl(value).getPB());
      }

      @Override
      public void clearAttribute() {
        switchToProtoBuilder();
        protoBuilder.clearAttribute();
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

      /** Get or create a KeyValuePairProtoImpl from a KeyValuePair. */
      private KeyValuePairProtoImpl getOrCreateKeyValuePairProtoImpl(KeyValuePair message) {
        if (message instanceof KeyValuePairProtoImpl) {
          return (KeyValuePairProtoImpl) message;
        } else {
          KeyValuePairProtoImpl messageImpl = new KeyValuePairProtoImpl();
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
              ? org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart.newBuilder()
              : org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart.newBuilder(proto);
          proto = null;
        }
      }

      private void invalidateAll() {
        proto = null;
        protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ElementStart.newBuilder();
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
        json.add("1", new JsonPrimitive(getType()));

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
          for (int i = 0; i < getAttributeSize(); i++) {
            JsonElement elem = ((GsonSerializable) getAttribute(i)).toGson(raw, gson);
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
          setType(elem.getAsString());
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
              KeyValuePairProtoImpl payload = new KeyValuePairProtoImpl();
              GsonUtil.extractJsonObject(payload, array.get(i), gson, raw);
              addAttribute(payload);
            }
          }
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        } else if (o instanceof ElementStartProtoImpl) {
          return getPB().equals(((ElementStartProtoImpl) o).getPB());
        } else {
          return false;
        }
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (equals(o)) {
          return true;
        } else if (o instanceof ElementStart) {
          return ElementStartUtil.isEqual(this, (ElementStart) o);
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
    public static final class ReplaceAttributesProtoImpl
        implements ReplaceAttributes,
    // Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
    // An import of it is not resolved correctly from inner classes.
    // This appears to be a javac bug. The Eclipse compiler handles it fine.
    org.waveprotocol.wave.communication.gson.GsonSerializable,
    org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes> {
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes proto = null;
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes.Builder protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes.newBuilder();
      public ReplaceAttributesProtoImpl() {
      }

      public ReplaceAttributesProtoImpl(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes proto) {
        this.proto = proto;
      }

      public ReplaceAttributesProtoImpl(ReplaceAttributes message) {
        copyFrom(message);
      }

      @Override
      public org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes getPB() {
        switchToProto();
        return proto;
      }

      @Override
      public void setPB(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes proto) {
        this.proto = proto;
        this.protoBuilder = null;
      }

      @Override
      public void copyFrom(ReplaceAttributes message) {

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
        if (message.hasEmpty()) {
          setEmpty(message.getEmpty());
        } else {
          clearEmpty();
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
        clearOldAttribute();
        for (KeyValuePair field : message.getOldAttribute()) {
          addOldAttribute(new KeyValuePairProtoImpl(field));
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
        clearNewAttribute();
        for (KeyValuePair field : message.getNewAttribute()) {
          addNewAttribute(new KeyValuePairProtoImpl(field));
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
      public boolean hasEmpty() {
        switchToProto();
        return proto.hasEmpty();
      }

      @Override
      public void clearEmpty() {
        switchToProtoBuilder();
        protoBuilder.clearEmpty();
      }

      @Override
      public boolean getEmpty() {
        switchToProto();
        return proto.getEmpty();
      }

      @Override
      public void setEmpty(boolean value) {
        switchToProtoBuilder();
        protoBuilder.setEmpty(value);
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
      public List<KeyValuePairProtoImpl> getOldAttribute() {
        switchToProto();
        List<KeyValuePairProtoImpl> list = new ArrayList<KeyValuePairProtoImpl>();
        for (int i = 0; i < getOldAttributeSize(); i++) {
          KeyValuePairProtoImpl message = new KeyValuePairProtoImpl(proto.getOldAttribute(i));
          list.add(message);
        }
        return list;
      }

      @Override
      public void addAllOldAttribute(List<? extends KeyValuePair> values) {
        for (KeyValuePair message : values) {
          addOldAttribute(message);
        }
      }

      @Override
      public KeyValuePairProtoImpl getOldAttribute(int n) {
        switchToProto();
        return new KeyValuePairProtoImpl(proto.getOldAttribute(n));
      }

      @Override
      public void setOldAttribute(int n, KeyValuePair value) {
        switchToProtoBuilder();
        protoBuilder.setOldAttribute(n, getOrCreateKeyValuePairProtoImpl(value).getPB());
      }

      @Override
      public int getOldAttributeSize() {
        switchToProto();
        return proto.getOldAttributeCount();
      }

      @Override
      public void addOldAttribute(KeyValuePair value) {
        switchToProtoBuilder();
        protoBuilder.addOldAttribute(getOrCreateKeyValuePairProtoImpl(value).getPB());
      }

      @Override
      public void clearOldAttribute() {
        switchToProtoBuilder();
        protoBuilder.clearOldAttribute();
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
      public List<KeyValuePairProtoImpl> getNewAttribute() {
        switchToProto();
        List<KeyValuePairProtoImpl> list = new ArrayList<KeyValuePairProtoImpl>();
        for (int i = 0; i < getNewAttributeSize(); i++) {
          KeyValuePairProtoImpl message = new KeyValuePairProtoImpl(proto.getNewAttribute(i));
          list.add(message);
        }
        return list;
      }

      @Override
      public void addAllNewAttribute(List<? extends KeyValuePair> values) {
        for (KeyValuePair message : values) {
          addNewAttribute(message);
        }
      }

      @Override
      public KeyValuePairProtoImpl getNewAttribute(int n) {
        switchToProto();
        return new KeyValuePairProtoImpl(proto.getNewAttribute(n));
      }

      @Override
      public void setNewAttribute(int n, KeyValuePair value) {
        switchToProtoBuilder();
        protoBuilder.setNewAttribute(n, getOrCreateKeyValuePairProtoImpl(value).getPB());
      }

      @Override
      public int getNewAttributeSize() {
        switchToProto();
        return proto.getNewAttributeCount();
      }

      @Override
      public void addNewAttribute(KeyValuePair value) {
        switchToProtoBuilder();
        protoBuilder.addNewAttribute(getOrCreateKeyValuePairProtoImpl(value).getPB());
      }

      @Override
      public void clearNewAttribute() {
        switchToProtoBuilder();
        protoBuilder.clearNewAttribute();
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

      /** Get or create a KeyValuePairProtoImpl from a KeyValuePair. */
      private KeyValuePairProtoImpl getOrCreateKeyValuePairProtoImpl(KeyValuePair message) {
        if (message instanceof KeyValuePairProtoImpl) {
          return (KeyValuePairProtoImpl) message;
        } else {
          KeyValuePairProtoImpl messageImpl = new KeyValuePairProtoImpl();
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
              ? org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes.newBuilder()
              : org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes.newBuilder(proto);
          proto = null;
        }
      }

      private void invalidateAll() {
        proto = null;
        protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.ReplaceAttributes.newBuilder();
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
        if (hasEmpty()) {
          json.add("1", new JsonPrimitive(getEmpty()));
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
          for (int i = 0; i < getOldAttributeSize(); i++) {
            JsonElement elem = ((GsonSerializable) getOldAttribute(i)).toGson(raw, gson);
            // NOTE(kalman): if multistage parsing worked, split point would go here.
            array.add(elem);
          }
          json.add("2", array);
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
          for (int i = 0; i < getNewAttributeSize(); i++) {
            JsonElement elem = ((GsonSerializable) getNewAttribute(i)).toGson(raw, gson);
            // NOTE(kalman): if multistage parsing worked, split point would go here.
            array.add(elem);
          }
          json.add("3", array);
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
          if (!elem.isJsonNull()) {
            setEmpty(elem.getAsBoolean());
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
              KeyValuePairProtoImpl payload = new KeyValuePairProtoImpl();
              GsonUtil.extractJsonObject(payload, array.get(i), gson, raw);
              addOldAttribute(payload);
            }
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
        if (jsonObject.has("3")) {
          JsonElement elem = jsonObject.get("3");
          {
            JsonArray array = elem.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
              KeyValuePairProtoImpl payload = new KeyValuePairProtoImpl();
              GsonUtil.extractJsonObject(payload, array.get(i), gson, raw);
              addNewAttribute(payload);
            }
          }
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        } else if (o instanceof ReplaceAttributesProtoImpl) {
          return getPB().equals(((ReplaceAttributesProtoImpl) o).getPB());
        } else {
          return false;
        }
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (equals(o)) {
          return true;
        } else if (o instanceof ReplaceAttributes) {
          return ReplaceAttributesUtil.isEqual(this, (ReplaceAttributes) o);
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
    public static final class UpdateAttributesProtoImpl
        implements UpdateAttributes,
    // Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
    // An import of it is not resolved correctly from inner classes.
    // This appears to be a javac bug. The Eclipse compiler handles it fine.
    org.waveprotocol.wave.communication.gson.GsonSerializable,
    org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes> {
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes proto = null;
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes.Builder protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes.newBuilder();
      public UpdateAttributesProtoImpl() {
      }

      public UpdateAttributesProtoImpl(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes proto) {
        this.proto = proto;
      }

      public UpdateAttributesProtoImpl(UpdateAttributes message) {
        copyFrom(message);
      }

      @Override
      public org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes getPB() {
        switchToProto();
        return proto;
      }

      @Override
      public void setPB(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes proto) {
        this.proto = proto;
        this.protoBuilder = null;
      }

      @Override
      public void copyFrom(UpdateAttributes message) {

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
        if (message.hasEmpty()) {
          setEmpty(message.getEmpty());
        } else {
          clearEmpty();
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
        clearAttributeUpdate();
        for (KeyValueUpdate field : message.getAttributeUpdate()) {
          addAttributeUpdate(new KeyValueUpdateProtoImpl(field));
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
      public boolean hasEmpty() {
        switchToProto();
        return proto.hasEmpty();
      }

      @Override
      public void clearEmpty() {
        switchToProtoBuilder();
        protoBuilder.clearEmpty();
      }

      @Override
      public boolean getEmpty() {
        switchToProto();
        return proto.getEmpty();
      }

      @Override
      public void setEmpty(boolean value) {
        switchToProtoBuilder();
        protoBuilder.setEmpty(value);
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
      public List<KeyValueUpdateProtoImpl> getAttributeUpdate() {
        switchToProto();
        List<KeyValueUpdateProtoImpl> list = new ArrayList<KeyValueUpdateProtoImpl>();
        for (int i = 0; i < getAttributeUpdateSize(); i++) {
          KeyValueUpdateProtoImpl message = new KeyValueUpdateProtoImpl(proto.getAttributeUpdate(i));
          list.add(message);
        }
        return list;
      }

      @Override
      public void addAllAttributeUpdate(List<? extends KeyValueUpdate> values) {
        for (KeyValueUpdate message : values) {
          addAttributeUpdate(message);
        }
      }

      @Override
      public KeyValueUpdateProtoImpl getAttributeUpdate(int n) {
        switchToProto();
        return new KeyValueUpdateProtoImpl(proto.getAttributeUpdate(n));
      }

      @Override
      public void setAttributeUpdate(int n, KeyValueUpdate value) {
        switchToProtoBuilder();
        protoBuilder.setAttributeUpdate(n, getOrCreateKeyValueUpdateProtoImpl(value).getPB());
      }

      @Override
      public int getAttributeUpdateSize() {
        switchToProto();
        return proto.getAttributeUpdateCount();
      }

      @Override
      public void addAttributeUpdate(KeyValueUpdate value) {
        switchToProtoBuilder();
        protoBuilder.addAttributeUpdate(getOrCreateKeyValueUpdateProtoImpl(value).getPB());
      }

      @Override
      public void clearAttributeUpdate() {
        switchToProtoBuilder();
        protoBuilder.clearAttributeUpdate();
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

      /** Get or create a KeyValueUpdateProtoImpl from a KeyValueUpdate. */
      private KeyValueUpdateProtoImpl getOrCreateKeyValueUpdateProtoImpl(KeyValueUpdate message) {
        if (message instanceof KeyValueUpdateProtoImpl) {
          return (KeyValueUpdateProtoImpl) message;
        } else {
          KeyValueUpdateProtoImpl messageImpl = new KeyValueUpdateProtoImpl();
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
              ? org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes.newBuilder()
              : org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes.newBuilder(proto);
          proto = null;
        }
      }

      private void invalidateAll() {
        proto = null;
        protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.UpdateAttributes.newBuilder();
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
        if (hasEmpty()) {
          json.add("1", new JsonPrimitive(getEmpty()));
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
          for (int i = 0; i < getAttributeUpdateSize(); i++) {
            JsonElement elem = ((GsonSerializable) getAttributeUpdate(i)).toGson(raw, gson);
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
          if (!elem.isJsonNull()) {
            setEmpty(elem.getAsBoolean());
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
              KeyValueUpdateProtoImpl payload = new KeyValueUpdateProtoImpl();
              GsonUtil.extractJsonObject(payload, array.get(i), gson, raw);
              addAttributeUpdate(payload);
            }
          }
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        } else if (o instanceof UpdateAttributesProtoImpl) {
          return getPB().equals(((UpdateAttributesProtoImpl) o).getPB());
        } else {
          return false;
        }
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (equals(o)) {
          return true;
        } else if (o instanceof UpdateAttributes) {
          return UpdateAttributesUtil.isEqual(this, (UpdateAttributes) o);
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
    public static final class AnnotationBoundaryProtoImpl
        implements AnnotationBoundary,
    // Note: fully-qualified path is required for GsonSerializable and ProtoWrapper.
    // An import of it is not resolved correctly from inner classes.
    // This appears to be a javac bug. The Eclipse compiler handles it fine.
    org.waveprotocol.wave.communication.gson.GsonSerializable,
    org.waveprotocol.wave.communication.proto.ProtoWrapper<org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary> {
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary proto = null;
      private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary.Builder protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary.newBuilder();
      public AnnotationBoundaryProtoImpl() {
      }

      public AnnotationBoundaryProtoImpl(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary proto) {
        this.proto = proto;
      }

      public AnnotationBoundaryProtoImpl(AnnotationBoundary message) {
        copyFrom(message);
      }

      @Override
      public org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary getPB() {
        switchToProto();
        return proto;
      }

      @Override
      public void setPB(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary proto) {
        this.proto = proto;
        this.protoBuilder = null;
      }

      @Override
      public void copyFrom(AnnotationBoundary message) {

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
        if (message.hasEmpty()) {
          setEmpty(message.getEmpty());
        } else {
          clearEmpty();
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
        clearEnd();
        for (String field : message.getEnd()) {
          addEnd(field);
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
        clearChange();
        for (KeyValueUpdate field : message.getChange()) {
          addChange(new KeyValueUpdateProtoImpl(field));
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
      public boolean hasEmpty() {
        switchToProto();
        return proto.hasEmpty();
      }

      @Override
      public void clearEmpty() {
        switchToProtoBuilder();
        protoBuilder.clearEmpty();
      }

      @Override
      public boolean getEmpty() {
        switchToProto();
        return proto.getEmpty();
      }

      @Override
      public void setEmpty(boolean value) {
        switchToProtoBuilder();
        protoBuilder.setEmpty(value);
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
      public List<String> getEnd() {
        switchToProto();
        return Collections.unmodifiableList(proto.getEndList());
      }

      @Override
      public void addAllEnd(List<String> values) {
        switchToProtoBuilder();
        protoBuilder.addAllEnd(values);
      }

      @Override
      public String getEnd(int n) {
        switchToProto();
        return proto.getEnd(n);
      }

      @Override
      public void setEnd(int n, String value) {
        switchToProtoBuilder();
        protoBuilder.setEnd(n, value);
      }

      @Override
      public int getEndSize() {
        switchToProto();
        return proto.getEndCount();
      }

      @Override
      public void addEnd(String value) {
        switchToProtoBuilder();
        protoBuilder.addEnd(value);
      }

      @Override
      public void clearEnd() {
        switchToProtoBuilder();
        protoBuilder.clearEnd();
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
      public List<KeyValueUpdateProtoImpl> getChange() {
        switchToProto();
        List<KeyValueUpdateProtoImpl> list = new ArrayList<KeyValueUpdateProtoImpl>();
        for (int i = 0; i < getChangeSize(); i++) {
          KeyValueUpdateProtoImpl message = new KeyValueUpdateProtoImpl(proto.getChange(i));
          list.add(message);
        }
        return list;
      }

      @Override
      public void addAllChange(List<? extends KeyValueUpdate> values) {
        for (KeyValueUpdate message : values) {
          addChange(message);
        }
      }

      @Override
      public KeyValueUpdateProtoImpl getChange(int n) {
        switchToProto();
        return new KeyValueUpdateProtoImpl(proto.getChange(n));
      }

      @Override
      public void setChange(int n, KeyValueUpdate value) {
        switchToProtoBuilder();
        protoBuilder.setChange(n, getOrCreateKeyValueUpdateProtoImpl(value).getPB());
      }

      @Override
      public int getChangeSize() {
        switchToProto();
        return proto.getChangeCount();
      }

      @Override
      public void addChange(KeyValueUpdate value) {
        switchToProtoBuilder();
        protoBuilder.addChange(getOrCreateKeyValueUpdateProtoImpl(value).getPB());
      }

      @Override
      public void clearChange() {
        switchToProtoBuilder();
        protoBuilder.clearChange();
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

      /** Get or create a KeyValueUpdateProtoImpl from a KeyValueUpdate. */
      private KeyValueUpdateProtoImpl getOrCreateKeyValueUpdateProtoImpl(KeyValueUpdate message) {
        if (message instanceof KeyValueUpdateProtoImpl) {
          return (KeyValueUpdateProtoImpl) message;
        } else {
          KeyValueUpdateProtoImpl messageImpl = new KeyValueUpdateProtoImpl();
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
              ? org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary.newBuilder()
              : org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary.newBuilder(proto);
          proto = null;
        }
      }

      private void invalidateAll() {
        proto = null;
        protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.AnnotationBoundary.newBuilder();
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
        if (hasEmpty()) {
          json.add("1", new JsonPrimitive(getEmpty()));
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
          for (int i = 0; i < getEndSize(); i++) {
            array.add(new JsonPrimitive(getEnd(i)));
          }
          json.add("2", array);
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
          for (int i = 0; i < getChangeSize(); i++) {
            JsonElement elem = ((GsonSerializable) getChange(i)).toGson(raw, gson);
            // NOTE(kalman): if multistage parsing worked, split point would go here.
            array.add(elem);
          }
          json.add("3", array);
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
          if (!elem.isJsonNull()) {
            setEmpty(elem.getAsBoolean());
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
              addEnd(array.get(i).getAsString());
            }
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
        if (jsonObject.has("3")) {
          JsonElement elem = jsonObject.get("3");
          {
            JsonArray array = elem.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
              KeyValueUpdateProtoImpl payload = new KeyValueUpdateProtoImpl();
              GsonUtil.extractJsonObject(payload, array.get(i), gson, raw);
              addChange(payload);
            }
          }
        }
      }

      @Override
      public boolean equals(Object o) {
        if (this == o) {
          return true;
        } else if (o instanceof AnnotationBoundaryProtoImpl) {
          return getPB().equals(((AnnotationBoundaryProtoImpl) o).getPB());
        } else {
          return false;
        }
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (equals(o)) {
          return true;
        } else if (o instanceof AnnotationBoundary) {
          return AnnotationBoundaryUtil.isEqual(this, (AnnotationBoundary) o);
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

    private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component proto = null;
    private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.Builder protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.newBuilder();
    public ComponentProtoImpl() {
    }

    public ComponentProtoImpl(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component proto) {
      this.proto = proto;
    }

    public ComponentProtoImpl(Component message) {
      copyFrom(message);
    }

    @Override
    public org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component getPB() {
      switchToProto();
      return proto;
    }

    @Override
    public void setPB(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component proto) {
      this.proto = proto;
      this.protoBuilder = null;
    }

    @Override
    public void copyFrom(Component message) {

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
      if (message.hasAnnotationBoundary()) {
        setAnnotationBoundary(new AnnotationBoundaryProtoImpl(message.getAnnotationBoundary()));
      } else {
        clearAnnotationBoundary();
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
      if (message.hasCharacters()) {
        setCharacters(message.getCharacters());
      } else {
        clearCharacters();
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
      if (message.hasElementStart()) {
        setElementStart(new ElementStartProtoImpl(message.getElementStart()));
      } else {
        clearElementStart();
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
      if (message.hasElementEnd()) {
        setElementEnd(message.getElementEnd());
      } else {
        clearElementEnd();
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
      if (message.hasRetainItemCount()) {
        setRetainItemCount(message.getRetainItemCount());
      } else {
        clearRetainItemCount();
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
      if (message.hasDeleteCharacters()) {
        setDeleteCharacters(message.getDeleteCharacters());
      } else {
        clearDeleteCharacters();
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
      if (message.hasDeleteElementStart()) {
        setDeleteElementStart(new ElementStartProtoImpl(message.getDeleteElementStart()));
      } else {
        clearDeleteElementStart();
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
      if (message.hasDeleteElementEnd()) {
        setDeleteElementEnd(message.getDeleteElementEnd());
      } else {
        clearDeleteElementEnd();
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
      if (message.hasReplaceAttributes()) {
        setReplaceAttributes(new ReplaceAttributesProtoImpl(message.getReplaceAttributes()));
      } else {
        clearReplaceAttributes();
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
      if (message.hasUpdateAttributes()) {
        setUpdateAttributes(new UpdateAttributesProtoImpl(message.getUpdateAttributes()));
      } else {
        clearUpdateAttributes();
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
    public boolean hasAnnotationBoundary() {
      switchToProto();
      return proto.hasAnnotationBoundary();
    }

    @Override
    public void clearAnnotationBoundary() {
      switchToProtoBuilder();
      protoBuilder.clearAnnotationBoundary();
    }

    @Override
    public AnnotationBoundaryProtoImpl getAnnotationBoundary() {
      switchToProto();
      return new AnnotationBoundaryProtoImpl(proto.getAnnotationBoundary());
    }

    @Override
    public void setAnnotationBoundary(AnnotationBoundary value) {
      switchToProtoBuilder();
      protoBuilder.clearAnnotationBoundary();
      protoBuilder.setAnnotationBoundary(getOrCreateAnnotationBoundaryProtoImpl(value).getPB());
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
    public boolean hasCharacters() {
      switchToProto();
      return proto.hasCharacters();
    }

    @Override
    public void clearCharacters() {
      switchToProtoBuilder();
      protoBuilder.clearCharacters();
    }

    @Override
    public String getCharacters() {
      switchToProto();
      return proto.getCharacters();
    }

    @Override
    public void setCharacters(String value) {
      switchToProtoBuilder();
      protoBuilder.setCharacters(value);
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
    public boolean hasElementStart() {
      switchToProto();
      return proto.hasElementStart();
    }

    @Override
    public void clearElementStart() {
      switchToProtoBuilder();
      protoBuilder.clearElementStart();
    }

    @Override
    public ElementStartProtoImpl getElementStart() {
      switchToProto();
      return new ElementStartProtoImpl(proto.getElementStart());
    }

    @Override
    public void setElementStart(ElementStart value) {
      switchToProtoBuilder();
      protoBuilder.clearElementStart();
      protoBuilder.setElementStart(getOrCreateElementStartProtoImpl(value).getPB());
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
    public boolean hasElementEnd() {
      switchToProto();
      return proto.hasElementEnd();
    }

    @Override
    public void clearElementEnd() {
      switchToProtoBuilder();
      protoBuilder.clearElementEnd();
    }

    @Override
    public boolean getElementEnd() {
      switchToProto();
      return proto.getElementEnd();
    }

    @Override
    public void setElementEnd(boolean value) {
      switchToProtoBuilder();
      protoBuilder.setElementEnd(value);
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
    public boolean hasRetainItemCount() {
      switchToProto();
      return proto.hasRetainItemCount();
    }

    @Override
    public void clearRetainItemCount() {
      switchToProtoBuilder();
      protoBuilder.clearRetainItemCount();
    }

    @Override
    public int getRetainItemCount() {
      switchToProto();
      return proto.getRetainItemCount();
    }

    @Override
    public void setRetainItemCount(int value) {
      switchToProtoBuilder();
      protoBuilder.setRetainItemCount(value);
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
    public boolean hasDeleteCharacters() {
      switchToProto();
      return proto.hasDeleteCharacters();
    }

    @Override
    public void clearDeleteCharacters() {
      switchToProtoBuilder();
      protoBuilder.clearDeleteCharacters();
    }

    @Override
    public String getDeleteCharacters() {
      switchToProto();
      return proto.getDeleteCharacters();
    }

    @Override
    public void setDeleteCharacters(String value) {
      switchToProtoBuilder();
      protoBuilder.setDeleteCharacters(value);
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
    public boolean hasDeleteElementStart() {
      switchToProto();
      return proto.hasDeleteElementStart();
    }

    @Override
    public void clearDeleteElementStart() {
      switchToProtoBuilder();
      protoBuilder.clearDeleteElementStart();
    }

    @Override
    public ElementStartProtoImpl getDeleteElementStart() {
      switchToProto();
      return new ElementStartProtoImpl(proto.getDeleteElementStart());
    }

    @Override
    public void setDeleteElementStart(ElementStart value) {
      switchToProtoBuilder();
      protoBuilder.clearDeleteElementStart();
      protoBuilder.setDeleteElementStart(getOrCreateElementStartProtoImpl(value).getPB());
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
    public boolean hasDeleteElementEnd() {
      switchToProto();
      return proto.hasDeleteElementEnd();
    }

    @Override
    public void clearDeleteElementEnd() {
      switchToProtoBuilder();
      protoBuilder.clearDeleteElementEnd();
    }

    @Override
    public boolean getDeleteElementEnd() {
      switchToProto();
      return proto.getDeleteElementEnd();
    }

    @Override
    public void setDeleteElementEnd(boolean value) {
      switchToProtoBuilder();
      protoBuilder.setDeleteElementEnd(value);
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
    public boolean hasReplaceAttributes() {
      switchToProto();
      return proto.hasReplaceAttributes();
    }

    @Override
    public void clearReplaceAttributes() {
      switchToProtoBuilder();
      protoBuilder.clearReplaceAttributes();
    }

    @Override
    public ReplaceAttributesProtoImpl getReplaceAttributes() {
      switchToProto();
      return new ReplaceAttributesProtoImpl(proto.getReplaceAttributes());
    }

    @Override
    public void setReplaceAttributes(ReplaceAttributes value) {
      switchToProtoBuilder();
      protoBuilder.clearReplaceAttributes();
      protoBuilder.setReplaceAttributes(getOrCreateReplaceAttributesProtoImpl(value).getPB());
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
    public boolean hasUpdateAttributes() {
      switchToProto();
      return proto.hasUpdateAttributes();
    }

    @Override
    public void clearUpdateAttributes() {
      switchToProtoBuilder();
      protoBuilder.clearUpdateAttributes();
    }

    @Override
    public UpdateAttributesProtoImpl getUpdateAttributes() {
      switchToProto();
      return new UpdateAttributesProtoImpl(proto.getUpdateAttributes());
    }

    @Override
    public void setUpdateAttributes(UpdateAttributes value) {
      switchToProtoBuilder();
      protoBuilder.clearUpdateAttributes();
      protoBuilder.setUpdateAttributes(getOrCreateUpdateAttributesProtoImpl(value).getPB());
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

    /** Get or create a AnnotationBoundaryProtoImpl from a AnnotationBoundary. */
    private AnnotationBoundaryProtoImpl getOrCreateAnnotationBoundaryProtoImpl(AnnotationBoundary message) {
      if (message instanceof AnnotationBoundaryProtoImpl) {
        return (AnnotationBoundaryProtoImpl) message;
      } else {
        AnnotationBoundaryProtoImpl messageImpl = new AnnotationBoundaryProtoImpl();
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

    /** Get or create a ElementStartProtoImpl from a ElementStart. */
    private ElementStartProtoImpl getOrCreateElementStartProtoImpl(ElementStart message) {
      if (message instanceof ElementStartProtoImpl) {
        return (ElementStartProtoImpl) message;
      } else {
        ElementStartProtoImpl messageImpl = new ElementStartProtoImpl();
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

    /** Get or create a ReplaceAttributesProtoImpl from a ReplaceAttributes. */
    private ReplaceAttributesProtoImpl getOrCreateReplaceAttributesProtoImpl(ReplaceAttributes message) {
      if (message instanceof ReplaceAttributesProtoImpl) {
        return (ReplaceAttributesProtoImpl) message;
      } else {
        ReplaceAttributesProtoImpl messageImpl = new ReplaceAttributesProtoImpl();
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

    /** Get or create a UpdateAttributesProtoImpl from a UpdateAttributes. */
    private UpdateAttributesProtoImpl getOrCreateUpdateAttributesProtoImpl(UpdateAttributes message) {
      if (message instanceof UpdateAttributesProtoImpl) {
        return (UpdateAttributesProtoImpl) message;
      } else {
        UpdateAttributesProtoImpl messageImpl = new UpdateAttributesProtoImpl();
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

    /** Get or create a KeyValuePairProtoImpl from a KeyValuePair. */
    private KeyValuePairProtoImpl getOrCreateKeyValuePairProtoImpl(KeyValuePair message) {
      if (message instanceof KeyValuePairProtoImpl) {
        return (KeyValuePairProtoImpl) message;
      } else {
        KeyValuePairProtoImpl messageImpl = new KeyValuePairProtoImpl();
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

    /** Get or create a KeyValueUpdateProtoImpl from a KeyValueUpdate. */
    private KeyValueUpdateProtoImpl getOrCreateKeyValueUpdateProtoImpl(KeyValueUpdate message) {
      if (message instanceof KeyValueUpdateProtoImpl) {
        return (KeyValueUpdateProtoImpl) message;
      } else {
        KeyValueUpdateProtoImpl messageImpl = new KeyValueUpdateProtoImpl();
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
            ? org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.newBuilder()
            : org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.newBuilder(proto);
        proto = null;
      }
    }

    private void invalidateAll() {
      proto = null;
      protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Component.newBuilder();
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
      if (hasAnnotationBoundary()) {
        {
          JsonElement elem = ((GsonSerializable) getAnnotationBoundary()).toGson(raw, gson);
          json.add("1", elem);
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
      if (hasCharacters()) {
        json.add("2", new JsonPrimitive(getCharacters()));
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
      if (hasElementStart()) {
        {
          JsonElement elem = ((GsonSerializable) getElementStart()).toGson(raw, gson);
          json.add("3", elem);
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
      if (hasElementEnd()) {
        json.add("4", new JsonPrimitive(getElementEnd()));
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
      if (hasRetainItemCount()) {
        json.add("5", new JsonPrimitive(getRetainItemCount()));
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
      if (hasDeleteCharacters()) {
        json.add("6", new JsonPrimitive(getDeleteCharacters()));
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
      if (hasDeleteElementStart()) {
        {
          JsonElement elem = ((GsonSerializable) getDeleteElementStart()).toGson(raw, gson);
          json.add("7", elem);
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
      if (hasDeleteElementEnd()) {
        json.add("8", new JsonPrimitive(getDeleteElementEnd()));
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
      if (hasReplaceAttributes()) {
        {
          JsonElement elem = ((GsonSerializable) getReplaceAttributes()).toGson(raw, gson);
          json.add("9", elem);
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
      if (hasUpdateAttributes()) {
        {
          JsonElement elem = ((GsonSerializable) getUpdateAttributes()).toGson(raw, gson);
          json.add("10", elem);
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
        if (!elem.isJsonNull()) {
          {
            AnnotationBoundaryProtoImpl payload = new AnnotationBoundaryProtoImpl();
            GsonUtil.extractJsonObject(payload, elem, gson, raw);
            setAnnotationBoundary(payload);
          }
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
        if (!elem.isJsonNull()) {
          setCharacters(elem.getAsString());
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
      if (jsonObject.has("3")) {
        JsonElement elem = jsonObject.get("3");
        if (!elem.isJsonNull()) {
          {
            ElementStartProtoImpl payload = new ElementStartProtoImpl();
            GsonUtil.extractJsonObject(payload, elem, gson, raw);
            setElementStart(payload);
          }
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
      if (jsonObject.has("4")) {
        JsonElement elem = jsonObject.get("4");
        if (!elem.isJsonNull()) {
          setElementEnd(elem.getAsBoolean());
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
      if (jsonObject.has("5")) {
        JsonElement elem = jsonObject.get("5");
        if (!elem.isJsonNull()) {
          setRetainItemCount(elem.getAsInt());
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
      if (jsonObject.has("6")) {
        JsonElement elem = jsonObject.get("6");
        if (!elem.isJsonNull()) {
          setDeleteCharacters(elem.getAsString());
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
      if (jsonObject.has("7")) {
        JsonElement elem = jsonObject.get("7");
        if (!elem.isJsonNull()) {
          {
            ElementStartProtoImpl payload = new ElementStartProtoImpl();
            GsonUtil.extractJsonObject(payload, elem, gson, raw);
            setDeleteElementStart(payload);
          }
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
      if (jsonObject.has("8")) {
        JsonElement elem = jsonObject.get("8");
        if (!elem.isJsonNull()) {
          setDeleteElementEnd(elem.getAsBoolean());
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
      if (jsonObject.has("9")) {
        JsonElement elem = jsonObject.get("9");
        if (!elem.isJsonNull()) {
          {
            ReplaceAttributesProtoImpl payload = new ReplaceAttributesProtoImpl();
            GsonUtil.extractJsonObject(payload, elem, gson, raw);
            setReplaceAttributes(payload);
          }
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
      if (jsonObject.has("10")) {
        JsonElement elem = jsonObject.get("10");
        if (!elem.isJsonNull()) {
          {
            UpdateAttributesProtoImpl payload = new UpdateAttributesProtoImpl();
            GsonUtil.extractJsonObject(payload, elem, gson, raw);
            setUpdateAttributes(payload);
          }
        }
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      } else if (o instanceof ComponentProtoImpl) {
        return getPB().equals(((ComponentProtoImpl) o).getPB());
      } else {
        return false;
      }
    }

    @Override
    public boolean isEqualTo(Object o) {
      if (equals(o)) {
        return true;
      } else if (o instanceof Component) {
        return ComponentUtil.isEqual(this, (Component) o);
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

  private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation proto = null;
  private org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.Builder protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.newBuilder();
  public ProtocolDocumentOperationProtoImpl() {
  }

  public ProtocolDocumentOperationProtoImpl(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation proto) {
    this.proto = proto;
  }

  public ProtocolDocumentOperationProtoImpl(ProtocolDocumentOperation message) {
    copyFrom(message);
  }

  @Override
  public org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation getPB() {
    switchToProto();
    return proto;
  }

  @Override
  public void setPB(org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation proto) {
    this.proto = proto;
    this.protoBuilder = null;
  }

  @Override
  public void copyFrom(ProtocolDocumentOperation message) {

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
    clearComponent();
    for (Component field : message.getComponent()) {
      addComponent(new ComponentProtoImpl(field));
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
  public List<ComponentProtoImpl> getComponent() {
    switchToProto();
    List<ComponentProtoImpl> list = new ArrayList<ComponentProtoImpl>();
    for (int i = 0; i < getComponentSize(); i++) {
      ComponentProtoImpl message = new ComponentProtoImpl(proto.getComponent(i));
      list.add(message);
    }
    return list;
  }

  @Override
  public void addAllComponent(List<? extends Component> values) {
    for (Component message : values) {
      addComponent(message);
    }
  }

  @Override
  public ComponentProtoImpl getComponent(int n) {
    switchToProto();
    return new ComponentProtoImpl(proto.getComponent(n));
  }

  @Override
  public void setComponent(int n, Component value) {
    switchToProtoBuilder();
    protoBuilder.setComponent(n, getOrCreateComponentProtoImpl(value).getPB());
  }

  @Override
  public int getComponentSize() {
    switchToProto();
    return proto.getComponentCount();
  }

  @Override
  public void addComponent(Component value) {
    switchToProtoBuilder();
    protoBuilder.addComponent(getOrCreateComponentProtoImpl(value).getPB());
  }

  @Override
  public void clearComponent() {
    switchToProtoBuilder();
    protoBuilder.clearComponent();
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

  /** Get or create a ComponentProtoImpl from a Component. */
  private ComponentProtoImpl getOrCreateComponentProtoImpl(Component message) {
    if (message instanceof ComponentProtoImpl) {
      return (ComponentProtoImpl) message;
    } else {
      ComponentProtoImpl messageImpl = new ComponentProtoImpl();
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

  /** Get or create a AnnotationBoundaryProtoImpl from a AnnotationBoundary. */
  private AnnotationBoundaryProtoImpl getOrCreateAnnotationBoundaryProtoImpl(AnnotationBoundary message) {
    if (message instanceof AnnotationBoundaryProtoImpl) {
      return (AnnotationBoundaryProtoImpl) message;
    } else {
      AnnotationBoundaryProtoImpl messageImpl = new AnnotationBoundaryProtoImpl();
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

  /** Get or create a ElementStartProtoImpl from a ElementStart. */
  private ElementStartProtoImpl getOrCreateElementStartProtoImpl(ElementStart message) {
    if (message instanceof ElementStartProtoImpl) {
      return (ElementStartProtoImpl) message;
    } else {
      ElementStartProtoImpl messageImpl = new ElementStartProtoImpl();
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

  /** Get or create a ReplaceAttributesProtoImpl from a ReplaceAttributes. */
  private ReplaceAttributesProtoImpl getOrCreateReplaceAttributesProtoImpl(ReplaceAttributes message) {
    if (message instanceof ReplaceAttributesProtoImpl) {
      return (ReplaceAttributesProtoImpl) message;
    } else {
      ReplaceAttributesProtoImpl messageImpl = new ReplaceAttributesProtoImpl();
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

  /** Get or create a UpdateAttributesProtoImpl from a UpdateAttributes. */
  private UpdateAttributesProtoImpl getOrCreateUpdateAttributesProtoImpl(UpdateAttributes message) {
    if (message instanceof UpdateAttributesProtoImpl) {
      return (UpdateAttributesProtoImpl) message;
    } else {
      UpdateAttributesProtoImpl messageImpl = new UpdateAttributesProtoImpl();
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

  /** Get or create a KeyValuePairProtoImpl from a KeyValuePair. */
  private KeyValuePairProtoImpl getOrCreateKeyValuePairProtoImpl(KeyValuePair message) {
    if (message instanceof KeyValuePairProtoImpl) {
      return (KeyValuePairProtoImpl) message;
    } else {
      KeyValuePairProtoImpl messageImpl = new KeyValuePairProtoImpl();
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

  /** Get or create a KeyValueUpdateProtoImpl from a KeyValueUpdate. */
  private KeyValueUpdateProtoImpl getOrCreateKeyValueUpdateProtoImpl(KeyValueUpdate message) {
    if (message instanceof KeyValueUpdateProtoImpl) {
      return (KeyValueUpdateProtoImpl) message;
    } else {
      KeyValueUpdateProtoImpl messageImpl = new KeyValueUpdateProtoImpl();
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
          ? org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.newBuilder()
          : org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.newBuilder(proto);
      proto = null;
    }
  }

  private void invalidateAll() {
    proto = null;
    protoBuilder = org.waveprotocol.wave.federation.Proto.ProtocolDocumentOperation.newBuilder();
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
      JsonArray array = new JsonArray();
      for (int i = 0; i < getComponentSize(); i++) {
        JsonElement elem = ((GsonSerializable) getComponent(i)).toGson(raw, gson);
        // NOTE(kalman): if multistage parsing worked, split point would go here.
        array.add(elem);
      }
      json.add("1", array);
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
        JsonArray array = elem.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
          ComponentProtoImpl payload = new ComponentProtoImpl();
          GsonUtil.extractJsonObject(payload, array.get(i), gson, raw);
          addComponent(payload);
        }
      }
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof ProtocolDocumentOperationProtoImpl) {
      return getPB().equals(((ProtocolDocumentOperationProtoImpl) o).getPB());
    } else {
      return false;
    }
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (equals(o)) {
      return true;
    } else if (o instanceof ProtocolDocumentOperation) {
      return ProtocolDocumentOperationUtil.isEqual(this, (ProtocolDocumentOperation) o);
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