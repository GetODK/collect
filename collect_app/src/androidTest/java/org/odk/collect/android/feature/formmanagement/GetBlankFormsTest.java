package org.odk.collect.android.feature.formmanagement;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.R;
import org.odk.collect.android.support.CollectTestRule;
import org.odk.collect.android.support.TestDependencies;
import org.odk.collect.android.support.TestRuleChain;
import org.odk.collect.android.support.pages.GetBlankFormPage;

@RunWith(AndroidJUnit4.class)
public class GetBlankFormsTest {

    public CollectTestRule rule = new CollectTestRule();

    final TestDependencies testDependencies = new TestDependencies();

    @Rule
    public RuleChain copyFormChain = TestRuleChain.chain(testDependencies)
            .around(rule);

    @Test
    public void whenThereIsAnAuthenticationErrorFetchingFormList_allowsUserToReenterCredentials() {
        testDependencies.server.setCredentials("Draymond", "Green");
        testDependencies.server.addForm("One Question", "one-question", "1", "one-question.xml");

        rule.startAtMainMenu()
                .setServer(testDependencies.server.getURL())
                .clickGetBlankFormWithAuthenticationError()
                .fillUsername("Draymond")
                .fillPassword("Green")
                .clickOK(new GetBlankFormPage())
                .assertText("One Question");
    }

    @Test
    public void whenThereIsAnErrorFetchingFormList_showsError() {
        testDependencies.server.alwaysReturnError();

        rule.startAtMainMenu()
                .setServer(testDependencies.server.getURL())
                .clickGetBlankFormWithError()
                .assertText(R.string.load_remote_form_error)
                .clickOK(new GetBlankFormPage());
    }

    @Test
    public void whenThereIsAnErrorFetchingForms_showsError() {
        testDependencies.server.addForm("One Question", "one-question", "1", "one-question.xml");
        testDependencies.server.errorOnFetchingForms();

        rule.startAtMainMenu()
                .setServer(testDependencies.server.getURL())
                .clickGetBlankForm()
                .clickGetSelected()
                .assertMessage("1 of 1 downloads failed!")
                .showDetails()
                .assertError("The server https://server.example.com returned status code 500. If you keep having this problem, report it to the person who asked you to collect data.")
                .navigateBack();
    }
}
