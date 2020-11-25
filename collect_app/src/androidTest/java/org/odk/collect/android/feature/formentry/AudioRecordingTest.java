package org.odk.collect.android.feature.formentry;

import android.Manifest;
import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.GrantPermissionRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;
import org.odk.collect.android.R;
import org.odk.collect.android.support.CollectTestRule;
import org.odk.collect.android.support.TestDependencies;
import org.odk.collect.android.support.TestRuleChain;
import org.odk.collect.android.support.pages.AdminSettingsPage;
import org.odk.collect.android.support.pages.FormEntryPage;
import org.odk.collect.android.support.pages.MainMenuPage;
import org.odk.collect.android.support.pages.OkDialog;
import org.odk.collect.android.support.pages.SaveOrIgnoreDialog;
import org.odk.collect.audiorecorder.recording.AudioRecorderViewModelFactory;
import org.odk.collect.audiorecorder.testsupport.StubAudioRecorderViewModel;

import java.io.File;
import java.io.IOException;

import static org.odk.collect.android.support.FileUtils.copyFileFromAssets;

@RunWith(AndroidJUnit4.class)
public class AudioRecordingTest {

    private StubAudioRecorderViewModel stubAudioRecorderViewModel;

    public final TestDependencies testDependencies = new TestDependencies() {
        @Override
        public AudioRecorderViewModelFactory providesAudioRecorderViewModelFactory(Application application) {
            return new AudioRecorderViewModelFactory(application) {
                @Override
                public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
                    if (stubAudioRecorderViewModel == null) {
                        try {
                            File stubRecording = File.createTempFile("test", ".m4a");
                            stubRecording.deleteOnExit();

                            copyFileFromAssets("media/test.m4a", stubRecording.getAbsolutePath());
                            stubAudioRecorderViewModel = new StubAudioRecorderViewModel(stubRecording.getAbsolutePath());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    return (T) stubAudioRecorderViewModel;
                }
            };
        }
    };

    public final CollectTestRule rule = new CollectTestRule();

    @Rule
    public final RuleChain chain = TestRuleChain.chain(testDependencies)
            .around(GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO))
            .around(rule);

    @Test
    public void onAudioQuestion_canRecordAudio() {
        new MainMenuPage(rule).assertOnPage()
                .copyForm("audio-question.xml")
                .startBlankFormIgnoringAudioWarning("Audio Question")
                .assertTextNotDisplayed(R.string.stop_recording)
                .clickOnString(R.string.capture_audio)
                .clickOnString(R.string.stop_recording)
                .assertTextNotDisplayed(R.string.stop_recording)
                .assertTextNotDisplayed(R.string.capture_audio)
                .assertContentDescriptionDisplayed(R.string.play_audio);
    }

    @Test
    public void loadingAudioQuestionForTheFirstTime_showsInternalRecordingWarning() {
        new MainMenuPage(rule).assertOnPage()
                .copyForm("audio-question.xml")

                .clickFillBlankForm()
                .clickOnFormWithAudioWarning("Audio Question")
                .assertText(R.string.internal_recorder_warning)
                .clickOK(new FormEntryPage("Audio Question", rule))
                .pressBack(new SaveOrIgnoreDialog<>("Audio Question", new MainMenuPage(rule), rule))
                .clickIgnoreChanges()

                // Check that the warning isn't shown again
                .clickFillBlankForm()
                .clickOnForm("Audio Question")
                .assertQuestion("What does it sound like?");
    }

    @Test
    public void loadingAudioQuestionForTheFirstTime_whenAdminHasDisabledExternal_recordingDoesNotShowWarning() {
        new MainMenuPage(rule).assertOnPage()
                .copyForm("audio-question.xml")
                .clickOnMenu()
                .clickAdminSettings()
                .clickOnString(R.string.user_settings)
                .scrollToRecyclerViewItemAndClickText(R.string.external_app_recording)
                .pressBack(new AdminSettingsPage(rule))
                .pressBack(new MainMenuPage(rule))

                .clickFillBlankForm()
                .clickOnForm("Audio Question")
                .assertQuestion("What does it sound like?");
    }

    @Test
    public void whileRecording_swipingToADifferentScreen_showsWarning_andStaysOnSameScreen() {
        new MainMenuPage(rule).assertOnPage()
                .copyForm("audio-question.xml")
                .startBlankFormIgnoringAudioWarning("Audio Question")
                .clickOnString(R.string.capture_audio)
                .swipeToEndScreenWhileRecording()
                .clickOK(new FormEntryPage("Audio Question", rule))

                .assertQuestion("What does it sound like?")
                .clickOnString(R.string.stop_recording)
                .assertTextNotDisplayed(R.string.stop_recording)
                .assertTextNotDisplayed(R.string.capture_audio)
                .assertContentDescriptionDisplayed(R.string.play_audio);
    }

    @Test
    public void whileRecording_quittingForm_showsWarning_andStaysOnSameScreen() {
        new MainMenuPage(rule).assertOnPage()
                .copyForm("audio-question.xml")
                .startBlankFormIgnoringAudioWarning("Audio Question")
                .clickOnString(R.string.capture_audio)
                .pressBack(new OkDialog(rule))
                .clickOK(new FormEntryPage("Audio Question", rule))

                .assertQuestion("What does it sound like?")
                .clickOnString(R.string.stop_recording)
                .assertTextNotDisplayed(R.string.stop_recording)
                .assertTextNotDisplayed(R.string.capture_audio)
                .assertContentDescriptionDisplayed(R.string.play_audio);
    }
}
