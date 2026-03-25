/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.wave.diff.jso;

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
import org.waveprotocol.wave.diff.DocumentDiffSnapshot;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.diff.jso.DocumentDiffSnapshotJsoImpl;
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
import org.waveprotocol.wave.diff.WaveletDiffSnapshot;
import org.waveprotocol.wave.diff.WaveletDiffSnapshotUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of WaveletDiffSnapshot backed by a GWT JavaScriptObject.
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

/** We have to use fully-qualified name of the GsonSerializable class here in order to make it
 * visible in case of nested classes.
 */
public final class WaveletDiffSnapshotJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements WaveletDiffSnapshot {
  private static final String keyWaveletId = "1";
  private static final String keyParticipant = "2";
  private static final String keyAddedParticipant = "21";
  private static final String keyRemovedParticipant = "22";
  private static final String keyDocument = "3";
  private static final String keyVersion = "4";
  private static final String keyLastModifiedTime = "5";
  private static final String keyCreator = "6";
  private static final String keyCreationTime = "7";
  protected WaveletDiffSnapshotJsoImpl() {
  }

  public static WaveletDiffSnapshotJsoImpl create() {
    WaveletDiffSnapshotJsoImpl instance = (WaveletDiffSnapshotJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearParticipant();
    instance.clearAddedParticipant();
    instance.clearRemovedParticipant();
    instance.clearDocument();
    return instance;
  }

  @Override
  public void copyFrom(WaveletDiffSnapshot message) {
    super.copyFrom((WaveletDiffSnapshotJsoImpl) message);
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
  public List<String> getParticipant() {
    initArray(this, keyParticipant);
    List<String> list = new ArrayList<String>();
    for (int i = 0; i < getParticipantSize(); i++) {
      list.add(getParticipant(i));
    }
    return list;
  }

  @Override
  public void addAllParticipant(List<String> values) {
    for (String value : values) {
      addParticipant(value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public String getParticipant(int n) {
    initArray(this, keyParticipant);
    JsArrayString array = getPropertyAsObject(this, keyParticipant).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  public void setParticipant(int n, String value) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyParticipant);
    ((JsArrayString) getPropertyAsObject(this, keyParticipant)).set(n, value);
  }

  @Override
  public int getParticipantSize() {
    return hasProperty(this, keyParticipant) ? ((JsArray<?>) getPropertyAsObject(this, keyParticipant)).length() : 0;
  }

  @Override
  public void addParticipant(String value) {
    initArray(this, keyParticipant);
    ((JsArrayString) getPropertyAsObject(this, keyParticipant)).push(value);
  }

  @Override
  public void clearParticipant() {
    clearArray(this, keyParticipant);
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
  public List<String> getAddedParticipant() {
    initArray(this, keyAddedParticipant);
    List<String> list = new ArrayList<String>();
    for (int i = 0; i < getAddedParticipantSize(); i++) {
      list.add(getAddedParticipant(i));
    }
    return list;
  }

  @Override
  public void addAllAddedParticipant(List<String> values) {
    for (String value : values) {
      addAddedParticipant(value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public String getAddedParticipant(int n) {
    initArray(this, keyAddedParticipant);
    JsArrayString array = getPropertyAsObject(this, keyAddedParticipant).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  public void setAddedParticipant(int n, String value) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyAddedParticipant);
    ((JsArrayString) getPropertyAsObject(this, keyAddedParticipant)).set(n, value);
  }

  @Override
  public int getAddedParticipantSize() {
    return hasProperty(this, keyAddedParticipant) ? ((JsArray<?>) getPropertyAsObject(this, keyAddedParticipant)).length() : 0;
  }

  @Override
  public void addAddedParticipant(String value) {
    initArray(this, keyAddedParticipant);
    ((JsArrayString) getPropertyAsObject(this, keyAddedParticipant)).push(value);
  }

  @Override
  public void clearAddedParticipant() {
    clearArray(this, keyAddedParticipant);
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
  public List<String> getRemovedParticipant() {
    initArray(this, keyRemovedParticipant);
    List<String> list = new ArrayList<String>();
    for (int i = 0; i < getRemovedParticipantSize(); i++) {
      list.add(getRemovedParticipant(i));
    }
    return list;
  }

  @Override
  public void addAllRemovedParticipant(List<String> values) {
    for (String value : values) {
      addRemovedParticipant(value);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public String getRemovedParticipant(int n) {
    initArray(this, keyRemovedParticipant);
    JsArrayString array = getPropertyAsObject(this, keyRemovedParticipant).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  public void setRemovedParticipant(int n, String value) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyRemovedParticipant);
    ((JsArrayString) getPropertyAsObject(this, keyRemovedParticipant)).set(n, value);
  }

  @Override
  public int getRemovedParticipantSize() {
    return hasProperty(this, keyRemovedParticipant) ? ((JsArray<?>) getPropertyAsObject(this, keyRemovedParticipant)).length() : 0;
  }

  @Override
  public void addRemovedParticipant(String value) {
    initArray(this, keyRemovedParticipant);
    ((JsArrayString) getPropertyAsObject(this, keyRemovedParticipant)).push(value);
  }

  @Override
  public void clearRemovedParticipant() {
    clearArray(this, keyRemovedParticipant);
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
  public List<DocumentDiffSnapshotJsoImpl> getDocument() {
    initArray(this, keyDocument);
    List<DocumentDiffSnapshotJsoImpl> list = new ArrayList<DocumentDiffSnapshotJsoImpl>();
    for (int i = 0; i < getDocumentSize(); i++) {
      list.add(getDocument(i));
    }
    return list;
  }

  @Override
  public void addAllDocument(List<? extends DocumentDiffSnapshot> models) {
    for (DocumentDiffSnapshot model : models) {
      addDocument(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public DocumentDiffSnapshotJsoImpl getDocument(int n) {
    initArray(this, keyDocument);
    JsArray<DocumentDiffSnapshotJsoImpl> array = getPropertyAsObject(this, keyDocument).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setDocument(int n, DocumentDiffSnapshot model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyDocument);
    ((JsArray<DocumentDiffSnapshotJsoImpl>) getPropertyAsObject(this, keyDocument)).set(n, (DocumentDiffSnapshotJsoImpl) model);
  }

  @Override
  public int getDocumentSize() {
    return hasProperty(this, keyDocument) ? ((JsArray<?>) getPropertyAsObject(this, keyDocument)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addDocument(DocumentDiffSnapshot model) {
    initArray(this, keyDocument);
    ((JsArray<DocumentDiffSnapshotJsoImpl>) getPropertyAsObject(this, keyDocument)).push((DocumentDiffSnapshotJsoImpl) model);
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
  public double getLastModifiedTime() {
    return hasProperty(this, keyLastModifiedTime) ? getPropertyAsDouble(this, keyLastModifiedTime) : 0L;
  }

  @Override
  public void setLastModifiedTime(double value) {
    setPropertyAsDouble(this, keyLastModifiedTime, value);
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
  public double getCreationTime() {
    return hasProperty(this, keyCreationTime) ? getPropertyAsDouble(this, keyCreationTime) : 0L;
  }

  @Override
  public void setCreationTime(double value) {
    setPropertyAsDouble(this, keyCreationTime, value);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof WaveletDiffSnapshotJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof WaveletDiffSnapshot) {
      return WaveletDiffSnapshotUtil.isEqual(this, (WaveletDiffSnapshot) o);
    } else {
      return false;
    }
  }

}