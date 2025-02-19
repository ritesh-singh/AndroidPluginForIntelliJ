/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.adtui.categorytable

import com.android.tools.adtui.swing.FakeUi
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.DataManager
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.impl.HeadlessDataManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestApplicationManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.SortOrder
import org.junit.Rule
import org.junit.Test

class CategoryTableTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val disposableRule = DisposableRule()

  fun createScrollPane(table: CategoryTable<*>) =
    JBScrollPane().also {
      it.setBounds(0, 0, 800, 400)
      // This clears the default 1px insets of the ScrollPane to keep layout simpler
      it.border = BorderFactory.createEmptyBorder()
      table.addToScrollPane(it)
    }

  @Test
  fun group() {
    val values = CategoryTableDemo.devices
    val sorted = groupAndSort(values, listOf(Status.attribute), emptyList())
    assertThat(sorted.map { it.status })
      .containsExactly("Offline", "Offline", "Offline", "Online", "Online", "Online")
  }

  @Test
  fun nestedGroup() {
    val values = CategoryTableDemo.devices
    val sorted = groupAndSort(values, listOf(Status.attribute, Type.attribute), emptyList())
    assertThat(sorted.map { it.type })
      .containsExactly("Phone", "Phone", "Tablet", "Phone", "Phone", "Tablet")
  }

  @Test
  fun addAndRemoveGrouping() {
    val table = CategoryTable(CategoryTableDemo.columns)

    CategoryTableDemo.devices.forEach { table.addRow(it) }
    assertThat(table.rowComponents).hasSize(6)

    table.toggleSortOrder(Name.attribute)
    table.addGrouping(Api.attribute)

    assertThat(table.rowComponents.map { it.stringValue() })
      .containsExactly(
        "25",
        "Nexus 7",
        "26",
        "Nexus 7",
        "31",
        "Pixel 6",
        "Pixel 6a",
        "32",
        "Pixel 5",
        "33",
        "Pixel 7"
      )

    table.addGrouping(Type.attribute)

    assertThat(table.rowComponents.map { it.stringValue() })
      .containsExactly(
        "25",
        "25, Tablet",
        "Nexus 7",
        "26",
        "26, Tablet",
        "Nexus 7",
        "31",
        "31, Phone",
        "Pixel 6",
        "Pixel 6a",
        "32",
        "32, Phone",
        "Pixel 5",
        "33",
        "33, Phone",
        "Pixel 7"
      )

    table.removeGrouping(Api.attribute)

    assertThat(table.rowComponents.map { it.stringValue() })
      .containsExactly(
        "Tablet",
        "Nexus 7",
        "Nexus 7",
        "Phone",
        "Pixel 5",
        "Pixel 6",
        "Pixel 6a",
        "Pixel 7"
      )
  }

  private fun RowComponent<CategoryTableDemo.Device>.stringValue() =
    when (this) {
      is CategoryRowComponent -> path.joinToString { it.value.toString() }
      is ValueRowComponent -> (componentList[0].component as JLabel).text
    }

  @Test
  fun tableLayout() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane, createFakeWindow = true)

    scrollPane.setBounds(0, 0, 800, 400)
    fakeUi.layout()

    // All columns have fixed width except the first, which should absorb the extra width.
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(410, 80, 80, 80, 150)
      .inOrder()

    scrollPane.setBounds(0, 0, 500, 400)
    fakeUi.layout()

    // We are space-constrained, take some space away from each element (except the last, which is
    // fixed-size).
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(247, 34, 34, 34, 150)
      .inOrder()

    scrollPane.setBounds(0, 0, 410, 400)
    fakeUi.layout()

    // We have just enough width to give every component its minimum width.
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(200, 20, 20, 20, 150)
      .inOrder()

    scrollPane.setBounds(0, 0, 200, 400)
    fakeUi.layout()

    // We don't have enough space for the minimum; the columns keep their width but the rendering is
    // truncated.
    assertThat(table.header.tableColumns.map { it.width })
      .containsExactly(200, 20, 20, 20, 150)
      .inOrder()
  }

  @Test
  fun sorting() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane, createFakeWindow = true)

    fakeUi.clickRelativeTo(scrollPane, 2, 2)
    assertThat(table.columnSorters)
      .containsExactly(ColumnSortOrder(table.columns[0].attribute, SortOrder.ASCENDING))

    fakeUi.clickRelativeTo(scrollPane, table.header.tableColumns[0].width + 2, 2)
    assertThat(table.columnSorters)
      .containsExactly(
        ColumnSortOrder(table.columns[1].attribute, SortOrder.ASCENDING),
        ColumnSortOrder(table.columns[0].attribute, SortOrder.ASCENDING)
      )

    fakeUi.clickRelativeTo(scrollPane, table.header.tableColumns[0].width + 2, 2)
    assertThat(table.columnSorters)
      .containsExactly(
        ColumnSortOrder(table.columns[1].attribute, SortOrder.DESCENDING),
        ColumnSortOrder(table.columns[0].attribute, SortOrder.ASCENDING)
      )
  }

  @Test
  @RunsInEdt
  fun selection() {
    // Set some distinct colors
    val colors =
      CategoryTable.Colors(
        selectedForeground = JBColor.BLUE,
        selectedBackground = JBColor.WHITE,
        unselectedBackground = JBColor.RED,
        unselectedForeground = JBColor.GREEN,
      )
    val table = CategoryTable(CategoryTableDemo.columns, colors = colors)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane, createFakeWindow = true)

    CategoryTableDemo.devices.forEach { table.addRow(it) }
    fakeUi.layout()

    assertThat(table.selection.selectedKeys()).isEmpty()

    val rowToSelect = table.rowComponents[0] as ValueRowComponent<CategoryTableDemo.Device>
    val nameColumn = table.columns.indexOf(Name)
    val nameColumnComponent = rowToSelect.componentList[nameColumn].component
    val actionColumn = table.columns.indexOf(Actions)
    val actionColumnComponent = rowToSelect.componentList[actionColumn].component
    val actionButton = actionColumnComponent.componentList[0]
    val originalButtonForeground = actionButton.foreground

    fakeUi.clickOn(rowToSelect)

    assertThat(table.selection.selectedKeys()).contains(rowToSelect.rowKey)

    IdeEventQueue.getInstance().flushQueue()

    assertThat(nameColumnComponent.foreground).isEqualTo(colors.selectedForeground)
    assertThat(nameColumnComponent.background).isEqualTo(colors.selectedBackground)
    assertThat(actionColumnComponent.foreground).isEqualTo(colors.selectedForeground)
    assertThat(actionColumnComponent.background).isEqualTo(colors.selectedBackground)
    assertThat(actionButton.foreground).isEqualTo(originalButtonForeground)
  }

  @Test
  @RunsInEdt
  fun collapsedRows() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane)

    CategoryTableDemo.devices.forEach { table.addRow(it) }
    table.addGrouping(Status.attribute)

    fakeUi.layout()

    val categoryRow = table.rowComponents[0] as CategoryRowComponent<CategoryTableDemo.Device>
    val category2Row = table.rowComponents[4] as CategoryRowComponent<CategoryTableDemo.Device>
    val row2Position = fakeUi.getPosition(table.rowComponents[1])
    val category2Position = fakeUi.getPosition(category2Row)

    assertThat(table.rowComponents[1].isVisible).isTrue()

    fakeUi.clickOn(categoryRow)
    fakeUi.layout()

    assertThat(table.rowComponents[1].isVisible).isFalse()
    assertThat(table.rowComponents[2].isVisible).isFalse()
    assertThat(fakeUi.getPosition(category2Row)).isEqualTo(row2Position)

    fakeUi.clickOn(categoryRow)
    fakeUi.layout()

    assertThat(table.rowComponents[1].isVisible).isTrue()
    assertThat(table.rowComponents[2].isVisible).isTrue()
    assertThat(fakeUi.getPosition(category2Row)).isEqualTo(category2Position)
  }

  @Test
  @RunsInEdt
  fun rowDataContext() {
    TestApplicationManager.getInstance()
    HeadlessDataManager.fallbackToProductionDataManager(disposableRule.disposable)

    val table =
      CategoryTable(
        CategoryTableDemo.columns,
        rowDataProvider = DefaultValueRowDataProvider(DEVICE_DATA_KEY)
      )

    CategoryTableDemo.devices.forEach { table.addRow(it) }

    val component = (table.rowComponents[0] as ValueRowComponent).componentList[0].component
    val data = DataManager.getInstance().getDataContext(component).getData(DEVICE_DATA_KEY)

    assertThat(data).isEqualTo(CategoryTableDemo.devices[0])
  }

  @Test
  @RunsInEdt
  fun hiddenRows() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane)

    CategoryTableDemo.devices.forEach { table.addRow(it) }
    fakeUi.layout()

    val position = fakeUi.getPosition(table.rowComponents[1])

    table.setRowVisibleByKey(CategoryTableDemo.devices[1], false)
    fakeUi.layout()

    assertThat(fakeUi.getPosition(table.rowComponents[2])).isEqualTo(position)

    // Clicking the row that shares the location of the hidden row should select
    // the visible row
    fakeUi.clickOn(table.rowComponents[2])
    assertThat(table.selection.selectedKeys()).containsExactly(table.rowComponents[2].rowKey)

    table.setRowVisibleByKey(CategoryTableDemo.devices[1], true)
    fakeUi.layout()

    assertThat(fakeUi.getPosition(table.rowComponents[2])).isNotEqualTo(position)
  }

  @Test
  @RunsInEdt
  fun hiddenCollapsedRows() {
    val table = CategoryTable(CategoryTableDemo.columns)
    val scrollPane = createScrollPane(table)
    val fakeUi = FakeUi(scrollPane)

    CategoryTableDemo.devices.forEach { table.addRow(it) }
    table.addGrouping(Status.attribute)
    fakeUi.layout()

    val categoryRow = table.rowComponents[0] as CategoryRowComponent<CategoryTableDemo.Device>
    fakeUi.clickOn(categoryRow)
    fakeUi.layout()

    assertThat(table.rowComponents[1].isVisible).isFalse()

    // Setting visibility of a collapsed row does not make it visible
    val value = (table.rowComponents[1] as ValueRowComponent<CategoryTableDemo.Device>).value
    table.setRowVisibleByKey(value, true)

    assertThat(table.rowComponents[1].isVisible).isFalse()

    // Un-collapsing an invisible row does not make it visible
    table.setRowVisibleByKey(value, false)

    fakeUi.clickOn(categoryRow)
    fakeUi.layout()

    assertThat(table.rowComponents[1].isVisible).isFalse()
    assertThat(table.rowComponents[2].isVisible).isTrue()
  }
}
