package org.odk.collect.android.regression;

import static java.util.Collections.singletonList;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.support.pages.FormEntryPage;
import org.odk.collect.android.support.rules.CollectTestRule;
import org.odk.collect.android.support.rules.TestRuleChain;

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
}
