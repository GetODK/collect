/*
 * Copyright (C) 2017 Nafundi
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

/**
 * Activity to upload completed forms to gme.
 *
 * @author Carl Hartung (chartung@nafundi.com)
 */

package org.odk.collect.android.gdrive;

import static org.odk.collect.android.gdrive.GoogleSheetsUploaderProgressDialog.GOOGLE_SHEETS_UPLOADER_PROGRESS_DIALOG_TAG;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.UserRecoverableAuthException;

import org.odk.collect.analytics.Analytics;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.CollectAbstractActivity;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.listeners.PermissionListener;
import org.odk.collect.android.network.NetworkStateProvider;
import org.odk.collect.android.preferences.keys.ProjectKeys;
import org.odk.collect.android.utilities.ArrayUtils;
import org.odk.collect.android.utilities.DialogUtils;
import org.odk.collect.android.utilities.FormsRepositoryProvider;
import org.odk.collect.android.utilities.InstanceUploaderUtils;
import org.odk.collect.android.utilities.InstancesRepositoryProvider;
import org.odk.collect.androidshared.utils.ToastUtils;

import java.io.IOException;
import java.util.HashMap;

import javax.inject.Inject;

import timber.log.Timber;

public class GoogleSheetsUploaderActivity extends CollectAbstractActivity implements InstanceUploaderListener, GoogleSheetsUploaderProgressDialog.OnSendingFormsCanceledListener {

    private static final int REQUEST_AUTHORIZATION = 1001;
    private static final int GOOGLE_USER_DIALOG = 3;
    private static final String ALERT_MSG = "alertmsg";
    private static final String ALERT_SHOWING = "alertshowing";
    private AlertDialog alertDialog;
    private String alertMsg;
    private boolean alertShowing;
    private Long[] instancesToSend;
    private InstanceGoogleSheetsUploaderTask instanceGoogleSheetsUploaderTask;

    @Inject
    GoogleAccountsManager accountsManager;

    @Inject
    GoogleApiProvider googleApiProvider;

    @Inject
    NetworkStateProvider connectivityProvider;

    @Inject
    Analytics analytics;

    @Inject
    InstancesRepositoryProvider instancesRepositoryProvider;

    @Inject
    FormsRepositoryProvider formsRepositoryProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.i("onCreate: %s", savedInstanceState == null ? "creating" : "re-initializing");

        DaggerUtils.getComponent(this).inject(this);

        // if we start this activity, the following must be true:
        // 1) Google Sheets is selected in preferences
        // 2) A google user is selected

        alertMsg = getString(R.string.please_wait);

        setTitle(getString(R.string.send_data));

        // get any simple saved state...
        // resets alert message and showing dialog if the screen is rotated
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(ALERT_MSG)) {
                alertMsg = savedInstanceState.getString(ALERT_MSG);
            }
            if (savedInstanceState.containsKey(ALERT_SHOWING)) {
                alertShowing = savedInstanceState.getBoolean(ALERT_SHOWING, false);
            }
        }

        long[] selectedInstanceIDs;

        Intent intent = getIntent();
        selectedInstanceIDs = intent.getLongArrayExtra(FormEntryActivity.KEY_INSTANCES);

        instancesToSend = ArrayUtils.toObject(selectedInstanceIDs);

        // at this point, we don't expect this to be empty...
        if (instancesToSend.length == 0) {
            Timber.e("onCreate: No instances to upload!");
            // drop through --
            // everything will process through OK
        } else {
            Timber.i("onCreate: Beginning upload of %d instances!", instancesToSend.length);
        }

        getResultsFromApi();
    }

    private void runTask() {
        instanceGoogleSheetsUploaderTask = new InstanceGoogleSheetsUploaderTask(googleApiProvider, analytics);
        instanceGoogleSheetsUploaderTask.setRepositories(instancesRepositoryProvider.get(), formsRepositoryProvider.get(), settingsProvider);

        // ensure we have a google account selected
        String googleUsername = settingsProvider.getGeneralSettings().getString(ProjectKeys.KEY_SELECTED_GOOGLE_ACCOUNT);
        if (googleUsername == null || googleUsername.equals("")) {
            showDialog(GOOGLE_USER_DIALOG);
        } else {
            new AuthorizationChecker().execute();
        }
    }

    /*
     * Attempt to call the API, after verifying that all the preconditions are
     * satisfied. The preconditions are: Google Play Services installed, an
     * account was selected and the device currently has online access. If any
     * of the preconditions are not satisfied, the app will prompt the user as
     * appropriate.
     */
    private void getResultsFromApi() {
        if (!accountsManager.isAccountSelected()) {
            selectAccount();
        } else if (!connectivityProvider.isDeviceOnline()) {
            ToastUtils.showShortToast(this, "No network connection available.");
        } else {
            runTask();
        }
    }

    private void selectAccount() {
        permissionsProvider.requestGetAccountsPermission(this, new PermissionListener() {
            @Override
            public void granted() {
                String account = accountsManager.getLastSelectedAccountIfValid();
                if (!account.isEmpty()) {
                    accountsManager.selectAccount(account);

                    // re-attempt to list google drive files
                    getResultsFromApi();
                } else {
                    GoogleAccountNotSetDialog.show(GoogleSheetsUploaderActivity.this);
                }
            }

            @Override
            public void denied() {
                finish();
            }
        });
    }

    /**
     * Called when an activity launched here (specifically, AccountPicker
     * and authorization) exits, giving you the requestCode you started it with,
     * the resultCode it returned, and any additional data from it.
     *
     * @param requestCode code indicating which activity result is incoming.
     * @param resultCode  code indicating the result of the incoming
     *                    activity result.
     * @param data        Intent (containing result data) returned by incoming
     *                    activity result.
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_CANCELED) {
            Timber.d("AUTHORIZE_DRIVE_ACCESS failed, asking to choose new account:");
            finish();
        }

        if (requestCode == REQUEST_AUTHORIZATION) {
            dismissProgressDialog();
            if (resultCode == RESULT_OK) {
                getResultsFromApi();
            }
        }
    }

    @Override
    protected void onResume() {
        if (instanceGoogleSheetsUploaderTask != null) {
            instanceGoogleSheetsUploaderTask.setUploaderListener(this);
        }
        if (alertShowing) {
            createAlertDialog(alertMsg);
        }
        super.onResume();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ALERT_MSG, alertMsg);
        outState.putBoolean(ALERT_SHOWING, alertShowing);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }

    @Override
    protected void onDestroy() {
        if (instanceGoogleSheetsUploaderTask != null) {
            if (!instanceGoogleSheetsUploaderTask.isCancelled()) {
                instanceGoogleSheetsUploaderTask.cancel(true);
            }
            instanceGoogleSheetsUploaderTask.setUploaderListener(null);
        }
        finish();
        super.onDestroy();
    }

    @Override
    public void uploadingComplete(HashMap<String, String> result) {
        try {
            dismissProgressDialog();
        } catch (Exception e) {
            Timber.w(e);
        }

        if (result == null) {
            // probably got an auth request, so ignore
            return;
        }
        Timber.i("uploadingComplete: Processing results ( %d ) from upload of %d instances!",
                result.size(), instancesToSend.length);

        createAlertDialog(InstanceUploaderUtils.getUploadResultMessage(instancesRepositoryProvider.get(), this, result));
    }

    @Override
    public void progressUpdate(int progress, int total) {
        alertMsg = getString(R.string.sending_items, String.valueOf(progress), String.valueOf(total));
        GoogleSheetsUploaderProgressDialog progressDialog = getProgressDialog();
        if (progressDialog != null) {
            progressDialog.setMessage(alertMsg);
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == GOOGLE_USER_DIALOG) {
            AlertDialog.Builder gudBuilder = new AlertDialog.Builder(this);

            gudBuilder.setTitle(getString(R.string.no_google_account));
            gudBuilder.setMessage(getString(R.string.google_set_account));
            gudBuilder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            gudBuilder.setCancelable(false);
            return gudBuilder.create();
        }
        return null;
    }

    private void createAlertDialog(String message) {
        alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(getString(R.string.upload_results));
        alertDialog.setMessage(message);
        DialogInterface.OnClickListener quitListener = (dialog, i) -> {
            if (i == DialogInterface.BUTTON1) { // ok
                // always exit this activity since it has no interface
                alertShowing = false;
                finish();
            }
        };
        alertDialog.setCancelable(false);
        alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.ok), quitListener);
        alertShowing = true;
        alertMsg = message;
        DialogUtils.showDialog(alertDialog, this);
    }

    @Override
    public void authRequest(Uri url, HashMap<String, String> doneSoFar) {
        // in interface, but not needed
    }

    private void authorized() {
        if (instanceGoogleSheetsUploaderTask.getStatus() == AsyncTask.Status.PENDING) {
            GoogleSheetsUploaderProgressDialog.newInstance(alertMsg)
                    .show(getSupportFragmentManager(), GOOGLE_SHEETS_UPLOADER_PROGRESS_DIALOG_TAG);

            instanceGoogleSheetsUploaderTask.setUploaderListener(this);
            instanceGoogleSheetsUploaderTask.execute(instancesToSend);
        }
    }

    private void dismissProgressDialog() {
        GoogleSheetsUploaderProgressDialog progressDialog = getProgressDialog();
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    private GoogleSheetsUploaderProgressDialog getProgressDialog() {
        return (GoogleSheetsUploaderProgressDialog) getSupportFragmentManager().findFragmentByTag(GOOGLE_SHEETS_UPLOADER_PROGRESS_DIALOG_TAG);
    }

    @Override
    public void onSendingFormsCanceled() {
        instanceGoogleSheetsUploaderTask.cancel(true);
        instanceGoogleSheetsUploaderTask.setUploaderListener(null);
        finish();
    }

    private class AuthorizationChecker extends AsyncTask<Void, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Void... params) {
            try {
                // Must be run from a background thread, not the main UI thread.
                if (accountsManager.getToken() != null) {
                    return true;
                }
            } catch (UserRecoverableAuthException e) {
                // Collect is not yet authorized to access current account, so request for authorization
                runOnUiThread(() -> startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION));
            } catch (IOException | GoogleAuthException e) {
                // authorization failed
                runOnUiThread(() -> createAlertDialog(getString(R.string.google_auth_io_exception_msg)));
            }

            return false;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result) {
                authorized();
            }
        }
    }
}
