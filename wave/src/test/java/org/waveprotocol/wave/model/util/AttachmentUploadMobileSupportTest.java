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

package org.waveprotocol.wave.model.util;

import junit.framework.TestCase;

public final class AttachmentUploadMobileSupportTest extends TestCase {

  public void testIsImageMimeAcceptsImagePrefix() {
    assertTrue(AttachmentUploadMobileSupport.isImageMime("image/png"));
  }

  public void testIsImageMimeRejectsNullAndNonImageTypes() {
    assertFalse(AttachmentUploadMobileSupport.isImageMime(null));
    assertFalse(AttachmentUploadMobileSupport.isImageMime("application/pdf"));
  }

  public void testShouldRecoverSelectionOnlyWhenAwaitingChooserReturn() {
    assertTrue(AttachmentUploadMobileSupport.shouldRecoverSelection(true, false, 1));
    assertFalse(AttachmentUploadMobileSupport.shouldRecoverSelection(false, false, 1));
    assertFalse(AttachmentUploadMobileSupport.shouldRecoverSelection(true, true, 1));
    assertFalse(AttachmentUploadMobileSupport.shouldRecoverSelection(true, false, 0));
  }
}
