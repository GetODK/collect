package org.odk.collect.android.support.pages

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.odk.collect.android.R

class FormsDownloadResultPage : Page<FormsDownloadResultPage>() {

    override fun assertOnPage(): FormsDownloadResultPage {
        onView(withText(R.string.ok)).inRoot(RootMatchers.isDialog()).check(matches(isDisplayed()))
        return this
    }

    fun assertMessage(message: String): FormsDownloadResultPage {
        assertText(message)
        return this
    }

    fun clickOK(): MainMenuPage {
        clickOnId(android.R.id.button1)
        return MainMenuPage()
    }

    fun showDetails(): FormsDownloadErrorPage {
        onView(withText(getTranslatedString(R.string.show_details))).perform(click())
        return FormsDownloadErrorPage().assertOnPage()
    }
}
