package org.odk.collect.android.projects

import org.odk.collect.android.backgroundwork.FormUpdateScheduler
import org.odk.collect.android.backgroundwork.InstanceSubmitScheduler
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.android.utilities.ChangeLockProvider
import org.odk.collect.android.utilities.InstancesRepositoryProvider
import org.odk.collect.db.sqlite.DatabaseConnection
import org.odk.collect.forms.instances.Instance
import org.odk.collect.projects.Project
import org.odk.collect.projects.ProjectsRepository
import org.odk.collect.settings.SettingsProvider
import java.io.File

class ProjectDeleter(
    private val projectsRepository: ProjectsRepository,
    private val projectsDataService: ProjectsDataService,
    private val formUpdateScheduler: FormUpdateScheduler,
    private val instanceSubmitScheduler: InstanceSubmitScheduler,
    private val instancesRepositoryProvider: InstancesRepositoryProvider,
    private val storagePathProvider: StoragePathProvider,
    private val changeLockProvider: ChangeLockProvider,
    private val settingsProvider: SettingsProvider
) {
    fun deleteProject(projectId: String = projectsDataService.requireCurrentProject().uuid): DeleteProjectResult {
        return when {
            unsentInstancesDetected(projectId) -> DeleteProjectResult.UnsentInstances
            runningBackgroundJobsDetected(projectId) -> DeleteProjectResult.RunningBackgroundJobs
            else -> performProjectDeletion(projectId)
        }
    }

    private fun unsentInstancesDetected(projectId: String): Boolean {
        return instancesRepositoryProvider.create(projectId).getAllByStatus(
            Instance.STATUS_INCOMPLETE,
            Instance.STATUS_INVALID,
            Instance.STATUS_VALID,
            Instance.STATUS_COMPLETE,
            Instance.STATUS_SUBMISSION_FAILED
        ).isNotEmpty()
    }

    private fun runningBackgroundJobsDetected(projectId: String): Boolean {
        val acquiredFormLock = changeLockProvider.getFormLock(projectId).withLock { acquiredLock ->
            acquiredLock
        }
        val acquiredInstanceLock = changeLockProvider.getInstanceLock(projectId).withLock { acquiredLock ->
            acquiredLock
        }

        return !acquiredFormLock || !acquiredInstanceLock
    }

    private fun performProjectDeletion(projectId: String): DeleteProjectResult {
        formUpdateScheduler.cancelUpdates(projectId)
        instanceSubmitScheduler.cancelSubmit(projectId)

        settingsProvider.getUnprotectedSettings(projectId).clear()
        settingsProvider.getProtectedSettings(projectId).clear()

        projectsRepository.delete(projectId)

        File(storagePathProvider.getProjectRootDirPath(projectId)).deleteRecursively()

        DatabaseConnection.cleanUp()

        return try {
            projectsDataService.requireCurrentProject()
            DeleteProjectResult.DeletedSuccessfullyInactiveProject
        } catch (e: IllegalStateException) {
            if (projectsRepository.getAll().isEmpty()) {
                DeleteProjectResult.DeletedSuccessfullyLastProject
            } else {
                val newProject = projectsRepository.getAll()[0]
                projectsDataService.setCurrentProject(newProject.uuid)
                DeleteProjectResult.DeletedSuccessfullyCurrentProject(newProject)
            }
        }
    }
}

sealed class DeleteProjectResult {
    object UnsentInstances : DeleteProjectResult()

    object RunningBackgroundJobs : DeleteProjectResult()

    object DeletedSuccessfullyLastProject : DeleteProjectResult()

    object DeletedSuccessfullyInactiveProject : DeleteProjectResult()

    data class DeletedSuccessfullyCurrentProject(val newCurrentProject: Project.Saved) : DeleteProjectResult()
}
