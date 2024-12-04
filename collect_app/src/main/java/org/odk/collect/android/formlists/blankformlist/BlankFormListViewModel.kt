package org.odk.collect.android.formlists.blankformlist

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.odk.collect.android.formmanagement.FormsDataService
import org.odk.collect.async.Scheduler
import org.odk.collect.async.flowOnBackground
import org.odk.collect.forms.Form
import org.odk.collect.forms.FormSourceException
import org.odk.collect.forms.FormSourceException.AuthRequired
import org.odk.collect.forms.instances.InstancesRepository
import org.odk.collect.settings.enums.FormUpdateMode
import org.odk.collect.settings.enums.StringIdEnumUtils.getFormUpdateMode
import org.odk.collect.settings.keys.ProjectKeys
import org.odk.collect.shared.settings.Settings

class BlankFormListViewModel(
    private val instancesRepository: InstancesRepository,
    private val application: Application,
    private val formsDataService: FormsDataService,
    private val scheduler: Scheduler,
    private val generalSettings: Settings,
    private val projectId: String,
    private val showAllVersions: Boolean = false
) : ViewModel() {

    private val _filterText = MutableStateFlow("")
    private val _sortingOrder = MutableStateFlow(getSortOrder())
    private val filteredForms = formsDataService.getForms(projectId)
        .combine(_filterText) { forms, filter ->
            Pair(forms, filter)
        }.combine(_sortingOrder) { (forms, filter), sort ->
            Triple(forms, filter, sort)
        }

    val formsToDisplay: LiveData<List<BlankFormListItem>> =
        filteredForms.map { (forms, filter, sort) ->
            filterAndSortForms(forms, sort, filter)
        }.flowOnBackground(scheduler).asLiveData()

    val syncResult: LiveData<String?> = formsDataService.getDiskError(projectId)
    val isLoading: LiveData<Boolean> = formsDataService.isSyncing(projectId)

    var sortingOrder: SortOrder = getSortOrder()
        get() { return getSortOrder() }

        set(value) {
            field = value
            generalSettings.save(ProjectKeys.KEY_BLANK_FORM_SORT_ORDER, value.ordinal)
            _sortingOrder.value = value
        }

    var filterText: String = ""
        set(value) {
            field = value
            _filterText.value = value
        }

    init {
        scheduler.immediate(
            background = {
                formsDataService.refresh(projectId)
            },
            foreground = {}
        )
    }

    fun syncWithServer(): LiveData<Boolean> {
        val result = MutableLiveData<Boolean>()
        scheduler.immediate(
            {
                formsDataService.matchFormsWithServer(projectId)
            },
            { value: Boolean ->
                result.value = value
            }
        )
        return result
    }

    fun isMatchExactlyEnabled(): Boolean {
        return generalSettings.getFormUpdateMode(application) == FormUpdateMode.MATCH_EXACTLY
    }

    fun isOutOfSyncWithServer(): LiveData<Boolean> {
        return formsDataService.getServerError(projectId).map { obj: FormSourceException? ->
            obj != null
        }
    }

    fun isAuthenticationRequired(): LiveData<Boolean> {
        return formsDataService.getServerError(projectId).map { error: FormSourceException? ->
            if (error != null) {
                error is AuthRequired
            } else {
                false
            }
        }
    }

    fun deleteForms(vararg databaseIds: Long) {
        scheduler.immediate(
            background = {
                databaseIds.forEach {
                    formsDataService.deleteForm(projectId, it)
                }
            },
            foreground = {}
        )
    }

    private fun filterAndSortForms(
        forms: List<Form>,
        sort: SortOrder,
        filter: String
    ): List<BlankFormListItem> {
        var newListOfForms = forms
            .filter {
                !it.isDeleted
            }.map { form ->
                form.toBlankFormListItem(projectId, instancesRepository)
            }

        if (!showAllVersions) {
            newListOfForms = newListOfForms.groupBy {
                it.formId
            }.map { (_, itemsWithSameId) ->
                itemsWithSameId.sortedBy {
                    it.dateOfCreation
                }.last()
            }
        }

        return when (sort) {
            SortOrder.NAME_ASC -> newListOfForms.sortedBy { it.formName.lowercase() }
            SortOrder.NAME_DESC -> newListOfForms.sortedByDescending { it.formName.lowercase() }
            SortOrder.DATE_DESC -> newListOfForms.sortedByDescending {
                it.dateOfLastDetectedAttachmentsUpdate ?: it.dateOfCreation
            }
            SortOrder.DATE_ASC -> newListOfForms.sortedBy {
                it.dateOfLastDetectedAttachmentsUpdate ?: it.dateOfCreation
            }
            SortOrder.LAST_SAVED -> newListOfForms.sortedByDescending { it.dateOfLastUsage }
        }.filter {
            filter.isBlank() || it.formName.contains(filter, true)
        }
    }

    private fun getSortOrder() =
        SortOrder.entries[generalSettings.getInt(ProjectKeys.KEY_BLANK_FORM_SORT_ORDER)]

    class Factory(
        private val instancesRepository: InstancesRepository,
        private val application: Application,
        private val formsDataService: FormsDataService,
        private val scheduler: Scheduler,
        private val generalSettings: Settings,
        private val projectId: String
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BlankFormListViewModel(
                instancesRepository,
                application,
                formsDataService,
                scheduler,
                generalSettings,
                projectId,
                !generalSettings.getBoolean(ProjectKeys.KEY_HIDE_OLD_FORM_VERSIONS)
            ) as T
        }
    }

    enum class SortOrder {
        NAME_ASC,
        NAME_DESC,
        DATE_DESC,
        DATE_ASC,
        LAST_SAVED
    }
}
