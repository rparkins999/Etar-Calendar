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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.QuickContact;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.calendar.CalendarController.ControllerAction;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.alerts.QuickResponseActivity;
import com.android.calendar.event.AttendeesView;
import com.android.calendar.event.EditEventActivity;
import com.android.calendar.event.EditEventHelper;
import com.android.calendar.event.EventColorPickerDialog;
import com.android.calendar.event.EventViewUtils;
import com.android.calendar.icalendar.IcalendarUtils;
import com.android.calendar.icalendar.VCalendar;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendarcommon2.DateException;
import com.android.calendarcommon2.Duration;
import com.android.calendarcommon2.EventRecurrence;
import com.android.calendar.colorpicker.ColorPickerSwatch.OnColorSelectedListener;
import com.android.calendar.colorpicker.HsvColorComparator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import ws.xsoh.etar.BuildConfig;
import ws.xsoh.etar.R;

import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static com.android.calendar.CalendarController.EVENT_EDIT_ON_LAUNCH;

@SuppressLint("ValidFragment")
public class EventInfoFragment extends DialogFragment
    implements OnCheckedChangeListener, CalendarController.ActionHandler, OnClickListener,
    DeleteEventHelper.DeleteNotifyListener, OnColorSelectedListener,
    AsyncQueryService.AsyncQueryDone
{

    public static final boolean DEBUG = false;

    public static final String TAG = "EventInfoFragment";
    // Style of view
    public static final int FULL_WINDOW_STYLE = 0;
    public static final int DIALOG_WINDOW_STYLE = 1;
    public static final int COLORS_INDEX_COLOR = 1;
    public static final int COLORS_INDEX_COLOR_KEY = 2;
    protected static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    protected static final String BUNDLE_KEY_START_MILLIS = "key_start_millis";
    protected static final String BUNDLE_KEY_END_MILLIS = "key_end_millis";
    protected static final String BUNDLE_KEY_IS_DIALOG = "key_fragment_is_dialog";
    protected static final String BUNDLE_KEY_DELETE_DIALOG_VISIBLE
        = "key_delete_dialog_visible";
    protected static final String BUNDLE_KEY_WINDOW_STYLE = "key_window_style";
    protected static final String BUNDLE_KEY_CALENDAR_COLOR = "key_calendar_color";
    protected static final String BUNDLE_KEY_CALENDAR_COLOR_INIT
        = "key_calendar_color_init";
    protected static final String BUNDLE_KEY_CURRENT_COLOR = "key_current_color";
    protected static final String BUNDLE_KEY_CURRENT_COLOR_KEY = "key_current_color_key";
    protected static final String BUNDLE_KEY_CURRENT_COLOR_INIT = "key_current_color_init";
    protected static final String BUNDLE_KEY_ORIGINAL_COLOR = "key_original_color";
    protected static final String BUNDLE_KEY_ORIGINAL_COLOR_INIT
        = "key_original_color_init";
    protected static final String BUNDLE_KEY_ATTENDEE_RESPONSE = "key_attendee_response";
    protected static final String BUNDLE_KEY_USER_SET_ATTENDEE_RESPONSE
        = "key_user_set_attendee_response";
    protected static final String BUNDLE_KEY_TENTATIVE_USER_RESPONSE
        =  "key_tentative_user_response";
    protected static final String BUNDLE_KEY_RESPONSE_WHICH_EVENTS
        = "key_response_which_events";
    protected static final String BUNDLE_KEY_REMINDER_MINUTES = "key_reminder_minutes";
    protected static final String BUNDLE_KEY_REMINDER_METHODS = "key_reminder_methods";
    /**
     * These are the corresponding indices into the array of strings
     * "R.array.change_response_labels" in the resource file.
     */
    static final int UPDATE_SINGLE = 0;
    static final int UPDATE_ALL = 1;
    static final String[] CALENDARS_PROJECTION = new String[]{
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.OWNER_ACCOUNT,
            Calendars.CAN_ORGANIZER_RESPOND,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE
    };
    // This looks a bit messy, but it makes the compiler do the work
    // and avoids the maintenance burden of keeping track of the indices by hand.
    private static final List<String> calendarsProjection =
        Arrays.asList(CALENDARS_PROJECTION);
    static final int CALENDARS_INDEX_DISPLAY_NAME =
        calendarsProjection.indexOf(Calendars.CALENDAR_DISPLAY_NAME);
    static final int CALENDARS_INDEX_OWNER_ACCOUNT =
        calendarsProjection.indexOf(Calendars.OWNER_ACCOUNT);
    static final int CALENDARS_INDEX_OWNER_CAN_RESPOND =
        calendarsProjection.indexOf(Calendars.CAN_ORGANIZER_RESPOND);
    static final int CALENDARS_INDEX_ACCOUNT_NAME =
        calendarsProjection.indexOf(Calendars.ACCOUNT_NAME);
    static final int CALENDARS_INDEX_ACCOUNT_TYPE =
        calendarsProjection.indexOf(Calendars.ACCOUNT_TYPE);

    static final String CALENDARS_WHERE = Calendars._ID + "=?";
    static final String CALENDARS_DUPLICATE_NAME_WHERE = Calendars.CALENDAR_DISPLAY_NAME + "=?";
    static final String CALENDARS_VISIBLE_WHERE = Calendars.VISIBLE + "=?";
    static final String[] COLORS_PROJECTION = new String[]{
            Colors._ID, // 0
            Colors.COLOR, // 1
            Colors.COLOR_KEY // 2
    };
    static final String COLORS_WHERE =
        Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE
        + "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT;
    @SuppressWarnings("unused")
    private static final int REQUEST_CODE_COLOR_PICKER = 0;
    private static final String PERIOD_SPACE = ". ";
    private static final String NO_EVENT_COLOR = "";
    // Query tokens for QueryHandler
    private static final int TOKEN_QUERY_EVENT = 1;
    private static final int TOKEN_QUERY_CALENDARS = 1 << 1;
    private static final int TOKEN_QUERY_ATTENDEES = 1 << 2;
    private static final int TOKEN_QUERY_DUPLICATE_CALENDARS = 1 << 3;
    private static final int TOKEN_QUERY_REMINDERS = 1 << 4;
    private static final int TOKEN_QUERY_VISIBLE_CALENDARS = 1 << 5;
    private static final int TOKEN_QUERY_COLORS = 1 << 6;
    private static final int TOKEN_QUERY_ALL = TOKEN_QUERY_DUPLICATE_CALENDARS
            | TOKEN_QUERY_ATTENDEES | TOKEN_QUERY_CALENDARS | TOKEN_QUERY_EVENT
            | TOKEN_QUERY_REMINDERS | TOKEN_QUERY_VISIBLE_CALENDARS | TOKEN_QUERY_COLORS;

    public static final File EXPORT_SDCARD_DIRECTORY = new File(
            Environment.getExternalStorageDirectory(), "CalendarEvents");

    private enum ShareType {
        SDCARD,
        INTENT
    }

    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,
        Events.TITLE,
        Events.RRULE,
        Events.ALL_DAY,
        Events.CALENDAR_ID,
        Events.DTSTART,
        Events._SYNC_ID,
        Events.EVENT_TIMEZONE,
        Events.DESCRIPTION,
        Events.EVENT_LOCATION,
        Calendars.CALENDAR_ACCESS_LEVEL,
        Events.CALENDAR_COLOR,
        Events.EVENT_COLOR,
        Events.HAS_ATTENDEE_DATA,
        Events.ORGANIZER,
        Events.HAS_ALARM,
        Calendars.ALLOWED_REMINDERS,
        Events.CUSTOM_APP_PACKAGE,
        Events.CUSTOM_APP_URI,
        Events.DTEND,
        Events.DURATION,
    };
    private static final List<String> eventProjection = Arrays.asList(EVENT_PROJECTION);
    private static final int EVENT_INDEX_ID =
        eventProjection.indexOf(Events._ID);
    private static final int EVENT_INDEX_TITLE =
        eventProjection.indexOf(Events.TITLE);
    private static final int EVENT_INDEX_RRULE =
        eventProjection.indexOf(Events.RRULE);
    private static final int EVENT_INDEX_ALL_DAY =
        eventProjection.indexOf(Events.ALL_DAY);
    private static final int EVENT_INDEX_CALENDAR_ID =
        eventProjection.indexOf(Events.CALENDAR_ID);
    private static final int EVENT_INDEX_DTSTART =
        eventProjection.indexOf(Events.DTSTART);
    @SuppressWarnings("unused")
    private static final int EVENT_INDEX_SYNC_ID =
        eventProjection.indexOf(Events._SYNC_ID);
    private static final int EVENT_INDEX_EVENT_TIMEZONE =
        eventProjection.indexOf(Events.EVENT_TIMEZONE);
    private static final int EVENT_INDEX_DESCRIPTION =
        eventProjection.indexOf(Events.DESCRIPTION);
    private static final int EVENT_INDEX_EVENT_LOCATION =
        eventProjection.indexOf(Events.EVENT_LOCATION);
    private static final int EVENT_INDEX_ACCESS_LEVEL =
        eventProjection.indexOf(Calendars.CALENDAR_ACCESS_LEVEL);
    private static final int EVENT_INDEX_CALENDAR_COLOR =
        eventProjection.indexOf(Events.CALENDAR_COLOR);
    private static final int EVENT_INDEX_EVENT_COLOR =
        eventProjection.indexOf(Events.EVENT_COLOR);
    private static final int EVENT_INDEX_HAS_ATTENDEE_DATA =
        eventProjection.indexOf(Events.HAS_ATTENDEE_DATA);
    private static final int EVENT_INDEX_ORGANIZER =
        eventProjection.indexOf(Events.ORGANIZER);
    private static final int EVENT_INDEX_HAS_ALARM =
        eventProjection.indexOf(Events.HAS_ALARM);
    private static final int EVENT_INDEX_ALLOWED_REMINDERS =
        eventProjection.indexOf(Calendars.ALLOWED_REMINDERS);
    private static final int EVENT_INDEX_CUSTOM_APP_PACKAGE =
        eventProjection.indexOf(Events.CUSTOM_APP_PACKAGE);
    private static final int EVENT_INDEX_CUSTOM_APP_URI =
        eventProjection.indexOf(Events.CUSTOM_APP_URI);
    private static final int EVENT_INDEX_DTEND =
        eventProjection.indexOf(Events.DTEND);
    private static final int EVENT_INDEX_DURATION =
        eventProjection.indexOf(Events.DURATION);

    private static final String[] ATTENDEES_PROJECTION = new String[] {
        Attendees._ID,                      // 0
        Attendees.ATTENDEE_NAME,            // 1
        Attendees.ATTENDEE_EMAIL,           // 2
        Attendees.ATTENDEE_RELATIONSHIP,    // 3
        Attendees.ATTENDEE_STATUS,          // 4
        Attendees.ATTENDEE_IDENTITY,        // 5
        Attendees.ATTENDEE_ID_NAMESPACE     // 6
    };
    private static final int ATTENDEES_INDEX_ID = 0;
    private static final int ATTENDEES_INDEX_NAME = 1;
    private static final int ATTENDEES_INDEX_EMAIL = 2;
    private static final int ATTENDEES_INDEX_RELATIONSHIP = 3;
    private static final int ATTENDEES_INDEX_STATUS = 4;
    private static final int ATTENDEES_INDEX_IDENTITY = 5;
    private static final int ATTENDEES_INDEX_ID_NAMESPACE = 6;
    private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=?";
    private static final String ATTENDEES_SORT_ORDER = Attendees.ATTENDEE_NAME + " ASC, "
            + Attendees.ATTENDEE_EMAIL + " ASC";
    private static final int FADE_IN_TIME = 300;   // in milliseconds
    private static final int LOADING_MSG_DELAY = 600;   // in milliseconds
    private static final int LOADING_MSG_MIN_DISPLAY_TIME = 600;
    private static float mScale = 0; // Used for supporting different screen densities
    private static int mCustomAppIconSize = 32;
    private static int mDialogWidth = 500;
    private static int mDialogHeight = 600;
    private static int DIALOG_TOP_MARGIN = 8;


    private final ArrayList<LinearLayout> mReminderViews = new ArrayList<>(0);
    public ArrayList<ReminderEntry> mReminders;
    public ArrayList<ReminderEntry> mOriginalReminders = new ArrayList<>();
    public ArrayList<ReminderEntry> mUnsupportedReminders = new ArrayList<>();
    ArrayList<Attendee> mAcceptedAttendees = new ArrayList<>();
    ArrayList<Attendee> mDeclinedAttendees = new ArrayList<>();
    ArrayList<Attendee> mTentativeAttendees = new ArrayList<>();
    ArrayList<Attendee> mNoResponseAttendees = new ArrayList<>();
    private final Context mContext;
    private int mWindowStyle;
    private int mCurrentQuery = 0;
    private View mView;
    private long mEventId; // not a valid index
    private Cursor mEventCursor;
    private Cursor mAttendeesCursor;
    private Cursor mCalendarsCursor;
    private long mStartMillis;
    private long mEndMillis;
    private boolean mAllDay;
    private boolean mHasAttendeeData;
    private String mEventOrganizerEmail;
    private String mEventOrganizerDisplayName = "";
    private boolean mIsOrganizer;
    private long mCalendarOwnerAttendeeId = EditEventHelper.ATTENDEE_ID_NONE;
    private boolean mOwnerCanRespond;
    private String mSyncAccountName;
    private String mCalendarOwnerAccount;
    private boolean mCanModifyCalendar;
    private boolean mCanModifyEvent;
    private boolean mIsBusyFreeCalendar;
    private int mNumOfAttendees;
    private EditResponseHelper mEditResponseHelper;
    private boolean mDeleteDialogVisible = false;
    private DeleteEventHelper mDeleteHelper;
    private int mOriginalAttendeeResponse;
    private final int mAttendeeResponseFromIntent;
    private int mUserSetResponse = Attendees.ATTENDEE_STATUS_NONE;
    private int mWhichEvents = -1;
    // Used as the temporary response until the dialog is confirmed. It is also
    // able to be used as a state marker for configuration changes.
    private int mTentativeUserSetResponse = Attendees.ATTENDEE_STATUS_NONE;
    private boolean mIsRepeating;
    private boolean mHasAlarm;
    private String mCalendarAllowedReminders;
    // Used to prevent saving changes in event if it is being deleted.
    private boolean mEventDeletionStarted = false;
    private TextView mTitle;
    private TextView mWhenDateTime;
    private TextView mWhere;
    private TextView mDesc;
    private AttendeesView mLongAttendees;
    private Button emailAttendeesButton;
    private Menu mMenu = null;
    private View mHeadlines;
    private ScrollView mScrollView;
    private View mLoadingMsgView;
    private ObjectAnimator mAnimateAlpha;
    private long mLoadingMsgStartTime;
    private final Runnable mLoadingMsgAlphaUpdater = new Runnable() {
        @Override
        public void run() {
            // Since this is run after a delay, make sure to only show the message
            // if the event's data is not shown yet.
            if (!mAnimateAlpha.isRunning() && mScrollView.getAlpha() == 0) {
                mLoadingMsgStartTime = System.currentTimeMillis();
                mLoadingMsgView.setAlpha(1);
            }
        }
    };
    private EventColorPickerDialog mColorPickerDialog;
    private final SparseArray<String> mDisplayColorKeyMap = new SparseArray<>();
    private int[] mColors;
    private int mOriginalColor = -1;
    private boolean mOriginalColorInitialized = false;
    private int mCalendarColor = -1;
    private boolean mCalendarColorInitialized = false;
    private int mCurrentColor = -1;
    private boolean mCurrentColorInitialized = false;
    private String mCurrentColorKey = NO_EVENT_COLOR;
    private boolean mNoCrossFade = false;  // Used to prevent repeated cross-fade
    private RadioGroup mResponseRadioGroup;
    private int mDefaultReminderMinutes;
    private boolean mUserModifiedReminders = false;
    /**
     * Contents of the "minutes" spinner.  This has default values from the XML file,
     * augmented with any additional values that were already associated with the event.
     */
    private ArrayList<Integer> mReminderMinuteValues;
    private ArrayList<String> mReminderMinuteLabels;
    /**
     * Contents of the "methods" spinner.  The "values" list specifies the method constant
     * (e.g. {@link Reminders#METHOD_ALERT}) associated with the labels.  Any methods that
     * aren't allowed by the Calendar will be removed.
     */
    private ArrayList<Integer> mReminderMethodValues;
    private ArrayList<String> mReminderMethodLabels;
    private AsyncQueryService mService;
    private OnItemSelectedListener mReminderChangeListener;
    private boolean mIsDialog;
    private boolean mIsPaused = true;
    private boolean mDismissOnResume = false;
    private final Runnable onDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (EventInfoFragment.this.mIsPaused) {
                mDismissOnResume = true;
                return;
            }
            if (EventInfoFragment.this.isVisible()) {
                EventInfoFragment.this.dismiss();
            }
        }
    };
    private int mX = -1;
    private int mY = -1;
    private int mMinTop;         // Dialog cannot be above this location
    private boolean mIsTabletConfig;
    private Activity mActivity;
    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            updateEvent(mView);
        }
    };
    private CalendarController mController;
    private final DynamicTheme mDynamicTheme = new DynamicTheme();


    @SuppressWarnings("deprecation")
    public EventInfoFragment(
        Context context, long eventId, long startMillis, long endMillis,
        int attendeeResponse, boolean isDialog, int windowStyle,
        ArrayList<ReminderEntry> reminders)
    {
        mContext = context;
        Resources r = context.getResources();
        if (mScale == 0) {
            mScale = context.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                mCustomAppIconSize *= mScale;
                if (isDialog) {
                    DIALOG_TOP_MARGIN *= mScale;
                }
            }
        }
        if (isDialog) {
            setDialogSize(r);
        }
        mIsDialog = isDialog;

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        mStartMillis = startMillis;
        mEndMillis = endMillis;
        mAttendeeResponseFromIntent = attendeeResponse;
        mWindowStyle = windowStyle;

        // Pass in null if no reminders are being specified.
        // This may be used to explicitly show certain reminders already known
        // about, such as during configuration changes.
        mReminders = reminders;
        mEventId = eventId;
    }

    public static int getResponseFromButtonId(int buttonId) {
        int response;
        if (buttonId == R.id.response_yes) {
            response = Attendees.ATTENDEE_STATUS_ACCEPTED;
        } else if (buttonId == R.id.response_maybe) {
            response = Attendees.ATTENDEE_STATUS_TENTATIVE;
        } else if (buttonId == R.id.response_no) {
            response = Attendees.ATTENDEE_STATUS_DECLINED;
        } else {
            response = Attendees.ATTENDEE_STATUS_NONE;
        }
        return response;
    }

    public static int findButtonIdForResponse(int response) {
        int buttonId;
        switch (response) {
            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                buttonId = R.id.response_yes;
                break;
            case Attendees.ATTENDEE_STATUS_TENTATIVE:
                buttonId = R.id.response_maybe;
                break;
            case Attendees.ATTENDEE_STATUS_DECLINED:
                buttonId = R.id.response_no;
                break;
            default:
                buttonId = -1;
        }
        return buttonId;
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
        String[] labels = r.getStringArray(resNum);
        return new ArrayList<>(Arrays.asList(labels));
    }

    private void sendAccessibilityEventIfQueryDone(int token) {
        mCurrentQuery |= token;
        if (mCurrentQuery == TOKEN_QUERY_ALL) {
            sendAccessibilityEvent();
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
        // Ensure that mIsTabletConfig is set before creating the menu.
        mIsTabletConfig = Utils.getConfigBool(mActivity, R.bool.tablet_config);
        mController = CalendarController.getInstance(mActivity);
        mController.registerActionHandler(R.layout.event_info, this);
        mEditResponseHelper = new EditResponseHelper(activity);
        mEditResponseHelper.setDismissListener(
            new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    // If the user dismisses the dialog (without hitting OK),
                    // then we want to revert the selection that opened the dialog.
                    if (mEditResponseHelper.getWhichEvents() != -1) {
                        mUserSetResponse = mTentativeUserSetResponse;
                        mWhichEvents = mEditResponseHelper.getWhichEvents();
                    } else {
                        // Revert the attending response radio selection to whatever
                        // was selected prior to this selection (possibly nothing).
                        int oldResponse;
                        if (mUserSetResponse != Attendees.ATTENDEE_STATUS_NONE) {
                            oldResponse = mUserSetResponse;
                        } else {
                            oldResponse = mOriginalAttendeeResponse;
                        }
                        int buttonToCheck = findButtonIdForResponse(oldResponse);

                        if (mResponseRadioGroup != null) {
                            mResponseRadioGroup.check(buttonToCheck);
                        }

                        // If the radio group is being cleared, also clear the
                        // dialog's selection of which events should be included
                        // in this response.
                        if (buttonToCheck == -1) {
                            mEditResponseHelper.setWhichEvents(-1);
                        }
                    }

                    // Since OnPause will force the dialog to dismiss, do
                    // not change the dialog status
                    if (!mIsPaused) {
                        mTentativeUserSetResponse = Attendees.ATTENDEE_STATUS_NONE;
                    }
                }
            });

        if (mAttendeeResponseFromIntent != Attendees.ATTENDEE_STATUS_NONE) {
            mEditResponseHelper.setWhichEvents(UPDATE_ALL);
            mWhichEvents = mEditResponseHelper.getWhichEvents();
        }
        mService = CalendarApplication.getAsyncQueryService();
        if (!mIsDialog) {
            setHasOptionsMenu(true);
        }
    }

    // onCreate is called after onAttach, but we don't override it.

    // onCreateView is called after onAttach and onCreate
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            mIsDialog = savedInstanceState.getBoolean(
                BUNDLE_KEY_IS_DIALOG, false);
            mWindowStyle = savedInstanceState.getInt(
                BUNDLE_KEY_WINDOW_STYLE, DIALOG_WINDOW_STYLE);
            mDeleteDialogVisible = savedInstanceState.getBoolean(
                BUNDLE_KEY_DELETE_DIALOG_VISIBLE,false);
            mCalendarColor = savedInstanceState.getInt(
                BUNDLE_KEY_CALENDAR_COLOR);
            mCalendarColorInitialized = savedInstanceState.getBoolean(
                BUNDLE_KEY_CALENDAR_COLOR_INIT);
            mOriginalColor = savedInstanceState.getInt(BUNDLE_KEY_ORIGINAL_COLOR);
            mOriginalColorInitialized = savedInstanceState.getBoolean(
                BUNDLE_KEY_ORIGINAL_COLOR_INIT);
            mCurrentColor = savedInstanceState.getInt(BUNDLE_KEY_CURRENT_COLOR);
            mCurrentColorInitialized = savedInstanceState.getBoolean(
                BUNDLE_KEY_CURRENT_COLOR_INIT);
            mCurrentColorKey = savedInstanceState.getString(BUNDLE_KEY_CURRENT_COLOR_KEY);

            mTentativeUserSetResponse = savedInstanceState.getInt(
                BUNDLE_KEY_TENTATIVE_USER_RESPONSE,
                Attendees.ATTENDEE_STATUS_NONE);
            if (mTentativeUserSetResponse != Attendees.ATTENDEE_STATUS_NONE &&
                mEditResponseHelper != null) {
                // If the edit response helper dialog is open, we'll need to
                // know if either of the choices were selected.
                mEditResponseHelper.setWhichEvents(savedInstanceState.getInt(
                    BUNDLE_KEY_RESPONSE_WHICH_EVENTS, -1));
            }
            mUserSetResponse = savedInstanceState.getInt(
                BUNDLE_KEY_USER_SET_ATTENDEE_RESPONSE,
                Attendees.ATTENDEE_STATUS_NONE);
            if (mUserSetResponse != Attendees.ATTENDEE_STATUS_NONE) {
                // If the response was set by the user before a configuration
                // change, we'll need to know which choice was selected.
                mWhichEvents = savedInstanceState.getInt(
                    BUNDLE_KEY_RESPONSE_WHICH_EVENTS, -1);
            }

            mReminders = Utils.readRemindersFromBundle(savedInstanceState);
            if (mEventId < 0) {
                // restore event ID from bundle
                mEventId = savedInstanceState.getLong(BUNDLE_KEY_EVENT_ID);
                mStartMillis = savedInstanceState.getLong(BUNDLE_KEY_START_MILLIS);
                mEndMillis = savedInstanceState.getLong(BUNDLE_KEY_END_MILLIS);
            }
        }

        if (mWindowStyle == DIALOG_WINDOW_STYLE) {
            mView = inflater.inflate(
                R.layout.event_info_dialog, container, false);
        } else {
            mView = inflater.inflate(R.layout.event_info, container, false);
        }

        Toolbar myToolbar =  mView.findViewById(R.id.toolbar);
        AppCompatActivity activity = (AppCompatActivity)mActivity;
        if (myToolbar != null && activity != null) {
            activity.setSupportActionBar(myToolbar);
            activity.getSupportActionBar().setDisplayShowTitleEnabled(false);
            myToolbar.setNavigationIcon(R.drawable.ic_arrow_back);
        }

        mScrollView = mView.findViewById(R.id.event_info_scroll_view);
        mLoadingMsgView = mView.findViewById(R.id.event_info_loading_msg);
        mTitle = mView.findViewById(R.id.title);
        mWhenDateTime = mView.findViewById(R.id.when_datetime);
        mWhere = mView.findViewById(R.id.where);
        mDesc = mView.findViewById(R.id.description);
        mHeadlines = mView.findViewById(R.id.event_info_headline);
        mLongAttendees = mView.findViewById(R.id.long_attendee_list);

        mResponseRadioGroup = mView.findViewById(R.id.response_value);

        mAnimateAlpha = ObjectAnimator.ofFloat(
            mScrollView, "Alpha", 0, 1);
        mAnimateAlpha.setDuration(FADE_IN_TIME);
        mAnimateAlpha.addListener(new AnimatorListenerAdapter() {
            int defLayerType;

            @Override
            public void onAnimationStart(Animator animation) {
                // Use hardware layer for better performance during animation
                defLayerType = mScrollView.getLayerType();
                mScrollView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                // Ensure that the loading message is gone before showing the
                // event info
                mLoadingMsgView.removeCallbacks(mLoadingMsgAlphaUpdater);
                mLoadingMsgView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mScrollView.setLayerType(defLayerType, null);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mScrollView.setLayerType(defLayerType, null);
                // Do not cross fade after the first time
                mNoCrossFade = true;
            }
        });

        mLoadingMsgView.setAlpha(0);
        mScrollView.setAlpha(0);
        mLoadingMsgView.postDelayed(mLoadingMsgAlphaUpdater, LOADING_MSG_DELAY);

        // start loading the data

        mService.startQuery(TOKEN_QUERY_EVENT, this, ContentUris.withAppendedId(Events.CONTENT_URI, mEventId), EVENT_PROJECTION,
            null, null, null);

        View b = mView.findViewById(R.id.delete);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCanModifyCalendar) {
                    return;
                }
                mDeleteHelper =
                    new DeleteEventHelper(mActivity, mActivity,
                        !mIsDialog && !mIsTabletConfig /* exitWhenDone */);
                mDeleteHelper.setDeleteNotificationListener(EventInfoFragment.this);
                mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
                mDeleteDialogVisible = true;
                mDeleteHelper.delete(
                    mStartMillis, mEndMillis, mEventId, onDeleteRunnable);
            }
        });

        b = mView.findViewById(R.id.change_color);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCanModifyCalendar) {
                    return;
                }
                showEventColorPickerDialog();
            }
        });

        // Hide Edit/Delete buttons if in full screen mode on a phone
        if (!mIsDialog && !mIsTabletConfig || mWindowStyle == EventInfoFragment.FULL_WINDOW_STYLE) {
            mView.findViewById(R.id.event_info_buttons_container).setVisibility(View.GONE);
        }

        // Create a listener for the email guests button
        emailAttendeesButton = mView.findViewById(R.id.email_attendees_button);
        if (emailAttendeesButton != null) {
            emailAttendeesButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    emailAttendees();
                }
            });
        }

        // Create a listener for the add reminder button
        View reminderAddButton = mView.findViewById(R.id.reminder_add);
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addReminder();
                mUserModifiedReminders = true;
            }
        };
        reminderAddButton.setOnClickListener(addReminderOnClickListener);

        // Set reminders variables

        SharedPreferences prefs
            = GeneralPreferences.Companion.getSharedPreferences(mActivity);
        String defaultReminderString = prefs.getString(
            GeneralPreferences.KEY_DEFAULT_REMINDER,
            GeneralPreferences.NO_REMINDER_STRING);
        mDefaultReminderMinutes = Integer.parseInt(defaultReminderString);
        prepareReminders();

        return mView;
    }

    // onActivityCreated is called after onAttach, onCreate and onCreateView
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mReminderChangeListener = new OnItemSelectedListener() {
            @Override
            public void onItemSelected(
                AdapterView<?> parent, View view, int position, long id) {
                Integer prevValue = (Integer) parent.getTag();
                if (prevValue == null || prevValue != position) {
                    parent.setTag(position);
                    mUserModifiedReminders = true;
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }
        };

        if (savedInstanceState != null) {
            mIsDialog
                = savedInstanceState.getBoolean(BUNDLE_KEY_IS_DIALOG, false);
            mWindowStyle
                = savedInstanceState.getInt(BUNDLE_KEY_WINDOW_STYLE, DIALOG_WINDOW_STYLE);
        }

        if (mIsDialog) {
            applyDialogParams();
        }

        mDynamicTheme.onCreate(mActivity);
    }

    // onViewStateRestored is called after onActivityCreated, but we don't override it
    // onStart is called after onViewStateRestored, but we don't override it

    // onResume is called after onAttach, onCreate, onCreateView and onActivityCreated
    @Override
    public void onResume() {
        super.onResume();
        if (mIsDialog) {
            setDialogSize(getActivity().getResources());
            applyDialogParams();
        }
        mIsPaused = false;
        if (mDismissOnResume) {
            if (isVisible()) {
                dismiss();
            }
            return;
        }
        // Display the "delete confirmation" or "edit response helper" dialog if needed
        if (mDeleteDialogVisible) {
            mDeleteHelper = new DeleteEventHelper(
                mActivity, mActivity,
                !mIsDialog && !mIsTabletConfig /* exitWhenDone */);
            mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
            mDeleteHelper.delete(
                mStartMillis, mEndMillis, mEventId, onDeleteRunnable);
        } else if (mTentativeUserSetResponse != Attendees.ATTENDEE_STATUS_NONE) {
            int buttonId = findButtonIdForResponse(mTentativeUserSetResponse);
            mResponseRadioGroup.check(buttonId);
            mEditResponseHelper.showDialog(mEditResponseHelper.getWhichEvents());
        }
    }

    @SuppressLint("RtlHardcoded")
    private void applyDialogParams() {
        Dialog dialog = getDialog();
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams a = window.getAttributes();
        a.dimAmount = .4f;

        a.width = mDialogWidth;
        a.height = mDialogHeight;


        // On tablets , do smart positioning of dialog
        // On phones , use the whole screen

        if (mX != -1 || mY != -1) {
            a.x = mX - mDialogWidth / 2;
            a.y = mY - mDialogHeight / 2;
            if (a.y < mMinTop) {
                a.y = mMinTop + DIALOG_TOP_MARGIN;
            }
            a.gravity = Gravity.LEFT | Gravity.TOP;
        }
        window.setAttributes(a);
    }

    // This isn't used at present, but will be needed
    // to reinstate the tablet version
    @SuppressWarnings("unused")
    public void setDialogParams(int x, int y, int minTop) {
        mX = x;
        mY = y;
        mMinTop = minTop;
    }

    // Implements OnCheckedChangeListener
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        // If we haven't finished the return from the dialog yet, don't display.
        if (mTentativeUserSetResponse != Attendees.ATTENDEE_STATUS_NONE) {
            return;
        }

        // If this is not a repeating event, then don't display the dialog
        // asking which events to change.
        int response = getResponseFromButtonId(checkedId);
        if (!mIsRepeating) {
            mUserSetResponse = response;
            return;
        }

        // If the selection is the same as the original, then don't display the
        // dialog asking which events to change.
        if (checkedId == findButtonIdForResponse(mOriginalAttendeeResponse)) {
            mUserSetResponse = response;
            return;
        }

        // This is a repeating event. We need to ask the user if they mean to
        // change just this one instance or all instances.
        mTentativeUserSetResponse = response;
        mEditResponseHelper.showDialog(mWhichEvents);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mController.deregisterActionHandler(R.layout.event_info);
    }

    private void updateTitle() {
        Resources res = getActivity().getResources();
        if (mCanModifyCalendar && !mIsOrganizer) {
            getActivity().setTitle(res.getString(R.string.event_info_title_invite));
        } else {
            getActivity().setTitle(res.getString(R.string.event_info_title));
        }
    }

    /**
     * Initializes the event cursor, which is expected to point to the first
     * (and only) result from a query.
     * @return true if the cursor is empty.
     */
    private boolean initEventCursor() {
        if ((mEventCursor == null) || (mEventCursor.getCount() == 0)) {
            return true;
        }
        mEventCursor.moveToFirst();
        mEventId = mEventCursor.getInt(EVENT_INDEX_ID);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        mIsRepeating = !TextUtils.isEmpty(rRule);
        // mHasAlarm will be true if it was saved in the event already, or if
        // we've explicitly been provided reminders (e.g. during rotation).
        mHasAlarm =    (mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) == 1)
                    || (mReminders != null && mReminders.size() > 0);
        mCalendarAllowedReminders =  mEventCursor.getString(EVENT_INDEX_ALLOWED_REMINDERS);
        return false;
    }

    private void initAttendeesCursor(View view) {
        mOriginalAttendeeResponse = Attendees.ATTENDEE_STATUS_NONE;
        mCalendarOwnerAttendeeId = EditEventHelper.ATTENDEE_ID_NONE;
        mNumOfAttendees = 0;
        if (mAttendeesCursor != null) {
            mNumOfAttendees = mAttendeesCursor.getCount();
            if (mAttendeesCursor.moveToFirst()) {
                mAcceptedAttendees.clear();
                mDeclinedAttendees.clear();
                mTentativeAttendees.clear();
                mNoResponseAttendees.clear();

                do {
                    int status = mAttendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
                    String name = mAttendeesCursor.getString(ATTENDEES_INDEX_NAME);
                    String email = mAttendeesCursor.getString(ATTENDEES_INDEX_EMAIL);

                    if (mAttendeesCursor.getInt(ATTENDEES_INDEX_RELATIONSHIP) ==
                            Attendees.RELATIONSHIP_ORGANIZER) {

                        // Overwrites the one from Event table if available
                        if (!TextUtils.isEmpty(name)) {
                            mEventOrganizerDisplayName = name;
                            if (!mIsOrganizer) {
                                setVisibilityCommon(
                                    view, R.id.organizer_container, View.VISIBLE);
                                setTextCommon(
                                    view, R.id.organizer, mEventOrganizerDisplayName);
                            }
                        }
                    }

                    if (mCalendarOwnerAttendeeId == EditEventHelper.ATTENDEE_ID_NONE &&
                            mCalendarOwnerAccount.equalsIgnoreCase(email)) {
                        mCalendarOwnerAttendeeId
                            = mAttendeesCursor.getInt(ATTENDEES_INDEX_ID);
                        mOriginalAttendeeResponse
                            = mAttendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
                    } else {
                        String identity
                            = mAttendeesCursor.getString(ATTENDEES_INDEX_IDENTITY);
                        String idNamespace
                            = mAttendeesCursor.getString(ATTENDEES_INDEX_ID_NAMESPACE);

                        // Don't show your own status in the list because:
                        //  1) it doesn't make sense for event without other guests.
                        //  2) there's a spinner for that for events with guests.
                        switch(status) {
                            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                                mAcceptedAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_ACCEPTED, identity,
                                        idNamespace));
                                break;
                            case Attendees.ATTENDEE_STATUS_DECLINED:
                                mDeclinedAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_DECLINED, identity,
                                        idNamespace));
                                break;
                            case Attendees.ATTENDEE_STATUS_TENTATIVE:
                                mTentativeAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_TENTATIVE, identity,
                                        idNamespace));
                                break;
                            default:
                                mNoResponseAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_NONE, identity,
                                        idNamespace));
                        }
                    }
                } while (mAttendeesCursor.moveToNext());
                mAttendeesCursor.moveToFirst();

                updateAttendees();
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_EVENT_ID, mEventId);
        outState.putLong(BUNDLE_KEY_START_MILLIS, mStartMillis);
        outState.putLong(BUNDLE_KEY_END_MILLIS, mEndMillis);
        outState.putBoolean(BUNDLE_KEY_IS_DIALOG, mIsDialog);
        outState.putInt(BUNDLE_KEY_WINDOW_STYLE, mWindowStyle);
        outState.putBoolean(BUNDLE_KEY_DELETE_DIALOG_VISIBLE, mDeleteDialogVisible);
        outState.putInt(BUNDLE_KEY_CALENDAR_COLOR, mCalendarColor);
        outState.putBoolean(BUNDLE_KEY_CALENDAR_COLOR_INIT, mCalendarColorInitialized);
        outState.putInt(BUNDLE_KEY_ORIGINAL_COLOR, mOriginalColor);
        outState.putBoolean(BUNDLE_KEY_ORIGINAL_COLOR_INIT, mOriginalColorInitialized);
        outState.putInt(BUNDLE_KEY_CURRENT_COLOR, mCurrentColor);
        outState.putBoolean(BUNDLE_KEY_CURRENT_COLOR_INIT, mCurrentColorInitialized);
        outState.putString(BUNDLE_KEY_CURRENT_COLOR_KEY, mCurrentColorKey);

        // We'll need the temporary response for configuration changes.
        outState.putInt(BUNDLE_KEY_TENTATIVE_USER_RESPONSE, mTentativeUserSetResponse);
        if (mTentativeUserSetResponse != Attendees.ATTENDEE_STATUS_NONE &&
                mEditResponseHelper != null) {
            outState.putInt(BUNDLE_KEY_RESPONSE_WHICH_EVENTS,
                    mEditResponseHelper.getWhichEvents());
        }

        // Save the current response.
        int response;
        if (mAttendeeResponseFromIntent != Attendees.ATTENDEE_STATUS_NONE) {
            response = mAttendeeResponseFromIntent;
        } else {
            response = mOriginalAttendeeResponse;
        }
        outState.putInt(BUNDLE_KEY_ATTENDEE_RESPONSE, response);
        if (mUserSetResponse != Attendees.ATTENDEE_STATUS_NONE) {
            response = mUserSetResponse;
            outState.putInt(BUNDLE_KEY_USER_SET_ATTENDEE_RESPONSE, response);
            outState.putInt(BUNDLE_KEY_RESPONSE_WHICH_EVENTS, mWhichEvents);
        }

        // Save the reminders.
        mReminders = EventViewUtils.reminderItemsToReminders(mReminderViews,
                mReminderMinuteValues, mReminderMethodValues);
        int numReminders = mReminders.size();
        ArrayList<Integer> reminderMinutes = new ArrayList<>(numReminders);
        ArrayList<Integer> reminderMethods = new ArrayList<>(numReminders);
        for (ReminderEntry reminder : mReminders) {
            reminderMinutes.add(reminder.getMinutes());
            reminderMethods.add(reminder.getMethod());
        }
        outState.putIntegerArrayList(BUNDLE_KEY_REMINDER_MINUTES, reminderMinutes);
        outState.putIntegerArrayList(BUNDLE_KEY_REMINDER_METHODS, reminderMethods);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Show color/edit/delete buttons only in non-dialog configuration
        if (   (!mIsDialog && !mIsTabletConfig)
            || (mWindowStyle == EventInfoFragment.FULL_WINDOW_STYLE))
        {
            inflater.inflate(R.menu.event_info_title_bar, menu);
            mMenu = menu;
            updateMenu();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // If we're a dialog we don't want to handle menu buttons
        if (mIsDialog) {
            return false;
        }
        // Handles option menu selections:
        // Home button - close event info activity and start the main calendar
        // one
        // Edit button - start the event edit activity and close the info
        // activity
        // Delete button - start a delete query that calls a runnable that close
        // the info activity

        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            Utils.returnToCalendarHome(mActivity);
            mActivity.finish();
            return true;
        } else if (itemId == R.id.info_action_edit) {
            doEdit();
            mActivity.finish();
        } else if (itemId == R.id.info_action_delete) {
            mDeleteHelper = new DeleteEventHelper(mActivity, mActivity, true);
            mDeleteHelper.setDeleteNotificationListener(EventInfoFragment.this);
            mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
            mDeleteDialogVisible = true;
            mDeleteHelper.delete(
                mStartMillis, mEndMillis, mEventId, onDeleteRunnable);
        } else if (itemId == R.id.info_action_change_color) {
            showEventColorPickerDialog();
        } else if (itemId == R.id.info_action_share_event) {
            shareEvent(ShareType.INTENT);
        } else if (itemId == R.id.info_action_export) {
            shareEvent(ShareType.SDCARD);
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Generates an .ics formatted file with the event info
     * and launches intent chooser to share said file.
     */
    @SuppressLint("SetWorldReadable")
    private void shareEvent(ShareType type) {
        // Create the respective ICalendar objects from the event info
        VCalendar calendar = new VCalendar();
        CalendarEventModel event = new CalendarEventModel();
        mEventCursor.moveToFirst();
        event.mId = mEventId;
        event.mEventStart = mStartMillis;
        event.mEventEnd = mEndMillis;
        event.mTimezoneEnd = event.mTimezoneStart =
            mEventCursor.getString(EVENT_INDEX_EVENT_TIMEZONE);
        event.mAllDay = mAllDay;
        event.mTitle = mEventCursor.getString(EVENT_INDEX_TITLE);
        event.mLocation = mEventCursor.getString(EVENT_INDEX_EVENT_LOCATION);
        event.mDescription = mEventCursor.getString(EVENT_INDEX_DESCRIPTION);
        event.mOrganizer = mEventOrganizerEmail;
        event.mOrganizerDisplayName = mEventOrganizerDisplayName;

        // Add Attendees to event
        for (Attendee attendee : mAcceptedAttendees) {
            event.mAttendeesList.put(attendee.mName, attendee);
        }

        for (Attendee attendee : mDeclinedAttendees) {
            event.mAttendeesList.put(attendee.mName, attendee);
        }

        for (Attendee attendee : mTentativeAttendees) {
            event.mAttendeesList.put(attendee.mName, attendee);
        }

        for (Attendee attendee : mNoResponseAttendees) {
            event.mAttendeesList.put(attendee.mName, attendee);
        }

        // Compose all of the ICalendar objects
        CalendarApplication.mEvents.add(event);

        // Create and share ics file
        boolean isShareSuccessful = false;
        try {
            // Event title serves as the file name prefix
            String filePrefix = event.mTitle;
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
                dir = EXPORT_SDCARD_DIRECTORY;
                if (!dir.exists()) {
                    dir.mkdir();
                }
            } else {
                dir = mActivity.getExternalCacheDir();
            }

            File inviteFile = IcalendarUtils.createTempFile(
                filePrefix, ".ics", dir);

            if (IcalendarUtils.writeCalendarToFile(calendar, inviteFile)) {
                if (type == ShareType.INTENT) {
                    // Set world-readable
                    inviteFile.setReadable(true, false);
                    Uri icsFile = FileProvider.getUriForFile(getActivity(),
                            BuildConfig.APPLICATION_ID + ".provider", inviteFile);
                    Intent shareIntent = new Intent();
                    shareIntent.setAction(Intent.ACTION_SEND);
                    shareIntent.putExtra(Intent.EXTRA_STREAM, icsFile);
                    // The ics file is sent as an extra,
                    // the receiving application decides whether to parse the file
                    // to extract calendar events or treat it as a regular file
                    shareIntent.setType("application/octet-stream");

                    Intent chooserIntent = Intent.createChooser(shareIntent,
                            getResources().getString(R.string.cal_share_intent_title));

                    // The MMS app only responds to "text/x-vcalendar" so we create
                    // a chooser intent that includes the targeted mms intent
                    // + any that respond to the above general
                    // purpose "application/octet-stream" intent.
                    File vcsInviteFile = File.createTempFile(filePrefix, ".vcs",
                            mActivity.getExternalCacheDir());

                    // For now, we are duplicating ics file and using that as the vcs file
                    // TODO: revisit above
                    if (IcalendarUtils.copyFile(inviteFile, vcsInviteFile)) {
                        Uri vcsFile = FileProvider.getUriForFile(getActivity(),
                            BuildConfig.APPLICATION_ID + ".provider",
                            vcsInviteFile);
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
            // else error is handled below because isShareSuccessful is still false
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (!isShareSuccessful) {
            Log.e(TAG, "Couldn't generate ics file");
            Toast.makeText(
                mActivity, R.string.error_generating_ics, Toast.LENGTH_SHORT).show();
        }
    }

    private void showEventColorPickerDialog() {
        if (mColorPickerDialog == null) {
            mColorPickerDialog = EventColorPickerDialog.newInstance(
                mContext, mColors, mCurrentColor,
                    mCalendarColor, mIsTabletConfig);
            mColorPickerDialog.setOnColorSelectedListener(this);
        }
        mColorPickerDialog.show();
    }

    private boolean saveEventColor() {
        if (mCurrentColor == mOriginalColor) {
            return false;
        }

        ContentValues values = new ContentValues();
        if (mCurrentColor != mCalendarColor) {
            values.put(Events.EVENT_COLOR_KEY, mCurrentColorKey);
        } else {
            values.put(Events.EVENT_COLOR_KEY, NO_EVENT_COLOR);
        }
        mService.startUpdate(null, this,
            ContentUris.withAppendedId(Events.CONTENT_URI, mEventId),
            values, null, null);
        return true;
    }

    @Override
    public void onPause() {
        mIsPaused = true;
        super.onPause();
        // Remove event deletion alert box since it is being rebuild in the OnResume
        // This is done to get the same behavior on OnResume since the AlertDialog
        // is gone on rotation but not if you press the HOME key
        if (mDeleteDialogVisible && mDeleteHelper != null) {
            mDeleteHelper.dismissAlertDialog();
            mDeleteHelper = null;
        }
        if (mTentativeUserSetResponse != Attendees.ATTENDEE_STATUS_NONE
            && mEditResponseHelper != null) {
            mEditResponseHelper.dismissAlertDialog();
        }
    }

    @Override
    public void onStop() {
        Activity act = getActivity();
        if (!mEventDeletionStarted && act != null && !act.isChangingConfigurations()) {

            boolean responseSaved = saveResponse();
            boolean eventColorSaved = saveEventColor();
            if (saveReminders() || responseSaved || eventColorSaved) {
                Toast.makeText(getActivity(), R.string.saving_event, Toast.LENGTH_SHORT).show();
            }
        }
        super.onStop();
    }

    // onDestroyView is called here, but we don't override it

    @Override
    public void onDestroy() {
        if (mEventCursor != null) {
            mEventCursor.close();
        }
        if (mCalendarsCursor != null) {
            mCalendarsCursor.close();
        }
        if (mAttendeesCursor != null) {
            mAttendeesCursor.close();
        }
        super.onDestroy();
    }

    /**
     * Asynchronously saves the response to an invitation if the user changed
     * the response. Returns true if the database will be updated.
     *
     * @return true if the database will be changed
     */
    private boolean saveResponse() {
        if (mAttendeesCursor == null || mEventCursor == null) {
            return false;
        }

        int status = getResponseFromButtonId(
                mResponseRadioGroup.getCheckedRadioButtonId());
        if (status == Attendees.ATTENDEE_STATUS_NONE) {
            return false;
        }

        // If the status has not changed, then don't update the database
        if (status == mOriginalAttendeeResponse) {
            return false;
        }

        // If we never got an owner attendee id we can't set the status
        if (mCalendarOwnerAttendeeId == EditEventHelper.ATTENDEE_ID_NONE) {
            return false;
        }

        if (!mIsRepeating) {
            // This is a non-repeating event
            updateResponse(mEventId, mCalendarOwnerAttendeeId, status);
            mOriginalAttendeeResponse = status;
            return true;
        }

        if (DEBUG) {
            Log.d(TAG, "Repeating event: mWhichEvents=" + mWhichEvents);
        }
        // This is a repeating event
        switch (mWhichEvents) {
            case -1:
                return false;
            case UPDATE_SINGLE:
                createExceptionResponse(mEventId, status);
                mOriginalAttendeeResponse = status;
                return true;
            case UPDATE_ALL:
                updateResponse(mEventId, mCalendarOwnerAttendeeId, status);
                mOriginalAttendeeResponse = status;
                return true;
            default:
                Log.e(TAG, "Unexpected choice for updating invitation response");
                break;
        }
        return false;
    }

    private void updateResponse(long eventId, long attendeeId, int status) {
        // Update the attendee status in the attendees table.  the provider
        // takes care of updating the self attendance status.
        ContentValues values = new ContentValues();

        if (!TextUtils.isEmpty(mCalendarOwnerAccount)) {
            values.put(Attendees.ATTENDEE_EMAIL, mCalendarOwnerAccount);
        }
        values.put(Attendees.ATTENDEE_STATUS, status);
        values.put(Attendees.EVENT_ID, eventId);

        Uri uri = ContentUris.withAppendedId(Attendees.CONTENT_URI, attendeeId);

        mService.startUpdate(null, this, uri, values,
                null, null);
    }

    /**
     * Creates an exception to a recurring event.
     * The only change we're making is to the "self attendee status" value.
     * The provider will take care of updating the corresponding
     * Attendees.attendeeStatus entry.
     *
     * @param eventId The recurring event.
     * @param status The new value for selfAttendeeStatus.
     */
    private void createExceptionResponse(long eventId, int status) {
        ContentValues values = new ContentValues();
        values.put(Events.ORIGINAL_INSTANCE_TIME, mStartMillis);
        values.put(Events.SELF_ATTENDEE_STATUS, status);
        values.put(Events.STATUS, Events.STATUS_CONFIRMED);

        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        Uri exceptionUri = Uri.withAppendedPath(Events.CONTENT_EXCEPTION_URI,
                String.valueOf(eventId));
        ops.add(
            ContentProviderOperation.newInsert(exceptionUri).withValues(values).build());

        mService.startBatch(
            null, this,  CalendarContract.AUTHORITY, ops);
    }

    private void doEdit() {
        Context c = getActivity();
        // This ensures that we aren't in the process of closing and have been
        // unattached already
        if (c != null) {
            Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
            Intent intent = new Intent(Intent.ACTION_EDIT, uri);
            intent.setClass(mActivity, EditEventActivity.class);
            intent.putExtra(EXTRA_EVENT_BEGIN_TIME, mStartMillis);
            intent.putExtra(EXTRA_EVENT_END_TIME, mEndMillis);
            intent.putExtra(EXTRA_EVENT_ALL_DAY, mAllDay);
            intent.putExtra(Events.EVENT_COLOR, mCurrentColor);
            intent.putExtra(EditEventActivity.EXTRA_EVENT_REMINDERS, EventViewUtils
                    .reminderItemsToReminders(mReminderViews, mReminderMinuteValues,
                    mReminderMethodValues));
            intent.putExtra(EVENT_EDIT_ON_LAUNCH, true);
            startActivity(intent);
        }
    }

    // suppression needed because textview.onTouch oasses the event to another view
    @SuppressLint("ClickableViewAccessibility")
    private void updateEvent(View view) {
        if (mEventCursor == null || view == null) {
            return;
        }

        Context context = view.getContext();
        if (context == null) {
            return;
        }

        // 3rd parties might not have specified the start/end time when firing the
        // Events.CONTENT_URI intent.  Update these with values read from the db.
        if (mStartMillis == 0 && mEndMillis == 0) {
            mStartMillis = mEventCursor.getLong(EVENT_INDEX_DTSTART);
            mEndMillis = mEventCursor.getLong(EVENT_INDEX_DTEND);
            if (mEndMillis == 0) {
                String duration = mEventCursor.getString(EVENT_INDEX_DURATION);
                if (!TextUtils.isEmpty(duration)) {
                    try {
                        Duration d = new Duration();
                        d.parse(duration);
                        long endMillis = mStartMillis + d.getMillis();
                        if (endMillis >= mStartMillis) {
                            mEndMillis = endMillis;
                        } else {
                            Log.d(TAG, "Invalid duration string: " + duration);
                        }
                    } catch (DateException e) {
                        Log.d(TAG, "Error parsing duration string " + duration, e);
                    }
                }
                if (mEndMillis == 0) {
                    mEndMillis = mStartMillis;
                }
            }
        }

        mAllDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        String location = mEventCursor.getString(EVENT_INDEX_EVENT_LOCATION);
        String description = mEventCursor.getString(EVENT_INDEX_DESCRIPTION);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        String eventTimezone = mEventCursor.getString(EVENT_INDEX_EVENT_TIMEZONE);

        mHeadlines.setBackgroundColor(mCurrentColor);

        String eventName = mEventCursor.getString(EVENT_INDEX_TITLE);
        if (eventName == null || eventName.length() == 0) {
            eventName = getActivity().getString(R.string.no_title_label);
        }
        setTextCommon(view, R.id.title, eventName);

        // When
        // Set the date and repeats (if any)
        String localTimezone = Utils.getTimeZone(mActivity, mTZUpdater);

        Resources resources = context.getResources();
        String displayedDatetime = Utils.getDisplayedDatetime(mStartMillis, mEndMillis,
                System.currentTimeMillis(), localTimezone, mAllDay, context);

        String displayedTimezone = null;
        if (!mAllDay) {
            displayedTimezone = Utils.getDisplayedTimezone(mStartMillis, localTimezone,
                    eventTimezone);
        }
        // Display the datetime.  Make the timezone (if any) transparent.
        if (displayedTimezone == null) {
            setTextCommon(view, R.id.when_datetime, displayedDatetime);
        } else {
            int timezoneIndex = displayedDatetime.length();
            displayedDatetime += "  " + displayedTimezone;
            SpannableStringBuilder sb = new SpannableStringBuilder(displayedDatetime);
            ForegroundColorSpan transparentColorSpan = new ForegroundColorSpan(
                    resources.getColor(R.color.event_info_headline_transparent_color));
            sb.setSpan(transparentColorSpan, timezoneIndex, displayedDatetime.length(),
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            setTextCommon(view, R.id.when_datetime, sb);
        }

        // Display the repeat string (if any)
        String repeatString = null;
        if (!TextUtils.isEmpty(rRule)) {
            EventRecurrence eventRecurrence = new EventRecurrence();
            eventRecurrence.parse(rRule);
            Time date = new Time(localTimezone);
            date.set(mStartMillis);
            if (mAllDay) {
                date.timezone = Time.TIMEZONE_UTC;
            }
            eventRecurrence.setStartDate(date);
            repeatString = EventRecurrenceFormatter.getRepeatString(mActivity, resources,
                    eventRecurrence, true);
        }
        if (repeatString == null) {
            view.findViewById(R.id.when_repeat).setVisibility(View.GONE);
        } else {
            setTextCommon(view, R.id.when_repeat, repeatString);
        }

        // Organizer view is setup in the updateCalendar method

        // Where
        if (location == null || location.trim().length() == 0) {
            setVisibilityCommon(view, R.id.where, View.GONE);
        } else {
            final TextView textView = mWhere;
            if (textView != null) {
                textView.setAutoLinkMask(0);
                textView.setText(location.trim());
                try {
                    textView.setText(Utils.extendedLinkify(
                        textView.getText().toString(), true));

                    // Linkify.addLinks() sets the TextView movement method
                    // if it finds any links. We must do the same here,
                    // in case linkify by itself did not find any.
                    // (This is cloned from Linkify.addLinkMovementMethod().)
                    MovementMethod mm = textView.getMovementMethod();
                    if (!(mm instanceof LinkMovementMethod)) {
                        if (textView.getLinksClickable()) {
                            textView.setMovementMethod(LinkMovementMethod.getInstance());
                        }
                    }
                } catch (Exception ex) {
                    // unexpected
                    Log.e(TAG, "Linkification failed", ex);
                }

                textView.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        try {
                            return v.onTouchEvent(event);
                        } catch (ActivityNotFoundException e) {
                            // ignore
                            return true;
                        }
                    }
                });
            }
        }

        // Description
        if (description != null && description.length() != 0) {
            mDesc.setText(description);
        }

        // Launch Custom App
        updateCustomAppButton();
    }

    private void updateCustomAppButton() {
        buttonSetup: {
            final Button launchButton= mView.findViewById(R.id.launch_custom_app_button);
            if (launchButton == null)
                break buttonSetup;

            final String customAppPackage
                = mEventCursor.getString(EVENT_INDEX_CUSTOM_APP_PACKAGE);
            final String customAppUri = mEventCursor.getString(EVENT_INDEX_CUSTOM_APP_URI);

            if (TextUtils.isEmpty(customAppPackage) || TextUtils.isEmpty(customAppUri))
                break buttonSetup;

            PackageManager pm = mActivity.getPackageManager();
            if (pm == null)
                break buttonSetup;

            ApplicationInfo info;
            try {
                info = pm.getApplicationInfo(customAppPackage, 0);
            } catch (NameNotFoundException e) {
                break buttonSetup;
            }

            Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
            final Intent intent
                = new Intent(CalendarContract.ACTION_HANDLE_CUSTOM_EVENT, uri);
            intent.setPackage(customAppPackage);
            intent.putExtra(CalendarContract.EXTRA_CUSTOM_APP_URI, customAppUri);
            intent.putExtra(EXTRA_EVENT_BEGIN_TIME, mStartMillis);

            // See if we have a taker for our intent
            if (pm.resolveActivity(intent, 0) == null)
                break buttonSetup;

            Drawable icon = pm.getApplicationIcon(info);

            Drawable[] d = launchButton.getCompoundDrawables();
            icon.setBounds(0, 0, mCustomAppIconSize, mCustomAppIconSize);
            launchButton.setCompoundDrawables(icon, d[1], d[2], d[3]);

            CharSequence label = pm.getApplicationLabel(info);
            if (label.length() != 0) {
                launchButton.setText(label);
            }

            // Launch custom app
            launchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivityForResult(intent, 0);
                    } catch (ActivityNotFoundException e) {
                        // Shouldn't happen as we checked it already
                        setVisibilityCommon(
                            mView, R.id.launch_custom_app_container, View.GONE);
                    }
                }
            });

            setVisibilityCommon(mView, R.id.launch_custom_app_container, View.VISIBLE);
            return;
        }

        setVisibilityCommon(mView, R.id.launch_custom_app_container, View.GONE);
    }

    private void sendAccessibilityEvent() {
        AccessibilityManager am = (AccessibilityManager)
            getActivity().getSystemService(Service.ACCESSIBILITY_SERVICE);
        if ((am == null) || !am.isEnabled()) {
            return;
        }

        AccessibilityEvent event
            = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setClassName(getClass().getName());
        event.setPackageName(getActivity().getPackageName());
        List<CharSequence> text = event.getText();

        addFieldToAccessibilityEvent(text, mTitle);
        addFieldToAccessibilityEvent(text, mWhenDateTime);
        addFieldToAccessibilityEvent(text, mWhere);
        addFieldToAccessibilityEvent(text, mDesc);

        if (mResponseRadioGroup.getVisibility() == View.VISIBLE) {
            int id = mResponseRadioGroup.getCheckedRadioButtonId();
            if (id != View.NO_ID) {
                text.add(
                    ((TextView) getView().findViewById(R.id.response_label)).getText());
                text.add(
                    ((RadioButton) mResponseRadioGroup.findViewById(id)).getText()
                        + PERIOD_SPACE);
            }
        }
        am.sendAccessibilityEvent(event);
    }

    private void addFieldToAccessibilityEvent(List<CharSequence> text, TextView tv) {
        CharSequence cs;
        if (tv != null) {
            cs = tv.getText();
        } else {
            return;
        }

        if (!TextUtils.isEmpty(cs)) {
            cs = cs.toString().trim();
            if (cs.length() > 0) {
                text.add(cs);
                text.add(PERIOD_SPACE);
            }
        }
    }

    private void updateCalendar(View view) {

        mCalendarOwnerAccount = "";
        if (mCalendarsCursor != null && mEventCursor != null) {
            mCalendarsCursor.moveToFirst();
            String tempAccount = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            mCalendarOwnerAccount = (tempAccount == null) ? "" : tempAccount;
            mOwnerCanRespond =
                mCalendarsCursor.getInt(CALENDARS_INDEX_OWNER_CAN_RESPOND) != 0;
            mSyncAccountName = mCalendarsCursor.getString(CALENDARS_INDEX_ACCOUNT_NAME);

            // start visible calendars query
            mService.startQuery(TOKEN_QUERY_VISIBLE_CALENDARS, this,
                Calendars.CONTENT_URI, CALENDARS_PROJECTION,
                CALENDARS_VISIBLE_WHERE, new String[] {"1"}, null);

            mEventOrganizerEmail = mEventCursor.getString(EVENT_INDEX_ORGANIZER);
            mIsOrganizer = mCalendarOwnerAccount.equalsIgnoreCase(mEventOrganizerEmail);

            if (   (!TextUtils.isEmpty(mEventOrganizerEmail))
                && !mEventOrganizerEmail.endsWith(Utils.MACHINE_GENERATED_ADDRESS))
            {
                mEventOrganizerDisplayName = mEventOrganizerEmail;
            }

            if (!mIsOrganizer && !TextUtils.isEmpty(mEventOrganizerDisplayName)) {
                setTextCommon(view, R.id.organizer, mEventOrganizerDisplayName);
                setVisibilityCommon(view, R.id.organizer_container, View.VISIBLE);
            } else {
                setVisibilityCommon(view, R.id.organizer_container, View.GONE);
            }
            mHasAttendeeData = mEventCursor.getInt(EVENT_INDEX_HAS_ATTENDEE_DATA) != 0;
            mCanModifyCalendar =
                mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL)
                    >= Calendars.CAL_ACCESS_CONTRIBUTOR;
            // TODO add "|| guestCanModify" after b/1299071 is fixed
            mCanModifyEvent = mCanModifyCalendar && mIsOrganizer;
            mIsBusyFreeCalendar =
                    mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL)
                        == Calendars.CAL_ACCESS_FREEBUSY;

            Button editButtob = mView.findViewById(R.id.edit);
            if (!mIsBusyFreeCalendar) {

                editButtob.setEnabled(true);
                editButtob.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doEdit();
                        // For dialogs, just close the fragment
                        // For full screen, close activity on phone, leave it for tablet
                        if (mIsDialog) {
                            EventInfoFragment.this.dismiss();
                        }
                        else if (!mIsTabletConfig){
                            getActivity().finish();
                        }
                    }
                });
            }
            View button;
            if (mCanModifyCalendar) {
                button = mView.findViewById(R.id.delete);
                if (button != null) {
                    button.setEnabled(true);
                    button.setVisibility(View.VISIBLE);
                }
            }
            if (mCanModifyEvent) {
                if (editButtob != null) {
                    editButtob.setEnabled(true);
                    editButtob.setVisibility(View.VISIBLE);
                }
            }
            if (   (   ((!mIsDialog) && (!mIsTabletConfig))
                    || (mWindowStyle == EventInfoFragment.FULL_WINDOW_STYLE))
                && (mMenu != null))
            {
                mActivity.invalidateOptionsMenu();
            }
        } else {
            setVisibilityCommon(view, R.id.calendar, View.GONE);
            sendAccessibilityEventIfQueryDone(TOKEN_QUERY_DUPLICATE_CALENDARS);
        }
    }

    /**
     *
     */
    private void updateMenu() {
        if (mMenu == null) {
            return;
        }
        MenuItem delete = mMenu.findItem(R.id.info_action_delete);
        MenuItem edit = mMenu.findItem(R.id.info_action_edit);
        MenuItem changeColor = mMenu.findItem(R.id.info_action_change_color);
        if (delete != null) {
            delete.setVisible(mCanModifyCalendar);
            delete.setEnabled(mCanModifyCalendar);
        }
        if (edit != null) {
            edit.setVisible(mCanModifyEvent);
            edit.setEnabled(mCanModifyEvent);
        }
        if (changeColor != null && mColors != null && mColors.length > 0) {
            changeColor.setVisible(mCanModifyCalendar);
            changeColor.setEnabled(mCanModifyCalendar);
        }
    }

    private void updateAttendees() {
        if (mAcceptedAttendees.size() + mDeclinedAttendees.size() +
                mTentativeAttendees.size() + mNoResponseAttendees.size() > 0) {
            mLongAttendees.clearAttendees();
            mLongAttendees.addAttendees(mAcceptedAttendees);
            mLongAttendees.addAttendees(mDeclinedAttendees);
            mLongAttendees.addAttendees(mTentativeAttendees);
            mLongAttendees.addAttendees(mNoResponseAttendees);
            mLongAttendees.setEnabled(false);
            mLongAttendees.setVisibility(View.VISIBLE);
        } else {
            mLongAttendees.setVisibility(View.GONE);
        }

        if (hasEmailableAttendees()) {
            setVisibilityCommon(mView, R.id.email_attendees_container, View.VISIBLE);
            if (emailAttendeesButton != null) {
                emailAttendeesButton.setText(R.string.email_guests_label);
            }
        } else if (hasEmailableOrganizer()) {
            setVisibilityCommon(mView, R.id.email_attendees_container, View.VISIBLE);
            if (emailAttendeesButton != null) {
                emailAttendeesButton.setText(R.string.email_organizer_label);
            }
        } else {
            setVisibilityCommon(mView, R.id.email_attendees_container, View.GONE);
        }
    }

    /**
     * Returns true if there is at least 1 attendee that is not the viewer.
     */
    private boolean hasEmailableAttendees() {
        for (Attendee attendee : mAcceptedAttendees) {
            if (Utils.isEmailableFrom(attendee.mEmail, mSyncAccountName)) {
                return true;
            }
        }
        for (Attendee attendee : mTentativeAttendees) {
            if (Utils.isEmailableFrom(attendee.mEmail, mSyncAccountName)) {
                return true;
            }
        }
        for (Attendee attendee : mNoResponseAttendees) {
            if (Utils.isEmailableFrom(attendee.mEmail, mSyncAccountName)) {
                return true;
            }
        }
        for (Attendee attendee : mDeclinedAttendees) {
            if (Utils.isEmailableFrom(attendee.mEmail, mSyncAccountName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasEmailableOrganizer() {
        return mEventOrganizerEmail != null &&
                Utils.isEmailableFrom(mEventOrganizerEmail, mSyncAccountName);
    }

    public void initReminders(Cursor cursor) {

        // Add reminders
        mOriginalReminders.clear();
        mUnsupportedReminders.clear();
        while (cursor.moveToNext()) {
            int minutes = cursor.getInt(EditEventHelper.REMINDERS_INDEX_MINUTES);
            int method = cursor.getInt(EditEventHelper.REMINDERS_INDEX_METHOD);

            if (   (method != Reminders.METHOD_DEFAULT)
                && !(mReminderMethodValues.contains(method)))
            {
                // Stash unsupported reminder types separately so we don't alter
                // them in the UI
                mUnsupportedReminders.add(ReminderEntry.valueOf(minutes, method));
            } else {
                mOriginalReminders.add(ReminderEntry.valueOf(minutes, method));
            }
        }
        // Sort appropriately for display (by time, then type)
        Collections.sort(mOriginalReminders);

        if (mUserModifiedReminders) {
            // If the user has changed the list of reminders don't change what's
            // shown.
            return;
        }

        LinearLayout parent = mScrollView.findViewById(R.id.reminder_items_container);
        if (parent != null) {
            parent.removeAllViews();
        }
        mReminderViews.clear();

        if (mHasAlarm) {
            ArrayList<ReminderEntry> reminders;
            // If applicable, use reminders saved in the bundle.
            if (mReminders != null) {
                reminders = mReminders;
            } else {
                reminders = mOriginalReminders;
            }
            // Insert any minute values that aren't represented in the minutes list.
            for (ReminderEntry re : reminders) {
                EventViewUtils.addMinutesToList(
                    mActivity, mReminderMinuteValues,
                    mReminderMinuteLabels, re.getMinutes());
            }
            // Create a UI element for each reminder.
            for (ReminderEntry re : reminders) {
                EventViewUtils.addReminder(
                    mActivity, mScrollView, this, mReminderViews,
                    mReminderMinuteValues, mReminderMinuteLabels, mReminderMethodValues,
                    mReminderMethodLabels, re, mReminderChangeListener);
            }
        }
    }

    void updateResponse(View view) {
        // we only let the user accept/reject/etc. a meeting if:
        // a) you can edit the event's containing calendar AND
        // b) you're not the organizer and only attendee AND
        // c) organizerCanRespond is enabled for the calendar
        // (if the attendee data has been hidden, the visible number of attendees
        // will be 1 -- the calendar owner's).
        // (there are more cases involved to be 100% accurate, such as
        // paying attention to whether or not an attendee status was
        // included in the feed, but we're currently omitting those corner cases
        // for simplicity).

        // TODO Switch to EditEventHelper.canRespond
        //  when this class uses CalendarEventModel.
        if (   (!mCanModifyCalendar)
            || (mHasAttendeeData && mIsOrganizer && mNumOfAttendees <= 1)
            || (mIsOrganizer && !mOwnerCanRespond))
        {
            setVisibilityCommon(view, R.id.response_container, View.GONE);
            return;
        }

        setVisibilityCommon(view, R.id.response_container, View.VISIBLE);

        int response;
        if (mTentativeUserSetResponse != Attendees.ATTENDEE_STATUS_NONE) {
            response = mTentativeUserSetResponse;
        } else if (mUserSetResponse != Attendees.ATTENDEE_STATUS_NONE) {
            response = mUserSetResponse;
        } else if (mAttendeeResponseFromIntent != Attendees.ATTENDEE_STATUS_NONE) {
            response = mAttendeeResponseFromIntent;
        } else {
            response = mOriginalAttendeeResponse;
        }

        int buttonToCheck = findButtonIdForResponse(response);
        mResponseRadioGroup.check(buttonToCheck); // -1 clear all radio buttons
        mResponseRadioGroup.setOnCheckedChangeListener(this);
    }

    private void setTextCommon(View view, int id, CharSequence text) {
        TextView textView = view.findViewById(id);
        if (textView == null)
            return;
        textView.setText(text);
    }

    private void setVisibilityCommon(View view, int id, int visibility) {
        View v = view.findViewById(id);
        if (v != null) {
            v.setVisibility(visibility);
        }
    }

    /**
     * Taken from com.google.android.gm.HtmlConversationActivity
     *
     * Send the intent that shows the Contact info corresponding to
     * the email address.
     *
     * Not currently used, but eventually we will need to be able to show the
     * contact info.
     */
    @SuppressWarnings("unused")
    public void showContactInfo(Attendee attendee, Rect rect) {
        // First perform lookup query to find existing contact
        final ContentResolver resolver = getActivity().getContentResolver();
        final String address = attendee.mEmail;
        final Uri dataUri = Uri.withAppendedPath(CommonDataKinds.Email.CONTENT_FILTER_URI,
                Uri.encode(address));
        final Uri lookupUri = ContactsContract.Data.getContactLookupUri(resolver, dataUri);

        if (lookupUri != null) {
            // Found matching contact, trigger QuickContact
            QuickContact.showQuickContact(getActivity(), rect, lookupUri,
                    QuickContact.MODE_MEDIUM, null);
        } else {
            // No matching contact, ask user to create one
            final Uri mailUri = Uri.fromParts("mailto", address, null);
            final Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT, mailUri);

            // Pass along full E-mail string for possible create dialog
            Rfc822Token sender
                = new Rfc822Token(attendee.mName, attendee.mEmail, null);
            intent.putExtra(Intents.EXTRA_CREATE_DESCRIPTION, sender.toString());

            // Only provide personal name hint if we have one
            final String senderPersonal = attendee.mName;
            if (!TextUtils.isEmpty(senderPersonal)) {
                intent.putExtra(Intents.Insert.NAME, senderPersonal);
            }

            startActivity(intent);
        }
    }

    @Override
    public void eventsChanged() {
    }

    @Override
    public long getSupportedActionTypes() {
        return ControllerAction.EVENTS_CHANGED;
    }

    @Override
    public void handleAction(CalendarController.ActionInfo actionInfo) {
        reloadEvents();
    }

    public void reloadEvents() {
        mService = CalendarApplication.getAsyncQueryService();
        mService.startQuery(TOKEN_QUERY_EVENT, this, ContentUris.withAppendedId(Events.CONTENT_URI, mEventId), EVENT_PROJECTION,
                null, null, null);
    }

    @Override
    public void onClick(View view) {

        // This must be a click on one of the "remove reminder" buttons
        LinearLayout reminderItem = (LinearLayout) view.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        parent.removeView(reminderItem);
        mReminderViews.remove(reminderItem);
        mUserModifiedReminders = true;
    }

    /**
     * Add a new reminder when the user hits the "add reminder" button.
     * We use the default reminder time and method.
     */
    private void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        //  last one in the list (if any).
        if (mDefaultReminderMinutes == GeneralPreferences.NO_REMINDER) {
            EventViewUtils.addReminder(
                mActivity, mScrollView, this, mReminderViews,
                mReminderMinuteValues, mReminderMinuteLabels,
                mReminderMethodValues, mReminderMethodLabels,
                ReminderEntry.valueOf(GeneralPreferences.REMINDER_DEFAULT_TIME),
                mReminderChangeListener);
        } else {
            EventViewUtils.addReminder(
                mActivity, mScrollView, this, mReminderViews,
                mReminderMinuteValues, mReminderMinuteLabels, mReminderMethodValues,
                mReminderMethodLabels, ReminderEntry.valueOf(mDefaultReminderMinutes),
                mReminderChangeListener);
        }
    }

    synchronized private void prepareReminders() {
        // Nothing to do if we've already built these lists _and_ we aren't
        // removing not allowed methods
        if (   (mReminderMinuteValues != null)
            && (mReminderMinuteLabels != null)
            && (mReminderMethodValues != null)
            && (mReminderMethodLabels != null)
            && (mCalendarAllowedReminders == null))
        {  return; }

        // Load the labels and corresponding numeric values for the minutes
        // and methods lists from the assets.  If we're switching calendars,
        // we need to clear and re-populate the lists
        // (which may have elements added and removed based on calendar properties).
        // This is mostly relevant for "methods", since we shouldn't have any "minutes"
        // values in a new event that aren't in the default set.
        Resources r = mActivity.getResources();
        mReminderMinuteValues = loadIntegerArray(r, R.array.reminder_minutes_values);
        mReminderMinuteLabels = loadStringArray(r, R.array.reminder_minutes_labels);
        mReminderMethodValues = loadIntegerArray(r, R.array.reminder_methods_values);
        mReminderMethodLabels = loadStringArray(r, R.array.reminder_methods_labels);

        // Remove any reminder methods that aren't allowed for this calendar.
        // If this is a new event,
        // mCalendarAllowedReminders may not be set the first time we're called.
        if (mCalendarAllowedReminders != null) {
            EventViewUtils.reduceMethodList(mReminderMethodValues, mReminderMethodLabels,
                    mCalendarAllowedReminders);
        }
        if (mView != null) {
            mView.invalidate();
        }
    }

    private boolean saveReminders() {
        ArrayList<ContentProviderOperation> ops = new ArrayList<>(3);

        // Read reminders from UI
        mReminders = EventViewUtils.reminderItemsToReminders(mReminderViews,
                mReminderMinuteValues, mReminderMethodValues);
        mOriginalReminders.addAll(mUnsupportedReminders);
        Collections.sort(mOriginalReminders);
        mReminders.addAll(mUnsupportedReminders);
        Collections.sort(mReminders);

        // Check if there are any changes in the reminder
        boolean changed = EditEventHelper.saveReminders(ops, mEventId, mReminders,
                mOriginalReminders, false /* no force save */);

        if (!changed) {
            return false;
        }

        // save new reminders
        mService.startBatch(
            0, this, Calendars.CONTENT_URI.getAuthority(), ops);
        mOriginalReminders = mReminders;
        // Update the "hasAlarm" field for the event
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
        int len = mReminders.size();
        boolean hasAlarm = len > 0;
        if (hasAlarm != mHasAlarm) {
            ContentValues values = new ContentValues();
            values.put(Events.HAS_ALARM, hasAlarm ? 1 : 0);
            mService.startUpdate(0, this, uri, values,
                null, null);
        }
        return true;
    }

    /**
     * Email all the attendees of the event, except for the viewer
     * (so as to not email himself) and resources like conference rooms.
     */
    private void emailAttendees() {
        Intent i = new Intent(getActivity(), QuickResponseActivity.class);
        i.putExtra(QuickResponseActivity.EXTRA_EVENT_ID, mEventId);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    @Override
    public void onDeleteStarted() {
        mEventDeletionStarted = true;
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

    public long getEventId() {
        return mEventId;
    }

    public long getStartMillis() {
        return mStartMillis;
    }

    public long getEndMillis() {
        return mEndMillis;
    }

    private void setDialogSize(Resources r) {
        mDialogWidth = (int)r.getDimension(R.dimen.event_info_dialog_width);
        mDialogHeight = (int)r.getDimension(R.dimen.event_info_dialog_height);
    }

    @Override
    public void onColorSelected(int color) {
        mCurrentColor = color;
        mCurrentColorKey = mDisplayColorKeyMap.get(color);
        mHeadlines.setBackgroundColor(color);
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
        if (cursor != null) {
            // If the Activity is finishing, then close the cursor.
            // Otherwise, use the new cursor in the adapter.
            if (mActivity == null || mActivity.isFinishing()) {
                cursor.close();
                return;
            }
            if (cookie != null) {
                switch((Integer)cookie) {
                    case TOKEN_QUERY_EVENT:
                        mEventCursor = Utils.matrixCursorFromCursor(cursor);
                        if (initEventCursor()) {
                            // The cursor is empty. This can happen if the event was
                            // deleted.
                            // FRAG_TODO we should no longer rely on Activity.finish()
                            mActivity.finish();
                            return;
                        }
                        if (!mCalendarColorInitialized) {
                            mCalendarColor = Utils.getDisplayColorFromColor(
                                mEventCursor.getInt(EVENT_INDEX_CALENDAR_COLOR));
                            mCalendarColorInitialized = true;
                        }

                        if (!mOriginalColorInitialized) {
                            mOriginalColor = mEventCursor.isNull(EVENT_INDEX_EVENT_COLOR)
                                ? mCalendarColor : Utils.getDisplayColorFromColor(
                                mEventCursor.getInt(EVENT_INDEX_EVENT_COLOR));
                            mOriginalColorInitialized = true;
                        }

                        if (!mCurrentColorInitialized) {
                            mCurrentColor = mOriginalColor;
                            mCurrentColorInitialized = true;
                        }

                        updateEvent(mView);
                        prepareReminders();

                        // start calendar query
                        Uri uri = Calendars.CONTENT_URI;
                        String[] args = new String[]{
                            Long.toString(mEventCursor.getLong(EVENT_INDEX_CALENDAR_ID))};
                        mService.startQuery(TOKEN_QUERY_CALENDARS, null,
                            uri, CALENDARS_PROJECTION, CALENDARS_WHERE, args,
                            null);
                        break;
                    case TOKEN_QUERY_CALENDARS:
                        mCalendarsCursor = Utils.matrixCursorFromCursor(cursor);
                        updateCalendar(mView);
                        // FRAG_TODO fragments shouldn't set the title anymore
                        updateTitle();

                        args = new String[]{
                            mCalendarsCursor.getString(CALENDARS_INDEX_ACCOUNT_NAME),
                            mCalendarsCursor.getString(CALENDARS_INDEX_ACCOUNT_TYPE)};
                        uri = Colors.CONTENT_URI;
                        mService.startQuery(TOKEN_QUERY_COLORS, null, uri, COLORS_PROJECTION,
                            COLORS_WHERE, args, null);

                        if (!mIsBusyFreeCalendar) {
                            args = new String[]{Long.toString(mEventId)};

                            // start attendees query
                            uri = Attendees.CONTENT_URI;
                            mService.startQuery(TOKEN_QUERY_ATTENDEES, null, uri,
                                ATTENDEES_PROJECTION, ATTENDEES_WHERE,
                                args, ATTENDEES_SORT_ORDER);
                        } else {
                            sendAccessibilityEventIfQueryDone(TOKEN_QUERY_ATTENDEES);
                        }
                        if (mHasAlarm) {
                            // start reminders query
                            args = new String[]{Long.toString(mEventId)};
                            uri = Reminders.CONTENT_URI;
                            mService.startQuery(TOKEN_QUERY_REMINDERS, null, uri,
                                EditEventHelper.REMINDERS_PROJECTION,
                                EditEventHelper.REMINDERS_WHERE, args, null);
                        } else {
                            sendAccessibilityEventIfQueryDone(TOKEN_QUERY_REMINDERS);
                        }
                        break;
                    case TOKEN_QUERY_COLORS:
                        ArrayList<Integer> colors = new ArrayList<>();
                        if (cursor.moveToFirst()) {
                            do {
                                String colorKey = cursor.getString(COLORS_INDEX_COLOR_KEY);
                                int rawColor = cursor.getInt(COLORS_INDEX_COLOR);
                                int displayColor = Utils.getDisplayColorFromColor(rawColor);
                                mDisplayColorKeyMap.put(displayColor, colorKey);
                                colors.add(displayColor);
                            } while (cursor.moveToNext());
                        }
                        cursor.close();
                        Integer[] sortedColors = new Integer[colors.size()];
                        Arrays.sort(colors.toArray(sortedColors), new HsvColorComparator());
                        mColors = new int[sortedColors.length];
                        for (int i = 0; i < sortedColors.length; i++) {
                            mColors[i] = sortedColors[i];

                            float[] hsv = new float[3];
                            Color.colorToHSV(mColors[i], hsv);
                            if (DEBUG) {
                                Log.d("Color", "H:"
                                    + hsv[0] + ",S:" + hsv[1] + ",V:" + hsv[2]);
                            }
                        }
                        if (mCanModifyCalendar) {
                            View button = mView.findViewById(R.id.change_color);
                            if (button != null && mColors.length > 0) {
                                button.setEnabled(true);
                                button.setVisibility(View.VISIBLE);
                            }
                        }
                        updateMenu();
                        break;
                    case TOKEN_QUERY_ATTENDEES:
                        mAttendeesCursor = Utils.matrixCursorFromCursor(cursor);
                        initAttendeesCursor(mView);
                        updateResponse(mView);
                        break;
                    case TOKEN_QUERY_REMINDERS:
                        Cursor remindersCursor = Utils.matrixCursorFromCursor(cursor);
                        initReminders(remindersCursor);
                        break;
                    case TOKEN_QUERY_VISIBLE_CALENDARS:
                        if (cursor.getCount() > 1) {
                            // Start duplicate calendars query to detect whether to
                            // add the calendar email to the calendar owner display.
                            String displayName
                                = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
                            mService.startQuery(
                                TOKEN_QUERY_DUPLICATE_CALENDARS, this,
                                Calendars.CONTENT_URI, CALENDARS_PROJECTION,
                                CALENDARS_DUPLICATE_NAME_WHERE,
                                new String[]{displayName},
                                null);
                        } else {
                            // Don't need to display the calendar owner when there is only
                            // a single calendar.  Skip the duplicate calendars query.
                            setVisibilityCommon(mView, R.id.calendar_container, View.GONE);
                            mCurrentQuery |= TOKEN_QUERY_DUPLICATE_CALENDARS;
                        }
                        break;
                    case TOKEN_QUERY_DUPLICATE_CALENDARS:
                        SpannableStringBuilder sb = new SpannableStringBuilder();

                        // Calendar display name
                        String calendarName
                            = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
                        sb.append(calendarName);

                        // Show email account if display name is not unique and
                        // display name != email
                        String email
                            = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
                        if (   (cursor.getCount() > 1)
                            && Utils.isValidEmail(email)
                            && !calendarName.equalsIgnoreCase(email))
                        {
                            sb.append(" (").append(email).append(")");
                        }

                        setVisibilityCommon(mView, R.id.calendar_container, View.VISIBLE);
                        setTextCommon(mView, R.id.calendar_name, sb);
                        break;
                }
                cursor.close();
                sendAccessibilityEventIfQueryDone((Integer)cookie);

                // All queries are done, show the view.
                if (mCurrentQuery == TOKEN_QUERY_ALL) {
                    if (mLoadingMsgView.getAlpha() == 1) {
                        // Loading message is showing, let it stay a bit more
                        // (to prevent flashing) by adding a start delay to
                        // the event animation
                        long timeDiff
                            = LOADING_MSG_MIN_DISPLAY_TIME
                            - (System.currentTimeMillis()
                            - mLoadingMsgStartTime);
                        if (timeDiff > 0) {
                            mAnimateAlpha.setStartDelay(timeDiff);
                        }
                    }
                    if (   (!mAnimateAlpha.isRunning())
                        && (!mAnimateAlpha.isStarted())
                        && (!mNoCrossFade))
                    {
                        mAnimateAlpha.start();
                    } else {
                        mScrollView.setAlpha(1);
                        mLoadingMsgView.setVisibility(View.GONE);
                    }
                }
            }
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
        // no action required
    }
}
