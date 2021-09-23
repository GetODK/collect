package org.odk.collect.android.feature.formentry;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.odk.collect.android.support.matchers.RecyclerViewMatcher.withRecyclerView;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.FormHierarchyActivity;
import org.odk.collect.android.support.CollectTestRule;
import org.odk.collect.android.support.CopyFormRule;
import org.odk.collect.android.support.ResetStateRule;
import org.odk.collect.android.support.matchers.RecyclerViewMatcher;
import org.odk.collect.android.support.pages.AddNewRepeatDialog;
import org.odk.collect.android.support.pages.FormEntryPage;
import org.odk.collect.android.support.pages.FormHierarchyPage;
import org.odk.collect.android.support.pages.MainMenuPage;

import timber.log.Timber;

public class FormHierarchyTest {

    public CollectTestRule rule = new CollectTestRule();

    @Rule
    public RuleChain copyFormChain = RuleChain
            .outerRule(new ResetStateRule())
            .around(new CopyFormRule("formHierarchy1.xml", null))
            .around(new CopyFormRule("formHierarchy2.xml", null))
            .around(new CopyFormRule("formHierarchy3.xml", null))
            .around(new CopyFormRule("repeat_group_new.xml", null))
            .around(new CopyFormRule("empty_first_repeat.xml", null))
            .around(new CopyFormRule("empty_first_repeat_beans.xml", null))
            .around(rule);

    @Test
    //https://github.com/getodk/collect/issues/2871
    public void allRelevantQuestionsShouldBeVisibleInHierarchyView() {
        new MainMenuPage()
                .startBlankForm("formHierarchy1")
                .clickGoToArrow();

        onView(withRecyclerView(R.id.list)
                .atPositionOnView(0, R.id.primary_text))
                .check(matches(withText("what is your name?")));

        onView(withRecyclerView(R.id.list)
                .atPositionOnView(1, R.id.primary_text))
                .check(matches(withText("what is your age?")));
    }

    @Test
    //https://github.com/getodk/collect/issues/2944
    public void notRelevantRepeatGroupsShouldNotBeVisibleInHierarchy() {
        final FormHierarchyPage page = new MainMenuPage()
                .startBlankForm("formHierarchy2")
                .inputText("2")
                .clickGoToArrow();

        onView(withId(R.id.list)).check(matches(RecyclerViewMatcher.withListSize(3)));
        onView(withRecyclerView(R.id.list)
                .atPositionOnView(0, R.id.primary_text))
                .check(matches(withText("How many guests are in your party?")));
        onView(withRecyclerView(R.id.list)
                .atPositionOnView(0, R.id.secondary_text))
                .check(matches(withText("2")));
        onView(withRecyclerView(R.id.list)
                .atPositionOnView(1, R.id.primary_text))
                .check(matches(withText("Please provide details for each guest.")));
        onView(withRecyclerView(R.id.list)
                .atPositionOnView(2, R.id.primary_text))
                .check(matches(withText("Guest details")));
        onView(withRecyclerView(R.id.list)
                .atPositionOnView(2, R.id.secondary_text))
                .check(matches(withText("Repeatable Group")));

        page.clickOnText("Guest details");

        onView(withId(R.id.list)).check(matches(RecyclerViewMatcher.withListSize(2)));
        onView(withRecyclerView(R.id.list)
                .atPositionOnView(0, R.id.primary_text))
                .check(matches(withText("Guest details > 1")));
        onView(withRecyclerView(R.id.list)
                .atPositionOnView(1, R.id.primary_text))
                .check(matches(withText("Guest details > 2")));

        page.clickGoToStart()
                .inputText("1")
                .clickGoToArrow()
                .clickOnText("Guest details");

        onView(withId(R.id.list)).check(matches(RecyclerViewMatcher.withListSize(1)));
        onView(withRecyclerView(R.id.list)
                .atPositionOnView(0, R.id.primary_text))
                .check(matches(withText("Guest details > 1")));
    }

    @Test
    //https://github.com/getodk/collect/issues/2936
    public void repeatGroupsShouldBeVisibleAsAppropriate() {
        FormHierarchyPage page = new MainMenuPage()
                .startBlankForm("formHierarchy3")
                .assertQuestion("Intro")
                .swipeToNextQuestion("Text")
                .swipeToNextQuestion("Integer 1_1")
                .swipeToNextQuestion("Integer 1_2")
                .swipeToNextQuestion("Integer 2_1")
                .swipeToNextQuestion("Integer 2_2")
                .swipeToNextQuestionWithRepeatGroup("Repeat Group 1")
                .clickOnAdd(new FormEntryPage("formHierarchy3"))
                .swipeToNextQuestionWithRepeatGroup("Repeat Group 1_1")
                .clickOnAdd(new FormEntryPage("formHierarchy3"))
                .swipeToNextQuestionWithRepeatGroup("Repeat Group 1_1")
                .clickOnDoNotAdd(new AddNewRepeatDialog("Repeat Group 1"))
                .clickOnDoNotAdd(new FormEntryPage("formHierarchy3"))
                .clickGoToArrow();

        onView(withId(R.id.list)).check(matches(RecyclerViewMatcher.withListSize(3)));

        page.clickOnText("Group 1");

        onView(withId(R.id.list)).check(matches(RecyclerViewMatcher.withListSize(3)));

        page.assertTextDoesNotExist("Repeat Group 1");
    }

    @Test
    //https://github.com/getodk/collect/issues/2942
    public void deletingLastGroupShouldNotBreakHierarchy() {
        FormHierarchyPage page = new MainMenuPage()
                .startBlankForm("formHierarchy3")
                .swipeToNextQuestion()
                .swipeToNextQuestion()
                .swipeToNextQuestion()
                .swipeToNextQuestion()
                .swipeToNextQuestion()
                .swipeToNextQuestion()
                .clickOnString(R.string.add_repeat)
                .swipeToNextQuestion()
                .clickOnString(R.string.add_repeat)
                .swipeToNextQuestion()
                .clickOnString(R.string.add_repeat)
                .swipeToNextQuestion()
                .clickOnDoNotAddGroup()
                .clickOnDoNotAddGroup()
                .clickGoToArrow()
                .clickOnText("Repeat Group 1")
                .clickOnText("Repeat Group 1 > 1")
                .clickOnText("Repeat Group 1_1")
                .clickOnText("Repeat Group 1_1 > 2")
                .deleteGroup();

        onView(withId(R.id.list)).check(matches(RecyclerViewMatcher.withListSize(1)));

        page.assertText("Repeat Group 1_1 > 1");
    }

    @Test
    //https://github.com/getodk/collect/issues/3971
    public void deletingLastGroupAndAddingOneShouldNotBreakHierarchy() {
        new MainMenuPage()
                .startBlankFormWithRepeatGroup("RepeatGroupNew", "People")
                .clickOnAdd(new FormEntryPage("RepeatGroupNew"))
                .swipeToNextQuestion()
                .swipeToNextQuestion()
                .clickOnAddGroup()
                .swipeToNextQuestion()
                .swipeToNextQuestion()
                .clickOnAddGroup()
                .clickGoToArrow()
                .deleteGroup()
                .addGroup();
    }

    @Test
    //https://github.com/getodk/collect/issues/4570
    public void showsRepeatsWhenFirstIsEmpty() {
        FormHierarchyActivity.Scenarios4570 scenario = FormHierarchyActivity.SCENARIO_4570;
        boolean withBeans = scenario.testWithBeans();
        boolean willPass = scenario.willPassTest();
        Timber.i("willPass = " + willPass);
        new MainMenuPage()
                .startBlankForm(withBeans
                        ? "empty_first_repeat_beans"
                        : "empty_first_repeat")
                .clickGoToArrow()
                .assertText(withBeans
                        ? "Beans, Beet, Bell pepper, Cabbage"
                        : "Beet, Bell pepper, Cabbage")
                .clickOnText("Frequencies")
                .assertText(withBeans
                        ? new String[]{"1.\u200E Beans", "2.\u200E Beet", "3.\u200E Bell pepper", "5.\u200E Cabbage"}
                        : new String[]{"2.\u200E Beet", "3.\u200E Bell pepper", "5.\u200E Cabbage"});
    }
}
