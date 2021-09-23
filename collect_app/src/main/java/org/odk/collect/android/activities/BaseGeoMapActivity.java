/*
 * Copyright 2018 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.activities;

import android.os.Bundle;

import org.odk.collect.android.R;
import org.odk.collect.androidshared.utils.ToastUtils;

/**
 * Implementation details common to the geo activities.  (After the migration
 * to storing user selections in the preferences, there's not a lot left here,
 * though this will probably grow as we add more geospatial capabilities.)
 */
public abstract class BaseGeoMapActivity extends CollectAbstractActivity {
    protected Bundle previousState;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        previousState = savedInstanceState;

        if (!permissionsProvider.areLocationPermissionsGranted()) {
            ToastUtils.showLongToast(this, R.string.not_granted_permission);
            finish();
        }
    }
}
