package org.odk.collect.android.external

import android.content.ContentResolver
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.R
import org.odk.collect.android.activities.FormFillingActivity
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.instancemanagement.InstanceDeleter
import org.odk.collect.android.instancemanagement.canBeEdited
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.android.savepoints.SavepointUseCases
import org.odk.collect.android.utilities.ApplicationConstants
import org.odk.collect.android.utilities.ChangeLockProvider
import org.odk.collect.android.utilities.ContentUriHelper
import org.odk.collect.android.utilities.FormsRepositoryProvider
import org.odk.collect.android.utilities.InstancesRepositoryProvider
import org.odk.collect.android.utilities.SavepointsRepositoryProvider
import org.odk.collect.async.Scheduler
import org.odk.collect.forms.savepoints.Savepoint
import org.odk.collect.projects.ProjectsRepository
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.strings.R.string
import org.odk.collect.strings.localization.LocalizedActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

/**
 * This class serves as a firewall for starting form filling. It should be used to do that
 * rather than [FormFillingActivity] directly as it ensures that the required data is valid.
 */
class FormUriActivity : LocalizedActivity() {

    @Inject
    lateinit var projectsDataService: ProjectsDataService

    @Inject
    lateinit var projectsRepository: ProjectsRepository

    @Inject
    lateinit var formsRepositoryProvider: FormsRepositoryProvider

    @Inject
    lateinit var instanceRepositoryProvider: InstancesRepositoryProvider

    @Inject
    lateinit var savepointsRepositoryProvider: SavepointsRepositoryProvider

    @Inject
    lateinit var settingsProvider: SettingsProvider

    @Inject
    lateinit var scheduler: Scheduler

    @Inject
    lateinit var changeLockProvider: ChangeLockProvider

    private var formFillingAlreadyStarted = false

    private val openForm =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            setResult(it.resultCode, it.data)
            finish()
        }

    private val formUriViewModel by viewModels<FormUriViewModel> {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                return FormUriViewModel(
                    intent,
                    scheduler,
                    projectsRepository,
                    projectsDataService,
                    contentResolver,
                    formsRepositoryProvider,
                    instanceRepositoryProvider,
                    settingsProvider,
                    savepointsRepositoryProvider,
                    changeLockProvider,
                    resources
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DaggerUtils.getComponent(this).inject(this)
        setContentView(R.layout.circular_progress_indicator)

        if (savedInstanceState?.getBoolean(FORM_FILLING_ALREADY_STARTED) == true) {
            return
        }

        formUriViewModel.formInspectionResult.observe(this) {
            when (it) {
                is FormInspectionResult.Error -> displayErrorDialog(it.error)
                is FormInspectionResult.Savepoint -> displaySavePointRecoveryDialog(it.savepoint)
                is FormInspectionResult.Valid -> startForm(intent.data!!)
            }
        }
    }

    private fun displaySavePointRecoveryDialog(savepoint: Savepoint) {
        MaterialAlertDialogBuilder(this)
            .setTitle(string.savepoint_recovery_dialog_title)
            .setMessage(SimpleDateFormat(getString(string.savepoint_recovery_dialog_message), Locale.getDefault()).format(File(savepoint.savepointFilePath).lastModified()))
            .setPositiveButton(string.recover) { _, _ ->
                val uri = intent.data!!
                val uriMimeType = contentResolver.getType(uri)!!
                if (uriMimeType == FormsContract.CONTENT_ITEM_TYPE) {
                    startForm(FormsContract.getUri(projectsDataService.requireCurrentProject().uuid, savepoint.formDbId))
                } else {
                    startForm(intent.data!!)
                }
            }
            .setNegativeButton(string.do_not_recover) { _, _ ->
                formUriViewModel.deleteSavepoint(savepoint)
            }
            .setOnCancelListener { finish() }
            .create()
            .show()
    }

    private fun startForm(uri: Uri) {
        formFillingAlreadyStarted = true
        openForm.launch(
            Intent(this, FormFillingActivity::class.java).apply {
                action = intent.action
                data = uri
                intent.extras?.let { sourceExtras -> putExtras(sourceExtras) }
            }
        )
    }

    private fun displayErrorDialog(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(string.form_cannot_be_used)
            .setMessage(message)
            .setPositiveButton(string.ok) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .create()
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(FORM_FILLING_ALREADY_STARTED, formFillingAlreadyStarted)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val FORM_FILLING_ALREADY_STARTED = "FORM_FILLING_ALREADY_STARTED"
    }
}

private class FormUriViewModel(
    private val intent: Intent,
    private val scheduler: Scheduler,
    private val projectsRepository: ProjectsRepository,
    private val projectsDataService: ProjectsDataService,
    private val contentResolver: ContentResolver,
    private val formsRepositoryProvider: FormsRepositoryProvider,
    private val instancesRepositoryProvider: InstancesRepositoryProvider,
    private val settingsProvider: SettingsProvider,
    private val savepointsRepositoryProvider: SavepointsRepositoryProvider,
    private val changeLockProvider: ChangeLockProvider,
    private val resources: Resources
) : ViewModel() {
    private val uri: Uri? = intent.data

    private val _formInspectionResult = MutableLiveData<FormInspectionResult>()
    val formInspectionResult: LiveData<FormInspectionResult> = _formInspectionResult

    init {
        scheduler.immediate(
            background = {
                val error = assertProjectListNotEmpty()
                    ?: assertCurrentProjectUsed()
                    ?: assertValidUri()
                    ?: assertFormExists()
                    ?: assertFormNotEncrypted()
                    ?: assertNonEditableFormsAreStartedWithCorrectMode()
                    ?: assertDoesNotUseEntitiesOrFormsUpdateNotInProgress()
                if (error != null) {
                    FormInspectionResult.Error(error)
                } else {
                    getSavePoint()?.let {
                        FormInspectionResult.Savepoint(it)
                    } ?: FormInspectionResult.Valid
                }
            },
            foreground = {
                _formInspectionResult.value = it
            }
        )
    }

    private fun assertProjectListNotEmpty(): String? {
        val projects = projectsRepository.getAll()
        return if (projects.isEmpty()) {
            resources.getString(string.app_not_configured)
        } else {
            null
        }
    }

    private fun assertCurrentProjectUsed(): String? {
        val projects = projectsRepository.getAll()
        val firstProject = projects.first()
        val uriProjectId = uri?.getQueryParameter("projectId")
        val projectId = uriProjectId ?: firstProject.uuid

        return if (projectId != projectsDataService.requireCurrentProject().uuid) {
            resources.getString(string.wrong_project_selected_for_form)
        } else {
            null
        }
    }

    private fun assertValidUri(): String? {
        val isUriValid = uri?.let {
            val uriMimeType = contentResolver.getType(it)
            if (uriMimeType == null) {
                false
            } else {
                uriMimeType == FormsContract.CONTENT_ITEM_TYPE || uriMimeType == InstancesContract.CONTENT_ITEM_TYPE
            }
        } ?: false

        return if (!isUriValid) {
            resources.getString(string.unrecognized_uri)
        } else {
            null
        }
    }

    private fun assertFormExists(): String? {
        val uriMimeType = contentResolver.getType(uri!!)

        return if (uriMimeType == FormsContract.CONTENT_ITEM_TYPE) {
            val formExists =
                formsRepositoryProvider.create().get(ContentUriHelper.getIdFromUri(uri))?.let {
                    File(it.formFilePath).exists()
                } ?: false

            if (formExists) {
                null
            } else {
                resources.getString(string.bad_uri)
            }
        } else {
            val instance = instancesRepositoryProvider.create().get(ContentUriHelper.getIdFromUri(uri))
            if (instance == null) {
                resources.getString(string.bad_uri)
            } else if (!File(instance.instanceFilePath).exists()) {
                Analytics.log(AnalyticsEvents.OPEN_DELETED_INSTANCE)
                InstanceDeleter(
                    instancesRepositoryProvider.create(),
                    formsRepositoryProvider.create()
                ).delete(instance.dbId)
                resources.getString(string.instance_deleted_message)
            } else {
                val candidateForms = formsRepositoryProvider.create()
                    .getAllByFormIdAndVersion(instance.formId, instance.formVersion)

                if (candidateForms.isEmpty()) {
                    val version = if (instance.formVersion == null) {
                        ""
                    } else {
                        "\n${resources.getString(string.version)} ${instance.formVersion}"
                    }

                    resources.getString(string.parent_form_not_present, "${instance.formId}$version")
                } else if (candidateForms.filter { !it.isDeleted }.size > 1) {
                    resources.getString(string.survey_multiple_forms_error)
                } else {
                    null
                }
            }
        }
    }

    private fun assertFormNotEncrypted(): String? {
        val uriMimeType = contentResolver.getType(uri!!)

        return if (uriMimeType == InstancesContract.CONTENT_ITEM_TYPE) {
            val instance = instancesRepositoryProvider.create().get(ContentUriHelper.getIdFromUri(uri))
            if (instance!!.canEditWhenComplete()) {
                null
            } else {
                resources.getString(string.encrypted_form)
            }
        } else {
            null
        }
    }

    private fun assertNonEditableFormsAreStartedWithCorrectMode(): String? {
        if (!canFormBeEdited()) {
            intent.putExtra(
                ApplicationConstants.BundleKeys.FORM_MODE,
                ApplicationConstants.FormModes.VIEW_SENT
            )
        }
        return null
    }

    private fun assertDoesNotUseEntitiesOrFormsUpdateNotInProgress(): String? {
        val uriMimeType = contentResolver.getType(uri!!)
        val projectId = projectsDataService.requireCurrentProject().uuid

        if (intent.extras?.getString(ApplicationConstants.BundleKeys.FORM_MODE) == ApplicationConstants.FormModes.VIEW_SENT) {
            return null
        }

        val usesEntities = if (uriMimeType == FormsContract.CONTENT_ITEM_TYPE) {
            val form = formsRepositoryProvider.create().get(ContentUriHelper.getIdFromUri(uri))!!
            form.usesEntities()
        } else {
            val instance = instancesRepositoryProvider.create().get(ContentUriHelper.getIdFromUri(uri))!!
            val form = formsRepositoryProvider.create().getAllByFormIdAndVersion(instance.formId, instance.formVersion).first()
            form.usesEntities()
        }

        if (usesEntities) {
            val formsLock = changeLockProvider.create(projectId).formsLock
            val isLocAcquired = formsLock.tryLock()

            return if (isLocAcquired) {
                null
            } else {
                resources.getString(string.cannot_open_form_because_of_forms_update)
            }
        } else {
            return null
        }
    }

    private fun getSavePoint(): Savepoint? {
        val uriMimeType = contentResolver.getType(uri!!)!!

        return SavepointUseCases.getSavepoint(
            uri,
            uriMimeType,
            formsRepositoryProvider.create(),
            instancesRepositoryProvider.create(),
            savepointsRepositoryProvider.create()
        )
    }

    fun deleteSavepoint(savepoint: Savepoint) {
        scheduler.immediate(
            background = {
                if (savepoint.instanceDbId == null) {
                    File(savepoint.instanceFilePath).parentFile?.deleteRecursively()
                }
                savepointsRepositoryProvider.create().delete(savepoint.formDbId, savepoint.instanceDbId)
            },
            foreground = {
                _formInspectionResult.value = FormInspectionResult.Valid
            }
        )
    }

    private fun canFormBeEdited(): Boolean {
        val uriMimeType = contentResolver.getType(uri!!)

        val formEditingEnabled = if (uriMimeType == InstancesContract.CONTENT_ITEM_TYPE) {
            val instance = instancesRepositoryProvider.create().get(ContentUriHelper.getIdFromUri(uri))
            instance!!.canBeEdited(settingsProvider)
        } else {
            true
        }

        return formEditingEnabled
    }
}

private sealed class FormInspectionResult {
    data class Error(val error: String) : FormInspectionResult()
    data class Savepoint(val savepoint: org.odk.collect.forms.savepoints.Savepoint) : FormInspectionResult()
    data object Valid : FormInspectionResult()
}
