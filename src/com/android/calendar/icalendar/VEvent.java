/*
 * Copyright (C) 2014-2016 The CyanogenMod Project
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

package com.android.calendar.icalendar;

import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.format.Time;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calendar.CalendarEventModel;

import java.util.ArrayList;
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

    // We leave an (i;;egal) unescaped backslash as a backslash.
    private static String unEscape(String s) {
        return s.replace("\\n", "\n")
                .replace("\\,", ",")
                .replace("\\;", ";")
                .replace("\\\\", "\\");
    }

    // This splits a property into name, possible parameters, and value
    // allowing for Unicode code points and quoted strings.
    // The property name is forced to upper case to be on the safe side:
    // RFC5545 allows mixed case although nobody uses it.
    // Line unfolding must already have been done.
    // It allows some illegal things:
    //     quoted-strings concatenated with other quoted strings or characters
    //     invalid characters in property names
    //     no property value
    // RFC 5545 does not provide a way to include a " in a parameter value.
    private static String[] splitProperty(@NonNull String s) {
        String[] result = new String[] {"", null, ""};
        boolean inQuotedString = false;
        boolean seencolon = false;
        boolean seensemicolon = false;
        StringBuilder sb = new StringBuilder();
        int n = s.codePointCount(0, s.length());
        for (int i = 0; i < n; ++i) {
            int code = s.codePointAt(s.offsetByCodePoints(0, i));
            if (seencolon) {
                sb.appendCodePoint(code);
            } else if (code == '"') {
                inQuotedString = !inQuotedString;
                sb.appendCodePoint(code);
            } else if (inQuotedString) {
                sb.appendCodePoint(code);
            } else if (code == ':') {
                if (seensemicolon) {
                    result[1] = sb.toString();
                } else {
                    result[0] = sb.toString().toUpperCase();
                }
                sb.setLength(0);
                seencolon = true;
            } else if (seensemicolon || (code != ';')) {
                sb.appendCodePoint(code);
            } else {
                result[0] = sb.toString().toUpperCase();
                seensemicolon = true;
                sb.setLength(0);
            }
        }
        if (seencolon) { result[2] = sb.toString(); }
        return result;
    }

    // This splits a parameter string into a list of parameter names and
    // values allowing for Unicode code points and quoted strings.
    // Parameter names are forced to upper case to be on the safe side:
    // RFC5545 allows mixed case although nobody uses it.
    // The line must have been unfolded and the initial semicolon removed.
    // If the argument is null, this returns an empty ArrayList.
    // Otherwise pairs of elements in the returned ArrYList
    // are a parameter name and its value.
    // It allows some illegal things:
    //     quoted-strings concatenated with other quoted strings or characters
    //     invalid characters in parameter names
    //     last parameter not having a value
    // RFC 5545 does not provide a way to include a " in a parameter value.
    private static ArrayList<String> splitParameters(@Nullable String s) {
        ArrayList<String> result = new ArrayList<>();
        if (s != null ) {
            boolean inQuotedString = false;
            boolean inValue = false;
            StringBuilder sb = new StringBuilder();
            int n = s.codePointCount(0, s.length());
            for (int i = 0; i < n; ++i) {
                int code = s.codePointAt(s.offsetByCodePoints(0, i));
                if (inValue) {
                    if (code == '"') {
                        inQuotedString = !inQuotedString;
                    } else if ((code == ';') && !inQuotedString) {
                        result.add(sb.toString());
                        sb.setLength(0);
                        inValue = false;
                    } else {
                        sb.appendCodePoint(code);
                    }
                } else if (code == '=') {
                    result.add(sb.toString().toUpperCase());
                    sb.setLength(0);
                    inValue = true;
                } else {
                    sb.appendCodePoint(code);
                }
            }
            if (inValue || (sb.length() > 0)) {
                result.add(sb.toString());
            }
        }
        return result;
    }

    // This quote a parameter value if it contains ";" or ":" or ","
    public String quote(String s) {
        if ((s.contains(";")) || (s.contains(":")) || (s.contains(","))) {
            return ("\"" + s + "\"");
        } else {
            return s;
        }
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
        if ((event.mOrganizer != null) || (event.mOrganizerDisplayName != null)) {
            sb.append("ORGANIZER");
            if (event.mOrganizerDisplayName != null) {
                sb.append(";CN=")
                    .append(quote(event.mOrganizerDisplayName));
            }
            if (event.mOrganizer != null) {
                sb.append(":mailto:").append(event.mOrganizer);
            } else {
                sb.append(":UNKNOWN");
            }
            sb.append("\n");
        }
        // Android doesn't preserve email addresses for email reminders: we try to send
        // to any attendees of the event who haven't declined if they have email addresses
        // or the Organizer if there are no such attendees.
        ArrayList<String> emails = new ArrayList<>();
        // Add event Attendees
        for (CalendarEventModel.Attendee attendee : event.mAttendeesList.values()) {
            StringBuilder asb = new StringBuilder();
            boolean emailIt = true;
            asb.append("ATTENDEE;RSVP=TRUE");
            if (attendee.mName != null) {
                asb.append(";CN=").append(quote(attendee.mName));
            }
            switch(attendee.mStatus) {
                default:
                case Attendees.ATTENDEE_STATUS_NONE:
                    break;
                case Attendees.ATTENDEE_STATUS_ACCEPTED:
                    asb.append(";PARTSTAT=ACCEPTED");
                    break;
                case Attendees.ATTENDEE_STATUS_DECLINED:
                    asb.append(";PARTSTAT=DECLINED");
                    emailIt = false;
                    break;
                case Attendees.ATTENDEE_STATUS_INVITED:
                    asb.append(";PARTSTAT=NEEDS-ACTION");
                    break;
                case Attendees.ATTENDEE_STATUS_TENTATIVE:
                    asb.append(";PARTSTAT=TENTATIVE");
                    break;
            }
            switch(attendee.mType) {
                default:
                case Attendees.TYPE_NONE:
                    asb.append(";CUTYPE=INDIVIDUAL;ROLE=NON-PARTICIPANT");
                    break;
                case Attendees.TYPE_REQUIRED:
                    asb.append(";CUTYPE=INDIVIDUAL;ROLE=REQ-PARTICIPANT");
                    break;
                case Attendees.TYPE_OPTIONAL:
                    asb.append(";CUTYPE=INDIVIDUAL;ROLE=OPT-PARTICIPANT");
                    break;
                case Attendees.TYPE_RESOURCE:
                    asb.append(";CUTYPE=RESOURCE;ROLE=REQ-PARTICIPANT");
                    emailIt = false;
                    break;
            }
            if (attendee.mEmail == null) {
                asb.append(":UNKNOWN\n");
            } else {
                // I don't check that the email addresses are valid.
                // I've been bitten too many times by web sites which try to,
                // and reject valid email addresses. The only valid decision
                // procedure is to try and send the email, and that isn't
                // happening here.
                asb.append(":MAILTO:").append(attendee.mEmail).append("\n");
                if (emailIt) {
                    emails.add(asb.toString());
                }
            }
            sb.append(asb);
        }
        // Add event reminders
        if (emails.isEmpty() && (event.mOrganizer != null)) {
            // If we don't have any attendees, send email reminders to the organizer.
            emails.add("ATTENDEE:MAILTO:" + event.mOrganizer + "\n");
        }
        int n = event.mReminders.size();
        for (int i = 0; i < n; ++i) {
            CalendarEventModel.ReminderEntry entry = event.mReminders.get(i);
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
                    if (event.mTitle != null) { sb.append(escape(event.mTitle)); }
                    else if (event.mLocation != null) {
                        sb.append(escape(event.mLocation));
                    }
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
                    if (event.mTitle != null) {
                        sb.append("SUMMARY:").append(event.mTitle).append("\n");
                    } else {
                        sb.append("SUMMARY:Event Reminder\n");
                    }
                    if (event.mDescription != null) {
                        sb.append("DESCRIPTION:").append(event.mDescription).append("\n");
                    } else {
                        sb.append("DESCRIPTION:Event Reminder\n");
                    }
                    for (String mail : emails) {
                        sb.append(mail);
                    }
                    break;
                case Reminders.METHOD_ALARM:
                    sb.append("ACTION:AUDIO\n");
                    // We don't have a way of passing a sound file so any device
                    // which reads this ical will just play its default reminder sound.
                    break;
            }
            int repeats = 0;
            int offsetSoFar = -1;
            while (i < n - 1) {
                // check next entry for repeat
                CalendarEventModel.ReminderEntry next =  event.mReminders.get(i + 1);
                int offset = entry.offsetTo(next);
                if ((offset > 0 && ((offsetSoFar == -1) || offsetSoFar == offset))) {
                    offsetSoFar = offset;
                    ++i;
                    ++repeats;
                    entry = next;
                } else {
                    break;
                }
            }
            if (repeats > 0) {
                sb.append("REPEAT:").append(repeats).append("\n");
                sb.append("DURATION:PT").append(offsetSoFar).append("M\n");
            }
            sb.append("END:VALARM\n");
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
    //FIXME What we really ought to do here is decode the timezone
    // declarations in the VCALENDAR.
    private static final String chop = "/freeassociation.sourceforge.net/";

    private static void parseDateTime(String[] splitLine, CalendarEventModel event)
    {
        String tz = Time.getCurrentTimezone();
        ArrayList<String> params = splitParameters(splitLine[1]);
        int n = params.size();
        for (int i = 0; i < n; i += 2) {
            if (params.get(i).compareTo("TZID") == 0) {
                tz = params.get(i + 1).replace(chop, "");
            }
        }
        Time t = new Time(tz);
        if (t.parse(splitLine[2])) { tz = "UTC"; }
        switch (splitLine[0]) {
            case "DTSTART":
                event.mOriginalStart = event.mStart = t.normalize(false);
                event.mTimezoneStart = tz;
                event.mAllDay = t.allDay;
                break;
            case "DTEND":
                event.mOriginalEnd = event.mEnd = t.normalize(false);
                event.mTimezoneEnd = tz;
                break;
        }
    }

    // This parses an RDATE or EXDATE property.
    // RFC5545 allows multiple EXDATE or RDATE properties,
    // and each one can have a comma-separated list of dates.
    // Android can only have a single comma-separated list,
    // So we pass the old list and append any valid dates we find to it.
    static private String parseDateList(String[] splitLine, String old)
    {
        String tz = null;
        ArrayList<String> params = splitParameters(splitLine[1]);
        int n = params.size();
        for (int i = 0; i < n; i += 2) {
            if (params.get(i).compareTo("TZID") == 0) {
                tz = params.get(i + 1).replace(chop, "");
            }
        }
        String[] dates = splitLine[2].split(",");
        n = dates.length;
        StringBuilder sb = (old == null ) ? new StringBuilder() : new StringBuilder(old);
        for (int i = 0; i < n; ++i) {
            String s = dates[i];
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
        ArrayList<String> params;
        int n;
        while (iter.hasNext()) {
            String line = iter.next();
            if ((line == null) || line.isEmpty()) { continue; }
            String[] splitLine = splitProperty(line);
            switch (splitLine[0]) {
                case "END":
                    if (splitLine[2].toUpperCase().compareTo("VEVENT") == 0) {
                        return; // finished this VEVENT
                    } else {
                        continue;
                    }
                    // DTSTAMP is ignored because Android doesn't handle it
                case "UID":
                    event.mUid = splitLine[2];
                    continue;
                case "DTSTART":
                    parseDateTime(splitLine, event);
                    continue;
                case "CLASS":
                    switch (splitLine[2].toUpperCase()) {
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
                    continue;
                    // CREATED is ignored because Android doesn't handle it
                case "DESCRIPTION":
                    event.mDescription = unEscape(splitLine[2]);
                    continue;
                    // GEO is ignored because Android doesn't handle it
                    // LAST-MODIFIED is ignored because Android doesn't handle it
                case "LOCATION":
                    event.mLocation = unEscape(splitLine[2]);
                    continue;
                case "ORGANIZER":
                    event.mOrganizerDisplayName = null;
                    event.mOrganizer = splitLine[2].replaceFirst(
                        "[mM][aA][iI][lL][tT][oO]:", "");
                    params = splitParameters(splitLine[1]);
                    n = params.size();
                    for (int i = 0; i < n; i += 2) {
                        if (params.get(i).compareTo("CN") == 0) {
                            event.mOrganizerDisplayName = params.get(i + 1);
                        }
                    }
                    continue;
                    // PRIORITY is ignored because Android doesn't handle it
                    // SEQUENCE is ignored because Android doesn't handle it
                case "STATUS":
                    switch (splitLine[2].toUpperCase()) {
                        case "TENTATIVE":
                            event.mEventStatus = Events.STATUS_TENTATIVE;
                            break;
                        case "CANCELED":
                            event.mEventStatus = Events.STATUS_CANCELED;
                            break;
                        case "CONFIRMED":
                            event.mEventStatus = Events.STATUS_CONFIRMED;
                    }
                    continue;
                case "SUMMARY":
                    event.mTitle = unEscape(splitLine[2]);
                    continue;
                case "TRANSP":
                    event.mAvailability =
                        (splitLine[2].toUpperCase().compareTo("TRANSPARENT") == 0)
                            ? Events.AVAILABILITY_FREE
                            : Events.AVAILABILITY_BUSY;
                    continue;
                    // URL is ignored because Android doesn't handle it
                    // RECURRENCE-ID is ignored because Android doesn't handle it
                case "RRULE":
                    event.mRrule = splitLine[2];
                    continue;
                case "DTEND":
                    parseDateTime(splitLine, event);
                    continue;
                case "DURATION":
                    event.mDuration = splitLine[2];
                    continue;
                    // ATTACH is ignored because Android doesn't handle it
                case "ATTENDEE":
                    String email = splitLine[2].replaceFirst(
                        "[mM][aA][iI][lL][tT][oO]:", "");
                    CalendarEventModel.Attendee attendee =
                        new CalendarEventModel.Attendee(null, email);
                    attendee.mType = Attendees.TYPE_NONE;
                    params = splitParameters(splitLine[1]);
                    n = params.size();
                    for (int i = 0; i < n; i += 2) {
                        switch (params.get(i)) {
                            case "CN":
                                attendee.mName = params.get(i + 1);
                                break;
                            case "PARTSTAT":
                                switch (params.get(i + 1).toUpperCase()) {
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
                                switch (params.get(i + 1).toUpperCase()) {
                                    case "RESOURCE":
                                    case "ROOM":
                                        attendee.mType = Attendees.TYPE_RESOURCE;
                                        break;
                                }
                                break;
                            case "ROLE":
                                if (attendee.mType != Attendees.TYPE_RESOURCE) {
                                    switch (params.get(i + 1).toUpperCase()) {
                                        case "OPT-PARTICIPANT":
                                            attendee.mType = Attendees.TYPE_OPTIONAL;
                                            break;
                                        default:
                                        case "CHAIR": // Don't assume CHAIR is organizer
                                        case "REQ-PARTICIPANT":
                                            attendee.mType = Attendees.TYPE_REQUIRED;
                                            break;
                                        case "NON-PARTICIPANT":
                                            attendee.mType = Attendees.TYPE_NONE;
                                    }
                                }
                        }
                    }
                    event.mAttendeesList.put(email, attendee);
                    continue;
                    // CATEGORIES is ignored because Android doesn't handle it
                    // COMMENT is ignored because Android doesn't handle it
                    // CONTACT is ignored because Android doesn't handle it
                case "EXDATE":
                    event.mExdate = parseDateList(splitLine, event.mExdate);
                    continue;
                case "RDATE":
                    event.mRdate = parseDateList(splitLine, event.mRdate);
                    continue;
                    // REQUEST-STATUS is ignored because Android doesn't handle it
                    // RELATED-TO is ignored because Android doesn't handle it
                    // RESOURCES is ignored because Android doesn't handle it
                case "BEGIN":
                    if (splitLine[2].compareTo("VALARM") == 0) {
                        // Start with an invalid minutes value: if we don't see
                        // a valid TRIGGER property, we'll throw the whole reminder away.
                        int minutes = -1;
                        // Android only understands notification reminders some minutes
                        // before the start of the event, so we ignore anything else
                        boolean validAlarm = true;
                        int method = Reminders.METHOD_DEFAULT;
                        int duration = -1;
                        int repeat = 0;
                        while (iter.hasNext()) {
                            line = iter.next();
                            if ((line == null) || line.isEmpty()) {
                                continue;
                            }
                            splitLine = splitProperty(line);
                            switch (splitLine[0]) {
                                case "END":
                                    if (splitLine[2].toUpperCase().compareTo("VALARM") == 0)
                                    {
                                        break;
                                    }
                                    continue;
                                case "ACTION":
                                    switch (splitLine[2]) {
                                        case "AUDIO":
                                            method = Reminders.METHOD_ALARM;
                                            break;
                                        case "DISPLAY":
                                            method = Reminders.METHOD_ALERT;
                                            break;
                                        case "EMAIL":
                                            // This isn't really much use because Android
                                            // doesn't have anywhere to put the email
                                            // address or the subject or body.
                                            method = Reminders.METHOD_EMAIL;
                                            break;
                                        default:
                                            // RFC 5545 says to ignore unknown ACTIONs
                                            validAlarm = false;
                                    }
                                    continue;
                                    // AUDIO alarms can have an ATTACH property
                                    // but Android doesn't handle it so we ignore it.
                                    // Android will play a default sound.
                                    // DISPLAY alarms must have a DESCRIPTION property
                                    // but Android doesn't handle it so we ignore it.
                                    // Android will make a notification
                                    // from the event title.
                                case "TRIGGER":
                                    params = splitParameters(splitLine[1]);
                                    n = params.size();
                                    for (int i = 0; i < n; i += 2) {
                                        switch (params.get(i)) {
                                            case "RELATED":
                                                // Android can't do alarm for end of event
                                                //FIXME offer to create a dummy
                                                // event to hold the alarm
                                                validAlarm = params.get(i + 1)
                                                    .toUpperCase()
                                                    .compareTo("START") == 0;
                                                break;
                                            case "VALUE":
                                                // Android can't do fixed time reminder
                                                //FIXME offer to create a dummy
                                                // event to hold the alarm
                                                validAlarm = params.get(i + 1)
                                                    .toUpperCase()
                                                    .compareTo("DURATION") == 0;
                                                break;
                                            default:
                                                // just ignore invalid parameter
                                        }
                                    }
                                    if (validAlarm) {
                                        // VALARM offsets are negative for before DTSTART
                                        // and positive after.
                                        // Android offset are always positive
                                        // and always before.
                                        // So we throw away any VALARM
                                        // with a positive DURATION.
                                        // and throw away the - sign for a negative one.
                                        //FIXME if the alarm is after the start of the event
                                        // offer to create a dummy event to hold the alarm
                                        if (splitLine[2].startsWith("-")) {
                                            minutes = parseDuration(
                                                splitLine[2].replaceFirst(
                                                    "-", ""));
                                        }
                                    }
                                    continue;
                                case "DURATION":
                                    duration = parseDuration(splitLine[2]);
                                    continue;
                                case "REPEAT":
                                    try {
                                        repeat = Integer.parseInt(splitLine[2]);
                                    } catch (NumberFormatException ignore) {
                                    }
                                default: //ignore anything we don't understand
                                    continue;
                            }
                            // If we fall out of the switch, we found END:VALARM
                            // so break out of the while loop
                            break;
                        }
                        if ((minutes >= 0) && validAlarm) {
                            event.mReminders.add(
                                CalendarEventModel.ReminderEntry.valueOf(minutes, method));
                            event.mHasAlarm = true;
                            // Android can't do repeating reminders.
                            // So we translate a repeating alarm into multiple reminders.
                            if (duration > 0) {
                                while (repeat > 0) {
                                    --repeat;
                                    minutes -= duration;
                                    if (minutes < 0) {
                                        // Oops, this repetition is now after the star
                                        // t of the event: Android can't do that.
                                        // We could offer to make a dummy event to hold
                                        // the alarm, but currently we don't do that.
                                        break;
                                    }
                                    event.mReminders.add(
                                        CalendarEventModel.ReminderEntry.valueOf(
                                            minutes, method));
                                }
                            }
                        }
                    }
                default: // ignore anything we don't recognise
            }
        }
    }
}
