/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
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
import org.waveprotocol.box.common.comms.ProtocolFragmentSnapshot;
import org.waveprotocol.box.common.comms.ProtocolFragmentOperation;
import org.waveprotocol.box.common.comms.jso.ProtocolFragmentSnapshotJsoImpl;
import org.waveprotocol.box.common.comms.jso.ProtocolFragmentOperationJsoImpl;

/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
import org.waveprotocol.box.common.comms.ProtocolFragment;
import org.waveprotocol.box.common.comms.ProtocolFragmentUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProtocolFragment backed by a GWT JavaScriptObject.
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
public final class ProtocolFragmentJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProtocolFragment {
  private static final String keySegment = "1";
  private static final String keySnapshot = "2";
  private static final String keyAdjustOperation = "3";
  private static final String keyDiffOperation = "4";
  protected ProtocolFragmentJsoImpl() {
  }

  public static ProtocolFragmentJsoImpl create() {
    ProtocolFragmentJsoImpl instance = (ProtocolFragmentJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearAdjustOperation();
    instance.clearDiffOperation();
    return instance;
  }

  @Override
  public void copyFrom(ProtocolFragment message) {
    super.copyFrom((ProtocolFragmentJsoImpl) message);
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
  public String getSegment() {
    return hasProperty(this, keySegment) ? getPropertyAsString(this, keySegment) : null;
  }

  @Override
  public void setSegment(String value) {
    setPropertyAsString(this, keySegment, value);
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
    return hasProperty(this, keySnapshot);
  }

  @Override
  public void clearSnapshot() {
    if (hasProperty(this, keySnapshot)) {
      deleteProperty(this, keySnapshot);
    }
  }

  @Override
  public ProtocolFragmentSnapshot getSnapshot() {
    return hasProperty(this, keySnapshot) ? ((ProtocolFragmentSnapshotJsoImpl) getPropertyAsObject(this, keySnapshot)) : null;
  }

  @Override
  public void setSnapshot(ProtocolFragmentSnapshot model) {
    setPropertyAsObject(this, keySnapshot, (ProtocolFragmentSnapshotJsoImpl) model);
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
  public List<ProtocolFragmentOperationJsoImpl> getAdjustOperation() {
    initArray(this, keyAdjustOperation);
    List<ProtocolFragmentOperationJsoImpl> list = new ArrayList<ProtocolFragmentOperationJsoImpl>();
    for (int i = 0; i < getAdjustOperationSize(); i++) {
      list.add(getAdjustOperation(i));
    }
    return list;
  }

  @Override
  public void addAllAdjustOperation(List<? extends ProtocolFragmentOperation> models) {
    for (ProtocolFragmentOperation model : models) {
      addAdjustOperation(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public ProtocolFragmentOperationJsoImpl getAdjustOperation(int n) {
    initArray(this, keyAdjustOperation);
    JsArray<ProtocolFragmentOperationJsoImpl> array = getPropertyAsObject(this, keyAdjustOperation).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setAdjustOperation(int n, ProtocolFragmentOperation model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyAdjustOperation);
    ((JsArray<ProtocolFragmentOperationJsoImpl>) getPropertyAsObject(this, keyAdjustOperation)).set(n, (ProtocolFragmentOperationJsoImpl) model);
  }

  @Override
  public int getAdjustOperationSize() {
    return hasProperty(this, keyAdjustOperation) ? ((JsArray<?>) getPropertyAsObject(this, keyAdjustOperation)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addAdjustOperation(ProtocolFragmentOperation model) {
    initArray(this, keyAdjustOperation);
    ((JsArray<ProtocolFragmentOperationJsoImpl>) getPropertyAsObject(this, keyAdjustOperation)).push((ProtocolFragmentOperationJsoImpl) model);
  }

  @Override
  public void clearAdjustOperation() {
    clearArray(this, keyAdjustOperation);
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
  public List<ProtocolFragmentOperationJsoImpl> getDiffOperation() {
    initArray(this, keyDiffOperation);
    List<ProtocolFragmentOperationJsoImpl> list = new ArrayList<ProtocolFragmentOperationJsoImpl>();
    for (int i = 0; i < getDiffOperationSize(); i++) {
      list.add(getDiffOperation(i));
    }
    return list;
  }

  @Override
  public void addAllDiffOperation(List<? extends ProtocolFragmentOperation> models) {
    for (ProtocolFragmentOperation model : models) {
      addDiffOperation(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public ProtocolFragmentOperationJsoImpl getDiffOperation(int n) {
    initArray(this, keyDiffOperation);
    JsArray<ProtocolFragmentOperationJsoImpl> array = getPropertyAsObject(this, keyDiffOperation).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setDiffOperation(int n, ProtocolFragmentOperation model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyDiffOperation);
    ((JsArray<ProtocolFragmentOperationJsoImpl>) getPropertyAsObject(this, keyDiffOperation)).set(n, (ProtocolFragmentOperationJsoImpl) model);
  }

  @Override
  public int getDiffOperationSize() {
    return hasProperty(this, keyDiffOperation) ? ((JsArray<?>) getPropertyAsObject(this, keyDiffOperation)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addDiffOperation(ProtocolFragmentOperation model) {
    initArray(this, keyDiffOperation);
    ((JsArray<ProtocolFragmentOperationJsoImpl>) getPropertyAsObject(this, keyDiffOperation)).push((ProtocolFragmentOperationJsoImpl) model);
  }

  @Override
  public void clearDiffOperation() {
    clearArray(this, keyDiffOperation);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProtocolFragmentJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProtocolFragment) {
      return ProtocolFragmentUtil.isEqual(this, (ProtocolFragment) o);
    } else {
      return false;
    }
  }

}