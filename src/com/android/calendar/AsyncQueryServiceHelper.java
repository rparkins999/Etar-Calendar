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

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentLinkedQueue;

public class AsyncQueryServiceHelper extends IntentService {
    private static final String TAG = "AsyncQuery";

    private static final ConcurrentLinkedQueue<OperationInfo> sWorkQueue
        = new ConcurrentLinkedQueue<>();

    protected Class<AsyncQueryService> mService = AsyncQueryService.class;

    public AsyncQueryServiceHelper(String name) {
        super(name);
    }

    public AsyncQueryServiceHelper() {
        super("AsyncQueryServiceHelper");
    }

    /**
     * Queues the operation for execution
     *
     * @param context The Conext passed from AsyncQueryService.
     * @param args OperationInfo object describing the operation
     */
    static public void queueOperation(Context context, OperationInfo args) {

        sWorkQueue.add(args);
        context.startService(new Intent(context, AsyncQueryServiceHelper.class));
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        OperationInfo args = sWorkQueue.poll();
        if (args == null) {
            return; // no work to do
        }

        ContentResolver resolver = args.resolver;
        if (resolver != null) {

            switch (args.op) {
                case OperationInfo.EVENT_ARG_QUERY:
                    Cursor cursor;
                    try {
                        cursor = resolver.query(args.uri, args.projection, args.selection,
                                args.selectionArgs, args.orderBy);
                        /*
                         * Calling getCount() causes the cursor window to be
                         * filled, which will make the first access on the main
                         * thread a lot faster
                         */
                        if (cursor != null) {
                            cursor.getCount();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, e.toString());
                        cursor = null;
                    }

                    args.result = cursor;
                    break;

                case OperationInfo.EVENT_ARG_INSERT:
                    args.result = resolver.insert(args.uri, args.values);
                    break;

                case OperationInfo.EVENT_ARG_UPDATE:
                    args.result = resolver.update(args.uri, args.values, args.selection,
                            args.selectionArgs);
                    break;

                case OperationInfo.EVENT_ARG_DELETE:
                    try {
                        args.result = resolver.delete(args.uri, args.selection, args.selectionArgs);
                    } catch (IllegalArgumentException e) {
                        Log.w(TAG, "Delete failed.");
                        Log.w(TAG, e.toString());
                        args.result = 0;
                    }

                    break;

                case OperationInfo.EVENT_ARG_BATCH:
                    try {
                        args.result = resolver.applyBatch(args.authority, args.cpo);
                    } catch (RemoteException | OperationApplicationException e)
                    {
                        Log.e(TAG, e.toString());
                        args.result = null;
                    }
                    break;
            }

            Message reply = args.handler.obtainMessage();
            reply.obj = args;
            reply.sendToTarget();
        }
    }

    protected static class OperationInfo {
        static final int EVENT_ARG_QUERY = 1;
        static final int EVENT_ARG_INSERT = 2;
        static final int EVENT_ARG_UPDATE = 3;
        static final int EVENT_ARG_DELETE = 4;
        static final int EVENT_ARG_BATCH = 5;
        public int op;

        public ContentResolver resolver;
        public Uri uri;
        public String authority;
        public String[] projection;
        public String selection;
        public String[] selectionArgs;
        public String orderBy;
        public ContentValues values;
        public ArrayList<ContentProviderOperation> cpo;
        public Handler handler;
        public AsyncQueryService.AsyncQueryDone caller;
        @Nullable public Object cookie;
        public Object result;

        private char opToChar(int op) {
            switch (op) {
                case EVENT_ARG_QUERY:
                    return 'Q';
                case EVENT_ARG_INSERT:
                    return 'I';
                case EVENT_ARG_UPDATE:
                    return 'U';
                case EVENT_ARG_DELETE:
                    return 'D';
                case EVENT_ARG_BATCH:
                    return 'B';
                default:
                    return '?';
            }
        }
        @Override
        public String toString() {
            return "OperationInfo [" +
                ",\n\t op= " + opToChar(op) +
                ",\n\t resolver= " + resolver +
                ",\n\t uri= " + uri +
                ",\n\t authority= " + authority +
                ",\n\t projection= " + Arrays.toString(projection) +
                ",\n\t selection= " + selection +
                ",\n\t selectionArgs= " + Arrays.toString(selectionArgs) +
                ",\n\t orderBy= " + orderBy +
                ",\n\t values= " + values +
                ",\n\t cpo= " + cpo +
                ",\n\t caller= " + caller.getClass().getName() +
                ",\n\t cookie= " + cookie +
                ",\n\t result= " + result + "\n]";
        }
    }
}
