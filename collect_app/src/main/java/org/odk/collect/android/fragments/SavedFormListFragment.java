/*
 * Copyright (C) 2017 University of Washington
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

package org.odk.collect.android.fragments;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import org.jetbrains.annotations.NotNull;
import org.odk.collect.android.R;
import org.odk.collect.android.adapters.InstanceListCursorAdapter;
import org.odk.collect.android.dao.CursorLoaderFactory;
import org.odk.collect.android.database.instances.DatabaseInstanceColumns;
import org.odk.collect.android.injection.DaggerUtils;
import org.odk.collect.android.listeners.DeleteInstancesListener;
import org.odk.collect.android.projects.CurrentProjectProvider;
import org.odk.collect.android.tasks.DeleteInstancesTask;
import org.odk.collect.android.utilities.FormsRepositoryProvider;
import org.odk.collect.android.utilities.InstancesRepositoryProvider;
import org.odk.collect.android.views.DayNightProgressDialog;
import org.odk.collect.androidshared.utils.ToastUtils;

import javax.inject.Inject;

import timber.log.Timber;

/**
 * Responsible for displaying and deleting all the saved form instances
 * directory.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class SavedFormListFragment extends InstanceListFragment implements DeleteInstancesListener, View.OnClickListener {
    private static final String DATA_MANAGER_LIST_SORTING_ORDER = "dataManagerListSortingOrder";

    DeleteInstancesTask deleteInstancesTask;
    private AlertDialog alertDialog;
    private ProgressDialog progressDialog;

    @Inject
    InstancesRepositoryProvider instancesRepositoryProvider;

    @Inject
    FormsRepositoryProvider formsRepositoryProvider;

    @Inject
    CurrentProjectProvider currentProjectProvider;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        DaggerUtils.getComponent(context).inject(this);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View rootView, Bundle savedInstanceState) {
        deleteButton.setOnClickListener(this);
        toggleButton.setOnClickListener(this);

        setupAdapter();

        super.onViewCreated(rootView, savedInstanceState);
    }

    @Override
    public void onResume() {
        // hook up to receive completion events
        if (deleteInstancesTask != null) {
            deleteInstancesTask.setDeleteListener(this);
        }
        super.onResume();
        // async task may have completed while we were reorienting...
        if (deleteInstancesTask != null
                && deleteInstancesTask.getStatus() == AsyncTask.Status.FINISHED) {
            deleteComplete(deleteInstancesTask.getDeleteCount());
        }
    }

    @Override
    public void onPause() {
        if (deleteInstancesTask != null) {
            deleteInstancesTask.setDeleteListener(null);
        }
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
        super.onPause();
    }

    private void setupAdapter() {
        String[] data = {DatabaseInstanceColumns.DISPLAY_NAME};
        int[] view = {R.id.form_title};

        listAdapter = new InstanceListCursorAdapter(getActivity(),
                R.layout.form_chooser_list_item_multiple_choice, null, data, view, false);
        setListAdapter(listAdapter);
        checkPreviouslyCheckedItems();
    }

    @Override
    protected String getSortingOrderKey() {
        return DATA_MANAGER_LIST_SORTING_ORDER;
    }

    @Override
    protected CursorLoader getCursorLoader() {
        return new CursorLoaderFactory(currentProjectProvider).createSavedInstancesCursorLoader(getFilterText(), getSortingOrder());
    }

    /**
     * Create the instance delete dialog
     */
    private void createDeleteInstancesDialog() {
        alertDialog = new AlertDialog.Builder(getContext()).create();
        alertDialog.setTitle(getString(R.string.delete_file));
        alertDialog.setMessage(getString(R.string.delete_confirm,
                String.valueOf(getCheckedCount())));
        DialogInterface.OnClickListener dialogYesNoListener =
                (dialog, i) -> {
                    if (i == DialogInterface.BUTTON_POSITIVE) { // delete
                        deleteSelectedInstances();
                        if (getListView().getCount() == getCheckedCount()) {
                            toggleButton.setEnabled(false);
                        }
                    }
                };
        alertDialog.setCancelable(false);
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.delete_yes),
                dialogYesNoListener);
        alertDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.delete_no),
                dialogYesNoListener);
        alertDialog.show();
    }

    @Override
    public void progressUpdate(int progress, int total) {
        String message = String.format(getResources().getString(R.string.deleting_form_dialog_update_message), progress, total);
        progressDialog.setMessage(message);
    }

    /**
     * Deletes the selected files. Content provider handles removing the files
     * from the filesystem.
     */
    private void deleteSelectedInstances() {
        if (deleteInstancesTask == null) {
            progressDialog = new DayNightProgressDialog(getContext());
            progressDialog.setMessage(getResources().getString(R.string.form_delete_message));
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.show();

            deleteInstancesTask = new DeleteInstancesTask(instancesRepositoryProvider.get(), formsRepositoryProvider.get());
            deleteInstancesTask.setDeleteListener(this);
            deleteInstancesTask.execute(getCheckedIdObjects());
        } else {
            ToastUtils.showLongToast(requireContext(), R.string.file_delete_in_progress);
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long rowId) {
        super.onListItemClick(l, v, position, rowId);
    }

    @Override
    public void deleteComplete(int deletedInstances) {
        Timber.i("Delete instances complete");
        final int toDeleteCount = deleteInstancesTask.getToDeleteCount();

        if (deletedInstances == toDeleteCount) {
            // all deletes were successful
            ToastUtils.showShortToast(requireContext(), getString(R.string.file_deleted_ok, String.valueOf(deletedInstances)));
        } else {
            // had some failures
            Timber.e("Failed to delete %d instances", toDeleteCount - deletedInstances);
            ToastUtils.showLongToast(requireContext(), getString(R.string.file_deleted_error,
                    String.valueOf(toDeleteCount - deletedInstances),
                    String.valueOf(toDeleteCount)));
        }

        deleteInstancesTask = null;
        getListView().clearChoices(); // doesn't unset the checkboxes
        for (int i = 0; i < getListView().getCount(); ++i) {
            getListView().setItemChecked(i, false);
        }
        deleteButton.setEnabled(false);

        updateAdapter();
        progressDialog.dismiss();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.delete_button:
                int checkedItemCount = getCheckedCount();
                if (checkedItemCount > 0) {
                    createDeleteInstancesDialog();
                } else {
                    ToastUtils.showShortToast(requireContext(), R.string.noselect_error);
                }
                break;

            case R.id.toggle_button:
                ListView lv = getListView();
                boolean allChecked = toggleChecked(lv);
                if (allChecked) {
                    for (int i = 0; i < lv.getCount(); i++) {
                        selectedInstances.add(lv.getItemIdAtPosition(i));
                    }
                } else {
                    selectedInstances.clear();
                }
                toggleButtonLabel(toggleButton, getListView());
                deleteButton.setEnabled(allChecked);
                break;
        }
    }

    @Override
    public void onLoadFinished(@NonNull @NotNull Loader<Cursor> loader, Cursor cursor) {
        super.onLoadFinished(loader, cursor);
        hideProgressBarAndAllow();
    }
}
