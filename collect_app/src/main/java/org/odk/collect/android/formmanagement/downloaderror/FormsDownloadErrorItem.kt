package org.odk.collect.android.formmanagement.downloaderror

import java.io.Serializable

data class FormsDownloadErrorItem(
    val formName: String,
    val formId: String,
    val formVersion: String,
    val errorMessage: String
) : Serializable
