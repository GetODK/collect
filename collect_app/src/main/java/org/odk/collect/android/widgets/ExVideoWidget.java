package org.odk.collect.android.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.View;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.databinding.ExVideoWidgetAnswerBinding;
import org.odk.collect.android.formentry.questions.QuestionDetails;
import org.odk.collect.android.utilities.ActivityAvailability;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.ExternalAppIntentProvider;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.MediaUtils;
import org.odk.collect.android.utilities.QuestionMediaManager;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.android.widgets.interfaces.WidgetDataReceiver;
import org.odk.collect.android.widgets.interfaces.FileWidget;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;

import java.io.File;

import timber.log.Timber;

@SuppressLint("ViewConstructor")
public class ExVideoWidget extends QuestionWidget implements FileWidget, WidgetDataReceiver {
    ExVideoWidgetAnswerBinding binding;

    private final WaitingForDataRegistry waitingForDataRegistry;
    private final QuestionMediaManager questionMediaManager;
    private final MediaUtils mediaUtils;
    private final ExternalAppIntentProvider externalAppIntentProvider;
    private final ActivityAvailability activityAvailability;

    File answerFile;

    public ExVideoWidget(Context context, QuestionDetails questionDetails, QuestionMediaManager questionMediaManager,
                         WaitingForDataRegistry waitingForDataRegistry, MediaUtils mediaUtils,
                         ExternalAppIntentProvider externalAppIntentProvider, ActivityAvailability activityAvailability) {
        super(context, questionDetails);

        this.waitingForDataRegistry = waitingForDataRegistry;
        this.questionMediaManager = questionMediaManager;
        this.mediaUtils = mediaUtils;
        this.externalAppIntentProvider = externalAppIntentProvider;
        this.activityAvailability = activityAvailability;
    }

    @Override
    protected View onCreateAnswerView(Context context, FormEntryPrompt prompt, int answerFontSize) {
        setupAnswerFile(prompt.getAnswerText());

        binding = ExVideoWidgetAnswerBinding.inflate(((Activity) context).getLayoutInflater());

        binding.captureVideoButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);
        binding.playVideoButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);
        binding.captureVideoButton.setVisibility(questionDetails.isReadOnly() ? GONE : VISIBLE);
        binding.captureVideoButton.setOnClickListener(view -> launchExternalApp());
        binding.playVideoButton.setOnClickListener(view -> mediaUtils.openFile(getContext(), answerFile, "video/*"));
        binding.playVideoButton.setEnabled(answerFile != null);

        return binding.getRoot();
    }

    @Override
    public void deleteFile() {
        questionMediaManager.deleteAnswerFile(getFormEntryPrompt().getIndex().toString(), answerFile.getAbsolutePath());
        answerFile = null;
    }

    @Override
    public void clearAnswer() {
        deleteFile();
        binding.playVideoButton.setEnabled(false);
        widgetValueChanged();
    }

    @Override
    public IAnswerData getAnswer() {
        return answerFile != null ? new StringData(answerFile.getName()) : null;
    }

    @Override
    public void setData(Object object) {
        if (answerFile != null) {
            clearAnswer();
        }

        if (object instanceof File && mediaUtils.isVideoFile((File) object)) {
            answerFile = (File) object;
            if (answerFile.exists()) {
                questionMediaManager.replaceAnswerFile(getFormEntryPrompt().getIndex().toString(), answerFile.getAbsolutePath());
                binding.playVideoButton.setEnabled(true);
                widgetValueChanged();
            } else {
                Timber.e("Inserting Video file FAILED");
            }
        } else if (object != null) {
            if (object instanceof File) {
                ToastUtils.showLongToast(Collect.getInstance(), R.string.invalid_file_type);
                mediaUtils.deleteMediaFile(((File) object).getAbsolutePath());
                Timber.e("ExVideoWidget's setBinaryData must receive a video file but received: %s", FileUtils.getMimeType((File) object));
            } else {
                Timber.e("ExVideoWidget's setBinaryData must receive a video file but received: %s", object.getClass());
            }
        }
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        binding.captureVideoButton.setOnLongClickListener(l);
        binding.playVideoButton.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        binding.captureVideoButton.cancelLongPress();
        binding.playVideoButton.cancelLongPress();
    }

    private void launchExternalApp() {
        waitingForDataRegistry.waitForData(getFormEntryPrompt().getIndex());
        try {
            Intent intent = externalAppIntentProvider.getIntentToRunExternalApp(getContext(), getFormEntryPrompt(), activityAvailability, Collect.getInstance().getPackageManager());
            fireActivityForResult(intent);
        } catch (Exception | Error e) {
            ToastUtils.showLongToast(Collect.getInstance(), e.getMessage());
        }
    }

    private void fireActivityForResult(Intent intent) {
        try {
            ((Activity) getContext()).startActivityForResult(intent, ApplicationConstants.RequestCodes.EX_VIDEO_CHOOSER);
        } catch (SecurityException e) {
            Timber.i(e);
            ToastUtils.showLongToast(Collect.getInstance(), R.string.not_granted_permission);
        }
    }

    private void setupAnswerFile(String fileName) {
        if (fileName != null && !fileName.isEmpty()) {
            answerFile = new File(getInstanceFolder() + File.separator + fileName);
        }
    }
}
