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

package org.waveprotocol.box.attachment.jso;

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
import org.waveprotocol.box.attachment.AttachmentMetadata;
import org.waveprotocol.box.attachment.jso.AttachmentMetadataJsoImpl;

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
import org.waveprotocol.box.attachment.AttachmentsResponse;
import org.waveprotocol.box.attachment.AttachmentsResponseUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of AttachmentsResponse backed by a GWT JavaScriptObject.
 *
 * Generated from org/waveprotocol/box/attachment/attachment.proto. Do not edit.
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
public final class AttachmentsResponseJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements AttachmentsResponse {
  private static final String keyAttachment = "1";
  protected AttachmentsResponseJsoImpl() {
  }

  public static AttachmentsResponseJsoImpl create() {
    AttachmentsResponseJsoImpl instance = (AttachmentsResponseJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearAttachment();
    return instance;
  }

  @Override
  public void copyFrom(AttachmentsResponse message) {
    super.copyFrom((AttachmentsResponseJsoImpl) message);
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
  public List<AttachmentMetadataJsoImpl> getAttachment() {
    initArray(this, keyAttachment);
    List<AttachmentMetadataJsoImpl> list = new ArrayList<AttachmentMetadataJsoImpl>();
    for (int i = 0; i < getAttachmentSize(); i++) {
      list.add(getAttachment(i));
    }
    return list;
  }

  @Override
  public void addAllAttachment(List<? extends AttachmentMetadata> models) {
    for (AttachmentMetadata model : models) {
      addAttachment(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public AttachmentMetadataJsoImpl getAttachment(int n) {
    initArray(this, keyAttachment);
    JsArray<AttachmentMetadataJsoImpl> array = getPropertyAsObject(this, keyAttachment).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setAttachment(int n, AttachmentMetadata model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyAttachment);
    ((JsArray<AttachmentMetadataJsoImpl>) getPropertyAsObject(this, keyAttachment)).set(n, (AttachmentMetadataJsoImpl) model);
  }

  @Override
  public int getAttachmentSize() {
    return hasProperty(this, keyAttachment) ? ((JsArray<?>) getPropertyAsObject(this, keyAttachment)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addAttachment(AttachmentMetadata model) {
    initArray(this, keyAttachment);
    ((JsArray<AttachmentMetadataJsoImpl>) getPropertyAsObject(this, keyAttachment)).push((AttachmentMetadataJsoImpl) model);
  }

  @Override
  public void clearAttachment() {
    clearArray(this, keyAttachment);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof AttachmentsResponseJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof AttachmentsResponse) {
      return AttachmentsResponseUtil.isEqual(this, (AttachmentsResponse) o);
    } else {
      return false;
    }
  }

}