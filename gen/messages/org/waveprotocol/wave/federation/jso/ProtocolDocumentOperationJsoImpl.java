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
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.AnnotationBoundary;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ElementStart;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ReplaceAttributes;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.UpdateAttributes;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValuePair;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValueUpdate;
import org.waveprotocol.wave.federation.jso.ProtocolDocumentOperationJsoImpl.ComponentJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.AnnotationBoundaryJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.ElementStartJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.ReplaceAttributesJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.UpdateAttributesJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.KeyValuePairJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolDocumentOperationJsoImpl.ComponentJsoImpl.KeyValueUpdateJsoImpl;

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
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProtocolDocumentOperation backed by a GWT JavaScriptObject.
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
public final class ProtocolDocumentOperationJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProtocolDocumentOperation {

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
  public static final class ComponentJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
      implements Component {

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
    public static final class KeyValuePairJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
        implements KeyValuePair {
      private static final String keyKey = "1";
      private static final String keyValue = "2";
      protected KeyValuePairJsoImpl() {
      }

      public static KeyValuePairJsoImpl create() {
        KeyValuePairJsoImpl instance = (KeyValuePairJsoImpl) JsonMessage.createJsonMessage();
        // Force all lists to start with an empty list rather than no property for
        // the list. This is so that the native JS equality works, since (obviously)
        // {} != {"foo": []} while in the context of messages they should be.
        return instance;
      }

      @Override
      public void copyFrom(KeyValuePair message) {
        super.copyFrom((KeyValuePairJsoImpl) message);
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
        return hasProperty(this, keyKey) ? getPropertyAsString(this, keyKey) : null;
      }

      @Override
      public void setKey(String value) {
        setPropertyAsString(this, keyKey, value);
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
        return hasProperty(this, keyValue) ? getPropertyAsString(this, keyValue) : null;
      }

      @Override
      public void setValue(String value) {
        setPropertyAsString(this, keyValue, value);
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (o instanceof KeyValuePairJsoImpl) {
          return nativeIsEqualTo(o);
        } else if (o instanceof KeyValuePair) {
          return KeyValuePairUtil.isEqual(this, (KeyValuePair) o);
        } else {
          return false;
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

    /** We have to use fully-qualified name of the GsonSerializable class here in order to make it
     * visible in case of nested classes.
     */
    public static final class KeyValueUpdateJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
        implements KeyValueUpdate {
      private static final String keyKey = "1";
      private static final String keyOldValue = "2";
      private static final String keyNewValue = "3";
      protected KeyValueUpdateJsoImpl() {
      }

      public static KeyValueUpdateJsoImpl create() {
        KeyValueUpdateJsoImpl instance = (KeyValueUpdateJsoImpl) JsonMessage.createJsonMessage();
        // Force all lists to start with an empty list rather than no property for
        // the list. This is so that the native JS equality works, since (obviously)
        // {} != {"foo": []} while in the context of messages they should be.
        return instance;
      }

      @Override
      public void copyFrom(KeyValueUpdate message) {
        super.copyFrom((KeyValueUpdateJsoImpl) message);
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
        return hasProperty(this, keyKey) ? getPropertyAsString(this, keyKey) : null;
      }

      @Override
      public void setKey(String value) {
        setPropertyAsString(this, keyKey, value);
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
        return hasProperty(this, keyOldValue);
      }

      @Override
      public void clearOldValue() {
        if (hasProperty(this, keyOldValue)) {
          deleteProperty(this, keyOldValue);
        }
      }

      @Override
      public String getOldValue() {
        return hasProperty(this, keyOldValue) ? getPropertyAsString(this, keyOldValue) : null;
      }

      @Override
      public void setOldValue(String value) {
        setPropertyAsString(this, keyOldValue, value);
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
        return hasProperty(this, keyNewValue);
      }

      @Override
      public void clearNewValue() {
        if (hasProperty(this, keyNewValue)) {
          deleteProperty(this, keyNewValue);
        }
      }

      @Override
      public String getNewValue() {
        return hasProperty(this, keyNewValue) ? getPropertyAsString(this, keyNewValue) : null;
      }

      @Override
      public void setNewValue(String value) {
        setPropertyAsString(this, keyNewValue, value);
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (o instanceof KeyValueUpdateJsoImpl) {
          return nativeIsEqualTo(o);
        } else if (o instanceof KeyValueUpdate) {
          return KeyValueUpdateUtil.isEqual(this, (KeyValueUpdate) o);
        } else {
          return false;
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

    /** We have to use fully-qualified name of the GsonSerializable class here in order to make it
     * visible in case of nested classes.
     */
    public static final class ElementStartJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
        implements ElementStart {
      private static final String keyType = "1";
      private static final String keyAttribute = "2";
      protected ElementStartJsoImpl() {
      }

      public static ElementStartJsoImpl create() {
        ElementStartJsoImpl instance = (ElementStartJsoImpl) JsonMessage.createJsonMessage();
        // Force all lists to start with an empty list rather than no property for
        // the list. This is so that the native JS equality works, since (obviously)
        // {} != {"foo": []} while in the context of messages they should be.
        instance.clearAttribute();
        return instance;
      }

      @Override
      public void copyFrom(ElementStart message) {
        super.copyFrom((ElementStartJsoImpl) message);
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
        return hasProperty(this, keyType) ? getPropertyAsString(this, keyType) : null;
      }

      @Override
      public void setType(String value) {
        setPropertyAsString(this, keyType, value);
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
      public List<KeyValuePairJsoImpl> getAttribute() {
        initArray(this, keyAttribute);
        List<KeyValuePairJsoImpl> list = new ArrayList<KeyValuePairJsoImpl>();
        for (int i = 0; i < getAttributeSize(); i++) {
          list.add(getAttribute(i));
        }
        return list;
      }

      @Override
      public void addAllAttribute(List<? extends KeyValuePair> models) {
        for (KeyValuePair model : models) {
          addAttribute(model);
        }
      }

      @Override
      @SuppressWarnings("unchecked")
          public KeyValuePairJsoImpl getAttribute(int n) {
        initArray(this, keyAttribute);
        JsArray<KeyValuePairJsoImpl> array = getPropertyAsObject(this, keyAttribute).cast();
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
        return array.get(n);
      }

      @Override
      @SuppressWarnings("unchecked")
          public void setAttribute(int n, KeyValuePair model) {
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        initArray(this, keyAttribute);
        ((JsArray<KeyValuePairJsoImpl>) getPropertyAsObject(this, keyAttribute)).set(n, (KeyValuePairJsoImpl) model);
      }

      @Override
      public int getAttributeSize() {
        return hasProperty(this, keyAttribute) ? ((JsArray<?>) getPropertyAsObject(this, keyAttribute)).length() : 0;
      }

      @Override
      @SuppressWarnings("unchecked")
          public void addAttribute(KeyValuePair model) {
        initArray(this, keyAttribute);
        ((JsArray<KeyValuePairJsoImpl>) getPropertyAsObject(this, keyAttribute)).push((KeyValuePairJsoImpl) model);
      }

      @Override
      public void clearAttribute() {
        clearArray(this, keyAttribute);
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (o instanceof ElementStartJsoImpl) {
          return nativeIsEqualTo(o);
        } else if (o instanceof ElementStart) {
          return ElementStartUtil.isEqual(this, (ElementStart) o);
        } else {
          return false;
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

    /** We have to use fully-qualified name of the GsonSerializable class here in order to make it
     * visible in case of nested classes.
     */
    public static final class ReplaceAttributesJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
        implements ReplaceAttributes {
      private static final String keyEmpty = "1";
      private static final String keyOldAttribute = "2";
      private static final String keyNewAttribute = "3";
      protected ReplaceAttributesJsoImpl() {
      }

      public static ReplaceAttributesJsoImpl create() {
        ReplaceAttributesJsoImpl instance = (ReplaceAttributesJsoImpl) JsonMessage.createJsonMessage();
        // Force all lists to start with an empty list rather than no property for
        // the list. This is so that the native JS equality works, since (obviously)
        // {} != {"foo": []} while in the context of messages they should be.
        instance.clearOldAttribute();
        instance.clearNewAttribute();
        return instance;
      }

      @Override
      public void copyFrom(ReplaceAttributes message) {
        super.copyFrom((ReplaceAttributesJsoImpl) message);
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
        return hasProperty(this, keyEmpty);
      }

      @Override
      public void clearEmpty() {
        if (hasProperty(this, keyEmpty)) {
          deleteProperty(this, keyEmpty);
        }
      }

      @Override
      public boolean getEmpty() {
        return hasProperty(this, keyEmpty) ? getPropertyAsBoolean(this, keyEmpty) : false;
      }

      @Override
      public void setEmpty(boolean value) {
        setPropertyAsBoolean(this, keyEmpty, value);
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
      public List<KeyValuePairJsoImpl> getOldAttribute() {
        initArray(this, keyOldAttribute);
        List<KeyValuePairJsoImpl> list = new ArrayList<KeyValuePairJsoImpl>();
        for (int i = 0; i < getOldAttributeSize(); i++) {
          list.add(getOldAttribute(i));
        }
        return list;
      }

      @Override
      public void addAllOldAttribute(List<? extends KeyValuePair> models) {
        for (KeyValuePair model : models) {
          addOldAttribute(model);
        }
      }

      @Override
      @SuppressWarnings("unchecked")
          public KeyValuePairJsoImpl getOldAttribute(int n) {
        initArray(this, keyOldAttribute);
        JsArray<KeyValuePairJsoImpl> array = getPropertyAsObject(this, keyOldAttribute).cast();
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
        return array.get(n);
      }

      @Override
      @SuppressWarnings("unchecked")
          public void setOldAttribute(int n, KeyValuePair model) {
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        initArray(this, keyOldAttribute);
        ((JsArray<KeyValuePairJsoImpl>) getPropertyAsObject(this, keyOldAttribute)).set(n, (KeyValuePairJsoImpl) model);
      }

      @Override
      public int getOldAttributeSize() {
        return hasProperty(this, keyOldAttribute) ? ((JsArray<?>) getPropertyAsObject(this, keyOldAttribute)).length() : 0;
      }

      @Override
      @SuppressWarnings("unchecked")
          public void addOldAttribute(KeyValuePair model) {
        initArray(this, keyOldAttribute);
        ((JsArray<KeyValuePairJsoImpl>) getPropertyAsObject(this, keyOldAttribute)).push((KeyValuePairJsoImpl) model);
      }

      @Override
      public void clearOldAttribute() {
        clearArray(this, keyOldAttribute);
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
      public List<KeyValuePairJsoImpl> getNewAttribute() {
        initArray(this, keyNewAttribute);
        List<KeyValuePairJsoImpl> list = new ArrayList<KeyValuePairJsoImpl>();
        for (int i = 0; i < getNewAttributeSize(); i++) {
          list.add(getNewAttribute(i));
        }
        return list;
      }

      @Override
      public void addAllNewAttribute(List<? extends KeyValuePair> models) {
        for (KeyValuePair model : models) {
          addNewAttribute(model);
        }
      }

      @Override
      @SuppressWarnings("unchecked")
          public KeyValuePairJsoImpl getNewAttribute(int n) {
        initArray(this, keyNewAttribute);
        JsArray<KeyValuePairJsoImpl> array = getPropertyAsObject(this, keyNewAttribute).cast();
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
        return array.get(n);
      }

      @Override
      @SuppressWarnings("unchecked")
          public void setNewAttribute(int n, KeyValuePair model) {
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        initArray(this, keyNewAttribute);
        ((JsArray<KeyValuePairJsoImpl>) getPropertyAsObject(this, keyNewAttribute)).set(n, (KeyValuePairJsoImpl) model);
      }

      @Override
      public int getNewAttributeSize() {
        return hasProperty(this, keyNewAttribute) ? ((JsArray<?>) getPropertyAsObject(this, keyNewAttribute)).length() : 0;
      }

      @Override
      @SuppressWarnings("unchecked")
          public void addNewAttribute(KeyValuePair model) {
        initArray(this, keyNewAttribute);
        ((JsArray<KeyValuePairJsoImpl>) getPropertyAsObject(this, keyNewAttribute)).push((KeyValuePairJsoImpl) model);
      }

      @Override
      public void clearNewAttribute() {
        clearArray(this, keyNewAttribute);
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (o instanceof ReplaceAttributesJsoImpl) {
          return nativeIsEqualTo(o);
        } else if (o instanceof ReplaceAttributes) {
          return ReplaceAttributesUtil.isEqual(this, (ReplaceAttributes) o);
        } else {
          return false;
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

    /** We have to use fully-qualified name of the GsonSerializable class here in order to make it
     * visible in case of nested classes.
     */
    public static final class UpdateAttributesJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
        implements UpdateAttributes {
      private static final String keyEmpty = "1";
      private static final String keyAttributeUpdate = "2";
      protected UpdateAttributesJsoImpl() {
      }

      public static UpdateAttributesJsoImpl create() {
        UpdateAttributesJsoImpl instance = (UpdateAttributesJsoImpl) JsonMessage.createJsonMessage();
        // Force all lists to start with an empty list rather than no property for
        // the list. This is so that the native JS equality works, since (obviously)
        // {} != {"foo": []} while in the context of messages they should be.
        instance.clearAttributeUpdate();
        return instance;
      }

      @Override
      public void copyFrom(UpdateAttributes message) {
        super.copyFrom((UpdateAttributesJsoImpl) message);
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
        return hasProperty(this, keyEmpty);
      }

      @Override
      public void clearEmpty() {
        if (hasProperty(this, keyEmpty)) {
          deleteProperty(this, keyEmpty);
        }
      }

      @Override
      public boolean getEmpty() {
        return hasProperty(this, keyEmpty) ? getPropertyAsBoolean(this, keyEmpty) : false;
      }

      @Override
      public void setEmpty(boolean value) {
        setPropertyAsBoolean(this, keyEmpty, value);
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
      public List<KeyValueUpdateJsoImpl> getAttributeUpdate() {
        initArray(this, keyAttributeUpdate);
        List<KeyValueUpdateJsoImpl> list = new ArrayList<KeyValueUpdateJsoImpl>();
        for (int i = 0; i < getAttributeUpdateSize(); i++) {
          list.add(getAttributeUpdate(i));
        }
        return list;
      }

      @Override
      public void addAllAttributeUpdate(List<? extends KeyValueUpdate> models) {
        for (KeyValueUpdate model : models) {
          addAttributeUpdate(model);
        }
      }

      @Override
      @SuppressWarnings("unchecked")
          public KeyValueUpdateJsoImpl getAttributeUpdate(int n) {
        initArray(this, keyAttributeUpdate);
        JsArray<KeyValueUpdateJsoImpl> array = getPropertyAsObject(this, keyAttributeUpdate).cast();
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
        return array.get(n);
      }

      @Override
      @SuppressWarnings("unchecked")
          public void setAttributeUpdate(int n, KeyValueUpdate model) {
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        initArray(this, keyAttributeUpdate);
        ((JsArray<KeyValueUpdateJsoImpl>) getPropertyAsObject(this, keyAttributeUpdate)).set(n, (KeyValueUpdateJsoImpl) model);
      }

      @Override
      public int getAttributeUpdateSize() {
        return hasProperty(this, keyAttributeUpdate) ? ((JsArray<?>) getPropertyAsObject(this, keyAttributeUpdate)).length() : 0;
      }

      @Override
      @SuppressWarnings("unchecked")
          public void addAttributeUpdate(KeyValueUpdate model) {
        initArray(this, keyAttributeUpdate);
        ((JsArray<KeyValueUpdateJsoImpl>) getPropertyAsObject(this, keyAttributeUpdate)).push((KeyValueUpdateJsoImpl) model);
      }

      @Override
      public void clearAttributeUpdate() {
        clearArray(this, keyAttributeUpdate);
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (o instanceof UpdateAttributesJsoImpl) {
          return nativeIsEqualTo(o);
        } else if (o instanceof UpdateAttributes) {
          return UpdateAttributesUtil.isEqual(this, (UpdateAttributes) o);
        } else {
          return false;
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

    /** We have to use fully-qualified name of the GsonSerializable class here in order to make it
     * visible in case of nested classes.
     */
    public static final class AnnotationBoundaryJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
        implements AnnotationBoundary {
      private static final String keyEmpty = "1";
      private static final String keyEnd = "2";
      private static final String keyChange = "3";
      protected AnnotationBoundaryJsoImpl() {
      }

      public static AnnotationBoundaryJsoImpl create() {
        AnnotationBoundaryJsoImpl instance = (AnnotationBoundaryJsoImpl) JsonMessage.createJsonMessage();
        // Force all lists to start with an empty list rather than no property for
        // the list. This is so that the native JS equality works, since (obviously)
        // {} != {"foo": []} while in the context of messages they should be.
        instance.clearEnd();
        instance.clearChange();
        return instance;
      }

      @Override
      public void copyFrom(AnnotationBoundary message) {
        super.copyFrom((AnnotationBoundaryJsoImpl) message);
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
        return hasProperty(this, keyEmpty);
      }

      @Override
      public void clearEmpty() {
        if (hasProperty(this, keyEmpty)) {
          deleteProperty(this, keyEmpty);
        }
      }

      @Override
      public boolean getEmpty() {
        return hasProperty(this, keyEmpty) ? getPropertyAsBoolean(this, keyEmpty) : false;
      }

      @Override
      public void setEmpty(boolean value) {
        setPropertyAsBoolean(this, keyEmpty, value);
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
        initArray(this, keyEnd);
        List<String> list = new ArrayList<String>();
        for (int i = 0; i < getEndSize(); i++) {
          list.add(getEnd(i));
        }
        return list;
      }

      @Override
      public void addAllEnd(List<String> values) {
        for (String value : values) {
          addEnd(value);
        }
      }

      @Override
      @SuppressWarnings("unchecked")
          public String getEnd(int n) {
        initArray(this, keyEnd);
        JsArrayString array = getPropertyAsObject(this, keyEnd).cast();
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
        return array.get(n);
      }

      @Override
      public void setEnd(int n, String value) {
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        initArray(this, keyEnd);
        ((JsArrayString) getPropertyAsObject(this, keyEnd)).set(n, value);
      }

      @Override
      public int getEndSize() {
        return hasProperty(this, keyEnd) ? ((JsArray<?>) getPropertyAsObject(this, keyEnd)).length() : 0;
      }

      @Override
      public void addEnd(String value) {
        initArray(this, keyEnd);
        ((JsArrayString) getPropertyAsObject(this, keyEnd)).push(value);
      }

      @Override
      public void clearEnd() {
        clearArray(this, keyEnd);
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
      public List<KeyValueUpdateJsoImpl> getChange() {
        initArray(this, keyChange);
        List<KeyValueUpdateJsoImpl> list = new ArrayList<KeyValueUpdateJsoImpl>();
        for (int i = 0; i < getChangeSize(); i++) {
          list.add(getChange(i));
        }
        return list;
      }

      @Override
      public void addAllChange(List<? extends KeyValueUpdate> models) {
        for (KeyValueUpdate model : models) {
          addChange(model);
        }
      }

      @Override
      @SuppressWarnings("unchecked")
          public KeyValueUpdateJsoImpl getChange(int n) {
        initArray(this, keyChange);
        JsArray<KeyValueUpdateJsoImpl> array = getPropertyAsObject(this, keyChange).cast();
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
        return array.get(n);
      }

      @Override
      @SuppressWarnings("unchecked")
          public void setChange(int n, KeyValueUpdate model) {
        if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
        initArray(this, keyChange);
        ((JsArray<KeyValueUpdateJsoImpl>) getPropertyAsObject(this, keyChange)).set(n, (KeyValueUpdateJsoImpl) model);
      }

      @Override
      public int getChangeSize() {
        return hasProperty(this, keyChange) ? ((JsArray<?>) getPropertyAsObject(this, keyChange)).length() : 0;
      }

      @Override
      @SuppressWarnings("unchecked")
          public void addChange(KeyValueUpdate model) {
        initArray(this, keyChange);
        ((JsArray<KeyValueUpdateJsoImpl>) getPropertyAsObject(this, keyChange)).push((KeyValueUpdateJsoImpl) model);
      }

      @Override
      public void clearChange() {
        clearArray(this, keyChange);
      }

      @Override
      public boolean isEqualTo(Object o) {
        if (o instanceof AnnotationBoundaryJsoImpl) {
          return nativeIsEqualTo(o);
        } else if (o instanceof AnnotationBoundary) {
          return AnnotationBoundaryUtil.isEqual(this, (AnnotationBoundary) o);
        } else {
          return false;
        }
      }

    }

    private static final String keyAnnotationBoundary = "1";
    private static final String keyCharacters = "2";
    private static final String keyElementStart = "3";
    private static final String keyElementEnd = "4";
    private static final String keyRetainItemCount = "5";
    private static final String keyDeleteCharacters = "6";
    private static final String keyDeleteElementStart = "7";
    private static final String keyDeleteElementEnd = "8";
    private static final String keyReplaceAttributes = "9";
    private static final String keyUpdateAttributes = "10";
    protected ComponentJsoImpl() {
    }

    public static ComponentJsoImpl create() {
      ComponentJsoImpl instance = (ComponentJsoImpl) JsonMessage.createJsonMessage();
      // Force all lists to start with an empty list rather than no property for
      // the list. This is so that the native JS equality works, since (obviously)
      // {} != {"foo": []} while in the context of messages they should be.
      return instance;
    }

    @Override
    public void copyFrom(Component message) {
      super.copyFrom((ComponentJsoImpl) message);
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
      return hasProperty(this, keyAnnotationBoundary);
    }

    @Override
    public void clearAnnotationBoundary() {
      if (hasProperty(this, keyAnnotationBoundary)) {
        deleteProperty(this, keyAnnotationBoundary);
      }
    }

    @Override
    public AnnotationBoundary getAnnotationBoundary() {
      return hasProperty(this, keyAnnotationBoundary) ? ((AnnotationBoundaryJsoImpl) getPropertyAsObject(this, keyAnnotationBoundary)) : null;
    }

    @Override
    public void setAnnotationBoundary(AnnotationBoundary model) {
      setPropertyAsObject(this, keyAnnotationBoundary, (AnnotationBoundaryJsoImpl) model);
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
      return hasProperty(this, keyCharacters);
    }

    @Override
    public void clearCharacters() {
      if (hasProperty(this, keyCharacters)) {
        deleteProperty(this, keyCharacters);
      }
    }

    @Override
    public String getCharacters() {
      return hasProperty(this, keyCharacters) ? getPropertyAsString(this, keyCharacters) : null;
    }

    @Override
    public void setCharacters(String value) {
      setPropertyAsString(this, keyCharacters, value);
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
      return hasProperty(this, keyElementStart);
    }

    @Override
    public void clearElementStart() {
      if (hasProperty(this, keyElementStart)) {
        deleteProperty(this, keyElementStart);
      }
    }

    @Override
    public ElementStart getElementStart() {
      return hasProperty(this, keyElementStart) ? ((ElementStartJsoImpl) getPropertyAsObject(this, keyElementStart)) : null;
    }

    @Override
    public void setElementStart(ElementStart model) {
      setPropertyAsObject(this, keyElementStart, (ElementStartJsoImpl) model);
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
      return hasProperty(this, keyElementEnd);
    }

    @Override
    public void clearElementEnd() {
      if (hasProperty(this, keyElementEnd)) {
        deleteProperty(this, keyElementEnd);
      }
    }

    @Override
    public boolean getElementEnd() {
      return hasProperty(this, keyElementEnd) ? getPropertyAsBoolean(this, keyElementEnd) : false;
    }

    @Override
    public void setElementEnd(boolean value) {
      setPropertyAsBoolean(this, keyElementEnd, value);
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
      return hasProperty(this, keyRetainItemCount);
    }

    @Override
    public void clearRetainItemCount() {
      if (hasProperty(this, keyRetainItemCount)) {
        deleteProperty(this, keyRetainItemCount);
      }
    }

    @Override
    public int getRetainItemCount() {
      return hasProperty(this, keyRetainItemCount) ? getPropertyAsInteger(this, keyRetainItemCount) : 0;
    }

    @Override
    public void setRetainItemCount(int value) {
      setPropertyAsInteger(this, keyRetainItemCount, value);
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
      return hasProperty(this, keyDeleteCharacters);
    }

    @Override
    public void clearDeleteCharacters() {
      if (hasProperty(this, keyDeleteCharacters)) {
        deleteProperty(this, keyDeleteCharacters);
      }
    }

    @Override
    public String getDeleteCharacters() {
      return hasProperty(this, keyDeleteCharacters) ? getPropertyAsString(this, keyDeleteCharacters) : null;
    }

    @Override
    public void setDeleteCharacters(String value) {
      setPropertyAsString(this, keyDeleteCharacters, value);
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
      return hasProperty(this, keyDeleteElementStart);
    }

    @Override
    public void clearDeleteElementStart() {
      if (hasProperty(this, keyDeleteElementStart)) {
        deleteProperty(this, keyDeleteElementStart);
      }
    }

    @Override
    public ElementStart getDeleteElementStart() {
      return hasProperty(this, keyDeleteElementStart) ? ((ElementStartJsoImpl) getPropertyAsObject(this, keyDeleteElementStart)) : null;
    }

    @Override
    public void setDeleteElementStart(ElementStart model) {
      setPropertyAsObject(this, keyDeleteElementStart, (ElementStartJsoImpl) model);
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
      return hasProperty(this, keyDeleteElementEnd);
    }

    @Override
    public void clearDeleteElementEnd() {
      if (hasProperty(this, keyDeleteElementEnd)) {
        deleteProperty(this, keyDeleteElementEnd);
      }
    }

    @Override
    public boolean getDeleteElementEnd() {
      return hasProperty(this, keyDeleteElementEnd) ? getPropertyAsBoolean(this, keyDeleteElementEnd) : false;
    }

    @Override
    public void setDeleteElementEnd(boolean value) {
      setPropertyAsBoolean(this, keyDeleteElementEnd, value);
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
      return hasProperty(this, keyReplaceAttributes);
    }

    @Override
    public void clearReplaceAttributes() {
      if (hasProperty(this, keyReplaceAttributes)) {
        deleteProperty(this, keyReplaceAttributes);
      }
    }

    @Override
    public ReplaceAttributes getReplaceAttributes() {
      return hasProperty(this, keyReplaceAttributes) ? ((ReplaceAttributesJsoImpl) getPropertyAsObject(this, keyReplaceAttributes)) : null;
    }

    @Override
    public void setReplaceAttributes(ReplaceAttributes model) {
      setPropertyAsObject(this, keyReplaceAttributes, (ReplaceAttributesJsoImpl) model);
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
      return hasProperty(this, keyUpdateAttributes);
    }

    @Override
    public void clearUpdateAttributes() {
      if (hasProperty(this, keyUpdateAttributes)) {
        deleteProperty(this, keyUpdateAttributes);
      }
    }

    @Override
    public UpdateAttributes getUpdateAttributes() {
      return hasProperty(this, keyUpdateAttributes) ? ((UpdateAttributesJsoImpl) getPropertyAsObject(this, keyUpdateAttributes)) : null;
    }

    @Override
    public void setUpdateAttributes(UpdateAttributes model) {
      setPropertyAsObject(this, keyUpdateAttributes, (UpdateAttributesJsoImpl) model);
    }

    @Override
    public boolean isEqualTo(Object o) {
      if (o instanceof ComponentJsoImpl) {
        return nativeIsEqualTo(o);
      } else if (o instanceof Component) {
        return ComponentUtil.isEqual(this, (Component) o);
      } else {
        return false;
      }
    }

  }

  private static final String keyComponent = "1";
  protected ProtocolDocumentOperationJsoImpl() {
  }

  public static ProtocolDocumentOperationJsoImpl create() {
    ProtocolDocumentOperationJsoImpl instance = (ProtocolDocumentOperationJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearComponent();
    return instance;
  }

  @Override
  public void copyFrom(ProtocolDocumentOperation message) {
    super.copyFrom((ProtocolDocumentOperationJsoImpl) message);
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
  public List<ComponentJsoImpl> getComponent() {
    initArray(this, keyComponent);
    List<ComponentJsoImpl> list = new ArrayList<ComponentJsoImpl>();
    for (int i = 0; i < getComponentSize(); i++) {
      list.add(getComponent(i));
    }
    return list;
  }

  @Override
  public void addAllComponent(List<? extends Component> models) {
    for (Component model : models) {
      addComponent(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public ComponentJsoImpl getComponent(int n) {
    initArray(this, keyComponent);
    JsArray<ComponentJsoImpl> array = getPropertyAsObject(this, keyComponent).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setComponent(int n, Component model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyComponent);
    ((JsArray<ComponentJsoImpl>) getPropertyAsObject(this, keyComponent)).set(n, (ComponentJsoImpl) model);
  }

  @Override
  public int getComponentSize() {
    return hasProperty(this, keyComponent) ? ((JsArray<?>) getPropertyAsObject(this, keyComponent)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addComponent(Component model) {
    initArray(this, keyComponent);
    ((JsArray<ComponentJsoImpl>) getPropertyAsObject(this, keyComponent)).push((ComponentJsoImpl) model);
  }

  @Override
  public void clearComponent() {
    clearArray(this, keyComponent);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProtocolDocumentOperationJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProtocolDocumentOperation) {
      return ProtocolDocumentOperationUtil.isEqual(this, (ProtocolDocumentOperation) o);
    } else {
      return false;
    }
  }

}