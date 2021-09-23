package org.odk.collect.android.preferences.screens

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import org.odk.collect.analytics.Analytics
import org.odk.collect.android.R
import org.odk.collect.android.activities.ActivityUtils
import org.odk.collect.android.activities.MainMenuActivity
import org.odk.collect.android.activities.SplashScreenActivity
import org.odk.collect.android.analytics.AnalyticsEvents
import org.odk.collect.android.configure.qr.QRCodeTabsActivity
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.preferences.dialogs.ResetDialogPreference
import org.odk.collect.android.preferences.dialogs.ResetDialogPreferenceFragmentCompat
import org.odk.collect.android.projects.DeleteProjectResult
import org.odk.collect.android.projects.ProjectDeleter
import org.odk.collect.android.utilities.MultiClickGuard
import org.odk.collect.androidshared.utils.ToastUtils
import javax.inject.Inject

class ProjectManagementPreferencesFragment :
    BaseAdminPreferencesFragment(),
    Preference.OnPreferenceClickListener {

    @Inject
    lateinit var projectDeleter: ProjectDeleter

    override fun onAttach(context: Context) {
        super.onAttach(context)
        DaggerUtils.getComponent(context).inject(this)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.project_management_preferences, rootKey)

        findPreference<Preference>(IMPORT_SETTINGS_KEY)!!.onPreferenceClickListener = this
        findPreference<Preference>(DELETE_PROJECT_KEY)!!.onPreferenceClickListener = this
    }

    override fun onDisplayPreferenceDialog(preference: Preference) {
        if (MultiClickGuard.allowClick(javaClass.name)) {
            var resetDialogPreference: ResetDialogPreference? = null
            if (preference is ResetDialogPreference) {
                resetDialogPreference = preference
            }
            if (resetDialogPreference != null) {
                val dialogFragment = ResetDialogPreferenceFragmentCompat.newInstance(preference.key)
                dialogFragment.setTargetFragment(this, 0)
                dialogFragment.show(parentFragmentManager, null)
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        if (MultiClickGuard.allowClick(javaClass.name)) {
            when (preference.key) {
                IMPORT_SETTINGS_KEY -> {
                    val pref = Intent(activity, QRCodeTabsActivity::class.java)
                    startActivity(pref)
                }
                DELETE_PROJECT_KEY -> AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.delete_project)
                    .setMessage(R.string.delete_project_confirm_message)
                    .setNegativeButton(R.string.delete_project_no) { _: DialogInterface?, _: Int -> }
                    .setPositiveButton(R.string.delete_project_yes) { _: DialogInterface?, _: Int -> deleteProject() }
                    .show()
            }
            return true
        }
        return false
    }

    fun deleteProject() {
        Analytics.log(AnalyticsEvents.DELETE_PROJECT)

        when (val deleteProjectResult = projectDeleter.deleteCurrentProject()) {
            is DeleteProjectResult.UnsentInstances -> {
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.cannot_delete_project_title)
                    .setMessage(R.string.cannot_delete_project_message_one)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
            is DeleteProjectResult.RunningBackgroundJobs -> {
                AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.cannot_delete_project_title)
                    .setMessage(R.string.cannot_delete_project_message_two)
                    .setPositiveButton(R.string.ok, null)
                    .show()
            }
            is DeleteProjectResult.DeletedSuccessfully -> {
                val newCurrentProject = deleteProjectResult.newCurrentProject
                if (newCurrentProject != null) {
                    ActivityUtils.startActivityAndCloseAllOthers(
                        requireActivity(),
                        MainMenuActivity::class.java
                    )
                    ToastUtils.showLongToast(
                        requireContext(),
                        getString(
                            R.string.switched_project,
                            newCurrentProject.name
                        )
                    )
                } else {
                    ActivityUtils.startActivityAndCloseAllOthers(
                        requireActivity(),
                        SplashScreenActivity::class.java
                    )
                }
            }
        }
    }

    companion object {
        const val IMPORT_SETTINGS_KEY = "import_settings"
        const val DELETE_PROJECT_KEY = "delete_project"
    }
}
