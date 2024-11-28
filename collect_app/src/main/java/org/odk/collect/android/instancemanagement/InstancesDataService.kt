package org.odk.collect.android.instancemanagement

import kotlinx.coroutines.flow.Flow
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.application.Collect
import org.odk.collect.android.backgroundwork.InstanceSubmitScheduler
import org.odk.collect.android.formentry.FormEntryUseCases
import org.odk.collect.android.formmanagement.CollectFormEntryControllerFactory
import org.odk.collect.android.instancemanagement.autosend.FormAutoSendMode
import org.odk.collect.android.instancemanagement.autosend.InstanceAutoSendFetcher
import org.odk.collect.android.instancemanagement.autosend.getAutoSendMode
import org.odk.collect.android.notifications.Notifier
import org.odk.collect.android.openrosa.OpenRosaHttpInterface
import org.odk.collect.android.projects.ProjectDependencyModule
import org.odk.collect.android.state.DataKeys
import org.odk.collect.android.utilities.ExternalizableFormDefCache
import org.odk.collect.android.utilities.FormsUploadResultInterpreter
import org.odk.collect.androidshared.data.AppState
import org.odk.collect.androidshared.data.getData
import org.odk.collect.forms.Form
import org.odk.collect.forms.instances.Instance
import org.odk.collect.metadata.PropertyManager
import org.odk.collect.projects.ProjectDependencyFactory
import java.io.File

class InstancesDataService(
    appState: AppState,
    private val instanceSubmitScheduler: InstanceSubmitScheduler,
    private val projectDependencyModuleFactory: ProjectDependencyFactory<ProjectDependencyModule>,
    private val notifier: Notifier,
    private val propertyManager: PropertyManager,
    private val httpInterface: OpenRosaHttpInterface,
    private val onUpdate: () -> Unit
) {

    private val editableCount = appState.getData(DataKeys.INSTANCES_EDITABLE_COUNT, 0)
    private val sendableCount = appState.getData(DataKeys.INSTANCES_SENDABLE_COUNT, 0)
    private val sentCount = appState.getData(DataKeys.INSTANCES_SENT_COUNT, 0)
    private val instances = appState.getData<List<Instance>>(DataKeys.INSTANCES, emptyList())

    fun getEditableCount(projectId: String): Flow<Int> = editableCount.get()
    fun getSendableCount(projectId: String): Flow<Int> = sendableCount.get()
    fun getSentCount(projectId: String): Flow<Int> = sentCount.get()

    fun getInstances(projectId: String): Flow<List<Instance>> {
        return instances.get(projectId)
    }

    fun update(projectId: String) {
        val projectDependencyModule = projectDependencyModuleFactory.create(projectId)
        val instancesRepository = projectDependencyModule.instancesRepository

        val sendableInstances = instancesRepository.getCountByStatus(
            Instance.STATUS_COMPLETE,
            Instance.STATUS_SUBMISSION_FAILED
        )
        val sentInstances = instancesRepository.getCountByStatus(
            Instance.STATUS_SUBMITTED,
            Instance.STATUS_SUBMISSION_FAILED
        )
        val editableInstances = instancesRepository.getCountByStatus(
            Instance.STATUS_INCOMPLETE,
            Instance.STATUS_INVALID,
            Instance.STATUS_VALID
        )

        editableCount.set(editableInstances)
        sendableCount.set(sendableInstances)
        sentCount.set(sentInstances)
        instances.set(projectId, instancesRepository.all)

        onUpdate()
    }

    fun finalizeAllDrafts(projectId: String): FinalizeAllResult {
        val projectDependencyModule = projectDependencyModuleFactory.create(projectId)
        val instancesRepository = projectDependencyModule.instancesRepository
        val formsRepository = projectDependencyModule.formsRepository
        val savepointsRepository = projectDependencyModule.savepointsRepository
        val entitiesRepository = projectDependencyModule.entitiesRepository

        val projectRootDir = File(projectDependencyModule.rootDir)

        val instances = instancesRepository.getAllByStatus(
            Instance.STATUS_INCOMPLETE,
            Instance.STATUS_INVALID,
            Instance.STATUS_VALID
        )

        val result = instances.fold(FinalizeAllResult(0, 0, false)) { result, instance ->
            val formDefAndForm = FormEntryUseCases.loadFormDef(
                instance,
                formsRepository,
                projectRootDir,
                ExternalizableFormDefCache()
            )

            if (formDefAndForm == null) {
                result.copy(failureCount = result.failureCount + 1)
            } else {
                val (formDef, form) = formDefAndForm

                val formMediaDir = File(form.formMediaPath)
                val formEntryController =
                    CollectFormEntryControllerFactory(
                        entitiesRepository,
                        projectDependencyModule.generalSettings
                    ).create(formDef, formMediaDir)
                val formController =
                    FormEntryUseCases.loadDraft(form, instance, formEntryController)
                if (formController == null) {
                    result.copy(failureCount = result.failureCount + 1)
                } else {
                    val savePoint = savepointsRepository.get(form.dbId, instance.dbId)
                    val needsEncrypted = form.basE64RSAPublicKey != null
                    val newResult = if (savePoint != null) {
                        Analytics.log(AnalyticsEvents.BULK_FINALIZE_SAVE_POINT)
                        result.copy(
                            failureCount = result.failureCount + 1,
                            unsupportedInstances = true
                        )
                    } else if (needsEncrypted) {
                        Analytics.log(AnalyticsEvents.BULK_FINALIZE_ENCRYPTED_FORM)
                        result.copy(
                            failureCount = result.failureCount + 1,
                            unsupportedInstances = true
                        )
                    } else {
                        val finalizedInstance = FormEntryUseCases.finalizeDraft(
                            formController,
                            instancesRepository,
                            entitiesRepository
                        )

                        if (finalizedInstance == null) {
                            result.copy(failureCount = result.failureCount + 1)
                        } else {
                            instanceFinalized(projectId, form)
                            result
                        }
                    }

                    Collect.getInstance().externalDataManager?.close()
                    newResult
                }
            }
        }

        update(projectId)

        return result.copy(successCount = instances.size - result.failureCount)
    }

    fun deleteInstances(projectId: String, instanceIds: LongArray): Boolean {
        val projectDependencyModule = projectDependencyModuleFactory.create(projectId)
        val instancesRepository = projectDependencyModule.instancesRepository
        val formsRepository = projectDependencyModule.formsRepository

        return projectDependencyModule.instancesLock.withLock { acquiredLock: Boolean ->
            if (acquiredLock) {
                instanceIds.forEach { instanceId ->
                    InstanceDeleter(
                        instancesRepository,
                        formsRepository
                    ).delete(
                        instanceId
                    )
                }

                update(projectId)
                true
            } else {
                false
            }
        }
    }

    fun reset(projectId: String): Boolean {
        val projectDependencyModule =
            projectDependencyModuleFactory.create(projectId)
        val instancesRepository = projectDependencyModule.instancesRepository

        return projectDependencyModule.instancesLock.withLock { acquiredLock: Boolean ->
            if (acquiredLock) {
                instancesRepository.all.forEach {
                    if (it.canDelete()) {
                        instancesRepository.delete(it.dbId)
                    }
                }
                update(projectId)
                true
            } else {
                false
            }
        }
    }

    fun sendInstances(projectId: String, formAutoSend: Boolean = false): Boolean {
        val projectDependencyModule =
            projectDependencyModuleFactory.create(projectId)

        val instanceSubmitter = InstanceSubmitter(
            projectDependencyModule.formsRepository,
            projectDependencyModule.generalSettings,
            propertyManager,
            httpInterface,
            projectDependencyModule.instancesRepository
        )

        return projectDependencyModule.instancesLock.withLock { acquiredLock: Boolean ->
            if (acquiredLock) {
                val toUpload = InstanceAutoSendFetcher.getInstancesToAutoSend(
                    projectDependencyModule.instancesRepository,
                    projectDependencyModule.formsRepository,
                    formAutoSend
                )

                if (toUpload.isNotEmpty()) {
                    val results = instanceSubmitter.submitInstances(toUpload)
                    notifier.onSubmission(results, projectDependencyModule.projectId)
                    update(projectId)

                    FormsUploadResultInterpreter.allFormsUploadedSuccessfully(results)
                } else {
                    true
                }
            } else {
                false
            }
        }
    }

    fun instanceFinalized(projectId: String, form: Form) {
        if (form.getAutoSendMode() == FormAutoSendMode.FORCED) {
            instanceSubmitScheduler.scheduleFormAutoSend(projectId)
        } else {
            instanceSubmitScheduler.scheduleAutoSend(projectId)
        }
    }
}

data class FinalizeAllResult(
    val successCount: Int,
    val failureCount: Int,
    val unsupportedInstances: Boolean
)
