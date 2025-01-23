package org.odk.collect.draw

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.odk.collect.draw.databinding.QuitDrawingDialogLayoutBinding

object QuitDrawingDialog {

    @JvmStatic
    fun show(
        activity: Activity,
        onDiscardChanges: Runnable?,
        onSaveChanges: Runnable?
    ): AlertDialog {
        return create(
            activity,
            onDiscardChanges,
            onSaveChanges
        ).also {
            it.show()
        }
    }

    private fun create(
        activity: Activity,
        onDiscardChanges: Runnable?,
        onSaveChanges: Runnable?
    ): AlertDialog {
        val binding = QuitDrawingDialogLayoutBinding.inflate(activity.layoutInflater)
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(org.odk.collect.strings.R.string.save_drawing_title)
            .setView(binding.root)
            .create()

        binding.discardChanges.setOnClickListener {
            onDiscardChanges?.run()
        }

        binding.keepEditing.setOnClickListener {
            dialog.dismiss()
        }

        binding.saveChanges.setOnClickListener {
            onSaveChanges?.run()
        }

        return dialog
    }
}
