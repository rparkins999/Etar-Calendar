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

import com.android.calendar.CalendarApplication;
import com.android.calendar.CalendarEventModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Models the Calendar/VCalendar component of the iCalendar format
 */
public class VCalendar {

    public final static String PRODID = "//github.com/rparkins999/Etar-Calendar";

    /**
     * Constructor
     */
    public VCalendar() {
        CalendarApplication.mEvents.clear();
    }

    /**
     * @return the iCal representation of the calendar and all of its inherent components
     */
    public String getICalFormattedString() {
        StringBuilder output = new StringBuilder();

        output.append("BEGIN:VCALENDAR\n")
              .append("PRODID:").append(PRODID).append("\n")
              .append("VERSION:2.0\n")
              .append("METHOD:PUBLISH\n");

        // Enforce line length requirements
        output = IcalendarUtils.enforceICalLineLength(output);
        // Add event
        VEvent v = new VEvent();
        for (CalendarEventModel event : CalendarApplication.mEvents) {
            output.append(v.getICalFormattedString(event));
        }

        output.append("END:VCALENDAR\n");

        return output.toString();
    }

    public void populateFromString(ArrayList<String> input) {
        ListIterator<String> iter = input.listIterator();
        while (iter.hasNext()) {
            String line = iter.next();
            if (line.toUpperCase().startsWith("BEGIN:VEVENT")) {
                // Offload to vevent for parsing
                CalendarEventModel event = new CalendarEventModel();
                VEvent.populateFromEntries(event, iter);
                CalendarApplication.mEvents.add(event);
            } else if (line.toUpperCase().startsWith("END:VCALENDAR")) {
                break;
            }
        }
    }
}
