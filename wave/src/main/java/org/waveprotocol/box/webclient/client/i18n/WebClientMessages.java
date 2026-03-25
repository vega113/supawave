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

package org.waveprotocol.box.webclient.client.i18n;

import com.google.gwt.i18n.client.Messages;
import com.google.gwt.i18n.client.Messages.DefaultMessage;

/**
 *
 * @author akaplanov (Andrew Kaplanov)
 */
public interface WebClientMessages extends Messages {
  @DefaultMessage("A turbulence detected!")
  String turbulenceDetected();

  @DefaultMessage("Please save your last changes to somewhere and reload the wave.")
  String saveAndReloadWave();

  @DefaultMessage("Online")
  String online();

  @DefaultMessage("Offline")
  String offline();

  @DefaultMessage("Connecting...")
  String connecting();

  @DefaultMessage("Sign out")
  String signout();

  // ---- Turbulence banner (ocean-themed redesign) ----

  @DefaultMessage("Riding some waves...")
  String turbulenceTitle();

  @DefaultMessage("Connection lost")
  String turbulenceElapsed();

  @DefaultMessage("Possible reasons")
  String turbulenceReasonsHeading();

  @DefaultMessage("Your internet connection may be temporarily interrupted")
  String turbulenceReasonInternet();

  @DefaultMessage("The server might be restarting (usually takes ~30 seconds)")
  String turbulenceReasonRestart();

  @DefaultMessage("A new version may be deploying")
  String turbulenceReasonDeploy();

  @DefaultMessage("What you can do")
  String turbulenceActionsHeading();

  @DefaultMessage("Wait a moment \u2014 we will automatically reconnect")
  String turbulenceActionWait();

  @DefaultMessage("If this persists for more than 1 minute, try refreshing the page")
  String turbulenceActionRefresh();

  @DefaultMessage("Check your internet connection")
  String turbulenceActionCheck();

  @DefaultMessage("Attempting to reconnect...")
  String turbulenceReconnecting();

  @DefaultMessage("Back online!")
  String turbulenceBackOnline();
}
