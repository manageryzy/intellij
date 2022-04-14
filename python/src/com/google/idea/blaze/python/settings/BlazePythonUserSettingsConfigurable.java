package com.google.idea.blaze.python.settings;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.settings.ui.BlazeUserSettingsCompositeConfigurable;
import com.google.idea.blaze.base.ui.FileSelectorWithStoredHistory;
import com.google.idea.common.settings.AutoConfigurable;
import com.google.idea.common.settings.ConfigurableSetting;
import com.google.idea.common.settings.SearchableText;
import com.google.idea.common.settings.SettingComponent;
import com.intellij.openapi.options.UnnamedConfigurable;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.util.ui.SwingHelper;

import javax.swing.*;

public class BlazePythonUserSettingsConfigurable extends AutoConfigurable {

  public static final String PYTHON_TOOLCHAIN_TARGET_KEY = "bazel.python.toolchain";
  private static final ConfigurableSetting<?, ?> PYTHON_TOOLCHAIN_TARGET =
      setting("bazel python toolchain label")
          .getter(BlazePythonUserSettings::getPythonSdkLabel)
          .setter(BlazePythonUserSettings::setPythonSdkLabel)
          .componentFactory(
              fileSelector(PYTHON_TOOLCHAIN_TARGET_KEY, "Specify the buildifier binary path"));
  private static final ImmutableList<ConfigurableSetting<?, ?>> SETTINGS =
      ImmutableList.of(PYTHON_TOOLCHAIN_TARGET);

  private BlazePythonUserSettingsConfigurable() {
    super(SETTINGS);
  }

  private static ConfigurableSetting.Builder<BlazePythonUserSettings> setting(String label) {
    return ConfigurableSetting.builder(BlazePythonUserSettings::getInstance).label(label);
  }

  private static ConfigurableSetting.ComponentFactory<
          SettingComponent.LabeledComponent<String, FileSelectorWithStoredHistory>>
      fileSelector(String historyKey, String title) {
    return SettingComponent.LabeledComponent.factory(
        () -> FileSelectorWithStoredHistory.create(historyKey, title),
        s -> Strings.nullToEmpty(s.getText()).trim(),
        FileSelectorWithStoredHistory::setTextWithHistory);
  }

  private static GridConstraints defaultNoGrowConstraints(
      int rowIndex, int columnIndex, int rowSpan, int columnSpan) {
    return new GridConstraints(
        rowIndex,
        columnIndex,
        rowSpan,
        columnSpan,
        GridConstraints.ANCHOR_WEST,
        GridConstraints.FILL_NONE,
        GridConstraints.SIZEPOLICY_FIXED,
        GridConstraints.SIZEPOLICY_FIXED,
        null,
        null,
        null,
        0,
        false);
  }

  @Override
  public JComponent createComponent() {
    return SwingHelper.newLeftAlignedVerticalPanel(createVerticalPanel(PYTHON_TOOLCHAIN_TARGET));
  }

  static class UiContributor implements BlazeUserSettingsCompositeConfigurable.UiContributor {
    @Override
    public UnnamedConfigurable getConfigurable() {
      return new BlazePythonUserSettingsConfigurable();
    }

    @Override
    public ImmutableCollection<SearchableText> getSearchableText() {
      return SearchableText.collect(SETTINGS);
    }
  }
}
