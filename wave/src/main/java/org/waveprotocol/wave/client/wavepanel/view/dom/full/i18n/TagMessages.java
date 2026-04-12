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

package org.waveprotocol.wave.client.wavepanel.view.dom.full.i18n;

import com.google.gwt.i18n.client.Messages;

/**
 * i18n messages for the tags view builder.
 *
 * Ported from Wiab.pro.
 *
 * @author akaplanov@gmail.com (Andrew Kaplanov)
 */
public interface TagMessages extends Messages {
  @DefaultMessage("Tags:")
  String tags();

  @DefaultMessage("less")
  String less();

  @DefaultMessage("more")
  String more();

  @DefaultMessage("Add tag")
  String addTagHint();

  @DefaultMessage("Enter tag name...")
  String tagInputPlaceholder();

  @DefaultMessage("Separate multiple tags with commas")
  String tagInputHint();

  @DefaultMessage("Add tags")
  String confirmAddTagHint();

  @DefaultMessage("Cancel tag entry")
  String cancelAddTagHint();
}
