package org.odk.collect.android.projects

import kotlinx.coroutines.flow.StateFlow
import org.odk.collect.android.application.initialization.AnalyticsInitializer
import org.odk.collect.android.application.initialization.MapsInitializer
import org.odk.collect.android.state.DataKeys
import org.odk.collect.androidshared.data.AppState
import org.odk.collect.androidshared.data.DataService
import org.odk.collect.projects.Project
import org.odk.collect.projects.ProjectsRepository
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.settings.keys.MetaKeys

class ProjectsDataService(
    appState: AppState,
    private val settingsProvider: SettingsProvider,
    private val projectsRepository: ProjectsRepository,
    private val analyticsInitializer: AnalyticsInitializer,
    private val mapsInitializer: MapsInitializer
) : DataService(appState) {

    private val currentProject by data(DataKeys.PROJECT, null) {
        val currentProjectId = getCurrentProjectId()
        if (currentProjectId != null) {
            projectsRepository.get(currentProjectId)
        } else {
            null
        }
    }

    fun getCurrentProjectFlow(): StateFlow<Project.Saved?> {
        return currentProject.flow()
    }

    fun getCurrentProject(): Project.Saved {
        val currentProjectId = getCurrentProjectId()

        if (currentProjectId != null) {
            val currentProject = projectsRepository.get(currentProjectId)

            if (currentProject != null) {
                return currentProject
            } else {
                throw IllegalStateException("Current project does not exist!")
            }
        } else {
            val projects = projectsRepository.getAll()

            if (projects.isNotEmpty()) {
                return projects[0]
            } else {
                throw IllegalStateException("No current project!")
            }
        }
    }

    fun setCurrentProject(projectId: String) {
        settingsProvider.getMetaSettings().save(MetaKeys.CURRENT_PROJECT_ID, projectId)

        analyticsInitializer.initialize()
        mapsInitializer.initialize()

        update()
    }

    private fun getCurrentProjectId(): String? {
        return settingsProvider.getMetaSettings().getString(MetaKeys.CURRENT_PROJECT_ID)
    }
}
