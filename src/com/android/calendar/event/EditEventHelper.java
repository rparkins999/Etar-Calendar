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

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarApplication;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.Utils;
import com.android.calendarcommon2.DateException;
import com.android.calendarcommon2.EventRecurrence;
import com.android.calendarcommon2.RecurrenceProcessor;
import com.android.calendarcommon2.RecurrenceSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

public class EditEventHelper {
    private static final String TAG = "EditEventHelper";

    private static final boolean DEBUG = false;

    // Used for parsing rrules for special cases.
    private final EventRecurrence mEventRecurrence = new EventRecurrence();

    private static final String NO_EVENT_COLOR = "";

    public static final String[] EVENT_PROJECTION = new String[] {
            Events._ID,
            Events.TITLE,
            Events.DESCRIPTION,
            Events.EVENT_LOCATION,
            Events.ALL_DAY,
            Events.HAS_ALARM,
            Events.CALENDAR_ID,
            Events.DTSTART,
            Events.DTEND,
            Events.DURATION,
            Events.EVENT_TIMEZONE,
            Events.RRULE,
            Events._SYNC_ID,
            Events.AVAILABILITY,
            Events.ACCESS_LEVEL,
            Events.OWNER_ACCOUNT,
            Events.HAS_ATTENDEE_DATA,
            Events.ORIGINAL_SYNC_ID,
            Events.ORGANIZER,
            Events.GUESTS_CAN_MODIFY,
            Events.ORIGINAL_ID,
            Events.STATUS,
            Events.CALENDAR_COLOR,
            Events.EVENT_COLOR,
            Events.EVENT_COLOR_KEY,
            Events.ACCOUNT_NAME,
            Events.ACCOUNT_TYPE,
            Events.UID_2445,
            Events.EVENT_END_TIMEZONE,
    };
    // This looks a bit messy, but it makes the compiler do the work
    // and avoids the maintenance burden of keeping track of the indices by hand.
    private static final List<String> eventProjection = Arrays.asList(EVENT_PROJECTION);
    protected static final int EVENT_INDEX_ID =
        eventProjection.indexOf(Events._ID);
    protected static final int EVENT_INDEX_TITLE =
        eventProjection.indexOf(Events.TITLE);
    protected static final int EVENT_INDEX_DESCRIPTION =
        eventProjection.indexOf(Events.DESCRIPTION);
    protected static final int EVENT_INDEX_EVENT_LOCATION =
        eventProjection.indexOf(Events.EVENT_LOCATION);
    protected static final int EVENT_INDEX_ALL_DAY =
        eventProjection.indexOf(Events.ALL_DAY);
    protected static final int EVENT_INDEX_HAS_ALARM =
        eventProjection.indexOf(Events.HAS_ALARM);
    protected static final int EVENT_INDEX_CALENDAR_ID =
        eventProjection.indexOf(Events.CALENDAR_ID);
    protected static final int EVENT_INDEX_DTSTART =
        eventProjection.indexOf(Events.DTSTART);
    protected static final int EVENT_INDEX_DTEND =
        eventProjection.indexOf(Events.DTEND);
    protected static final int EVENT_INDEX_DURATION =
        eventProjection.indexOf(Events.DURATION);
    protected static final int EVENT_INDEX_TIMEZONE =
        eventProjection.indexOf(Events.EVENT_TIMEZONE);
    protected static final int EVENT_INDEX_RRULE =
        eventProjection.indexOf(Events.RRULE);
    protected static final int EVENT_INDEX_SYNC_ID =
        eventProjection.indexOf(Events._SYNC_ID);
    protected static final int EVENT_INDEX_AVAILABILITY =
        eventProjection.indexOf(Events.AVAILABILITY);
    protected static final int EVENT_INDEX_ACCESS_LEVEL =
        eventProjection.indexOf(Events.ACCESS_LEVEL);
    protected static final int EVENT_INDEX_OWNER_ACCOUNT =
        eventProjection.indexOf(Events.OWNER_ACCOUNT);
    protected static final int EVENT_INDEX_HAS_ATTENDEE_DATA =
        eventProjection.indexOf(Events.HAS_ATTENDEE_DATA);
    protected static final int EVENT_INDEX_ORIGINAL_SYNC_ID =
        eventProjection.indexOf(Events.ORIGINAL_SYNC_ID);
    protected static final int EVENT_INDEX_ORGANIZER =
        eventProjection.indexOf(Events.ORGANIZER);
    protected static final int EVENT_INDEX_GUESTS_CAN_MODIFY =
        eventProjection.indexOf(Events.GUESTS_CAN_MODIFY);
    protected static final int EVENT_INDEX_ORIGINAL_ID =
        eventProjection.indexOf(Events.ORIGINAL_ID);
    protected static final int EVENT_INDEX_EVENT_STATUS =
        eventProjection.indexOf(Events.STATUS);
    protected static final int EVENT_INDEX_CALENDAR_COLOR =
        eventProjection.indexOf(Events.CALENDAR_COLOR);
    protected static final int EVENT_INDEX_EVENT_COLOR =
        eventProjection.indexOf(Events.EVENT_COLOR);
    protected static final int EVENT_INDEX_EVENT_COLOR_KEY =
        eventProjection.indexOf(Events.EVENT_COLOR_KEY);
    protected static final int EVENT_INDEX_ACCOUNT_NAME =
        eventProjection.indexOf(Events.ACCOUNT_NAME);
    protected static final int EVENT_INDEX_ACCOUNT_TYPE =
        eventProjection.indexOf(Events.ACCOUNT_TYPE);
    protected static final int EVENT_INDEX_UID =
        eventProjection.indexOf(Events.UID_2445);
    protected static final int EVENT_INDEX_END_TIMEZONE =
        eventProjection.indexOf(Events.EVENT_END_TIMEZONE);

    public static final String[] REMINDERS_PROJECTION = new String[] {
            Reminders._ID,
            Reminders.MINUTES,
            Reminders.METHOD,
    };
    private static final List<String> remindersProjection
        = Arrays.asList(REMINDERS_PROJECTION);
    public static final int REMINDERS_INDEX_MINUTES =
        remindersProjection.indexOf(Reminders.MINUTES);
    public static final int REMINDERS_INDEX_METHOD =
        remindersProjection.indexOf(Reminders.METHOD);
    public static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=?";

    // Visible for testing
    static final String ATTENDEES_DELETE_PREFIX = Attendees.EVENT_ID + "=? AND "
            + Attendees.ATTENDEE_EMAIL + " IN (";

    protected static final int MODIFY_UNINITIALIZED = 0;
    protected static final int MODIFY_SELECTED = 1;
    protected static final int MODIFY_ALL_FOLLOWING = 2;
    protected static final int MODIFY_ALL = 3;

    protected static final int DAY_IN_SECONDS = 24 * 60 * 60;

    private final AsyncQueryService mService;

    // This allows us to flag the event if something is wrong with it, right now
    // if an uri is provided for an event that doesn't exist in the db.
    protected boolean mEventOk = true;

    static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.OWNER_ACCOUNT,
            Calendars.CALENDAR_COLOR,
            Calendars.CAN_ORGANIZER_RESPOND,
            Calendars.CALENDAR_ACCESS_LEVEL,
            Calendars.VISIBLE,
            Calendars.ALLOWED_ATTENDEE_TYPES,
            Calendars.ALLOWED_AVAILABILITY,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
    };
    private static final List<String> calendarsProjection
        = Arrays.asList(CALENDARS_PROJECTION);
    static final int CALENDARS_INDEX_ID =
        calendarsProjection.indexOf(Calendars._ID);
    static final int CALENDARS_INDEX_DISPLAY_NAME =
        calendarsProjection.indexOf(Calendars.CALENDAR_DISPLAY_NAME);
    static final int CALENDARS_INDEX_OWNER_ACCOUNT =
        calendarsProjection.indexOf(Calendars.OWNER_ACCOUNT);
    static final int CALENDARS_INDEX_COLOR =
        calendarsProjection.indexOf(Calendars.CALENDAR_COLOR);
    static final int CALENDARS_INDEX_CAN_ORGANIZER_RESPOND =
        calendarsProjection.indexOf(Calendars.CAN_ORGANIZER_RESPOND);
    static final int CALENDARS_INDEX_ACCESS_LEVEL =
        calendarsProjection.indexOf(Calendars.CALENDAR_ACCESS_LEVEL);
    static final int CALENDARS_INDEX_VISIBLE =
        calendarsProjection.indexOf(Calendars.VISIBLE);
    static final int CALENDARS_INDEX_ALLOWED_ATTENDEE_TYPES =
        calendarsProjection.indexOf(Calendars.ALLOWED_ATTENDEE_TYPES);
    static final int CALENDARS_INDEX_ALLOWED_AVAILABILITY =
        calendarsProjection.indexOf(Calendars.ALLOWED_AVAILABILITY);
    static final int CALENDARS_INDEX_ACCOUNT_NAME =
        calendarsProjection.indexOf(Calendars.ACCOUNT_NAME);
    static final int CALENDARS_INDEX_ACCOUNT_TYPE =
        calendarsProjection.indexOf(Calendars.ACCOUNT_TYPE);
    static final String CALENDARS_WHERE_WRITEABLE_VISIBLE
        = Calendars.CALENDAR_ACCESS_LEVEL + ">="
          + Calendars.CAL_ACCESS_CONTRIBUTOR + " AND " + Calendars.VISIBLE + "=1";
    static final String CALENDARS_WHERE = Calendars._ID + "=?";

    static final String[] COLORS_PROJECTION = new String[] {
        Colors._ID,
        Colors.ACCOUNT_NAME,
        Colors.ACCOUNT_TYPE,
        Colors.COLOR,
        Colors.COLOR_KEY,
    };
    private static final List<String> colorsProjection
        = Arrays.asList(COLORS_PROJECTION);
    static final int COLORS_INDEX_ACCOUNT_NAME =
        colorsProjection.indexOf(Colors.ACCOUNT_NAME);
    static final int COLORS_INDEX_ACCOUNT_TYPE =
        colorsProjection.indexOf(Colors.ACCOUNT_TYPE);
    static final int COLORS_INDEX_COLOR =
        colorsProjection.indexOf(Colors.COLOR);
    static final int COLORS_INDEX_COLOR_KEY =
        colorsProjection.indexOf(Colors.COLOR_KEY);
    static final String COLORS_WHERE =
        Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE
            + "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT;

    static final String[] ATTENDEES_PROJECTION = new String[] {
            Attendees._ID,
            Attendees.ATTENDEE_NAME,
            Attendees.ATTENDEE_EMAIL,
            Attendees.ATTENDEE_RELATIONSHIP,
            Attendees.ATTENDEE_STATUS,
            Attendees.ATTENDEE_TYPE,
            Attendees.ATTENDEE_IDENTITY,
            Attendees.ATTENDEE_ID_NAMESPACE,
    };
    private static final List<String> attendessProjection
        = Arrays.asList(ATTENDEES_PROJECTION);
    static final int ATTENDEES_INDEX_ID =
        attendessProjection.indexOf(Attendees._ID);
    static final int ATTENDEES_INDEX_NAME =
        attendessProjection.indexOf(Attendees.ATTENDEE_NAME);
    static final int ATTENDEES_INDEX_EMAIL =
        attendessProjection.indexOf(Attendees.ATTENDEE_EMAIL);
    static final int ATTENDEES_INDEX_RELATIONSHIP =
        attendessProjection.indexOf(Attendees.ATTENDEE_RELATIONSHIP);
    static final int ATTENDEES_INDEX_STATUS =
        attendessProjection.indexOf(Attendees.ATTENDEE_STATUS);
    static final int ATTENDEES_INDEX_TYPE =
        attendessProjection.indexOf(Attendees.ATTENDEE_TYPE);
    static final int ATTENDEES_INDEX_IDENTITY =
        attendessProjection.indexOf(Attendees.ATTENDEE_IDENTITY);
    static final int ATTENDEES_INDEX_ID_NAMESPACE =
        attendessProjection.indexOf(Attendees.ATTENDEE_ID_NAMESPACE);
    static final String ATTENDEES_WHERE
        = Attendees.EVENT_ID + "=? AND attendeeEmail IS NOT NULL";
    public static final int ATTENDEE_ID_NONE = -1;

    public static class AttendeeItem {
        public boolean mRemoved;
        public Attendee mAttendee;
        public Drawable mBadge;
        public int mUpdateCounts;
        public View mView;
        public Uri mContactLookupUri;

        public AttendeeItem(Attendee attendee, Drawable badge) {
            mAttendee = attendee;
            mBadge = badge;
        }
    }

    public EditEventHelper() {
        mService = CalendarApplication.getAsyncQueryService();
    }

    /**
     * Saves the event. Returns true if the event was successfully saved, false
     * otherwise.
     *
     * @param model The event model to save
     * @param originalModel A model of the original event if it exists
     * @param modifyWhich For recurring events which type of series modification to use
     * @return true if the event was successfully queued for saving
     */
    public boolean saveEvent(CalendarEventModel model,
                             CalendarEventModel originalModel, int modifyWhich)
    {
        boolean forceSaveReminders = false;

        if (DEBUG) {
            Log.d(TAG, "Saving event model: " + model);
        }

        if (!mEventOk) {
            if (DEBUG) {
                Log.w(TAG, "Event no longer exists. Event was not saved.");
            }
            return false;
        }

        // It's a problem if we try to save a non-existent or invalid model or if we're
        // modifying an existing event and we have the wrong original model
        if (model == null) {
            Log.e(TAG, "Attempted to save null model.");
            return false;
        } else if (!model.isValid()) {
            Log.e(TAG, "Attempted to save invalid model.");
            return false;
        }
        Uri uri = ContentUris.withAppendedId(
            Events.CONTENT_URI, model.mId);
        if (originalModel != null) {
            if (   (model.mCalendarId != originalModel.mCalendarId)
                || (model.mId != originalModel.mId))
            {
                Log.e(TAG, "Attempted to update existing event but"
                    + " models didn't refer to the same event.");
                return false;
            } else if (!model.eventModified(originalModel)) {
                // no need to save the event if it hasn't been modified
                return false;
            }
        } else if (model.mId >= 0) {
            Log.e(TAG, "Existing event but no originalModel provided. Aborting save.");
            return false;
        }

        ArrayList<ContentProviderOperation> ops = new ArrayList<>();
        int eventIdIndex = -1;

        ContentValues values = getContentValuesFromModel(model);

        // Update the "hasAlarm" field for the event
        ArrayList<ReminderEntry> reminders = model.mReminders;
        int len = reminders.size();
        values.put(Events.HAS_ALARM, (len > 0) ? 1 : 0);

        if ((model.mUid != null ) && !model.mUid.isEmpty()) {
            values.put(Events.UID_2445, model.mUid);
        }

        if (model.mId < 0) {
            // Add hasAttendeeData for a new event
            values.put(Events.HAS_ATTENDEE_DATA, 1);
            values.put(Events.STATUS, Events.STATUS_CONFIRMED);
            eventIdIndex = ops.size();
            ContentProviderOperation.Builder b
                = ContentProviderOperation.newInsert( Events.CONTENT_URI).withValues(values);
            ops.add(b.build());
            forceSaveReminders = true;

        } else if (   TextUtils.isEmpty(model.mRrule)
                   && TextUtils.isEmpty(originalModel.mRrule))
        {
            // Simple update to a non-recurring event
            checkTimeDependentFields(originalModel, model, values, modifyWhich);
            ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());

        } else if (TextUtils.isEmpty(originalModel.mRrule)) {
            // This event was changed from a non-repeating event to a
            // repeating event.
            ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());

        } else if (modifyWhich == MODIFY_SELECTED) {
            // Modify contents of the current instance of repeating event
            // Create a recurrence exception
            values.put(Events.ORIGINAL_SYNC_ID, originalModel.mSyncId);
            values.put(Events.ORIGINAL_INSTANCE_TIME,
                originalModel.mInstanceStart);
            boolean allDay = originalModel.mAllDay;
            values.put(Events.ORIGINAL_ALL_DAY, allDay ? 1 : 0);
            values.put(Events.STATUS, originalModel.mEventStatus);

            eventIdIndex = ops.size();
            ContentProviderOperation.Builder b = ContentProviderOperation.newInsert(
                    Events.CONTENT_URI).withValues(values);
            ops.add(b.build());
            forceSaveReminders = true;

        } else if (modifyWhich == MODIFY_ALL_FOLLOWING) {

            if (TextUtils.isEmpty(model.mRrule)) {
                // We've changed a recurring event to a non-recurring event.
                // If the event we are editing is the first in the series,
                // then delete the whole series. Otherwise, update the series
                // to end at the new start time.
                if (isFirstEventInSeries(model, originalModel)) {
                    ops.add(ContentProviderOperation.newDelete(uri).build());
                } else {
                    // Update the current repeating event to end at the new start time.  We
                    // ignore the RRULE returned because the exception event
                    // doesn't want one.
                    updatePastEvents(ops, originalModel, model.mInstanceStart);
                }
                eventIdIndex = ops.size();
                values.put(Events.STATUS, originalModel.mEventStatus);
                ops.add(ContentProviderOperation.newInsert(
                    Events.CONTENT_URI).withValues(values).build());
            } else {
                if (isFirstEventInSeries(model, originalModel)) {
                    checkTimeDependentFields(originalModel, model, values, modifyWhich);
                    ContentProviderOperation.Builder b
                        = ContentProviderOperation.newUpdate(uri).withValues(values);
                    ops.add(b.build());
                } else {
                    // We need to update the existing recurrence to end before the exception
                    // event starts.  If the recurrence rule has a COUNT, we need to adjust
                    // that in the original and in the exception.  This call rewrites the
                    // original event's recurrence rule (in "ops"), and returns a new rule
                    // for the exception.  If the exception explicitly set a new rule,
                    // however, we don't want to overwrite it.
                    String newRrule = updatePastEvents(
                        ops, originalModel, model.mInstanceStart);
                    if (model.mRrule.equals(originalModel.mRrule)) {
                        values.put(Events.RRULE, newRrule);
                    }

                    // Create a new event with the user-modified fields
                    eventIdIndex = ops.size();
                    values.put(Events.STATUS, originalModel.mEventStatus);
                    ops.add(ContentProviderOperation.newInsert(
                        Events.CONTENT_URI).withValues(values).build());
                }
            }
            forceSaveReminders = true;

        } else if (modifyWhich == MODIFY_ALL) {

            // Modify all instances of repeating event
            if (TextUtils.isEmpty(model.mRrule)) {
                // We've changed a recurring event to a non-recurring event.
                // Delete the whole series and replace it with a new
                // non-recurring event.
                ops.add(ContentProviderOperation.newDelete(uri).build());

                eventIdIndex = ops.size();
                ops.add(ContentProviderOperation.newInsert(
                    Events.CONTENT_URI).withValues(values).build());
                forceSaveReminders = true;
            } else {
                checkTimeDependentFields(originalModel, model, values, modifyWhich);
                ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());
            }
        }

        // New Event or New Exception to an existing event
        boolean newEvent = (eventIdIndex != -1);
        ArrayList<ReminderEntry> originalReminders;
        if (originalModel != null) {
            originalReminders = originalModel.mReminders;
        } else {
            originalReminders = new ArrayList<>();
        }

        if (newEvent) {
            saveRemindersWithBackRef(ops, eventIdIndex, reminders, originalReminders,
                    forceSaveReminders);
        } else if (model.mId >= 0) {
            long eventId = ContentUris.parseId(uri);
            saveReminders(ops, eventId, reminders, originalReminders, forceSaveReminders);
        }

        ContentProviderOperation.Builder b;

        if (model.mHasAttendeeData) {
            if (model.mOwnerAttendeeId == -1) {
                // Organizer is not an attendee

                String ownerEmail = model.mOwnerAccount;
                if (model.mAttendeesList.size() != 0 && Utils.isValidEmail(ownerEmail)) {
                    // Add organizer as attendee since we got some attendees

                    values.clear();
                    values.put(Attendees.ATTENDEE_EMAIL, ownerEmail);
                    values.put(Attendees.ATTENDEE_RELATIONSHIP,
                        Attendees.RELATIONSHIP_ORGANIZER);
                    values.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED);
                    values.put(Attendees.ATTENDEE_STATUS,
                        Attendees.ATTENDEE_STATUS_ACCEPTED);

                    if (newEvent) {
                        b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI)
                            .withValues(values);
                        b.withValueBackReference(Attendees.EVENT_ID, eventIdIndex);
                    } else {
                        values.put(Attendees.EVENT_ID, model.mId);
                        b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI)
                            .withValues(values);
                    }
                    ops.add(b.build());
                }
            } else if (model.mSelfAttendeeStatus != originalModel.mSelfAttendeeStatus) {
                if (DEBUG) {
                    Log.d(TAG, "Setting attendee status to "
                        + model.mSelfAttendeeStatus);
                }
                Uri attUri = ContentUris.withAppendedId(
                    Attendees.CONTENT_URI, model.mOwnerAttendeeId);

                values.clear();
                values.put(Attendees.ATTENDEE_STATUS, model.mSelfAttendeeStatus);
                values.put(Attendees.EVENT_ID, model.mId);
                b = ContentProviderOperation.newUpdate(attUri).withValues(values);
                ops.add(b.build());
            }

            // TODO: is this the right test? this currently checks if this is
            // a new event or an existing event. or is this a paranoia check?
            if ((newEvent || model.mId >= 0)) {
                // Hit the content provider only if this is a new event
                // or the user/ has changed it
                if (   newEvent
                    || (originalModel == null)
                    || model.differentAttendees(originalModel))
                {
                    // figure out which attendees need to be added and which ones
                    // need to be deleted. use a linked hash set, so we maintain
                    // order (but also remove duplicates).
                    HashMap<String, Attendee> newAttendees = model.mAttendeesList;
                    LinkedList<String> removedAttendees = new LinkedList<>();

                    // only compute deltas if this is an existing event.
                    // new events (being inserted into the Events table) won't
                    // have any existing attendees.
                    if (!newEvent) {
                        removedAttendees.clear();
                        HashMap<String, Attendee> originalAttendees
                            = originalModel.mAttendeesList;
                        for (String originalEmail : originalAttendees.keySet()) {
                            if (newAttendees.containsKey(originalEmail)) {
                                // existing attendee. remove from new attendees set.
                                newAttendees.remove(originalEmail);
                            } else {
                                // no longer in attendees. mark as removed.
                                removedAttendees.add(originalEmail);
                            }
                        }

                        // delete removed attendees if necessary
                        if (removedAttendees.size() > 0) {
                            b = ContentProviderOperation.newDelete(Attendees.CONTENT_URI);

                            String[] args = new String[removedAttendees.size() + 1];
                            args[0] = Long.toString(model.mId);
                            int i = 1;
                            StringBuilder deleteWhere
                                = new StringBuilder(ATTENDEES_DELETE_PREFIX);
                            for (String removedAttendee : removedAttendees) {
                                if (i > 1) {
                                    deleteWhere.append(",");
                                }
                                deleteWhere.append("?");
                                args[i++] = removedAttendee;
                            }
                            deleteWhere.append(")");
                            b.withSelection(deleteWhere.toString(), args);
                            ops.add(b.build());
                        }
                    }

                    if (newAttendees.size() > 0) {
                        // Insert the new attendees
                        for (Attendee attendee : newAttendees.values()) {
                            values.clear();
                            values.put(Attendees.ATTENDEE_NAME, attendee.mName);
                            values.put(Attendees.ATTENDEE_EMAIL, attendee.mEmail);
                            values.put(Attendees.ATTENDEE_RELATIONSHIP,
                                Attendees.RELATIONSHIP_ATTENDEE);
                            values.put(Attendees.ATTENDEE_TYPE, attendee.mType);
                            values.put(Attendees.ATTENDEE_STATUS, attendee.mStatus);
                            values.put(Attendees.ATTENDEE_IDENTITY, attendee.mIdentity);
                            values.put(Attendees.ATTENDEE_ID_NAMESPACE, attendee.mIdNamespace);

                            if (newEvent) {
                                b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI)
                                    .withValues(values);
                                b.withValueBackReference(Attendees.EVENT_ID, eventIdIndex);
                            } else {
                                values.put(Attendees.EVENT_ID, model.mId);
                                b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI)
                                    .withValues(values);
                            }
                            ops.add(b.build());
                        }
                    }
                }
            }
        }


        mService.startBatch(null, null,
            android.provider.CalendarContract.AUTHORITY, ops);

        return true;
    }

    // TODO think about how useful this is. Probably check if our event has
    // changed early on and either update all or nothing. Should still do the if
    // MODIFY_ALL bit.
    void checkTimeDependentFields(CalendarEventModel originalModel, CalendarEventModel model,
            ContentValues values, int modifyWhich) {
        long oldBegin = originalModel.mInstanceStart;
        String oldRrule = originalModel.mRrule;

        long newBegin = model.mInstanceStart;
        boolean newAllDay = model.mAllDay;
        String newRrule = model.mRrule;

        // If none of the time-dependent fields changed, then remove them.
        if (   (oldBegin == newBegin)
            && (originalModel.mInstanceEnd == model.mInstanceEnd)
            && (originalModel.mAllDay == newAllDay)
            && TextUtils.equals(oldRrule, newRrule)
            && TextUtils.equals(
                originalModel.mTimezoneStart, model.mTimezoneStart)
            && TextUtils.equals(
                originalModel.mTimezoneEnd, model.mTimezoneEnd))
        {
            values.remove(Events.DTSTART);
            values.remove(Events.DTEND);
            values.remove(Events.DURATION);
            values.remove(Events.ALL_DAY);
            values.remove(Events.RRULE);
            values.remove(Events.EVENT_TIMEZONE);
            values.remove(Events.EVENT_END_TIMEZONE);
            return;
        }

        if (TextUtils.isEmpty(oldRrule) || TextUtils.isEmpty(newRrule)) {
            return;
        }

        // If we are modifying all events then we need to set DTSTART to the
        // start time of the first event in the series, not the current
        // date and time. If the start time of the event was changed
        // (from, say, 3pm to 4pm), then we want to add the time difference
        // to the start time of the first event in the series (the DTSTART
        // value). If we are modifying one instance or all following instances,
        // then we leave the DTSTART field alone.
        if (modifyWhich == MODIFY_ALL) {
            long oldStartMillis = originalModel.mEventStart;
            if (oldBegin != newBegin) {
                // The user changed the start time of this event
                long offset = newBegin - oldBegin;
                oldStartMillis += offset;
            }
            if (newAllDay) {
                Time time = new Time(Time.TIMEZONE_UTC);
                time.set(oldStartMillis);
                time.hour = 0;
                time.minute = 0;
                time.second = 0;
                oldStartMillis = time.toMillis(false);
            }
            values.put(Events.DTSTART, oldStartMillis);
        }
    }

    /**
     * Prepares an update to the original event so it stops where the new series
     * begins. When we update 'this and all following' events we need to change
     * the original event to end before a new series starts. This creates an
     * update to the old event's rrule to do that.
     *<p>
     * If the event's recurrence rule has a COUNT, we also need to reduce the count in the
     * RRULE for the exception event.
     *
     * @param ops The list of operations to add the update to
     * @param originalModel The original event that we're updating
     * @param endTimeMillis The time before which the event must end (i.e. the start time of the
     *        exception event instance).
     * @return A replacement exception recurrence rule.
     */
    public String updatePastEvents(ArrayList<ContentProviderOperation> ops,
            CalendarEventModel originalModel, long endTimeMillis) {
        boolean origAllDay = originalModel.mAllDay;
        String origRrule = originalModel.mRrule;
        String newRrule = origRrule;

        EventRecurrence origRecurrence = new EventRecurrence();
        origRecurrence.parse(origRrule);

        // Get the start time of the first instance in the original recurrence.
        long startTimeMillis = originalModel.mEventStart;
        Time dtstart = new Time();
        dtstart.timezone = originalModel.mTimezoneStart;
        dtstart.set(startTimeMillis);

        ContentValues updateValues = new ContentValues();

        if (origRecurrence.count > 0) {
            /*
             * Generate the full set of instances for this recurrence, from the first to the
             * one just before endTimeMillis.  The list should never be empty, because this method
             * should not be called for the first instance.  All we're really interested in is
             * the *number* of instances found.
             *
             * TODO: the model assumes RRULE and ignores RDATE, EXRULE, and EXDATE.  For the
             * current environment this is reasonable, but that may not hold in the future.
             *
             * TODO: if COUNT is 1, should we convert the event to non-recurring?  e.g. we
             * do an "edit this and all future events" on the 2nd instances.
             */
            RecurrenceSet recurSet = new RecurrenceSet(originalModel.mRrule, null, null, null);
            RecurrenceProcessor recurProc = new RecurrenceProcessor();
            long[] recurrences;
            try {
                recurrences = recurProc.expand(dtstart, recurSet, startTimeMillis, endTimeMillis);
            } catch (DateException de) {
                throw new RuntimeException(de);
            }

            if (recurrences.length == 0) {
                throw new RuntimeException("can't use this method on first instance");
            }

            EventRecurrence excepRecurrence = new EventRecurrence();
            excepRecurrence.parse(origRrule);  // TODO: add+use a copy constructor instead
            excepRecurrence.count -= recurrences.length;
            newRrule = excepRecurrence.toString();

            origRecurrence.count = recurrences.length;

        } else {
            // The "until" time must be in UTC time in order for Google calendar
            // to display it properly. For all-day events, the "until" time string
            // must include just the date field, and not the time field. The
            // repeating events repeat up to and including the "until" time.
            Time untilTime = new Time();
            untilTime.timezone = Time.TIMEZONE_UTC;

            // Subtract one second from the old begin time to get the new
            // "until" time.
            untilTime.set(endTimeMillis - 1000); // subtract one second (1000 millis)
            if (origAllDay) {
                untilTime.hour = 0;
                untilTime.minute = 0;
                untilTime.second = 0;
                untilTime.allDay = true;
                untilTime.normalize(false);

                // This should no longer be necessary -- DTSTART should already be in the correct
                // format for an all-day event.
                dtstart.hour = 0;
                dtstart.minute = 0;
                dtstart.second = 0;
                dtstart.allDay = true;
                dtstart.timezone = Time.TIMEZONE_UTC;
            }
            origRecurrence.until = untilTime.format2445();
        }

        updateValues.put(Events.RRULE, origRecurrence.toString());
        updateValues.put(Events.DTSTART, dtstart.normalize(true));
        ContentProviderOperation.Builder b =
                ContentProviderOperation.newUpdate(
                        ContentUris.withAppendedId(
                            Events.CONTENT_URI, originalModel.mId))
                .withValues(updateValues);
        ops.add(b.build());

        return newRrule;
    }

    /**
     * Saves the reminders, if they changed. Returns true if operations to
     * update the database were added.
     *
     * @param ops the array of ContentProviderOperations
     * @param eventId the id of the event whose reminders are being updated
     * @param reminders the array of reminders set by the user
     * @param originalReminders the original array of reminders
     * @param forceSave if true, then save the reminders even if they didn't change
     * @return true if operations to update the database were added
     */
    public static boolean saveReminders(ArrayList<ContentProviderOperation> ops, long eventId,
            ArrayList<ReminderEntry> reminders, ArrayList<ReminderEntry> originalReminders,
            boolean forceSave) {
        // If the reminders have not changed, then don't update the database
        if (reminders.equals(originalReminders) && !forceSave) {
            return false;
        }

        // Delete all the existing reminders for this event
        String where = Reminders.EVENT_ID + "=?";
        String[] args = new String[] {Long.toString(eventId)};
        ContentProviderOperation.Builder b = ContentProviderOperation
                .newDelete(Reminders.CONTENT_URI);
        b.withSelection(where, args);
        ops.add(b.build());

        ContentValues values = new ContentValues();
        int len = reminders.size();

        // Insert the new reminders, if any
        for (int i = 0; i < len; i++) {
            ReminderEntry re = reminders.get(i);

            values.clear();
            values.put(Reminders.MINUTES, re.getMinutes());
            values.put(Reminders.METHOD, re.getMethod());
            values.put(Reminders.EVENT_ID, eventId);
            b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(values);
            ops.add(b.build());
        }
        return true;
    }

    /**
     * Saves the reminders, if they changed. Returns true if operations to
     * update the database were added. Uses a reference id since an id isn't
     * created until the row is added.
     *
     * @param ops the array of ContentProviderOperations
     * @param eventIdIndex the id of the event whose reminders are being updated
     * @param reminders the array of reminders set by the user
     * @param originalReminders the original array of reminders
     * @param forceSave if true, then save the reminders even if they didn't change
     */
    public static void saveRemindersWithBackRef(
        ArrayList<ContentProviderOperation> ops, int eventIdIndex,
        ArrayList<ReminderEntry> reminders, ArrayList<ReminderEntry> originalReminders,
        boolean forceSave)
    {
        // If the reminders have not changed, then don't update the database
        if (reminders.equals(originalReminders) && !forceSave) {
            return;
        }

        // Delete all the existing reminders for this event
        ContentProviderOperation.Builder b = ContentProviderOperation
                .newDelete(Reminders.CONTENT_URI);
        b.withSelection(Reminders.EVENT_ID + "=?", new String[1]);
        b.withSelectionBackReference(0, eventIdIndex);
        ops.add(b.build());

        ContentValues values = new ContentValues();
        int len = reminders.size();

        // Insert the new reminders, if any
        for (int i = 0; i < len; i++) {
            ReminderEntry re = reminders.get(i);

            values.clear();
            values.put(Reminders.MINUTES, re.getMinutes());
            values.put(Reminders.METHOD, re.getMethod());
            b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(values);
            b.withValueBackReference(Reminders.EVENT_ID, eventIdIndex);
            ops.add(b.build());
        }
    }

    // It's the first event in the series if the start time before being
    // modified is the same as the original event's start time
    static boolean isFirstEventInSeries(CalendarEventModel model,
            CalendarEventModel originalModel) {
        return model.mInstanceStart == originalModel.mEventStart;
    }

    // Adds an rRule and duration to a set of content values
    void addRecurrenceRule(ContentValues values, CalendarEventModel model) {
        String rrule = model.mRrule;

        values.put(Events.RRULE, rrule);
        long end = model.mEventEnd;
        long start = model.mEventStart;
        String duration = model.mDuration;

        boolean isAllDay = model.mAllDay;
        if (end >= start) {
            if (isAllDay) {
                // if it's all day compute the duration in days
                long days = (end - start + DateUtils.DAY_IN_MILLIS - 1)
                        / DateUtils.DAY_IN_MILLIS;
                duration = "P" + days + "D";
            } else {
                // otherwise compute the duration in seconds
                long seconds = (end - start) / DateUtils.SECOND_IN_MILLIS;
                duration = "P" + seconds + "S";
            }
        } else if (TextUtils.isEmpty(duration)) {

            // If no good duration info exists assume the default
            if (isAllDay) {
                duration = "P1D";
            } else {
                duration = "P3600S";
            }
        }
        // recurring events should have a duration and dtend set to null
        values.put(Events.DURATION, duration);
        values.put(Events.DTEND, (Long) null);
    }

    /**
     * Uses an event cursor to fill in the given model This method assumes the
     * cursor used {@link #EVENT_PROJECTION} as its query projection. It uses
     * the cursor to fill in the given model with all the information available.
     *
     * @param model The model to fill in
     * @param cursor An event cursor that used {@link #EVENT_PROJECTION} for the query
     */
    public static void setModelFromCursor(
        CalendarEventModel model, Cursor cursor)
    {
        if (model == null || cursor == null || cursor.getCount() != 1) {
            Log.wtf(TAG,
                "Attempted to build non-existent model or from an incorrect query.");
            return;
        }

        cursor.moveToFirst();

        model.mId = cursor.getInt(EVENT_INDEX_ID);
        model.mUid = cursor.getString(EVENT_INDEX_UID);
        model.mTitle = cursor.getString(EVENT_INDEX_TITLE);
        model.mDescription = cursor.getString(EVENT_INDEX_DESCRIPTION);
        model.mLocation = cursor.getString(EVENT_INDEX_EVENT_LOCATION);
        model.mAllDay = cursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        model.mHasAlarm = cursor.getInt(EVENT_INDEX_HAS_ALARM) != 0;
        model.mCalendarId = cursor.getInt(EVENT_INDEX_CALENDAR_ID);
        model.mEventStart = cursor.getLong(EVENT_INDEX_DTSTART);
        String tz = cursor.getString(EVENT_INDEX_TIMEZONE);
        if (TextUtils.isEmpty(tz)) {
            Log.w(TAG, "Query did not return a timezone for the event.");
            model.mTimezoneStart = TimeZone.getDefault().getID();
        } else {
            model.mTimezoneStart = tz;
        }
        tz = cursor.getString(EVENT_INDEX_END_TIMEZONE);
        if (TextUtils.isEmpty(tz)) {
            model.mTimezoneEnd = model.mTimezoneStart;
        } else {
            model.mTimezoneEnd = tz;
        }
        String rRule = cursor.getString(EVENT_INDEX_RRULE);
        model.mRrule = rRule;
        model.mSyncId = cursor.getString(EVENT_INDEX_SYNC_ID);
        model.mSyncAccountName = cursor.getString(EVENT_INDEX_ACCOUNT_NAME);
        model.mSyncAccountType = cursor.getString(EVENT_INDEX_ACCOUNT_TYPE);
        model.mAvailability = cursor.getInt(EVENT_INDEX_AVAILABILITY);
        int accessLevel = cursor.getInt(EVENT_INDEX_ACCESS_LEVEL);
        model.mOwnerAccount = cursor.getString(EVENT_INDEX_OWNER_ACCOUNT);
        model.mHasAttendeeData = cursor.getInt(EVENT_INDEX_HAS_ATTENDEE_DATA) != 0;
        model.mOriginalSyncId = cursor.getString(EVENT_INDEX_ORIGINAL_SYNC_ID);
        model.mOriginalId = cursor.getLong(EVENT_INDEX_ORIGINAL_ID);
        model.mOrganizer = cursor.getString(EVENT_INDEX_ORGANIZER);
        model.mIsOrganizer = model.mOwnerAccount.equalsIgnoreCase(model.mOrganizer);
        model.mGuestsCanModify = cursor.getInt(EVENT_INDEX_GUESTS_CAN_MODIFY) != 0;

        int rawEventColor;
        if (cursor.isNull(EVENT_INDEX_EVENT_COLOR)) {
            rawEventColor = cursor.getInt(EVENT_INDEX_CALENDAR_COLOR);
        } else {
            rawEventColor = cursor.getInt(EVENT_INDEX_EVENT_COLOR);
        }
        model.setEventColor(Utils.getDisplayColorFromColor(rawEventColor));

        model.mAccessLevel = accessLevel;
        model.mEventStatus = cursor.getInt(EVENT_INDEX_EVENT_STATUS);

        boolean hasRRule = !TextUtils.isEmpty(rRule);

        // We expect only one of these, so ignore the other
        if (hasRRule) {
            model.mDuration = cursor.getString(EVENT_INDEX_DURATION);
        } else {
            model.mEventEnd = cursor.getLong(EVENT_INDEX_DTEND);
        }

        model.mModelUpdatedWithEventCursor = true;
    }

    /**
     * Uses a calendar cursor to fill in the given model This method assumes the
     * cursor used {@link #CALENDARS_PROJECTION} as it's query projection
     * It uses the cursor to fill in the given model with all the information
     * available.
     *
     * @param model The model to fill in
     * @param cursor An event cursor that used {@link #CALENDARS_PROJECTION} for the query
     */
    public static void setModelFromCalendarCursor(CalendarEventModel model, Cursor cursor) {
        if (model == null || cursor == null) {
            Log.wtf(TAG, "Attempted to build non-existent model or from an incorrect query.");
            return;
        }

        if (model.mCalendarId == -1) {
            return;
        }

        if (!model.mModelUpdatedWithEventCursor) {
            Log.wtf(TAG,
                    "Can't update model with a Calendar cursor until it has seen an Event cursor.");
            return;
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            if (model.mCalendarId != cursor.getInt(CALENDARS_INDEX_ID)) {
                continue;
            }

            model.mOrganizerCanRespond = cursor.getInt(CALENDARS_INDEX_CAN_ORGANIZER_RESPOND) != 0;

            model.mCalendarAccessLevel = cursor.getInt(CALENDARS_INDEX_ACCESS_LEVEL);
            model.mCalendarDisplayName = cursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
            model.setCalendarColor(Utils.getDisplayColorFromColor(
                    cursor.getInt(CALENDARS_INDEX_COLOR)));

            model.mCalendarAccountName = cursor.getString(CALENDARS_INDEX_ACCOUNT_NAME);
            model.mCalendarAccountType = cursor.getString(CALENDARS_INDEX_ACCOUNT_TYPE);

            model.mCalendarAllowedAttendeeTypes = cursor
                    .getString(CALENDARS_INDEX_ALLOWED_ATTENDEE_TYPES);
            model.mCalendarAllowedAvailability = cursor
                    .getString(CALENDARS_INDEX_ALLOWED_AVAILABILITY);

            return;
       }
    }

    public static boolean canModifyEvent(CalendarEventModel model) {
        return canModifyCalendar(model)
                && (model.mIsOrganizer || model.mGuestsCanModify);
    }

    public static boolean canModifyCalendar(CalendarEventModel model) {
        return model.mCalendarAccessLevel >= Calendars.CAL_ACCESS_CONTRIBUTOR
                || model.mCalendarId == -1;
    }

    public static boolean canAddReminders(CalendarEventModel model) {
        return model.mCalendarAccessLevel >= Calendars.CAL_ACCESS_READ;
    }

    public static boolean canRespond(CalendarEventModel model) {
        // For non-organizers, write permission to the calendar is sufficient.
        // For organizers, the user needs a) write permission to the calendar
        // AND b) ownerCanRespond == true AND c) attendee data exist
        // (this means num of attendees > 1, the calendar owner's and others).
        // Note that mAttendeeList omits the organizer.

        // (there are more cases involved to be 100% accurate, such as
        // paying attention to whether or not an attendee status was
        // included in the feed, but we're currently omitting those corner cases
        // for simplicity).

        if (!canModifyCalendar(model)) {
            return false;
        }

        if (!model.mIsOrganizer) {
            return true;
        }

        if (!model.mOrganizerCanRespond) {
            return false;
        }

        // This means we don't have the attendees data so we can't send
        // the list of attendees and the status back to the server
        return !model.mHasAttendeeData || model.mAttendeesList.size() != 0;
    }

    /**
     * Goes through an event model and fills in content values for saving. This
     * method will perform the initial collection of values from the model and
     * put them into a set of ContentValues. It performs some basic work such as
     * fixing the time on allDay events and choosing whether to use an rrule or
     * dtend.
     *
     * @param model The complete model of the event you want to save
     * @return values
     */
    ContentValues getContentValuesFromModel(CalendarEventModel model) {
        String title = model.mTitle;
        boolean isAllDay = model.mAllDay;
        String rrule = model.mRrule;
        String startTimezone = model.mTimezoneStart;
        if (startTimezone == null) {
            startTimezone = TimeZone.getDefault().getID();
        }
        String endTimezone = model.mTimezoneEnd;
        if (endTimezone == null) {
            endTimezone = startTimezone;
        }
        Time startTime = new Time(startTimezone);
        Time endTime = new Time(endTimezone);

        startTime.set(model.mInstanceStart);
        endTime.set(model.mInstanceEnd);
        // Check if we have a weekly occurrence and the start day of the
        // event is not the occurrence day.
        // ??? why don't we do this for monthly or yearly?
        if ((rrule != null) && !rrule.isEmpty()) {
            mEventRecurrence.parse(rrule);
            if (   (mEventRecurrence.freq == EventRecurrence.WEEKLY)
                && (mEventRecurrence.byday != null)
                && (mEventRecurrence.byday.length
                   <= mEventRecurrence.bydayCount))
            {
                offsetStartTimeIfNecessary(startTime, endTime, model);
            }
        }
        ContentValues values = new ContentValues();

        long startMillis;
        long endMillis;
        long calendarId = model.mCalendarId;
        if (isAllDay) {
            // Reset start and end time, ensure at least 1 day duration, and set
            // the timezone to UTC, as required for all-day events.
            startTimezone = Time.TIMEZONE_UTC;
            startTime.hour = 0;
            startTime.minute = 0;
            startTime.second = 0;
            startTime.timezone = startTimezone;
            startMillis = startTime.normalize(true);

            endTime.hour = 0;
            endTime.minute = 0;
            endTime.second = 0;
            endTime.timezone = startTimezone;
            endMillis = endTime.normalize(true);
            if (endMillis < startMillis + DateUtils.DAY_IN_MILLIS) {
                // EditEventView#fillModelFromUI() should treat this case, but we want to ensure
                // the condition anyway.
                endMillis = startMillis + DateUtils.DAY_IN_MILLIS;
            }
        } else {
            startMillis = startTime.toMillis(true);
            endMillis = endTime.toMillis(true);
        }

        values.put(Events.CALENDAR_ID, calendarId);
        values.put(Events.EVENT_TIMEZONE, startTimezone);
        values.put(Events.EVENT_END_TIMEZONE, endTimezone);
        values.put(Events.TITLE, title);
        values.put(Events.ALL_DAY, isAllDay ? 1 : 0);
        values.put(Events.DTSTART, startMillis);
        values.put(Events.RRULE, rrule);
        if (!TextUtils.isEmpty(rrule)) {
            addRecurrenceRule(values, model);
        } else {
            values.put(Events.DURATION, (String) null);
            values.put(Events.DTEND, endMillis);
        }
        if (model.mDescription != null) {
            values.put(Events.DESCRIPTION, model.mDescription.trim());
        } else {
            values.put(Events.DESCRIPTION, (String) null);
        }
        if (model.mLocation != null) {
            values.put(Events.EVENT_LOCATION, model.mLocation.trim());
        } else {
            values.put(Events.EVENT_LOCATION, (String) null);
        }
        values.put(Events.AVAILABILITY, model.mAvailability);
        values.put(Events.HAS_ATTENDEE_DATA, model.mHasAttendeeData ? 1 : 0);

        values.put(Events.ACCESS_LEVEL, model.mAccessLevel);
        values.put(Events.STATUS, model.mEventStatus);
        if (model.isEventColorInitialized()) {
            if (model.getEventColor() == model.getCalendarColor()) {
                values.put(Events.EVENT_COLOR_KEY, NO_EVENT_COLOR);
            } else {
                values.put(Events.EVENT_COLOR_KEY, model.getEventColorKey());
            }
        }
        return values;
    }

    /**
     * If the recurrence rule is such that the event start date doesn't
     * actually fall in one of the recurrences, then push the start date
     * up to the first actual instance of the event.
     */
    private void offsetStartTimeIfNecessary(
        Time startTime, Time endTime, CalendarEventModel model)
    {
        // Start to figure out what the nearest weekday is.
        int closestWeekday = Integer.MAX_VALUE;
        int weekstart = EventRecurrence.day2TimeDay(mEventRecurrence.wkst);
        int startDay = startTime.weekDay;
        for (int i = 0; i < mEventRecurrence.bydayCount; i++) {
            int day = EventRecurrence.day2TimeDay(mEventRecurrence.byday[i]);
            if (day == startDay) {
                // Our start day is one of the recurring days, so we're good.
                return;
            }

            if (day < weekstart) {
                // Let's not make any assumptions about what weekstart can be.
                day += 7;
            }
            // We either want the earliest day that is later in the week than startDay ...
            if (day > startDay && (day < closestWeekday || closestWeekday < startDay)) {
                closestWeekday = day;
            }
            // ... or if there are no days later than startDay, we want the earliest day that is
            // earlier in the week than startDay.
            if (closestWeekday == Integer.MAX_VALUE || closestWeekday < startDay) {
                // We haven't found a day that's later in the week than startDay yet.
                if (day < closestWeekday) {
                    closestWeekday = day;
                }
            }
        }

        // We're here, so unfortunately our event's start day is not included
        // in the days of the week of the recurrence. To save this event
        // correctly we'll need to push the start date to the closest weekday
        // that *is* part of the recurrence.
        if (closestWeekday < startDay) {
            closestWeekday += 7;
        }
        int daysOffset = closestWeekday - startDay;
        startTime.monthDay += daysOffset;
        endTime.monthDay += daysOffset;
        model.mInstanceStart = startTime.normalize(true);
        model.mInstanceEnd = endTime.normalize(true);
    }

    public interface EditDoneRunnable extends Runnable {
        void setDoneCode(int code);
    }
}
