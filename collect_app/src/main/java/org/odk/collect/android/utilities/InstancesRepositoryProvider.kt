package org.odk.collect.android.utilities

import android.content.Context
import org.odk.collect.android.application.Collect
import org.odk.collect.android.database.instances.DatabaseInstancesRepository
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.android.storage.StoragePaths
import org.odk.collect.forms.instances.InstancesRepository
import org.odk.collect.projects.ProjectDependencyFactory

class InstancesRepositoryProvider @JvmOverloads constructor(
    private val context: Context,
    private val storagePathFactory: ProjectDependencyFactory<StoragePaths> = StoragePathProvider()
) : ProjectDependencyFactory<InstancesRepository> {

    override fun create(projectId: String): InstancesRepository {
        val storagePaths = storagePathFactory.create(projectId)
        return DatabaseInstancesRepository(
            context,
            storagePaths.metaDir,
            storagePaths.instancesDir,
            System::currentTimeMillis
        )
    }

    @Deprecated("Creating dependency without specified project is dangerous")
    fun create(): InstancesRepository {
        val currentProject =
            DaggerUtils.getComponent(Collect.getInstance()).currentProjectProvider()
                .requireCurrentProject()
        return create(currentProject.uuid)
    }
}
