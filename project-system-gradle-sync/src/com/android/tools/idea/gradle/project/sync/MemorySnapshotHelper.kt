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
package com.android.tools.idea.gradle.project.sync

import com.android.tools.memory.usage.LightweightHeapTraverse
import com.android.tools.memory.usage.LightweightHeapTraverseConfig
import com.android.tools.memory.usage.LightweightTraverseResult
import com.intellij.util.MemoryDumpHelper
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.system.measureTimeMillis

fun captureSnapshot(outputPath: String, name: String) {
  try {
    val file = File(outputPath).resolve("${getTimestamp()}-$name.hprof")
    println("Capturing memory snapshot at: ${file.absolutePath}")
    val elapsedTime = measureTimeMillis {
      MemoryDumpHelper.captureMemoryDump(file.absolutePath)
    }
    println("Done in $elapsedTime")
  } catch (e: Exception) {
    println("Error capturing snapshot:  ${e.stackTraceToString()}")
  }
}

fun analyzeCurrentProcessHeap(outputPath: String, name: String, lightweightMode: Boolean) {
  val collectReachableObjectsInfo = !lightweightMode
  println("Starting heap traversal for $name")
  var result : LightweightTraverseResult?
  val elapsedTime = measureTimeMillis {
    result = LightweightHeapTraverse.collectReport(LightweightHeapTraverseConfig(false, collectReachableObjectsInfo, true))
  }
  println("Heap traversal for $name finished in $elapsedTime milliseconds")

  if (collectReachableObjectsInfo) {
    println("Heap $name total size MBs: ${result!!.totalReachableObjectsSizeBytes shr 20} ")
    println("Heap $name total object count: ${result!!.totalReachableObjectsNumber} ")
    val fileTotal = File(outputPath).resolve("${getTimestamp()}_${name}_total")
    fileTotal.writeText(result!!.totalReachableObjectsSizeBytes.toString())
  }
  println("Heap $name strong size MBs: ${result!!.totalStrongReferencedObjectsSizeBytes shr 20} ")
  println("Heap $name strong object count: ${result!!.totalStrongReferencedObjectsNumber} ")
  val fileStrong = File(outputPath).resolve("${getTimestamp()}_${name}_strong")
  fileStrong.writeText(result!!.totalStrongReferencedObjectsSizeBytes.toString())
}

private fun getTimestamp() = DateTimeFormatter
  .ofPattern("yyyy.MM.dd-HH:mm")
  .withZone(ZoneOffset.UTC)
  .format(Instant.now())
