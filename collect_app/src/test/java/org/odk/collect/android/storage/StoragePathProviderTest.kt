package org.odk.collect.android.storage

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import org.hamcrest.CoreMatchers.`is`
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.projects.Project
import org.odk.collect.projects.ProjectsRepository
import java.io.File

class StoragePathProviderTest {
    private val root = createTempDir()
    private var projectsRepository = mock(ProjectsRepository::class.java)
    private lateinit var storagePathProvider: StoragePathProvider

    @Before
    fun setup() {
        val projectsDataService = mock(ProjectsDataService::class.java)
        `when`(projectsDataService.requireCurrentProject()).thenReturn(
            Project.Saved(
                "123",
                "Project",
                "D",
                "#ffffff"
            )
        )
        `when`(projectsRepository.get("123")).thenReturn(
            Project.Saved(
                "123",
                "Project",
                "D",
                "#ffffff"
            )
        )
        storagePathProvider = StoragePathProvider(projectsDataService, projectsRepository, root.absolutePath)
    }

    @After
    fun teardown() {
        root.delete()
    }

    @Test
    fun storageRootDirPath_returnsRoot() {
        assertThat(storagePathProvider.odkRootDirPath, `is`(root.absolutePath))
    }

    @Test
    fun projectRootDirPath_returnsAndCreatesDirForProject() {
        val path = storagePathProvider.getProjectRootDirPath("123")
        assertThat(path, `is`(root.absolutePath + "/projects/123"))
        assertThat(File(path).exists(), `is`(true))
    }

    @Test
    fun projectRootDirPath_createsFileWithSanitizedProjectName() {
        `when`(projectsRepository.get("123")).thenReturn(
            Project.Saved(
                "123",
                "Project<>",
                "D",
                "#ffffff"
            )
        )

        val path = storagePathProvider.getProjectRootDirPath("123")
        assertThat(File(path + File.separator + "Project__").exists(), `is`(true))
    }

    @Test
    fun odkDirPath_withForms_returnsAndCreatesFormsDirForCurrentProject() {
        val path = storagePathProvider.getOdkDirPath(StorageSubdirectory.FORMS)
        assertThat(path, `is`(root.absolutePath + "/projects/123/forms"))
        assertThat(File(path).exists(), `is`(true))
    }

    @Test
    fun odkDirPath_withInstances_returnsAndCreatesInstancesDirForCurrentProject() {
        val path = storagePathProvider.getOdkDirPath(StorageSubdirectory.INSTANCES)
        assertThat(path, `is`(root.absolutePath + "/projects/123/instances"))
        assertThat(File(path).exists(), `is`(true))
    }

    @Test
    fun odkDirPath_withMetadata_returnsAndCreatesMetadataDirForCurrentProject() {
        val path = storagePathProvider.getOdkDirPath(StorageSubdirectory.METADATA)
        assertThat(path, `is`(root.absolutePath + "/projects/123/metadata"))
        assertThat(File(path).exists(), `is`(true))
    }

    @Test
    fun odkDirPath_withCache_returnsAndCreatesCacheDirForCurrentProject() {
        val path = storagePathProvider.getOdkDirPath(StorageSubdirectory.CACHE)
        assertThat(path, `is`(root.absolutePath + "/projects/123/.cache"))
        assertThat(File(path).exists(), `is`(true))
    }

    @Test
    fun odkDirPath_withLayers_returnsAndCreatesLayersDirForCurrentProject() {
        val path = storagePathProvider.getOdkDirPath(StorageSubdirectory.LAYERS)
        assertThat(path, `is`(root.absolutePath + "/projects/123/layers"))
        assertThat(File(path).exists(), `is`(true))
    }

    @Test
    fun odkDirPath_withSettings_returnsAndCreatesSettingsDirForCurrentProject() {
        val path = storagePathProvider.getOdkDirPath(StorageSubdirectory.SETTINGS)
        assertThat(path, `is`(root.absolutePath + "/projects/123/settings"))
        assertThat(File(path).exists(), `is`(true))
    }

    @Test
    fun getOdkDirPath_withSharedLayers_returnsAndCreatesSharedLayersDir() {
        val path = storagePathProvider.getOdkDirPath(StorageSubdirectory.SHARED_LAYERS)
        assertThat(path, `is`(root.absolutePath + "/layers"))
        assertThat(File(path).exists(), `is`(true))
    }
}
