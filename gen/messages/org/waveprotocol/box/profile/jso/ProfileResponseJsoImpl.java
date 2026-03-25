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

package org.waveprotocol.box.profile.jso;

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
import org.waveprotocol.box.profile.ProfileResponse.FetchedProfile;
import org.waveprotocol.box.profile.jso.ProfileResponseJsoImpl.FetchedProfileJsoImpl;

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
import org.waveprotocol.box.profile.ProfileResponse;
import org.waveprotocol.box.profile.ProfileResponseUtil;

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
import org.waveprotocol.box.profile.ProfileResponse.FetchedProfile;
import org.waveprotocol.box.profile.ProfileResponseUtil.FetchedProfileUtil;
import org.waveprotocol.wave.communication.Blob;
import org.waveprotocol.wave.communication.ProtoEnums;
import org.waveprotocol.wave.communication.gwt.*;
import org.waveprotocol.wave.communication.json.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Client implementation of ProfileResponse backed by a GWT JavaScriptObject.
 *
 * Generated from org/waveprotocol/box/profile/profiles.proto. Do not edit.
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
public final class ProfileResponseJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
    implements ProfileResponse {

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
  public static final class FetchedProfileJsoImpl extends org.waveprotocol.wave.communication.gwt.JsonMessage
      implements FetchedProfile {
    private static final String keyAddress = "1";
    private static final String keyName = "2";
    private static final String keyImageUrl = "3";
    private static final String keyProfileUrl = "4";
    protected FetchedProfileJsoImpl() {
    }

    public static FetchedProfileJsoImpl create() {
      FetchedProfileJsoImpl instance = (FetchedProfileJsoImpl) JsonMessage.createJsonMessage();
      // Force all lists to start with an empty list rather than no property for
      // the list. This is so that the native JS equality works, since (obviously)
      // {} != {"foo": []} while in the context of messages they should be.
      return instance;
    }

    @Override
    public void copyFrom(FetchedProfile message) {
      super.copyFrom((FetchedProfileJsoImpl) message);
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
    public String getAddress() {
      return hasProperty(this, keyAddress) ? getPropertyAsString(this, keyAddress) : null;
    }

    @Override
    public void setAddress(String value) {
      setPropertyAsString(this, keyAddress, value);
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
    public String getName() {
      return hasProperty(this, keyName) ? getPropertyAsString(this, keyName) : null;
    }

    @Override
    public void setName(String value) {
      setPropertyAsString(this, keyName, value);
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
    public String getImageUrl() {
      return hasProperty(this, keyImageUrl) ? getPropertyAsString(this, keyImageUrl) : null;
    }

    @Override
    public void setImageUrl(String value) {
      setPropertyAsString(this, keyImageUrl, value);
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
    public boolean hasProfileUrl() {
      return hasProperty(this, keyProfileUrl);
    }

    @Override
    public void clearProfileUrl() {
      if (hasProperty(this, keyProfileUrl)) {
        deleteProperty(this, keyProfileUrl);
      }
    }

    @Override
    public String getProfileUrl() {
      return hasProperty(this, keyProfileUrl) ? getPropertyAsString(this, keyProfileUrl) : null;
    }

    @Override
    public void setProfileUrl(String value) {
      setPropertyAsString(this, keyProfileUrl, value);
    }

    @Override
    public boolean isEqualTo(Object o) {
      if (o instanceof FetchedProfileJsoImpl) {
        return nativeIsEqualTo(o);
      } else if (o instanceof FetchedProfile) {
        return FetchedProfileUtil.isEqual(this, (FetchedProfile) o);
      } else {
        return false;
      }
    }

  }

  private static final String keyProfiles = "1";
  protected ProfileResponseJsoImpl() {
  }

  public static ProfileResponseJsoImpl create() {
    ProfileResponseJsoImpl instance = (ProfileResponseJsoImpl) JsonMessage.createJsonMessage();
    // Force all lists to start with an empty list rather than no property for
    // the list. This is so that the native JS equality works, since (obviously)
    // {} != {"foo": []} while in the context of messages they should be.
    instance.clearProfiles();
    return instance;
  }

  @Override
  public void copyFrom(ProfileResponse message) {
    super.copyFrom((ProfileResponseJsoImpl) message);
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
  public List<FetchedProfileJsoImpl> getProfiles() {
    initArray(this, keyProfiles);
    List<FetchedProfileJsoImpl> list = new ArrayList<FetchedProfileJsoImpl>();
    for (int i = 0; i < getProfilesSize(); i++) {
      list.add(getProfiles(i));
    }
    return list;
  }

  @Override
  public void addAllProfiles(List<? extends FetchedProfile> models) {
    for (FetchedProfile model : models) {
      addProfiles(model);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
      public FetchedProfileJsoImpl getProfiles(int n) {
    initArray(this, keyProfiles);
    JsArray<FetchedProfileJsoImpl> array = getPropertyAsObject(this, keyProfiles).cast();
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    if (array.length() <= n) throw new IllegalArgumentException("index " + n + ">= array length " + array.length());
    return array.get(n);
  }

  @Override
  @SuppressWarnings("unchecked")
      public void setProfiles(int n, FetchedProfile model) {
    if (n < 0) throw new IllegalArgumentException("index " + n + " < 0");
    initArray(this, keyProfiles);
    ((JsArray<FetchedProfileJsoImpl>) getPropertyAsObject(this, keyProfiles)).set(n, (FetchedProfileJsoImpl) model);
  }

  @Override
  public int getProfilesSize() {
    return hasProperty(this, keyProfiles) ? ((JsArray<?>) getPropertyAsObject(this, keyProfiles)).length() : 0;
  }

  @Override
  @SuppressWarnings("unchecked")
      public void addProfiles(FetchedProfile model) {
    initArray(this, keyProfiles);
    ((JsArray<FetchedProfileJsoImpl>) getPropertyAsObject(this, keyProfiles)).push((FetchedProfileJsoImpl) model);
  }

  @Override
  public void clearProfiles() {
    clearArray(this, keyProfiles);
  }

  @Override
  public boolean isEqualTo(Object o) {
    if (o instanceof ProfileResponseJsoImpl) {
      return nativeIsEqualTo(o);
    } else if (o instanceof ProfileResponse) {
      return ProfileResponseUtil.isEqual(this, (ProfileResponse) o);
    } else {
      return false;
    }
  }

}