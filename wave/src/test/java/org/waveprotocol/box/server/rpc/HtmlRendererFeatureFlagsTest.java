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
package org.waveprotocol.box.server.rpc;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public final class HtmlRendererFeatureFlagsTest {
  @Test
  public void adminPageShowsDomainHintAndPerUserToggleHooks() {
    String html = HtmlRenderer.renderAdminPage("owner@supawave.ai", "supawave.ai", "owner");

    assertTrue(html.contains("e.g. vega (will become vega@supawave.ai)"));
    assertTrue(html.contains("toggleAllowedUser("));
    assertTrue(html.contains("legacyUser = legacyUser.trim();"));
    assertTrue(html.contains("var email = (user.email || '').trim();"));
    assertTrue(html.contains("if (flagEditingName === flag.name && flagForm.style.display !== 'none') {"));
    assertTrue(
        html.contains(
            "document.getElementById('flagCancelBtn').addEventListener('click', function() {\n"
                + "    resetFlagEditingState();\n"
                + "    flagForm.style.display = 'none';\n"
                + "  });"));
    assertTrue(html.contains("var rowFlag = normalizeFlag(flagsData[flagIndex]);"));
    assertTrue(html.contains("var rowUserEmail = rowFlag.allowedUsers[userIndex].email;"));
    assertTrue(html.contains("var payload = buildFlagPayload(rowFlag);"));
    assertTrue(html.contains("var payloadUserFound = false;"));
    assertTrue(html.contains("payload.allowedUsers.push({ email: rowUserEmail, enabled: nextEnabled });"));
    assertTrue(html.contains("var rowFlag = normalizeFlag(flagsData[idx]);"));
    assertTrue(html.contains("var payload = buildFlagPayload(rowFlag);"));
    assertTrue(html.contains("payload.enabled = !rowFlag.enabled;"));
    assertFalse(html.contains("flagsData[idx] ="));
    assertTrue(html.contains("this.checked"));
    assertTrue(html.contains("syncEditingFlag(payload);"));
    assertTrue(html.contains("closeForm: false"));
    assertTrue(html.contains("resetFlagEditingState()"));
    assertTrue(
        html.contains(
            "if (data.error) {\n"
                + "          showToast(data.error, 'error');\n"
                + "          if (options.closeForm === false) { fetchFlags(); }\n"
                + "          return;\n"
                + "        }"));
    assertTrue(
        html.contains(
            "}).catch(function(e){\n"
                + "        showToast('Failed: ' + e.message, 'error');\n"
                + "        if (options.closeForm === false) { fetchFlags(); }\n"
                + "      });"));
    assertTrue(
        html.contains(
            "if (options.closeForm !== false) {\n"
                + "          flagForm.style.display = 'none';\n"
                + "          resetFlagEditingState();\n"
                + "        } else {\n"
                + "          syncEditingFlag(payload);\n"
                + "        }"));
    assertTrue(html.contains("resetFlagEditingState();\n    flagForm.style.display = 'none';"));
    assertTrue(
        html.contains(
            "          resetFlagEditingState();\n"
                + "          flagForm.style.display = 'none';\n"
                + "          showToast('Flag deleted', 'success');\n"
                + "          fetchFlags();"));
    assertTrue(html.contains("flagNameInput.value = flag.name;"));
    assertTrue(html.contains("return legacyUser ? { email: legacyUser, enabled: legacyEnabled } : null;"));
  }
}
