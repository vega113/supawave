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
import org.waveprotocol.wave.federation.ProtocolWaveletOperation.MutateDocument;
import org.waveprotocol.wave.federation.ProtocolDocumentOperation;
import org.waveprotocol.wave.federation.jso.ProtocolWaveletOperationJsoImpl.MutateDocumentJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolDocumentOperationJsoImpl;

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
import org.waveprotocol.wave.federation.ProtocolWaveletOperation;
import org.waveprotocol.wave.federation.ProtocolWaveletOperationUtil;

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
import org.waveprotocol.wave.federation.ProtocolWaveletOperation.MutateDocument;
import org.waveprotocol.wave.federation.ProtocolWaveletOperationUtil.MutateDocumentUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProtocolWaveletOperation backed by a GWT JavaScriptObject.
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
public final class ProtocolWaveletOperationJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProtocolWaveletOperation {

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
  public static final class MutateDocumentJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
      implements MutateDocument {
    private static final String keyDocumentId = "1";
    private static final String keyDocumentOperation = "2";
    protected MutateDocumentJsoImpl() {
    }

    public static MutateDocumentJsoImpl create() {
      MutateDocumentJsoImpl instance = (MutateDocumentJsoImpl) JsonMessage.createJsonMessage();
      // Force all lists to start with an empty list rather than no property for
      // the list. This is so that the native JS equality works, since (obviously)
      // {} != {"foo": []} while in the context of messages they should be.
      return instance;
    }

    @Override
    public void copyFrom(MutateDocument message) {
      super.copyFrom((MutateDocumentJsoImpl) message);
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
    public String getDocumentId() {
      return hasProperty(this, keyDocumentId) ? getPropertyAsString(this, keyDocumentId) : null;
    }

    @Override
    public void setDocumentId(String value) {
      setPropertyAsString(this, keyDocumentId, value);
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
    public ProtocolDocumentOperation getDocumentOperation() {
      return hasProperty(this, keyDocumentOperation) ? ((ProtocolDocumentOperationJsoImpl) getPropertyAsObject(this, keyDocumentOperation)) : null;
    }

    @Override
    public void setDocumentOperation(ProtocolDocumentOperation model) {
      setPropertyAsObject(this, keyDocumentOperation, (ProtocolDocumentOperationJsoImpl) model);
    }

    @Override
    public boolean isEqualTo(Object o) {
      if (o instanceof MutateDocumentJsoImpl) {
        return nativeIsEqualTo(o);
      } else if (o instanceof MutateDocument) {
        return MutateDocumentUtil.isEqual(this, (MutateDocument) o);
      } else {
        return false;
      }
    }

  }

  private static final String keyAddParticipant = "1";
  private static final String keyRemoveParticipant = "2";
  private static final String keyMutateDocument = "3";
  private static final String keyNoOp = "4";
  protected ProtocolWaveletOperationJsoImpl() {
  }

  public static ProtocolWaveletOperationJsoImpl create() {
    ProtocolWaveletOperationJsoImpl instance = (ProtocolWaveletOperationJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    return instance;
  }

  @Override
  public void copyFrom(ProtocolWaveletOperation message) {
    super.copyFrom((ProtocolWaveletOperationJsoImpl) message);
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
  public boolean hasAddParticipant() {
    return hasProperty(this, keyAddParticipant);
  }

  @Override
  public void clearAddParticipant() {
    if (hasProperty(this, keyAddParticipant)) {
      deleteProperty(this, keyAddParticipant);
    }
  }

  @Override
  public String getAddParticipant() {
    return hasProperty(this, keyAddParticipant) ? getPropertyAsString(this, keyAddParticipant) : null;
  }

  @Override
  public void setAddParticipant(String value) {
    setPropertyAsString(this, keyAddParticipant, value);
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
  public boolean hasRemoveParticipant() {
    return hasProperty(this, keyRemoveParticipant);
  }

  @Override
  public void clearRemoveParticipant() {
    if (hasProperty(this, keyRemoveParticipant)) {
      deleteProperty(this, keyRemoveParticipant);
    }
  }

  @Override
  public String getRemoveParticipant() {
    return hasProperty(this, keyRemoveParticipant) ? getPropertyAsString(this, keyRemoveParticipant) : null;
  }

  @Override
  public void setRemoveParticipant(String value) {
    setPropertyAsString(this, keyRemoveParticipant, value);
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
  public boolean hasMutateDocument() {
    return hasProperty(this, keyMutateDocument);
  }

  @Override
  public void clearMutateDocument() {
    if (hasProperty(this, keyMutateDocument)) {
      deleteProperty(this, keyMutateDocument);
    }
  }

  @Override
  public MutateDocument getMutateDocument() {
    return hasProperty(this, keyMutateDocument) ? ((MutateDocumentJsoImpl) getPropertyAsObject(this, keyMutateDocument)) : null;
  }

  @Override
  public void setMutateDocument(MutateDocument model) {
    setPropertyAsObject(this, keyMutateDocument, (MutateDocumentJsoImpl) model);
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
  public boolean hasNoOp() {
    return hasProperty(this, keyNoOp);
  }

  @Override
  public void clearNoOp() {
    if (hasProperty(this, keyNoOp)) {
      deleteProperty(this, keyNoOp);
    }
  }

  @Override
  public boolean getNoOp() {
    return hasProperty(this, keyNoOp) ? getPropertyAsBoolean(this, keyNoOp) : false;
  }

  @Override
  public void setNoOp(boolean value) {
    setPropertyAsBoolean(this, keyNoOp, value);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProtocolWaveletOperationJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProtocolWaveletOperation) {
      return ProtocolWaveletOperationUtil.isEqual(this, (ProtocolWaveletOperation) o);
    } else {
      return false;
    }
  }

}