package org.odk.collect.android.formmanagement.formmap

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.odk.collect.android.R
import org.odk.collect.forms.Form
import org.odk.collect.forms.instances.Instance
import org.odk.collect.formstest.FormUtils
import org.odk.collect.formstest.InMemFormsRepository
import org.odk.collect.formstest.InMemInstancesRepository
import org.odk.collect.formstest.InstanceUtils
import org.odk.collect.geo.selection.IconifiedText
import org.odk.collect.geo.selection.MappableSelectItem
import org.odk.collect.geo.selection.Status
import org.odk.collect.maps.MapPoint
import org.odk.collect.settings.InMemSettingsProvider
import org.odk.collect.settings.keys.ProtectedProjectKeys
import org.odk.collect.shared.TempFiles
import org.odk.collect.testshared.FakeScheduler
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FormMapViewModelTest {

    private val formsRepository = InMemFormsRepository()
    private val instancesRepository = InMemInstancesRepository()
    private val settingsProvider = InMemSettingsProvider().also {
        it.getProtectedSettings().save(ProtectedProjectKeys.KEY_EDIT_SAVED, true)
    }

    private val application = ApplicationProvider.getApplicationContext<Application>()
    private val scheduler = FakeScheduler()

    @Test
    fun `returns count based on form instances`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )
        instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .build()
        )
        instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .build()
        )

        val viewModel = createAndLoadViewModel(form)
        assertThat(viewModel.getItemCount().value, equalTo(2))
    }

    @Test
    fun `returns items based on form instances with point geometry`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )

        instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("")
                .geometryType("Something else")
                .build()
        )
        instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .build()
        )

        val instanceWithPoint = instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(true)
                .build()
        )

        val viewModel = createAndLoadViewModel(form)
        assertThat(viewModel.getMappableItems().value!!.size, equalTo(1))

        val expectedItem = MappableSelectItem.MappableSelectPoint(
            instanceWithPoint.dbId,
            instanceWithPoint.displayName,
            point = MapPoint(2.0, 1.0),
            smallIcon = R.drawable.ic_room_form_state_incomplete_24dp,
            largeIcon = R.drawable.ic_room_form_state_incomplete_48dp,
            action = IconifiedText(
                org.odk.collect.icons.R.drawable.ic_edit,
                application.getString(org.odk.collect.strings.R.string.edit_data)
            ),
            info = formatDate(
                org.odk.collect.strings.R.string.saved_on_date_at_time,
                instanceWithPoint.lastStatusChangeDate
            ),
            status = Status.ERRORS
        )
        assertThat(viewModel.getMappableItems().value!![0], equalTo(expectedItem))
    }

    @Test
    fun `valid drafts with geometry have proper icons, actions and no info`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )
        val instance = instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(true)
                .status(Instance.STATUS_VALID)
                .build()
        )

        val viewModel = createAndLoadViewModel(form)
        val expectedItem = MappableSelectItem.MappableSelectPoint(
            instance.dbId,
            instance.displayName,
            point = MapPoint(2.0, 1.0),
            smallIcon = R.drawable.ic_room_form_state_incomplete_24dp,
            largeIcon = R.drawable.ic_room_form_state_incomplete_48dp,
            action = IconifiedText(
                org.odk.collect.icons.R.drawable.ic_edit,
                application.getString(org.odk.collect.strings.R.string.edit_data)
            ),
            info = formatDate(
                org.odk.collect.strings.R.string.saved_on_date_at_time,
                instance.lastStatusChangeDate
            ),
            status = Status.NO_ERRORS
        )
        assertThat(viewModel.getMappableItems().value!![0], equalTo(expectedItem))
    }

    @Test
    fun `invalid drafts with geometry have proper icons, actions, status and no info`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )
        val instance = instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(true)
                .status(Instance.STATUS_INVALID)
                .build()
        )

        val viewModel = createAndLoadViewModel(form)
        val expectedItem = MappableSelectItem.MappableSelectPoint(
            instance.dbId,
            instance.displayName,
            point = MapPoint(2.0, 1.0),
            smallIcon = R.drawable.ic_room_form_state_incomplete_24dp,
            largeIcon = R.drawable.ic_room_form_state_incomplete_48dp,
            action = IconifiedText(
                org.odk.collect.icons.R.drawable.ic_edit,
                application.getString(org.odk.collect.strings.R.string.edit_data)
            ),
            info = formatDate(
                org.odk.collect.strings.R.string.saved_on_date_at_time,
                instance.lastStatusChangeDate
            ),
            status = Status.ERRORS
        )
        assertThat(viewModel.getMappableItems().value!![0], equalTo(expectedItem))
    }

    @Test
    fun `finalized instances with geometry have view action and no info`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )
        val instance = instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(true)
                .status(Instance.STATUS_COMPLETE)
                .build()
        )

        val viewModel = createAndLoadViewModel(form)
        val expectedItem = MappableSelectItem.MappableSelectPoint(
            instance.dbId,
            instance.displayName,
            point = MapPoint(2.0, 1.0),
            smallIcon = R.drawable.ic_room_form_state_complete_24dp,
            largeIcon = R.drawable.ic_room_form_state_complete_48dp,
            action = IconifiedText(
                R.drawable.ic_visibility,
                application.getString(org.odk.collect.strings.R.string.view_data)
            ),
            info = formatDate(
                org.odk.collect.strings.R.string.finalized_on_date_at_time,
                instance.lastStatusChangeDate
            )
        )
        assertThat(viewModel.getMappableItems().value!![0], equalTo(expectedItem))
    }

    @Test
    fun `finalized instances with geometry when edit after save disabled have view action and no info`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )
        val instance = instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(true)
                .status(Instance.STATUS_COMPLETE)
                .build()
        )

        settingsProvider.getProtectedSettings().save(ProtectedProjectKeys.KEY_EDIT_SAVED, false)

        val viewModel = createAndLoadViewModel(form)
        val expectedItem = MappableSelectItem.MappableSelectPoint(
            instance.dbId,
            instance.displayName,
            point = MapPoint(2.0, 1.0),
            smallIcon = R.drawable.ic_room_form_state_complete_24dp,
            largeIcon = R.drawable.ic_room_form_state_complete_48dp,
            action = IconifiedText(
                R.drawable.ic_visibility,
                application.getString(org.odk.collect.strings.R.string.view_data)
            ),
            info = formatDate(
                org.odk.collect.strings.R.string.finalized_on_date_at_time,
                instance.lastStatusChangeDate
            )
        )
        assertThat(viewModel.getMappableItems().value!![0], equalTo(expectedItem))
    }

    @Test
    fun `deleted instances with geometry have info and no action`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )
        val instance = instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(true)
                .deletedDate(123L)
                .build()
        )

        val viewModel = createAndLoadViewModel(form)
        val expectedItem = MappableSelectItem.MappableSelectPoint(
            instance.dbId,
            instance.displayName,
            point = MapPoint(2.0, 1.0),
            smallIcon = R.drawable.ic_room_form_state_incomplete_24dp,
            largeIcon = R.drawable.ic_room_form_state_incomplete_48dp,
            info = formatDate(
                org.odk.collect.strings.R.string.saved_on_date_at_time,
                instance.lastStatusChangeDate
            ) + "\n" +
                formatDate(
                    org.odk.collect.strings.R.string.deleted_on_date_at_time,
                    123L
                )
        )
        assertThat(viewModel.getMappableItems().value!![0], equalTo(expectedItem))
    }

    @Test
    fun `submitted instances with geometry have view action and no info`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )
        val instance = instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(true)
                .status(Instance.STATUS_SUBMITTED)
                .build()
        )

        val viewModel = createAndLoadViewModel(form)
        val expectedItem = MappableSelectItem.MappableSelectPoint(
            instance.dbId,
            instance.displayName,
            point = MapPoint(2.0, 1.0),
            smallIcon = R.drawable.ic_room_form_state_submitted_24dp,
            largeIcon = R.drawable.ic_room_form_state_submitted_48dp,
            action = IconifiedText(
                R.drawable.ic_visibility,
                application.getString(org.odk.collect.strings.R.string.view_data)
            ),
            info = formatDate(
                org.odk.collect.strings.R.string.sent_on_date_at_time,
                instance.lastStatusChangeDate
            )
        )
        assertThat(viewModel.getMappableItems().value!![0], equalTo(expectedItem))
    }

    @Test
    fun `instances that failed to submit with geometry have view action and no info`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )
        val instance = instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(true)
                .status(Instance.STATUS_SUBMISSION_FAILED)
                .build()
        )

        val viewModel = createAndLoadViewModel(form)
        val expectedItem = MappableSelectItem.MappableSelectPoint(
            instance.dbId,
            instance.displayName,
            point = MapPoint(2.0, 1.0),
            smallIcon = R.drawable.ic_room_form_state_submission_failed_24dp,
            largeIcon = R.drawable.ic_room_form_state_submission_failed_48dp,
            action = IconifiedText(
                R.drawable.ic_visibility,
                application.getString(org.odk.collect.strings.R.string.view_data)
            ),
            info = formatDate(
                org.odk.collect.strings.R.string.sending_failed_on_date_at_time,
                instance.lastStatusChangeDate
            )
        )
        assertThat(viewModel.getMappableItems().value!![0], equalTo(expectedItem))
    }

    @Test
    fun `instances that cannot be edited after completion with geometry have info and no action`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )
        instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(false)
                .status(Instance.STATUS_COMPLETE)
                .build()
        )
        instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(false)
                .status(Instance.STATUS_SUBMITTED)
                .build()
        )
        instancesRepository.save(
            InstanceUtils.buildInstance(
                form.formId,
                form.version,
                TempFiles.createTempDir().absolutePath
            )
                .geometry("{ \"coordinates\": [1.0, 2.0] }")
                .geometryType("Point")
                .canEditWhenComplete(false)
                .status(Instance.STATUS_SUBMISSION_FAILED)
                .build()
        )

        val viewModel = createAndLoadViewModel(form)
        val items = viewModel.getMappableItems().value

        assertThat(
            items!![0].info,
            containsString(application.getString(org.odk.collect.strings.R.string.cannot_edit_completed_form))
        )

        assertThat(
            items[1].info,
            containsString(application.getString(org.odk.collect.strings.R.string.cannot_edit_completed_form))
        )

        assertThat(
            items[2].info,
            containsString(application.getString(org.odk.collect.strings.R.string.cannot_edit_completed_form))
        )
    }

    @Test
    fun `is loading is true and items is null while forms and instances are being fetched`() {
        val form = formsRepository.save(
            FormUtils.buildForm("id", "version", TempFiles.createTempDir().absolutePath)
                .build()
        )

        val viewModel = createViewModel(form)
        assertThat(viewModel.isLoading().value, equalTo(false))
        assertThat(viewModel.getMappableItems().value, equalTo(null))

        viewModel.load()
        assertThat(viewModel.isLoading().value, equalTo(true))
        assertThat(viewModel.getMappableItems().value, equalTo(null))

        scheduler.runBackground()
        scheduler.runForeground()
        assertThat(viewModel.isLoading().value, equalTo(false))
    }

    private fun createAndLoadViewModel(form: Form): FormMapViewModel {
        val viewModel = createViewModel(form)
        viewModel.load()
        scheduler.flush()
        return viewModel
    }

    private fun createViewModel(form: Form): FormMapViewModel {
        return FormMapViewModel(
            application.resources,
            form.dbId,
            formsRepository,
            instancesRepository,
            settingsProvider,
            scheduler
        )
    }

    private fun formatDate(string: Int, date: Long): String {
        return SimpleDateFormat(
            application.getString(string),
            Locale.getDefault()
        ).format(date)
    }
}
