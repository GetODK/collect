package org.odk.collect.android.formentry.saving;

import static org.odk.collect.android.tasks.SaveFormToDisk.SAVED;
import static org.odk.collect.android.tasks.SaveFormToDisk.SAVED_AND_EXIT;
import static org.odk.collect.android.tasks.SaveFormToDisk.SAVE_ERROR;
import static org.odk.collect.shared.strings.StringUtils.isBlank;

import android.net.Uri;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

import org.apache.commons.io.IOUtils;
import org.javarosa.form.api.FormEntryController;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.helpers.InstancesDaoHelper;
import org.odk.collect.android.dynamicpreload.ExternalDataManager;
import org.odk.collect.android.formentry.FormSession;
import org.odk.collect.android.formentry.audit.AuditEvent;
import org.odk.collect.android.formentry.audit.AuditUtils;
import org.odk.collect.android.instancemanagement.InstancesDataService;
import org.odk.collect.android.javarosawrapper.FormController;
import org.odk.collect.android.projects.ProjectsDataService;
import org.odk.collect.android.tasks.SaveFormToDisk;
import org.odk.collect.android.tasks.SaveToDiskResult;
import org.odk.collect.android.utilities.FileUtils;
import org.odk.collect.android.utilities.MediaUtils;
import org.odk.collect.android.utilities.QuestionMediaManager;
import org.odk.collect.androidshared.livedata.LiveDataUtils;
import org.odk.collect.async.Cancellable;
import org.odk.collect.async.Scheduler;
import org.odk.collect.audiorecorder.recording.AudioRecorder;
import org.odk.collect.entities.storage.EntitiesRepository;
import org.odk.collect.forms.Form;
import org.odk.collect.forms.instances.Instance;
import org.odk.collect.forms.instances.InstancesRepository;
import org.odk.collect.forms.savepoints.SavepointsRepository;
import org.odk.collect.material.MaterialProgressDialogFragment;
import org.odk.collect.shared.strings.Md5;
import org.odk.collect.utilities.Result;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import timber.log.Timber;

public class FormSaveViewModel extends ViewModel implements MaterialProgressDialogFragment.OnCancelCallback, QuestionMediaManager {

    public static final String ORIGINAL_FILES = "originalFiles";
    public static final String RECENT_FILES = "recentFiles";

    private final SavedStateHandle stateHandle;
    private final Supplier<Long> clock;
    private final FormSaver formSaver;
    private final MediaUtils mediaUtils;

    private final MutableLiveData<SaveResult> saveResult = new MutableLiveData<>(null);

    private String reason = "";

    private Map<String, String> originalFiles = new HashMap<>();
    private Map<String, String> recentFiles = new HashMap<>();
    private final MutableLiveData<Boolean> isSavingAnswerFile = new MutableLiveData<>(false);
    private final MutableLiveData<String> answerFileError = new MutableLiveData<>(null);


    @Nullable
    private FormController formController;

    @Nullable
    private AsyncTask<Void, String, SaveToDiskResult> saveTask;

    private final Scheduler scheduler;
    private final AudioRecorder audioRecorder;
    private final ProjectsDataService projectsDataService;
    private final EntitiesRepository entitiesRepository;
    private final InstancesRepository instancesRepository;
    private final SavepointsRepository savepointsRepository;
    private Form form;
    private Instance instance;
    private final Cancellable formSessionObserver;
    private InstancesDataService instancesDataService;

    public FormSaveViewModel(SavedStateHandle stateHandle, Supplier<Long> clock, FormSaver formSaver,
                             MediaUtils mediaUtils, Scheduler scheduler, AudioRecorder audioRecorder,
                             ProjectsDataService projectsDataService, LiveData<FormSession> formSession,
                             EntitiesRepository entitiesRepository, InstancesRepository instancesRepository,
                             SavepointsRepository savepointsRepository, InstancesDataService instancesDataService
    ) {
        this.stateHandle = stateHandle;
        this.clock = clock;
        this.formSaver = formSaver;
        this.mediaUtils = mediaUtils;
        this.scheduler = scheduler;
        this.audioRecorder = audioRecorder;
        this.projectsDataService = projectsDataService;
        this.entitiesRepository = entitiesRepository;
        this.instancesRepository = instancesRepository;
        this.savepointsRepository = savepointsRepository;
        this.instancesDataService = instancesDataService;

        if (stateHandle.get(ORIGINAL_FILES) != null) {
            originalFiles = stateHandle.get(ORIGINAL_FILES);
        }
        if (stateHandle.get(RECENT_FILES) != null) {
            recentFiles = stateHandle.get(RECENT_FILES);
        }

        formSessionObserver = LiveDataUtils.observe(formSession, it -> {
            formController = it.getFormController();
            form = it.getForm();
            instance = it.getInstance();
        });
    }

    @Override
    protected void onCleared() {
        formSessionObserver.cancel();
    }

    public void saveForm(Uri instanceContentURI, boolean shouldFinalize, String updatedSaveName, boolean viewExiting) {
        if (isSaving() || formController == null) {
            return;
        }

        SaveRequest saveRequest = new SaveRequest(instanceContentURI, viewExiting, updatedSaveName, shouldFinalize);
        formController.getAuditEventLogger().flush();

        if (requiresReasonToSave()) {
            this.saveResult.setValue(new SaveResult(SaveResult.State.CHANGE_REASON_REQUIRED, saveRequest));
        } else if (viewExiting && audioRecorder.isRecording()) {
            this.saveResult.setValue(new SaveResult(SaveResult.State.WAITING_TO_SAVE, saveRequest));
            audioRecorder.stop();
        } else {
            this.saveResult.setValue(new SaveResult(SaveResult.State.SAVING, saveRequest));
            saveToDisk(saveRequest);
        }
    }

    // Cleanup when user exits a form without saving
    public void ignoreChanges() {
        if (audioRecorder.isRecording()) {
            audioRecorder.cleanUp();
        }

        ExternalDataManager manager = Collect.getInstance().getExternalDataManager();
        if (manager != null) {
            manager.close();
        }

        if (formController != null) {
            formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.FORM_EXIT, true, System.currentTimeMillis());

            if (formController.getInstanceFile() != null) {
                removeSavepoint(form.getDbId(), instance != null ? instance.getDbId() : null);
                SaveFormToDisk.removeIndexFile(formController.getInstanceFile().getName());

                // if it's not already saved, erase everything
                if (!InstancesDaoHelper.isInstanceAvailable(getAbsoluteInstancePath())) {
                    String instanceFolder = formController.getInstanceFile().getParent();
                    FileUtils.purgeMediaPath(instanceFolder);
                }
            }
        }

        for (String filePath : recentFiles.values()) {
            mediaUtils.deleteMediaFile(filePath);
        }

        clearMediaFiles();
    }

    public void resumeSave() {
        if (saveResult.getValue() != null) {
            SaveRequest saveRequest = saveResult.getValue().request;

            if (saveResult.getValue().getState() == SaveResult.State.CHANGE_REASON_REQUIRED) {
                if (!saveReason()) {
                    return;
                } else if (saveRequest.viewExiting && audioRecorder.isRecording()) {
                    this.saveResult.setValue(new SaveResult(SaveResult.State.WAITING_TO_SAVE, saveRequest));
                    audioRecorder.stop();
                    return;
                }
            }

            this.saveResult.setValue(new SaveResult(SaveResult.State.SAVING, saveRequest));
            saveToDisk(saveRequest);
        }
    }

    @Nullable
    public String getAbsoluteInstancePath() {
        return formController != null ? formController.getAbsoluteInstancePath() : null;
    }

    public boolean isSaving() {
        return saveResult.getValue() != null && saveResult.getValue().getState().equals(SaveResult.State.SAVING);
    }

    @Override
    public boolean cancel() {
        if (saveTask != null) {
            return saveTask.cancel(true);
        } else {
            return false;
        }
    }

    public void setReason(@NonNull String reason) {
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }

    private boolean saveReason() {
        if (reason == null || isBlank(reason) || formController == null) {
            return false;
        }

        formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.CHANGE_REASON, null, true, null, clock.get(), reason);
        return true;
    }

    private void saveToDisk(SaveRequest saveRequest) {
        saveTask = new SaveTask(saveRequest, formSaver, formController, mediaUtils, new SaveTask.Listener() {
            @Override
            public void onProgressPublished(String progress) {
                saveResult.setValue(new SaveResult(SaveResult.State.SAVING, saveRequest, progress));
            }

            @Override
            public void onComplete(SaveToDiskResult saveToDiskResult) {
                handleTaskResult(saveToDiskResult, saveRequest);
                clearMediaFiles();
            }
        }, new ArrayList<>(originalFiles.values()), projectsDataService.requireCurrentProject().getUuid(), entitiesRepository, instancesRepository).execute();
    }

    private void handleTaskResult(SaveToDiskResult taskResult, SaveRequest saveRequest) {
        if (formController == null) {
            return;
        }

        if (taskResult.getSaveResult() != SAVE_ERROR) {
            removeSavepoint(form.getDbId(), instance != null ? instance.getDbId() : null);
        }

        instance = taskResult.getInstance();

        switch (taskResult.getSaveResult()) {
            case SAVED:
            case SAVED_AND_EXIT: {
                formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.FORM_SAVE, false, clock.get());

                if (saveRequest.viewExiting) {
                    if (saveRequest.shouldFinalize) {
                        formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.FORM_EXIT, false, clock.get());
                        formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.FORM_FINALIZE, true, clock.get());

                        instancesDataService.instanceFinalized(projectsDataService.requireCurrentProject().getUuid(), form);
                    } else {
                        formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.FORM_EXIT, true, clock.get());
                    }
                } else {
                    AuditUtils.logCurrentScreen(formController, formController.getAuditEventLogger(), clock.get());
                }

                saveResult.setValue(new SaveResult(SaveResult.State.SAVED, saveRequest, taskResult.getSaveErrorMessage()));
                break;
            }

            case SAVE_ERROR: {
                formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.SAVE_ERROR, true, clock.get());
                saveResult.setValue(new SaveResult(SaveResult.State.SAVE_ERROR, saveRequest, taskResult.getSaveErrorMessage()));
                break;
            }

            case SaveFormToDisk.ENCRYPTION_ERROR: {
                formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.FINALIZE_ERROR, true, clock.get());
                saveResult.setValue(new SaveResult(SaveResult.State.FINALIZE_ERROR, saveRequest, taskResult.getSaveErrorMessage()));
                break;
            }

            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY: {
                formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.CONSTRAINT_ERROR, true, clock.get());
                saveResult.setValue(new SaveResult(SaveResult.State.CONSTRAINT_ERROR, saveRequest, taskResult.getSaveErrorMessage()));
                break;
            }
        }
    }

    public LiveData<SaveResult> getSaveResult() {
        return saveResult;
    }

    public void resumeFormEntry() {
        saveResult.setValue(null);
    }

    private boolean requiresReasonToSave() {
        return formController != null
                && formController.isEditing()
                && formController.getAuditEventLogger().isChangeReasonRequired();
    }

    public String getFormName() {
        if (formController == null) {
            return null;
        }
        return formController.getFormTitle();
    }

    @Override
    public void deleteAnswerFile(String questionIndex, String fileName) {
        if (questionIndex != null && fileName != null) {
            // We don't want to delete the "original" answer file as we might need to restore it
            // but we can delete any follow up deletions
            if (originalFiles.containsKey(questionIndex)) {
                mediaUtils.deleteMediaFile(fileName);
            } else {
                originalFiles.put(questionIndex, fileName);
                stateHandle.set(ORIGINAL_FILES, originalFiles);
            }
        }
    }

    @Override
    public void replaceAnswerFile(String questionIndex, String fileName) {
        if (questionIndex != null && fileName != null) {
            // If we're replacing an answer's file for a second time we can just get rid of the
            // first (replacement) file we were going to use
            if (recentFiles.containsKey(questionIndex)) {
                mediaUtils.deleteMediaFile(recentFiles.get(questionIndex));
            }
            recentFiles.put(questionIndex, fileName);
            stateHandle.set(RECENT_FILES, recentFiles);
        }
    }

    @Override
    public LiveData<Result<File>> createAnswerFile(File file) {
        MutableLiveData<Result<File>> liveData = new MutableLiveData<>(null);

        isSavingAnswerFile.setValue(true);
        scheduler.immediate(() -> {
            String newFileHash = Md5.getMd5Hash(file);
            String instanceDir = formController.getInstanceFile().getParent();

            File[] answerFiles = new File(instanceDir).listFiles();
            for (File answerFile : answerFiles) {
                if (Md5.getMd5Hash(answerFile).equals(newFileHash)) {
                    return answerFile;
                }
            }

            String fileName = file.getName();
            String extension = fileName.substring(fileName.lastIndexOf('.') + 1);
            String newFileName = System.currentTimeMillis() + "." + extension;
            String newFilePath = instanceDir + File.separator + newFileName;

            try (InputStream inputStream = new FileInputStream(file)) {
                try (OutputStream outputStream = new FileOutputStream(newFilePath)) {
                    IOUtils.copy(inputStream, outputStream);
                }
            } catch (IOException e) {
                Timber.e(e);
                return null;
            }

            return new File(newFilePath);
        }, answerFile -> {
            liveData.setValue(new Result<>(answerFile));
            isSavingAnswerFile.setValue(false);

            if (answerFile == null) {
                answerFileError.setValue(file.getAbsolutePath());
            }
        });

        return liveData;
    }

    @Override
    @Nullable
    public File getAnswerFile(String fileName) {
        if (formController != null && formController.getInstanceFile() != null) {
            return new File(formController.getInstanceFile().getParent(), fileName);
        } else {
            return null;
        }
    }

    public LiveData<Boolean> isSavingAnswerFile() {
        return isSavingAnswerFile;
    }

    private void clearMediaFiles() {
        originalFiles.clear();
        recentFiles.clear();
    }

    public LiveData<String> getAnswerFileError() {
        return answerFileError;
    }

    public void answerFileErrorDisplayed() {
        answerFileError.setValue(null);
    }

    public Long getLastSavedTime() {
        return instance != null ? instance.getLastStatusChangeDate() : null;
    }

    @Nullable
    public Instance getInstance() {
        return instance;
    }

    private void removeSavepoint(long formDbId, @Nullable Long instanceDbId) {
        scheduler.immediate(() -> {
            savepointsRepository.delete(formDbId, instanceDbId);
            return null;
        }, result -> {
        });
    }

    public static class SaveResult {
        private final State state;
        private final String message;
        private final SaveRequest request;

        SaveResult(State state, SaveRequest request) {
            this(state, request, null);
        }

        SaveResult(State state, SaveRequest request, String message) {
            this.state = state;
            this.message = message;
            this.request = request;
        }

        public State getState() {
            return state;
        }

        public String getMessage() {
            return message;
        }

        public enum State {
            CHANGE_REASON_REQUIRED,
            SAVING,
            SAVED,
            SAVE_ERROR,
            FINALIZE_ERROR,
            CONSTRAINT_ERROR,
            WAITING_TO_SAVE
        }

        public SaveRequest getRequest() {
            return request;
        }
    }

    public static class SaveRequest {

        private final boolean shouldFinalize;
        private final boolean viewExiting;
        private final String updatedSaveName;
        private final Uri uri;

        SaveRequest(Uri instanceContentURI, boolean viewExiting, String updatedSaveName, boolean shouldFinalize) {
            this.shouldFinalize = shouldFinalize;
            this.viewExiting = viewExiting;
            this.updatedSaveName = updatedSaveName;
            this.uri = instanceContentURI;
        }

        public boolean shouldFinalize() {
            return shouldFinalize;
        }

        public boolean viewExiting() {
            return viewExiting;
        }
    }

    private static class SaveTask extends AsyncTask<Void, String, SaveToDiskResult> {

        private final SaveRequest saveRequest;
        private final FormSaver formSaver;

        private final Listener listener;
        private final FormController formController;
        private final MediaUtils mediaUtils;
        private final ArrayList<String> tempFiles;
        private final String currentProjectId;
        private final EntitiesRepository entitiesRepository;
        private final InstancesRepository instancesRepository;

        SaveTask(SaveRequest saveRequest, FormSaver formSaver, FormController formController, MediaUtils mediaUtils,
                 Listener listener, ArrayList<String> tempFiles, String currentProjectId, EntitiesRepository entitiesRepository, InstancesRepository instancesRepository) {
            this.saveRequest = saveRequest;
            this.formSaver = formSaver;
            this.listener = listener;
            this.formController = formController;
            this.mediaUtils = mediaUtils;
            this.tempFiles = tempFiles;
            this.currentProjectId = currentProjectId;
            this.entitiesRepository = entitiesRepository;
            this.instancesRepository = instancesRepository;
        }

        @Override
        protected SaveToDiskResult doInBackground(Void... voids) {
            return formSaver.save(saveRequest.uri, formController,
                    mediaUtils, saveRequest.shouldFinalize,
                    saveRequest.viewExiting, saveRequest.updatedSaveName,
                    this::publishProgress, tempFiles,
                    currentProjectId, entitiesRepository, instancesRepository);
        }

        @Override
        protected void onProgressUpdate(String... values) {
            listener.onProgressPublished(values[0]);
        }

        @Override
        protected void onPostExecute(SaveToDiskResult saveToDiskResult) {
            listener.onComplete(saveToDiskResult);
        }

        interface Listener {
            void onProgressPublished(String progress);

            void onComplete(SaveToDiskResult saveToDiskResult);
        }
    }
}
