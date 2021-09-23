package org.odk.collect.android.preferences.screens;

import static org.odk.collect.android.logic.PropertyManager.PROPMGR_DEVICE_ID;
import static org.odk.collect.android.logic.PropertyManager.PROPMGR_PHONE_NUMBER;
import static org.odk.collect.android.preferences.keys.ProjectKeys.KEY_METADATA_EMAIL;
import static org.odk.collect.android.preferences.keys.ProjectKeys.KEY_METADATA_PHONENUMBER;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.Nullable;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.R;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.permissions.PermissionsProvider;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.shared.strings.Validator;

import javax.inject.Inject;

public class FormMetadataPreferencesFragment extends BaseProjectPreferencesFragment {

    @Inject
    PermissionsProvider permissionsProvider;

    @Inject
    PropertyManager propertyManager;

    private Preference emailPreference;
    private EditTextPreference phonePreference;
    private Preference deviceIDPreference;

    @Override
    public void onAttach(@NotNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        setPreferencesFromResource(R.xml.form_metadata_preferences, rootKey);

        emailPreference = findPreference(KEY_METADATA_EMAIL);
        phonePreference = findPreference(KEY_METADATA_PHONENUMBER);
        deviceIDPreference = findPreference(PROPMGR_DEVICE_ID);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setupPrefs();

        if (permissionsProvider.isReadPhoneStatePermissionGranted()) {
            phonePreference.setSummaryProvider(new PropertyManagerPropertySummaryProvider(propertyManager, PROPMGR_PHONE_NUMBER));
        } else if (savedInstanceState == null) {
            permissionsProvider.requestReadPhoneStatePermission(getActivity(), true, new PermissionListener() {
                @Override
                public void granted() {
                    phonePreference.setSummaryProvider(new PropertyManagerPropertySummaryProvider(propertyManager, PROPMGR_PHONE_NUMBER));
                }

                @Override
                public void denied() {
                }
            });
        }
    }

    private void setupPrefs() {
        emailPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String newValueString = newValue.toString();
            if (!newValueString.isEmpty() && !Validator.isEmailAddressValid(newValueString)) {
                ToastUtils.showLongToast(requireContext(), R.string.invalid_email_address);
                return false;
            }

            return true;
        });

        phonePreference.setOnBindEditTextListener(editText -> editText.setInputType(EditorInfo.TYPE_CLASS_PHONE));
        deviceIDPreference.setSummaryProvider(new PropertyManagerPropertySummaryProvider(propertyManager, PROPMGR_DEVICE_ID));
    }

    private class PropertyManagerPropertySummaryProvider implements Preference.SummaryProvider<EditTextPreference> {

        private final PropertyManager propertyManager;
        private final String propertyKey;

        PropertyManagerPropertySummaryProvider(PropertyManager propertyManager, String propertyName) {
            this.propertyManager = propertyManager;
            this.propertyKey = propertyName;
        }

        @Override
        public CharSequence provideSummary(EditTextPreference preference) {
            String value = propertyManager.reload().getSingularProperty(propertyKey);
            if (!TextUtils.isEmpty(value)) {
                return value;
            } else {
                return getString(R.string.preference_not_available);
            }
        }
    }
}
