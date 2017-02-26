/*
 * Copyright (C) 2009 University of Washington
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

package org.odk.collect.android.tasks;

import android.os.AsyncTask;
import android.util.Log;

import static android.os.SystemClock.sleep;

/**
 * Background task for appending a timer event to the timer log
 */
public class TimerSaveTask extends AsyncTask<String, Void, Void> {
    private final static String t = "TimerSaveTask";

    @Override
    protected Void doInBackground(String... params) {

        if (params.length > 1) {
            String filepath = params[0];
            for (int i = 1; i < params.length; i++) {
                Log.i(t, "########### saving: " + params[i]);
            }
        }
        return null;
    }


}
