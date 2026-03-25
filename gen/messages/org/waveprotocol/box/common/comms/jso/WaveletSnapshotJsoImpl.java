/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.common.comms.jso;

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
import org.waveprotocol.box.common.comms.DocumentSnapshot;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.box.common.comms.jso.DocumentSnapshotJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolHashedVersionJsoImpl;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.waveprotocol.box.common.comms.WaveletSnapshot;
import org.waveprotocol.box.common.comms.WaveletSnapshotUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of WaveletSnapshot backed by a GWT JavaScriptObject.
 *
 * Generated from org/waveprotocol/box/common/comms/waveclient-rpc.proto. Do not edit.
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
public final class WaveletSnapshotJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements WaveletSnapshot {
  private static final String keyWaveletId = "1";
  private static final String keyParticipantId = "2";
  private static final String keyDocument = "3";
  private static final String keyVersion = "4";
  private static final String keyLastModifiedTime = "5";
  private static final String keyCreator = "6";
  private static final String keyCreationTime = "7";
  protected WaveletSnapshotJsoImpl() {
  }

  public static WaveletSnapshotJsoImpl create() {
    WaveletSnapshotJsoImpl instance = (WaveletSnapshotJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearParticipantId();
    instance.clearDocument();
    return instance;
  }

  @Override
  public void copyFrom(WaveletSnapshot message) {
    super.copyFrom((WaveletSnapshotJsoImpl) message);
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
  public List<String> getParticipantId() {
    initArray(this, keyParticipantId);
    List<String> list = new ArrayList<String>();
    for (int i = 0; i < getParticipantIdSize(); i++) {
      list.add(getParticipantId(i));
    }
    return list;
  }

  @Override
  public void addAllParticipantId(List<String> values) {
    for (String value : values) {
      addParticipantId(value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public String getParticipantId(int n) {
    initArray(this, keyParticipantId);
    JsArrayString array = getPropertyAsObject(this, keyParticipantId).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  public void setParticipantId(int n, String value) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyParticipantId);
    ((JsArrayString) getPropertyAsObject(this, keyParticipantId)).set(n, value);
  }

  @Override
  public int getParticipantIdSize() {
    return hasProperty(this, keyParticipantId) ? ((JsArray<?>) getPropertyAsObject(this, keyParticipantId)).length() : 0;
  }

  @Override
  public void addParticipantId(String value) {
    initArray(this, keyParticipantId);
    ((JsArrayString) getPropertyAsObject(this, keyParticipantId)).push(value);
  }

  @Override
  public void clearParticipantId() {
    clearArray(this, keyParticipantId);
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
  public List<DocumentSnapshotJsoImpl> getDocument() {
    initArray(this, keyDocument);
    List<DocumentSnapshotJsoImpl> list = new ArrayList<DocumentSnapshotJsoImpl>();
    for (int i = 0; i < getDocumentSize(); i++) {
      list.add(getDocument(i));
    }
    return list;
  }

  @Override
  public void addAllDocument(List<? extends DocumentSnapshot> models) {
    for (DocumentSnapshot model : models) {
      addDocument(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public DocumentSnapshotJsoImpl getDocument(int n) {
    initArray(this, keyDocument);
    JsArray<DocumentSnapshotJsoImpl> array = getPropertyAsObject(this, keyDocument).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setDocument(int n, DocumentSnapshot model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyDocument);
    ((JsArray<DocumentSnapshotJsoImpl>) getPropertyAsObject(this, keyDocument)).set(n, (DocumentSnapshotJsoImpl) model);
  }

  @Override
  public int getDocumentSize() {
    return hasProperty(this, keyDocument) ? ((JsArray<?>) getPropertyAsObject(this, keyDocument)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addDocument(DocumentSnapshot model) {
    initArray(this, keyDocument);
    ((JsArray<DocumentSnapshotJsoImpl>) getPropertyAsObject(this, keyDocument)).push((DocumentSnapshotJsoImpl) model);
  }

  @Override
  public void clearDocument() {
    clearArray(this, keyDocument);
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
  public ProtocolHashedVersion getVersion() {
    return hasProperty(this, keyVersion) ? ((ProtocolHashedVersionJsoImpl) getPropertyAsObject(this, keyVersion)) : null;
  }

  @Override
  public void setVersion(ProtocolHashedVersion model) {
    setPropertyAsObject(this, keyVersion, (ProtocolHashedVersionJsoImpl) model);
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
  public long getLastModifiedTime() {
    return hasProperty(this, keyLastModifiedTime) ? getPropertyAsLong(this, keyLastModifiedTime) : 0L;
  }

  @Override
  public void setLastModifiedTime(long value) {
    setPropertyAsLong(this, keyLastModifiedTime, value);
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
  public String getCreator() {
    return hasProperty(this, keyCreator) ? getPropertyAsString(this, keyCreator) : null;
  }

  @Override
  public void setCreator(String value) {
    setPropertyAsString(this, keyCreator, value);
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
  public long getCreationTime() {
    return hasProperty(this, keyCreationTime) ? getPropertyAsLong(this, keyCreationTime) : 0L;
  }

  @Override
  public void setCreationTime(long value) {
    setPropertyAsLong(this, keyCreationTime, value);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof WaveletSnapshotJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof WaveletSnapshot) {
      return WaveletSnapshotUtil.isEqual(this, (WaveletSnapshot) o);
    } else {
      return false;
    }
  }

}