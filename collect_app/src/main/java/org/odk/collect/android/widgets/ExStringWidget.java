/*
 * Copyright (C) 2012 University of Washington
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.Selection;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.javarosa.core.model.data.StringData;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.externaldata.ExternalAppsUtils;
import org.odk.collect.android.formentry.questions.QuestionDetails;
import org.odk.collect.android.formentry.questions.WidgetViewUtils;
import org.odk.collect.android.utilities.ActivityAvailability;
import org.odk.collect.android.utilities.ExternalAppIntentProvider;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.android.widgets.interfaces.ButtonClickListener;
import org.odk.collect.android.widgets.interfaces.WidgetDataReceiver;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;

import javax.inject.Inject;

import timber.log.Timber;

import static android.content.Intent.ACTION_SENDTO;
import static org.odk.collect.android.formentry.questions.WidgetViewUtils.createSimpleButton;
import static org.odk.collect.android.injection.DaggerUtils.getComponent;
import static org.odk.collect.android.utilities.ApplicationConstants.RequestCodes;

/**
 * <p>Launch an external app to supply a string value. If the app
 * does not launch, enable the text area for regular data entry.</p>
 * <p>
 * <p>The default button text is "Launch"
 * <p>
 * <p>You may override the button text and the error text that is
 * displayed when the app is missing by using jr:itext() values.
 * <p>
 * <p>To use this widget, define an appearance on the &lt;input/&gt;
 * tag that begins "ex:" and then contains the intent action to lauch.
 * <p>
 * <p>e.g.,
 * <p>
 * <pre>
 * &lt;input appearance="ex:change.uw.android.TEXTANSWER" ref="/form/passPhrase" &gt;
 * </pre>
 * <p>or, to customize the button text and error strings with itext:
 * <pre>
 *      ...
 *      &lt;bind nodeset="/form/passPhrase" type="string" /&gt;
 *      ...
 *      &lt;itext&gt;
 *        &lt;translation lang="English"&gt;
 *          &lt;text id="textAnswer"&gt;
 *            &lt;value form="short"&gt;Text question&lt;/value&gt;
 *            &lt;value form="long"&gt;Enter your pass phrase&lt;/value&gt;
 *            &lt;value form="buttonText"&gt;Get Pass Phrase&lt;/value&gt;
 *            &lt;value form="noAppErrorString"&gt;Pass Phrase Tool is not installed!
 *             Please proceed to manually enter pass phrase.&lt;/value&gt;
 *          &lt;/text&gt;
 *        &lt;/translation&gt;
 *      &lt;/itext&gt;
 *    ...
 *    &lt;input appearance="ex:change.uw.android.TEXTANSWER" ref="/form/passPhrase"&gt;
 *      &lt;label ref="jr:itext('textAnswer')"/&gt;
 *    &lt;/input&gt;
 * </pre>
 */
@SuppressLint("ViewConstructor")
public class ExStringWidget extends StringWidget implements WidgetDataReceiver, ButtonClickListener {
    protected static final String DATA_NAME = "value";
    private final WaitingForDataRegistry waitingForDataRegistry;

    private boolean hasExApp = true;
    public Button launchIntentButton;

    @Inject
    public ActivityAvailability activityAvailability;

    public ExStringWidget(Context context, QuestionDetails questionDetails, WaitingForDataRegistry waitingForDataRegistry) {
        super(context, questionDetails);
        this.waitingForDataRegistry = waitingForDataRegistry;
        getComponent(context).inject(this);
    }

    @Override
    protected void setUpLayout(Context context) {
        answerText.setText(getFormEntryPrompt().getAnswerText());
        launchIntentButton = createSimpleButton(getContext(), getFormEntryPrompt().isReadOnly(), getButtonText(), getAnswerFontSize(), this);

        LinearLayout answerLayout = new LinearLayout(getContext());
        answerLayout.setOrientation(LinearLayout.VERTICAL);
        answerLayout.addView(launchIntentButton);
        answerLayout.addView(answerText);
        addAnswerView(answerLayout, WidgetViewUtils.getStandardMargin(context));
    }

    private String getButtonText() {
        String v = getFormEntryPrompt().getSpecialFormQuestionText("buttonText");
        return v != null ? v : getContext().getString(R.string.launch_app);
    }

    protected void fireActivity(Intent i) throws ActivityNotFoundException {
        i.putExtra(DATA_NAME, getFormEntryPrompt().getAnswerText());
        try {
            ((Activity) getContext()).startActivityForResult(i, RequestCodes.EX_STRING_CAPTURE);
        } catch (SecurityException e) {
            Timber.i(e);
            ToastUtils.showLongToast(Collect.getInstance(), R.string.not_granted_permission);
        }
    }

    @Override
    public void setData(Object answer) {
        StringData stringData = ExternalAppsUtils.asStringData(answer);
        answerText.setText(stringData == null ? null : stringData.getValue().toString());
        widgetValueChanged();
    }

    @Override
    public void setFocus(Context context) {
        if (hasExApp) {
            softKeyboardController.hideSoftKeyboard(answerText);
            // focus on launch button
            launchIntentButton.requestFocus();
        } else {
            if (!getFormEntryPrompt().isReadOnly()) {
                softKeyboardController.showSoftKeyboard(answerText);
            /*
             * If you do a multi-question screen after a "add another group" dialog, this won't
             * automatically pop up. It's an Android issue.
             *
             * That is, if I have an edit text in an activity, and pop a dialog, and in that
             * dialog's button's OnClick() I call edittext.requestFocus() and
             * showSoftInput(edittext, 0), showSoftinput() returns false. However, if the
             * edittext
             * is focused before the dialog pops up, everything works fine. great.
             */
            } else {
                softKeyboardController.hideSoftKeyboard(answerText);
            }
        }
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        answerText.setOnLongClickListener(l);
        launchIntentButton.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        answerText.cancelLongPress();
        launchIntentButton.cancelLongPress();
    }

    @Override
    public void onButtonClick(int buttonId) {
        waitingForDataRegistry.waitForData(getFormEntryPrompt().getIndex());
        try {
            Intent intent = new ExternalAppIntentProvider().getIntentToRunExternalApp(getContext(), getFormEntryPrompt(), activityAvailability, Collect.getInstance().getPackageManager());
            // ACTION_SENDTO used for sending text messages or emails doesn't require any results
            if (ACTION_SENDTO.equals(intent.getAction())) {
                getContext().startActivity(intent);
            } else {
                fireActivity(intent);
            }
        } catch (Exception | Error e) {
            onException(e.getMessage());
        }
    }

    private void focusAnswer() {
        softKeyboardController.showSoftKeyboard(answerText);
    }

    private void onException(String toastText) {
        hasExApp = false;
        if (!getFormEntryPrompt().isReadOnly()) {
            answerText.setBackground((new EditText(getContext())).getBackground());
            answerText.setFocusable(true);
            answerText.setFocusableInTouchMode(true);
            answerText.setEnabled(true);
            answerText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    widgetValueChanged();
                }
            });
        }
        launchIntentButton.setEnabled(false);
        launchIntentButton.setFocusable(false);
        waitingForDataRegistry.cancelWaitingForData();

        Toast.makeText(getContext(),
                toastText, Toast.LENGTH_SHORT)
                .show();
        Timber.d(toastText);
        focusAnswer();
        Selection.setSelection(answerText.getText(), answerText.getText().toString().length());
    }
}
