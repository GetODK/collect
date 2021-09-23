package org.odk.collect.android.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.View;

import com.bumptech.glide.Glide;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.databinding.ExImageWidgetAnswerBinding;
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
public class ExImageWidget extends QuestionWidget implements FileWidget, WidgetDataReceiver {
    ExImageWidgetAnswerBinding binding;

    private final WaitingForDataRegistry waitingForDataRegistry;
    private final QuestionMediaManager questionMediaManager;
    private final MediaUtils mediaUtils;
    private final ExternalAppIntentProvider externalAppIntentProvider;
    private final ActivityAvailability activityAvailability;

    File answerFile;

    public ExImageWidget(Context context, QuestionDetails questionDetails, QuestionMediaManager questionMediaManager,
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

        binding = ExImageWidgetAnswerBinding.inflate(((Activity) context).getLayoutInflater());

        binding.launchExternalAppButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);
        binding.launchExternalAppButton.setVisibility(questionDetails.isReadOnly() ? GONE : VISIBLE);
        binding.launchExternalAppButton.setOnClickListener(view -> launchExternalApp());
        binding.imageView.setOnClickListener(view -> mediaUtils.openFile(getContext(), answerFile, "image/*"));
        if (answerFile != null) {
            displayImage();
        } else {
            binding.imageView.setVisibility(GONE);
        }

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
        binding.imageView.setVisibility(GONE);
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

        if (object instanceof File && mediaUtils.isImageFile((File) object)) {
            answerFile = (File) object;
            if (answerFile.exists()) {
                questionMediaManager.replaceAnswerFile(getFormEntryPrompt().getIndex().toString(), answerFile.getAbsolutePath());
                displayImage();
                widgetValueChanged();
            } else {
                Timber.e("Inserting Image file FAILED");
            }
        } else if (object != null) {
            if (object instanceof File) {
                ToastUtils.showLongToast(Collect.getInstance(), R.string.invalid_file_type);
                mediaUtils.deleteMediaFile(((File) object).getAbsolutePath());
                Timber.e("ExImageWidget's setBinaryData must receive an image file but received: %s", FileUtils.getMimeType((File) object));
            } else {
                Timber.e("ExImageWidget's setBinaryData must receive an image file but received: %s", object.getClass());
            }
        }
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        binding.launchExternalAppButton.setOnLongClickListener(l);
        binding.imageView.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        binding.launchExternalAppButton.cancelLongPress();
        binding.imageView.cancelLongPress();
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
            ((Activity) getContext()).startActivityForResult(intent, ApplicationConstants.RequestCodes.EX_IMAGE_CHOOSER);
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

    private void displayImage() {
        Glide.with(this)
                .load(answerFile)
                .fitCenter()
                .into(binding.imageView);
        binding.imageView.setVisibility(VISIBLE);
    }
}
