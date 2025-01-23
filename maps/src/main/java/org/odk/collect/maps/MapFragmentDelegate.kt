package org.odk.collect.maps

import android.os.Bundle
import org.odk.collect.settings.keys.MetaKeys.LAST_KNOWN_ZOOM_LEVEL
import org.odk.collect.shared.settings.Settings
import org.odk.collect.shared.settings.Settings.OnSettingChangeListener
import java.util.function.Consumer

class MapFragmentDelegate(
    private val mapFragment: MapFragment,
    configuratorProvider: () -> MapConfigurator,
    unprotectedSettingsProvider: () -> Settings,
    metaSettingsProvider: () -> Settings,
    private val onConfigChanged: Consumer<Bundle>
) : OnSettingChangeListener {

    private val configurator by lazy { configuratorProvider() }
    private val unprotectedSettings by lazy { unprotectedSettingsProvider() }
    private val metaSettings by lazy { metaSettingsProvider() }

    private var savedInstanceState: Bundle? = null
    var zoomLevel: Float? = null
        private set

    fun onCreate(savedInstanceState: Bundle?) {
        this.savedInstanceState = savedInstanceState
    }

    fun onReady() {
        val mapCenter: MapPoint? = savedInstanceState?.getParcelable(MAP_CENTER_KEY)
        val mapZoom: Double? = savedInstanceState?.getDouble(MAP_ZOOM_KEY)

        if (mapCenter != null && mapZoom != null) {
            mapFragment.zoomToPoint(mapCenter, mapZoom, false)
        } else if (mapCenter != null) {
            mapFragment.zoomToPoint(mapCenter, false)
        }
    }

    fun onStart() {
        if (metaSettings.contains(LAST_KNOWN_ZOOM_LEVEL)) {
            zoomLevel = metaSettings.getFloat(LAST_KNOWN_ZOOM_LEVEL)
        }
        onConfigChanged.accept(configurator.buildConfig(unprotectedSettings))
        unprotectedSettings.registerOnSettingChangeListener(this)
    }

    fun onStop() {
        if (zoomLevel != null) {
            metaSettings.save(LAST_KNOWN_ZOOM_LEVEL, zoomLevel)
        }
        unprotectedSettings.unregisterOnSettingChangeListener(this)
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putParcelable(MAP_CENTER_KEY, mapFragment.getCenter())
        outState.putDouble(MAP_ZOOM_KEY, mapFragment.getZoom())
    }

    fun onZoomLevelChangedByUserListener(zoomLevel: Float?) {
        this.zoomLevel = zoomLevel
    }

    override fun onSettingChanged(key: String) {
        if (configurator.prefKeys.contains(key)) {
            onConfigChanged.accept(configurator.buildConfig(unprotectedSettings))
        }
    }

    companion object {
        private const val MAP_CENTER_KEY = "map_center"
        private const val MAP_ZOOM_KEY = "map_zoom"
    }
}
