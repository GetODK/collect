package org.odk.collect.android.feature.formentry.audit

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.support.StorageUtils
import org.odk.collect.android.support.StorageUtils.getAuditLogForFirstInstance
import org.odk.collect.android.support.pages.FormEntryPage
import org.odk.collect.android.support.pages.FormHierarchyPage
import org.odk.collect.android.support.pages.IdentifyUserPromptPage
import org.odk.collect.android.support.pages.MainMenuPage
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class IdentifyUserTest {
    private val rule = CollectTestRule()

    @get:Rule
    val ruleChain: RuleChain = TestRuleChain
        .chain()
        .around(rule)

    @Test
    @Throws(IOException::class)
    fun openingForm_andThenEnteringIdentity_andThenFillingForm_logsUser() {
        rule.startAtMainMenu()
            .copyForm(IDENTIFY_USER_AUDIT_FORM)
            .clickFillBlankForm()
            .clickOnFormWithIdentityPrompt("Identify User")
            .enterIdentity("Lucius")
            .clickKeyboardEnter(FormEntryPage("Identify User"))
            .swipeToEndScreen()
            .clickFinalize()

        val auditLog = getAuditLogForFirstInstance()
        val formStartEvent = auditLog[0]
        assertThat(formStartEvent[0], equalTo("form start"))
        assertThat(formStartEvent[4], equalTo("Lucius"))
    }

    @Test
    @Throws(IOException::class)
    fun openingSavedForm_andThenEnteringIdentity_andThenFillingForm_logsUser() {
        rule.startAtMainMenu()
            .copyForm(IDENTIFY_USER_AUDIT_FORM)
            .clickFillBlankForm()
            .clickOnFormWithIdentityPrompt("Identify User")
            .enterIdentity("Lucius")
            .clickKeyboardEnter(FormEntryPage("Identify User"))
            .swipeToEndScreen()
            .clickSaveAsDraft()
            .clickDrafts()
            .clickOnFormWithIdentityPrompt("Identify User")
            .enterIdentity("Jack")
            .clickKeyboardEnter(FormHierarchyPage("Identify User"))
            .clickJumpEndButton()
            .clickFinalize()

        val auditLog = getAuditLogForFirstInstance()
        val formResumeEvent = auditLog[5]
        assertThat(formResumeEvent[0], equalTo("form resume"))
        assertThat(formResumeEvent[4], equalTo("Jack"))
    }

    @Test
    fun openingForm_andEnteringBlankIdentity_remainsOnIdentityPrompt() {
        rule.startAtMainMenu()
            .copyForm(IDENTIFY_USER_AUDIT_FORM)
            .clickFillBlankForm()
            .clickOnFormWithIdentityPrompt("Identify User")
            .enterIdentity("  ")
            .clickKeyboardEnterWithValidationError()
    }

    @Test
    fun openingForm_andPressingBack_returnsToMainMenuAndDoesNotLeaveAnEmptyInstanceDir() {
        rule.startAtMainMenu()
            .copyForm(IDENTIFY_USER_AUDIT_FORM)
            .clickFillBlankForm()
            .clickOnFormWithIdentityPrompt("Identify User")
            .closeSoftKeyboard()
            .pressBack(MainMenuPage())

        assertThat(File(StorageUtils.getInstancesDirPath()).listFiles().size, equalTo(0))
    }

    @Test
    fun openingForm_andRotating_remainsOnIdentityPrompt() {
        rule.startAtMainMenu()
            .copyForm(IDENTIFY_USER_AUDIT_FORM)
            .clickFillBlankForm()
            .clickOnFormWithIdentityPrompt("Identify User")
            .enterIdentity("Blah")
            .rotateToLandscape(IdentifyUserPromptPage("Identify User"))
            .assertText("Blah")
    }

    @Test
    fun minimizingAndReopeningApp_remainsOnIdentityPrompt() {
        rule.startAtMainMenu()
            .copyForm(IDENTIFY_USER_AUDIT_FORM)
            .clickFillBlankForm()
            .clickOnFormWithIdentityPrompt("Identify User")
            .enterIdentity("Blah")
            .minimizeAndReopenApp(IdentifyUserPromptPage("Identify User"))
            .assertText("Blah")
    }

    @Test
    fun openingForm_andPressingCloseCross_returnsToMainMenuAndDoesNotLeaveAnEmptyInstanceDir() {
        rule.startAtMainMenu()
            .copyForm(IDENTIFY_USER_AUDIT_FORM)
            .clickFillBlankForm()
            .clickOnFormWithIdentityPrompt("Identify User")
            .pressClose()

        assertThat(File(StorageUtils.getInstancesDirPath()).listFiles().size, equalTo(0))
    }

    @Test
    fun openFormWithIdentifyUserFalse_proceedsToForm() {
        rule.startAtMainMenu()
            .copyForm(IDENTIFY_USER_AUDIT_FALSE_FORM)
            .clickFillBlankForm()
            .clickOnForm("Identify User False")
            .swipeToEndScreen()
            .clickFinalize()
    }

    companion object {
        private const val IDENTIFY_USER_AUDIT_FORM = "identify-user-audit.xml"
        private const val IDENTIFY_USER_AUDIT_FALSE_FORM = "identify-user-audit-false.xml"
    }
}
