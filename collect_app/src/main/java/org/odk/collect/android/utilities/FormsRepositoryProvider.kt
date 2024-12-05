package org.odk.collect.android.utilities

import android.content.Context
import org.odk.collect.android.application.Collect
import org.odk.collect.android.database.forms.DatabaseFormsRepository
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.android.storage.StoragePaths
import org.odk.collect.forms.FormsRepository
import org.odk.collect.forms.savepoints.SavepointsRepository
import org.odk.collect.projects.ProjectDependencyFactory

class FormsRepositoryProvider @JvmOverloads constructor(
    private val context: Context,
    private val storagePathFactory: ProjectDependencyFactory<StoragePaths> = StoragePathProvider(),
    private val savepointsRepositoryProvider: ProjectDependencyFactory<SavepointsRepository> = SavepointsRepositoryProvider(
        context,
        storagePathFactory
    )
) : ProjectDependencyFactory<FormsRepository> {

    private val clock = { System.currentTimeMillis() }

    override fun create(projectId: String): FormsRepository {
        val storagePaths = storagePathFactory.create(projectId)
        return DatabaseFormsRepository(
            context,
            storagePaths.metaDir,
            storagePaths.formsDir,
            storagePaths.cacheDir,
            clock,
            savepointsRepositoryProvider.create(projectId)
        )
    }

    @Deprecated("Creating dependency without specified project is dangerous")
    fun create(): FormsRepository {
        val currentProject =
            DaggerUtils.getComponent(Collect.getInstance()).currentProjectProvider()
                .requireCurrentProject()
        return create(currentProject.uuid)
    }
}
