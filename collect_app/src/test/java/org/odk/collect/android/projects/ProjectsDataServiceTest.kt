package org.odk.collect.android.projects

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`is`
import org.junit.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.odk.collect.android.application.initialization.AnalyticsInitializer
import org.odk.collect.androidshared.data.AppState
import org.odk.collect.projects.InMemProjectsRepository
import org.odk.collect.projects.Project
import org.odk.collect.settings.InMemSettingsProvider
import org.odk.collect.settings.keys.MetaKeys

class ProjectsDataServiceTest {

    private val projectsRepository = InMemProjectsRepository()
    private val settingsProvider = InMemSettingsProvider()
    private val metaSettings = settingsProvider.getMetaSettings()
    private val analyticsInitializer = mock<AnalyticsInitializer>()
    private val projectsDataService = ProjectsDataService(
        AppState(),
        settingsProvider,
        projectsRepository,
        analyticsInitializer,
        mock()
    )

    @Test
    fun `A project should be returned after calling requireCurrentProject if there is a project for given id`() {
        val project = projectsRepository.save(Project.New("ProjectX", "X", "#00FF00"))
        metaSettings.save(MetaKeys.CURRENT_PROJECT_ID, project.uuid)

        assertThat(projectsDataService.requireCurrentProject(), `is`(project))
    }

    @Test
    fun `save() on meta settings should be called after current project is set`() {
        projectsDataService.setCurrentProject("123e4567")
        assertThat(metaSettings.getString(MetaKeys.CURRENT_PROJECT_ID), equalTo("123e4567"))
    }

    @Test(expected = IllegalStateException::class)
    fun `requireCurrentProject throws IllegalStateException when there is no current project`() {
        projectsDataService.requireCurrentProject()
    }

    @Test
    fun `requireCurrentProject returns first project when there is no current project but there are projects`() {
        val firstProject = projectsRepository.save(Project.New("ProjectX", "X", "#00FF00"))
        projectsRepository.save(Project.New("ProjectY", "Y", "#00FF00"))
        assertThat(projectsDataService.requireCurrentProject(), `is`(firstProject))
    }

    @Test(expected = IllegalStateException::class)
    fun `requireCurrentProject throws IllegalStateException when current project does not exist`() {
        projectsDataService.setCurrentProject("123e4567")
        projectsDataService.requireCurrentProject()
    }

    @Test
    fun `setCurrentProject() re-initializes analytics`() {
        projectsRepository.save(Project.Saved("456", "Project Y", "Y", "#ffffff"))
        projectsDataService.setCurrentProject("456")
        verify(analyticsInitializer).initialize()
    }
}
