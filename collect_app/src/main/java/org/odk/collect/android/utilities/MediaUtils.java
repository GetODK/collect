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

package org.odk.collect.android.utilities;

import static org.odk.collect.android.utilities.FileUtils.deleteAndReport;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import org.odk.collect.android.BuildConfig;
import org.odk.collect.android.R;
import org.odk.collect.androidshared.utils.ToastUtils;

import java.io.File;

import timber.log.Timber;

/**
 * Consolidate all interactions with media providers here.
 * <p>
 * The functionality of getPath() was provided by paulburke as described here:
 * See
 * http://stackoverflow.com/questions/20067508/get-real-path-from-uri-android
 * -kitkat-new-storage-access-framework for details
 *
 * @author mitchellsundt@gmail.com
 * @author paulburke
 */
public class MediaUtils {
    public void deleteMediaFile(String imageFile) {
        deleteAndReport(new File(imageFile));
    }

    public void openFile(Context context, File file, String mimeType) {
        Uri contentUri = ContentUriProvider.getUriForFile(context, BuildConfig.APPLICATION_ID + ".provider", file);
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = FileUtils.getMimeType(file);
        }
        intent.setDataAndType(contentUri, mimeType);
        FileUtils.grantFileReadPermissions(intent, contentUri, context);

        if (new ActivityAvailability(context).isActivityAvailable(intent)) {
            context.startActivity(intent);
        } else {
            String message = context.getString(R.string.activity_not_found, context.getString(R.string.open_file));
            ToastUtils.showLongToast(context, message);
            Timber.w(message);
        }
    }

    public void pickFile(Activity activity, String mimeType, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        activity.startActivityForResult(intent, requestCode);
    }

    public boolean isVideoFile(File file) {
        return FileUtils.getMimeType(file).startsWith("video");
    }

    public boolean isImageFile(File file) {
        return FileUtils.getMimeType(file).startsWith("image");
    }

    public boolean isAudioFile(File file) {
        return FileUtils.getMimeType(file).startsWith("audio");
    }
}
