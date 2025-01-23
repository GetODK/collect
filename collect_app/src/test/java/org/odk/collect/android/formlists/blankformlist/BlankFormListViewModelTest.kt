package org.odk.collect.android.formlists.blankformlist

import android.app.Application
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.MutableLiveData
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.MutableStateFlow
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.odk.collect.android.formmanagement.FormsDataService
import org.odk.collect.android.utilities.ChangeLockProvider
import org.odk.collect.forms.Form
import org.odk.collect.forms.FormSourceException
import org.odk.collect.forms.instances.Instance
import org.odk.collect.formstest.FormUtils
import org.odk.collect.formstest.InMemInstancesRepository
import org.odk.collect.settings.enums.FormUpdateMode
import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.shared.locks.BooleanChangeLock
import org.odk.collect.shared.settings.InMemSettings
import org.odk.collect.testshared.FakeScheduler
import org.odk.collect.testshared.getOrAwaitValue

@RunWith(AndroidJUnit4::class)
class BlankFormListViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val instancesRepository = InMemInstancesRepository()
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val formsDataService: FormsDataService = mock {
        on { getForms(any()) } doReturn MutableStateFlow(emptyList())
    }

    private val scheduler = FakeScheduler()
    private val generalSettings = InMemSettings()
    private val changeLockProvider: ChangeLockProvider = mock()
    private val projectId = "projectId"

    private val changeLock = BooleanChangeLock()
    private lateinit var viewModel: BlankFormListViewModel

    @Test
    fun `updates forms when created`() {
        createViewModel()
        verify(formsDataService).refresh(projectId)
    }

    @Test
    fun `syncWithServer when task finishes sets result to true`() {
        createViewModel()
        generalSettings.save(ProjectKeys.KEY_SERVER_URL, "https://sample.com")
        doReturn(true).whenever(formsDataService).matchFormsWithServer(projectId)
        val result = viewModel.syncWithServer()
        scheduler.flush()
        assertThat(result.value, `is`(true))
    }

    @Test
    fun `syncWithServer when there is an error sets result to false`() {
        createViewModel()
        generalSettings.save(ProjectKeys.KEY_SERVER_URL, "https://sample.com")
        doReturn(false).whenever(formsDataService).matchFormsWithServer(projectId)
        val result = viewModel.syncWithServer()
        scheduler.flush()
        assertThat(result.value, `is`(false))
    }

    @Test
    fun `isMatchExactlyEnabled returns correct value based on settings`() {
        generalSettings.save(
            ProjectKeys.KEY_FORM_UPDATE_MODE,
            FormUpdateMode.MANUAL.getValue(context)
        )

        createViewModel()

        assertThat(viewModel.isMatchExactlyEnabled(), `is`(false))

        generalSettings.save(
            ProjectKeys.KEY_FORM_UPDATE_MODE,
            FormUpdateMode.MATCH_EXACTLY.getValue(context)
        )

        assertThat(viewModel.isMatchExactlyEnabled(), `is`(true))
    }

    @Test
    fun `isOutOfSyncWithServer follows repository syncError`() {
        createViewModel()

        val liveData = MutableLiveData<FormSourceException?>(FormSourceException.FetchError())
        whenever(formsDataService.getServerError(projectId)).thenReturn(liveData)

        val outOfSync = viewModel.isOutOfSyncWithServer()
        assertThat(outOfSync.getOrAwaitValue(scheduler), `is`(true))
        liveData.value = null
        assertThat(outOfSync.getOrAwaitValue(scheduler), `is`(false))
    }

    @Test
    fun `isAuthenticationRequired follows repository syncError`() {
        createViewModel()

        val liveData = MutableLiveData<FormSourceException?>(FormSourceException.FetchError())
        whenever(formsDataService.getServerError(projectId)).thenReturn(liveData)

        val authenticationRequired = viewModel.isAuthenticationRequired()
        assertThat(authenticationRequired.getOrAwaitValue(scheduler), `is`(false))
        liveData.value = FormSourceException.AuthRequired()
        assertThat(authenticationRequired.getOrAwaitValue(scheduler), `is`(true))
        liveData.value = null
        assertThat(authenticationRequired.getOrAwaitValue(scheduler), `is`(false))
    }

    @Test
    fun `original list of forms should be fetched from database initially and sorted by name ASC`() {
        saveForms(
            form(dbId = 1, formId = "1"),
            form(dbId = 2, formId = "2")
        )

        createViewModel()

        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 1, formId = "1"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 2, formId = "2"))
    }

    @Test
    fun `deleted forms should be ignored`() {
        saveForms(
            form(dbId = 1, formId = "1"),
            form(dbId = 2, formId = "2", deleted = true)
        )

        createViewModel()

        val formsToDisplay = viewModel.formsToDisplay.getOrAwaitValue(scheduler)
        assertThat(formsToDisplay.size, `is`(1))
        assertFormItem(formsToDisplay[0], form(dbId = 1, formId = "1"))
    }

    @Test
    fun `only the newest version of every form (by date) should be visible if hiding old form versions is enabled`() {
        saveForms(
            form(dbId = 1, formId = "1", version = "2"),
            form(dbId = 2, formId = "1", version = "1")
        )

        createViewModel(showAllVersions = false)

        assertThat(viewModel.formsToDisplay.getOrAwaitValue(scheduler).size, `is`(1))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 2, formId = "1", version = "1"))
    }

    @Test
    fun `all form versions should be visible if showAllVersions is true`() {
        saveForms(
            form(dbId = 1, formId = "1", version = "2"),
            form(dbId = 2, formId = "1", version = "1")
        )

        createViewModel(showAllVersions = true)

        assertThat(viewModel.formsToDisplay.getOrAwaitValue(scheduler).size, `is`(2))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 1, formId = "1", version = "2"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 2, formId = "1", version = "1"))
    }

    @Test
    fun `when list of forms sorted 'by name ASC', saved should forms be ordered properly`() {
        saveForms(
            form(dbId = 1, formId = "1", formName = "1Form"),
            form(dbId = 2, formId = "2", formName = "BForm"),
            form(dbId = 3, formId = "3", formName = "aForm"),
            form(dbId = 4, formId = "4", formName = "AForm"),
            form(dbId = 5, formId = "5", formName = "2Form")
        )

        createViewModel()

        viewModel.sortingOrder = BlankFormListViewModel.SortOrder.NAME_ASC

        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 1, formId = "1", formName = "1Form"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 5, formId = "5", formName = "2Form"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[2], form(dbId = 3, formId = "3", formName = "aForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[3], form(dbId = 4, formId = "4", formName = "AForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[4], form(dbId = 2, formId = "2", formName = "BForm"))
    }

    @Test
    fun `when list of forms sorted 'by name DESC', saved should forms be ordered properly`() {
        saveForms(
            form(dbId = 1, formId = "1", formName = "1Form"),
            form(dbId = 2, formId = "2", formName = "BForm"),
            form(dbId = 3, formId = "3", formName = "aForm"),
            form(dbId = 4, formId = "4", formName = "AForm"),
            form(dbId = 5, formId = "5", formName = "2Form")
        )

        createViewModel()

        viewModel.sortingOrder = BlankFormListViewModel.SortOrder.NAME_DESC

        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 2, formId = "2", formName = "BForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 3, formId = "3", formName = "aForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[2], form(dbId = 4, formId = "4", formName = "AForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[3], form(dbId = 5, formId = "5", formName = "2Form"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[4], form(dbId = 1, formId = "1", formName = "1Form"))
    }

    @Test
    fun `when list of forms sorted 'by date newest first', saved should forms be ordered properly`() {
        saveForms(
            form(dbId = 1, formId = "1", formName = "1Form"),
            form(dbId = 2, formId = "2", formName = "BForm", lastDetectedAttachmentsUpdateDate = 6),
            form(dbId = 3, formId = "3", formName = "aForm"),
            form(dbId = 4, formId = "4", formName = "AForm", lastDetectedAttachmentsUpdateDate = 7),
            form(dbId = 5, formId = "5", formName = "2Form")
        )

        createViewModel()

        viewModel.sortingOrder = BlankFormListViewModel.SortOrder.DATE_DESC

        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 4, formId = "4", formName = "AForm", lastDetectedAttachmentsUpdateDate = 7))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 2, formId = "2", formName = "BForm", lastDetectedAttachmentsUpdateDate = 6))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[2], form(dbId = 5, formId = "5", formName = "2Form"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[3], form(dbId = 3, formId = "3", formName = "aForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[4], form(dbId = 1, formId = "1", formName = "1Form"))
    }

    @Test
    fun `when list of forms sorted 'by date oldest first', saved should forms be ordered properly`() {
        saveForms(
            form(dbId = 1, formId = "1", formName = "1Form"),
            form(dbId = 2, formId = "2", formName = "BForm", lastDetectedAttachmentsUpdateDate = 6),
            form(dbId = 3, formId = "3", formName = "aForm"),
            form(dbId = 4, formId = "4", formName = "AForm", lastDetectedAttachmentsUpdateDate = 7),
            form(dbId = 5, formId = "5", formName = "2Form")
        )

        createViewModel()

        viewModel.sortingOrder = BlankFormListViewModel.SortOrder.DATE_ASC

        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 1, formId = "1", formName = "1Form"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 3, formId = "3", formName = "aForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[2], form(dbId = 5, formId = "5", formName = "2Form"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[3], form(dbId = 2, formId = "2", formName = "BForm", lastDetectedAttachmentsUpdateDate = 6))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[4], form(dbId = 4, formId = "4", formName = "AForm", lastDetectedAttachmentsUpdateDate = 7))
    }

    @Test
    fun `when list of forms sorted 'by last saved', should forms be ordered properly`() {
        saveForms(
            form(dbId = 1, formId = "1", formName = "1Form"),
            form(dbId = 2, formId = "2", formName = "BForm"),
            form(dbId = 3, formId = "3", formName = "aForm"),
            form(dbId = 4, formId = "4", formName = "AForm"),
            form(dbId = 5, formId = "5", formName = "2Form")
        )

        saveInstances(
            instance(formId = "1", lastStatusChangeDate = 1L),
            instance(formId = "3", lastStatusChangeDate = 2L),
            instance(formId = "5", lastStatusChangeDate = 3L),
            instance(formId = "4", lastStatusChangeDate = 4L),
            instance(formId = "2", lastStatusChangeDate = 5L)
        )

        createViewModel()

        viewModel.sortingOrder = BlankFormListViewModel.SortOrder.LAST_SAVED

        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 2, formId = "2", formName = "BForm"), 5L)
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 4, formId = "4", formName = "AForm"), 4L)
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[2], form(dbId = 5, formId = "5", formName = "2Form"), 3L)
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[3], form(dbId = 3, formId = "3", formName = "aForm"), 2L)
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[4], form(dbId = 1, formId = "1", formName = "1Form"), 1L)
    }

    @Test
    fun `when list of forms sorted 'by last saved' and there are no saved instances, should the order from database be preserved`() {
        saveForms(
            form(dbId = 1, formId = "1", formName = "1Form"),
            form(dbId = 2, formId = "2", formName = "BForm"),
            form(dbId = 3, formId = "3", formName = "aForm"),
            form(dbId = 4, formId = "4", formName = "AForm"),
            form(dbId = 5, formId = "5", formName = "2Form")
        )

        createViewModel()

        viewModel.sortingOrder = BlankFormListViewModel.SortOrder.LAST_SAVED

        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 1, formId = "1", formName = "1Form"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 2, formId = "2", formName = "BForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[2], form(dbId = 3, formId = "3", formName = "aForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[3], form(dbId = 4, formId = "4", formName = "AForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[4], form(dbId = 5, formId = "5", formName = "2Form"))
    }

    @Test
    fun `when list of forms sorted 'by last saved', forms with no saved instances should be displayed at the bottom`() {
        saveForms(
            form(dbId = 1, formId = "1", formName = "1Form"),
            form(dbId = 2, formId = "2", formName = "BForm"),
            form(dbId = 3, formId = "3", formName = "aForm"),
            form(dbId = 4, formId = "4", formName = "AForm"),
            form(dbId = 5, formId = "5", formName = "2Form")
        )

        saveInstances(
            instance(formId = "1", lastStatusChangeDate = 1L),
            instance(formId = "3", lastStatusChangeDate = 2L)
        )

        createViewModel()

        viewModel.sortingOrder = BlankFormListViewModel.SortOrder.LAST_SAVED

        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 3, formId = "3", formName = "aForm"), 2L)
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 1, formId = "1", formName = "1Form"), 1L)
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[2], form(dbId = 2, formId = "2", formName = "BForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[3], form(dbId = 4, formId = "4", formName = "AForm"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[4], form(dbId = 5, formId = "5", formName = "2Form"))
    }

    @Test
    fun `when list of forms sorted 'by last saved', and there are different versions of the same form, sorting should distinguish different form versions`() {
        saveForms(
            form(dbId = 1, formId = "1", formName = "AForm v1", version = "1"),
            form(dbId = 2, formId = "1", formName = "AForm v2", version = "2"),
            form(dbId = 3, formId = "2", formName = "BForm")
        )

        saveInstances(
            instance(formId = "1", lastStatusChangeDate = 1L, version = "1"),
            instance(formId = "2", lastStatusChangeDate = 2L),
            instance(formId = "1", lastStatusChangeDate = 3L, version = "2")
        )

        createViewModel(showAllVersions = true)

        viewModel.sortingOrder = BlankFormListViewModel.SortOrder.LAST_SAVED

        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 2, formId = "1", formName = "AForm v2", version = "2"), 3L)
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 3, formId = "2", formName = "BForm"), 2L)
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[2], form(dbId = 1, formId = "1", formName = "AForm v1", version = "1"), 1L)
    }

    @Test
    fun `list of forms should be filtered when filterText is changed`() {
        saveForms(
            form(dbId = 1, formId = "1"),
            form(dbId = 2, formId = "2", formName = "Form 2"),
            form(dbId = 3, formId = "3", formName = "Form 2x")
        )

        createViewModel()

        viewModel.filterText = "2"

        assertThat(viewModel.formsToDisplay.getOrAwaitValue(scheduler).size, `is`(2))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 2, formId = "2"))
        assertFormItem(
            viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1],
            form(dbId = 3, formId = "3", formName = "Form 2x")
        )

        viewModel.filterText = "2x"

        assertThat(viewModel.formsToDisplay.getOrAwaitValue(scheduler).size, `is`(1))
        assertFormItem(
            viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0],
            form(dbId = 3, formId = "3", formName = "Form 2x")
        )

        viewModel.filterText = ""

        assertThat(viewModel.formsToDisplay.getOrAwaitValue(scheduler).size, `is`(3))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 1, formId = "1"))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 2, formId = "2"))
        assertFormItem(
            viewModel.formsToDisplay.getOrAwaitValue(scheduler)[2],
            form(dbId = 3, formId = "3", formName = "Form 2x")
        )
    }

    @Test
    fun `filtering and sorting should work together`() {
        saveForms(
            form(dbId = 1, formId = "1"),
            form(dbId = 2, formId = "2"),
            form(dbId = 3, formId = "3", formName = "Form 2x")
        )
        createViewModel()

        viewModel.filterText = "2"

        assertThat(viewModel.formsToDisplay.getOrAwaitValue(scheduler).size, `is`(2))
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0], form(dbId = 2, formId = "2"))
        assertFormItem(
            viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1],
            form(dbId = 3, formId = "3", formName = "Form 2x")
        )

        viewModel.sortingOrder = BlankFormListViewModel.SortOrder.NAME_DESC

        assertThat(viewModel.formsToDisplay.getOrAwaitValue(scheduler).size, `is`(2))
        assertFormItem(
            viewModel.formsToDisplay.getOrAwaitValue(scheduler)[0],
            form(dbId = 3, formId = "3", formName = "Form 2x")
        )
        assertFormItem(viewModel.formsToDisplay.getOrAwaitValue(scheduler)[1], form(dbId = 2, formId = "2"))
    }

    private fun saveForms(vararg forms: Form) {
        whenever(formsDataService.getForms(any())).thenReturn(MutableStateFlow(forms.toList()))
    }

    private fun saveInstances(vararg instances: Instance) {
        instancesRepository.deleteAll()

        instances.forEach {
            instancesRepository.save(it)
        }
    }

    private fun createViewModel(
        runAllBackgroundTasks: Boolean = true,
        showAllVersions: Boolean = false
    ) {
        whenever(formsDataService.isSyncing(projectId)).thenReturn(MutableLiveData(false))
        whenever(changeLockProvider.getFormLock(projectId)).thenReturn(changeLock)

        viewModel = BlankFormListViewModel(
            instancesRepository,
            context,
            formsDataService,
            scheduler,
            generalSettings,
            projectId,
            showAllVersions
        )

        if (runAllBackgroundTasks) {
            scheduler.flush()
        }
    }

    private fun assertFormItem(blankFormListItem: BlankFormListItem, form: Form, lastStatusChangeDate: Long = 0) {
        assertThat(
            blankFormListItem,
            `is`(
                form.toBlankFormListItem(projectId, instancesRepository)
            )
        )
    }

    private fun form(
        dbId: Long,
        formId: String = "1",
        version: String? = null,
        formName: String = "Form $formId",
        deleted: Boolean = false,
        lastDetectedAttachmentsUpdateDate: Long? = null
    ) = Form.Builder()
        .dbId(dbId)
        .formId(formId)
        .version(version)
        .displayName(formName)
        .date(dbId)
        .deleted(deleted)
        .formFilePath(FormUtils.createXFormFile(formId, version).absolutePath)
        .lastDetectedAttachmentsUpdateDate(lastDetectedAttachmentsUpdateDate)
        .build()

    private fun instance(
        formId: String,
        lastStatusChangeDate: Long,
        version: String? = null
    ) = Instance.Builder()
        .formId(formId)
        .formVersion(version)
        .lastStatusChangeDate(lastStatusChangeDate)
        .build()
}
