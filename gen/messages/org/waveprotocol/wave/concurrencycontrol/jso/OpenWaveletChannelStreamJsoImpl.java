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
import org.waveprotocol.wave.concurrencycontrol.WaveletUpdate;
import org.waveprotocol.wave.federation.ProtocolHashedVersion;
import org.waveprotocol.wave.concurrencycontrol.WaveletChannelTerminator;
import org.waveprotocol.wave.concurrencycontrol.jso.WaveletUpdateJsoImpl;
import org.waveprotocol.wave.federation.jso.ProtocolHashedVersionJsoImpl;
import org.waveprotocol.wave.concurrencycontrol.jso.WaveletChannelTerminatorJsoImpl;

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
import org.waveprotocol.wave.concurrencycontrol.OpenWaveletChannelStream;
import org.waveprotocol.wave.concurrencycontrol.OpenWaveletChannelStreamUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of OpenWaveletChannelStream backed by a GWT JavaScriptObject.
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
public final class OpenWaveletChannelStreamJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements OpenWaveletChannelStream {
  private static final String keyChannelId = "1";
  private static final String keyDelta = "2";
  private static final String keyCommitVersion = "3";
  private static final String keyTerminator = "4";
  protected OpenWaveletChannelStreamJsoImpl() {
  }

  public static OpenWaveletChannelStreamJsoImpl create() {
    OpenWaveletChannelStreamJsoImpl instance = (OpenWaveletChannelStreamJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    return instance;
  }

  @Override
  public void copyFrom(OpenWaveletChannelStream message) {
    super.copyFrom((OpenWaveletChannelStreamJsoImpl) message);
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
  public boolean hasChannelId() {
    return hasProperty(this, keyChannelId);
  }

  @Override
  public void clearChannelId() {
    if (hasProperty(this, keyChannelId)) {
      deleteProperty(this, keyChannelId);
    }
  }

  @Override
  public String getChannelId() {
    return hasProperty(this, keyChannelId) ? getPropertyAsString(this, keyChannelId) : null;
  }

  @Override
  public void setChannelId(String value) {
    setPropertyAsString(this, keyChannelId, value);
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
  public boolean hasDelta() {
    return hasProperty(this, keyDelta);
  }

  @Override
  public void clearDelta() {
    if (hasProperty(this, keyDelta)) {
      deleteProperty(this, keyDelta);
    }
  }

  @Override
  public WaveletUpdate getDelta() {
    return hasProperty(this, keyDelta) ? ((WaveletUpdateJsoImpl) getPropertyAsObject(this, keyDelta)) : null;
  }

  @Override
  public void setDelta(WaveletUpdate model) {
    setPropertyAsObject(this, keyDelta, (WaveletUpdateJsoImpl) model);
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
  public boolean hasCommitVersion() {
    return hasProperty(this, keyCommitVersion);
  }

  @Override
  public void clearCommitVersion() {
    if (hasProperty(this, keyCommitVersion)) {
      deleteProperty(this, keyCommitVersion);
    }
  }

  @Override
  public ProtocolHashedVersion getCommitVersion() {
    return hasProperty(this, keyCommitVersion) ? ((ProtocolHashedVersionJsoImpl) getPropertyAsObject(this, keyCommitVersion)) : null;
  }

  @Override
  public void setCommitVersion(ProtocolHashedVersion model) {
    setPropertyAsObject(this, keyCommitVersion, (ProtocolHashedVersionJsoImpl) model);
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
  public boolean hasTerminator() {
    return hasProperty(this, keyTerminator);
  }

  @Override
  public void clearTerminator() {
    if (hasProperty(this, keyTerminator)) {
      deleteProperty(this, keyTerminator);
    }
  }

  @Override
  public WaveletChannelTerminator getTerminator() {
    return hasProperty(this, keyTerminator) ? ((WaveletChannelTerminatorJsoImpl) getPropertyAsObject(this, keyTerminator)) : null;
  }

  @Override
  public void setTerminator(WaveletChannelTerminator model) {
    setPropertyAsObject(this, keyTerminator, (WaveletChannelTerminatorJsoImpl) model);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof OpenWaveletChannelStreamJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof OpenWaveletChannelStream) {
      return OpenWaveletChannelStreamUtil.isEqual(this, (OpenWaveletChannelStream) o);
    } else {
      return false;
    }
  }

}