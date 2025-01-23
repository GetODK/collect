package org.odk.collect.android.support.pages

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import org.odk.collect.strings.R
import org.odk.collect.strings.R.plurals
import org.odk.collect.strings.localization.getLocalizedQuantityString

class BulkFinalizationConfirmationDialogPage(private val count: Int) : Page<BulkFinalizationConfirmationDialogPage>() {
    override fun assertOnPage(): BulkFinalizationConfirmationDialogPage {
        val title = ApplicationProvider.getApplicationContext<Application>()
            .getLocalizedQuantityString(plurals.bulk_finalize_confirmation, count, count)

        onView(withText(title)).inRoot(isDialog()).check(matches(isDisplayed()))
        return this
    }

    fun clickFinalize(): EditSavedFormPage {
        return this.clickOnTextInDialog(R.string.finalize, EditSavedFormPage())
    }

    fun clickCancel(): EditSavedFormPage {
        return this.clickOnTextInDialog(R.string.cancel, EditSavedFormPage())
    }
}
