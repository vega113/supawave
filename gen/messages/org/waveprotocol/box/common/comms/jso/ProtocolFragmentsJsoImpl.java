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
import org.waveprotocol.box.common.comms.ProtocolFragmentRange;
import org.waveprotocol.box.common.comms.ProtocolFragment;
import org.waveprotocol.box.common.comms.jso.ProtocolFragmentRangeJsoImpl;
import org.waveprotocol.box.common.comms.jso.ProtocolFragmentJsoImpl;

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
import org.waveprotocol.box.common.comms.ProtocolFragments;
import org.waveprotocol.box.common.comms.ProtocolFragmentsUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProtocolFragments backed by a GWT JavaScriptObject.
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
public final class ProtocolFragmentsJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProtocolFragments {
  private static final String keySnapshotVersion = "1";
  private static final String keyStartVersion = "2";
  private static final String keyEndVersion = "3";
  private static final String keyRange = "4";
  private static final String keyFragment = "5";
  protected ProtocolFragmentsJsoImpl() {
  }

  public static ProtocolFragmentsJsoImpl create() {
    ProtocolFragmentsJsoImpl instance = (ProtocolFragmentsJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearRange();
    instance.clearFragment();
    return instance;
  }

  @Override
  public void copyFrom(ProtocolFragments message) {
    super.copyFrom((ProtocolFragmentsJsoImpl) message);
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
  public boolean hasSnapshotVersion() {
    return hasProperty(this, keySnapshotVersion);
  }

  @Override
  public void clearSnapshotVersion() {
    if (hasProperty(this, keySnapshotVersion)) {
      deleteProperty(this, keySnapshotVersion);
    }
  }

  @Override
  public long getSnapshotVersion() {
    return hasProperty(this, keySnapshotVersion) ? getPropertyAsLong(this, keySnapshotVersion) : 0L;
  }

  @Override
  public void setSnapshotVersion(long value) {
    setPropertyAsLong(this, keySnapshotVersion, value);
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
  public boolean hasStartVersion() {
    return hasProperty(this, keyStartVersion);
  }

  @Override
  public void clearStartVersion() {
    if (hasProperty(this, keyStartVersion)) {
      deleteProperty(this, keyStartVersion);
    }
  }

  @Override
  public long getStartVersion() {
    return hasProperty(this, keyStartVersion) ? getPropertyAsLong(this, keyStartVersion) : 0L;
  }

  @Override
  public void setStartVersion(long value) {
    setPropertyAsLong(this, keyStartVersion, value);
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
  public boolean hasEndVersion() {
    return hasProperty(this, keyEndVersion);
  }

  @Override
  public void clearEndVersion() {
    if (hasProperty(this, keyEndVersion)) {
      deleteProperty(this, keyEndVersion);
    }
  }

  @Override
  public long getEndVersion() {
    return hasProperty(this, keyEndVersion) ? getPropertyAsLong(this, keyEndVersion) : 0L;
  }

  @Override
  public void setEndVersion(long value) {
    setPropertyAsLong(this, keyEndVersion, value);
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
  public List<ProtocolFragmentRangeJsoImpl> getRange() {
    initArray(this, keyRange);
    List<ProtocolFragmentRangeJsoImpl> list = new ArrayList<ProtocolFragmentRangeJsoImpl>();
    for (int i = 0; i < getRangeSize(); i++) {
      list.add(getRange(i));
    }
    return list;
  }

  @Override
  public void addAllRange(List<? extends ProtocolFragmentRange> models) {
    for (ProtocolFragmentRange model : models) {
      addRange(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public ProtocolFragmentRangeJsoImpl getRange(int n) {
    initArray(this, keyRange);
    JsArray<ProtocolFragmentRangeJsoImpl> array = getPropertyAsObject(this, keyRange).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setRange(int n, ProtocolFragmentRange model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyRange);
    ((JsArray<ProtocolFragmentRangeJsoImpl>) getPropertyAsObject(this, keyRange)).set(n, (ProtocolFragmentRangeJsoImpl) model);
  }

  @Override
  public int getRangeSize() {
    return hasProperty(this, keyRange) ? ((JsArray<?>) getPropertyAsObject(this, keyRange)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addRange(ProtocolFragmentRange model) {
    initArray(this, keyRange);
    ((JsArray<ProtocolFragmentRangeJsoImpl>) getPropertyAsObject(this, keyRange)).push((ProtocolFragmentRangeJsoImpl) model);
  }

  @Override
  public void clearRange() {
    clearArray(this, keyRange);
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
  public List<ProtocolFragmentJsoImpl> getFragment() {
    initArray(this, keyFragment);
    List<ProtocolFragmentJsoImpl> list = new ArrayList<ProtocolFragmentJsoImpl>();
    for (int i = 0; i < getFragmentSize(); i++) {
      list.add(getFragment(i));
    }
    return list;
  }

  @Override
  public void addAllFragment(List<? extends ProtocolFragment> models) {
    for (ProtocolFragment model : models) {
      addFragment(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public ProtocolFragmentJsoImpl getFragment(int n) {
    initArray(this, keyFragment);
    JsArray<ProtocolFragmentJsoImpl> array = getPropertyAsObject(this, keyFragment).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setFragment(int n, ProtocolFragment model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyFragment);
    ((JsArray<ProtocolFragmentJsoImpl>) getPropertyAsObject(this, keyFragment)).set(n, (ProtocolFragmentJsoImpl) model);
  }

  @Override
  public int getFragmentSize() {
    return hasProperty(this, keyFragment) ? ((JsArray<?>) getPropertyAsObject(this, keyFragment)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addFragment(ProtocolFragment model) {
    initArray(this, keyFragment);
    ((JsArray<ProtocolFragmentJsoImpl>) getPropertyAsObject(this, keyFragment)).push((ProtocolFragmentJsoImpl) model);
  }

  @Override
  public void clearFragment() {
    clearArray(this, keyFragment);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProtocolFragmentsJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProtocolFragments) {
      return ProtocolFragmentsUtil.isEqual(this, (ProtocolFragments) o);
    } else {
      return false;
    }
  }

}