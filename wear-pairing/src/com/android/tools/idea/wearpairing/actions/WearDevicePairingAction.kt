/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.wearpairing.actions

import com.android.annotations.concurrency.UiThread
import com.android.tools.adtui.common.ColoredIconGenerator.generateWhiteIcon
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.wearpairing.WearDevicePairingWizard
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.ExperimentalUI.isNewUI
import icons.StudioIcons

/**
 * The action to show the [WearDevicePairingWizard] dialog.
 */
class WearDevicePairingAction : AnAction() {
  @UiThread
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.apply {
      isEnabledAndVisible = e.project != null && !StudioFlags.PAIRED_DEVICES_TAB_ENABLED.get()
      if (isEnabled && selectedIcon == null && !isNewUI()) {
        selectedIcon = generateWhiteIcon(StudioIcons.LayoutEditor.Toolbar.INSERT_HORIZ_CHAIN)
      }
    }
  }

  @UiThread
  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    WearDevicePairingWizard().show(project, null)
  }

  companion object {
    const val ID = "Android.WearDevicePairing"
  }
}
