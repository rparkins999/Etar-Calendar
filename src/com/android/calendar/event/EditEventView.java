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

import android.app.Activity;
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
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.EditorInfo;
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
import android.widget.TextView.OnEditorActionListener;
import android.widget.TimePicker;

import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
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

public class EditEventView implements View.OnClickListener, DialogInterface.OnCancelListener,
        DialogInterface.OnClickListener, OnItemSelectedListener,
        RecurrencePickerDialog.OnRecurrenceSetListener,
        TimeZonePickerDialog.OnTimeZoneSetListener {

    private static final String TAG = "EditEvent";
    private static final String GOOGLE_SECONDARY_CALENDAR = "calendar.google.com";
    private static final String PERIOD_SPACE = ". ";

    private static final String FRAG_TAG_TIME_ZONE_PICKER = "timeZonePickerDialogFragment";
    private static final String FRAG_TAG_RECUR_PICKER = "recurrencePickerDialogFragment";
    private static final StringBuilder mSB = new StringBuilder(50);
    private static final Formatter mF = new Formatter(mSB, Locale.getDefault());
    public boolean mIsMultipane;
    ArrayList<View> mEditOnlyList = new ArrayList<>();
    ArrayList<View> mEditViewList = new ArrayList<>();
    ArrayList<View> mViewOnlyList = new ArrayList<>();
    TextView mLoadingMessage;
    ScrollView mScrollView;
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
    TextView mStartTimezoneLabel;
    TextView mEndTimezoneTextView;
    TextView mEndTimezoneLabel;
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

    private final Activity mActivity;
    private final EditDoneRunnable mDone;
    private final View mView;
    private CalendarEventModel mModel;
    private Cursor mCalendarsCursor;
    private final Rfc822Validator mEmailValidator;
    private TimePickerDialog mStartTimePickerDialog;
    private TimePickerDialog mEndTimePickerDialog;
    private DatePickerDialog mDatePickerDialog;
    /**
     * Contents of the "minutes" spinner.  This has default values from the XML file,
     * augmented with any additional values that were already associated with the event.
     */
    private ArrayList<Integer> mReminderMinuteValues;
    private ArrayList<String> mReminderMinuteLabels;
    /**
     * Contents of the "methods" spinner.  The "values" list specifies the method constant
     * (e.g. {@link Reminders#METHOD_ALERT}) associated with the labels.
     */
    private ArrayList<Integer> mReminderMethodValues;
    private ArrayList<String> mReminderMethodLabels;
    /**
     * Contents of the "availability" spinner. The "values" list specifies the
     * type constant (e.g. {@link Events#AVAILABILITY_BUSY}) associated with the
     * labels. Any types that aren't allowed by the Calendar will be removed.
     */
    private ArrayList<Integer> mAvailabilityValues;
    private ArrayAdapter<String> mAvailabilityAdapter;
    private boolean mAvailabilityExplicitlySet;
    private boolean mAllDayChangingAvailability;
    private int mAvailabilityCurrentlySelected;
    private int mDefaultReminderMinutes;
    private boolean mSaveAfterQueryComplete = false;
    private TimeZonePickerUtils mTzPickerUtils;
    private final Time mStartTime;
    private final Time mEndTime;
    private String mStartTimezone;
    private String mEndTimezone;
    private boolean mAllDay = false;
    private int mModification = EditEventHelper.MODIFY_UNINITIALIZED;
    private final EventRecurrence mEventRecurrence = new EventRecurrence();
    private final ArrayList<LinearLayout> mReminderItems = new ArrayList<>(0);
    private final ArrayList<ReminderEntry> mUnsupportedReminders = new ArrayList<>();
    private String mRrule;

    public EditEventView(Activity activity, View view, EditDoneRunnable done) {

        mActivity = activity;
        mView = view;
        mDone = done;

        // cache top level view elements
        mLoadingMessage = (TextView) view.findViewById(R.id.loading_message);
        mScrollView = (ScrollView) view.findViewById(R.id.scroll_view);

        // cache all the widgets
        mCalendarsSpinner = (Spinner) view.findViewById(R.id.calendars_spinner);
        mTitleTextView = (TextView) view.findViewById(R.id.title);
        mLocationTextView = (AutoCompleteTextView) view.findViewById(R.id.location);
        mDescriptionTextView = (TextView) view.findViewById(R.id.description);
        mStartDateButton = (Button) view.findViewById(R.id.start_date);
        mEndDateButton = (Button) view.findViewById(R.id.end_date);
        mWhenView = (TextView) mView.findViewById(R.id.when);
        mStartTimezoneLabel = (TextView) view.findViewById(R.id.start_timezone_label);
        mStartTimezoneTextView = (TextView) mView.findViewById(R.id.start_timezone_textView);
        mStartTimeButton = (Button) view.findViewById(R.id.start_time);
        mStartTimezoneButton = (Button) view.findViewById(R.id.start_timezone_button);
        mStartTimezoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimezoneDialog(true);
            }
        });
        mStartTimezoneRow = view.findViewById(R.id.start_timezone_button_row);
        mEndTimezoneLabel = (TextView) view.findViewById(R.id.end_timezone_label);
        mEndTimezoneTextView = (TextView) mView.findViewById(R.id.end_timezone_textView);
        mEndTimeButton = (Button) view.findViewById(R.id.end_time);
        mEndTimezoneButton = (Button) view.findViewById(R.id.end_timezone_button);
        mEndTimezoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimezoneDialog(false);
            }
        });
        mEndTimezoneRow = view.findViewById(R.id.end_timezone_button_row);
        mStartTimeHome = (TextView) view.findViewById(R.id.start_time_home_tz);
        mStartDateHome = (TextView) view.findViewById(R.id.start_date_home_tz);
        mEndTimeHome = (TextView) view.findViewById(R.id.end_time_home_tz);
        mEndDateHome = (TextView) view.findViewById(R.id.end_date_home_tz);
        mAllDayCheckBox = (CheckBox) view.findViewById(R.id.is_all_day);
        mRruleButton = (Button) view.findViewById(R.id.rrule);
        mAvailabilitySpinner = (Spinner) view.findViewById(R.id.availability);
        mAccessLevelSpinner = (Spinner) view.findViewById(R.id.visibility);
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
        mColorPickerExistingEvent = view.findViewById(R.id.change_color_existing_event);

        mTitleTextView.setTag(mTitleTextView.getBackground());
        mLocationTextView.setTag(mLocationTextView.getBackground());
        mLocationAdapter = new EventLocationAdapter(activity);
        mLocationTextView.setAdapter(mLocationAdapter);
        mLocationTextView.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    // Dismiss the suggestions dropdown.  Return false so the other
                    // side effects still occur (soft keyboard going away, etc.).
                    mLocationTextView.dismissDropDown();
                }
                return false;
            }
        });

        mAvailabilityExplicitlySet = false;
        mAllDayChangingAvailability = false;
        mAvailabilityCurrentlySelected = -1;
        mAvailabilitySpinner.setOnItemSelectedListener(
                new OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                                               View view, int position, long id) {
                        // The spinner's onItemSelected gets called while it is being
                        // initialized to the first item, and when we explicitly set it
                        // in the allDay checkbox toggling, so we need these checks to
                        // find out when the spinner is actually being clicked.

                        // Set the initial selection.
                        if (mAvailabilityCurrentlySelected == -1) {
                            mAvailabilityCurrentlySelected = position;
                        }

                        if (mAvailabilityCurrentlySelected != position &&
                                !mAllDayChangingAvailability) {
                            mAvailabilityExplicitlySet = true;
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

        mResponseRadioGroup = (RadioGroup) view.findViewById(R.id.response_value);
        mRemindersContainer =
            (LinearLayout) view.findViewById(R.id.reminder_items_container);

        mStartTimezone = Utils.getTimeZone(activity, null);
        mIsMultipane = activity.getResources().getBoolean(R.bool.tablet_config);
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

    /**
     * Loads an integer array asset into a list.
     */
    private static ArrayList<Integer> loadIntegerArray(Resources r, int resNum) {
        int[] vals = r.getIntArray(resNum);
        ArrayList<Integer> list = new ArrayList<>(vals.length);
        for (int val : vals) {
            list.add(val);
        }
        return list;
    }

    /**
     * Loads a String array asset into a list.
     */
    private static ArrayList<String> loadStringArray(Resources r, int resNum) {
        return new ArrayList<>(Arrays.asList(r.getStringArray(resNum)));
    }

    // Fills in the date and time fields
    private void setDateTimeListeners() {
        mStartDateButton.setOnClickListener(new DateClickListener(mStartTime));
        mEndDateButton.setOnClickListener(new DateClickListener(mEndTime));
        mStartTimeButton.setOnClickListener(new TimeClickListener(mStartTime));
        mEndTimeButton.setOnClickListener(new TimeClickListener(mEndTime));
    }

    private void fixStartTimeZone(long timeMillis, String timeZone) {
        mStartTimezone = timeZone;
        if (mTzPickerUtils == null) {
            mTzPickerUtils = new TimeZonePickerUtils(mActivity);
        }
        CharSequence displayName =
            mTzPickerUtils.getGmtDisplayName(
                mActivity,timeZone, timeMillis, true);
        mStartTimezoneTextView.setText(displayName);
        mStartTimezoneButton.setText(displayName);
    }

    private void fixEndTimeZone(long timeMillis, String timeZone) {
        mEndTimezone = timeZone;
        if (mTzPickerUtils == null) {
            mTzPickerUtils = new TimeZonePickerUtils(mActivity);
        }
        CharSequence displayName =
            mTzPickerUtils.getGmtDisplayName(
                mActivity,timeZone, timeMillis, true);
        mEndTimezoneTextView.setText(displayName);
        mEndTimezoneButton.setText(displayName);
    }

    // Implements OnTimeZoneSetListener
    @Override
    public void onTimeZoneSet(TimeZoneInfo tzi, boolean isStart) {
        if (isStart) {
            mStartTime.timezone = tzi.mTzId;
            long timeMillis = mStartTime.normalize(true);
            fixStartTimeZone(timeMillis, tzi.mTzId);
        } else {
            mEndTime.timezone = tzi.mTzId;
            long timeMillis = mEndTime.normalize(true);
            fixEndTimeZone(timeMillis, tzi.mTzId);
        }
        updateTimes();
    }

    private void showTimezoneDialog(boolean isStart) {
        Bundle b = new Bundle();
        b.putLong(
            TimeZonePickerDialog.BUNDLE_EVENT_TIME_MILLIS,
                  mStartTime.toMillis(false));
        b.putString(
            TimeZonePickerDialog.BUNDLE_EVENT_TIME_ZONE, mStartTimezone);
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
            repeatString = EventRecurrenceFormatter.getRepeatString(mActivity, r,
                    mEventRecurrence, true);

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

    /**
     * Does prep steps for saving a calendar event.
     *
     * This triggers a parse of the attendees list and checks if the event is
     * ready to be saved. An event is ready to be saved so long as a model
     * exists and has a calendar it can be associated with, either because it's
     * an existing event or we've finished querying.
     *
     * @return false if there is no model or no calendar had been loaded yet,
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
        mModel.mAllDay = mAllDayCheckBox.isChecked();
        mModel.mLocation = mLocationTextView.getText().toString();
        mModel.mDescription = mDescriptionTextView.getText().toString();
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

        if (mModel.mAllDay) {
            // Reset start and end time, increment the monthDay by 1, and set
            // the timezone to UTC, as required for all-day events.
            mStartTimezone = Time.TIMEZONE_UTC;
            mStartTime.hour = 0;
            mStartTime.minute = 0;
            mStartTime.second = 0;
            mStartTime.timezone = mStartTimezone;
            mModel.mStart = mStartTime.normalize(true);

            mEndTime.hour = 0;
            mEndTime.minute = 0;
            mEndTime.second = 0;
            mEndTime.timezone = mEndTimezone;
            // When a user see the event duration as "X - Y" (e.g. Oct. 28 - Oct. 29),
            // end time should be Y + 1 (Oct.30).
            final long normalizedEndTimeMillis =
                    mEndTime.normalize(true) + DateUtils.DAY_IN_MILLIS;
            if (normalizedEndTimeMillis < mModel.mStart) {
                // mEnd should be midnight of the next day of mStart.
                mModel.mEnd = mModel.mStart + DateUtils.DAY_IN_MILLIS;
            } else {
                mModel.mEnd = normalizedEndTimeMillis;
            }
        } else {
            mStartTime.timezone = mStartTimezone;
            mEndTime.timezone = mEndTimezone;
            mModel.mStart = mStartTime.toMillis(true);
            mModel.mEnd = mEndTime.toMillis(true);
        }
        mModel.mTimezoneStart = mStartTimezone;
        mModel.mTimezoneEnd = mEndTimezone;
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

        long begin = model.mStart;
        long end = model.mEnd;
        mStartTimezone = model.mTimezoneStart; // this will be UTC for all day events
        mEndTimezone = model.mTimezoneEnd; // this will be UTC for all day events

        // Set up the starting times
        if (begin > 0) {
            mStartTime.timezone = mStartTimezone;
            mStartTime.set(begin);
            mStartTime.normalize(true);
        }
        if (end > 0) {
            mEndTime.timezone = mEndTimezone;
            mEndTime.set(end);
            mEndTime.normalize(true);
        }

        mRrule = model.mRrule;
        if (!TextUtils.isEmpty(mRrule)) {
            mEventRecurrence.parse(mRrule);
        }

        if (mEventRecurrence.startDate == null) {
            mEventRecurrence.startDate = mStartTime;
        }

        // If the user is allowed to change the attendees set up the view and
        // validator
        if (!model.mHasAttendeeData) {
            mAttendeesGroup.setVisibility(View.GONE);
        }

        mAllDayCheckBox.setOnCheckedChangeListener(
            new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setAllDayViewsVisibility(isChecked);
            }
        });

        boolean prevAllDay = mAllDayCheckBox.isChecked();
        mAllDay = false; // default to false. Let setAllDayViewsVisibility update it as needed
        if (model.mAllDay) {
            mAllDayCheckBox.setChecked(true);
            // put things back in local time for all day events
            mStartTimezone = Utils.getTimeZone(mActivity, null);
            mStartTime.timezone = mStartTimezone;
            mEndTime.timezone = mStartTimezone;
            mEndTime.normalize(true);
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

        if (model.mTitle != null) {
            mTitleTextView.setTextKeepState(model.mTitle);
        }

        if (model.mIsOrganizer || TextUtils.isEmpty(model.mOrganizer)
                || model.mOrganizer.endsWith(GOOGLE_SECONDARY_CALENDAR)) {
            mView.findViewById(R.id.organizer_label).setVisibility(View.GONE);
            mView.findViewById(R.id.organizer).setVisibility(View.GONE);
            mOrganizerGroup.setVisibility(View.GONE);
        } else {
            ((TextView) mView.findViewById(R.id.organizer)).setText(model.mOrganizerDisplayName);
        }

        if (model.mLocation != null) {
            mLocationTextView.setTextKeepState(model.mLocation);
        }

        if (model.mDescription != null) {
            mDescriptionTextView.setTextKeepState(model.mDescription);
        }

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
            TextView tv = (TextView) mView.findViewById(R.id.calendar_textview);
            tv.setText(model.mCalendarDisplayName);
            tv = (TextView) mView.findViewById(R.id.calendar_textview_secondary);
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

        setDateTimeListeners();
        populateRepeats();
        updateAttendees(model.mAttendeesList);

        updateView();
        mScrollView.setVisibility(View.VISIBLE);
        mLoadingMessage.setVisibility(View.GONE);
        sendAccessibilityEvent();
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
        when = DateUtils
                .formatDateRange(mActivity, mF, startMillis, endMillis, flags, tz).toString();
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
    public void setCalendarsCursor(Cursor cursor, boolean userVisible, long selectedCalendarId) {
        // If there are no syncable calendars, then we cannot allow
        // creating a new event.
        mCalendarsCursor = cursor;
        if (cursor == null || cursor.getCount() == 0) {
            // Cancel the "loading calendars" dialog if it exists
            if (mSaveAfterQueryComplete) {
                mLoadingCalendarsDialog.cancel();
            }
            if (!userVisible) {
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
            if (prepareForSave() && fillModelFromUI()) {
                int exit = userVisible ? Utils.DONE_EXIT : 0;
                mDone.setDoneCode(Utils.DONE_SAVE | exit);
                mDone.run();
            } else if (userVisible) {
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
    }

   /**
     * @param isChecked true if the All Day button is now checked
     */
    protected void setAllDayViewsVisibility(boolean isChecked) {
        if (isChecked) {
            if (mEndTime.hour == 0 && mEndTime.minute == 0) {
                if (mAllDay != isChecked) {
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
                if (mAllDay != isChecked) {
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

        mAllDay = isChecked;
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
            (LinearLayout) mScrollView.findViewById(R.id.reminder_items_container);
        reminderLayout.removeAllViews();
        prepareReminders();
        prepareAvailability();
        prepareAccess();
    }

    /* This sets the displayed times and dates.
     * If the event is all day, only the start and end date
     * (it can be a multiple day event) are shown, but no times.
     * if the start zone isn't in the local time zone,
     * the start date and time in the local time zone are shown.
     * if the end zone isn't in the local time zone,
     * the end date and time in the local time zone are shown.
     */
    private void updateTimes() {
        long millisStart = mStartTime.toMillis(false);
        long millisEnd = mEndTime.toMillis(false);
        int dateFlags = DateUtils.FORMAT_ABBREV_ALL
            | DateUtils.FORMAT_SHOW_DATE
            | DateUtils.FORMAT_SHOW_YEAR
            | DateUtils.FORMAT_SHOW_WEEKDAY;
        int timeFlags = DateUtils.FORMAT_SHOW_TIME;
        boolean is24Format = DateFormat.is24HourFormat(mActivity);
        if (is24Format) {
            timeFlags |= DateUtils.FORMAT_24HOUR;
        }
        mSB.setLength(0);
        mStartDateButton.setText(DateUtils.formatDateRange(
            mActivity, mF, millisStart, millisStart,
            dateFlags, mStartTimezone).toString());
        mSB.setLength(0);
        mEndDateButton.setText(DateUtils.formatDateRange(
            mActivity, mF, millisEnd, millisEnd,
            dateFlags, mEndTimezone).toString());
        if (mAllDayCheckBox.isChecked()) {
            mStartTimeButton.setVisibility(View.GONE);
            mStartHomeGroup.setVisibility(View.GONE);
            mStartTimezoneRow.setVisibility(View.GONE);
            mEndTimeButton.setVisibility(View.GONE);
            mEndHomeGroup.setVisibility(View.GONE);
            mEndTimezoneRow.setVisibility(View.GONE);
        } else {
            mStartTimeButton.setVisibility(View.VISIBLE);
            mSB.setLength(0);
            mStartTimeButton.setText(DateUtils.formatDateRange(
                mActivity, mF, millisStart, millisStart,
                timeFlags, mStartTimezone).toString());
            mStartTimezoneRow.setVisibility(View.VISIBLE);
            mEndTimeButton.setVisibility(View.VISIBLE);
            mSB.setLength(0);
            mEndTimeButton.setText(DateUtils.formatDateRange(
                mActivity, mF, millisEnd, millisEnd,
                timeFlags, mEndTimezone).toString());
            mEndTimezoneRow.setVisibility(View.VISIBLE);
            boolean isDSTStart = mStartTime.isDst != 0;
            boolean isDSTEnd = mEndTime.isDst != 0;
            String tz = Utils.getTimeZone(mActivity, null);
            long gmtoff = new Time(tz).gmtoff;
            if (mStartTime.gmtoff == gmtoff) {
                mStartHomeGroup.setVisibility(View.GONE);
            } else { // show local start date and time
                mSB.setLength(0);
                mStartDateHome.setText(DateUtils.formatDateRange(
                        mActivity, mF, millisStart, millisStart,
                        dateFlags, tz).toString());
                String tzDisplay =
                    TimeZone.getTimeZone(tz).getDisplayName(
                    isDSTStart, TimeZone.SHORT, Locale.getDefault());
                mSB.setLength(0);
                StringBuilder time = new StringBuilder();
                time.append(DateUtils.formatDateRange(
                    mActivity, mF, millisStart, millisStart,
                    timeFlags, tz))
                    .append(" ").append(tzDisplay);
                mStartTimeHome.setText(time.toString());
                mStartHomeGroup.setVisibility(View.VISIBLE);
            }
            if (mEndTime.gmtoff == gmtoff) {
                mEndHomeGroup.setVisibility(View.GONE);
            } else {
                mSB.setLength(0);
                mEndDateHome.setText(DateUtils.formatDateRange(
                    mActivity, mF, millisEnd, millisEnd,
                    dateFlags, tz).toString());
                String tzDisplay =
                    TimeZone.getTimeZone(tz).getDisplayName(
                    isDSTEnd, TimeZone.SHORT, Locale.getDefault());
                mSB.setLength(0);
                StringBuilder time = new StringBuilder();
                time.append(DateUtils.formatDateRange(
                        mActivity, mF, millisEnd, millisEnd,
                        timeFlags, tz))
                        .append(" ").append(tzDisplay);
                mEndTimeHome.setText(time.toString());
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

            TextView name = (TextView) view.findViewById(R.id.calendar_name);
            if (name != null) {
                String displayName = cursor.getString(nameColumn);
                name.setText(displayName);

                TextView accountName = (TextView) view.findViewById(R.id.account_name);
                if (accountName != null) {
                    accountName.setText(cursor.getString(ownerColumn));
                    accountName.setVisibility(TextView.VISIBLE);
                }
            }
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

            // Cache the start and end millis so that we limit the number
            // of calls to normalize() and toMillis(), which are fairly
            // expensive.
            long startMillis;
            long endMillis;
            if (mView == mStartTimeButton) {
                // The start time was changed.
                int hourDuration = endTime.hour - startTime.hour;
                int minuteDuration = endTime.minute - startTime.minute;

                startTime.hour = hourOfDay;
                startTime.minute = minute;
                startMillis = startTime.normalize(true);
                fixStartTimeZone(startMillis, startTime.timezone);

                // Also update the end tim
                // to keep the duration constant.
                endTime.hour = hourOfDay + hourDuration;
                endTime.minute = minute + minuteDuration;

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

            endMillis = endTime.normalize(true);
            fixEndTimeZone(endMillis, endTime.timezone);

            updateTimes();
        }
    }

    private class TimeClickListener implements View.OnClickListener {
        private final Time mTime;

        public TimeClickListener(Time time) {
            mTime = time;
        }

        @Override
        public void onClick(View v) {

            TimePickerDialog dialog;
            if (v == mStartTimeButton) {
                if (mStartTimePickerDialog != null) {
                    mStartTimePickerDialog.dismiss();
                }
                mStartTimePickerDialog = new TimePickerDialog(mActivity, new TimeListener(v),
                        mTime.hour, mTime.minute, DateFormat.is24HourFormat(mActivity));
                dialog = mStartTimePickerDialog;
            } else {
                if (mEndTimePickerDialog != null) {
                    mEndTimePickerDialog.dismiss();
                }
                mEndTimePickerDialog = new TimePickerDialog(mActivity, new TimeListener(v),
                        mTime.hour, mTime.minute, DateFormat.is24HourFormat(mActivity));
                dialog = mEndTimePickerDialog;

            }

            dialog.show();

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
                // The start date was changed.
                int yearDuration = endTime.year - startTime.year;
                int monthDuration = endTime.month - startTime.month;
                int monthDayDuration = endTime.monthDay - startTime.monthDay;

                startTime.year = year;
                startTime.month = month;
                startTime.monthDay = monthDay;
                startMillis = startTime.normalize(true);

                // Also update the end date to keep the duration constant.
                endTime.year = year + yearDuration;
                endTime.month = month + monthDuration;
                endTime.monthDay = monthDay + monthDayDuration;
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

                // Do not allow an event to have an end time before the start
                // time.
                if (endTime.before(startTime)) {
                    endTime.set(startTime);
                    endMillis = startMillis;
                }
            }
            fixStartTimeZone(startMillis, startTime.timezone);
            fixEndTimeZone(endMillis, endTime.timezone);
            updateTimes();
        }
    }

    private class DateClickListener implements View.OnClickListener {
        private final Time mTime;

        public DateClickListener(Time time) {
            mTime = time;
        }

        @Override
        public void onClick(View v) {


            final DateListener listener = new DateListener(v);
            if (mDatePickerDialog != null) {
                mDatePickerDialog.dismiss();
            }
            mDatePickerDialog = new DatePickerDialog(mActivity, listener,
                    mTime.year, mTime.month, mTime.monthDay);
            if (Build.VERSION.SDK_INT >= 21) {
                mDatePickerDialog.getDatePicker().setFirstDayOfWeek(Utils.getFirstDayOfWeekAsCalendar(mActivity));
            }
            mDatePickerDialog.show();
        }
    }
}
