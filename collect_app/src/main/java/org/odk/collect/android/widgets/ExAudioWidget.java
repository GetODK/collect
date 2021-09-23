package org.odk.collect.android.widgets;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.util.TypedValue;
import android.view.View;

import androidx.appcompat.app.AlertDialog;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.R;
import org.odk.collect.android.analytics.AnalyticsEvents;
import org.odk.collect.android.analytics.AnalyticsUtils;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.audio.AudioControllerView;
import org.odk.collect.android.databinding.ExAudioWidgetAnswerBinding;
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
import org.odk.collect.android.widgets.utilities.AudioPlayer;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;
import org.odk.collect.audioclips.Clip;

import java.io.File;

import timber.log.Timber;

@SuppressLint("ViewConstructor")
public class ExAudioWidget extends QuestionWidget implements FileWidget, WidgetDataReceiver {
    ExAudioWidgetAnswerBinding binding;

    private final AudioPlayer audioPlayer;
    private final WaitingForDataRegistry waitingForDataRegistry;
    private final QuestionMediaManager questionMediaManager;
    private final MediaUtils mediaUtils;
    private final ExternalAppIntentProvider externalAppIntentProvider;
    private final ActivityAvailability activityAvailability;

    File answerFile;

    public ExAudioWidget(Context context, QuestionDetails questionDetails, QuestionMediaManager questionMediaManager,
                         AudioPlayer audioPlayer, WaitingForDataRegistry waitingForDataRegistry, MediaUtils mediaUtils,
                         ExternalAppIntentProvider externalAppIntentProvider, ActivityAvailability activityAvailability) {
        super(context, questionDetails);

        this.audioPlayer = audioPlayer;
        this.waitingForDataRegistry = waitingForDataRegistry;
        this.questionMediaManager = questionMediaManager;
        this.mediaUtils = mediaUtils;
        this.externalAppIntentProvider = externalAppIntentProvider;
        this.activityAvailability = activityAvailability;

        updateVisibilities();
        updatePlayerMedia();
    }

    @Override
    protected View onCreateAnswerView(Context context, FormEntryPrompt prompt, int answerFontSize) {
        setupAnswerFile(prompt.getAnswerText());

        binding = ExAudioWidgetAnswerBinding.inflate(((Activity) context).getLayoutInflater());

        binding.launchExternalAppButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontSize);
        binding.launchExternalAppButton.setOnClickListener(view -> launchExternalApp());

        return binding.getRoot();
    }

    @Override
    public void deleteFile() {
        audioPlayer.stop();
        questionMediaManager.deleteAnswerFile(getFormEntryPrompt().getIndex().toString(), answerFile.getAbsolutePath());
        answerFile = null;
    }

    @Override
    public void clearAnswer() {
        deleteFile();
        updateVisibilities();
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

        if (object instanceof File && mediaUtils.isAudioFile((File) object)) {
            answerFile = (File) object;
            if (answerFile.exists()) {
                questionMediaManager.replaceAnswerFile(getFormEntryPrompt().getIndex().toString(), answerFile.getAbsolutePath());
                updateVisibilities();
                updatePlayerMedia();
                widgetValueChanged();
            } else {
                Timber.e("Inserting Audio file FAILED");
            }
        } else if (object != null) {
            if (object instanceof File) {
                ToastUtils.showLongToast(Collect.getInstance(), R.string.invalid_file_type);
                mediaUtils.deleteMediaFile(((File) object).getAbsolutePath());
                Timber.e("ExAudioWidget's setBinaryData must receive a audio file but received: %s", FileUtils.getMimeType((File) object));
            } else {
                Timber.e("ExAudioWidget's setBinaryData must receive a audio file but received: %s", object.getClass());
            }
        }
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        binding.launchExternalAppButton.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        binding.launchExternalAppButton.cancelLongPress();
    }

    private void updateVisibilities() {
        if (answerFile == null) {
            binding.launchExternalAppButton.setVisibility(VISIBLE);
            binding.audioPlayer.recordingDuration.setVisibility(GONE);
            binding.audioPlayer.waveform.setVisibility(GONE);
            binding.audioPlayer.audioController.setVisibility(GONE);
        } else {
            binding.launchExternalAppButton.setVisibility(GONE);
            binding.audioPlayer.recordingDuration.setVisibility(GONE);
            binding.audioPlayer.waveform.setVisibility(GONE);
            binding.audioPlayer.audioController.setVisibility(VISIBLE);
        }

        if (questionDetails.isReadOnly()) {
            binding.launchExternalAppButton.setVisibility(GONE);
        }
    }

    private void updatePlayerMedia() {
        if (answerFile != null) {
            Clip clip = new Clip("audio:" + getFormEntryPrompt().getIndex().toString(), answerFile.getAbsolutePath());

            audioPlayer.onPlayingChanged(clip.getClipID(), binding.audioPlayer.audioController::setPlaying);
            audioPlayer.onPositionChanged(clip.getClipID(), binding.audioPlayer.audioController::setPosition);
            binding.audioPlayer.audioController.setDuration(getDurationOfFile(clip.getURI()));
            binding.audioPlayer.audioController.setListener(new AudioControllerView.Listener() {
                @Override
                public void onPlayClicked() {
                    audioPlayer.play(clip);
                }

                @Override
                public void onPauseClicked() {
                    audioPlayer.pause();
                }

                @Override
                public void onPositionChanged(Integer newPosition) {
                    AnalyticsUtils.logFormEvent(AnalyticsEvents.AUDIO_PLAYER_SEEK);
                    audioPlayer.setPosition(clip.getClipID(), newPosition);
                }

                @Override
                public void onRemoveClicked() {
                    new AlertDialog.Builder(getContext())
                            .setTitle(R.string.delete_answer_file_question)
                            .setMessage(R.string.answer_file_delete_warning)
                            .setPositiveButton(R.string.delete_answer_file, (dialog, which) -> clearAnswer())
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                }
            });

        }
    }

    private Integer getDurationOfFile(String uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(uri);
        String durationString = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        return durationString != null ? Integer.parseInt(durationString) : 0;
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
            ((Activity) getContext()).startActivityForResult(intent, ApplicationConstants.RequestCodes.EX_AUDIO_CHOOSER);
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
