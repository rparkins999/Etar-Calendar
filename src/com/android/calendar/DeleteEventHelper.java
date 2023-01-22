/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.text.TextUtils;
import android.text.format.Time;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.calendar.event.EditEventHelper;
import com.android.calendar.persistence.CalendarRepository;
import com.android.calendarcommon2.EventRecurrence;

import java.util.ArrayList;
import java.util.Arrays;

import ws.xsoh.etar.R;

/**
 * A helper class for deleting events.  If a normal event is selected for
 * deletion, then this pops up a confirmation dialog.  If the user confirms,
 * then the normal event is deleted.
 *
 * <p>
 * If a repeating event is selected for deletion, then this pops up a dialog
 * asking if the user wants to delete just this one instance, or all the
 * events in the series, or this event plus all following events.  The user
 * may also cancel the delete.
 * </p>
 *
 * <p>
 * To use this class, create an instance, passing in the parent activity
 * and a boolean that determines if the parent activity should exit if the
 * event is deleted.  Then to use the instance, call one of the
 * {@see delete()} methods on this class.
 *
 * An instance of this class may be created once and reused (by calling
 * {@see #delete()} multiple times).
 */
public class DeleteEventHelper implements AsyncQueryService.AsyncQueryDone
{
    /**
     * These are the corresponding indices into the array of strings
     * "R.array.delete_repeating_labels" in the resource file.
     */
    public static final int DELETE_SELECTED = 0;
    public static final int DELETE_ALL_FOLLOWING = 1;
    public static final int DELETE_ALL = 2;
    private final Activity mParent;
    private final Context mContext;
    // Start time of instance, in UTC milliseconds since the epoch.
    private long mInstanceStart;
    // End time of instance, in UTC milliseconds since the epoch.
    private long mInstanceEnd;
    private CalendarEventModel mModel;
    // If true, then call finish() on the parent activity when done.
    private final boolean mExitWhenDone;
    // the runnable to execute when the delete is confirmed
    private Runnable mCallback;
    private int mWhichDelete;
    private ArrayList<Integer> mWhichIndex;
    private AlertDialog mAlertDialog;
    private Dialog.OnDismissListener mDismissListener;

    private String mSyncId;

    private final AsyncQueryService mService;

    private DeleteNotifyListener mDeleteStartedListener = null;

    // This callback is used when a normal event is deleted.
    private final DialogInterface.OnClickListener mDeleteNormalDialogListener
        = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int button) {
            deleteStarted();
            long id = mModel.mId;

            // If this event is part of a local calendar,
            // really remove it from the database
            //
            // "There are two versions of delete: as an app and as a sync adapter.
            // An app delete will set the deleted column on an event
            // and remove all instances of that event.
            // A sync adapter delete will remove the event
            // and all associated data from the database."
            // from https://developer.android.com/reference/android/provider/CalendarContract.Events
            boolean isLocal = mModel.mSyncAccountType.equals(CalendarContract.ACCOUNT_TYPE_LOCAL);
            Uri deleteContentUri = isLocal ? CalendarRepository.asLocalCalendarSyncAdapter(mModel.mSyncAccountName, Events.CONTENT_URI) : Events.CONTENT_URI;

            Uri uri = ContentUris.withAppendedId(deleteContentUri, id);
            mService.startDelete(null, DeleteEventHelper.this,
                uri, null, null);
        }
    };
    // This callback is used when an exception to an event is deleted.
    private final DialogInterface.OnClickListener mDeleteExceptionDialogListener
        = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int button) {
            deleteStarted();
            deleteExceptionEvent();
        }
    };
    // This callback is used when a list item for a repeating event is selected
    private final DialogInterface.OnClickListener mDeleteListListener
        = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int button) {
            // set mWhichDelete to the delete type at that index
            mWhichDelete = mWhichIndex.get(button);

            // Enable the "ok" button now that the user has selected which
            // events in the series to delete.
            Button ok = mAlertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            ok.setEnabled(true);
        }
    };
    // This callback is used when a repeating event is deleted.
    private final DialogInterface.OnClickListener mDeleteRepeatingDialogListener
        = new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int button) {
            deleteStarted();
            if (mWhichDelete != -1) {
                deleteRepeatingEvent(mWhichDelete);
            }
        }
    };

    public DeleteEventHelper(
        Context context, Activity parentActivity, boolean exitWhenDone)
    {
        if (exitWhenDone && parentActivity == null) {
            throw new IllegalArgumentException("parentActivity is required to exit when done");
        }
        mContext = context;
        mParent = parentActivity;
        mService = CalendarApplication.getAsyncQueryService();
        mExitWhenDone = exitWhenDone;
    }

    public void tidyup() {
        if (mCallback != null) {
            mCallback.run();
        }
        if (mExitWhenDone) {
            mParent.finish();
        }
    }

    public void deleteAfterQuery(CalendarEventModel model) {
        delete(mInstanceStart, mInstanceEnd, model, mWhichDelete);
    }

    /**
     * Does the required processing for deleting an event, which includes
     * first popping up a dialog asking for confirmation (if the event is
     * a normal event) or a dialog asking which events to delete (if the
     * event is a repeating event).  The "which" parameter is used to check
     * the initial selection and is only used for repeating events.  Set
     * "which" to -1 to have nothing selected initially.
     *
     * @param begin the begin time of the instance, in UTC milliseconds
     * @param end the end time of the instance, in UTC milliseconds
     * @param eventId the event id
     */
    public void delete(long begin, long end, long eventId) {
        // eventId must be >=0, since the UI doesn't allow the user
        // to delete when creating a new event.
        // Dismissing the EditEventActivity will throw away
        // a partially created event.
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);
        mService.startQuery(null, this, uri,
            EditEventHelper.EVENT_PROJECTION, null,
            null, null);
        // Save these to put into the model when we've made it
        mInstanceStart = begin;
        mInstanceEnd = end;
        mWhichDelete = -1;
    }

    public void delete(long begin, long end, long eventId, Runnable callback) {
        delete(begin, end, eventId);
        mCallback = callback;
    }

    /**
     * Does the processing for deleting an event
     * after the {@link CalendarEventModel} has been filled from the database.
     * The {@link CalendarEventModel} must have at least the following fields
     * valid.
     * For a non-recurring event:
     *
     * <ul>
     *   <li> mId </li>
     *   <li> mRrule (which will be null or empty)</li>
     *   <li> mOriginalSyncId</li>
     *   <li> mSyncAccountName </li>
     *   <li> mSyncAccountType </li>
     * </ul>
     *
     * For a recurring event:
     *
     * <ul>
     *   <li> mId </li>
     *   <li> mEventStart </li>
     *   <li> mEventEnd </li>
     *   <li> mInstanceEnd </li>
     *   <li> mAllDay </li>
     *   <li> mRrule (which will be nonempty)</li>
     *   <li> mTitle (not essential, but used)</li>
     *   <li> mIsOrganizer </li>
     *   <li> mSyncId </li>
     *   <li> mSyncAccountName </li>
     *   <li> mSyncAccountType </li>
     * </ul>
     *
     * This will always prompt the user but if by the time the dialog
     * is dismissed the event no longer exists in the database
     * (which can happen if another app deletes it) this method
     * will return without modifying the database.
     *
     * @param begin the begin time of the instance, in UTC milliseconds
     * @param end the end time of the instance, in UTC milliseconds
     * @param which one of the values {@see DELETE_SELECTED},
     *  {@see DELETE_ALL_FOLLOWING}, {@see DELETE_ALL}, or -1
     */
    public void delete(
        long begin, long end, CalendarEventModel model, int which)
    {
        mWhichDelete = which;
        model.mInstanceStart = mInstanceStart = begin;
        model.mInstanceEnd = mInstanceEnd = end;
        mModel = model;
        mSyncId = model.mSyncId;

        if (TextUtils.isEmpty(model.mRrule)) {
            // This is a normal (non-recurring) event
            mAlertDialog = new AlertDialog.Builder(mContext)
                .setMessage(R.string.delete_this_event_title)
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setPositiveButton(android.R.string.ok,
                    (model.mOriginalSyncId == null)
                        ? mDeleteNormalDialogListener
                        : mDeleteExceptionDialogListener)
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(mDismissListener)
                .show();
        } else {
            // This is (an instance of) a recurring event.
            // Pop up a dialog asking which events to delete.
            Resources res = mContext.getResources();
            ArrayList<String> labelArray
                = new ArrayList<>(Arrays.asList(
                    res.getStringArray(R.array.delete_repeating_labels)));
            // asList doesn't like int[] so creating it manually.
            int[] labelValues = res.getIntArray(R.array.delete_repeating_values);
            ArrayList<Integer> labelIndex = new ArrayList<>();
            for (int val : labelValues) {
                labelIndex.add(val);
            }

            if (mSyncId == null) {
                // remove 'Only this event' item
                labelArray.remove(0);
                labelIndex.remove(0);
                if (!model.mIsOrganizer) {
                    // remove 'This and future events' item
                    labelArray.remove(0);
                    labelIndex.remove(0);
                }
            } else if (!model.mIsOrganizer) {
                // remove 'This and future events' item
                labelArray.remove(1);
                labelIndex.remove(1);
            }
            if (which != -1) {
                // transform the which to the index in the array
                which = labelIndex.indexOf(which);
            }
            mWhichIndex = labelIndex;
            ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext,
                    android.R.layout.simple_list_item_single_choice, labelArray);
            mAlertDialog = new AlertDialog.Builder(mContext)
                .setTitle( mContext.getString(R.string.delete_recurring_event_title,model.mTitle))
                .setIconAttribute(android.R.attr.alertDialogIcon)
                .setSingleChoiceItems(adapter, which, mDeleteListListener)
                .setPositiveButton(android.R.string.ok,
                    mDeleteRepeatingDialogListener)
                .setNegativeButton(android.R.string.cancel, null)
                .setOnDismissListener(mDismissListener)
                .show();

            // Disable the "Ok" button until the user selects which events
            // to delete.
            mAlertDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                .setEnabled(which != -1);
        }
    }

    private void deleteExceptionEvent() {
        long id = mModel.mId;

        // This exception is a real event whose ORIGINAL_INSTANCE_TIME
        // (which we didn't read because we don't need it)
        // is the start time of the instance it overrides: the start time
        // of this event may be different because the exception could be a
        // change of start time.
        // We change its status to Events.STATUS_CANCELED which makes it into
        // a deletion of the instance it overrides, with no replacement event.
        ContentValues values = new ContentValues();
        values.put(Events.STATUS, Events.STATUS_CANCELED);

        Uri uri = ContentUris.withAppendedId(
            CalendarContract.Events.CONTENT_URI, id);
        mService.startUpdate(null, this, uri, values,
            null, null);
    }

    private void deleteRepeatingEvent(int which) {
        String rRule = mModel.mRrule;
        boolean allDay = mModel.mAllDay;
        long dtstart = mModel.mEventStart;
        long id = mModel.mId; // mCursor.getInt(mEventIndexId);

        // See mDeleteNormalDialogListener for more info on this
        boolean isLocal = mModel.mSyncAccountType.equals(CalendarContract.ACCOUNT_TYPE_LOCAL);
        Uri deleteContentUri = isLocal ? CalendarRepository.asLocalCalendarSyncAdapter(mModel.mSyncAccountName, Events.CONTENT_URI) : Events.CONTENT_URI;

        switch (which) {
            case DELETE_SELECTED: {
                // If we are deleting the first event in the series, then
                // instead of creating a recurrence exception, we could
                // just change the start time of the recurrence.
                // if (dtstart == mInstanceStart) {
                // TODO not implemented yet
                // }

                // Create a recurrence exception by creating a new event
                // with Events.ORIGINAL_INSTANCE_TIME the start time of the
                // instance to delete, and with Events.STATUS set to
                // Events.STATUS_CANCELED so that we don't create a new event.
                // We never see this event when reading back from
                // the database: it's just a placeholder to prevent the
                // database from giving us the deleted instance.
                ContentValues values = new ContentValues();

                // The title might not be necessary, but it makes it easier
                // to find this entry in the database when there is a problem.
                values.put(Events.TITLE, mModel.mTitle);

                String timezone = mModel.mTimezoneStart;
                long calendarId = mModel.mCalendarId;
                values.put(Events.EVENT_TIMEZONE, timezone);
                values.put(Events.ALL_DAY, allDay ? 1 : 0);
                values.put(Events.ORIGINAL_ALL_DAY, allDay ? 1 : 0);
                values.put(Events.CALENDAR_ID, calendarId);
                values.put(Events.DTSTART, mInstanceStart);
                values.put(Events.DTEND, mInstanceEnd);
                values.put(Events.ORIGINAL_SYNC_ID, mSyncId);
                values.put(Events.ORIGINAL_ID, id);
                values.put(Events.ORIGINAL_INSTANCE_TIME, mInstanceStart);
                values.put(Events.STATUS, Events.STATUS_CANCELED);

                mService.startInsert(
                    null, this, Events.CONTENT_URI, values);
                break;
            }
            case DELETE_ALL_FOLLOWING: {
                // If we are deleting the first event in the series and all
                // following events, then just fall through to delete all.
                if (dtstart != mInstanceStart) {
                    Uri uri = ContentUris.withAppendedId(deleteContentUri, id);
                    mService.startDelete(null, this, uri,
                        null, null);
                    break;
                }
                // Modify the repeating event to end just before this event time
                EventRecurrence eventRecurrence = new EventRecurrence();
                eventRecurrence.parse(rRule);
                Time date = new Time();
                if (allDay) {
                    date.timezone = Time.TIMEZONE_UTC;
                }
                date.set(mInstanceStart);
                date.second--;
                date.normalize(false);

                // Google calendar seems to require the UNTIL string to be
                // in UTC.
                date.switchTimezone(Time.TIMEZONE_UTC);
                eventRecurrence.until = date.format2445();

                ContentValues values = new ContentValues();
                values.put(Events.DTSTART, dtstart);
                values.put(Events.RRULE, eventRecurrence.toString());
                Uri uri = ContentUris.withAppendedId
                    (CalendarContract.Events.CONTENT_URI, id);
                mService.startUpdate(null, this, uri, values,
                    null, null);                    break;
                //FALLTHRU
            }
            case DELETE_ALL: {
                // We just delete the whole event, so instances will
                // no longer be generated by the calendar provider.
                // Exception events also get removed.
                Uri uri = ContentUris.withAppendedId(deleteContentUri, id);
                mService.startDelete(null, this, uri,
                    null, null);
                break;
            }
        }
    }

    public void setDeleteNotificationListener(DeleteNotifyListener listener) {
        mDeleteStartedListener = listener;
    }

    private void deleteStarted() {
        if (mDeleteStartedListener != null) {
            mDeleteStartedListener.onDeleteStarted();
        }
    }

    public void setOnDismissListener(Dialog.OnDismissListener listener) {
        if (mAlertDialog != null) {
            mAlertDialog.setOnDismissListener(listener);
        }
        mDismissListener = listener;
    }

    public void dismissAlertDialog() {
        if (mAlertDialog != null) {
            mAlertDialog.dismiss();
        }
    }

    public interface DeleteNotifyListener {
        void onDeleteStarted();
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
        if (   (cursor != null)
            && cursor.moveToFirst())
        {
            CalendarEventModel model = new CalendarEventModel();
            EditEventHelper.setModelFromCursor(model, cursor);
            cursor.close();
            deleteAfterQuery(model);
        } else {
            Toast.makeText(mContext, R.string.delete_event_fail,
                    Toast.LENGTH_SHORT)
                .show();
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
        if (uri != null) {
            tidyup();
        } else {
            Toast.makeText(
                    mContext, R.string.delete_event_fail, Toast.LENGTH_SHORT)
                 .show();
        }
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
        if (result == 1) {
            tidyup();
        } else {
            Toast.makeText(mContext, R.string.delete_event_fail,
                    Toast.LENGTH_SHORT)
                 .show();
        }
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
        if (result == 1) {
            tidyup();
        } else {
            Toast.makeText(mContext, R.string.delete_event_fail,
                    Toast.LENGTH_SHORT)
                .show();
        }
    }

    /**
     * Called when an asynchronous ContentProviderOperation is
     * completed.
     *
     * @param cookie  the cookie object that's passed in from
     *                AsyncQueryService.startBatch().
     * @param results an array of results from the operations:
     *                the type of each result depends on the operation.
     */
    @Override
    public void onBatchDone(@Nullable Object cookie, ContentProviderResult[] results) {

    }
}
