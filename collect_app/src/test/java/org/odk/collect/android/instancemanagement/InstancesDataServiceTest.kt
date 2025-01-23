package org.odk.collect.android.instancemanagement

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.odk.collect.android.notifications.Notifier
import org.odk.collect.android.openrosa.HttpGetResult
import org.odk.collect.android.openrosa.OpenRosaHttpInterface
import org.odk.collect.android.projects.ProjectDependencyModule
import org.odk.collect.android.utilities.ChangeLocks
import org.odk.collect.androidshared.data.AppState
import org.odk.collect.forms.instances.Instance.STATUS_COMPLETE
import org.odk.collect.forms.instances.Instance.STATUS_INCOMPLETE
import org.odk.collect.forms.instances.Instance.STATUS_INVALID
import org.odk.collect.forms.instances.Instance.STATUS_SUBMISSION_FAILED
import org.odk.collect.forms.instances.Instance.STATUS_SUBMITTED
import org.odk.collect.forms.instances.Instance.STATUS_VALID
import org.odk.collect.formstest.FormFixtures
import org.odk.collect.formstest.InMemFormsRepository
import org.odk.collect.formstest.InMemInstancesRepository
import org.odk.collect.formstest.InstanceFixtures
import org.odk.collect.projects.ProjectDependencyFactory
import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.shared.locks.BooleanChangeLock
import org.odk.collect.shared.settings.InMemSettings
import java.io.File

@RunWith(AndroidJUnit4::class)
class InstancesDataServiceTest {
    private val projectsDependencyModuleFactory = CachingProjectDependencyModuleFactory { projectId ->
        ProjectDependencyModule(
            projectId,
            {
                InMemSettings().also {
                    it.save(ProjectKeys.KEY_SERVER_URL, "http://example.com")
                }
            },
            { InMemFormsRepository() },
            { InMemInstancesRepository() },
            mock(),
            { ChangeLocks(BooleanChangeLock(), BooleanChangeLock()) },
            mock(),
            mock(),
            mock()
        )
    }

    private val projectId = "projectId"
    private val projectDependencyModule = projectsDependencyModuleFactory.create(projectId)
    private val httpInterface = mock<OpenRosaHttpInterface>()
    private val notifier = mock<Notifier>()

    private val instancesDataService =
        InstancesDataService(
            AppState(),
            mock(),
            projectsDependencyModuleFactory,
            notifier,
            mock(),
            httpInterface,
            mock()
        )

    @Test
    fun `instances should not be deleted if the instances database is locked`() {
        projectDependencyModule.instancesLock.lock()
        val result = instancesDataService.deleteInstances(projectId, longArrayOf(1))
        assertThat(result, equalTo(false))
    }

    @Test
    fun `instances should be deleted if the instances database is not locked`() {
        val result = instancesDataService.deleteInstances(projectId, longArrayOf(1))
        assertThat(result, equalTo(true))
    }

    @Test
    fun `sendInstances() returns true when there are no instances to send`() {
        val result = instancesDataService.sendInstances(projectId)
        assertThat(result, equalTo(true))
    }

    @Test
    fun `sendInstances() does not notify when there are no instances to send`() {
        instancesDataService.sendInstances(projectId)
        verifyNoInteractions(notifier)
    }

    @Test
    fun `sendInstances() returns false when an instance fails to send`() {
        val formsRepository = projectDependencyModule.formsRepository
        val form = formsRepository.save(FormFixtures.form())

        val instancesRepository = projectDependencyModule.instancesRepository
        instancesRepository.save(InstanceFixtures.instance(form = form, status = STATUS_COMPLETE))

        whenever(httpInterface.executeGetRequest(any(), any(), any()))
            .doReturn(HttpGetResult(null, emptyMap(), "", 500))

        val result = instancesDataService.sendInstances(projectId)
        assertThat(result, equalTo(false))
    }

    @Test
    fun `#reset does not reset instances that can't be deleted before sending`() {
        val formsRepository = projectDependencyModule.formsRepository
        val form = formsRepository.save(FormFixtures.form())

        val instancesRepository = projectDependencyModule.instancesRepository
        instancesRepository.save(
            InstanceFixtures.instance(
                form = form,
                canDeleteBeforeSend = false,
                status = STATUS_INCOMPLETE
            )
        )
        instancesRepository.save(
            InstanceFixtures.instance(
                form = form,
                canDeleteBeforeSend = false,
                status = STATUS_COMPLETE
            )
        )
        instancesRepository.save(
            InstanceFixtures.instance(
                form = form,
                canDeleteBeforeSend = false,
                status = STATUS_INVALID
            )
        )
        instancesRepository.save(
            InstanceFixtures.instance(
                form = form,
                canDeleteBeforeSend = false,
                status = STATUS_VALID
            )
        )
        instancesRepository.save(
            InstanceFixtures.instance(
                form = form,
                canDeleteBeforeSend = false,
                status = STATUS_SUBMITTED
            )
        )
        instancesRepository.save(
            InstanceFixtures.instance(
                form = form,
                canDeleteBeforeSend = false,
                status = STATUS_SUBMISSION_FAILED
            )
        )

        instancesDataService.reset(projectDependencyModule.projectId)
        val remainingInstances = instancesRepository.all
        assertThat(remainingInstances.size, equalTo(2))
        assertThat(remainingInstances.any { it.status == STATUS_COMPLETE }, equalTo(true))
        assertThat(remainingInstances.any { it.status == STATUS_SUBMISSION_FAILED }, equalTo(true))
        assertThat(File(remainingInstances[0].instanceFilePath).parentFile?.exists(), equalTo(true))
        assertThat(File(remainingInstances[1].instanceFilePath).parentFile?.exists(), equalTo(true))
    }

    @Test
    fun `#update updates instances and counts`() {
        val instancesRepository = projectDependencyModule.instancesRepository
        instancesRepository.save(InstanceFixtures.instance(status = STATUS_COMPLETE))
        instancesRepository.save(InstanceFixtures.instance(status = STATUS_SUBMITTED))
        instancesRepository.save(InstanceFixtures.instance(status = STATUS_INCOMPLETE))

        instancesDataService.update(projectId)
        assertThat(
            instancesDataService.getInstances(projectId).value,
            equalTo(instancesRepository.all)
        )
        assertThat(instancesDataService.getSentCount(projectId).value, equalTo(1))
        assertThat(instancesDataService.getEditableCount(projectId).value, equalTo(1))
        assertThat(instancesDataService.getSendableCount(projectId).value, equalTo(1))
        assertThat(instancesDataService.getInstances("otherProjectId").value, equalTo(emptyList()))
        assertThat(instancesDataService.getSentCount("otherProjectId").value, equalTo(0))
        assertThat(instancesDataService.getEditableCount("otherProjectId").value, equalTo(0))
        assertThat(instancesDataService.getSendableCount("otherProjectId").value, equalTo(0))
    }
}

class CachingProjectDependencyModuleFactory(private val moduleFactory: (String) -> ProjectDependencyModule) :
    ProjectDependencyFactory<ProjectDependencyModule> {

    private val modules = mutableMapOf<String, ProjectDependencyModule>()

    override fun create(projectId: String): ProjectDependencyModule {
        return modules.getOrPut(projectId) {
            moduleFactory(projectId)
        }
    }
}
