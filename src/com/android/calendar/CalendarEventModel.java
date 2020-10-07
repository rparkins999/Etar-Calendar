/*
 * Copyright (C) 2010 The Android Open Source Project
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
import android.net.Uri;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;
import android.text.util.Rfc822Token;

import com.android.calendar.event.EditEventActivity;
import com.android.calendar.event.EditEventHelper;
import com.android.calendar.event.EventColorCache;
import com.android.calendar.fromcommon.Rfc822Validator;
import com.android.calendar.settings.GeneralPreferences;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.TimeZone;

import ws.xsoh.etar.R;

/**
 * Stores all the information needed to fill out an entry in the events table.
 * This is a convenient way for storing information needed by the UI to write to
 * the events table.
 */
public class CalendarEventModel implements Serializable {
    /**
     * The uri of the event in the db. This should only be null for a new event
     * or an event read from an ical file, or an event which has been deleted.
     */
    public Uri mUri = null;
    /**
     * The event ID of the event in the db.
     * This should only be null for new events.
     */
    public long mId = -1;
    public long mOriginalId = -1;
    /* This is the UID field for the VEVENT entry in the iCal file.
     * If we're writing to a file and we don't already have a UID,
     * we create a random one.
     */
    public String mUid = null;
    public long mStart = -1; // UTC milliseconds since the epoch
    public String mTimezoneStart; // Displayed timezone for start
    // This should be set the same as mStart when created and is used
    // for making changes to recurring events.
    // It should not be updated after it is initially set.
    public long mOriginalStart = -1; // UTC milliseconds since the epoch
    public long mEnd = -1; // UTC milliseconds since the epoch
    // Displayed timezone for end
    // Can be different from mTimezoneStart, for example for a flight between time zones
    public String mTimezoneEnd;
    // This should be set the same as mEnd when created and is used
    // for making changes to recurring events.
    // It should not be updated after it is initially set.
    public long mOriginalEnd = -1;
    // Recurrent events have a duration rather than an end time
    // The format of the string is defined in RFC5545
    public String mDuration = null;
    public boolean mAllDay = false;
    // Recurrence rule: a string as defined in RFC5545
    public String mRrule = null;
    // Currently the UI doesn't allow display or editing of RDATEs or EXDATEs
    // but Android handles them, so we can read and write them in an ical file
    public String mRdate = null; // list of extra dates or datetimes
    public String mExdate = null; // list of excluded dates or datetimes
    public boolean mIsFirstEventInSeries = true;
    public String mTitle = null;
    public String mLocation = null;
    public String mDescription = null;
    public int mEventColor = -1;
    public boolean mEventColorInitialized = false;
    public EventColorCache mEventColorCache;
    // android.provider.CalendarContract.EventsColumns.ACCESS_DEFAULT is 0,
    // but we can't import it because it's in a protected interface.
    public int mAccessLevel = 0;
    public int mAvailability = Events.AVAILABILITY_BUSY;
    public int mEventStatus = Events.STATUS_CONFIRMED;
    public long mCalendarId = -1;
    // Make sure this is in sync with the mCalendarId
    public String mCalendarDisplayName = "";
    private int mCalendarColor = -1;
    private boolean mCalendarColorInitialized = false;
    public String mCalendarAccountName;
    public String mCalendarAccountType;
    public int mCalendarMaxReminders;
    public String mCalendarAllowedReminders;
    public String mCalendarAllowedAttendeeTypes;
    public String mCalendarAllowedAvailability;
    public int mCalendarAccessLevel = Calendars.CAL_ACCESS_CONTRIBUTOR;
    // PROVIDER_NOTES owner account comes from the calendars table
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
    public boolean mModelUpdatedWithEventCursor;

    public CalendarEventModel() {
        mReminders = new ArrayList<>();
        mDefaultReminders = new ArrayList<>();
        mAttendeesList = new LinkedHashMap<>();
        mTimezoneStart = TimeZone.getDefault().getID();
    }

    // This little utility gets around the unchecked cast error
    // produced by using the generic clone().
    public ArrayList<ReminderEntry> cloneReminders (ArrayList<ReminderEntry> other) {
        ArrayList<ReminderEntry> result = new ArrayList<>(other.size());
        for (ReminderEntry entry : other) {
            result.add(ReminderEntry.valueOf(entry.getMinutes(), entry.getMethod()));
        }
        return result;
    }

    // copy constructor
    public CalendarEventModel(CalendarEventModel other) {
        mUri = other.mUri;
        mId = other.mId;
        mOriginalId = other.mOriginalId;
        mUid = other.mUid;
        mStart = other.mStart;
        mTimezoneStart = other.mTimezoneStart;
        mOriginalStart = other.mOriginalStart;
        mEnd = other.mEnd;
        mTimezoneEnd = other.mTimezoneEnd;
        mOriginalEnd = other.mOriginalEnd;
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
        mCalendarMaxReminders = other.mCalendarMaxReminders;
        mCalendarAllowedReminders = other.mCalendarAllowedReminders;
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

    public CalendarEventModel(Context context, Intent intent) {
        this(context);

        if (intent == null) {
            return;
        }

        String rrule = intent.getStringExtra(Events.RRULE);
        if (!TextUtils.isEmpty(rrule)) {
            mRrule = rrule;
        }

        String title = intent.getStringExtra(Events.TITLE);
        if (title != null) {
            mTitle = title;
        }

        String location = intent.getStringExtra(Events.EVENT_LOCATION);
        if (location != null) {
            mLocation = location;
        }

        String description = intent.getStringExtra(Events.DESCRIPTION);
        if (description != null) {
            mDescription = description;
        }

        mEventColor = intent.getIntExtra(
            EditEventActivity.EXTRA_EVENT_COLOR, -1);
        mEventColorInitialized = intent.hasExtra(
            EditEventActivity.EXTRA_EVENT_COLOR);

        int accessLevel = intent.getIntExtra(Events.ACCESS_LEVEL, -1);
        if (accessLevel != -1) {
            mAccessLevel = accessLevel;
        }

        int availability = intent.getIntExtra(Events.AVAILABILITY, -1);
        if (availability != -1) {
            mAvailability = availability;
        }

        // Using otherwise unnecessary local variable to suppress warning
        @SuppressWarnings("unchecked")
        ArrayList<ReminderEntry> reminders =
            (ArrayList<ReminderEntry>)intent.getSerializableExtra(
                EditEventActivity.EXTRA_EVENT_REMINDERS);
        if (reminders != null) {
            mReminders = reminders;
        }

        String emails = intent.getStringExtra(Intent.EXTRA_EMAIL);
        if (!TextUtils.isEmpty(emails)) {
            String[] emailArray = emails.split("[ ,;]");
            for (String email : emailArray) {
                if (!TextUtils.isEmpty(email) && email.contains("@")) {
                    email = email.trim();
                    if (!mAttendeesList.containsKey(email)) {
                        mAttendeesList.put(email, new Attendee("", email));
                    }
                }
            }
        }
    }

    public boolean isValid() {
        return (   (mCalendarId != -1)
            && !TextUtils.isEmpty(mOwnerAccount));
    }

    public boolean isEmpty() {
        return (   (   (mTitle == null)
                    || (mTitle.trim().length() == 0))
                && (   (mLocation == null)
                    || (mLocation.trim().length() == 0))
                && (   (mDescription == null)
                    || (mDescription.trim().length() == 0)));
    }

    public void clear() {
        mUri = null;
        mId = -1;
        mOriginalId = -1;
        mUid = null;
        mStart = -1;
        mTimezoneStart = null;
        mOriginalStart = -1;
        mEnd = -1;
        mTimezoneEnd = null;
        mOriginalEnd = -1;
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
        mCalendarAllowedReminders = null;
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

    public void addAttendees(String attendees, Rfc822Validator validator) {
        final LinkedHashSet<Rfc822Token> addresses = EditEventHelper.getAddressesFromList(
                attendees, validator);
        synchronized (this) {
            for (final Rfc822Token address : addresses) {
                final Attendee attendee = new Attendee(address.getName(), address.getAddress());
                if (TextUtils.isEmpty(attendee.mName)) {
                    attendee.mName = attendee.mEmail;
                }
                addAttendee(attendee);
            }
        }
    }

    // We should be able to delete an attendee, but there is no UI for it yet
    @SuppressWarnings("unused")
    public void removeAttendee(Attendee attendee) {
        mAttendeesList.remove(attendee.mEmail);
    }

    public boolean sameAttendees(CalendarEventModel other) {
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
                    return false;
                }
            } else {
                return false;
            }
        }
        for (String key : other.mAttendeesList.keySet()) {
            if (!mAttendeesList.containsKey(key)) {
                return false;
            }
        }
        return true;
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
        result = prime * result + ((mAttendeesList == null) ? 0 : getAttendeesString().hashCode());
        result = prime * result + (int) (mCalendarId ^ (mCalendarId >>> 32));
        result = prime * result + ((mDescription == null) ? 0 : mDescription.hashCode());
        result = prime * result + ((mDuration == null) ? 0 : mDuration.hashCode());
        result = prime * result + (int) (mEnd ^ (mEnd >>> 32));
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
        result = prime * result + (int) (mOriginalEnd ^ (mOriginalEnd >>> 32));
        result = prime * result + ((mOriginalSyncId == null) ? 0 : mOriginalSyncId.hashCode());
        result = prime * result + (int) (mOriginalId ^ (mOriginalEnd >>> 32));
        result = prime * result + (int) (mOriginalStart ^ (mOriginalStart >>> 32));
        result = prime * result + ((mOwnerAccount == null) ? 0 : mOwnerAccount.hashCode());
        result = prime * result + ((mReminders == null) ? 0 : mReminders.hashCode());
        result = prime * result + ((mRrule == null) ? 0 : mRrule.hashCode());
        result = prime * result + mSelfAttendeeStatus;
        result = prime * result + mOwnerAttendeeId;
        result = prime * result + (int) (mStart ^ (mStart >>> 32));
        result = prime * result + ((mSyncAccountName == null) ? 0 : mSyncAccountName.hashCode());
        result = prime * result + ((mSyncAccountType == null) ? 0 : mSyncAccountType.hashCode());
        result = prime * result + ((mSyncId == null) ? 0 : mSyncId.hashCode());
        result = prime * result + ((mTimezoneStart == null) ? 0 : mTimezoneStart.hashCode());
        result = prime * result + ((mTimezoneEnd == null) ? 0 : mTimezoneEnd.hashCode());
        result = prime * result + ((mTitle == null) ? 0 : mTitle.hashCode());
        result = prime * result + (mAvailability);
        result = prime * result + ((mUri == null) ? 0 : mUri.hashCode());
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
        if (!checkOriginalModelFields(other)) {
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

        if (mEnd != other.mEnd) {
            return false;
        }
        if (mIsFirstEventInSeries != other.mIsFirstEventInSeries) {
            return false;
        }
        if (mOriginalEnd != other.mOriginalEnd) {
            return false;
        }

        if (mOriginalStart != other.mOriginalStart) {
            return false;
        }
        if (mStart != other.mStart) {
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
    public boolean isUnchanged(CalendarEventModel originalModel) {
        if (this == originalModel) {
            return true;
        }
        if (originalModel == null) {
            return false;
        }

        if (!checkOriginalModelFields(originalModel)) {
            return false;
        }

        if (TextUtils.isEmpty(mLocation)) {
            if (!TextUtils.isEmpty(originalModel.mLocation)) {
                return false;
            }
        } else if (!mLocation.equals(originalModel.mLocation)) {
            return false;
        }

        if (TextUtils.isEmpty(mTitle)) {
            if (!TextUtils.isEmpty(originalModel.mTitle)) {
                return false;
            }
        } else if (!mTitle.equals(originalModel.mTitle)) {
            return false;
        }

        if (TextUtils.isEmpty(mDescription)) {
            if (!TextUtils.isEmpty(originalModel.mDescription)) {
                return false;
            }
        } else if (!mDescription.equals(originalModel.mDescription)) {
            return false;
        }

        if (TextUtils.isEmpty(mDuration)) {
            if (!TextUtils.isEmpty(originalModel.mDuration)) {
                return false;
            }
        } else if (!mDuration.equals(originalModel.mDuration)) {
            return false;
        }

        if (mEnd != mOriginalEnd) {
            return false;
        }
        if (mStart != mOriginalStart) {
            return false;
        }

        // If this changed the original id and it's not just an exception to the
        // original event
        if (mOriginalId != originalModel.mOriginalId && mOriginalId != originalModel.mId) {
            return false;
        }

        if (TextUtils.isEmpty(mRrule)) {
            // if the rrule is no longer empty check if this is an exception
            if (!TextUtils.isEmpty(originalModel.mRrule)) {
                boolean syncIdNotReferenced = mOriginalSyncId == null
                    || !mOriginalSyncId.equals(originalModel.mSyncId);
                boolean localIdNotReferenced = mOriginalId == -1
                    || !(mOriginalId == originalModel.mId);
                return !syncIdNotReferenced || !localIdNotReferenced;
            } else {
                return true;
            }
        } else return mRrule.equals(originalModel.mRrule);
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
    protected boolean checkOriginalModelFields(CalendarEventModel originalModel) {
        if (mAllDay != originalModel.mAllDay) {
            return false;
        }
        if (mAttendeesList == null) {
            if (originalModel.mAttendeesList != null) {
                return false;
            }
        } else if (!sameAttendees(originalModel)) {
            return false;
        }

        if (mCalendarId != originalModel.mCalendarId) {
            return false;
        }
        if (mCalendarColor != originalModel.mCalendarColor) {
            return false;
        }
        if (mCalendarColorInitialized != originalModel.mCalendarColorInitialized) {
            return false;
        }
        if (mGuestsCanInviteOthers != originalModel.mGuestsCanInviteOthers) {
            return false;
        }
        if (mGuestsCanModify != originalModel.mGuestsCanModify) {
            return false;
        }
        if (mGuestsCanSeeGuests != originalModel.mGuestsCanSeeGuests) {
            return false;
        }
        if (mOrganizerCanRespond != originalModel.mOrganizerCanRespond) {
            return false;
        }
        if (mCalendarAccessLevel != originalModel.mCalendarAccessLevel) {
            return false;
        }
        if (mModelUpdatedWithEventCursor != originalModel.mModelUpdatedWithEventCursor) {
            return false;
        }
        if (mHasAlarm != originalModel.mHasAlarm) {
            return false;
        }
        if (mHasAttendeeData != originalModel.mHasAttendeeData) {
            return false;
        }
        if (mId != originalModel.mId) {
            return false;
        }
        if (mIsOrganizer != originalModel.mIsOrganizer) {
            return false;
        }

        if (mOrganizer == null) {
            if (originalModel.mOrganizer != null) {
                return false;
            }
        } else if (!mOrganizer.equals(originalModel.mOrganizer)) {
            return false;
        }

        if (mOwnerAccount == null) {
            if (originalModel.mOwnerAccount != null) {
                return false;
            }
        } else if (!mOwnerAccount.equals(originalModel.mOwnerAccount)) {
            return false;
        }

        if (mReminders == null) {
            if (originalModel.mReminders != null) {
                return false;
            }
        } else if (!mReminders.equals(originalModel.mReminders)) {
            return false;
        }

        if (mSelfAttendeeStatus != originalModel.mSelfAttendeeStatus) {
            return false;
        }
        if (mOwnerAttendeeId != originalModel.mOwnerAttendeeId) {
            return false;
        }
        if (mSyncAccountName == null) {
            if (originalModel.mSyncAccountName != null) {
                return false;
            }
        } else if (!mSyncAccountName.equals(originalModel.mSyncAccountName)) {
            return false;
        }

        if (mSyncAccountType == null) {
            if (originalModel.mSyncAccountType != null) {
                return false;
            }
        } else if (!mSyncAccountType.equals(originalModel.mSyncAccountType)) {
            return false;
        }

        if (mSyncId == null) {
            if (originalModel.mSyncId != null) {
                return false;
            }
        } else if (!mSyncId.equals(originalModel.mSyncId)) {
            return false;
        }

        if (mTimezoneStart == null) {
            if (originalModel.mTimezoneStart != null) {
                return false;
            }
        } else if (!mTimezoneStart.equals(originalModel.mTimezoneStart)) {
            return false;
        }

        if (mTimezoneEnd == null) {
            if (originalModel.mTimezoneEnd != null) {
                return false;
            }
        } else if (!mTimezoneEnd.equals(originalModel.mTimezoneEnd)) {
            return false;
        }

        if (mAvailability != originalModel.mAvailability) {
            return false;
        }

        if (mUri == null) {
            if (originalModel.mUri != null) {
                return false;
            }
        } else if (!mUri.equals(originalModel.mUri)) {
            return false;
        }

        if (mAccessLevel != originalModel.mAccessLevel) {
            return false;
        }

        if (mEventStatus != originalModel.mEventStatus) {
            return false;
        }

        if (mEventColor != originalModel.mEventColor) {
            return false;
        }

        return mEventColorInitialized == originalModel.mEventColorInitialized;
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

        public Attendee(String name, String email, int status) {
            this(name, email, status, Attendees.TYPE_NONE);
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

        String getDisplayName() {
            if (TextUtils.isEmpty(mName)) {
                return mEmail;
            } else {
                return mName;
            }
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
         * @param minutes Number of minutes before the start of the event that the alert will fire.
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
         * @param minutes Number of minutes before the start of the event that the alert will fire.
         * @param method Type of alert ({@link Reminders#METHOD_ALERT}, etc).
         */
        public static ReminderEntry valueOf(int minutes, int method) {
            // TODO: cache common instances
            return new ReminderEntry(minutes, method);
        }

        /**
         * Returns a ReminderEntry, with the specified number of minutes and a default alert method.
         *
         * @param minutes Number of minutes before the start of the event that the alert will fire.
         */
        public static ReminderEntry valueOf(int minutes) {
            return valueOf(minutes, Reminders.METHOD_DEFAULT);
        }

        @Override
        public int hashCode() {
            return mMinutes * 10 + mMethod;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ReminderEntry)) {
                return false;
            }

            ReminderEntry re = (ReminderEntry) obj;

            if (re.mMinutes != mMinutes) {
                return false;
            }

            // Treat ALERT and DEFAULT as equivalent.  This is useful during the
            // "has anything changed" test, so that if DEFAULT is present,
            // but we don't change anything, the internal conversion of
            // DEFAULT to ALERT doesn't force a database update.
            return    (re.mMethod == mMethod)
                   || (    (re.mMethod == Reminders.METHOD_DEFAULT)
                        && (mMethod == Reminders.METHOD_ALERT))
                   || (    (re.mMethod == Reminders.METHOD_ALERT)
                        && (mMethod == Reminders.METHOD_DEFAULT));
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
