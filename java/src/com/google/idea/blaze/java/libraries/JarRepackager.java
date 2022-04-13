/*
 * Copyright 2022 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.java.libraries;

import com.intellij.openapi.application.ApplicationManager;
import java.io.File;
import java.io.IOException;

/** Provide repackage service for jars. */
public interface JarRepackager {
  static JarRepackager getInstance() {
    return ApplicationManager.getApplication().getService(JarRepackager.class);
  }

  default String getRepackagedJarName(String originalJarName) {
    return originalJarName;
  }

  default String getOriginalJarName(String repackagedJarName) {
    return repackagedJarName;
  }

  default void processJar(File jar) throws IOException, InterruptedException {}
}
