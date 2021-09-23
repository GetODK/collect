/*
 * Copyright (C) 2013 Nafundi
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

import static org.odk.collect.android.utilities.ApplicationConstants.RequestCodes;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.util.TypedValue;
import android.view.View;
import android.widget.EditText;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.BearingActivity;
import org.odk.collect.android.databinding.BearingWidgetAnswerBinding;
import org.odk.collect.android.formentry.questions.QuestionDetails;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.android.widgets.interfaces.WidgetDataReceiver;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;

/**
 * BearingWidget is the widget that allows the user to get a compass heading.
 */
@SuppressLint("ViewConstructor")
public class BearingWidget extends QuestionWidget implements WidgetDataReceiver {
    BearingWidgetAnswerBinding binding;

    private final WaitingForDataRegistry waitingForDataRegistry;
    private final SensorManager sensorManager;

    public BearingWidget(Context context, QuestionDetails questionDetails, WaitingForDataRegistry waitingForDataRegistry, SensorManager sensorManager) {
        super(context, questionDetails);
        this.waitingForDataRegistry = waitingForDataRegistry;
        this.sensorManager = sensorManager;
    }

    @Override
    protected View onCreateAnswerView(Context context, FormEntryPrompt prompt, int answerFontSize) {
        binding = BearingWidgetAnswerBinding.inflate(((Activity) context).getLayoutInflater());

        if (prompt.isReadOnly()) {
            binding.bearingButton.setVisibility(GONE);
        } else {
            binding.bearingButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);
            binding.bearingButton.setOnClickListener(v -> onButtonClick());
        }
        binding.answerText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);
        binding.answerText.setBackground(null);

        String answerText = prompt.getAnswerText();
        if (answerText != null && !answerText.isEmpty()) {
            binding.bearingButton.setText(getContext().getString(R.string.replace_bearing));
            binding.answerText.setText(answerText);
        }

        return binding.getRoot();
    }

    @Override
    public void clearAnswer() {
        binding.answerText.setText(null);
        binding.bearingButton.setText(getContext().getString(R.string.get_bearing));
        widgetValueChanged();
    }

    @Override
    public IAnswerData getAnswer() {
        String answerText = binding.answerText.getText().toString();
        return answerText.isEmpty() ? null : new StringData(answerText);
    }

    @Override
    public void setData(Object answer) {
        binding.answerText.setText((String) answer);
        binding.bearingButton.setText(getContext().getString(R.string.replace_bearing));
        widgetValueChanged();
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        binding.bearingButton.setOnLongClickListener(l);
        binding.answerText.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        binding.bearingButton.cancelLongPress();
        binding.answerText.cancelLongPress();
    }

    private boolean areSensorsAvailable() {
        return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                && sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null;
    }

    private void onButtonClick() {
        if (areSensorsAvailable()) {
            Intent intent = new Intent(getContext(), BearingActivity.class);
            ((Activity) getContext()).startActivityForResult(intent, RequestCodes.BEARING_CAPTURE);

            waitingForDataRegistry.waitForData(getFormEntryPrompt().getIndex());
        } else {
            ToastUtils.showLongToast(getContext(), R.string.bearing_lack_of_sensors);

            binding.bearingButton.setEnabled(false);

            binding.answerText.setBackground(new EditText(getContext()).getBackground());
            binding.answerText.setFocusable(true);
            binding.answerText.setFocusableInTouchMode(true);
            binding.answerText.requestFocus();
        }
    }
}
