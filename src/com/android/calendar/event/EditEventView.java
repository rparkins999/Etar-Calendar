/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Modifications from the original version Copyright (C) Richard Parkins 2023
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

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.FragmentManager;
import android.app.ProgressDialog;
import android.app.Service;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Reminders;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ResourceCursorAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.DayView;
import com.android.calendar.DynamicTheme;
import com.android.calendar.EventInfoFragment;
import com.android.calendar.EventRecurrenceFormatter;
import com.android.calendar.Utils;
import com.android.calendar.event.EditEventHelper.EditDoneRunnable;
import com.android.calendar.fromcommon.Rfc822Validator;
import com.android.calendar.recurrencepicker.RecurrencePickerDialog;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendarcommon2.EventRecurrence;
import com.android.calendar.timezonepicker.TimeZoneInfo;
import com.android.calendar.timezonepicker.TimeZonePickerDialog;
import com.android.calendar.timezonepicker.TimeZonePickerUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

import ws.xsoh.etar.R;

public class EditEventView
    implements View.OnClickListener, OnItemSelectedListener,
        DialogInterface.OnDismissListener,
        DialogInterface.OnCancelListener, DialogInterface.OnClickListener,
        RecurrencePickerDialog.OnRecurrenceSetListener,
        TimeZonePickerDialog.OnTimeZoneSetListener
{
    private static final String TAG = "EditEvent";
    private static final String GOOGLE_SECONDARY_CALENDAR
        = "calendar.google.com";
    private static final String PERIOD_SPACE = ". ";
    private static final String FRAG_TAG_TIME_ZONE_PICKER
        = "timeZonePickerDialogFragment";
    private static final String FRAG_TAG_RECUR_PICKER
        = "recurrencePickerDialogFragment";
    private static final StringBuilder mSB
        = new StringBuilder(50);
    public boolean mIsMultipane;
    ArrayList<View> mEditOnlyList = new ArrayList<>();
    ArrayList<View> mEditViewList = new ArrayList<>();
    ArrayList<View> mViewOnlyList = new ArrayList<>();
    TextView mLoadingMessage;
    ScrollView mScrollView;
    int mSpinnerButtonColor;
    Button mStartDateButton;
    Button mEndDateButton;
    Button mStartTimeButton;
    Button mStartTimezoneButton;
    Button mEndTimeButton;
    Button mEndTimezoneButton;
    View mColorPickerNewEvent;
    View mColorPickerExistingEvent;
    View mStartTimezoneRow;
    View mEndTimezoneRow;
    TextView mStartTimeHome;
    TextView mStartDateHome;
    TextView mEndTimeHome;
    TextView mEndDateHome;
    CheckBox mAllDayCheckBox;
    Spinner mCalendarsSpinner;
    Button mRruleButton;
    Spinner mAvailabilitySpinner;
    Spinner mAccessLevelSpinner;
    RadioGroup mResponseRadioGroup;
    TextView mTitleTextView;
    AutoCompleteTextView mLocationTextView;
    EventLocationAdapter mLocationAdapter;
    TextView mDescriptionTextView;
    TextView mWhenView;
    TextView mStartTimezoneTextView;
    TextView mEndTimezoneTextView;
    LinearLayout mRemindersContainer;
    AttendeesView mAttendeesContainer;
    View mCalendarSelectorGroup;
    View mCalendarSelectorWrapper;
    View mCalendarStaticGroup;
    View mLocationGroup;
    View mDescriptionGroup;
    View mRemindersGroup;
    View mResponseGroup;
    View mOrganizerGroup;
    View mAttendeesGroup;
    View mStartHomeGroup;
    View mEndHomeGroup;
    private final int[] mOriginalPadding = new int[4];
    private ProgressDialog mLoadingCalendarsDialog;
    private AlertDialog mNoCalendarsDialog;
    private final EditEventActivity mActivity;
    private final EditDoneRunnable mDone;
    private final View mView;
    private CalendarEventModel mModel;
    private Cursor mCalendarsCursor;
    private final Rfc822Validator mEmailValidator;
    private DatePickerDialog mDatePickerDialog;
    /* Contents of the "minutes" spinner.  This has default values from the
     * XML file, augmented with any additional values that were already
     * associated with the event.
     */
    private ArrayList<Integer> mReminderMinuteValues;
    private ArrayList<String> mReminderMinuteLabels;
    /* Contents of the "methods" spinner.  The "values" list specifies the
     * method constant (e.g. {@link Reminders#METHOD_ALERT}) associated with
     * the labels.
     */
    private ArrayList<Integer> mReminderMethodValues;
    private ArrayList<String> mReminderMethodLabels;
    /* Contents of the "availability" spinner. The "values" list specifies the
     * type constant (e.g. {@link Events#AVAILABILITY_BUSY}) associated with
     * the labels. Any types that aren't allowed by the Calendar will be
     * removed.
     */
    private ArrayList<Integer> mAvailabilityValues;
    private ArrayAdapter<String> mAvailabilityAdapter;
    private boolean mAvailabilityExplicitlySet;
    private boolean mAllDayChangingAvailability;
    private int mAvailabilityCurrentlySelected;
    private int mDefaultReminderMinutes;
    private boolean mSaveAfterQueryComplete = false;
    private TimeZonePickerUtils mTzPickerUtils;
    /* This is the start time as displayed.
     * If this isn't an all day event, this is the start time of
     * the instance in the event's start timezone, from the database
     * or from the intent. If the event's start timezone isn't the
     * same as the user's current timezone, the start time in
     * the user's current timezone will be shown as well.
     * If this is an all day event, this is 00:00 on the first day of
     * the event in the user's current timezone, but only the date
     * is displayed and can be edited.
     */
    private final Time mStartTime;
    /* This is the end time as displayed.
     * If this isn't an all day event, this is the end time of
     * the instance in the event's end timezone, from the database
     * or from the intent. If the event's end timezone isn't the
     * same as the user's current timezone, the end time in
     * the user's current timezone will be shown as well.
     * If this is an all day event, this is 00:00 on the last day of
     * the event in the user's current timezone, but only the date
     * is displayed and can be edited. Note that this differs from the
     * database representation of the end time of an all day event,
     * which is 00:00 on the day *after* the last day of the event.
     */
    private final Time mEndTime;
    private String mStartTimezone;
    private String mEndTimezone;
    // This ISN'T the flag for an all day event,
    // but the flag for displaying it in teh all day region.
    private boolean mIsAllDay = false;
    private int mModification
        = EditEventHelper.MODIFY_UNINITIALIZED;
    private final EventRecurrence mEventRecurrence
        = new EventRecurrence();
    private final ArrayList<LinearLayout> mReminderItems
        = new ArrayList<>(0);
    private final ArrayList<ReminderEntry>
        mUnsupportedReminders = new ArrayList<>();
    private String mRrule;

    public EditEventView(
        EditEventActivity activity, View view, EditDoneRunnable done)
    {
        mActivity = activity;
        mView = view;
        mDone = done;
        mSpinnerButtonColor = DynamicTheme.getColor(
            mActivity, "spinner_button_color");
        // cache top level view elements
        mLoadingMessage = view.findViewById(R.id.loading_message);
        mScrollView = view.findViewById(R.id.scroll_view);
        // cache all the widgets
        mCalendarsSpinner = view.findViewById(R.id.calendars_spinner);
        mTitleTextView = view.findViewById(R.id.title);
        mLocationTextView = view.findViewById(R.id.location);
        mDescriptionTextView = view.findViewById(R.id.description);
        mStartDateButton = view.findViewById(R.id.start_date);
        mStartDateButton.setTextColor(mSpinnerButtonColor);
        mEndDateButton = view.findViewById(R.id.end_date);
        mEndDateButton.setTextColor(mSpinnerButtonColor);
        mWhenView = mView.findViewById(R.id.when);
        mStartTimezoneTextView
            = mView.findViewById(R.id.start_timezone_textView);
        mStartTimeButton = view.findViewById(R.id.start_time);
        mStartTimeButton.setTextColor(mSpinnerButtonColor);
        mStartTimezoneButton = view.findViewById(R.id.start_timezone_button);
        mStartTimezoneButton.setTextColor(mSpinnerButtonColor);
        mStartTimezoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Don't allow user to change the start timezone
                // while the all day box is checked.
                if(!mAllDayCheckBox.isChecked()) {
                    showTimezoneDialog(true);
                }
            }
        });
        mStartTimezoneRow = view.findViewById(R.id.start_timezone_button_row);
        mEndTimezoneTextView = mView.findViewById(R.id.end_timezone_textView);
        mEndTimeButton = view.findViewById(R.id.end_time);
        mEndTimeButton.setTextColor(mSpinnerButtonColor);
        mEndTimezoneButton = view.findViewById(R.id.end_timezone_button);
        mEndTimezoneButton.setTextColor(mSpinnerButtonColor);
        mEndTimezoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Don't allow user to change the end timezone
                // while the all day box is checked.
                if(!mAllDayCheckBox.isChecked()) {
                    showTimezoneDialog(false);
                }
            }
        });
        mEndTimezoneRow = view.findViewById(R.id.end_timezone_button_row);
        mStartTimeHome = view.findViewById(R.id.start_time_home_tz);
        mStartTimeHome.setTextColor(mSpinnerButtonColor);
        mStartDateHome = view.findViewById(R.id.start_date_home_tz);
        mStartDateHome.setTextColor(mSpinnerButtonColor);
        mEndTimeHome = view.findViewById(R.id.end_time_home_tz);
        mEndTimeHome.setTextColor(mSpinnerButtonColor);
        mEndDateHome = view.findViewById(R.id.end_date_home_tz);
        mEndDateHome.setTextColor(mSpinnerButtonColor);
        mAllDayCheckBox = view.findViewById(R.id.is_all_day);
        mRruleButton = view.findViewById(R.id.rrule);
        mRruleButton.setTextColor(mSpinnerButtonColor);
        mAvailabilitySpinner = view.findViewById(R.id.availability);
        mAccessLevelSpinner = view.findViewById(R.id.visibility);
        mCalendarSelectorGroup = view.findViewById(R.id.calendar_selector_group);
        mCalendarSelectorWrapper = view.findViewById(R.id.calendar_selector_wrapper);
        mCalendarStaticGroup = view.findViewById(R.id.calendar_group);
        mRemindersGroup = view.findViewById(R.id.reminders_row);
        mResponseGroup = view.findViewById(R.id.response_row);
        mOrganizerGroup = view.findViewById(R.id.organizer_row);
        mAttendeesGroup = view.findViewById(R.id.add_attendees_row);
        mLocationGroup = view.findViewById(R.id.where_row);
        mDescriptionGroup = view.findViewById(R.id.description_row);
        mStartHomeGroup = view.findViewById(R.id.from_row_home_tz);
        mEndHomeGroup = view.findViewById(R.id.to_row_home_tz);
        mAttendeesContainer = view.findViewById(R.id.long_attendee_list);
        mColorPickerNewEvent = view.findViewById(R.id.change_color_new_event);
        mColorPickerExistingEvent = view.findViewById(
            R.id.change_color_existing_event);
        mTitleTextView.setTag(mTitleTextView.getBackground());
        mLocationTextView.setTag(mLocationTextView.getBackground());
        mLocationAdapter = new EventLocationAdapter(activity);
        mLocationTextView.setAdapter(mLocationAdapter);
        mAvailabilityExplicitlySet = false;
        mAllDayChangingAvailability = false;
        mAvailabilityCurrentlySelected = -1;
        mAvailabilitySpinner.setOnItemSelectedListener(
            new OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    AdapterView<?> parent, View view, int position, long id)
                {
                    // The spinner's onItemSelected gets called while it is
                    // being initialized to the first item, and when we
                    // explicitly set it in the allDay checkbox toggling,
                    // so we need these checks to find out when the spinner
                    // is actually being clicked.
                    // Set the initial selection.
                    if (mAvailabilityCurrentlySelected == -1) {
                        mAvailabilityCurrentlySelected = position;
                    }
                    if (   mAvailabilityCurrentlySelected != position
                        && !mAllDayChangingAvailability)
                    {
                        mAvailabilityCurrentlySelected = position;
                        mAvailabilityExplicitlySet = true;
                        somethingChanged();
                    } else {
                        mAvailabilityCurrentlySelected = position;
                        mAllDayChangingAvailability = false;
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> arg0) {

                }
            });
        mDescriptionTextView.setTag(mDescriptionTextView.getBackground());
        mAttendeesContainer.setTag(mAttendeesContainer.getBackground());
        mOriginalPadding[0] = mLocationTextView.getPaddingLeft();
        mOriginalPadding[1] = mLocationTextView.getPaddingTop();
        mOriginalPadding[2] = mLocationTextView.getPaddingRight();
        mOriginalPadding[3] = mLocationTextView.getPaddingBottom();

        mEditViewList.add(mTitleTextView);
        mEditViewList.add(mLocationTextView);
        mEditViewList.add(mDescriptionTextView);
        mEditViewList.add(mAttendeesContainer);

        mViewOnlyList.add(view.findViewById(R.id.when_row));
        mViewOnlyList.add(view.findViewById(R.id.start_timezone_textview_row));
        mViewOnlyList.add(view.findViewById(R.id.end_timezone_textview_row));

        mEditOnlyList.add(view.findViewById(R.id.all_day_row));
        mEditOnlyList.add(view.findViewById(R.id.availability_row));
        mEditOnlyList.add(view.findViewById(R.id.visibility_row));
        mEditOnlyList.add(view.findViewById(R.id.from_row));
        mEditOnlyList.add(mStartTimezoneRow);
        mEditOnlyList.add(view.findViewById(R.id.to_row));
        mEditOnlyList.add(mEndTimezoneRow);
        mEditOnlyList.add(mStartHomeGroup);
        mEditOnlyList.add(mEndHomeGroup);

        mResponseRadioGroup = view.findViewById(R.id.response_value);
        mRemindersContainer =
            view.findViewById(R.id.reminder_items_container);

        mIsMultipane = activity.getResources().getBoolean(
            R.bool.tablet_config);

        // Do we really need these initialisations?
        mStartTimezone = Utils.getTimeZone(activity, null);
        mStartTime = new Time(mStartTimezone);
        mEndTimezone = mStartTimezone;
        mEndTime = new Time(mEndTimezone);
        mEmailValidator = new Rfc822Validator(null);

        // Display loading screen
        setModel(null);

        FragmentManager fm = activity.getFragmentManager();
        RecurrencePickerDialog rpd = (RecurrencePickerDialog) fm
                .findFragmentByTag(FRAG_TAG_RECUR_PICKER);
        if (rpd != null) {
            rpd.setOnRecurrenceSetListener(this);
        }
    }

    // Loads an integer array asset into a list.
    private static ArrayList<Integer> loadIntegerArray(
        Resources r, int resNum)
    {
        int[] vals = r.getIntArray(resNum);
        ArrayList<Integer> list = new ArrayList<>(vals.length);
        for (int val : vals) {
            list.add(val);
        }
        return list;
    }

    // Loads a String array asset into a list.
    private static ArrayList<String> loadStringArray(
        Resources r, int resNum)
    {
        return new ArrayList<>(Arrays.asList(r.getStringArray(resNum)));
    }

    private void fixStartTimeZone(
        long timeMillis, String timeZone)
    {
        mStartTimezone = timeZone;
        if (mTzPickerUtils == null) {
            mTzPickerUtils = new TimeZonePickerUtils(mActivity);
        }
        CharSequence displayName =
            mTzPickerUtils.getGmtDisplayName(
                mActivity, timeZone, timeMillis, false);
        mStartTimezoneTextView.setText(displayName);
        mStartTimezoneButton.setText(displayName);
    }

    private void fixEndTimeZone(
        long timeMillis, String timeZone)
    {
        mEndTimezone = timeZone;
        if (mTzPickerUtils == null) {
            mTzPickerUtils = new TimeZonePickerUtils(mActivity);
        }
        CharSequence displayName =
            mTzPickerUtils.getGmtDisplayName(
                mActivity, timeZone, timeMillis, false);
        mEndTimezoneTextView.setText(displayName);
        mEndTimezoneButton.setText(displayName);
    }

    // Implements TimeZonePickerDialog.OnTimeZoneSetListener
    @Override
    public void onTimeZoneSet(TimeZoneInfo tzi, boolean isStart) {
        long startMillis = mStartTime.normalize(true);
        long endMillis = mEndTime.normalize(true);
        if (isStart) {
            mStartTime.timezone = tzi.mTzId;
            long timeMillis = mStartTime.normalize(true);
            if (timeMillis >= endMillis) {
                mEndTime.set(timeMillis + endMillis - startMillis);
            }
            fixStartTimeZone(timeMillis, tzi.mTzId);
        } else {
            mEndTime.timezone = tzi.mTzId;
            long timeMillis = mEndTime.normalize(true);
            if (timeMillis <= startMillis) {
                mStartTime.set(timeMillis - (endMillis - startMillis));
            }
            fixEndTimeZone(timeMillis, tzi.mTzId);
        }
        updateTimes();
        somethingChanged();
    }

    private void showTimezoneDialog(boolean isStart) {
        Bundle b = new Bundle();
        b.putLong(
            TimeZonePickerDialog.BUNDLE_EVENT_TIME_MILLIS,
                  mStartTime.toMillis(false));
        b.putString(TimeZonePickerDialog.BUNDLE_EVENT_TIME_ZONE,
            isStart ? mStartTimezone : mEndTimezone);
        b.putBoolean(
            TimeZonePickerDialog.BUNDLE_EVENT_IS_START, isStart);

        FragmentManager fm = mActivity.getFragmentManager();
        TimeZonePickerDialog tzpd = (TimeZonePickerDialog) fm
                .findFragmentByTag(FRAG_TAG_TIME_ZONE_PICKER);
        if (tzpd != null) {
            tzpd.dismiss();
        }
        tzpd = new TimeZonePickerDialog();
        tzpd.setArguments(b);
        tzpd.setOnTimeZoneSetListener(EditEventView.this);
        tzpd.show(fm, FRAG_TAG_TIME_ZONE_PICKER);
    }

    private void populateRepeats() {
        Resources r = mActivity.getResources();
        String repeatString;
        boolean enabled;
        if (!TextUtils.isEmpty(mRrule)) {
            repeatString = EventRecurrenceFormatter.getRepeatString(
                mActivity, r, mEventRecurrence, true);
            if (repeatString == null) {
                repeatString = r.getString(R.string.custom);
                Log.e(TAG, "Can't generate display string for " + mRrule);
                enabled = false;
            } else {
                // TODO Should give option to clear/reset rrule
                enabled = RecurrencePickerDialog.canHandleRecurrenceRule(mEventRecurrence);
                if (!enabled) {
                    Log.e(TAG, "UI can't handle " + mRrule);
                }
            }
        } else {
            repeatString = r.getString(R.string.does_not_repeat);
            enabled = true;
        }

        mRruleButton.setText(repeatString);

        // Don't allow the user to make exceptions recurring events.
        if (mModel.mOriginalSyncId != null) {
            enabled = false;
        }
        mRruleButton.setOnClickListener(this);
        mRruleButton.setEnabled(enabled);
    }

    /* Does prep steps for saving a calendar event.
     *
     * This triggers a parse of the attendees list and checks if the event is
     * ready to be saved. An event is ready to be saved so long as a model
     * exists and has a calendar it can be associated with, either because
     * it's an existing event or we've finished querying.
     *
     * Returns false if there is no model or no calendar had been loaded yet,
     * true otherwise.
     */
    public boolean prepareForSave() {
        if (   (mModel == null)
            || (   (mCalendarsCursor == null)
                && (mModel.mId < 0)))
        { return false; }
        return fillModelFromUI();
    }

    // This is called if the user clicks on one of the buttons: "Save",
    // "Discard", or "Delete". This is also called if the user clicks
    // on the "remove reminder" button.
    @Override
    public void onClick(View view) {
        if (view == mRruleButton) {
            Bundle b = new Bundle();
            b.putLong(RecurrencePickerDialog.BUNDLE_START_TIME_MILLIS,
                    mStartTime.toMillis(false));
            b.putString(RecurrencePickerDialog.BUNDLE_TIME_ZONE, mStartTime.timezone);

            // TODO may be more efficient to serialize and pass in EventRecurrence
            b.putString(RecurrencePickerDialog.BUNDLE_RRULE, mRrule);

            FragmentManager fm = mActivity.getFragmentManager();
            RecurrencePickerDialog rpd = (RecurrencePickerDialog) fm
                    .findFragmentByTag(FRAG_TAG_RECUR_PICKER);
            if (rpd != null) {
                rpd.dismiss();
            }
            rpd = new RecurrencePickerDialog();
            rpd.setArguments(b);
            rpd.setOnRecurrenceSetListener(EditEventView.this);
            rpd.show(fm, FRAG_TAG_RECUR_PICKER);
            return;
        }

        // This must be a click on one of the "remove reminder" buttons
        LinearLayout reminderItem = (LinearLayout) view.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        parent.removeView(reminderItem);
        mReminderItems.remove(reminderItem);
        updateRemindersVisibility(mReminderItems.size());
        somethingChanged();
    }

    @Override
    public void onRecurrenceSet(String rrule) {
        Log.d(TAG, "Old rrule:" + mRrule);
        Log.d(TAG, "New rrule:" + rrule);
        mRrule = rrule;
        if (mRrule != null) {
            mEventRecurrence.parse(mRrule);
        }
        populateRepeats();
        somethingChanged();
    }

    // This is called if the user cancels the "No calendars" dialog.
    // The "No calendars" dialog is shown if there are no syncable calendars.
    @Override
    public void onCancel(DialogInterface dialog) {
        if (dialog == mLoadingCalendarsDialog) {
            mLoadingCalendarsDialog = null;
            mSaveAfterQueryComplete = false;
        } else if (dialog == mNoCalendarsDialog) {
            mDone.setDoneCode(Utils.DONE_REVERT);
            mDone.run();
        }
    }

    // This is called if the user clicks on a dialog button.
    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (dialog == mNoCalendarsDialog) {
            mDone.setDoneCode(Utils.DONE_REVERT);
            mDone.run();
            if (which == DialogInterface.BUTTON_POSITIVE) {
                Intent nextIntent = new Intent(Settings.ACTION_ADD_ACCOUNT);
                final String[] array = {"com.android.calendar"};
                nextIntent.putExtra(Settings.EXTRA_AUTHORITIES, array);
                nextIntent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                mActivity.startActivity(nextIntent);
            }
        }
    }

    // Goes through the UI elements and updates the model as necessary
    private boolean fillModelFromUI() {
        if (mModel == null) {
            return false;
        }
        mModel.mReminders = EventViewUtils.reminderItemsToReminders(mReminderItems,
                mReminderMinuteValues, mReminderMethodValues);
        mModel.mReminders.addAll(mUnsupportedReminders);
        mModel.normalizeReminders();
        mModel.mHasAlarm = mReminderItems.size() > 0;
        mModel.mTitle = mTitleTextView.getText().toString();
        mModel.mLocation = mLocationTextView.getText().toString();
        mModel.mDescription = mDescriptionTextView.getText().toString();
        if (TextUtils.isEmpty(mModel.mTitle)) {
            mModel.mTitle = null;
        }
        if (TextUtils.isEmpty(mModel.mLocation)) {
            mModel.mLocation = null;
        }
        if (TextUtils.isEmpty(mModel.mDescription)) {
            mModel.mDescription = null;
        }

        int status = EventInfoFragment.getResponseFromButtonId(mResponseRadioGroup
                .getCheckedRadioButtonId());
        if (status != Attendees.ATTENDEE_STATUS_NONE) {
            mModel.mSelfAttendeeStatus = status;
        }

        if (mAttendeesContainer != null) {
            mModel.mAttendeesList.clear();
            for (Attendee attendee : mAttendeesContainer.mAttendeesList) {
                mModel.addAttendee(attendee);
            }
        }

        // If this was a new event we need to fill in the Calendar information
        if (mModel.mId < 0) {
            mModel.mCalendarId = mCalendarsSpinner.getSelectedItemId();
            int calendarCursorPosition = mCalendarsSpinner.getSelectedItemPosition();
            if (mCalendarsCursor.moveToPosition(calendarCursorPosition)) {
                String calendarOwner = mCalendarsCursor.getString(
                        EditEventHelper.CALENDARS_INDEX_OWNER_ACCOUNT);
                String calendarName = mCalendarsCursor.getString(
                        EditEventHelper.CALENDARS_INDEX_DISPLAY_NAME);
                String defaultCalendar = calendarOwner + "/" + calendarName;
                Utils.setSharedPreference(mActivity,
                    GeneralPreferences.KEY_DEFAULT_CALENDAR, defaultCalendar);
                mModel.mOwnerAccount = calendarOwner;
                mModel.mOrganizer = calendarOwner;
                mModel.mCalendarId =
                    mCalendarsCursor.getLong(EditEventHelper.CALENDARS_INDEX_ID);
            }
        }

        if (mAllDayCheckBox.isChecked()) {
            // Event is (now) an all day event,
            // set the model's timezone to UTC
            // and the model's time to start of day.
            Time t = new Time(mStartTime);
            // Same date and time (00:00) in UTC
            t.timezone = Time.TIMEZONE_UTC;
            long startMillis = t.toMillis(true);
            t.set(mEndTime);
            t.timezone = Time.TIMEZONE_UTC;
            t.hour = 24; // add back the day that we took off
            long endMillis = t.toMillis(true);
            if (!mModel.mAllDay) {
                // mAllDayCheckBox has changed to checked.
                // Save some information in case it was a mistake.
                mModel.mSaveInstanceStart = mModel.mInstanceStart;
                mModel.mSaveTimezoneStart = mModel.mTimezoneStart;
                mModel.mSaveInstanceEnd = mModel.mInstanceEnd;
                mModel.mSaveTimezoneEnd = mModel.mTimezoneEnd;
            } else {
                if (mModel.mInstanceStart != startMillis) {
                    // User changed start time while all mAllDayCheckBox
                    // was checked, so update the saved copy.
                    mModel.mSaveInstanceStart = startMillis;
                }
                if (mModel.mSaveInstanceEnd != endMillis) {
                    // User changed end time while mAllDayCheckBox
                    // was checked, so update the saved copy.
                    mModel.mSaveInstanceEnd = endMillis;
                }
            }
            mModel.mInstanceStart = startMillis;
            mModel.mInstanceEnd = endMillis;
            mModel.mTimezoneStart = Time.TIMEZONE_UTC;
            mModel.mTimezoneEnd = Time.TIMEZONE_UTC;
            mModel.mAllDay = true;
        } else {
            if (mModel.mAllDay) {
                // mAllDayCheckBox has changed to unchecked
                // set mModel.mInstanceStart and mModel.mInstanceEnd
                // from saved copies, which will have been updated
                // if user changed time while mAllDayCheckBox was checked.
                mModel.mInstanceStart = mModel.mSaveInstanceStart;
                mModel.mTimezoneStart = mModel.mSaveTimezoneStart;
                mModel.mInstanceEnd = mModel.mSaveInstanceEnd;
                mModel.mTimezoneEnd = mModel.mSaveTimezoneEnd;
            } else {
                // Time or timezone may have changed
                // while mAllDayCheckBox was unchecked
                mModel.mInstanceStart = mStartTime.toMillis(true);
                mModel.mSaveTimezoneStart =
                    mModel.mTimezoneStart = mStartTime.timezone;
                mModel.mInstanceEnd = mEndTime.toMillis(true);
                mModel.mSaveTimezoneEnd =
                    mModel.mTimezoneEnd = mEndTime.timezone;
            }
            mModel.mAllDay = false;
        }
        mModel.mAccessLevel = mAccessLevelSpinner.getSelectedItemPosition();
        // TODO set correct availability value
        mModel.mAvailability = mAvailabilityValues.get(mAvailabilitySpinner
                .getSelectedItemPosition());

        // rrrule
        // If we're making an exception we don't want it to be a repeating
        // event.
        if (mModification == EditEventHelper.MODIFY_SELECTED) {
            mModel.mRrule = null;
        } else {
            mModel.mRrule = mRrule;
        }

        return true;
    }

    private void prepareAccess() {
        Resources r = mActivity.getResources();
        ArrayAdapter<String> mAccessAdapter = new ArrayAdapter<>(
            mActivity, android.R.layout.simple_spinner_item,
            loadStringArray(r, R.array.visibility));
        mAccessAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        mAccessLevelSpinner.setAdapter(mAccessAdapter);
    }

    private void prepareAvailability() {
        Resources r = mActivity.getResources();

        mAvailabilityValues = loadIntegerArray(r, R.array.availability_values);
        ArrayList<String> availabilityLabels = loadStringArray(r, R.array.availability);

        if (mModel.mCalendarAllowedAvailability != null) {
            EventViewUtils.reduceMethodList(mAvailabilityValues, availabilityLabels,
                    mModel.mCalendarAllowedAvailability);
        }

        mAvailabilityAdapter = new ArrayAdapter<>(mActivity,
                android.R.layout.simple_spinner_item, availabilityLabels);
        mAvailabilityAdapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item);
        mAvailabilitySpinner.setAdapter(mAvailabilityAdapter);
    }

    /*
     * Prepares the Attendee UI elements.
     */
    private void prepareAttendees() {
        mAttendeesContainer.clearAttendees();
        for (Attendee attendee : mModel.mAttendeesList.values()) {
            mAttendeesContainer.addOneAttendee(attendee);
        }
    }

    /**
     * Prepares the reminder UI elements.
     * (Re-)loads the minutes / methods lists from the XML assets, adds/removes items as
     * needed for the current set of reminders and calendar properties, and then creates UI
     * elements.
     */
    private void prepareReminders() {
        CalendarEventModel model = mModel;
        Resources r = mActivity.getResources();

        // Load the labels and corresponding numeric values for the minutes and methods
        // lists from the assets.  If we're switching calendars, we need to clear and
        // re-populate the lists (which may have elements added and removed based on
        // calendar properties). This is mostly relevant for "methods", since we shouldn't
        // have any "minutes" values in a new event that aren't in the default set.
        mReminderMinuteValues = loadIntegerArray(r, R.array.reminder_minutes_values);
        mReminderMinuteLabels = loadStringArray(r, R.array.reminder_minutes_labels);
        mReminderMethodValues = loadIntegerArray(r, R.array.reminder_methods_values);
        mReminderMethodLabels = loadStringArray(r, R.array.reminder_methods_labels);

        int numReminders = 0;
        if (model.mHasAlarm) {
            ArrayList<ReminderEntry> reminders = model.mReminders;
            numReminders = reminders.size();
            // Insert any minute values that aren't represented in the minutes list.
            for (ReminderEntry re : reminders) {
                if (mReminderMethodValues.contains(re.getMethod())) {
                    EventViewUtils.addMinutesToList(mActivity, mReminderMinuteValues,
                            mReminderMinuteLabels, re.getMinutes());
                }
            }

            // Create a UI element for each reminder.  We display all of the reminders
            // we get from the provider.
            // We don't bother about Calendars.MAX_REMINDERS: Some calendars have
            // a value other than Integer.MAX_VALUE, but noen of them enforces it.
            //FIXME Why don't we set up a reminderChangedListener
            mUnsupportedReminders.clear();
            for (ReminderEntry re : reminders) {
                if (mReminderMethodValues.contains(re.getMethod())
                        || re.getMethod() == Reminders.METHOD_DEFAULT) {
                    EventViewUtils.addReminder(
                        mActivity, mScrollView, this, mReminderItems,
                        mReminderMinuteValues, mReminderMinuteLabels,
                        mReminderMethodValues, mReminderMethodLabels,
                        re, null);
                } else {
                    // TODO figure out a way to display unsupported reminders
                    mUnsupportedReminders.add(re);
                }
            }
        }

        updateRemindersVisibility(numReminders);
        View reminderAddButton = mView.findViewById(R.id.reminder_add);
        if (reminderAddButton != null) {
            reminderAddButton.setEnabled(true);
            reminderAddButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Fill in the view with the contents of the given event model. This allows
     * an edit view to be initialized before the event has been loaded. Passing
     * in null for the model will display a loading screen. A non-null model
     * will fill in the view's fields with the data contained in the model.
     *
     * @param model The event model to pull the data from
     */
    public void setModel(CalendarEventModel model) {
        mModel = model;

        if (model == null) {
            // Display loading screen
            mLoadingMessage.setVisibility(View.VISIBLE);
            mScrollView.setVisibility(View.GONE);
            return;
        }

        boolean canRespond = EditEventHelper.canRespond(model);

        long begin = model.mInstanceStart;
        if (begin > 0) {
            mStartTime.timezone = mStartTimezone = mModel.mTimezoneStart;
            mStartTime.set(begin);
        }
        long end = model.mInstanceEnd;
        if (begin > 0) {
            mEndTime.timezone = mEndTimezone = mModel.mTimezoneEnd;
            mEndTime.set(end);
        }

        // Set up the starting times

        mRrule = model.mRrule;
        if (!TextUtils.isEmpty(mRrule)) {
            mEventRecurrence.parse(mRrule);
        }

        if (mEventRecurrence.startDate == null) {
            mEventRecurrence.startDate = mStartTime;
        }

        // If the user is allowed to change the attendees set up the view and
        // validator
        // FIXME is this where we need to allow adding attendees?
        if (!model.mHasAttendeeData) {
            mAttendeesGroup.setVisibility(View.GONE);
        }

        // Default to false. Let setAllDayViewsVisibility update it as needed.
        mIsAllDay = false;
        if (model.mAllDay) {
            mAllDayCheckBox.setChecked(true);
            // put things back in local time for all day events
            mEndTimezone = mStartTimezone
                = Utils.getTimeZone(mActivity, null);
            // This leaves the same date and time (00:00) in the local timezone
            mStartTime.timezone = mStartTimezone;
            // Subtract a day to display the end date as the last day,
            // not the day after it.
            // mEndTime is currently UTC, so this can't move us
            // across a DST boundary.
            mEndTime.set(end - DayView.MILLIS_PER_DAY);
            // This leaves the same date and time (00:00) in the local timezone
            mEndTime.timezone = mEndTimezone;
            // Now there *can* be a DST boundary between mStartTime and
            // mEndTime if this is a multiple day event.
        } else {
            mAllDayCheckBox.setChecked(false);
        }

        fixStartTimeZone(mStartTime.normalize(true),
                         mStartTime.timezone);
        fixEndTimeZone(mEndTime.normalize(true),
                       mEndTime.timezone);

        SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(mActivity);
        String defaultReminderString = prefs.getString(
                GeneralPreferences.KEY_DEFAULT_REMINDER, GeneralPreferences.NO_REMINDER_STRING);
        mDefaultReminderMinutes = Integer.parseInt(defaultReminderString);

        prepareAttendees();
        prepareReminders();
        prepareAvailability();
        prepareAccess();

        View reminderAddButton = mView.findViewById(R.id.reminder_add);
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addReminder();
            }
        };
        reminderAddButton.setOnClickListener(addReminderOnClickListener);

        if (!mIsMultipane) {
            mView.findViewById(R.id.is_all_day_label).setOnClickListener(
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            mAllDayCheckBox.setChecked(!mAllDayCheckBox.isChecked());
                        }
                    });
        }

        /* This replaces  the OnEditorActionListener in order to handle
         * the case pf an external keyboard which has no dismiss key
         * to generate EditorInfo.IME_ACTION_DONE.
         */
        TextWatcher tw = new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s)
            {
                somethingChanged();
            }
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after)
            {}
            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count)
            {}
        };

        if (   (mTitleTextView.length() == 0)
            && (model.mTitle != null)) {
            mTitleTextView.setText(model.mTitle);
        }
        /*
         * Note we do this after sstting the text since that action
         * would call afterTextChanged().
         */
        mTitleTextView.addTextChangedListener(tw);

        if (model.mIsOrganizer || TextUtils.isEmpty(model.mOrganizer)
                || model.mOrganizer.endsWith(GOOGLE_SECONDARY_CALENDAR)) {
            mView.findViewById(R.id.organizer_label).setVisibility(View.GONE);
            mView.findViewById(R.id.organizer).setVisibility(View.GONE);
            mOrganizerGroup.setVisibility(View.GONE);
        } else {
            ((TextView) mView.findViewById(R.id.organizer)).setText(model.mOrganizerDisplayName);
        }

        if (   (mLocationTextView.length() == 0)
            && (model.mLocation != null)) {
            mLocationTextView.setText(model.mLocation);
        }
        /*
         * Note we do this after sstting the text since that action
         * would call afterTextChanged().
         */
        mLocationTextView.addTextChangedListener(tw);

        if (   (mDescriptionTextView.length() == 0)
            && (model.mDescription != null)) {
            mDescriptionTextView.setText(model.mDescription);
        }
        /*
         * Note we do this after sstting the text since that action
         * would call afterTextChanged().
         */
        mDescriptionTextView.addTextChangedListener(tw);

        int availIndex = mAvailabilityValues.indexOf(model.mAvailability);
        if (availIndex != -1) {
            mAvailabilitySpinner.setSelection(availIndex);
        }
        mAccessLevelSpinner.setSelection(model.mAccessLevel);

        View responseLabel = mView.findViewById(R.id.response_label);
        if (canRespond) {
            int buttonToCheck = EventInfoFragment
                    .findButtonIdForResponse(model.mSelfAttendeeStatus);
            mResponseRadioGroup.check(buttonToCheck); // -1 clear all radio buttons
            mResponseRadioGroup.setVisibility(View.VISIBLE);
            responseLabel.setVisibility(View.VISIBLE);
        } else {
            responseLabel.setVisibility(View.GONE);
            mResponseRadioGroup.setVisibility(View.GONE);
            mResponseGroup.setVisibility(View.GONE);
        }

        if (model.mId >= 0) {
            // This is an existing event so hide the calendar spinner
            // since we can't change the calendar.
            View calendarGroup = mView.findViewById(R.id.calendar_selector_group);
            calendarGroup.setVisibility(View.GONE);
            TextView tv = mView.findViewById(R.id.calendar_textview);
            tv.setText(model.mCalendarDisplayName);
            tv = mView.findViewById(R.id.calendar_textview_secondary);
            if (tv != null) {
                tv.setText(model.mOwnerAccount);
            }
        } else {
            View calendarGroup = mView.findViewById(R.id.calendar_group);
            calendarGroup.setVisibility(View.GONE);
        }
        if (model.isEventColorInitialized()) {
            updateHeadlineColor(model, model.getEventColor());
        }

        mStartDateButton.setOnClickListener(new DateClickListener(mStartTime));
        mEndDateButton.setOnClickListener(new DateClickListener(mEndTime));
        mStartTimeButton.setOnClickListener(new TimeClickListener(mStartTime));
        mEndTimeButton.setOnClickListener(new TimeClickListener(mEndTime));
        populateRepeats();
        updateAttendees(model.mAttendeesList);

        updateView();
        mScrollView.setVisibility(View.VISIBLE);
        mLoadingMessage.setVisibility(View.GONE);
        sendAccessibilityEvent();

        mAllDayCheckBox.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    setAllDayViewsVisibility(isChecked);
                    somethingChanged();
                }
            });

        // only one of these can be visible
        if (mActivity.getStartDateDialogVisibility()) {
            showDatePickerDialog(mStartDateButton, mStartTime);
        } else if (mActivity.getEndDateDialogVisibility()) {
            showDatePickerDialog(mEndDateButton, mEndTime);
        } else if (mActivity.getStartTimeDialogVisibility()) {
            showTimePickerDialog(mStartTimeButton, mStartTime);
        } else if (mActivity.getEndTimeDialogVisibility()) {
            showTimePickerDialog(mEndTimeButton, mEndTime);
        }

    }

    public void updateHeadlineColor(CalendarEventModel model, int displayColor) {
        if (model.mId >= 0) {
            if (mIsMultipane) {
                mView.findViewById(R.id.calendar_textview_with_colorpicker)
                    .setBackgroundColor(displayColor);
            } else {
                mView.findViewById(R.id.calendar_group).setBackgroundColor(displayColor);
            }
        } else {
            setSpinnerBackgroundColor(displayColor);
        }
    }

    private void setSpinnerBackgroundColor(int displayColor) {
        if (mIsMultipane) {
            mCalendarSelectorWrapper.setBackgroundColor(displayColor);
        } else {
            mCalendarSelectorGroup.setBackgroundColor(displayColor);
        }
    }

    private void sendAccessibilityEvent() {
        AccessibilityManager am = (AccessibilityManager) mActivity.getSystemService(
                Service.ACCESSIBILITY_SERVICE);
        if ((am == null) || (!am.isEnabled()) || (mModel == null)) {
            return;
        }
        StringBuilder b = new StringBuilder();
        addFieldsRecursive(b, mView);
        CharSequence msg = b.toString();

        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setClassName(getClass().getName());
        event.setPackageName(mActivity.getPackageName());
        event.getText().add(msg);
        event.setAddedCount(msg.length());

        am.sendAccessibilityEvent(event);
    }

    private void addFieldsRecursive(StringBuilder b, View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) {
            return;
        }
        if (v instanceof TextView) {
            CharSequence tv = ((TextView) v).getText();
            if (!TextUtils.isEmpty(tv.toString().trim())) {
                b.append(tv).append(PERIOD_SPACE);
            }
        } else if (v instanceof RadioGroup) {
            RadioGroup rg = (RadioGroup) v;
            int id = rg.getCheckedRadioButtonId();
            if (id != View.NO_ID) {
                b.append(((RadioButton) (v.findViewById(id))).getText())
                    .append(PERIOD_SPACE);
            }
        } else if (v instanceof Spinner) {
            Spinner s = (Spinner) v;
            if (s.getSelectedItem() instanceof String) {
                String str = ((String) (s.getSelectedItem())).trim();
                if (!TextUtils.isEmpty(str)) {
                    b.append(str).append(PERIOD_SPACE);
                }
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int children = vg.getChildCount();
            for (int i = 0; i < children; i++) {
                addFieldsRecursive(b, vg.getChildAt(i));
            }
        }
    }

    /**
     * Creates a single line string for the time/duration
     */
    protected void setWhenString() {
        String when;
        int flags = DateUtils.FORMAT_SHOW_DATE;
        String tz = mStartTimezone;
        if (mModel.mAllDay) {
            flags |= DateUtils.FORMAT_SHOW_WEEKDAY;
            tz = Time.TIMEZONE_UTC;
        } else {
            flags |= DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(mActivity)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
        }
        long startMillis = mStartTime.normalize(true);
        long endMillis = mEndTime.normalize(true);
        mSB.setLength(0);
        when = DateUtils.formatDateRange(
            mActivity, new Formatter(mSB, Locale.getDefault()),
            startMillis, endMillis, flags, tz).toString();
        mWhenView.setText(when);
    }

    /**
     * Configures the Calendars spinner.  This is only done for new events, because only new
     * events allow you to select a calendar while editing an event.
     * <p>
     * We tuck a reference to a Cursor with calendar database data into the spinner, so that
     * we can easily extract calendar-specific values when the value changes (the spinner's
     * onItemSelected callback is configured).
     */
    public void setCalendarsCursor(Cursor cursor, long selectedCalendarId) {
        // If there are no syncable calendars, then we cannot allow
        // creating a new event.
        mCalendarsCursor = cursor;
        if (cursor == null || cursor.getCount() == 0) {
            // Cancel the "loading calendars" dialog if it exists
            if (mSaveAfterQueryComplete) {
                mLoadingCalendarsDialog.cancel();
            }
            if (mView.getWindowVisibility() != View.VISIBLE) {
                return;
            }
            // Create an error message for the user that, when clicked,
            // will exit this activity without saving the event.
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setTitle(R.string.no_syncable_calendars).setIconAttribute(
                    android.R.attr.alertDialogIcon).setMessage(R.string.no_calendars_found)
                    .setPositiveButton(R.string.add_account, this)
                    .setNegativeButton(android.R.string.no, this).setOnCancelListener(this);
            mNoCalendarsDialog = builder.show();
            return;
        }

        int selection;
        if (selectedCalendarId != -1) {
            selection = findSelectedCalendarPosition(cursor, selectedCalendarId);
        } else {
            selection = findDefaultCalendarPosition(cursor);
        }

        // populate the calendars spinner
        CalendarsAdapter adapter = new CalendarsAdapter(mActivity,
            R.layout.calendars_spinner_item, cursor);
        mCalendarsSpinner.setAdapter(adapter);
        mCalendarsSpinner.setOnItemSelectedListener(this);
        mCalendarsSpinner.setSelection(selection);

        if (mSaveAfterQueryComplete) {
            mLoadingCalendarsDialog.cancel();
            if (prepareForSave()) {
                int exit = (mView.getWindowVisibility() == View.VISIBLE)
                    ? Utils.DONE_EXIT : 0;
                mDone.setDoneCode(Utils.DONE_SAVE | exit);
                mDone.run();
            } else if (mView.getWindowVisibility() == View.VISIBLE) {
                mDone.setDoneCode(Utils.DONE_EXIT);
                mDone.run();
            } else if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "SetCalendarsCursor:Save failed and unable to exit view");
            }
        }
    }

    /**
     * Updates the view based on {@link #mModification} and {@link #mModel}
     */
    public void updateView() {
        if (mModel == null) {
            return;
        }
        if (EditEventHelper.canModifyEvent(mModel)) {
            setViewStates(mModification);
        } else {
            setViewStates(Utils.MODIFY_UNINITIALIZED);
        }
    }

    private void setViewStates(int mode) {
        // Extra canModify check just in case
        if (mode == Utils.MODIFY_UNINITIALIZED || !EditEventHelper.canModifyEvent(mModel)) {
            setWhenString();

            for (View v : mViewOnlyList) {
                v.setVisibility(View.VISIBLE);
            }
            for (View v : mEditOnlyList) {
                v.setVisibility(View.GONE);
            }
            for (View v : mEditViewList) {
                v.setEnabled(false);
                v.setBackgroundDrawable(null);
            }
            mCalendarSelectorGroup.setVisibility(View.GONE);
            mCalendarStaticGroup.setVisibility(View.VISIBLE);
            mRruleButton.setEnabled(false);
            if (EditEventHelper.canAddReminders(mModel)) {
                mRemindersGroup.setVisibility(View.VISIBLE);
            } else {
                mRemindersGroup.setVisibility(View.GONE);
            }
            if (TextUtils.isEmpty(mLocationTextView.getText())) {
                mLocationGroup.setVisibility(View.GONE);
            }
            if (TextUtils.isEmpty(mDescriptionTextView.getText())) {
                mDescriptionGroup.setVisibility(View.GONE);
            }
        } else {
            for (View v : mViewOnlyList) {
                v.setVisibility(View.GONE);
            }
            for (View v : mEditOnlyList) {
                v.setVisibility(View.VISIBLE);
            }
            for (View v : mEditViewList) {
                v.setEnabled(true);
                if (v.getTag() != null) {
                    v.setBackgroundDrawable((Drawable) v.getTag());
                    v.setPadding(mOriginalPadding[0], mOriginalPadding[1], mOriginalPadding[2],
                            mOriginalPadding[3]);
                }
            }
            if (mModel.mId < 0) {
                mCalendarSelectorGroup.setVisibility(View.VISIBLE);
                mCalendarStaticGroup.setVisibility(View.GONE);
            } else {
                mCalendarSelectorGroup.setVisibility(View.GONE);
                mCalendarStaticGroup.setVisibility(View.VISIBLE);
            }
            if (mModel.mOriginalSyncId == null) {
                mRruleButton.setEnabled(true);
            } else {
                mRruleButton.setEnabled(false);
                mRruleButton.setBackgroundDrawable(null);
            }
            mRemindersGroup.setVisibility(View.VISIBLE);

            mLocationGroup.setVisibility(View.VISIBLE);
            mDescriptionGroup.setVisibility(View.VISIBLE);
        }
        setAllDayViewsVisibility(mAllDayCheckBox.isChecked());
    }

    public void setModification(int modifyWhich) {
        mModification = modifyWhich;
        updateView();
    }

    private int findSelectedCalendarPosition(Cursor calendarsCursor, long calendarId) {
        if (calendarsCursor.getCount() <= 0) {
            return -1;
        }
        int calendarIdColumn = calendarsCursor.getColumnIndexOrThrow(Calendars._ID);
        int position = 0;
        calendarsCursor.moveToPosition(-1);
        while (calendarsCursor.moveToNext()) {
            if (calendarsCursor.getLong(calendarIdColumn) == calendarId) {
                return position;
            }
            position++;
        }
        return 0;
    }

    // Find the calendar position in the cursor that matches calendar in
    // preference
    private int findDefaultCalendarPosition(Cursor calendarsCursor) {
        if (calendarsCursor.getCount() <= 0) {
            return -1;
        }

        String defaultCalendar = Utils.getSharedPreference(
                mActivity, GeneralPreferences.KEY_DEFAULT_CALENDAR, (String) null);

        int calendarsOwnerIndex = calendarsCursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
        int calendarNameIndex = calendarsCursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
        int accountNameIndex = calendarsCursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        int accountTypeIndex = calendarsCursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE);
        int position = 0;
        calendarsCursor.moveToPosition(-1);
        while (calendarsCursor.moveToNext()) {
            String calendarOwner = calendarsCursor.getString(calendarsOwnerIndex);
            String calendarName = calendarsCursor.getString(calendarNameIndex);
            String currentCalendar = calendarOwner + "/" + calendarName;
            if (defaultCalendar == null) {
                // There is no stored default upon the first time running.  Use a primary
                // calendar in this case.
                if (calendarOwner != null &&
                        calendarOwner.equals(calendarsCursor.getString(accountNameIndex)) &&
                        !CalendarContract.ACCOUNT_TYPE_LOCAL.equals(
                                calendarsCursor.getString(accountTypeIndex))) {
                    return position;
                }
            } else if (defaultCalendar.equals(currentCalendar)) {
                // Found the default calendar.
                return position;
            }
            position++;
        }
        return 0;
    }

    private void updateAttendees(HashMap<String, Attendee> attendeesList) {
        if (attendeesList == null || attendeesList.isEmpty()) {
            return;
        }
        mAttendeesContainer.clearAttendees();
        boolean none = true;
        for (Attendee attendee : attendeesList.values()) {
            mAttendeesContainer.addOneAttendee(attendee);
            none = false;
        }
        if (none) {
            mAttendeesContainer.setVisibility(View.GONE);
        } else {
            mAttendeesContainer.setVisibility(View.VISIBLE);
        }
        somethingChanged();
    }

    private void updateRemindersVisibility(int numReminders) {
        if (numReminders == 0) {
            mRemindersContainer.setVisibility(View.GONE);
        } else {
            mRemindersContainer.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Add a new reminder when the user hits the "add reminder" button.  We use the default
     * reminder time and method.
     */
    private void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        // last one in the list (if any).
        if (mDefaultReminderMinutes == GeneralPreferences.NO_REMINDER) {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderItems,
                    mReminderMinuteValues, mReminderMinuteLabels,
                    mReminderMethodValues, mReminderMethodLabels,
                    ReminderEntry.valueOf(GeneralPreferences.REMINDER_DEFAULT_TIME),
                null);
        } else {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderItems,
                    mReminderMinuteValues, mReminderMinuteLabels,
                    mReminderMethodValues, mReminderMethodLabels,
                    ReminderEntry.valueOf(mDefaultReminderMinutes),
                null);
        }
        updateRemindersVisibility(mReminderItems.size());
        somethingChanged();
    }

   /**
     * @param isChecked true if the All Day button is now checked
     */
    protected void setAllDayViewsVisibility(boolean isChecked) {
        if (isChecked) {
            // FIXME should we be doing this here?
            if (mEndTime.hour == 0 && mEndTime.minute == 0) {
                if (!mIsAllDay) {
                    mEndTime.monthDay--;
                }

                // Do not allow an event to have an end time
                // before the
                // start time.
                if (mEndTime.before(mStartTime)) {
                    mEndTime.set(mStartTime);
                }
            }
        } else {
            if (mEndTime.hour == 0 && mEndTime.minute == 0) {
                if (mIsAllDay) {
                    mEndTime.monthDay++;
                }
            }
        }

        // If this is a new event, and if availability has not yet been
        // explicitly set, toggle busy/available as the inverse of all day.
        if (mModel.mId < 0 && !mAvailabilityExplicitlySet) {
            // Values are from R.arrays.availability_values.
            // 0 = busy
            // 1 = available
            int newAvailabilityValue = isChecked? 1 : 0;
            if (mAvailabilityAdapter != null && mAvailabilityValues != null
                    && mAvailabilityValues.contains(newAvailabilityValue)) {
                // We'll need to let the spinner's listener know that we're
                // explicitly toggling it.
                mAllDayChangingAvailability = true;

                String newAvailabilityLabel =
                    loadStringArray(mActivity.getResources(), R.array.availability)
                        .get(newAvailabilityValue);
                int newAvailabilityPos =
                    mAvailabilityAdapter.getPosition(newAvailabilityLabel);
                mAvailabilitySpinner.setSelection(newAvailabilityPos);
            }
        }

        mIsAllDay = isChecked;
        updateTimes();
    }

    public void setColorPickerButtonStates(int[] colorArray) {
        setColorPickerButtonStates(colorArray != null && colorArray.length > 0);
    }

    public void setColorPickerButtonStates(boolean showColorPalette) {
        if (showColorPalette) {
            mColorPickerNewEvent.setVisibility(View.VISIBLE);
            mColorPickerExistingEvent.setVisibility(View.VISIBLE);
        } else {
            mColorPickerNewEvent.setVisibility(View.INVISIBLE);
            mColorPickerExistingEvent.setVisibility(View.GONE);
        }
    }

    public boolean isColorPaletteVisible() {
        return mColorPickerNewEvent.getVisibility() == View.VISIBLE ||
                mColorPickerExistingEvent.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        // This is only used for the Calendar spinner in new events,
        // and only fires when the calendar selection changes or on screen rotation
        Cursor c = (Cursor) parent.getItemAtPosition(position);
        if (c == null) {
            // TODO: can this happen? should we drop this check?
            Log.w(TAG, "Cursor not set on calendar item");
            return;
        }

        // Do nothing if the selection didn't change so that reminders will not get lost
        int idColumn = c.getColumnIndexOrThrow(Calendars._ID);
        long calendarId = c.getLong(idColumn);
        int colorColumn = c.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR);
        int color = c.getInt(colorColumn);
        int displayColor = Utils.getDisplayColorFromColor(color);

        // Prevents resetting of data (reminders, etc.) on orientation change.
        if (calendarId == mModel.mCalendarId && mModel.isCalendarColorInitialized() &&
                displayColor == mModel.getCalendarColor()) {
            return;
        }

        setSpinnerBackgroundColor(displayColor);

        mModel.mCalendarId = calendarId;
        mModel.setCalendarColor(displayColor);
        mModel.mCalendarAccountName
            = c.getString(EditEventHelper.CALENDARS_INDEX_ACCOUNT_NAME);
        mModel.mCalendarAccountType
            = c.getString(EditEventHelper.CALENDARS_INDEX_ACCOUNT_TYPE);
        mModel.setEventColor(mModel.getCalendarColor());

        setColorPickerButtonStates(mModel.getCalendarEventColors());

        // Update the max/allowed reminders with the new calendar properties.
        int allowedAttendeeTypesColumn
            = c.getColumnIndexOrThrow(Calendars.ALLOWED_ATTENDEE_TYPES);
        mModel.mCalendarAllowedAttendeeTypes = c.getString(allowedAttendeeTypesColumn);
        int allowedAvailabilityColumn
            = c.getColumnIndexOrThrow(Calendars.ALLOWED_AVAILABILITY);
        mModel.mCalendarAllowedAvailability = c.getString(allowedAvailabilityColumn);

        if (mModel.mReminders.size() == 0) {
            mModel.mReminders = mModel.cloneReminders(mModel.mDefaultReminders);
        }
        for (ReminderEntry entry : mModel.mReminders) {
            EventViewUtils.addReminder(mActivity, mScrollView,
                this, mReminderItems,
                mReminderMinuteValues, mReminderMinuteLabels,
                mReminderMethodValues, mReminderMethodLabels,
                ReminderEntry.valueOf(entry.getMinutes(), entry.getMethod()),
                null);
        }
        mModel.mHasAlarm = mModel.mReminders.size() != 0;

        // Update the UI elements.
        mReminderItems.clear();
        LinearLayout reminderLayout =
            mScrollView.findViewById(R.id.reminder_items_container);
        reminderLayout.removeAllViews();
        prepareReminders();
        prepareAvailability();
        prepareAccess();
        mActivity.invalidateOptionsMenu();
    }

    /* This sets the displayed times and dates.
     * If the event is all day, only the start and end date
     * (it can be a multiple day event) are shown, but no times.
     * if the start time zone isn't in the local time zone,
     * the start date and time in the local time zone are shown.
     * if the end time zone isn't in the local time zone,
     * the end date and time in the local time zone are shown.
     */
    private void updateTimes() {
        Formatter formatter = new Formatter(mSB, Locale.getDefault());
        int dateFlags = DateUtils.FORMAT_ABBREV_ALL
            | DateUtils.FORMAT_SHOW_DATE
            | DateUtils.FORMAT_SHOW_YEAR
            | DateUtils.FORMAT_SHOW_WEEKDAY;
        if (mIsAllDay) {
            // show the start and end dates
            Time displayedStart = new Time(mStartTime);
            Time displayedEnd = new Time(mEndTime);
            mSB.setLength(0);
            mStartDateButton.setText(DateUtils.formatDateTime(
                mActivity, displayedStart.toMillis(false),
                dateFlags));
            mEndDateButton.setText(DateUtils.formatDateTime(
                mActivity, displayedEnd.toMillis(false),
                dateFlags));
            mStartTimeButton.setVisibility(View.GONE);
            mStartHomeGroup.setVisibility(View.GONE);
            mStartTimezoneRow.setVisibility(View.GONE);
            mEndTimeButton.setVisibility(View.GONE);
            mEndHomeGroup.setVisibility(View.GONE);
            mEndTimezoneRow.setVisibility(View.GONE);
        } else {
            // show atart and end dates and times
            long millisStart = mStartTime.toMillis(false);
            long millisEnd = mEndTime.toMillis(false);
            int timeFlags = DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(mActivity)) {
                timeFlags |= DateUtils.FORMAT_24HOUR;
            }
            mSB.setLength(0);
            mStartDateButton.setText(DateUtils.formatDateRange(
                mActivity, formatter, millisStart, millisStart,
                dateFlags, mStartTimezone).toString());
            mSB.setLength(0);
            mEndDateButton.setText(DateUtils.formatDateRange(
                mActivity, formatter, millisEnd, millisEnd,
                dateFlags, mEndTimezone).toString());
            mStartTimeButton.setVisibility(View.VISIBLE);
            mEndTimeButton.setVisibility(View.VISIBLE);
            mSB.setLength(0);
            mStartTimeButton.setText(DateUtils.formatDateRange(
                mActivity, formatter, millisStart, millisStart,
                timeFlags, mStartTimezone).toString());
            mSB.setLength(0);
            mEndTimeButton.setText(DateUtils.formatDateRange(
                mActivity, formatter, millisEnd, millisEnd,
                timeFlags, mEndTimezone).toString());
            mStartTimezoneRow.setVisibility(View.VISIBLE);
            mEndTimezoneRow.setVisibility(View.VISIBLE);
            String tz = Utils.getTimeZone(mActivity, null);
            Time hereNow = new Time();
            hereNow.setToNow();
            if (mStartTime.gmtoff == hereNow.gmtoff) {
                // start time is in local time zone
                mStartHomeGroup.setVisibility(View.GONE);
            } else { // show local start date and time as well
                mSB.setLength(0);
                mStartDateHome.setText(DateUtils.formatDateRange(
                    mActivity, formatter, millisStart, millisStart,
                    dateFlags, tz).toString());
                String tzDisplay =
                    TimeZone.getTimeZone(tz).getDisplayName(
                        hereNow.isDst > 0,
                        TimeZone.SHORT, Locale.getDefault());
                mSB.setLength(0);
                String time = DateUtils.formatDateRange(
                    mActivity, formatter, millisStart, millisStart,
                    timeFlags, tz) +
                    " " + tzDisplay;
                mStartTimeHome.setText(time);
                mStartHomeGroup.setVisibility(View.VISIBLE);
            }
            if (mEndTime.gmtoff == hereNow.gmtoff) {
                mEndHomeGroup.setVisibility(View.GONE);
            } else {
                mSB.setLength(0);
                mEndDateHome.setText(DateUtils.formatDateRange(
                    mActivity, formatter, millisEnd, millisEnd,
                    dateFlags, tz).toString());
                String tzDisplay =
                    TimeZone.getTimeZone(tz).getDisplayName(
                        hereNow.isDst > 0,
                        TimeZone.SHORT, Locale.getDefault());
                mSB.setLength(0);
                String time = DateUtils.formatDateRange(
                    mActivity, formatter, millisEnd, millisEnd,
                    timeFlags, tz) +
                    " " + tzDisplay;
                mEndTimeHome.setText(time);
                mEndHomeGroup.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    public static class CalendarsAdapter extends ResourceCursorAdapter {
        public CalendarsAdapter(Context context, int resourceId, Cursor c) {
            super(context, resourceId, c);
            setDropDownViewResource(R.layout.calendars_dropdown_item);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            View colorBar = view.findViewById(R.id.color);
            int colorColumn = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR);
            int nameColumn = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
            int ownerColumn = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
            if (colorBar != null) {
                colorBar.setBackgroundColor(Utils.getDisplayColorFromColor(cursor
                        .getInt(colorColumn)));
            }

            TextView name = view.findViewById(R.id.calendar_name);
            if (name != null) {
                String displayName = cursor.getString(nameColumn);
                name.setText(displayName);

                TextView accountName = view.findViewById(R.id.account_name);
                if (accountName != null) {
                    accountName.setText(cursor.getString(ownerColumn));
                    accountName.setVisibility(TextView.VISIBLE);
                }
            }
        }
    }

    // This is called when mDatePickerDialog or mTimePickerDialog
    // is dismissed.
    @Override
    public void onDismiss(DialogInterface dialog) {
        if (dialog == mDatePickerDialog) {
            mActivity.setStartDateDialogVisibility(false);
            mActivity.setEndDateDialogVisibility(false);
        } else {
            mActivity.setStartTimeDialogVisibility(false);
            mActivity.setEndTimeDialogVisibility(false);
        }
    }

    /* This class is used to update the time buttons. */
    private class TimeListener implements TimePickerDialog.OnTimeSetListener {
        private final View mView;

        public TimeListener(View view) {
            mView = view;
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            // Cache the member variables locally to avoid inner class overhead.
            Time startTime = mStartTime;
            Time endTime = mEndTime;

            if (mView == mStartTimeButton) {
                // The start time was changed.
                // Get the actual duration of the event even if the
                // start and end times have different GMT offsets.
                long duration =
                    endTime.normalize(false)
                        - startTime.normalize(false);
                startTime.hour = hourOfDay;
                startTime.minute = minute;
                long startMillis = startTime.normalize(true);
                fixStartTimeZone(startMillis, startTime.timezone);
                // Update the end time to keep the duration constant.
                endTime.set(startMillis + duration);
            } else {
                // The end time was changed.
                endTime.hour = hourOfDay;
                endTime.minute = minute;

                // Move to a day after the start time
                // if the end time is before the start time.
                if (endTime.before(startTime)) {
                    endTime.monthDay = startTime.monthDay + 1;
                }
            }

            fixEndTimeZone(
                endTime.normalize(true), endTime.timezone);
            
            updateTimes();
            somethingChanged();
        }
    }

    private void showTimePickerDialog(View v, Time time) {
        TimePickerDialog mTimePickerDialog = new TimePickerDialog(
            mActivity, new TimeListener(v),
            time.hour, time.minute, DateFormat.is24HourFormat(mActivity));
        mTimePickerDialog.setOnDismissListener(this);
        mTimePickerDialog.show();
        if (v == mStartTimeButton) {
            mActivity.setStartTimeDialogVisibility(true);
        } else {
            mActivity.setEndTimeDialogVisibility(true);
        }
    }

    private class TimeClickListener implements View.OnClickListener {
        private final Time mTime;

        public TimeClickListener(Time time) {
            mTime = time;
        }

        @Override
        public void onClick(View v) {
            showTimePickerDialog(v, mTime);
        }
    }

    private class DateListener implements DatePickerDialog.OnDateSetListener {
        View mView;

        public DateListener(View view) {
            mView = view;
        }

        @Override
        public void onDateSet(DatePicker view, int year, int month, int monthDay) {
            Log.d(TAG, "onDateSet: " + year + " " + month + " " + monthDay);
            // Cache the member variables locally to avoid inner class overhead.
            Time startTime = mStartTime;
            Time endTime = mEndTime;

            // Cache the start and end millis so that we limit the number
            // of calls to normalize() and toMillis(), which are fairly
            // expensive.
            long startMillis;
            long endMillis;
            if (mView == mStartDateButton) {
                int startDay =
                    Time.getJulianDay(startTime.normalize(false),
                        startTime.gmtoff);
                int endDay =
                    Time.getJulianDay(endTime.normalize(false),
                        endTime.gmtoff);

                startTime.year = year;
                startTime.month = month;
                startTime.monthDay = monthDay;
                startMillis = startTime.normalize(true);
                int newstart =
                    Time.getJulianDay(startTime.normalize(false),
                        startTime.gmtoff);

                // Update the end date to keep the duration constant.
                Time t = new Time(endTime);
                t.setJulianDay(newstart + endDay - startDay);
                endTime.year = t.year;
                endTime.month = t.month;
                endTime.monthDay = t.monthDay;
                endMillis = endTime.normalize(true);

                // If the start date has changed then update the repeats.
                populateRepeats();
            } else {
                // The end date was changed.
                startMillis = startTime.toMillis(true);
                endTime.year = year;
                endTime.month = month;
                endTime.monthDay = monthDay;
                endMillis = endTime.normalize(true);

                // Do not allow an event to have an end time
                // before the start time.
                if (endTime.before(startTime)) {
                    // force start time back to one day event
                    if (mAllDayCheckBox.isChecked()) {
                        startTime.set(endTime);
                    } else {
                        int endDay =
                            Time.getJulianDay(endTime.normalize(false),
                                endTime.gmtoff);
                        Time t = new Time(endTime);
                        t.setJulianDay(endDay - 1);
                        endTime.year = t.year;
                        endTime.month = t.month;
                        endTime.monthDay = t.monthDay;
                        endMillis = endTime.normalize(true);
                    }
                    // If the start date has changed then update the repeats.
                    populateRepeats();
                }
            }
            fixStartTimeZone(startMillis, startTime.timezone);
            fixEndTimeZone(endMillis, endTime.timezone);
            updateTimes();
            somethingChanged();
        }
    }

    private void showDatePickerDialog(View v, Time time) {
        mDatePickerDialog = new DatePickerDialog(
            mActivity, new DateListener(v),
            time.year, time.month, time.monthDay);
        if (Build.VERSION.SDK_INT >= 21) {
            mDatePickerDialog.getDatePicker().setFirstDayOfWeek(Utils.getFirstDayOfWeekAsCalendar(mActivity));
        }
        mDatePickerDialog.setOnDismissListener(this);
        mDatePickerDialog.show();
        if (v == mStartDateButton) {
            mActivity.setStartDateDialogVisibility(true);
        } else {
            mActivity.setEndDateDialogVisibility(true);
        }
    }

    private class DateClickListener implements View.OnClickListener {
        private final Time mTime;

        public DateClickListener(Time time) {
            mTime = time;
        }

        @Override
        public void onClick(View v) {
            showDatePickerDialog(v, mTime);
        }
    }

    // This gets called when anything changes,
    // because the options menu may need to be updated.
    private void somethingChanged() {
        //FIXME
        /* Do we need to show something down the edge for events
         * which overlap more than a single day? At present they are only
         * shown at the top of the day column.
         */
        fillModelFromUI();
        mActivity.invalidateOptionsMenu();
    }
}
