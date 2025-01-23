/*
 * Copyright (C) 2012 University of Washington
 * Copyright (C) 2007 The Android Open Source Project
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

package org.odk.collect.draw;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.viewmodel.CreationExtras;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.odk.collect.androidshared.bitmap.ImageFileUtils;
import org.odk.collect.androidshared.ui.DialogFragmentUtils;
import org.odk.collect.androidshared.ui.FragmentFactoryBuilder;
import org.odk.collect.async.Scheduler;
import org.odk.collect.settings.SettingsProvider;
import org.odk.collect.settings.keys.MetaKeys;
import org.odk.collect.strings.localization.LocalizedActivity;

import java.io.File;

import javax.inject.Inject;

/**
 * Modified from the FingerPaint example found in The Android Open Source
 * Project.
 *
 * @author BehrAtherton@gmail.com
 */
public class DrawActivity extends LocalizedActivity {
    public static final String OPTION = "option";
    public static final String OPTION_SIGNATURE = "signature";
    public static final String OPTION_ANNOTATE = "annotate";
    public static final String OPTION_DRAW = "draw";
    public static final String REF_IMAGE = "refImage";
    public static final String SCREEN_ORIENTATION = "screenOrientation";
    public static final String EXTRA_OUTPUT = android.provider.MediaStore.EXTRA_OUTPUT;
    // restore

    private FloatingActionButton fabActions;

    // incoming options...
    private String loadOption;
    private File refImage;
    private File output;
    private File savepointImage;

    private DrawView drawView;

    private DrawViewModel drawViewModel;

    @Inject
    Scheduler scheduler;

    @Inject
    SettingsProvider settingsProvider;

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            createQuitDrawDialog();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        ((DrawDependencyComponentProvider) getApplicationContext()).getDrawDependencyComponent().inject(this);

        PenColorPickerViewModel viewModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
                return (T) new PenColorPickerViewModel(settingsProvider.getMetaSettings(), MetaKeys.LAST_USED_PEN_COLOR);
            }
        }).get(PenColorPickerViewModel.class);

        this.getSupportFragmentManager().setFragmentFactory(new FragmentFactoryBuilder()
                .forClass(PenColorPickerDialog.class, () -> new PenColorPickerDialog(viewModel))
                .build());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.draw_layout);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        drawView = findViewById(R.id.drawView);

        fabActions = findViewById(R.id.fab_actions);
        final FloatingActionButton fabSetColor = findViewById(R.id.fab_set_color);
        final CardView cardViewSetColor = findViewById(R.id.cv_set_color);
        final FloatingActionButton fabSaveAndClose = findViewById(R.id.fab_save_and_close);
        final CardView cardViewSaveAndClose = findViewById(R.id.cv_save_and_close);
        final FloatingActionButton fabClear = findViewById(R.id.fab_clear);
        final CardView cardViewClear = findViewById(R.id.cv_clear);

        fabActions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int status = Integer.parseInt(view.getTag().toString());
                if (status == 0) {
                    status = 1;
                    fabActions.animate().rotation(45).setInterpolator(new AccelerateDecelerateInterpolator())
                            .setDuration(100).start();

                    scaleInAnimation(fabSetColor, 50, 150, new OvershootInterpolator(), true);
                    scaleInAnimation(cardViewSetColor, 50, 150, new OvershootInterpolator(), true);
                    scaleInAnimation(fabSaveAndClose, 100, 150, new OvershootInterpolator(), true);
                    scaleInAnimation(cardViewSaveAndClose, 100, 150, new OvershootInterpolator(), true);
                    scaleInAnimation(fabClear, 150, 150, new OvershootInterpolator(), true);
                    scaleInAnimation(cardViewClear, 150, 150, new OvershootInterpolator(), true);

                    fabSetColor.show();
                    cardViewSetColor.setVisibility(View.VISIBLE);
                    fabSaveAndClose.show();
                    cardViewSaveAndClose.setVisibility(View.VISIBLE);
                    fabClear.show();
                    cardViewClear.setVisibility(View.VISIBLE);
                } else {
                    status = 0;
                    fabActions.animate().rotation(0).setInterpolator(new AccelerateDecelerateInterpolator())
                            .setDuration(100).start();

                    fabSetColor.hide();
                    cardViewSetColor.setVisibility(View.INVISIBLE);
                    fabSaveAndClose.hide();
                    cardViewSaveAndClose.setVisibility(View.INVISIBLE);
                    fabClear.hide();
                    cardViewClear.setVisibility(View.INVISIBLE);
                }
                view.setTag(status);
            }
        });

        cardViewClear.setOnClickListener(this::clear);
        fabClear.setOnClickListener(this::clear);
        cardViewSaveAndClose.setOnClickListener(this::close);
        fabSaveAndClose.setOnClickListener(this::close);
        cardViewSetColor.setOnClickListener(this::setColor);
        fabSetColor.setOnClickListener(this::setColor);

        Bundle extras = getIntent().getExtras();
        String imagePath = drawView.getImagePath();
        if (extras.getInt(SCREEN_ORIENTATION) == 1) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        loadOption = extras.getString(OPTION);
        if (loadOption == null) {
            loadOption = OPTION_DRAW;
        }
        // refImage can also be present if resuming a drawing
        Uri uri = (Uri) extras.get(REF_IMAGE);
        if (uri != null) {
            refImage = new File(uri.getPath());
        }
        savepointImage = new File(imagePath);
        savepointImage.delete();
        if (refImage != null && refImage.exists()) {
            ImageFileUtils.copyImageAndApplyExifRotation(refImage, savepointImage);
        }
        uri = (Uri) extras.get(EXTRA_OUTPUT);
        if (uri != null) {
            output = new File(uri.getPath());
        } else {
            output = new File(imagePath);
        }

        // At this point, we have:
        // loadOption -- type of activity (draw, signature, annotate)
        // refImage -- original image to work with
        // savepointImage -- drawing to use as a starting point (may be copy of
        // original)
        // output -- where the output should be written

        drawView.setupView(OPTION_SIGNATURE.equals(loadOption));

        viewModel.getPenColor().observe(this, penColor -> {
            if (OPTION_SIGNATURE.equals(loadOption) && viewModel.isDefaultValue()) {
                drawView.setColor(Color.BLACK);
            } else {
                drawView.setColor(penColor);
            }
        });

        drawViewModel = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @NonNull
            @Override
            public <T extends ViewModel> T create(@NonNull Class<T> modelClass, @NonNull CreationExtras extras) {
                return (T) new DrawViewModel(output, scheduler);
            }
        }).get(DrawViewModel.class);

        drawViewModel.getSaveResult().observe(this, (success) -> {
            if (success) {
                setResult(Activity.RESULT_OK);
            } else {
                setResult(Activity.RESULT_CANCELED);
            }

            finish();
        });

        getOnBackPressedDispatcher().addCallback(onBackPressedCallback);
    }

    private void reset() {
        savepointImage.delete();
        if (!OPTION_SIGNATURE.equals(loadOption) && refImage != null
                && refImage.exists()) {
            ImageFileUtils.copyImageAndApplyExifRotation(refImage, savepointImage);
        }
        drawView.reset();
        drawView.invalidate();
    }

    private void cancelAndClose() {
        setResult(Activity.RESULT_CANCELED);
        this.finish();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.isAltPressed()) {
                    createQuitDrawDialog();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Create a dialog with options to save and exit, save, or quit without
     * saving
     */
    private void createQuitDrawDialog() {
        QuitDrawingDialog.show(
                this,
                this::cancelAndClose,
                () -> drawViewModel.save(drawView)
        );
    }

    private void clear(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            fabActions.performClick();
            reset();
        }
    }

    private void close(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            fabActions.performClick();
            drawViewModel.save(drawView);
        }
    }

    private void setColor(View view) {
        if (view.getVisibility() == View.VISIBLE) {
            fabActions.performClick();

            DialogFragmentUtils.showIfNotShowing(PenColorPickerDialog.class, getSupportFragmentManager());
        }
    }

    private static void scaleInAnimation(final View view, int startOffset, int duration,
                                         Interpolator interpolator, final boolean isInvisible) {
        ScaleAnimation scaleInAnimation = new ScaleAnimation(0f, 1f, 0f, 1f, Animation.RELATIVE_TO_SELF,
                0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        scaleInAnimation.setInterpolator(interpolator);
        scaleInAnimation.setDuration(duration);
        scaleInAnimation.setStartOffset(startOffset);
        scaleInAnimation.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                if (isInvisible) {
                    view.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onAnimationEnd(Animation animation) {
            }

            @Override
            public void onAnimationRepeat(Animation animation) {
            }
        });
        view.startAnimation(scaleInAnimation);
    }
}
