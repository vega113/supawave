/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.waveprotocol.box.webclient.folder;

import com.google.gwt.http.client.URL;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class to build folder operation URLs.
 *
 * Ported from Wiab.pro.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public final class FolderOperationBuilderImpl implements FolderOperationBuilder {

  static class Parameter {

    public final String name;
    public final String value;

    public Parameter(String name, String value) {
      this.name = name;
      this.value = value;
    }
  }

  private final List<Parameter> parameters = new ArrayList<Parameter>();

  public FolderOperationBuilderImpl() {
  }

  @Override
  public FolderOperationBuilder addParameter(String name, String value) {
    parameters.add(new Parameter(name, value));
    return this;
  }

  @Override
  public String getUrl() {
    StringBuilder sb = new StringBuilder();
    sb.append(FOLDER_OPERATION_URL_BASE);
    sb.append("/?");
    for (int i = 0; i < parameters.size(); i++) {
      if (i != 0) {
        sb.append("&");
      }
      Parameter param = parameters.get(i);
      sb.append(param.name).append("=").append(URL.encodeQueryString(param.value));
    }
    return sb.toString();
  }
}
