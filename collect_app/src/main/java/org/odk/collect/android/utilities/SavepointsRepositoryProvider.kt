package org.odk.collect.android.utilities

import android.content.Context
import org.odk.collect.android.application.Collect
import org.odk.collect.android.database.savepoints.DatabaseSavepointsRepository
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.storage.StoragePaths
import org.odk.collect.forms.savepoints.SavepointsRepository
import org.odk.collect.projects.ProjectDependencyFactory

class SavepointsRepositoryProvider(
    private val context: Context,
    private val storagePathFactory: ProjectDependencyFactory<StoragePaths>
) : ProjectDependencyFactory<SavepointsRepository> {

    override fun create(projectId: String): SavepointsRepository {
        val storagePaths = storagePathFactory.create(projectId)
        return DatabaseSavepointsRepository(
            context,
            storagePaths.metaDir,
            storagePaths.cacheDir,
            storagePaths.instancesDir
        )
    }

    @Deprecated("Creating dependency without specified project is dangerous")
    fun create(): SavepointsRepository {
        val currentProject =
            DaggerUtils.getComponent(Collect.getInstance()).currentProjectProvider()
                .requireCurrentProject()
        return create(currentProject.uuid)
    }
}
