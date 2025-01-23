package org.odk.collect.geo.selection

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import androidx.activity.OnBackPressedDispatcher
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.notNullValue
import org.hamcrest.Matchers.nullValue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.odk.collect.androidshared.livedata.MutableNonNullLiveData
import org.odk.collect.androidshared.ui.FragmentFactoryBuilder
import org.odk.collect.async.Scheduler
import org.odk.collect.fragmentstest.FragmentScenarioLauncherRule
import org.odk.collect.geo.DaggerGeoDependencyComponent
import org.odk.collect.geo.GeoDependencyModule
import org.odk.collect.geo.R
import org.odk.collect.geo.support.FakeMapFragment
import org.odk.collect.geo.support.Fixtures
import org.odk.collect.geo.support.RobolectricApplication
import org.odk.collect.maps.MapFragment
import org.odk.collect.maps.MapFragmentFactory
import org.odk.collect.maps.MapPoint
import org.odk.collect.maps.layers.OfflineMapLayersPickerBottomSheetDialogFragment
import org.odk.collect.maps.layers.ReferenceLayerRepository
import org.odk.collect.material.BottomSheetBehavior
import org.odk.collect.material.MaterialProgressDialogFragment
import org.odk.collect.permissions.PermissionsChecker
import org.odk.collect.settings.InMemSettingsProvider
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.testshared.RobolectricHelpers.getFragmentByClass
import org.odk.collect.webpage.ExternalWebPageHelper

@RunWith(AndroidJUnit4::class)
class SelectionMapFragmentTest {

    private val application = ApplicationProvider.getApplicationContext<RobolectricApplication>()

    private lateinit var map: FakeMapFragment
    private val data = mock<SelectionMapData> {
        on { isLoading() } doReturn MutableNonNullLiveData(false)
        on { getMapTitle() } doReturn MutableLiveData("")
        on { getItemType() } doReturn "Things"
        on { getItemCount() } doReturn MutableNonNullLiveData(0)
        on { getMappableItems() } doReturn MutableLiveData(emptyList())
    }

    private val onBackPressedDispatcher = OnBackPressedDispatcher()

    @get:Rule
    val launcherRule = FragmentScenarioLauncherRule(
        object : FragmentFactory() {
            override fun instantiate(classLoader: ClassLoader, className: String): Fragment {
                return SelectionMapFragment(data, onBackPressedDispatcher = { onBackPressedDispatcher })
            }
        }
    )

    @Before
    fun setup() {
        application.geoDependencyComponent = DaggerGeoDependencyComponent.builder()
            .application(application)
            .geoDependencyModule(object : GeoDependencyModule() {
                override fun providesMapFragmentFactory(): MapFragmentFactory {
                    return object : MapFragmentFactory {
                        override fun createMapFragment(): MapFragment {
                            return FakeMapFragment().also {
                                map = it
                            }
                        }
                    }
                }

                override fun providesPermissionChecker(context: Context): PermissionsChecker {
                    return object : PermissionsChecker {
                        override fun isPermissionGranted(vararg permissions: String): Boolean {
                            return true
                        }
                    }
                }

                override fun providesReferenceLayerRepository(): ReferenceLayerRepository {
                    return mock()
                }

                override fun providesScheduler(): Scheduler {
                    return mock()
                }

                override fun providesSettingsProvider(): SettingsProvider {
                    return InMemSettingsProvider()
                }

                override fun providesExternalWebPageHelper(): ExternalWebPageHelper {
                    return mock()
                }
            }).build()

        BottomSheetBehavior.DRAGGING_ENABLED = false
    }

    @Test
    fun `summary sheet is hidden initially`() {
        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        onView(withId(R.id.summary_sheet)).check(matches(not(isDisplayed())))
    }

    @Test
    fun `does not have enabled back callbacks`() {
        val scenario = launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        scenario.onFragment {
            assertThat(onBackPressedDispatcher.hasEnabledCallbacks(), equalTo(false))
        }
    }

    @Test
    fun `updates markers when items update`() {
        val items: List<MappableSelectItem> = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0))
        )
        val itemsLiveData = MutableLiveData(items)
        whenever(data.getMappableItems()).thenReturn(itemsLiveData)

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        assertThat(map.getMarkers(), equalTo(itemsLiveData.value?.map { (it as MappableSelectItem.MappableSelectPoint).toMapPoint() }))

        itemsLiveData.value = emptyList()
        assertThat(map.getMarkers(), equalTo(emptyList()))
    }

    @Test
    fun `updates item count when items update`() {
        val items: List<MappableSelectItem> = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0),
            Fixtures.actionMappableSelectPoint().copy(id = 1)
        )

        val itemsLiveData = MutableLiveData(items)
        whenever(data.getMappableItems()).thenReturn(itemsLiveData)

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        onView(withText(application.getString(org.odk.collect.strings.R.string.select_item_count, "Things", 0, 2)))
            .check(matches(isDisplayed()))

        itemsLiveData.value = emptyList()
        onView(withText(application.getString(org.odk.collect.strings.R.string.select_item_count, "Things", 0, 0)))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `shows polyline when item has multiple points`() {
        val items: List<MappableSelectItem> = listOf(
            Fixtures.actionMappableSelectLine().copy(
                id = 0,
                points = listOf(
                    MapPoint(40.0, 0.0),
                    MapPoint(41.0, 0.0)
                ),
                strokeWidth = "10",
                strokeColor = "#ffffff"
            )
        )

        val itemsLiveData = MutableLiveData(items)
        whenever(data.getMappableItems()).thenReturn(itemsLiveData)

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()
        assertThat(map.getPolyLines()[0].points, equalTo(itemsLiveData.value?.map { (it as MappableSelectItem.MappableSelectLine).points }?.first()))
        assertThat(map.getPolyLines()[0].getStrokeWidth(), equalTo(10f))
        assertThat(map.getPolyLines()[0].getStrokeColor(), equalTo(-1))
        onView(withText(application.getString(org.odk.collect.strings.R.string.select_item_count, "Things", 0, 1)))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `shows polygon when item has multiple closed points`() {
        val items: List<MappableSelectItem> = listOf(
            Fixtures.actionMappableSelectPolygon().copy(
                id = 0,
                points = listOf(
                    MapPoint(40.0, 0.0),
                    MapPoint(41.0, 0.0),
                    MapPoint(40.0, 0.0)
                ),
                strokeWidth = "10",
                strokeColor = "#aaccee",
                fillColor = "#ffffff"
            )
        )

        val itemsLiveData = MutableLiveData(items)
        whenever(data.getMappableItems()).thenReturn(itemsLiveData)

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        assertThat(map.getPolygons()[0].points, equalTo(itemsLiveData.value?.map { (it as MappableSelectItem.MappableSelectPolygon).points }?.first()))
        assertThat(map.getPolygons()[0].getStrokeWidth(), equalTo(10f))
        assertThat(map.getPolygons()[0].getStrokeColor(), equalTo(-5583634))
        assertThat(map.getPolygons()[0].getFillColor(), equalTo(1157627903))
        onView(withText(application.getString(org.odk.collect.strings.R.string.select_item_count, "Things", 0, 1)))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `zooms to fit all items`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0))
        )

        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        val points = items.map { it.toMapPoint() }
        assertThat(map.getZoomBoundingBox(), equalTo(Pair(points, 0.8)))
    }

    @Test
    fun `zooms to fit all points in for item with multiple points`() {
        val points = listOf(MapPoint(40.0, 0.0), MapPoint(41.0, 0.0))
        val items: List<MappableSelectItem> = listOf(
            Fixtures.actionMappableSelectLine().copy(id = 0, points = points)
        )

        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        assertThat(map.getZoomBoundingBox(), equalTo(Pair(points, 0.8)))
    }

    @Test
    fun `does not zoom to fit all items again when they change`() {
        val originalItems =
            listOf(Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)))
        val itemsLiveData: MutableLiveData<List<MappableSelectItem>?> =
            MutableLiveData(originalItems)
        whenever(data.getMappableItems()).thenReturn(itemsLiveData)

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        itemsLiveData.value =
            listOf(Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(52.0, 0.0)))

        val points = originalItems.map { it.toMapPoint() }
        assertThat(map.getZoomBoundingBox(), equalTo(Pair(points, 0.8)))
    }

    @Test
    fun `does not zoom to fit all items if map already has center`() {
        val items = listOf(Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)))
        val itemsLiveData: MutableLiveData<List<MappableSelectItem>?> =
            MutableLiveData(items)
        whenever(data.getMappableItems()).thenReturn(itemsLiveData)

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.setCenter(MapPoint(12.3, 45.6), false)
        map.ready()

        assertThat(map.getCenter(), equalTo(MapPoint(12.3, 45.6)))
    }

    @Test
    fun `zooms to current location when zoomToFitItems is false`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0))
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(
            SelectionMapFragment::class.java,
            factory = FragmentFactoryBuilder()
                .forClass(SelectionMapFragment::class.java) {
                    SelectionMapFragment(
                        data,
                        zoomToFitItems = false,
                        onBackPressedDispatcher = { onBackPressedDispatcher }
                    )
                }.build()
        )
        map.ready()

        assertThat(map.hasCenter(), equalTo(false))

        map.setLocation(MapPoint(1.0, 2.0))
        assertThat(map.getCenter(), equalTo(MapPoint(1.0, 2.0)))
    }

    @Test
    fun `zooms to current location with default zoom level when there are no items and there is no zoom level saved`() {
        whenever(data.getMappableItems()).doReturn(MutableLiveData(emptyList()))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        assertThat(map.hasCenter(), equalTo(false))

        map.setLocation(MapPoint(1.0, 2.0))
        assertThat(map.getCenter(), equalTo(MapPoint(1.0, 2.0)))
        assertThat(map.getZoom(), equalTo(MapFragment.POINT_ZOOM.toDouble()))
    }

    @Test
    fun `zooms to current location with saved zoom level when there are no items and there is last zoom level set by user saved`() {
        whenever(data.getMappableItems()).doReturn(MutableLiveData(emptyList()))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.setZoomLevel(10f)
        map.ready()

        assertThat(map.hasCenter(), equalTo(false))

        map.setLocation(MapPoint(1.0, 2.0))
        assertThat(map.getCenter(), equalTo(MapPoint(1.0, 2.0)))
        assertThat(map.getZoom(), equalTo(10.0))
    }

    @Test
    fun `does not zoom to current location when it changes`() {
        whenever(data.getMappableItems()).doReturn(MutableLiveData(emptyList()))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        assertThat(map.hasCenter(), equalTo(false))

        map.setLocation(MapPoint(1.0, 2.0))
        assertThat(map.getCenter(), equalTo(MapPoint(1.0, 2.0)))

        map.setLocation(MapPoint(3.0, 4.0))
        assertThat(map.getCenter(), equalTo(MapPoint(1.0, 2.0)))
    }

    @Test
    fun `does not zoom to current location when items change`() {
        val originalItems =
            listOf(Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)))
        val itemsLiveData: MutableLiveData<List<MappableSelectItem>?> =
            MutableLiveData(originalItems)
        whenever(data.getMappableItems()).thenReturn(itemsLiveData)

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()
        map.setLocation(MapPoint(67.0, 48.0))

        itemsLiveData.value =
            listOf(Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(52.0, 0.0)))

        val points = originalItems.map { it.toMapPoint() }
        assertThat(map.getZoomBoundingBox(), equalTo(Pair(points, 0.8)))
    }

    @Test
    fun `clicking current location button zooms to gps location`() {
        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.setLocation(MapPoint(40.181389, 44.514444))
        onView(withId(R.id.zoom_to_location)).perform(click())

        assertThat(map.getCenter(), equalTo(MapPoint(40.181389, 44.514444)))
        assertThat(map.getZoom(), equalTo(MapFragment.POINT_ZOOM.toDouble()))
    }

    @Test
    fun `clicking zoom to fit button zooms to fit all items`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0))
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        onView(withId(R.id.zoom_to_bounds)).perform(click())

        val points = items.map { it.toMapPoint() }
        assertThat(map.getZoomBoundingBox(), equalTo(Pair(points, 0.8)))
    }

    @Test
    fun `clicking zoom to fit button zooms to fit all polys`() {
        val points = listOf(MapPoint(40.0, 0.0), MapPoint(41.0, 0.0))
        val items: List<MappableSelectItem> = listOf(
            Fixtures.actionMappableSelectLine().copy(id = 0, points = points)
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        onView(withId(R.id.zoom_to_bounds)).perform(click())
        assertThat(map.getZoomBoundingBox(), equalTo(Pair(points, 0.8)))
    }

    @Test
    fun `clicking layers button navigates to layers settings`() {
        val scenario = launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        onView(withId(R.id.layer_menu)).perform(click())
        scenario.onFragment {
            assertThat(
                it.childFragmentManager.findFragmentByTag(OfflineMapLayersPickerBottomSheetDialogFragment::class.java.name),
                instanceOf(OfflineMapLayersPickerBottomSheetDialogFragment::class.java)
            )
        }
    }

    @Test
    fun `clicking on item centers on that item with current zoom level`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0))
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.zoomToPoint(MapPoint(55.0, 66.0), 2.0, false)

        map.clickOnFeature(1)
        assertThat(map.getCenter(), equalTo(items[1].toMapPoint()))
        assertThat(map.getZoom(), equalTo(2.0))
    }

    @Test
    fun `clicking on item with multiple points zooms to fit all item points`() {
        val itemPoints = listOf(MapPoint(40.0, 0.0), MapPoint(41.0, 0.0))
        val items = listOf(
            Fixtures.actionMappableSelectLine().copy(id = 0, points = listOf(MapPoint(40.0, 0.0))),
            Fixtures.actionMappableSelectLine().copy(id = 1, points = itemPoints)
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(1)
        assertThat(map.getZoomBoundingBox(), equalTo(Pair(itemPoints, 0.8)))
    }

    /**
     * This looks like a duplicated test, but it's easy to write an implementation that will work
     * for everything else and break for interleaved points and traces.
     */
    @Test
    fun `clicking on item always selects correct item`() {
        val items = listOf(
            Fixtures.actionMappableSelectLine().copy(id = 0, points = listOf(MapPoint(40.0, 0.0), MapPoint(41.0, 0.0))),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(45.0, 0.0))
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeatureId(map.getFeatureId(listOf((items[1] as MappableSelectItem.MappableSelectPoint).point)))
        assertThat(map.getCenter(), equalTo((items[1] as MappableSelectItem.MappableSelectPoint).point))
    }

    @Test
    fun `clicking on item switches item marker to large icon`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(
                id = 0,
                smallIcon = android.R.drawable.ic_lock_idle_charging,
                largeIcon = android.R.drawable.ic_lock_idle_alarm,
                symbol = "A",
                color = "#ffffff"
            ),
            Fixtures.actionMappableSelectPoint().copy(
                id = 1,
                smallIcon = android.R.drawable.ic_lock_idle_charging,
                largeIcon = android.R.drawable.ic_lock_idle_alarm,
                symbol = "B",
                color = "#000000"
            )
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(1)

        val firstIcon = map.getMarkerIcons()[0]!!
        assertThat(firstIcon.icon, equalTo(items[0].smallIcon))
        assertThat(firstIcon.getSymbol(), equalTo("A"))
        assertThat(firstIcon.getColor(), equalTo(Color.parseColor("#ffffff")))

        val secondIcon = map.getMarkerIcons()[1]!!
        assertThat(secondIcon.icon, equalTo(items[1].largeIcon))
        assertThat(secondIcon.getSymbol(), equalTo("B"))
        assertThat(secondIcon.getColor(), equalTo(Color.parseColor("#000000")))
    }

    @Test
    fun `clicking on item when another has been tapped switches the first one back to its small icon`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(
                id = 0,
                smallIcon = android.R.drawable.ic_lock_idle_charging,
                largeIcon = android.R.drawable.ic_lock_idle_alarm,
                symbol = "A",
                color = "#ffffff"
            ),
            Fixtures.actionMappableSelectPoint().copy(
                id = 1,
                smallIcon = android.R.drawable.ic_lock_idle_charging,
                largeIcon = android.R.drawable.ic_lock_idle_alarm,
                symbol = "B",
                color = "#000000"
            )
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(0)
        map.clickOnFeature(1)

        val firstIcon = map.getMarkerIcons()[0]!!
        assertThat(firstIcon.icon, equalTo(items[0].smallIcon))
        assertThat(firstIcon.getSymbol(), equalTo("A"))
        assertThat(firstIcon.getColor(), equalTo(Color.parseColor("#ffffff")))

        val secondIcon = map.getMarkerIcons()[1]!!
        assertThat(secondIcon.icon, equalTo(items[1].largeIcon))
        assertThat(secondIcon.getSymbol(), equalTo("B"))
        assertThat(secondIcon.getColor(), equalTo(Color.parseColor("#000000")))
    }

    @Test
    fun `clicking on item sets item on summary sheet`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, name = "Blah1"),
            Fixtures.actionMappableSelectPoint().copy(id = 1, name = "Blah2")
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(0)
        onView(allOf(isDescendantOfA(withId(R.id.summary_sheet)), withText("Blah1")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `clicking on item returns item ID as result when skipSummary is true`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0),
            Fixtures.actionMappableSelectPoint().copy(id = 1)
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        val scenario = launcherRule.launchInContainer(
            SelectionMapFragment::class.java,
            factory = FragmentFactoryBuilder()
                .forClass(SelectionMapFragment::class.java) {
                    SelectionMapFragment(
                        data,
                        skipSummary = true,
                        onBackPressedDispatcher = { onBackPressedDispatcher }
                    )
                }.build()
        )
        map.ready()

        var actualResult: Bundle? = null
        scenario.onFragment {
            it.parentFragmentManager.setFragmentResultListener(
                SelectionMapFragment.REQUEST_SELECT_ITEM,
                it
            ) { _: String?, result: Bundle ->
                actualResult = result
            }
        }

        map.clickOnFeature(0)
        assertThat(
            actualResult!!.getLong(SelectionMapFragment.RESULT_SELECTED_ITEM),
            equalTo(items[0].id)
        )
    }

    @Test
    fun `clicking map with an item selected deselects it`() {
        val item = Fixtures.actionMappableSelectPoint().copy(id = 0, name = "Blah1")
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(listOf(item)))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(0)
        map.click(MapPoint(0.0, 0.0))

        onView(allOf(isDescendantOfA(withId(R.id.summary_sheet)), withText("Blah1")))
            .check(matches(not(isDisplayed())))
        assertThat(map.getMarkerIcons()[0]!!.icon, equalTo(item.smallIcon))
    }

    @Test
    fun `pressing back with an item selected deselects it`() {
        val item = Fixtures.actionMappableSelectPoint()
            .copy(id = 0, name = "Blah1", symbol = "A", color = "#ffffff")
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(listOf(item)))

        val scenario = launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(0)
        scenario.onFragment {
            onBackPressedDispatcher.onBackPressed()
        }

        onView(allOf(isDescendantOfA(withId(R.id.summary_sheet)), withText("Blah1")))
            .check(matches(not(isDisplayed())))

        assertThat(map.getMarkerIcons()[0]!!.icon, equalTo(item.smallIcon))
        assertThat(map.getMarkerIcons()[0]!!.getSymbol(), equalTo("A"))
        assertThat(map.getMarkerIcons()[0]!!.getColor(), equalTo(Color.parseColor("#ffffff")))
    }

    @Test
    fun `pressing back after deselecting item disables back callbacks`() {
        val item = Fixtures.actionMappableSelectPoint().copy(id = 0, name = "Blah1")
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(listOf(item)))

        val scenario = launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(0)
        scenario.onFragment {
            onBackPressedDispatcher.onBackPressed()
            assertThat(onBackPressedDispatcher.hasEnabledCallbacks(), equalTo(false))
        }
    }

    @Test
    fun `recreating after deselecting item has no item selected`() {
        val items = listOf(Fixtures.actionMappableSelectPoint().copy(id = 0, name = "Point1"))
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        val scenario = launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(0)
        scenario.onFragment {
            onBackPressedDispatcher.onBackPressed()
        }

        scenario.recreate()
        map.ready()

        onView(allOf(isDescendantOfA(withId(R.id.summary_sheet)), withText("Point1")))
            .check(doesNotExist())
    }

    @Test
    fun `clicking action hides summary sheet`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(
                id = 0,
                name = "Item",
                action = IconifiedText(null, "Action")
            )
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(0)
        onView(withText("Action")).perform(click())
        onView(withText("Item")).check(matches(not(isDisplayed())))
    }

    @Test
    fun `centers on already selected item`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(40.1, 0.0), selected = true)
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        assertThat(map.getCenter(), equalTo(items[1].toMapPoint()))
        assertThat(map.getZoom(), equalTo(MapFragment.POINT_ZOOM.toDouble()))
    }

    @Test
    fun `does not move when location changes when centered on already selected item`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0), selected = true)
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.setLocation(MapPoint(1.0, 2.0))
        assertThat(map.getCenter(), equalTo(items[1].toMapPoint()))
    }

    @Test
    fun `hides new item button when showNewItemButton is false`() {
        launcherRule.launchInContainer(
            SelectionMapFragment::class.java,
            factory = FragmentFactoryBuilder()
                .forClass(SelectionMapFragment::class.java) {
                    SelectionMapFragment(
                        data,
                        showNewItemButton = false,
                        onBackPressedDispatcher = { onBackPressedDispatcher }
                    )
                }.build()
        )
        map.ready()

        onView(withContentDescription(org.odk.collect.strings.R.string.new_item)).check(matches(not(isDisplayed())))
    }

    @Test
    fun `ignores feature clicks for IDs that are not item features`() {
        launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeatureId(-1)
        map.clickOnFeatureId(-2) // First click is fine but second could use the ID and crash
    }

    @Test
    fun `recreating maintains selection`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0), name = "Point1"),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0), name = "Point2")
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        val scenario = launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(1)

        scenario.recreate()
        map.ready()

        onView(allOf(isDescendantOfA(withId(R.id.summary_sheet)), withText("Point2")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `recreating with initial selection maintains new selection`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint()
                .copy(id = 0, point = MapPoint(40.0, 0.0), name = "Point1", selected = true),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0), name = "Point2")
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        val scenario = launcherRule.launchInContainer(SelectionMapFragment::class.java)
        map.ready()

        map.clickOnFeature(1)

        scenario.recreate()
        map.ready()

        onView(allOf(isDescendantOfA(withId(R.id.summary_sheet)), withText("Point2")))
            .check(matches(isDisplayed()))
    }

    @Test
    fun `shows progress dialog when loading`() {
        val loadingLiveData = MutableNonNullLiveData(false)
        whenever(data.isLoading()).thenReturn(loadingLiveData)

        launcherRule.launchInContainer(SelectionMapFragment::class.java).onFragment {
            val dialogClass = MaterialProgressDialogFragment::class.java
            assertThat(getFragmentByClass(it.childFragmentManager, dialogClass), nullValue())

            loadingLiveData.value = true
            assertThat(getFragmentByClass(it.childFragmentManager, dialogClass), notNullValue())

            loadingLiveData.value = false
            assertThat(getFragmentByClass(it.childFragmentManager, dialogClass), nullValue())
        }
    }

    @Test
    fun `onDestroy works if the view was never created`() {
        val scenario = launcherRule.launchInContainer(
            SelectionMapFragment::class.java,
            initialState = Lifecycle.State.CREATED // `onCreateView` is called at `RESUMED`
        )

        scenario.moveToState(Lifecycle.State.DESTROYED)
    }

    @Test
    fun `opening the map with already selected item when skipSummary is true does not close the map`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0), selected = true)
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        val scenario = launcherRule.launchInContainer(
            SelectionMapFragment::class.java,
            factory = FragmentFactoryBuilder()
                .forClass(SelectionMapFragment::class.java) {
                    SelectionMapFragment(
                        data,
                        skipSummary = true,
                        onBackPressedDispatcher = { onBackPressedDispatcher }
                    )
                }.build()
        )
        var actualResult: Bundle? = null
        scenario.onFragment {
            it.parentFragmentManager.setFragmentResultListener(
                SelectionMapFragment.REQUEST_SELECT_ITEM,
                it
            ) { _: String?, result: Bundle ->
                actualResult = result
            }
        }

        map.ready()

        assertThat(actualResult, equalTo(null))
    }

    @Test
    fun `recreating the map with already selected item when skipSummary is true does not close the map`() {
        val items = listOf(
            Fixtures.actionMappableSelectPoint().copy(id = 0, point = MapPoint(40.0, 0.0)),
            Fixtures.actionMappableSelectPoint().copy(id = 1, point = MapPoint(41.0, 0.0), selected = true)
        )
        whenever(data.getMappableItems()).thenReturn(MutableLiveData(items))

        val scenario = launcherRule.launchInContainer(
            SelectionMapFragment::class.java,
            factory = FragmentFactoryBuilder()
                .forClass(SelectionMapFragment::class.java) {
                    SelectionMapFragment(
                        data,
                        skipSummary = true,
                        onBackPressedDispatcher = { onBackPressedDispatcher }
                    )
                }.build()
        )
        map.ready()
        scenario.recreate()
        var actualResult: Bundle? = null
        scenario.onFragment {
            it.parentFragmentManager.setFragmentResultListener(
                SelectionMapFragment.REQUEST_SELECT_ITEM,
                it
            ) { _: String?, result: Bundle ->
                actualResult = result
            }
        }
        map.ready()

        assertThat(actualResult, equalTo(null))
    }

    private fun MappableSelectItem.MappableSelectPoint.toMapPoint(): MapPoint {
        return MapPoint(this.point.latitude, this.point.longitude)
    }
}
