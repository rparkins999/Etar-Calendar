/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Events;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.text.format.Time;
import android.util.Log;
import android.view.MenuItem;

import com.android.calendar.AbstractCalendarActivity;
import com.android.calendar.CalendarApplication;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.DynamicTheme;
import com.android.calendar.Utils;

import java.util.ArrayList;

import ws.xsoh.etar.R;

import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;

public class EditEventActivity extends AbstractCalendarActivity {
    public static final String EXTRA_EVENT_COLOR = "event_color";
    public static final String EXTRA_EVENT_REMINDERS = "reminders";
    public static final String EXTRA_READ_ONLY = "read_only";
    private static final String TAG = "EditEventActivity";
    private static final boolean DEBUG = false;
    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";

    private Intent mIntent;

    private final DynamicTheme dynamicTheme = new DynamicTheme();

    private EventInfo getEventInfoFromModel(CalendarEventModel model) {
        EventInfo eventInfo = new EventInfo();
        eventInfo.id = model.mId;
        eventInfo.startTime = eventInfo.selectedTime;
        eventInfo.endTime = new Time(Time.TIMEZONE_UTC);
        eventInfo.endTime.set(model.mEnd);
        eventInfo.eventTitle = model.mTitle;
        eventInfo.calendarId = model.mCalendarId;
        if (model.mAllDay) {
            eventInfo.extraLong = CalendarController.EXTRA_CREATE_ALL_DAY;
        } else {
            eventInfo.extraLong = 0;
        }
        return eventInfo;
    }

    @SuppressWarnings("unchecked")
    private ArrayList<ReminderEntry> getReminderEntriesFromIntent() {
        return (ArrayList<ReminderEntry>)
            mIntent.getSerializableExtra(EXTRA_EVENT_REMINDERS);
    }

    private EventInfo getEventInfoFromIntent(Bundle icicle) {
        EventInfo info = new EventInfo();
        long eventId = -1;
        Uri data = mIntent.getData();
        if (icicle != null && icicle.containsKey(BUNDLE_KEY_EVENT_ID)) {
            eventId = icicle.getLong(BUNDLE_KEY_EVENT_ID);
        } else if (data != null) {
            try {
                eventId = Long.parseLong(data.getLastPathSegment());
            } catch (NumberFormatException e) {
                if (DEBUG) {
                    Log.d(TAG, "Create new event");
                }
            }
        }

        boolean allDay = mIntent.getBooleanExtra(EXTRA_EVENT_ALL_DAY, false);

        long begin = mIntent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, -1);
        long end = mIntent.getLongExtra(EXTRA_EVENT_END_TIME, -1);
        if (end != -1) {
            info.endTime = new Time();
            if (allDay) {
                info.endTime.timezone = Time.TIMEZONE_UTC;
            }
            info.endTime.set(end);
        }
        if (begin != -1) {
            info.startTime = new Time();
            if (allDay) {
                info.startTime.timezone = Time.TIMEZONE_UTC;
            }
            info.startTime.set(begin);
        }
        info.id = eventId;
        info.eventTitle = mIntent.getStringExtra(Events.TITLE);
        info.calendarId = mIntent.getLongExtra(Events.CALENDAR_ID, -1);

        if (allDay) {
            info.extraLong = CalendarController.EXTRA_CREATE_ALL_DAY;
        } else {
            info.extraLong = 0;
        }
        return info;
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        dynamicTheme.onCreate(this);
        mIntent = getIntent();
        setContentView(R.layout.simple_frame_layout_material);
        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        EventInfo eventInfo;
        ArrayList<ReminderEntry> reminders;
        int eventColor;
        boolean eventColorInitialized ;
        CalendarEventModel model = CalendarApplication.mEvents.peekFirst();
        if (model == null) {
            eventInfo = getEventInfoFromIntent(icicle);
            reminders = getReminderEntriesFromIntent();
            eventColor = mIntent.getIntExtra(EXTRA_EVENT_COLOR, -1);
            eventColorInitialized = getIntent().hasExtra(EXTRA_EVENT_COLOR);
        } else {
            eventInfo = getEventInfoFromModel(model);
            reminders = model.mReminders;
            eventColor = model.mEventColor;
            eventColorInitialized = model.mEventColorInitialized;
        }

        if (Utils.getConfigBool(this, R.bool.multiple_pane_config)) {
            getSupportActionBar().setDisplayOptions(
                    ActionBar.DISPLAY_SHOW_TITLE,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME
                            | ActionBar.DISPLAY_SHOW_TITLE);
            getSupportActionBar().setTitle(
                    eventInfo.id == -1 ? R.string.event_create : R.string.event_edit);
        }
        else {
            getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME|
                    ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        }

        EditEventFragment editFragment =
            (EditEventFragment) getFragmentManager().findFragmentById(R.id.body_frame);
        if (editFragment == null) {

            boolean readOnly =
                   (eventInfo.id == -1)
                && mIntent.getBooleanExtra(EXTRA_READ_ONLY, false);
            editFragment = new EditEventFragment(eventInfo, reminders,
                eventColorInitialized, eventColor, readOnly, mIntent);

            editFragment.mShowModifyDialogOnLaunch = mIntent.getBooleanExtra(
                    CalendarController.EVENT_EDIT_ON_LAUNCH, false);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(R.id.body_frame, editFragment);
            ft.show(editFragment);
            ft.commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Utils.returnToCalendarHome(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
