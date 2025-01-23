package org.odk.collect.android.injection.config;

import static androidx.core.content.FileProvider.getUriForFile;
import static org.odk.collect.androidshared.data.AppStateKt.getState;
import static org.odk.collect.settings.keys.MetaKeys.KEY_INSTALL_ID;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

import android.app.Application;
import android.content.Context;
import android.media.MediaPlayer;
import android.webkit.MimeTypeMap;

import androidx.work.WorkManager;

import com.google.gson.Gson;

import org.javarosa.core.reference.ReferenceManager;
import org.json.JSONArray;
import org.json.JSONObject;
import org.odk.collect.analytics.Analytics;
import org.odk.collect.analytics.BlockableFirebaseAnalytics;
import org.odk.collect.analytics.NoopAnalytics;
import org.odk.collect.android.BuildConfig;
import org.odk.collect.android.R;
import org.odk.collect.android.application.CollectSettingsChangeHandler;
import org.odk.collect.android.application.MapboxClassInstanceCreator;
import org.odk.collect.android.application.initialization.AnalyticsInitializer;
import org.odk.collect.android.application.initialization.ApplicationInitializer;
import org.odk.collect.android.application.initialization.CachedFormsCleaner;
import org.odk.collect.android.application.initialization.ExistingProjectMigrator;
import org.odk.collect.android.application.initialization.ExistingSettingsMigrator;
import org.odk.collect.android.application.initialization.GoogleDriveProjectsDeleter;
import org.odk.collect.android.application.initialization.MapsInitializer;
import org.odk.collect.android.application.initialization.SavepointsImporter;
import org.odk.collect.android.application.initialization.ScheduledWorkUpgrade;
import org.odk.collect.android.application.initialization.upgrade.UpgradeInitializer;
import org.odk.collect.android.backgroundwork.FormUpdateAndInstanceSubmitScheduler;
import org.odk.collect.android.backgroundwork.FormUpdateScheduler;
import org.odk.collect.android.backgroundwork.InstanceSubmitScheduler;
import org.odk.collect.android.configure.qr.AppConfigurationGenerator;
import org.odk.collect.android.configure.qr.CachingQRCodeGenerator;
import org.odk.collect.android.configure.qr.QRCodeGenerator;
import org.odk.collect.android.database.itemsets.DatabaseFastExternalItemsetsRepository;
import org.odk.collect.android.entities.EntitiesRepositoryProvider;
import org.odk.collect.android.external.InstancesContract;
import org.odk.collect.android.formentry.AppStateFormSessionRepository;
import org.odk.collect.android.formentry.FormSessionRepository;
import org.odk.collect.android.formentry.media.AudioHelperFactory;
import org.odk.collect.android.formentry.media.ScreenContextAudioHelperFactory;
import org.odk.collect.android.formlists.blankformlist.BlankFormListViewModel;
import org.odk.collect.android.formmanagement.CollectFormEntryControllerFactory;
import org.odk.collect.android.formmanagement.FormSourceProvider;
import org.odk.collect.android.formmanagement.FormsDataService;
import org.odk.collect.android.formmanagement.ServerFormsDetailsFetcher;
import org.odk.collect.android.geo.MapConfiguratorProvider;
import org.odk.collect.android.geo.MapFragmentFactoryImpl;
import org.odk.collect.android.instancemanagement.InstancesDataService;
import org.odk.collect.android.instancemanagement.autosend.AutoSendSettingsProvider;
import org.odk.collect.android.instancemanagement.send.ReadyToSendViewModel;
import org.odk.collect.android.itemsets.FastExternalItemsetsRepository;
import org.odk.collect.android.mainmenu.MainMenuViewModelFactory;
import org.odk.collect.android.notifications.NotificationManagerNotifier;
import org.odk.collect.android.notifications.Notifier;
import org.odk.collect.android.openrosa.CollectThenSystemContentTypeMapper;
import org.odk.collect.android.openrosa.OpenRosaHttpInterface;
import org.odk.collect.android.openrosa.okhttp.OkHttpConnection;
import org.odk.collect.android.openrosa.okhttp.OkHttpOpenRosaServerClientProvider;
import org.odk.collect.android.preferences.Defaults;
import org.odk.collect.android.preferences.PreferenceVisibilityHandler;
import org.odk.collect.android.preferences.ProjectPreferencesViewModel;
import org.odk.collect.android.preferences.source.SharedPreferencesSettingsProvider;
import org.odk.collect.android.projects.ProjectCreator;
import org.odk.collect.android.projects.ProjectDeleter;
import org.odk.collect.android.projects.ProjectResetter;
import org.odk.collect.android.projects.ProjectsDataService;
import org.odk.collect.android.storage.StoragePathProvider;
import org.odk.collect.android.storage.StorageSubdirectory;
import org.odk.collect.android.tasks.FormLoaderTask;
import org.odk.collect.android.utilities.AdminPasswordProvider;
import org.odk.collect.android.utilities.AndroidUserAgent;
import org.odk.collect.android.utilities.ChangeLockProvider;
import org.odk.collect.android.utilities.CodeCaptureManagerFactory;
import org.odk.collect.android.utilities.ContentUriProvider;
import org.odk.collect.android.utilities.ExternalAppIntentProvider;
import org.odk.collect.android.utilities.FileProvider;
import org.odk.collect.android.utilities.FormsRepositoryProvider;
import org.odk.collect.android.utilities.ImageCompressionController;
import org.odk.collect.android.utilities.InstancesRepositoryProvider;
import org.odk.collect.android.utilities.MediaUtils;
import org.odk.collect.android.utilities.SavepointsRepositoryProvider;
import org.odk.collect.android.utilities.SoftKeyboardController;
import org.odk.collect.android.utilities.WebCredentialsUtils;
import org.odk.collect.android.version.VersionInformation;
import org.odk.collect.android.views.BarcodeViewDecoder;
import org.odk.collect.androidshared.bitmap.ImageCompressor;
import org.odk.collect.androidshared.system.IntentLauncher;
import org.odk.collect.androidshared.system.IntentLauncherImpl;
import org.odk.collect.androidshared.utils.ScreenUtils;
import org.odk.collect.async.CoroutineAndWorkManagerScheduler;
import org.odk.collect.async.Scheduler;
import org.odk.collect.async.network.ConnectivityProvider;
import org.odk.collect.async.network.NetworkStateProvider;
import org.odk.collect.audiorecorder.recording.AudioRecorder;
import org.odk.collect.audiorecorder.recording.AudioRecorderFactory;
import org.odk.collect.entities.storage.EntitiesRepository;
import org.odk.collect.forms.FormsRepository;
import org.odk.collect.imageloader.GlideImageLoader;
import org.odk.collect.imageloader.ImageLoader;
import org.odk.collect.location.GoogleFusedLocationClient;
import org.odk.collect.location.LocationClient;
import org.odk.collect.location.LocationClientProvider;
import org.odk.collect.maps.MapFragmentFactory;
import org.odk.collect.maps.layers.DirectoryReferenceLayerRepository;
import org.odk.collect.maps.layers.ReferenceLayerRepository;
import org.odk.collect.metadata.InstallIDProvider;
import org.odk.collect.metadata.PropertyManager;
import org.odk.collect.metadata.SettingsInstallIDProvider;
import org.odk.collect.permissions.ContextCompatPermissionChecker;
import org.odk.collect.permissions.PermissionsChecker;
import org.odk.collect.permissions.PermissionsProvider;
import org.odk.collect.projects.Project;
import org.odk.collect.projects.ProjectsRepository;
import org.odk.collect.projects.SharedPreferencesProjectsRepository;
import org.odk.collect.qrcode.QRCodeCreatorImpl;
import org.odk.collect.qrcode.QRCodeDecoder;
import org.odk.collect.qrcode.QRCodeDecoderImpl;
import org.odk.collect.settings.ODKAppSettingsImporter;
import org.odk.collect.settings.ODKAppSettingsMigrator;
import org.odk.collect.settings.SettingsProvider;
import org.odk.collect.settings.importing.ProjectDetailsCreatorImpl;
import org.odk.collect.settings.importing.SettingsChangeHandler;
import org.odk.collect.settings.keys.AppConfigurationKeys;
import org.odk.collect.settings.keys.MetaKeys;
import org.odk.collect.settings.keys.ProjectKeys;
import org.odk.collect.shared.strings.UUIDGenerator;
import org.odk.collect.utilities.UserAgentProvider;
import org.odk.collect.webpage.ExternalWebPageHelper;

import java.io.File;

import javax.inject.Named;
import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import okhttp3.OkHttpClient;

/**
 * Add dependency providers here (annotated with @Provides)
 * for objects you need to inject
 */
@Module
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class AppDependencyModule {

    @Provides
    Context context(Application application) {
        return application;
    }

    @Provides
    MimeTypeMap provideMimeTypeMap() {
        return MimeTypeMap.getSingleton();
    }

    @Provides
    @Singleton
    UserAgentProvider providesUserAgent() {
        return new AndroidUserAgent();
    }

    @Provides
    @Singleton
    public OpenRosaHttpInterface provideHttpInterface(MimeTypeMap mimeTypeMap, UserAgentProvider userAgentProvider, Application application, VersionInformation versionInformation) {
        String cacheDir = application.getCacheDir().getAbsolutePath();
        return new OkHttpConnection(
                new OkHttpOpenRosaServerClientProvider(new OkHttpClient(), cacheDir),
                new CollectThenSystemContentTypeMapper(mimeTypeMap),
                userAgentProvider.getUserAgent()
        );
    }

    @Provides
    WebCredentialsUtils provideWebCredentials(SettingsProvider settingsProvider) {
        return new WebCredentialsUtils(settingsProvider.getUnprotectedSettings());
    }

    @Provides
    @Singleton
    public Analytics providesAnalytics(Application application) {
        try {
            return new BlockableFirebaseAnalytics(application);
        } catch (IllegalStateException e) {
            // Couldn't setup Firebase so use no-op instance
            return new NoopAnalytics();
        }
    }

    @Provides
    public PermissionsProvider providesPermissionsProvider(PermissionsChecker permissionsChecker) {
        return new PermissionsProvider(permissionsChecker);
    }

    @Provides
    public ReferenceManager providesReferenceManager() {
        return ReferenceManager.instance();
    }

    @Provides
    public AudioHelperFactory providesAudioHelperFactory(Scheduler scheduler) {
        return new ScreenContextAudioHelperFactory(scheduler, MediaPlayer::new);
    }

    @Provides
    @Singleton
    public SettingsProvider providesSettingsProvider(Context context) {
        return new SharedPreferencesSettingsProvider(context);
    }


    @Provides
    public InstallIDProvider providesInstallIDProvider(SettingsProvider settingsProvider) {
        return new SettingsInstallIDProvider(settingsProvider.getMetaSettings(), KEY_INSTALL_ID);
    }

    @Provides
    public StoragePathProvider providesStoragePathProvider(Context context, ProjectsDataService projectsDataService, ProjectsRepository projectsRepository) {
        File externalFilesDir = context.getExternalFilesDir(null);

        if (externalFilesDir != null) {
            return new StoragePathProvider(projectsDataService, projectsRepository, externalFilesDir.getAbsolutePath());
        } else {
            throw new IllegalStateException("Storage is not available!");
        }
    }

    @Provides
    public AdminPasswordProvider providesAdminPasswordProvider(SettingsProvider settingsProvider) {
        return new AdminPasswordProvider(settingsProvider.getProtectedSettings());
    }

    @Provides
    public FormUpdateScheduler providesFormUpdateManger(Scheduler scheduler, SettingsProvider settingsProvider, Application application) {
        return new FormUpdateAndInstanceSubmitScheduler(scheduler, settingsProvider, application);
    }

    @Provides
    public InstanceSubmitScheduler providesFormSubmitManager(Scheduler scheduler, SettingsProvider settingsProvider, Application application) {
        return new FormUpdateAndInstanceSubmitScheduler(scheduler, settingsProvider, application);
    }

    @Provides
    public NetworkStateProvider providesNetworkStateProvider(Context context) {
        return new ConnectivityProvider(context);
    }

    @Provides
    public QRCodeGenerator providesQRCodeGenerator() {
        return new CachingQRCodeGenerator(new QRCodeCreatorImpl());
    }

    @Provides
    public VersionInformation providesVersionInformation() {
        return new VersionInformation(() -> BuildConfig.VERSION_NAME);
    }

    @Provides
    public FileProvider providesFileProvider(Context context) {
        return filePath -> getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", new File(filePath));
    }

    @Provides
    public WorkManager providesWorkManager(Context context) {
        return WorkManager.getInstance(context);
    }

    @Provides
    public Scheduler providesScheduler(WorkManager workManager) {
        return new CoroutineAndWorkManagerScheduler(workManager);
    }

    @Provides
    public ODKAppSettingsMigrator providesPreferenceMigrator(SettingsProvider settingsProvider) {
        return new ODKAppSettingsMigrator(settingsProvider.getMetaSettings());
    }

    @Provides
    @Singleton
    public PropertyManager providesPropertyManager(InstallIDProvider installIDProvider, SettingsProvider settingsProvider) {
        return new PropertyManager(installIDProvider, settingsProvider);
    }

    @Provides
    public SettingsChangeHandler providesSettingsChangeHandler(PropertyManager propertyManager, FormUpdateScheduler formUpdateScheduler, FormsDataService formsDataService) {
        return new CollectSettingsChangeHandler(propertyManager, formUpdateScheduler, formsDataService);
    }

    @Provides
    public ODKAppSettingsImporter providesODKAppSettingsImporter(Context context, ProjectsRepository projectsRepository, SettingsProvider settingsProvider, SettingsChangeHandler settingsChangeHandler) {
        JSONObject deviceUnsupportedSettings = new JSONObject();
        if (!MapboxClassInstanceCreator.isMapboxAvailable()) {
            try {
                deviceUnsupportedSettings.put(
                        AppConfigurationKeys.GENERAL,
                        new JSONObject().put(ProjectKeys.KEY_BASEMAP_SOURCE, new JSONArray(singletonList(ProjectKeys.BASEMAP_SOURCE_MAPBOX)))
                );
            } catch (Throwable ignored) {
                // ignore
            }
        }

        return new ODKAppSettingsImporter(
                projectsRepository,
                settingsProvider,
                Defaults.getUnprotected(),
                Defaults.getProtected(),
                asList(context.getResources().getStringArray(R.array.project_colors)),
                settingsChangeHandler,
                deviceUnsupportedSettings
        );
    }

    @Provides
    public BarcodeViewDecoder providesBarcodeViewDecoder() {
        return new BarcodeViewDecoder();
    }

    @Provides
    public QRCodeDecoder providesQRCodeDecoder() {
        return new QRCodeDecoderImpl();
    }

    @Provides
    public ServerFormsDetailsFetcher providesServerFormDetailsFetcher(FormsRepositoryProvider formsRepositoryProvider, FormSourceProvider formSourceProvider, ProjectsDataService projectsDataService) {
        Project.Saved currentProject = projectsDataService.requireCurrentProject();
        FormsRepository formsRepository = formsRepositoryProvider.create(currentProject.getUuid());
        return new ServerFormsDetailsFetcher(formsRepository, formSourceProvider.create(currentProject.getUuid()));
    }

    @Provides
    public Notifier providesNotifier(Application application, SettingsProvider settingsProvider, ProjectsRepository projectsRepository) {
        return new NotificationManagerNotifier(application, settingsProvider, projectsRepository);
    }

    @Provides
    @Singleton
    public ChangeLockProvider providesChangeLockProvider() {
        return new ChangeLockProvider();
    }

    @Provides
    ScreenUtils providesScreenUtils(Context context) {
        return new ScreenUtils(context);
    }

    @Provides
    public AudioRecorder providesAudioRecorder(Application application) {
        return new AudioRecorderFactory(application).create();
    }

    @Provides
    public EntitiesRepositoryProvider provideEntitiesRepositoryProvider(Context context, StoragePathProvider storagePathProvider) {
        return new EntitiesRepositoryProvider(context, storagePathProvider);
    }

    @Provides
    public SoftKeyboardController provideSoftKeyboardController() {
        return SoftKeyboardController.INSTANCE;
    }

    @Provides
    public AppConfigurationGenerator providesJsonPreferencesGenerator(SettingsProvider settingsProvider, ProjectsDataService projectsDataService) {
        return new AppConfigurationGenerator(settingsProvider, projectsDataService);
    }

    @Provides
    @Singleton
    public PermissionsChecker providesPermissionsChecker(Context context) {
        return new ContextCompatPermissionChecker(context);
    }

    @Provides
    @Singleton
    public ExternalAppIntentProvider providesExternalAppIntentProvider() {
        return new ExternalAppIntentProvider();
    }

    @Provides
    public FormSessionRepository providesFormSessionStore(Application application) {
        return new AppStateFormSessionRepository(application);
    }

    @Provides
    public ExternalWebPageHelper providesExternalWebPageHelper() {
        return new ExternalWebPageHelper();
    }

    @Provides
    @Singleton
    public ProjectsRepository providesProjectsRepository(UUIDGenerator uuidGenerator, Gson gson, SettingsProvider settingsProvider) {
        return new SharedPreferencesProjectsRepository(uuidGenerator, gson, settingsProvider.getMetaSettings(), MetaKeys.KEY_PROJECTS);
    }

    @Provides
    public ProjectCreator providesProjectCreator(ProjectsRepository projectsRepository, ProjectsDataService projectsDataService,
                                                 ODKAppSettingsImporter settingsImporter, SettingsProvider settingsProvider) {
        return new ProjectCreator(projectsRepository, projectsDataService, settingsImporter, settingsProvider);
    }

    @Provides
    public Gson providesGson() {
        return new Gson();
    }

    @Provides
    @Singleton
    public UUIDGenerator providesUUIDGenerator() {
        return new UUIDGenerator();
    }

    @Provides
    public InstancesDataService providesInstancesDataService(Application application, ProjectsDataService projectsDataService, InstanceSubmitScheduler instanceSubmitScheduler, ProjectDependencyModuleFactory projectsDependencyProviderFactory, Notifier notifier, PropertyManager propertyManager, OpenRosaHttpInterface httpInterface) {
        Function0<Unit> onUpdate = () -> {
            application.getContentResolver().notifyChange(
                    InstancesContract.getUri(projectsDataService.requireCurrentProject().getUuid()),
                    null
            );

            return null;
        };

        return new InstancesDataService(getState(application), instanceSubmitScheduler, projectsDependencyProviderFactory, notifier, propertyManager, httpInterface, onUpdate);
    }

    @Provides
    public FastExternalItemsetsRepository providesItemsetsRepository() {
        return new DatabaseFastExternalItemsetsRepository();
    }

    @Provides
    public ProjectsDataService providesCurrentProjectProvider(Application application, SettingsProvider settingsProvider, ProjectsRepository projectsRepository, AnalyticsInitializer analyticsInitializer, Context context, MapsInitializer mapsInitializer) {
        return new ProjectsDataService(getState(application), settingsProvider, projectsRepository, analyticsInitializer, mapsInitializer);
    }

    @Provides
    public FormsRepositoryProvider providesFormsRepositoryProvider(Application application) {
        return new FormsRepositoryProvider(application);
    }

    @Provides
    public InstancesRepositoryProvider providesInstancesRepositoryProvider(Context context, StoragePathProvider storagePathProvider) {
        return new InstancesRepositoryProvider(context, storagePathProvider);
    }

    @Provides
    public SavepointsRepositoryProvider providesSavepointsRepositoryProvider(Context context, StoragePathProvider storagePathProvider) {
        return new SavepointsRepositoryProvider(context, storagePathProvider);
    }

    @Provides
    public ProjectPreferencesViewModel.Factory providesProjectPreferencesViewModel(AdminPasswordProvider adminPasswordProvider) {
        return new ProjectPreferencesViewModel.Factory(adminPasswordProvider);
    }

    @Provides
    public ReadyToSendViewModel.Factory providesReadyToSendViewModel(InstancesRepositoryProvider instancesRepositoryProvider, Scheduler scheduler) {
        return new ReadyToSendViewModel.Factory(instancesRepositoryProvider.create(), scheduler, System::currentTimeMillis);
    }

    @Provides
    public MainMenuViewModelFactory providesMainMenuViewModelFactory(VersionInformation versionInformation, Application application,
                                                                     SettingsProvider settingsProvider, InstancesDataService instancesDataService,
                                                                     Scheduler scheduler, ProjectsDataService projectsDataService,
                                                                     AnalyticsInitializer analyticsInitializer, PermissionsChecker permissionChecker,
                                                                     FormsRepositoryProvider formsRepositoryProvider, InstancesRepositoryProvider instancesRepositoryProvider,
                                                                     AutoSendSettingsProvider autoSendSettingsProvider) {
        return new MainMenuViewModelFactory(versionInformation, application, settingsProvider, instancesDataService, scheduler, projectsDataService, permissionChecker, formsRepositoryProvider, instancesRepositoryProvider, autoSendSettingsProvider);
    }

    @Provides
    public AnalyticsInitializer providesAnalyticsInitializer(Analytics analytics, VersionInformation versionInformation, SettingsProvider settingsProvider) {
        return new AnalyticsInitializer(analytics, versionInformation, settingsProvider);
    }

    @Provides
    public FormSourceProvider providesFormSourceProvider(SettingsProvider settingsProvider, OpenRosaHttpInterface openRosaHttpInterface) {
        return new FormSourceProvider(settingsProvider::getUnprotectedSettings, openRosaHttpInterface);
    }

    @Provides
    public FormsDataService providesFormsUpdater(Application application, Notifier notifier, ProjectDependencyModuleFactory projectDependencyModuleFactory) {
        return new FormsDataService(getState(application), notifier, projectDependencyModuleFactory, System::currentTimeMillis);
    }

    @Provides
    public AutoSendSettingsProvider providesAutoSendSettingsProvider(Application application, SettingsProvider settingsProvider, NetworkStateProvider networkStateProvider) {
        return new AutoSendSettingsProvider(application, networkStateProvider, settingsProvider);
    }

    @Provides
    public CodeCaptureManagerFactory providesCodeCaptureManagerFactory() {
        return CodeCaptureManagerFactory.INSTANCE;
    }

    @Provides
    public ExistingProjectMigrator providesExistingProjectMigrator(Context context, StoragePathProvider storagePathProvider, ProjectsRepository projectsRepository, SettingsProvider settingsProvider, ProjectsDataService projectsDataService) {
        return new ExistingProjectMigrator(context, storagePathProvider, projectsRepository, settingsProvider, projectsDataService, new ProjectDetailsCreatorImpl(asList(context.getResources().getStringArray(R.array.project_colors)), Defaults.getUnprotected()));
    }

    @Provides
    public ScheduledWorkUpgrade providesFormUpdatesUpgrader(Scheduler scheduler, ProjectsRepository projectsRepository, FormUpdateScheduler formUpdateScheduler, InstanceSubmitScheduler instanceSubmitScheduler) {
        return new ScheduledWorkUpgrade(scheduler, projectsRepository, formUpdateScheduler, instanceSubmitScheduler);
    }

    @Provides
    public ExistingSettingsMigrator providesExistingSettingsMigrator(ProjectsRepository projectsRepository, SettingsProvider settingsProvider, ODKAppSettingsMigrator settingsMigrator) {
        return new ExistingSettingsMigrator(projectsRepository, settingsProvider, settingsMigrator);
    }

    @Provides
    public GoogleDriveProjectsDeleter providesGoogleDriveProjectsDeleter(ProjectsRepository projectsRepository, SettingsProvider settingsProvider, ProjectDeleter projectDeleter) {
        return new GoogleDriveProjectsDeleter(projectsRepository, settingsProvider, projectDeleter);
    }

    @Provides
    public UpgradeInitializer providesUpgradeInitializer(Context context, SettingsProvider settingsProvider, ExistingProjectMigrator existingProjectMigrator, ExistingSettingsMigrator existingSettingsMigrator, ScheduledWorkUpgrade scheduledWorkUpgrade, GoogleDriveProjectsDeleter googleDriveProjectsDeleter, ProjectsRepository projectsRepository, ProjectDependencyModuleFactory projectDependencyModuleFactory) {
        return new UpgradeInitializer(
                context,
                settingsProvider,
                existingProjectMigrator,
                existingSettingsMigrator,
                scheduledWorkUpgrade,
                googleDriveProjectsDeleter,
                new SavepointsImporter(projectsRepository, projectDependencyModuleFactory),
                new CachedFormsCleaner(projectsRepository, projectDependencyModuleFactory)
        );
    }

    @Provides
    public ApplicationInitializer providesApplicationInitializer(Application context, PropertyManager propertyManager, Analytics analytics, UpgradeInitializer upgradeInitializer, AnalyticsInitializer analyticsInitializer, ProjectsRepository projectsRepository, SettingsProvider settingsProvider, MapsInitializer mapsInitializer, EntitiesRepositoryProvider entitiesRepositoryProvider, ProjectsDataService projectsDataService) {
        return new ApplicationInitializer(context, propertyManager, analytics, upgradeInitializer, analyticsInitializer, mapsInitializer, projectsRepository, settingsProvider, entitiesRepositoryProvider, projectsDataService);
    }

    @Provides
    public ProjectDeleter providesProjectDeleter(ProjectsRepository projectsRepository, ProjectsDataService projectsDataService, FormUpdateScheduler formUpdateScheduler, InstanceSubmitScheduler instanceSubmitScheduler, InstancesRepositoryProvider instancesRepositoryProvider, StoragePathProvider storagePathProvider, ChangeLockProvider changeLockProvider, SettingsProvider settingsProvider) {
        return new ProjectDeleter(projectsRepository, projectsDataService, formUpdateScheduler, instanceSubmitScheduler, instancesRepositoryProvider, storagePathProvider, changeLockProvider, settingsProvider);
    }

    @Provides
    public ProjectResetter providesProjectResetter(StoragePathProvider storagePathProvider, PropertyManager propertyManager, SettingsProvider settingsProvider, FormsRepositoryProvider formsRepositoryProvider, SavepointsRepositoryProvider savepointsRepositoryProvider, InstancesDataService instancesDataService, ProjectsDataService projectsDataService) {
        return new ProjectResetter(storagePathProvider, propertyManager, settingsProvider, formsRepositoryProvider, savepointsRepositoryProvider, instancesDataService, projectsDataService.requireCurrentProject().getUuid());
    }

    @Provides
    public PreferenceVisibilityHandler providesDisabledPreferencesRemover(SettingsProvider settingsProvider, VersionInformation versionInformation) {
        return new PreferenceVisibilityHandler(settingsProvider, versionInformation);
    }

    @Provides
    public ReferenceLayerRepository providesReferenceLayerRepository(StoragePathProvider storagePathProvider, SettingsProvider settingsProvider) {
        return new DirectoryReferenceLayerRepository(
                storagePathProvider.getOdkDirPath(StorageSubdirectory.SHARED_LAYERS),
                storagePathProvider.getOdkDirPath(StorageSubdirectory.LAYERS),
                () -> MapConfiguratorProvider.getConfigurator(
                        settingsProvider.getUnprotectedSettings().getString(ProjectKeys.KEY_BASEMAP_SOURCE)
                )
        );
    }

    @Provides
    public IntentLauncher providesIntentLauncher() {
        return IntentLauncherImpl.INSTANCE;
    }

    @Provides
    public LocationClient providesLocationClient(Application application) {
        return LocationClientProvider.getClient(application);
    }

    @Provides
    @Named("fused")
    public LocationClient providesFusedLocationClient(Application application) {
        return new GoogleFusedLocationClient(application);
    }

    @Provides
    public MediaUtils providesMediaUtils(IntentLauncher intentLauncher) {
        return new MediaUtils(intentLauncher, new ContentUriProvider());
    }

    @Provides
    public MapFragmentFactory providesMapFragmentFactory(SettingsProvider settingsProvider) {
        return new MapFragmentFactoryImpl(settingsProvider);
    }

    @Provides
    public ImageLoader providesImageLoader() {
        return new GlideImageLoader();
    }

    @Provides
    public BlankFormListViewModel.Factory providesBlankFormListViewModel(FormsRepositoryProvider formsRepositoryProvider, InstancesRepositoryProvider instancesRepositoryProvider, Application application, FormsDataService formsDataService, Scheduler scheduler, SettingsProvider settingsProvider, ChangeLockProvider changeLockProvider, ProjectsDataService projectsDataService) {
        return new BlankFormListViewModel.Factory(instancesRepositoryProvider.create(), application, formsDataService, scheduler, settingsProvider.getUnprotectedSettings(), projectsDataService.requireCurrentProject().getUuid());
    }

    @Provides
    @Singleton
    public ImageCompressionController providesImageCompressorManager() {
        return new ImageCompressionController(ImageCompressor.INSTANCE);
    }

    @Provides
    public FormLoaderTask.FormEntryControllerFactory formEntryControllerFactory(ProjectsDataService projectsDataService, EntitiesRepositoryProvider entitiesRepositoryProvider, SettingsProvider settingsProvider) {
        String projectId = projectsDataService.requireCurrentProject().getUuid();
        EntitiesRepository entitiesRepository = entitiesRepositoryProvider.create(projectId);
        return new CollectFormEntryControllerFactory(entitiesRepository, settingsProvider.getUnprotectedSettings(projectId));
    }
}
