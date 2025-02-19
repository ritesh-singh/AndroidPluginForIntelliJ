/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.adtui.PANNABLE_KEY
import com.android.tools.adtui.Pannable
import com.android.tools.adtui.stdui.ActionData
import com.android.tools.adtui.stdui.UrlData
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.actions.DESIGN_SURFACE
import com.android.tools.idea.common.editor.ActionsToolbar
import com.android.tools.idea.common.error.IssuePanelSplitter
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.GuiInputHandler
import com.android.tools.idea.compose.preview.ComposePreviewBundle.message
import com.android.tools.idea.common.surface.handleLayoutlibNativeCrash
import com.android.tools.idea.editors.build.ProjectBuildStatusManager
import com.android.tools.idea.editors.build.ProjectStatus
import com.android.tools.idea.editors.notifications.NotificationPanel
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.projectsystem.requestBuild
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.ui.EditorNotifications
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Point
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.LayoutFocusTraversalPolicy

private const val ISSUE_SPLITTER_DIVIDER_WIDTH_PX = 3
private const val COMPOSE_PREVIEW_DOC_URL = "https://d.android.com/jetpack/compose/preview"

/** Interface that isolates the view of the Compose view so it can be replaced for testing. */
interface ComposePreviewView {

  val mainSurface: NlDesignSurface

  /**
   * Returns the [JComponent] containing this [ComposePreviewView] that can be used to embed it
   * other panels.
   */
  val component: JComponent

  /**
   * Allows replacing the bottom panel in the [ComposePreviewView]. Used to display the animations
   * component.
   */
  var bottomPanel: JComponent?

  /**
   * Sets whether the panel has content to display. If it does not, it will display an overlay with
   * a message for the user.
   */
  var hasContent: Boolean

  /** True if the view is displaying an overlay with a message. */
  val isMessageBeingDisplayed: Boolean

  /** If true, the contents have been at least rendered once. */
  var hasRendered: Boolean

  /** Method called to force an update on the notifications for the given [FileEditor]. */
  fun updateNotifications(parentEditor: FileEditor)

  /**
   * Updates the surface visibility and displays the content or an error message depending on the
   * build state. This method is called after certain updates like a build or a preview refresh has
   * happened. Calling this method will also update the FileEditor notifications.
   */
  fun updateVisibilityAndNotifications()

  /** If the content is not already visible it shows the given message. */
  fun updateProgress(message: String)

  /** Called when a refresh in progress was cancelled by the user. */
  fun onRefreshCancelledByTheUser()

  /** Called when a refresh has completed. */
  fun onRefreshCompleted()

  /**
   * Called when a Layoutlib native crash is detected. It will show a notification to the user
   * allowing to re-enable the disabled Layoutlib. If the user chooses to re-enable layoutlib,
   * [onLayoutlibReEnable] will be called.
   */
  fun onLayoutlibNativeCrash(onLayoutlibReEnable: () -> Unit)
}

fun interface ComposePreviewViewProvider {
  fun invoke(
    project: Project,
    psiFilePointer: SmartPsiElementPointer<PsiFile>,
    projectBuildStatusManager: ProjectBuildStatusManager,
    dataProvider: DataProvider,
    mainDesignSurfaceBuilder: NlDesignSurface.Builder,
    parentDisposable: Disposable
  ): ComposePreviewView
}

/**
 * [WorkBench] panel used to contain all the Compose Preview elements.
 *
 * This view contains all the different components that are part of the Compose Preview. If
 * expanded, in the content area the different areas look as follows:
 * ```
 * +------------------------------------------+
 * |                                          |     |
 * |       mainSurface                        |     |
 * |                                          |     |
 * |                                          |     |
 * +------------------------------------------+     | mainPanelSplitter
 * |                                          |     |
 * |   bottomPanel (for Animations Preview)   |     |
 * |                                          |     |
 * |                                          |     |
 * +------------------------------------------+
 * ```
 *
 * The [mainPanelSplitter] top panel contains the scrollable panel ([mainSurface]) and, also as
 * overlays, the zoom controls.
 *
 * @param project the current open project
 * @param psiFilePointer an [SmartPsiElementPointer] pointing to the file being rendered within this
 *   panel. Used to handle which notifications should be displayed.
 * @param projectBuildStatusManager [ProjectBuildStatusManager] used to detect the current build
 *   status and show/hide the correct loading message.
 * @param dataProvider the [DataProvider] to be used by the [mainSurface] panel.
 * @param mainDesignSurfaceBuilder a builder to create main design surface
 * @param parentDisposable the [Disposable] to use as parent disposable for this panel.
 */
internal class ComposePreviewViewImpl(
  private val project: Project,
  private val psiFilePointer: SmartPsiElementPointer<PsiFile>,
  private val projectBuildStatusManager: ProjectBuildStatusManager,
  dataProvider: DataProvider,
  mainDesignSurfaceBuilder: NlDesignSurface.Builder,
  parentDisposable: Disposable
) : ComposePreviewView, Pannable, DataProvider {

  private val workbench =
    WorkBench<DesignSurface<*>>(project, "Compose Preview", null, parentDisposable, 0)

  private val log = Logger.getInstance(ComposePreviewViewImpl::class.java)

  override val mainSurface =
    mainDesignSurfaceBuilder
      .setDelegateDataProvider { key ->
        if (PANNABLE_KEY.`is`(key)) {
          this@ComposePreviewViewImpl
        } else if (GuiInputHandler.CURSOR_RECEIVER.`is`(key)) {
          // TODO(b/229842640): We should actually pass the [scrollPane] here, but it does not work
          workbench
        } else dataProvider.getData(key)
      }
      .build()
      .apply {
        // Set the initial scale value to 0.25, so the preview is not large before first
        // zoom-to-fit is triggered.
        setScale(0.25)
      }

  override val isPannable: Boolean
    get() = mainSurface.isPannable
  override var isPanning: Boolean
    get() = mainSurface.isPanning
    set(value) {
      mainSurface.isPanning = value
    }

  override val component: JComponent = workbench

  private val notificationPanel =
    NotificationPanel(
      ExtensionPointName.create(
        "com.android.tools.idea.compose.preview.composeEditorNotificationProvider"
      )
    )

  override var scrollPosition: Point
    get() = mainSurface.scrollPosition
    set(value) {
      mainSurface.setScrollPosition(value.x, value.y)
    }

  /**
   * Vertical splitter where the top component is the [mainSurface] and the bottom component, when
   * visible, is an auxiliary panel associated with the preview. For example, it can be an animation
   * inspector that lists all the animations the preview has.
   */
  private val mainPanelSplitter =
    JBSplitter(true, 0.7f).apply { dividerWidth = ISSUE_SPLITTER_DIVIDER_WIDTH_PX }

  /** [ActionData] that triggers Build and Refresh of the preview. */
  private val buildAndRefreshAction: ActionData
    get() {
      val actionDataText =
        "${message("panel.needs.build.action.text")}${getBuildAndRefreshShortcut().asString()}"
      return ActionData(actionDataText) {
        psiFilePointer.element?.virtualFile?.let { project.requestBuild(it) }
        workbench
          .repaint() // Repaint the workbench, otherwise the text and link will keep displaying if
        // the mouse is hovering the link
      }
    }
  private val actionsToolbar: ActionsToolbar

  init {
    mainSurface.name = "Compose"

    val contentPanel =
      JPanel(BorderLayout()).apply {
        actionsToolbar = ActionsToolbar(parentDisposable, mainSurface)
        add(actionsToolbar.toolbarComponent, BorderLayout.NORTH)

        // Panel containing notifications label
        val topPanel =
          JPanel(VerticalLayout(0)).apply {
            isOpaque = false
            isFocusable = false
            add(notificationPanel, VerticalLayout.FILL)
          }

        val content = JPanel(BorderLayout())
        content.add(topPanel, BorderLayout.NORTH)
        content.add(mainSurface, BorderLayout.CENTER)

        add(content, BorderLayout.CENTER)
      }

    mainPanelSplitter.firstComponent = contentPanel

    val issueErrorSplitter =
      IssuePanelSplitter(psiFilePointer.virtualFile, mainSurface, mainPanelSplitter)

    workbench.init(issueErrorSplitter, mainSurface, listOf(), false)
    workbench.hideContent()
    if (projectBuildStatusManager.status == ProjectStatus.NeedsBuild) {
      log.debug("Project needs build")
      showNeedsToBuildErrorPanel()
    } else {
      val message =
        when {
          projectBuildStatusManager.isBuilding -> message("panel.building")
          DumbService.getInstance(project).isDumb -> message("panel.indexing")
          else -> message("panel.initializing")
        }
      log.debug("Show loading: $message")
      workbench.showLoading(message)
    }
    workbench.focusTraversalPolicy = LayoutFocusTraversalPolicy()
    workbench.isFocusCycleRoot = true

    DataManager.registerDataProvider(workbench) { getData(it) }
    Disposer.register(parentDisposable) { DataManager.removeDataProvider(workbench) }
  }

  override fun updateProgress(message: String) =
    UIUtil.invokeLaterIfNeeded {
      log.debug("updateProgress: $message")
      if (workbench.isMessageVisible) {
        workbench.showLoading(message)
        workbench.hideContent()
      }
    }

  private fun showModalErrorMessage(message: String, actionData: ActionData?) =
    UIUtil.invokeLaterIfNeeded {
      log.debug("showModelErrorMessage: $message")
      workbench.loadingStopped(message, actionData)
    }

  override fun updateNotifications(parentEditor: FileEditor) =
    UIUtil.invokeLaterIfNeeded {
      if (Disposer.isDisposed(workbench) || project.isDisposed || !parentEditor.isValid)
        return@invokeLaterIfNeeded

      notificationPanel.updateNotifications(psiFilePointer.virtualFile, parentEditor, project)
    }

  /** Method called to ask all notifications to update. */
  private fun updateNotifications() =
    UIUtil.invokeLaterIfNeeded {
      actionsToolbar.updateActions()
      // Make sure all notifications are cleared-up
      if (!project.isDisposed) {
        EditorNotifications.getInstance(project).updateNotifications(psiFilePointer.virtualFile)
      }
    }

  /**
   * Shows an error message saying a successful build is needed and a link to trigger a new build.
   */
  private fun showNeedsToBuildErrorPanel() {
    showModalErrorMessage(message("panel.needs.build"), buildAndRefreshAction)
  }

  override fun onRefreshCancelledByTheUser() {
    if (!hasRendered)
      showModalErrorMessage(message("panel.refresh.cancelled"), buildAndRefreshAction)
    else onRefreshCompleted()
  }

  override fun onRefreshCompleted() {
    updateVisibilityAndNotifications()
  }

  override fun onLayoutlibNativeCrash(onLayoutlibReEnable: () -> Unit) {
    workbench.handleLayoutlibNativeCrash(onLayoutlibReEnable)
  }

  /**
   * Updates the surface visibility and displays the content or an error message depending on the
   * build state. This method is called after certain updates like a build or a preview refresh has
   * happened. Calling this method will also update the FileEditor notifications.
   */
  override fun updateVisibilityAndNotifications() =
    UIUtil.invokeLaterIfNeeded {
      if (
        workbench.isMessageVisible && projectBuildStatusManager.status == ProjectStatus.NeedsBuild
      ) {
        log.debug("Needs successful build")
        showNeedsToBuildErrorPanel()
      } else {
        if (hasRendered) {
          log.debug("Show content")
          workbench.hideLoading()
          if (hasContent) {
            workbench.showContent()
          } else {
            workbench.hideContent()
            workbench.loadingStopped(
              message("panel.no.previews.defined"),
              null,
              UrlData(message("panel.no.previews.action"), COMPOSE_PREVIEW_DOC_URL),
              null
            )
          }
        }
      }

      updateNotifications()
    }

  override var hasContent: Boolean = false

  override val isMessageBeingDisplayed: Boolean
    get() = this.workbench.isMessageVisible

  @get:Synchronized
  override var hasRendered: Boolean = false
    @Synchronized
    set(value) {
      field = value
      updateVisibilityAndNotifications()
    }

  override var bottomPanel: JComponent?
    set(value) {
      mainPanelSplitter.secondComponent = value
    }
    get() = mainPanelSplitter.secondComponent

  override fun getData(dataId: String): Any? {
    return if (DESIGN_SURFACE.`is`(dataId)) mainSurface else null
  }
}
