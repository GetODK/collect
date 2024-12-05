/*
 * Copyright (C) 2011 University of Washington
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
package org.odk.collect.android.activities

import android.os.Bundle
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.odk.collect.android.R
import org.odk.collect.android.external.FormsContract
import org.odk.collect.android.formmanagement.FormFillingIntentFactory
import org.odk.collect.android.formmanagement.formmap.FormMapViewModel
import org.odk.collect.android.injection.DaggerUtils
import org.odk.collect.android.projects.ProjectsDataService
import org.odk.collect.android.utilities.FormsRepositoryProvider
import org.odk.collect.android.utilities.InstancesRepositoryProvider
import org.odk.collect.androidshared.ui.FragmentFactoryBuilder
import org.odk.collect.async.Scheduler
import org.odk.collect.geo.selection.SelectionMapFragment
import org.odk.collect.settings.SettingsProvider
import org.odk.collect.strings.localization.LocalizedActivity
import javax.inject.Inject

/**
 * Show a map with points representing saved instances of the selected form.
 */
class FormMapActivity : LocalizedActivity() {

    @Inject
    lateinit var formsRepositoryProvider: FormsRepositoryProvider

    @Inject
    lateinit var instancesRepositoryProvider: InstancesRepositoryProvider

    @Inject
    lateinit var settingsProvider: SettingsProvider

    @Inject
    lateinit var projectsDataService: ProjectsDataService

    @Inject
    lateinit var scheduler: Scheduler

    private val formId by lazy { intent.getLongExtra(EXTRA_FORM_ID, -1) }
    private val viewModel: FormMapViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return FormMapViewModel(
                    resources,
                    formId,
                    formsRepositoryProvider.create(),
                    instancesRepositoryProvider.create(),
                    settingsProvider,
                    scheduler
                ) as T
            }
        }
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        DaggerUtils.getComponent(this).inject(this)
        supportFragmentManager.fragmentFactory = FragmentFactoryBuilder()
            .forClass(SelectionMapFragment::class.java) {
                SelectionMapFragment(
                    selectionMapData = viewModel
                )
            }
            .build()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.form_map_activity)

        supportFragmentManager.setFragmentResultListener(
            SelectionMapFragment.REQUEST_SELECT_ITEM,
            this
        ) { _: String?, result: Bundle ->
            if (result.containsKey(SelectionMapFragment.RESULT_SELECTED_ITEM)) {
                val instanceId = result.getLong(SelectionMapFragment.RESULT_SELECTED_ITEM)
                startActivity(FormFillingIntentFactory.editInstanceIntent(this, projectsDataService.requireCurrentProject().uuid, instanceId))
            } else if (result.containsKey(SelectionMapFragment.RESULT_CREATE_NEW_ITEM)) {
                startActivity(FormFillingIntentFactory.newInstanceIntent(this, FormsContract.getUri(projectsDataService.requireCurrentProject().uuid, formId)))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.load()
    }

    companion object {
        const val EXTRA_FORM_ID = "form_id"
    }
}
