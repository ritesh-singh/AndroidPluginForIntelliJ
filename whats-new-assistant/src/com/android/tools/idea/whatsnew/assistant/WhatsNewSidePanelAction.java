/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant;

import com.android.tools.idea.assistant.AssistantBundleCreator;
import com.android.tools.idea.assistant.OpenAssistSidePanelAction;
import com.intellij.ide.BrowserUtil;
import com.intellij.ide.actions.WhatsNewAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCloseListener;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.android.tools.idea.assistant.AssistantToolWindowService.TOOL_WINDOW_TITLE;

public class WhatsNewSidePanelAction extends OpenAssistSidePanelAction {
  @NotNull
  private static WhatsNewAction action = new WhatsNewAction();

  @Override
  public void update(@NotNull AnActionEvent e) {
    // Project being null can happen when Studio first starts and doesn't have window focus
    Presentation presentation = e.getPresentation();
    if (e.getProject() == null) {
      presentation.setEnabled(false);
    }
    else if (!presentation.isEnabled()) {
      presentation.setEnabled(true);
    }

    action.update(e);
    presentation.setDescription(AndroidBundle.message("whatsnew.action.custom.description",
                                                      ApplicationNamesInfo.getInstance().getFullProductName()));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    openWhatsNewSidePanel(Objects.requireNonNull(event.getProject()), false);
  }

  public void openWhatsNewSidePanel(@NotNull Project project, boolean isAutoOpened) {
    WhatsNewBundleCreator bundleCreator = AssistantBundleCreator.EP_NAME.findExtension(WhatsNewBundleCreator.class);
    if (bundleCreator == null || !bundleCreator.shouldShowWhatsNew()) {
      BrowserUtil.browse(ApplicationInfoEx.getInstanceEx().getWhatsNewUrl());
      return;
    }

    WhatsNewToolWindowListener.fireOpenEvent(project, isAutoOpened);
    openWindow(WhatsNewBundleCreator.BUNDLE_ID, project);
  }

  public static class WhatsNewToolWindowListener implements ToolWindowManagerListener {
    @NotNull private final Project myProject;
    private boolean isOpen;

    public WhatsNewToolWindowListener(@NotNull Project project) {
      myProject = project;
      isOpen = true; // Start off as opened so we don't fire an extra opened event

      // Need an additional listener for project close, because the below invokeLater isn't fired in time before closing
      project.getMessageBus().connect().subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
        @Override
        public void projectClosed(@NotNull Project project) {
          if (!project.equals(myProject)) {
            return;
          }
          if (isOpen) {
            fireClosedEvent(myProject);
            isOpen = false;
          }
        }
      });
    }

    @Override
    public void toolWindowUnregistered(@NotNull String id, @NotNull ToolWindow toolWindow) {
      if (id.equals(TOOL_WINDOW_TITLE)) {
        WhatsNewMetricsTracker.getInstance().clearDataFor(myProject);
      }
    }

    /**
     * Fire WNA metrics and update the actual state after a state change is received.
     * The logic is wrapped in invokeLater because dragging and dropping the StripeButton temporarily
     * hides and then shows the window. Otherwise, the handler would think the window was closed,
     * even though it was only dragged.
     */
    @Override
    public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (myProject.isDisposed()) {
          return;
        }

        ToolWindow window = toolWindowManager.getToolWindow(TOOL_WINDOW_TITLE);
        if (window == null) {
          return;
        }
        if (!WhatsNewBundleCreator.BUNDLE_ID.equals(window.getHelpId())) {
          return;
        }
        if (isOpen && !window.isVisible()) {
          fireClosedEvent(myProject);
          isOpen = false;
        }
        else if (!isOpen && window.isVisible()) {
          // TODO b/139709466: Cannot detect WNA window not having a scrollbar if it is reopened from side tab
          fireOpenEvent(myProject, false);
          isOpen = true;
        }
      });
    }

    private static void fireOpenEvent(@NotNull Project project, boolean isAutoOpened) {
      WhatsNewMetricsTracker.getInstance().open(project, isAutoOpened);
    }

    private static void fireClosedEvent(@NotNull Project project) {
      WhatsNewMetricsTracker.getInstance().close(project);
    }
  }
}
