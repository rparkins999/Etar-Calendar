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

package com.android.calendar;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.text.format.Time;
import android.util.Log;
import android.util.Pair;

import com.android.calendar.event.EditEventActivity;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.settings.SettingsActivity;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.WeakHashMap;

import static android.provider.CalendarContract.Attendees.ATTENDEE_STATUS;
import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;

public class CalendarController {
    public static final String EVENT_EDIT_ON_LAUNCH = "editMode";
    public static final int MIN_CALENDAR_WEEK = 0;
    public static final int MAX_CALENDAR_WEEK = 3497; // weeks between 1/1/1970 and 1/1/2037
    /**
     * Pass to the ExtraLong parameter for ControllerAction.CREATE_EVENT to create
     * an all-day event
     */
    public static final long EXTRA_CREATE_ALL_DAY = 0x10;
    /**
     * Pass to the ExtraLong parameter for ControllerAction.GO_TO to signal the time
     * can be ignored
     */
    public static final long EXTRA_GOTO_DATE = 1;
    public static final long EXTRA_GOTO_TIME = 2;
    public static final long EXTRA_GOTO_BACK_TO_PREVIOUS = 4;
    public static final long EXTRA_GOTO_TODAY = 8;
    private static final boolean DEBUG = false;
    private static final String TAG = "CalendarController";
    private static final WeakHashMap<Context, WeakReference<CalendarController>> instances
        = new WeakHashMap<>();
    private final Context mContext;
    // This uses a LinkedHashMap so that we can replace fragments based on the
    // view id they are being expanded into since we can't guarantee a reference
    // to the handler will be findable
    private final LinkedHashMap<Integer, ActionHandler> actionHandlers
        = new LinkedHashMap<>(5);
    private final LinkedList<Integer> mToBeRemovedActionHandlers = new LinkedList<>();
    private final LinkedHashMap<Integer, ActionHandler> mToBeAddedEventHandlers =
        new LinkedHashMap<>();
    private final WeakHashMap<Object, Long> filters = new WeakHashMap<>(1);
    private final Time mTime = new Time();
    private final Runnable mUpdateTimezone = new Runnable() {
        @Override
        public void run() {
            mTime.switchTimezone(Utils.getTimeZone(mContext, this));
        }
    };
    private Pair<Integer, ActionHandler> mFirstActionHandler;
    private Pair<Integer, ActionHandler> mToBeAddedFirstActionHandler;
    private volatile int mDispatchInProgressCounter = 0;
    private int mViewType = -1;
    private int mDetailViewType;
    private int mPreviousViewType = -1;
    private long mEventId = -1;
    private long mDateFlags = 0;

    private CalendarController(Context context) {
        mContext = context;
        mUpdateTimezone.run();
        mTime.setToNow();
        mDetailViewType = Utils.getSharedPreference(mContext,
                GeneralPreferences.KEY_DETAILED_VIEW,
                GeneralPreferences.DEFAULT_DETAILED_VIEW);
    }

    /**
     * Creates and/or returns an instance of CalendarController associated with
     * the supplied context. It is best to pass in the current Activity.
     *
     * @param context The activity if at all possible.
     */
    public static CalendarController getInstance(Context context) {
        CalendarController controller = null;
        synchronized (instances) {
            WeakReference<CalendarController> weakController = instances.get(context);
            if (weakController != null) {
                controller = weakController.get();
            }

            if (controller == null) {
                controller = new CalendarController(context);
                instances.put(context, new WeakReference<>(controller));
            }
        }
        return controller;
    }

    /**
     * Removes an instance when it is no longer needed. This should be called in
     * an activity's onDestroy method.
     *
     * @param context The activity used to create the controller
     */
    public static void removeInstance(Context context)
    {
        instances.remove(context);
    }

    public void sendEventAction(
        Object sender, long actionType, long eventId, long startMillis,
        long endMillis, int x, int y, long selectedMillis)
    {
        // TODO: pass the real allDay status or at least a status that says
        // we don't know the status and have the receiver query the data.
        // The current use of this method for VIEW_EVENT is by the day view
        // to show an ActionInfo so currently the missing allDay status has no effect.
        sendEventActionWithExtra(sender, actionType, eventId, startMillis, endMillis, x, y,
                ActionInfo.buildViewExtraLong(Attendees.ATTENDEE_STATUS_NONE, false),
                selectedMillis);
    }

    /**
     * Helper for sending New/View/Edit/Delete events
     *
     * @param sender object of the caller
     * @param actionType one of {@link ControllerAction}
     * @param eventId event id
     * @param startMillis start time
     * @param endMillis end time
     * @param x x coordinate in the activity space
     * @param y y coordinate in the activity space
     * @param extraLong default response value for the "simple event view" and all day indication.
     *        Use Attendees.ATTENDEE_STATUS_NONE for no response.
     * @param selectedMillis The time to specify as selected
     */
    public void sendEventActionWithExtra(
        Object sender, long actionType, long eventId, long startMillis, long endMillis,
        int x, int y, long extraLong, long selectedMillis)
    {
        sendEventActionWithExtraWithTitleWithCalendarId(
            sender, actionType, eventId, startMillis, endMillis, x, y, extraLong,
            selectedMillis, null, -1);
    }

    /**
     * Helper for sending New/View/Edit/Delete events
     *
     * @param sender object of the caller
     * @param actionType one of {@link ControllerAction}
     * @param eventId event id
     * @param startMillis start time
     * @param endMillis end time
     * @param x x coordinate in the activity space
     * @param y y coordinate in the activity space
     * @param extraLong default response value for the "simple event view" and all day indication.
     *        Use Attendees.ATTENDEE_STATUS_NONE for no response.
     * @param selectedMillis The time to specify as selected
     * @param title The title of the event
     * @param calendarId The id of the calendar which the event belongs to
     */
    public void sendEventActionWithExtraWithTitleWithCalendarId(
        Object sender, long actionType, long eventId, long startMillis, long endMillis,
        int x, int y, long extraLong, long selectedMillis, String title, long calendarId)
    {
        ActionInfo actionInfo = new ActionInfo();
        actionInfo.actionType = actionType;
        if (actionType == ControllerAction.EDIT_EVENT) {
            actionInfo.viewType = ViewType.CURRENT;
        }

        actionInfo.eventId = eventId;
        actionInfo.startTime = new Time(Utils.getTimeZone(mContext, mUpdateTimezone));
        actionInfo.startTime.set(startMillis);
        if (selectedMillis != -1) {
            actionInfo.selectedTime = new Time(Utils.getTimeZone(mContext, mUpdateTimezone));
            actionInfo.selectedTime.set(selectedMillis);
        } else {
            actionInfo.selectedTime = actionInfo.startTime;
        }
        actionInfo.endTime = new Time(Utils.getTimeZone(mContext, mUpdateTimezone));
        actionInfo.endTime.set(endMillis);
        actionInfo.x = x;
        actionInfo.y = y;
        actionInfo.extraLong = extraLong;
        actionInfo.eventTitle = title;
        actionInfo.calendarId = calendarId;
        this.sendAction(sender, actionInfo);
    }

    /**
     * Helper for sending non-calendar-event actions
     *
     * @param sender    object of the caller
     * @param actionType one of {@link ControllerAction}
     * @param start     start time
     * @param end       end time
     * @param eventId   event id
     * @param viewType  {@link ViewType}
     */
    public void sendAction(
        Object sender, long actionType, Time start, Time end, long eventId, int viewType) {
        sendAction(sender, actionType, start, end, start, eventId, viewType,
            EXTRA_GOTO_TIME, null, null);
    }

    /**
     * sendAction() variant with extraLong, search query, and search component name.
     */
    public void sendAction(
        Object sender, long actionType, Time start, Time end, long eventId, int viewType,
        long extraLong, String query, ComponentName componentName)
    {
        sendAction(sender, actionType, start, end, start, eventId, viewType, extraLong,
            query, componentName);
    }

    public void sendAction(
        Object sender, long actionType, Time start, Time end, Time selected, long eventId,
        int viewType, long extraLong, String query, ComponentName componentName)
    {
        ActionInfo info = new ActionInfo();
        info.actionType = actionType;
        info.startTime = start;
        info.selectedTime = selected;
        info.endTime = end;
        info.eventId = eventId;
        info.viewType = viewType;
        info.query = query;
        info.componentName = componentName;
        info.extraLong = extraLong;
        this.sendAction(sender, info);
    }

    public void sendAction(Object sender, final ActionInfo actionInfo) {
        // TODO Throw exception on invalid actions

        if (DEBUG) {
            Log.d(TAG, actionInfoToString(actionInfo));
        }

        Long filteredTypes = filters.get(sender);
        if (   (filteredTypes != null)
            && ((filteredTypes & actionInfo.actionType) != 0))
        {
            // Suppress event per filter
            if (DEBUG) {
                Log.d(TAG, "Event suppressed");
            }
            return;
        }

        mPreviousViewType = mViewType;

        // Fix up view if not specified
        if (actionInfo.viewType == ViewType.DETAIL) {
            actionInfo.viewType = mDetailViewType;
            mViewType = mDetailViewType;
        } else if (actionInfo.viewType == ViewType.CURRENT) {
            actionInfo.viewType = mViewType;
        } else if (actionInfo.viewType != ViewType.EDIT) {
            mViewType = actionInfo.viewType;

            if (   (actionInfo.viewType == ViewType.AGENDA)
                || (actionInfo.viewType == ViewType.DAY)
                || (   Utils.getAllowWeekForDetailView()
                    && (actionInfo.viewType == ViewType.WEEK)))
            {
                mDetailViewType = mViewType;
            }
        }

        if (DEBUG) {
            Log.d(TAG, "vvvvvvvvvvvvvvv");
            Log.d(TAG, "Start  " +
                ((actionInfo.startTime == null) ? "null" : actionInfo.startTime.toString()));
            Log.d(TAG, "End    " +
                ((actionInfo.endTime == null) ? "null" : actionInfo.endTime.toString()));
            Log.d(TAG, "Select " +
                ((actionInfo.selectedTime == null)
                    ? "null" : actionInfo.selectedTime.toString()));
            Log.d(TAG, "mTime  " + mTime.toString());
        }

        long startMillis = 0;
        if (actionInfo.startTime != null) {
            startMillis = actionInfo.startTime.toMillis(false);
        }

        // Set mTime if selectedTime is set
        if (   (actionInfo.selectedTime != null)
            && (actionInfo.selectedTime.toMillis(false) != 0))
        {
            mTime.set(actionInfo.selectedTime);
        } else {
            if (startMillis != 0) {
                // selectedTime is not set so set mTime to startTime iff it is not
                // within start and end times
                long mtimeMillis = mTime.toMillis(false);
                if (   (mtimeMillis < startMillis)
                    || (   (actionInfo.endTime != null)
                        && (mtimeMillis > actionInfo.endTime.toMillis(false))))
                {
                    mTime.set(actionInfo.startTime);
                }
            }
            actionInfo.selectedTime = mTime;
        }
        // Store the formatting flags if this is an update to the title
        if (actionInfo.actionType == ControllerAction.UPDATE_TITLE) {
            mDateFlags = actionInfo.extraLong;
        }

        // Fix up start time if not specified
        if (startMillis == 0) {
            actionInfo.startTime = mTime;
        }
        if (DEBUG) {
            Log.d(TAG, "Start  " +
                ((actionInfo.startTime == null) ? "null" : actionInfo.startTime.toString()));
            Log.d(TAG, "End    " +
                ((actionInfo.endTime == null) ? "null" : actionInfo.endTime.toString()));
            Log.d(TAG, "Select " +
                ((actionInfo.selectedTime == null)
                    ? "null" : actionInfo.selectedTime.toString()));
            Log.d(TAG, "mTime  " + mTime.toString());
            Log.d(TAG, "^^^^^^^^^^^^^^^");
        }

        // Store the eventId if we're entering edit event
        if ((actionInfo.actionType
            & (ControllerAction.CREATE_EVENT | ControllerAction.EDIT_EVENT)) != 0)
        {
            if (actionInfo.eventId > 0) {
                mEventId = actionInfo.eventId;
            } else {
                mEventId = -1;
            }
        }

        boolean handled = false;
        synchronized (this) {
            mDispatchInProgressCounter++;

            if (DEBUG) {
                Log.d(TAG, "sendAction: Dispatching to " +
                    actionHandlers.size() + " handlers");
            }
            // Dispatch to event handler(s)
            if (mFirstActionHandler != null) {
                // Handle the 'first' one before handling the others
                ActionHandler handler = mFirstActionHandler.second;
                if (   (handler != null)
                    && ((handler.getSupportedActionTypes() & actionInfo.actionType) != 0)
                    && !mToBeRemovedActionHandlers.contains(mFirstActionHandler.first))
                {
                    handler.handleAction(actionInfo);
                    handled = true;
                }
            }
            for (Entry<Integer, ActionHandler> entry : actionHandlers.entrySet()) {
                int key = entry.getKey();
                if ((mFirstActionHandler != null) && (key == mFirstActionHandler.first)) {
                    // If this was the 'first' handler it was already handled
                    continue;
                }
                ActionHandler actionHandler = entry.getValue();
                if ((actionHandler != null)
                    && ((actionHandler.getSupportedActionTypes()
                    & actionInfo.actionType) != 0)) {
                    if (mToBeRemovedActionHandlers.contains(key)) {
                        continue;
                    }
                    actionHandler.handleAction(actionInfo);
                    handled = true;
                }
            }

            mDispatchInProgressCounter--;

            if (mDispatchInProgressCounter == 0) {
                // Deregister removed handlers
                if (mToBeRemovedActionHandlers.size() > 0) {
                    for (Integer zombie : mToBeRemovedActionHandlers) {
                        actionHandlers.remove(zombie);
                        if (   (mFirstActionHandler != null)
                            && zombie.equals(mFirstActionHandler.first))
                        {
                            mFirstActionHandler = null;
                        }
                    }
                    mToBeRemovedActionHandlers.clear();
                }
                // Add new handlers
                if (mToBeAddedFirstActionHandler != null) {
                    mFirstActionHandler = mToBeAddedFirstActionHandler;
                    mToBeAddedFirstActionHandler = null;
                }
                if (mToBeAddedEventHandlers.size() > 0) {
                    for (Entry<Integer, ActionHandler> food :
                        mToBeAddedEventHandlers.entrySet())
                    {
                        actionHandlers.put(food.getKey(), food.getValue());
                    }
                }
            }
        }

        if (!handled) {
            // Launch Settings
            if (actionInfo.actionType == ControllerAction.LAUNCH_SETTINGS) {
                launchSettings();
                return;
            }

            // Create/View/Edit/Delete Event
            long endTime = (actionInfo.endTime == null)
                            ? -1 : actionInfo.endTime.toMillis(false);
            if (actionInfo.actionType == ControllerAction.CREATE_EVENT) {
                launchCreateEvent(actionInfo.startTime.toMillis(false), endTime,
                    actionInfo.extraLong == EXTRA_CREATE_ALL_DAY,
                    actionInfo.eventTitle, actionInfo.calendarId);
            } else if (actionInfo.actionType == ControllerAction.EDIT_EVENT) {
                launchEditEvent(actionInfo.eventId,
                    actionInfo.startTime.toMillis(false), endTime);
            } else if (actionInfo.actionType == ControllerAction.DELETE_EVENT) {
                launchDeleteEvent(actionInfo.eventId,
                    actionInfo.startTime.toMillis(false), endTime);
            } else if (actionInfo.actionType == ControllerAction.SEARCH) {
                launchSearch(actionInfo.query, actionInfo.componentName);
            }
        }
    }

    /**
     * Adds or updates an action handler. This uses a LinkedHashMap so that we can
     * replace fragments based on the view id they are being expanded into.
     *
     * @param key The view id or placeholder for this handler
     * @param actionHandler Typically a fragment or activity in the calendar app
     */
    public void registerActionHandler(int key, ActionHandler actionHandler) {
        synchronized (this) {
            if (mDispatchInProgressCounter > 0) {
                mToBeAddedEventHandlers.put(key, actionHandler);
            } else {
                actionHandlers.put(key, actionHandler);
            }
        }
    }

    public void registerFirstActionHandler(int key, ActionHandler actionHandler) {
        synchronized (this) {
            registerActionHandler(key, actionHandler);
            if (mDispatchInProgressCounter > 0) {
                mToBeAddedFirstActionHandler = new Pair<>(key, actionHandler);
            } else {
                mFirstActionHandler = new Pair<>(key, actionHandler);
            }
        }
    }

    public void deregisterActionHandler(int key) {
        synchronized (this) {
            if (mDispatchInProgressCounter > 0) {
                // To avoid ConcurrencyException, stash away the event handler for now.
                mToBeRemovedActionHandlers.add(key);
            } else {
                actionHandlers.remove(key);
                if (mFirstActionHandler != null && mFirstActionHandler.first == key) {
                    mFirstActionHandler = null;
                }
            }
        }
    }

    public void deregisterAllActionHandlers() {
        synchronized (this) {
            if (mDispatchInProgressCounter > 0) {
                // To avoid ConcurrencyException, stash away the event handler for now.
                mToBeRemovedActionHandlers.addAll(actionHandlers.keySet());
            } else {
                actionHandlers.clear();
                mFirstActionHandler = null;
            }
        }
    }

    // FRAG_TODO doesn't work yet
    @SuppressWarnings("unused")
    public void filterBroadcasts(Object sender, long eventTypes) {
        filters.put(sender, eventTypes);
    }

    /**
     * @return the time that this controller is currently pointed at
     */
    public long getTime() {
        return mTime.toMillis(false);
    }

    /**
     * Set the time this controller is currently pointed at
     *
     * @param millisTime Time since epoch in millis
     */
    public void setTime(long millisTime) {
        mTime.set(millisTime);
    }

    /**
     * @return the last set of date flags sent with
     * {@link ControllerAction#UPDATE_TITLE}
     */
    public long getDateFlags() {
        return mDateFlags;
    }

    /**
     * @return the last event ID the edit view was launched with
     */
    public long getEventId() {
        return mEventId;
    }

    // Sets the eventId. Should only be used for initialization.
    public void setEventId(long eventId) {
        mEventId = eventId;
    }

    public int getViewType() {
        return mViewType;
    }

    // Forces the viewType. Should only be used for initialization.
    public void setViewType(int viewType) {
        mViewType = viewType;
    }

    public int getPreviousViewType() {
        return mPreviousViewType;
    }

    private void launchSettings() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(mContext, SettingsActivity.class);
        intent.setFlags(
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mContext.startActivity(intent);
    }

    private void launchCreateEvent(long startMillis, long endMillis, boolean allDayEvent,
                                   String title, long calendarId)
    {
        Intent intent = generateCreateEventIntent(
            startMillis, endMillis, allDayEvent, title, calendarId);
        mEventId = -1;
        mContext.startActivity(intent);
    }

    public Intent generateCreateEventIntent(
        long startMillis, long endMillis,  boolean allDayEvent,
        String title, long calendarId)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(mContext, EditEventActivity.class);
        intent.putExtra(EXTRA_EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(EXTRA_EVENT_END_TIME, endMillis);
        intent.putExtra(EXTRA_EVENT_ALL_DAY, allDayEvent);
        intent.putExtra(Events.CALENDAR_ID, calendarId);
        intent.putExtra(Events.TITLE, title);
        return intent;
    }

    public void launchViewEvent(
        long eventId, long startMillis, long endMillis, int response)
    {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
        intent.setData(eventUri);
        intent.setClass(mContext, AllInOneActivity.class);
        intent.putExtra(EXTRA_EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(EXTRA_EVENT_END_TIME, endMillis);
        intent.putExtra(ATTENDEE_STATUS, response);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivity(intent);
    }

    private void launchEditEvent(
        long eventId, long startMillis, long endMillis)
    {
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
        Intent intent = new Intent(Intent.ACTION_EDIT, uri);
        intent.putExtra(EXTRA_EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(EXTRA_EVENT_END_TIME, endMillis);
        intent.setClass(mContext, EditEventActivity.class);
        intent.putExtra(EVENT_EDIT_ON_LAUNCH, true);
        mEventId = eventId;
        mContext.startActivity(intent);
    }

    private void launchDeleteEvent(long eventId, long startMillis, long endMillis) {
        launchDeleteEventAndFinish(
            eventId, startMillis, endMillis);
    }

    private void launchDeleteEventAndFinish(
        long eventId, long startMillis, long endMillis)
    {
        DeleteEventHelper deleteEventHelper = new DeleteEventHelper(
            mContext, null, false /* exit when done */);
        deleteEventHelper.delete(startMillis, endMillis, eventId, -1);
    }

    private void launchSearch(String query, ComponentName componentName) {
        final SearchManager searchManager =
                (SearchManager) mContext.getSystemService(Context.SEARCH_SERVICE);
        final SearchableInfo searchableInfo = searchManager.getSearchableInfo(componentName);
        final Intent intent = new Intent(Intent.ACTION_SEARCH);
        intent.putExtra(SearchManager.QUERY, query);
        intent.setComponent(searchableInfo.getSearchActivity());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mContext.startActivity(intent);
    }

    /**
     * Performs a manual refresh of calendars in all known accounts.
     */
    public void refreshCalendars() {
        Account[] accounts = AccountManager.get(mContext).getAccounts();
        Log.d(TAG, "Refreshing " + accounts.length + " accounts");

        String authority = Calendars.CONTENT_URI.getAuthority();
        for (Account account : accounts) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Refreshing calendars for: " + account);
            }
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            ContentResolver.requestSync(account, authority, extras);
        }
    }

    private String actionInfoToString(ActionInfo actionInfo) {
        String tmp = "Unknown";

        StringBuilder builder = new StringBuilder();
        if ((actionInfo.actionType & ControllerAction.GO_TO) != 0) {
            tmp = "Go to time/event";
        } else if ((actionInfo.actionType & ControllerAction.CREATE_EVENT) != 0) {
            tmp = "New event";
        } else if ((actionInfo.actionType & ControllerAction.EDIT_EVENT) != 0) {
            tmp = "Edit event";
        } else if ((actionInfo.actionType & ControllerAction.DELETE_EVENT) != 0) {
            tmp = "Delete event";
        } else if ((actionInfo.actionType & ControllerAction.LAUNCH_SETTINGS) != 0) {
            tmp = "Launch settings";
        } else if ((actionInfo.actionType & ControllerAction.EVENTS_CHANGED) != 0) {
            tmp = "Refresh events";
        } else if ((actionInfo.actionType & ControllerAction.SEARCH) != 0) {
            tmp = "Search";
        } else if ((actionInfo.actionType & ControllerAction.USER_HOME) != 0) {
            tmp = "Gone home";
        } else if ((actionInfo.actionType & ControllerAction.UPDATE_TITLE) != 0) {
            tmp = "Update title";
        }
        builder.append(tmp);
        builder.append(": id=");
        builder.append(actionInfo.eventId);
        builder.append(", selected=");
        builder.append(actionInfo.selectedTime);
        builder.append(", start=");
        builder.append(actionInfo.startTime);
        builder.append(", end=");
        builder.append(actionInfo.endTime);
        builder.append(", viewType=");
        builder.append(actionInfo.viewType);
        builder.append(", x=");
        builder.append(actionInfo.x);
        builder.append(", y=");
        builder.append(actionInfo.y);
        return builder.toString();
    }

    /**
     * One of the action types that are sent to or from the controller
     */
    public interface ControllerAction {
        long CREATE_EVENT = 1L;

        // full detail view in edit mode
        long EDIT_EVENT = 1L << 3;

        long DELETE_EVENT = 1L << 4;

        long GO_TO = 1L << 5;

        long LAUNCH_SETTINGS = 1L << 6;

        long EVENTS_CHANGED = 1L << 7;

        long SEARCH = 1L << 8;

        // User has pressed the home key
        long USER_HOME = 1L << 9;

        // date range has changed, update the title
        long UPDATE_TITLE = 1L << 10;
    }

    /**
     * One of the Agenda/Day/Week/Month view types
     */
    public interface ViewType {
        int DETAIL = -1;
        int CURRENT = 0;
        int AGENDA = 1;
        int DAY = 2;
        int WEEK = 3;
        int MONTH = 4;
        int EDIT = 5;
        int MAX_VALUE = 5;
    }

    public interface ActionHandler {
        long getSupportedActionTypes();

        void handleAction(ActionInfo actionInfo);

        /**
         * This notifies the handler that the database has changed and it should
         * update its view.
         */
        void eventsChanged();
    }

    public static class ActionInfo {

        private static final long ALL_DAY_MASK = 0x100;
        private static final int ATTENDEE_STATUS_NONE_MASK = 0x01;
        private static final int ATTENDEE_STATUS_ACCEPTED_MASK = 0x02;
        private static final int ATTENDEE_STATUS_DECLINED_MASK = 0x04;
        private static final int ATTENDEE_STATUS_TENTATIVE_MASK = 0x08;

        public long actionType; // one of the ControllerAction's
        public int viewType; // one of the ViewType
        public long eventId; // event id
        public Time selectedTime; // the selected time in focus

        // Event start and end times.  All-day events are represented in:
        // - local time for GO_TO commands
        // - UTC time for VIEW_EVENT and other event-related commands
        public Time startTime;
        public Time endTime;

        public int x; // x coordinate in the activity space
        public int y; // y coordinate in the activity space
        public String query; // query for a user search
        public ComponentName componentName;  // used in combination with query
        public String eventTitle;
        public long calendarId;

        /**
         * For ControllerAction.CREATE_EVENT:
         * Set to {@link #EXTRA_CREATE_ALL_DAY} for creating an all-day event.
         * <p/>
         * For ControllerAction.GO_TO:
         * Set to {@link #EXTRA_GOTO_TIME} to go to the specified date/time.
         * Set to {@link #EXTRA_GOTO_DATE} to consider the date but ignore the time.
         * Set to {@link #EXTRA_GOTO_BACK_TO_PREVIOUS} if back should bring back
         * previous view.
         * Set to {@link #EXTRA_GOTO_TODAY} if this is a user request to go to the
         * current time.
         * <p/>
         * For ControllerAction.UPDATE_TITLE:
         * Set formatting flags for Utils.formatDateRange
         */
        public long extraLong;

        // Used to build the extra long for a VIEW event.
        public static long buildViewExtraLong(int response, boolean allDay) {
            long extra = allDay ? ALL_DAY_MASK : 0;

            switch (response) {
                case Attendees.ATTENDEE_STATUS_NONE:
                    extra |= ATTENDEE_STATUS_NONE_MASK;
                    break;
                case Attendees.ATTENDEE_STATUS_ACCEPTED:
                    extra |= ATTENDEE_STATUS_ACCEPTED_MASK;
                    break;
                case Attendees.ATTENDEE_STATUS_DECLINED:
                    extra |= ATTENDEE_STATUS_DECLINED_MASK;
                    break;
                case Attendees.ATTENDEE_STATUS_TENTATIVE:
                    extra |= ATTENDEE_STATUS_TENTATIVE_MASK;
                    break;
                default:
                    Log.wtf(TAG, "Unknown attendee response " + response);
                    extra |= ATTENDEE_STATUS_NONE_MASK;
                    break;
            }
            return extra;
        }

        public boolean isAllDay() {
            return (extraLong & ALL_DAY_MASK) != 0;
        }
    }
}
