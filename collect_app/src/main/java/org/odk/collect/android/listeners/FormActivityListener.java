package org.odk.collect.android.listeners;

import android.net.Uri;

import org.javarosa.core.model.FormIndex;
import org.odk.collect.android.widgets.QuestionWidget;

public interface FormActivityListener {

    void createConstraintToast(FormIndex index, int status);

    void createErrorDialog(String message, boolean shouldExit);

    void saveChosenFile(QuestionWidget questionWidget, Uri uri);

    void refreshCurrentView();

    boolean saveAnswersForCurrentScreen(boolean evaluateConstraints);
}
