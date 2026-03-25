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

package org.waveprotocol.wave.concurrencycontrol.jso;

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
import org.waveprotocol.wave.concurrencycontrol.ResponseStatus;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.concurrencycontrol.jso.ResponseStatusJsoImpl;
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
import org.waveprotocol.wave.concurrencycontrol.SubmitDeltaResponse;
import org.waveprotocol.wave.concurrencycontrol.SubmitDeltaResponseUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of SubmitDeltaResponse backed by a GWT JavaScriptObject.
 *
 * Generated from org/waveprotocol/wave/concurrencycontrol/clientserver.proto. Do not edit.
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
public final class SubmitDeltaResponseJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements SubmitDeltaResponse {
  private static final String keyStatus = "1";
  private static final String keyOperationsApplied = "2";
  private static final String keyHashedVersionAfterApplication = "3";
  private static final String keyTimestampAfterApplication = "4";
  protected SubmitDeltaResponseJsoImpl() {
  }

  public static SubmitDeltaResponseJsoImpl create() {
    SubmitDeltaResponseJsoImpl instance = (SubmitDeltaResponseJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    return instance;
  }

  @Override
  public void copyFrom(SubmitDeltaResponse message) {
    super.copyFrom((SubmitDeltaResponseJsoImpl) message);
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
  public ResponseStatus getStatus() {
    return hasProperty(this, keyStatus) ? ((ResponseStatusJsoImpl) getPropertyAsObject(this, keyStatus)) : null;
  }

  @Override
  public void setStatus(ResponseStatus model) {
    setPropertyAsObject(this, keyStatus, (ResponseStatusJsoImpl) model);
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
  public int getOperationsApplied() {
    return hasProperty(this, keyOperationsApplied) ? getPropertyAsInteger(this, keyOperationsApplied) : 0;
  }

  @Override
  public void setOperationsApplied(int value) {
    setPropertyAsInteger(this, keyOperationsApplied, value);
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
  public boolean hasHashedVersionAfterApplication() {
    return hasProperty(this, keyHashedVersionAfterApplication);
  }

  @Override
  public void clearHashedVersionAfterApplication() {
    if (hasProperty(this, keyHashedVersionAfterApplication)) {
      deleteProperty(this, keyHashedVersionAfterApplication);
    }
  }

  @Override
  public ProtocolHashedVersion getHashedVersionAfterApplication() {
    return hasProperty(this, keyHashedVersionAfterApplication) ? ((ProtocolHashedVersionJsoImpl) getPropertyAsObject(this, keyHashedVersionAfterApplication)) : null;
  }

  @Override
  public void setHashedVersionAfterApplication(ProtocolHashedVersion model) {
    setPropertyAsObject(this, keyHashedVersionAfterApplication, (ProtocolHashedVersionJsoImpl) model);
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
  public boolean hasTimestampAfterApplication() {
    return hasProperty(this, keyTimestampAfterApplication);
  }

  @Override
  public void clearTimestampAfterApplication() {
    if (hasProperty(this, keyTimestampAfterApplication)) {
      deleteProperty(this, keyTimestampAfterApplication);
    }
  }

  @Override
  public long getTimestampAfterApplication() {
    return hasProperty(this, keyTimestampAfterApplication) ? getPropertyAsLong(this, keyTimestampAfterApplication) : 0L;
  }

  @Override
  public void setTimestampAfterApplication(long value) {
    setPropertyAsLong(this, keyTimestampAfterApplication, value);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof SubmitDeltaResponseJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof SubmitDeltaResponse) {
      return SubmitDeltaResponseUtil.isEqual(this, (SubmitDeltaResponse) o);
    } else {
      return false;
    }
  }

}