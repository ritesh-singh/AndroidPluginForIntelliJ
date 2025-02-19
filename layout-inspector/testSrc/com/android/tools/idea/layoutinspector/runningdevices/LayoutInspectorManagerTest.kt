/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.runningdevices

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.layoutinspector.LAYOUT_INSPECTOR_DATA_KEY
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorProjectService
import com.android.tools.idea.layoutinspector.model
import com.android.tools.idea.layoutinspector.pipeline.DisconnectedClient
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.util.FakeTreeSettings
import com.android.tools.idea.streaming.emulator.EmulatorViewRule
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JPanel

class LayoutInspectorManagerTest {

  @get:Rule
  val edtRule = EdtRule()

  @get:Rule
  val displayViewRule = EmulatorViewRule()

  private lateinit var layoutInspector: LayoutInspector

  private lateinit var tab1: TabInfo
  private lateinit var tab2: TabInfo

  private lateinit var fakeToolWindowManager: FakeToolWindowManager

  @Before
  fun setUp() {
    tab1 = TabInfo(TabId("tab1"), JPanel(), JPanel(), displayViewRule.newEmulatorView())
    tab2 = TabInfo(TabId("tab2"), JPanel(), JPanel(), displayViewRule.newEmulatorView())
    fakeToolWindowManager = FakeToolWindowManager(displayViewRule.project, listOf(tab1, tab2))

    // replace ToolWindowManager with fake one
    displayViewRule.project.replaceService(
      ToolWindowManager::class.java,
      fakeToolWindowManager,
      displayViewRule.testRootDisposable
    )

    val mockLayoutInspectorProjectService = mock<LayoutInspectorProjectService>()

    layoutInspector = LayoutInspector(
      coroutineScope = AndroidCoroutineScope(displayViewRule.testRootDisposable),
      layoutInspectorClientSettings = InspectorClientSettings(displayViewRule.project),
      client = DisconnectedClient,
      layoutInspectorModel = model { },
      treeSettings = FakeTreeSettings()
    )

    whenever(mockLayoutInspectorProjectService.getLayoutInspector()).thenAnswer { layoutInspector }
    displayViewRule.project.replaceService(LayoutInspectorProjectService::class.java, mockLayoutInspectorProjectService, displayViewRule.testRootDisposable)

    RunningDevicesStateObserver.getInstance(displayViewRule.project).update(true)
  }

  @After
  fun tearDown() {
    RunningDevicesStateObserver.getInstance(displayViewRule.project).update(false)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnOff() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, true)

    assertHasWorkbench(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, false)

    assertDoesNotHaveWorkbench(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTimesForSameTab() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, true)
    layoutInspectorManager.enableLayoutInspector(tab1.tabId, true)

    assertHasWorkbench(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, false)

    assertDoesNotHaveWorkbench(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOffMultipleTimesForSameTab() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, true)

    assertHasWorkbench(tab1)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, false)
    layoutInspectorManager.enableLayoutInspector(tab1.tabId, false)

    assertDoesNotHaveWorkbench(tab1)
  }

  @Test
  @RunsInEdt
  fun testToggleLayoutInspectorOnMultipleTabs() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, true)

    assertHasWorkbench(tab1)

    layoutInspectorManager.enableLayoutInspector(tab2.tabId, true)

    assertDoesNotHaveWorkbench(tab1)
    assertHasWorkbench(tab2)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, false)

    assertDoesNotHaveWorkbench(tab1)
    assertHasWorkbench(tab2)

    layoutInspectorManager.enableLayoutInspector(tab2.tabId, false)

    assertDoesNotHaveWorkbench(tab2)
  }

  @Test
  @RunsInEdt
  fun testWorkbenchHasDataProvider() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, true)

    val workbench = tab1.content.parents().filterIsInstance<WorkBench<LayoutInspector>>().first()
    val dataContext = DataManager.getInstance().getDataContext(workbench)
    val layoutInspector = dataContext.getData(LAYOUT_INSPECTOR_DATA_KEY)
    assertThat(layoutInspector).isEqualTo(layoutInspector)
  }

  @Test
  @RunsInEdt
  fun testSelectedTabDoesNotChange() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    layoutInspectorManager.enableLayoutInspector(tab1.tabId, true)

    assertHasWorkbench(tab1)

    // adding a new tab that doesn't have Layout Inspector enabled
    fakeToolWindowManager.addContent(tab2)

    assertHasWorkbench(tab1)
    assertDoesNotHaveWorkbench(tab2)
  }

  @Test
  @RunsInEdt
  fun testWorkbenchIsInjectedWhenSelectedTabChanges() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    fakeToolWindowManager.setSelectedContent(tab1)
    layoutInspectorManager.enableLayoutInspector(tab1.tabId, true)

    fakeToolWindowManager.setSelectedContent(tab2)
    layoutInspectorManager.enableLayoutInspector(tab2.tabId, true)

    assertDoesNotHaveWorkbench(tab1)
    assertHasWorkbench(tab2)

    fakeToolWindowManager.setSelectedContent(tab1)

    assertHasWorkbench(tab1)
    assertDoesNotHaveWorkbench(tab2)
  }

  @Test
  @RunsInEdt
  fun testSelectedTabIsRemoved() {
    val layoutInspectorManager = LayoutInspectorManager.getInstance(displayViewRule.project)

    fakeToolWindowManager.setSelectedContent(tab1)
    layoutInspectorManager.enableLayoutInspector(tab1.tabId, true)

    fakeToolWindowManager.setSelectedContent(tab2)
    layoutInspectorManager.enableLayoutInspector(tab2.tabId, true)

    assertDoesNotHaveWorkbench(tab1)
    assertHasWorkbench(tab2)

    fakeToolWindowManager.setSelectedContent(tab1)

    assertHasWorkbench(tab1)
    assertDoesNotHaveWorkbench(tab2)

    fakeToolWindowManager.removeContent(tab1)

    assertDoesNotHaveWorkbench(tab1)
    assertHasWorkbench(tab2)
  }

  private fun assertHasWorkbench(tabInfo: TabInfo) {
    assertThat(tabInfo.content.parents().filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(1)
    assertThat(tabInfo.container.components.filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(1)

    val toolbars = tabInfo.container
      .children()
      .filterIsInstance<ActionToolbar>()
      .filter { it.component.name == "LayoutInspector.MainToolbar" }

    assertThat(toolbars).hasSize(1)
  }

  private fun assertDoesNotHaveWorkbench(tabInfo: TabInfo) {
    assertThat(tabInfo.content.parents().filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(0)
    assertThat(tabInfo.container.components.filterIsInstance<WorkBench<LayoutInspector>>()).hasSize(0)
    assertThat(tabInfo.content.parent).isEqualTo(tabInfo.container)

    val toolbars = tabInfo.container
      .children()
      .filterIsInstance<ActionToolbar>()
      .filter { it.component.name == "LayoutInspector.MainToolbar" }

    assertThat(toolbars).hasSize(0)
  }

  private fun Component.parents(): List<Container> {
    val parents = mutableListOf<Container>()
    var component = this
    while (component.parent != null) {
      parents.add(component.parent)
      component = component.parent
    }
    return parents
  }

  private fun Container.children(): List<Component> {
    val children = mutableListOf<Component>()
    for (component in components) {
      children.add(component)
      if (component is Container) {
        children.addAll(component.children())
      }
    }
    return children
  }
}