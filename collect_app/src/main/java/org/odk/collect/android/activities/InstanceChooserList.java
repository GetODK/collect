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

import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_DATE_ASC;
import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_DATE_DESC;
import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_NAME_ASC;
import static org.odk.collect.android.utilities.ApplicationConstants.SortingOrder.BY_NAME_DESC;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.odk.collect.android.R;
import org.odk.collect.android.adapters.InstanceListCursorAdapter;
import org.odk.collect.android.dao.CursorLoaderFactory;
import org.odk.collect.db.sqlite.DatabaseConnection;
import org.odk.collect.android.database.instances.DatabaseInstanceColumns;
import org.odk.collect.android.entities.EntitiesRepositoryProvider;
import org.odk.collect.android.external.FormUriActivity;
import org.odk.collect.android.external.InstancesContract;
import org.odk.collect.android.formlists.sorting.FormListSortingOption;
import org.odk.collect.android.formmanagement.drafts.BulkFinalizationViewModel;
import org.odk.collect.android.formmanagement.drafts.DraftsMenuProvider;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.instancemanagement.FinalizeAllSnackbarPresenter;
import org.odk.collect.android.instancemanagement.InstancesDataService;
import org.odk.collect.android.projects.ProjectsDataService;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.FormsRepositoryProvider;
import org.odk.collect.android.utilities.InstancesRepositoryProvider;
import org.odk.collect.androidshared.ui.multiclicksafe.MultiClickGuard;
import org.odk.collect.async.Scheduler;
import org.odk.collect.lists.EmptyListView;
import org.odk.collect.material.MaterialProgressDialogFragment;
import org.odk.collect.settings.SettingsProvider;
import org.odk.collect.settings.keys.MetaKeys;
import org.odk.collect.strings.R.string;

import java.util.Arrays;

import javax.inject.Inject;

/**
 * Responsible for displaying all the valid instances in the instance directory.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 * @author Carl Hartung (carlhartung@gmail.com)
 * @deprecated Uses {@link CursorLoaderFactory} and interacts with {@link DatabaseConnection} on the
 * UI thread.
 */
@Deprecated
public class InstanceChooserList extends AppListActivity implements AdapterView.OnItemClickListener, LoaderManager.LoaderCallbacks<Cursor> {
    private static final String INSTANCE_LIST_ACTIVITY_SORTING_ORDER = "instanceListActivitySortingOrder";
    private static final String VIEW_SENT_FORM_SORTING_ORDER = "ViewSentFormSortingOrder";

    private boolean editMode;

    @Inject
    ProjectsDataService projectsDataService;

    @Inject
    FormsRepositoryProvider formsRepositoryProvider;

    @Inject
    Scheduler scheduler;

    @Inject
    InstancesRepositoryProvider instancesRepositoryProvider;

    @Inject
    EntitiesRepositoryProvider entitiesRepositoryProvider;

    @Inject
    SettingsProvider settingsProvider;

    @Inject
    InstancesDataService instancesDataService;

    private final ActivityResultLauncher<Intent> formLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        setResult(RESULT_OK, result.getData());
        finish();
    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.form_chooser_list);
        DaggerUtils.getComponent(this).inject(this);

        String formMode = getIntent().getStringExtra(ApplicationConstants.BundleKeys.FORM_MODE);
        if (formMode == null || ApplicationConstants.FormModes.EDIT_SAVED.equalsIgnoreCase(formMode)) {
            setTitle(getString(org.odk.collect.strings.R.string.review_data));
            editMode = true;

            if (!settingsProvider.getMetaSettings().getBoolean(MetaKeys.DRAFTS_PILLS_EDUCATION_SHOWN)) {
                new MaterialAlertDialogBuilder(this)
                        .setTitle(string.new_feature)
                        .setMessage(string.drafts_pills_education_message)
                        .setPositiveButton(string.ok, null)
                        .show();

                settingsProvider.getMetaSettings().save(MetaKeys.DRAFTS_PILLS_EDUCATION_SHOWN, true);
            }
        } else {
            setTitle(getString(org.odk.collect.strings.R.string.view_sent_forms));
            EmptyListView emptyListView = findViewById(android.R.id.empty);
            emptyListView.setIcon(R.drawable.ic_baseline_inbox_72);
            emptyListView.setTitle(getString(org.odk.collect.strings.R.string.empty_list_of_sent_forms_title));
            emptyListView.setSubtitle(getString(org.odk.collect.strings.R.string.empty_list_of_sent_forms_subtitle));
        }

        sortingOptions = Arrays.asList(
                new FormListSortingOption(
                        R.drawable.ic_sort_by_alpha,
                        org.odk.collect.strings.R.string.sort_by_name_asc
                ),
                new FormListSortingOption(
                        R.drawable.ic_sort_by_alpha,
                        org.odk.collect.strings.R.string.sort_by_name_desc
                ),
                new FormListSortingOption(
                        R.drawable.ic_access_time,
                        org.odk.collect.strings.R.string.sort_by_date_desc
                ),
                new FormListSortingOption(
                        R.drawable.ic_access_time,
                        org.odk.collect.strings.R.string.sort_by_date_asc
                )
        );

        init();

        BulkFinalizationViewModel bulkFinalizationViewModel = new BulkFinalizationViewModel(
                projectsDataService.requireCurrentProject().getUuid(),
                scheduler,
                instancesDataService,
                settingsProvider
        );

        MaterialProgressDialogFragment.showOn(this, bulkFinalizationViewModel.isFinalizing(), getSupportFragmentManager(), () -> {
            MaterialProgressDialogFragment dialog = new MaterialProgressDialogFragment();
            dialog.setMessage("Finalizing drafts...");
            return dialog;
        });

        if (bulkFinalizationViewModel.isEnabled() && editMode) {
            DraftsMenuProvider draftsMenuProvider = new DraftsMenuProvider(this, bulkFinalizationViewModel::finalizeAllDrafts);
            addMenuProvider(draftsMenuProvider, this);
            bulkFinalizationViewModel.getDraftsCount().observe(this, draftsCount -> {
                draftsMenuProvider.setDraftsCount(draftsCount);
                invalidateMenu();
            });

            bulkFinalizationViewModel.getFinalizedForms().observe(
                    this,
                    new FinalizeAllSnackbarPresenter(this.findViewById(android.R.id.content), this)
            );
        }
    }

    private void init() {
        setupAdapter();
        getSupportLoaderManager().initLoader(LOADER_ID, null, this);
    }

    /**
     * Stores the path of selected instance in the parent class and finishes.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if (MultiClickGuard.allowClick(getClass().getName())) {
            if (view.isEnabled()) {
                Cursor c = (Cursor) listView.getAdapter().getItem(position);
                long instanceId = c.getLong(c.getColumnIndex(DatabaseInstanceColumns._ID));
                Uri instanceUri = InstancesContract.getUri(projectsDataService.requireCurrentProject().getUuid(), instanceId);

                String action = getIntent().getAction();
                if (Intent.ACTION_PICK.equals(action)) {
                    // caller is waiting on a picked form
                    setResult(RESULT_OK, new Intent().setData(instanceUri));
                    finish();
                } else {
                    // caller wants to view/edit a form, so launch FormFillingActivity
                    Intent parentIntent = this.getIntent();
                    Intent intent = new Intent(this, FormUriActivity.class);
                    intent.setAction(Intent.ACTION_EDIT);
                    intent.setData(instanceUri);
                    String formMode = parentIntent.getStringExtra(ApplicationConstants.BundleKeys.FORM_MODE);
                    if (formMode == null || ApplicationConstants.FormModes.EDIT_SAVED.equalsIgnoreCase(formMode)) {
                        intent.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.EDIT_SAVED);
                        formLauncher.launch(intent);
                    } else {
                        intent.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.VIEW_SENT);
                        startActivity(intent);
                        finish();
                    }
                }
            } else {
                TextView disabledCause = view.findViewById(R.id.form_subtitle2);
                Toast.makeText(this, disabledCause.getText(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void setupAdapter() {
        String[] data = {DatabaseInstanceColumns.DISPLAY_NAME, DatabaseInstanceColumns.DELETED_DATE};
        int[] view = {R.id.form_title, R.id.form_subtitle2};

        boolean shouldCheckDisabled = !editMode;
        listAdapter = new InstanceListCursorAdapter(
                this, R.layout.form_chooser_list_item, null, data, view, shouldCheckDisabled);
        listView.setAdapter(listAdapter);
    }

    @Override
    protected String getSortingOrderKey() {
        return editMode ? INSTANCE_LIST_ACTIVITY_SORTING_ORDER : VIEW_SENT_FORM_SORTING_ORDER;
    }

    @Override
    protected void updateAdapter() {
        getSupportLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        showProgressBar();
        if (editMode) {
            return new CursorLoaderFactory(projectsDataService).createEditableInstancesCursorLoader(getFilterText(), getSortingOrder());
        } else {
            return new CursorLoaderFactory(projectsDataService).createSentInstancesCursorLoader(getFilterText(), getSortingOrder());
        }
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        hideProgressBarAndAllow();
        listAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(@NonNull Loader loader) {
        listAdapter.swapCursor(null);
    }

    protected String getSortingOrder() {
        String sortingOrder = DatabaseInstanceColumns.DISPLAY_NAME + " COLLATE NOCASE ASC, " + DatabaseInstanceColumns.STATUS + " DESC";
        switch (getSelectedSortingOrder()) {
            case BY_NAME_ASC:
                sortingOrder = DatabaseInstanceColumns.DISPLAY_NAME + " COLLATE NOCASE ASC, " + DatabaseInstanceColumns.STATUS + " DESC";
                break;
            case BY_NAME_DESC:
                sortingOrder = DatabaseInstanceColumns.DISPLAY_NAME + " COLLATE NOCASE DESC, " + DatabaseInstanceColumns.STATUS + " DESC";
                break;
            case BY_DATE_ASC:
                sortingOrder = DatabaseInstanceColumns.LAST_STATUS_CHANGE_DATE + " ASC";
                break;
            case BY_DATE_DESC:
                sortingOrder = DatabaseInstanceColumns.LAST_STATUS_CHANGE_DATE + " DESC";
                break;
        }
        return sortingOrder;
    }
}
