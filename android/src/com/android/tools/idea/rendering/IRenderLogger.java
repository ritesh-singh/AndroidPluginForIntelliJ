/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.ILayoutLog;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for logger that records problems it encounters and offers them as
 * a single summary at the end.
 */
public interface IRenderLogger extends ILayoutLog {
  /** Empty logger producing no side effects. */
  IRenderLogger NULL_LOGGER = new IRenderLogger() {
    private final HtmlLinkManager myLinkManager = HtmlLinkManager.NOOP_LINK_MANAGER;

    @Override
    public void addMessage(@NotNull RenderProblem message) {
    }

    @Override
    public void addBrokenClass(@NotNull String className, @NotNull Throwable exception) {
    }

    @Override
    public void addMissingClass(@NotNull String className) {
    }

    @Override
    public void setHasLoadedClasses() {
    }

    @Override
    public void setResourceClass(@NotNull String resourceClass) {
    }

    @Override
    public void setMissingResourceClass() {
    }

    @Override
    @NotNull
    public HtmlLinkManager getLinkManager() {
      return myLinkManager;
    }
  };

  void addMessage(@NotNull RenderProblem message);

  void addBrokenClass(@NotNull String className, @NotNull Throwable exception);

  void addMissingClass(@NotNull String className);

  void setHasLoadedClasses();

  void setResourceClass(@NotNull String resourceClass);

  void setMissingResourceClass();

  @NotNull
  HtmlLinkManager getLinkManager();
}
