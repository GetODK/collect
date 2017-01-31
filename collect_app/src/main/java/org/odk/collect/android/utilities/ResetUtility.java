/*
 * Copyright 2017 Nafundi
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
package org.odk.collect.android.utilities;

import android.content.Context;
import android.preference.PreferenceManager;

import org.odk.collect.android.R;
import org.odk.collect.android.provider.DatabaseReader;
import org.odk.collect.android.tasks.DeleteInstancesTask;

public class ResetUtility {

    public void reset(final Context context, boolean resetPreferences, boolean resetInstances) {
        if (resetPreferences) {
            resetPreferences(context);
        }
        if (resetInstances) {
            resetInstances(context);
        }
    }

    private void resetPreferences(Context context) {
        PreferenceManager
                .getDefaultSharedPreferences(context)
                .edit()
                .clear()
                .apply();

        PreferenceManager.setDefaultValues(context, R.xml.preferences, true);
    }

    private void resetInstances(final Context context) {
        final Long[] allInstances = new DatabaseReader().getAllInstancesIDs(context);

        DeleteInstancesTask task = new DeleteInstancesTask();
        task.setContentResolver(context.getContentResolver());
        task.execute(allInstances);
    }
}