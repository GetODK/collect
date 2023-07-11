package org.odk.collect.android.external

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtraWithKey
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.not
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.odk.collect.android.R
import org.odk.collect.android.activities.FormFillingActivity
import org.odk.collect.android.injection.config.AppDependencyModule
import org.odk.collect.android.instancemanagement.OCTOBER_1st_2023_UTC
import org.odk.collect.android.projects.CurrentProjectProvider
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.android.support.CollectHelpers
import org.odk.collect.android.utilities.ApplicationConstants
import org.odk.collect.android.utilities.FormsRepositoryProvider
import org.odk.collect.android.utilities.InstancesRepositoryProvider
import org.odk.collect.androidtest.ActivityScenarioLauncherRule
import org.odk.collect.androidtest.RecordedIntentsRule
import org.odk.collect.forms.instances.Instance
import org.odk.collect.formstest.FormUtils
import org.odk.collect.formstest.InMemFormsRepository
import org.odk.collect.formstest.InMemInstancesRepository
import org.odk.collect.projects.InMemProjectsRepository
import org.odk.collect.projects.Project
import org.odk.collect.projects.ProjectsRepository
import org.odk.collect.settings.InMemSettingsProvider
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.settings.keys.ProtectedProjectKeys
import org.odk.collect.shared.TempFiles
import org.odk.collect.shared.strings.UUIDGenerator
import java.io.File

@RunWith(AndroidJUnit4::class)
class FormUriActivityTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val projectsRepository = InMemProjectsRepository()
    private val currentProjectProvider = mock<CurrentProjectProvider>()
    private val formsRepository = InMemFormsRepository()
    private val instancesRepository = InMemInstancesRepository()
    private val settingsProvider = InMemSettingsProvider().apply {
        getProtectedSettings().save(ProtectedProjectKeys.KEY_EDIT_SAVED, true)
    }

    @get:Rule
    val activityRule = RecordedIntentsRule()

    @get:Rule
    val launcherRule = ActivityScenarioLauncherRule()

    @Before
    fun setup() {
        CollectHelpers.overrideAppDependencyModule(object : AppDependencyModule() {
            override fun providesProjectsRepository(
                uuidGenerator: UUIDGenerator,
                gson: Gson,
                settingsProvider: SettingsProvider
            ): ProjectsRepository {
                return projectsRepository
            }

            override fun providesCurrentProjectProvider(
                settingsProvider: SettingsProvider,
                projectsRepository: ProjectsRepository
            ): CurrentProjectProvider {
                return currentProjectProvider
            }

            override fun providesFormsRepositoryProvider(application: Application): FormsRepositoryProvider {
                return mock<FormsRepositoryProvider>().apply {
                    whenever(get()).thenReturn(formsRepository)
                }
            }

            override fun providesInstancesRepositoryProvider(
                context: Context,
                storagePathProvider: StoragePathProvider
            ): InstancesRepositoryProvider {
                return mock<InstancesRepositoryProvider>().apply {
                    whenever(get()).thenReturn(instancesRepository)
                }
            }

            override fun providesSettingsProvider(context: Context): SettingsProvider {
                return settingsProvider
            }
        })
    }

    @Test
    fun `When there are no projects then display alert dialog`() {
        val scenario = launcherRule.launchForResult(FormUriActivity::class.java)

        assertErrorDialog(scenario, context.getString(R.string.app_not_configured))
    }

    @Test
    fun `When there is project id specified in uri but it is not the currently used project then display alert dialog`() {
        val firstProject = Project.Saved("123", "First project", "A", "#cccccc")
        val secondProject = Project.Saved("345", "Second project", "A", "#cccccc")
        projectsRepository.save(firstProject)
        projectsRepository.save(secondProject)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(firstProject)

        val form = formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val scenario = launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(secondProject.uuid, form.dbId))

        assertErrorDialog(scenario, context.getString(R.string.wrong_project_selected_for_form))
    }

    @Test
    fun `When there is no project id specified in uri and first available project is not the currently used project then display alert dialog`() {
        val firstProject = Project.Saved("123", "First project", "A", "#cccccc")
        val secondProject = Project.Saved("345", "Second project", "A", "#cccccc")
        projectsRepository.save(firstProject)
        projectsRepository.save(secondProject)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(secondProject)

        val form = formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val scenario = launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(null, form.dbId))

        assertErrorDialog(scenario, context.getString(R.string.wrong_project_selected_for_form))
    }

    @Test
    fun `When uri is null then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getBlankFormIntent(project.uuid, form.dbId).apply {
                data = null
            }
        )

        assertErrorDialog(scenario, context.getString(R.string.unrecognized_uri))
    }

    @Test
    fun `When uri is invalid then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getBlankFormIntent(project.uuid, form.dbId).apply {
                data = Uri.parse("blah")
            }
        )

        assertErrorDialog(scenario, context.getString(R.string.unrecognized_uri))
    }

    @Test
    fun `When uri represents a blank form that does not exist then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val scenario = launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(project.uuid, 1))

        assertErrorDialog(scenario, context.getString(R.string.bad_uri))
    }

    @Test
    fun `When uri represents a blank form with non existing form file then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        File(form.formFilePath).delete()
        val scenario = launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))

        assertErrorDialog(scenario, context.getString(R.string.bad_uri))
    }

    @Test
    fun `When uri represents a saved form that does not exist then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val scenario = launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, 1))

        assertErrorDialog(scenario, context.getString(R.string.bad_uri))
    }

    @Test
    fun `When attempting to edit a form with non existing instance file then display alert dialog and remove the instance from the database`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_SUBMISSION_FAILED)
                .build()
        )

        File(instance.instanceFilePath).delete()
        val scenario = launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        assertThat(instancesRepository.get(instance.dbId), equalTo(null))
        assertErrorDialog(scenario, context.getString(R.string.instance_deleted_message))
    }

    @Test
    fun `When attempting to edit a form with zero form definitions then display alert dialog with formId if version does not exist`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("20")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        val expectedMessage = context.getString(R.string.parent_form_not_present, instance.formId)

        assertErrorDialog(scenario, expectedMessage)
    }

    @Test
    fun `When attempting to edit a form with zero form definitions then display alert dialog with formId and version if both exist`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        val expectedMessage = context.getString(R.string.parent_form_not_present, "${instance.formId}\n${context.getString(R.string.version)} ${instance.formVersion}")

        assertErrorDialog(scenario, expectedMessage)
    }

    @Test
    fun `When attempting to edit a form with multiple non-deleted form definitions then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath, FormUtils.createXFormBody("1", "1", "Form 1")).build())
        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath, FormUtils.createXFormBody("1", "1", "Form 2")).build())

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        assertErrorDialog(scenario, context.getString(R.string.survey_multiple_forms_error))
    }

    @Test
    fun `When attempting to edit an encrypted form then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath, FormUtils.createXFormBody("1", "1", "Form 1")).build())

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .canEditWhenComplete(false)
                .build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        assertErrorDialog(scenario, context.getString(R.string.encrypted_form))
    }

    @Test
    fun `When attempting to edit an incomplete form with disabled editing then start form for view only`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        settingsProvider.getProtectedSettings().save(ProtectedProjectKeys.KEY_EDIT_SAVED, false)

        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        assertStartSavedFormIntent(project.uuid, instance.dbId, false)
    }

    @Test
    fun `When attempting to start a new form then there should be no form mode passed with the intent even if editing saved forms is disabled`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        settingsProvider.getProtectedSettings().save(ProtectedProjectKeys.KEY_EDIT_SAVED, false)

        val form = formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))

        assertStartBlankFormIntent(project.uuid, form.dbId)
    }

    @Test
    fun `When attempting to edit a finalized form saved before October 1st 2023 UTC then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .lastStatusChangeDate(OCTOBER_1st_2023_UTC - 1)
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_COMPLETE)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        assertStartSavedFormIntent(project.uuid, instance.dbId, true)
    }

    @Test
    fun `When attempting to edit a finalized form saved after October 1st 2023 UTC then start form for view only`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .lastStatusChangeDate(OCTOBER_1st_2023_UTC + 1)
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_COMPLETE)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        assertStartSavedFormIntent(project.uuid, instance.dbId, false)
    }

    @Test
    fun `When attempting to edit a submitted form then start form for view only`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_SUBMITTED)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        assertStartSavedFormIntent(project.uuid, instance.dbId, false)
    }

    @Test
    fun `When attempting to edit a form that failed to submit then start form for view only`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_SUBMISSION_FAILED)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        assertStartSavedFormIntent(project.uuid, instance.dbId, false)
    }

    @Test
    fun `Form filling should not be started again after recreating the activity or getting back to it`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val scenario = launcherRule.launch<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))
        scenario.recreate()

        Intents.intended(hasComponent(FormFillingActivity::class.java.name), Intents.times(1))
    }

    @Test
    fun `When there is project id specified in uri that represents a blank form and it matches current project id then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        launcherRule.launch<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))

        assertStartBlankFormIntent(project.uuid, form.dbId)
    }

    @Test
    fun `When there is project id specified in uri that represents a saved form and it matches current project id then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        launcherRule.launch<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))

        assertStartSavedFormIntent(project.uuid, instance.dbId, true)
    }

    @Test
    fun `When there is no project id specified in uri that represents a blank form and first available project id matches current project id then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        launcherRule.launch<FormUriActivity>(getBlankFormIntent(null, form.dbId))

        assertStartBlankFormIntent(null, form.dbId)
    }

    @Test
    fun `When there is no project id specified in uri that represents a saved form and first available project id matches current project id then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(currentProjectProvider.getCurrentProject()).thenReturn(project)

        formsRepository.save(FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build())

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        launcherRule.launch<FormUriActivity>(getSavedIntent(null, instance.dbId))

        assertStartSavedFormIntent(null, instance.dbId, true)
    }

    // TODO: Replace the explicit FormUriActivity intent with an implicit one Intent.ACTION_EDIT once it's possible https://github.com/android/android-test/issues/496
    private fun getBlankFormIntent(projectId: String?, dbId: Long) =
        Intent(context, FormUriActivity::class.java).apply {
            data = if (projectId == null) {
                getFormsUriInOldFormatWithNoProjectId(dbId)
            } else {
                FormsContract.getUri(projectId, dbId)
            }
            putExtra("KEY_1", "Text")
        }

    private fun getSavedIntent(projectId: String?, dbId: Long) =
        Intent(context, FormUriActivity::class.java).apply {
            data = if (projectId == null) {
                getInstancesUriInOldFormatWithNoProjectId(dbId)
            } else {
                InstancesContract.getUri(projectId, dbId)
            }
            putExtra("KEY_1", "Text")
        }

    private fun getFormsUriInOldFormatWithNoProjectId(dbId: Long): Uri {
        val uri = FormsContract.getUri("", dbId)
        return Uri.Builder()
            .scheme(uri.scheme)
            .authority(uri.authority)
            .path(uri.path)
            .query(null)
            .build()
    }

    private fun getInstancesUriInOldFormatWithNoProjectId(dbId: Long): Uri {
        val uri = InstancesContract.getUri("", dbId)
        return Uri.Builder()
            .scheme(uri.scheme)
            .authority(uri.authority)
            .path(uri.path)
            .query(null)
            .build()
    }

    private fun assertErrorDialog(scenario: ActivityScenario<FormUriActivity>, message: String) {
        onView(withText(message)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(android.R.id.button1)).perform(click())

        assertThat(scenario.result.resultCode, `is`(Activity.RESULT_CANCELED))
    }

    private fun assertStartBlankFormIntent(projectId: String?, dbId: Long) {
        Intents.intended(hasComponent(FormFillingActivity::class.java.name))
        if (projectId == null) {
            Intents.intended(hasData(getFormsUriInOldFormatWithNoProjectId(dbId)))
        } else {
            Intents.intended(hasData(FormsContract.getUri(projectId, dbId)))
        }
        Intents.intended(not(hasExtraWithKey(ApplicationConstants.BundleKeys.FORM_MODE)))
        Intents.intended(hasExtra("KEY_1", "Text"))
    }

    private fun assertStartSavedFormIntent(projectId: String?, dbId: Long, canBeEdited: Boolean) {
        Intents.intended(hasComponent(FormFillingActivity::class.java.name))
        if (projectId == null) {
            Intents.intended(hasData(getInstancesUriInOldFormatWithNoProjectId(dbId)))
        } else {
            Intents.intended(hasData(InstancesContract.getUri(projectId, dbId)))
        }
        if (canBeEdited) {
            Intents.intended(not(hasExtraWithKey(ApplicationConstants.BundleKeys.FORM_MODE)))
        } else {
            Intents.intended(hasExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.VIEW_SENT))
        }
        Intents.intended(hasExtra("KEY_1", "Text"))
    }
}
