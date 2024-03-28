package org.odk.collect.android.support.rules

import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assume.assumeTrue
import org.junit.rules.ExternalResource
import org.odk.collect.android.support.CollectHelpers
import org.odk.collect.testshared.DummyActivity

class RecentAppsRule : ExternalResource() {

    override fun before() {
        assumeTrue(SUPPORTED_SDKS.contains(Build.VERSION.SDK_INT)) // Skip if we're not using API 30
        removeRecentAppsTooltips()
    }

    fun killApp() {
        if (Build.VERSION.SDK_INT == 30) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            device.pressRecentApps()
            device
                .findObject(UiSelector().descriptionContains("Collect"))
                .swipeUp(10).also {
                    CollectHelpers.simulateProcessRestart() // the process is not restarted automatically (probably to keep the test running) so we have simulate it
                }
        } else {
            throw NotImplementedError()
        }
    }

    /**
     * Makes sure `Page#killAndReopenApp` doesn't run into problems with tooltips by opening
     * Recent Apps and dismissing before any test runs. Only needs to run once per test process.
     */
    private fun removeRecentAppsTooltips() {
        if (Build.VERSION.SDK_INT == 30) {
            val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

            // Open dummy activity so there is something in Recent Apps
            InstrumentationRegistry.getInstrumentation().targetContext.apply {
                val intent = Intent(this.applicationContext, DummyActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                device.wait(Until.hasObject(By.textStartsWith(DummyActivity.TEXT)), 1000)
            }

            // Open Recent Apps and dismiss tooltips if they're there
            device.pressRecentApps()
            val foundToolTip = device.wait(
                Until.hasObject(By.textStartsWith("Select text and images to copy")),
                1000
            )
            if (foundToolTip) {
                device.pressBack() // the first time we open the list of recent apps, a tooltip might be displayed and we need to close it
            }

            // Close recent apps
            device.pressBack()

            // Close dummy activity
            device.pressBack()
        } else {
            throw NotImplementedError()
        }
    }

    companion object {
        private val SUPPORTED_SDKS = listOf(30)
    }
}