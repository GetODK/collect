package org.odk.collect.android.feature.smoke;

import android.Manifest;
import android.webkit.MimeTypeMap;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.injection.config.AppDependencyModule;
import org.odk.collect.android.openrosa.OpenRosaHttpInterface;
import org.odk.collect.android.support.CollectTestRule;
import org.odk.collect.android.support.ResetStateRule;
import org.odk.collect.android.support.StubOpenRosaServer;
import org.odk.collect.utilities.UserAgentProvider;

@RunWith(AndroidJUnit4.class)
public class BadServerTest {

    public final StubOpenRosaServer server = new StubOpenRosaServer();

    public CollectTestRule rule = new CollectTestRule();

    @Rule
    public RuleChain copyFormChain = RuleChain
            .outerRule(GrantPermissionRule.grant(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.GET_ACCOUNTS
            ))
            .around(new ResetStateRule(new AppDependencyModule() {
                @Override
                public OpenRosaHttpInterface provideHttpInterface(MimeTypeMap mimeTypeMap, UserAgentProvider userAgentProvider) {
                    return server;
                }
            }))
            .around(rule);

    @Test
    // The hash from the form list wasn't used for a long time so some server implementations omitted it even though
    // it's required by the spec. Now we explicitly show an error.
    public void whenHashNotIncludedInFormList_showError() {
        server.removeHashInFormList();
        server.addForm("One Question", "one-question", "1", "one-question.xml");

        rule.startAtMainMenu()
                .setServer(server.getURL())
                .clickGetBlankForm()
                .clickGetSelected()
                .assertMessage("1 of 1 downloads failed!")
                .showDetails()
                .assertError("The form has no hash. If you keep having this problem, report it to the person who asked you to collect data.")
                .navigateBack();
    }
}
