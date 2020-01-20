package org.odk.collect.android.support.pages;

import androidx.test.rule.ActivityTestRule;

import org.odk.collect.android.R;
import org.odk.collect.android.application.FeatureFlags;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

public class AddNewRepeatDialog extends Page<AddNewRepeatDialog> {

    private final String repeatName;

    AddNewRepeatDialog(String repeatName, ActivityTestRule rule) {
        super(rule);
        this.repeatName = repeatName;
    }

    @Override
    public AddNewRepeatDialog assertOnPage() {
        if (FeatureFlags.STATIC_ADD_REPEAT_LANGUAGE) {
            onView(withText(getTranslatedString(R.string.add_repeat_question, repeatName))).check(matches(isDisplayed()));
        } else {
            onView(withText(getTranslatedString(R.string.add_new_repeat_question, repeatName))).check(matches(isDisplayed()));
        }
        return this;
    }

    public FormEntryPage clickOnAddGroup(FormEntryPage destination) {
        clickOnString(R.string.add_repeat);
        return destination;
    }

    public FormEntryPage clickOnDoNotAddGroup(FormEntryPage destination) {
        clickOnString(R.string.add_repeat_no);
        return destination;
    }

}
