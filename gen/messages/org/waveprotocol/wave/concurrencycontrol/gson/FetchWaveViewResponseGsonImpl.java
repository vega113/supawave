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

package org.waveprotocol.wave.concurrencycontrol.gson;

// Import order matters here due to what looks like a javac bug.
// Eclipse doesn't seem to have this problem.
import org.waveprotocol.wave.communication.gson.GsonSerializable;

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
import org.waveprotocol.wave.concurrencycontrol.FetchWaveViewResponse;
import org.waveprotocol.wave.concurrencycontrol.FetchWaveViewResponseUtil;
import org.waveprotocol.wave.concurrencycontrol.impl.FetchWaveViewResponseImpl;

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
import org.waveprotocol.wave.concurrencycontrol.impl.FetchWaveViewResponseImpl.WaveletImpl;
import org.waveprotocol.wave.concurrencycontrol.ResponseStatus;
import org.waveprotocol.wave.concurrencycontrol.FetchWaveViewResponse.Wavelet;
import org.waveprotocol.wave.concurrencycontrol.WaveletSnapshot;
import org.waveprotocol.wave.concurrencycontrol.gson.ResponseStatusGsonImpl;
import org.waveprotocol.wave.concurrencycontrol.gson.FetchWaveViewResponseGsonImpl.WaveletGsonImpl;
import org.waveprotocol.wave.concurrencycontrol.gson.WaveletSnapshotGsonImpl;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gson.GsonException;
import org.waveprotocol.wave.communication.gson.GsonUtil;
import org.waveprotocol.wave.communication.json.RawStringData;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

/**
 * Pojo implementation of FetchWaveViewResponse with gson serialization and deserialization.
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
public final class FetchWaveViewResponseGsonImpl extends FetchWaveViewResponseImpl
    implements GsonSerializable {

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
  public static final class WaveletGsonImpl extends WaveletImpl
      implements GsonSerializable {
    public WaveletGsonImpl() {
      super();
    }

    public WaveletGsonImpl(Wavelet message) {
      super(message);
    }

    @Override
    public JsonElement toGson(RawStringData raw, Gson gson) {
      return toGsonHelper(this, raw, gson);
    }

    /**
     * Static implementation-independent GSON serializer. Call this from
     * {@link #toGson} to avoid subclassing issues with inner message types.
     */
    public static JsonElement toGsonHelper(Wavelet message, RawStringData raw, Gson gson) {
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
      json.add("1", new JsonPrimitive(message.getWaveletId()));

      /**
       * Licensed to the Apache Software Foundation (ASF) under one
       * or more contributor license agreements. See the NOTICE file
       * distributed with this work for additional information
       * regarding copyright ownership. The ASF licenses this file
       * to you under the Apache License, Version 2.0 (the
       * "License");
       you may not use this file except in compliance
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
        json.add("2", WaveletSnapshotGsonImpl.toGsonHelper(message.getSnapshot(), raw, gson));
      }
      return json;
    }

    @Override
    public void fromGson(JsonElement json, Gson gson, RawStringData raw) throws GsonException {
      reset();
      JsonObject jsonObject = json.getAsJsonObject();
      // NOTE: always check with has(...) as the json might not have all required
      // fields set.

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
        setWaveletId(jsonObject.get("1").getAsString());
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
          WaveletSnapshotGsonImpl payload = new WaveletSnapshotGsonImpl();
          GsonUtil.extractJsonObject(payload, elem, gson, raw);
          setSnapshot(payload);
        }
      }
    }

  }

  public FetchWaveViewResponseGsonImpl() {
    super();
  }

  public FetchWaveViewResponseGsonImpl(FetchWaveViewResponse message) {
    super(message);
  }

  @Override
  public JsonElement toGson(RawStringData raw, Gson gson) {
    return toGsonHelper(this, raw, gson);
  }

  /**
   * Static implementation-independent GSON serializer. Call this from
   * {@link #toGson} to avoid subclassing issues with inner message types.
   */
  public static JsonElement toGsonHelper(FetchWaveViewResponse message, RawStringData raw, Gson gson) {
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
    json.add("1", ResponseStatusGsonImpl.toGsonHelper(message.getStatus(), raw, gson));

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
      JsonArray array = new JsonArray();
      for (int i = 0; i < message.getWaveletSize(); i++) {
        // NOTE(kalman): if multistage parsing worked, we would add split points here.
        array.add(WaveletGsonImpl.toGsonHelper(message.getWavelet(i), raw, gson));
      }
      json.add("2", array);
    }
    return json;
  }

  @Override
  public void fromGson(JsonElement json, Gson gson, RawStringData raw) throws GsonException {
    reset();
    JsonObject jsonObject = json.getAsJsonObject();
    // NOTE: always check with has(...) as the json might not have all required
    // fields set.

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
      ResponseStatusGsonImpl payload = new ResponseStatusGsonImpl();
      GsonUtil.extractJsonObject(payload, jsonObject.get("1"), gson, raw);
      setStatus(payload);
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
      JsonArray array = jsonObject.get("2").getAsJsonArray();
      for (int i = 0; i < array.size(); i++) {
        WaveletGsonImpl payload = new WaveletGsonImpl();
        GsonUtil.extractJsonObject(payload, array.get(i), gson, raw);
        addWavelet(payload);
      }
    }
  }

}