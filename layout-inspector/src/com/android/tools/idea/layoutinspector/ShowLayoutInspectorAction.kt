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
package com.android.tools.idea.layoutinspector

import com.intellij.facet.ProjectFacetManager
import com.intellij.facet.ui.FacetDependentToolWindow
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import icons.StudioIcons
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.util.AndroidBundle

internal class ShowLayoutInspectorAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabled = project != null && ProjectFacetManager.getInstance(project).hasFacets(AndroidFacet.ID)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    for (windowEp in FacetDependentToolWindow.EXTENSION_POINT_NAME.extensionList) {
      if (windowEp.id == LAYOUT_INSPECTOR_TOOL_WINDOW_ID) {
        ToolWindowManager.getInstance(project).getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)!!.activate(null)
        break
      }
    }
  }
}