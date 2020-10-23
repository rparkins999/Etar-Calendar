/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Modifications from the original version Copyright (C) Richard Parkins 2020
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
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarApplication;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventHandler;
import com.android.calendar.CalendarController.ActionInfo;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.DeleteEventHelper;
import com.android.calendar.Utils;
import com.android.calendar.icalendar.IcalendarUtils;
import com.android.calendar.icalendar.VCalendar;
import com.android.colorpicker.ColorPickerSwatch.OnColorSelectedListener;
import com.android.colorpicker.HsvColorComparator;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;

import ws.xsoh.etar.BuildConfig;
import ws.xsoh.etar.R;

public class EditEventFragment extends DialogFragment implements EventHandler, OnColorSelectedListener, DeleteEventHelper.DeleteNotifyListener {
    private static final String TAG = "EditEventActivity";
    private static final String COLOR_PICKER_DIALOG_TAG = "ColorPickerDialog";

    private static final int REQUEST_CODE_COLOR_PICKER = 0;

    private static final String BUNDLE_KEY_MODEL = "key_model";
    private static final String BUNDLE_KEY_EDIT_STATE = "key_edit_state";
    private static final String BUNDLE_KEY_EVENT = "key_event";
    private static final String BUNDLE_KEY_READ_ONLY = "key_read_only";
    private static final String BUNDLE_KEY_EDIT_ON_LAUNCH = "key_edit_on_launch";
    private static final String BUNDLE_KEY_SHOW_COLOR_PALETTE = "show_color_palette";
    private static final String BUNDLE_KEY_DELETE_DIALOG_VISIBLE =
        "key_delete_dialog_visible";

    private static final String BUNDLE_KEY_DATE_BUTTON_CLICKED = "date_button_clicked";

    private static final boolean DEBUG = false;

    private static final int TOKEN_EVENT = 1;
    private static final int TOKEN_ATTENDEES = 1 << 1;
    private static final int TOKEN_REMINDERS = 1 << 2;
    private static final int TOKEN_CALENDARS = 1 << 3;
    private static final int TOKEN_COLORS = 1 << 4;

    private static final int TOKEN_ALL =
        TOKEN_EVENT | TOKEN_ATTENDEES | TOKEN_REMINDERS | TOKEN_CALENDARS | TOKEN_COLORS;
    private static final int TOKEN_UNINITIALIZED = 1 << 31;
    /**
     * A bitfield of TOKEN_* to keep track which query hasn't been completed
     * yet. Once all queries have returned, the model can be applied to the
     * view.
     */
    private int mOutstandingQueries = TOKEN_UNINITIALIZED;
    private final ActionInfo mActionInfo;
    public boolean mShowModifyDialogOnLaunch = false;
    private EditEventHelper mHelper;
    private CalendarEventModel mModel;
    private CalendarEventModel mOriginalModel;
    private EditEventView mView;
    private QueryHandler mHandler;
    private int mModification = Utils.MODIFY_UNINITIALIZED;
    private AlertDialog mModifyDialog;
    private EventBundle mEventBundle;
    private ArrayList<ReminderEntry> mReminders;
    private int mEventColor;
    private boolean mEventColorInitialized = false;
    private EventColorPickerDialog mColorPickerDialog;
    private AppCompatActivity mActivity;
    private boolean mSaveOnDetach = true;
    private boolean mIsReadOnly = false;
    private boolean mShowColorPalette = false;
    private DeleteEventHelper mDeleteHelper;
    private boolean mIsPaused = true;
    private boolean mDismissOnResume = false;
    private boolean mDeleteDialogVisible = false;
    private InputMethodManager mInputMethodManager;
    class Done implements EditEventHelper.EditDoneRunnable {
        private int mCode = -1;

        @Override
        public void setDoneCode(int code) {
            mCode = code;
        }

        @Override
        public void run() {
            // We only want this to get called once, either because the user
            // pressed back/home or one of the buttons on screen
            mSaveOnDetach = false;
            if (mModification == Utils.MODIFY_UNINITIALIZED) {
                // If this is uninitialized the user hit back, the only
                // changeable item is response to default to all events.
                mModification = Utils.MODIFY_ALL;
            }

            if (   ((mCode & Utils.DONE_SAVE) != 0)
                && (mModel != null)
                && (   EditEventHelper.canRespond(mModel)
                    || EditEventHelper.canModifyEvent(mModel))
                && (!isEmptyNewEvent())
                && mModel.normalizeReminders()
                && mHelper.saveEvent(mModel, mOriginalModel, mModification))
            {
                int stringResource;
                if (!mModel.mAttendeesList.isEmpty()) {
                    if (mModel.mUri != null) {
                        stringResource = R.string.saving_event_with_guest;
                    } else {
                        stringResource = R.string.creating_event_with_guest;
                    }
                } else {
                    if (mModel.mUri != null) {
                        stringResource = R.string.saving_event;
                    } else {
                        stringResource = R.string.creating_event;
                        // FIXME arrange to select the event
                    }
                }
                Toast.makeText(mActivity, stringResource, Toast.LENGTH_SHORT).show();
            } else if (   ((mCode & Utils.DONE_SAVE) != 0)
                       && (mModel != null)
                       && isEmptyNewEvent())
            {
                Toast.makeText(
                    mActivity, R.string.empty_event, Toast.LENGTH_SHORT).show();
            }

            if (   ((mCode & Utils.DONE_DELETE) != 0)
                && (mOriginalModel != null)
                && EditEventHelper.canModifyCalendar(mOriginalModel))
            {
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
                            (-1, start, end, Attendees.ATTENDEE_STATUS_NONE);
                    }
                }
                Activity a = EditEventFragment.this.getActivity();
                if (a != null) {
                    a.finish();
                }
            }

            // Hide a software keyboard so that user won't see it
            // even after this Fragment's disappearing.
            final View focusedView = mActivity.getCurrentFocus();
            if (focusedView != null) {
                mInputMethodManager.hideSoftInputFromWindow(
                    focusedView.getWindowToken(), 0);
            }
        }
    }
    private final Done mOnDone = new Done();
    private final View.OnClickListener mActionBarListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            onActionBarItemSelected(v.getId());
        }
    };
    private View.OnClickListener mOnColorPickerClicked = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int[] colors = mModel.getCalendarEventColors();
            if (mColorPickerDialog == null) {
                mColorPickerDialog = EventColorPickerDialog.newInstance(
                    colors, mModel.getEventColor(),
                    mModel.getCalendarColor(), mView.mIsMultipane);
                mColorPickerDialog.setOnColorSelectedListener(EditEventFragment.this);
            } else {
                mColorPickerDialog.setCalendarColor(mModel.getCalendarColor());
                mColorPickerDialog.setColors(colors, mModel.getEventColor());
            }
            final FragmentManager fragmentManager = getFragmentManager();
            fragmentManager.executePendingTransactions();
            if (!mColorPickerDialog.isAdded()) {
                mColorPickerDialog.show(fragmentManager, COLOR_PICKER_DIALOG_TAG);
            }
        }
    };

    private enum ShareType {
        SDCARD,
        INTENT
    }

    public long getEventId() {
        return mModel.mId;
    }

    public long getStartMillis() {
        return mModel.mStart;
    }

    public long getEndMillis() {
        return mModel.mEnd;
    }

    public EditEventFragment() {
        this(null, null, false, -1,
            false, null);
    }

    @SuppressLint("ValidFragment")
    public EditEventFragment(ActionInfo actionInfo, ArrayList<ReminderEntry> reminders,
                             boolean eventColorInitialized, int eventColor,
                             boolean readOnly, Intent intent) {
        mActionInfo = actionInfo;
        mIsReadOnly = readOnly;
        mReminders = reminders;
        mEventColorInitialized = eventColorInitialized;
        if (eventColorInitialized) {
            mEventColor = eventColor;
        }
        setHasOptionsMenu(true);
    }

    // This gets called immediately after creating the fragment
    public void setModel(CalendarEventModel model)
    {
        mModel = model;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = (AppCompatActivity) activity;
        mHelper = new EditEventHelper(activity, null);
        mHandler = new QueryHandler(activity.getContentResolver());
        mInputMethodManager = (InputMethodManager)
            activity.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    // uses mActivity, so must be called after onAttach()
    boolean havePermission(String permission) {
        return (Build.VERSION.SDK_INT < 23) // All manifest permissions granted
            || (ContextCompat.checkSelfPermission( mActivity, permission)
                == PackageManager.PERMISSION_GRANTED);
    }

    // This is called after onAttach().
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!havePermission(Manifest.permission.READ_CONTACTS)) {
            ActivityCompat.requestPermissions(
                mActivity,
                new String[] { Manifest.permission.READ_CONTACTS }, 0);
        }
    }

    // This is called after onCreate().
    @SuppressLint("InflateParams")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(BUNDLE_KEY_MODEL)) {
                mModel = (CalendarEventModel)
                    savedInstanceState.getSerializable(BUNDLE_KEY_MODEL);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_EDIT_STATE)) {
                mModification = savedInstanceState.getInt(BUNDLE_KEY_EDIT_STATE);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_EDIT_ON_LAUNCH)) {
                mShowModifyDialogOnLaunch = savedInstanceState
                    .getBoolean(BUNDLE_KEY_EDIT_ON_LAUNCH);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_EVENT)) {
                mEventBundle =
                    (EventBundle) savedInstanceState.getSerializable(BUNDLE_KEY_EVENT);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_READ_ONLY)) {
                mIsReadOnly = savedInstanceState.getBoolean(BUNDLE_KEY_READ_ONLY);
            }
            if (savedInstanceState.containsKey(BUNDLE_KEY_SHOW_COLOR_PALETTE)) {
                mShowColorPalette =
                    savedInstanceState.getBoolean(BUNDLE_KEY_SHOW_COLOR_PALETTE);
            }
            mDeleteDialogVisible =
                savedInstanceState.getBoolean(
                    BUNDLE_KEY_DELETE_DIALOG_VISIBLE,false);
        }
        View view;
        // "InflateParams": root needs to be null to prevent
        // IllegalStateException: The specified child already has a parent.
        if (mIsReadOnly) {
            view = inflater.inflate(R.layout.edit_event_single_column, null);
        } else {
            view = inflater.inflate(R.layout.edit_event, null);
        }
        mView = new EditEventView(mActivity, view, mOnDone);

        if (havePermission(Manifest.permission.READ_CALENDAR)) {
            if (mModel == null) {
                mModel = new CalendarEventModel();
                if (mActionInfo != null) {
                    if (mActionInfo.startTime != null) {
                        mModel.mStart = mActionInfo.startTime.toMillis(true);
                        mModel.mEnd = mModel.mStart;
                    }
                    if (mActionInfo.endTime != null) {
                        mModel.mEnd = mActionInfo.endTime.toMillis(true);
                    }
                    if (mActionInfo.calendarId != -1) {
                        mModel.mCalendarId = mActionInfo.calendarId;
                    }
                    if (mActionInfo.id != -1) {
                        mModel.mId = mActionInfo.id;
                        mModel.mUri = ContentUris.withAppendedId(
                            Events.CONTENT_URI, mActionInfo.id);
                    } else {
                        // New event. All day?
                        mModel.mAllDay =
                            mActionInfo.extraLong ==
                                CalendarController.EXTRA_CREATE_ALL_DAY;
                    }
                } else if (mEventBundle != null) {
                    if (mEventBundle.id != -1) {
                        mModel.mId = mEventBundle.id;
                        mModel.mUri = ContentUris.withAppendedId(
                            Events.CONTENT_URI, mEventBundle.id);
                    }
                    mModel.mStart = mEventBundle.start;
                    mModel.mEnd = mEventBundle.end;
                }
                mModel.mOriginalStart = mModel.mStart;
                mModel.mOriginalEnd = mModel.mEnd;
                mModel.mSelfAttendeeStatus = Attendees.ATTENDEE_STATUS_ACCEPTED;
                if (mReminders != null) {
                    mModel.mReminders = mReminders;
                }
                if (mEventColorInitialized) {
                    mModel.setEventColor(mEventColor);
                }
            }

            if (mModel.mStart <= 0) {
                // use a default value instead
                mModel.mStart =
                    mHelper.constructDefaultStartTime(System.currentTimeMillis());
            }
            if (mModel.mEnd < mModel.mStart) {
                // use a default value instead
                mModel.mEnd = mHelper.constructDefaultEndTime(mModel.mStart, mActivity);
            }

            // Kick off the query for the event
            boolean newEvent = mModel.mUri == null;
            if (!newEvent) {
                mModel.mCalendarAccessLevel = Calendars.CAL_ACCESS_NONE;
                mOutstandingQueries = TOKEN_ALL;
                if (DEBUG) {
                    Log.d(TAG, "startQuery: uri for event is "
                        + mModel.mUri.toString());
                }
                mHandler.startQuery(
                    TOKEN_EVENT, null, mModel.mUri,
                    EditEventHelper.EVENT_PROJECTION,
                    null, null, null);
            } else {
                mOutstandingQueries = TOKEN_CALENDARS | TOKEN_COLORS;
                if (DEBUG) {
                    Log.d(TAG, "startQuery: Editing a new event.");
                }

                // Start a query in the background to read the list of calendars and colors
                mHandler.startQuery(TOKEN_CALENDARS, null, Calendars.CONTENT_URI,
                    EditEventHelper.CALENDARS_PROJECTION,
                    EditEventHelper.CALENDARS_WHERE_WRITEABLE_VISIBLE,
                    null /* selection args */,
                    null /* sort order */);
                // mHandler.onQueryComplete will be called when a query completes.

                mHandler.startQuery(TOKEN_COLORS, null, Colors.CONTENT_URI,
                    EditEventHelper.COLORS_PROJECTION,
                    Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT,
                    null, null);
                // mHandler.onQueryComplete will be called when a query completes.

                mModification = Utils.MODIFY_ALL;
                mView.setModification(mModification);
            }
        } else {
            Toast.makeText(mActivity, R.string.calendar_permission_not_granted,
                Toast.LENGTH_LONG).show();
        }
        return view;
    }

    // This is called after onCreateView().
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mColorPickerDialog = (EventColorPickerDialog) getActivity().getFragmentManager()
            .findFragmentByTag(COLOR_PICKER_DIALOG_TAG);
        if (mColorPickerDialog != null) {
            mColorPickerDialog.setOnColorSelectedListener(this);
        }
    }

    public final Runnable onDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (EditEventFragment.this.mIsPaused) {
                mDismissOnResume = true;
                return;
            }
            if (EditEventFragment.this.isVisible()) {
                EditEventFragment.this.dismiss();
            }
        }
    };

    private void startDeleteHelper() {
        mDeleteHelper = new DeleteEventHelper(
            mActivity, mActivity, !mIsReadOnly /* exitWhenDone */);
        mDeleteHelper.setDeleteNotificationListener(this);
        mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
        mDeleteHelper.delete(
            mModel.mStart, mModel.mEnd, mModel.mId, -1, onDeleteRunnable);
    }

    // onViewStateRestored and onStart are not overridden.
    // This is called after onActivityCreated().
    @Override
    public void onResume() {
        super.onResume();
        mIsPaused = false;
        if (mDismissOnResume) {
            mHandler.post(onDeleteRunnable);
        }
        // Display the "delete confirmation" or "edit response helper" dialog if needed
        if (mDeleteDialogVisible) {
            startDeleteHelper();
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.edit_event_title_bar, menu);
        if (menu instanceof MenuBuilder) {
            MenuBuilder m = (MenuBuilder) menu;
            m.setOptionalIconsVisible(true);
        }
    }

    /**
     * Prepare the Screen's standard options menu to be displayed.  This is
     * called right before the menu is shown, every time it is shown.  You can
     * use this method to efficiently enable/disable items or otherwise
     * dynamically modify the contents.  See
     * {@link Activity#onPrepareOptionsMenu(Menu) Activity.onPrepareOptionsMenu}
     * for more information.
     *
     * @param menu The options menu as last shown or first initialized by
     *             onCreateOptionsMenu().
     * @see #setHasOptionsMenu
     * @see #onCreateOptionsMenu
     */
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
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
        if (mModel.mUri == null) {
            MenuItem m = menu.findItem(R.id.info_action_delete);
            m.setEnabled(false);
            m.setVisible(false);
            m = menu.findItem(R.id.info_action_delete_menu);
            m.setEnabled(false);
            m.setVisible(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return onActionBarItemSelected(item.getItemId());
    }

    @Override
    public void onDeleteStarted() {
    }

    /**
     * Generates an .ics formatted file with the event info and launches intent chooser to
     * share said file
     */
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
                    Uri icsFile = FileProvider.getUriForFile(getActivity(),
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
                        Uri vcsFile = FileProvider.getUriForFile(getActivity(),
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

    private Dialog.OnDismissListener createDeleteOnDismissListener() {
        return new Dialog.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                // Since OnPause will force the dialog to dismiss , do
                // not change the dialog status
                if (!mIsPaused) {
                    mDeleteDialogVisible = false;
                }
            }
        };
    }

    /**
     * Handles menu item selections, whether they come from our custom action bar buttons
     * or from the standard menu items. Depends on the menu item ids matching
     * the custom action bar button ids.
     *
     * @param itemId the button or menu item id
     * @return whether the event was handled here
     */
    private boolean onActionBarItemSelected(int itemId) {
        if (   (itemId == R.id.action_cancel)
            || (itemId == R.id.action_cancel_menu))
        {
            mOnDone.setDoneCode(Utils.DONE_REVERT);
            mOnDone.run();
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
            } else if (   EditEventHelper.canAddReminders(mModel)
                       && (mModel.mId != -1)
                       && (mOriginalModel != null)
                       && mView.prepareForSave())
            {
                saveReminders();
                mOnDone.setDoneCode(Utils.DONE_EXIT);
                mOnDone.run();
            } else {
                mOnDone.setDoneCode(Utils.DONE_REVERT);
                mOnDone.run();
            }
        } else if (   (itemId == R.id.info_action_delete)
                   || (itemId == R.id.info_action_delete_menu))
        {
            startDeleteHelper();
        } else if (   (itemId == R.id.info_action_export)
                   || (itemId == R.id.info_action_export_menu))
        {
            shareEvent(ShareType.SDCARD);
        } else if (   (itemId == R.id.info_action_share_event)
                   || (itemId == R.id.info_action_share_event_menu))
        {
            shareEvent(ShareType.INTENT);
        }
        return true;
    }

    private void saveReminders() {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>(3);
        boolean changed = EditEventHelper.saveReminders(
            ops, mModel.mId, mModel.mReminders,
            mOriginalModel.mReminders, false /* no force save */);

        if (!changed) {
            return;
        }

        AsyncQueryService service = new AsyncQueryService(getActivity());
        service.startBatch(
            0, null, Calendars.CONTENT_URI.getAuthority(), ops, 0);
        // Update the "hasAlarm" field for the event
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mModel.mId);
        int len = mModel.mReminders.size();
        boolean hasAlarm = len > 0;
        if (hasAlarm != mOriginalModel.mHasAlarm) {
            ContentValues values = new ContentValues();
            values.put(Events.HAS_ALARM, hasAlarm ? 1 : 0);
            service.startUpdate(
                0, null, uri, values,
                null, null, 0);
        }

        Toast.makeText(mActivity, R.string.saving_event, Toast.LENGTH_SHORT).show();
    }

    protected void displayEditWhichDialog() {
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
                items[itemIndex++] = mActivity.getText(R.string.modify_all_following);
            }

            // Display the modification dialog.
            if (mModifyDialog != null) {
                mModifyDialog.dismiss();
                mModifyDialog = null;
            }
            mModifyDialog = new AlertDialog.Builder(mActivity)
                .setTitle(R.string.edit_event_label)
                .setItems(items, new OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (which == 0) {
                                // Update this if we start allowing exceptions
                                // to unsynced events in the app
                                mModification = notSynced ? Utils.MODIFY_ALL
                                        : Utils.MODIFY_SELECTED;
                                if (mModification == Utils.MODIFY_SELECTED) {
                                    mModel.mOriginalSyncId = notSynced
                                        ? null : mModel.mSyncId;
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

            mModifyDialog.setOnCancelListener(new OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    Activity a = EditEventFragment.this.getActivity();
                    if (a != null) {
                        a.finish();
                    }
                }
            });
        }
    }

    boolean isEmptyNewEvent() {
        if (mOriginalModel != null) {
            // Not new
            return false;
        }

        if (   (mModel.mOriginalStart != mModel.mStart)
            || (mModel.mOriginalEnd != mModel.mEnd))
        {
            return false;
        }

        if (!mModel.mAttendeesList.isEmpty()) {
            return false;
        }

        return mModel.isEmpty();
    }

    @Override
    public void onPause() {
        if (   mSaveOnDetach
            && (mActivity != null)
            && (!mIsReadOnly)
            && (!mActivity.isChangingConfigurations())
            && mView.prepareForSave())
        {
            mOnDone.setDoneCode(Utils.DONE_SAVE);
            mOnDone.run();
        }
        if (   (mActivity != null)
            && (havePermission(Manifest.permission.READ_CONTACTS)))
        {
            mActivity.finish();
        }
        mIsPaused = true;
        // Remove event deletion alert box since it is being rebuild in OnResume.
        // This is done to get the same behavior on OnResume since the AlertDialog
        // is gone on rotation but not if you press the HOME key.
        if (mDeleteDialogVisible && mDeleteHelper != null) {
            mDeleteHelper.dismissAlertDialog();
            mDeleteHelper = null;
        }
        super.onPause();
    }

    // onStop() and onDestroyView() are not overridden
    // This is called after onPause
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

    @Override
    public void eventsChanged() {
        // TODO Requery to see if event has changed
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mView.prepareForSave();
        outState.putSerializable(BUNDLE_KEY_MODEL, mModel);
        outState.putInt(BUNDLE_KEY_EDIT_STATE, mModification);
        if (mEventBundle == null && mActionInfo != null) {
            mEventBundle = new EventBundle();
            mEventBundle.id = mActionInfo.id;
            if (mActionInfo.startTime != null) {
                mEventBundle.start = mActionInfo.startTime.toMillis(true);
            }
            if (mActionInfo.endTime != null) {
                mEventBundle.end = mActionInfo.endTime.toMillis(true);
            }
        }
        outState.putBoolean(BUNDLE_KEY_EDIT_ON_LAUNCH, mShowModifyDialogOnLaunch);
        outState.putSerializable(BUNDLE_KEY_EVENT, mEventBundle);
        outState.putBoolean(BUNDLE_KEY_READ_ONLY, mIsReadOnly);
        outState.putBoolean(BUNDLE_KEY_SHOW_COLOR_PALETTE, mView.isColorPaletteVisible());
        outState.putBoolean(BUNDLE_KEY_DELETE_DIALOG_VISIBLE, mDeleteDialogVisible);
    }

    @Override
    public long getSupportedEventTypes() {
        return CalendarController.ControllerAction.USER_HOME;
    }

    @Override
    public void handleAction(CalendarController.ActionInfo actionInfo) {
        // It's currently unclear if we want to save the event or not when home
        // is pressed. When creating a new event we shouldn't save since we
        // can't get the id of the new event easily.
        if (   (actionInfo.actionType == CalendarController.ControllerAction.GO_TO)
            && mSaveOnDetach)
        {
            if (mView != null && mView.prepareForSave()) {
                mOnDone.setDoneCode(Utils.DONE_SAVE);
                mOnDone.run();
            }
        }
    }

    @Override
    public void onColorSelected(int color) {
        if (   (!mModel.isEventColorInitialized())
            || (mModel.getEventColor() != color))
        {
            mModel.setEventColor(color);
            mView.updateHeadlineColor(mModel, color);
        }
    }

    private static class EventBundle implements Serializable {
        private static final long serialVersionUID = 1L;
        long id = -1;
        long start = -1;
        long end = -1;
    }

    private void setModelIfDone(int queryType) {
        synchronized (this) {
            mOutstandingQueries &= ~queryType;
            if (mOutstandingQueries == 0) {
                if (   mShowModifyDialogOnLaunch
                    && (mModification == Utils.MODIFY_UNINITIALIZED))
                {
                    if (!TextUtils.isEmpty(mModel.mRrule)) {
                        displayEditWhichDialog();
                    } else {
                        mModification = Utils.MODIFY_ALL;
                    }

                }
                mView.setModel(mModel);
                mView.setModification(mModification);
            }
        }
    }

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        // We don't override startQuery()
        // onQueryComplete() is responsible for closing the cursor that is passed to it.
        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
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
                        mSaveOnDetach = false;
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
                        Uri attUri = Attendees.CONTENT_URI;
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
                        Uri rUri = Reminders.CONTENT_URI;
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
                        TOKEN_CALENDARS, null, Calendars.CONTENT_URI,
                        EditEventHelper.CALENDARS_PROJECTION,
                        EditEventHelper.CALENDARS_WHERE,
                        selArgs /* selection args */, null /* sort order */);

                    // TOKEN_COLORS
                    mHandler.startQuery(TOKEN_COLORS, null, Colors.CONTENT_URI,
                            EditEventHelper.COLORS_PROJECTION,
                            Colors.COLOR_TYPE + "="
                                + Colors.TYPE_EVENT, null, null);

                    setModelIfDone(TOKEN_EVENT);
                    break;
                case TOKEN_ATTENDEES:
                    try {
                        while (cursor.moveToNext()) {
                            String name =
                                cursor.getString(EditEventHelper.ATTENDEES_INDEX_NAME);
                            String email =
                                cursor.getString(EditEventHelper.ATTENDEES_INDEX_EMAIL);
                            int status =
                                cursor.getInt(EditEventHelper.ATTENDEES_INDEX_STATUS);
                            int relationship = cursor
                                    .getInt(EditEventHelper.ATTENDEES_INDEX_RELATIONSHIP);
                            if (relationship == Attendees.RELATIONSHIP_ORGANIZER) {
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
                                if (   (mModel.mOwnerAccount != null)
                                    && mModel.mOwnerAccount.equalsIgnoreCase(email))
                                {
                                    int attendeeId = cursor.getInt(
                                        EditEventHelper.ATTENDEES_INDEX_ID);
                                    mModel.mOwnerAttendeeId = attendeeId;
                                    mModel.mSelfAttendeeStatus = status;
                                    mOriginalModel.mOwnerAttendeeId = attendeeId;
                                    mOriginalModel.mSelfAttendeeStatus = status;
                                    continue;
                                }
                            }
                            Attendee attendee = new Attendee(name, email);
                            attendee.mStatus = status;
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
                            ReminderEntry re = ReminderEntry.valueOf(minutes, method);
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
                                matrixCursor, isAdded() && isResumed(),
                                mModel.mCalendarId);
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
                    if (   (mModel.mCalendarAccountName == null)
                        || (mModel.mCalendarAccountType == null))
                    {
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
}
