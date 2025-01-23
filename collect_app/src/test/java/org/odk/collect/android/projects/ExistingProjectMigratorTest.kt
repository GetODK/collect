package org.odk.collect.android.projects

import android.app.Application
import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.storage.StorageSubdirectory
import org.odk.collect.settings.keys.MetaKeys
import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.shared.TempFiles
import java.io.File

@RunWith(AndroidJUnit4::class)
class ExistingProjectMigratorTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val component = DaggerUtils.getComponent(context)
    private val existingProjectMigrator = component.existingProjectMigrator()
    private val storagePathProvider = component.storagePathProvider()
    private val projectsRepository = component.projectsRepository()
    private val settingsProvider = component.settingsProvider()
    private val currentProjectProvider = component.currentProjectProvider()
    private val rootDir = storagePathProvider.odkRootDirPath

    @Before
    fun setup() {
        // Should not run when upgrading from version with LAST_LAUNCHED
        settingsProvider.getMetaSettings().remove(MetaKeys.LAST_LAUNCHED)
    }

    @Test
    fun `creates existing project with details based on its url`() {
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .putString(ProjectKeys.KEY_SERVER_URL, "https://my-server.com")
            .apply()

        existingProjectMigrator.run()

        assertThat(projectsRepository.getAll().size, `is`(1))

        val project = projectsRepository.getAll()[0]
        assertThat(project.name, `is`("my-server.com"))
        assertThat(project.icon, `is`("M"))
        assertThat(project.color, `is`("#53BDD4"))
    }

    @Test
    fun `moves files from root`() {
        val legacyRootDirs = listOf(
            File(rootDir, "forms"),
            File(rootDir, "instances"),
            File(rootDir, "metadata"),
            File(rootDir, "layers"),
            File(rootDir, "settings")
        )

        legacyRootDirs.forEach {
            it.mkdir()
            TempFiles.createTempFile(it, "file", ".temp")
        }

        existingProjectMigrator.run()
        val existingProject = currentProjectProvider.requireCurrentProject()

        legacyRootDirs.forEach {
            assertThat(it.exists(), `is`(false))
        }

        getProjectDirPaths(existingProject.uuid).forEach {
            val dir = File(it)
            assertThat(dir.exists(), `is`(true))
            assertThat(dir.isDirectory, `is`(true))
            if (!it.endsWith("cache")) {
                assertThat(dir.listFiles()!!.isEmpty(), `is`(false))
            }
        }
    }

    @Test
    fun `deletes and does not move cache directory`() {
        val cacheDir = File(rootDir, ".cache")
        cacheDir.mkdir()
        TempFiles.createTempFile(cacheDir, "file", ".temp")

        existingProjectMigrator.run()
        val existingProject = currentProjectProvider.requireCurrentProject()

        assertThat(cacheDir.exists(), `is`(false))

        getProjectDirPaths(existingProject.uuid).forEach {
            val dir = File(it)
            assertThat(dir.exists(), `is`(true))
            assertThat(dir.isDirectory, `is`(true))
            assertThat(dir.listFiles()!!.isEmpty(), `is`(true))
        }
    }

    @Test
    fun `still copies other files if a directory is missing`() {
        val legacyRootDirsWithoutForms = listOf(
            File(rootDir, "instances"),
            File(rootDir, "metadata"),
            File(rootDir, "layers"),
            File(rootDir, "settings")
        )

        legacyRootDirsWithoutForms.forEach {
            it.mkdir()
            TempFiles.createTempFile(it, "file", ".temp")
        }

        existingProjectMigrator.run()
        val existingProject = currentProjectProvider.requireCurrentProject()
        getProjectDirPaths(existingProject.uuid).forEach {
            val dir = File(it)
            assertThat(dir.exists(), `is`(true))
            assertThat(dir.isDirectory, `is`(true))

            if (it.endsWith("forms") || it.endsWith("cache")) {
                assertThat(dir.listFiles()!!.isEmpty(), `is`(true))
            } else {
                assertThat(dir.listFiles()!!.isEmpty(), `is`(false))
            }
        }
    }

    @Test
    fun `migrates unprotected and protected settings`() {
        val oldGeneralSettings = PreferenceManager.getDefaultSharedPreferences(context)
        oldGeneralSettings.edit().putString("generalKey", "generalValue").apply()
        val oldAdminSettings = context.getSharedPreferences("admin_prefs", Context.MODE_PRIVATE)
        oldAdminSettings.edit().putString("adminKey", "adminValue").apply()

        existingProjectMigrator.run()
        val existingProject = currentProjectProvider.requireCurrentProject()

        val generalSettings = settingsProvider.getUnprotectedSettings(existingProject.uuid)
        assertThat(
            generalSettings.getString("generalKey"),
            `is`("generalValue")
        )
        val adminSettings = settingsProvider.getProtectedSettings(existingProject.uuid)
        assertThat(adminSettings.getString("adminKey"), `is`("adminValue"))
    }

    @Test
    fun `has key`() {
        assertThat(existingProjectMigrator.key(), `is`(MetaKeys.EXISTING_PROJECT_IMPORTED))
    }

    /**
     * Versions of the app before v2021.3.3 did not include a call to [AppUpgrader.install] and so
     * this [Upgrade] would get incorrectly run on upgrades from versions after
     * [AppUpgrader] and Projects (along with LAST_LAUNCHED) were added but before the call
     * to [AppUpgrader.install] was introduced.
     */
    @Test
    fun `does not run if previous app version already had projects`() {
        PreferenceManager
            .getDefaultSharedPreferences(context)
            .edit()
            .putString(ProjectKeys.KEY_SERVER_URL, "https://my-server.com")
            .apply()

        settingsProvider.getMetaSettings().save(MetaKeys.LAST_LAUNCHED, 3115)

        existingProjectMigrator.run()
        assertThat(projectsRepository.getAll().size, `is`(0))
    }

    private fun getProjectDirPaths(projectId: String): Array<String> {
        return arrayOf(
            storagePathProvider.getOdkDirPath(StorageSubdirectory.FORMS, projectId),
            storagePathProvider.getOdkDirPath(StorageSubdirectory.INSTANCES, projectId),
            storagePathProvider.getOdkDirPath(StorageSubdirectory.CACHE, projectId),
            storagePathProvider.getOdkDirPath(StorageSubdirectory.METADATA, projectId),
            storagePathProvider.getOdkDirPath(StorageSubdirectory.LAYERS, projectId),
            storagePathProvider.getOdkDirPath(StorageSubdirectory.SETTINGS, projectId)
        )
    }
}
