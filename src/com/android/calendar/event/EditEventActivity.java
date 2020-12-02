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

import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import android.text.format.Time;
import android.view.MenuItem;

import com.android.calendar.AbstractCalendarActivity;
import com.android.calendar.CalendarApplication;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.ActionInfo;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.DynamicTheme;
import com.android.calendar.Utils;

import ws.xsoh.etar.R;

public class EditEventActivity extends AbstractCalendarActivity {
    public static final String EXTRA_EVENT_REMINDERS = "reminders";
    public static final String EXTRA_READ_ONLY = "read_only";
    private static final String TAG = "EditEventActivity";
    private static final boolean DEBUG = false;
    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";

    public CalendarEventModel mModel;

    private final DynamicTheme dynamicTheme = new DynamicTheme();

    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        dynamicTheme.onCreate(this);
        Intent mIntent = getIntent();
        setContentView(R.layout.simple_frame_layout_material);
        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);

        synchronized (CalendarApplication.mEvents) {
            try {
                mModel = CalendarApplication.mEvents.remove(0);
            } catch (IndexOutOfBoundsException ignore) {
                mModel = null;
            }
        }
        if (mModel == null) {
            long id = (icicle == null)
                ? -1 : icicle.getLong(BUNDLE_KEY_EVENT_ID, -1);
            mModel = new CalendarEventModel(this, mIntent);
            if (id >= 0) { mModel.mId = id; }
        }
        ActionInfo actionInfo = new ActionInfo();
        actionInfo.eventId = mModel.mId;
        actionInfo.startTime = new Time(Time.TIMEZONE_UTC);
        actionInfo.startTime.set(mModel.mStart);
        actionInfo.endTime = new Time(Time.TIMEZONE_UTC);
        actionInfo.endTime.set(mModel.mEnd);
        actionInfo.eventTitle = mModel.mTitle;
        actionInfo.calendarId = mModel.mCalendarId;
        if (mModel.mAllDay) {
            actionInfo.extraLong = CalendarController.EXTRA_CREATE_ALL_DAY;
        } else {
            actionInfo.extraLong = 0;
        }

        if (Utils.getConfigBool(this, R.bool.multiple_pane_config)) {
            getSupportActionBar().setDisplayOptions(
                    ActionBar.DISPLAY_SHOW_TITLE,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME
                            | ActionBar.DISPLAY_SHOW_TITLE);
            getSupportActionBar().setTitle(
                mModel.mId == -1 ? R.string.event_create : R.string.event_edit);
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
                   (actionInfo.eventId == -1)
                && mIntent.getBooleanExtra(EXTRA_READ_ONLY, false);
            editFragment = new EditEventFragment(actionInfo, mModel.mReminders,
                mModel.mEventColorInitialized, mModel.mEventColor, readOnly);
            editFragment.setModel(mModel);
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
