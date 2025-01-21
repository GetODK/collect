package org.odk.collect.android.mainmenu

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.projects.Project

class CurrentProjectViewModel(
    private val projectsDataService: ProjectsDataService
) : ViewModel() {

    init {
        projectsDataService.update()
    }

    val currentProject = projectsDataService.getCurrentProject().asLiveData()

    fun setCurrentProject(project: Project.Saved) {
        Analytics.log(AnalyticsEvents.SWITCH_PROJECT)
        projectsDataService.setCurrentProject(project.uuid)
    }

    fun hasCurrentProject(): Boolean {
        return projectsDataService.getCurrentProject().value != null
    }
}
