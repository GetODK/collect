/*
 * Copyright (C) 2017 Shobhit
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.utilities;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;

import java.util.ArrayList;
import java.util.Collection;

import timber.log.Timber;

import static android.content.Context.MODE_PRIVATE;
import static org.odk.collect.android.preferences.AdminKeys.KEY_ADMIN_PW;
import static org.odk.collect.android.preferences.AdminPreferencesFragment.ADMIN_PREFERENCES;
import static org.odk.collect.android.preferences.PreferenceKeys.ALL_GENERAL_KEYS;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_APP_LANGUAGE;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_AUTOSEND_NETWORK;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_AUTOSEND_WIFI;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_CONSTRAINT_BEHAVIOR;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_DELETE_AFTER_SEND;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_FONT_SIZE;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_FORMLIST_URL;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_LAST_VERSION;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_MAP_BASEMAP;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_MAP_SDK;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_NAVIGATION;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_PASSWORD;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_PROTOCOL;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_SERVER_URL;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_SHOW_SPLASH;
import static org.odk.collect.android.preferences.PreferenceKeys.KEY_SUBMISSION_URL;

/**
 * Created by shobhit on 12/4/17.
 */

public class SharedPreferencesUtils {

    private final Context context = Collect.getInstance();
    private final SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(context);
    private final SharedPreferences.Editor editor = sharedPrefs.edit();
    private final SharedPreferences adminSharedPrefs =
            Collect.getInstance().getSharedPreferences(ADMIN_PREFERENCES, MODE_PRIVATE);
    private final SharedPreferences.Editor adminEditor = adminSharedPrefs.edit();

    String getJSONFromPreferences(Collection<String> keys) throws JSONException {
        SharedPreferencesUtils obj = new SharedPreferencesUtils();
        keys.addAll(ALL_GENERAL_KEYS);
        JSONObject sharedPrefJson = obj.getModifiedPrefs(keys);
        Timber.i(sharedPrefJson.toString());
        return sharedPrefJson.toString();
    }

    private JSONObject getModifiedPrefs(Collection<String> keys) throws JSONException {
        JSONObject jsonObject = new JSONObject();

        for (String key : keys) {
            String stringValue;
            String defaultStringValue;

            // checking for admin password
            if (key.equals(KEY_ADMIN_PW)) {
                stringValue = adminSharedPrefs.getString(key, "");
                if (!stringValue.equals("")) {
                    jsonObject.put(key, stringValue);

                    //skip further checking
                    continue;
                }
            }

            try {
                stringValue = getStringValue(key);
                defaultStringValue = getDefaultStringValue(key);
                if (!stringValue.equals(defaultStringValue)) {
                    jsonObject.put(key, stringValue);
                }
            } catch (ClassCastException e) {
                try {
                    boolean booleanValue = getBooleanValue(key);
                    boolean defaultBooleanValue = getDefaultBooleanValue(key);
                    if (booleanValue != defaultBooleanValue) {
                        jsonObject.put(key, booleanValue);
                    }
                } catch (ClassCastException e1) {
                    long longValue = getLongValue(key);
                    long defaultLongValue = getDefaultLongValue(key);
                    if (longValue != defaultLongValue) {
                        jsonObject.put(key, longValue);
                    }
                }
            }
        }
        return jsonObject;
    }

    private String getDefaultStringValue(String key) {
        String defValue;
        switch (key) {
            case KEY_SERVER_URL:
                defValue = context.getString(R.string.default_server_url);
                break;
            case KEY_FORMLIST_URL:
                defValue = context.getString(R.string.default_odk_formlist);
                break;
            case KEY_SUBMISSION_URL:
                defValue = context.getString(R.string.default_odk_submission);
                break;
            case KEY_APP_LANGUAGE:
                defValue = "";
                break;
            case KEY_NAVIGATION:
                defValue = "swipe";
                break;
            case KEY_CONSTRAINT_BEHAVIOR:
                defValue = "on_swipe";
                break;
            case KEY_FONT_SIZE:
                defValue = "21";
                break;
            case KEY_PROTOCOL:
                defValue = "odk_default";
                break;
            case KEY_MAP_SDK:
                defValue = "google_maps";
                break;
            case KEY_MAP_BASEMAP:
                defValue = "streets";
                break;
            default:
                defValue = "";
        }
        return defValue;
    }

    private boolean getDefaultBooleanValue(String key) {
        boolean defValue;
        switch (key) {
            case KEY_AUTOSEND_WIFI:
            case KEY_AUTOSEND_NETWORK:
            case KEY_DELETE_AFTER_SEND:
            case KEY_SHOW_SPLASH:
                defValue = false;
                break;
            default:
                defValue = true;
        }
        return defValue;
    }

    private String getStringValue(String key) {
        return sharedPrefs.getString(key, getDefaultStringValue(key));
    }

    private boolean getBooleanValue(String key) {
        return sharedPrefs.getBoolean(key, getDefaultBooleanValue(key));
    }

    private long getLongValue(String key) {
        return sharedPrefs.getLong(key, getDefaultLongValue(key));
    }

    private long getDefaultLongValue(String key) {
        long defValue = 0;
        switch (key) {
            case KEY_LAST_VERSION:
                try {
                    defValue = (long) context.getPackageManager().getPackageInfo(context.getPackageName(),
                            PackageManager.GET_META_DATA).versionCode;
                } catch (PackageManager.NameNotFoundException e) {
                    Timber.e(e, "Unable to get package info");
                }
                break;
            default:
                defValue = 0;
        }
        return defValue;
    }

    public void savePreferencesFromJSON(JSONObject settingsJson) throws JSONException {

        Collection<String> allKeys = getAllKeys();
        for (String key : allKeys) {

            if (key.equals(KEY_ADMIN_PW) && settingsJson.has(key)) {
                adminEditor.putString(key, settingsJson.getString(key));
                adminEditor.apply();

                // skip further checking
                continue;
            }

            if (settingsJson.has(key)) {
                try {
                    editor.putString(key, settingsJson.getString(key));
                    Timber.i(key + " : string (applied)");
                } catch (Exception e) {
                    try {
                        editor.putBoolean(key, settingsJson.getBoolean(key));
                        Timber.i(key, " : boolean (applied)");
                    } catch (Exception e1) {
                        editor.putLong(key, settingsJson.getLong(key));
                        Timber.i(key, " : long (applied)");
                    }
                }
            } else {
                try {
                    String stringValue = getStringValue(key);
                    editor.putString(key, getDefaultStringValue(key));
                    Timber.i(key + " : string (default)");
                } catch (ClassCastException e) {
                    try {
                        boolean booleanValue = getBooleanValue(key);
                        editor.putBoolean(key, getDefaultBooleanValue(key));
                        Timber.i(key + " : boolean (default)");
                    } catch (ClassCastException e1) {
                        long longValue = getLongValue(key);
                        editor.putLong(key, getDefaultLongValue(key));
                        Timber.i(key + " : long (default)");
                    }
                }
            }
        }
        editor.apply();

        //settings import confirmation toast
        ToastUtils.showLongToast(context.getString(R.string.successfully_imported_settings));
    }

    private Collection<String> getAllKeys() {
        Collection<String> keys = new ArrayList<>();
        keys.addAll(ALL_GENERAL_KEYS);
        keys.add(KEY_ADMIN_PW);
        keys.add(KEY_PASSWORD);
        return keys;
    }
}

