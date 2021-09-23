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

import org.javarosa.core.model.data.DecimalData;
import org.javarosa.core.model.data.IAnswerData;
import org.odk.collect.android.R;
import org.odk.collect.android.externaldata.ExternalAppsUtils;
import org.odk.collect.android.formentry.questions.QuestionDetails;
import org.odk.collect.android.widgets.utilities.StringWidgetUtils;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;

import timber.log.Timber;

import static org.odk.collect.android.utilities.ApplicationConstants.RequestCodes;

/**
 * Launch an external app to supply a decimal value. If the app
 * does not launch, enable the text area for regular data entry.
 * <p>
 * See {@link org.odk.collect.android.widgets.ExStringWidget} for usage.
 */
@SuppressLint("ViewConstructor")
public class ExDecimalWidget extends ExStringWidget {

    public ExDecimalWidget(Context context, QuestionDetails questionDetails, WaitingForDataRegistry waitingForDataRegistry) {
        super(context, questionDetails, waitingForDataRegistry);
        StringWidgetUtils.adjustEditTextAnswerToDecimalWidget(answerText, questionDetails.getPrompt());
    }

    @Override
    protected void fireActivity(Intent i) throws ActivityNotFoundException {
        i.putExtra(DATA_NAME, StringWidgetUtils.getDoubleAnswerValueFromIAnswerData(getFormEntryPrompt().getAnswerValue()));
        try {
            ((Activity) getContext()).startActivityForResult(i, RequestCodes.EX_DECIMAL_CAPTURE);
        } catch (SecurityException e) {
            Timber.i(e);
            ToastUtils.showLongToast(getContext(), R.string.not_granted_permission);
        }
    }

    @Override
    public IAnswerData getAnswer() {
        return StringWidgetUtils.getDecimalData(answerText.getText().toString(), getFormEntryPrompt());
    }

    @Override
    public void setData(Object answer) {
        DecimalData decimalData = ExternalAppsUtils.asDecimalData(answer);
        answerText.setText(decimalData == null ? null : decimalData.getValue().toString());
        widgetValueChanged();
    }
}
