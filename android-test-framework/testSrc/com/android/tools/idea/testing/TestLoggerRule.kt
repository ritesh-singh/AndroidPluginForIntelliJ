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
package com.android.tools.idea.testing

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestLoggerFactory
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * A rule that prints all logs to stderr if the test fails.
 *
 * When the test starts, this rule calls [TestLoggerFactory.onTestStarted] which clears the internal buffer. This helps to reduce the amount
 * of logs printed.
 *
 * However, any logs generated by `@Before` and `@After` methods will still be emitted as well as any logs generated by rules performed
 * after this rule in a rule chain. Therefore, in order to minimize the amount of irrelevant logs printed, this rule should be the last rule
 * in the chain and the `@Before` and `@After` methods should strive to not emit any logs.
 *
 * @param categories A list of categories to force DEBUG logs. Note that unless the test is marked as a stress test
 * ([com.intellij.openapi.application.ex.ApplicationManagerEx.isInStressTest]) DEBUG will be enabled for all categories anyway. This can
 * be changed by calling `ApplicationManagerEx.setInStressTest(true)` but may have side effects.
 */
class TestLoggerRule(vararg val categories: String) : TestWatcher() {
  private val disposable = Disposer.newDisposable("TestLoggerRule")

  override fun starting(description: Description?) {
    TestLoggerFactory.enableDebugLogging(disposable, *categories)
    TestLoggerFactory.onTestStarted()
  }

  override fun succeeded(description: Description) {
    TestLoggerFactory.onTestFinished(true, description)
  }

  override fun failed(e: Throwable, description: Description) {
    TestLoggerFactory.onTestFinished(false, description)
  }

  override fun finished(description: Description) {
    Disposer.dispose(disposable)
  }
}
