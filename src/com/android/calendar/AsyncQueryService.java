/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Modifications from the original version Copyright (C) Richard Parkins 2022
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calendar;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;

import androidx.annotation.Nullable;

import com.android.calendar.AsyncQueryServiceHelper.OperationInfo;

import java.util.ArrayList;

/**
 * A helper class that executes {@link ContentResolver} calls in a background
 * {@link android.app.Service}. This minimizes the chance of the call getting
 * lost because the caller ({@link android.app.Activity}) is killed and reduces
 * the burden on the UI thread. It is designed for easy migration from {@link
 * android.content.AsyncQueryHandler} which calls the {@link ContentResolver}
 * in a background thread. This supports query/insert/update/delete and also
 * batch mode i.e. {@link ContentProviderOperation}. Note that there's one
 * queue per application which serializes all the calls.
 */
public class AsyncQueryService extends Handler {

    private final Context mContext;

    // This is used to send results back to the caller
    public interface AsyncQueryDone {
        /**
         * Called when an asynchronous query is completed.
         *
         * @param cookie the cookie object that's passed in from
         *               AsyncQueryService.startQuery().
         * @param cursor The cursor holding the results from the query,
         *               may be empty if nothing matched or null if it failed.
         */
        void onQueryDone(@Nullable Object cookie, Cursor cursor);
        /**
         * Called when an asynchronous insert is completed.
         *
         * @param cookie the cookie object that's passed in from
         *               AsyncQueryService.startInsert().
         * @param uri    the URL of the newly created row,
         *               null indicates failure.
         */
        void onInsertDone(@Nullable Object cookie, Uri uri);
        /**
         * Called when an asynchronous update is completed.
         *
         * @param cookie the cookie object that's passed in from
         *               AsyncQueryService.startUpdate().
         * @param result the number of rows updated
         *               zero indicates failure.
         */
        void onUpdateDone(@Nullable Object cookie, int result);
        /**
         * Called when an asynchronous delete is completed.
         *
         * @param cookie the cookie object that's passed in from
         *               AsyncQueryService.startDelete().
         * @param result the number of rows deleted: zero indicates failure.
         */
        void onDeleteDone(@Nullable Object cookie, int result);
        /**
         * Called when an asynchronous {@link ContentProviderOperation} is
         * completed.
         *
         * @param cookie  the cookie object that's passed in from
         *                AsyncQueryService.startBatch().
         * @param results an array of results from the operations:
         *                the type of each result depends on the operation.
         */
        void onBatchDone(@Nullable Object cookie,
                         ContentProviderResult[] results);
    }

    // Creator: this should be created once per app. so that all the
    // activities can share one instance.
    public AsyncQueryService(Context context) {
        mContext = context;
    }

    /**
     * This method begins an asynchronous query. When the query is done
     * caller.onQueryDone() is called.
     *
     * @param cookie Object that gets passed back to caller.onQueryDone().
     * @param caller The interface to call to report completion,
     *               can be null if no response required.
     * @param uri The URI, using the content:// scheme, for the content to
     *            retrieve.
     * @param projection A list of which columns to return. Passing null will
     *            return all columns, which is discouraged to prevent reading
     *            data from storage that isn't going to be used.
     * @param selection A filter declaring which rows to return, formatted as an
     *            SQL WHERE clause (excluding the WHERE itself). Passing null
     *            will return all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be
     *            replaced by the values from selectionArgs, in the order that
     *            they appear in the selection. The values will be bound as
     *            Strings.
     * @param orderBy How to order the rows, formatted as an SQL ORDER BY clause
     *            (excluding the ORDER BY itself). Passing null will use the
     *            default sort order, which may be unordered.
     */
    public void startQuery(
        @Nullable Object cookie, @Nullable AsyncQueryDone caller, Uri uri,
        String[] projection, String selection,
        String[] selectionArgs, String orderBy)
    {
        OperationInfo info = new OperationInfo();
        info.op = OperationInfo.EVENT_ARG_QUERY;
        info.resolver = mContext.getContentResolver();
        info.uri = uri;
        info.projection = projection;
        info.selection = selection;
        info.selectionArgs = selectionArgs;
        info.orderBy = orderBy;
        info.handler = this;
        info.caller = caller;
        info.cookie = cookie;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    /**
     * This method begins an asynchronous insert. When the insert operation is
     * done caller.onInsertDone() is called.
     *
     * @param cookie Object that gets passed back to caller.onInsertComplete().
     * @param caller The interface to call to report completion,
     *               can be null if no response required.
     * @param uri the Uri passed to the insert operation.
     * @param initialValues the ContentValues parameter passed to the insert
     *            operation.
     */
    public void startInsert(
        @Nullable Object cookie, @Nullable AsyncQueryDone caller,
        Uri uri, ContentValues initialValues)
    {
        OperationInfo info = new OperationInfo();
        info.op = OperationInfo.EVENT_ARG_INSERT;
        info.resolver = mContext.getContentResolver();
        info.uri = uri;
        info.values = initialValues;
        info.handler = this;
        info.cookie = cookie;
        info.caller = caller;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    /**
     * This method begins an asynchronous update. When the update operation is
     * done caller.onUpdateDone() is called.
     *
     * @param cookie Object that gets passed back to caller.onUpdateComplete().
     * @param caller The interface to call to report completion,
     *               can be null if no response required.
     * @param uri the Uri passed to the update operation.
     * @param values the ContentValues parameter passed to the update operation.
     * @param selection A filter declaring which rows to update, formatted as an
     *            SQL WHERE clause (excluding the WHERE itself). Passing null
     *            will update all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be
     *            replaced by the values from selectionArgs, in the order that
     *            they appear in the selection. The values will be bound as
     *            Strings.
     */
    public void startUpdate(
        @Nullable Object cookie, @Nullable AsyncQueryDone caller, Uri uri,
        ContentValues values, String selection, String[] selectionArgs)
    {
        OperationInfo info = new OperationInfo();
        info.op = OperationInfo.EVENT_ARG_UPDATE;
        info.resolver = mContext.getContentResolver();
        info.handler = this;
        info.cookie = cookie;
        info.caller = caller;
        info.uri = uri;
        info.values = values;
        info.selection = selection;
        info.selectionArgs = selectionArgs;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    /**
     * This method begins an asynchronous delete. When the delete operation is
     * done caller.onDeleteDone is called.
     *
     * @param cookie Object that gets passed back to caller.onDeleteComplete().)
     * @param caller The interface to call to report completion
     *               can be null if no response required.
     * @param uri the Uri passed to the delete operation.
     * @param selection A filter declaring which rows to delete, formatted as an
     *            SQL WHERE clause (excluding the WHERE itself). Passing null
     *            will delete all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be
     *            replaced by the values from selectionArgs, in the order that
     *            they appear in the selection. The values will be bound as
     *            Strings.
     */
    public void startDelete(
        @Nullable Object cookie, @Nullable AsyncQueryDone caller, Uri uri,
        String selection, String[] selectionArgs)
    {
        OperationInfo info = new OperationInfo();
        info.op = OperationInfo.EVENT_ARG_DELETE;
        info.resolver = mContext.getContentResolver();
        info.handler = this;
        info.cookie = cookie;
        info.caller = caller;
        info.uri = uri;
        info.selection = selection;
        info.selectionArgs = selectionArgs;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    /**
     * This method begins an asynchronous {@link ContentProviderOperation}. When
     * the operation is done caller.onBatchDone is called.
     *
     * @param cookie Object that gets passed back to caller.onDeleteComplete().
     * @param caller The interface to call to report completion
     * @param authority the authority used for the
     *            {@link ContentProviderOperation}.
     * @param cpo the {@link ContentProviderOperation} to be executed.
     */
    public void startBatch(
        @Nullable Object cookie, @Nullable AsyncQueryDone caller,
        String authority, ArrayList<ContentProviderOperation> cpo)
    {
        OperationInfo info = new OperationInfo();
        info.op = OperationInfo.EVENT_ARG_BATCH;
        info.resolver = mContext.getContentResolver();
        info.handler = this;
        info.cookie = cookie;
        info.caller = caller;
        info.authority = authority;
        info.cpo = cpo;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    @Override
    public void handleMessage(Message msg) {
        OperationInfo info = (OperationInfo) msg.obj;
        if (info.caller != null) {

            // pass cookie back to caller on each callback.
            switch (info.op) {
                case OperationInfo.EVENT_ARG_QUERY:
                    info.caller.onQueryDone(info.cookie, (Cursor) info.result);
                    break;

                case OperationInfo.EVENT_ARG_INSERT:
                    info.caller.onInsertDone(info.cookie, (Uri) info.result);
                    break;

                case OperationInfo.EVENT_ARG_UPDATE:
                    info.caller.onUpdateDone(info.cookie, (Integer) info.result);
                    break;

                case OperationInfo.EVENT_ARG_DELETE:
                    info.caller.onDeleteDone(info.cookie, (Integer) info.result);
                    break;

                case OperationInfo.EVENT_ARG_BATCH:
                    info.caller.onBatchDone(
                        info.cookie, (ContentProviderResult[]) info.result);
                    break;
            }
        }
    }
}
