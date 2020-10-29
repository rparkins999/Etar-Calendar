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

package com.android.calendar.month;

import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView.LayoutParams;

import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.ControllerAction;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.Event;
import com.android.calendar.Llog;
import com.android.calendar.Utils;
import com.android.calendar.event.EditEventActivity;

import java.util.ArrayList;
import java.util.HashMap;

import ws.xsoh.etar.R;

import static com.android.calendar.CalendarController.EVENT_EDIT_ON_LAUNCH;

public class MonthByWeekAdapter extends SimpleWeeksAdapter {
    public static final String WEEK_PARAMS_IS_MINI = "mini_month";
    private static final String TAG = "MonthByWeekAdapter";
    private static final long ANIMATE_TODAY_TIMEOUT = 1000;
    // Used to insure minimal time for seeing the click animation before switching views
    private static final int mOnTapDelay = 100;
    protected static int DEFAULT_QUERY_DAYS = 7 * 8; // 8 weeks
    // Minimal time for a down touch action before stating the click animation, this insures that
    // there is no click animation on flings
    private static int mOnDownDelay;
    private static int mTotalClickDelay;
    // Minimal distance to move the finger in order to cancel the click animation
    private static float mMovedPixelToCancel;
    private final boolean mShowAgendaWithMonth;
    protected CalendarController mController;
    protected String mHomeTimeZone;
    protected Time mTempTime;
    protected Time mToday;
    protected int mFirstJulianDay;
    protected int mQueryDays;
    protected boolean mIsMiniMonth = true;
    protected int mOrientation = Configuration.ORIENTATION_LANDSCAPE;
    protected ArrayList<ArrayList<Event>> mEventDayList = new ArrayList<ArrayList<Event>>();
    protected ArrayList<Event> mEvents = null;
    // Synchronisation object needs to be final, but mClickedView is modified
    private final Object mClickedViewSync = new Object();
    private MonthWeekEventsView mClickedView;
    MonthWeekEventsView mSingleTapUpView;
    MonthWeekEventsView mLongClickedView;
    float mClickedXLocation; // Used to find which day was clicked
    float mClickedYLocation; // Used to find which event if any was clicked
    long mClickedEventId;
    // Perform the tap animation in a runnable to allow a delay before showing the tap color.
    // This is done to prevent a click animation when a fling is done.
    private final Runnable mDoClick = new Runnable() {
        @Override
        public void run() {
            mClickedEventId = -1;
            // do as little work as possible inside the synchronized block
            synchronized (mClickedViewSync) {
                if (mClickedView != null) {
                    mClickedEventId =
                        mClickedView.getClickedEventId(mClickedXLocation, mClickedYLocation);
                    mLongClickedView = mClickedView;
                    mClickedView = null;
                }
            }
        }
    };
    // Performs the single tap operation: go to the tapped day.
    // This is done in a runnable to allow the click animation to finish
    // before switching views
    private final Runnable mDoSingleTapUp = new Runnable() {
        @Override
        public void run() {
            synchronized (mClickedViewSync) {
                if (mSingleTapUpView != null) {
                    if (mClickedEventId >= 0) {
                        // User clicked on an event
                        Uri uri = ContentUris.withAppendedId(
                            CalendarContract.Events.CONTENT_URI, mClickedEventId);
                        Intent intent = new Intent(Intent.ACTION_EDIT, uri);
                        intent.setClass(mContext, EditEventActivity.class);
                        intent.putExtra(EVENT_EDIT_ON_LAUNCH, true);
                        mContext.startActivity(intent);
                    } else {
                        Time day = mSingleTapUpView.getDayFromLocation(mClickedXLocation);
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Touched day at Row=" + mSingleTapUpView.mWeek + " day=" + day.toString());
                        }
                        if (day != null) {
                            onDayTapped(day);
                        }
                    }
                    clearClickedView(mSingleTapUpView);
                    mSingleTapUpView = null;
                }
            }
        }
    };
    long mClickTime;                        // Used to calculate minimum click animation time
    private boolean mAnimateToday = false;
    private long mAnimateTime = 0;

    public MonthByWeekAdapter(Context context, HashMap<String, Integer> params) {
        super(context, params);
        if (params.containsKey(WEEK_PARAMS_IS_MINI)) {
            mIsMiniMonth = params.get(WEEK_PARAMS_IS_MINI) != 0;
        }
        mShowAgendaWithMonth = Utils.getConfigBool(context, R.bool.show_agenda_with_month);
        ViewConfiguration vc = ViewConfiguration.get(context);
        mOnDownDelay = ViewConfiguration.getTapTimeout();
        mMovedPixelToCancel = vc.getScaledTouchSlop();
        mTotalClickDelay = mOnDownDelay + mOnTapDelay;
    }

    public void animateToday() {
        mAnimateToday = true;
        mAnimateTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        super.init();
        mGestureDetector = new GestureDetector(mContext, new CalendarGestureListener());
        mController = CalendarController.getInstance(mContext);
        mHomeTimeZone = Utils.getTimeZone(mContext, null);
        mSelectedDay.switchTimezone(mHomeTimeZone);
        mToday = new Time(mHomeTimeZone);
        mToday.setToNow();
        mTempTime = new Time(mHomeTimeZone);
    }

    private void updateTimeZones() {
        mSelectedDay.timezone = mHomeTimeZone;
        mSelectedDay.normalize(true);
        mToday.timezone = mHomeTimeZone;
        mToday.setToNow();
        mTempTime.switchTimezone(mHomeTimeZone);
    }

    @Override
    public void setSelectedDay(Time selectedTime) {
        mSelectedDay.set(selectedTime);
        long millis = mSelectedDay.normalize(true);
        mSelectedWeek = Utils.getWeeksSinceEpochFromJulianDay(
                Time.getJulianDay(millis, mSelectedDay.gmtoff), mFirstDayOfWeek);
        notifyDataSetChanged();
    }

    public void setEvents(int firstJulianDay, int numDays, ArrayList<Event> events) {
        if (mIsMiniMonth) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Attempted to set events for mini view. Events only supported in full"
                        + " view.");
            }
            return;
        }
        mEvents = events;
        mFirstJulianDay = firstJulianDay;
        mQueryDays = numDays;
        // Create a new list, this is necessary since the weeks are referencing
        // pieces of the old list
        ArrayList<ArrayList<Event>> eventDayList = new ArrayList<ArrayList<Event>>();
        for (int i = 0; i < numDays; i++) {
            eventDayList.add(new ArrayList<Event>());
        }

        if (events == null || events.size() == 0) {
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No events. Returning early--go schedule something fun.");
            }
            mEventDayList = eventDayList;
            refresh();
            return;
        }

        // Compute the new set of days with events
        for (Event event : events) {
            int startDay = event.startDay - mFirstJulianDay;
            int endDay = event.endDay - mFirstJulianDay + 1;
            if (startDay < numDays || endDay >= 0) {
                if (startDay < 0) {
                    startDay = 0;
                }
                if (startDay > numDays) {
                    continue;
                }
                if (endDay < 0) {
                    continue;
                }
                if (endDay > numDays) {
                    endDay = numDays;
                }
                for (int j = startDay; j < endDay; j++) {
                    eventDayList.get(j).add(event);
                }
            }
        }
        if(Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Processed " + events.size() + " events.");
        }
        mEventDayList = eventDayList;
        refresh();
    }

    @SuppressWarnings("unchecked")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mIsMiniMonth) {
            return super.getView(position, convertView, parent);
        }
        MonthWeekEventsView v;
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        HashMap<String, Integer> drawingParams = null;
        boolean isAnimatingToday = false;
        if (convertView != null) {
            v = (MonthWeekEventsView) convertView;
            // Checking updateToday uses the current params instead of the new
            // params, so this is assuming the view is relatively stable
            if (mAnimateToday && v.updateToday(mSelectedDay.timezone)) {
                long currentTime = System.currentTimeMillis();
                // If it's been too long since we tried to start the animation
                // don't show it. This can happen if the user stops a scroll
                // before reaching today.
                if (currentTime - mAnimateTime > ANIMATE_TODAY_TIMEOUT) {
                    mAnimateToday = false;
                    mAnimateTime = 0;
                } else {
                    isAnimatingToday = true;
                    // There is a bug that causes invalidates to not work some
                    // of the time unless we recreate the view.
                    v = new MonthWeekEventsView(mContext);
               }
            } else {
                drawingParams = (HashMap<String, Integer>) v.getTag();
            }
        } else {
            v = new MonthWeekEventsView(mContext);
        }
        if (drawingParams == null) {
            drawingParams = new HashMap<String, Integer>();
        }
        drawingParams.clear();

        v.setLayoutParams(params);
        v.setClickable(true);
        v.setOnTouchListener(this);

        int selectedDay = -1;
        if (mSelectedWeek == position) {
            selectedDay = mSelectedDay.weekDay;
        }

        drawingParams.put(SimpleWeekView.VIEW_PARAMS_HEIGHT, parent.getHeight() / mNumWeeks);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SELECTED_DAY, selectedDay);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SHOW_WK_NUM, mShowWeekNumber ? 1 : 0);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK_START, mFirstDayOfWeek);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_NUM_DAYS, mDaysPerWeek);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK, position);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_FOCUS_MONTH, mFocusMonth);
        drawingParams.put(MonthWeekEventsView.VIEW_PARAMS_ORIENTATION, mOrientation);

        if (isAnimatingToday) {
            drawingParams.put(MonthWeekEventsView.VIEW_PARAMS_ANIMATE_TODAY, 1);
            mAnimateToday = false;
        }

        v.setWeekParams(drawingParams, mSelectedDay.timezone);
        sendEventsToView(v);
        return v;
    }

    private void sendEventsToView(MonthWeekEventsView v) {
        if (mEventDayList.size() == 0) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No events loaded, did not pass any events to view.");
            }
            v.setEvents(null, null);
            return;
        }
        int viewJulianDay = v.getFirstJulianDay();
        int start = viewJulianDay - mFirstJulianDay;
        int end = start + v.mNumDays;
        if (start < 0 || end > mEventDayList.size()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Week is outside range of loaded events. viewStart: " + viewJulianDay
                        + " eventsStart: " + mFirstJulianDay);
            }
            v.setEvents(null, null);
            return;
        }
        v.setEvents(mEventDayList.subList(start, end), mEvents);
    }

    @Override
    protected void refresh() {
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        mShowWeekNumber = Utils.getShowWeekNumber(mContext);
        mHomeTimeZone = Utils.getTimeZone(mContext, null);
        mOrientation = mContext.getResources().getConfiguration().orientation;
        updateTimeZones();
        notifyDataSetChanged();
    }

    @Override
    protected void onDayTapped(Time day) {
        setDayParameters(day);
         if (mShowAgendaWithMonth || mIsMiniMonth) {
            // If agenda view is visible with month view , refresh the views
            // with the selected day's info
            mController.sendAction(mContext, ControllerAction.GO_TO, day, day, -1,
                    ViewType.CURRENT, CalendarController.EXTRA_GOTO_DATE, null, null);
        } else {
            // Else , switch to the detailed view
            mController.sendAction(mContext, ControllerAction.GO_TO, day, day, -1,
                    ViewType.DETAIL,
                            CalendarController.EXTRA_GOTO_DATE
                            | CalendarController.EXTRA_GOTO_BACK_TO_PREVIOUS, null, null);
        }
    }

    private void setDayParameters(Time day) {
        day.timezone = mHomeTimeZone;
        Time currTime = new Time(mHomeTimeZone);
        currTime.set(mController.getTime());
        day.hour = currTime.hour;
        day.minute = currTime.minute;
        day.allDay = false;
        day.normalize(true);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!(v instanceof MonthWeekEventsView)) {
            return super.onTouch(v, event);
        }

        int action = event.getAction();

        // Event was tapped - switch to the detailed view making sure the click animation
        // is done first.
        if (mGestureDetector.onTouchEvent(event)) {
            mSingleTapUpView = (MonthWeekEventsView) v;
            long delay = System.currentTimeMillis() - mClickTime;
            // Make sure the animation is visible for at least mOnTapDelay - mOnDownDelay ms
            mListView.postDelayed(mDoSingleTapUp,
                    delay > mTotalClickDelay ? 0 : mTotalClickDelay - delay);
            return true;
        } else {
            // Animate a click - on down: show the selected day in the "clicked" color.
            // On Up/scroll/move/cancel: hide the "clicked" color.
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    synchronized (mClickedViewSync) {
                        mClickedView = (MonthWeekEventsView) v;
                    }
                    // These positions are relative to the top left corner of
                    // mClickedView, which is the week row.
                    mClickedXLocation = event.getX();
                    mClickedYLocation = event.getY();
                    Llog.d("getX()->" + event.getX() + ", getY()->" + event.getY());
                    mClickTime = System.currentTimeMillis();
                    mListView.postDelayed(mDoClick, mOnDownDelay);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_SCROLL:
                case MotionEvent.ACTION_CANCEL:
                    clearClickedView((MonthWeekEventsView) v);
                    break;
                case MotionEvent.ACTION_MOVE:
                    // No need to cancel on vertical movement, ACTION_SCROLL will do that.
                    if (Math.abs(event.getX() - mClickedXLocation) > mMovedPixelToCancel) {
                        clearClickedView((MonthWeekEventsView) v);
                    }
                    break;
                default:
                    break;
            }
        }
        // Do not tell the frameworks we consumed the touch action so that fling actions can be
        // processed by the fragment.
        return false;
    }

    // Clear the visual cues of the click animation and related running code.
    private void clearClickedView(MonthWeekEventsView v) {
        mListView.removeCallbacks(mDoClick);
        synchronized(v) {
            v.clearClickedDay();
        }
        synchronized (mClickedViewSync) {
            mClickedView = null;
        }
    }

    /**
     * This is here so we can identify events and process them
     */
    protected class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (mLongClickedView != null) {
                Time day = mLongClickedView.getDayFromLocation(mClickedXLocation);
                if (day != null) {
                    mLongClickedView.performHapticFeedback(
                        HapticFeedbackConstants.LONG_PRESS);
                    mController.sendEventActionWithExtraWithTitleWithCalendarId(
                        this, CalendarController.ControllerAction.CREATE_EVENT,
                        -1, day.toMillis(true),
                        day.toMillis(true) + DateUtils.DAY_IN_MILLIS,
                        0, 0,
                        CalendarController.EXTRA_CREATE_ALL_DAY, -1,
                        "", -1);
                }
                mLongClickedView.clearClickedDay();
                mLongClickedView = null;
            }
        }
    }
}
