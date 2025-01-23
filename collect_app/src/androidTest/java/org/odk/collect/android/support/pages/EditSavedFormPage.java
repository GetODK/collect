/*
 * Copyright 2019 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.support.pages;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withTagValue;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.odk.collect.android.instancemanagement.InstanceExtKt.getInstanceIcon;

import android.widget.RelativeLayout;

import androidx.appcompat.widget.Toolbar;

import org.odk.collect.android.R;

public class EditSavedFormPage extends Page<EditSavedFormPage> {

    @Override
    public EditSavedFormPage assertOnPage() {
        assertText(org.odk.collect.strings.R.string.review_data);
        return this;
    }

    public EditSavedFormPage checkInstanceState(String instanceName, String desiredStatus) {
        int desiredImageId = getInstanceIcon(desiredStatus);

        onView(allOf(instanceOf(RelativeLayout.class),
                hasDescendant(withText(instanceName)),
                not(hasDescendant(instanceOf(Toolbar.class)))))
                .check(matches(hasDescendant(withTagValue(equalTo(desiredImageId)))));
        return this;
    }

    public OkDialog clickOnFormWithDialog(String instanceName) {
        scrollToAndClickOnForm(instanceName);
        return new OkDialog().assertOnPage();
    }

    public IdentifyUserPromptPage clickOnFormWithIdentityPrompt(String formName) {
        scrollToAndClickOnForm(formName);
        return new IdentifyUserPromptPage(formName).assertOnPage();
    }

    public FormHierarchyPage clickOnForm(String formName, String instanceName) {
        scrollToAndClickOnForm(instanceName);
        return new FormHierarchyPage(formName).assertOnPage();
    }

    public FormHierarchyPage clickOnForm(String formName) {
        scrollToAndClickOnForm(formName);
        return new FormHierarchyPage(formName).assertOnPage();
    }

    public AppClosedPage clickOnFormClosingApp(String formName) {
        scrollToAndClickOnForm(formName);
        return new AppClosedPage().assertOnPage();
    }

    private void scrollToAndClickOnForm(String formName) {
        onView(withText(formName)).perform(scrollTo(), click());
    }

    public EditSavedFormPage clickMenuFilter() {
        onView(withId(R.id.menu_filter)).perform(click());
        return this;
    }

    public EditSavedFormPage searchInBar(String query) {
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(replaceText(query));
        return this;
    }

    public BulkFinalizationConfirmationDialogPage clickFinalizeAll(int count) {
        this.clickOptionsIcon(org.odk.collect.strings.R.string.finalize_all_drafts)
                .clickOnString(org.odk.collect.strings.R.string.finalize_all_drafts);

        return new BulkFinalizationConfirmationDialogPage(count).assertOnPage();
    }
}
