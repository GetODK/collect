package org.odk.collect.android.feature.projects

import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.odk.collect.android.R
import org.odk.collect.android.support.CollectTestRule
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.TestRuleChain
import org.odk.collect.android.support.pages.MainMenuPage
import org.odk.collect.android.support.pages.ProjectSettingsPage

class SwitchProjectTest {

    val rule = CollectTestRule()
    val testDependencies = TestDependencies()

    @get:Rule
    var chain: RuleChain = TestRuleChain.chain(testDependencies)
        .around(rule)

    @Test
    fun canSwitchActiveProjectToAnotherInList() {
        // Add project Turtle nesting
        rule.startAtMainMenu()
            .assertProjectIcon("D")
            .openProjectSettings()
            .clickAddProject()
            .switchToManualMode()
            .inputUrl("https://my-server.com")
            .inputUsername("John")
            .addProject()

            // Switch to Turtle nesting
            .openProjectSettings()
            .assertCurrentProject("my-server.com", "John / my-server.com")
            .assertInactiveProject("Demo project", "demo.getodk.org")
            .selectProject("Demo project")
            .checkIsToastWithMessageDisplayed(R.string.switched_project, "Demo project")
            .assertProjectIcon("D")
    }

    @Test
    fun switchingProject_switchesServerFormsAndInstances() {
        testDependencies.server.addForm("One Question", "one-question", "1", "one-question.xml")

        rule.startAtMainMenu()
            // Copy and fill form
            .copyForm("two-question.xml")
            .startBlankForm("Two Question")
            .swipeToNextQuestion("What is your age?")
            .swipeToEndScreen()
            .clickSaveAndExit()
            .clickEditSavedForm(1)
            .assertText("Two Question")
            .pressBack(MainMenuPage())

            // Create and switch to new project
            .assertProjectIcon("D")
            .openProjectSettings()
            .clickAddProject()
            .switchToManualMode()
            .inputUrl("https://my-server.com")
            .inputUsername("John")
            .addProject()

            // Set server and download form
            .setServer(testDependencies.server.url)
            .clickGetBlankForm()
            .clickGetSelected()
            .clickOK()

            // Fill form
            .startBlankForm("One Question")
            .swipeToEndScreen()
            .clickSaveAndExit()
            .clickEditSavedForm(1)
            .assertText("One Question")
            .pressBack(MainMenuPage())

            // Switch back to first project
            .openProjectSettings()
            .selectProject("Demo project")

            // Check server
            .openProjectSettings()
            .clickGeneralSettings()
            .clickServerSettings()
            .clickOnURL()
            .assertText("https://demo.getodk.org")
            .clickOKOnDialog()
            .pressBack(ProjectSettingsPage())
            .pressBack(MainMenuPage())

            // Check forms
            .clickFillBlankForm()
            .assertFormExists("Two Question")
            .pressBack(MainMenuPage())

            // Check instances
            .clickSendFinalizedForm(1)
            .assertText("Two Question")
    }
}
