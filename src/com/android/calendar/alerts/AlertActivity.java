/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.calendar.alerts;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.TaskStackBuilder;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.CalendarAlerts;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;

import androidx.annotation.Nullable;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarApplication;
import com.android.calendar.EventInfoActivity;
import com.android.calendar.alerts.GlobalDismissManager.AlarmId;

import java.util.LinkedList;
import java.util.List;

import ws.xsoh.etar.R;

/**
 * The alert panel that pops up when there is a calendar event alarm.
 * This activity is started by an intent that specifies an event id.
  */
public class AlertActivity extends Activity
    implements OnClickListener, AsyncQueryService.AsyncQueryDone
{
    public static final int INDEX_ROW_ID = 0;
    public static final int INDEX_TITLE = 1;
    public static final int INDEX_EVENT_LOCATION = 2;
    public static final int INDEX_ALL_DAY = 3;
    public static final int INDEX_BEGIN = 4;
    public static final int INDEX_END = 5;
    public static final int INDEX_EVENT_ID = 6;
    public static final int INDEX_COLOR = 7;
    public static final int INDEX_RRULE = 8;
    public static final int INDEX_HAS_ALARM = 9;
    public static final int INDEX_STATE = 10;
    public static final int INDEX_ALARM_TIME = 11;
    private static final String TAG = "AlertActivity";
    private static final String[] PROJECTION = new String[] {
        CalendarAlerts._ID,              // 0
        CalendarAlerts.TITLE,            // 1
        CalendarAlerts.EVENT_LOCATION,   // 2
        CalendarAlerts.ALL_DAY,          // 3
        CalendarAlerts.BEGIN,            // 4
        CalendarAlerts.END,              // 5
        CalendarAlerts.EVENT_ID,         // 6
        CalendarAlerts.CALENDAR_COLOR,   // 7
        CalendarAlerts.RRULE,            // 8
        CalendarAlerts.HAS_ALARM,        // 9
        CalendarAlerts.STATE,            // 10
        CalendarAlerts.ALARM_TIME,       // 11
    };
    private static final String SELECTION = CalendarAlerts.STATE + "=?";
    private static final String[] SELECTIONARG = new String[] {
        Integer.toString(CalendarAlerts.STATE_FIRED)
    };

    private AlertAdapter mAdapter;
    private AsyncQueryService mService;
    private Cursor mCursor;
    private ListView mListView;
    private final OnItemClickListener mViewListener = new OnItemClickListener() {

        @SuppressLint("NewApi")
        @Override
        public void onItemClick(
            AdapterView<?> parent, View view, int position, long i) {
            AlertActivity alertActivity = AlertActivity.this;
            Cursor cursor = alertActivity.getItemForView(view);

            long alarmId = cursor.getLong(INDEX_ROW_ID);
            long eventId = cursor.getLong(AlertActivity.INDEX_EVENT_ID);
            long startMillis = cursor.getLong(AlertActivity.INDEX_BEGIN);

            // Mark this alarm as DISMISSED
            dismissAlarm(alarmId, eventId, startMillis);

            // build an intent and task stack to start EventInfoActivity with
            // AllInOneActivity as the parent activity rooted to home.
            long endMillis = cursor.getLong(AlertActivity.INDEX_END);
            Intent eventIntent = AlertUtils.buildEventViewIntent(
                AlertActivity.this, eventId, startMillis, endMillis);

            TaskStackBuilder.create(AlertActivity.this)
                .addParentStack(EventInfoActivity.class)
                .addNextIntent(eventIntent).startActivities();
            alertActivity.finish();
        }
    };
    private Button mDismissAllButton;

    private void dismissFiredAlarms() {
        ContentValues values = new ContentValues(1 /* size */);
        values.put(PROJECTION[INDEX_STATE], CalendarAlerts.STATE_DISMISSED);
        String selection =
            CalendarAlerts.STATE + "=" + CalendarAlerts.STATE_FIRED;
        mService.startUpdate(0, this, CalendarAlerts.CONTENT_URI,
            values, selection, null);

        if (mCursor == null) {
            Log.e(TAG, "Unable to globally dismiss all notifications because cursor was null.");
            return;
        }
        if (mCursor.isClosed()) {
            Log.e(TAG, "Unable to globally dismiss all notifications because cursor was closed.");
            return;
        }
        if (!mCursor.moveToFirst()) {
            Log.e(TAG, "Unable to globally dismiss all notifications because cursor was empty.");
            return;
        }

        List<AlarmId> alarmIds = new LinkedList<AlarmId>();
        do {
            long eventId = mCursor.getLong(INDEX_EVENT_ID);
            long eventStart = mCursor.getLong(INDEX_BEGIN);
            alarmIds.add(new AlarmId(eventId, eventStart));
        } while (mCursor.moveToNext());
        initiateGlobalDismiss(alarmIds);
    }

    private void dismissAlarm(long id, long eventId, long startTime) {
        ContentValues values = new ContentValues(1 /* size */);
        values.put(PROJECTION[INDEX_STATE], CalendarAlerts.STATE_DISMISSED);
        String selection = CalendarAlerts._ID + "=" + id;
        mService.startUpdate(0, this, CalendarAlerts.CONTENT_URI,
            values, selection, null /* selectionArgs */);

        List<AlarmId> alarmIds = new LinkedList<AlarmId>();
        alarmIds.add(new AlarmId(eventId, startTime));
        initiateGlobalDismiss(alarmIds);
    }

    @SuppressWarnings("unchecked")
    private void initiateGlobalDismiss(List<AlarmId> alarmIds) {
        new AsyncTask<List<AlarmId>, Void, Void>() {
            @Override
            protected Void doInBackground(List<AlarmId>... params) {
                GlobalDismissManager.dismissGlobally(getApplicationContext(), params[0]);
                return null;
            }
        }.execute(alarmIds);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.alert_activity);
        setTitle(R.string.alert_title);

        mService = CalendarApplication.getAsyncQueryService();
        mAdapter = new AlertAdapter(this, R.layout.alert_item);

        mListView = findViewById(R.id.alert_container);
        mListView.setItemsCanFocus(true);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(mViewListener);

        mDismissAllButton = findViewById(R.id.dismiss_all);
        mDismissAllButton.setOnClickListener(this);

        // Disable the buttons, since they need mCursor, which is created asynchronously
        mDismissAllButton.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If the cursor is null, start the async handler. If it is not null just requery.
        if (mCursor == null) {
            Uri uri = CalendarAlerts.CONTENT_URI_BY_INSTANCE;
            mService.startQuery(0, this, uri, PROJECTION, SELECTION,
                SELECTIONARG, CalendarContract.CalendarAlerts.DEFAULT_SORT_ORDER);
        } else {
            if (!mCursor.requery()) {
                Log.w(TAG, "Cursor#requery() failed.");
                mCursor.close();
                mCursor = null;
            }
        }
    }

    void closeActivityIfEmpty() {
        if (mCursor != null && !mCursor.isClosed() && mCursor.getCount() == 0) {
            AlertActivity.this.finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        AlertService.updateAlertNotification(this);

        if (mCursor != null) {
            mCursor.deactivate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCursor != null) {
            mCursor.close();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mDismissAllButton) {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancelAll();

            dismissFiredAlarms();

            finish();
        }
    }

    public boolean isEmpty() {
        return mCursor == null || (mCursor.getCount() == 0);
    }

    public Cursor getItemForView(View view) {
        final int index = mListView.getPositionForView(view);
        if (index < 0) {
            return null;
        }
        return (Cursor) mListView.getAdapter().getItem(index);
    }

    /**
     * Called when an asynchronous query is completed.
     *
     * @param cookie the cookie object that's passed in from
     *               AsyncQueryService.startQuery().
     * @param cursor The cursor holding the results from the query,
     *               may be empty if nothing matched or null if it failed.
     */
    @Override
    public void onQueryDone(@Nullable Object cookie, Cursor cursor) {
        // Only set mCursor if the Activity is not finishing.
        // Otherwise close the cursor.
        if (!isFinishing()) {
            mCursor = cursor;
            mAdapter.changeCursor(cursor);
            mListView.setSelection(cursor.getCount() - 1);

            // The results are in, enable the buttons
            mDismissAllButton.setEnabled(true);
        } else {
            cursor.close();
        }
    }

    /**
     * Called when an asynchronous insert is completed.
     *
     * @param cookie the cookie object that's passed in from
     *               AsyncQueryService.startInsert().
     * @param uri    the URL of the newly created row,
     *               null indicates failure.
     */
    @Override
    public void onInsertDone(@Nullable Object cookie, Uri uri) {
        // never called
    }

    /**
     * Called when an asynchronous update is completed.
     *
     * @param cookie the cookie object that's passed in from
     *               AsyncQueryService.startUpdate().
     * @param result the number of rows updated
     *               zero indicates failure.
     */
    @Override
    public void onUpdateDone(@Nullable Object cookie, int result) {
        // no action required
    }

    /**
     * Called when an asynchronous delete is completed.
     *
     * @param cookie the cookie object that's passed in from
     *               AsyncQueryService.startDelete().
     * @param result the number of rows deleted: zero indicates failure.
     */
    @Override
    public void onDeleteDone(@Nullable Object cookie, int result) {
        // never called
    }

    /**
     * Called when an asynchronous {@link ContentProviderOperation} is
     * completed.
     *
     * @param cookie  the cookie object that's passed in from
     *                AsyncQueryService.startBatch().
     * @param results an array of results from the operations:
     *                the type of each result depends on the operation.
     */
    @Override
    public void onBatchDone(
        @Nullable Object cookie, ContentProviderResult[] results)
    {
        // never called
    }
}
