/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
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
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.federation.ProtocolWaveletOperation;
import org.waveprotocol.wave.federation.jso.ProtocolHashedVersionJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolWaveletOperationJsoImpl;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.waveprotocol.wave.federation.ProtocolWaveletDelta;
import org.waveprotocol.wave.federation.ProtocolWaveletDeltaUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProtocolWaveletDelta backed by a GWT JavaScriptObject.
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
public final class ProtocolWaveletDeltaJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProtocolWaveletDelta {
  private static final String keyHashedVersion = "1";
  private static final String keyAuthor = "2";
  private static final String keyOperation = "3";
  private static final String keyAddressPath = "4";
  protected ProtocolWaveletDeltaJsoImpl() {
  }

  public static ProtocolWaveletDeltaJsoImpl create() {
    ProtocolWaveletDeltaJsoImpl instance = (ProtocolWaveletDeltaJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearOperation();
    instance.clearAddressPath();
    return instance;
  }

  @Override
  public void copyFrom(ProtocolWaveletDelta message) {
    super.copyFrom((ProtocolWaveletDeltaJsoImpl) message);
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
  public ProtocolHashedVersion getHashedVersion() {
    return hasProperty(this, keyHashedVersion) ? ((ProtocolHashedVersionJsoImpl) getPropertyAsObject(this, keyHashedVersion)) : null;
  }

  @Override
  public void setHashedVersion(ProtocolHashedVersion model) {
    setPropertyAsObject(this, keyHashedVersion, (ProtocolHashedVersionJsoImpl) model);
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
  public String getAuthor() {
    return hasProperty(this, keyAuthor) ? getPropertyAsString(this, keyAuthor) : null;
  }

  @Override
  public void setAuthor(String value) {
    setPropertyAsString(this, keyAuthor, value);
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
  public List<ProtocolWaveletOperationJsoImpl> getOperation() {
    initArray(this, keyOperation);
    List<ProtocolWaveletOperationJsoImpl> list = new ArrayList<ProtocolWaveletOperationJsoImpl>();
    for (int i = 0; i < getOperationSize(); i++) {
      list.add(getOperation(i));
    }
    return list;
  }

  @Override
  public void addAllOperation(List<? extends ProtocolWaveletOperation> models) {
    for (ProtocolWaveletOperation model : models) {
      addOperation(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public ProtocolWaveletOperationJsoImpl getOperation(int n) {
    initArray(this, keyOperation);
    JsArray<ProtocolWaveletOperationJsoImpl> array = getPropertyAsObject(this, keyOperation).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setOperation(int n, ProtocolWaveletOperation model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyOperation);
    ((JsArray<ProtocolWaveletOperationJsoImpl>) getPropertyAsObject(this, keyOperation)).set(n, (ProtocolWaveletOperationJsoImpl) model);
  }

  @Override
  public int getOperationSize() {
    return hasProperty(this, keyOperation) ? ((JsArray<?>) getPropertyAsObject(this, keyOperation)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addOperation(ProtocolWaveletOperation model) {
    initArray(this, keyOperation);
    ((JsArray<ProtocolWaveletOperationJsoImpl>) getPropertyAsObject(this, keyOperation)).push((ProtocolWaveletOperationJsoImpl) model);
  }

  @Override
  public void clearOperation() {
    clearArray(this, keyOperation);
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
  public List<String> getAddressPath() {
    initArray(this, keyAddressPath);
    List<String> list = new ArrayList<String>();
    for (int i = 0; i < getAddressPathSize(); i++) {
      list.add(getAddressPath(i));
    }
    return list;
  }

  @Override
  public void addAllAddressPath(List<String> values) {
    for (String value : values) {
      addAddressPath(value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public String getAddressPath(int n) {
    initArray(this, keyAddressPath);
    JsArrayString array = getPropertyAsObject(this, keyAddressPath).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  public void setAddressPath(int n, String value) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyAddressPath);
    ((JsArrayString) getPropertyAsObject(this, keyAddressPath)).set(n, value);
  }

  @Override
  public int getAddressPathSize() {
    return hasProperty(this, keyAddressPath) ? ((JsArray<?>) getPropertyAsObject(this, keyAddressPath)).length() : 0;
  }

  @Override
  public void addAddressPath(String value) {
    initArray(this, keyAddressPath);
    ((JsArrayString) getPropertyAsObject(this, keyAddressPath)).push(value);
  }

  @Override
  public void clearAddressPath() {
    clearArray(this, keyAddressPath);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProtocolWaveletDeltaJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProtocolWaveletDelta) {
      return ProtocolWaveletDeltaUtil.isEqual(this, (ProtocolWaveletDelta) o);
    } else {
      return false;
    }
  }

}