/*
 * Copyright (C) 2018 Nafundi
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

package org.odk.collect.android.activities;

import static org.odk.collect.android.widgets.utilities.ActivityGeoDataRequester.READ_ONLY;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AlertDialog;

import org.odk.collect.android.R;
import org.odk.collect.android.geo.MapFragment;
import org.odk.collect.android.geo.MapPoint;
import org.odk.collect.android.geo.MapProvider;
import org.odk.collect.android.geo.SettingsDialogFragment;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.preferences.screens.MapsPreferencesFragment;
import org.odk.collect.android.utilities.DialogUtils;
import org.odk.collect.android.utilities.GeoUtils;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.location.Location;
import org.odk.collect.location.tracker.LocationTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class GeoPolyActivity extends BaseGeoMapActivity implements SettingsDialogFragment.SettingsDialogCallback {
    public static final String ANSWER_KEY = "answer";
    public static final String OUTPUT_MODE_KEY = "output_mode";
    public static final String MAP_CENTER_KEY = "map_center";
    public static final String MAP_ZOOM_KEY = "map_zoom";
    public static final String POINTS_KEY = "points";
    public static final String INPUT_ACTIVE_KEY = "input_active";
    public static final String RECORDING_ENABLED_KEY = "recording_enabled";
    public static final String RECORDING_AUTOMATIC_KEY = "recording_automatic";
    public static final String INTERVAL_INDEX_KEY = "interval_index";
    public static final String ACCURACY_THRESHOLD_INDEX_KEY = "accuracy_threshold_index";

    public enum OutputMode { GEOTRACE, GEOSHAPE }

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture schedulerHandler;

    private OutputMode outputMode;

    @Inject
    MapProvider mapProvider;

    @Inject
    LocationTracker locationTracker;

    private MapFragment map;
    private int featureId = -1;  // will be a positive featureId once map is ready
    private String originalAnswerString = "";

    private ImageButton zoomButton;
    private ImageButton playButton;
    private ImageButton clearButton;
    private Button recordButton;
    private ImageButton pauseButton;
    private ImageButton backspaceButton;

    private TextView locationStatus;
    private TextView collectionStatus;

    private View settingsView;

    private static final int[] INTERVAL_OPTIONS = {
        1, 5, 10, 20, 30, 60, 300, 600, 1200, 1800
    };
    private static final int DEFAULT_INTERVAL_INDEX = 3; // default is 20 seconds

    private static final int[] ACCURACY_THRESHOLD_OPTIONS = {
        0, 3, 5, 10, 15, 20
    };
    private static final int DEFAULT_ACCURACY_THRESHOLD_INDEX = 3; // default is 10 meters

    private boolean inputActive; // whether we are ready for the user to add points
    private boolean recordingEnabled; // whether points are taken from GPS readings (if not, placed by tapping)
    private boolean recordingAutomatic; // whether GPS readings are taken at regular intervals (if not, only when user-directed)
    private boolean intentReadOnly; // whether the intent requested for the path to be read-only.

    private int intervalIndex = DEFAULT_INTERVAL_INDEX;

    private int accuracyThresholdIndex = DEFAULT_ACCURACY_THRESHOLD_INDEX;

    // restored from savedInstanceState
    private MapPoint restoredMapCenter;
    private Double restoredMapZoom;
    private List<MapPoint> restoredPoints;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        DaggerUtils.getComponent(this).inject(this);

        if (savedInstanceState != null) {
            restoredMapCenter = savedInstanceState.getParcelable(MAP_CENTER_KEY);
            restoredMapZoom = savedInstanceState.getDouble(MAP_ZOOM_KEY);
            restoredPoints = savedInstanceState.getParcelableArrayList(POINTS_KEY);
            inputActive = savedInstanceState.getBoolean(INPUT_ACTIVE_KEY, false);
            recordingEnabled = savedInstanceState.getBoolean(RECORDING_ENABLED_KEY, false);
            recordingAutomatic = savedInstanceState.getBoolean(RECORDING_AUTOMATIC_KEY, false);
            intervalIndex = savedInstanceState.getInt(INTERVAL_INDEX_KEY, DEFAULT_INTERVAL_INDEX);
            accuracyThresholdIndex = savedInstanceState.getInt(
                ACCURACY_THRESHOLD_INDEX_KEY, DEFAULT_ACCURACY_THRESHOLD_INDEX);
        }

        intentReadOnly = getIntent().getBooleanExtra(READ_ONLY, false);
        outputMode = (OutputMode) getIntent().getSerializableExtra(OUTPUT_MODE_KEY);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTitle(getString(outputMode == OutputMode.GEOTRACE ?
            R.string.geotrace_title : R.string.geoshape_title));
        setContentView(R.layout.geopoly_layout);

        Context context = getApplicationContext();
        mapProvider.createMapFragment(context)
            .addTo(this, R.id.map_container, this::initMap, this::finish);
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
        state.putParcelable(MAP_CENTER_KEY, map.getCenter());
        state.putDouble(MAP_ZOOM_KEY, map.getZoom());
        state.putParcelableArrayList(POINTS_KEY, new ArrayList<>(map.getPolyPoints(featureId)));
        state.putBoolean(INPUT_ACTIVE_KEY, inputActive);
        state.putBoolean(RECORDING_ENABLED_KEY, recordingEnabled);
        state.putBoolean(RECORDING_AUTOMATIC_KEY, recordingAutomatic);
        state.putInt(INTERVAL_INDEX_KEY, intervalIndex);
        state.putInt(ACCURACY_THRESHOLD_INDEX_KEY, accuracyThresholdIndex);
    }

    @Override protected void onDestroy() {
        if (schedulerHandler != null && !schedulerHandler.isCancelled()) {
            schedulerHandler.cancel(true);
        }

        locationTracker.stop();
        super.onDestroy();
    }

    public void initMap(MapFragment newMapFragment) {
        map = newMapFragment;

        locationStatus = findViewById(R.id.location_status);
        collectionStatus = findViewById(R.id.collection_status);
        settingsView = getLayoutInflater().inflate(R.layout.geopoly_dialog, null);

        clearButton = findViewById(R.id.clear);
        clearButton.setOnClickListener(v -> showClearDialog());

        pauseButton = findViewById(R.id.pause);
        pauseButton.setOnClickListener(v -> {
            inputActive = false;
            try {
                schedulerHandler.cancel(true);
            } catch (Exception e) {
                // Do nothing
            }
            updateUi();
        });

        backspaceButton = findViewById(R.id.backspace);
        backspaceButton.setOnClickListener(v -> removeLastPoint());

        ImageButton saveButton = findViewById(R.id.save);
        saveButton.setOnClickListener(v -> {
            if (!map.getPolyPoints(featureId).isEmpty()) {
                if (outputMode == OutputMode.GEOTRACE) {
                    saveAsPolyline();
                } else {
                    saveAsPolygon();
                }
            } else {
                finishWithResult();
            }
        });

        playButton = findViewById(R.id.play);
        playButton.setOnClickListener(v -> {
            if (map.getPolyPoints(featureId).isEmpty()) {
                DialogUtils.showIfNotShowing(SettingsDialogFragment.class, getSupportFragmentManager());
            } else {
                startInput();
            }
        });

        recordButton = findViewById(R.id.record_button);
        recordButton.setOnClickListener(v -> recordPoint(map.getGpsLocation()));

        findViewById(R.id.layers).setOnClickListener(v -> {
            MapsPreferencesFragment.showReferenceLayerDialog(this);
        });

        zoomButton = findViewById(R.id.zoom);
        zoomButton.setOnClickListener(v -> map.zoomToPoint(map.getGpsLocation(), true));

        List<MapPoint> points = new ArrayList<>();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(ANSWER_KEY)) {
            originalAnswerString = intent.getStringExtra(ANSWER_KEY);
            points = parsePoints(originalAnswerString);
        }
        if (restoredPoints != null) {
            points = restoredPoints;
        }
        featureId = map.addDraggablePoly(points, outputMode == OutputMode.GEOSHAPE);

        if (inputActive && !intentReadOnly) {
            startInput();
        }

        map.setClickListener(this::onClick);
        // Also allow long press to place point to match prior versions
        map.setLongPressListener(this::onClick);
        map.setGpsLocationEnabled(true);
        map.setGpsLocationListener(this::onGpsLocation);
        if (restoredMapCenter != null && restoredMapZoom != null) {
            map.zoomToPoint(restoredMapCenter, restoredMapZoom, false);
        } else if (!points.isEmpty()) {
            map.zoomToBoundingBox(points, 0.6, false);
        } else {
            map.runOnGpsLocationReady(this::onGpsLocationReady);
        }
        updateUi();
    }

    private void saveAsPolyline() {
        if (map.getPolyPoints(featureId).size() > 1) {
            finishWithResult();
        } else {
            ToastUtils.showShortToastInMiddle(this, getString(R.string.polyline_validator));
        }
    }

    private void saveAsPolygon() {
        if (map.getPolyPoints(featureId).size() > 2) {
            // Close the polygon.
            List<MapPoint> points = map.getPolyPoints(featureId);
            int count = points.size();
            if (count > 1 && !points.get(0).equals(points.get(count - 1))) {
                map.appendPointToPoly(featureId, points.get(0));
            }
            finishWithResult();
        } else {
            ToastUtils.showShortToastInMiddle(this, getString(R.string.polygon_validator));
        }
    }

    private void finishWithResult() {
        List<MapPoint> points = map.getPolyPoints(featureId);
        setResult(RESULT_OK, new Intent().putExtra(
            FormEntryActivity.ANSWER_KEY, GeoUtils.formatPointsResultString(points, outputMode.equals(OutputMode.GEOSHAPE))));
        finish();
    }

    @Override public void onBackPressed() {
        if (map != null && !parsePoints(originalAnswerString).equals(map.getPolyPoints(featureId))) {
            showBackDialog();
        } else {
            finish();
        }
    }

    /**
     * Parses a form result string, as previously formatted by formatPoints,
     * into a list of vertices.
     */
    private List<MapPoint> parsePoints(String coords) {
        List<MapPoint> points = new ArrayList<>();
        for (String vertex : (coords == null ? "" : coords).split(";")) {
            String[] words = vertex.trim().split(" ");
            if (words.length >= 2) {
                double lat;
                double lon;
                double alt;
                double sd;
                try {
                    lat = Double.parseDouble(words[0]);
                    lon = Double.parseDouble(words[1]);
                    alt = words.length > 2 ? Double.parseDouble(words[2]) : 0;
                    sd = words.length > 3 ? Double.parseDouble(words[3]) : 0;
                } catch (NumberFormatException e) {
                    continue;
                }
                points.add(new MapPoint(lat, lon, alt, sd));
            }
        }
        if (outputMode == OutputMode.GEOSHAPE) {
            // Closed polygons are stored with a last point that duplicates the
            // first point.  To prepare a polygon for display and editing, we
            // need to remove this duplicate point.
            int count = points.size();
            if (count > 1 && points.get(0).equals(points.get(count - 1))) {
                points.remove(count - 1);
            }
        }
        return points;
    }

    @Override
    public void startInput() {
        inputActive = true;
        if (recordingEnabled && recordingAutomatic) {
            locationTracker.start();

            recordPoint(map.getGpsLocation());
            schedulerHandler = scheduler.scheduleAtFixedRate(() -> runOnUiThread(() -> {
                Location currentLocation = locationTracker.getCurrentLocation();

                if (currentLocation != null) {
                    MapPoint currentMapPoint = new MapPoint(
                            currentLocation.getLatitude(),
                            currentLocation.getLongitude(),
                            currentLocation.getAltitude(),
                            currentLocation.getAccuracy()
                    );

                    recordPoint(currentMapPoint);
                }
            }), INTERVAL_OPTIONS[intervalIndex], INTERVAL_OPTIONS[intervalIndex], TimeUnit.SECONDS);
        }
        updateUi();
    }

    @Override
    public void updateRecordingMode(int id) {
        recordingEnabled = id != R.id.placement_mode;
        recordingAutomatic = id == R.id.automatic_mode;
    }

    @Override
    public int getCheckedId() {
        if (recordingEnabled) {
            return recordingAutomatic ? R.id.automatic_mode : R.id.manual_mode;
        } else {
            return R.id.placement_mode;
        }
    }

    @Override
    public int getIntervalIndex() {
        return intervalIndex;
    }

    @Override
    public int getAccuracyThresholdIndex() {
        return accuracyThresholdIndex;
    }

    @Override
    public void setIntervalIndex(int intervalIndex) {
        this.intervalIndex = intervalIndex;
    }

    @Override
    public void setAccuracyThresholdIndex(int accuracyThresholdIndex) {
        this.accuracyThresholdIndex = accuracyThresholdIndex;
    }

    private void onClick(MapPoint point) {
        if (inputActive && !recordingEnabled) {
            map.appendPointToPoly(featureId, point);
            updateUi();
        }
    }

    private void onGpsLocationReady(MapFragment map) {
        // Don't zoom to current location if a user is manually entering points
        if (getWindow().isActive() && (!inputActive || recordingEnabled)) {
            map.zoomToPoint(map.getGpsLocation(), true);
        }
        updateUi();
    }

    private void onGpsLocation(MapPoint point) {
        if (inputActive && recordingEnabled) {
            map.setCenter(point, false);
        }
        updateUi();
    }

    private void recordPoint(MapPoint point) {
        if (point != null && isLocationAcceptable(point)) {
            map.appendPointToPoly(featureId, point);
            updateUi();
        }
    }

    private boolean isLocationAcceptable(MapPoint point) {
        if (!isAccuracyThresholdActive()) {
            return true;
        }
        return point.sd <= ACCURACY_THRESHOLD_OPTIONS[accuracyThresholdIndex];
    }

    private boolean isAccuracyThresholdActive() {
        int meters = ACCURACY_THRESHOLD_OPTIONS[accuracyThresholdIndex];
        return recordingEnabled && recordingAutomatic && meters > 0;
    }

    private void removeLastPoint() {
        if (featureId != -1) {
            map.removePolyLastPoint(featureId);
            updateUi();
        }
    }

    private void clear() {
        map.clearFeatures();
        featureId = map.addDraggablePoly(new ArrayList<>(), outputMode == OutputMode.GEOSHAPE);
        inputActive = false;
        updateUi();
    }

    /** Updates the state of various UI widgets to reflect internal state. */
    private void updateUi() {
        final int numPoints = map.getPolyPoints(featureId).size();
        final MapPoint location = map.getGpsLocation();

        // Visibility state
        playButton.setVisibility(inputActive ? View.GONE : View.VISIBLE);
        pauseButton.setVisibility(inputActive ? View.VISIBLE : View.GONE);
        recordButton.setVisibility(inputActive && recordingEnabled && !recordingAutomatic ? View.VISIBLE : View.GONE);

        // Enabled state
        zoomButton.setEnabled(location != null);
        backspaceButton.setEnabled(numPoints > 0);
        clearButton.setEnabled(!inputActive && numPoints > 0);
        settingsView.findViewById(R.id.manual_mode).setEnabled(location != null);
        settingsView.findViewById(R.id.automatic_mode).setEnabled(location != null);

        if (intentReadOnly) {
            playButton.setEnabled(false);
            backspaceButton.setEnabled(false);
            clearButton.setEnabled(false);
        }
        // Settings dialog

        // GPS status
        boolean usingThreshold = isAccuracyThresholdActive();
        boolean acceptable = location != null && isLocationAcceptable(location);
        int seconds = INTERVAL_OPTIONS[intervalIndex];
        int minutes = seconds / 60;
        int meters = ACCURACY_THRESHOLD_OPTIONS[accuracyThresholdIndex];
        locationStatus.setText(
            location == null ? getString(R.string.location_status_searching)
                : !usingThreshold ? getString(R.string.location_status_accuracy, location.sd)
                : acceptable ? getString(R.string.location_status_acceptable, location.sd)
                : getString(R.string.location_status_unacceptable, location.sd)
        );
        locationStatus.setBackgroundColor(
                location == null ? themeUtils.getColorPrimary()
                        : acceptable ? themeUtils.getColorPrimary()
                        : themeUtils.getColorError()
        );
        collectionStatus.setText(
            !inputActive ? getString(R.string.collection_status_paused, numPoints)
                : !recordingEnabled ? getString(R.string.collection_status_placement, numPoints)
                : !recordingAutomatic ? getString(R.string.collection_status_manual, numPoints)
                : !usingThreshold ? (
                    minutes > 0 ?
                        getString(R.string.collection_status_auto_minutes, numPoints, minutes) :
                        getString(R.string.collection_status_auto_seconds, numPoints, seconds)
                )
                : (
                    minutes > 0 ?
                        getString(R.string.collection_status_auto_minutes_accuracy, numPoints, minutes, meters) :
                        getString(R.string.collection_status_auto_seconds_accuracy, numPoints, seconds, meters)
                )
        );
    }

    private void showClearDialog() {
        if (!map.getPolyPoints(featureId).isEmpty()) {
            new AlertDialog.Builder(this)
                .setMessage(R.string.geo_clear_warning)
                .setPositiveButton(R.string.clear, (dialog, id) -> clear())
                .setNegativeButton(R.string.cancel, null)
                .show();
        }
    }

    private void showBackDialog() {
        new AlertDialog.Builder(this)
            .setMessage(getString(R.string.geo_exit_warning))
            .setPositiveButton(R.string.discard, (dialog, id) -> finish())
            .setNegativeButton(R.string.cancel, null)
            .show();

    }

    @VisibleForTesting public MapFragment getMapFragment() {
        return map;
    }
}
