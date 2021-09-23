package org.odk.collect.android.preferences.dialogs

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.widget.CompoundButton
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import org.odk.collect.android.R
import org.odk.collect.android.databinding.PasswordDialogLayoutBinding
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.preferences.ProjectPreferencesViewModel
import org.odk.collect.android.preferences.keys.ProtectedProjectKeys
import org.odk.collect.android.preferences.source.SettingsProvider
import org.odk.collect.android.utilities.SoftKeyboardController
import org.odk.collect.androidshared.utils.ToastUtils
import javax.inject.Inject

class ChangeAdminPasswordDialog : DialogFragment() {
    @Inject
    lateinit var factory: ProjectPreferencesViewModel.Factory

    @Inject
    lateinit var settingsProvider: SettingsProvider

    @Inject
    lateinit var softKeyboardController: SoftKeyboardController

    lateinit var binding: PasswordDialogLayoutBinding

    lateinit var projectPreferencesViewModel: ProjectPreferencesViewModel

    override fun onAttach(context: Context) {
        super.onAttach(context)
        DaggerUtils.getComponent(context).inject(this)
        projectPreferencesViewModel = ViewModelProvider(requireActivity(), factory)[ProjectPreferencesViewModel::class.java]
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = PasswordDialogLayoutBinding.inflate(LayoutInflater.from(context))

        binding.pwdField.post {
            softKeyboardController.showSoftKeyboard(binding.pwdField)
        }
        binding.checkBox2.setOnCheckedChangeListener { _: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                binding.pwdField.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                binding.pwdField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }

        return AlertDialog.Builder(requireContext())
            .setView(binding.root)
            .setTitle(R.string.change_admin_password)
            .setView(binding.root)
            .setPositiveButton(getString(R.string.ok)) { _: DialogInterface?, _: Int ->
                val password = binding.pwdField.text.toString()

                settingsProvider.getAdminSettings().save(ProtectedProjectKeys.KEY_ADMIN_PW, password)

                if (password.isEmpty()) {
                    projectPreferencesViewModel.setStateNotProtected()
                    ToastUtils.showShortToast(
                        requireContext(),
                        R.string.admin_password_disabled
                    )
                } else {
                    projectPreferencesViewModel.setStateUnlocked()
                    ToastUtils.showShortToast(
                        requireContext(),
                        R.string.admin_password_changed
                    )
                }
                dismiss()
            }
            .setNegativeButton(getString(R.string.cancel)) { _: DialogInterface?, _: Int -> dismiss() }
            .setCancelable(false)
            .create()
    }
}
