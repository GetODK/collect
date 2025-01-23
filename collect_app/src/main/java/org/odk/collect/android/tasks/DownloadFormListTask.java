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

import android.net.Uri;
import android.os.AsyncTask;

import androidx.core.util.Pair;

import org.odk.collect.android.application.Collect;
import org.odk.collect.android.formmanagement.FormsDataService;
import org.odk.collect.android.formmanagement.ServerFormDetails;
import org.odk.collect.android.formmanagement.ServerFormsDetailsFetcher;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.listeners.FormListDownloaderListener;
import org.odk.collect.android.projects.ProjectsDataService;
import org.odk.collect.android.utilities.WebCredentialsUtils;
import org.odk.collect.forms.FormSourceException;

import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;

/**
 * Background task for downloading forms from urls or a formlist from a url. We overload this task
 * a bit so that we don't have to keep track of two separate downloading tasks and it simplifies
 * interfaces. If LIST_URL is passed to doInBackground(), we fetch a form list. If a hashmap
 * containing form/url pairs is passed, we download those forms.
 *
 * @author carlhartung
 *
 * @deprecated Server form list should be retrieved from {@link FormsDataService}
 */
@Deprecated
public class DownloadFormListTask extends AsyncTask<Void, String, Pair<List<ServerFormDetails>, FormSourceException>> {

    private final ServerFormsDetailsFetcher serverFormsDetailsFetcher;

    private FormListDownloaderListener stateListener;
    private WebCredentialsUtils webCredentialsUtils;
    private String url;
    private String username;
    private String password;

    @Inject
    FormsDataService formsDataService;

    @Inject
    ProjectsDataService projectsDataService;

    public DownloadFormListTask(ServerFormsDetailsFetcher serverFormsDetailsFetcher) {
        this.serverFormsDetailsFetcher = serverFormsDetailsFetcher;
        DaggerUtils.getComponent(Collect.getInstance()).inject(this);
    }

    @Override
    protected Pair<List<ServerFormDetails>, FormSourceException> doInBackground(Void... values) {
        formsDataService.refresh(projectsDataService.requireCurrentProject().getUuid());

        if (webCredentialsUtils != null) {
            setTemporaryCredentials();
        }

        List<ServerFormDetails> formList = null;
        FormSourceException exception = null;

        try {
            formList = serverFormsDetailsFetcher.fetchFormDetails();
        } catch (FormSourceException e) {
            exception = e;
        } finally {
            if (webCredentialsUtils != null) {
                clearTemporaryCredentials();
            }
        }

        return new Pair<>(formList, exception);
    }

    @Override
    protected void onPostExecute(Pair<List<ServerFormDetails>, FormSourceException> result) {
        synchronized (this) {
            if (stateListener != null) {
                if (result.first != null) {
                    HashMap<String, ServerFormDetails> detailsHashMap = new HashMap<>();
                    for (ServerFormDetails details : result.first) {
                        detailsHashMap.put(details.getFormId(), details);
                    }

                    stateListener.formListDownloadingComplete(detailsHashMap, result.second);
                } else {
                    stateListener.formListDownloadingComplete(null, result.second);
                }
            }
        }
    }

    public void setDownloaderListener(FormListDownloaderListener sl) {
        synchronized (this) {
            stateListener = sl;
        }
    }

    public void setAlternateCredentials(WebCredentialsUtils webCredentialsUtils, String url, String username, String password) {
        this.webCredentialsUtils = webCredentialsUtils;
        serverFormsDetailsFetcher.updateCredentials(webCredentialsUtils);

        this.url = url;
        if (url != null && !url.isEmpty()) {
            serverFormsDetailsFetcher.updateUrl(url);
        }

        this.username = username;
        this.password = password;
    }

    private void setTemporaryCredentials() {
        if (url != null) {
            String host = Uri.parse(url).getHost();

            if (host != null) {
                if (username != null && password != null) {
                    webCredentialsUtils.saveCredentials(url, username, password);
                } else {
                    webCredentialsUtils.clearCredentials(url);
                }
            }
        }
    }

    private void clearTemporaryCredentials() {
        if (url != null) {
            String host = Uri.parse(url).getHost();

            if (host != null) {
                webCredentialsUtils.clearCredentials(url);
            }
        }
    }
}
