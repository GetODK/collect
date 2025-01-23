package org.odk.collect.android.widgets

import android.view.View
import android.view.View.OnLongClickListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.javarosa.core.model.FormIndex
import org.javarosa.core.model.data.StringData
import org.javarosa.form.api.FormEntryPrompt
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.verify
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.odk.collect.android.fakes.FakePermissionsProvider
import org.odk.collect.android.formentry.questions.QuestionDetails
import org.odk.collect.android.support.MockFormEntryPromptBuilder
import org.odk.collect.android.utilities.Appearances
import org.odk.collect.android.widgets.support.FakeWaitingForDataRegistry
import org.odk.collect.android.widgets.support.QuestionWidgetHelpers
import org.odk.collect.androidshared.system.CameraUtils
import org.odk.collect.strings.R.string
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowToast

@RunWith(AndroidJUnit4::class)
class BarcodeWidgetTest {
    private val waitingForDataRegistry = FakeWaitingForDataRegistry()
    private val permissionsProvider = FakePermissionsProvider().apply {
        setPermissionGranted(true)
    }
    private val widgetTestActivity = QuestionWidgetHelpers.widgetTestActivity()
    private val shadowActivity = Shadows.shadowOf(widgetTestActivity)
    private val cameraUtils = mock<CameraUtils>()
    private val listener = mock<OnLongClickListener>()
    private val formIndex = mock<FormIndex>()

    @Test
    fun `The button is hidden in read-only mode`() {
        assertThat(
            createWidget(QuestionWidgetHelpers.promptWithReadOnly()).binding.barcodeButton.visibility,
            equalTo(View.GONE)
        )
    }

    @Test
    fun `Display the 'Replace Barcode' button if answer is present`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(StringData("blah")))
        assertThat(
            widget.binding.barcodeButton.text.toString(),
            equalTo(widgetTestActivity.getString(string.replace_barcode))
        )
    }

    @Test
    fun `Display the answer if answer is present`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(StringData("blah")))
        assertThat(
            widget.binding.answerView.getAnswer(),
            equalTo("blah")
        )
    }

    @Test
    fun `#getAnswer returns null when there is no answer`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(null))
        assertThat(widget.answer, equalTo(null))
    }

    @Test
    fun `#getAnswer returns the answer when there is answer`() {
        val widget = createWidget(
            QuestionWidgetHelpers.promptWithAnswer(
                StringData("blah")
            )
        )
        assertThat(widget.answer!!.displayText, equalTo("blah"))
    }

    @Test
    fun `#clearAnswer removes answer and updates button title`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(StringData("blah")))
        widget.clearAnswer()

        assertThat(
            widget.binding.answerView.getAnswer(),
            equalTo("")
        )
        assertThat(
            widget.binding.barcodeButton.text.toString(),
            equalTo(widgetTestActivity.getString(string.get_barcode))
        )
    }

    @Test
    fun `#clearAnswer calls #valueChangeListener`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(StringData("blah")))
        val valueChangedListener = QuestionWidgetHelpers.mockValueChangedListener(widget)
        widget.clearAnswer()

        verify(valueChangedListener).widgetValueChanged(widget)
    }

    @Test
    fun `#setData displays sanitized answer`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(null))
        widget.setData("\ud800blah\b")
        assertThat(
            widget.binding.answerView.getAnswer(),
            equalTo("blah")
        )
    }

    @Test
    fun `#setData updates the button title`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(null))
        widget.setData("\ud800blah\b")
        assertThat(
            widget.binding.barcodeButton.text,
            equalTo(widgetTestActivity.getString(string.replace_barcode))
        )
    }

    @Test
    fun `#setData call #valueChangeListener`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(null))
        val valueChangedListener = QuestionWidgetHelpers.mockValueChangedListener(widget)
        widget.setData("blah")

        verify(valueChangedListener).widgetValueChanged(widget)
    }

    @Test
    fun `Long-pressing the button and the answer triggers #onLongClickListener`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(null))
        widget.setOnLongClickListener(listener)
        widget.binding.barcodeButton.performLongClick()
        widget.binding.answerView.performLongClick()

        verify(listener).onLongClick(widget.binding.barcodeButton)
        verify(listener).onLongClick(widget.binding.answerView)
    }

    @Test
    fun `pressing the button with permission not granted does not launch anything`() {
        val widget = createWidget(QuestionWidgetHelpers.promptWithAnswer(null))
        permissionsProvider.setPermissionGranted(false)
        widget.setPermissionsProvider(permissionsProvider)
        widget.binding.barcodeButton.performClick()

        assertThat(shadowActivity.nextStartedActivity, equalTo(null))
        assertThat(waitingForDataRegistry.waiting.isEmpty(), equalTo(true))
    }

    @Test
    fun `pressing the button with permission granted registers widget for data waiting`() {
        val prompt = QuestionWidgetHelpers.promptWithAnswer(null)
        whenever(prompt.index).thenReturn(formIndex)

        val widget = createWidget(prompt)
        widget.setPermissionsProvider(permissionsProvider)
        widget.binding.barcodeButton.performClick()

        assertThat(
            waitingForDataRegistry.waiting.contains(formIndex),
            equalTo(true)
        )
    }

    @Test
    fun `pressing the button when front camera should be used but it is not available displays a toast`() {
        whenever(cameraUtils.isFrontCameraAvailable(ArgumentMatchers.any())).thenReturn(false)
        val widget = createWidget(QuestionWidgetHelpers.promptWithAppearance(Appearances.FRONT))
        widget.setPermissionsProvider(permissionsProvider)
        widget.binding.barcodeButton.performClick()

        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(widgetTestActivity.getString(string.error_front_camera_unavailable))
        )
    }

    @Test
    fun `pressing the button when front camera should be used and it is available launches correct intent`() {
        whenever(cameraUtils.isFrontCameraAvailable(ArgumentMatchers.any())).thenReturn(true)
        val widget = createWidget(QuestionWidgetHelpers.promptWithAppearance(Appearances.FRONT))
        widget.setPermissionsProvider(permissionsProvider)
        widget.binding.barcodeButton.performClick()

        assertThat(
            shadowActivity.nextStartedActivity.getBooleanExtra(Appearances.FRONT, false),
            equalTo(true)
        )
    }

    @Test
    fun `The answer is not displayed with hidden mode`() {
        val prompt =
            MockFormEntryPromptBuilder(QuestionWidgetHelpers.promptWithAppearance(Appearances.HIDDEN_ANSWER))
                .withAnswer(StringData("original contents"))
                .build()

        val widget = createWidget(prompt)

        // Check initial value is not shown
        assertThat(
            widget.binding.answerView.visibility,
            equalTo(View.GONE)
        )
        assertThat(
            widget.binding.barcodeButton.text,
            equalTo(widgetTestActivity.getString(string.replace_barcode))
        )
        assertThat(widget.answer, equalTo(StringData("original contents")))

        // Check updates aren't shown
        widget.setData("updated contents")
        assertThat(
            widget.binding.answerView.visibility,
            equalTo(View.GONE)
        )
        assertThat(
            widget.binding.barcodeButton.text,
            equalTo(widgetTestActivity.getString(string.replace_barcode))
        )
        assertThat(
            widget.answer,
            equalTo(StringData("updated contents"))
        )
    }

    private fun createWidget(prompt: FormEntryPrompt?) = BarcodeWidget(
        widgetTestActivity,
        QuestionDetails(prompt),
        waitingForDataRegistry,
        cameraUtils
    )
}
