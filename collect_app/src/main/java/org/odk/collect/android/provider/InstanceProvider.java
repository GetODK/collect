/*
 * Copyright (C) 2007 The Android Open Source Project
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

package org.odk.collect.android.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.database.ODKSQLiteOpenHelper;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.utilities.MediaUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import timber.log.Timber;

import static org.odk.collect.android.utilities.PermissionUtils.areStoragePermissionsGranted;

public class InstanceProvider extends ContentProvider {


    private static final String DATABASE_NAME = "instances.db";
    private static final int DATABASE_VERSION = 11;		// smap
    private static final String INSTANCES_TABLE_NAME = "instances";

    private static HashMap<String, String> sInstancesProjectionMap;

    private static final int INSTANCES = 1;
    private static final int INSTANCE_ID = 2;

    private static final UriMatcher URI_MATCHER;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends ODKSQLiteOpenHelper {

        DatabaseHelper(String databaseName) {
            super(Collect.METADATA_PATH, databaseName, null, DATABASE_VERSION);
        }


        @Override
        public void onCreate(SQLiteDatabase db) {
           db.execSQL("CREATE TABLE " + INSTANCES_TABLE_NAME + " ("
               + InstanceColumns._ID + " integer primary key, "
               + InstanceColumns.DISPLAY_NAME + " text not null, "
               + InstanceColumns.SUBMISSION_URI + " text, "
               + InstanceColumns.CAN_EDIT_WHEN_COMPLETE + " text, "
               + InstanceColumns.INSTANCE_FILE_PATH + " text not null, "
               + InstanceColumns.JR_FORM_ID + " text not null, "
               + InstanceColumns.JR_VERSION + " text, "
               + InstanceColumns.SOURCE + " text, "		        // smap
               + InstanceColumns.FORM_PATH + " text, "		    // smap
               + InstanceColumns.ACT_LON + " double, "		    // smap
               + InstanceColumns.ACT_LAT + " double, "		    // smap
               + InstanceColumns.SCHED_LON + " double, "		// smap
               + InstanceColumns.SCHED_LAT + " double, "		// smap
               + InstanceColumns.T_TITLE + " text, "		    // smap
               + InstanceColumns.T_SCHED_START + " long, "		// smap
               + InstanceColumns.T_ACT_START + " long, "		// smap
               + InstanceColumns.T_ACT_FINISH + " long, "		// smap
               + InstanceColumns.T_ADDRESS + " text, "		    // smap
               + InstanceColumns.T_GEOM + " text, "		        // smap
               + InstanceColumns.T_GEOM_TYPE + " text, "		// smap
               + InstanceColumns.T_IS_SYNC + " text, "		    // smap
               + InstanceColumns.T_ASS_ID + " long, "		    // smap
               + InstanceColumns.T_TASK_STATUS + " text, "		// smap
               + InstanceColumns.T_REPEAT + " integer, "		// smap
               + InstanceColumns.T_UPDATEID + " text, "		    // smap
               + InstanceColumns.T_LOCATION_TRIGGER + " text, " // smap
               + InstanceColumns.T_SURVEY_NOTES + " text, "     // smap
               + InstanceColumns.UUID + " text, "		        // smap
               + InstanceColumns.T_UPDATED + " integer, "       // smap
               + InstanceColumns.STATUS + " text not null, "
               + InstanceColumns.LAST_STATUS_CHANGE_DATE + " date not null, "
               + InstanceColumns.DISPLAY_SUBTEXT + " text not null, "
               + InstanceColumns.T_SHOW_DIST + " integer default 0, "   // smap
               + InstanceColumns.T_HIDE + " integer default 0, "        // smap
               + InstanceColumns.DELETED_DATE + " date );" );
        }


        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        	int initialVersion = oldVersion;
        	if ( oldVersion < 2 ) {
        		db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
        					InstanceColumns.CAN_EDIT_WHEN_COMPLETE + " text;");
        		db.execSQL("UPDATE " + INSTANCES_TABLE_NAME + " SET " +
        					InstanceColumns.CAN_EDIT_WHEN_COMPLETE + " = '" + Boolean.toString(true) + "' WHERE " +
        					InstanceColumns.STATUS + " IS NOT NULL AND " +
        					InstanceColumns.STATUS + " != '" + InstanceProviderAPI.STATUS_INCOMPLETE + "'");
        	}
        	if ( oldVersion < 3 ) {
        		db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
    					InstanceColumns.JR_VERSION + " text;");
        	}
            // Smap Start
        	if ( oldVersion < 4 ) {
        		db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
    					InstanceColumns.SOURCE + " text;");
        	}
            if ( oldVersion < 5 ) {
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.FORM_PATH + " text;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.ACT_LON + " double;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.ACT_LAT + " double;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.SCHED_LON + " double;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.SCHED_LAT + " double;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_TITLE + " text;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_SCHED_START + " long;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_ACT_START + " long;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_ACT_FINISH + " long;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_ADDRESS + " text;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_GEOM + " text;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_GEOM_TYPE + " text;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_IS_SYNC + " text;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_ASS_ID + " long;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.T_TASK_STATUS + " text;");
                db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                        InstanceColumns.UUID + " text;");
            }
            if ( oldVersion < 6 ) {
                try {
                    db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                            InstanceColumns.T_REPEAT + " integer;");
                    db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                            InstanceColumns.T_UPDATEID + " text;");
                } catch(Exception e) {
                    // Catch errors, its possible the user upgraded then downgraded
                    Timber.w("Error in upgrading to database version 6");
                    e.printStackTrace();
                }
            }
            if ( oldVersion < 7 ) {
                try {
                    db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                            InstanceColumns.T_LOCATION_TRIGGER + " text;");
                } catch(Exception e) {
                    // Catch errors, its possible the user upgraded then downgraded
                    Timber.w("Error in upgrading to database version 7");
                    e.printStackTrace();
                }
            }
            if ( oldVersion < 8 ) {
                try {
                    db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                            InstanceColumns.T_SURVEY_NOTES + " text;");
                } catch(Exception e) {
                    // Catch errors, its possible the user upgraded then downgraded
                    Timber.w("Error in upgrading to database version 8");
                    e.printStackTrace();
                }
            }
            if (oldVersion < 9) {
                try {
                    db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                            InstanceColumns.DELETED_DATE + " date;");
                } catch(Exception e) {
                    // Catch errors, its possible the user upgraded then downgraded
                    Timber.w("Error in upgrading to database version 9");
                    e.printStackTrace();
                }

            }
            if (oldVersion < 10) {
        	    try {
                    db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                            InstanceColumns.T_UPDATED + " integer;");
                } catch(Exception e) {
                    // Catch errors, its possible the user upgraded then downgraded
                    Timber.w("Error in upgrading to database version 10");
                    e.printStackTrace();
                }
            }
            if (oldVersion < 11) {
                try {
                    db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                            InstanceColumns.T_SHOW_DIST + " integer default 0;");
                    db.execSQL("ALTER TABLE " + INSTANCES_TABLE_NAME + " ADD COLUMN " +
                            InstanceColumns.T_HIDE + " integer default 0;");
                } catch(Exception e) {
                    // Catch errors, its possible the user upgraded then downgraded
                    Timber.w("Error in upgrading to database version 10");
                    e.printStackTrace();
                }
            }
            Timber.w("Successfully upgraded database from version %d to %d, without destroying all the old data",
                    initialVersion, newVersion);
        }
    }

    private DatabaseHelper databaseHelper;

    private DatabaseHelper getDbHelper() {
        // wrapper to test and reset/set the dbHelper based upon the attachment state of the device.
        try {
            Collect.createODKDirs();
        } catch (RuntimeException e) {
            databaseHelper = null;
            Timber.e(e);    // smap
        }

        if (databaseHelper != null) {
            return databaseHelper;
        }
        databaseHelper = new DatabaseHelper(DATABASE_NAME);     // smap instance of InstanceDatabaseHelper

        return databaseHelper;
    }

    @Override
    public boolean onCreate() {
        if (!areStoragePermissionsGranted(getContext())) {
            Timber.i("Read and write permissions are required for this content provider to function.");
            return false;
        }

        // must be at the beginning of any activity that can be called from an external intent
        DatabaseHelper h = getDbHelper();
        return h != null;
    }

    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {

        if (!areStoragePermissionsGranted(getContext())) {
            return null;
        }

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(INSTANCES_TABLE_NAME);
        qb.setProjectionMap(sInstancesProjectionMap);
        qb.setStrict(true);

        switch (URI_MATCHER.match(uri)) {
            case INSTANCES:
                break;

            case INSTANCE_ID:
                qb.appendWhere(InstanceColumns._ID + "=" + uri.getPathSegments().get(1));
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // Get the database and run the query
        SQLiteDatabase db = getDbHelper().getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(@NonNull Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case INSTANCES:
                return InstanceColumns.CONTENT_TYPE;

            case INSTANCE_ID:
                return InstanceColumns.CONTENT_ITEM_TYPE;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(@NonNull Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (URI_MATCHER.match(uri) != INSTANCES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        if (!areStoragePermissionsGranted(getContext())) {
            return null;
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = System.currentTimeMillis();

        // Make sure that the fields are all set
        if (!values.containsKey(InstanceColumns.LAST_STATUS_CHANGE_DATE)) {
            values.put(InstanceColumns.LAST_STATUS_CHANGE_DATE, now);
        }

        if (!values.containsKey(InstanceColumns.DISPLAY_SUBTEXT)) {
            Date today = new Date();
            String text = getDisplaySubtext(InstanceProviderAPI.STATUS_INCOMPLETE, today);
            values.put(InstanceColumns.DISPLAY_SUBTEXT, text);
        }

        if (!values.containsKey(InstanceColumns.STATUS)) {
            values.put(InstanceColumns.STATUS, InstanceProviderAPI.STATUS_INCOMPLETE);
        }

        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        long rowId = db.insert(INSTANCES_TABLE_NAME, null, values);
        if (rowId > 0) {
            Uri instanceUri = ContentUris.withAppendedId(InstanceColumns.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(instanceUri, null);
            return instanceUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    private String getDisplaySubtext(String state, Date date) {
        try {
            if (state == null) {
                return new SimpleDateFormat(getContext().getString(R.string.added_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else if (InstanceProviderAPI.STATUS_INCOMPLETE.equalsIgnoreCase(state)) {
                return new SimpleDateFormat(getContext().getString(R.string.saved_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else if (InstanceProviderAPI.STATUS_COMPLETE.equalsIgnoreCase(state)) {
                return new SimpleDateFormat(getContext().getString(R.string.finalized_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else if (InstanceProviderAPI.STATUS_SUBMITTED.equalsIgnoreCase(state)) {
                return new SimpleDateFormat(getContext().getString(R.string.sent_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else if (InstanceProviderAPI.STATUS_SUBMISSION_FAILED.equalsIgnoreCase(state)) {
                return new SimpleDateFormat(
                        getContext().getString(R.string.sending_failed_on_date_at_time),
                        Locale.getDefault()).format(date);
            } else {
                return new SimpleDateFormat(getContext().getString(R.string.added_on_date_at_time),
                        Locale.getDefault()).format(date);
            }
        } catch (IllegalArgumentException e) {
            Timber.e(e);
            return "";
        }
    }

    private void deleteAllFilesInDirectory(File directory) {
        if (directory.exists()) {
            // do not delete the directory if it might be an
            // ODK Tables instance data directory. Let ODK Tables
            // manage the lifetimes of its filled-in form data
            // media attachments.
            if (directory.isDirectory() && !Collect.isODKTablesInstanceDataDirectory(directory)) {
                // delete any media entries for files in this directory...
                int images = MediaUtils.deleteImagesInFolderFromMediaProvider(directory);
                int audio = MediaUtils.deleteAudioInFolderFromMediaProvider(directory);
                int video = MediaUtils.deleteVideoInFolderFromMediaProvider(directory);

                Timber.i("removed from content providers: %d image files, %d audio files,"
                        + " and %d video files.", images, audio, video);

                // delete all the files in the directory
                File[] files = directory.listFiles();
                for (File f : files) {
                    // should make this recursive if we get worried about
                    // the media directory containing directories
                    f.delete();
                }
            }
            directory.delete();
        }
    }

    /**
     * This method removes the entry from the content provider, and also removes any associated
     * files.
     * files:  form.xml, [formmd5].formdef, formname-media {directory}
     */
    @Override
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        if (!areStoragePermissionsGranted(getContext())) {
            return 0;
        }
        int count = 0;

        SQLiteDatabase db = getDbHelper().getWritableDatabase();
        switch (URI_MATCHER.match(uri)) {
            case INSTANCES:
                Cursor del = null;
                try {
                    del = this.query(uri, null, where, whereArgs, null);
                    if (del != null && del.getCount() > 0) {
                        del.moveToFirst();
                        do {
                            String instanceFile = del.getString(
                                    del.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
                            File instanceDir = (new File(instanceFile)).getParentFile();
                            deleteAllFilesInDirectory(instanceDir);
                        } while (del.moveToNext());
                    }
                } finally {
                    if (del != null) {
                        del.close();
                    }
                }
                count = db.delete(INSTANCES_TABLE_NAME, where, whereArgs);
                break;

            case INSTANCE_ID:
                String instanceId = uri.getPathSegments().get(1);

                Cursor c = null;
                String status = null;
                try {
                    c = this.query(uri, null, where, whereArgs, null);
                    if (c != null && c.getCount() > 0) {
                        c.moveToFirst();
                        status = c.getString(c.getColumnIndex(InstanceColumns.STATUS));
                        do {
                            String instanceFile = c.getString(
                                    c.getColumnIndex(InstanceColumns.INSTANCE_FILE_PATH));
                            File instanceDir = (new File(instanceFile)).getParentFile();
                            deleteAllFilesInDirectory(instanceDir);
                        } while (c.moveToNext());
                    }
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }

                //We are going to update the status, if the form is submitted
                //We will not delete the record in table but we will delete the file
                if (status != null && status.equals(InstanceProviderAPI.STATUS_SUBMITTED)) {
                    ContentValues cv = new ContentValues();
                    cv.put(InstanceColumns.DELETED_DATE, System.currentTimeMillis());
                    count = Collect.getInstance().getContentResolver().update(uri, cv, null, null);
                } else {
                        String[] newWhereArgs;
                        if (whereArgs == null || whereArgs.length == 0) {
                            newWhereArgs = new String[] {instanceId};
                        } else {
                            newWhereArgs = new String[(whereArgs.length + 1)];
                            newWhereArgs[0] = instanceId;
                            System.arraycopy(whereArgs, 0, newWhereArgs, 1, whereArgs.length);
                        }

                    count =
                            db.delete(INSTANCES_TABLE_NAME,
                                        InstanceColumns._ID
                                                + "=?"
                                                + (!TextUtils.isEmpty(where) ? " AND ("
                                                + where + ')' : ""), newWhereArgs);
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String where, String[] whereArgs) {
        if (!areStoragePermissionsGranted(getContext())) {
            return 0;
        }
        int count = 0;

        SQLiteDatabase db = getDbHelper().getWritableDatabase();

        Long now = System.currentTimeMillis();

        // Make sure that the fields are all set
        if (!values.containsKey(InstanceColumns.LAST_STATUS_CHANGE_DATE)) {
            values.put(InstanceColumns.LAST_STATUS_CHANGE_DATE, now);
        }

        String status;
            switch (URI_MATCHER.match(uri)) {
            case INSTANCES:
                if (values.containsKey(InstanceColumns.STATUS)) {
                    status = values.getAsString(InstanceColumns.STATUS);

                    if (!values.containsKey(InstanceColumns.DISPLAY_SUBTEXT)) {
                        Date today = new Date();
                        String text = getDisplaySubtext(status, today);
                        values.put(InstanceColumns.DISPLAY_SUBTEXT, text);
                    }
                }

                count = db.update(INSTANCES_TABLE_NAME, values, where, whereArgs);
                break;

            case INSTANCE_ID:
                String instanceId = uri.getPathSegments().get(1);

                if (values.containsKey(InstanceColumns.STATUS)) {
                    status = values.getAsString(InstanceColumns.STATUS);

                    if (!values.containsKey(InstanceColumns.DISPLAY_SUBTEXT)) {
                        Date today = new Date();
                        String text = getDisplaySubtext(status, today);
                        values.put(InstanceColumns.DISPLAY_SUBTEXT, text);
                    }
                }

                    String[] newWhereArgs;
                    if (whereArgs == null || whereArgs.length == 0) {
                        newWhereArgs = new String[] {instanceId};
                    } else {
                        newWhereArgs = new String[(whereArgs.length + 1)];
                        newWhereArgs[0] = instanceId;
                        System.arraycopy(whereArgs, 0, newWhereArgs, 1, whereArgs.length);
                    }

                count =
                            db.update(INSTANCES_TABLE_NAME,
                                    values,
                                    InstanceColumns._ID
                                            + "=?"
                                            + (!TextUtils.isEmpty(where) ? " AND ("
                                            + where + ')' : ""), newWhereArgs);
                break;

            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(InstanceProviderAPI.AUTHORITY, "instances", INSTANCES);
        URI_MATCHER.addURI(InstanceProviderAPI.AUTHORITY, "instances/#", INSTANCE_ID);

        sInstancesProjectionMap = new HashMap<>();
        sInstancesProjectionMap.put(InstanceColumns._ID, InstanceColumns._ID);
        sInstancesProjectionMap.put(InstanceColumns.DISPLAY_NAME, InstanceColumns.DISPLAY_NAME);
        sInstancesProjectionMap.put(InstanceColumns.SUBMISSION_URI, InstanceColumns.SUBMISSION_URI);
        sInstancesProjectionMap.put(InstanceColumns.CAN_EDIT_WHEN_COMPLETE,
                InstanceColumns.CAN_EDIT_WHEN_COMPLETE);
        sInstancesProjectionMap.put(InstanceColumns.INSTANCE_FILE_PATH,
                InstanceColumns.INSTANCE_FILE_PATH);
        sInstancesProjectionMap.put(InstanceColumns.JR_FORM_ID, InstanceColumns.JR_FORM_ID);
        sInstancesProjectionMap.put(InstanceColumns.JR_VERSION, InstanceColumns.JR_VERSION);
        sInstancesProjectionMap.put(InstanceColumns.STATUS, InstanceColumns.STATUS);
        sInstancesProjectionMap.put(InstanceColumns.T_REPEAT, InstanceColumns.T_REPEAT);                // smap
        sInstancesProjectionMap.put(InstanceColumns.T_UPDATEID, InstanceColumns.T_UPDATEID);            // smap
        sInstancesProjectionMap.put(InstanceColumns.T_LOCATION_TRIGGER, InstanceColumns.T_LOCATION_TRIGGER);    // smap
        sInstancesProjectionMap.put(InstanceColumns.T_SURVEY_NOTES, InstanceColumns.T_SURVEY_NOTES);    // smap
        sInstancesProjectionMap.put(InstanceColumns.T_UPDATED, InstanceColumns.T_UPDATED);              // smap
        sInstancesProjectionMap.put(InstanceColumns.LAST_STATUS_CHANGE_DATE,
                InstanceColumns.LAST_STATUS_CHANGE_DATE);
        sInstancesProjectionMap.put(InstanceColumns.DISPLAY_SUBTEXT, InstanceColumns.DISPLAY_SUBTEXT);
        sInstancesProjectionMap.put(InstanceColumns.SOURCE, InstanceColumns.SOURCE);                // smap
        sInstancesProjectionMap.put(InstanceColumns.FORM_PATH, InstanceColumns.FORM_PATH);          // smap
        sInstancesProjectionMap.put(InstanceColumns.ACT_LON, InstanceColumns.ACT_LON);              // smap
        sInstancesProjectionMap.put(InstanceColumns.ACT_LAT, InstanceColumns.ACT_LAT);              // smap
        sInstancesProjectionMap.put(InstanceColumns.SCHED_LON, InstanceColumns.SCHED_LON);          // smap
        sInstancesProjectionMap.put(InstanceColumns.SCHED_LAT, InstanceColumns.SCHED_LAT);          // smap
        sInstancesProjectionMap.put(InstanceColumns.T_TITLE, InstanceColumns.T_TITLE);              // smap
        sInstancesProjectionMap.put(InstanceColumns.T_SCHED_START, InstanceColumns.T_SCHED_START);  // smap
        sInstancesProjectionMap.put(InstanceColumns.T_ACT_FINISH, InstanceColumns.T_ACT_FINISH);    // smap
        sInstancesProjectionMap.put(InstanceColumns.T_ADDRESS, InstanceColumns.T_ADDRESS);          // smap
        sInstancesProjectionMap.put(InstanceColumns.T_GEOM, InstanceColumns.T_GEOM);                // smap
        sInstancesProjectionMap.put(InstanceColumns.T_GEOM_TYPE, InstanceColumns.T_GEOM_TYPE);      // smap
        sInstancesProjectionMap.put(InstanceColumns.T_IS_SYNC, InstanceColumns.T_IS_SYNC);          // smap
        sInstancesProjectionMap.put(InstanceColumns.T_ASS_ID, InstanceColumns.T_ASS_ID);            // smap
        sInstancesProjectionMap.put(InstanceColumns.T_TASK_STATUS, InstanceColumns.T_TASK_STATUS);  // smap
        sInstancesProjectionMap.put(InstanceColumns.T_SHOW_DIST, InstanceColumns.T_SHOW_DIST);      // smap
        sInstancesProjectionMap.put(InstanceColumns.T_HIDE, InstanceColumns.T_HIDE);                // smap
        sInstancesProjectionMap.put(InstanceColumns.UUID, InstanceColumns.UUID);                    // smap
        sInstancesProjectionMap.put(InstanceColumns.DISPLAY_SUBTEXT,
                InstanceColumns.DISPLAY_SUBTEXT);
        sInstancesProjectionMap.put(InstanceColumns.DELETED_DATE, InstanceColumns.DELETED_DATE);
    }
}
