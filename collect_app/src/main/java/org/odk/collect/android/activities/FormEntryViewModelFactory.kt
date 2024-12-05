package org.odk.collect.android.activities

import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.savedstate.SavedStateRegistryOwner
import org.javarosa.core.model.actions.recordaudio.RecordAudioActions
import org.javarosa.core.model.instance.TreeReference
import org.odk.collect.android.entities.EntitiesRepositoryProvider
import org.odk.collect.android.formentry.BackgroundAudioViewModel
import org.odk.collect.android.formentry.BackgroundAudioViewModel.RecordAudioActionRegistry
import org.odk.collect.android.formentry.FormEndViewModel
import org.odk.collect.android.formentry.FormEntryViewModel
import org.odk.collect.android.formentry.FormSessionRepository
import org.odk.collect.android.formentry.PrinterWidgetViewModel
import org.odk.collect.android.formentry.audit.IdentityPromptViewModel
import org.odk.collect.android.formentry.backgroundlocation.BackgroundLocationHelper
import org.odk.collect.android.formentry.backgroundlocation.BackgroundLocationManager
import org.odk.collect.android.formentry.backgroundlocation.BackgroundLocationViewModel
import org.odk.collect.android.formentry.saving.DiskFormSaver
import org.odk.collect.android.formentry.saving.FormSaveViewModel
import org.odk.collect.android.instancemanagement.InstancesDataService
import org.odk.collect.android.instancemanagement.autosend.AutoSendSettingsProvider
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.android.utilities.ApplicationConstants
import org.odk.collect.android.utilities.ChangeLockProvider
import org.odk.collect.android.utilities.FormsRepositoryProvider
import org.odk.collect.android.utilities.InstancesRepositoryProvider
import org.odk.collect.android.utilities.MediaUtils
import org.odk.collect.android.utilities.SavepointsRepositoryProvider
import org.odk.collect.async.Scheduler
import org.odk.collect.audiorecorder.recording.AudioRecorder
import org.odk.collect.location.LocationClient
import org.odk.collect.permissions.PermissionsChecker
import org.odk.collect.permissions.PermissionsProvider
import org.odk.collect.printer.HtmlPrinter
import org.odk.collect.qrcode.QRCodeCreator
import org.odk.collect.settings.SettingsProvider
import java.util.function.BiConsumer

class FormEntryViewModelFactory(
    owner: SavedStateRegistryOwner,
    private val mode: String?,
    private val sessionId: String,
    private val scheduler: Scheduler,
    private val formSessionRepository: FormSessionRepository,
    private val mediaUtils: MediaUtils,
    private val audioRecorder: AudioRecorder,
    private val projectsDataService: ProjectsDataService,
    private val entitiesRepositoryProvider: EntitiesRepositoryProvider,
    private val settingsProvider: SettingsProvider,
    private val permissionsChecker: PermissionsChecker,
    private val fusedLocationClient: LocationClient,
    private val permissionsProvider: PermissionsProvider,
    private val autoSendSettingsProvider: AutoSendSettingsProvider,
    private val formsRepositoryProvider: FormsRepositoryProvider,
    private val instancesRepositoryProvider: InstancesRepositoryProvider,
    private val savepointsRepositoryProvider: SavepointsRepositoryProvider,
    private val qrCodeCreator: QRCodeCreator,
    private val htmlPrinter: HtmlPrinter,
    private val instancesDataService: InstancesDataService,
    private val changeLockProvider: ChangeLockProvider
) : AbstractSavedStateViewModelFactory(owner, null) {

    override fun <T : ViewModel> create(
        key: String,
        modelClass: Class<T>,
        handle: SavedStateHandle
    ): T {
        val projectId = projectsDataService.requireCurrentProject().uuid

        return when (modelClass) {
            FormEntryViewModel::class.java -> FormEntryViewModel(
                System::currentTimeMillis,
                scheduler,
                formSessionRepository,
                sessionId,
                formsRepositoryProvider.create(projectId),
                changeLockProvider.create(projectId)
            )

            FormSaveViewModel::class.java -> {
                FormSaveViewModel(
                    handle,
                    System::currentTimeMillis,
                    DiskFormSaver(),
                    mediaUtils,
                    scheduler,
                    audioRecorder,
                    projectsDataService,
                    formSessionRepository.get(sessionId),
                    entitiesRepositoryProvider.create(projectId),
                    instancesRepositoryProvider.create(projectId),
                    savepointsRepositoryProvider.create(projectId),
                    instancesDataService
                )
            }

            BackgroundAudioViewModel::class.java -> {
                val recordAudioActionRegistry =
                    if (mode == ApplicationConstants.FormModes.VIEW_SENT) {
                        object : RecordAudioActionRegistry {
                            override fun register(listener: BiConsumer<TreeReference, String?>) {}
                            override fun unregister() {}
                        }
                    } else {
                        object : RecordAudioActionRegistry {
                            override fun register(listener: BiConsumer<TreeReference, String?>) {
                                RecordAudioActions.setRecordAudioListener { absoluteTargetRef: TreeReference, quality: String? ->
                                    listener.accept(absoluteTargetRef, quality)
                                }
                            }

                            override fun unregister() {
                                RecordAudioActions.setRecordAudioListener(null)
                            }
                        }
                    }

                BackgroundAudioViewModel(
                    audioRecorder,
                    settingsProvider.getUnprotectedSettings(),
                    recordAudioActionRegistry,
                    permissionsChecker,
                    System::currentTimeMillis,
                    formSessionRepository.get(sessionId)
                )
            }

            BackgroundLocationViewModel::class.java -> {
                val locationManager = BackgroundLocationManager(
                    fusedLocationClient,
                    BackgroundLocationHelper(
                        permissionsProvider,
                        settingsProvider.getUnprotectedSettings(),
                        formSessionRepository,
                        sessionId
                    )
                )

                BackgroundLocationViewModel(locationManager)
            }

            IdentityPromptViewModel::class.java -> IdentityPromptViewModel()

            FormEndViewModel::class.java -> FormEndViewModel(
                formSessionRepository,
                sessionId,
                settingsProvider,
                autoSendSettingsProvider
            )

            PrinterWidgetViewModel::class.java -> PrinterWidgetViewModel(scheduler, qrCodeCreator, htmlPrinter)

            else -> throw IllegalArgumentException()
        } as T
    }
}
