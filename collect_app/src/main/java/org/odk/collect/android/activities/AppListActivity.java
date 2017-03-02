/*
 * Copyright 2017 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.activities;

import android.app.ListActivity;
import android.support.annotation.NonNull;
import android.widget.Button;
import android.widget.ListView;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.ActivityLogger;

abstract class AppListActivity extends ListActivity {
    protected final ActivityLogger logger = Collect.getInstance().getActivityLogger();

    protected boolean areCheckedItems() {
        return getCheckedCount() > 0;
    }

    /** The positions and IDs of the checked items */
    class CheckedItemInfo {
        final int[] positions;
        final long[] ids;

        CheckedItemInfo(int[] positions, long[] ids) {
            this.positions = positions;
            this.ids = ids;
        }
    }

    /** Returns the positions and IDs of the checked items */
    protected CheckedItemInfo getCheckedItemInfo() {
        return getCheckedItemInfo(getListView());
    }

    /** Returns the positions and IDs of the checked items */
    protected CheckedItemInfo getCheckedItemInfo(ListView lv) {
        int itemCount = lv.getCount();
        int checkedItemCount = lv.getCheckedItemCount();
        int[] checkedPositions = new int[checkedItemCount];
        long[] checkedIds = new long[checkedItemCount];
        int resultIndex = 0;
        for (int posIdx = 0; posIdx < itemCount; ++posIdx) {
            if (lv.isItemChecked(posIdx)) {
                checkedPositions[resultIndex] = posIdx;
                checkedIds      [resultIndex] = lv.getItemIdAtPosition(posIdx);
                resultIndex++;
            }
        }
        return new CheckedItemInfo(checkedPositions, checkedIds);
    }

    /** Returns the IDs of the checked items, as an array of Long */
    @NonNull
    protected Long[] getCheckedIdObjects() {
        long[] checkedIds = getCheckedItemInfo().ids;
        Long[] checkedIdObjects = new Long[checkedIds.length];
        for (int i = 0; i < checkedIds.length; ++i) {
            checkedIdObjects[i] = checkedIds[i];
        }
        return checkedIdObjects;
    }

    protected int getCheckedCount() {
        return getListView().getCheckedItemCount();
    }

    // toggles to all checked or all unchecked
    // returns:
    // true if result is all checked
    // false if result is all unchecked
    //
    // Toggle behavior is as follows:
    // if ANY items are unchecked, check them all
    // if ALL items are checked, uncheck them all
    public static boolean toggleChecked(ListView lv) {
        // shortcut null case
        if (lv == null) return false;

        boolean newCheckState = lv.getCount() > lv.getCheckedItemCount();
        setAllToCheckedState(lv, newCheckState);
        return newCheckState;
    }

    public static void setAllToCheckedState(ListView lv, boolean check) {
        // no-op if ListView null
        if (lv == null) return;

        for (int x = 0; x < lv.getCount(); x++) {
            lv.setItemChecked(x, check);
        }
    }

    // Function to toggle button label
    public static void toggleButtonLabel(Button mToggleButton, ListView lv) {
        if (lv.getCheckedItemCount() != lv.getCount()) {
            mToggleButton.setText(R.string.select_all);
        } else {
            mToggleButton.setText(R.string.clear_all);
        }
    }
}
