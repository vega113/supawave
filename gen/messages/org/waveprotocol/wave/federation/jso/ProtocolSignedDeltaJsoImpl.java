/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.wave.federation.jso;

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
import org.waveprotocol.wave.federation.ProtocolSignature;
import org.waveprotocol.wave.federation.jso.ProtocolSignatureJsoImpl;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.waveprotocol.wave.federation.ProtocolSignedDelta;
import org.waveprotocol.wave.federation.ProtocolSignedDeltaUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProtocolSignedDelta backed by a GWT JavaScriptObject.
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

/** We have to use fully-qualified name of the GsonSerializable class here in order to make it
 * visible in case of nested classes.
 */
public final class ProtocolSignedDeltaJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProtocolSignedDelta {
  private static final String keyDelta = "1";
  private static final String keySignature = "2";
  protected ProtocolSignedDeltaJsoImpl() {
  }

  public static ProtocolSignedDeltaJsoImpl create() {
    ProtocolSignedDeltaJsoImpl instance = (ProtocolSignedDeltaJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearSignature();
    return instance;
  }

  @Override
  public void copyFrom(ProtocolSignedDelta message) {
    super.copyFrom((ProtocolSignedDeltaJsoImpl) message);
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
  public Blob getDelta() {
    return hasProperty(this, keyDelta) ? new Blob(getPropertyAsString(this, keyDelta)) : null;
  }

  @Override
  public void setDelta(Blob blob) {
    setPropertyAsString(this, keyDelta, blob.getData());
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
  public List<ProtocolSignatureJsoImpl> getSignature() {
    initArray(this, keySignature);
    List<ProtocolSignatureJsoImpl> list = new ArrayList<ProtocolSignatureJsoImpl>();
    for (int i = 0; i < getSignatureSize(); i++) {
      list.add(getSignature(i));
    }
    return list;
  }

  @Override
  public void addAllSignature(List<? extends ProtocolSignature> models) {
    for (ProtocolSignature model : models) {
      addSignature(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public ProtocolSignatureJsoImpl getSignature(int n) {
    initArray(this, keySignature);
    JsArray<ProtocolSignatureJsoImpl> array = getPropertyAsObject(this, keySignature).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setSignature(int n, ProtocolSignature model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keySignature);
    ((JsArray<ProtocolSignatureJsoImpl>) getPropertyAsObject(this, keySignature)).set(n, (ProtocolSignatureJsoImpl) model);
  }

  @Override
  public int getSignatureSize() {
    return hasProperty(this, keySignature) ? ((JsArray<?>) getPropertyAsObject(this, keySignature)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addSignature(ProtocolSignature model) {
    initArray(this, keySignature);
    ((JsArray<ProtocolSignatureJsoImpl>) getPropertyAsObject(this, keySignature)).push((ProtocolSignatureJsoImpl) model);
  }

  @Override
  public void clearSignature() {
    clearArray(this, keySignature);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProtocolSignedDeltaJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProtocolSignedDelta) {
      return ProtocolSignedDeltaUtil.isEqual(this, (ProtocolSignedDelta) o);
    } else {
      return false;
    }
  }

}