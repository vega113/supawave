/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.wave.diff.gson;

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
import org.waveprotocol.wave.diff.FetchDiffResponse;
import org.waveprotocol.wave.diff.FetchDiffResponseUtil;
import org.waveprotocol.wave.diff.impl.FetchDiffResponseImpl;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
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
import org.waveprotocol.wave.diff.impl.FetchDiffResponseImpl.WaveletDiffImpl;
import org.waveprotocol.wave.concurrencycontrol.ResponseStatus;
import org.waveprotocol.wave.diff.FetchDiffResponse.WaveletDiff;
import org.waveprotocol.wave.diff.WaveletDiffSnapshot;
import org.waveprotocol.wave.concurrencycontrol.gson.ResponseStatusGsonImpl;
import org.waveprotocol.wave.diff.gson.FetchDiffResponseGsonImpl.WaveletDiffGsonImpl;
import org.waveprotocol.wave.diff.gson.WaveletDiffSnapshotGsonImpl;
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
 * Pojo implementation of FetchDiffResponse with gson serialization and deserialization.
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
public final class FetchDiffResponseGsonImpl extends FetchDiffResponseImpl
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
  public static final class WaveletDiffGsonImpl extends WaveletDiffImpl
      implements GsonSerializable {
    public WaveletDiffGsonImpl() {
      super();
    }

    public WaveletDiffGsonImpl(WaveletDiff message) {
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
    public static JsonElement toGsonHelper(WaveletDiff message, RawStringData raw, Gson gson) {
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
        json.add("2", WaveletDiffSnapshotGsonImpl.toGsonHelper(message.getSnapshot(), raw, gson));
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
          WaveletDiffSnapshotGsonImpl payload = new WaveletDiffSnapshotGsonImpl();
          GsonUtil.extractJsonObject(payload, elem, gson, raw);
          setSnapshot(payload);
        }
      }
    }

  }

  public FetchDiffResponseGsonImpl() {
    super();
  }

  public FetchDiffResponseGsonImpl(FetchDiffResponse message) {
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
  public static JsonElement toGsonHelper(FetchDiffResponse message, RawStringData raw, Gson gson) {
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
        array.add(WaveletDiffGsonImpl.toGsonHelper(message.getWavelet(i), raw, gson));
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
        WaveletDiffGsonImpl payload = new WaveletDiffGsonImpl();
        GsonUtil.extractJsonObject(payload, array.get(i), gson, raw);
        addWavelet(payload);
      }
    }
  }

}