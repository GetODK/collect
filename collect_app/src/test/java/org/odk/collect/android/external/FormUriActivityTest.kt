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
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.intent.matcher.IntentMatchers.hasData
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtraWithKey
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
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
import org.odk.collect.android.activities.FormFillingActivity
import org.odk.collect.android.application.initialization.AnalyticsInitializer
import org.odk.collect.android.application.initialization.MapsInitializer
import org.odk.collect.android.injection.config.AppDependencyModule
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.android.support.CollectHelpers
import org.odk.collect.android.utilities.ApplicationConstants
import org.odk.collect.android.utilities.ChangeLockProvider
import org.odk.collect.android.utilities.FormsRepositoryProvider
import org.odk.collect.android.utilities.InstancesRepositoryProvider
import org.odk.collect.android.utilities.SavepointsRepositoryProvider
import org.odk.collect.androidtest.ActivityScenarioLauncherRule
import org.odk.collect.androidtest.RecordedIntentsRule
import org.odk.collect.async.Scheduler
import org.odk.collect.forms.instances.Instance
import org.odk.collect.forms.savepoints.Savepoint
import org.odk.collect.formstest.FormUtils
import org.odk.collect.formstest.InMemFormsRepository
import org.odk.collect.formstest.InMemInstancesRepository
import org.odk.collect.formstest.InMemSavepointsRepository
import org.odk.collect.projects.InMemProjectsRepository
import org.odk.collect.projects.Project
import org.odk.collect.projects.ProjectsRepository
import org.odk.collect.settings.InMemSettingsProvider
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.settings.keys.ProtectedProjectKeys
import org.odk.collect.shared.TempFiles
import org.odk.collect.shared.locks.BooleanChangeLock
import org.odk.collect.shared.strings.UUIDGenerator
import org.odk.collect.testshared.FakeScheduler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FormUriActivityTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val projectsRepository = InMemProjectsRepository()
    private val projectsDataService = mock<ProjectsDataService>()
    private val formsRepository = InMemFormsRepository()
    private val instancesRepository = InMemInstancesRepository { 0 }
    private val fakeScheduler = FakeScheduler()
    private val settingsProvider = InMemSettingsProvider().apply {
        getProtectedSettings().save(ProtectedProjectKeys.KEY_EDIT_SAVED, true)
    }
    private val savepointsRepository = InMemSavepointsRepository()
    private val savepointsRepositoryProvider = mock<SavepointsRepositoryProvider>().apply {
        whenever(create()).thenReturn(savepointsRepository)
    }
    private val changeLock = BooleanChangeLock()
    private val changeLockProvider = ChangeLockProvider { changeLock }

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
                application: Application,
                settingsProvider: SettingsProvider,
                projectsRepository: ProjectsRepository,
                analyticsInitializer: AnalyticsInitializer,
                context: Context,
                mapsInitializer: MapsInitializer
            ): ProjectsDataService {
                return projectsDataService
            }

            override fun providesFormsRepositoryProvider(application: Application): FormsRepositoryProvider {
                return mock<FormsRepositoryProvider>().apply {
                    whenever(create()).thenReturn(formsRepository)
                }
            }

            override fun providesInstancesRepositoryProvider(
                context: Context,
                storagePathProvider: StoragePathProvider
            ): InstancesRepositoryProvider {
                return mock<InstancesRepositoryProvider>().apply {
                    whenever(create()).thenReturn(instancesRepository)
                }
            }

            override fun providesSettingsProvider(context: Context): SettingsProvider {
                return settingsProvider
            }

            override fun providesScheduler(workManager: WorkManager?): Scheduler {
                return fakeScheduler
            }

            override fun providesSavepointsRepositoryProvider(context: Context?, storagePathProvider: StoragePathProvider?): SavepointsRepositoryProvider {
                return savepointsRepositoryProvider
            }

            override fun providesChangeLockProvider(): ChangeLockProvider {
                return changeLockProvider
            }
        })
    }

    @Test
    fun `When there are no projects then display alert dialog`() {
        val scenario = launcherRule.launchForResult(FormUriActivity::class.java)
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.app_not_configured)
        )
    }

    @Test
    fun `When there is project id specified in uri but it is not the currently used project then display alert dialog`() {
        val firstProject = Project.Saved("123", "First project", "A", "#cccccc")
        val secondProject = Project.Saved("345", "Second project", "A", "#cccccc")
        projectsRepository.save(firstProject)
        projectsRepository.save(secondProject)
        whenever(projectsDataService.getCurrentProject()).thenReturn(firstProject)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getBlankFormIntent(
                secondProject.uuid,
                form.dbId
            )
        )
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.wrong_project_selected_for_form)
        )
    }

    @Test
    fun `When there is no project id specified in uri and first available project is not the currently used project then display alert dialog`() {
        val firstProject = Project.Saved("123", "First project", "A", "#cccccc")
        val secondProject = Project.Saved("345", "Second project", "A", "#cccccc")
        projectsRepository.save(firstProject)
        projectsRepository.save(secondProject)
        whenever(projectsDataService.getCurrentProject()).thenReturn(secondProject)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val scenario =
            launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(null, form.dbId))
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.wrong_project_selected_for_form)
        )
    }

    @Test
    fun `When uri is null then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getBlankFormIntent(project.uuid, form.dbId).apply {
                data = null
            }
        )
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.unrecognized_uri)
        )
    }

    @Test
    fun `When uri is invalid then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getBlankFormIntent(project.uuid, form.dbId).apply {
                data = Uri.parse("blah")
            }
        )
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.unrecognized_uri)
        )
    }

    @Test
    fun `When uri represents a blank form that does not exist then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val scenario =
            launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(project.uuid, 1))
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(scenario, context.getString(org.odk.collect.strings.R.string.bad_uri))
    }

    @Test
    fun `When uri represents a blank form with non existing form file then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        File(form.formFilePath).delete()
        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getBlankFormIntent(
                project.uuid,
                form.dbId
            )
        )
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(scenario, context.getString(org.odk.collect.strings.R.string.bad_uri))
    }

    @Test
    fun `When uri represents a saved form that does not exist then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val scenario =
            launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, 1))
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(scenario, context.getString(org.odk.collect.strings.R.string.bad_uri))
    }

    @Test
    fun `When attempting to edit a form with non existing instance file then display alert dialog and remove the instance from the database`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_SUBMISSION_FAILED)
                .build()
        )

        File(instance.instanceFilePath).delete()
        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getSavedIntent(
                project.uuid,
                instance.dbId
            )
        )
        fakeScheduler.flush()
        fakeScheduler.flush()

        assertThat(instancesRepository.get(instance.dbId), equalTo(null))
        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.instance_deleted_message)
        )
    }

    @Test
    fun `When attempting to edit a form with zero form definitions then display alert dialog with formId if version does not exist`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("20")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getSavedIntent(
                project.uuid,
                instance.dbId
            )
        )
        fakeScheduler.flush()

        val expectedMessage = context.getString(
            org.odk.collect.strings.R.string.parent_form_not_present,
            instance.formId
        )

        assertErrorDialogAndClickCancelButton(scenario, expectedMessage)
    }

    @Test
    fun `When attempting to edit a form with zero form definitions then display alert dialog with formId and version if both exist`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getSavedIntent(
                project.uuid,
                instance.dbId
            )
        )
        fakeScheduler.flush()

        val expectedMessage = context.getString(
            org.odk.collect.strings.R.string.parent_form_not_present,
            "${instance.formId}\n${
                context.getString(org.odk.collect.strings.R.string.version)
            } ${instance.formVersion}"
        )

        assertErrorDialogAndClickCancelButton(scenario, expectedMessage)
    }

    @Test
    fun `When attempting to edit a form with multiple form definitions then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath,
                FormUtils.createXFormBody("1", "1", "Form 1")
            ).build()
        )
        formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath,
                FormUtils.createXFormBody("1", "1", "Form 2")
            ).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getSavedIntent(
                project.uuid,
                instance.dbId
            )
        )
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.survey_multiple_forms_error)
        )
    }

    @Test
    fun `When attempting to edit a form with only one non-deleted form definitions then start form`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form1 = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath,
                FormUtils.createXFormBody("1", "1", "Form 1")
            ).build()
        )
        val form2 = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath,
                FormUtils.createXFormBody("1", "1", "Form 2")
            ).build()
        )

        formsRepository.softDelete(form1.dbId)

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(
            getSavedIntent(
                project.uuid,
                instance.dbId
            )
        )
        fakeScheduler.flush()

        assertStartSavedFormIntent(project.uuid, instance.dbId, true)
    }

    @Test
    fun `When attempting to edit an encrypted form then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath,
                FormUtils.createXFormBody("1", "1", "Form 1")
            ).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .canEditWhenComplete(false)
                .build()
        )

        val scenario = launcherRule.launchForResult<FormUriActivity>(
            getSavedIntent(
                project.uuid,
                instance.dbId
            )
        )
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.encrypted_form)
        )
    }

    @Test
    fun `When attempting to edit an incomplete form with disabled editing then start form for view only`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        settingsProvider.getProtectedSettings().save(ProtectedProjectKeys.KEY_EDIT_SAVED, false)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertStartSavedFormIntent(project.uuid, instance.dbId, false)
    }

    @Test
    fun `When attempting to start a new form then there should be no form mode passed with the intent even if editing saved forms is disabled`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        settingsProvider.getProtectedSettings().save(ProtectedProjectKeys.KEY_EDIT_SAVED, false)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))
        fakeScheduler.flush()

        assertStartBlankFormIntent(project.uuid, form.dbId)
    }

    @Test
    fun `When attempting to edit a finalized form then start form for view only`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_COMPLETE)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertStartSavedFormIntent(project.uuid, instance.dbId, false)
    }

    @Test
    fun `When attempting to edit a submitted form then start form for view only`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_SUBMITTED)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertStartSavedFormIntent(project.uuid, instance.dbId, false)
    }

    @Test
    fun `When attempting to edit a form that failed to submit then start form for view only`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_SUBMISSION_FAILED)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertStartSavedFormIntent(project.uuid, instance.dbId, false)
    }

    @Test
    fun `Form filling should not be started again after recreating the activity`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val scenario =
            launcherRule.launch<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))
        fakeScheduler.flush()
        scenario.recreate()
        fakeScheduler.flush()

        Intents.intended(hasComponent(FormFillingActivity::class.java.name), Intents.times(1))
    }

    @Test
    fun `Form filling should not be started again after recreating the activity before starting`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val scenario =
            launcherRule.launch<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))
        scenario.recreate()
        fakeScheduler.flush()

        Intents.intended(hasComponent(FormFillingActivity::class.java.name), Intents.times(1))
    }

    @Test
    fun `When there is project id specified in uri that represents a blank form and it matches current project id then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        launcherRule.launch<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))
        fakeScheduler.flush()

        assertStartBlankFormIntent(project.uuid, form.dbId)
    }

    @Test
    fun `When there is project id specified in uri that represents an incomplete form and it matches current project id then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        launcherRule.launch<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertStartSavedFormIntent(project.uuid, instance.dbId, true)
    }

    @Test
    fun `When there is project id specified in uri that represents an invalid form and it matches current project id then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INVALID)
                .build()
        )

        launcherRule.launch<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertStartSavedFormIntent(project.uuid, instance.dbId, true)
    }

    @Test
    fun `When there is no project id specified in uri that represents a blank form and first available project id matches current project id then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        launcherRule.launch<FormUriActivity>(getBlankFormIntent(null, form.dbId))
        fakeScheduler.flush()

        assertStartBlankFormIntent(null, form.dbId)
    }

    @Test
    fun `When there is no project id specified in uri that represents a saved form and first available project id matches current project id then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        launcherRule.launch<FormUriActivity>(getSavedIntent(null, instance.dbId))
        fakeScheduler.flush()

        assertStartSavedFormIntent(null, instance.dbId, true)
    }

    @Test
    fun `The activity should be finished after clicking the back button when an error dialog is displayed`() {
        val scenario = launcherRule.launchForResult(FormUriActivity::class.java)
        fakeScheduler.flush()

        assertErrorDialogAndClickBackButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.app_not_configured)
        )
    }

    @Test
    fun `If there is a savepoint, display a recovery dialog before starting a blank form`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )
        val savepointFile = TempFiles.createTempFile()
        val savepoint = Savepoint(form.dbId, null, savepointFile.absolutePath, TempFiles.createTempFile().absolutePath)
        savepointsRepository.save(savepoint)

        launcherRule.launch<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))
        fakeScheduler.flush()

        assertSavepointRecoveryDialog(savepointFile)
    }

    @Test
    fun `If there is a savepoint, display a recovery dialog before starting a saved form`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )
        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        val savepointFile = TempFiles.createTempFile()
        val savepoint = Savepoint(form.dbId, instance.dbId, savepointFile.absolutePath, TempFiles.createTempFile().absolutePath)
        savepointsRepository.save(savepoint)

        launcherRule.launch<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertSavepointRecoveryDialog(savepointFile)
    }

    @Test
    fun `If there is a savepoint for older version of the blank form, display a recovery dialog and start the old version of the blank form if a user accepts`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val formV1 = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val formV2 = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "2",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val savepointFile = TempFiles.createTempFile()
        val savepoint = Savepoint(formV1.dbId, null, savepointFile.absolutePath, TempFiles.createTempFile().absolutePath)
        savepointsRepository.save(savepoint)

        launcherRule.launch<FormUriActivity>(getBlankFormIntent(project.uuid, formV2.dbId))
        fakeScheduler.flush()

        assertSavepointRecoveryDialog(savepointFile)
        onView(withText(org.odk.collect.strings.R.string.recover)).perform(click())
        assertStartBlankFormIntent(project.uuid, formV1.dbId)
    }

    @Test
    fun `If there is a savepoint for older version of the blank form, display a recovery dialog and start the new version of the blank form if a user declines`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val formV1 = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val formV2 = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "2",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        val savepointFile = TempFiles.createTempFile()
        val savepoint = Savepoint(formV1.dbId, null, savepointFile.absolutePath, TempFiles.createTempFile().absolutePath)
        savepointsRepository.save(savepoint)

        launcherRule.launch<FormUriActivity>(getBlankFormIntent(project.uuid, formV2.dbId))
        fakeScheduler.flush()

        assertSavepointRecoveryDialog(savepointFile)
        onView(withText(org.odk.collect.strings.R.string.do_not_recover)).perform(click())
        fakeScheduler.flush()
        assertStartBlankFormIntent(project.uuid, formV2.dbId)
    }

    @Test
    fun `An existing savepoint for a blank form should be removed when a user declines`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )
        val savepointFile = TempFiles.createTempFile()
        val instanceDirFile = TempFiles.createTempDir()
        val instanceFile = TempFiles.createTempFile(instanceDirFile)
        val savepoint = Savepoint(form.dbId, null, savepointFile.absolutePath, instanceFile.absolutePath)
        savepointsRepository.save(savepoint)

        launcherRule.launch<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))
        fakeScheduler.flush()

        assertSavepointRecoveryDialog(savepointFile)
        onView(withText(org.odk.collect.strings.R.string.do_not_recover)).perform(click())
        fakeScheduler.flush()
        assertThat(savepointsRepository.getAll().isEmpty(), equalTo(true))
        assertThat(instanceDirFile.exists(), equalTo(false))
        assertThat(instanceFile.exists(), equalTo(false))
    }

    @Test
    fun `An existing savepoint for a saved form should be removed when a user declines`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )
        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        val savepointFile = TempFiles.createTempFile()
        val instanceDirFile = TempFiles.createTempDir()
        val instanceFile = TempFiles.createTempFile(instanceDirFile)
        val savepoint = Savepoint(form.dbId, instance.dbId, savepointFile.absolutePath, instanceFile.absolutePath)
        savepointsRepository.save(savepoint)

        launcherRule.launch<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertSavepointRecoveryDialog(savepointFile)
        onView(withText(org.odk.collect.strings.R.string.do_not_recover)).perform(click())
        fakeScheduler.flush()
        assertThat(savepointsRepository.getAll().isEmpty(), equalTo(true))
        assertThat(instanceDirFile.exists(), equalTo(true))
        assertThat(instanceFile.exists(), equalTo(true))
    }

    @Test
    fun `When attempting to start a new form that does not use entities and the forms database is locked then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath
            ).build()
        )

        changeLock.lock()
        launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))
        fakeScheduler.flush()

        assertStartBlankFormIntent(project.uuid, form.dbId)
    }

    @Test
    fun `When attempting to start a new form that uses entities and the forms database is locked then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        val form = formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath,
                usesEntities = true
            ).build()
        )

        changeLock.lock()
        val scenario = launcherRule.launchForResult<FormUriActivity>(getBlankFormIntent(project.uuid, form.dbId))
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.cannot_open_form_because_of_forms_update)
        )
    }

    @Test
    fun `When attempting to edit a form that does not use entities and the forms database is locked then start form filling`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm("1", "1", TempFiles.createTempDir().absolutePath).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        changeLock.lock()
        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertStartSavedFormIntent(project.uuid, instance.dbId, true)
    }

    @Test
    fun `When attempting to edit a form that uses entities and the forms database is locked then display alert dialog`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath,
                usesEntities = true
            ).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )

        changeLock.lock()
        val scenario = launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertErrorDialogAndClickCancelButton(
            scenario,
            context.getString(org.odk.collect.strings.R.string.cannot_open_form_because_of_forms_update)
        )
    }

    @Test
    fun `When attempting to view a non-editable form that uses entities and the forms database is locked then start form for view only`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath,
                usesEntities = true
            ).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_COMPLETE)
                .build()
        )

        changeLock.lock()
        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertStartSavedFormIntent(project.uuid, instance.dbId, false)
    }

    @Test
    fun `When attempting to view a non-editable form that uses entities then do not lock the forms database`() {
        val project = Project.Saved("123", "First project", "A", "#cccccc")
        projectsRepository.save(project)
        whenever(projectsDataService.getCurrentProject()).thenReturn(project)

        formsRepository.save(
            FormUtils.buildForm(
                "1",
                "1",
                TempFiles.createTempDir().absolutePath,
                usesEntities = true
            ).build()
        )

        val instance = instancesRepository.save(
            Instance.Builder()
                .formId("1")
                .formVersion("1")
                .instanceFilePath(TempFiles.createTempFile(TempFiles.createTempDir()).absolutePath)
                .status(Instance.STATUS_COMPLETE)
                .build()
        )

        launcherRule.launchForResult<FormUriActivity>(getSavedIntent(project.uuid, instance.dbId))
        fakeScheduler.flush()

        assertThat(changeLock.tryLock(), equalTo(true))
    }

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

    private fun assertErrorDialogAndClickCancelButton(scenario: ActivityScenario<FormUriActivity>, message: String) {
        onView(withText(org.odk.collect.strings.R.string.form_cannot_be_used)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(message)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withId(android.R.id.button1)).perform(click())

        assertThat(scenario.result.resultCode, `is`(Activity.RESULT_CANCELED))
    }

    private fun assertErrorDialogAndClickBackButton(scenario: ActivityScenario<FormUriActivity>, message: String) {
        onView(withText(message)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(isRoot()).perform(pressBack())

        scenario.onActivity {
            assertThat(scenario.result.resultCode, `is`(Activity.RESULT_CANCELED))
        }
    }

    private fun assertSavepointRecoveryDialog(savepointFile: File) {
        onView(withText(org.odk.collect.strings.R.string.savepoint_recovery_dialog_title)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(SimpleDateFormat(context.getString(org.odk.collect.strings.R.string.savepoint_recovery_dialog_message), Locale.getDefault()).format(savepointFile.lastModified()))).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(org.odk.collect.strings.R.string.recover)).inRoot(isDialog()).check(matches(isDisplayed()))
        onView(withText(org.odk.collect.strings.R.string.do_not_recover)).inRoot(isDialog()).check(matches(isDisplayed()))
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
            Intents.intended(
                hasExtra(
                    ApplicationConstants.BundleKeys.FORM_MODE,
                    ApplicationConstants.FormModes.VIEW_SENT
                )
            )
        }
        Intents.intended(hasExtra("KEY_1", "Text"))
    }
}
