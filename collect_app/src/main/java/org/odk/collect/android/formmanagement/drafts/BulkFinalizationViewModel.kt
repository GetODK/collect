package org.odk.collect.android.formmanagement.drafts

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import org.odk.collect.android.instancemanagement.FinalizeAllResult
import org.odk.collect.android.instancemanagement.InstancesDataService
import org.odk.collect.androidshared.data.Consumable
import org.odk.collect.androidshared.livedata.MutableNonNullLiveData
import org.odk.collect.androidshared.livedata.NonNullLiveData
import org.odk.collect.async.Scheduler
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.settings.keys.ProtectedProjectKeys

class BulkFinalizationViewModel(
    private val projectId: String,
    private val scheduler: Scheduler,
    private val instancesDataService: InstancesDataService,
    settingsProvider: SettingsProvider
) {
    private val _finalizedForms = MutableLiveData<Consumable<FinalizeAllResult>>()
    val finalizedForms: LiveData<Consumable<FinalizeAllResult>> = _finalizedForms

    private val _isFinalizing = MutableNonNullLiveData(false)
    val isFinalizing: NonNullLiveData<Boolean> = _isFinalizing

    val draftsCount = instancesDataService.getEditableCount(projectId).asLiveData()
    val isEnabled =
        settingsProvider.getProtectedSettings().getBoolean(ProtectedProjectKeys.KEY_BULK_FINALIZE)

    fun finalizeAllDrafts() {
        _isFinalizing.value = true

        scheduler.immediate(
            background = {
                instancesDataService.finalizeAllDrafts(projectId)
            },
            foreground = {
                _isFinalizing.value = false
                _finalizedForms.value = Consumable(it)
            }
        )
    }
}
