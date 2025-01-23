package org.odk.collect.android.preferences.screens

import android.app.Application
import android.content.Context
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.`is`
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.odk.collect.android.application.Collect
import org.odk.collect.android.application.initialization.AnalyticsInitializer
import org.odk.collect.android.application.initialization.MapsInitializer
import org.odk.collect.android.injection.config.AppDependencyModule
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.android.support.CollectHelpers
import org.odk.collect.fragmentstest.FragmentScenarioLauncherRule
import org.odk.collect.projects.Project
import org.odk.collect.projects.ProjectsRepository
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.shared.strings.UUIDGenerator
import org.odk.collect.strings.localization.getLocalizedString

@RunWith(AndroidJUnit4::class)
class ProjectDisplayPreferencesFragmentTest {

    lateinit var projectsDataService: ProjectsDataService
    lateinit var projectsRepository: ProjectsRepository

    @get:Rule
    val launcherRule = FragmentScenarioLauncherRule()

    @Before
    fun setup() {
        projectsDataService = mock(ProjectsDataService::class.java)
        projectsRepository = mock(ProjectsRepository::class.java)

        `when`(projectsDataService.requireCurrentProject())
            .thenReturn(Project.Saved("123", "Project X", "X", "#cccccc"))

        CollectHelpers.overrideAppDependencyModule(object : AppDependencyModule() {
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

            override fun providesProjectsRepository(uuidGenerator: UUIDGenerator, gson: Gson, settingsProvider: SettingsProvider): ProjectsRepository {
                return projectsRepository
            }
        })
    }

    @Test
    fun `Project Name preference should be visible`() {
        val scenario = launcherRule.launch(ProjectDisplayPreferencesFragment::class.java)
        scenario.onFragment {
            assertThat(
                it.findPreference<EditTextPreference>(ProjectDisplayPreferencesFragment.PROJECT_NAME_KEY)!!.isVisible,
                `is`(true)
            )
        }
    }

    @Test
    fun `Project Name preference should have proper title`() {
        val scenario = launcherRule.launch(ProjectDisplayPreferencesFragment::class.java)
        scenario.onFragment {
            assertThat(
                it.findPreference<EditTextPreference>(ProjectDisplayPreferencesFragment.PROJECT_NAME_KEY)!!.title,
                `is`(
                    ApplicationProvider.getApplicationContext<Collect>().getLocalizedString(
                        org.odk.collect.strings.R.string.project_name
                    )
                )
            )
        }
    }

    @Test
    fun `Project Name preference should have proper summary`() {
        val scenario = launcherRule.launch(ProjectDisplayPreferencesFragment::class.java)
        scenario.onFragment {
            assertThat(
                it.findPreference<EditTextPreference>(ProjectDisplayPreferencesFragment.PROJECT_NAME_KEY)!!.summary,
                `is`("Project X")
            )
        }
    }

    @Test
    fun `Project Icon preference should be visible`() {
        val scenario = launcherRule.launch(ProjectDisplayPreferencesFragment::class.java)
        scenario.onFragment {
            assertThat(
                it.findPreference<EditTextPreference>(ProjectDisplayPreferencesFragment.PROJECT_ICON_KEY)!!.isVisible,
                `is`(true)
            )
        }
    }

    @Test
    fun `Project Icon preference should have proper title`() {
        val scenario = launcherRule.launch(ProjectDisplayPreferencesFragment::class.java)
        scenario.onFragment {
            assertThat(
                it.findPreference<EditTextPreference>(ProjectDisplayPreferencesFragment.PROJECT_ICON_KEY)!!.title,
                `is`(
                    ApplicationProvider.getApplicationContext<Collect>().getLocalizedString(
                        org.odk.collect.strings.R.string.project_icon
                    )
                )
            )
        }
    }

    @Test
    fun `Project Icon preference should have proper summary`() {
        val scenario = launcherRule.launch(ProjectDisplayPreferencesFragment::class.java)
        scenario.onFragment {
            assertThat(
                it.findPreference<EditTextPreference>(ProjectDisplayPreferencesFragment.PROJECT_ICON_KEY)!!.summary,
                `is`("X")
            )
        }
    }

    @Test
    fun `Project Color preference should be visible`() {
        val scenario = launcherRule.launch(ProjectDisplayPreferencesFragment::class.java)
        scenario.onFragment {
            assertThat(
                it.findPreference<Preference>(ProjectDisplayPreferencesFragment.PROJECT_COLOR_KEY)!!.isVisible,
                `is`(true)
            )
        }
    }

    @Test
    fun `Project Color preference should have proper title`() {
        val scenario = launcherRule.launch(ProjectDisplayPreferencesFragment::class.java)
        scenario.onFragment {
            assertThat(
                it.findPreference<Preference>(ProjectDisplayPreferencesFragment.PROJECT_COLOR_KEY)!!.title,
                `is`(
                    ApplicationProvider.getApplicationContext<Collect>().getLocalizedString(
                        org.odk.collect.strings.R.string.project_color
                    )
                )
            )
        }
    }

    @Test
    fun `Project Color preference should have proper summary`() {
        val scenario = launcherRule.launch(ProjectDisplayPreferencesFragment::class.java)
        scenario.onFragment {
            assertThat(
                it.findPreference<Preference>(ProjectDisplayPreferencesFragment.PROJECT_COLOR_KEY)!!.summary.toString(),
                `is`("■")
            )
        }
    }
}
