package org.odk.collect.android.configure.qr

import android.content.Context
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeResult
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.R
import org.odk.collect.android.activities.ActivityUtils
import org.odk.collect.android.activities.MainMenuActivity
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.configure.SettingsImporter
import org.odk.collect.android.fragments.BarCodeScannerFragment
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.projects.CurrentProjectProvider
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.android.utilities.CompressionUtils
import org.odk.collect.androidshared.utils.ToastUtils.showLongToast
import java.io.File
import java.io.IOException
import java.util.zip.DataFormatException
import javax.inject.Inject

class QRCodeScannerFragment : BarCodeScannerFragment() {
    @Inject
    lateinit var settingsImporter: SettingsImporter

    @Inject
    lateinit var currentProjectProvider: CurrentProjectProvider

    @Inject
    lateinit var storagePathProvider: StoragePathProvider

    override fun onAttach(context: Context) {
        super.onAttach(context)
        DaggerUtils.getComponent(context).inject(this)
    }

    @Throws(IOException::class, DataFormatException::class)
    override fun handleScanningResult(result: BarcodeResult) {
        val oldProjectName = currentProjectProvider.getCurrentProject().name

        val importSuccess = settingsImporter.fromJSON(
            CompressionUtils.decompress(result.text),
            currentProjectProvider.getCurrentProject()
        )

        if (importSuccess) {
            Analytics.log(AnalyticsEvents.RECONFIGURE_PROJECT)

            val newProjectName = currentProjectProvider.getCurrentProject().name
            if (newProjectName != oldProjectName) {
                File(storagePathProvider.getProjectRootDirPath() + File.separator + oldProjectName).delete()
                File(storagePathProvider.getProjectRootDirPath() + File.separator + newProjectName).createNewFile()
            }

            showLongToast(requireContext(), getString(R.string.successfully_imported_settings))
            ActivityUtils.startActivityAndCloseAllOthers(
                requireActivity(),
                MainMenuActivity::class.java
            )
        } else {
            showLongToast(requireContext(), getString(R.string.invalid_qrcode))
        }
    }

    override fun getSupportedCodeFormats(): Collection<String> {
        return listOf(IntentIntegrator.QR_CODE)
    }
}
