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

package com.android.calendar.event;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.os.Environment;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.android.calendar.AbstractCalendarActivity;
import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarApplication;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.DeleteEventHelper;
import com.android.calendar.DynamicTheme;
import com.android.calendar.Utils;
import com.android.calendar.colorpicker.ColorPickerSwatch;
import com.android.calendar.colorpicker.HsvColorComparator;
import com.android.calendar.icalendar.IcalendarUtils;
import com.android.calendar.icalendar.VCalendar;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

import ws.xsoh.etar.BuildConfig;
import ws.xsoh.etar.R;

// This class handles event editing without using a fragment
public class EditEventActivity extends AbstractCalendarActivity
    implements CalendarController.ActionHandler,
    ColorPickerSwatch.OnColorSelectedListener,
    DeleteEventHelper.DeleteNotifyListener
{
    // for logging
    private static final String TAG = "EditEventActivity";

    // used in CalendarEventModel
    public static final String EXTRA_EVENT_REMINDERS = "reminders";

    // Apparently this Intent extra is never set
    public static final String EXTRA_READ_ONLY = "read_only";

    // InstanceState bundle keys
    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    private static final String BUNDLE_MODEL = "key_model";
    private static final String BUNDLE_EDIT_STATE = "key_edit_state";
    private static final String BUNDLE_READ_ONLY = "key_read_only";
    private static final String BUNDLE_EDIT_ON_LAUNCH = "key_edit_on_launch";
    private static final String BUNDLE_SHOW_COLOR_PALETTE =
        "show_color_palette";
    private static final String BUNDLE_DELETE_DIALOG_VISIBLE =
        "key_delete_dialog_visible";
    private static final String BUNDLE_COLORPICKER_DIALOG_VISIBLE =
        "key_colorpicker_dialog_visible";
    private static final boolean DEBUG = false;

    // Copy of "this" for use in nested classes
    private EditEventActivity mActivity;

    private AsyncModifyService mService;

    // Get event details from calendar provider
    private QueryHandler mHandler;
    /**
     * A bitfield of TOKEN_* to keep track which query hasn't been completed
     * yet. Once all queries have returned, the model can be applied to the
     * view.
     */
    private int mOutstandingQueries = TOKEN_UNINITIALIZED;
    private static final int TOKEN_EVENT = 1;
    private static final int TOKEN_ATTENDEES = 1 << 1;
    private static final int TOKEN_REMINDERS = 1 << 2;
    private static final int TOKEN_CALENDARS = 1 << 3;
    private static final int TOKEN_COLORS = 1 << 4;
    private static final int TOKEN_ALL =
        TOKEN_EVENT | TOKEN_ATTENDEES | TOKEN_REMINDERS
            | TOKEN_CALENDARS | TOKEN_COLORS;
    private static final int TOKEN_UNINITIALIZED = 1 << 31;

    // Various Dialogs, and flags to redisplay if screen rotated
    private AlertDialog mModifyDialog;
    private EventColorPickerDialog mColorPickerDialog;
    private boolean mColorPickerDialogVisible = false;
    private DeleteEventHelper mDeleteHelper;
    private boolean mDeleteDialogVisible = false;

    private boolean mShowColorPalette = false;

    private EditEventHelper mHelper;
    private CalendarEventModel mModel = null;
    private CalendarEventModel mOriginalModel;
    private EditEventView mView;  // Despite its name, does not extend "View"
    private int mModification = Utils.MODIFY_UNINITIALIZED;
    private ArrayList<CalendarEventModel.ReminderEntry> mReminders;
    private int mEventColor;
    private boolean mEventColorInitialized;
    private boolean mIsReadOnly;
    private boolean mIsPaused = true;
    private boolean mDismissOnResume = false;
    private InputMethodManager mInputMethodManager;
    private final DynamicTheme dynamicTheme = new DynamicTheme();

    class AsyncModifyService extends AsyncQueryService {
        public AsyncModifyService(Context context) {
            super(context);
        }

        /**
         * Called when an asynchronous insert is completed.
         *
         * @param token  the token to identify the query, passed in from
         *               {@link #startInsert}.
         * @param cookie the cookie object that's passed in from
         *               {@link #startInsert}.
         * @param uri    the URL of the newly created row
         *               null indicates failure.
         */
        @Override
        protected void onInsertComplete(int token, @Nullable Object cookie,
                                        Uri uri)
        {
        }

        /**
         * Called when an asynchronous update is completed.
         *
         * @param token  the token to identify the query, passed in from
         *               {@link #startUpdate}.
         * @param cookie the cookie object that's passed in from
         *               {@link #startUpdate}.
         * @param result the nimber of rows updated
         *               zero indicates failure.
         */
        @Override
        protected void onUpdateComplete(int token, @Nullable Object cookie,
                                        int result)
        {
        }

        /**
         * Called when an asynchronous delete is completed.
         *
         * @param token  the token to identify the query, passed in from
         *               {@link #startDelete}.
         * @param cookie the cookie object that's passed in from
         *               {@link #startDelete}.
         * @param result the number of rows deleted
         *               xero indicates failure
         */
        @Override
        protected void onDeleteComplete(int token, @Nullable Object cookie,
                                        int result)
        {
        }

        /**
         * Called when an asynchronous {@link ContentProviderOperation} is
         * completed.
         *
         * @param token   the token to identify the query, passed in from
         *                {@link #startBatch}.
         * @param cookie  the cookie object that's passed in from
         *                {@link #startBatch}.
         * @param results an array of results from the operations
         *                the type of each result depends on the operation
         */
        @Override
        protected void onBatchComplete(int token, @Nullable Object cookie,
                                       ContentProviderResult[] results)
        {
        }
    }

    @Override
    public synchronized AsyncQueryService getAsyncQueryService() {
        if (mService == null) {
            mService = new AsyncModifyService(this);
        }
        return mService;
    }

    class Done implements EditEventHelper.EditDoneRunnable {
        private int mCode = -1;

        boolean isEmptyNewEvent() {
            if (mOriginalModel != null) {
                // Not new
                return false;
            }

            if ((mModel.mOriginalStart != mModel.mStart)
                || (mModel.mOriginalEnd != mModel.mEnd)) {
                return false;
            }

            if (!mModel.mAttendeesList.isEmpty()) {
                return false;
            }

            return mModel.isEmpty();
        }

        @Override
        public void setDoneCode(int code) {
            mCode = code;
        }

        @Override
        public void run() {
            // We only want this to get called once, either because the user
            // pressed back/home or one of the buttons on screen
            if (mModification == Utils.MODIFY_UNINITIALIZED) {
                // If this is uninitialized the user hit back, the only
                // changeable item is response to default to all events.
                mModification = Utils.MODIFY_ALL;
            }

            if (((mCode & Utils.DONE_SAVE) != 0)
                && (mModel != null)
                && (EditEventHelper.canRespond(mModel)
                || EditEventHelper.canModifyEvent(mModel))
                && (!isEmptyNewEvent())
                && mModel.normalizeReminders()
                && mHelper.saveEvent(mModel, mOriginalModel, mModification)) {
                int stringResource;
                if (!mModel.mAttendeesList.isEmpty()) {
                    if (mModel.mId >= 0) {
                        stringResource = R.string.saving_event_with_guest;
                    } else {
                        stringResource = R.string.creating_event_with_guest;
                    }
                } else {
                    if (mModel.mId >= 0) {
                        stringResource = R.string.saving_event;
                    } else {
                        stringResource = R.string.creating_event;
                    }
                }
                Toast.makeText(mActivity, stringResource, Toast.LENGTH_SHORT).show();
            } else if (((mCode & Utils.DONE_SAVE) != 0)
                && (mModel != null)
                && isEmptyNewEvent()) {
                Toast.makeText(
                    mActivity, R.string.empty_event, Toast.LENGTH_SHORT).show();
            }

            if (((mCode & Utils.DONE_DELETE) != 0)
                && (mOriginalModel != null)
                && EditEventHelper.canModifyCalendar(mOriginalModel)) {
                assert mModel != null; // ignored in release build
                long begin = mModel.mStart;
                long end = mModel.mEnd;
                int which = -1;
                switch (mModification) {
                    case Utils.MODIFY_SELECTED:
                        which = DeleteEventHelper.DELETE_SELECTED;
                        break;
                    case Utils.MODIFY_ALL_FOLLOWING:
                        which = DeleteEventHelper.DELETE_ALL_FOLLOWING;
                        break;
                    case Utils.MODIFY_ALL:
                        which = DeleteEventHelper.DELETE_ALL;
                        break;
                }
                DeleteEventHelper deleteHelper = new DeleteEventHelper(
                    mActivity, mActivity, !mIsReadOnly /* exitWhenDone */);
                deleteHelper.delete(begin, end, mOriginalModel, which);
            }

            if ((mCode & Utils.DONE_EXIT) != 0) {
                // This will exit the edit event screen, should be called
                // when we want to return to the main calendar views
                if ((mCode & Utils.DONE_SAVE) != 0) {
                    if (mActivity != null) {
                        assert mModel != null; // ignored in release build
                        long start = mModel.mStart;
                        long end = mModel.mEnd;
                        if (mModel.mAllDay) {
                            // For allday events we want to go to the day in the
                            // user's current tz
                            String tz = Utils.getTimeZone(mActivity, null);
                            Time t = new Time(Time.TIMEZONE_UTC);
                            t.set(start);
                            t.timezone = tz;
                            start = t.toMillis(true);

                            t.timezone = Time.TIMEZONE_UTC;
                            t.set(end);
                            t.timezone = tz;
                            end = t.toMillis(true);
                        }
                        CalendarController.getInstance(mActivity).launchViewEvent
                            (-1, start, end, CalendarContract.Attendees.ATTENDEE_STATUS_NONE);
                    }
                }
                finish();
            }

            // Hide a software keyboard so that user won't see it
            // even after this Activity's disappearing.
            final View focusedView = mActivity.getCurrentFocus();
            if (focusedView != null) {
                mInputMethodManager.hideSoftInputFromWindow(
                    focusedView.getWindowToken(), 0);
            }
        }
    }

    private final Done mOnDone = new Done();
    private final View.OnClickListener mOnColorPickerClicked =
        new View.OnClickListener()
    {
        @Override
        public void onClick(View v) {
            int[] colors = mModel.getCalendarEventColors();
            if (mColorPickerDialog == null) {
                mColorPickerDialog = EventColorPickerDialog.newInstance(
                    mActivity, colors, mModel.getEventColor(),
                    mModel.getCalendarColor(), mView.mIsMultipane);
                mColorPickerDialog.setOnColorSelectedListener(mActivity);
                mColorPickerDialog.setOnDismissListener(createDeleteOnDismissListener());
            } else {
                mColorPickerDialog.setCalendarColor(mModel.getCalendarColor());
                mColorPickerDialog.setColors(colors, mModel.getEventColor());
            }
            mColorPickerDialog.show();
            mColorPickerDialogVisible = true;
        }
    };

    // Not currently used - share isn't implemented yet
    private enum ShareType {
        SDCARD,
        INTENT
    }

    @Override // There are no actions for this Activity
    public long getSupportedActionTypes() {
        return 0;
    }

    @Override
    public void handleAction(CalendarController.ActionInfo actionInfo) {
    }

    @Override
    public void eventsChanged() {
        // TODO Requery to see if event has changed
    }

    @Override
    public void onColorSelected(int color) {
        if ((!mModel.isEventColorInitialized())
            || (mModel.getEventColor() != color)) {
            mModel.setEventColor(color);
            mView.updateHeadlineColor(mModel, color);
        }
    }

    @Override
    public void onDeleteStarted() {
    }

    public final Runnable onDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (mActivity.mIsPaused) {
                mDismissOnResume = true;
            } else {
                finish();
            }
        }
    };

    private void startDeleteHelper() {
        mDeleteHelper = new DeleteEventHelper(
            mActivity, mActivity, !mIsReadOnly /* exitWhenDone */);
        mDeleteHelper.setDeleteNotificationListener(this);
        mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
        mDeleteHelper.delete(
            mModel.mStart, mModel.mEnd, mModel.mId, onDeleteRunnable);
    }

    /* The alleged "leak" here isn't actually a memory leak at all.
     * A real memory leak occurs when the memory is *never* reclaimed, or at
     * least not until the device is rebooted or a long running application
     * is closed.
     * This case is simply a delayed garbage collection: the Activity
     * will not be garbage-collected until the handler has run and exited.
     * For handlers which just encapsulate a background task
     * or which call back into the Activity
     * (which therefore needs to stay around) this is not a problem.
     */
    @SuppressLint("HandlerLeak")
    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        private void displayEditWhichDialog() {
            if (mModification == Utils.MODIFY_UNINITIALIZED) {
                final boolean notSynced = TextUtils.isEmpty(mModel.mSyncId);
                boolean isFirstEventInSeries = mModel.mIsFirstEventInSeries;
                int itemIndex = 0;
                CharSequence[] items;

                if (notSynced) {
                    // If this event has not been synced, then don't allow deleting
                    // or changing a single instance.
                    if (isFirstEventInSeries) {
                        // Still display the option so the user knows all events are
                        // changing
                        items = new CharSequence[1];
                    } else {
                        items = new CharSequence[2];
                    }
                } else {
                    if (isFirstEventInSeries) {
                        items = new CharSequence[2];
                    } else {
                        items = new CharSequence[3];
                    }
                    items[itemIndex++] = mActivity.getText(R.string.modify_event);
                }
                items[itemIndex++] = mActivity.getText(R.string.modify_all);

                // Do one more check to make sure this remains at the end of the list
                if (!isFirstEventInSeries) {
                    items[itemIndex] = mActivity.getText(R.string.modify_all_following);
                }

                // Display the modification dialog.
                if (mModifyDialog != null) {
                    mModifyDialog.dismiss();
                    mModifyDialog = null;
                }
                mModifyDialog = new AlertDialog.Builder(mActivity)
                    .setTitle(R.string.edit_event_label)
                    .setItems(items, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {
                                // Update this if we start allowing exceptions
                                // to unsynced events in the app
                                mModification = notSynced ? Utils.MODIFY_ALL
                                    : Utils.MODIFY_SELECTED;
                                if (mModification == Utils.MODIFY_SELECTED) {
                                    mModel.mOriginalSyncId = mModel.mSyncId;
                                    mModel.mOriginalId = mModel.mId;
                                }
                            } else if (which == 1) {
                                mModification = notSynced
                                    ? Utils.MODIFY_ALL_FOLLOWING : Utils.MODIFY_ALL;
                            } else if (which == 2) {
                                mModification = Utils.MODIFY_ALL_FOLLOWING;
                            }

                            mView.setModification(mModification);
                        }
                    }).show();

                mModifyDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        mActivity.finish();
                    }
                });
            }
        }

        private void setModelIfDone(int queryType) {
            synchronized (this) {
                mOutstandingQueries &= ~queryType;
                if (mOutstandingQueries == 0) {
                    if (mModification == Utils.MODIFY_UNINITIALIZED) {
                        if (!TextUtils.isEmpty(mModel.mRrule)) {
                            displayEditWhichDialog();
                        } else {
                            mModification = Utils.MODIFY_ALL;
                        }
                    }
                    mView.setModel(mModel);
                    mView.setModification(mModification);
                    if (mColorPickerDialog == null) {
                        mColorPickerDialog =
                            EventColorPickerDialog.newInstance(
                                mActivity, mModel.getCalendarEventColors(),
                                mModel.getEventColor(),
                                mModel.getCalendarColor(),
                                mView.mIsMultipane);
                        mColorPickerDialog.setOnColorSelectedListener(
                            mActivity);
                        mColorPickerDialog.setOnDismissListener(
                            createDeleteOnDismissListener());
                    }
                    mColorPickerDialog.setOnColorSelectedListener(mActivity);
                    if (mColorPickerDialogVisible) {
                        mColorPickerDialog.show();
                    }
                    if (mDeleteDialogVisible) {
                        startDeleteHelper();
                    }
                }
            }
        }

        // We don't override startQuery()
        // onQueryComplete() is responsible for closing
        // the cursor that is passed to it.
        @Override
        protected void onQueryComplete(
            int token, Object cookie, Cursor cursor) {
            // If the query didn't return a cursor for some reason return
            if (cursor == null) {
                return;
            }

            // If the Activity is finishing, then close the cursor.
            // Otherwise, use the new cursor in the adapter.
            if (mActivity == null || mActivity.isFinishing()) {
                cursor.close();
                return;
            }
            long eventId;
            switch (token) {
                case TOKEN_EVENT:
                    if (cursor.getCount() == 0) {
                        // The cursor is empty. This can happen if the event
                        // was deleted.
                        cursor.close();
                        mOnDone.setDoneCode(Utils.DONE_EXIT);
                        mOnDone.run();
                        return;
                    }
                    EditEventHelper.setModelFromCursor(mModel, cursor);
                    cursor.close();
                    mOriginalModel = new CalendarEventModel(mModel);
                    if (mModel.mId == mModel.mOriginalId) {
                        mModel.mIsFirstEventInSeries = true;
                        mModel.mOriginalStart = mModel.mStart;
                        mModel.mOriginalEnd = mModel.mEnd;
                    } else {
                        // We probably shouldn't set mModel.mOriginalStart
                        // or mModel.mOriginalStart here.
                        mModel.mIsFirstEventInSeries = false;
                    }
                    if (mEventColorInitialized) {
                        mModel.setEventColor(mEventColor);
                    }
                    eventId = mModel.mId;

                    // TOKEN_ATTENDEES
                    if (mModel.mHasAttendeeData && eventId != -1) {
                        Uri attUri = CalendarContract.Attendees.CONTENT_URI;
                        String[] whereArgs = {
                            Long.toString(eventId)
                        };
                        mHandler.startQuery(TOKEN_ATTENDEES, null, attUri,
                            EditEventHelper.ATTENDEES_PROJECTION,
                            EditEventHelper.ATTENDEES_WHERE /* selection */,
                            whereArgs /* selection args */,
                            null /* sort order */);
                    } else {
                        setModelIfDone(TOKEN_ATTENDEES);
                    }

                    // TOKEN_REMINDERS
                    if (mModel.mHasAlarm) {
                        Uri rUri = CalendarContract.Reminders.CONTENT_URI;
                        String[] remArgs = {
                            Long.toString(eventId)
                        };
                        mHandler.startQuery(TOKEN_REMINDERS, null, rUri,
                            EditEventHelper.REMINDERS_PROJECTION,
                            EditEventHelper.REMINDERS_WHERE /* selection */,
                            remArgs /* selection args */,
                            null /* sort order */);
                    } else {
                        if (mReminders == null) {
                            // mReminders should not be null.
                            mReminders = new ArrayList<>();
                        } else {
                            Collections.sort(mReminders);
                        }
                        mOriginalModel.mReminders = mReminders;
                        mModel.mReminders = mModel.cloneReminders(mReminders);
                        setModelIfDone(TOKEN_REMINDERS);
                    }

                    // TOKEN_CALENDARS
                    String[] selArgs = {
                        Long.toString(mModel.mCalendarId)
                    };
                    mHandler.startQuery(
                        TOKEN_CALENDARS, null, CalendarContract.Calendars.CONTENT_URI,
                        EditEventHelper.CALENDARS_PROJECTION,
                        EditEventHelper.CALENDARS_WHERE,
                        selArgs /* selection args */, null /* sort order */);

                    // TOKEN_COLORS
                    mHandler.startQuery(TOKEN_COLORS, null, CalendarContract.Colors.CONTENT_URI,
                        EditEventHelper.COLORS_PROJECTION,
                        CalendarContract.Colors.COLOR_TYPE + "="
                            + CalendarContract.Colors.TYPE_EVENT, null, null);

                    setModelIfDone(TOKEN_EVENT);
                    break;
                case TOKEN_ATTENDEES:
                    try {
                        while (cursor.moveToNext()) {
                            String name =
                                cursor.getString(EditEventHelper.ATTENDEES_INDEX_NAME);
                            String email =
                                cursor.getString(EditEventHelper.ATTENDEES_INDEX_EMAIL);
                            int relationship = cursor
                                .getInt(EditEventHelper.ATTENDEES_INDEX_RELATIONSHIP);
                            int status =
                                cursor.getInt(EditEventHelper.ATTENDEES_INDEX_STATUS);
                            int type =
                                cursor.getInt(EditEventHelper.ATTENDEES_INDEX_TYPE);
                            String identity =
                                cursor.getString(EditEventHelper.ATTENDEES_INDEX_IDENTITY);
                            String idNamespace =
                                cursor.getString(
                                    EditEventHelper.ATTENDEES_INDEX_ID_NAMESPACE);
                            if (relationship == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER) {
                                if (email != null) {
                                    mModel.mOrganizer = email;
                                    mModel.mIsOrganizer =
                                        mModel.mOwnerAccount
                                            .equalsIgnoreCase(email);
                                    mOriginalModel.mOrganizer = email;
                                    mOriginalModel.mIsOrganizer =
                                        mOriginalModel.mOwnerAccount
                                            .equalsIgnoreCase(email);
                                }

                                if (TextUtils.isEmpty(name)) {
                                    mModel.mOrganizerDisplayName =
                                        mModel.mOrganizer;
                                    mOriginalModel.mOrganizerDisplayName =
                                        mOriginalModel.mOrganizer;
                                } else {
                                    mModel.mOrganizerDisplayName = name;
                                    mOriginalModel.mOrganizerDisplayName = name;
                                }
                            }

                            if (email != null) {
                                if ((mModel.mOwnerAccount != null)
                                    && mModel.mOwnerAccount.equalsIgnoreCase(email)) {
                                    int attendeeId = cursor.getInt(
                                        EditEventHelper.ATTENDEES_INDEX_ID);
                                    mModel.mOwnerAttendeeId = attendeeId;
                                    mModel.mSelfAttendeeStatus = status;
                                    mOriginalModel.mOwnerAttendeeId = attendeeId;
                                    mOriginalModel.mSelfAttendeeStatus = status;
                                    continue;
                                }
                            }
                            CalendarEventModel.Attendee attendee = new CalendarEventModel.Attendee(
                                name, email, status, type, identity, idNamespace);
                            mModel.addAttendee(attendee);
                            mOriginalModel.addAttendee(attendee);
                        }
                    } finally {
                        cursor.close();
                    }

                    setModelIfDone(TOKEN_ATTENDEES);
                    break;
                case TOKEN_REMINDERS:
                    try {
                        // Add all reminders to the models
                        while (cursor.moveToNext()) {
                            int minutes = cursor.getInt(
                                EditEventHelper.REMINDERS_INDEX_MINUTES);
                            int method = cursor.getInt(
                                EditEventHelper.REMINDERS_INDEX_METHOD);
                            CalendarEventModel.ReminderEntry re = CalendarEventModel.ReminderEntry.valueOf(minutes, method);
                            mModel.mReminders.add(re);
                            mOriginalModel.mReminders.add(re);
                        }

                        // Sort appropriately for display
                        Collections.sort(mModel.mReminders);
                        Collections.sort(mOriginalModel.mReminders);
                    } finally {
                        cursor.close();
                    }

                    setModelIfDone(TOKEN_REMINDERS);
                    break;
                case TOKEN_CALENDARS:
                    try {
                        if (mModel.mId == -1) {
                            // Populate Calendar spinner only if no event id is set.
                            MatrixCursor matrixCursor =
                                Utils.matrixCursorFromCursor(cursor);
                            if (DEBUG) {
                                Log.d(TAG, "onQueryComplete: setting cursor with "
                                    + matrixCursor.getCount() + " calendars");
                            }
                            mView.setCalendarsCursor(
                                matrixCursor, mModel.mCalendarId);
                        } else {
                            // Populate model for an existing event
                            EditEventHelper.setModelFromCalendarCursor(
                                mModel, cursor);
                            EditEventHelper.setModelFromCalendarCursor(
                                mOriginalModel, cursor);
                        }
                    } finally {
                        cursor.close();
                    }
                    setModelIfDone(TOKEN_CALENDARS);
                    break;
                case TOKEN_COLORS:
                    if (cursor.moveToFirst()) {
                        EventColorCache cache = new EventColorCache();
                        do {
                            String colorKey =
                                cursor.getString(EditEventHelper.COLORS_INDEX_COLOR_KEY);
                            int rawColor =
                                cursor.getInt(EditEventHelper.COLORS_INDEX_COLOR);
                            int displayColor = Utils.getDisplayColorFromColor(rawColor);
                            String accountName = cursor
                                .getString(EditEventHelper.COLORS_INDEX_ACCOUNT_NAME);
                            String accountType = cursor
                                .getString(EditEventHelper.COLORS_INDEX_ACCOUNT_TYPE);
                            cache.insertColor(accountName, accountType,
                                displayColor, colorKey);
                        } while (cursor.moveToNext());
                        cache.sortPalettes(new HsvColorComparator());

                        mModel.mEventColorCache = cache;
                        mView.mColorPickerNewEvent.setOnClickListener(
                            mOnColorPickerClicked);
                        mView.mColorPickerExistingEvent.setOnClickListener(
                            mOnColorPickerClicked);
                    }
                    cursor.close();

                    // If the account name/type is null, the calendar event colors cannot
                    // be determined, so take the default/savedInstanceState value.
                    if ((mModel.mCalendarAccountName == null)
                        || (mModel.mCalendarAccountType == null)) {
                        mView.setColorPickerButtonStates(mShowColorPalette);
                    } else {
                        mView.setColorPickerButtonStates(mModel.getCalendarEventColors());
                    }

                    setModelIfDone(TOKEN_COLORS);
                    break;
                default:
                    cursor.close();
                    break;
            }
        }
    }

    /* Returns true if we don't have @code permission.
     *
     */
    boolean noPermission(String permission) {
        return (Build.VERSION.SDK_INT >= 23) // anifest permissions not granted
            && (ContextCompat.checkSelfPermission(this, permission)
                != PackageManager.PERMISSION_GRANTED);
    }

    private void StartQuery() {
        if (mModel.mId >= 0) {
            mModel.mCalendarAccessLevel = CalendarContract.Calendars.CAL_ACCESS_NONE;
            mOutstandingQueries = TOKEN_ALL;
            Uri uri = ContentUris.withAppendedId(
                CalendarContract.Events.CONTENT_URI, mModel.mId);
            if (DEBUG) {
                Log.d(TAG, "startQuery: uri for event is "
                    + uri);
            }
            mHandler.startQuery(
                TOKEN_EVENT, null, uri,
                EditEventHelper.EVENT_PROJECTION,
                null, null, null);
        } else {
            mOutstandingQueries = TOKEN_CALENDARS | TOKEN_COLORS;
            if (DEBUG) {
                Log.d(TAG, "startQuery: Editing a new event.");
            }

            // Start queries in the background
            // to read the lists of calendars and colors
            mHandler.startQuery(TOKEN_CALENDARS, null,
                CalendarContract.Calendars.CONTENT_URI,
                EditEventHelper.CALENDARS_PROJECTION,
                EditEventHelper.CALENDARS_WHERE_WRITEABLE_VISIBLE,
                null /* selection args */,
                null /* sort order */);
            // mHandler.onQueryComplete will be called
            // when a query completes.

            mHandler.startQuery(TOKEN_COLORS, null,
                CalendarContract.Colors.CONTENT_URI,
                EditEventHelper.COLORS_PROJECTION,
                CalendarContract.Colors.COLOR_TYPE + "=" + CalendarContract.Colors.TYPE_EVENT,
                null, null);
            // mHandler.onQueryComplete will be called
            // when a query completes.

            mModification = Utils.MODIFY_ALL;
            mView.setModification(mModification);
        }
    }

    public void onRequestPermissionsResult(
        int requestCode, @NonNull String[] permissions,
        @NonNull int[] grantResults)
    {
        for (int i = 0; i < permissions.length; ++i) {
            if ((permissions[i].equals(Manifest.permission.READ_CALENDAR))
                && (grantResults[i] == PackageManager.PERMISSION_GRANTED)) {
                StartQuery();
                return;
            }
        }
        Toast.makeText(this, R.string.calendar_permission_not_granted,
            Toast.LENGTH_LONG).show();
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mActivity = this;

        dynamicTheme.onCreate(this);
        ArrayList<String> permissions = new ArrayList<>(2);
        if (noPermission(Manifest.permission.READ_CONTACTS)) {
            permissions.add(Manifest.permission.READ_CONTACTS);
        }
        boolean needCalendarPermission =
            noPermission(Manifest.permission.READ_CALENDAR);
        if (needCalendarPermission) {
            permissions.add(Manifest.permission.READ_CALENDAR);
        }
        if (permissions.size() > 0) {
            ActivityCompat.requestPermissions(
                this, permissions.toArray(new String[0]), 0);
        }
        Intent mIntent = getIntent();
        setContentView(R.layout.simple_frame_layout_material);
        findViewById(R.id.body_frame);
        mIsReadOnly = mIntent.getBooleanExtra(EXTRA_READ_ONLY,
            (savedInstanceState != null)
                && savedInstanceState.getBoolean(
                BUNDLE_READ_ONLY, false));
        View v = getLayoutInflater().inflate(
            mIsReadOnly ? R.layout.edit_event_single_column
                : R.layout.edit_event, null);
        ((ViewGroup) findViewById(R.id.body_frame)).addView(v);
        mView = new EditEventView(this, v, mOnDone);

        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        mHelper = new EditEventHelper(this);
        mHandler = new QueryHandler(getContentResolver());
        mInputMethodManager = (InputMethodManager)
            getSystemService(Context.INPUT_METHOD_SERVICE);

        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BUNDLE_MODEL)) {
                mModel = (CalendarEventModel)
                    savedInstanceState.getSerializable(BUNDLE_MODEL);
                mEventColorInitialized = mModel.mEventColorInitialized;
                if (mModel.mEventColorInitialized) {
                    mEventColor = mModel.mEventColor;
                }
            }
            if (savedInstanceState.containsKey(BUNDLE_EDIT_STATE)) {
                mModification = savedInstanceState.getInt(BUNDLE_EDIT_STATE);
            }
            if (savedInstanceState.containsKey(BUNDLE_SHOW_COLOR_PALETTE)) {
                mShowColorPalette =
                    savedInstanceState.getBoolean(BUNDLE_SHOW_COLOR_PALETTE);
            }
            mDeleteDialogVisible =
                savedInstanceState.getBoolean(
                    BUNDLE_DELETE_DIALOG_VISIBLE, false);
            mColorPickerDialogVisible =
                savedInstanceState.getBoolean(
                    BUNDLE_COLORPICKER_DIALOG_VISIBLE, false);
        } else {
            synchronized (CalendarApplication.mEvents) {
                try {
                    mModel = CalendarApplication.mEvents.remove(0);
                    // FIXME check if this can ever happen
                    if (mModel.mStart <= 0) {
                        // use a default value instead
                        long now = System.currentTimeMillis();
                        Time defaultStart = new Time();
                        defaultStart.set(now);
                        defaultStart.second = 0;
                        defaultStart.minute = 30;
                        long defaultStartMillis =
                            defaultStart.toMillis(false);
                        if (now < defaultStartMillis) {
                            mModel.mStart = defaultStartMillis;
                        } else {
                            mModel.mStart =
                                defaultStartMillis + 30 * DateUtils.MINUTE_IN_MILLIS;
                        }
                    }
                    if (mModel.mEnd < mModel.mStart) {
                        // use a default value instead
                        mModel.mEnd = mModel.mStart
                            + Utils.getDefaultEventDurationInMillis(this);
                    }
                } catch (IndexOutOfBoundsException ignore) {
                }
            }
        }
        if (mModel == null) {
            mModel = new CalendarEventModel(this, mIntent);
            if (mModel.mId < 0) {
                mModel.mId = (savedInstanceState == null)
                    ? -1 : savedInstanceState.getLong(
                    BUNDLE_KEY_EVENT_ID, -1);
            }
        }
        if (!needCalendarPermission) {
            StartQuery();
        } // Otherwise onRequestPermissionsResult() will start it
    }

    @Override
    protected void onResume() {
        super.onResume();
        mIsPaused = false;
        if (mDismissOnResume) {
            mHandler.post(onDeleteRunnable);
        }
        // Display the "delete confirmation" dialog if needed
        if (mDeleteDialogVisible) {
            startDeleteHelper();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.edit_event_title_bar, menu);
        if (menu instanceof MenuBuilder) {
            MenuBuilder m = (MenuBuilder) menu;
            m.setOptionalIconsVisible(true);
        }
        return true;
    }

    /**
     * Prepare the Screen's standard options menu to be displayed.  This is
     * called right before the menu is shown, every time it is shown.  You can
     * use this method to efficiently enable/disable items or otherwise
     * dynamically modify the contents.  See
     * for more information.
     *
     * @param menu The options menu as last shown or first initialized by
     *             onCreateOptionsMenu().
     * @see #onCreateOptionsMenu
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (   EditEventHelper.canModifyEvent(mModel)
            || EditEventHelper.canRespond(mModel))
        {
            MenuItem m = menu.findItem(R.id.action_done);
            m.setEnabled(true);
            m.setVisible(true);
            m = menu.findItem(R.id.action_done_menu);
            m.setEnabled(true);
            m.setVisible(true);
        }
        if (mModel.mId < 0) {
            MenuItem m = menu.findItem(R.id.info_action_delete);
            m.setEnabled(false);
            m.setVisible(false);
            m = menu.findItem(R.id.info_action_delete_menu);
            m.setEnabled(false);
            m.setVisible(false);
        }
        return true;
    }

    @SuppressLint("SetWorldReadable")
    private void shareEvent(ShareType type) {
        // Create the respective ICalendar objects from the event info
        VCalendar calendar = new VCalendar();
        CalendarApplication.mEvents.add(mModel);
        // Create and share ics file
        boolean isShareSuccessful = false;
        try {
            // Event title serves as the file name prefix
            String filePrefix = mModel.mTitle;
            if (filePrefix == null || filePrefix.length() < 3) {
                // Default to a generic filename if event title doesn't qualify
                // Prefix length constraint is imposed by File#createTempFile
                filePrefix = "invite";
            }

            filePrefix = filePrefix.replaceAll("\\W+", " ");

            if (!filePrefix.endsWith(" ")) {
                filePrefix += " ";
            }

            File dir;
            if (type == ShareType.SDCARD) {
                dir = new File(
                    Environment.getExternalStorageDirectory(), "CalendarEvents");
                if (!dir.exists()) {
                    dir.mkdir();
                }
            } else {
                dir = mActivity.getExternalCacheDir();
            }

            File inviteFile = IcalendarUtils.createTempFile(filePrefix, ".ics",
                dir);

            if (IcalendarUtils.writeCalendarToFile(calendar, inviteFile)) {
                if (type == ShareType.INTENT) {
                    // Set world-readable
                    inviteFile.setReadable(true, false);
                    Uri icsFile = FileProvider.getUriForFile(mActivity,
                        BuildConfig.APPLICATION_ID + ".provider", inviteFile);
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, icsFile);
                    // The ics file is sent as an extra, the receiving application
                    // decides whether to parse the file to extract calendar events
                    // or treat it as a regular file
                    shareIntent.setType("application/octet-stream");

                    Intent chooserIntent = Intent.createChooser(shareIntent,
                        getResources().getString(R.string.cal_share_intent_title));

                    // The MMS app only responds to "text/x-vcalendar" so we create a
                    // chooser intent that includes the targeted mms intent + any
                    // that respond to the above general
                    // purpose "application/octet-stream" intent.
                    File vcsInviteFile = File.createTempFile(filePrefix, ".vcs",
                        mActivity.getExternalCacheDir());

                    // For now, we are duplicating ics file and using that as the vcs file
                    // TODO: revisit above
                    if (IcalendarUtils.copyFile(inviteFile, vcsInviteFile)) {
                        Uri vcsFile = FileProvider.getUriForFile(mActivity,
                            BuildConfig.APPLICATION_ID
                                + ".provider", vcsInviteFile);
                        Intent mmsShareIntent = new Intent();
                        mmsShareIntent.setAction(Intent.ACTION_SEND);
                        mmsShareIntent.setPackage("com.android.mms");
                        mmsShareIntent.putExtra(Intent.EXTRA_STREAM, vcsFile);
                        mmsShareIntent.setType("text/x-vcalendar");
                        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                            new Intent[]{mmsShareIntent});
                    }
                    startActivity(chooserIntent);
                } else {
                    String msg = getString(R.string.cal_export_succ_msg);
                    Toast.makeText(mActivity, String.format(msg, inviteFile),
                        Toast.LENGTH_SHORT).show();
                }
                isShareSuccessful = true;

            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!isShareSuccessful) {
            Log.e(TAG, "Couldn't generate ics file");
            Toast.makeText(mActivity,
                R.string.error_generating_ics, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveReminders() {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>(3);
        boolean changed = EditEventHelper.saveReminders(
            ops, mModel.mId, mModel.mReminders,
            mOriginalModel.mReminders, false /* no force save */);

        if (!changed) {
            return;
        }

        AsyncQueryService service = new AsyncQueryService(mActivity);
        service.startBatch(
            0, null, CalendarContract.Calendars.CONTENT_URI.getAuthority(), ops, 0);
        // Update the "hasAlarm" field for the event
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, mModel.mId);
        int len = mModel.mReminders.size();
        boolean hasAlarm = len > 0;
        if (hasAlarm != mOriginalModel.mHasAlarm) {
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.HAS_ALARM, hasAlarm ? 1 : 0);
            service.startUpdate(
                0, null, uri, values,
                null, null, 0);
        }

        Toast.makeText(mActivity, R.string.saving_event, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        long itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            Utils.returnToCalendarHome(this);
            return true;
        }
        if (   (itemId == R.id.action_cancel)
            || (itemId == R.id.action_cancel_menu))
        {
            mOnDone.setDoneCode(Utils.DONE_REVERT);
            mOnDone.run();
            return true;
        } else if (   (itemId == R.id.action_done)
            || (itemId == R.id.action_done_menu)
        ) {
            if (   EditEventHelper.canModifyEvent(mModel)
                || EditEventHelper.canRespond(mModel))
            {
                if ((mView != null) && mView.prepareForSave()) {
                    if (mModification == Utils.MODIFY_UNINITIALIZED) {
                        mModification = Utils.MODIFY_ALL;
                    }
                    mOnDone.setDoneCode(Utils.DONE_SAVE | Utils.DONE_EXIT);
                    mOnDone.run();
                } else {
                    mOnDone.setDoneCode(Utils.DONE_REVERT);
                    mOnDone.run();
                }
                return true;
            } else if (   EditEventHelper.canAddReminders(mModel)
                       && (mModel.mId != -1)
                       && (mOriginalModel != null)
                       && mView.prepareForSave())
            {
                saveReminders();
                mOnDone.setDoneCode(Utils.DONE_EXIT);
                mOnDone.run();
                return true;
            } else {
                mOnDone.setDoneCode(Utils.DONE_REVERT);
                mOnDone.run();
                return true;
            }
        } else if (   (itemId == R.id.info_action_delete)
            || (itemId == R.id.info_action_delete_menu))
        {
            startDeleteHelper();
            return true;
        } else if (   (itemId == R.id.info_action_export)
            || (itemId == R.id.info_action_export_menu))
        {
            shareEvent(ShareType.SDCARD);
            return true;
        } else if (   (itemId == R.id.info_action_share_event)
            || (itemId == R.id.info_action_share_event_menu))
        {
            shareEvent(ShareType.INTENT);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsPaused = true;
        // Remove event deletion alert box since it is being rebuilt in
        // OnResume. This is done to get the same behavior on OnResume since the
        // AlertDialog is gone on rotation but not if you press the HOME key.
        if (mDeleteDialogVisible && mDeleteHelper != null) {
            mDeleteHelper.dismissAlertDialog();
            mDeleteHelper = null;
        }
        if (mColorPickerDialogVisible && mColorPickerDialog != null) {
            mColorPickerDialog.dismiss();
            mColorPickerDialog = null;
        }
    }

    private Dialog.OnDismissListener createDeleteOnDismissListener() {
        return new Dialog.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                // Since OnPause will force the dialog to dismiss , do
                // not change the dialog status
                if (!mIsPaused) {
                    mDeleteDialogVisible = false;
                    mColorPickerDialogVisible = false;
                }
            }
        };
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(BUNDLE_READ_ONLY, mIsReadOnly);
        outState.putSerializable(BUNDLE_MODEL, mModel);
        outState.putInt(BUNDLE_EDIT_STATE, mModification);
        outState.putBoolean(BUNDLE_SHOW_COLOR_PALETTE,
            mView.isColorPaletteVisible());
        outState.putBoolean(BUNDLE_DELETE_DIALOG_VISIBLE,
            mDeleteDialogVisible);
        outState.putBoolean(BUNDLE_COLORPICKER_DIALOG_VISIBLE,
            mColorPickerDialogVisible);
        outState.putLong(BUNDLE_KEY_EVENT_ID, mModel.mId);
    }

    @Override
    public void onDestroy() {
        if (mView != null) {
            mView.setModel(null);
        }
        if (mModifyDialog != null) {
            mModifyDialog.dismiss();
            mModifyDialog = null;
        }
        super.onDestroy();
    }
}
