package org.odk.collect.android.configure.qr

import android.content.Context
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeResult
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.activities.ActivityUtils
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.fragments.BarCodeScannerFragment
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.mainmenu.MainMenuActivity
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.androidshared.ui.ToastUtils.showLongToast
import org.odk.collect.androidshared.utils.CompressionUtils
import org.odk.collect.settings.ODKAppSettingsImporter
import org.odk.collect.settings.importing.SettingsImportingResult
import java.io.File
import java.io.IOException
import java.util.zip.DataFormatException
import javax.inject.Inject

class QRCodeScannerFragment : BarCodeScannerFragment() {

    @Inject
    lateinit var settingsImporter: ODKAppSettingsImporter

    @Inject
    lateinit var projectsDataService: ProjectsDataService

    @Inject
    lateinit var storagePathProvider: StoragePathProvider

    override fun onAttach(context: Context) {
        super.onAttach(context)
        DaggerUtils.getComponent(context).inject(this)
    }

    @Throws(IOException::class, DataFormatException::class)
    override fun handleScanningResult(result: BarcodeResult) {
        val oldProjectName = projectsDataService.requireCurrentProject().name

        val settingsImportingResult = settingsImporter.fromJSON(
            CompressionUtils.decompress(result.text),
            projectsDataService.requireCurrentProject()
        )

        when (settingsImportingResult) {
            SettingsImportingResult.SUCCESS -> {
                Analytics.log(AnalyticsEvents.RECONFIGURE_PROJECT)

                val newProjectName = projectsDataService.requireCurrentProject().name
                if (newProjectName != oldProjectName) {
                    File(storagePathProvider.getProjectRootDirPath() + File.separator + oldProjectName).delete()
                    File(storagePathProvider.getProjectRootDirPath() + File.separator + newProjectName).createNewFile()
                }

                showLongToast(
                    requireContext(),
                    getString(org.odk.collect.strings.R.string.successfully_imported_settings)
                )
                ActivityUtils.startActivityAndCloseAllOthers(
                    requireActivity(),
                    MainMenuActivity::class.java
                )
            }

            SettingsImportingResult.INVALID_SETTINGS -> showLongToast(
                requireContext(),
                getString(
                    org.odk.collect.strings.R.string.invalid_qrcode
                )
            )

            SettingsImportingResult.GD_PROJECT -> showLongToast(
                requireContext(),
                getString(org.odk.collect.strings.R.string.settings_with_gd_protocol)
            )
        }
    }

    override fun getSupportedCodeFormats(): Collection<String> {
        return listOf(IntentIntegrator.QR_CODE)
    }
}
