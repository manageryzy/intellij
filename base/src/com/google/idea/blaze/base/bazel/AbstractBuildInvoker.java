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
package com.google.idea.blaze.base.bazel;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.idea.blaze.base.async.FutureUtil;
import com.google.idea.blaze.base.async.FutureUtil.FutureResult;
import com.google.idea.blaze.base.bazel.BuildSystem.BuildInvoker;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.command.BlazeFlags;
import com.google.idea.blaze.base.command.BlazeInvocationContext;
import com.google.idea.blaze.base.command.info.BlazeInfo;
import com.google.idea.blaze.base.command.info.BlazeInfoRunner;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.scope.BlazeContext;
import com.google.idea.blaze.base.scope.scopes.TimingScope.EventType;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.SyncScope.SyncFailedException;
import com.intellij.openapi.project.Project;
import java.util.List;
import javax.annotation.Nullable;

/** Base class for implementations of {@link BuildInvoker} that deals with running `blaze info`. */
public abstract class AbstractBuildInvoker implements BuildInvoker {

  protected final Project project;
  private final BlazeContext blazeContext;

  private BlazeInfo blazeInfo;

  public AbstractBuildInvoker(Project project, BlazeContext blazeContext) {
    this.project = project;
    this.blazeContext = blazeContext;
  }

  protected abstract BuildSystem getBuildSystem();

  @Override
  @Nullable
  public synchronized BlazeInfo getBlazeInfo() throws SyncFailedException {
    if (blazeInfo == null) {
      blazeInfo = createBlazeInfo();
    }
    return blazeInfo;
  }

  private ListenableFuture<BlazeInfo> runBlazeInfo() {
    ProjectViewSet viewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (viewSet == null) {
      // defer the failure until later when it can be handler more easily:
      return Futures.immediateFailedFuture(new IllegalStateException("Empty project view state"));
    }
    List<String> syncFlags =
        BlazeFlags.blazeFlags(
            project, viewSet, BlazeCommandName.INFO, BlazeInvocationContext.SYNC_CONTEXT);
    return BlazeInfoRunner.getInstance()
        .runBlazeInfo(
            blazeContext,
            getBuildSystem().getName(),
            getBinaryPath(),
            WorkspaceRoot.fromProject(project),
            syncFlags);
  }

  private BlazeInfo createBlazeInfo() throws SyncFailedException {
    ListenableFuture<BlazeInfo> future = runBlazeInfo();
    FutureResult<BlazeInfo> result =
        FutureUtil.waitForFuture(blazeContext, future)
            .timed(Blaze.buildSystemName(project) + "Info", EventType.BlazeInvocation)
            .withProgressMessage(
                String.format("Running %s info...", Blaze.buildSystemName(project)))
            .onError(String.format("Could not run %s info", Blaze.buildSystemName(project)))
            .run();
    if (result.success()) {
      return result.result();
    }
    throw new SyncFailedException(
        String.format("Failed to run `%s info`", getBinaryPath()), result.exception());
  }
}
