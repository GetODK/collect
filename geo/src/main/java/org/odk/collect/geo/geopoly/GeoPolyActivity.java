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

package org.odk.collect.geo.geopoly;

import static org.odk.collect.androidshared.system.ContextUtils.getThemeAttributeValue;
import static org.odk.collect.geo.Constants.EXTRA_READ_ONLY;
import static org.odk.collect.geo.GeoActivityUtils.requireLocationPermissions;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentContainerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.odk.collect.androidshared.ui.DialogFragmentUtils;
import org.odk.collect.androidshared.ui.FragmentFactoryBuilder;
import org.odk.collect.androidshared.ui.ToastUtils;
import org.odk.collect.async.Scheduler;
import org.odk.collect.externalapp.ExternalAppUtils;
import org.odk.collect.geo.Constants;
import org.odk.collect.geo.GeoDependencyComponentProvider;
import org.odk.collect.geo.GeoUtils;
import org.odk.collect.geo.R;
import org.odk.collect.location.Location;
import org.odk.collect.location.tracker.LocationTracker;
import org.odk.collect.maps.LineDescription;
import org.odk.collect.maps.MapConsts;
import org.odk.collect.maps.MapFragment;
import org.odk.collect.maps.MapFragmentFactory;
import org.odk.collect.maps.MapPoint;
import org.odk.collect.maps.layers.OfflineMapLayersPickerBottomSheetDialogFragment;
import org.odk.collect.maps.layers.ReferenceLayerRepository;
import org.odk.collect.settings.SettingsProvider;
import org.odk.collect.strings.localization.LocalizedActivity;
import org.odk.collect.webpage.ExternalWebPageHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class GeoPolyActivity extends LocalizedActivity implements GeoPolySettingsDialogFragment.SettingsDialogCallback {
    public static final String EXTRA_POLYGON = "answer";
    public static final String OUTPUT_MODE_KEY = "output_mode";
    public static final String POINTS_KEY = "points";
    public static final String INPUT_ACTIVE_KEY = "input_active";
    public static final String RECORDING_ENABLED_KEY = "recording_enabled";
    public static final String RECORDING_AUTOMATIC_KEY = "recording_automatic";
    public static final String INTERVAL_INDEX_KEY = "interval_index";
    public static final String ACCURACY_THRESHOLD_INDEX_KEY = "accuracy_threshold_index";
    protected Bundle previousState;

    public enum OutputMode { GEOTRACE, GEOSHAPE }

    private final ScheduledExecutorService executorServiceScheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture schedulerHandler;

    private OutputMode outputMode;

    @Inject
    MapFragmentFactory mapFragmentFactory;

    @Inject
    LocationTracker locationTracker;

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
    private List<MapPoint> originalPoly;

    private ImageButton zoomButton;
    ImageButton playButton;
    ImageButton clearButton;
    private Button recordButton;
    private ImageButton pauseButton;
    ImageButton backspaceButton;
    ImageButton saveButton;

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
    private List<MapPoint> restoredPoints;

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (!intentReadOnly && map != null && !originalPoly.equals(map.getPolyLinePoints(featureId))) {
                showBackDialog();
            } else {
                finish();
            }
        }
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        ((GeoDependencyComponentProvider) getApplication()).getGeoDependencyComponent().inject(this);

        getSupportFragmentManager().setFragmentFactory(new FragmentFactoryBuilder()
                .forClass(MapFragment.class, () -> (Fragment) mapFragmentFactory.createMapFragment())
                .forClass(OfflineMapLayersPickerBottomSheetDialogFragment.class, () -> new OfflineMapLayersPickerBottomSheetDialogFragment(getActivityResultRegistry(), referenceLayerRepository, scheduler, settingsProvider, externalWebPageHelper))
                .build()
        );

        super.onCreate(savedInstanceState);

        requireLocationPermissions(this);

        previousState = savedInstanceState;

        if (savedInstanceState != null) {
            restoredPoints = savedInstanceState.getParcelableArrayList(POINTS_KEY);
            inputActive = savedInstanceState.getBoolean(INPUT_ACTIVE_KEY, false);
            recordingEnabled = savedInstanceState.getBoolean(RECORDING_ENABLED_KEY, false);
            recordingAutomatic = savedInstanceState.getBoolean(RECORDING_AUTOMATIC_KEY, false);
            intervalIndex = savedInstanceState.getInt(INTERVAL_INDEX_KEY, DEFAULT_INTERVAL_INDEX);
            accuracyThresholdIndex = savedInstanceState.getInt(
                ACCURACY_THRESHOLD_INDEX_KEY, DEFAULT_ACCURACY_THRESHOLD_INDEX);
        }

        intentReadOnly = getIntent().getBooleanExtra(EXTRA_READ_ONLY, false);
        outputMode = (OutputMode) getIntent().getSerializableExtra(OUTPUT_MODE_KEY);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setTitle(getString(outputMode == OutputMode.GEOTRACE ?
            org.odk.collect.strings.R.string.geotrace_title : org.odk.collect.strings.R.string.geoshape_title));
        setContentView(R.layout.geopoly_layout);

        MapFragment mapFragment = ((FragmentContainerView) findViewById(R.id.map_container)).getFragment();
        mapFragment.init(this::initMap, this::finish);

        getOnBackPressedDispatcher().addCallback(onBackPressedCallback);
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
        state.putParcelableArrayList(POINTS_KEY, new ArrayList<>(map.getPolyLinePoints(featureId)));
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

        saveButton = findViewById(R.id.save);
        saveButton.setOnClickListener(v -> {
            if (!map.getPolyLinePoints(featureId).isEmpty()) {
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
            if (map.getPolyLinePoints(featureId).isEmpty()) {
                DialogFragmentUtils.showIfNotShowing(GeoPolySettingsDialogFragment.class, getSupportFragmentManager());
            } else {
                startInput();
            }
        });

        recordButton = findViewById(R.id.record_button);
        recordButton.setOnClickListener(v -> recordPoint(map.getGpsLocation()));

        findViewById(R.id.layers).setOnClickListener(v -> {
            DialogFragmentUtils.showIfNotShowing(OfflineMapLayersPickerBottomSheetDialogFragment.class, getSupportFragmentManager());
        });

        zoomButton = findViewById(R.id.zoom);
        zoomButton.setOnClickListener(v -> map.zoomToCurrentLocation(map.getGpsLocation()));

        List<MapPoint> points = new ArrayList<>();
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra(EXTRA_POLYGON)) {
            ArrayList<MapPoint> extraPoly = intent.getParcelableArrayListExtra(EXTRA_POLYGON);

            if (!extraPoly.isEmpty()) {
                if (outputMode == OutputMode.GEOSHAPE) {
                    points = extraPoly.subList(0, extraPoly.size() - 1);
                } else {
                    points = extraPoly;
                }
            }

            originalPoly = extraPoly;
        }

        if (restoredPoints != null) {
            points = restoredPoints;
        }
        featureId = map.addPolyLine(new LineDescription(points, String.valueOf(MapConsts.DEFAULT_STROKE_WIDTH), null, !intentReadOnly, outputMode == OutputMode.GEOSHAPE));

        if (inputActive && !intentReadOnly) {
            startInput();
        }

        map.setClickListener(this::onClick);
        // Also allow long press to place point to match prior versions
        map.setLongPressListener(this::onClick);
        map.setGpsLocationEnabled(true);
        map.setGpsLocationListener(this::onGpsLocation);

        if (!map.hasCenter()) {
            if (!points.isEmpty()) {
                map.zoomToBoundingBox(points, 0.6, false);
            } else {
                map.runOnGpsLocationReady(this::onGpsLocationReady);
            }
        }

        updateUi();
    }

    private void saveAsPolyline() {
        if (map.getPolyLinePoints(featureId).size() > 1) {
            finishWithResult();
        } else {
            ToastUtils.showShortToastInMiddle(this, getString(org.odk.collect.strings.R.string.polyline_validator));
        }
    }

    private void saveAsPolygon() {
        if (map.getPolyLinePoints(featureId).size() > 2) {
            // Close the polygon.
            List<MapPoint> points = map.getPolyLinePoints(featureId);
            int count = points.size();
            if (count > 1 && !points.get(0).equals(points.get(count - 1))) {
                map.appendPointToPolyLine(featureId, points.get(0));
            }
            finishWithResult();
        } else {
            ToastUtils.showShortToastInMiddle(this, getString(org.odk.collect.strings.R.string.polygon_validator));
        }
    }

    private void finishWithResult() {
        List<MapPoint> points = map.getPolyLinePoints(featureId);
        String result = GeoUtils.formatPointsResultString(points, outputMode.equals(OutputMode.GEOSHAPE));
        ExternalAppUtils.returnSingleValue(this, result);
    }

    @Override
    public void startInput() {
        inputActive = true;
        if (recordingEnabled && recordingAutomatic) {
            boolean retainMockAccuracy = getIntent().getBooleanExtra(Constants.EXTRA_RETAIN_MOCK_ACCURACY, false);
            locationTracker.start(retainMockAccuracy);

            recordPoint(map.getGpsLocation());
            schedulerHandler = executorServiceScheduler.scheduleAtFixedRate(() -> runOnUiThread(() -> {
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
            appendPointIfNew(point);
        }
    }

    private void onGpsLocationReady(MapFragment map) {
        // Don't zoom to current location if a user is manually entering points
        if (getWindow().isActive() && (!inputActive || recordingEnabled)) {
            map.zoomToCurrentLocation(map.getGpsLocation());
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
            appendPointIfNew(point);
        }
    }

    private void appendPointIfNew(MapPoint point) {
        List<MapPoint> points = map.getPolyLinePoints(featureId);
        if (points.isEmpty() || !point.equals(points.get(points.size() - 1))) {
            map.appendPointToPolyLine(featureId, point);
            updateUi();
        }
    }

    private boolean isLocationAcceptable(MapPoint point) {
        if (!isAccuracyThresholdActive()) {
            return true;
        }
        return point.accuracy <= ACCURACY_THRESHOLD_OPTIONS[accuracyThresholdIndex];
    }

    private boolean isAccuracyThresholdActive() {
        int meters = ACCURACY_THRESHOLD_OPTIONS[accuracyThresholdIndex];
        return recordingEnabled && recordingAutomatic && meters > 0;
    }

    private void removeLastPoint() {
        if (featureId != -1) {
            map.removePolyLineLastPoint(featureId);
            updateUi();
        }
    }

    private void clear() {
        map.clearFeatures();
        featureId = map.addPolyLine(new LineDescription(new ArrayList<>(), String.valueOf(MapConsts.DEFAULT_STROKE_WIDTH), null, !intentReadOnly, outputMode == OutputMode.GEOSHAPE));
        inputActive = false;
        updateUi();
    }

    /** Updates the state of various UI widgets to reflect internal state. */
    private void updateUi() {
        final int numPoints = map.getPolyLinePoints(featureId).size();
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
            saveButton.setEnabled(false);
        }
        // Settings dialog

        // GPS status
        boolean usingThreshold = isAccuracyThresholdActive();
        boolean acceptable = location != null && isLocationAcceptable(location);
        int seconds = INTERVAL_OPTIONS[intervalIndex];
        int minutes = seconds / 60;
        int meters = ACCURACY_THRESHOLD_OPTIONS[accuracyThresholdIndex];
        locationStatus.setText(
            location == null ? getString(org.odk.collect.strings.R.string.location_status_searching)
                : !usingThreshold ? getString(org.odk.collect.strings.R.string.location_status_accuracy, location.accuracy)
                : acceptable ? getString(org.odk.collect.strings.R.string.location_status_acceptable, location.accuracy)
                : getString(org.odk.collect.strings.R.string.location_status_unacceptable, location.accuracy)
        );
        locationStatus.setBackgroundColor(
                location == null ? getThemeAttributeValue(this, com.google.android.material.R.attr.colorPrimary)
                        : acceptable ? getThemeAttributeValue(this, com.google.android.material.R.attr.colorPrimary)
                        : getThemeAttributeValue(this, com.google.android.material.R.attr.colorError)
        );
        collectionStatus.setText(
            !inputActive ? getString(org.odk.collect.strings.R.string.collection_status_paused, numPoints)
                : !recordingEnabled ? getString(org.odk.collect.strings.R.string.collection_status_placement, numPoints)
                : !recordingAutomatic ? getString(org.odk.collect.strings.R.string.collection_status_manual, numPoints)
                : !usingThreshold ? (
                    minutes > 0 ?
                        getString(org.odk.collect.strings.R.string.collection_status_auto_minutes, numPoints, minutes) :
                        getString(org.odk.collect.strings.R.string.collection_status_auto_seconds, numPoints, seconds)
                )
                : (
                    minutes > 0 ?
                        getString(org.odk.collect.strings.R.string.collection_status_auto_minutes_accuracy, numPoints, minutes, meters) :
                        getString(org.odk.collect.strings.R.string.collection_status_auto_seconds_accuracy, numPoints, seconds, meters)
                )
        );
    }

    private void showClearDialog() {
        if (!map.getPolyLinePoints(featureId).isEmpty()) {
            new MaterialAlertDialogBuilder(this)
                .setMessage(org.odk.collect.strings.R.string.geo_clear_warning)
                .setPositiveButton(org.odk.collect.strings.R.string.clear, (dialog, id) -> clear())
                .setNegativeButton(org.odk.collect.strings.R.string.cancel, null)
                .show();
        }
    }

    private void showBackDialog() {
        new MaterialAlertDialogBuilder(this)
            .setMessage(getString(org.odk.collect.strings.R.string.geo_exit_warning))
            .setPositiveButton(org.odk.collect.strings.R.string.discard, (dialog, id) -> finish())
            .setNegativeButton(org.odk.collect.strings.R.string.cancel, null)
            .show();

    }
}
