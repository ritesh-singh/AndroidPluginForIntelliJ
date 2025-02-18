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
package com.android.tools.idea.gradle.dsl.api.dependencies

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.util.PsiElementHolder

/**
 * Model for library declaration in version catalog.
 */
interface LibraryDeclarationModel: PsiElementHolder {
  fun compactNotation(): String

  fun getSpec(): LibraryDeclarationSpec

  fun name(): ResolvedPropertyModel

  fun group(): ResolvedPropertyModel

  fun version(): ResolvedPropertyModel

  fun completeModel(): ResolvedPropertyModel?
}