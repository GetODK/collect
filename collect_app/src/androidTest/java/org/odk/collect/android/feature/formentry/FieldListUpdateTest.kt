package org.odk.collect.android.feature.formentry

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import org.hamcrest.Matchers
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.odk.collect.android.R
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.preferences.GuidanceHint
import org.odk.collect.android.storage.StoragePathProvider
import org.odk.collect.android.support.pages.FormEntryPage
import org.odk.collect.android.support.rules.FormEntryActivityTestRule
import org.odk.collect.android.support.rules.TestRuleChain.chain
import org.odk.collect.androidtest.RecordedIntentsRule
import org.odk.collect.settings.keys.ProjectKeys
import java.io.File
import java.io.FileOutputStream
import java.util.Random

class FieldListUpdateTest {
    private var rule: FormEntryActivityTestRule = FormEntryActivityTestRule()

    @get:Rule
    var copyFormChain: RuleChain = chain()
        .around(RecordedIntentsRule())
        .around(rule)

    @Test
    fun relevanceChangeAtEnd_ShouldToggleLastWidgetVisibility() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .assertTextDoesNotExist("Target1")
            .answerQuestion("Source1", "A")
            .assertQuestion("Target1")
            .assertQuestionsOrder("Source1", "Target1")
            .answerQuestion("Source1", "")
            .assertTextDoesNotExist("Target1")
    }

    @Test
    fun relevanceChangeAtBeginning_ShouldToggleFirstWidgetVisibility() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Single relevance at beginning")
            .clickOnQuestion("Source2")
            .assertTextDoesNotExist("Target2")
            .answerQuestion("Source2", "A")
            .assertQuestion("Target2")
            .assertQuestionsOrder("Target2", "Source2")
            .answerQuestion("Source2", "")
            .assertTextDoesNotExist("Target2")
    }

    @Test
    fun relevanceChangeInMiddle_ShouldToggleMiddleWidgetVisibility() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Single relevance in middle")
            .clickOnQuestion("Source3")
            .assertTextDoesNotExist("Target3")
            .answerQuestion("Source3", "A")
            .assertQuestion("Target3")
            .assertQuestionsOrder("Source3", "Filler3")
            .assertQuestionsOrder("Target3", "Filler3")
            .answerQuestion("Source3", "")
            .assertTextDoesNotExist("Target3")
    }

    @Test
    fun longPress_ShouldClearAndUpdate() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Single relevance in middle")
            .clickOnQuestion("Source3")
            .answerQuestion(0, "")
            .assertTextDoesNotExist("Target3")
            .answerQuestion(0, "A")
            .assertText("Target3")
            .longPressOnQuestion("Source3")
            .removeResponse()
            .assertTextDoesNotExist("A")
            .assertTextDoesNotExist("Target3")
    }

    @Test
    fun changeInValueUsedInLabel_ShouldChangeLabelText() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Label change")
            .clickOnQuestion("Hello, , how are you today?")
            .assertQuestion("Hello, , how are you today?")
            .answerQuestion("What is your name?", "John")
            .assertQuestion("Hello, John, how are you today?")
            .longPressOnQuestion("What is your name?")
            .removeResponse()
            .assertQuestion("Hello, , how are you today?")
    }

    @Test
    fun changeInValueUsedInHint_ShouldChangeHintText() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Hint change")
            .clickOnQuestion("What is your name?")
            .assertText("Please don't use your calculator, !")
            .answerQuestion("What is your name?", "John")
            .assertText("Please don't use your calculator, John!")
            .longPressOnQuestion("What is your name?")
            .removeResponse()
            .assertText("Please don't use your calculator, !")
    }

    @Test
    fun changeInValueUsedInOtherField_ShouldChangeValue() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Value change")
            .clickOnQuestion("What is your name?")
            .assertAnswer("Name length", "0")
            .assertAnswer("First name letter", "")
            .answerQuestion("What is your name?", "John")
            .assertAnswer("Name length", "4")
            .assertAnswer("First name letter", "J")
            .longPressOnQuestion("What is your name?")
            .removeResponse()
            .assertAnswer("Name length", "0")
            .assertAnswer("First name letter", "")
    }

    @Test
    fun selectionChangeAtFirstCascadeLevel_ShouldUpdateNextLevels() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml", listOf("fruits.csv"))
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Cascading select")
            .clickOnQuestion("Level1") // No choices should be shown for levels 2 and 3 when no selection is made for level 1
            .assertTextsDoNotExist("A1", "B1", "C1", "A1A") // Selecting C for level 1 should only reveal options for C at level 2
            .clickOnText("C")
            .assertTextsDoNotExist("A1", "B1", "A1A")
            .assertText("C1") // Selecting A for level 1 should reveal options for A at level 2
            .clickOnText("A")
            .assertTextsDoNotExist("A1A", "B1", "C1")
            .assertText("A1") // Selecting A1 for level 2 should reveal options for A1 at level 3
            .clickOnText("A1")
            .assertText("A1A")
            .assertTextsDoNotExist("B1", "C1")
    }

    @Test
    fun clearingParentSelect_ShouldUpdateAllDependentLevels() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml", listOf("fruits.csv"))
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Cascading select")
            .clickOnQuestion("Level1")
            .clickOnText("A")
            .clickOnText("A1")
            .clickOnText("A1B")
            .longPressOnQuestion("Level1")
            .removeResponse()
            .assertTextsDoNotExist("A1", "A1B")
    }

    @Test
    fun selectionChangeAtOneCascadeLevelWithMinimalAppearance_ShouldUpdateNextLevels() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml", listOf("fruits.csv"))
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Cascading select minimal")
            .clickOnQuestion("Level1")
            .assertTextsDoNotExist("A1", "B1", "C1", "A1A") // No choices should be shown for levels 2 and 3 when no selection is made for level 1
            .openSelectMinimalDialog(0)
            .selectItem("C") // Selecting C for level 1 should only reveal options for C at level 2
            .assertTextsDoNotExist("A1", "B1")
            .openSelectMinimalDialog(1)
            .selectItem("C1")
            .assertTextDoesNotExist("A1A")
            .clickOnText("C")
            .clickOnText("A") // Selecting A for level 1 should reveal options for A at level 2
            .openSelectMinimalDialog(1)
            .assertText("A1")
            .assertTextsDoNotExist("A1A", "B1", "C1")
            .selectItem("A1") // Selecting A1 for level 2 should reveal options for A1 at level 3
            .openSelectMinimalDialog(2)
            .assertText("A1A")
            .assertTextsDoNotExist("B1A", "B1", "C1")
    }

    @Test
    fun questionsAppearingBeforeCurrentTextQuestion_ShouldNotChangeFocus() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Push off screen")
            .clickOnQuestion("Source9")
            .assertTextDoesNotExist("Target9-15")
            .answerQuestion("Source9", "A")
            .assertQuestion("Target9-15")
            .assertQuestionHasFocus("Source9")
    }

    @Test
    fun questionsAppearingBeforeCurrentBinaryQuestion_ShouldNotChangeFocus() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .apply {
                // FormFillingActivity expects an image at a fixed path so copy the app logo there
                val icon = BitmapFactory.decodeResource(
                    ApplicationProvider.getApplicationContext<Context>().resources,
                    R.drawable.notes
                )
                val tmpJpg = File(StoragePathProvider().getTmpImageFilePath())
                tmpJpg.createNewFile()
                val fos = FileOutputStream(tmpJpg)
                icon.compress(Bitmap.CompressFormat.JPEG, 90, fos)
                Intents.intending(Matchers.not(IntentMatchers.isInternal())).respondWith(
                    Instrumentation.ActivityResult(
                        Activity.RESULT_OK, null
                    )
                )
            }
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Push off screen binary")
            .clickOnQuestion("Source10")
            .assertTextDoesNotExist("Target10-15")
            .clickOnString(org.odk.collect.strings.R.string.capture_image)
            .assertText("Target10-15")
            .assertText(org.odk.collect.strings.R.string.capture_image)
    }

    @Test
    fun changeInValueUsedInGuidanceHint_ShouldChangeGuidanceHintText() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .apply {
                DaggerUtils.getComponent(ApplicationProvider.getApplicationContext<Application>())
                    .settingsProvider()
                    .getUnprotectedSettings()
                    .save(ProjectKeys.KEY_GUIDANCE_HINT, GuidanceHint.YES.toString())
            }
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Guidance hint")
            .clickOnQuestion("Source11")
            .assertTextDoesNotExist("10")
            .answerQuestion("Source11", "5")
            .assertQuestion("10")
    }

    @Test
    fun selectingADateForDateTime_ShouldChangeRelevanceOfRelatedField() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Date time")
            .clickOnQuestion("Source12")
            .assertTextDoesNotExist("Target12")
            .clickOnString(org.odk.collect.strings.R.string.select_date)
            .clickOKOnDialog()
            .assertQuestion("Target12")
    }

    @Test
    fun selectingARating_ShouldChangeRelevanceOfRelatedField() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Rating")
            .clickOnQuestion("Source13")
            .assertTextDoesNotExist("Target13")
            .setRating(3.0f)
            .assertQuestion("Target13")
            .longPressOnQuestion("Source13")
            .removeResponse()
            .assertTextDoesNotExist("Target13")
    }

    @Test
    fun manuallySelectingAValueForMissingExternalApp_ShouldTriggerUpdate() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("External app")
            .clickOnQuestion("Source14")
            .clickOnText("Launch")
            .assertTextDoesNotExist("Target14")
            .answerQuestion("Source14", Random().nextInt().toString())
            .assertQuestion("Target14")
    }

    @Test
    fun searchMinimalInFieldList() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Search in field-list")
            .clickOnQuestion("Source15")
            .openSelectMinimalDialog()
            .assertTexts("Mango", "Oranges", "Strawberries")
            .selectItem("Strawberries")
            .assertText("Target15")
            .assertSelectMinimalDialogAnswer("Strawberries")
    }

    @Test
    fun listOfQuestionsShouldNotBeScrolledToTheLastEditedQuestionAfterClickingOnAQuestion() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Long list of questions")
            .clickOnQuestion("Question1")
            .answerQuestion(0, "X")
            .clickOnQuestionField("Question20")
            .assertText("Question20")
    }

    @Test
    fun recordingAudio_ShouldChangeRelevanceOfRelatedField() {
        rule.setUpProjectAndCopyForm("fieldlist-updates.xml")
            .fillNewForm("fieldlist-updates.xml", "fieldlist-updates")
            .clickGoToArrow()
            .clickGoUpIcon()
            .clickOnGroup("Audio")
            .clickOnQuestion("Source16")
            .assertTextDoesNotExist("Target16")
            .clickOnString(org.odk.collect.strings.R.string.capture_audio)
            .clickOnContentDescription(org.odk.collect.strings.R.string.stop_recording)
            .assertText("Target16")
            .clickOnString(org.odk.collect.strings.R.string.delete_answer_file)
            .clickOnTextInDialog(
                org.odk.collect.strings.R.string.delete_answer_file,
                FormEntryPage("fieldlist-updates")
            )
            .assertTextDoesNotExist("Target16")
    }

    @Test
    fun changeInValueUsedToDetermineIfAQuestionIsRequired_ShouldUpdateTheRelatedRequiredQuestion() {
        rule.setUpProjectAndCopyForm("dynamic_required_question.xml")
            .fillNewForm("dynamic_required_question.xml", "dynamic_required_question")
            .assertQuestion("Target", false)
            .answerQuestion("Source", "blah")
            .assertQuestion("Target", true)
            .swipeToNextQuestionWithConstraintViolation(org.odk.collect.strings.R.string.required_answer_error)
            .longPressOnQuestion("Source")
            .removeResponse()
            .assertQuestion("Target", false)
            .assertTextDoesNotExist(org.odk.collect.strings.R.string.required_answer_error)
    }
}