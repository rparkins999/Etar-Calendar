/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.settings.ViewDetailsPreferences;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class CalendarApplication extends Application {

    // Thanks to stackoverflow.com for this trick to enable access to application context
    // and resources without having to pass a context around all the time.
    private static Context mContext;
    public static Context getContext() {
        return mContext;
    }

    /**
     * This is a bit sad: we can't pass all the data needed to create a list of
     * CalendarEventModel's between Activities in an Intent. We could serialize each
     * CalendarEventModel out to a file or a bundle and back in again, but this is horribly
     * inefficient. Instead we keep this static list around. It is empty when we
     * aren't using it.
     * We need synchronisation here because an event can be removed from this list
     * in a thread other than the UI thread.
     */
    public static final List<CalendarEventModel> mEvents
        = Collections.synchronizedList(new LinkedList<CalendarEventModel>());

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;

        /*
         * Ensure the default values are set for any receiver, activity,
         * service, etc. of Calendar
         * please increment SHARED_PREFS_VERSION each time the new default value appears
         * in a layout xml file in order to make sure it will be initialized
         */
        final int SHARED_PREFS_VERSION = 1;
        final String VERSION_KEY = "spv";
        SharedPreferences preferences = GeneralPreferences.Companion.getSharedPreferences(this);
        if (preferences.getInt(VERSION_KEY, 0) != SHARED_PREFS_VERSION) {
            GeneralPreferences.Companion.setDefaultValues(this);
            ViewDetailsPreferences.Companion.setDefaultValues(this);
            preferences.edit().putInt(VERSION_KEY, SHARED_PREFS_VERSION).apply();
        }

        // Save the version number, for upcoming 'What's new' screen.  This will be later be
        // moved to that implementation.
        Utils.setSharedPreference(this, GeneralPreferences.KEY_VERSION,
                Utils.getVersionCode(this));

        // Initialize the registry mapping some custom behavior.
        ExtensionsFactory.init(getAssets());
    }
}
