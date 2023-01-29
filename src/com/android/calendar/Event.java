/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.calendar;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Debug;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.calendar.settings.GeneralPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ws.xsoh.etar.R;

// TODO: should Event be Parcelable so it can be passed via Intents?
/* Note that "Event" here is actually a (non-deleted and non-excepted)
 * instance from the virtual CalendarContract.Instances table, not an event
 * from the CalendarContract.Events table.
 * I really ought to rename it everywhere as "Instance", but this is a
 * massive task to fix what the original Google developers got wrong.
 */
public class Event implements Cloneable {

    private static final String TAG = "CalEvent";
    private static final boolean PROFILE = false;

    /**
     * The sort order is:
     * 1) events with an earlier start (begin for normal events, startday for allday)
     * 2) events with a later end (end for normal events, endday for allday)
     * 3) the title (unnecessary, but nice)
     *
     * The start and end day is sorted first so that all day events are
     * sorted correctly with respect to events that are >24 hours (and
     * therefore show up in the allday area).
     */
    private static final String SORT_EVENTS_BY =
            "begin ASC, end DESC, title ASC";
    private static final String SORT_ALLDAY_BY =
            "startDay ASC, endDay DESC, title ASC";
    private static final String DISPLAY_AS_ALLDAY = "dispAllday";
    // The projection to use when querying instances to build a list of events
    public static final String[] EVENT_PROJECTION = new String[] {
            Instances.TITLE,
            Instances.EVENT_LOCATION,
            Instances.ALL_DAY,
            Instances.DISPLAY_COLOR,
            Instances.EVENT_TIMEZONE,
            Instances.EVENT_ID,
            Instances.BEGIN,
            Instances.END,
            Instances._ID,
            Instances.START_DAY,
            Instances.END_DAY,
            Instances.START_MINUTE,
            Instances.END_MINUTE,
            Instances.HAS_ALARM,
            Instances.RRULE,
            Instances.RDATE,
            Instances.SELF_ATTENDEE_STATUS,
            Events.ORGANIZER,
            Events.GUESTS_CAN_MODIFY,
            Instances.ALL_DAY + "=1 OR (" + Instances.END + "-" + Instances.BEGIN + ")>="
                    + DateUtils.DAY_IN_MILLIS + " AS " + DISPLAY_AS_ALLDAY, // 19
    };
    // This looks a bit messy, but it makes the compiler do the work
    // and avoids the maintenance burden of keeping track of the indices by hand.
    private static final List<String> eventProjection = Arrays.asList(EVENT_PROJECTION);
    // The indices for the projection array above.
    private static final int PROJECTION_TITLE_INDEX =
        eventProjection.indexOf(Instances.TITLE);
    private static final int PROJECTION_LOCATION_INDEX =
        eventProjection.indexOf(Instances.EVENT_LOCATION);
    private static final int PROJECTION_ALL_DAY_INDEX =
        eventProjection.indexOf(Instances.ALL_DAY);
    private static final int PROJECTION_COLOR_INDEX =
        eventProjection.indexOf(Instances.DISPLAY_COLOR);
    @SuppressWarnings("unused")
    private static final int PROJECTION_TIMEZONE_INDEX =
        eventProjection.indexOf(Instances.EVENT_TIMEZONE);
    private static final int PROJECTION_EVENT_ID_INDEX =
        eventProjection.indexOf(Instances.EVENT_ID);
    private static final int PROJECTION_BEGIN_INDEX =
        eventProjection.indexOf(Instances.BEGIN);
    private static final int PROJECTION_END_INDEX =
        eventProjection.indexOf(Instances.END);
    private static final int PROJECTION_START_DAY_INDEX =
        eventProjection.indexOf(Instances.START_DAY);
    private static final int PROJECTION_END_DAY_INDEX =
        eventProjection.indexOf(Instances.END_DAY);
    private static final int PROJECTION_START_MINUTE_INDEX =
        eventProjection.indexOf(Instances.START_MINUTE);
    private static final int PROJECTION_END_MINUTE_INDEX =
        eventProjection.indexOf(Instances.END_MINUTE);
    private static final int PROJECTION_HAS_ALARM_INDEX =
        eventProjection.indexOf(Instances.HAS_ALARM);
    private static final int PROJECTION_RRULE_INDEX =
        eventProjection.indexOf(Instances.RRULE);
    private static final int PROJECTION_RDATE_INDEX =
        eventProjection.indexOf(Instances.RDATE);
    private static final int PROJECTION_SELF_ATTENDEE_STATUS_INDEX =
        eventProjection.indexOf(Instances.SELF_ATTENDEE_STATUS);
    private static final int PROJECTION_ORGANIZER_INDEX =
        eventProjection.indexOf(Events.ORGANIZER);
    private static final int PROJECTION_GUESTS_CAN_MODIFY =
        eventProjection.indexOf(Instances.GUESTS_CAN_MODIFY);

    private static final String EVENTS_WHERE = DISPLAY_AS_ALLDAY + "=0";
    private static final String ALLDAY_WHERE = DISPLAY_AS_ALLDAY + "=1";
    private static String mNoTitleString;
    private static int mNoColorColor;
    public long id;
    public int color;
    public CharSequence title;
    public CharSequence location;
    public boolean allDay;
    public String organizer;
    public boolean guestsCanModify;
    public int startDay;       // start Julian day
    public int endDay;         // end Julian day
    public int startTime;      // Start and end time are in minutes since midnight
    public int endTime;
    public long startMillis;   // UTC milliseconds since the epoch
    public long endMillis;     // UTC milliseconds since the epoch
    public boolean hasAlarm;
    public boolean isRepeating;
    public int selfAttendeeStatus;
    // The coordinates of the event rectangle drawn on the screen.
    // Note these are not constant: if the event overlaps two days they get overwritten
    public float left;
    public float right;
    public float top;
    public float bottom;
    // These 4 fields are used for navigating among events within the selected
    // hour in the Day and Week view.
    private int mColumn;
    private int mMaxColumns;

    /**
     * Loads <i>days</i> days worth of instances starting at <i>startDay</i>.
     */
    public static void loadEvents(Context context, ArrayList<Event> events, int startDay, int days,
            int requestId, AtomicInteger sequenceNumber) {

        if (PROFILE) {
            Debug.startMethodTracing("loadEvents");
        }

        if (Build.VERSION.SDK_INT >= 23 && context.checkSelfPermission(
                Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            //If permission is not granted then just return.
            return;
        }

        Cursor cEvents = null;
        Cursor cAllday = null;

        events.clear();
        try {
            int endDay = startDay + days - 1;

            // We use the byDay instances query to get a list of all events for
            // the days we're interested in.
            // The sort order is: events with an earlier start time occur
            // first and if the start times are the same, then events with
            // a later end time occur first. The later end time is ordered
            // first so that long rectangles in the calendar views appear on
            // the left side.  If the start and end times of two events are
            // the same then we sort alphabetically on the title.  This isn't
            // required for correctness, it just adds a nice touch.

            // Respect the preference to show/hide declined events
            SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(context);
            boolean hideDeclined = prefs.getBoolean(GeneralPreferences.KEY_HIDE_DECLINED,
                    false);

            String where = EVENTS_WHERE;
            String whereAllday = ALLDAY_WHERE;
            if (hideDeclined) {
                String hideString = " AND " + Instances.SELF_ATTENDEE_STATUS + "!="
                        + Attendees.ATTENDEE_STATUS_DECLINED;
                where += hideString;
                whereAllday += hideString;
            }

            cEvents = instancesQuery(context.getContentResolver(), startDay,
                    endDay, where, SORT_EVENTS_BY);
            cAllday = instancesQuery(context.getContentResolver(), startDay,
                    endDay, whereAllday, SORT_ALLDAY_BY);

            // Check if we should return early because there are more recent
            // load requests waiting.
            if (requestId != sequenceNumber.get()) {
                return;
            }

            buildEventsFromCursor(events, cEvents, context, startDay, endDay);
            buildEventsFromCursor(events, cAllday, context, startDay, endDay);

        } finally {
            if (cEvents != null) {
                cEvents.close();
            }
            if (cAllday != null) {
                cAllday.close();
            }
            if (PROFILE) {
                Debug.stopMethodTracing();
            }
        }
    }

    /**
     * Performs a query to return all visible instances in the given range
     * that match the given selection. This is a blocking function and
     * should not be done on the UI thread. This will cause an expansion of
     * recurring events to fill this time range if they are not already
     * expanded and will slow down for larger time ranges with many
     * recurring events.
     *
     * @param cr The ContentResolver to use for the query
     * @param startDay The start of the time range to query in UTC millis since
     *            epoch
     * @param endDay The end of the time range to query in UTC millis since
     *            epoch
     * @param selection Filter on the query as an SQL WHERE statement
     * @param orderBy How to order the rows as an SQL ORDER BY statement
     * @return A Cursor of instances matching the selection
     */
    private static Cursor instancesQuery(
        ContentResolver cr, int startDay, int endDay, String selection, String orderBy) {
        final String WHERE_CALENDARS_SELECTED = Calendars.VISIBLE + "=?";
        final String[] selectionArgs = {"1"};

        Uri.Builder builder = Instances.CONTENT_BY_DAY_URI.buildUpon();
        ContentUris.appendId(builder, startDay);
        ContentUris.appendId(builder, endDay);
        if (TextUtils.isEmpty(selection)) {
            selection = WHERE_CALENDARS_SELECTED;
        } else {
            selection = "(" + selection + ") AND " + WHERE_CALENDARS_SELECTED;
        }
        return cr.query(builder.build(), Event.EVENT_PROJECTION, selection, selectionArgs,
                orderBy == null ? "begin ASC" : orderBy);
    }

    /**
     * Adds all the events from the cursors to the events list.
     *
     * @param events The list of events
     * @param cEvents Events to add to the list
     * @param context Context for gfetting resources
     * @param startDay start day for choosing events from query result
     * @param endDay end day for choosing events from query result
     */
    public static void buildEventsFromCursor(ArrayList<Event> events, Cursor cEvents,
                                             Context context, int startDay, int endDay)
    {
        if (cEvents == null || events == null) {
            Log.e(TAG, "buildEventsFromCursor: null cursor or null events list!");
            return;
        }

        int count = cEvents.getCount();

        if (count == 0) {
            return;
        }

        Resources res = context.getResources();
        mNoTitleString = res.getString(R.string.no_title_label);
        mNoColorColor = res.getColor(R.color.event_center);
        // Sort events in two passes so we ensure the allday and standard events
        // get sorted in the correct order
        cEvents.moveToPosition(-1);
        while (cEvents.moveToNext()) {
            Event e = generateEventFromCursor(cEvents);
            if (e.startDay > endDay || e.endDay < startDay) {
                continue;
            }
            events.add(e);
        }
    }

    /**
     * @param cEvents Cursor pointing at event
     * @return An event created from the cursor
     */
    private static Event generateEventFromCursor(Cursor cEvents) {
        Event e = new Event();

        e.id = cEvents.getLong(PROJECTION_EVENT_ID_INDEX);
        e.title = cEvents.getString(PROJECTION_TITLE_INDEX);
        e.location = cEvents.getString(PROJECTION_LOCATION_INDEX);
        e.allDay = cEvents.getInt(PROJECTION_ALL_DAY_INDEX) != 0;
        e.organizer = cEvents.getString(PROJECTION_ORGANIZER_INDEX);
        e.guestsCanModify = cEvents.getInt(PROJECTION_GUESTS_CAN_MODIFY) != 0;

        if (e.title == null || e.title.length() == 0) {
            e.title = mNoTitleString;
        }

        if (!cEvents.isNull(PROJECTION_COLOR_INDEX)) {
            // Read the color from the database
            e.color = Utils.getDisplayColorFromColor(cEvents.getInt(PROJECTION_COLOR_INDEX));
        } else {
            e.color = mNoColorColor;
        }

        long eStart = cEvents.getLong(PROJECTION_BEGIN_INDEX);
        long eEnd = cEvents.getLong(PROJECTION_END_INDEX);

        e.startMillis = eStart;
        e.startTime = cEvents.getInt(PROJECTION_START_MINUTE_INDEX);
        e.startDay = cEvents.getInt(PROJECTION_START_DAY_INDEX);

        e.endMillis = eEnd;
        e.endTime = cEvents.getInt(PROJECTION_END_MINUTE_INDEX);
        e.endDay = cEvents.getInt(PROJECTION_END_DAY_INDEX);

        e.hasAlarm = cEvents.getInt(PROJECTION_HAS_ALARM_INDEX) != 0;

        // Check if this is a repeating event
        String rrule = cEvents.getString(PROJECTION_RRULE_INDEX);
        String rdate = cEvents.getString(PROJECTION_RDATE_INDEX);
        e.isRepeating = !TextUtils.isEmpty(rrule) || !TextUtils.isEmpty(rdate);

        e.selfAttendeeStatus = cEvents.getInt(PROJECTION_SELF_ATTENDEE_STATUS_INDEX);
        return e;
    }

    /**
     * Computes a position for each event.  Each event is displayed
     * as a non-overlapping rectangle.  For normal events, these rectangles
     * are displayed in separate columns in the week view and day view.  For
     * all-day events, these rectangles are displayed in separate rows along
     * the top.  In both cases, each event is assigned two numbers: N, and
     * Max, that specify that this event is the Nth event of Max number of
     * events that are displayed in a group. The width and position of each
     * rectangle depend on the maximum number of rectangles that occur at
     * the same time.
     *
     * @param eventsList the list of events, sorted into increasing time order
     * @param minimumDurationMillis minimum duration acceptable as cell height of each event
     * rectangle in millisecond. Should be 0 when it is not determined.
     */
    /* package */ static void computePositions(ArrayList<Event> eventsList,
            long minimumDurationMillis) {
        if (eventsList == null) {
            return;
        }

        // Compute the column positions separately for the all-day events
        doComputePositions(eventsList, minimumDurationMillis, false);
        doComputePositions(eventsList, minimumDurationMillis, true);
    }

    private static void doComputePositions(ArrayList<Event> eventsList,
            long minimumDurationMillis, boolean doAlldayEvents) {
        final ArrayList<Event> activeList = new ArrayList<>();
        final ArrayList<Event> groupList = new ArrayList<>();

        if (minimumDurationMillis < 0) {
            minimumDurationMillis = 0;
        }

        long colMask = 0;
        int maxCols = 0;
        for (Event event : eventsList) {
            // Process all-day events separately
            if (event.drawAsAllday() != doAlldayEvents)
                continue;

           if (!doAlldayEvents) {
                colMask = removeNonAlldayActiveEvents(
                        event, activeList.iterator(), minimumDurationMillis, colMask);
            } else {
                colMask = removeAlldayActiveEvents(event, activeList.iterator(), colMask);
            }

            // If the active list is empty, then reset the max columns, clear
            // the column bit mask, and empty the groupList.
            if (activeList.isEmpty()) {
                for (Event ev : groupList) {
                    ev.setMaxColumns(maxCols);
                }
                maxCols = 0;
                colMask = 0;
                groupList.clear();
            }

            // Find the first empty column.  Empty columns are represented by
            // zero bits in the column mask "colMask".
            int col = findFirstZeroBit(colMask);
            if (col == 64)
                col = 63;
            colMask |= (1L << col);
            event.setColumn(col);
            activeList.add(event);
            groupList.add(event);
            int len = activeList.size();
            if (maxCols < len)
                maxCols = len;
        }
        for (Event ev : groupList) {
            ev.setMaxColumns(maxCols);
        }
    }

    private static long removeAlldayActiveEvents(Event event, Iterator<Event> iter, long colMask) {
        // Remove the inactive allday events. An event on the active list
        // becomes inactive when the end day is less than the current event's
        // start day.
        while (iter.hasNext()) {
            final Event active = iter.next();
            if (active.endDay < event.startDay) {
                colMask &= ~(1L << active.getColumn());
                iter.remove();
            }
        }
        return colMask;
    }

    private static long removeNonAlldayActiveEvents(
            Event event, Iterator<Event> iter, long minDurationMillis, long colMask) {
        long start = event.getStartMillis();
        // Remove the inactive events. An event on the active list
        // becomes inactive when its end time is less than or equal to
        // the current event's start time.
        while (iter.hasNext()) {
            final Event active = iter.next();

            final long duration = Math.max(
                    active.getEndMillis() - active.getStartMillis(), minDurationMillis);
            if ((active.getStartMillis() + duration) <= start) {
                colMask &= ~(1L << active.getColumn());
                iter.remove();
            }
        }
        return colMask;
    }

    public static int findFirstZeroBit(long val) {
        for (int ii = 0; ii < 64; ++ii) {
            if ((val & (1L << ii)) == 0)
                return ii;
        }
        return 64;
    }

    /**
     * Indicates whether some other object is "equal to" this one.
     * <p>
     * The {@code equals} method implements an equivalence relation
     * on non-null object references:
     * <ul>
     * <li>It is <i>reflexive</i>: for any non-null reference value
     *     {@code x}, {@code x.equals(x)} should return
     *     {@code true}.
     * <li>It is <i>symmetric</i>: for any non-null reference values
     *     {@code x} and {@code y}, {@code x.equals(y)}
     *     should return {@code true} if and only if
     *     {@code y.equals(x)} returns {@code true}.
     * <li>It is <i>transitive</i>: for any non-null reference values
     *     {@code x}, {@code y}, and {@code z}, if
     *     {@code x.equals(y)} returns {@code true} and
     *     {@code y.equals(z)} returns {@code true}, then
     *     {@code x.equals(z)} should return {@code true}.
     * <li>It is <i>consistent</i>: for any non-null reference values
     *     {@code x} and {@code y}, multiple invocations of
     *     {@code x.equals(y)} consistently return {@code true}
     *     or consistently return {@code false}, provided no
     *     information used in {@code equals} comparisons on the
     *     objects is modified.
     * <li>For any non-null reference value {@code x},
     *     {@code x.equals(null)} should return {@code false}.
     * </ul>
     * <p>
     * The {@code equals} method for class {@code Object} implements
     * the most discriminating possible equivalence relation on objects;
     * that is, for any non-null reference values {@code x} and
     * {@code y}, this method returns {@code true} if and only
     * if {@code x} and {@code y} refer to the same object
     * ({@code x == y} has the value {@code true}).
     * <p>
     * Note that it is generally necessary to override the {@code hashCode}
     * method whenever this method is overridden, so as to maintain the
     * general contract for the {@code hashCode} method, which states
     * that equal objects must have equal hash codes.
     *
     * @param obj the reference object with which to compare.
     * @return {@code true} if this object is the same as the obj
     * argument; {@code false} otherwise.
     * @see #hashCode()
     * @see HashMap
     *
     * In this case events get regularly recreated when the events are re-read
     * from the calendar, and some properties of the event may differ according
     * to the view which holds it. To make event selection persist, we need to
     * reimplement this function so that references to the same underlying
     * event are "equal". We don't fix up hashCode() since we don't use it for
     * events.
     */
    @Override
    public boolean equals(Object obj) {
        if (   (obj == null)
            || (obj.getClass() != getClass()))
        {
            return false;
        } else {
            Event e = (Event)obj;
            if (title == null) {
                if (e.title != null) { return false; }
            } else if (e.title == null) {
                return false;
            } else if (title.toString().compareTo(e.title.toString()) != 0) {
                return false;
            }
            if (location == null) {
                if (e.location != null) { return false; }
            } else if (e.location == null) {
                return false;
            } else if (location.toString().compareTo(e.location.toString()) != 0) {
                return false;
            }
            return (   (e.id == id)
                    && (e.startMillis == startMillis)
                    && (e.endMillis == endMillis));
        }
    }

    @Override
    public final Object clone() throws CloneNotSupportedException {
        super.clone();
        Event e = new Event();

        e.title = title;
        e.color = color;
        e.location = location;
        e.allDay = allDay;
        e.startDay = startDay;
        e.endDay = endDay;
        e.startTime = startTime;
        e.endTime = endTime;
        e.startMillis = startMillis;
        e.endMillis = endMillis;
        e.hasAlarm = hasAlarm;
        e.isRepeating = isRepeating;
        e.selfAttendeeStatus = selfAttendeeStatus;
        e.organizer = organizer;
        e.guestsCanModify = guestsCanModify;

        return e;
    }

    public final void copyTo(Event dest) {
        dest.id = id;
        dest.title = title;
        dest.color = color;
        dest.location = location;
        dest.allDay = allDay;
        dest.startDay = startDay;
        dest.endDay = endDay;
        dest.startTime = startTime;
        dest.endTime = endTime;
        dest.startMillis = startMillis;
        dest.endMillis = endMillis;
        dest.hasAlarm = hasAlarm;
        dest.isRepeating = isRepeating;
        dest.selfAttendeeStatus = selfAttendeeStatus;
        dest.organizer = organizer;
        dest.guestsCanModify = guestsCanModify;
    }

    @SuppressWarnings("unused")
    public final void dump() {
        Log.e("Cal", "+-----------------------------------------+");
        Log.e("Cal", "+        id = " + id);
        Log.e("Cal", "+     color = " + color);
        Log.e("Cal", "+     title = " + title);
        Log.e("Cal", "+  location = " + location);
        Log.e("Cal", "+    allDay = " + allDay);
        Log.e("Cal", "+  startDay = " + startDay);
        Log.e("Cal", "+    endDay = " + endDay);
        Log.e("Cal", "+ startTime = " + startTime);
        Log.e("Cal", "+   endTime = " + endTime);
        Log.e("Cal", "+ organizer = " + organizer);
        Log.e("Cal", "+  guestwrt = " + guestsCanModify);
    }

    /**
     * Returns the event title and location separated by a comma.  If the
     * location is already part of the title (at the end of the title), then
     * just the title is returned.
     *
     * @return the event title and location as a String
     */
    public String getTitleAndLocation() {
        String text = title.toString();

        // Append the location to the title, unless the title ends with the
        // location (for example, "meeting in building 42" ends with the
        // location).
        if (location != null) {
            String locationString = location.toString();
            if (!text.endsWith(locationString)) {
                text += ", " + locationString;
            }
        }
        return text;
    }

    public int getColumn() {
        return mColumn;
    }

    public void setColumn(int column) {
        mColumn = column;
    }

    public int getMaxColumns() {
        return mMaxColumns;
    }

    public void setMaxColumns(int maxColumns) {
        mMaxColumns = maxColumns;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public void setEndMillis(long endMillis) {
        this.endMillis = endMillis;
    }

    public boolean drawAsAllday() {
        // Use >= so we'll pick up Exchange allday events
        return allDay || endMillis - startMillis >= DateUtils.DAY_IN_MILLIS;
    }
}
