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


package org.waveprotocol.wave.client.scheduler.knobs;

import com.google.gwt.user.client.ui.Widget;
import org.waveprotocol.wave.client.scheduler.Controller;
import org.waveprotocol.wave.client.scheduler.Scheduler.Priority;
import org.waveprotocol.wave.client.scheduler.Scheduler.Schedulable;

import org.waveprotocol.wave.model.util.CollectionUtils;
import org.waveprotocol.wave.model.util.IdentityMap;

/**
 * Presenter of a panel of per-level knobs.
 *
 */
public final class KnobsPresenter {

  /**
   * Controller implementation that presents a panel of per-level controls.
   */
  // Avoid GWT class initialization during JVM tests; client binds real impl elsewhere.
  public static final Controller KNOBS = Controller.NOOP;

  /** Controller for each priority level. */
  private final IdentityMap<Priority, KnobPresenter> knobs = CollectionUtils.createIdentityMap();

  /** View implementation. */
  private final KnobsView view;

  /**
   * Creates a knob controller.
   *
   * @param view  UI component displaying this panel
   */
  KnobsPresenter(KnobsView view) {
    this.view = view;

    buildLevels();

    knobs.get(Priority.INTERNAL_SUPPRESS).disable();
  }

  /**
   * Creates a per-level control for each priority level.
   */
  private void buildLevels() {
    // Create controller for each priority level, add it to this panel, and remember it.
    for (Priority p : Priority.values()) {
      KnobView levelView = view.create(p);
      KnobPresenter c = new KnobPresenter(levelView);
      knobs.put(p, c);
    }
  }

  /**
   * Notifies this presenter that a job has been added at the given priority.
   */
  public void jobAdded(Priority priority, Schedulable job) {
    knobs.get(priority).addJob(job);
  }

  /**
   * Notifies this presenter that a job has been removed at the given priority.
   */
  public void jobRemoved(Priority priority, Schedulable job) {
    knobs.get(priority).removeJob(job);
  }

  /**
   * Tests is a priority level is runnable.
   *
   * @param priority  level
   * @return true if level {@code priority} is enabled.
   */
  public boolean isRunnable(Priority priority) {
    return knobs.get(priority).isEnabled();
  }

  /**
   * @see Controller#isSuppressed(Priority, Schedulable)
   */
  public boolean isSuppressed(Priority priority, Schedulable job) {
    return knobs.get(priority).isSuppressed(job);
  }
}
