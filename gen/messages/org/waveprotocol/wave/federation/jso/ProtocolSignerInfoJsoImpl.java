/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
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
import org.waveprotocol.wave.federation.ProtocolSignerInfo.HashAlgorithm;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.waveprotocol.wave.federation.ProtocolSignerInfo;
import org.waveprotocol.wave.federation.ProtocolSignerInfoUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProtocolSignerInfo backed by a GWT JavaScriptObject.
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
public final class ProtocolSignerInfoJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProtocolSignerInfo {
  private static final String keyHashAlgorithm = "1";
  private static final String keyDomain = "2";
  private static final String keyCertificate = "3";
  protected ProtocolSignerInfoJsoImpl() {
  }

  public static ProtocolSignerInfoJsoImpl create() {
    ProtocolSignerInfoJsoImpl instance = (ProtocolSignerInfoJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearCertificate();
    return instance;
  }

  @Override
  public void copyFrom(ProtocolSignerInfo message) {
    super.copyFrom((ProtocolSignerInfoJsoImpl) message);
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
  public HashAlgorithm getHashAlgorithm() {
    return hasProperty(this, keyHashAlgorithm) ? ProtoEnums.valOf(getPropertyAsInteger(this, keyHashAlgorithm), HashAlgorithm.values()) : null;
  }

  @Override
  public void setHashAlgorithm(HashAlgorithm enumValue) {
    setPropertyAsInteger(this, keyHashAlgorithm, enumValue.getValue());
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
  public String getDomain() {
    return hasProperty(this, keyDomain) ? getPropertyAsString(this, keyDomain) : null;
  }

  @Override
  public void setDomain(String value) {
    setPropertyAsString(this, keyDomain, value);
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
  public List<Blob> getCertificate() {
    initArray(this, keyCertificate);
    List<Blob> list = new ArrayList<Blob>();
    for (int i = 0; i < getCertificateSize(); i++) {
      list.add(getCertificate(i));
    }
    return list;
  }

  @Override
  public void addAllCertificate(List<Blob> values) {
    for (Blob value : values) {
      addCertificate(value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public Blob getCertificate(int n) {
    initArray(this, keyCertificate);
    JsArrayString array = getPropertyAsObject(this, keyCertificate).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return new Blob(array.get(n));
  }

  @Override
  public void setCertificate(int n, Blob blob) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyCertificate);
    ((JsArrayString) getPropertyAsObject(this, keyCertificate)).set(n, blob.getData());
  }

  @Override
  public int getCertificateSize() {
    return hasProperty(this, keyCertificate) ? ((JsArray<?>) getPropertyAsObject(this, keyCertificate)).length() : 0;
  }

  @Override
  public void addCertificate(Blob blob) {
    initArray(this, keyCertificate);
    ((JsArrayString) getPropertyAsObject(this, keyCertificate)).push(blob.getData());
  }

  @Override
  public void clearCertificate() {
    clearArray(this, keyCertificate);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProtocolSignerInfoJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProtocolSignerInfo) {
      return ProtocolSignerInfoUtil.isEqual(this, (ProtocolSignerInfo) o);
    } else {
      return false;
    }
  }

}