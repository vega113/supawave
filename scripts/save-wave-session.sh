#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
set -euo pipefail

cat <<'EOF'
Save your Wave session cookie to ~/.wave-session:

1. Sign in to Wave in your browser.
2. Open DevTools.
3. Open the Storage/Application tab.
4. Select Cookies for https://supawave.ai.
   Use http://localhost:9898 instead if you plan to run scripts/feature-flag.sh --local.
5. Copy the full Cookie header for the signed-in page.
   The script accepts either:
   - the raw Cookie header copied from DevTools, or
   - plain NAME=value pairs separated by semicolons.
   Include both JSESSIONID and wave-session-jwt.
   Example:
   Cookie: JSESSIONID=abc123; wave-session-jwt=eyJhbGciOi...
6. Save it locally:

   printf '%s\n' 'Cookie: JSESSIONID=abc123; wave-session-jwt=eyJhbGciOi...' > ~/.wave-session
   chmod 600 ~/.wave-session

7. Verify the file contains the current signed-in cookies:

   cat ~/.wave-session

If scripts/feature-flag.sh says the stored wave-session-jwt is expired or invalid,
sign in again and refresh ~/.wave-session from the latest browser cookies.

Then you can manage flags with commands like:

  scripts/feature-flag.sh list
  scripts/feature-flag.sh set demo-flag 'Demo flag' --allowed alice@example.com
  scripts/feature-flag.sh enable demo-flag
EOF
