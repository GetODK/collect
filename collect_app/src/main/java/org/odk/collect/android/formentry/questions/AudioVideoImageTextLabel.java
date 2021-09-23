/*
 * Copyright (C) 2011 University of Washington
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

package org.odk.collect.android.formentry.questions;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.method.LinkMovementMethod;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.BuildConfig;
import org.odk.collect.android.R;
import org.odk.collect.android.audio.AudioButton;
import org.odk.collect.android.audio.AudioHelper;
import org.odk.collect.android.listeners.SelectItemClickListener;
import org.odk.collect.android.utilities.ContentUriProvider;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.FormEntryPromptUtils;
import org.odk.collect.android.utilities.HtmlUtils;
import org.odk.collect.android.utilities.ScreenContext;
import org.odk.collect.android.utilities.ThemeUtils;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.audioclips.Clip;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import timber.log.Timber;

/**
 * Represents a label for a prompt/question or a select choice. The label can have media
 * attached to it as well as text (such as audio, video or an image).
 */
public class AudioVideoImageTextLabel extends RelativeLayout implements View.OnClickListener {

    @BindView(R.id.audioButton)
    AudioButton audioButton;

    @BindView(R.id.videoButton)
    MaterialButton videoButton;

    @BindView(R.id.imageView)
    ImageView imageView;

    @BindView(R.id.missingImage)
    TextView missingImage;

    @BindView(R.id.text_container)
    FrameLayout textContainer;

    @BindView(R.id.text_label)
    TextView labelTextView;

    @BindView(R.id.media_buttons)
    LinearLayout mediaButtonsContainer;

    private int originalTextColor;
    private int playTextColor = Color.BLUE;
    private CharSequence questionText;
    private SelectItemClickListener listener;
    private File videoFile;
    private File bigImageFile;

    public AudioVideoImageTextLabel(Context context) {
        super(context);

        View.inflate(context, R.layout.audio_video_image_text_label, this);
        ButterKnife.bind(this);
    }

    public AudioVideoImageTextLabel(Context context, AttributeSet attrs) {
        super(context, attrs);

        View.inflate(context, R.layout.audio_video_image_text_label, this);
        ButterKnife.bind(this);
    }

    public void setTextView(TextView questionText) {
        this.questionText = questionText.getText();

        labelTextView = questionText;
        labelTextView.setId(R.id.text_label);
        labelTextView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClicked();
            }
        });

        textContainer.removeAllViews();
        textContainer.addView(labelTextView);
    }

    public void setText(String questionText, boolean isRequiredQuestion, float fontSize) {
        this.questionText = questionText;

        if (questionText != null && !questionText.isEmpty()) {
            labelTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize);
            labelTextView.setText(HtmlUtils.textToHtml(FormEntryPromptUtils.markQuestionIfIsRequired(questionText, isRequiredQuestion)));
            labelTextView.setMovementMethod(LinkMovementMethod.getInstance());

            // Wrap to the size of the parent view
            labelTextView.setHorizontallyScrolling(false);
        } else {
            labelTextView.setVisibility(View.GONE);
        }
    }

    public void setAudio(String audioURI, AudioHelper audioHelper) {
        setupAudioButton(audioURI, audioHelper);
    }

    public void setImage(@NonNull File imageFile) {
        if (imageFile.exists()) {
            imageView.layout(0, 0, 0, 0);

            Glide.with(this)
                    .load(imageFile)
                    .centerInside()
                    .into(imageView);

            imageView.setVisibility(VISIBLE);
            imageView.setOnClickListener(this);
        } else {
            missingImage.setVisibility(VISIBLE);
            missingImage.setText(getContext().getString(R.string.file_missing, imageFile));
        }
    }

    public void setBigImage(@NonNull File bigImageFile) {
        this.bigImageFile = bigImageFile;
    }

    public void setVideo(@NonNull File videoFile) {
        this.videoFile = videoFile;
        setupVideoButton();
    }

    public void setPlayTextColor(int textColor) {
        playTextColor = textColor;
        audioButton.setColors(getThemeUtils().getColorOnSurface(), playTextColor);
    }

    public void playVideo() {
        if (!videoFile.exists()) {
            // We should have a video clip, but the file doesn't exist.
            String errorMsg = getContext().getString(R.string.file_missing, videoFile);
            Timber.d("File %s is missing", videoFile);
            ToastUtils.showLongToast(getContext(), errorMsg);
            return;
        }

        Intent intent = new Intent("android.intent.action.VIEW");
        Uri uri =
                ContentUriProvider.getUriForFile(getContext(), BuildConfig.APPLICATION_ID + ".provider", videoFile);
        FileUtils.grantFileReadPermissions(intent, uri, getContext());
        intent.setDataAndType(uri, "video/*");
        if (intent.resolveActivity(getContext().getPackageManager()) != null) {
            getContext().startActivity(intent);
        } else {
            ToastUtils.showShortToast(getContext(), getContext().getString(R.string.activity_not_found, getContext().getString(R.string.view_video)));
        }
    }

    public TextView getLabelTextView() {
        return labelTextView;
    }

    public ImageView getImageView() {
        return imageView;
    }

    public TextView getMissingImage() {
        return missingImage;
    }

    public Button getVideoButton() {
        return videoButton;
    }

    public Button getAudioButton() {
        return audioButton;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.videoButton:
                playVideo();
                break;
            case R.id.imageView:
                onImageClick();
                break;
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        labelTextView.setEnabled(enabled);
        imageView.setEnabled(enabled);
    }

    @Override
    public boolean isEnabled() {
        return labelTextView.isEnabled() && imageView.isEnabled();
    }

    private void onImageClick() {
        if (bigImageFile != null) {
            openImage();
        } else {
            selectItem();
        }
    }

    private void openImage() {
        try {
            Intent intent = new Intent("android.intent.action.VIEW");
            Uri uri =
                    ContentUriProvider.getUriForFile(getContext(), BuildConfig.APPLICATION_ID + ".provider", bigImageFile);
            FileUtils.grantFileReadPermissions(intent, uri, getContext());
            intent.setDataAndType(uri, "image/*");
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Timber.d(e, "No Activity found to handle due to %s", e.getMessage());
            ToastUtils.showShortToast(getContext(), getContext().getString(R.string.activity_not_found,
                    getContext().getString(R.string.view_image)));
        }
    }

    private void selectItem() {
        if (labelTextView instanceof RadioButton) {
            ((RadioButton) labelTextView).setChecked(true);
        } else if (labelTextView instanceof CheckBox) {
            CheckBox checkbox = (CheckBox) labelTextView;
            checkbox.setChecked(!checkbox.isChecked());
        }
        if (listener != null) {
            listener.onItemClicked();
        }
    }

    private void setupVideoButton() {
        videoButton.setVisibility(VISIBLE);
        mediaButtonsContainer.setVisibility(VISIBLE);
        videoButton.setOnClickListener(this);
    }

    private void setupAudioButton(String audioURI, AudioHelper audioHelper) {
        audioButton.setVisibility(VISIBLE);
        mediaButtonsContainer.setVisibility(VISIBLE);

        ScreenContext activity = getScreenContext();
        String clipID = getTag() != null ? getTag().toString() : "";
        LiveData<Boolean> isPlayingLiveData = audioHelper.setAudio(audioButton, new Clip(clipID, audioURI));

        originalTextColor = labelTextView.getTextColors().getDefaultColor();
        isPlayingLiveData.observe(activity.getViewLifecycle(), isPlaying -> {
            if (isPlaying) {
                labelTextView.setTextColor(playTextColor);
            } else {
                labelTextView.setTextColor(originalTextColor);
                // then set the text to our original (brings back any html formatting)
                labelTextView.setText(questionText);
            }
        });
    }

    @NotNull
    private ThemeUtils getThemeUtils() {
        return new ThemeUtils(getContext());
    }

    private ScreenContext getScreenContext() {
        try {
            return (ScreenContext) getContext();
        } catch (ClassCastException e) {
            throw new RuntimeException(getContext().toString() + " must implement " + ScreenContext.class.getName());
        }
    }

    public void setItemClickListener(SelectItemClickListener listener) {
        this.listener = listener;
    }
}
