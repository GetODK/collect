/*
 * Copyright (C) 2009 University of Washington
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

package org.odk.collect.android.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;

import org.javarosa.core.model.FormDef;
import org.javarosa.core.model.FormIndex;
import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.form.api.FormEntryCaption;
import org.javarosa.form.api.FormEntryController;
import org.javarosa.form.api.FormEntryPrompt;
import org.jetbrains.annotations.NotNull;
import org.joda.time.DateTime;
import org.joda.time.LocalDateTime;
import org.odk.collect.analytics.Analytics;
import org.odk.collect.android.R;
import org.odk.collect.android.analytics.AnalyticsEvents;
import org.odk.collect.android.analytics.AnalyticsUtils;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.audio.AMRAppender;
import org.odk.collect.android.audio.AudioControllerView;
import org.odk.collect.android.audio.M4AAppender;
import org.odk.collect.android.backgroundwork.InstanceSubmitScheduler;
import org.odk.collect.android.dao.helpers.InstancesDaoHelper;
import org.odk.collect.android.events.ReadPhoneStatePermissionRxEvent;
import org.odk.collect.android.events.RxEventBus;
import org.odk.collect.android.exception.JavaRosaException;
import org.odk.collect.android.external.FormsContract;
import org.odk.collect.android.external.InstancesContract;
import org.odk.collect.android.formentry.BackgroundAudioPermissionDialogFragment;
import org.odk.collect.android.formentry.BackgroundAudioViewModel;
import org.odk.collect.android.formentry.FormEndView;
import org.odk.collect.android.formentry.FormEntryMenuDelegate;
import org.odk.collect.android.formentry.FormEntryViewModel;
import org.odk.collect.android.formentry.FormIndexAnimationHandler;
import org.odk.collect.android.formentry.FormIndexAnimationHandler.Direction;
import org.odk.collect.android.formentry.FormLoadingDialogFragment;
import org.odk.collect.android.formentry.ODKView;
import org.odk.collect.android.formentry.QuitFormDialogFragment;
import org.odk.collect.android.formentry.RecordingHandler;
import org.odk.collect.android.formentry.RecordingWarningDialogFragment;
import org.odk.collect.android.formentry.audit.AuditEvent;
import org.odk.collect.android.formentry.audit.AuditUtils;
import org.odk.collect.android.formentry.audit.ChangesReasonPromptDialogFragment;
import org.odk.collect.android.formentry.audit.IdentifyUserPromptDialogFragment;
import org.odk.collect.android.formentry.audit.IdentityPromptViewModel;
import org.odk.collect.android.formentry.backgroundlocation.BackgroundLocationManager;
import org.odk.collect.android.formentry.backgroundlocation.BackgroundLocationViewModel;
import org.odk.collect.android.formentry.loading.FormInstanceFileCreator;
import org.odk.collect.android.formentry.repeats.AddRepeatDialog;
import org.odk.collect.android.formentry.repeats.DeleteRepeatDialogFragment;
import org.odk.collect.android.formentry.saving.FormSaveViewModel;
import org.odk.collect.android.formentry.saving.SaveAnswerFileErrorDialogFragment;
import org.odk.collect.android.formentry.saving.SaveAnswerFileProgressDialogFragment;
import org.odk.collect.android.formentry.saving.SaveFormProgressDialogFragment;
import org.odk.collect.android.fragments.MediaLoadingFragment;
import org.odk.collect.android.fragments.dialogs.CustomDatePickerDialog;
import org.odk.collect.android.fragments.dialogs.CustomTimePickerDialog;
import org.odk.collect.android.fragments.dialogs.LocationProvidersDisabledDialog;
import org.odk.collect.android.fragments.dialogs.NumberPickerDialog;
import org.odk.collect.android.fragments.dialogs.ProgressDialogFragment;
import org.odk.collect.android.fragments.dialogs.RankingWidgetDialog;
import org.odk.collect.android.fragments.dialogs.SelectMinimalDialog;
import org.odk.collect.android.instancemanagement.InstanceDeleter;
import org.odk.collect.android.javarosawrapper.FormController;
import org.odk.collect.android.javarosawrapper.FormController.FailedConstraint;
import org.odk.collect.android.javarosawrapper.FormDesignException;
import org.odk.collect.android.listeners.AdvanceToNextListener;
import org.odk.collect.android.listeners.FormLoaderListener;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.listeners.SavePointListener;
import org.odk.collect.android.listeners.SwipeHandler;
import org.odk.collect.android.listeners.WidgetValueChangedListener;
import org.odk.collect.android.logic.ImmutableDisplayableQuestion;
import org.odk.collect.android.logic.PropertyManager;
import org.odk.collect.android.permissions.PermissionsChecker;
import org.odk.collect.android.preferences.keys.ProtectedProjectKeys;
import org.odk.collect.android.preferences.keys.ProjectKeys;
import org.odk.collect.android.projects.CurrentProjectProvider;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.storage.StorageSubdirectory;
import org.odk.collect.android.tasks.FormLoaderTask;
import org.odk.collect.android.tasks.SaveFormIndexTask;
import org.odk.collect.android.tasks.SavePointTask;
import org.odk.collect.android.utilities.ActivityAvailability;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.ContentUriHelper;
import org.odk.collect.android.utilities.DestroyableLifecyleOwner;
import org.odk.collect.android.utilities.DialogUtils;
import org.odk.collect.android.utilities.ExternalAppIntentProvider;
import org.odk.collect.android.utilities.FormsRepositoryProvider;
import org.odk.collect.android.utilities.InstancesRepositoryProvider;
import org.odk.collect.android.utilities.MultiClickGuard;
import org.odk.collect.android.utilities.PlayServicesChecker;
import org.odk.collect.android.utilities.ScreenContext;
import org.odk.collect.android.utilities.SnackbarUtils;
import org.odk.collect.android.utilities.SoftKeyboardController;
import org.odk.collect.androidshared.utils.ToastUtils;
import org.odk.collect.android.widgets.DateTimeWidget;
import org.odk.collect.android.widgets.QuestionWidget;
import org.odk.collect.android.widgets.RangePickerDecimalWidget;
import org.odk.collect.android.widgets.RangePickerIntegerWidget;
import org.odk.collect.android.widgets.interfaces.WidgetDataReceiver;
import org.odk.collect.android.widgets.utilities.ExternalAppRecordingRequester;
import org.odk.collect.android.widgets.utilities.FormControllerWaitingForDataRegistry;
import org.odk.collect.android.widgets.utilities.InternalRecordingRequester;
import org.odk.collect.android.widgets.utilities.ViewModelAudioPlayer;
import org.odk.collect.android.widgets.utilities.WaitingForDataRegistry;
import org.odk.collect.async.Scheduler;
import org.odk.collect.audioclips.AudioClipViewModel;
import org.odk.collect.audiorecorder.recording.AudioRecorder;
import org.odk.collect.forms.Form;
import org.odk.collect.forms.FormsRepository;
import org.odk.collect.forms.instances.Instance;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static android.content.DialogInterface.BUTTON_POSITIVE;
import static android.view.animation.AnimationUtils.loadAnimation;
import static org.javarosa.form.api.FormEntryController.EVENT_PROMPT_NEW_REPEAT;
import static org.odk.collect.android.analytics.AnalyticsEvents.OPEN_MAP_KIT_RESPONSE;
import static org.odk.collect.android.analytics.AnalyticsEvents.SAVE_INCOMPLETE;
import static org.odk.collect.android.formentry.FormIndexAnimationHandler.Direction.BACKWARDS;
import static org.odk.collect.android.formentry.FormIndexAnimationHandler.Direction.FORWARDS;
import static org.odk.collect.android.preferences.keys.ProtectedProjectKeys.KEY_MOVING_BACKWARDS;
import static org.odk.collect.android.preferences.keys.ProjectKeys.KEY_COMPLETED_DEFAULT;
import static org.odk.collect.android.preferences.keys.ProjectKeys.KEY_NAVIGATION;
import static org.odk.collect.android.utilities.AnimationUtils.areAnimationsEnabled;
import static org.odk.collect.android.utilities.ApplicationConstants.RequestCodes;
import static org.odk.collect.android.utilities.DialogUtils.getDialog;
import static org.odk.collect.android.utilities.DialogUtils.showIfNotShowing;
import static org.odk.collect.androidshared.utils.ToastUtils.showLongToast;
import static org.odk.collect.androidshared.utils.ToastUtils.showShortToast;

/**
 * FormEntryActivity is responsible for displaying questions, animating
 * transitions between questions, and allowing the user to enter data.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Thomas Smyth, Sassafras Tech Collective (tom@sassafrastech.com; constraint behavior
 * option)
 */

@SuppressWarnings("PMD.CouplingBetweenObjects")
public class FormEntryActivity extends CollectAbstractActivity implements AnimationListener,
        FormLoaderListener, AdvanceToNextListener, SwipeHandler.OnSwipeListener,
        SavePointListener, NumberPickerDialog.NumberPickerListener,
        RankingWidgetDialog.RankingListener, SaveFormIndexTask.SaveFormIndexListener,
        WidgetValueChangedListener, ScreenContext, FormLoadingDialogFragment.FormLoadingDialogFragmentListener,
        AudioControllerView.SwipableParent, FormIndexAnimationHandler.Listener,
        QuitFormDialogFragment.Listener, DeleteRepeatDialogFragment.DeleteRepeatDialogCallback,
        SelectMinimalDialog.SelectMinimalDialogListener, CustomDatePickerDialog.DateChangeListener,
        CustomTimePickerDialog.TimeChangeListener {

    private static final boolean EVALUATE_CONSTRAINTS = true;
    public static final boolean DO_NOT_EVALUATE_CONSTRAINTS = false;

    public static final String ANSWER_KEY = "value"; // this value can not be changed because it is also used by external apps

    public static final String KEY_INSTANCES = "instances";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_ERROR = "error";
    private static final String KEY_SAVE_NAME = "saveName";
    private static final String KEY_LOCATION_PERMISSIONS_GRANTED = "location_permissions_granted";

    private static final String TAG_MEDIA_LOADING_FRAGMENT = "media_loading_fragment";

    // Identifies the gp of the form used to launch form entry
    public static final String KEY_FORMPATH = "formpath";

    // Identifies whether this is a new form, or reloading a form after a screen
    // rotation (or similar)
    private static final String NEWFORM = "newform";
    // these are only processed if we shut down and are restoring after an
    // external intent fires

    public static final String KEY_INSTANCEPATH = "instancepath";
    public static final String KEY_XPATH = "xpath";
    public static final String KEY_XPATH_WAITING_FOR_DATA = "xpathwaiting";

    // Tracks whether we are autosaving
    public static final String KEY_AUTO_SAVED = "autosaved";

    public static final String KEY_READ_PHONE_STATE_PERMISSION_REQUEST_NEEDED = "readPhoneStatePermissionRequestNeeded";

    private boolean autoSaved;
    private boolean allowMovingBackwards;

    // Random ID
    private static final int DELETE_REPEAT = 654321;

    private String formPath;
    private String saveName;

    private Animation inAnimation;
    private Animation outAnimation;

    private FrameLayout questionHolder;
    private View currentView;

    private AlertDialog alertDialog;
    private String errorMessage;
    private boolean shownAlertDialogIsGroupRepeat;

    private FormLoaderTask formLoaderTask;

    private TextView nextButton;
    private TextView backButton;

    private ODKView odkView;
    private final DestroyableLifecyleOwner odkViewLifecycle = new DestroyableLifecyleOwner();

    private String instancePath;
    private String startingXPath;
    private String waitingXPath;
    private boolean newForm = true;
    private boolean readPhoneStatePermissionRequestNeeded;

    private final CompositeDisposable compositeDisposable = new CompositeDisposable();

    MediaLoadingFragment mediaLoadingFragment;
    private FormEntryMenuDelegate menuDelegate;
    private FormIndexAnimationHandler formIndexAnimationHandler;
    private WaitingForDataRegistry waitingForDataRegistry;
    private InternalRecordingRequester internalRecordingRequester;
    private ExternalAppRecordingRequester externalAppRecordingRequester;

    @Override
    public void allowSwiping(boolean doSwipe) {
        swipeHandler.setAllowSwiping(doSwipe);
    }

    enum AnimationType {
        LEFT, RIGHT, FADE
    }

    private boolean showNavigationButtons;

    @Inject
    RxEventBus eventBus;

    @Inject
    Analytics analytics;

    @Inject
    StoragePathProvider storagePathProvider;

    @Inject
    FormsRepositoryProvider formsRepositoryProvider;
    private FormsRepository formsRepository;

    @Inject
    PropertyManager propertyManager;

    @Inject
    InstanceSubmitScheduler instanceSubmitScheduler;

    @Inject
    Scheduler scheduler;

    @Inject
    AudioRecorder audioRecorder;

    @Inject
    FormSaveViewModel.FactoryFactory formSaveViewModelFactoryFactory;

    @Inject
    FormEntryViewModel.Factory formEntryViewModelFactory;

    @Inject
    SoftKeyboardController softKeyboardController;

    @Inject
    PermissionsChecker permissionsChecker;

    @Inject
    ActivityAvailability activityAvailability;

    @Inject
    ExternalAppIntentProvider externalAppIntentProvider;

    @Inject
    BackgroundAudioViewModel.Factory backgroundAudioViewModelFactory;

    @Inject
    CurrentProjectProvider currentProjectProvider;

    private final LocationProvidersReceiver locationProvidersReceiver = new LocationProvidersReceiver();

    private SwipeHandler swipeHandler;

    /**
     * True if the Android location permission was granted last time it was checked. Allows for
     * detection of location permissions changes while the activity is in the background.
     */
    private boolean locationPermissionsPreviouslyGranted;

    private BackgroundLocationViewModel backgroundLocationViewModel;
    private IdentityPromptViewModel identityPromptViewModel;
    private FormSaveViewModel formSaveViewModel;
    private FormEntryViewModel formEntryViewModel;
    private BackgroundAudioViewModel backgroundAudioViewModel;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Workaround for https://issuetracker.google.com/issues/37124582. Some widgets trigger
        // this issue by including WebViews
        if (Build.VERSION.SDK_INT >= 24) {
            new WebView(this);
        }

        super.onCreate(savedInstanceState);

        Collect.getInstance().getComponent().inject(this);
        formsRepository = formsRepositoryProvider.get();

        setContentView(R.layout.form_entry);
        setupViewModels();
        swipeHandler = new SwipeHandler(this, settingsProvider.getGeneralSettings());

        compositeDisposable.add(eventBus
                .register(ReadPhoneStatePermissionRxEvent.class)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    readPhoneStatePermissionRequestNeeded = true;
                }));

        errorMessage = null;

        questionHolder = findViewById(R.id.questionholder);

        initToolbar();

        formIndexAnimationHandler = new FormIndexAnimationHandler(this);
        menuDelegate = new FormEntryMenuDelegate(
                this,
                () -> getAnswers(),
                formIndexAnimationHandler,
                formSaveViewModel,
                formEntryViewModel,
                audioRecorder,
                backgroundLocationViewModel,
                backgroundAudioViewModel,
                settingsProvider
        );

        nextButton = findViewById(R.id.form_forward_button);
        nextButton.setOnClickListener(v -> {
            swipeHandler.setBeenSwiped(true);
            onSwipeForward();
        });

        backButton = findViewById(R.id.form_back_button);
        backButton.setOnClickListener(v -> {
            swipeHandler.setBeenSwiped(true);
            onSwipeBackward();
        });

        if (savedInstanceState == null) {
            mediaLoadingFragment = new MediaLoadingFragment();
            getSupportFragmentManager().beginTransaction().add(mediaLoadingFragment, TAG_MEDIA_LOADING_FRAGMENT).commit();
        } else {
            mediaLoadingFragment = (MediaLoadingFragment) getSupportFragmentManager().findFragmentByTag(TAG_MEDIA_LOADING_FRAGMENT);
        }

        setupFields(savedInstanceState);
        loadForm();
    }

    private void setupViewModels() {
        backgroundLocationViewModel = ViewModelProviders
                .of(this, new BackgroundLocationViewModel.Factory(permissionsProvider, settingsProvider.getGeneralSettings()))
                .get(BackgroundLocationViewModel.class);

        backgroundAudioViewModel = new ViewModelProvider(this, backgroundAudioViewModelFactory).get(BackgroundAudioViewModel.class);
        backgroundAudioViewModel.isPermissionRequired().observe(this, isPermissionRequired -> {
            if (isPermissionRequired) {
                showIfNotShowing(BackgroundAudioPermissionDialogFragment.class, getSupportFragmentManager());
            }
        });


        identityPromptViewModel = ViewModelProviders.of(this).get(IdentityPromptViewModel.class);
        identityPromptViewModel.requiresIdentityToContinue().observe(this, requiresIdentity -> {
            if (requiresIdentity) {
                showIfNotShowing(IdentifyUserPromptDialogFragment.class, getSupportFragmentManager());
            }
        });

        identityPromptViewModel.isFormEntryCancelled().observe(this, isFormEntryCancelled -> {
            if (isFormEntryCancelled) {
                finish();
            }
        });

        formEntryViewModel = ViewModelProviders
                .of(this, formEntryViewModelFactory)
                .get(FormEntryViewModel.class);

        formEntryViewModel.getError().observe(this, error -> {
            if (error instanceof FormEntryViewModel.NonFatal) {
                createErrorDialog(((FormEntryViewModel.NonFatal) error).getMessage(), false);
                formEntryViewModel.errorDisplayed();
            }
        });

        formSaveViewModel = new ViewModelProvider(this, formSaveViewModelFactoryFactory.create(this, null)).get(FormSaveViewModel.class);
        formSaveViewModel.getSaveResult().observe(this, this::handleSaveResult);
        formSaveViewModel.isSavingAnswerFile().observe(this, isSavingAnswerFile -> {
            if (isSavingAnswerFile) {
                DialogUtils.showIfNotShowing(SaveAnswerFileProgressDialogFragment.class, getSupportFragmentManager());
            } else {
                DialogUtils.dismissDialog(SaveAnswerFileProgressDialogFragment.class, getSupportFragmentManager());
            }
        });

        formSaveViewModel.getAnswerFileError().observe(this, file -> {
            if (file != null) {
                DialogUtils.showIfNotShowing(SaveAnswerFileErrorDialogFragment.class, getSupportFragmentManager());
            }
        });

        internalRecordingRequester = new InternalRecordingRequester(this, audioRecorder, permissionsProvider, formEntryViewModel);

        waitingForDataRegistry = new FormControllerWaitingForDataRegistry();
        externalAppRecordingRequester = new ExternalAppRecordingRequester(this, activityAvailability, waitingForDataRegistry, permissionsProvider, formEntryViewModel);

        RecordingHandler recordingHandler = new RecordingHandler(formSaveViewModel, this, audioRecorder, new AMRAppender(), new M4AAppender());
        audioRecorder.getCurrentSession().observe(this, session -> {
            if (session != null && session.getFile() != null) {
                recordingHandler.handle(getFormController(), session, success -> {
                    if (success) {
                        onScreenRefresh();
                        formSaveViewModel.resumeSave();
                    } else {
                        String path = session.getFile().getAbsolutePath();
                        String message = getString(R.string.answer_file_copy_failed_message, path);
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    // Precondition: the instance directory must be ready so that the audit file can be created
    private void formControllerAvailable(@NonNull FormController formController) {
        menuDelegate.formLoaded(formController);

        identityPromptViewModel.formLoaded(formController);
        formEntryViewModel.formLoaded(formController);
        formSaveViewModel.formLoaded(formController);
        backgroundAudioViewModel.formLoaded(formController);
    }

    private void setupFields(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(KEY_FORMPATH)) {
                formPath = savedInstanceState.getString(KEY_FORMPATH);
            }
            if (savedInstanceState.containsKey(KEY_INSTANCEPATH)) {
                instancePath = savedInstanceState.getString(KEY_INSTANCEPATH);
            }
            if (savedInstanceState.containsKey(KEY_XPATH)) {
                startingXPath = savedInstanceState.getString(KEY_XPATH);
                Timber.i("startingXPath is: %s", startingXPath);
            }
            if (savedInstanceState.containsKey(KEY_XPATH_WAITING_FOR_DATA)) {
                waitingXPath = savedInstanceState
                        .getString(KEY_XPATH_WAITING_FOR_DATA);
                Timber.i("waitingXPath is: %s", waitingXPath);
            }
            if (savedInstanceState.containsKey(NEWFORM)) {
                newForm = savedInstanceState.getBoolean(NEWFORM, true);
            }
            if (savedInstanceState.containsKey(KEY_ERROR)) {
                errorMessage = savedInstanceState.getString(KEY_ERROR);
            }
            saveName = savedInstanceState.getString(KEY_SAVE_NAME);
            if (savedInstanceState.containsKey(KEY_AUTO_SAVED)) {
                autoSaved = savedInstanceState.getBoolean(KEY_AUTO_SAVED);
            }
            if (savedInstanceState.containsKey(KEY_READ_PHONE_STATE_PERMISSION_REQUEST_NEEDED)) {
                readPhoneStatePermissionRequestNeeded = savedInstanceState.getBoolean(KEY_READ_PHONE_STATE_PERMISSION_REQUEST_NEEDED);
            }
            if (savedInstanceState.containsKey(KEY_LOCATION_PERMISSIONS_GRANTED)) {
                locationPermissionsPreviouslyGranted = savedInstanceState.getBoolean(KEY_LOCATION_PERMISSIONS_GRANTED);
            }
        }
    }

    private void loadForm() {
        propertyManager.reload();
        allowMovingBackwards = settingsProvider.getAdminSettings().getBoolean(KEY_MOVING_BACKWARDS);

        // If a parse error message is showing then nothing else is loaded
        // Dialogs mid form just disappear on rotation.
        if (errorMessage != null) {
            createErrorDialog(errorMessage, true);
            return;
        }

        // Check to see if this is a screen flip or a new form load.
        Object data = getLastCustomNonConfigurationInstance();
        if (data instanceof FormLoaderTask) {
            formLoaderTask = (FormLoaderTask) data;
        } else if (data == null) {
            if (!newForm) {
                FormController formController = getFormController();

                if (formController != null) {
                    formControllerAvailable(formController);
                    onScreenRefresh();
                } else {
                    Timber.w("Reloading form and restoring state.");
                    formLoaderTask = new FormLoaderTask(instancePath, startingXPath, waitingXPath);
                    showIfNotShowing(FormLoadingDialogFragment.class, getSupportFragmentManager());
                    formLoaderTask.execute(formPath);
                }

                return;
            }

            // Not a restart from a screen orientation change (or other).
            Collect.getInstance().setFormController(null);

            Intent intent = getIntent();
            if (intent != null) {
                loadFromIntent(intent);
            }
        }
    }

    private void loadFromIntent(Intent intent) {
        Uri uri = intent.getData();
        String uriMimeType = null;

        if (uri != null) {
            uriMimeType = getContentResolver().getType(uri);
        }

        if (uriMimeType != null && uriMimeType.equals(InstancesContract.CONTENT_ITEM_TYPE)) {
            Instance instance = new InstancesRepositoryProvider(Collect.getInstance()).get().get(ContentUriHelper.getIdFromUri(uri));

            if (instance == null) {
                createErrorDialog(getString(R.string.bad_uri, uri), true);
                return;
            }

            instancePath = instance.getInstanceFilePath();
            if (!new File(instancePath).exists()) {
                analytics.logEvent(AnalyticsEvents.OPEN_DELETED_INSTANCE);
                new InstanceDeleter(new InstancesRepositoryProvider(Collect.getInstance()).get(), formsRepository).delete(instance.getDbId());
                createErrorDialog(getString(R.string.instance_deleted_message), true);
                return;
            }

            List<Form> candidateForms = formsRepository.getAllByFormIdAndVersion(instance.getFormId(), instance.getFormVersion());

            if (candidateForms.isEmpty()) {
                createErrorDialog(getString(
                        R.string.parent_form_not_present,
                        instance.getFormId())
                                + ((instance.getFormVersion() == null) ? ""
                                : "\n" + getString(R.string.version) + " " + instance.getFormVersion()),
                        true);
                return;
            } else if (candidateForms.stream().filter(f -> !f.isDeleted()).count() > 1) {
                createErrorDialog(getString(R.string.survey_multiple_forms_error), true);
                return;
            }

            formPath = candidateForms.get(0).getFormFilePath();
        } else if (uriMimeType != null && uriMimeType.equals(FormsContract.CONTENT_ITEM_TYPE)) {
            Form form = formsRepositoryProvider.get().get(ContentUriHelper.getIdFromUri(uri));
            if (form != null) {
                formPath = form.getFormFilePath();
            }

            if (formPath == null) {
                createErrorDialog(getString(R.string.bad_uri, uri), true);
                return;
            } else {
                /**
                 * This is the fill-blank-form code path.See if there is a savepoint for this form
                 * that has never been explicitly saved by the user. If there is, open this savepoint(resume this filled-in form).
                 * Savepoints for forms that were explicitly saved will be recovered when that
                 * explicitly saved instance is edited via edit-saved-form.
                 */
                instancePath = loadSavePoint();
            }
        } else {
            Timber.i("Unrecognized URI: %s", uri);
            createErrorDialog(getString(R.string.unrecognized_uri, uri), true);
            return;
        }

        formLoaderTask = new FormLoaderTask(instancePath, null, null);
        showIfNotShowing(FormLoadingDialogFragment.class, getSupportFragmentManager());
        formLoaderTask.execute(formPath);
    }

    private String loadSavePoint() {
        final String filePrefix = formPath.substring(
                formPath.lastIndexOf('/') + 1,
                formPath.lastIndexOf('.'))
                + "_";
        final String fileSuffix = ".xml.save";
        File cacheDir = new File(storagePathProvider.getOdkDirPath(StorageSubdirectory.CACHE));
        File[] files = cacheDir.listFiles(pathname -> {
            String name = pathname.getName();
            return name.startsWith(filePrefix)
                    && name.endsWith(fileSuffix);
        });

        if (files != null) {
            /**
             * See if any of these savepoints are for a filled-in form that has never
             * been explicitly saved by the user.
             */
            for (File candidate : files) {
                String instanceDirName = candidate.getName()
                        .substring(
                                0,
                                candidate.getName().length()
                                        - fileSuffix.length());
                File instanceDir = new File(
                        storagePathProvider.getOdkDirPath(StorageSubdirectory.INSTANCES) + File.separator
                                + instanceDirName);
                File instanceFile = new File(instanceDir,
                        instanceDirName + ".xml");
                if (instanceDir.exists()
                        && instanceDir.isDirectory()
                        && !instanceFile.exists()) {
                    // yes! -- use this savepoint file
                    return instanceFile
                            .getAbsolutePath();
                }
            }

        } else {
            Timber.e("Couldn't access cache directory when looking for save points!");
        }

        return null;
    }


    private void initToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setTitle(getString(R.string.loading_form));
    }

    /**
     * Creates save-points asynchronously in order to not affect swiping performance on larger forms.
     * If moving backwards through a form is disabled, also saves the index of the form element that
     * was last shown to the user so that no matter how the app exits and relaunches, the user can't
     * see previous questions.
     */
    private void nonblockingCreateSavePointData() {
        try {
            SavePointTask savePointTask = new SavePointTask(this);
            savePointTask.execute();

            if (!allowMovingBackwards) {
                FormController formController = getFormController();
                if (formController != null) {
                    new SaveFormIndexTask(this, formController.getFormIndex()).execute();
                }
            }
        } catch (Exception e) {
            Timber.e("Could not schedule SavePointTask. Perhaps a lot of swiping is taking place?");
        }
    }

    // This method may return null if called before form loading is finished
    @Nullable
    private FormController getFormController() {
        return Collect.getInstance().getFormController();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_FORMPATH, formPath);
        FormController formController = getFormController();
        if (formController != null) {
            if (formController.getInstanceFile() != null) {
                outState.putString(KEY_INSTANCEPATH, getAbsoluteInstancePath());
            }
            outState.putString(KEY_XPATH,
                    formController.getXPath(formController.getFormIndex()));
            FormIndex waiting = formController.getIndexWaitingForData();
            if (waiting != null) {
                outState.putString(KEY_XPATH_WAITING_FOR_DATA,
                        formController.getXPath(waiting));
            }
            // save the instance to a temp path...
            nonblockingCreateSavePointData();
        }
        outState.putBoolean(NEWFORM, false);
        outState.putString(KEY_ERROR, errorMessage);
        outState.putString(KEY_SAVE_NAME, saveName);
        outState.putBoolean(KEY_AUTO_SAVED, autoSaved);
        outState.putBoolean(KEY_READ_PHONE_STATE_PERMISSION_REQUEST_NEEDED, readPhoneStatePermissionRequestNeeded);
        outState.putBoolean(KEY_LOCATION_PERMISSIONS_GRANTED, locationPermissionsPreviouslyGranted);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        FormController formController = getFormController();
        if (formController == null) {
            // we must be in the midst of a reload of the FormController.
            // try to save this callback data to the FormLoaderTask
            if (formLoaderTask != null
                    && formLoaderTask.getStatus() != AsyncTask.Status.FINISHED) {
                formLoaderTask.setActivityResult(requestCode, resultCode, intent);
            } else {
                Timber.e("Got an activityResult without any pending form loader");
            }
            return;
        }

        // If we're coming back from the hierarchy view, the user has either tapped the back
        // button or another question to jump to so we need to rebuild the view.
        if (requestCode == RequestCodes.HIERARCHY_ACTIVITY || requestCode == RequestCodes.CHANGE_SETTINGS) {
            onScreenRefresh();
            return;
        }

        if (resultCode == RESULT_CANCELED) {
            waitingForDataRegistry.cancelWaitingForData();
            return;
        }

        // intent is needed for all requestCodes except of DRAW_IMAGE, ANNOTATE_IMAGE, SIGNATURE_CAPTURE, IMAGE_CAPTURE and HIERARCHY_ACTIVITY
        if (intent == null && requestCode != RequestCodes.DRAW_IMAGE && requestCode != RequestCodes.ANNOTATE_IMAGE
                && requestCode != RequestCodes.SIGNATURE_CAPTURE && requestCode != RequestCodes.IMAGE_CAPTURE) {
            Timber.d("The intent has a null value for requestCode: %s", requestCode);
            showLongToast(Collect.getInstance(), getString(R.string.null_intent_value));
            return;
        }

        switch (requestCode) {
            case RequestCodes.OSM_CAPTURE:
                AnalyticsUtils.logFormEvent(OPEN_MAP_KIT_RESPONSE);
                setWidgetData(intent.getStringExtra("OSM_FILE_NAME"));
                break;
            case RequestCodes.EX_ARBITRARY_FILE_CHOOSER:
            case RequestCodes.EX_VIDEO_CHOOSER:
            case RequestCodes.EX_IMAGE_CHOOSER:
            case RequestCodes.EX_AUDIO_CHOOSER:
                if (intent.getClipData() != null
                        && intent.getClipData().getItemCount() > 0
                        && intent.getClipData().getItemAt(0) != null) {
                    loadFile(intent.getClipData().getItemAt(0).getUri());
                } else {
                    setWidgetData(null);
                }
                break;
            case RequestCodes.EX_GROUP_CAPTURE:
                try {
                    Bundle extras = intent.getExtras();
                    if (getCurrentViewIfODKView() != null) {
                        getCurrentViewIfODKView().setDataForFields(extras);
                    }
                } catch (JavaRosaException e) {
                    Timber.e(e);
                    createErrorDialog(e.getCause().getMessage(), false);
                }
                break;
            case RequestCodes.DRAW_IMAGE:
            case RequestCodes.ANNOTATE_IMAGE:
            case RequestCodes.SIGNATURE_CAPTURE:
            case RequestCodes.IMAGE_CAPTURE:
                loadFile(Uri.fromFile(new File(storagePathProvider.getTmpImageFilePath())));
                break;
            case RequestCodes.ALIGNED_IMAGE:
            case RequestCodes.ARBITRARY_FILE_CHOOSER:
            case RequestCodes.AUDIO_CAPTURE:
            case RequestCodes.AUDIO_CHOOSER:
            case RequestCodes.VIDEO_CAPTURE:
            case RequestCodes.VIDEO_CHOOSER:
            case RequestCodes.IMAGE_CHOOSER:
                loadFile(intent.getData());
                break;
            case RequestCodes.LOCATION_CAPTURE:
            case RequestCodes.GEOSHAPE_CAPTURE:
            case RequestCodes.GEOTRACE_CAPTURE:
            case RequestCodes.BEARING_CAPTURE:
            case RequestCodes.BARCODE_CAPTURE:
            case RequestCodes.EX_STRING_CAPTURE:
            case RequestCodes.EX_INT_CAPTURE:
            case RequestCodes.EX_DECIMAL_CAPTURE:
                setWidgetData(intent.getExtras().get(ANSWER_KEY));
                break;
        }
    }

    private void loadFile(Uri uri) {
        permissionsProvider.requestReadUriPermission(this, uri, getContentResolver(), new PermissionListener() {
            @Override
            public void granted() {
                ProgressDialogFragment progressDialog = new ProgressDialogFragment();
                progressDialog.setMessage(getString(R.string.please_wait));
                progressDialog.show(getSupportFragmentManager(), ProgressDialogFragment.COLLECT_PROGRESS_DIALOG_TAG);

                mediaLoadingFragment.beginMediaLoadingTask(uri);
            }

            @Override
            public void denied() {

            }
        });
    }

    public QuestionWidget getWidgetWaitingForBinaryData() {
        ODKView odkView = getCurrentViewIfODKView();

        if (odkView != null) {
            for (QuestionWidget qw : odkView.getWidgets()) {
                if (waitingForDataRegistry.isWaitingForData(qw.getFormEntryPrompt().getIndex())) {
                    return qw;
                }
            }
        } else {
            Timber.e("currentView returned null.");
        }
        return null;
    }

    public void setWidgetData(Object data) {
        ODKView currentViewIfODKView = getCurrentViewIfODKView();

        if (currentViewIfODKView != null) {
            boolean set = false;
            for (QuestionWidget widget : currentViewIfODKView.getWidgets()) {
                if (widget instanceof WidgetDataReceiver) {
                    if (waitingForDataRegistry.isWaitingForData(widget.getFormEntryPrompt().getIndex())) {
                        try {
                            ((WidgetDataReceiver) widget).setData(data);
                            waitingForDataRegistry.cancelWaitingForData();
                        } catch (Exception e) {
                            Timber.e(e);
                            ToastUtils.showLongToast(Collect.getInstance(), currentViewIfODKView.getContext().getString(R.string.error_attaching_binary_file,
                                    e.getMessage()));
                        }
                        set = true;
                        break;
                    }
                }
            }

            if (!set) {
                Timber.e("Attempting to return data to a widget or set of widgets not looking for data");
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menuDelegate.onCreateOptionsMenu(getMenuInflater(), menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menuDelegate.onPrepareOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (!MultiClickGuard.allowClick(getClass().getName())) {
            return true;
        }

        if (menuDelegate.onOptionsItemSelected(item)) {
            return true;
        }

        // These actions should move into the `FormEntryMenuDelegate`
        switch (item.getItemId()) {
            case R.id.menu_languages:
                createLanguageDialog();
                return true;

            case R.id.menu_save:
                // don't exit
                saveForm(false, InstancesDaoHelper.isInstanceComplete(false, settingsProvider.getGeneralSettings().getBoolean(KEY_COMPLETED_DEFAULT)), null, true);
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Attempt to save the answer(s) in the current screen to into the data
     * model.
     *
     * @return false if any error occurs while saving (constraint violated,
     * etc...), true otherwise.
     */
    private boolean saveAnswersForCurrentScreen(boolean evaluateConstraints) {
        FormController formController = getFormController();
        // only try to save if the current event is a question or a field-list group
        // and current view is an ODKView (occasionally we show blank views that do not have any
        // controls to save data from)
        if (formController != null && formController.currentPromptIsQuestion()
                && getCurrentViewIfODKView() != null) {
            HashMap<FormIndex, IAnswerData> answers = getAnswers();
            try {
                FailedConstraint constraint = formController.saveAllScreenAnswers(answers,
                        evaluateConstraints);
                if (constraint != null) {
                    createConstraintToast(constraint.index, constraint.status);
                    if (formController.indexIsInFieldList() && formController.getQuestionPrompts().length > 1) {
                        getCurrentViewIfODKView().highlightWidget(constraint.index);
                    }
                    return false;
                }
            } catch (JavaRosaException | FormDesignException e) {
                Timber.e(e);
                createErrorDialog(e.getMessage(), false);
                return false;
            }
        }

        return true;
    }

    // The method saves questions one by one in order to support calculations in field-list groups
    private void saveAnswersForCurrentScreen(FormEntryPrompt[] mutableQuestionsBeforeSave, List<ImmutableDisplayableQuestion> immutableQuestionsBeforeSave) {
        FormController formController = getFormController();
        ODKView currentView = getCurrentViewIfODKView();
        if (formController == null || currentView == null) {
            return;
        }

        int index = 0;
        for (Map.Entry<FormIndex, IAnswerData> answer : currentView.getAnswers().entrySet()) {
            // Questions with calculates will have their answers updated as the questions they depend on are saved
            if (!isQuestionRecalculated(mutableQuestionsBeforeSave[index], immutableQuestionsBeforeSave.get(index))) {
                try {
                    formController.saveOneScreenAnswer(answer.getKey(), answer.getValue(), false);
                } catch (JavaRosaException e) {
                    Timber.e(e);
                }
            }
            index++;
        }
    }

    /**
     * Clears the answer on the screen.
     */
    private void clearAnswer(QuestionWidget qw) {
        if (qw.getAnswer() != null || qw instanceof DateTimeWidget) {
            qw.clearAnswer();
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenuInfo menuInfo) {
        if (!swipeHandler.beenSwiped()) {
            super.onCreateContextMenu(menu, v, menuInfo);
            FormController formController = getFormController();

            menu.add(0, v.getId(), 0, getString(R.string.clear_answer));
            if (formController.indexContainsRepeatableGroup()) {
                menu.add(0, DELETE_REPEAT, 0, getString(R.string.delete_repeat));
            }
            menu.setHeaderTitle(getString(R.string.edit_prompt));
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getItemId() == DELETE_REPEAT) {
            DialogUtils.showIfNotShowing(DeleteRepeatDialogFragment.class, getSupportFragmentManager());
        } else {
            ODKView odkView = getCurrentViewIfODKView();
            if (odkView != null) {
                for (QuestionWidget qw : odkView.getWidgets()) {
                    if (item.getItemId() == qw.getId()) {
                        createClearDialog(qw);
                        break;
                    }
                }
            }
        }

        return super.onContextItemSelected(item);
    }

    @Override
    public void deleteGroup() {
        FormController formController = getFormController();
        if (formController != null && !formController.indexIsInFieldList()) {
            swipeHandler.setBeenSwiped(true);
            onSwipeForward();
        } else {
            onScreenRefresh();
        }
    }

    /**
     * If we're loading, then we pass the loading thread to our next instance.
     */
    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        FormController formController = getFormController();
        // if a form is loading, pass the loader task
        if (formLoaderTask != null
                && formLoaderTask.getStatus() != AsyncTask.Status.FINISHED) {
            return formLoaderTask;
        }

        // mFormEntryController is static so we don't need to pass it.
        if (formController != null && formController.currentPromptIsQuestion()) {
            saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
        }
        return null;
    }

    /**
     * Creates and returns a new view based on the event type passed in. The view returned is
     * of type {@link View} if the event passed in represents the end of the form or of type
     * {@link ODKView} otherwise.
     *
     * @param advancingPage -- true if this results from advancing through the form
     * @return newly created View
     */
    private View createView(int event, boolean advancingPage) {
        releaseOdkView();

        FormController formController = getFormController();

        String formTitle = formController.getFormTitle();
        setTitle(formTitle);

        if (event != FormEntryController.EVENT_QUESTION) {
            formController.getAuditEventLogger().logEvent(AuditEvent.getAuditEventTypeFromFecType(event),
                    formController.getFormIndex(), true, null, System.currentTimeMillis(), null);
        }

        switch (event) {
            case FormEntryController.EVENT_BEGINNING_OF_FORM:
                return createViewForFormBeginning(formController);
            case FormEntryController.EVENT_END_OF_FORM:
                return createViewForFormEnd(formController);
            case FormEntryController.EVENT_QUESTION:
            case FormEntryController.EVENT_GROUP:
            case FormEntryController.EVENT_REPEAT:
                // should only be a group here if the event_group is a field-list
                try {
                    AuditUtils.logCurrentScreen(formController, formController.getAuditEventLogger(), System.currentTimeMillis());

                    FormEntryCaption[] groups = formController
                            .getGroupsForCurrentIndex();
                    FormEntryPrompt[] prompts = formController.getQuestionPrompts();

                    odkView = createODKView(advancingPage, prompts, groups);
                    odkView.setWidgetValueChangedListener(this);
                    Timber.i("Created view for group %s %s",
                            groups.length > 0 ? groups[groups.length - 1].getLongText() : "[top]",
                            prompts.length > 0 ? prompts[0].getQuestionText() : "[no question]");
                } catch (RuntimeException | FormDesignException e) {
                    Timber.e(e);
                    // this is badness to avoid a crash.
                    try {
                        event = formController.stepToNextScreenEvent();
                        createErrorDialog(e.getMessage(), false);
                    } catch (JavaRosaException e1) {
                        Timber.d(e1);
                        createErrorDialog(e.getMessage() + "\n\n" + e1.getCause().getMessage(),
                                false);
                    }
                    return createView(event, advancingPage);
                }

                if (showNavigationButtons) {
                    updateNavigationButtonVisibility();
                }
                return odkView;

            case EVENT_PROMPT_NEW_REPEAT:
                createRepeatDialog();
                return new EmptyView(this);

            default:
                Timber.e("Attempted to create a view that does not exist.");
                // this is badness to avoid a crash.
                try {
                    event = formController.stepToNextScreenEvent();
                    createErrorDialog(getString(R.string.survey_internal_error), true);
                } catch (JavaRosaException e) {
                    Timber.d(e);
                    createErrorDialog(e.getCause().getMessage(), true);
                }
                return createView(event, advancingPage);
        }
    }

    @NotNull
    private ODKView createODKView(boolean advancingPage, FormEntryPrompt[] prompts, FormEntryCaption[] groups) {
        odkViewLifecycle.start();

        AudioClipViewModel.Factory factory = new AudioClipViewModel.Factory(MediaPlayer::new, scheduler);
        ViewModelAudioPlayer viewModelAudioPlayer = new ViewModelAudioPlayer(ViewModelProviders
                .of(this, factory)
                .get(AudioClipViewModel.class), odkViewLifecycle);

        return new ODKView(this, prompts, groups, advancingPage, formSaveViewModel, waitingForDataRegistry, viewModelAudioPlayer, audioRecorder, formEntryViewModel, internalRecordingRequester, externalAppRecordingRequester);
    }

    @Override
    public FragmentActivity getActivity() {
        return this;
    }

    @Override
    public LifecycleOwner getViewLifecycle() {
        return odkViewLifecycle;
    }

    private void releaseOdkView() {
        odkViewLifecycle.destroy();

        if (odkView != null) {
            odkView = null;
        }
    }

    /**
     * Steps to the next screen and creates a view for it. Always sets {@code advancingPage} to true
     * to auto-play media.
     */
    private View createViewForFormBeginning(FormController formController) {
        int event = FormEntryController.EVENT_BEGINNING_OF_FORM;
        try {
            event = formController.stepToNextScreenEvent();
        } catch (JavaRosaException e) {
            Timber.d(e);
            if (e.getMessage().equals(e.getCause().getMessage())) {
                createErrorDialog(e.getMessage(), false);
            } else {
                createErrorDialog(e.getMessage() + "\n\n" + e.getCause().getMessage(), false);
            }
        }

        return createView(event, true);
    }

    /**
     * Creates the final screen in a form-filling interaction. Allows the user to set a display
     * name for the instance and to decide whether the form should be finalized or not. Presents
     * a button for saving and exiting.
     */
    private View createViewForFormEnd(FormController formController) {
        if (formController.getSubmissionMetadata().instanceName != null) {
            saveName = formController.getSubmissionMetadata().instanceName;
        } else {
            // no meta/instanceName field in the form -- see if we have a
            // name for this instance from a previous save attempt...
            String uriMimeType = null;
            Uri instanceUri = getIntent().getData();
            if (instanceUri != null) {
                uriMimeType = getContentResolver().getType(instanceUri);
            }

            if (saveName == null && uriMimeType != null
                    && uriMimeType.equals(InstancesContract.CONTENT_ITEM_TYPE)) {
                Instance instance = new InstancesRepositoryProvider(Collect.getInstance()).get().get(ContentUriHelper.getIdFromUri(instanceUri));
                if (instance != null) {
                    saveName = instance.getDisplayName();
                }
            }

            if (saveName == null) {
                saveName = formSaveViewModel.getFormName();
            }
        }

        FormEndView endView = new FormEndView(this, formSaveViewModel.getFormName(), saveName, InstancesDaoHelper.isInstanceComplete(true, settingsProvider.getGeneralSettings().getBoolean(KEY_COMPLETED_DEFAULT)), new FormEndView.Listener() {
            @Override
            public void onSaveAsChanged(String saveAs) {
                // Seems like this is needed for rotation?
                saveName = saveAs;
            }

            @Override
            public void onSaveClicked(boolean markAsFinalized) {
                if (saveName.length() < 1) {
                    showShortToast(Collect.getInstance(), R.string.save_as_error);
                } else {
                    formSaveViewModel.saveForm(getIntent().getData(), markAsFinalized, saveName, true);
                }
            }
        });

        if (!settingsProvider.getAdminSettings().getBoolean(ProtectedProjectKeys.KEY_MARK_AS_FINALIZED)) {
            endView.findViewById(R.id.mark_finished).setVisibility(View.GONE);
        }

        if (formController.getSubmissionMetadata().instanceName != null) {
            // if instanceName is defined in form, this is the name -- no
            // revisions
            // display only the name, not the prompt, and disable edits
            endView.findViewById(R.id.save_form_as).setVisibility(View.GONE);
            endView.findViewById(R.id.save_name).setEnabled(false);
            endView.findViewById(R.id.save_name).setVisibility(View.VISIBLE);
        }

        // override the visibility settings based upon admin preferences
        if (!settingsProvider.getAdminSettings().getBoolean(ProtectedProjectKeys.KEY_SAVE_AS)) {
            endView.findViewById(R.id.save_form_as).setVisibility(View.GONE);
            endView.findViewById(R.id.save_name).setVisibility(View.GONE);
        }

        if (showNavigationButtons) {
            updateNavigationButtonVisibility();
        }

        return endView;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent mv) {
        boolean handled = swipeHandler.getGestureDetector().onTouchEvent(mv);
        if (!handled) {
            return super.dispatchTouchEvent(mv);
        }

        return handled; // this is always true
    }

    @Override
    public void onSwipeForward() {
        if (!moveScreen(FORWARDS)) {
            swipeHandler.setBeenSwiped(false);
        }
    }

    @Override
    public void onSwipeBackward() {
        if (!moveScreen(BACKWARDS)) {
            swipeHandler.setBeenSwiped(false);
        }
    }

    private boolean moveScreen(Direction direction) {
        currentView.cancelPendingInputEvents();
        closeContextMenu();
        FormController formController = getFormController();
        if (formController == null) {
            Timber.d("FormController has a null value");
            return false;
        }

        if (audioRecorder.isRecording() && !backgroundAudioViewModel.isBackgroundRecording()) {
            // We want the user to stop recording before changing screens
            DialogUtils.showIfNotShowing(RecordingWarningDialogFragment.class, getSupportFragmentManager());
            return false;
        }

        if (direction == FORWARDS) {
            if (formController.getEvent() == FormEntryController.EVENT_END_OF_FORM) {
                return false;
            }

            if (!saveBeforeNextView(formController)) {
                formEntryViewModel.moveForward();
                formIndexAnimationHandler.handle(formController.getFormIndex());
                return true;
            } else {
                return false;
            }
        } else {
            if (formController.isCurrentQuestionFirstInForm() || !allowMovingBackwards) {
                return false;
            }

            // The answer is saved on a back swipe, but question constraints are ignored.
            if (formController.currentPromptIsQuestion()) {
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
            }

            formEntryViewModel.moveBackward();
            formIndexAnimationHandler.handle(formController.getFormIndex());
            return true;
        }
    }

    @Override
    public void onScreenChange(Direction direction) {
        final int event = getFormController().getEvent();

        switch (direction) {
            case FORWARDS:
                animateToNextView(event);
                break;
            case BACKWARDS:
                if (event == FormEntryController.EVENT_GROUP || event == FormEntryController.EVENT_QUESTION) {
                    // create savepoint
                    nonblockingCreateSavePointData();
                }

                animateToPreviousView(event);
                break;
        }
    }

    /**
     * Rebuilds the current view. the controller and the displayed view can get
     * out of sync due to dialogs and restarts caused by screen orientation
     * changes, so they're resynchronized here.
     */
    @Override
    public void onScreenRefresh() {
        int event = getFormController().getEvent();

        View current = createView(event, false);
        showView(current, AnimationType.FADE);

        formIndexAnimationHandler.setLastIndex(getFormController().getFormIndex());
    }

    private void animateToNextView(int event) {
        switch (event) {
            case FormEntryController.EVENT_QUESTION:
            case FormEntryController.EVENT_GROUP:
                // create a savepoint
                nonblockingCreateSavePointData();
                showView(createView(event, true), AnimationType.RIGHT);
                break;
            case FormEntryController.EVENT_END_OF_FORM:
            case FormEntryController.EVENT_REPEAT:
            case EVENT_PROMPT_NEW_REPEAT:
                showView(createView(event, true), AnimationType.RIGHT);
                break;
            case FormEntryController.EVENT_REPEAT_JUNCTURE:
                Timber.i("Repeat juncture: %s", getFormController().getFormIndex().getReference());
                // skip repeat junctures until we implement them
                break;
            default:
                Timber.d("JavaRosa added a new EVENT type and didn't tell us... shame on them.");
                break;
        }

        formIndexAnimationHandler.setLastIndex(getFormController().getFormIndex());
    }

    private void animateToPreviousView(int event) {
        View next = createView(event, false);
        showView(next, AnimationType.LEFT);

        formIndexAnimationHandler.setLastIndex(getFormController().getFormIndex());
    }

    private boolean saveBeforeNextView(FormController formController) {
        if (formController.currentPromptIsQuestion()) {
            // get constraint behavior preference value with appropriate default
            String constraintBehavior = settingsProvider.getGeneralSettings().getString(ProjectKeys.KEY_CONSTRAINT_BEHAVIOR);

            // if constraint behavior says we should validate on swipe, do so
            if (constraintBehavior.equals(ProjectKeys.CONSTRAINT_BEHAVIOR_ON_SWIPE)) {
                if (!saveAnswersForCurrentScreen(EVALUATE_CONSTRAINTS)) {
                    // A constraint was violated so a dialog should be showing.
                    swipeHandler.setBeenSwiped(false);
                    return true;
                }

                // otherwise, just save without validating (constraints will be validated on
                // finalize)
            } else {
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
            }
        }

        return false;
    }

    /**
     * Displays the View specified by the parameter 'next', animating both the
     * current view and next appropriately given the AnimationType. Also updates
     * the progress bar.
     */
    public void showView(View next, AnimationType from) {
        invalidateOptionsMenu();

        // disable notifications...
        if (inAnimation != null) {
            inAnimation.setAnimationListener(null);
        }
        if (outAnimation != null) {
            outAnimation.setAnimationListener(null);
        }

        // logging of the view being shown is already done, as this was handled
        // by createView()
        switch (from) {
            case RIGHT:
                inAnimation = loadAnimation(this,
                        R.anim.push_left_in);
                outAnimation = loadAnimation(this,
                        R.anim.push_left_out);
                // if animation is left or right then it was a swipe, and we want to re-save on
                // entry
                autoSaved = false;
                break;
            case LEFT:
                inAnimation = loadAnimation(this,
                        R.anim.push_right_in);
                outAnimation = loadAnimation(this,
                        R.anim.push_right_out);
                autoSaved = false;
                break;
            case FADE:
                inAnimation = loadAnimation(this, R.anim.fade_in);
                outAnimation = loadAnimation(this, R.anim.fade_out);
                break;
        }

        // complete setup for animations...
        inAnimation.setAnimationListener(this);
        outAnimation.setAnimationListener(this);

        if (!areAnimationsEnabled(this)) {
            inAnimation.setDuration(0);
            outAnimation.setDuration(0);
        }

        // drop keyboard before transition...
        if (currentView != null) {
            softKeyboardController.hideSoftKeyboard(currentView);
        }

        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        // adjust which view is in the layout container...
        View staleView = currentView;
        currentView = next;
        swipeHandler.setOdkView(getCurrentViewIfODKView());
        questionHolder.addView(currentView, lp);
        animationCompletionSet = 0;

        if (staleView != null) {
            // start OutAnimation for transition...
            staleView.startAnimation(outAnimation);
            // and remove the old view (MUST occur after start of animation!!!)
            questionHolder.removeView(staleView);
        } else {
            animationCompletionSet = 2;
        }
        // start InAnimation for transition...
        currentView.startAnimation(inAnimation);

        FormController formController = getFormController();
        if (formController.getEvent() == FormEntryController.EVENT_QUESTION
                || formController.getEvent() == FormEntryController.EVENT_GROUP
                || formController.getEvent() == FormEntryController.EVENT_REPEAT) {

            try {
                FormEntryPrompt[] prompts = getFormController().getQuestionPrompts();
                for (FormEntryPrompt p : prompts) {
                    List<TreeElement> attrs = p.getBindAttributes();
                    for (int i = 0; i < attrs.size(); i++) {
                        if (!autoSaved && "saveIncomplete".equals(attrs.get(i).getName())) {
                            analytics.logEvent(SAVE_INCOMPLETE, "saveIncomplete", AnalyticsUtils.getFormHash(Collect.getInstance().getFormController()));

                            saveForm(false, false, null, false);
                            autoSaved = true;
                        }
                    }
                }
            } catch (FormDesignException e) {
                Timber.e(e);
                createErrorDialog(e.getMessage(), false);
            }
        }
    }

    /**
     * Creates and displays a dialog displaying the violated constraint.
     */
    private void createConstraintToast(FormIndex index, int saveStatus) {
        FormController formController = getFormController();
        String constraintText;
        switch (saveStatus) {
            case FormEntryController.ANSWER_CONSTRAINT_VIOLATED:
                constraintText = formController
                        .getQuestionPromptConstraintText(index);
                if (constraintText == null) {
                    constraintText = formController.getQuestionPrompt(index)
                            .getSpecialFormQuestionText("constraintMsg");
                    if (constraintText == null) {
                        constraintText = getString(R.string.invalid_answer_error);
                    }
                }
                break;
            case FormEntryController.ANSWER_REQUIRED_BUT_EMPTY:
                constraintText = formController
                        .getQuestionPromptRequiredText(index);
                if (constraintText == null) {
                    constraintText = formController.getQuestionPrompt(index)
                            .getSpecialFormQuestionText("requiredMsg");
                    if (constraintText == null) {
                        constraintText = getString(R.string.required_answer_error);
                    }
                }
                break;
            default:
                return;
        }

        ToastUtils.showShortToastInMiddle(Collect.getInstance(), constraintText);
    }

    /**
     * Creates and displays a dialog asking the user if they'd like to create a
     * repeat of the current group.
     */
    private void createRepeatDialog() {
        swipeHandler.setBeenSwiped(true);

        // In some cases dialog might be present twice because refreshView() is being called
        // from onResume(). This ensures that we do not preset this modal dialog if it's already
        // visible. Checking for shownAlertDialogIsGroupRepeat because the same field
        // alertDialog is being used for all alert dialogs in this activity.
        if (shownAlertDialogIsGroupRepeat) {
            return;
        }

        shownAlertDialogIsGroupRepeat = true;

        AddRepeatDialog.show(this, getFormController().getLastGroupText(), new AddRepeatDialog.Listener() {
            @Override
            public void onAddRepeatClicked() {
                swipeHandler.setBeenSwiped(false);
                shownAlertDialogIsGroupRepeat = false;
                formEntryViewModel.addRepeat();
                formIndexAnimationHandler.handle(formEntryViewModel.getCurrentIndex());
            }

            @Override
            public void onCancelClicked() {
                swipeHandler.setBeenSwiped(false);
                shownAlertDialogIsGroupRepeat = false;

                // Make sure the error dialog will not disappear.
                //
                // When showNextView() popups an error dialog (because of a
                // JavaRosaException)
                // the issue is that the "add new repeat dialog" is referenced by
                // alertDialog
                // like the error dialog. When the "no repeat" is clicked, the error dialog
                // is shown. Android by default dismisses the dialogs when a button is
                // clicked,
                // so instead of closing the first dialog, it closes the second.
                new Thread() {
                    @Override
                    public void run() {
                        FormEntryActivity.this.runOnUiThread(() -> {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException e) {
                                //This is rare
                                Timber.e(e);
                            }

                            formEntryViewModel.cancelRepeatPrompt();
                            formIndexAnimationHandler.handle(formEntryViewModel.getCurrentIndex());
                        });
                    }
                }.start();
            }
        });
    }

    /**
     * Creates and displays dialog with the given errorMsg.
     */
    private void createErrorDialog(String errorMsg, final boolean shouldExit) {
        if (alertDialog != null && alertDialog.isShowing()) {
            errorMsg = errorMessage + "\n\n" + errorMsg;
            errorMessage = errorMsg;
        } else {
            alertDialog = new AlertDialog.Builder(this).create();
            errorMessage = errorMsg;
        }

        alertDialog.setTitle(getString(R.string.error_occured));
        alertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case BUTTON_POSITIVE:
                        if (shouldExit) {
                            errorMessage = null;
                            finish();
                        }
                        break;
                }
            }
        };
        alertDialog.setCancelable(false);
        alertDialog.setButton(BUTTON_POSITIVE, getString(R.string.ok), errorListener);
        swipeHandler.setBeenSwiped(false);
        alertDialog.show();
    }

    /**
     * Saves data and writes it to disk. If exit is set, program will exit after
     * save completes. Complete indicates whether the user has marked the
     * isntancs as complete. If updatedSaveName is non-null, the instances
     * content provider is updated with the new name
     */
    private boolean saveForm(boolean exit, boolean complete, String updatedSaveName,
                             boolean current) {
        // save current answer
        if (current) {
            if (!saveAnswersForCurrentScreen(complete)) {
                showShortToast(Collect.getInstance(), R.string.data_saved_error);
                return false;
            }
        }

        formSaveViewModel.saveForm(getIntent().getData(), complete, updatedSaveName, exit);

        return true;
    }

    private void handleSaveResult(FormSaveViewModel.SaveResult result) {
        if (result == null) {
            return;
        }

        switch (result.getState()) {
            case CHANGE_REASON_REQUIRED:
                showIfNotShowing(ChangesReasonPromptDialogFragment.class, getSupportFragmentManager());
                break;

            case SAVING:
                autoSaved = true;
                showIfNotShowing(SaveFormProgressDialogFragment.class, getSupportFragmentManager());
                break;

            case SAVED:
                DialogUtils.dismissDialog(SaveFormProgressDialogFragment.class, getSupportFragmentManager());
                DialogUtils.dismissDialog(ChangesReasonPromptDialogFragment.class, getSupportFragmentManager());

                showShortToast(Collect.getInstance(), R.string.data_saved_ok);

                if (result.getRequest().viewExiting()) {
                    if (result.getRequest().shouldFinalize()) {
                        instanceSubmitScheduler.scheduleSubmit(currentProjectProvider.getCurrentProject().getUuid());
                    }

                    finishAndReturnInstance();
                }
                formSaveViewModel.resumeFormEntry();
                break;

            case SAVE_ERROR:
                DialogUtils.dismissDialog(SaveFormProgressDialogFragment.class, getSupportFragmentManager());
                DialogUtils.dismissDialog(ChangesReasonPromptDialogFragment.class, getSupportFragmentManager());

                String message;

                if (result.getMessage() != null) {
                    message = getString(R.string.data_saved_error) + " "
                            + result.getMessage();
                } else {
                    message = getString(R.string.data_saved_error);
                }

                showLongToast(Collect.getInstance(), message);
                formSaveViewModel.resumeFormEntry();
                break;

            case FINALIZE_ERROR:
                DialogUtils.dismissDialog(SaveFormProgressDialogFragment.class, getSupportFragmentManager());
                DialogUtils.dismissDialog(ChangesReasonPromptDialogFragment.class, getSupportFragmentManager());

                showLongToast(Collect.getInstance(), String.format(getString(R.string.encryption_error_message),
                        result.getMessage()));
                finishAndReturnInstance();
                formSaveViewModel.resumeFormEntry();
                break;

            case CONSTRAINT_ERROR: {
                DialogUtils.dismissDialog(SaveFormProgressDialogFragment.class, getSupportFragmentManager());
                DialogUtils.dismissDialog(ChangesReasonPromptDialogFragment.class, getSupportFragmentManager());

                onScreenRefresh();

                // get constraint behavior preference value with appropriate default
                String constraintBehavior = settingsProvider.getGeneralSettings().getString(ProjectKeys.KEY_CONSTRAINT_BEHAVIOR);

                // an answer constraint was violated, so we need to display the proper toast(s)
                // if constraint behavior is on_swipe, this will happen if we do a 'swipe' to the
                // next question
                if (constraintBehavior.equals(ProjectKeys.CONSTRAINT_BEHAVIOR_ON_SWIPE)) {
                    next();
                } else {
                    // otherwise, we can get the proper toast(s) by saving with constraint check
                    saveAnswersForCurrentScreen(EVALUATE_CONSTRAINTS);
                }
                formSaveViewModel.resumeFormEntry();
                break;
            }
        }
    }

    @Override
    public void onSaveChangesClicked() {
        saveForm(true, InstancesDaoHelper.isInstanceComplete(false, settingsProvider.getGeneralSettings().getBoolean(KEY_COMPLETED_DEFAULT)), null, true);
    }

    @Nullable
    private String getAbsoluteInstancePath() {
        FormController formController = getFormController();
        return formController != null ? formController.getAbsoluteInstancePath() : null;
    }

    /**
     * Confirm clear answer dialog
     */
    private void createClearDialog(final QuestionWidget qw) {
        alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(getString(R.string.clear_answer_ask));

        String question = qw.getFormEntryPrompt().getLongText();
        if (question == null) {
            question = "";
        }
        if (question.length() > 50) {
            question = question.substring(0, 50) + "...";
        }

        alertDialog.setMessage(getString(R.string.clearanswer_confirm,
                question));

        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case BUTTON_POSITIVE: // yes
                        clearAnswer(qw);
                        saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                        break;
                }
            }
        };
        alertDialog.setCancelable(false);
        alertDialog
                .setButton(BUTTON_POSITIVE, getString(R.string.discard_answer), quitListener);
        alertDialog.setButton(BUTTON_NEGATIVE, getString(R.string.clear_answer_no),
                quitListener);
        alertDialog.show();
    }

    /**
     * Creates and displays a dialog allowing the user to set the language for
     * the form.
     */
    private void createLanguageDialog() {
        FormController formController = getFormController();
        final String[] languages = formController.getLanguages();
        int selected = -1;
        if (languages != null) {
            String language = formController.getLanguage();
            for (int i = 0; i < languages.length; i++) {
                if (language.equals(languages[i])) {
                    selected = i;
                }
            }
        }
        alertDialog = new AlertDialog.Builder(this)
                .setSingleChoiceItems(languages, selected,
                        (dialog, whichButton) -> {
                            Form form = formsRepository.getOneByPath(formPath);
                            if (form != null) {
                                formsRepository.save(new Form.Builder(form)
                                        .language(languages[whichButton])
                                        .build()
                                );
                            }

                            getFormController().setLanguage(languages[whichButton]);
                            dialog.dismiss();
                            if (getFormController().currentPromptIsQuestion()) {
                                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
                            }
                            onScreenRefresh();
                        })
                .setTitle(getString(R.string.change_language))
                .setNegativeButton(getString(R.string.do_not_change), null).create();
        alertDialog.show();
    }

    /**
     * Shows the next or back button, neither or both. Both buttons are displayed unless:
     * - we are at the first question in the form so the back button is hidden
     * - we are at the end screen so the next button is hidden
     * - settings prevent backwards navigation of the form so the back button is hidden
     * <p>
     * The visibility of the container for these buttons is determined once {@link #onResume()}.
     */
    private void updateNavigationButtonVisibility() {
        FormController formController = getFormController();
        if (formController == null) {
            return;
        }

        backButton.setVisibility(!formController.isCurrentQuestionFirstInForm() && allowMovingBackwards ? View.VISIBLE : View.GONE);
        nextButton.setVisibility(formController.getEvent() != FormEntryController.EVENT_END_OF_FORM ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onStart() {
        super.onStart();
        FormController formController = getFormController();

        // Register to receive location provider change updates and write them to the audit log
        if (formController != null && formController.currentFormAuditsLocation()
                && new PlayServicesChecker().isGooglePlayServicesAvailable(this)) {
            registerReceiver(locationProvidersReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
        }

        // User may have changed location permissions in Android settings
        if (permissionsProvider.areLocationPermissionsGranted() != locationPermissionsPreviouslyGranted) {
            backgroundLocationViewModel.locationPermissionChanged();
            locationPermissionsPreviouslyGranted = !locationPermissionsPreviouslyGranted;
        }
    }

    @Override
    protected void onPause() {
        backgroundLocationViewModel.activityHidden();

        FormController formController = getFormController();

        // make sure we're not already saving to disk. if we are, currentPrompt
        // is getting constantly updated
        if (!formSaveViewModel.isSaving()) {
            if (currentView != null && formController != null
                    && formController.currentPromptIsQuestion()) {
                saveAnswersForCurrentScreen(DO_NOT_EVALUATE_CONSTRAINTS);
            }
        }

        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();

        activityDisplayed();

        String navigation = settingsProvider.getGeneralSettings().getString(KEY_NAVIGATION);
        showNavigationButtons = navigation.contains(ProjectKeys.NAVIGATION_BUTTONS);

        findViewById(R.id.buttonholder).setVisibility(showNavigationButtons ? View.VISIBLE : View.GONE);

        if (showNavigationButtons) {
            updateNavigationButtonVisibility();
        }

        if (errorMessage != null) {
            if (alertDialog != null && !alertDialog.isShowing()) {
                createErrorDialog(errorMessage, true);
            } else {
                return;
            }
        }

        FormController formController = getFormController();

        if (formController != null && !formEntryViewModel.isFormControllerSet()) {
            Timber.e("FormController set in App but not ViewModel");
        }

        if (formLoaderTask != null) {
            formLoaderTask.setFormLoaderListener(this);
            if (formController == null
                    && formLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
                FormController fec = formLoaderTask.getFormController();
                if (fec != null) {
                    if (!readPhoneStatePermissionRequestNeeded) {
                        loadingComplete(formLoaderTask, formLoaderTask.getFormDef(), null);
                    }
                } else {
                    DialogUtils.dismissDialog(FormLoadingDialogFragment.class, getSupportFragmentManager());
                    FormLoaderTask t = formLoaderTask;
                    formLoaderTask = null;
                    t.cancel(true);
                    t.destroy();
                    // there is no formController -- fire MainMenu activity?
                    startActivity(new Intent(this, MainMenuActivity.class));
                }
            }
        } else {
            if (formController == null) {
                // there is no formController -- fire MainMenu activity?
                startActivity(new Intent(this, MainMenuActivity.class));
                finish();
                return;
            }
        }
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        /*
          Make sure the progress dialog is dismissed.
          In most cases that dialog is dismissed in MediaLoadingTask#onPostExecute() but if the app
          is in the background when MediaLoadingTask#onPostExecute() is called then the dialog
          can not be dismissed. In such a case we need to make sure it's dismissed in order
          to avoid blocking the UI.
         */
        if (!mediaLoadingFragment.isMediaLoadingTaskRunning()) {
            Fragment progressDialogFragment =
                    getSupportFragmentManager().findFragmentByTag(ProgressDialogFragment.COLLECT_PROGRESS_DIALOG_TAG);
            if (progressDialogFragment != null) {
                ((DialogFragment) progressDialogFragment).dismiss();
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (audioRecorder.isRecording() && !backgroundAudioViewModel.isBackgroundRecording()) {
                    // We want the user to stop recording before changing screens
                    DialogUtils.showIfNotShowing(RecordingWarningDialogFragment.class, getSupportFragmentManager());
                    return true;
                }

                showIfNotShowing(QuitFormDialogFragment.class, getSupportFragmentManager());
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.isAltPressed() && !swipeHandler.beenSwiped()) {
                    swipeHandler.setBeenSwiped(true);
                    onSwipeForward();
                    return true;
                }
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (event.isAltPressed() && !swipeHandler.beenSwiped()) {
                    swipeHandler.setBeenSwiped(true);
                    onSwipeBackward();
                    return true;
                }
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (formLoaderTask != null) {
            formLoaderTask.setFormLoaderListener(null);
            // We have to call cancel to terminate the thread, otherwise it
            // lives on and retains the FEC in memory.
            // but only if it's done, otherwise the thread never returns
            if (formLoaderTask.getStatus() == AsyncTask.Status.FINISHED) {
                FormLoaderTask t = formLoaderTask;
                formLoaderTask = null;
                t.cancel(true);
                t.destroy();
            }
        }

        releaseOdkView();
        compositeDisposable.dispose();

        try {
            unregisterReceiver(locationProvidersReceiver);
        } catch (IllegalArgumentException e) {
            // This is the common case -- the form didn't have location audits enabled so the
            // receiver was not registered.
        }

        super.onDestroy();
    }

    private int animationCompletionSet;

    private void afterAllAnimations() {
        if (getCurrentViewIfODKView() != null) {
            getCurrentViewIfODKView().setFocus(this);
        }
        swipeHandler.setBeenSwiped(false);
    }

    @Override
    public void onAnimationEnd(Animation animation) {
        if (inAnimation == animation) {
            animationCompletionSet |= 1;
        } else if (outAnimation == animation) {
            animationCompletionSet |= 2;
        } else {
            Timber.e("Unexpected animation");
        }

        if (animationCompletionSet == 3) {
            this.afterAllAnimations();
        }
    }

    @Override
    public void onAnimationRepeat(Animation animation) {
    }

    @Override
    public void onAnimationStart(Animation animation) {
    }

    /**
     * Given a {@link FormLoaderTask} which has created a {@link FormController} for either a new or
     * existing instance, shows that instance to the user. Either launches {@link FormHierarchyActivity}
     * if an existing instance is being edited or builds the view for the current question(s) if a
     * new instance is being created.
     * <p>
     * May do some or all of these depending on current state:
     * - Ensures phone state permissions are given if this form needs them
     * - Cleans up {@link #formLoaderTask}
     * - Sets the global form controller and database manager for search()/pulldata()
     * - Restores the last-used language
     * - Handles activity results that may have come in while the form was loading
     * - Alerts the user of a recovery from savepoint
     * - Verifies whether an instance folder exists and creates one if not
     * - Initializes background location capture (only if the instance being loaded is a new one)
     */
    @Override
    public void loadingComplete(FormLoaderTask task, FormDef formDef, String warningMsg) {
        DialogUtils.dismissDialog(FormLoadingDialogFragment.class, getSupportFragmentManager());

        final FormController formController = task.getFormController();

        if (formController != null) {
            if (readPhoneStatePermissionRequestNeeded) {
                permissionsProvider.requestReadPhoneStatePermission(this, true, new PermissionListener() {
                    @Override
                    public void granted() {
                        readPhoneStatePermissionRequestNeeded = false;
                        loadForm();
                    }

                    @Override
                    public void denied() {
                        finish();
                    }
                });
            } else {
                formLoaderTask.setFormLoaderListener(null);
                FormLoaderTask t = formLoaderTask;
                formLoaderTask = null;
                t.cancel(true);
                t.destroy();

                Collect.getInstance().setFormController(formController);

                backgroundLocationViewModel.formFinishedLoading();
                Collect.getInstance().setExternalDataManager(task.getExternalDataManager());

                // Set the language if one has already been set in the past
                String[] languageTest = formController.getLanguages();
                if (languageTest != null) {
                    String defaultLanguage = formController.getLanguage();
                    Form form = formsRepository.getOneByPath(formPath);

                    if (form != null) {
                        String newLanguage = form.getLanguage();

                        try {
                            formController.setLanguage(newLanguage);
                        } catch (Exception e) {
                            // if somehow we end up with a bad language, set it to the default
                            Timber.i("Ended up with a bad language. %s", newLanguage);
                            formController.setLanguage(defaultLanguage);
                        }
                    }
                }

                boolean pendingActivityResult = task.hasPendingActivityResult();

                if (pendingActivityResult) {
                    Timber.w("Calling onActivityResult from loadingComplete");

                    formControllerAvailable(formController);
                    onScreenRefresh();
                    onActivityResult(task.getRequestCode(), task.getResultCode(), task.getIntent());
                    return;
                }

                // it can be a normal flow for a pending activity result to restore from a savepoint
                // (the call flow handled by the above if statement). For all other use cases, the
                // user should be notified, as it means they wandered off doing other things then
                // returned to ODK Collect and chose Edit Saved Form, but that the savepoint for
                // that form is newer than the last saved version of their form data.
                boolean hasUsedSavepoint = task.hasUsedSavepoint();

                if (hasUsedSavepoint) {
                    runOnUiThread(() -> showLongToast(Collect.getInstance(), R.string.savepoint_used));
                }

                if (formController.getInstanceFile() == null) {
                    FormInstanceFileCreator formInstanceFileCreator = new FormInstanceFileCreator(
                            storagePathProvider,
                            System::currentTimeMillis
                    );

                    File instanceFile = formInstanceFileCreator.createInstanceFile(formPath);
                    if (instanceFile != null) {
                        formController.setInstanceFile(instanceFile);
                    } else {
                        showFormLoadErrorAndExit(getString(R.string.loading_form_failed));
                    }

                    formControllerAvailable(formController);
                    identityPromptViewModel.requiresIdentityToContinue().observe(this, requiresIdentity -> {
                        if (!requiresIdentity) {
                            formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.FORM_START, true, System.currentTimeMillis());
                            startFormEntry(formController, warningMsg);
                        }
                    });
                } else {
                    Intent reqIntent = getIntent();
                    boolean showFirst = reqIntent.getBooleanExtra("start", false);

                    if (!showFirst) {
                        // we've just loaded a saved form, so start in the hierarchy view
                        String formMode = reqIntent.getStringExtra(ApplicationConstants.BundleKeys.FORM_MODE);
                        if (formMode == null || ApplicationConstants.FormModes.EDIT_SAVED.equalsIgnoreCase(formMode)) {
                            formControllerAvailable(formController);
                            identityPromptViewModel.requiresIdentityToContinue().observe(this, requiresIdentity -> {
                                if (!requiresIdentity) {
                                    if (!allowMovingBackwards) {
                                        // we aren't allowed to jump around the form so attempt to
                                        // go directly to the question we were on last time the
                                        // form was saved.
                                        // TODO: revisit the fallback. If for some reason the index
                                        // wasn't saved, we can now jump around which doesn't seem right.
                                        FormIndex formIndex = SaveFormIndexTask.loadFormIndexFromFile();
                                        if (formIndex != null) {
                                            formController.jumpToIndex(formIndex);
                                            onScreenRefresh();
                                            return;
                                        }
                                    }

                                    formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.FORM_RESUME, true, System.currentTimeMillis());
                                    formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.HIERARCHY, true, System.currentTimeMillis());
                                    startActivityForResult(new Intent(this, FormHierarchyActivity.class), RequestCodes.HIERARCHY_ACTIVITY);
                                }
                            });

                            formSaveViewModel.editingForm();
                        } else {
                            if (ApplicationConstants.FormModes.VIEW_SENT.equalsIgnoreCase(formMode)) {
                                startActivity(new Intent(this, ViewOnlyFormHierarchyActivity.class));
                            }
                            finish();
                        }
                    } else {
                        formControllerAvailable(formController);
                        identityPromptViewModel.requiresIdentityToContinue().observe(this, requiresIdentity -> {
                            if (!requiresIdentity) {
                                formController.getAuditEventLogger().logEvent(AuditEvent.AuditEventType.FORM_RESUME, true, System.currentTimeMillis());
                                startFormEntry(formController, warningMsg);
                            }
                        });
                    }
                }
            }
        } else {
            Timber.e("FormController is null");
            showLongToast(Collect.getInstance(), R.string.loading_form_failed);
            finish();
        }
    }

    private void startFormEntry(FormController formController, String warningMsg) {
        // Register to receive location provider change updates and write them to the audit
        // log. onStart has already run but the formController was null so try again.
        if (formController.currentFormAuditsLocation()
                && new PlayServicesChecker().isGooglePlayServicesAvailable(this)) {
            registerReceiver(locationProvidersReceiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
        }

        // onResume ran before the form was loaded. Let the viewModel know that the activity
        // is about to be displayed and configured. Do this before the refresh actually
        // happens because if audit logging is enabled, the refresh logs a question event
        // and we want that to show up after initialization events.
        activityDisplayed();

        onScreenRefresh();

        if (warningMsg != null) {
            showLongToast(Collect.getInstance(), warningMsg);
            Timber.w(warningMsg);
        }
    }

    /**
     * called by the FormLoaderTask if something goes wrong.
     */
    @Override
    public void loadingError(String errorMsg) {
        showFormLoadErrorAndExit(errorMsg);
    }

    private void showFormLoadErrorAndExit(String errorMsg) {
        DialogUtils.dismissDialog(FormLoadingDialogFragment.class, getSupportFragmentManager());

        if (errorMsg != null) {
            createErrorDialog(errorMsg, true);
        } else {
            createErrorDialog(getString(R.string.parse_error), true);
        }
    }

    public void onProgressStep(String stepMessage) {
        showIfNotShowing(FormLoadingDialogFragment.class, getSupportFragmentManager());

        FormLoadingDialogFragment dialog = getDialog(FormLoadingDialogFragment.class, getSupportFragmentManager());
        if (dialog != null) {
            dialog.setMessage(getString(R.string.please_wait) + "\n\n" + stepMessage);
        }
    }

    public void next() {
        if (!swipeHandler.beenSwiped()) {
            swipeHandler.setBeenSwiped(true);
            onSwipeForward();
        }
    }

    /**
     * Returns the instance that was just filled out to the calling activity, if
     * requested.
     */
    private void finishAndReturnInstance() {
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_EDIT.equals(action)) {
            // caller is waiting on a picked form
            Uri uri = null;
            String path = getAbsoluteInstancePath();
            if (path != null) {
                Instance instance = new InstancesRepositoryProvider(this).get().getOneByPath(path);
                if (instance != null) {
                    uri = InstancesContract.getUri(currentProjectProvider.getCurrentProject().getUuid(), instance.getDbId());
                }
            }

            if (uri != null) {
                setResult(RESULT_OK, new Intent().setData(uri));
            }
        }

        finish();
    }

    @Override
    public void advance() {
        next();
    }

    @Override
    public void onSavePointError(String errorMessage) {
        if (errorMessage != null && errorMessage.trim().length() > 0) {
            showLongToast(Collect.getInstance(), getString(R.string.save_point_error, errorMessage));
        }
    }

    @Override
    public void onSaveFormIndexError(String errorMessage) {
        if (errorMessage != null && errorMessage.trim().length() > 0) {
            showLongToast(Collect.getInstance(), getString(R.string.save_point_error, errorMessage));
        }
    }

    @Override
    public void onNumberPickerValueSelected(int widgetId, int value) {
        if (currentView != null) {
            for (QuestionWidget qw : ((ODKView) currentView).getWidgets()) {
                if (qw instanceof RangePickerIntegerWidget && widgetId == qw.getId()) {
                    ((RangePickerIntegerWidget) qw).setNumberPickerValue(value);
                    widgetValueChanged(qw);
                    return;
                } else if (qw instanceof RangePickerDecimalWidget && widgetId == qw.getId()) {
                    ((RangePickerDecimalWidget) qw).setNumberPickerValue(value);
                    widgetValueChanged(qw);
                    return;
                }
            }
        }
    }

    @Override
    public void onDateChanged(LocalDateTime selectedDate) {
        onDataChanged(selectedDate);
    }

    @Override
    public void onTimeChanged(DateTime selectedTime) {
        onDataChanged(selectedTime);
    }

    @Override
    public void onRankingChanged(List<SelectChoice> items) {
        onDataChanged(items);
    }

    /*
     *TODO: this is not an ideal way to solve communication between a dialog created by a widget and the widget.
     * Instead we should use viewmodels: https://github.com/getodk/collect/pull/3964#issuecomment-670155433
     */
    @Override
    public void updateSelectedItems(List<Selection> items) {
        ODKView odkView = getCurrentViewIfODKView();
        if (odkView != null) {
            QuestionWidget widgetGettingNewValue = getWidgetWaitingForBinaryData();
            setWidgetData(items);
            widgetValueChanged(widgetGettingNewValue);
        }
    }

    @Override
    public void onCancelFormLoading() {
        if (formLoaderTask != null) {
            formLoaderTask.setFormLoaderListener(null);
            FormLoaderTask t = formLoaderTask;
            formLoaderTask = null;
            t.cancel(true);
            t.destroy();
        }
        finish();
    }

    private void onDataChanged(Object data) {
        ODKView odkView = getCurrentViewIfODKView();
        if (odkView != null) {
            QuestionWidget widgetGettingNewValue = getWidgetWaitingForBinaryData();
            setWidgetData(data);
            widgetValueChanged(widgetGettingNewValue);
        }
    }

    /**
     * getter for currentView variable. This method should always be used
     * to access currentView as an ODKView object to avoid inconsistency
     **/
    @Nullable
    public ODKView getCurrentViewIfODKView() {
        if (currentView instanceof ODKView) {
            return (ODKView) currentView;
        }
        return null;
    }

    /**
     * Used whenever we need to show empty view and be able to recognize it from the code
     */
    static class EmptyView extends View {

        EmptyView(Context context) {
            super(context);
        }
    }

    private class LocationProvidersReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction() != null
                    && intent.getAction().matches(LocationManager.PROVIDERS_CHANGED_ACTION)) {
                backgroundLocationViewModel.locationProvidersChanged();
            }
        }
    }

    private void activityDisplayed() {
        displayUIFor(backgroundLocationViewModel.activityDisplayed());

        if (backgroundLocationViewModel.isBackgroundLocationPermissionsCheckNeeded()) {
            permissionsProvider.requestLocationPermissions(this, new PermissionListener() {
                @Override
                public void granted() {
                    displayUIFor(backgroundLocationViewModel.locationPermissionsGranted());
                }

                @Override
                public void denied() {
                    backgroundLocationViewModel.locationPermissionsDenied();
                }
            });
        }
    }

    /**
     * Displays UI representing the given background location message, if there is one.
     */
    private void displayUIFor(@Nullable BackgroundLocationManager.BackgroundLocationMessage
                                      backgroundLocationMessage) {
        if (backgroundLocationMessage == null) {
            return;
        }

        if (backgroundLocationMessage == BackgroundLocationManager.BackgroundLocationMessage.PROVIDERS_DISABLED) {
            new LocationProvidersDisabledDialog().show(getSupportFragmentManager(), LocationProvidersDisabledDialog.LOCATION_PROVIDERS_DISABLED_DIALOG_TAG);
            return;
        }

        String snackBarText;

        if (backgroundLocationMessage.isMenuCharacterNeeded()) {
            snackBarText = String.format(getString(backgroundLocationMessage.getMessageTextResourceId()), "⋮");
        } else {
            snackBarText = getString(backgroundLocationMessage.getMessageTextResourceId());
        }

        SnackbarUtils.showLongSnackbar(findViewById(R.id.llParent), snackBarText);
    }

    @Override
    public void widgetValueChanged(QuestionWidget changedWidget) {
        FormController formController = getFormController();
        if (formController == null) {
            // TODO: As usual, no idea if/how this is possible.
            return;
        }

        if (formController.indexIsInFieldList()) {
            // Some widgets may call widgetValueChanged from a non-main thread but odkView can only be modified from the main thread
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    try {
                        updateFieldListQuestions(changedWidget.getFormEntryPrompt().getIndex());

                        odkView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                            @Override
                            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                                if (!odkView.isDisplayed(changedWidget)) {
                                    odkView.scrollTo(changedWidget);
                                }
                                odkView.removeOnLayoutChangeListener(this);
                            }
                        });
                    } catch (FormDesignException e) {
                        Timber.e(e);
                        createErrorDialog(e.getMessage(), false);
                    } catch (Exception | Error e) {
                        Timber.e(e);
                        createErrorDialog(getString(R.string.update_widgets_error), true);
                    }
                }
            });
        }
    }

    /**
     * Saves the form and updates displayed widgets accordingly:
     * - removes widgets corresponding to questions that are no longer relevant
     * - adds widgets corresponding to questions that are newly-relevant
     * - removes and rebuilds widgets corresponding to questions that have changed in some way. For
     * example, the question text or hint may have updated due to a value they refer to changing.
     * <p>
     * The widget corresponding to the {@param lastChangedIndex} is never changed.
     */
    private void updateFieldListQuestions(FormIndex lastChangedIndex) throws FormDesignException {
        // Save the user-visible state for all questions in this field-list
        FormEntryPrompt[] questionsBeforeSave = getFormController().getQuestionPrompts();
        List<ImmutableDisplayableQuestion> immutableQuestionsBeforeSave = new ArrayList<>();
        for (FormEntryPrompt questionBeforeSave : questionsBeforeSave) {
            immutableQuestionsBeforeSave.add(new ImmutableDisplayableQuestion(questionBeforeSave));
        }

        saveAnswersForCurrentScreen(questionsBeforeSave, immutableQuestionsBeforeSave);

        FormEntryPrompt[] questionsAfterSave = getFormController().getQuestionPrompts();

        Map<FormIndex, FormEntryPrompt> questionsAfterSaveByIndex = new HashMap<>();
        for (FormEntryPrompt question : questionsAfterSave) {
            questionsAfterSaveByIndex.put(question.getIndex(), question);
        }

        // Identify widgets to remove or rebuild (by removing and re-adding). We'd like to do the
        // identification and removal in the same pass but removal has to be done in a loop that
        // starts from the end and itemset-based select choices will only be correctly recomputed
        // if accessed from beginning to end because the call on sameAs is what calls
        // populateDynamicChoices. See https://github.com/getodk/javarosa/issues/436
        List<FormEntryPrompt> questionsThatHaveNotChanged = new ArrayList<>();
        List<FormIndex> formIndexesToRemove = new ArrayList<>();
        for (ImmutableDisplayableQuestion questionBeforeSave : immutableQuestionsBeforeSave) {
            FormEntryPrompt questionAtSameFormIndex = questionsAfterSaveByIndex.get(questionBeforeSave.getFormIndex());

            // Always rebuild questions that use database-driven external data features since they
            // bypass SelectChoices stored in ImmutableDisplayableQuestion
            if (questionBeforeSave.sameAs(questionAtSameFormIndex)
                    && !getFormController().usesDatabaseExternalDataFeature(questionBeforeSave.getFormIndex())) {
                questionsThatHaveNotChanged.add(questionAtSameFormIndex);
            } else if (!lastChangedIndex.equals(questionBeforeSave.getFormIndex())) {
                formIndexesToRemove.add(questionBeforeSave.getFormIndex());
            }
        }

        for (int i = immutableQuestionsBeforeSave.size() - 1; i >= 0; i--) {
            ImmutableDisplayableQuestion questionBeforeSave = immutableQuestionsBeforeSave.get(i);

            if (formIndexesToRemove.contains(questionBeforeSave.getFormIndex())) {
                odkView.removeWidgetAt(i);
            }
        }

        for (int i = 0; i < questionsAfterSave.length; i++) {
            if (!questionsThatHaveNotChanged.contains(questionsAfterSave[i])
                    && !questionsAfterSave[i].getIndex().equals(lastChangedIndex)) {
                // The values of widgets in intent groups are set by the view so widgetValueChanged
                // is never called. This means readOnlyOverride can always be set to false.
                odkView.addWidgetForQuestion(questionsAfterSave[i], i);
            }
        }
    }

    // If an answer has changed after saving one of previous answers that means it has been recalculated automatically
    private boolean isQuestionRecalculated(FormEntryPrompt mutableQuestionBeforeSave, ImmutableDisplayableQuestion immutableQuestionBeforeSave) {
        return !Objects.equals(mutableQuestionBeforeSave.getAnswerText(), immutableQuestionBeforeSave.getAnswerText());
    }

    private HashMap<FormIndex, IAnswerData> getAnswers() {
        ODKView currentViewIfODKView = getCurrentViewIfODKView();

        if (currentViewIfODKView != null) {
            return currentViewIfODKView.getAnswers();
        } else {
            return new HashMap<>();
        }
    }
}
