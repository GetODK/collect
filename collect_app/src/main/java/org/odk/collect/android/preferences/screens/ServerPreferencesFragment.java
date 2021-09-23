/*
 * Copyright 2017 Shobhit
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.preferences.screens;

import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.analytics.Analytics;
import org.odk.collect.android.R;
import org.odk.collect.android.backgroundwork.FormUpdateScheduler;
import org.odk.collect.android.gdrive.GoogleAccountsManager;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.listeners.OnBackPressedListener;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.android.preferences.ServerPreferencesAdder;
import org.odk.collect.android.preferences.filters.ControlCharacterFilter;
import org.odk.collect.android.preferences.filters.WhitespaceFilter;
import org.odk.collect.android.preferences.keys.ProjectKeys;
import org.odk.collect.android.utilities.PlayServicesChecker;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.shared.strings.Md5;
import org.odk.collect.shared.strings.Validator;

import java.io.ByteArrayInputStream;

import javax.inject.Inject;

import static android.app.Activity.RESULT_OK;
import static org.odk.collect.android.analytics.AnalyticsEvents.SET_FALLBACK_SHEETS_URL;
import static org.odk.collect.android.preferences.keys.ProjectKeys.KEY_PROTOCOL;
import static org.odk.collect.android.preferences.keys.ProjectKeys.KEY_SELECTED_GOOGLE_ACCOUNT;
import static org.odk.collect.android.utilities.DialogUtils.showDialog;

public class ServerPreferencesFragment extends BaseProjectPreferencesFragment implements OnBackPressedListener {

    private static final int REQUEST_ACCOUNT_PICKER = 1000;

    private EditTextPreference passwordPreference;

    @Inject
    GoogleAccountsManager accountsManager;

    @Inject
    Analytics analytics;

    @Inject
    FormUpdateScheduler formUpdateScheduler;

    @Inject
    PermissionsProvider permissionsProvider;

    private Preference selectedGoogleAccountPreference;
    private boolean allowClickSelectedGoogleAccountPreference = true;

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);

        ((ProjectPreferencesActivity) context).setOnBackPressedListener(this);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.server_preferences, rootKey);
        initProtocolPrefs();
    }

    private void initProtocolPrefs() {
        ListPreference protocolPref = (ListPreference) findPreference(KEY_PROTOCOL);
        protocolPref.setSummary(protocolPref.getEntry());
        protocolPref.setOnPreferenceChangeListener((preference, newValue) -> {
            if (preference.getKey().equals(KEY_PROTOCOL)) {
                String stringValue = (String) newValue;
                ListPreference lpref = (ListPreference) preference;
                String oldValue = lpref.getValue();
                lpref.setValue(stringValue);

                if (!newValue.equals(oldValue)) {
                    getPreferenceScreen().removeAll();
                    addPreferencesFromResource(R.xml.server_preferences);
                    initProtocolPrefs();
                }
            }
            return true;
        });

        if (ProjectKeys.PROTOCOL_GOOGLE_SHEETS.equals(protocolPref.getValue())) {
            addGooglePreferences();
        } else {
            addServerPreferences();
        }
    }

    public void addServerPreferences() {
        if (!new ServerPreferencesAdder(this).add()) {
            return;
        }
        EditTextPreference serverUrlPreference = findPreference(ProjectKeys.KEY_SERVER_URL);
        EditTextPreference usernamePreference = findPreference(ProjectKeys.KEY_USERNAME);
        passwordPreference = findPreference(ProjectKeys.KEY_PASSWORD);

        serverUrlPreference.setOnPreferenceChangeListener(createChangeListener());
        serverUrlPreference.setSummary(serverUrlPreference.getText());

        usernamePreference.setOnPreferenceChangeListener(createChangeListener());
        usernamePreference.setSummary(usernamePreference.getText());

        usernamePreference.setOnBindEditTextListener(editText -> {
            editText.setFilters(new InputFilter[]{new ControlCharacterFilter()});
        });

        passwordPreference.setOnPreferenceChangeListener(createChangeListener());
        maskPasswordSummary(passwordPreference.getText());

        passwordPreference.setOnBindEditTextListener(editText -> {
            editText.setFilters(new InputFilter[]{new ControlCharacterFilter()});
        });
    }

    public void addGooglePreferences() {
        addPreferencesFromResource(R.xml.google_preferences);
        selectedGoogleAccountPreference = findPreference(KEY_SELECTED_GOOGLE_ACCOUNT);

        EditTextPreference googleSheetsUrlPreference = (EditTextPreference) findPreference(
                ProjectKeys.KEY_GOOGLE_SHEETS_URL);
        googleSheetsUrlPreference.setOnBindEditTextListener(editText -> editText.setFilters(new InputFilter[] {new ControlCharacterFilter(), new WhitespaceFilter() }));
        googleSheetsUrlPreference.setOnPreferenceChangeListener(createChangeListener());

        String currentGoogleSheetsURL = googleSheetsUrlPreference.getText();
        if (currentGoogleSheetsURL != null && currentGoogleSheetsURL.length() > 0) {
            googleSheetsUrlPreference.setSummary(currentGoogleSheetsURL + "\n\n"
                    + getString(R.string.google_sheets_url_hint));
        }
        initAccountPreferences();
    }

    public void initAccountPreferences() {
        selectedGoogleAccountPreference.setSummary(accountsManager.getLastSelectedAccountIfValid());
        selectedGoogleAccountPreference.setOnPreferenceClickListener(preference -> {
            if (allowClickSelectedGoogleAccountPreference) {
                if (new PlayServicesChecker().isGooglePlayServicesAvailable(getActivity())) {
                    allowClickSelectedGoogleAccountPreference = false;
                    requestAccountsPermission();
                } else {
                    new PlayServicesChecker().showGooglePlayServicesAvailabilityErrorDialog(getActivity());
                }
            }
            return true;
        });
    }

    private void requestAccountsPermission() {
        permissionsProvider.requestGetAccountsPermission(getActivity(), new PermissionListener() {
            @Override
            public void granted() {
                Intent intent = accountsManager.getAccountChooserIntent();
                startActivityForResult(intent, REQUEST_ACCOUNT_PICKER);
            }

            @Override
            public void denied() {
                allowClickSelectedGoogleAccountPreference = true;
            }
        });
    }

    private Preference.OnPreferenceChangeListener createChangeListener() {
        return (preference, newValue) -> {
            switch (preference.getKey()) {
                case ProjectKeys.KEY_SERVER_URL:
                    String url = newValue.toString();

                    if (Validator.isUrlValid(url)) {
                        preference.setSummary(newValue.toString());
                    } else {
                        ToastUtils.showShortToast(requireContext(), R.string.url_error);
                        return false;
                    }
                    break;

                case ProjectKeys.KEY_USERNAME:
                    String username = newValue.toString();

                    // do not allow leading and trailing whitespace
                    if (!username.equals(username.trim())) {
                        ToastUtils.showShortToast(requireContext(), R.string.username_error_whitespace);
                        return false;
                    }

                    preference.setSummary(username);
                    return true;

                case ProjectKeys.KEY_PASSWORD:
                    String pw = newValue.toString();

                    // do not allow leading and trailing whitespace
                    if (!pw.equals(pw.trim())) {
                        ToastUtils.showShortToast(requireContext(), R.string.password_error_whitespace);
                        return false;
                    }

                    maskPasswordSummary(pw);
                    break;

                case ProjectKeys.KEY_GOOGLE_SHEETS_URL:
                    url = newValue.toString();

                    if (Validator.isUrlValid(url)) {
                        preference.setSummary(url + "\n\n" + getString(R.string.google_sheets_url_hint));

                        String urlHash = Md5.getMd5Hash(new ByteArrayInputStream(url.getBytes()));
                        analytics.logEvent(SET_FALLBACK_SHEETS_URL, urlHash);
                    } else if (url.length() == 0) {
                        preference.setSummary(getString(R.string.google_sheets_url_hint));
                    } else {
                        ToastUtils.showShortToast(requireContext(), R.string.url_error);
                        return false;
                    }
                    break;
            }
            return true;
        };
    }

    private void maskPasswordSummary(String password) {
        passwordPreference.setSummary(password != null && password.length() > 0
                ? "********"
                : "");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_ACCOUNT_PICKER:
                if (resultCode == RESULT_OK && data != null && data.getExtras() != null) {
                    String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
                    accountsManager.selectAccount(accountName);
                    selectedGoogleAccountPreference.setSummary(accountName);
                }
                allowClickSelectedGoogleAccountPreference = true;
                break;
        }
    }

    private void runGoogleAccountValidation() {
        String account = settingsProvider.getGeneralSettings().getString(KEY_SELECTED_GOOGLE_ACCOUNT);
        String protocol = settingsProvider.getGeneralSettings().getString(KEY_PROTOCOL);

        if (TextUtils.isEmpty(account) && protocol.equals(ProjectKeys.PROTOCOL_GOOGLE_SHEETS)) {

            AlertDialog alertDialog = new AlertDialog.Builder(getActivity())
                    .setIcon(android.R.drawable.ic_dialog_info)
                    .setTitle(R.string.missing_google_account_dialog_title)
                    .setMessage(R.string.missing_google_account_dialog_desc)
                    .setPositiveButton(getString(R.string.ok), (dialog, which) -> dialog.dismiss())
                    .create();

            showDialog(alertDialog, getActivity());
        } else {
            continueOnBackPressed();
        }
    }

    private void continueOnBackPressed() {
        ((ProjectPreferencesActivity) getActivity()).setOnBackPressedListener(null);
        getActivity().onBackPressed();
    }

    @Override
    public void doBack() {
        runGoogleAccountValidation();
    }
}
