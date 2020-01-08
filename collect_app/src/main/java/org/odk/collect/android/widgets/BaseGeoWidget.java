package org.odk.collect.android.widgets;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.odk.collect.android.R;
import org.odk.collect.android.formentry.questions.QuestionDetails;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.widgets.interfaces.GeoWidget;

import static org.odk.collect.android.utilities.ViewUtils.dpFromPx;
import static org.odk.collect.android.widgets.StringWidget.FIELD_HORIZONTAL_MARGIN_MODIFIER;

public abstract class BaseGeoWidget extends QuestionWidget implements GeoWidget {
    public Button startGeoButton;
    public TextView answerDisplay;
    protected boolean readOnly;

    public BaseGeoWidget(Context context, QuestionDetails questionDetails) {
        super(context, questionDetails);
        answerDisplay = getCenteredAnswerTextView();
        startGeoButton = getSimpleButton(getDefaultButtonLabel());
        readOnly = questionDetails.getPrompt().isReadOnly();
        setUpLayout(context, questionDetails.getPrompt().getAnswerText());
    }

    @Override
    public void clearAnswer() {
        answerDisplay.setText(null);
        updateButtonLabelsAndVisibility(false);
        widgetValueChanged();
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        startGeoButton.setOnLongClickListener(l);
        answerDisplay.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        startGeoButton.cancelLongPress();
        answerDisplay.cancelLongPress();
    }

    @Override
    public void setBinaryData(Object answer) {
        String answerText = answer.toString();
        answerDisplay.setText(getAnswerToDisplay(answerText));
        updateButtonLabelsAndVisibility(!answerText.isEmpty());
        widgetValueChanged();
    }

    public void onButtonClick(int buttonId) {
        getPermissionUtils().requestLocationPermissions((Activity) getContext(), new PermissionListener() {
            @Override
            public void granted() {
                waitForData();
                startGeoActivity();
            }

            @Override
            public void denied() {
            }
        });
    }

    protected void setUpLayout(Context context, String answerText) {
        LinearLayout answerLayout = new LinearLayout(getContext());
        answerLayout.setOrientation(LinearLayout.VERTICAL);
        answerLayout.addView(startGeoButton);
        answerLayout.addView(answerDisplay);

        Resources resources = context.getResources();
        int marginStandard = dpFromPx(context, resources.getDimensionPixelSize(R.dimen.margin_standard));
        int margin = marginStandard - FIELD_HORIZONTAL_MARGIN_MODIFIER;
        addAnswerView(answerLayout, margin);

        boolean dataAvailable = false;
        if (answerText != null && !answerText.isEmpty()) {
            dataAvailable = true;
            setBinaryData(answerText);
        }

        updateButtonLabelsAndVisibility(dataAvailable);
    }
}
