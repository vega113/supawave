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

package org.waveprotocol.wave.federation;

import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.AnnotationBoundary;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ElementStart;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.ReplaceAttributes;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.UpdateAttributes;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValuePair;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation.Component.KeyValueUpdate;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import java.util.List;

/**
 * Model interface for ProtocolDocumentOperation.
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
public interface ProtocolDocumentOperation {

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
  public interface Component {

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
    public interface KeyValuePair {

      /** Does a deep copy from model. */
      void copyFrom(KeyValuePair model);

      /**
       * Tests if this model is equal to another object.
       * "Equal" is recursively defined as:
       * <ul>
       * <li>both objects implement this interface,</li>
       * <li>all corresponding primitive fields of both objects have the same value, and</li>
       * <li>all corresponding nested-model fields of both objects are "equal".</li>
       * </ul>
       *
       * This is a coarser equivalence than provided by the equals() methods. Two
       * objects may not be equal() to each other, but may be isEqualTo() each other.
       */
      boolean isEqualTo(Object o);

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

      /** Returns key, or null if hasn't been set. */
      String getKey();

      /** Sets key. */
      void setKey(String key);

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

      /** Returns value, or null if hasn't been set. */
      String getValue();

      /** Sets value. */
      void setValue(String value);
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
    public interface KeyValueUpdate {

      /** Does a deep copy from model. */
      void copyFrom(KeyValueUpdate model);

      /**
       * Tests if this model is equal to another object.
       * "Equal" is recursively defined as:
       * <ul>
       * <li>both objects implement this interface,</li>
       * <li>all corresponding primitive fields of both objects have the same value, and</li>
       * <li>all corresponding nested-model fields of both objects are "equal".</li>
       * </ul>
       *
       * This is a coarser equivalence than provided by the equals() methods. Two
       * objects may not be equal() to each other, but may be isEqualTo() each other.
       */
      boolean isEqualTo(Object o);

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

      /** Returns key, or null if hasn't been set. */
      String getKey();

      /** Sets key. */
      void setKey(String key);

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

      /** Returns whether oldValue has been set. */
      boolean hasOldValue();

      /** Clears the value of oldValue. */
      void clearOldValue();

      /** Returns oldValue, or null if hasn't been set. */
      String getOldValue();

      /** Sets oldValue. */
      void setOldValue(String oldValue);

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

      /** Returns whether newValue has been set. */
      boolean hasNewValue();

      /** Clears the value of newValue. */
      void clearNewValue();

      /** Returns newValue, or null if hasn't been set. */
      String getNewValue();

      /** Sets newValue. */
      void setNewValue(String newValue);
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
    public interface ElementStart {

      /** Does a deep copy from model. */
      void copyFrom(ElementStart model);

      /**
       * Tests if this model is equal to another object.
       * "Equal" is recursively defined as:
       * <ul>
       * <li>both objects implement this interface,</li>
       * <li>all corresponding primitive fields of both objects have the same value, and</li>
       * <li>all corresponding nested-model fields of both objects are "equal".</li>
       * </ul>
       *
       * This is a coarser equivalence than provided by the equals() methods. Two
       * objects may not be equal() to each other, but may be isEqualTo() each other.
       */
      boolean isEqualTo(Object o);

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

      /** Returns type, or null if hasn't been set. */
      String getType();

      /** Sets type. */
      void setType(String type);

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

      /** Returns attribute, or null if hasn't been set. */
      List<? extends KeyValuePair> getAttribute();

      /** Adds an element to attribute. */
      void addAttribute(KeyValuePair value);

      /** Adds a list of elements to attribute. */
      void addAllAttribute(List<? extends KeyValuePair> attribute);

      /** Returns the nth element of attribute. */
      KeyValuePair getAttribute(int n);

      /** Sets the nth element of attribute. */
      void setAttribute(int n, KeyValuePair value);

      /** Returns the length of attribute. */
      int getAttributeSize();

      /** Clears attribute. */
      void clearAttribute();
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
    public interface ReplaceAttributes {

      /** Does a deep copy from model. */
      void copyFrom(ReplaceAttributes model);

      /**
       * Tests if this model is equal to another object.
       * "Equal" is recursively defined as:
       * <ul>
       * <li>both objects implement this interface,</li>
       * <li>all corresponding primitive fields of both objects have the same value, and</li>
       * <li>all corresponding nested-model fields of both objects are "equal".</li>
       * </ul>
       *
       * This is a coarser equivalence than provided by the equals() methods. Two
       * objects may not be equal() to each other, but may be isEqualTo() each other.
       */
      boolean isEqualTo(Object o);

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

      /** Returns whether empty has been set. */
      boolean hasEmpty();

      /** Clears the value of empty. */
      void clearEmpty();

      /** Returns empty, or null if hasn't been set. */
      boolean getEmpty();

      /** Sets empty. */
      void setEmpty(boolean empty);

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

      /** Returns oldAttribute, or null if hasn't been set. */
      List<? extends KeyValuePair> getOldAttribute();

      /** Adds an element to oldAttribute. */
      void addOldAttribute(KeyValuePair value);

      /** Adds a list of elements to oldAttribute. */
      void addAllOldAttribute(List<? extends KeyValuePair> oldAttribute);

      /** Returns the nth element of oldAttribute. */
      KeyValuePair getOldAttribute(int n);

      /** Sets the nth element of oldAttribute. */
      void setOldAttribute(int n, KeyValuePair value);

      /** Returns the length of oldAttribute. */
      int getOldAttributeSize();

      /** Clears oldAttribute. */
      void clearOldAttribute();

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

      /** Returns newAttribute, or null if hasn't been set. */
      List<? extends KeyValuePair> getNewAttribute();

      /** Adds an element to newAttribute. */
      void addNewAttribute(KeyValuePair value);

      /** Adds a list of elements to newAttribute. */
      void addAllNewAttribute(List<? extends KeyValuePair> newAttribute);

      /** Returns the nth element of newAttribute. */
      KeyValuePair getNewAttribute(int n);

      /** Sets the nth element of newAttribute. */
      void setNewAttribute(int n, KeyValuePair value);

      /** Returns the length of newAttribute. */
      int getNewAttributeSize();

      /** Clears newAttribute. */
      void clearNewAttribute();
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
    public interface UpdateAttributes {

      /** Does a deep copy from model. */
      void copyFrom(UpdateAttributes model);

      /**
       * Tests if this model is equal to another object.
       * "Equal" is recursively defined as:
       * <ul>
       * <li>both objects implement this interface,</li>
       * <li>all corresponding primitive fields of both objects have the same value, and</li>
       * <li>all corresponding nested-model fields of both objects are "equal".</li>
       * </ul>
       *
       * This is a coarser equivalence than provided by the equals() methods. Two
       * objects may not be equal() to each other, but may be isEqualTo() each other.
       */
      boolean isEqualTo(Object o);

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

      /** Returns whether empty has been set. */
      boolean hasEmpty();

      /** Clears the value of empty. */
      void clearEmpty();

      /** Returns empty, or null if hasn't been set. */
      boolean getEmpty();

      /** Sets empty. */
      void setEmpty(boolean empty);

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

      /** Returns attributeUpdate, or null if hasn't been set. */
      List<? extends KeyValueUpdate> getAttributeUpdate();

      /** Adds an element to attributeUpdate. */
      void addAttributeUpdate(KeyValueUpdate value);

      /** Adds a list of elements to attributeUpdate. */
      void addAllAttributeUpdate(List<? extends KeyValueUpdate> attributeUpdate);

      /** Returns the nth element of attributeUpdate. */
      KeyValueUpdate getAttributeUpdate(int n);

      /** Sets the nth element of attributeUpdate. */
      void setAttributeUpdate(int n, KeyValueUpdate value);

      /** Returns the length of attributeUpdate. */
      int getAttributeUpdateSize();

      /** Clears attributeUpdate. */
      void clearAttributeUpdate();
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
    public interface AnnotationBoundary {

      /** Does a deep copy from model. */
      void copyFrom(AnnotationBoundary model);

      /**
       * Tests if this model is equal to another object.
       * "Equal" is recursively defined as:
       * <ul>
       * <li>both objects implement this interface,</li>
       * <li>all corresponding primitive fields of both objects have the same value, and</li>
       * <li>all corresponding nested-model fields of both objects are "equal".</li>
       * </ul>
       *
       * This is a coarser equivalence than provided by the equals() methods. Two
       * objects may not be equal() to each other, but may be isEqualTo() each other.
       */
      boolean isEqualTo(Object o);

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

      /** Returns whether empty has been set. */
      boolean hasEmpty();

      /** Clears the value of empty. */
      void clearEmpty();

      /** Returns empty, or null if hasn't been set. */
      boolean getEmpty();

      /** Sets empty. */
      void setEmpty(boolean empty);

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

      /** Returns end, or null if hasn't been set. */
      List<String> getEnd();

      /** Adds an element to end. */
      void addEnd(String value);

      /** Adds a list of elements to end. */
      void addAllEnd(List<String> end);

      /** Returns the nth element of end. */
      String getEnd(int n);

      /** Sets the nth element of end. */
      void setEnd(int n, String value);

      /** Returns the length of end. */
      int getEndSize();

      /** Clears end. */
      void clearEnd();

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

      /** Returns change, or null if hasn't been set. */
      List<? extends KeyValueUpdate> getChange();

      /** Adds an element to change. */
      void addChange(KeyValueUpdate value);

      /** Adds a list of elements to change. */
      void addAllChange(List<? extends KeyValueUpdate> change);

      /** Returns the nth element of change. */
      KeyValueUpdate getChange(int n);

      /** Sets the nth element of change. */
      void setChange(int n, KeyValueUpdate value);

      /** Returns the length of change. */
      int getChangeSize();

      /** Clears change. */
      void clearChange();
    }

    /** Does a deep copy from model. */
    void copyFrom(Component model);

    /**
     * Tests if this model is equal to another object.
     * "Equal" is recursively defined as:
     * <ul>
     * <li>both objects implement this interface,</li>
     * <li>all corresponding primitive fields of both objects have the same value, and</li>
     * <li>all corresponding nested-model fields of both objects are "equal".</li>
     * </ul>
     *
     * This is a coarser equivalence than provided by the equals() methods. Two
     * objects may not be equal() to each other, but may be isEqualTo() each other.
     */
    boolean isEqualTo(Object o);

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

    /** Returns whether annotationBoundary has been set. */
    boolean hasAnnotationBoundary();

    /** Clears the value of annotationBoundary. */
    void clearAnnotationBoundary();

    /** Returns annotationBoundary, or null if hasn't been set. */
    AnnotationBoundary getAnnotationBoundary();

    /** Sets annotationBoundary. */
    void setAnnotationBoundary(AnnotationBoundary annotationBoundary);

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

    /** Returns whether characters has been set. */
    boolean hasCharacters();

    /** Clears the value of characters. */
    void clearCharacters();

    /** Returns characters, or null if hasn't been set. */
    String getCharacters();

    /** Sets characters. */
    void setCharacters(String characters);

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

    /** Returns whether elementStart has been set. */
    boolean hasElementStart();

    /** Clears the value of elementStart. */
    void clearElementStart();

    /** Returns elementStart, or null if hasn't been set. */
    ElementStart getElementStart();

    /** Sets elementStart. */
    void setElementStart(ElementStart elementStart);

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

    /** Returns whether elementEnd has been set. */
    boolean hasElementEnd();

    /** Clears the value of elementEnd. */
    void clearElementEnd();

    /** Returns elementEnd, or null if hasn't been set. */
    boolean getElementEnd();

    /** Sets elementEnd. */
    void setElementEnd(boolean elementEnd);

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

    /** Returns whether retainItemCount has been set. */
    boolean hasRetainItemCount();

    /** Clears the value of retainItemCount. */
    void clearRetainItemCount();

    /** Returns retainItemCount, or null if hasn't been set. */
    int getRetainItemCount();

    /** Sets retainItemCount. */
    void setRetainItemCount(int retainItemCount);

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

    /** Returns whether deleteCharacters has been set. */
    boolean hasDeleteCharacters();

    /** Clears the value of deleteCharacters. */
    void clearDeleteCharacters();

    /** Returns deleteCharacters, or null if hasn't been set. */
    String getDeleteCharacters();

    /** Sets deleteCharacters. */
    void setDeleteCharacters(String deleteCharacters);

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

    /** Returns whether deleteElementStart has been set. */
    boolean hasDeleteElementStart();

    /** Clears the value of deleteElementStart. */
    void clearDeleteElementStart();

    /** Returns deleteElementStart, or null if hasn't been set. */
    ElementStart getDeleteElementStart();

    /** Sets deleteElementStart. */
    void setDeleteElementStart(ElementStart deleteElementStart);

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

    /** Returns whether deleteElementEnd has been set. */
    boolean hasDeleteElementEnd();

    /** Clears the value of deleteElementEnd. */
    void clearDeleteElementEnd();

    /** Returns deleteElementEnd, or null if hasn't been set. */
    boolean getDeleteElementEnd();

    /** Sets deleteElementEnd. */
    void setDeleteElementEnd(boolean deleteElementEnd);

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

    /** Returns whether replaceAttributes has been set. */
    boolean hasReplaceAttributes();

    /** Clears the value of replaceAttributes. */
    void clearReplaceAttributes();

    /** Returns replaceAttributes, or null if hasn't been set. */
    ReplaceAttributes getReplaceAttributes();

    /** Sets replaceAttributes. */
    void setReplaceAttributes(ReplaceAttributes replaceAttributes);

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

    /** Returns whether updateAttributes has been set. */
    boolean hasUpdateAttributes();

    /** Clears the value of updateAttributes. */
    void clearUpdateAttributes();

    /** Returns updateAttributes, or null if hasn't been set. */
    UpdateAttributes getUpdateAttributes();

    /** Sets updateAttributes. */
    void setUpdateAttributes(UpdateAttributes updateAttributes);
  }

  /** Does a deep copy from model. */
  void copyFrom(ProtocolDocumentOperation model);

  /**
   * Tests if this model is equal to another object.
   * "Equal" is recursively defined as:
   * <ul>
   * <li>both objects implement this interface,</li>
   * <li>all corresponding primitive fields of both objects have the same value, and</li>
   * <li>all corresponding nested-model fields of both objects are "equal".</li>
   * </ul>
   *
   * This is a coarser equivalence than provided by the equals() methods. Two
   * objects may not be equal() to each other, but may be isEqualTo() each other.
   */
  boolean isEqualTo(Object o);

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

  /** Returns component, or null if hasn't been set. */
  List<? extends Component> getComponent();

  /** Adds an element to component. */
  void addComponent(Component value);

  /** Adds a list of elements to component. */
  void addAllComponent(List<? extends Component> component);

  /** Returns the nth element of component. */
  Component getComponent(int n);

  /** Sets the nth element of component. */
  void setComponent(int n, Component value);

  /** Returns the length of component. */
  int getComponentSize();

  /** Clears component. */
  void clearComponent();
}