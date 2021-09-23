package org.odk.collect.android.geo;

import static org.odk.collect.android.preferences.keys.ProjectKeys.KEY_GOOGLE_MAP_STYLE;
import static org.odk.collect.android.preferences.keys.ProjectKeys.KEY_REFERENCE_LAYER;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;

import androidx.preference.Preference;

import com.google.android.gms.maps.GoogleMap;
import com.google.common.collect.ImmutableSet;

import org.odk.collect.android.R;
import org.odk.collect.android.geo.MbtilesFile.LayerType;
import org.odk.collect.android.preferences.PrefUtils;
import org.odk.collect.android.utilities.PlayServicesChecker;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.shared.Settings;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

class GoogleMapConfigurator implements MapConfigurator {
    private final String prefKey;
    private final int sourceLabelId;
    private final GoogleMapTypeOption[] options;

    /** Constructs a configurator with a few Google map type options to choose from. */
    GoogleMapConfigurator(String prefKey, int sourceLabelId, GoogleMapTypeOption... options) {
        this.prefKey = prefKey;
        this.sourceLabelId = sourceLabelId;
        this.options = options;
    }

    @Override public boolean isAvailable(Context context) {
        return isGoogleMapsSdkAvailable(context) && isGooglePlayServicesAvailable(context);
    }

    @Override public void showUnavailableMessage(Context context) {
        if (!isGoogleMapsSdkAvailable(context)) {
            ToastUtils.showLongToast(context, context.getString(
                R.string.basemap_source_unavailable, context.getString(sourceLabelId)));
        }
        if (!isGooglePlayServicesAvailable(context)) {
            new PlayServicesChecker().showGooglePlayServicesAvailabilityErrorDialog(context);
        }
    }

    private boolean isGoogleMapsSdkAvailable(Context context) {
        // The Google Maps SDK for Android requires OpenGL ES version 2.
        // See https://developers.google.com/maps/documentation/android-sdk/config
        return ((ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE))
            .getDeviceConfigurationInfo().reqGlEsVersion >= 0x20000;
    }

    private boolean isGooglePlayServicesAvailable(Context context) {
        return new PlayServicesChecker().isGooglePlayServicesAvailable(context);
    }

    @Override public MapFragment createMapFragment(Context context) {
        return new GoogleMapFragment();
    }

    @Override public List<Preference> createPrefs(Context context) {
        int[] labelIds = new int[options.length];
        String[] values = new String[options.length];
        for (int i = 0; i < options.length; i++) {
            labelIds[i] = options[i].labelId;
            values[i] = Integer.toString(options[i].mapType);
        }
        String prefTitle = context.getString(
            R.string.map_style_label, context.getString(sourceLabelId));
        return Collections.singletonList(PrefUtils.createListPref(
            context, prefKey, prefTitle, labelIds, values
        ));
    }

    @Override public Set<String> getPrefKeys() {
        return prefKey.isEmpty() ? ImmutableSet.of(KEY_REFERENCE_LAYER) :
            ImmutableSet.of(prefKey, KEY_REFERENCE_LAYER);
    }

    @Override public Bundle buildConfig(Settings prefs) {
        Bundle config = new Bundle();
        config.putInt(GoogleMapFragment.KEY_MAP_TYPE,
            PrefUtils.getInt(KEY_GOOGLE_MAP_STYLE, GoogleMap.MAP_TYPE_NORMAL));
        config.putString(GoogleMapFragment.KEY_REFERENCE_LAYER,
            prefs.getString(KEY_REFERENCE_LAYER));
        return config;
    }

    @Override public boolean supportsLayer(File file) {
        // GoogleMapFragment supports only raster tiles.
        return MbtilesFile.readLayerType(file) == LayerType.RASTER;
    }

    @Override public String getDisplayName(File file) {
        String name = MbtilesFile.readName(file);
        return name != null ? name : file.getName();
    }

    static class GoogleMapTypeOption {
        final int mapType;
        final int labelId;

        GoogleMapTypeOption(int mapType, int labelId) {
            this.mapType = mapType;
            this.labelId = labelId;
        }
    }
}
