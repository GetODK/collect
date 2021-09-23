package org.odk.collect.android.feature.settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.R;
import org.odk.collect.android.RecordedIntentsRule;
import org.odk.collect.android.gdrive.sheets.DriveHelper;
import org.odk.collect.android.support.CollectTestRule;
import org.odk.collect.android.support.TestDependencies;
import org.odk.collect.android.support.TestRuleChain;
import org.odk.collect.android.support.pages.ProjectSettingsPage;
import org.odk.collect.android.support.pages.MainMenuPage;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@RunWith(AndroidJUnit4.class)
public class ServerSettingsTest {

    private final TestDependencies testDependencies = new TestDependencies();

    public final CollectTestRule rule = new CollectTestRule();

    @Rule
    public RuleChain copyFormChain = TestRuleChain.chain(testDependencies)
            .around(new RecordedIntentsRule())
            .around(rule);

    @Test
    public void whenUsingODKServer_canAddCredentialsForServer() {
        testDependencies.server.setCredentials("Joe", "netsky");
        testDependencies.server.addForm("One Question", "one-question", "1", "one-question.xml");

        new MainMenuPage().assertOnPage()
                .openProjectSettings()
                .clickGeneralSettings()
                .clickServerSettings()
                .clickOnURL()
                .inputText(testDependencies.server.getURL())
                .clickOKOnDialog()
                .assertText(testDependencies.server.getURL())
                .clickServerUsername()
                .inputText("Joe")
                .clickOKOnDialog()
                .assertText("Joe")
                .clickServerPassword()
                .inputText("netsky")
                .clickOKOnDialog()
                .assertText("********")
                .pressBack(new ProjectSettingsPage())
                .pressBack(new MainMenuPage())

                .clickGetBlankForm()
                .clickGetSelected()
                .assertMessage("All downloads succeeded!")
                .clickOK();
    }

    /**
     * This test could definitely be extended to cover form download/submit with the creation
     * of a stub
     * {@link DriveHelper} and
     * {@link org.odk.collect.android.gdrive.GoogleAccountsManager}
     */
    @Test
    public void selectingGoogleAccount_showsGoogleAccountSettings() {
        new MainMenuPage().assertOnPage()
                .openProjectSettings()
                .clickGeneralSettings()
                .clickServerSettings()
                .clickOnServerType()
                .clickOnString(R.string.server_platform_google_sheets)
                .assertText(R.string.selected_google_account_text)
                .assertText(R.string.google_sheets_url);
    }

    @Test
    public void selectingGoogleAccount_disablesAutomaticUpdates() {
        MainMenuPage mainMenu = new MainMenuPage().assertOnPage()
                .enablePreviouslyDownloadedOnlyUpdates();
        assertThat(testDependencies.scheduler.getDeferredTasks().size(), is(1));

        testDependencies.googleAccountPicker.setDeviceAccount("steph@curry.basket");
        mainMenu.setGoogleAccount("steph@curry.basket");
        assertThat(testDependencies.scheduler.getDeferredTasks().size(), is(0));
    }
}
