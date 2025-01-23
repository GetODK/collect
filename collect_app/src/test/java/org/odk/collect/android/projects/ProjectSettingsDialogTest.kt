package org.odk.collect.android.projects

import androidx.core.view.children
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.pressBack
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.Matchers.notNullValue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.odk.collect.android.activities.AboutActivity
import org.odk.collect.android.injection.config.AppDependencyModule
import org.odk.collect.android.mainmenu.CurrentProjectViewModel
import org.odk.collect.android.preferences.screens.ProjectPreferencesActivity
import org.odk.collect.android.support.CollectHelpers
import org.odk.collect.androidshared.ui.FragmentFactoryBuilder
import org.odk.collect.fragmentstest.FragmentScenarioLauncherRule
import org.odk.collect.projects.InMemProjectsRepository
import org.odk.collect.projects.Project
import org.odk.collect.projects.ProjectsRepository
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.shared.strings.UUIDGenerator
import org.odk.collect.testshared.RobolectricHelpers

@RunWith(AndroidJUnit4::class)
class ProjectSettingsDialogTest {

    private val currentProjectViewModel: CurrentProjectViewModel = mock {
        on { currentProject } doReturn MutableLiveData(
            Project.Saved(
                "x",
                "Project X",
                "X",
                "#ffffff"
            )
        )
    }

    private val projectsRepository = InMemProjectsRepository(UUIDGenerator())

    private val viewModelFactory = viewModelFactory {
        initializer {
            currentProjectViewModel
        }
    }

    @get:Rule
    val launcherRule = FragmentScenarioLauncherRule(
        defaultFactory = FragmentFactoryBuilder()
            .forClass(ProjectSettingsDialog::class) { ProjectSettingsDialog(viewModelFactory) }
            .build()
    )

    @Before
    fun setup() {
        CollectHelpers.overrideAppDependencyModule(object : AppDependencyModule() {
            override fun providesProjectsRepository(
                uuidGenerator: UUIDGenerator?,
                gson: Gson?,
                settingsProvider: SettingsProvider?
            ): ProjectsRepository {
                return projectsRepository
            }
        })
    }

    @Test
    fun `The dialog should be dismissed after clicking on the 'X' button`() {
        val scenario = launcherRule.launch(ProjectSettingsDialog::class.java)

        scenario.onFragment {
            assertThat(it.dialog!!.isShowing, `is`(true))
            it.binding.closeIcon.performClick()
            RobolectricHelpers.runLooper()
            assertThat(it.dialog, `is`(nullValue()))
        }
    }

    @Test
    fun `The dialog should be dismissed after clicking on a device back button`() {
        val scenario = launcherRule.launch(ProjectSettingsDialog::class.java)
        scenario.onFragment {
            assertThat(it.dialog!!.isShowing, `is`(true))
            onView(isRoot()).perform(pressBack())
            RobolectricHelpers.runLooper()
            assertThat(it.dialog, `is`(nullValue()))
        }
    }

    @Test
    fun `Project settings should be started after clicking on the 'Settings' button`() {
        val scenario = launcherRule.launch(ProjectSettingsDialog::class.java)
        scenario.onFragment {
            Intents.init()
            assertThat(it.dialog!!.isShowing, `is`(true))
            it.binding.generalSettingsButton.performClick()
            RobolectricHelpers.runLooper()
            assertThat(it.dialog, `is`(nullValue()))
            assertThat(
                Intents.getIntents()[0],
                hasComponent(ProjectPreferencesActivity::class.java.name)
            )
            Intents.release()
        }
    }

    @Test
    fun `About section should be started after clicking on the 'About' button`() {
        val scenario = launcherRule.launch(ProjectSettingsDialog::class.java)
        scenario.onFragment {
            Intents.init()
            assertThat(it.dialog!!.isShowing, `is`(true))
            it.binding.aboutButton.performClick()
            RobolectricHelpers.runLooper()
            assertThat(it.dialog, `is`(nullValue()))
            assertThat(Intents.getIntents()[0], hasComponent(AboutActivity::class.java.name))
            Intents.release()
        }
    }

    @Test
    fun `QrCodeProjectCreatorDialog should be displayed after clicking on the 'Add project' button`() {
        val scenario = launcherRule.launch(ProjectSettingsDialog::class.java)
        scenario.onFragment {
            it.binding.addProjectButton.performClick()
            assertThat(
                it.activity!!.supportFragmentManager.findFragmentByTag(
                    QrCodeProjectCreatorDialog::class.java.name
                ),
                `is`(notNullValue())
            )
        }
    }

    @Test
    fun `currentProjectViewModel should be notified when project switched`() {
        val projectY = projectsRepository.save(Project.New("Project Y", "Y", "#ffffff"))

        val scenario = launcherRule.launch(ProjectSettingsDialog::class.java)
        scenario.onFragment {
            it.binding.projectList.children.iterator().asSequence().first().performClick()
            verify(currentProjectViewModel).setCurrentProject(projectY)
        }
    }
}
