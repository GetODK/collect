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

package org.odk.collect.googlemaps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.LocationListener;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.maps.model.TileOverlay;
import com.google.android.gms.maps.model.TileOverlayOptions;

import org.odk.collect.androidshared.system.ContextUtils;
import org.odk.collect.androidshared.ui.ToastUtils;
import org.odk.collect.googlemaps.GoogleMapConfigurator.GoogleMapTypeOption;
import org.odk.collect.googlemaps.scaleview.MapScaleView;
import org.odk.collect.location.LocationClient;
import org.odk.collect.maps.LineDescription;
import org.odk.collect.maps.MapConfigurator;
import org.odk.collect.maps.MapFragment;
import org.odk.collect.maps.MapFragmentDelegate;
import org.odk.collect.maps.MapPoint;
import org.odk.collect.maps.PolygonDescription;
import org.odk.collect.maps.layers.MapFragmentReferenceLayerUtils;
import org.odk.collect.maps.layers.ReferenceLayerRepository;
import org.odk.collect.maps.markers.MarkerDescription;
import org.odk.collect.maps.markers.MarkerIconDescription;
import org.odk.collect.settings.SettingsProvider;
import org.odk.collect.settings.keys.ProjectKeys;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import javax.inject.Inject;

import timber.log.Timber;

public class GoogleMapFragment extends Fragment implements
        MapFragment, LocationListener, LocationClient.LocationClientListener,
        GoogleMap.OnMapClickListener, GoogleMap.OnMapLongClickListener,
        GoogleMap.OnMarkerClickListener, GoogleMap.OnMarkerDragListener,
        GoogleMap.OnPolylineClickListener, GoogleMap.OnPolygonClickListener {

    // Bundle keys understood by applyConfig().
    static final String KEY_MAP_TYPE = "MAP_TYPE";

    @Inject
    ReferenceLayerRepository referenceLayerRepository;

    @Inject
    LocationClient locationClient;

    @Inject
    SettingsProvider settingsProvider;

    private final MapFragmentDelegate mapFragmentDelegate = new MapFragmentDelegate(
            this,
            this::createConfigurator,
            () -> settingsProvider.getUnprotectedSettings(),
            () -> settingsProvider.getMetaSettings(),
            this::onConfigChanged
    );

    private GoogleMap map;
    private MapScaleView scaleView;
    private ReadyListener readyListener;
    private ErrorListener errorListener;
    private Marker locationCrosshairs;
    private Circle accuracyCircle;
    private final List<ReadyListener> gpsLocationReadyListeners = new ArrayList<>();
    private PointListener clickListener;
    private PointListener longPressListener;
    private PointListener gpsLocationListener;
    private FeatureListener featureClickListener;
    private FeatureListener dragEndListener;

    private boolean clientWantsLocationUpdates;
    private MapPoint lastLocationFix;
    private String lastLocationProvider;

    private int nextFeatureId = 1;
    private final Map<Integer, MapFeature> features = new HashMap<>();
    private int mapType;
    private File referenceLayerFile;
    private TileOverlay referenceOverlay;
    private boolean hasCenter;
    private boolean isUserZooming;

    @Override
    public void init(@Nullable ReadyListener readyListener, @Nullable ErrorListener errorListener) {
        this.readyListener = readyListener;
        this.errorListener = errorListener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mapFragmentDelegate.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    @SuppressLint("MissingPermission") // Permission checks for location services handled in widgets
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.map_layout, container, false);

        scaleView = view.findViewById(R.id.scale_view);

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync((GoogleMap googleMap) -> {
            if (googleMap == null) {
                ToastUtils.showShortToast(requireContext(), org.odk.collect.strings.R.string.google_play_services_error_occured);
                if (errorListener != null) {
                    errorListener.onError();
                }
                return;
            }
            this.map = googleMap;
            googleMap.setMapType(mapType);
            googleMap.setOnMapClickListener(this);
            googleMap.setOnMapLongClickListener(this);
            googleMap.setOnMarkerClickListener(this);
            googleMap.setOnPolylineClickListener(this);
            googleMap.setOnPolygonClickListener(this);
            googleMap.setOnMarkerDragListener(this);
            googleMap.getUiSettings().setCompassEnabled(true);
            // Don't show the blue dot on the map; we'll draw crosshairs instead.
            googleMap.setMyLocationEnabled(false);
            googleMap.setMinZoomPreference(1);
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    toLatLng(MapFragment.Companion.getINITIAL_CENTER()), INITIAL_ZOOM));
            googleMap.setOnCameraMoveListener(() -> scaleView.update(googleMap.getCameraPosition().zoom, googleMap.getCameraPosition().target.latitude));
            googleMap.setOnCameraMoveStartedListener(reason -> {
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    isUserZooming = true;
                }
            });
            googleMap.setOnCameraIdleListener(() -> {
                scaleView.update(googleMap.getCameraPosition().zoom, googleMap.getCameraPosition().target.latitude);
                if (isUserZooming) {
                    mapFragmentDelegate.onZoomLevelChangedByUserListener(googleMap.getCameraPosition().zoom);
                    isUserZooming = false;
                }
            });
            loadReferenceOverlay();

            // If the screen is rotated before the map is ready, this fragment
            // could already be detached, which makes it unsafe to use.  Only
            // call the ReadyListener if this fragment is still attached.
            if (readyListener != null && getActivity() != null) {
                mapFragmentDelegate.onReady();
                readyListener.onReady(this);
            }
        });

        return view;
    }

    @Override public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        GoogleMapsDependencyComponent component = ((GoogleMapsDependencyComponentProvider) context.getApplicationContext()).getGoogleMapsDependencyComponent();
        component.inject(this);
    }

    @Override public void onStart() {
        super.onStart();
        mapFragmentDelegate.onStart();
    }

    @Override public void onResume() {
        super.onResume();
        enableLocationUpdates(clientWantsLocationUpdates);
    }

    @Override public void onPause() {
        super.onPause();
        enableLocationUpdates(false);
    }

    @Override public void onStop() {
        super.onStop();
        mapFragmentDelegate.onStop();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapFragmentDelegate.onSaveInstanceState(outState);
    }

    @Override public void onDestroy() {
        BitmapDescriptorCache.clearCache();
        super.onDestroy();
    }

    @NonNull
    @Override
    public MapFragmentDelegate getMapFragmentDelegate() {
        return mapFragmentDelegate;
    }

    @Override public @NonNull MapPoint getCenter() {
        if (map == null) {  // during Robolectric tests, map will be null
            return MapFragment.Companion.getINITIAL_CENTER();
        }
        LatLng target = map.getCameraPosition().target;
        return new MapPoint(target.latitude, target.longitude);
    }

    @Override public void setCenter(@Nullable MapPoint center, boolean animate) {
        if (map == null) {  // during Robolectric tests, map will be null
            return;
        }
        if (center != null) {
            moveOrAnimateCamera(CameraUpdateFactory.newLatLng(toLatLng(center)), animate);
        }

        hasCenter = true;
    }

    @Override public double getZoom() {
        if (map == null) {  // during Robolectric tests, map will be null
            return INITIAL_ZOOM;
        }
        return map.getCameraPosition().zoom;
    }

    @Override public void zoomToPoint(@Nullable MapPoint center, boolean animate) {
        zoomToPoint(center, POINT_ZOOM, animate);
    }

    @Override public void zoomToPoint(@Nullable MapPoint center, double zoom, boolean animate) {
        if (map == null) {  // during Robolectric tests, map will be null
            return;
        }
        if (center != null) {
            moveOrAnimateCamera(
                CameraUpdateFactory.newLatLngZoom(toLatLng(center), (float) zoom), animate);
        }
        hasCenter = true;
    }

    @Override public void zoomToBoundingBox(@Nullable Iterable<MapPoint> points, double scaleFactor, boolean animate) {
        if (map == null) {  // during Robolectric tests, map will be null
            return;
        }
        if (points != null) {
            int count = 0;
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            MapPoint lastPoint = null;
            for (MapPoint point : points) {
                lastPoint = point;
                builder.include(toLatLng(point));
                count++;
            }
            if (count == 1) {
                zoomToPoint(lastPoint, animate);
            } else if (count > 1) {
                final LatLngBounds bounds = expandBounds(builder.build(), 1 / scaleFactor);
                new Handler().postDelayed(() -> {
                    try {
                        moveOrAnimateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0), animate);
                    } catch (IllegalArgumentException
                             //https://github.com/getodk/collect/issues/5379
                             |
                             IllegalStateException e) { // https://github.com/getodk/collect/issues/5634
                        LatLng boxCenter = bounds.getCenter();
                        zoomToPoint(new MapPoint(boxCenter.latitude, boxCenter.longitude), map.getMinZoomLevel(), false);
                    }
                }, 100);
            }
        }

        hasCenter = true;
    }

    @Override public int addMarker(MarkerDescription markerDescription) {
        int featureId = nextFeatureId++;
        features.put(featureId, new MarkerFeature(getActivity(), markerDescription, map));
        return featureId;
    }

    @Override
    public List<Integer> addMarkers(List<MarkerDescription> markers) {
        List<Integer> featureIds = new ArrayList<>();
        for (MarkerDescription markerDescription : markers) {
            int featureId = addMarker(markerDescription);
            featureIds.add(featureId);
        }

        return featureIds;
    }

    @Override public void setMarkerIcon(int featureId, MarkerIconDescription markerIconDescription) {
        MapFeature feature = features.get(featureId);
        if (feature instanceof MarkerFeature) {
            ((MarkerFeature) feature).setIcon(markerIconDescription);
        }
    }

    @Override public @Nullable MapPoint getMarkerPoint(int featureId) {
        MapFeature feature = features.get(featureId);
        return feature instanceof MarkerFeature ? ((MarkerFeature) feature).getPoint() : null;
    }

    @Override public int addPolyLine(LineDescription lineDescription) {
        int featureId = nextFeatureId++;
        if (lineDescription.getDraggable()) {
            features.put(featureId, new DynamicPolyLineFeature(getActivity(), lineDescription, map));
        } else {
            features.put(featureId, new StaticPolyLineFeature(lineDescription, map));
        }
        return featureId;
    }

    @Override
    public int addPolygon(PolygonDescription polygonDescription) {
        int featureId = nextFeatureId++;
        features.put(featureId, new StaticPolygonFeature(map, polygonDescription));
        return featureId;
    }

    @Override public void appendPointToPolyLine(int featureId, @NonNull MapPoint point) {
        MapFeature feature = features.get(featureId);
        if (feature instanceof DynamicPolyLineFeature) {
            ((DynamicPolyLineFeature) feature).addPoint(point);
        }
    }

    @Override public @NonNull List<MapPoint> getPolyLinePoints(int featureId) {
        MapFeature feature = features.get(featureId);
        if (feature instanceof LineFeature) {
            return ((LineFeature) feature).getPoints();
        }

        return new ArrayList<>();
    }

    @Override public void removePolyLineLastPoint(int featureId) {
        MapFeature feature = features.get(featureId);
        if (feature instanceof DynamicPolyLineFeature) {
            ((DynamicPolyLineFeature) feature).removeLastPoint();
        }
    }

    @Override public void clearFeatures() {
        if (map != null) {  // during Robolectric tests, map will be null
            for (MapFeature feature : features.values()) {
                feature.dispose();
            }
        }
        features.clear();
        nextFeatureId = 1;
    }

    @Override public void setClickListener(@Nullable PointListener listener) {
        clickListener = listener;
    }

    @Override public void setLongPressListener(@Nullable PointListener listener) {
        longPressListener = listener;
    }

    @Override public void setFeatureClickListener(@Nullable FeatureListener listener) {
        featureClickListener = listener;
    }

    @Override public void setDragEndListener(@Nullable FeatureListener listener) {
        dragEndListener = listener;
    }

    @Override public void setGpsLocationListener(@Nullable PointListener listener) {
        gpsLocationListener = listener;
    }

    @Override
    public void setRetainMockAccuracy(boolean retainMockAccuracy) {
        locationClient.setRetainMockAccuracy(retainMockAccuracy);
    }

    @Override
    public boolean hasCenter() {
        return hasCenter;
    }

    @Override public void setGpsLocationEnabled(boolean enable) {
        if (enable != clientWantsLocationUpdates) {
            clientWantsLocationUpdates = enable;
            enableLocationUpdates(clientWantsLocationUpdates);
        }
    }

    @Override public void runOnGpsLocationReady(@NonNull ReadyListener listener) {
        if (lastLocationFix != null) {
            listener.onReady(this);
        } else {
            gpsLocationReadyListeners.add(listener);
        }
    }

    @Override public void onLocationChanged(Location location) {
        Timber.i("onLocationChanged: location = %s", location);
        lastLocationFix = fromLocation(location);
        lastLocationProvider = location.getProvider();
        for (ReadyListener listener : gpsLocationReadyListeners) {
            listener.onReady(this);
        }
        gpsLocationReadyListeners.clear();
        if (gpsLocationListener != null) {
            gpsLocationListener.onPoint(lastLocationFix);
        }

        if (getActivity() != null) {
            updateLocationIndicator(toLatLng(lastLocationFix), location.getAccuracy());
        }
    }

    @Override public @Nullable MapPoint getGpsLocation() {
        return lastLocationFix;
    }

    @Override public @Nullable String getLocationProvider() {
        return lastLocationProvider;
    }

    @Override public void onMapClick(LatLng latLng) {
        if (clickListener != null) {
            clickListener.onPoint(fromLatLng(latLng));
        }
    }

    @Override public void onMapLongClick(LatLng latLng) {
        if (longPressListener != null) {
            longPressListener.onPoint(fromLatLng(latLng));
        }
    }

    @Override public boolean onMarkerClick(Marker marker) {
        // Avoid calling listeners if location crosshair is clicked on.
        if (marker == locationCrosshairs) {
            return true;
        }

        if (featureClickListener != null) { // FormMapActivity
            featureClickListener.onFeature(findFeature(marker));
        } else { // GeoWidget
            onMapClick(marker.getPosition());
        }
        return true;  // consume the event (no default zoom and popup behaviour)
    }

    @Override public void onPolylineClick(Polyline polyline) {
        if (featureClickListener != null) {
            featureClickListener.onFeature(findFeature(polyline));
        }
    }

    @Override public void onPolygonClick(@NonNull Polygon polygon) {
        if (featureClickListener != null) {
            featureClickListener.onFeature(findFeature(polygon));
        }
    }

    @Override public void onMarkerDragStart(Marker marker) {
        // When dragging starts, GoogleMap makes the marker jump up to move it
        // out from under the user's finger; whenever a marker moves, we have
        // to update its corresponding feature.
        updateFeature(findFeature(marker));
    }

    @Override public void onMarkerDrag(Marker marker) {
        // When a marker is manually dragged, the position is no longer
        // obtained from a GPS reading, so the altitude and standard deviation
        // fields are no longer meaningful; reset them to zero.
        marker.setSnippet("0;0");
        updateFeature(findFeature(marker));
    }

    @Override public void onMarkerDragEnd(Marker marker) {
        int featureId = findFeature(marker);
        updateFeature(featureId);
        if (dragEndListener != null && featureId != -1) {
            dragEndListener.onFeature(featureId);
        }
    }

    @Override public void onClientStart() {
        lastLocationFix = fromLocation(locationClient.getLastLocation());
        Timber.i("Requesting location updates (to %s)", this);
        locationClient.requestLocationUpdates(this);
    }

    @Override public void onClientStartFailure() {
    }

    @Override public void onClientStop() {
    }

    private static @NonNull MapPoint fromLatLng(@NonNull LatLng latLng) {
        return new MapPoint(latLng.latitude, latLng.longitude);
    }

    private static @Nullable MapPoint fromLocation(@Nullable Location location) {
        if (location == null) {
            return null;
        }
        return new MapPoint(location.getLatitude(), location.getLongitude(),
            location.getAltitude(), location.getAccuracy());
    }

    private static @NonNull MapPoint fromMarker(@NonNull Marker marker) {
        LatLng position = marker.getPosition();
        String snippet = marker.getSnippet();
        String[] parts = (snippet != null ? snippet : "").split(";");
        double alt = 0;
        double sd = 0;
        try {
            if (parts.length >= 1) {
                alt = Double.parseDouble(parts[0]);
            }
            if (parts.length >= 2) {
                sd = Double.parseDouble(parts[1]);
            }
        } catch (NumberFormatException e) {
            Timber.w("Marker.getSnippet() did not contain two numbers");
        }
        return new MapPoint(position.latitude, position.longitude, alt, sd);
    }

    private static @NonNull LatLng toLatLng(@NonNull MapPoint point) {
        return new LatLng(point.latitude, point.longitude);
    }

    /** Updates the map to reflect the value of referenceLayerFile. */
    private void loadReferenceOverlay() {
        if (referenceOverlay != null) {
            referenceOverlay.remove();
            referenceOverlay = null;
        }
        if (referenceLayerFile != null) {
            referenceOverlay = this.map.addTileOverlay(new TileOverlayOptions().tileProvider(
                new GoogleMapsMapBoxOfflineTileProvider(referenceLayerFile)
            ));
            setLabelsVisibility("off");
        } else {
            setLabelsVisibility("on");
        }
    }

    private void setLabelsVisibility(String state) {
        String style = String.format(" [ { featureType: all, elementType: labels, stylers: [ { visibility: %s } ] } ]", state);
        map.setMapStyle(new MapStyleOptions(style));
    }

    private LatLngBounds expandBounds(LatLngBounds bounds, double factor) {
        double north = bounds.northeast.latitude;
        double south = bounds.southwest.latitude;
        double latCenter = (north + south) / 2;
        double latRadius = ((north - south) / 2) * factor;
        north = Math.min(90, latCenter + latRadius);
        south = Math.max(-90, latCenter - latRadius);

        double east = bounds.northeast.longitude;
        double west = bounds.southwest.longitude;
        while (east < west) {
            east += 360;
        }
        double lonCenter = (east + west) / 2;
        double lonRadius = Math.min(180 - 1e-6, ((east - west) / 2) * factor);
        east = lonCenter + lonRadius;
        west = lonCenter - lonRadius;

        return new LatLngBounds(new LatLng(south, west), new LatLng(north, east));
    }

    private void moveOrAnimateCamera(CameraUpdate movement, boolean animate) {
        if (animate) {
            map.animateCamera(movement);
        } else {
            map.moveCamera(movement);
        }
    }

    private void enableLocationUpdates(boolean enable) {
        if (enable) {
            Timber.i("Starting LocationClient %s (for MapFragment %s)", locationClient, this);
            locationClient.start(this);
        } else {
            Timber.i("Stopping LocationClient %s (for MapFragment %s)", locationClient, this);
            locationClient.stop();
        }
    }

    private void updateLocationIndicator(LatLng loc, double radius) {
        if (map == null) {
            return;
        }
        if (locationCrosshairs == null) {
            locationCrosshairs = map.addMarker(new MarkerOptions()
                .position(loc)
                .icon(getBitmapDescriptor(getContext(), new MarkerIconDescription(org.odk.collect.maps.R.drawable.ic_crosshairs)))
                .anchor(0.5f, 0.5f)  // center the crosshairs on the position
            );
        }
        if (accuracyCircle == null) {
            int stroke = ContextUtils.getThemeAttributeValue(requireContext(), com.google.android.material.R.attr.colorPrimaryDark);
            int fill = getResources().getColor(org.odk.collect.androidshared.R.color.color_primary_low_emphasis);
            accuracyCircle = map.addCircle(new CircleOptions()
                .center(loc)
                .radius(radius)
                .strokeWidth(1)
                .strokeColor(stroke)
                .fillColor(fill)
            );
        }

        locationCrosshairs.setPosition(loc);
        accuracyCircle.setCenter(loc);
        accuracyCircle.setRadius(radius);
    }

    /** Finds the feature to which the given marker belongs. */
    private int findFeature(Marker marker) {
        for (int featureId : features.keySet()) {
            if (features.get(featureId).ownsMarker(marker)) {
                return featureId;
            }
        }
        return -1;  // not found
    }

    /** Finds the feature to which the given polyline belongs. */
    private int findFeature(Polyline polyline) {
        for (int featureId : features.keySet()) {
            if (features.get(featureId).ownsPolyline(polyline)) {
                return featureId;
            }
        }
        return -1;  // not found
    }

    private int findFeature(Polygon polygon) {
        for (int featureId : features.keySet()) {
            if (features.get(featureId).ownsPolygon(polygon)) {
                return featureId;
            }
        }
        return -1;  // not found
    }

    private void updateFeature(int featureId) {
        MapFeature feature = features.get(featureId);
        if (feature != null) {
            feature.update();
        }
    }

    private static Marker createMarker(Context context, MarkerDescription markerDescription, GoogleMap map) {
        if (map == null || context == null) {  // during Robolectric tests, map will be null
            return null;
        }
        // A Marker's position is a LatLng with just latitude and longitude
        // fields.  We need to store the point's altitude and standard
        // deviation values somewhere, so they go in the marker's snippet.
        return map.addMarker(new MarkerOptions()
            .position(toLatLng(markerDescription.getPoint()))
            .snippet(markerDescription.getPoint().altitude + ";" + markerDescription.getPoint().accuracy)
            .draggable(markerDescription.isDraggable())
            .icon(getBitmapDescriptor(context, markerDescription.getIconDescription()))
            .anchor(getIconAnchorValueX(markerDescription.getIconAnchor()), getIconAnchorValueY(markerDescription.getIconAnchor()))  // center the icon on the position
        );
    }

    private static float getIconAnchorValueX(@MapFragment.Companion.IconAnchor String iconAnchor) {
        switch (iconAnchor) {
            case BOTTOM:
            default:
                return 0.5f;
        }
    }

    private static float getIconAnchorValueY(@MapFragment.Companion.IconAnchor String iconAnchor) {
        switch (iconAnchor) {
            case BOTTOM:
                return 1.0f;
            default:
                return 0.5f;
        }
    }

    private static BitmapDescriptor getBitmapDescriptor(Context context, MarkerIconDescription markerIconDescription) {
        return BitmapDescriptorCache.getBitmapDescriptor(context, markerIconDescription);
    }

    private void onConfigChanged(Bundle config) {
        mapType = config.getInt(KEY_MAP_TYPE, GoogleMap.MAP_TYPE_NORMAL);
        referenceLayerFile = MapFragmentReferenceLayerUtils.getReferenceLayerFile(config, referenceLayerRepository);
        if (map != null) {
            map.setMapType(mapType);
            loadReferenceOverlay();
        }
    }

    private MapConfigurator createConfigurator() {
        return new GoogleMapConfigurator(
                ProjectKeys.KEY_GOOGLE_MAP_STYLE, org.odk.collect.strings.R.string.basemap_source_google,
                new GoogleMapTypeOption(GoogleMap.MAP_TYPE_NORMAL, org.odk.collect.strings.R.string.streets),
                new GoogleMapTypeOption(GoogleMap.MAP_TYPE_TERRAIN, org.odk.collect.strings.R.string.terrain),
                new GoogleMapTypeOption(GoogleMap.MAP_TYPE_HYBRID, org.odk.collect.strings.R.string.hybrid),
                new GoogleMapTypeOption(GoogleMap.MAP_TYPE_SATELLITE, org.odk.collect.strings.R.string.satellite)
        );
    }

    /**
     * A MapFeature is a physical feature on a map, such as a point, a road,
     * a building, a region, etc.  It is presented to the user as one editable
     * object, though its appearance may be constructed from multiple overlays
     * (e.g. geometric elements, handles for manipulation, etc.).
     */
    interface MapFeature {
        /** Returns true if the given marker belongs to this feature. */
        boolean ownsMarker(Marker marker);

        /** Returns true if the given polyline belongs to this feature. */
        boolean ownsPolyline(Polyline polyline);

        boolean ownsPolygon(Polygon polygon);

        /** Updates the feature's geometry after any UI handles have moved. */
        void update();

        /** Removes the feature from the map, leaving it no longer usable. */
        void dispose();
    }

    private static class MarkerFeature implements MapFeature {
        private Marker marker;
        private final Context context;

        MarkerFeature(Context context, MarkerDescription markerDescription, GoogleMap map) {
            this.context = context;
            marker = createMarker(context, markerDescription, map);
        }

        public void setIcon(MarkerIconDescription markerIconDescription) {
            marker.setIcon(getBitmapDescriptor(context, markerIconDescription));
        }

        public MapPoint getPoint() {
            return fromMarker(marker);
        }

        public boolean ownsMarker(Marker givenMarker) {
            return marker.equals(givenMarker);
        }

        public boolean ownsPolyline(Polyline givenPolyline) {
            return false;
        }

        @Override
        public boolean ownsPolygon(Polygon polygon) {
            return false;
        }

        public void update() { }

        public void dispose() {
            marker.remove();
            marker = null;
        }
    }

    private interface LineFeature extends MapFeature {

        List<MapPoint> getPoints();
    }

    /** A polyline or polygon that can not be manipulated by dragging markers at its vertices. */
    private static class StaticPolyLineFeature implements LineFeature {

        private List<MapPoint> points;
        private Polyline polyline;

        StaticPolyLineFeature(LineDescription lineDescription, GoogleMap map) {
            if (map == null) {  // during Robolectric tests, map will be null
                return;
            }

            points = lineDescription.getPoints();
            List<LatLng> latLngs = StreamSupport.stream(points.spliterator(), false).map(mapPoint -> new LatLng(mapPoint.latitude, mapPoint.longitude)).collect(Collectors.toList());
            if (lineDescription.getClosed() && !latLngs.isEmpty()) {
                latLngs.add(latLngs.get(0));
            }
            if (latLngs.isEmpty()) {
                clearPolyline();
            } else if (polyline == null) {
                polyline = map.addPolyline(new PolylineOptions()
                        .color(lineDescription.getStrokeColor())
                        .zIndex(1)
                        .width(lineDescription.getStrokeWidth())
                        .addAll(latLngs)
                        .clickable(true)
                );
            } else {
                polyline.setPoints(latLngs);
            }
        }

        @Override
        public boolean ownsMarker(Marker givenMarker) {
            return false;
        }

        @Override
        public boolean ownsPolyline(Polyline givenPolyline) {
            return polyline.equals(givenPolyline);
        }

        @Override
        public boolean ownsPolygon(Polygon polygon) {
            return false;
        }

        @Override
        public void update() {
        }

        @Override
        public void dispose() {
            clearPolyline();
        }

        private void clearPolyline() {
            if (polyline != null) {
                polyline.remove();
                polyline = null;
            }
        }

        @Override
        public List<MapPoint> getPoints() {
            return points;
        }
    }

    /** A polyline or polygon that can be manipulated by dragging markers at its vertices. */
    private static class DynamicPolyLineFeature implements LineFeature {

        private final Context context;
        private final GoogleMap map;
        private final List<Marker> markers = new ArrayList<>();
        private final LineDescription lineDescription;
        private Polyline polyline;

        DynamicPolyLineFeature(Context context, LineDescription lineDescription, GoogleMap map) {
            this.context = context;
            this.lineDescription = lineDescription;
            this.map = map;

            if (map == null) {  // during Robolectric tests, map will be null
                return;
            }

            for (MapPoint point : lineDescription.getPoints()) {
                markers.add(createMarker(context, new MarkerDescription(point, true, CENTER, new MarkerIconDescription(org.odk.collect.icons.R.drawable.ic_map_point)), map));
            }

            update();
        }

        @Override
        public boolean ownsMarker(Marker givenMarker) {
            return markers.contains(givenMarker);
        }

        @Override
        public boolean ownsPolyline(Polyline givenPolyline) {
            return polyline.equals(givenPolyline);
        }

        @Override
        public boolean ownsPolygon(Polygon polygon) {
            return false;
        }

        @Override
        public void update() {
            List<LatLng> latLngs = new ArrayList<>();
            for (Marker marker : markers) {
                latLngs.add(marker.getPosition());
            }
            if (lineDescription.getClosed() && !latLngs.isEmpty()) {
                latLngs.add(latLngs.get(0));
            }
            if (markers.isEmpty()) {
                clearPolyline();
            } else if (polyline == null) {
                polyline = map.addPolyline(new PolylineOptions()
                    .color(lineDescription.getStrokeColor())
                    .zIndex(1)
                    .width(lineDescription.getStrokeWidth())
                    .addAll(latLngs)
                    .clickable(true)
                );
            } else {
                polyline.setPoints(latLngs);
            }
        }

        @Override
        public void dispose() {
            clearPolyline();
            for (Marker marker : markers) {
                marker.remove();
            }
            markers.clear();
        }

        public List<MapPoint> getPoints() {
            List<MapPoint> points = new ArrayList<>();
            for (Marker marker : markers) {
                points.add(fromMarker(marker));
            }
            return points;
        }

        public void addPoint(MapPoint point) {
            if (map == null) {  // during Robolectric tests, map will be null
                return;
            }
            markers.add(createMarker(context, new MarkerDescription(point, true, CENTER, new MarkerIconDescription(org.odk.collect.icons.R.drawable.ic_map_point)), map));
            update();
        }

        public void removeLastPoint() {
            if (!markers.isEmpty()) {
                int last = markers.size() - 1;
                markers.get(last).remove();
                markers.remove(last);
                update();
            }
        }

        private void clearPolyline() {
            if (polyline != null) {
                polyline.remove();
                polyline = null;
            }
        }
    }

    private static class StaticPolygonFeature implements MapFeature {
        private Polygon polygon;

        StaticPolygonFeature(GoogleMap map, PolygonDescription polygonDescription) {
            polygon = map.addPolygon(new PolygonOptions()
                    .addAll(StreamSupport.stream(polygonDescription.getPoints().spliterator(), false).map(mapPoint -> new LatLng(mapPoint.latitude, mapPoint.longitude)).collect(Collectors.toList()))
                    .strokeColor(polygonDescription.getStrokeColor())
                    .strokeWidth(polygonDescription.getStrokeWidth())
                    .fillColor(polygonDescription.getFillColor())
                    .clickable(true)
            );
        }

        @Override
        public boolean ownsMarker(Marker marker) {
            return false;
        }

        @Override
        public boolean ownsPolyline(Polyline polyline) {
            return false;
        }

        @Override
        public boolean ownsPolygon(Polygon polygon) {
            return polygon.equals(this.polygon);
        }

        @Override
        public void update() {
        }

        @Override
        public void dispose() {
            if (polygon != null) {
                polygon.remove();
                polygon = null;
            }
        }
    }
}
