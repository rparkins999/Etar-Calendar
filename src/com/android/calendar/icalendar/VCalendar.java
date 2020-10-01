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

import com.android.calendar.CalendarEventModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.ListIterator;

/**
 * Models the Calendar/VCalendar component of the iCalendar format
 */
public class VCalendar {

    // Valid property identifiers of the component
    // TODO: only a partial list of attributes have been implemented, implement the rest
    public static String VERSION = "VERSION";
    public static String PRODID = "PRODID";
    public static String CALSCALE = "CALSCALE";
    public static String METHOD = "METHOD";

    public final static String PRODUCT_IDENTIFIER = "-//Etar//ws.xsoh.etar";

    // Stores the -arity of the attributes that this component can have
    private final static HashMap<String, Integer> sPropertyList = new HashMap<>();

    // Initialize approved list of iCal Calendar properties
    static {
        sPropertyList.put(VERSION, 1);
        sPropertyList.put(PRODID, 1);
        sPropertyList.put(CALSCALE, 1);
        sPropertyList.put(METHOD, 1);
    }

    // Stores attributes and their corresponding values belonging to the Calendar object
    public HashMap<String, String> mProperties;
    // Events that belong to this Calendar object
    public LinkedList<CalendarEventModel> mEvents;

    /**
     * Constructor
     */
    public VCalendar() {
        mProperties = new HashMap<>();
        mEvents = new LinkedList<>();
    }

    /**
     * Add specified property
     * @param property String name of the property to add
     * @param value String value for the property
     */
    public void addProperty(String property, String value) {
        // Since all the required mProperties are unary (only one can exist),
        // take a shortcut here
        if (sPropertyList.containsKey(property) && value != null) {
            mProperties.put(property, IcalendarUtils.cleanseString(value));
        }
    }

    /**
     * @param event the CalendarEventModel to add
     */
    public void addEvent(CalendarEventModel event) {
        if (event != null) mEvents.add(event);
    }

    /**
     * @return the list of events found in this VCALENDAR
     */
    public LinkedList<CalendarEventModel> getAllEvents() {
        return mEvents;
    }

    /**
     * @return the iCal representation of the calendar and all of its inherent components
     */
    public String getICalFormattedString() {
        StringBuilder output = new StringBuilder();

        // Add Calendar properties
        // TODO: add the ability to specify the order in which to compose the properties
        output.append("BEGIN:VCALENDAR\n");
        for (String property : mProperties.keySet() ) {
            output.append(property).append(":")
                  .append(mProperties.get(property)).append("\n");
        }

        // Enforce line length requirements
        output = IcalendarUtils.enforceICalLineLength(output);
        // Add event
        VEvent v = new VEvent();
        for (CalendarEventModel event : mEvents) {
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
                mEvents.add(event);
            } else if (line.toUpperCase().startsWith("END:VCALENDAR")) {
                break;
            }
        }
    }
}
