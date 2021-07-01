package org.odk.collect.android.utilities;

import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectOneData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.jetbrains.annotations.Nullable;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.exception.JavaRosaException;
import org.odk.collect.android.javarosawrapper.FormController;
import org.odk.collect.android.widgets.items.ItemsWidget;
import org.odk.collect.android.widgets.items.SelectOneMinimalWidget;
import org.odk.collect.android.widgets.items.SelectOneWidget;

import java.util.List;
import java.util.function.Supplier;

import timber.log.Timber;

public class SelectOneWidgetUtils {

    private SelectOneWidgetUtils() {

    }

    public static void checkFastExternalCascade(ItemsWidget caller) {
        //Exclude untested usage
        if (!(caller instanceof SelectOneWidget || caller instanceof SelectOneMinimalWidget)) {
            throw new IllegalArgumentException("This method is only tested for calls from " +
                    SelectOneWidget.class.getSimpleName() + " or " + SelectOneMinimalWidget.class.getSimpleName());
        }

        FormController fc = Collect.getInstance().getFormController();
        //Formality
        if (fc == null) {
            return;
        }
        //Abort in field list...
        else if (fc.indexIsInFieldList(fc.getFormIndex())) {
            return;
        }
        //...or in unit test
        else if(fc.getQuestionPrompt()==null){
            return;
        }

        //Mini method
        Supplier<String> getCheckName = () -> {
            String raw = fc
                    .getQuestionPrompt()
                    .getFormElement()
                    .getBind().getReference().toString();
            return raw.replaceAll(".+/([^/]+)$", "$1");
        };

        try {
            //Remember where we started
            FormIndex startIndex = fc.getFormIndex();

            //To search for in query string
            String checkName = getCheckName.get();

            //Loop until non-question
            while (true) {
                int event = fc.stepToNextScreenEvent();
                if (event != FormEntryController.EVENT_QUESTION) {
                    break;
                }

                //Next question
                FormEntryPrompt question = fc.getQuestionPrompt();

                //Skip if not FEI…
                String query = question.getFormElement()
                        .getAdditionalAttribute(null, "query");
                if (query == null) {
                    continue;
                }

                //…or no match
                if (!query.matches(".*\\b" + checkName + "\\b.*")) {
                    continue;
                }

                //Otherwise reset
                fc.saveAnswer(question.getIndex(), null);

                //Prepare to move down cascade
                checkName = getCheckName.get();
            }

            //Back to start
            fc.jumpToIndex(startIndex);

        } catch (JavaRosaException e) {
            Timber.d(e);
        }
    }

    public static @Nullable Selection getSelectedItem(FormEntryPrompt prompt, List<SelectChoice> items) {
        IAnswerData answer = prompt.getAnswerValue();
        if (answer == null) {
            return null;
        } else if (answer instanceof SelectOneData) {
            return (Selection) answer.getValue();
        } else if (answer instanceof StringData) { // Fast external itemset
            for (SelectChoice item : items) {
                if (answer.getValue().equals(item.selection().xmlValue)) {
                    return item.selection();
                }
            }
            return null;
        }
        return null;
    }
}
