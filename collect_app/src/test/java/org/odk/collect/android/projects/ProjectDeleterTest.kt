package org.odk.collect.android.projects

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.odk.collect.android.backgroundwork.FormUpdateScheduler
import org.odk.collect.android.backgroundwork.InstanceSubmitScheduler
import org.odk.collect.android.preferences.Defaults
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.android.utilities.ChangeLockProvider
import org.odk.collect.android.utilities.InstancesRepositoryProvider
import org.odk.collect.androidshared.data.AppState
import org.odk.collect.forms.instances.Instance
import org.odk.collect.formstest.InMemInstancesRepository
import org.odk.collect.projects.InMemProjectsRepository
import org.odk.collect.projects.Project
import org.odk.collect.settings.InMemSettingsProvider
import org.odk.collect.settings.keys.MetaKeys
import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.settings.keys.ProtectedProjectKeys
import org.odk.collect.shared.TempFiles
import org.odk.collect.shared.locks.BooleanChangeLock
import java.io.File

class ProjectDeleterTest {
    private val project1 = Project.Saved("1", "1", "1", "#ffffff")
    private val projectsRepository = InMemProjectsRepository().apply {
        save(project1)
    }
    private val instancesRepository = InMemInstancesRepository()
    private val instancesRepositoryProvider = mock<InstancesRepositoryProvider>().apply {
        whenever(create(project1.uuid)).thenReturn(instancesRepository)
    }
    private val settingsProvider = InMemSettingsProvider()
    private val projectsDataService = ProjectsDataService(AppState(), settingsProvider, projectsRepository, mock(), mock())
    private val formUpdateScheduler = mock<FormUpdateScheduler>()
    private val instanceSubmitScheduler = mock<InstanceSubmitScheduler>()
    private val storagePathProvider = mock<StoragePathProvider>().apply {
        whenever(getProjectRootDirPath(project1.uuid)).thenReturn("")
    }
    private val changeLockProvider = mock<ChangeLockProvider> {
        on { getFormLock(any()) } doReturn BooleanChangeLock()
        on { getInstanceLock(any()) } doReturn BooleanChangeLock()
    }
    private val deleter = ProjectDeleter(
        projectsRepository,
        projectsDataService,
        formUpdateScheduler,
        instanceSubmitScheduler,
        instancesRepositoryProvider,
        storagePathProvider,
        changeLockProvider,
        settingsProvider
    )

    @Test
    fun `If there are incomplete instances the project should not be deleted`() {
        instancesRepository.save(
            Instance.Builder()
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.UnsentInstances::class.java))
        assertThat(projectsRepository.projects.contains(project1), equalTo(true))
    }

    @Test
    fun `If there are invalid instances the project should not be deleted`() {
        instancesRepository.save(
            Instance.Builder()
                .status(Instance.STATUS_INVALID)
                .build()
        )

        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.UnsentInstances::class.java))
        assertThat(projectsRepository.projects.contains(project1), equalTo(true))
    }

    @Test
    fun `If there are valid instances the project should not be deleted`() {
        instancesRepository.save(
            Instance.Builder()
                .status(Instance.STATUS_VALID)
                .build()
        )

        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.UnsentInstances::class.java))
        assertThat(projectsRepository.projects.contains(project1), equalTo(true))
    }

    @Test
    fun `If there are complete instances the project should not be deleted`() {
        instancesRepository.save(
            Instance.Builder()
                .status(Instance.STATUS_COMPLETE)
                .build()
        )

        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.UnsentInstances::class.java))
        assertThat(projectsRepository.projects.contains(project1), equalTo(true))
    }

    @Test
    fun `If there are submission failed instances the project should not be deleted`() {
        instancesRepository.save(
            Instance.Builder()
                .status(Instance.STATUS_SUBMISSION_FAILED)
                .build()
        )

        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.UnsentInstances::class.java))
        assertThat(projectsRepository.projects.contains(project1), equalTo(true))
    }

    @Test
    fun `If there are saved instances but all sent the project should be deleted`() {
        instancesRepository.save(
            Instance.Builder()
                .status(Instance.STATUS_SUBMITTED)
                .build()
        )

        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.DeletedSuccessfullyLastProject::class.java))
        assertThat(projectsRepository.projects.size, equalTo(0))
    }

    @Test
    fun `If there are no instances the project should be deleted`() {
        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.DeletedSuccessfullyLastProject::class.java))
        assertThat(projectsRepository.projects.size, equalTo(0))
    }

    @Test
    fun `If there are running background jobs that use blank forms the project should not be deleted`() {
        val formChangeLock = BooleanChangeLock().apply {
            lock()
        }
        whenever(changeLockProvider.getFormLock(any())).thenReturn(formChangeLock)

        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.RunningBackgroundJobs::class.java))
        assertThat(projectsRepository.projects.contains(project1), equalTo(true))
    }

    @Test
    fun `If there are running background jobs that use saved forms the project should not be deleted`() {
        val changeLock = BooleanChangeLock().apply {
            lock()
        }
        whenever(changeLockProvider.getInstanceLock(any())).thenReturn(changeLock)

        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.RunningBackgroundJobs::class.java))
        assertThat(projectsRepository.projects.contains(project1), equalTo(true))
    }

    @Test
    fun `Deleting project cancels scheduled form updates and instance submits`() {
        deleter.deleteProject(project1.uuid)

        verify(formUpdateScheduler).cancelUpdates(project1.uuid)
        verify(instanceSubmitScheduler).cancelSubmit(project1.uuid)
    }

    @Test
    fun `Deleting project clears its settings`() {
        settingsProvider.getMetaSettings().save(MetaKeys.KEY_INSTALL_ID, "1234")
        settingsProvider.getUnprotectedSettings(project1.uuid).save(ProjectKeys.KEY_SERVER_URL, "https://my-server.com")
        settingsProvider.getProtectedSettings(project1.uuid).save(ProtectedProjectKeys.KEY_AUTOSEND, false)
        settingsProvider.getUnprotectedSettings("2").save(ProjectKeys.KEY_SERVER_URL, "https://my-server.com")
        settingsProvider.getProtectedSettings("2").save(ProtectedProjectKeys.KEY_AUTOSEND, false)

        deleter.deleteProject(project1.uuid)

        assertThat(settingsProvider.getMetaSettings().getString(MetaKeys.KEY_INSTALL_ID), equalTo("1234"))
        settingsProvider.getUnprotectedSettings(project1.uuid).getAll().forEach { (key, value) ->
            assertThat(value, equalTo(Defaults.protected[key]))
        }
        settingsProvider.getProtectedSettings(project1.uuid).getAll().forEach { (key, value) ->
            assertThat(value, equalTo(Defaults.protected[key]))
        }

        assertThat(settingsProvider.getUnprotectedSettings("2").getString(ProjectKeys.KEY_SERVER_URL), equalTo("https://my-server.com"))
        assertThat(settingsProvider.getProtectedSettings("2").getBoolean(ProtectedProjectKeys.KEY_AUTOSEND), equalTo(false))
    }

    @Test
    fun `If the deleted project was the last one return DeletedSuccessfully with null parameter`() {
        val result = deleter.deleteProject(project1.uuid)

        assertThat(result, instanceOf(DeleteProjectResult.DeletedSuccessfullyLastProject::class.java))
    }

    @Test
    fun `If the deleted project was the current one and not the last one set the current project and return the new current one`() {
        val project2 = Project.Saved("2", "2", "2", "#cccccc")
        projectsRepository.save(project2)
        projectsDataService.setCurrentProject(project1.uuid)

        val result = deleter.deleteProject(project1.uuid)

        assertThat(projectsDataService.getCurrentProject().uuid, equalTo(project2.uuid))
        assertThat((result as DeleteProjectResult.DeletedSuccessfullyCurrentProject).newCurrentProject, equalTo(project2))
    }

    @Test
    fun `If the deleted project was not the current one and not the last one do not set the current project and return DeletedSuccessfully with null parameter`() {
        val project2 = Project.Saved("2", "2", "2", "#cccccc")
        projectsRepository.save(project2)
        projectsDataService.setCurrentProject(project2.uuid)

        val result = deleter.deleteProject(project1.uuid)

        assertThat(projectsDataService.getCurrentProject().uuid, equalTo(project2.uuid))
        assertThat(result, instanceOf(DeleteProjectResult.DeletedSuccessfullyInactiveProject::class.java))
    }

    @Test
    fun `Project directory should be removed`() {
        val projectDir = TempFiles.createTempDir()
        File(projectDir, "dir").mkdir()

        assertThat(projectDir.exists(), equalTo(true))
        assertThat(projectDir.listFiles().size, equalTo(1))

        whenever(storagePathProvider.getProjectRootDirPath(project1.uuid)).thenReturn(projectDir.absolutePath)

        deleter.deleteProject(project1.uuid)

        assertThat(projectDir.exists(), equalTo(false))
    }

    @Test
    fun `If there is no project id passed to ProjectDeleter#deleteProject() then delete the current project`() {
        val project2 = Project.Saved("2", "2", "2", "#cccccc")
        projectsRepository.save(project2)
        projectsDataService.setCurrentProject(project2.uuid)
        whenever(instancesRepositoryProvider.create(project2.uuid)).thenReturn(instancesRepository)
        whenever(storagePathProvider.getProjectRootDirPath(project2.uuid)).thenReturn("")

        val result = deleter.deleteProject()

        assertThat(result, instanceOf(DeleteProjectResult.DeletedSuccessfullyCurrentProject::class.java))
    }
}
