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
package com.android.tools.idea.gradle.project.sync.memory

import com.android.testutils.TestUtils
import com.intellij.util.io.createDirectories
import java.io.File
import java.text.NumberFormat
import kotlin.io.path.absolutePathString

data class HeapUsageMetrics(val totalUsage: Long, val softlyReferenced: Long, val weaklyReferenced: Long) {
  fun excludeSoftAndWeak(): Long = totalUsage - softlyReferenced - weaklyReferenced
}

/**
 * Invokes Eclipse Memory Analyzer to inspect the contents of an hprof file.
 *
 * More info at: https://help.eclipse.org/latest/topic/org.eclipse.mat.ui.help/welcome.html
 */
class EclipseMatHelper(private val scriptPath: String = "prebuilts/tools/common/eclipse-mat/ParseHeapDump.sh") {
  /** Returns the retained heap size by invoking the tool and parsing the report generated. */
  fun getHeapUsageMetrics(hprofPath: String) : HeapUsageMetrics {
    check(hprofPath.endsWith(".hprof") )

    // See https://help.eclipse.org/latest/index.jsp?topic=%2Forg.eclipse.mat.ui.help%2Freference%2Finspections%2Fquery_report.html for
    // example usage of the following script. This particular command creates a component report with all the classes included, which
    // also includes weak and soft references information.
    exec(scriptPath,
         "-vm", "${getJdkPath()}/bin",
         "-configuration", CONFIGURATION_DIRECTORY,
         hprofPath,
         "-format=txt",
         "-unzip",
         "-command=component_report .*",
         "org.eclipse.mat.api:query",
         "-vmargs", "-Xmx8g"
    )
    // The script will generate the result files with arbitrary names.
    // Unfortunately there is no option to give it an output path instead,
    // so the paths and file name prefixes are hardcoded.
    return HeapUsageMetrics(
      totalUsage = parseValueFromFile(hprofPath, DISTRIBUTION_PREFIX, DISTRIBUTION_PATTERN),
      softlyReferenced = parseValueFromFile(hprofPath, SOFTLY_RETAINED_PREFIX, TOTAL_ENTRIES_PATTERN),
      weaklyReferenced = parseValueFromFile(hprofPath, WEAKLY_RETAINED_PREFIX, TOTAL_ENTRIES_PATTERN)
    )
  }

  private fun parseValueFromFile(hprofPath: String, prefix: String, pattern: Regex): Long {
    val resultFile = findFileWithPrefix(hprofPath, prefix)
    checkNotNull(resultFile) { "Expected file not found with prefix: $prefix" }
    return resultFile.readLines().firstNotNullOf {
      pattern.findAll(it).firstOrNull()?.groups
    }[GROUP_NAME]!!.value.parseLong()
  }
  private fun findFileWithPrefix(hprofPath: String, prefix: String) =
    File(hprofPath.replace(".hprof", RELATIVE_RESULTS_DIR)).listFiles()?.first{ it.name.startsWith(prefix)}


  private fun getJdkPath(): String {
    val jdk = if (System.getProperty("java.version", "").startsWith("17")) "jdk17" else "jdk11"
    return TestUtils.getWorkspaceRoot().resolve("prebuilts/studio/jdk/${jdk}/linux").absolutePathString()
  }

  private fun String.parseLong() = NumberFormat.getNumberInstance().parse(this).toLong()

  private fun exec(vararg cmd: String) {
    val exitCode = ProcessBuilder().command(*cmd).inheritIO().start().waitFor()
    if (exitCode != 0) {
       throw RuntimeException("Script exited with code: $exitCode")
    }
  }

  companion object {
    private const val RELATIVE_RESULTS_DIR = "_Query/pages"
    private const val DISTRIBUTION_PREFIX = "Distribution"
    private const val SOFTLY_RETAINED_PREFIX = "Only_Softly_Retained"
    private const val WEAKLY_RETAINED_PREFIX = "Only_Weakly_Retained"
    private const val GROUP_NAME = "size"
    private val DISTRIBUTION_PATTERN = """Slice 1: (?<$GROUP_NAME>.*) """.toRegex()
    private val TOTAL_ENTRIES_PATTERN = """Total:.*\|.*\| *(?<$GROUP_NAME>.*)$""".toRegex()
    private val CONFIGURATION_DIRECTORY = File(System.getenv("TEST_TMPDIR"), "eclipse-configurations").also {
      if (!it.exists()) {
        it.toPath().createDirectories()
      }
    }.absolutePath
  }
}