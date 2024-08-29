package org.odk.collect.android.regression;

import static junit.framework.TestCase.assertNotSame;
import static java.util.Collections.singletonList;

import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.FormFillingActivity;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.storage.StorageSubdirectory;
import org.odk.collect.android.support.ActivityHelpers;
import org.odk.collect.android.support.pages.FormEndPage;
import org.odk.collect.android.support.pages.FormEntryPage;
import org.odk.collect.android.support.pages.MainMenuPage;
import org.odk.collect.android.support.rules.CollectTestRule;
import org.odk.collect.android.support.rules.TestRuleChain;

import java.util.ArrayList;
import java.util.List;

//Issue NODK-244
@RunWith(AndroidJUnit4.class)
public class FillBlankFormTest {

    public CollectTestRule rule = new CollectTestRule();

    @Rule
    public RuleChain copyFormChain = TestRuleChain.chain()
            .around(rule);

    @Test
    public void answers_ShouldBeSuggestedInComplianceWithSelectedLetters() {
        //TestCase41
        rule.startAtMainMenu()
                .copyForm("formulaire_adherent.xml", singletonList("espece.csv"))
                .startBlankFormWithRepeatGroup("formulaire_adherent", "Ajouté une observation")
                .clickOnAdd(new FormEntryPage("formulaire_adherent"))
                .clickOnText("Plante")
                .inputText("Abi")
                .swipeToNextQuestion("Nom latin de l'espece", true)
                .assertText("Abies")
                .swipeToPreviousQuestion("Nom latin de l'espece - au moins 3 lettres", true)
                .inputText("Abr")
                .swipeToNextQuestion("Nom latin de l'espece", true)
                .assertText("Abrotanum alpestre");
    }

    @Test
    public void searchExpression_ShouldDisplayWhenItContainsOtherAppearanceName() {
        //TestCase26
        // This form doesn't define an instanceID and also doesn't request encryption so this case
        // would catch regressions for https://github.com/getodk/collect/issues/3340
        rule.startAtMainMenu()
                .copyForm("CSVerrorForm.xml", singletonList("TrapLists.csv"))
                .startBlankForm("CSV error Form")
                .clickOnText("Greg Pommen")
                .swipeToNextQuestion("* Select trap program:")
                .clickOnText("Mountain pine beetle")
                .swipeToNextQuestion("Pick Trap Name:")
                .assertText("2018-COE-MPB-001 @ Wellington")
                .swipeToPreviousQuestion("* Select trap program:")
                .clickOnText("Invasive alien species")
                .swipeToNextQuestion("Pick Trap Name:")
                .assertText("2018-COE-IAS-e-001 @ Coronation")
                .swipeToPreviousQuestion("* Select trap program:")
                .clickOnText("Longhorn beetles")
                .swipeToNextQuestion("Pick Trap Name:")
                .assertText("2018-COE-LGH-M-001 @ Acheson")
                .clickOnText("2018-COE-LGH-L-004 @ Acheson")
                .swipeToNextQuestion("* Were there specimens in the trap:")
                .clickOnText("No")
                .swipeToNextQuestion("Any other notes?")
                .swipeToEndScreen()
                .clickFinalize()
                .checkIsSnackbarWithMessageDisplayed(org.odk.collect.strings.R.string.form_saved);
    }

    @Test
    public void predicateWarning_ShouldBeAbleToFillTheForm() {
        //TestCase24
        rule.startAtMainMenu()
                .copyForm("predicate-warning.xml")
                .startBlankForm("predicate-warning")
                .clickOnText("Apple")
                .swipeToNextQuestion("Variety (absolute reference)")
                .clickOnText("Gala")
                .swipeToNextQuestion("Variety (relative reference)")
                .swipeToNextQuestion("Varieties (absolute reference)")
                .clickOnText("Gala")
                .clickOnText("Granny Smith")
                .swipeToNextQuestion("Varieties (relative reference)")
                .swipeToEndScreen()
                .clickFinalize();
    }

    @Test
    public void searchAppearance_ShouldDisplayWhenSearchAppearanceIsSpecified() {
        //TestCase25
        rule.startAtMainMenu()
                .copyForm("different-search-appearances.xml", singletonList("fruits.csv"))
                .startBlankForm("different-search-appearances")
                .clickOnText("Mango")
                .swipeToNextQuestion("The fruit mango pulled from csv")
                .assertText("The fruit mango pulled from csv")
                .swipeToNextQuestion("Static select with no appearance")
                .clickOnText("Wolf")
                .swipeToNextQuestion("Static select with search appearance")
                .inputText("w")
                .closeSoftKeyboard()
                .assertText("Wolf")
                .assertText("Warthog")
                .clickOnText("Wolf")
                .swipeToNextQuestion("Static select with autocomplete appearance")
                .inputText("r")
                .closeSoftKeyboard()
                .assertText("Warthog")
                .assertText("Raccoon")
                .assertText("Rabbit")
                .closeSoftKeyboard()
                .clickOnText("Rabbit")
                .swipeToNextQuestion("Select from a CSV using search() appearance/function and search appearance")
                .inputText("r")
                .closeSoftKeyboard()
                .assertText("Oranges")
                .assertText("Strawberries")
                .clickOnText("Oranges")
                .swipeToNextQuestion("Select from a CSV using search() appearance/function and autocomplete appearance")
                .inputText("n")
                .closeSoftKeyboard()
                .assertText("Mango")
                .assertText("Oranges")
                .clickOnText("Mango")
                .swipeToNextQuestion("Select from a CSV using search() appearance/function")
                .clickOnText("Mango")
                .clickOnText("Strawberries")
                .swipeToNextQuestion("Static select with no appearance")
                .clickOnText("Raccoon")
                .clickOnText("Rabbit")
                .swipeToNextQuestion("Static select with search appearance")
                .inputText("w")
                .closeSoftKeyboard()
                .assertText("Wolf")
                .assertText("Warthog")
                .clickOnText("Wolf")
                .clickOnText("Warthog")
                .swipeToNextQuestion("Static select with autocomplete appearance")
                .inputText("r")
                .closeSoftKeyboard()
                .assertText("Warthog")
                .assertText("Raccoon")
                .assertText("Rabbit")
                .clickOnText("Raccoon")
                .clickOnText("Rabbit")
                .swipeToNextQuestion("Select from a CSV using search() appearance/function and search appearance")
                .inputText("m")
                .closeSoftKeyboard()
                .assertText("Mango")
                .clickOnText("Mango")
                .swipeToNextQuestion("Select from a CSV using search() appearance/function and autocomplete appearance")
                .inputText("n")
                .closeSoftKeyboard()
                .closeSoftKeyboard()
                .assertText("Mango")
                .assertText("Oranges")
                .clickOnText("Mango")
                .clickOnText("Oranges")
                .swipeToEndScreen()
                .clickFinalize()
                .checkIsSnackbarWithMessageDisplayed(org.odk.collect.strings.R.string.form_saved);
    }

    @Test
    public void values_ShouldBeRandom() {
        rule.startAtMainMenu()
                .copyForm("random.xml")
                .copyForm("randomTest_broken.xml");

        //TestCase22
        List<String> firstQuestionAnswers = new ArrayList<>();
        List<String> secondQuestionAnswers = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            FormEntryPage formEntryPage = new MainMenuPage().startBlankForm("random");
            firstQuestionAnswers.add(getQuestionText());
            formEntryPage.swipeToNextQuestion("Your random once value:");
            secondQuestionAnswers.add(getQuestionText());
            formEntryPage.swipeToEndScreen().clickFinalize();
        }

        assertNotSame(firstQuestionAnswers.get(0), firstQuestionAnswers.get(1));
        assertNotSame(firstQuestionAnswers.get(0), firstQuestionAnswers.get(2));
        assertNotSame(firstQuestionAnswers.get(1), firstQuestionAnswers.get(2));

        assertNotSame(secondQuestionAnswers.get(0), secondQuestionAnswers.get(1));
        assertNotSame(secondQuestionAnswers.get(0), secondQuestionAnswers.get(2));
        assertNotSame(secondQuestionAnswers.get(1), secondQuestionAnswers.get(2));

        firstQuestionAnswers.clear();

        for (int i = 1; i <= 3; i++) {
            FormEntryPage formEntryPage = new MainMenuPage().startBlankForm("random test");
            formEntryPage.inputText("3");
            formEntryPage.swipeToNextQuestion("Your random number was");
            firstQuestionAnswers.add(getQuestionText());
            formEntryPage.swipeToEndScreen().clickFinalize();
        }

        assertNotSame(firstQuestionAnswers.get(0), firstQuestionAnswers.get(1));
        assertNotSame(firstQuestionAnswers.get(0), firstQuestionAnswers.get(2));
        assertNotSame(firstQuestionAnswers.get(1), firstQuestionAnswers.get(2));
    }

    @Test
    public void question_ShouldBeVisibleOnTheTopOfHierarchy() {
        //TestCase23
        rule.startAtMainMenu()
                .copyForm("manyQ.xml")
                .startBlankForm("manyQ")
                .swipeToNextQuestion("t2")
                .swipeToNextQuestion("n1")
                .clickGoToArrow()
                .assertText("n1")
                .assertTextDoesNotExist("t1")
                .assertTextDoesNotExist("t2");
    }

    @Test
    public void noDataLost_ShouldRememberAnswersForMultiSelectWidget() {
        //TestCase44
        rule.startAtMainMenu()
                .copyForm("test_multiselect_cleared.xml")
                .startBlankForm("test_multiselect_cleared")
                .clickOnText("a")
                .clickOnText("c")
                .swipeToNextQuestion("If you go back, the answers are deleted if you selected more than 1 option.")
                .swipeToNextQuestion("choice2", true)
                .clickOnText("b")
                .clickOnText("d")
                .swipeToEndScreen()
                .swipeToPreviousQuestion("choice2", true)
                .swipeToPreviousQuestion("If you go back, the answers are deleted if you selected more than 1 option.")
                .swipeToPreviousQuestion("choice1", true)
                .clickGoToArrow()
                .assertText("a, c")
                .assertText("b, d")
                .clickJumpEndButton()
                .clickGoToArrow();
    }

    @Test
    public void typeMismatchErrorMessage_shouldBeDisplayed() {
        //TestCase48
        rule.startAtMainMenu()
                .copyForm("validate.xml")
                .startBlankForm("validate")
                .longPressOnQuestion("year")
                .removeResponse()
                .swipeToNextQuestionWithError(false)
                .checkIsTextDisplayedOnDialog("The value \"-01-01\" can't be converted to a date.");
    }

    @Test
    public void answers_shouldBeAutoFilled() {
        //TestCase50
        rule.startAtMainMenu()
                .copyForm("event-odk-new-repeat.xml")
                .startBlankForm("Event: odk-new-repeat")
                .inputText("3")
                .swipeToNextQuestionWithRepeatGroup("null")
                .clickOnAdd(new FormEntryPage("Event: odk-new-repeat"))
                .assertText("1")
                .swipeToNextQuestion("B value")
                .assertText("5")
                .swipeToNextQuestionWithRepeatGroup("null")
                .clickOnAdd(new FormEntryPage("Event: odk-new-repeat"))
                .assertText("2")
                .swipeToNextQuestion("B value")
                .assertText("5")
                .swipeToNextQuestionWithRepeatGroup("null")
                .clickOnAdd(new FormEntryPage("Event: odk-new-repeat"))
                .assertText("3")
                .swipeToNextQuestion("B value")
                .assertText("5")
                .swipeToNextQuestionWithRepeatGroup("null")
                .clickOnAdd(new FormEntryPage("Event: odk-new-repeat"))
                .assertText("4")
                .swipeToNextQuestion("B value")
                .assertText("5")
                .swipeToNextQuestionWithRepeatGroup("null")
                .clickOnDoNotAdd(new FormEntryPage("Event: odk-new-repeat"))
                .inputText("2")
                .swipeToNextQuestion("A value")
                .assertText("1")
                .swipeToNextQuestion("A value")
                .assertText("2")
                .swipeToNextQuestion("C value")
                .swipeToNextQuestion("C value")
                .swipeToNextQuestionWithRepeatGroup("null")
                .clickOnDoNotAdd(new FormEndPage("Event: odk-new-repeat"))
                .clickFinalize();
    }

    @Test
    public void questions_shouldHavePrefilledValue() {
        //TestCase51
        rule.startAtMainMenu()
                .copyForm("multiple-events.xml")
                .startBlankForm("Space-separated event list")
                .assertText("cheese")
                .swipeToNextQuestion("First load group")
                .assertText("more cheese")
                .swipeToNextQuestion("My value")
                .assertText("5")
                .swipeToEndScreen()
                .clickFinalize();
    }

    @Test
    public void when_chooseAnswer_should_beVisibleInNextQuestion() {
        //TestCase52
        rule.startAtMainMenu()
                .copyForm("CalcTest.xml")
                .startBlankFormWithRepeatGroup("CalcTest", "Fishing gear type")
                .clickOnAdd(new FormEntryPage("CalcTest"))
                .clickOnText("Gillnet")
                .swipeToNextQuestion("7.2 What is the size of the mesh for the Gillnet ?", true)
                .swipeToPreviousQuestion("7.1 Select the type of fishing equipment used today to catch the fish present", true)
                .clickOnText("Seinenet")
                .swipeToNextQuestion("7.2 What is the size of the mesh for the Seinenet ?", true);
    }

    @Test
    public void missingFileMessage_shouldBeDisplayedIfExternalFileIsMissing() {
        String formsDirPath = new StoragePathProvider().getOdkDirPath(StorageSubdirectory.FORMS);

        //TestCase55
        rule.startAtMainMenu()
                .copyForm("search_and_select.xml")
                .startBlankForm("search_and_select")
                .assertText("File: " + formsDirPath + "/search_and_select-media/nombre.csv is missing.")
                .assertText("File: " + formsDirPath + "/search_and_select-media/nombre2.csv is missing.")
                .swipeToEndScreen()
                .clickFinalize()

                .copyForm("select_one_external.xml")
                .startBlankForm("cascading select test")
                .clickOnText("Texas")
                .swipeToNextQuestion("county")
                .assertText("File: " + formsDirPath + "/select_one_external-media/itemsets.csv is missing.")
                .swipeToNextQuestion("city")
                .assertText("File: " + formsDirPath + "/select_one_external-media/itemsets.csv is missing.")
                .swipeToEndScreen()
                .clickFinalize()

                .copyForm("fieldlist-updates_nocsv.xml")
                .startBlankForm("fieldlist-updates")
                .clickGoToArrow()
                .clickGoUpIcon()
                .clickOnElementInHierarchy(14)
                .clickOnQuestion("Source15")
                .assertText("File: " + formsDirPath + "/fieldlist-updates_nocsv-media/fruits.csv is missing.")
                .swipeToEndScreen()
                .clickFinalize();
    }

    private String getQuestionText() {
        FormFillingActivity formFillingActivity = (FormFillingActivity) ActivityHelpers.getActivity();
        FrameLayout questionContainer = formFillingActivity.findViewById(R.id.text_container);
        TextView questionView = (TextView) questionContainer.getChildAt(0);
        return questionView.getText().toString();
    }
}
