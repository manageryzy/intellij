package com.google.idea.blaze.python.sdk;

import com.google.devtools.build.lib.query2.proto.proto2api.Build;
import com.google.devtools.build.lib.query2.proto.proto2api.Build.QueryResult;
import com.google.devtools.intellij.ideinfo.IntellijIdeInfo;
import com.google.idea.blaze.base.async.process.ExternalTask;
import com.google.idea.blaze.base.async.process.LineProcessingOutputStream;
import com.google.idea.blaze.base.bazel.BuildSystem;
import com.google.idea.blaze.base.command.BlazeCommand;
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.model.BlazeProjectData;
import com.google.idea.blaze.base.model.primitives.Kind;
import com.google.idea.blaze.base.model.primitives.TargetExpression;
import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.query.BlazeQueryMacroTargetProvider;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.sync.data.BlazeProjectDataManager;
import com.google.idea.blaze.python.settings.BlazePythonUserSettings;
import com.google.idea.blaze.python.sync.PySdkSuggester;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.addSdk;
import static com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil.setupSdk;

/**
 * find python sdk by user provided py_toolchain rule.
 *
 * @author manageryzy@gmail.com
 * @implNote Not implemented by google. Maybe bot work on blaze.
 */
public class PythonSdkSuggester extends PySdkSuggester {
  private static final Logger logger = Logger.getInstance(BlazeQueryMacroTargetProvider.class);

  @Nullable
  @Override
  protected String suggestPythonHomePath(
      Project project, IntellijIdeInfo.PyIdeInfo.PythonVersion version) {

    String toolchain = BlazePythonUserSettings.getInstance().getPythonSdkLabel();
    if (toolchain == null) {
      return null;
    }

    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }

    String interpreter = getInterpreter(project, version, toolchain);
    if (interpreter == null) {
      return null;
    }

    // test interpreter or interpreter_path. Maybe a nonexistence file?
    if (!interpreter.contains(":")) {
      return interpreter;
    }

    ByteArrayOutputStream out = new ByteArrayOutputStream(4096);

    BuildSystem.BuildInvoker buildInvoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project);

    BlazeCommand command =
        BlazeCommand.builder(buildInvoker, BlazeCommandName.fromString("cquery"))
            .addBlazeFlags("--output=starlark")
            .addBlazeFlags("--keep_going")
            .addTargets(TargetExpression.fromStringSafe(toolchain))
            .addBlazeFlags("--starlark:expr=providers(target)['PyRuntimeInfo'].interpreter.path")
            .build();

    ExternalTask.builder(WorkspaceRoot.fromProject(project))
        .addBlazeCommand(command)
        .stdout(out)
        .stderr(
            LineProcessingOutputStream.of(
                line -> {
                  // errors are expected, so limit logging to info level
                  logger.info(line);
                  return true;
                }))
        .build()
        .run();

    return projectData
        .getWorkspacePathResolver()
        .resolveToFile(out.toString().replaceAll("\n", ""))
        .getPath();
  }

  @Nullable
  private String getInterpreter(
      Project project, IntellijIdeInfo.PyIdeInfo.PythonVersion version, String toolchain) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(/* size= */ 4096);

    QueryResult toolchainTargets;

    BuildSystem.BuildInvoker buildInvoker =
        Blaze.getBuildSystemProvider(project).getBuildSystem().getBuildInvoker(project);

    // I have to query again, for there is no information in bazel ide proto.
    BlazeCommand command =
        BlazeCommand.builder(buildInvoker, BlazeCommandName.QUERY)
            .addBlazeFlags("--output=proto")
            .addBlazeFlags("--keep_going")
            .addTargets(TargetExpression.fromStringSafe(toolchain))
            .build();

    ExternalTask.builder(WorkspaceRoot.fromProject(project))
        .addBlazeCommand(command)
        .stdout(out)
        .stderr(
            LineProcessingOutputStream.of(
                line -> {
                  // errors are expected, so limit logging to info level
                  logger.info(line);
                  return true;
                }))
        .build()
        .run();

    try {
      toolchainTargets = QueryResult.parseFrom(new ByteArrayInputStream(out.toByteArray()));
    } catch (IOException e) {
      logger.warn("Couldn't parse blaze query proto output", e);
      return null;
    }

    if (toolchainTargets.getTargetCount() != 1) {
      return null;
    }
    Build.Target target = toolchainTargets.getTarget(0);
    if (!target.hasRule()) {
      return null;
    }

    Build.Rule rule = target.getRule();
    if (!"py_runtime".equals(rule.getRuleClass())) {
      return null;
    }

    List<Build.Attribute> attributeList = rule.getAttributeList();
    Optional<Build.Attribute> pythonVersion =
        attributeList.stream().filter(attr -> "python_version".equals(attr.getName())).findFirst();
    if (!pythonVersion.isPresent()
        || !pythonVersion.get().getStringValue().equals(version.name())) {
      return null;
    }

    Optional<Build.Attribute> interpreter =
        attributeList.stream().filter(attr -> "interpreter".equals(attr.getName())).findFirst();

    return interpreter
        .map(Build.Attribute::getStringValue)
        .orElse(
            attributeList.stream()
                .filter(attr -> "interpreter_path".equals(attr.getName()))
                .findFirst()
                .map(Build.Attribute::getStringValue)
                .orElse(null));
  }

  @Nullable
  @Override
  protected Sdk createSdk(
      Project project, IntellijIdeInfo.PyIdeInfo.PythonVersion version, String homePath) {
    BlazeProjectData projectData =
        BlazeProjectDataManager.getInstance(project).getBlazeProjectData();
    if (projectData == null) {
      return null;
    }

    VirtualFile sdkHome =
        WriteAction.compute(
            () ->
                LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(homePath)));

    Set<VirtualFile> sdkAdditionalPath;
    try {
      sdkAdditionalPath =
          WriteAction.compute(
              () ->
                  projectData.getTargetMap().targets().stream()
                      .filter(t -> t.getKind().isOneOf(Kind.fromRuleName("py_library")))
                      .map(t -> t.getKey().getLabel())
                      .map(
                          l -> {
                            ByteArrayOutputStream out = new ByteArrayOutputStream(/* size= */ 4096);

                            BuildSystem.BuildInvoker buildInvoker =
                                Blaze.getBuildSystemProvider(project)
                                    .getBuildSystem()
                                    .getBuildInvoker(project);

                            // query each py_library and get imports
                            BlazeCommand command =
                                BlazeCommand.builder(
                                        buildInvoker, BlazeCommandName.fromString("cquery"))
                                    .addBlazeFlags("--output=starlark")
                                    .addBlazeFlags("--keep_going")
                                    .addTargets(TargetExpression.fromStringSafe(l.toString()))
                                    .addBlazeFlags(
                                        "--starlark:expr=';'.join(providers(target)['PyInfo'].imports.to_list())")
                                    .build();

                            ExternalTask.builder(WorkspaceRoot.fromProject(project))
                                .addBlazeCommand(command)
                                .stdout(out)
                                .stderr(
                                    LineProcessingOutputStream.of(
                                        line -> {
                                          // errors are expected, so limit logging to info level
                                          logger.info(line);
                                          return true;
                                        }))
                                .build()
                                .run();

                            return Arrays.stream(out.toString().replaceAll("\n", "").split(";"))
                                .map(
                                    p -> {
                                      Path path =
                                          projectData.getBlazeInfo().getExecutionRoot().toPath();

                                      if (l.isExternal()) {
                                        return path.resolve("external")
                                            .resolve(p)
                                            .toAbsolutePath()
                                            .toString();

                                      } else {
                                        return path.resolve(p).toAbsolutePath().toString();
                                      }
                                    })
                                .collect(Collectors.toList());
                          })
                      .flatMap(List::stream)
                      .filter(Objects::nonNull)
                      .map(
                          f ->
                              LocalFileSystem.getInstance()
                                  .refreshAndFindFileByPath(FileUtil.toSystemIndependentName(f)))
                      .filter(Objects::nonNull)
                      .collect(Collectors.toSet()));
    } catch (RuntimeException e) {
      // something wrong... we do nothing
      return null;
    }

    if (sdkHome != null) {
      PythonSdkAdditionalData pythonSdkAdditionalData = new PythonSdkAdditionalData(null);
      pythonSdkAdditionalData.setAddedPathsFromVirtualFiles(sdkAdditionalPath);

      Sdk newSdk =
          setupSdk(
              ProjectJdkTable.getInstance().getAllJdks(),
              sdkHome,
              PythonSdkType.getInstance(),
              true,
              pythonSdkAdditionalData,
              homePath);
      if (newSdk != null) {
        addSdk(newSdk);
      }

      return newSdk;
    }

    return null;
  }

  @Override
  public boolean isDeprecatedSdk(Sdk sdk) {
    return false;
  }
}
