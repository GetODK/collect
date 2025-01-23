package org.odk.collect.android.preferences.screens;

import android.content.Context;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.preferences.PreferenceVisibilityHandler;
import org.odk.collect.android.preferences.ProjectPreferencesViewModel;
import org.odk.collect.android.preferences.source.SettingsStore;
import org.odk.collect.android.utilities.AdminPasswordProvider;
import org.odk.collect.shared.settings.Settings;

import javax.inject.Inject;

public abstract class BaseProjectPreferencesFragment extends BasePreferencesFragment {

    @Inject
    AdminPasswordProvider adminPasswordProvider;

    @Inject
    ProjectPreferencesViewModel.Factory factory;

    @Inject
    PreferenceVisibilityHandler preferenceVisibilityHandler;

    protected ProjectPreferencesViewModel projectPreferencesViewModel;
    private Settings settings;
    private String projectId;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);
        projectPreferencesViewModel = new ViewModelProvider(requireActivity(), factory).get(ProjectPreferencesViewModel.class);

        projectId = projectsDataService.requireCurrentProject().getUuid();
        settings = settingsProvider.getUnprotectedSettings(projectId);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setPreferenceDataStore(new SettingsStore(settings));
    }

    @Override
    public void onViewCreated(@NotNull View view, @Nullable Bundle savedInstanceState) {
        preferenceVisibilityHandler.updatePreferencesVisibility(getPreferenceScreen(), projectPreferencesViewModel.getState().getValue().getValue());
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        super.onResume();
        settings.registerOnSettingChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        settings.unregisterOnSettingChangeListener(this);
    }

    @Override
    public void onSettingChanged(@NotNull String key) {
        settingsChangeHandler.onSettingChanged(projectId, settings.getAll().get(key), key);
    }
}
