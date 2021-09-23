package org.odk.collect.android.projects

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import com.google.zxing.client.android.BeepManager
import com.google.zxing.integration.android.IntentIntegrator
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.CaptureManager
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.R
import org.odk.collect.android.activities.ActivityUtils
import org.odk.collect.android.activities.MainMenuActivity
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.configure.SettingsImporter
import org.odk.collect.android.configure.qr.QRCodeDecoder
import org.odk.collect.android.databinding.QrCodeProjectCreatorDialogLayoutBinding
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.listeners.PermissionListener
import org.odk.collect.android.permissions.PermissionsProvider
import org.odk.collect.android.preferences.source.SettingsProvider
import org.odk.collect.android.utilities.ActivityAvailability
import org.odk.collect.android.utilities.CodeCaptureManagerFactory
import org.odk.collect.android.utilities.CompressionUtils
import org.odk.collect.android.utilities.DialogUtils
import org.odk.collect.android.views.BarcodeViewDecoder
import org.odk.collect.androidshared.utils.ToastUtils
import org.odk.collect.androidshared.utils.ToastUtils.showShortToast
import org.odk.collect.material.MaterialFullScreenDialogFragment
import org.odk.collect.projects.ProjectsRepository
import timber.log.Timber
import javax.inject.Inject

class QrCodeProjectCreatorDialog :
    MaterialFullScreenDialogFragment(),
    DuplicateProjectConfirmationDialog.DuplicateProjectConfirmationListener {

    @Inject
    lateinit var codeCaptureManagerFactory: CodeCaptureManagerFactory

    @Inject
    lateinit var barcodeViewDecoder: BarcodeViewDecoder

    @Inject
    lateinit var permissionsProvider: PermissionsProvider

    @Inject
    lateinit var projectCreator: ProjectCreator

    @Inject
    lateinit var currentProjectProvider: CurrentProjectProvider

    @Inject
    lateinit var projectsRepository: ProjectsRepository

    @Inject
    lateinit var settingsProvider: SettingsProvider

    lateinit var settingsConnectionMatcher: SettingsConnectionMatcher

    private var capture: CaptureManager? = null

    private lateinit var beepManager: BeepManager
    lateinit var binding: QrCodeProjectCreatorDialogLayoutBinding

    @Inject
    lateinit var activityAvailability: ActivityAvailability

    @Inject
    lateinit var qrCodeDecoder: QRCodeDecoder

    @Inject
    lateinit var settingsImporter: SettingsImporter

    private val imageQrCodeImportResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
            val imageUri: Uri? = result.data?.data
            if (imageUri != null) {
                permissionsProvider.requestReadUriPermission(
                    requireActivity(),
                    imageUri,
                    requireActivity().contentResolver,
                    object : PermissionListener {
                        override fun granted() {
                            // Do not call from a fragment that does not exist anymore https://github.com/getodk/collect/issues/4741
                            if (isAdded) {
                                requireActivity().contentResolver.openInputStream(imageUri).use {
                                    try {
                                        val settingsJson = qrCodeDecoder.decode(it)
                                        createProjectOrError(settingsJson)
                                    } catch (e: QRCodeDecoder.InvalidException) {
                                        showShortToast(
                                            requireContext(),
                                            R.string.invalid_qrcode
                                        )
                                    } catch (e: QRCodeDecoder.NotFoundException) {
                                        showShortToast(
                                            requireContext(),
                                            R.string.qr_code_not_found
                                        )
                                    }
                                }
                            }
                        }

                        override fun denied() {}
                    }
                )
            }
        }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        DaggerUtils.getComponent(context).inject(this)

        settingsConnectionMatcher = SettingsConnectionMatcher(projectsRepository, settingsProvider)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = QrCodeProjectCreatorDialogLayoutBinding.inflate(inflater)

        configureMenu()

        binding.configureManuallyButton.setOnClickListener {
            DialogUtils.showIfNotShowing(
                ManualProjectCreatorDialog::class.java,
                requireActivity().supportFragmentManager
            )
        }
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        beepManager = BeepManager(requireActivity())
        permissionsProvider.requestCameraPermission(
            requireActivity(),
            object : PermissionListener {
                override fun granted() {
                    // Do not call from a fragment that does not exist anymore https://github.com/getodk/collect/issues/4741
                    if (isAdded) {
                        startScanning(savedInstanceState)
                    }
                }

                override fun denied() {
                }
            }
        )
        return binding.root
    }

    private fun configureMenu() {
        binding.toolbar.menu.removeItem(R.id.menu_item_share)

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_item_scan_sd_card -> {
                    val photoPickerIntent = Intent(Intent.ACTION_GET_CONTENT)
                    photoPickerIntent.type = "image/*"
                    if (activityAvailability.isActivityAvailable(photoPickerIntent)) {
                        imageQrCodeImportResultLauncher.launch(photoPickerIntent)
                    } else {
                        showShortToast(
                            requireContext(),
                            getString(
                                R.string.activity_not_found,
                                getString(R.string.choose_image)
                            )
                        )
                        Timber.w(
                            getString(
                                R.string.activity_not_found,
                                getString(R.string.choose_image)
                            )
                        )
                    }
                }
            }
            false
        }
    }

    override fun onCloseClicked() {
    }

    override fun onBackPressed() {
        dismiss()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        capture?.onSaveInstanceState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        super.onPause()
        binding.barcodeView.pauseAndWait()
        capture?.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.barcodeView.resume()
        capture?.onResume()
    }

    override fun onDestroy() {
        capture?.onDestroy()
        super.onDestroy()
    }

    override fun getToolbar(): Toolbar? {
        return binding.toolbar
    }

    private fun startScanning(savedInstanceState: Bundle?) {
        capture = codeCaptureManagerFactory.getCaptureManager(
            requireActivity(),
            binding.barcodeView,
            savedInstanceState,
            listOf(IntentIntegrator.QR_CODE)
        )

        barcodeViewDecoder.waitForBarcode(binding.barcodeView).observe(
            viewLifecycleOwner,
            { barcodeResult: BarcodeResult ->
                try {
                    beepManager.playBeepSoundAndVibrate()
                } catch (e: Exception) {
                    // ignore because beeping isn't essential and this can crash the whole app
                }

                try {
                    val settingsJson = CompressionUtils.decompress(barcodeResult.text)
                    createProjectOrError(settingsJson)
                } catch (e: Exception) {
                    showShortToast(requireContext(), getString(R.string.invalid_qrcode))
                }
            }
        )
    }

    private fun createProjectOrError(settingsJson: String) {
        try {
            settingsConnectionMatcher.getProjectWithMatchingConnection(settingsJson)?.let { uuid ->
                val confirmationArgs = Bundle()
                confirmationArgs.putString(
                    DuplicateProjectConfirmationKeys.SETTINGS_JSON,
                    settingsJson
                )
                confirmationArgs.putString(DuplicateProjectConfirmationKeys.MATCHING_PROJECT, uuid)
                DialogUtils.showIfNotShowing(
                    DuplicateProjectConfirmationDialog::class.java,
                    confirmationArgs,
                    childFragmentManager
                )
            } ?: run {
                createProject(settingsJson)
            }
        } catch (e: Exception) {
            showShortToast(requireContext(), getString(R.string.invalid_qrcode))
        }
    }

    override fun createProject(settingsJson: String) {
        val projectCreatedSuccessfully = projectCreator.createNewProject(settingsJson)

        if (projectCreatedSuccessfully) {
            Analytics.log(AnalyticsEvents.QR_CREATE_PROJECT)

            ActivityUtils.startActivityAndCloseAllOthers(activity, MainMenuActivity::class.java)
            ToastUtils.showLongToast(
                requireContext(),
                getString(
                    R.string.switched_project,
                    currentProjectProvider.getCurrentProject().name
                )
            )
        } else {
            ToastUtils.showLongToast(requireContext(), getString(R.string.invalid_qrcode))
        }
    }

    override fun switchToProject(uuid: String) {
        currentProjectProvider.setCurrentProject(uuid)
        ActivityUtils.startActivityAndCloseAllOthers(activity, MainMenuActivity::class.java)
        ToastUtils.showLongToast(
            requireContext(),
            getString(
                R.string.switched_project,
                currentProjectProvider.getCurrentProject().name
            )
        )
    }
}
