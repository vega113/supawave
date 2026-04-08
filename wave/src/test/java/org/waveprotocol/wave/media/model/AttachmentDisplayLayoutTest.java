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

package org.waveprotocol.wave.media.model;

import junit.framework.TestCase;

public class AttachmentDisplayLayoutTest extends TestCase {

  public void testDecideUsesThumbnailForSmallImages() {
    AttachmentDisplayLayout.Decision decision =
        AttachmentDisplayLayout.decide("small", false, true);

    assertEquals(AttachmentDisplayLayout.SourceKind.THUMBNAIL, decision.getSourceKind());
    assertFalse(decision.hideChrome());
  }

  public void testDecideUsesAttachmentForInlineImageModes() {
    AttachmentDisplayLayout.Decision medium =
        AttachmentDisplayLayout.decide("medium", false, true);
    AttachmentDisplayLayout.Decision large =
        AttachmentDisplayLayout.decide("large", false, true);
    AttachmentDisplayLayout.Decision full =
        AttachmentDisplayLayout.decide(null, true, true);

    assertEquals(AttachmentDisplayLayout.SourceKind.ATTACHMENT, medium.getSourceKind());
    assertEquals(AttachmentDisplayLayout.SourceKind.ATTACHMENT, large.getSourceKind());
    assertEquals(AttachmentDisplayLayout.SourceKind.ATTACHMENT, full.getSourceKind());
    assertTrue(medium.hideChrome());
    assertTrue(large.hideChrome());
    assertTrue(full.hideChrome());
  }

  public void testDecideKeepsNonImageAttachmentsOnCardPath() {
    AttachmentDisplayLayout.Decision decision =
        AttachmentDisplayLayout.decide("large", false, false);

    assertEquals(AttachmentDisplayLayout.SourceKind.THUMBNAIL, decision.getSourceKind());
    assertFalse(decision.hideChrome());
  }

  public void testScaleToFitPreservesAspectRatioForConfiguredSizes() {
    assertEquals(new AttachmentDisplayLayout.Size(120, 80),
        AttachmentDisplayLayout.scaleToFit(1200, 800, "small", false));
    assertEquals(new AttachmentDisplayLayout.Size(300, 200),
        AttachmentDisplayLayout.scaleToFit(1200, 800, "medium", false));
    assertEquals(new AttachmentDisplayLayout.Size(600, 400),
        AttachmentDisplayLayout.scaleToFit(1200, 800, "large", false));
  }

  public void testScaleToFitFallsBackToModeBoxWhenDimensionsUnknown() {
    assertEquals(new AttachmentDisplayLayout.Size(300, 200),
        AttachmentDisplayLayout.scaleToFit(0, 0, "medium", false));
  }

  public void testScaleToFitReturnsOriginalSizeForLegacyFullMode() {
    assertEquals(new AttachmentDisplayLayout.Size(1200, 800),
        AttachmentDisplayLayout.scaleToFit(1200, 800, "small", true));
  }
}
