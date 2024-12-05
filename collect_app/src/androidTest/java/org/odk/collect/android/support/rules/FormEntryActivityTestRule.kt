package org.odk.collect.android.support.rules

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.ExternalResource
import org.odk.collect.android.external.FormsContract
import org.odk.collect.android.formmanagement.FormFillingIntentFactory
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.storage.StorageSubdirectory
import org.odk.collect.android.support.CollectHelpers
import org.odk.collect.android.support.StorageUtils
import org.odk.collect.android.support.pages.FormEntryPage
import org.odk.collect.android.support.pages.FormHierarchyPage
import org.odk.collect.android.support.pages.Page
import org.odk.collect.android.support.pages.SavepointRecoveryDialogPage
import timber.log.Timber
import java.io.IOException

open class FormEntryActivityTestRule :
    ExternalResource() {

    private lateinit var intent: Intent
    private lateinit var scenario: ActivityScenario<Activity>

    override fun after() {
        try {
            scenario.close()
        } catch (e: Throwable) {
            Timber.e(Error("Error closing ActivityScenario: $e"))
        }
    }

    @JvmOverloads
    fun setUpProjectAndCopyForm(
        formFilename: String,
        mediaFilePaths: List<String>? = null
    ): FormEntryActivityTestRule {
        try {
            // Set up demo project
            CollectHelpers.addDemoProject()
            StorageUtils.copyFormToDemoProject(formFilename, mediaFilePaths, true)
        } catch (e: IOException) {
            throw RuntimeException(e)
        }

        return this
    }

    fun <D : Page<D>> fillNewForm(formFilename: String, destination: D): D {
        intent = createNewFormIntent(formFilename)
        scenario = ActivityScenario.launch(intent)
        return destination.assertOnPage()
    }

    fun fillNewForm(formFilename: String, formName: String): FormEntryPage {
        return fillNewForm(formFilename, FormEntryPage(formName))
    }

    fun fillNewFormWithSavepoint(formFilename: String): SavepointRecoveryDialogPage {
        intent = createNewFormIntent(formFilename)
        scenario = ActivityScenario.launch(intent)
        return SavepointRecoveryDialogPage().assertOnPage()
    }

    fun editForm(formFilename: String, instanceName: String): FormHierarchyPage {
        intent = createEditFormIntent(formFilename)
        scenario = ActivityScenario.launch(intent)
        return FormHierarchyPage(instanceName).assertOnPage()
    }

    fun editFormWithSavepoint(formFilename: String): SavepointRecoveryDialogPage {
        intent = createEditFormIntent(formFilename)
        scenario = ActivityScenario.launch(intent)
        return SavepointRecoveryDialogPage().assertOnPage()
    }

    fun simulateProcessRestart(): FormEntryActivityTestRule {
        CollectHelpers.simulateProcessRestart()
        return this
    }

    private fun createNewFormIntent(formFilename: String): Intent {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val formPath = DaggerUtils.getComponent(application).storagePathProvider()
            .getOdkDirPath(StorageSubdirectory.FORMS) + "/" + formFilename
        val form = DaggerUtils.getComponent(application).formsRepositoryProvider().create()
            .getOneByPath(formPath)
        val projectId = DaggerUtils.getComponent(application).currentProjectProvider()
            .requireCurrentProject().uuid

        return FormFillingIntentFactory.newInstanceIntent(
            application,
            FormsContract.getUri(projectId, form!!.dbId)
        )
    }

    private fun createEditFormIntent(formFilename: String): Intent {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val formPath = DaggerUtils.getComponent(application).storagePathProvider()
            .getOdkDirPath(StorageSubdirectory.FORMS) + "/" + formFilename
        val form = DaggerUtils.getComponent(application).formsRepositoryProvider().create()
            .getOneByPath(formPath)
        val instance = DaggerUtils.getComponent(application).instancesRepositoryProvider().create()
            .getAllByFormId(form!!.formId).first()
        val projectId = DaggerUtils.getComponent(application).currentProjectProvider()
            .requireCurrentProject().uuid

        return FormFillingIntentFactory.editInstanceIntent(
            application,
            projectId,
            instance.dbId
        )
    }
}
