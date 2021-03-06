/**
 *   Nextcloud Android client application
 *
 *   Copyright (C) 2016 Andy Scherzinger
 *   Copyright (C) 2016 Nextcloud.
 *
 *   This program is free software; you can redistribute it and/or
 *   modify it under the terms of the GNU AFFERO GENERAL PUBLIC LICENSE
 *   License as published by the Free Software Foundation; either
 *   version 3 of the License, or any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU AFFERO GENERAL PUBLIC LICENSE for more details.
 *
 *   You should have received a copy of the GNU Affero General Public
 *   License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.owncloud.android.datamodel;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.owncloud.android.MainApp;
import com.owncloud.android.db.ProviderMeta;
import com.owncloud.android.lib.common.utils.Log_OC;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;

/**
 * Database provider for handling the persistence aspects of {@link SyncedFolder}s.
 */
public class SyncedFolderProvider extends Observable {
    static private final String TAG = SyncedFolderProvider.class.getSimpleName();

    private ContentResolver mContentResolver;

    /**
     * constructor.
     *
     * @param contentResolver the ContentResolver to work with.
     */
    public SyncedFolderProvider(ContentResolver contentResolver) {
        if (contentResolver == null) {
            throw new IllegalArgumentException("Cannot create an instance with a NULL contentResolver");
        }
        mContentResolver = contentResolver;
    }

    /**
     * Stores an media folder sync object in database.
     *
     * @param syncedFolder synced folder to store
     * @return synced folder id, -1 if the insert process fails.
     */
    public long storeFolderSync(SyncedFolder syncedFolder) {
        Log_OC.v(TAG, "Inserting " + syncedFolder.getLocalPath() + " with enabled=" + syncedFolder.isEnabled());

        ContentValues cv = createContentValuesFromSyncedFolder(syncedFolder);

        Uri result = mContentResolver.insert(ProviderMeta.ProviderTableMeta.CONTENT_URI_SYNCED_FOLDERS, cv);

        if (result != null) {
            notifyFolderSyncObservers(syncedFolder);
            return Long.parseLong(result.getPathSegments().get(1));
        } else {
            Log_OC.e(TAG, "Failed to insert item " + syncedFolder.getLocalPath() + " into folder sync db.");
            return -1;
        }
    }

    /**
     * get all synced folder entries.
     *
     * @return all synced folder entries, empty if none have been found
     */
    public List<SyncedFolder> getSyncedFolders() {
        Cursor cursor = mContentResolver.query(
                ProviderMeta.ProviderTableMeta.CONTENT_URI_SYNCED_FOLDERS,
                null,
                "1=1",
                null,
                null
        );

        if (cursor != null) {
            List<SyncedFolder> list = new ArrayList<>(cursor.getCount());
            if (cursor.moveToFirst()) {
                do {
                    SyncedFolder syncedFolder = createSyncedFolderFromCursor(cursor);
                    if (syncedFolder == null) {
                        Log_OC.e(TAG, "SyncedFolder could not be created from cursor");
                    } else {
                        list.add(cursor.getPosition(), syncedFolder);
                    }
                } while (cursor.moveToNext());

            }
            cursor.close();
            return list;
        } else {
            Log_OC.e(TAG, "DB error creating read all cursor for synced folders.");
        }

        return new ArrayList<>(0);
    }

    /**
     * Update upload status of file uniquely referenced by id.
     *
     * @param id      folder sync id.
     * @param enabled new status.
     * @return the number of rows updated.
     */
    public int updateFolderSyncEnabled(long id, Boolean enabled) {
        Log_OC.v(TAG, "Storing sync folder id" + id + " with enabled=" + enabled);

        int result = 0;
        Cursor cursor = mContentResolver.query(
                ProviderMeta.ProviderTableMeta.CONTENT_URI_SYNCED_FOLDERS,
                null,
                ProviderMeta.ProviderTableMeta._ID + "=?",
                new String[]{String.valueOf(id)},
                null
        );

        if (cursor != null && cursor.getCount() == 1) {
            while (cursor.moveToNext()) {
                // read sync folder object and update
                SyncedFolder syncedFolder = createSyncedFolderFromCursor(cursor);

                syncedFolder.setEnabled(enabled);

                // update sync folder object in db
                result = updateSyncFolder(syncedFolder);

                cursor.close();
            }
        } else {
            if (cursor == null) {
                Log_OC.e(TAG, "Sync folder db cursor for ID=" + id + " in NULL.");
            } else {
                Log_OC.e(TAG, cursor.getCount() + " items for id=" + id + " available in sync folder database. " +
                        "Expected 1. Failed to update sync folder db.");
            }
        }

        return result;
    }

    /**
     * find a synced folder by local path.
     *
     * @param localPath the local path of the local folder
     * @return the synced folder if found, else null
     */
    public SyncedFolder findByLocalPath(String localPath) {
        SyncedFolder result = null;
        Cursor cursor = mContentResolver.query(
                ProviderMeta.ProviderTableMeta.CONTENT_URI_SYNCED_FOLDERS,
                null,
                ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_LOCAL_PATH + "==" + localPath,
                null,
                null
        );

        if (cursor != null && cursor.getCount() == 1) {
            result = createSyncedFolderFromCursor(cursor);
            cursor.close();
        } else {
            if (cursor == null) {
                Log_OC.e(TAG, "Sync folder db cursor for local path=" + localPath + " in NULL.");
            } else {
                Log_OC.e(TAG, cursor.getCount() + " items for local path=" + localPath
                        + " available in sync folder db. Expected 1. Failed to update sync folder db.");
            }
        }

        return result;

    }

    /**
     * update given synced folder.
     *
     * @param syncedFolder the synced folder to be updated.
     * @return the number of rows updated.
     */
    public int updateSyncFolder(SyncedFolder syncedFolder) {
        Log_OC.v(TAG, "Updating " + syncedFolder.getLocalPath() + " with enabled=" + syncedFolder.isEnabled());

        ContentValues cv = createContentValuesFromSyncedFolder(syncedFolder);

        int result = mContentResolver.update(
                ProviderMeta.ProviderTableMeta.CONTENT_URI_SYNCED_FOLDERS,
                cv,
                ProviderMeta.ProviderTableMeta._ID + "=?",
                new String[]{String.valueOf(syncedFolder.getId())}
        );

        if (result > 0) {
            notifyFolderSyncObservers(syncedFolder);
        }

        return result;
    }

    /**
     * maps a cursor into a SyncedFolder object.
     *
     * @param cursor the cursor
     * @return the mapped SyncedFolder, null if cursor is null
     */
    private SyncedFolder createSyncedFolderFromCursor(Cursor cursor) {
        SyncedFolder syncedFolder = null;
        if (cursor != null) {
            long id = cursor.getLong(cursor.getColumnIndex(ProviderMeta.ProviderTableMeta._ID));
            String localPath = cursor.getString(cursor.getColumnIndex(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_LOCAL_PATH));
            String remotePath = cursor.getString(cursor.getColumnIndex(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_REMOTE_PATH));
            Boolean wifiOnly = cursor.getInt(cursor.getColumnIndex(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_WIFI_ONLY)) == 1;
            Boolean chargingOnly = cursor.getInt(cursor.getColumnIndex(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_CHARGING_ONLY)) == 1;
            Boolean subfolderByDate = cursor.getInt(cursor.getColumnIndex(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_SUBFOLDER_BY_DATE)) == 1;
            String accountName = cursor.getString(cursor.getColumnIndex(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_ACCOUNT));
            Integer uploadAction = cursor.getInt(cursor.getColumnIndex(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_UPLOAD_ACTION));
            Boolean enabled = cursor.getInt(cursor.getColumnIndex(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_ENABLED)) == 1;

            syncedFolder = new SyncedFolder(id, localPath, remotePath, wifiOnly, chargingOnly, subfolderByDate,
                    accountName, uploadAction, enabled);
        }
        return syncedFolder;
    }

    /**
     * create ContentValues object based on given SyncedFolder.
     *
     * @param syncedFolder the synced folder
     * @return the corresponding ContentValues object
     */
    @NonNull
    private ContentValues createContentValuesFromSyncedFolder(SyncedFolder syncedFolder) {
        ContentValues cv = new ContentValues();
        cv.put(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_LOCAL_PATH, syncedFolder.getLocalPath());
        cv.put(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_REMOTE_PATH, syncedFolder.getRemotePath());
        cv.put(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_WIFI_ONLY, syncedFolder.getWifiOnly());
        cv.put(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_CHARGING_ONLY, syncedFolder.getChargingOnly());
        cv.put(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_ENABLED, syncedFolder.isEnabled());
        cv.put(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_SUBFOLDER_BY_DATE, syncedFolder.getSubfolderByDate());
        cv.put(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_ACCOUNT, syncedFolder.getAccount());
        cv.put(ProviderMeta.ProviderTableMeta.SYNCED_FOLDER_UPLOAD_ACTION, syncedFolder.getUploadAction());
        return cv;
    }

    /**
     * Inform all observers about data change.
     */
    private void notifyFolderSyncObservers(SyncedFolder syncedFolder) {
        MainApp.getSyncedFolderObserverService().restartObserver(syncedFolder);
        Log_OC.d(TAG, "notifying folder sync data observers for changed/added: " + syncedFolder.getLocalPath());
    }
}
