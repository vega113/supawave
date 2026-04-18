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

package org.waveprotocol.wave.concurrencycontrol.channel;

/**
 * Optional browser/client hook for fragment-delivery diagnostics.
 *
 * <p>This keeps the concurrency-control core pure Java while still allowing
 * client wiring to observe fragment batches for dev-only UI indicators.</p>
 */
public interface ViewChannelDebugHook {
  ViewChannelDebugHook NO_OP = new ViewChannelDebugHook() {
    @Override
    public void onRangesReceived(int rangeCount) {
    }
  };

  void onRangesReceived(int rangeCount);
}
