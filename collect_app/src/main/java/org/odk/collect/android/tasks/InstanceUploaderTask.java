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

package org.odk.collect.android.tasks;

import static org.odk.collect.android.analytics.AnalyticsEvents.SUBMISSION;
import static org.odk.collect.strings.localization.LocalizedApplicationKt.getLocalizedString;

import android.net.Uri;
import android.os.AsyncTask;

import org.odk.collect.analytics.Analytics;
import org.odk.collect.android.analytics.AnalyticsEvents;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.instancemanagement.InstanceDeleter;
import org.odk.collect.android.instancemanagement.InstancesDataService;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.openrosa.OpenRosaHttpInterface;
import org.odk.collect.android.projects.ProjectsDataService;
import org.odk.collect.android.upload.FormUploadAuthRequestedException;
import org.odk.collect.android.upload.FormUploadException;
import org.odk.collect.android.upload.InstanceServerUploader;
import org.odk.collect.android.utilities.InstanceAutoDeleteChecker;
import org.odk.collect.android.utilities.InstancesRepositoryProvider;
import org.odk.collect.android.utilities.WebCredentialsUtils;
import org.odk.collect.forms.FormsRepository;
import org.odk.collect.forms.instances.Instance;
import org.odk.collect.forms.instances.InstancesRepository;
import org.odk.collect.metadata.PropertyManager;
import org.odk.collect.settings.SettingsProvider;
import org.odk.collect.settings.keys.ProjectKeys;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

/**
 * Background task for uploading completed forms.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class InstanceUploaderTask extends AsyncTask<Long, Integer, InstanceUploaderTask.Outcome> {
    @Inject
    OpenRosaHttpInterface httpInterface;

    @Inject
    WebCredentialsUtils webCredentialsUtils;

    @Inject
    PropertyManager propertyManager;

    @Inject
    InstancesDataService instancesDataService;

    @Inject
    ProjectsDataService projectsDataService;

    // Custom submission URL, username and password that can be sent via intent extras by external
    // applications
    private String completeDestinationUrl;
    private String referrer;
    private String customUsername;
    private String customPassword;
    private InstancesRepository instancesRepository;
    private FormsRepository formsRepository;
    private SettingsProvider settingsProvider;
    private InstanceUploaderListener stateListener;
    private Boolean deleteInstanceAfterSubmission;

    public InstanceUploaderTask() {
        Collect.getInstance().getComponent().inject(this);
    }

    @Override
    public Outcome doInBackground(Long... instanceIdsToUpload) {
        Outcome outcome = new Outcome();

        InstanceServerUploader uploader = new InstanceServerUploader(httpInterface, webCredentialsUtils, settingsProvider.getUnprotectedSettings(), instancesRepository);
        List<Instance> instancesToUpload = uploader
                .getInstancesFromIds(instanceIdsToUpload)
                .stream()
                .sorted(Comparator.comparing(Instance::getLastStatusChangeDate))
                .collect(Collectors.toList());

        String deviceId = propertyManager.getSingularProperty(PropertyManager.PROPMGR_DEVICE_ID);

        for (int i = 0; i < instancesToUpload.size(); i++) {
            if (isCancelled()) {
                return outcome;
            }
            Instance instance = instancesToUpload.get(i);

            publishProgress(i + 1, instancesToUpload.size());

            if (completeDestinationUrl != null) {
                Analytics.log(AnalyticsEvents.INSTANCE_UPLOAD_CUSTOM_SERVER, "label", referrer != null ? referrer : "");
            }

            try {
                String destinationUrl = uploader.getUrlToSubmitTo(instance, deviceId, completeDestinationUrl, null);
                String customMessage = uploader.uploadOneSubmission(instance, destinationUrl);
                outcome.messagesByInstanceId.put(instance.getDbId().toString(),
                        customMessage != null ? customMessage : getLocalizedString(Collect.getInstance(), org.odk.collect.strings.R.string.success));

                Analytics.log(SUBMISSION, "HTTP", Collect.getFormIdentifierHash(instance.getFormId(), instance.getFormVersion()));
            } catch (FormUploadAuthRequestedException e) {
                outcome.authRequestingServer = e.getAuthRequestingServer();
                // Don't add the instance that caused an auth request to the map because we want to
                // retry. Items present in the map are considered already attempted and won't be
                // retried.
            } catch (FormUploadException e) {
                outcome.messagesByInstanceId.put(instance.getDbId().toString(),
                        e.getMessage());
            }
        }

        // Delete instances that were successfully sent and that need to be deleted
        // either because app-level auto-delete is enabled or because the form
        // specifies it.
        Set<String> instanceIds = outcome.messagesByInstanceId.keySet();

        boolean isFormAutoDeleteOptionEnabled;

        // The custom configuration from the third party app overrides
        // the app preferences set for delete after submission
        if (deleteInstanceAfterSubmission != null) {
            isFormAutoDeleteOptionEnabled = deleteInstanceAfterSubmission;
        } else {
            isFormAutoDeleteOptionEnabled = settingsProvider.getUnprotectedSettings().getBoolean(ProjectKeys.KEY_DELETE_AFTER_SEND);
        }

        Stream<Instance> instancesToDelete = instanceIds.stream()
                .map(id -> new InstancesRepositoryProvider(Collect.getInstance()).create().get(Long.parseLong(id)))
                .filter(instance -> instance.getStatus().equals(Instance.STATUS_SUBMITTED))
                .filter(instance -> InstanceAutoDeleteChecker.shouldInstanceBeDeleted(formsRepository, isFormAutoDeleteOptionEnabled, instance));

        InstanceDeleter instanceDeleter = new InstanceDeleter(instancesRepository, formsRepository);
        instanceDeleter.delete(instancesToDelete.map(Instance::getDbId).toArray(Long[]::new));

        instancesDataService.update(projectsDataService.requireCurrentProject().getUuid());
        return outcome;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        synchronized (this) {
            if (stateListener != null) {
                stateListener.progressUpdate(values[0], values[1]);
            }
        }
    }

    @Override
    protected void onPostExecute(Outcome outcome) {
        synchronized (this) {
            if (outcome != null && stateListener != null) {
                if (outcome.authRequestingServer != null) {
                    stateListener.authRequest(outcome.authRequestingServer, outcome.messagesByInstanceId);
                } else {
                    stateListener.uploadingComplete(outcome.messagesByInstanceId);
                }
            }
        }

        // Clear temp credentials
        clearTemporaryCredentials();
    }

    @Override
    protected void onCancelled() {
        clearTemporaryCredentials();
    }

    public void setUploaderListener(InstanceUploaderListener sl) {
        synchronized (this) {
            stateListener = sl;
        }
    }

    public void setDeleteInstanceAfterSubmission(Boolean deleteInstanceAfterSubmission) {
        this.deleteInstanceAfterSubmission = deleteInstanceAfterSubmission;
    }

    public void setRepositories(InstancesRepository instancesRepository, FormsRepository formsRepository, SettingsProvider settingsProvider) {
        this.instancesRepository = instancesRepository;
        this.formsRepository = formsRepository;
        this.settingsProvider = settingsProvider;
    }

    public void setCompleteDestinationUrl(String completeDestinationUrl, String referrer, boolean clearPreviousConfig) {
        this.completeDestinationUrl = completeDestinationUrl;
        this.referrer = referrer;
        if (clearPreviousConfig) {
            setTemporaryCredentials();
        }
    }

    public void setCustomUsername(String customUsername) {
        this.customUsername = customUsername;
        setTemporaryCredentials();
    }

    public void setCustomPassword(String customPassword) {
        this.customPassword = customPassword;
        setTemporaryCredentials();
    }

    private void setTemporaryCredentials() {
        if (customUsername != null && customPassword != null) {
            webCredentialsUtils.saveCredentials(completeDestinationUrl, customUsername, customPassword);
        } else {
            // In the case for anonymous logins, clear the previous credentials for that host
            webCredentialsUtils.clearCredentials(completeDestinationUrl);
        }
    }

    private void clearTemporaryCredentials() {
        if (customUsername != null && customPassword != null) {
            webCredentialsUtils.clearCredentials(completeDestinationUrl);
        }
    }

    /**
     * Represents the results of a submission attempt triggered by explicit user action (as opposed
     * to auto-send). A submission attempt can include finalized forms going to several different
     * servers because the app-level server configuration can be overridden by the blank form.
     * <p>
     * The user-facing message that describes the result of a submission attempt for each specific
     * finalized form is written messages to {@link #messagesByInstanceId}. In the case of an
     * authentication request from the server, {@link #authRequestingServer} is set instead.
     */
    public static class Outcome {
        /**
         * The URI for the server that requested authentication when the latest finalized form was
         * attempted to be sent. This URI may not match the server specified in the app settings or
         * the blank form because there could have been a redirect. It is included in the Outcome so
         * that it can be shown to the user so s/he will know where the auth request came from.
         * <p>
         * When this field is set, the overall submission attempt is halted so that the user can be
         * asked for credentials. Once credentials are provided, the submission attempt resumes.
         */
        public Uri authRequestingServer;


        /**
         * Map of database IDs for finalized forms to the user-facing status message for the latest
         * submission attempt. Currently this can be either a localized message in the case of a
         * common status or an English message in the case of a rare status that is needed for
         * developer troubleshooting.
         * <p>
         * The keys in the map are also used to identify filled forms that were part of the ongoing
         * submission attempt and don't need to be retried in the case of an authentication request.
         * See {@link #authRequestingServer}.
         * <p>
         * TODO: Consider mapping to something machine-readable like a message ID or status ID
         * instead of a mix of localized and non-localized user-facing strings.
         */
        public HashMap<String, String> messagesByInstanceId = new HashMap<>();
    }
}
