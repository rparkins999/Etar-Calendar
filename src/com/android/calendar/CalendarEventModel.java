/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Modifications from the original version Copyright (C) Richard Parkins 2023
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.calendar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;

import com.android.calendar.event.EventColorCache;
import com.android.calendar.settings.GeneralPreferences;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.TimeZone;

import ws.xsoh.etar.R;

import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static com.android.calendar.event.EditEventActivity.EXTRA_EVENT_REMINDERS;

/* Stores all the information that we can ever want about a calendar event.
 * Most of it gets into the database, but some is only needed while we are
 * creating or editing the event.
 *
 * for a normal event (not related to a recurrence)
 * mOriginalId is zero
 * mRrule is null
 * mEventStart and mInstanceStart are both the start time of the event
 * mEventEnd and mInstanceEnd are both the end time of the event

 * for the first instance of a recurrence
 * mOriginalId is zero
 * mRrule is the rule (as a text string)
 * mEventStart and mInstanceStart are both the start time of the instance
 * mEventEnd and mInstanceEnd are both the end time of the instance

 * for a non-first instance of a recurrence
 * mOriginalId is zero
 * mRrule is the rule (as a text string)
 * mEventStart is the start time of the first instance of the recurrence
 * mInstanceStart is the start time of this instance
 * mEventEnd and mInstanceEnd are both the end time of this instance

 * for an exception to a recurrence
 * mOriginalId is nonzero (the id of the recurrence)
 * mRrule is null
 * mEventStart and mInstanceStart are both the start time of the exception
 * mEventEnd and mInstanceEnd are both the end time of the exception
 */
public class CalendarEventModel implements Serializable {
    // The event ID of the event in the db.
    // This should only be -1 when creating a new event.
    public long mId = -1;
    public long mOriginalId = -1;
    // This is the UID field for the VEVENT entry in the iCal file.
    // If we're writing to a file and we don't already have a UID,
    // we create a random one.
    public String mUid = null;
    // Start time of event, in UTC milliseconds since the epoch.
    // For a recurring event, it is the start time of the first instance.
    // When creating a new event, it is the requested start time.
    public long mEventStart = -1;
    // Start time of instance, in UTC milliseconds since the epoch.
    // When editing an instance of a recurring event,
    // it is the start time of the instance being edited.
    // In all other cases it is the same as mEventStart.
    public long mInstanceStart = -1;
    // Displayed timezone for start
    public String mTimezoneStart;
    // End time of event, in UTC milliseconds since the epoch.
    // Recurring events have a duration, not an end time, so this is -1.
    // When creating a new event, it is the requested end time.
    public long mEventEnd = -1;
    // End time of instance, in UTC milliseconds since the epoch
    // When editing an instance of a recurring event,
    // it is the end time of the instance being edited.
    // In all other cases it is the same as mEventEnd.
    public long mInstanceEnd = -1;
    // Displayed timezone for end, which can be different from mTimezoneStart,
    // for example for a flight between time zones.
    public String mTimezoneEnd;
    // Recurring events have a duration rather than an end time
    // The format of the string is defined in RFC5545
    public String mDuration = null;
    public boolean mAllDay = false;
    // Recurrence rule: a string as defined in RFC5545
    public String mRrule = null;
    // Currently the UI doesn't allow display or editing of RDATEs or EXDATEs
    // but Android handles them, so we can read and write them in an ical file
    // list of extra dates or datetimes
    public String mRdate = null;
    // list of excluded dates or datetimes
    public String mExdate = null;
    public boolean mIsFirstEventInSeries = true;
    public String mTitle = null; // Summary in the ical file
    public String mLocation = null;
    public String mDescription = null;
    public int mEventColor = -1;
    public boolean mEventColorInitialized = false;
    public EventColorCache mEventColorCache;
    public int mAccessLevel = Events.ACCESS_DEFAULT;
    public int mAvailability = Events.AVAILABILITY_BUSY;
    public int mEventStatus = Events.STATUS_CONFIRMED;
    public long mCalendarId = -1;
    // Make sure this is in sync with the mCalendarId
    public String mCalendarDisplayName = "";
    private int mCalendarColor = -1;
    private boolean mCalendarColorInitialized = false;
    public String mCalendarAccountName;
    public String mCalendarAccountType;
    public String mCalendarAllowedAttendeeTypes;
    public String mCalendarAllowedAvailability;
    public int mCalendarAccessLevel
        = Calendars.CAL_ACCESS_CONTRIBUTOR;
    // from the calendars table
    public String mOwnerAccount = null;
    public boolean mGuestsCanInviteOthers = false;
    public boolean mGuestsCanModify = false;
    public boolean mGuestsCanSeeGuests = false;
    // Needed to update the database if it has changed
    // because all reminders were removed or there were none and we added some
    public boolean mHasAlarm = false;
    public ArrayList<ReminderEntry> mReminders;
    public ArrayList<ReminderEntry> mDefaultReminders;
    // Needed to update the database if it has changed
    // because all attendees were removed or there were none and we added some
    public boolean mHasAttendeeData = true;
    public LinkedHashMap<String, Attendee> mAttendeesList;
    public boolean mIsOrganizer = true;
    public String mOrganizer = null; // email address
    public String mOrganizerDisplayName = null;
    public int mSelfAttendeeStatus = -1;
    public int mOwnerAttendeeId = -1;
    public boolean mOrganizerCanRespond = false;
    public String mSyncId = null;
    public String mOriginalSyncId = null;
    public String mSyncAccountName = null;
    public String mSyncAccountType = null;
    // The model can't be updated with a calendar cursor until it has been
    // updated with an event cursor.
    public boolean mModelUpdatedWithEventCursor = false;

    // This just creates an empty model:
    // it doesn't set mInstanceStart and mInstanceEnd.
    public CalendarEventModel() {
        mReminders = new ArrayList<>();
        mDefaultReminders = new ArrayList<>();
        mAttendeesList = new LinkedHashMap<>();
        mTimezoneStart = TimeZone.getDefault().getID();
    }

    // This little utility gets around the unchecked cast error
    // produced by using the generic clone().
    public ArrayList<ReminderEntry> cloneReminders (
        ArrayList<ReminderEntry> other)
    {
        ArrayList<ReminderEntry> result = new ArrayList<>(other.size());
        for (ReminderEntry entry : other) {
            result.add(ReminderEntry.valueOf(entry.getMinutes(), entry.getMethod()));
        }
        return result;
    }

    // copy constructor
    // sets mInstanceStart and mInstanceEnd if "other" has them
    public CalendarEventModel(CalendarEventModel other) {
        mId = other.mId;
        mOriginalId = other.mOriginalId;
        mUid = other.mUid;
        mEventStart = other.mEventStart;
        mInstanceStart = other.mInstanceStart;
        mTimezoneStart = other.mTimezoneStart;
        mEventEnd = other.mEventEnd;
        mInstanceEnd = other.mInstanceEnd;
        mTimezoneEnd = other.mTimezoneEnd;
        mDuration = other.mDuration;
        mAllDay = other.mAllDay;
        mRrule = other.mRrule;
        mRdate = other.mRdate;
        mExdate = other.mExdate;
        mIsFirstEventInSeries = other.mIsFirstEventInSeries;
        mTitle = other.mTitle;
        mLocation = other.mLocation;
        mDescription = other.mDescription;
        mEventColor = other.mEventColor;
        mEventColorInitialized = other.mEventColorInitialized;
        mEventColorCache = other.mEventColorCache;
        mAccessLevel = other.mAccessLevel;
        mAvailability = other.mAvailability;
        mEventStatus = other.mEventStatus;
        mCalendarId = other.mCalendarId;
        mCalendarDisplayName = other.mCalendarDisplayName;
        mCalendarColor = other.mCalendarColor;
        mCalendarColorInitialized = other.mCalendarColorInitialized;
        mCalendarAccountName = other.mCalendarAccountName;
        mCalendarAccountType = other.mCalendarAccountType;
        mCalendarAllowedAttendeeTypes = other.mCalendarAllowedAttendeeTypes;
        mCalendarAllowedAvailability = other.mCalendarAllowedAvailability;
        mCalendarAccessLevel = other.mCalendarAccessLevel;
        mOwnerAccount = other.mOwnerAccount;
        mGuestsCanInviteOthers = other.mGuestsCanInviteOthers;
        mGuestsCanModify = other.mGuestsCanModify;
        mGuestsCanSeeGuests = other.mGuestsCanSeeGuests;
        mHasAlarm = other.mHasAlarm;
        mReminders = cloneReminders(other.mReminders);
        mDefaultReminders = cloneReminders(other.mDefaultReminders);
        mHasAttendeeData = other.mHasAttendeeData;
        mAttendeesList = new LinkedHashMap<>();
        for (String key : other.mAttendeesList.keySet()) {
            Attendee old = other.mAttendeesList.get(key);
            assert old != null;
            Attendee value = new Attendee(
                old.mName, old.mEmail, old.mStatus, old.mType,
                old.mIdentity, old.mIdNamespace);
            mAttendeesList.put(key, value);
        }
        mIsOrganizer = other.mIsOrganizer;
        mOrganizer = other.mOrganizer;
        mOrganizerDisplayName = other.mOrganizerDisplayName;
        mSelfAttendeeStatus = other.mSelfAttendeeStatus;
        mOwnerAttendeeId = other.mOwnerAttendeeId;
        mOrganizerCanRespond = other.mOrganizerCanRespond;
        mSyncId = other.mSyncId;
        mOriginalSyncId = other.mOriginalSyncId;
        mSyncAccountName = other.mSyncAccountName;
        mSyncAccountType = other.mSyncAccountType;
        mModelUpdatedWithEventCursor = other.mModelUpdatedWithEventCursor;
    }

    // This doesn't set mInstanceStart and mInstanceEnd
    public CalendarEventModel(Context context) {
        this();

        mTimezoneEnd = mTimezoneStart = Utils.getTimeZone(context, null);
        SharedPreferences prefs =
            GeneralPreferences.Companion.getSharedPreferences(context);

        String defaultReminder = prefs.getString(
                GeneralPreferences.KEY_DEFAULT_REMINDER,
                GeneralPreferences.NO_REMINDER_STRING);
        int defaultReminderMins = Integer.parseInt(defaultReminder);
        if (defaultReminderMins != GeneralPreferences.NO_REMINDER) {
            // Assume all calendars allow at least one reminder.
            mHasAlarm = true;
            mReminders.add(ReminderEntry.valueOf(defaultReminderMins));
            mDefaultReminders.add(ReminderEntry.valueOf(defaultReminderMins));
        }
    }

    // This sets mInstanceStart and mInstanceEnd
    public CalendarEventModel(Context context, Intent intent) {
        this(context);
        if (intent == null) {
            // This shouldn't happen, but we behave like
            // CalendarEventModel(Context context) if it does.
            return;
        }
        try {
            mId = Long.parseLong(intent.getData().getLastPathSegment());
        } catch (NullPointerException | NumberFormatException ignored) { }

        long value = intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, -1);
        // If mId>=0 (this is an existing event) mEventStart will be overwritten
        // by the event start time from the database, which may be different
        // if this is an instance of a recurring event
        if (value >= 0) { mInstanceStart = mEventStart = value; }
        value = intent.getLongExtra(EXTRA_EVENT_END_TIME, -1);
        if (value >= 0) { mInstanceEnd = mEventEnd = value; }
        mAllDay = intent.getBooleanExtra(
            EXTRA_EVENT_ALL_DAY, false);
        String title = intent.getStringExtra(Events.TITLE);
        if (title != null) { mTitle = title; }
        value = intent.getLongExtra(Events.CALENDAR_ID, -1);
        if (value >= 0) { mCalendarId = value; }
        // These can only be sent by EventInfoFragment
        // which we intend to remove
        @SuppressWarnings("unchecked")
        ArrayList<ReminderEntry> reminders = (ArrayList<ReminderEntry>)
            intent.getSerializableExtra(EXTRA_EVENT_REMINDERS);
        if (reminders != null) { mReminders = reminders; }
        int color = intent.getIntExtra(Events.EVENT_COLOR, -1);
        if (color >= 0) {
            mEventColor = color;
            mEventColorInitialized = true;
        }
    }

    public boolean isValid() {
        return (   (mCalendarId != -1)
            && !TextUtils.isEmpty(mOwnerAccount));
    }

    // We refuse to create an event with no title, location, or description.
    // This checks for that possibility.
    public boolean isEmpty() {
        return (   (   (mTitle == null)
                    || (mTitle.trim().length() == 0))
                && (   (mLocation == null)
                    || (mLocation.trim().length() == 0))
                && (   (mDescription == null)
                    || (mDescription.trim().length() == 0)));
    }

    // This is called to initialise everything except the Uri
    // before filling the model with data from the CalendarProvider.
    // The Uri must be valid because we used it to access the CalendarProvider.
    public void clear() {
        // Don't throw away the Uri
        mId = -1;
        mOriginalId = -1;
        mUid = null;
        mEventStart = -1;
        mTimezoneStart = null;
        mInstanceStart = -1;
        mEventEnd = -1;
        mTimezoneEnd = null;
        mInstanceEnd = -1;
        mDuration = null;
        mAllDay = false;
        mRrule = null;
        mRdate = null;
        mExdate = null;
        mIsFirstEventInSeries = true;
        mTitle = null;
        mLocation = null;
        mDescription = null;
        mEventColor = -1;
        mEventColorInitialized = false;
        mEventColorCache = null;
        mAccessLevel = Events.ACCESS_DEFAULT;
        mAvailability = Events.AVAILABILITY_BUSY;
        mEventStatus = Events.STATUS_CONFIRMED;
        mCalendarId = -1;
        mCalendarDisplayName = "";
        mCalendarColor = -1;
        mCalendarColorInitialized = false;
        mCalendarAllowedAttendeeTypes = null;
        mCalendarAllowedAvailability = null;
        mCalendarAccessLevel = Calendars.CAL_ACCESS_CONTRIBUTOR;
        mOwnerAccount = null;
        mGuestsCanInviteOthers = false;
        mGuestsCanModify = false;
        mGuestsCanSeeGuests = false;
        mHasAlarm = false;
        mReminders = new ArrayList<>();
        mHasAttendeeData = true;
        mAttendeesList.clear();
        mIsOrganizer = true;
        mOrganizer = null;
        mOrganizerDisplayName = null;
        mSelfAttendeeStatus = -1;
        mOwnerAttendeeId = -1;
        mOrganizerCanRespond = false;
        mSyncId = null;
        mOriginalSyncId = null;
        mSyncAccountName = null;
        mSyncAccountType = null;
        mModelUpdatedWithEventCursor = false;
    }

    public void addAttendee(Attendee attendee) {
        mAttendeesList.put(attendee.mEmail, attendee);
    }

    // We should be able to delete an attendee, but there is no UI for it yet
    @SuppressWarnings("unused")
    public void removeAttendee(Attendee attendee) {
        mAttendeesList.remove(attendee.mEmail);
    }

    public boolean differentAttendees(CalendarEventModel other) {
        for (String key : mAttendeesList.keySet()) {
            if (other.mAttendeesList.containsKey(key)) {
                Attendee mine = mAttendeesList.get(key);
                Attendee theirs = other.mAttendeesList.get(key);
                if (   (!Utils.equals(mine.mName, theirs.mName))
                    || (!Utils.equals(mine.mEmail, theirs.mEmail))
                    || (mine.mStatus != theirs.mStatus)
                    || (mine.mType != theirs.mType)
                    || (!Utils.equals(mine.mIdentity, theirs.mIdentity))
                    || (!Utils.equals(mine.mIdNamespace, theirs.mIdNamespace)))
                {
                    return true;
                }
            } else {
                return true;
            }
        }
        for (String key : other.mAttendeesList.keySet()) {
            if (!mAttendeesList.containsKey(key)) {
                return true;
            }
        }
        return false;
    }

    public String getAttendeesString() {
        StringBuilder b = new StringBuilder();
        for (Attendee attendee : mAttendeesList.values()) {
            String name = attendee.mName;
            String email = attendee.mEmail;
            String status = Integer.toString(attendee.mStatus);
            b.append("name:").append(name);
            b.append(" email:").append(email);
            b.append(" status:").append(status);
        }
        return b.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (mAllDay ? 1231 : 1237);
        result = prime * result +
            ((mAttendeesList == null) ? 0 : getAttendeesString().hashCode());
        result = prime * result + (int) (mCalendarId ^ (mCalendarId >>> 32));
        result = prime * result + ((mDescription == null) ? 0 : mDescription.hashCode());
        result = prime * result + ((mDuration == null) ? 0 : mDuration.hashCode());
        result = prime * result + (int) (mEventEnd ^ (mEventEnd >>> 32));
        result = prime * result + (mGuestsCanInviteOthers ? 1231 : 1237);
        result = prime * result + (mGuestsCanModify ? 1231 : 1237);
        result = prime * result + (mGuestsCanSeeGuests ? 1231 : 1237);
        result = prime * result + (mOrganizerCanRespond ? 1231 : 1237);
        result = prime * result + (mModelUpdatedWithEventCursor ? 1231 : 1237);
        result = prime * result + mCalendarAccessLevel;
        result = prime * result + (mHasAlarm ? 1231 : 1237);
        result = prime * result + (mHasAttendeeData ? 1231 : 1237);
        result = prime * result + (int) (mId ^ (mId >>> 32));
        result = prime * result + (mIsFirstEventInSeries ? 1231 : 1237);
        result = prime * result + (mIsOrganizer ? 1231 : 1237);
        result = prime * result + ((mLocation == null) ? 0 : mLocation.hashCode());
        result = prime * result + ((mOrganizer == null) ? 0 : mOrganizer.hashCode());
        result = prime * result + (int) (mInstanceEnd ^ (mInstanceEnd >>> 32));
        result = prime * result + ((mOriginalSyncId == null) ? 0 : mOriginalSyncId.hashCode());
        result = prime * result + (int) (mOriginalId ^ (mInstanceEnd >>> 32));
        result = prime * result + (int) (mInstanceStart ^ (mInstanceStart >>> 32));
        result = prime * result + ((mOwnerAccount == null) ? 0 : mOwnerAccount.hashCode());
        result = prime * result + ((mReminders == null) ? 0 : mReminders.hashCode());
        result = prime * result + ((mRrule == null) ? 0 : mRrule.hashCode());
        result = prime * result + mSelfAttendeeStatus;
        result = prime * result + mOwnerAttendeeId;
        result = prime * result + (int) (mEventStart ^ (mEventStart >>> 32));
        result = prime * result + ((mSyncAccountName == null) ? 0 : mSyncAccountName.hashCode());
        result = prime * result + ((mSyncAccountType == null) ? 0 : mSyncAccountType.hashCode());
        result = prime * result + ((mSyncId == null) ? 0 : mSyncId.hashCode());
        result = prime * result + ((mTimezoneStart == null) ? 0 : mTimezoneStart.hashCode());
        result = prime * result + ((mTimezoneEnd == null) ? 0 : mTimezoneEnd.hashCode());
        result = prime * result + ((mTitle == null) ? 0 : mTitle.hashCode());
        result = prime * result + (mAvailability);
        result = prime * result + mAccessLevel;
        result = prime * result + mEventStatus;
        return result;
    }

    // Autogenerated equals method
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof CalendarEventModel)) {
            return false;
        }

        CalendarEventModel other = (CalendarEventModel) obj;
        if (originalModelFieldsDiffer(other)) {
            return false;
        }

        if (mLocation == null) {
            if (other.mLocation != null) {
                return false;
            }
        } else if (!mLocation.equals(other.mLocation)) {
            return false;
        }

        if (mTitle == null) {
            if (other.mTitle != null) {
                return false;
            }
        } else if (!mTitle.equals(other.mTitle)) {
            return false;
        }

        if (mDescription == null) {
            if (other.mDescription != null) {
                return false;
            }
        } else if (!mDescription.equals(other.mDescription)) {
            return false;
        }

        if (mDuration == null) {
            if (other.mDuration != null) {
                return false;
            }
        } else if (!mDuration.equals(other.mDuration)) {
            return false;
        }

        if (mEventEnd != other.mEventEnd) {
            return false;
        }
        if (mIsFirstEventInSeries != other.mIsFirstEventInSeries) {
            return false;
        }
        if (mInstanceEnd != other.mInstanceEnd) {
            return false;
        }

        if (mInstanceStart != other.mInstanceStart) {
            return false;
        }
        if (mEventStart != other.mEventStart) {
            return false;
        }

        if (mOriginalId != other.mOriginalId) {
            return false;
        }

        if (mOriginalSyncId == null) {
            if (other.mOriginalSyncId != null) {
                return false;
            }
        } else if (!mOriginalSyncId.equals(other.mOriginalSyncId)) {
            return false;
        }

        if (mRrule == null) {
            return other.mRrule == null;
        } else return mRrule.equals(other.mRrule);
    }

    /**
     * Whether the event has been modified based on its original model.
     *
     * @param originalModel the model to compare with
     * @return true if the model is unchanged, false otherwise
     */
    public boolean eventModified(CalendarEventModel originalModel) {
        if (this == originalModel) {
            return false;
        }
        if (originalModel == null) {
            return true;
        }

        if (originalModelFieldsDiffer(originalModel)) {
            return true;
        }

        if (TextUtils.isEmpty(mLocation)) {
            if (!TextUtils.isEmpty(originalModel.mLocation)) {
                return true;
            }
        } else if (!mLocation.equals(originalModel.mLocation)) {
            return true;
        }

        if (TextUtils.isEmpty(mTitle)) {
            if (!TextUtils.isEmpty(originalModel.mTitle)) {
                return true;
            }
        } else if (!mTitle.equals(originalModel.mTitle)) {
            return true;
        }

        if (TextUtils.isEmpty(mDescription)) {
            if (!TextUtils.isEmpty(originalModel.mDescription)) {
                return true;
            }
        } else if (!mDescription.equals(originalModel.mDescription)) {
            return true;
        }

        if (TextUtils.isEmpty(mDuration)) {
            if (!TextUtils.isEmpty(originalModel.mDuration)) {
                return true;
            }
        } else if (!mDuration.equals(originalModel.mDuration)) {
            return true;
        }

        if (mEventEnd != mInstanceEnd) {
            return true;
        }
        if (mEventStart != mInstanceStart) {
            return true;
        }

        // If this changed the original id and it's not just an exception to the
        // original event
        if (mOriginalId != originalModel.mOriginalId && mOriginalId != originalModel.mId) {
            return true;
        }

        if (TextUtils.isEmpty(mRrule)) {
            // if the rrule is no longer empty check if this is an exception
            return (   (!TextUtils.isEmpty(originalModel.mRrule))
                    && (   ((mOriginalSyncId == null)
                        || !mOriginalSyncId.equals(originalModel.mSyncId))
                    && (   (mOriginalId == -1)
                        || !(mOriginalId == originalModel.mId))));
        } else return !mRrule.equals(originalModel.mRrule);
    }

    /**
     * Checks against an original model for changes to an event. This covers all
     * the fields that should remain consistent between an original event model
     * and the new one if nothing in the event was modified. This is also the
     * portion that overlaps with equality between two event models.
     *
     * @param originalModel the model to compare with
     * @return true if these fields are unchanged, false otherwise
     */
    protected boolean originalModelFieldsDiffer(CalendarEventModel originalModel) {
        return (   (mAllDay != originalModel.mAllDay)
                || (  (mAttendeesList == null)
                    ? (originalModel.mAttendeesList != null)
                    : differentAttendees(originalModel))
                || (mCalendarId != originalModel.mCalendarId)
                || (mCalendarColor != originalModel.mCalendarColor)
                || (mCalendarColorInitialized != originalModel.mCalendarColorInitialized)
                || (mGuestsCanInviteOthers != originalModel.mGuestsCanInviteOthers)
                || (mGuestsCanModify != originalModel.mGuestsCanModify)
                || (mGuestsCanSeeGuests != originalModel.mGuestsCanSeeGuests)
                || (mOrganizerCanRespond != originalModel.mOrganizerCanRespond)
                || (mCalendarAccessLevel != originalModel.mCalendarAccessLevel)
                || (mModelUpdatedWithEventCursor !=
                        originalModel.mModelUpdatedWithEventCursor)
                || (mHasAlarm != originalModel.mHasAlarm)
                || (mHasAttendeeData != originalModel.mHasAttendeeData)
                || (mId != originalModel.mId)
                || (mIsOrganizer != originalModel.mIsOrganizer)
                || (!Objects.equals(mOrganizer, originalModel.mOrganizer))
                || (!Objects.equals(mOwnerAccount, originalModel.mOwnerAccount))
                || (!Objects.equals(mReminders, originalModel.mReminders))
                || (mSelfAttendeeStatus != originalModel.mSelfAttendeeStatus)
                || (mOwnerAttendeeId != originalModel.mOwnerAttendeeId)
                || (!Objects.equals(mSyncAccountName, originalModel.mSyncAccountName))
                || (!Objects.equals(mSyncAccountType, originalModel.mSyncAccountType))
                || (!Objects.equals(mSyncId, originalModel.mSyncId))
                || (!Objects.equals(mTimezoneStart, originalModel.mTimezoneStart))
                || (!Objects.equals(mTimezoneEnd, originalModel.mTimezoneEnd))
                || (mAvailability != originalModel.mAvailability)
                || (mAccessLevel != originalModel.mAccessLevel)
                || (mEventStatus != originalModel.mEventStatus)
                || (mEventColor != originalModel.mEventColor)
                || (mEventColorInitialized != originalModel.mEventColorInitialized));
    }

    /**
     * Sort and uniquify mReminderMinutes.
     *
     * @return true (for convenience of caller)
     */
    public boolean normalizeReminders() {
        if (mReminders.size() <= 1) {
            return true;
        }

        // sort
        Collections.sort(mReminders);

        // remove duplicates
        ReminderEntry prev = mReminders.get(mReminders.size()-1);
        for (int i = mReminders.size()-2; i >= 0; --i) {
            ReminderEntry cur = mReminders.get(i);
            if (prev.equals(cur)) {
                // match, remove later entry
                mReminders.remove(i+1);
            }
            prev = cur;
        }

        return true;
    }

    public boolean isCalendarColorInitialized() {
        return mCalendarColorInitialized;
    }

    public boolean isEventColorInitialized() {
        return mEventColorInitialized;
    }

    public int getCalendarColor() {
        return mCalendarColor;
    }

    public void setCalendarColor(int color) {
        mCalendarColor = color;
        mCalendarColorInitialized = true;
    }

    public int getEventColor() {
        return mEventColor;
    }

    public void setEventColor(int color) {
        mEventColor = color;
        mEventColorInitialized = true;
    }

    public int[] getCalendarEventColors() {
        if (mEventColorCache != null) {
            return mEventColorCache.getColorArray(mCalendarAccountName, mCalendarAccountType);
        }
        return null;
    }

    public String getEventColorKey() {
        if (mEventColorCache != null) {
            return mEventColorCache.getColorKey(mCalendarAccountName, mCalendarAccountType,
                    mEventColor);
        }
        return "";
    }

    public static class Attendee implements Serializable {
        public String mName;
        public String mEmail;
        public int mStatus;
        public int mType;
        public String mIdentity;
        public String mIdNamespace;

        public Attendee(String name, String email) {
            this(name, email, Attendees.ATTENDEE_STATUS_NONE,
                Attendees.TYPE_NONE);
        }

        public Attendee(String name, String email, int status, int type) {
            this(name, email, status, type, null, null);
        }

        public Attendee(String name, String email, int status,
                        String identity, String idNamespace) {
            this(name, email, status, Attendees.TYPE_NONE, identity, idNamespace);
        }

        public Attendee(String name, String email, int status, int type,
                        String identity, String idNamespace) {
            mName = name;
            mEmail = email;
            mStatus = status;
            mType = type;
            mIdentity = identity;
            mIdNamespace = idNamespace;
        }

        @Override
        public int hashCode() {
            return (mEmail == null) ? 0 : mEmail.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Attendee)) {
                return false;
            }
            Attendee other = (Attendee) obj;
            return TextUtils.equals(mEmail, other.mEmail);
        }
    }

    /**
     * A single reminder entry.
     * Instances of the class are immutable.
     */
    public static class ReminderEntry implements Comparable<ReminderEntry>, Serializable {
        private final int mMinutes;
        private final int mMethod;

        /**
         * Constructs a new ReminderEntry.
         *
         * @param minutes Number of minutes before the start of the event
         *                that the alert will fire.
         * @param method Type of alert ({@link Reminders#METHOD_ALERT}, etc).
         */
        private ReminderEntry(int minutes, int method) {
            // TODO: error-check args
            mMinutes = minutes;
            mMethod = method;
        }

        /**
         * Returns a new ReminderEntry, with the specified minutes and method.
         *
         * @param minutes Number of minutes before the start of the event
         *                that the alert will fire.
         * @param method Type of alert ({@link Reminders#METHOD_ALERT}, etc).
         */
        public static ReminderEntry valueOf(int minutes, int method) {
            // TODO: cache common instances
            return new ReminderEntry(minutes, method);
        }

        /**
         * Returns a ReminderEntry,
         * with the specified number of minutes and a default alert method.
         *
         * @param minutes Number of minutes before the start of the event
         *                that the alert will fire.
         */
        public static ReminderEntry valueOf(int minutes) {
            return valueOf(minutes, Reminders.METHOD_DEFAULT);
        }

        @Override
        public int hashCode() {
            return mMinutes * 10 + mMethod;
        }

        /**
         * Test if another reminder has "the same" method as this one
         * @param other the reminder to compare with
         * @return true if the methods are "the same"
         */
        private boolean methodsMatch(ReminderEntry other) {
            // Treat ALERT and DEFAULT as equivalent.  This is useful during the
            // "has anything changed" test, so that if DEFAULT is present,
            // but we don't change anything, the internal conversion of
            // DEFAULT to ALERT doesn't force a database update.
            return    (other.mMethod == mMethod)
                   || (    (other.mMethod == Reminders.METHOD_DEFAULT)
                        && (mMethod == Reminders.METHOD_ALERT))
                   || (    (other.mMethod == Reminders.METHOD_ALERT)
                        && (mMethod == Reminders.METHOD_DEFAULT));
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ReminderEntry)) {
                return false;
            }
            ReminderEntry other = (ReminderEntry) obj;
            return (   (mMinutes == other.mMinutes)
                    && methodsMatch(other));

        }

        /**
         * See if another ReminderEntry is the same as this one
         * apart from the time being later.
         *
         * @param other the ReminderEntry to compare with
         * @return -1 or the number of minutes that "other" is after "this"
         */
        public int offsetTo(ReminderEntry other) {
            // mMinutes is the time before the event.
            int offset = mMinutes - other.mMinutes;
            if (   (offset > 0)
                && methodsMatch(other))
            {
                return offset;
            } else {
                return -1;
            }
        }

        @NotNull
        @Override
        public String toString() {
            Resources r = CalendarApplication.getContext().getResources();
            int[] reminderMethodValues =
                r.getIntArray(R.array.reminder_methods_values);
            String[] reminderMethodLabels =
                r.getStringArray(R.array.reminder_methods_labels);
            String method = String.valueOf(mMethod);
            for (int i = 0; i < reminderMethodValues.length; ++i) {
                if (reminderMethodValues[i] == mMethod) {
                    method = reminderMethodLabels[i];
                }
            }
            return "ReminderEntry min=" + mMinutes + " meth=" + method;
        }

        /**
         * Comparison function for a sort ordered primarily descending by minutes,
         * secondarily ascending by method type.
         */
        @Override
        public int compareTo(ReminderEntry re) {
            if (re.mMinutes != mMinutes) {
                return re.mMinutes - mMinutes;
            }
            if (re.mMethod != mMethod) {
                return mMethod - re.mMethod;
            }
            return 0;
        }

        /** Returns the minutes. */
        public int getMinutes() {
            return mMinutes;
        }

        /** Returns the alert method. */
        public int getMethod() {
            return mMethod;
        }
    }
}
