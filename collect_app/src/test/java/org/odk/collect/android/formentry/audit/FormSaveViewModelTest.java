package org.odk.collect.android.formentry.audit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.odk.collect.android.formentry.saving.FormSaveViewModel.SaveResult.State.CHANGE_REASON_REQUIRED;
import static org.odk.collect.android.formentry.saving.FormSaveViewModel.SaveResult.State.CONSTRAINT_ERROR;
import static org.odk.collect.android.formentry.saving.FormSaveViewModel.SaveResult.State.FINALIZE_ERROR;
import static org.odk.collect.android.formentry.saving.FormSaveViewModel.SaveResult.State.SAVED;
import static org.odk.collect.android.formentry.saving.FormSaveViewModel.SaveResult.State.SAVE_ERROR;
import static org.odk.collect.android.formentry.saving.FormSaveViewModel.SaveResult.State.SAVING;
import static org.odk.collect.android.formentry.saving.FormSaveViewModel.SaveResult.State.WAITING_TO_SAVE;
import static org.odk.collect.androidshared.livedata.LiveDataUtils.liveDataOf;

import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.common.io.Files;

import org.javarosa.form.api.FormEntryController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.odk.collect.android.formentry.FormSession;
import org.odk.collect.android.formentry.saving.FormSaveViewModel;
import org.odk.collect.android.formentry.saving.FormSaver;
import org.odk.collect.android.javarosawrapper.FormController;
import org.odk.collect.android.projects.ProjectsDataService;
import org.odk.collect.android.support.CollectHelpers;
import org.odk.collect.android.tasks.SaveFormToDisk;
import org.odk.collect.android.tasks.SaveToDiskResult;
import org.odk.collect.android.utilities.MediaUtils;
import org.odk.collect.audiorecorder.recording.AudioRecorder;
import org.odk.collect.entities.storage.EntitiesRepository;
import org.odk.collect.forms.Form;
import org.odk.collect.forms.instances.Instance;
import org.odk.collect.forms.instances.InstancesRepository;
import org.odk.collect.forms.savepoints.SavepointsRepository;
import org.odk.collect.formstest.InMemInstancesRepository;
import org.odk.collect.formstest.InMemSavepointsRepository;
import org.odk.collect.projects.Project;
import org.odk.collect.shared.TempFiles;
import org.odk.collect.testshared.FakeScheduler;
import org.odk.collect.utilities.Result;
import org.robolectric.Robolectric;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class FormSaveViewModelTest {
    private static final long CURRENT_TIME = 123L;

    private final SavedStateHandle savedStateHandle = new SavedStateHandle();
    private final FakeFormSaver formSaver = new FakeFormSaver();
    private final FakeScheduler scheduler = new FakeScheduler();

    private AuditEventLogger logger;
    private FormSaveViewModel viewModel;
    private MediaUtils mediaUtils;
    private FormController formController;
    private Form form;
    private AudioRecorder audioRecorder;
    private ProjectsDataService projectsDataService;

    private final EntitiesRepository entitiesRepository = mock(EntitiesRepository.class);

    private final InstancesRepository instancesRepository = new InMemInstancesRepository();
    private final SavepointsRepository savepointsRepository = new InMemSavepointsRepository();
    private MutableLiveData<FormSession> formSession;

    @Before
    public void setup() {
        // Useful given some methods will execute AsyncTasks
        Robolectric.getBackgroundThreadScheduler().pause();

        formController = mock(FormController.class);
        form = mock(Form.class);
        logger = mock(AuditEventLogger.class);
        mediaUtils = mock(MediaUtils.class);

        File instanceFile = new File(TempFiles.getPathInTempDir());
        when(formController.getInstanceFile()).thenReturn(instanceFile);
        when(formController.getAuditEventLogger()).thenReturn(logger);
        when(logger.isChangeReasonRequired()).thenReturn(false);

        audioRecorder = mock(AudioRecorder.class);
        projectsDataService = mock(ProjectsDataService.class);
        when(projectsDataService.requireCurrentProject()).thenReturn(Project.Companion.getDEMO_PROJECT());

        formSession = new MutableLiveData<>(new FormSession(formController, form));
        viewModel = new FormSaveViewModel(savedStateHandle, () -> CURRENT_TIME, formSaver, mediaUtils, scheduler, audioRecorder, projectsDataService, formSession, entitiesRepository, instancesRepository, savepointsRepository, mock());

        CollectHelpers.createDemoProject(); // Needed to deal with `new StoragePathProvider()` calls in `FormSaveViewModel`
    }

    @Test
    public void saveForm_returnsSaveResult_inSavingState() {
        viewModel.saveForm(Uri.parse("file://form"), true, "", false);
        FormSaveViewModel.SaveResult saveResult1 = viewModel.getSaveResult().getValue();
        assertThat(saveResult1.getState(), equalTo(SAVING));
    }

    @Test
    public void saveForm_wontRunMultipleSavesAtOnce() {
        viewModel.saveForm(Uri.parse("file://form"), true, "", false);
        viewModel.saveForm(Uri.parse("file://form"), true, "", false);

        whenFormSaverFinishes(SaveFormToDisk.SAVED);
        assertThat(formSaver.numberOfTimesCalled, equalTo(1));

        Robolectric.getBackgroundThreadScheduler().advanceToLastPostedRunnable(); // Run any other queued tasks

        assertThat(formSaver.numberOfTimesCalled, equalTo(1));
    }

    @Test
    public void saveForm_whenReasonRequiredToSave_returnsSaveResult_inChangeReasonRequiredState() {
        whenReasonRequiredToSave();

        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();
        viewModel.saveForm(Uri.parse("file://form"), true, "", false);
        assertThat(saveResult.getValue().getState(), equalTo(CHANGE_REASON_REQUIRED));
    }

    @Test
    public void saveForm_whenReasonRequiredToSave_andAudioIsRecording_andExiting_returnsSaveResult_inChangeReasonRequiredState() {
        whenReasonRequiredToSave();
        when(audioRecorder.isRecording()).thenReturn(true);

        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();
        viewModel.saveForm(Uri.parse("file://form"), true, "", false);
        assertThat(saveResult.getValue().getState(), equalTo(CHANGE_REASON_REQUIRED));
    }

    @Test
    public void whenFormSaverFinishes_saved_setsSaveResultState_toSaved() {
        viewModel.saveForm(Uri.parse("file://form"), true, "", false);
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();

        whenFormSaverFinishes(SaveFormToDisk.SAVED);
        assertThat(saveResult.getValue().getState(), equalTo(SAVED));
    }

    @Test
    public void whenFormSaverFinishes_whenViewExiting_logsFormSaveAndFormExitAuditEventAfterFlush() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", true);

        whenFormSaverFinishes(SaveFormToDisk.SAVED);

        InOrder verifier = inOrder(logger);
        verifier.verify(logger).flush();
        verifier.verify(logger).logEvent(
                AuditEvent.AuditEventType.FORM_SAVE,
                false,
                CURRENT_TIME
        );
        verifier.verify(logger).logEvent(AuditEvent.AuditEventType.FORM_EXIT, true, CURRENT_TIME);
    }

    @Test
    public void whenFormSaverFinishes_whenFormComplete_andViewExiting_logsFormExitAndFinalizeAuditEventsAfterFlush() {
        viewModel.saveForm(Uri.parse("file://form"), true, "", true);

        whenFormSaverFinishes(SaveFormToDisk.SAVED);

        InOrder verifier = inOrder(logger);
        verifier.verify(logger).flush();
        verifier.verify(logger).logEvent(
                AuditEvent.AuditEventType.FORM_SAVE,
                false,
                CURRENT_TIME
        );
        verifier.verify(logger).logEvent(AuditEvent.AuditEventType.FORM_EXIT, false, CURRENT_TIME);
        verifier.verify(logger).logEvent(AuditEvent.AuditEventType.FORM_FINALIZE, true, CURRENT_TIME);
    }

    @Test
    public void whenFormSaverFinishes_savedAndExit_setsSaveResultState_toSaved() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();

        whenFormSaverFinishes(SaveFormToDisk.SAVED_AND_EXIT);
        assertThat(saveResult.getValue().getState(), equalTo(SAVED));
    }

    @Test
    public void whenFormSaverFinishes_saveError_setSaveResultState_toSaveErrorWithMessage() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();

        whenFormSaverFinishes(SaveFormToDisk.SAVE_ERROR, "OH NO");
        assertThat(saveResult.getValue().getState(), equalTo(SAVE_ERROR));
        assertThat(saveResult.getValue().getMessage(), equalTo("OH NO"));
    }

    @Test
    public void whenFormSaverFinishes_saveError_logsSaveErrorAuditEvenAfterFlush() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);

        whenFormSaverFinishes(SaveFormToDisk.SAVE_ERROR);

        InOrder verifier = inOrder(logger);
        verifier.verify(logger).flush();
        verifier.verify(logger).logEvent(AuditEvent.AuditEventType.SAVE_ERROR, true, CURRENT_TIME);
    }

    @Test
    public void whenFormSaverFinishes_encryptionError_setSaveResultState_toFinalizeErrorWithMessage() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();

        whenFormSaverFinishes(SaveFormToDisk.ENCRYPTION_ERROR, "OH NO");
        assertThat(saveResult.getValue().getState(), equalTo(FINALIZE_ERROR));
        assertThat(saveResult.getValue().getMessage(), equalTo("OH NO"));
    }

    @Test
    public void whenFormSaverFinishes_encryptionError_logsFinalizeErrorAuditEventAfterFlush() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);

        whenFormSaverFinishes(SaveFormToDisk.ENCRYPTION_ERROR);

        InOrder verifier = inOrder(logger);
        verifier.verify(logger).flush();
        verifier.verify(logger).logEvent(AuditEvent.AuditEventType.FINALIZE_ERROR, true, CURRENT_TIME);
    }

    @Test
    public void whenFormSaverFinishes_answerConstraintViolated_setSaveResultState_toConstraintError() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();

        whenFormSaverFinishes(FormEntryController.ANSWER_CONSTRAINT_VIOLATED);
        assertThat(saveResult.getValue().getState(), equalTo(CONSTRAINT_ERROR));
    }

    @Test
    public void whenFormSaverFinishes_answerConstraintViolated_finalizesAndLogsConstraintErrorAuditEvent() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);

        whenFormSaverFinishes(FormEntryController.ANSWER_CONSTRAINT_VIOLATED);

        InOrder verifier = inOrder(logger);
        verifier.verify(logger).flush();
        verifier.verify(logger).logEvent(AuditEvent.AuditEventType.CONSTRAINT_ERROR, true, CURRENT_TIME);
    }

    @Test
    public void whenFormSaverFinishes_answerRequiredButEmpty_setSaveResultState_toConstraintError() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();

        whenFormSaverFinishes(FormEntryController.ANSWER_REQUIRED_BUT_EMPTY);
        assertThat(saveResult.getValue().getState(), equalTo(CONSTRAINT_ERROR));
    }

    @Test
    public void whenFormSaverFinishes_isSaving_returnsFalse() {
        assertThat(viewModel.isSaving(), equalTo(false));

        viewModel.saveForm(Uri.parse("file://form"), false, "", false);
        assertThat(viewModel.isSaving(), equalTo(true));

        whenFormSaverFinishes(SaveFormToDisk.SAVED);
        assertThat(viewModel.isSaving(), equalTo(false));
    }

    @Test
    public void saveForm_savesCorrectFiles() {
        viewModel.deleteAnswerFile("index", "blah");
        viewModel.replaceAnswerFile("index", "blah");

        viewModel.saveForm(Uri.parse("file://form"), true, "", true);
        whenFormSaverFinishes(SaveFormToDisk.SAVED);

        assertThat(formSaver.tempFiles.contains("blah"), equalTo(true));

        viewModel.saveForm(Uri.parse("file://form"), true, "", true);
        whenFormSaverFinishes(SaveFormToDisk.SAVED);

        assertThat(formSaver.tempFiles.isEmpty(), equalTo(true));
    }

    @Test
    public void whenReasonRequiredToSave_resumeSave_setsSaveResultState_toSaving() {
        whenReasonRequiredToSave();
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();

        viewModel.setReason("blah");
        viewModel.resumeSave();
        assertThat(saveResult.getValue().getState(), equalTo(SAVING));
    }

    @Test
    public void whenReasonRequiredToSave_resumeSave_logsChangeReasonAuditEvent() {
        whenReasonRequiredToSave();
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);

        viewModel.setReason("Blah");
        viewModel.resumeSave();

        verify(logger).logEvent(AuditEvent.AuditEventType.CHANGE_REASON, null, true, null, CURRENT_TIME, "Blah");
    }

    @Test
    public void whenReasonRequiredToSave_resumeSave_whenReasonIsNotValid_doesNotSave() {
        whenReasonRequiredToSave();
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();

        viewModel.setReason("");
        viewModel.resumeSave();
        assertThat(saveResult.getValue().getState(), equalTo(CHANGE_REASON_REQUIRED));

        viewModel.setReason("  ");
        viewModel.resumeSave();
        assertThat(saveResult.getValue().getState(), equalTo(CHANGE_REASON_REQUIRED));
    }

    @Test
    public void whenReasonRequiredToSave_andRecordingAudio_andExiting_resumeSave_savesRecording() {
        whenReasonRequiredToSave();
        when(audioRecorder.isRecording()).thenReturn(true);

        viewModel.saveForm(Uri.parse("file://form"), false, "", true);
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();

        viewModel.setReason("blah");
        viewModel.resumeSave();
        assertThat(saveResult.getValue().getState(), equalTo(WAITING_TO_SAVE));
    }

    @Test
    public void resumeFormEntry_clearsSaveResult() {
        LiveData<FormSaveViewModel.SaveResult> saveResult = viewModel.getSaveResult();
        viewModel.saveForm(Uri.parse("file://form"), true, "", false);
        viewModel.resumeFormEntry();
        assertThat(saveResult.getValue(), equalTo(null));
    }

    @Test
    public void ignoreChanges_whenThereAreUnsavedFiles_shouldDeleteThoseFiles() {
        viewModel.replaceAnswerFile("index", "blah1");
        viewModel.ignoreChanges();

        verify(mediaUtils).deleteMediaFile("blah1");
    }

    @Test
    public void ignoreChanges_whenAudioIsRecording_cleansUpAudioRecorder() {
        when(audioRecorder.isRecording()).thenReturn(true);
        viewModel.ignoreChanges();

        verify(audioRecorder).cleanUp();
    }

    //region QuestionMediaManager implementation

    /**
     * Covers clearing an answer, adding a new answer and then clearing again - we'd never need
     * to restore the new answer file in this case.
     */
    @Test
    public void deleteAnswerFile_whenAnswerFileHasAlreadyBeenDeleted_actuallyDeletesNewFile() {
        viewModel.deleteAnswerFile("index", "blah1");
        viewModel.deleteAnswerFile("index", "blah2");

        verify(mediaUtils).deleteMediaFile("blah2");
    }

    @Test
    public void deleteAnswerFile_whenAnswerFileHasAlreadyBeenDeleted_onRecreatingViewModel_actuallyDeletesNewFile() {
        viewModel.deleteAnswerFile("index", "blah1");

        FormSaveViewModel restoredViewModel = new FormSaveViewModel(savedStateHandle, () -> CURRENT_TIME, formSaver, mediaUtils, scheduler, mock(AudioRecorder.class), projectsDataService, liveDataOf(new FormSession(formController, form)), entitiesRepository, instancesRepository, savepointsRepository, mock());
        restoredViewModel.deleteAnswerFile("index", "blah2");

        verify(mediaUtils).deleteMediaFile("blah2");
    }

    /**
     * Covers replacing an answer, and then replacing an answer again - we'd never need
     * to restore the first replacement in this case
     */
    @Test
    public void replaceAnswerFile_whenAnswerFileHasAlreadyBeenReplaced_deletesPreviousReplacement() {
        viewModel.replaceAnswerFile("index", "blah1");
        viewModel.replaceAnswerFile("index", "blah2");

        verify(mediaUtils).deleteMediaFile("blah1");
    }

    @Test
    public void replaceAnswerFile_whenAnswerFileHasAlreadyBeenReplaced_afterRecreatingViewModel_deletesPreviousReplacement() {
        viewModel.replaceAnswerFile("index", "blah1");

        FormSaveViewModel restoredViewModel = new FormSaveViewModel(savedStateHandle, () -> CURRENT_TIME, formSaver, mediaUtils, scheduler, mock(AudioRecorder.class), projectsDataService, liveDataOf(new FormSession(formController, form)), entitiesRepository, instancesRepository, savepointsRepository, mock());
        restoredViewModel.replaceAnswerFile("index", "blah2");

        verify(mediaUtils).deleteMediaFile("blah1");
    }

    @Test
    public void getAnswerFile_returnsFileFromInstance() {
        File tempDir = Files.createTempDir();
        when(formController.getInstanceFile()).thenReturn(new File(tempDir + File.separator + "instance.xml"));

        File answerFile = viewModel.getAnswerFile("answer.file");
        assertThat(answerFile, is(new File(tempDir, "answer.file")));
    }

    @Test
    public void createAnswerFile_copiesFileToInstanceFolder_andReturnsNewName() throws Exception {
        File tempDir = Files.createTempDir();
        when(formController.getInstanceFile()).thenReturn(new File(tempDir + File.separator + "instance.xml"));

        File externalFile = File.createTempFile("external", ".file");
        LiveData<Result<File>> answerFile = viewModel.createAnswerFile(externalFile);
        scheduler.flush();

        assertThat(tempDir.listFiles().length, is(1));
        assertThat(answerFile.getValue().getOrNull().getName(), is(tempDir.listFiles()[0].getName()));
    }

    @Test
    public void createAnswerFile_whenThereIsAnError_returnsNull_andSetsAnswerFileErrorToFilePath() throws Exception {
        File tempDir = Files.createTempDir();
        tempDir.setWritable(false);
        when(formController.getInstanceFile()).thenReturn(new File(tempDir + File.separator + "instance.xml"));

        File externalFile = File.createTempFile("external", ".file");
        LiveData<Result<File>> answerFile = viewModel.createAnswerFile(externalFile);
        scheduler.flush();

        assertThat(answerFile.getValue().getOrNull(), nullValue());
        assertThat(viewModel.getAnswerFileError().getValue(), equalTo(externalFile.getAbsolutePath()));
    }

    @Test
    public void createAnswerFile_forSameFile_returnsSameName() throws Exception {
        File tempDir = Files.createTempDir();
        when(formController.getInstanceFile()).thenReturn(new File(tempDir + File.separator + "instance.xml"));

        File externalFile = File.createTempFile("external", ".file");
        LiveData<Result<File>> fileName1 = viewModel.createAnswerFile(externalFile);
        scheduler.flush();
        LiveData<Result<File>> fileName2 = viewModel.createAnswerFile(externalFile);
        scheduler.flush();

        assertThat(fileName1.getValue().getOrNull(), is(fileName2.getValue().getOrNull()));
    }

    //endregion

    @Test
    public void isSavingFileAnswerFile_isTrueWhenWhileIsSaving() throws Exception {
        File tempDir = Files.createTempDir();
        when(formController.getInstanceFile()).thenReturn(new File(tempDir + File.separator + "instance.xml"));

        assertThat(viewModel.isSavingAnswerFile().getValue(), is(false));

        viewModel.createAnswerFile(File.createTempFile("external", ".file"));
        assertThat(viewModel.isSavingAnswerFile().getValue(), is(true));

        scheduler.flush();
        assertThat(viewModel.isSavingAnswerFile().getValue(), is(false));
    }

    @Test
    public void ignoreChanges_whenFormControllerNotSet_doesNothing() {
        FormSaveViewModel viewModel = new FormSaveViewModel(savedStateHandle, () -> CURRENT_TIME, formSaver, mediaUtils, scheduler, mock(AudioRecorder.class), projectsDataService, liveDataOf(new FormSession(formController, form)), entitiesRepository, instancesRepository, savepointsRepository, mock());
        viewModel.ignoreChanges(); // Checks nothing explodes
    }

    @Test
    public void getLastSavedTime_whenNewInstance_returnsNull() {
        assertThat(viewModel.getLastSavedTime(), equalTo(null));
    }

    @Test
    public void getLastSavedTime_whenInstanceNotSaved_returnsLastStatusChange() {
        Instance instance = new Instance.Builder()
                .lastStatusChangeDate(123L)
                .build();

        formSession.setValue(new FormSession(formController, form, instance));
        assertThat(viewModel.getLastSavedTime(), equalTo(instance.getLastStatusChangeDate()));
    }

    @Test
    public void getLastSavedTime_whenInstanceSaved_returnsLastStatusChange() {
        viewModel.saveForm(Uri.parse("file://form"), false, "", false);
        whenFormSaverFinishes(SaveFormToDisk.SAVED);

        assertThat(viewModel.getLastSavedTime(), equalTo(formSaver.instance.getLastStatusChangeDate()));
    }

    private void whenReasonRequiredToSave() {
        when(formController.isEditing()).thenReturn(true);
        when(logger.isChangeReasonRequired()).thenReturn(true);
    }

    private void whenFormSaverFinishes(int result) {
        whenFormSaverFinishes(result, null);
    }

    private void whenFormSaverFinishes(int result, String message) {
        SaveToDiskResult saveToDiskResult = new SaveToDiskResult();
        saveToDiskResult.setSaveResult(result, true);
        saveToDiskResult.setSaveErrorMessage(message);

        formSaver.saveToDiskResult = saveToDiskResult;
        Robolectric.getBackgroundThreadScheduler().runOneTask();
    }

    public static class FakeFormSaver implements FormSaver {

        public SaveToDiskResult saveToDiskResult;
        public ArrayList<String> tempFiles;

        public int numberOfTimesCalled;

        public final Instance instance = new Instance.Builder()
                .lastStatusChangeDate(123L)
                .build();

        @Override
        public SaveToDiskResult save(Uri instanceContentURI, FormController formController, MediaUtils mediaUtils, boolean shouldFinalize,
                                     boolean exitAfter, String updatedSaveName, ProgressListener progressListener, ArrayList<String> tempFiles, String currentProjectId, EntitiesRepository entitiesRepository, InstancesRepository instancesRepository) {
            this.tempFiles = tempFiles;
            numberOfTimesCalled++;

            if (saveToDiskResult.getSaveResult() == SaveFormToDisk.SAVED) {
                saveToDiskResult.setInstance(new Instance.Builder()
                        .lastStatusChangeDate(123L)
                        .build());
            }

            return saveToDiskResult;
        }
    }
}
