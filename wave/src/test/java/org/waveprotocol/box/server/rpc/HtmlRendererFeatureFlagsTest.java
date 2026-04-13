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
    String html = HtmlRenderer.renderAdminPage(
        "owner@supawave.ai", "supawave.ai", "/wave", "owner");

    assertTrue(html.contains("e.g. vega (will become vega@supawave.ai)"));
    assertTrue(html.contains("toggleAllowedUser("));
    assertTrue(html.contains("legacyUser = normalizeAllowedUserEmail(legacyUser.trim());"));
    assertTrue(html.contains("var email = normalizeAllowedUserEmail((user.email || '').trim());"));
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
    assertFalse(html.contains("flagsData[flagIndex] ="));
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
    assertFalse(
        html.contains(
            "if (options.closeForm === false) {\n"
                + "          resetFlagEditingState();\n"
                + "        }"));
    assertTrue(html.contains("resetFlagEditingState();\n    flagForm.style.display = 'none';"));
    assertTrue(
        html.contains(
            "if (flagEditingName === name) {\n"
                + "            resetFlagEditingState();\n"
                + "            flagForm.style.display = 'none';\n"
                + "          }\n"
                + "          showToast('Flag deleted', 'success');\n"
                + "          fetchFlags();"));
    assertTrue(html.contains("flagNameInput.value = flag.name;"));
    assertTrue(html.contains("return legacyUser ? { email: legacyUser, enabled: legacyEnabled } : null;"));
    assertTrue(html.contains("var entry = normalizeAllowedUserEntry(parts[i]);"));
    assertTrue(html.contains("href=\"/wave/admin\""));
    assertFalse(html.contains("data-tab=\"analytics\">Analytics</button>"));
    assertFalse(html.contains("id=\"panel-analytics\""));
    assertFalse(
        html.contains(
            "if (tab.dataset.tab === 'analytics') { loadAnalyticsHistory(analyticsActiveWindow); loadAnalyticsStatus(); }"));
    assertTrue(html.contains("if (tab.dataset.tab === 'ops') { loadOpsStatus(); }"));
    assertFalse(html.contains("var analyticsActiveWindow = '24h';"));
    assertFalse(
        html.contains("fetch('/admin/api/analytics/history?window=' + encodeURIComponent(win))"));
    assertFalse(
        html.contains(
            "chartWaves = createWaveChart('chartWaves', 'Waves Created', '#0077b6', 'bar');"));
    assertFalse(html.contains("var href = (_ctx || '') + '/waveref/' + encodeURIComponent(id);"));
    assertFalse(html.contains("fetch('/admin/api/analytics/status')"));
    assertFalse(
        html.contains(
            "el = document.getElementById('histActiveUsers'); if (el) el.textContent = (t.activeUsers || 0).toLocaleString();"));
    assertTrue(html.contains("document.querySelectorAll('.ops-subtab').forEach(function(btn) {"));
    assertTrue(
        html.contains(
            "document.getElementById('opsIncrementalAvg').textContent = si.incrementalAvgMs != null ? si.incrementalAvgMs.toFixed(1) + ' ms' : '\\u2014';"));
    assertTrue(
        html.contains(
            "document.getElementById('opsIncrementalCount').textContent = si.incrementalIndexCount != null ? fmtNum(si.incrementalIndexCount) : '\\u2014';"));
    assertTrue(
        html.contains(
            "document.getElementById('opsQueryAvg').textContent = si.queryAvgMs != null ? si.queryAvgMs.toFixed(1) + ' ms' : '\\u2014';"));
  }
}
