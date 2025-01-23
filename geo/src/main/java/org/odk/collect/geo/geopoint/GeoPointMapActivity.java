/*
 * Copyright (C) 2011 University of Washington
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

package org.odk.collect.geo.geopoint;

import static org.odk.collect.geo.Constants.EXTRA_DRAGGABLE_ONLY;
import static org.odk.collect.geo.Constants.EXTRA_READ_ONLY;
import static org.odk.collect.geo.Constants.EXTRA_RETAIN_MOCK_ACCURACY;
import static org.odk.collect.geo.GeoActivityUtils.requireLocationPermissions;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;

import org.odk.collect.androidshared.ui.DialogFragmentUtils;
import org.odk.collect.androidshared.ui.FragmentFactoryBuilder;
import org.odk.collect.androidshared.ui.ToastUtils;
import org.odk.collect.async.Scheduler;
import org.odk.collect.externalapp.ExternalAppUtils;
import org.odk.collect.geo.GeoDependencyComponentProvider;
import org.odk.collect.geo.GeoUtils;
import org.odk.collect.geo.R;
import org.odk.collect.maps.MapFragment;
import org.odk.collect.maps.MapFragmentFactory;
import org.odk.collect.maps.MapPoint;
import org.odk.collect.maps.layers.OfflineMapLayersPickerBottomSheetDialogFragment;
import org.odk.collect.maps.layers.ReferenceLayerRepository;
import org.odk.collect.maps.markers.MarkerDescription;
import org.odk.collect.maps.markers.MarkerIconDescription;
import org.odk.collect.settings.SettingsProvider;
import org.odk.collect.strings.localization.LocalizedActivity;
import org.odk.collect.webpage.ExternalWebPageHelper;

import java.text.DecimalFormat;

import javax.inject.Inject;

import timber.log.Timber;

/**
 * Allow the user to indicate a location by placing a marker on a map, either
 * by touching a point on the map or by tapping a button to place the marker
 * at the current location (obtained from GPS or other location sensors).
 */
public class GeoPointMapActivity extends LocalizedActivity {

    public static final String POINT_KEY = "point";

    public static final String IS_DRAGGED_KEY = "is_dragged";
    public static final String CAPTURE_LOCATION_KEY = "capture_location";
    public static final String FOUND_FIRST_LOCATION_KEY = "found_first_location";
    public static final String SET_CLEAR_KEY = "set_clear";
    public static final String POINT_FROM_INTENT_KEY = "point_from_intent";
    public static final String INTENT_READ_ONLY_KEY = "intent_read_only";
    public static final String INTENT_DRAGGABLE_KEY = "intent_draggable";
    public static final String IS_POINT_LOCKED_KEY = "is_point_locked";

    public static final String PLACE_MARKER_BUTTON_ENABLED_KEY = "place_marker_button_enabled";
    public static final String ZOOM_BUTTON_ENABLED_KEY = "zoom_button_enabled";
    public static final String CLEAR_BUTTON_ENABLED_KEY = "clear_button_enabled";
    public static final String LOCATION_STATUS_VISIBILITY_KEY = "location_status_visibility";
    public static final String LOCATION_INFO_VISIBILITY_KEY = "location_info_visibility";

    public static final String EXTRA_LOCATION = "gp";

    protected Bundle previousState;

    @Inject
    MapFragmentFactory mapFragmentFactory;

    @Inject
    ReferenceLayerRepository referenceLayerRepository;

    @Inject
    Scheduler scheduler;

    @Inject
    SettingsProvider settingsProvider;

    @Inject
    ExternalWebPageHelper externalWebPageHelper;

    private MapFragment map;
    private int featureId = -1;  // will be a positive featureId once map is ready

    private TextView locationStatus;
    private TextView locationInfo;

    private MapPoint location;
    private ImageButton placeMarkerButton;

    private boolean isDragged;

    private ImageButton zoomButton;
    private ImageButton clearButton;

    private boolean captureLocation;
    private boolean foundFirstLocation;

    /**
     * True if a tap on the clear button removed an existing marker and
     * no new marker has been placed.
     */
    private boolean setClear;

    /** True if the current point came from the intent. */
    private boolean pointFromIntent;

    /** True if the intent requested for the point to be read-only. */
    private boolean intentReadOnly;

    /** True if the intent requested for the marker to be draggable. */
    private boolean intentDraggable;

    /** While true, the point cannot be moved by dragging or long-pressing. */
    private boolean isPointLocked;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ((GeoDependencyComponentProvider) getApplication()).getGeoDependencyComponent().inject(this);
        getSupportFragmentManager().setFragmentFactory(new FragmentFactoryBuilder()
                .forClass(MapFragment.class, () -> (Fragment) mapFragmentFactory.createMapFragment())
                .forClass(OfflineMapLayersPickerBottomSheetDialogFragment.class, () -> new OfflineMapLayersPickerBottomSheetDialogFragment(getActivityResultRegistry(), referenceLayerRepository, scheduler, settingsProvider, externalWebPageHelper))
                .build()
        );
        super.onCreate(savedInstanceState);

        requireLocationPermissions(this);

        previousState = savedInstanceState;

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        try {
            setContentView(R.layout.geopoint_layout);
        } catch (NoClassDefFoundError e) {
            Timber.e(e, "Google maps not accessible due to: %s ", e.getMessage());
            ToastUtils.showShortToast(this, org.odk.collect.strings.R.string.google_play_services_error_occured);
            finish();
            return;
        }

        locationStatus = findViewById(R.id.location_status);
        locationInfo = findViewById(R.id.location_info);
        placeMarkerButton = findViewById(R.id.place_marker);
        zoomButton = findViewById(R.id.zoom);

        MapFragment mapFragment = ((FragmentContainerView) findViewById(R.id.map_container)).getFragment();
        mapFragment.init(this::initMap, this::finish);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (map == null) {
            // initMap() is called asynchronously, so map can be null if the activity
            // is stopped (e.g. by screen rotation) before initMap() gets to run.
            // In this case, preserve any provided instance state.
            if (previousState != null) {
                state.putAll(previousState);
            }
            return;
        }

        state.putParcelable(POINT_KEY, map.getMarkerPoint(featureId));

        // Flags
        state.putBoolean(IS_DRAGGED_KEY, isDragged);
        state.putBoolean(CAPTURE_LOCATION_KEY, captureLocation);
        state.putBoolean(FOUND_FIRST_LOCATION_KEY, foundFirstLocation);
        state.putBoolean(SET_CLEAR_KEY, setClear);
        state.putBoolean(POINT_FROM_INTENT_KEY, pointFromIntent);
        state.putBoolean(INTENT_READ_ONLY_KEY, intentReadOnly);
        state.putBoolean(INTENT_DRAGGABLE_KEY, intentDraggable);
        state.putBoolean(IS_POINT_LOCKED_KEY, isPointLocked);

        // UI state
        state.putBoolean(PLACE_MARKER_BUTTON_ENABLED_KEY, placeMarkerButton.isEnabled());
        state.putBoolean(ZOOM_BUTTON_ENABLED_KEY, zoomButton.isEnabled());
        state.putBoolean(CLEAR_BUTTON_ENABLED_KEY, clearButton.isEnabled());
        state.putInt(LOCATION_STATUS_VISIBILITY_KEY, locationStatus.getVisibility());
        state.putInt(LOCATION_INFO_VISIBILITY_KEY, locationInfo.getVisibility());
    }

    public void returnLocation() {
        String result = null;

        if (setClear || (intentReadOnly && featureId == -1)) {
            result = "";
        } else if (isDragged || intentReadOnly || pointFromIntent) {
            result = formatResult(map.getMarkerPoint(featureId));
        } else if (location != null) {
            result = formatResult(location);
        }

        if (result != null) {
            ExternalAppUtils.returnSingleValue(this, result);
        } else {
            finish();
        }
    }

    @SuppressLint("MissingPermission") // Permission handled in Constructor
    public void initMap(MapFragment newMapFragment) {
        map = newMapFragment;
        map.setDragEndListener(this::onDragEnd);
        map.setLongPressListener(this::onLongPress);

        ImageButton acceptLocation = findViewById(R.id.accept_location);
        acceptLocation.setOnClickListener(v -> returnLocation());

        placeMarkerButton.setEnabled(false);
        placeMarkerButton.setOnClickListener(v -> {
            MapPoint mapPoint = map.getGpsLocation();
            if (mapPoint != null) {
                placeMarker(mapPoint);
                zoomToMarker(true);
            }
        });

        // Focuses on marked location
        zoomButton.setEnabled(false);
        zoomButton.setOnClickListener(v -> map.zoomToCurrentLocation(map.getGpsLocation()));

        // Menu Layer Toggle
        findViewById(R.id.layer_menu).setOnClickListener(v -> {
            DialogFragmentUtils.showIfNotShowing(OfflineMapLayersPickerBottomSheetDialogFragment.class, getSupportFragmentManager());
        });

        clearButton = findViewById(R.id.clear);
        clearButton.setEnabled(false);
        clearButton.setOnClickListener(v -> {
            clear();
            if (map.getGpsLocation() != null) {
                placeMarkerButton.setEnabled(true);
                // locationStatus.setVisibility(View.VISIBLE);
            }
            // placeMarkerButton.setEnabled(true);
            locationInfo.setVisibility(View.VISIBLE);
            locationStatus.setVisibility(View.VISIBLE);
            pointFromIntent = false;
        });

        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            intentDraggable = intent.getBooleanExtra(EXTRA_DRAGGABLE_ONLY, false);
            if (!intentDraggable) {
                // Not Draggable, set text for Map else leave as placement-map text
                locationInfo.setText(getString(org.odk.collect.strings.R.string.geopoint_no_draggable_instruction));
            }

            intentReadOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false);
            if (intentReadOnly) {
                captureLocation = true;
                clearButton.setEnabled(false);
            }

            if (intent.hasExtra(EXTRA_LOCATION)) {
                MapPoint point = intent.getParcelableExtra(EXTRA_LOCATION);

                // If the point is initially set from the intent, the "place marker"
                // button, dragging, and long-pressing are all initially disabled.
                // To enable them, the user must clear the marker and add a new one.
                isPointLocked = true;
                placeMarker(point);
                placeMarkerButton.setEnabled(false);

                captureLocation = true;
                pointFromIntent = true;
                locationInfo.setVisibility(View.GONE);
                locationStatus.setVisibility(View.GONE);
                zoomButton.setEnabled(true);
                foundFirstLocation = true;
                zoomToMarker(false);
            }
        }

        map.setRetainMockAccuracy(intent.getBooleanExtra(EXTRA_RETAIN_MOCK_ACCURACY, false));
        map.setGpsLocationListener(this::onLocationChanged);
        map.setGpsLocationEnabled(true);

        if (previousState != null) {
            restoreFromInstanceState(previousState);
        }
    }

    protected void restoreFromInstanceState(Bundle state) {
        isDragged = state.getBoolean(IS_DRAGGED_KEY, false);
        captureLocation = state.getBoolean(CAPTURE_LOCATION_KEY, false);
        foundFirstLocation = state.getBoolean(FOUND_FIRST_LOCATION_KEY, false);
        setClear = state.getBoolean(SET_CLEAR_KEY, false);
        pointFromIntent = state.getBoolean(POINT_FROM_INTENT_KEY, false);
        intentReadOnly = state.getBoolean(INTENT_READ_ONLY_KEY, false);
        intentDraggable = state.getBoolean(INTENT_DRAGGABLE_KEY, false);
        isPointLocked = state.getBoolean(IS_POINT_LOCKED_KEY, false);

        // Restore the marker and dialog after the flags, because they use some of them.
        MapPoint point = state.getParcelable(POINT_KEY);
        if (point != null) {
            placeMarker(point);
        } else {
            clear();
        }

        // Restore the flags again, because placeMarker() and clear() modify some of them.
        isDragged = state.getBoolean(IS_DRAGGED_KEY, false);
        captureLocation = state.getBoolean(CAPTURE_LOCATION_KEY, false);
        foundFirstLocation = state.getBoolean(FOUND_FIRST_LOCATION_KEY, false);
        setClear = state.getBoolean(SET_CLEAR_KEY, false);
        pointFromIntent = state.getBoolean(POINT_FROM_INTENT_KEY, false);
        intentReadOnly = state.getBoolean(INTENT_READ_ONLY_KEY, false);
        intentDraggable = state.getBoolean(INTENT_DRAGGABLE_KEY, false);
        isPointLocked = state.getBoolean(IS_POINT_LOCKED_KEY, false);

        placeMarkerButton.setEnabled(state.getBoolean(PLACE_MARKER_BUTTON_ENABLED_KEY, false));
        zoomButton.setEnabled(state.getBoolean(ZOOM_BUTTON_ENABLED_KEY, false));
        clearButton.setEnabled(state.getBoolean(CLEAR_BUTTON_ENABLED_KEY, false));

        locationInfo.setVisibility(state.getInt(LOCATION_INFO_VISIBILITY_KEY, View.GONE));
        locationStatus.setVisibility(state.getInt(LOCATION_STATUS_VISIBILITY_KEY, View.GONE));
    }

    public void onLocationChanged(MapPoint point) {
        if (setClear) {
            placeMarkerButton.setEnabled(true);
        }

        this.location = point;

        if (point != null) {
            enableZoomButton(true);

            if (!captureLocation && !setClear) {
                placeMarker(point);
                placeMarkerButton.setEnabled(true);
            }

            if (!foundFirstLocation) {
                map.zoomToCurrentLocation(map.getGpsLocation());
                foundFirstLocation = true;
            }

            locationStatus.setText(formatLocationStatus(map.getLocationProvider(), point.accuracy));
        }
    }

    public String formatResult(MapPoint point) {
        return String.format("%s %s %s %s", point.latitude, point.longitude, point.altitude, point.accuracy);
    }

    public String formatLocationStatus(String provider, double accuracyRadius) {
        return getString(org.odk.collect.strings.R.string.location_accuracy, new DecimalFormat("#.##").format(accuracyRadius))
                + " " + getString(org.odk.collect.strings.R.string.location_provider, GeoUtils.capitalizeGps(provider));
    }

    public void onDragEnd(int draggedFeatureId) {
        if (draggedFeatureId == featureId) {
            isDragged = true;
            captureLocation = true;
            setClear = false;
            map.setCenter(map.getMarkerPoint(featureId), true);
        }
    }

    public void onLongPress(MapPoint point) {
        if (intentDraggable && !intentReadOnly && !isPointLocked) {
            placeMarker(point);
            enableZoomButton(true);
            isDragged = true;
        }
    }

    private void enableZoomButton(boolean shouldEnable) {
        if (zoomButton != null) {
            zoomButton.setEnabled(shouldEnable);
        }
    }

    public void zoomToMarker(boolean animate) {
        map.zoomToPoint(map.getMarkerPoint(featureId), animate);
    }

    private void clear() {
        map.clearFeatures();
        featureId = -1;
        clearButton.setEnabled(false);

        isPointLocked = false;
        isDragged = false;
        captureLocation = false;
        setClear = true;
    }

    /** Places the marker and enables the button to remove it. */
    private void placeMarker(@NonNull MapPoint point) {
        map.clearFeatures();
        featureId = map.addMarker(new MarkerDescription(point, intentDraggable && !intentReadOnly && !isPointLocked, MapFragment.CENTER, new MarkerIconDescription(org.odk.collect.icons.R.drawable.ic_map_point)));
        if (!intentReadOnly) {
            clearButton.setEnabled(true);
        }
        captureLocation = true;
        setClear = false;
    }

    @VisibleForTesting public String getLocationStatus() {
        return locationStatus.getText().toString();
    }
}
