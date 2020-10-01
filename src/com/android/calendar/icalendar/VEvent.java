/*
 * Copyright (C) 2014-2016 The CyanogenMod Project
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

package com.android.calendar.icalendar;

import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.format.Time;

import com.android.calendar.CalendarEventModel;

import java.util.ListIterator;
import java.util.UUID;

/**
 * Models the Event/VEvent component of the iCalendar format
 */
public class VEvent {

    /**
     * Constructor
     */
    public VEvent() {
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n");
    }

    private static String unEscaoe(String s) {
        return s.replace("\\n", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    /**
     * Returns the iCal representation of the Event component
     */
    public String getICalFormattedString(CalendarEventModel event) {
        StringBuilder sb = new StringBuilder();

        // Add Event properties
        sb.append("BEGIN:VEVENT\n");
        Time tStart = new Time("UTC");
        tStart.setToNow();
        Time tEnd = new Time("UTC");
        tEnd.setToNow();
        sb.append(tStart.format("DTSTAMP:%Y%m%dT%H%M%SZ\n"));
        if (event.mUid == null) {
            // UID is required, so we must generate one
            event.mUid = UUID.randomUUID().toString() + tStart.format("Q%Y%m%dT%H%M%S");
        }
        sb.append("UID:").append(event.mUid).append("\n");
        if (event.mAllDay) {
            tStart.set(event.mStart);
            sb.append(tStart.format("DTSTART;VALUE=DATE:%Y%m%d\n"));
            if (event.mDuration == null ) {
                tEnd.set(event.mEnd);
                sb.append(tEnd.format("DTEND;VALUE=DATE:%Y%m%d\n"));
            }
        } else {
            String timezone = event.mTimezoneStart;
            if (timezone != null) {
                tStart.switchTimezone(timezone);
            }
            tStart.set(event.mStart);
            sb.append("DTSTART;TZID=").append(timezone)
                .append(tStart.format(":%Y%m%dT%H%M%S\n"));
            if (event.mDuration == null ) {
                tEnd.set(event.mEnd);
                if (event.mTimezoneEnd != null) {
                    timezone = event.mTimezoneEnd;
                }
                tEnd.switchTimezone(timezone);
                sb.append("DTEND;TZID=").append(timezone)
                    .append(tEnd.format(":%Y%m%dT%H%M%S\n"));
            }
        }
        if (event.mDuration != null) {
            sb.append("DURATION:").append(event.mDuration).append("\n");
        }
        if (event.mRrule != null) {
            sb.append("RRULE:").append(event.mRrule).append("\n");
        }
        if (event.mRdate != null) {
            sb.append("RDATE:").append(event.mRdate).append("\n");
        }
        if (event.mExdate != null) {
            sb.append("EXDATE:").append(event.mExdate).append("\n");
        }
        if (event.mTitle != null) {
            sb.append("SUMMARY:").append(escape(event.mTitle)).append("\n");
        }
        if (event.mDescription != null) {
            sb.append("DESCRIPTION:").append(escape(event.mDescription)).append("\n");
        }
        if (event.mLocation != null) {
            sb.append("LOCATION:").append(escape(event.mLocation)).append("\n");
        }
        switch (event.mAccessLevel) {
            default:
            case Events.ACCESS_DEFAULT:
            case Events.ACCESS_PUBLIC:
                // This is the default for RFC5545, so do nothing
                break;
            case Events.ACCESS_PRIVATE:
                sb.append("CLASS:PRIVATE");
                break;
            case Events.ACCESS_CONFIDENTIAL:
                // This is used by RFC5545, but currently not by Android
                sb.append("CLASS:CONFIDENTIAL");
                break;
        }
        if (event.mAvailability == Events.AVAILABILITY_FREE) {
            sb.append("TRANSP:TRANSPARENT\n");
        }
        switch(event.mEventStatus) {
            case Events.STATUS_TENTATIVE:
                sb.append("STATUS:TENTATIVE\n");
                break;
            case Events.STATUS_CANCELED:
                sb.append("STATUS:CANCELED\n");
                break;
            default:
            case Events.STATUS_CONFIRMED:
                // do nothing: this is the default for RFC5545
                break;
        }
        // Add event reminders
        for (CalendarEventModel.ReminderEntry entry : event.mReminders) {
            sb.append("BEGIN:VALARM\n");
            sb.append("TRIGGER;VALUE=DURATION:-PT");
            int minutes = entry.getMinutes();
            if (minutes < 0) { minutes = 0; }
            sb.append(minutes);
            sb.append("M\n");
            switch (entry.getMethod()) {
                default:
                case Reminders.METHOD_DEFAULT:
                case Reminders.METHOD_ALERT:
                    sb.append("ACTION:DISPLAY\n");
                    sb.append("DESCRIPTION:");
                    if (event.mTitle != null) { sb.append(event.mTitle); }
                    else if (event.mLocation != null) { sb.append(event.mLocation); }
                    else if (event.mDescription != null) {
                        sb.append(escape(event.mDescription));
                    } else {
                        // RFC5545 requires that we must have some text here
                        sb.append("Event reminder");
                    }
                    sb.append("\n");
                    break;
                case Reminders.METHOD_EMAIL:
                case Reminders.METHOD_SMS:
                    // RFC5545 doesn't know about SMS, treat as email
                    sb.append("ACTION:EMAIL\n");
                    if (event.mTitle == null) {
                        sb.append("SUMMARY:").append(event.mTitle).append("\n");
                    } else {
                        sb.append("SUMMARY:Event Reminder\n");
                    }
                    if (event.mDescription == null) {
                        sb.append("DESCRIPTION:").append(event.mDescription).append("\n");
                    } else {
                        sb.append("DESCRIPTION:Event Reminder\n");
                    }
                    for (CalendarEventModel.Attendee attendee
                        : event.mAttendeesList.values())
                    {
                        if (   (attendee.mStatus == Attendees.ATTENDEE_STATUS_ACCEPTED)
                            && (attendee.mType != Attendees.TYPE_RESOURCE)
                            && (attendee.mEmail != null))
                        {
                            sb.append("ATTENDEE:MAILTO:")
                                .append(attendee.mEmail).append("\n");
                        }
                    }
                    break;
                case Reminders.METHOD_ALARM:
                    sb.append("ACTION:AUDIO\n");
                    // We don't have a way of passing a sound file so any device
                    // which reads this ical will just play its default reminder sound.
                    break;
            }
            sb.append("END:VALARM\n");
        }
        // Add event Attendees
        for (CalendarEventModel.Attendee attendee : event.mAttendeesList.values()) {
            sb.append("ATTENDEE;RSVP=TRUE");
            if (attendee.mName != null) {
                sb.append(";CN=").append(attendee.mName);
            }
            switch(attendee.mStatus) {
                default:
                case Attendees.ATTENDEE_STATUS_NONE:
                    break;
                case Attendees.ATTENDEE_STATUS_ACCEPTED:
                    sb.append(";PARTSTAT=ACCEPTED");
                    break;
                case Attendees.ATTENDEE_STATUS_DECLINED:
                    sb.append(";PARTSTAT=DECLINED");
                    break;
                case Attendees.ATTENDEE_STATUS_INVITED:
                    sb.append(";PARTSTAT=NEEDS-ACTION");
                    break;
                case Attendees.ATTENDEE_STATUS_TENTATIVE:
                    sb.append(";PARTSTAT=TENTATIVE");
                    break;
            }
            switch(attendee.mType) {
                default:
                case Attendees.TYPE_NONE:
                    sb.append(";CUTYPE=INDIVIDUAL;ROLE=NON-PARTICIPANT");
                    break;
                case Attendees.TYPE_REQUIRED:
                    sb.append(";CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT");
                    break;
                case Attendees.TYPE_OPTIONAL:
                    sb.append(";CUTYPE=INDIVIDUAL;ROLE=OPT-PARTICIPANT");
                    break;
                case Attendees.TYPE_RESOURCE:
                    sb.append(";CUTYPE=RESOURCE;ROLE=REQ-PARTICIPANT");
                    break;
            }
            if (attendee.mEmail == null) {
                sb.append(":UNKNOWN\n");
            } else {
                sb.append(":MAILTO:").append(attendee.mEmail).append("\n");
            }
        }
        if ((event.mOrganizer != null) || (event.mOrganizerDisplayName != null)) {
            sb.append("ORGANIZER");
            if (event.mOrganizerDisplayName != null) {
                sb.append(";CN=")
                    .append(escape(event.mOrganizerDisplayName));
            }
            if (event.mOrganizer != null) {
                sb.append(":mailto:").append(event.mOrganizer);
            } else {
                sb.append("UNKNOWN");
            }
            sb.append("\n");
        }

        sb.append("END:VEVENT\n");

        // Enforce line length requirements
        sb = IcalendarUtils.enforceICalLineLength(sb);

        return sb.toString();
    }

    // Returns duration in minutes or -1 if bad.
    // This doesn't catch all bad ones, but should decode all good ones.
    // Android doesn't support negative durations, so we reject them
    private static int parseDuration(String s) {
        if ((s == null) || s.isEmpty() || s.contains(",")) { return -1; }
        int i;
        for (i = 0; i < s.length(); ++i) {
            char c = s.charAt(i);
            if (c == '-') { return -1; }
            if (c == '+') { continue; }
            if ((c == 'p') || (c == 'P')) { break; }
            return -1;
        }
        int result = 0;
        int part = 0;
        for (i = i + 1; i < s.length(); ++i) {
            char c = s.charAt(i);
            if ((c >= '0') && (c <= '9')) {
                part = part * 10 + c - '0';
            } else if ((c == 'w') || (c == 'W')) {
                return part * 60 * 24 * 7;
            } else if ((c == 'd') || ( c== 'D')) {
                result = part * 60 * 24;
                part = 0;
                if (i == s.length() - 1) { return result; }
                c = s.charAt(++i);
                if ((c == 't') || (c== 'T')) { break; } else {return -1; }
            } else if ((c == 't') || (c == 'T')) {
                if (part != 0) { return -1; } else { break; }
            } else { return -1; }
        }
        for (++i; i < s.length(); ++i) {
            char c = s.charAt(i);
            if ((c >= '0') && (c <= '9')) {
                part = part * 10 + c - '0';
            } else if ((c == 'h') || (c == 'H')) {
                result += part * 60; part = 0;
            } else if ((c == 'm') || (c == 'M')) {
                result += part; part = 0;
            } else if ((c == 's') || (c == 'S')) {
                result += part / 60; part = 0;
            }
        }
        if (part != 0) { return -1; } else { return result; }
    }

    // This is an ugly hack: some icals have timezones prefixed by
    // /freeassociation.sourceforge.net/ which Android doesn't understand
    private static final String chop = "/freeassociation.sourceforge.net/";

    private static final int which_DTSTART = 0;
    private static final int which_DTEND = 1;
    private static void parseDateTime(String[] split1, CalendarEventModel event, int which)
    {
        if (!split1[0].isEmpty()) {
            String tz = Time.getCurrentTimezone();
            String[] split2 = split1[0].split("[=;]");
            int n = split2.length / 2;
            for (int i = 0; i < n; ++i) {
                if (split2[2 * i + 1].equalsIgnoreCase("TZID")) {
                    tz = split2[2 * i + 2].replace(chop, "");
                }
            }
            Time t = new Time(tz);
            if (t.parse(split1[1])) { tz = "UTC"; }
            switch (which) {
                case which_DTSTART:
                    event.mOriginalStart = event.mStart = t.normalize(false);
                    event.mTimezoneStart = tz;
                    event.mAllDay = t.allDay;
                    break;
                case which_DTEND:
                    event.mOriginalEnd = event.mEnd = t.normalize(false);
                    event.mTimezoneEnd = tz;
                    break;
            }
        }
    }

    // This parses an RDATE or EXDATE property.
    // RFC5545 allows multiple EXDATE or RDATE properties,
    // and each one can have a comma-separated list of dates.
    // Android can only have a single comma-separated list,
    // So we pass the old list and append any valid dates we find to it.
    static private String parseDateList(String[] split1, String old)
    {
        String[] split2 = split1[0].split("[=;]");
        String tz = null;
        int n = split2.length;
        for (int i = 1; i < n; i += 2) {
            if (split2[i].equalsIgnoreCase("TZID")) {
                tz = split2[i + 1].replace(chop, "");
            }
        }
        split2 = split1[1].split(",");
        n = split2.length;
        StringBuilder sb = new StringBuilder(old);
        for (int i = 0; i < n; ++i) {
            String s = split2[i];
            if (tz != null) {
                // A timezone was specified
                // Android doesn't understand this, so we switch to UTC
                Time t = new Time(tz);
                t.parse(s);
                t.switchTimezone("UTC");
                s = t.format2445();
            }
            sb.append(",").append(s);
        }
        return sb.toString();
    }

    // Extract one event from an ical file and populate a CalendarEventModel for it
    static public void populateFromEntries(
        CalendarEventModel event, ListIterator<String> iter)
    {
        while (iter.hasNext()) {
            String line = iter.next();
            // RFC 5545 specifies that property names are case-insensitive: in practice
            // everyone uses upper case, but just to be on the safe side we use a
            // forced to upper case version of the line to test for property names.
            String uLine = line.toUpperCase();
            if (uLine.startsWith("END:VEVENT")) {
                break;
            }
            if (uLine.startsWith("BEGIN:VALARM")) {
                // Start with an invalid minutes value: if we don't see
                // a valid TRIGGER property, we'll throw the whole reminder away.
                int minutes = -1;
                // Android only understands notification reminders some minutes
                // before the start of the event, so we ignore anything else
                boolean validAlarm = true;
                int method = Reminders.METHOD_DEFAULT;
                // FIXME Android doesn't handle repeating alarms but we can
                //  convert them into multiple alarms which it can handle.
                while (iter.hasNext()) {
                    String line1 = iter.next();
                    if (line1.toUpperCase().startsWith("END:VALARM")) {
                        break;
                    }
                    String[] split1 = line1.split(":", 2);
                    if (split1.length > 1) {
                        String[] split2 = split1[0].split("[=;]");
                        int n = split2.length / 2;
                        switch (split2[0].toUpperCase()) {
                            case "TRIGGER":
                                for (int i = 0; i < n; ++i) {
                                    switch (split2[2 * i + 1].toUpperCase()) {
                                        case "RELATED":
                                            if (!split2[2 * i + 2]
                                                .equalsIgnoreCase("START"))
                                            {
                                                // Androis can't do reminder at end
                                                validAlarm = false;
                                            }
                                            break;
                                        case "VALUE":
                                            if (!split2[2 * i + 2]
                                                .equalsIgnoreCase("DURATION"))
                                            {
                                                // Android can't do fixed time reminder
                                                validAlarm = false;
                                            }
                                            break;
                                        default:
                                            // just ignore invalid parameter
                                    }
                                }
                                if (validAlarm) {
                                    String offset = split1[1];
                                    // VALARM offsets are negative for before DTSTART
                                    // and positive after.
                                    // Android offset are always positive and always before
                                    // So we throw away any VALARM with a positive DURATION
                                    // and throw away the - sign for a negative one.
                                    if (offset.startsWith("-")) {
                                        minutes = parseDuration(
                                            offset.replaceFirst("-", ""));
                                    }
                                }
                                break;
                            case "ACTION":
                                switch (split1[1].toUpperCase()) {
                                    case "AUDIO":
                                        method = Reminders.METHOD_ALARM;
                                        break;
                                    case "DISPLAY":
                                        method = Reminders.METHOD_ALERT;
                                        break;
                                    case "EMAIL":
                                        // Although Android can store METHOD_EMAIL,
                                        // it can't store the addressee(s) or the
                                        // subject or the text, so it isn't useful
                                        // to try to decode this reminder.
                                    default:
                                        // RFC 5545 says to ignore unknown ACTIONs
                                        validAlarm = false;
                                }
                                break;
                            // ignore REPEAT or DURATION because Android
                            // can't handle them.
                        }
                    }
                }
                if ((minutes >= 0) && validAlarm) {
                    event.mReminders.add(CalendarEventModel.ReminderEntry.valueOf(
                        minutes, method));
                    event.mHasAlarm = true;
                }
            } else {
                String[] split1 = line.split(":", 2);
                if (split1.length > 1) {
                    if (uLine.startsWith("DTSTART")) {
                        parseDateTime(split1, event, which_DTSTART);
                    } else if (uLine.startsWith("DTEND")) {
                        parseDateTime(split1, event, which_DTEND);
                    } else if (uLine.startsWith("RRULE")) {
                        event.mRrule = split1[1];
                    } else if (uLine.startsWith("RDATE")) {
                        event.mRdate = parseDateList(split1, event.mRdate);
                    } else if (uLine.startsWith("EXDATE")) {
                        event.mExdate = parseDateList(split1, event.mExdate);
                    } else if (uLine.startsWith("UID")) {
                        event.mUid = split1[1];
                    } else if (uLine.startsWith("SUMMARY")) {
                        event.mTitle = unEscaoe(split1[1]);
                    } else if (uLine.startsWith("DESCRIPTION")) {
                        event.mDescription = unEscaoe(split1[1]);
                    } else if (uLine.startsWith("LOCATION")) {
                        event.mLocation = unEscaoe(split1[1]);
                    } else if (uLine.startsWith("TRANSP")) {
                        event.mAvailability =
                            (split1[1].compareTo("TRANSPARENT") == 0)
                                ? Events.AVAILABILITY_FREE
                                : Events.AVAILABILITY_BUSY;
                    } else if (uLine.startsWith("CLASS")) {
                        switch (split1[1].toUpperCase()) {
                            case "CONFIDENTIAL":
                                event.mAccessLevel = Events.ACCESS_CONFIDENTIAL;
                                break;
                            case "PRIVATE":
                                event.mAccessLevel = Events.ACCESS_PRIVATE;
                                break;
                            case "PUBLIC":
                                event.mAccessLevel = Events.ACCESS_PUBLIC;
                                break;
                        }
                    } else if (uLine.startsWith("ORGANIZER")) {
                        event.mOrganizerDisplayName = null;
                        split1 =
                            line.split(":([mM][aA][iI][lL][tT][oO]:)?", 2);
                        event.mOrganizer = split1[1];
                        String[] split2 = split1[0].split("[=;]");
                        int n = split2.length;
                        for (int i = 1; i < n; i += 2) {
                            if (split2[i].equalsIgnoreCase("CN")) {
                                event.mOrganizerDisplayName = split2[i + 1];
                            }
                        }
                    } else if (uLine.startsWith("ATTENDEE")) {
                        split1 =
                            line.split(":([mM][aA][iI][lL][tT][oO]:)?", 2);
                        String email = split1[1];
                        CalendarEventModel.Attendee attendee =
                            new CalendarEventModel.Attendee(null, email);
                        attendee.mType = Attendees.TYPE_NONE;
                        String[] split2 = split1[0].split("[=;]");
                        int n = split2.length;
                        for (int i = 1; i < n; i += 2) {
                            switch (split2[i].toUpperCase()) {
                                case "CN":
                                    attendee.mName = split2[i + 1];
                                    break;
                                case "PARTSTAT":
                                    switch (split2[i + 1].toUpperCase()) {
                                        case "ACCEPTED":
                                            attendee.mStatus =
                                                Attendees.ATTENDEE_STATUS_ACCEPTED;
                                            break;
                                        case "DECLINED":
                                            attendee.mStatus =
                                                Attendees.ATTENDEE_STATUS_DECLINED;
                                            break;
                                        case "NEEDS-ACTION":
                                            attendee.mStatus =
                                                Attendees.ATTENDEE_STATUS_INVITED;
                                            break;
                                        case "TENTATIVE":
                                            attendee.mStatus =
                                                Attendees.ATTENDEE_STATUS_TENTATIVE;
                                            break;
                                        default:
                                            attendee.mStatus =
                                                Attendees.ATTENDEE_STATUS_NONE;
                                    }
                                    break;
                                case "CUTYPE":
                                    switch (split2[i + 1].toUpperCase()) {
                                        case "RESOURCE":
                                        case "ROOM":
                                            attendee.mType = Attendees.TYPE_RESOURCE;
                                            break;
                                    }
                                    break;
                                case "ROLE":
                                    if (attendee.mType != Attendees.TYPE_RESOURCE) {
                                        switch (split2[i + 1].toUpperCase()) {
                                            case "OPT-PARTICIPANT":
                                                attendee.mType = Attendees.TYPE_OPTIONAL;
                                                break;
                                            case "REQ-PARTICIPANT":
                                                attendee.mType = Attendees.TYPE_REQUIRED;
                                        }
                                    }
                            }
                        }
                        event.mAttendeesList.put(email, attendee);
                    }
                }
            }
        }
    }
}
