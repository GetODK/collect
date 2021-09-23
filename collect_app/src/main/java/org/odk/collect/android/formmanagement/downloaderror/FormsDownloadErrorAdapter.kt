package org.odk.collect.android.formmanagement.downloaderror

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.odk.collect.android.R
import org.odk.collect.android.utilities.TranslationHandler

class FormsDownloadErrorAdapter(val failures: List<FormsDownloadErrorItem>) : RecyclerView.Adapter<FormsDownloadErrorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val formName: TextView = view.findViewById(R.id.form_name)
        val formDetails: TextView = view.findViewById(R.id.form_details)
        val errorMessage: TextView = view.findViewById(R.id.error_message)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.form_download_error, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.formName.text = failures[position].formName
        holder.formDetails.text = TranslationHandler.getString(holder.itemView.context, R.string.form_details, failures[position].formId, failures[position].formVersion)
        holder.errorMessage.text = failures[position].errorMessage
    }

    override fun getItemCount() = failures.size
}
