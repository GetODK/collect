package org.odk.collect.android.preferences;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;

public final class PreferenceKeys {

    // aggregate_preferences.xml
    public static final String KEY_SERVER_URL               = "server_url";
    public static final String KEY_USERNAME                 = "username";
    public static final String KEY_PASSWORD                 = "password";

    // form_management_preferences.xml
    public static final String KEY_AUTOSEND                 = "autosend";
    public static final String KEY_DELETE_AFTER_SEND        = "delete_send";
    public static final String KEY_COMPLETED_DEFAULT        = "default_completed";
    public static final String KEY_CONSTRAINT_BEHAVIOR      = "constraint_behavior";
    public static final String KEY_HIGH_RESOLUTION          = "high_resolution";
    public static final String KEY_IMAGE_SIZE               = "image_size";
    public static final String KEY_GUIDANCE_HINT            = "guidance_hint";
    public static final String KEY_INSTANCE_SYNC            = "instance_sync";
    public static final String KEY_PERIODIC_FORM_UPDATES_CHECK = "periodic_form_updates_check";
    public static final String KEY_AUTOMATIC_UPDATE         = "automatic_update";
    public static final String KEY_HIDE_OLD_FORM_VERSIONS   = "hide_old_form_versions";

    // form_metadata_preferences.xml
    public static final String KEY_METADATA_USERNAME        = "metadata_username";
    public static final String KEY_METADATA_PHONENUMBER     = "metadata_phonenumber";
    public static final String KEY_METADATA_EMAIL           = "metadata_email";

    // google_preferences.xml
    public static final String KEY_SELECTED_GOOGLE_ACCOUNT  = "selected_google_account";
    public static final String KEY_GOOGLE_SHEETS_URL        = "google_sheets_url";

    // identity_preferences.xml
    static final String KEY_FORM_METADATA                   = "form_metadata";
    public static final String KEY_ANALYTICS                = "analytics";

    // other_preferences.xml
    public static final String KEY_FORMLIST_URL             = "formlist_url";
    public static final String KEY_SUBMISSION_URL           = "submission_url";

    // server_preferences.xml
    public static final String KEY_PROTOCOL                 = "protocol";

    // user_interface_preferences.xml
    public static final String KEY_APP_THEME                = "appTheme";
    public static final String KEY_APP_LANGUAGE             = "app_language";
    public static final String KEY_FONT_SIZE                = "font_size";
    public static final String KEY_NAVIGATION               = "navigation";
    public static final String KEY_SHOW_SPLASH              = "showSplash";
    public static final String KEY_SPLASH_PATH              = "splashPath";
    public static final String KEY_MAP_SDK                  = "map_sdk_behavior";
    public static final String KEY_MAP_BASEMAP              = "map_basemap_behavior";

    // other keys
    public static final String ACTIVITY_LOGGER_ANALYTICS    = "activity_logger_event";
    public static final String KEY_LAST_VERSION             = "lastVersion";
    public static final String KEY_FIRST_RUN                = "firstRun";
    /** Whether any existing username and email values have been migrated to form metadata */
    static final String KEY_METADATA_MIGRATED               = "metadata_migrated";
    static final String KEY_AUTOSEND_WIFI                   = "autosend_wifi";
    static final String KEY_AUTOSEND_NETWORK                = "autosend_network";

    // values
    public static final String NAVIGATION_SWIPE             = "swipe";
    public static final String CONSTRAINT_BEHAVIOR_ON_SWIPE = "on_swipe";
    public static final String NAVIGATION_BUTTONS           = "buttons";
    public static final String GOOGLE_MAPS                 = "google_maps";     // smap make public
    private static final String AUTOSEND_OFF                = "off";
    private static final String GUIDANCE_HINT_OFF           = "no";
    static final String GOOGLE_MAPS_BASEMAP_DEFAULT         = "streets";
    static final String OSM_BASEMAP_KEY                     = "osmdroid";
    static final String OSM_MAPS_BASEMAP_DEFAULT            = "openmap_streets";

    // start smap
    public static final String KEY_SMAP_REVIEW_FINAL = "review_final";    // Allow review of Form after finalising
    public static final String KEY_SMAP_USER_LOCATION = "smap_gps_trail";    // Record a user trail
    public static final String KEY_SMAP_LOCATION_TRIGGER = "location_trigger";  // Enable triggering of forms by location
    public static final String KEY_SMAP_ODK_STYLE_MENUS = "odk_style_menus";  // Show ODK style menus as well as refresh
    public static final String KEY_SMAP_ODK_INSTANCENAME = "odk_instancename";  // Allow user to change instance name
    public static final String KEY_SMAP_ODK_ADMIN_MENU = "odk_admin_menu";  // Show ODK admin menu
    public static final String KEY_SMAP_OVERRIDE_SYNC = "smap_override_sync";  // Override the local settings for synchronisation
    public static final String KEY_SMAP_OVERRIDE_LOCATION = "smap_override_location";  // Override the local settings for user trail
    public static final String KEY_SMAP_OVERRIDE_DELETE = "smap_override_del";  // Override the local settings for delete after send
    public static final String KEY_SMAP_REGISTRATION_ID = "registration_id";  // Android notifications id
    public static final String KEY_SMAP_REGISTRATION_SERVER = "registration_server";  // Server name that has been registered
    public static final String KEY_SMAP_REGISTRATION_USER = "registration_user";  // User name that has been registered
    // end smap

    private static HashMap<String, Object> getHashMap() {
        HashMap<String, Object> hashMap = new HashMap<>();
        // aggregate_preferences.xml
        hashMap.put(KEY_SERVER_URL,                 Collect.getInstance().getString(R.string.default_server_url));
        hashMap.put(KEY_USERNAME,                   "");
        // form_management_preferences.xml
        hashMap.put(KEY_AUTOSEND,                   AUTOSEND_OFF);
        hashMap.put(KEY_GUIDANCE_HINT,              GUIDANCE_HINT_OFF);
        hashMap.put(KEY_DELETE_AFTER_SEND,          false);
        hashMap.put(KEY_COMPLETED_DEFAULT,          true);
        hashMap.put(KEY_CONSTRAINT_BEHAVIOR,        CONSTRAINT_BEHAVIOR_ON_SWIPE);
        hashMap.put(KEY_HIGH_RESOLUTION,            true);
        hashMap.put(KEY_IMAGE_SIZE,                 "original_image_size");
        hashMap.put(KEY_INSTANCE_SYNC,              true);
        hashMap.put(KEY_PERIODIC_FORM_UPDATES_CHECK, "never");
        hashMap.put(KEY_AUTOMATIC_UPDATE,           false);
        hashMap.put(KEY_HIDE_OLD_FORM_VERSIONS,     true);
        // form_metadata_preferences.xml
        hashMap.put(KEY_METADATA_USERNAME,          "");
        hashMap.put(KEY_METADATA_PHONENUMBER,       "");
        hashMap.put(KEY_METADATA_EMAIL,             "");
        // google_preferences.xml
        hashMap.put(KEY_SELECTED_GOOGLE_ACCOUNT,    "");
        hashMap.put(KEY_GOOGLE_SHEETS_URL,          "");
        // identity_preferences.xml
        hashMap.put(KEY_ANALYTICS,                  true);
        // other_preferences.xml
        hashMap.put(KEY_FORMLIST_URL,               Collect.getInstance().getString(R.string.default_odk_formlist));
        hashMap.put(KEY_SUBMISSION_URL,             Collect.getInstance().getString(R.string.default_odk_submission));
        // server_preferences.xml
        hashMap.put(KEY_PROTOCOL,                   Collect.getInstance().getString(R.string.protocol_odk_default));
        // user_interface_preferences.xml
        hashMap.put(KEY_APP_THEME,                  Collect.getInstance().getString(R.string.app_theme_light));
        hashMap.put(KEY_APP_LANGUAGE,               "");
        hashMap.put(KEY_FONT_SIZE,                  Collect.DEFAULT_FONTSIZE);
        hashMap.put(KEY_NAVIGATION,                 NAVIGATION_SWIPE);
        hashMap.put(KEY_SHOW_SPLASH,                false);
        hashMap.put(KEY_SPLASH_PATH,                Collect.getInstance().getString(R.string.default_splash_path));
        hashMap.put(KEY_MAP_SDK,                    GOOGLE_MAPS);
        hashMap.put(KEY_MAP_BASEMAP,                GOOGLE_MAPS_BASEMAP_DEFAULT);

        // start smap
        hashMap.put(KEY_SMAP_REVIEW_FINAL, true);
        hashMap.put(KEY_SMAP_USER_LOCATION, false);
        hashMap.put(KEY_SMAP_LOCATION_TRIGGER, true);
        hashMap.put(KEY_SMAP_ODK_STYLE_MENUS, true);
        hashMap.put(KEY_SMAP_ODK_INSTANCENAME, false);
        hashMap.put(KEY_SMAP_ODK_ADMIN_MENU, false);

        hashMap.put(KEY_SMAP_OVERRIDE_SYNC, false);
        hashMap.put(KEY_SMAP_OVERRIDE_DELETE, false);
        hashMap.put(KEY_SMAP_OVERRIDE_LOCATION, false);
        hashMap.put(KEY_SMAP_REGISTRATION_ID, "");
        hashMap.put(KEY_SMAP_REGISTRATION_SERVER, "");
        hashMap.put(KEY_SMAP_REGISTRATION_USER, "");
        // end smap

        return hashMap;
    }

    static final Collection<String> KEYS_WE_SHOULD_NOT_RESET = Arrays.asList(
            KEY_LAST_VERSION,
            KEY_FIRST_RUN,
            KEY_METADATA_MIGRATED,
            KEY_AUTOSEND_WIFI,
            KEY_AUTOSEND_NETWORK,
            ACTIVITY_LOGGER_ANALYTICS
    );

    public static final HashMap<String, Object> GENERAL_KEYS = getHashMap();

    private PreferenceKeys() {

    }

}
