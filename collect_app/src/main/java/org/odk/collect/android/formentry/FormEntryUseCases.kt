package org.odk.collect.android.formentry

import org.apache.commons.io.FileUtils.readFileToByteArray
import org.javarosa.core.model.FormDef
import org.javarosa.core.model.instance.InstanceInitializationFactory
import org.javarosa.core.model.instance.TreeReference
import org.javarosa.core.model.instance.utils.DefaultAnswerResolver
import org.javarosa.core.reference.ReferenceManager
import org.javarosa.form.api.FormEntryController
import org.javarosa.xform.parse.XFormParser
import org.javarosa.xform.util.XFormUtils
import org.odk.collect.android.externaldata.ExternalAnswerResolver
import org.odk.collect.android.javarosawrapper.FailedValidationResult
import org.odk.collect.android.javarosawrapper.FormController
import org.odk.collect.android.javarosawrapper.JavaRosaFormController
import org.odk.collect.android.utilities.FileUtils
import org.odk.collect.android.utilities.FormDefCache
import org.odk.collect.android.utilities.FormUtils
import org.odk.collect.entities.EntitiesRepository
import org.odk.collect.forms.Form
import org.odk.collect.forms.FormsRepository
import org.odk.collect.forms.instances.Instance
import org.odk.collect.forms.instances.InstancesRepository
import java.io.File

object FormEntryUseCases {

    fun loadFormDef(
        instance: Instance,
        formsRepository: FormsRepository,
        projectRootDir: File
    ): LoadedFormDef {
        val form = formsRepository.getAllByFormId(instance.formId)[0]
        return loadFormDef(form, projectRootDir)
    }

    fun loadFormDef(
        form: Form,
        projectRootDir: File
    ): LoadedFormDef {
        val xForm = File(form.formFilePath)
        val formMediaDir = FileUtils.getFormMediaDir(xForm)

        FormUtils.setupReferenceManagerForForm(
            ReferenceManager.instance(),
            projectRootDir,
            formMediaDir
        )

        val formDef = createFormDefFromCacheOrXml(xForm)!!
        return LoadedFormDef(formDef, formMediaDir)
    }

    fun loadBlankForm(
        formEntryController: FormEntryController,
        formMediaDir: File,
        instanceFile: File
    ): JavaRosaFormController {
        val instanceInit = InstanceInitializationFactory()
        formEntryController.model.form.initialize(true, instanceInit)

        return JavaRosaFormController(
            formMediaDir,
            formEntryController,
            instanceFile
        )
    }

    @JvmStatic
    fun loadDraft(
        formEntryController: FormEntryController,
        formMediaDir: File,
        instance: File
    ): FormController {
        val instanceInit = InstanceInitializationFactory()

        importInstance(instance, formEntryController)
        formEntryController.model.form.initialize(false, instanceInit)

        return JavaRosaFormController(
            formMediaDir,
            formEntryController,
            instance
        )
    }

    fun getSavePoint(formController: FormController, cacheDir: File): File? {
        val instanceXml = formController.getInstanceFile()!!
        val savepointFile = File(cacheDir, "${instanceXml.name}.save")

        return if (savepointFile.exists() && savepointFile.lastModified() > instanceXml.lastModified()) {
            savepointFile
        } else {
            null
        }
    }

    fun saveDraft(
        formController: JavaRosaFormController,
        instancesRepository: InstancesRepository,
        instanceFile: File
    ): Instance {
        saveFormToDisk(formController)
        return instancesRepository.save(
            Instance.Builder()
                .instanceFilePath(instanceFile.absolutePath)
                .status(Instance.STATUS_INCOMPLETE)
                .build()
        )
    }

    @JvmStatic
    fun finalizeDraft(
        formController: FormController,
        instancesRepository: InstancesRepository,
        entitiesRepository: EntitiesRepository
    ): Instance? {
        val instance =
            getInstanceFromFormController(formController, instancesRepository)!!

        val valid = finalizeInstance(formController, entitiesRepository)

        return if (valid) {
            saveFormToDisk(formController)
            val instanceName = formController.getSubmissionMetadata()?.instanceName

            instancesRepository.save(
                Instance.Builder(instance)
                    .status(Instance.STATUS_COMPLETE)
                    .canEditWhenComplete(formController.isSubmissionEntireForm())
                    .displayName(instanceName ?: instance.displayName)
                    .build()
            )
        } else {
            instancesRepository.save(
                Instance.Builder(instance)
                    .status(Instance.STATUS_INVALID)
                    .build()
            )

            null
        }
    }

    private fun getInstanceFromFormController(
        formController: FormController,
        instancesRepository: InstancesRepository
    ): Instance? {
        val instancePath = formController.getInstanceFile()!!.absolutePath
        return instancesRepository.getOneByPath(instancePath)
    }

    private fun saveFormToDisk(formController: FormController) {
        val payload = formController.getSubmissionXml()
        FileUtils.write(formController.getInstanceFile(), payload!!.payloadBytes)
    }

    @JvmStatic
    private fun finalizeInstance(
        formController: FormController,
        entitiesRepository: EntitiesRepository
    ): Boolean {
        val validationResult = formController.validateAnswers(true)
        if (validationResult is FailedValidationResult) {
            return false
        }

        formController.finalizeForm()
        formController.getEntities().forEach { entity -> entitiesRepository.save(entity) }
        return true
    }

    private fun createFormDefFromCacheOrXml(xForm: File): FormDef? {
        val formDefFromCache = FormDefCache.readCache(xForm)
        if (formDefFromCache != null) {
            return formDefFromCache
        }

        val lastSavedSrc = FileUtils.getOrCreateLastSavedSrc(xForm)
        return XFormUtils.getFormFromFormXml(xForm.absolutePath, lastSavedSrc)?.also {
            FormDefCache.writeCache(it, xForm.path)
        }
    }

    private fun importInstance(instanceFile: File, formEntryController: FormEntryController) {
        // convert files into a byte array
        val fileBytes = readFileToByteArray(instanceFile)

        // get the root of the saved and template instances
        val savedRoot = XFormParser.restoreDataModel(fileBytes, null).root
        val templateRoot = formEntryController.model.form.instance.root.deepCopy(true)

        // weak check for matching forms
        if (savedRoot.name != templateRoot.name || savedRoot.mult != 0) {
            return
        }

        // populate the data model
        val tr = TreeReference.rootRef()
        tr.add(templateRoot.name, TreeReference.INDEX_UNBOUND)

        // Here we set the Collect's implementation of the IAnswerResolver.
        // We set it back to the default after select choices have been populated.
        XFormParser.setAnswerResolver(ExternalAnswerResolver())
        templateRoot.populate(savedRoot, formEntryController.model.form)
        XFormParser.setAnswerResolver(DefaultAnswerResolver())

        // FormInstanceParser.parseInstance is responsible for initial creation of instances. It explicitly sets the
        // main instance name to null so we force this again on deserialization because some code paths rely on the main
        // instance not having a name. Must be before the call on setRoot because setRoot also sets the root's name.
        formEntryController.model.form.instance.name = null

        // populated model to current form
        formEntryController.model.form.instance.root = templateRoot

        // fix any language issues
        // :
        // http://bitbucket.org/javarosa/main/issue/5/itext-n-appearing-in-restored-instances
        if (formEntryController.model.languages != null) {
            formEntryController.model.form
                .localeChanged(
                    formEntryController.model.language,
                    formEntryController.model.form.localizer
                )
        }
    }
}

data class LoadedFormDef(val formDef: FormDef, val formMediaDir: File)
