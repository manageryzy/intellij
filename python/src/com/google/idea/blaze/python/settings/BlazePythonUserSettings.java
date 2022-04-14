package com.google.idea.blaze.python.settings;

import com.google.common.collect.ImmutableMap;
import com.google.idea.blaze.base.bazel.BuildSystemProvider;
import com.google.idea.blaze.base.logging.LoggedSettingsProvider;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;

@State(name = "BlazePythonUserSettings", storages = @Storage("blaze.python.user.settings.xml"))
public class BlazePythonUserSettings
    implements PersistentStateComponent<
        com.google.idea.blaze.python.settings.BlazePythonUserSettings> {
  private String pythonSdkLabel = null;

  public static BlazePythonUserSettings getInstance() {
    return ServiceManager.getService(BlazePythonUserSettings.class);
  }

  private static boolean getDefaultJarCacheValue() {
    return BuildSystemProvider.defaultBuildSystem().buildSystem() == BuildSystemName.Blaze;
  }

  @Override
  public BlazePythonUserSettings getState() {
    return this;
  }

  @Override
  public void loadState(BlazePythonUserSettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  public String getPythonSdkLabel() {
    return pythonSdkLabel;
  }

  public void setPythonSdkLabel(String pythonSdkLabel) {
    this.pythonSdkLabel = pythonSdkLabel;
  }

  static class SettingsLogger implements LoggedSettingsProvider {
    @Override
    public String getNamespace() {
      return "BlazePythonUserSettings";
    }

    @Override
    public ImmutableMap<String, String> getApplicationSettings() {
      BlazePythonUserSettings settings = BlazePythonUserSettings.getInstance();

      ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
      builder.put("pythonSdkLabel", settings.pythonSdkLabel);
      return builder.build();
    }
  }
}
