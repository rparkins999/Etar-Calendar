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

import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract.Events;
import android.provider.SearchRecentSuggestions;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.android.calendar.CalendarController.ActionInfo;
import com.android.calendar.CalendarController.ControllerAction;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.agenda.AgendaFragment;
import com.android.calendar.event.EditEventActivity;

import ws.xsoh.etar.R;

import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;

public class SearchActivity extends AppCompatActivity implements CalendarController.ActionHandler,
        SearchView.OnQueryTextListener, MenuItemCompat.OnActionExpandListener {


    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    protected static final String BUNDLE_KEY_RESTORE_SEARCH_QUERY =
        "key_restore_search_query";
    private static final String TAG = SearchActivity.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final int HANDLER_KEY = 0;
   private static boolean mIsMultipane;
    // display event details to the side of the event list
    private boolean mShowEventDetailsWithAgenda;
    private CalendarController mController;
    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            eventsChanged();
        }
    };
    private long mCurrentEventId = -1;
    private String mQuery;
    private SearchView mSearchView;
    private DeleteEventHelper mDeleteEventHelper;
    private Handler mHandler;
    // runs when a timezone was changed and updates the today icon
    private final Runnable mTimeChangesUpdater = new Runnable() {
        @Override
        public void run() {
            Utils.setMidnightUpdater(mHandler, mTimeChangesUpdater,
                    Utils.getTimeZone(SearchActivity.this, mTimeChangesUpdater));
            SearchActivity.this.invalidateOptionsMenu();
        }
    };
    private BroadcastReceiver mTimeChangesReceiver;
    private ContentResolver mContentResolver;
    private final DynamicTheme dynamicTheme = new DynamicTheme();

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        dynamicTheme.onCreate(this);
        // This needs to be created before setContentView
        mController = CalendarController.getInstance(this);
        mHandler = new Handler();

        mIsMultipane = Utils.getConfigBool(this, R.bool.multiple_pane_config);
        mShowEventDetailsWithAgenda =
            Utils.getConfigBool(this, R.bool.show_event_details_with_agenda);

        setContentView(R.layout.simple_frame_layout_material);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        mContentResolver = getContentResolver();

        if (mIsMultipane) {
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
            }
        } else {
            if (getSupportActionBar() != null) {
               getSupportActionBar().setDisplayOptions(0, ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
            }
        }

        // Must be the first to register because this activity can modify the
        // list of event handlers in it's handle method. This affects who the
        // rest of the handlers the controller dispatches to are.
        mController.registerActionHandler(HANDLER_KEY, this);

        mDeleteEventHelper = new DeleteEventHelper(this, this,
                false /* don't exit when done */);

        long millis = 0;
        if (icicle != null) {
            // Returns 0 if key not found
            millis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
            if (DEBUG) {
                Log.v(TAG, "Restore value from icicle: " + millis);
            }
        }
        Intent intent = getIntent();
        if (millis == 0) {
            // Didn't find a time in the bundle, look in intent or current time
            millis = Utils.timeFromIntentInMillis(intent);
        }

        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query;
            if (icicle != null && icicle.containsKey(BUNDLE_KEY_RESTORE_SEARCH_QUERY)) {
                query = icicle.getString(BUNDLE_KEY_RESTORE_SEARCH_QUERY);
            } else {
                query = intent.getStringExtra(SearchManager.QUERY);
            }
            initFragments(millis, query);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mController.deregisterAllActionHandlers();
        CalendarController.removeInstance(this);
    }

    private void initFragments(long timeMillis, String query) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();

        AgendaFragment searchResultsFragment = new AgendaFragment(timeMillis, true);
        ft.replace(R.id.body_frame, searchResultsFragment);
        mController.registerActionHandler(R.id.body_frame, searchResultsFragment);

        ft.commit();
        Time t = new Time();
        t.set(timeMillis);
        search(query, t);
    }

    private void editEvent(ActionInfo actionInfo) {
        Intent intent = new Intent(Intent.ACTION_EDIT);
        Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, actionInfo.eventId);
        intent.setData(eventUri);
        intent.setClass(this, EditEventActivity.class);
        intent.putExtra(EXTRA_EVENT_BEGIN_TIME,
                actionInfo.startTime != null ? actionInfo.startTime.toMillis(true) : -1);
        intent.putExtra(
                EXTRA_EVENT_END_TIME, actionInfo.endTime != null ? actionInfo.endTime.toMillis(true) : -1);
        startActivity(intent);
        mCurrentEventId = actionInfo.eventId;
    }

    private void search(String searchQuery, Time goToTime) {
        // save query in recent queries
        SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                Utils.getSearchAuthority(this),
                CalendarRecentSuggestionsProvider.MODE);
        suggestions.saveRecentQuery(searchQuery, null);


        ActionInfo searchActionInfo = new ActionInfo();
        searchActionInfo.actionType = ControllerAction.SEARCH;
        searchActionInfo.query = searchQuery;
        searchActionInfo.viewType = ViewType.AGENDA;
        if (goToTime != null) {
            searchActionInfo.startTime = goToTime;
        }
        mController.sendAction(this, searchActionInfo);
        mQuery = searchQuery;
        if (mSearchView != null) {
            mSearchView.setQuery(mQuery, false);
            mSearchView.clearFocus();
        }
    }

    private void deleteEvent(long eventId, long startMillis, long endMillis) {
        mDeleteEventHelper.delete(startMillis, endMillis, eventId);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.search_title_bar, menu);

        // replace the default top layer drawable of the today icon with a custom drawable
        // that shows the day of the month of today
        MenuItem menuItem = menu.findItem(R.id.action_today);
        LayerDrawable icon = (LayerDrawable) menuItem.getIcon();
        Utils.setTodayIcon(
                icon, this, Utils.getTimeZone(SearchActivity.this, mTimeChangesUpdater));

        MenuItem item = menu.findItem(R.id.action_search);

        MenuItemCompat.expandActionView(item);
        MenuItemCompat.setOnActionExpandListener(item, this);
        mSearchView = (SearchView) MenuItemCompat.getActionView(item);
        Utils.setUpSearchView(mSearchView, this);
        mSearchView.setQuery(mQuery, false);
        mSearchView.clearFocus();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Time t = null;
        final int itemId = item.getItemId();
        if (itemId == R.id.action_today) {
            t = new Time();
            t.setToNow();
            mController.sendAction(this, CalendarController.ControllerAction.GO_TO, t, null, -1, ViewType.CURRENT);
            return true;
        } else if (itemId == R.id.action_search) {
            return false;
        } else if (itemId == R.id.action_settings) {
            mController.sendAction(this, ControllerAction.LAUNCH_SETTINGS, null, null, 0, 0);
            return true;
        } else if (itemId == android.R.id.home) {
            Utils.returnToCalendarHome(this);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // From the Android Dev Guide: "It's important to note that when
        // onNewIntent(Intent) is called, the Activity has not been restarted,
        // so the getIntent() method will still return the Intent that was first
        // received with onCreate(). This is why setIntent(Intent) is called
        // inside onNewIntent(Intent) (just in case you call getIntent() at a
        // later time)."
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            search(query, null);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_RESTORE_TIME, mController.getTime());
        outState.putString(BUNDLE_KEY_RESTORE_SEARCH_QUERY, mQuery);
    }

    @Override
    protected void onResume() {
        super.onResume();
        dynamicTheme.onResume(this);

        Utils.setMidnightUpdater(
                mHandler, mTimeChangesUpdater, Utils.getTimeZone(this, mTimeChangesUpdater));
        // Make sure the today icon is up to date
        invalidateOptionsMenu();
        mTimeChangesReceiver = Utils.setTimeChangesReceiver(this, mTimeChangesUpdater);
        mContentResolver.registerContentObserver(Events.CONTENT_URI, true, mObserver);
        // We call this in case the user changed the time zone
        eventsChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Utils.resetMidnightUpdater(mHandler, mTimeChangesUpdater);
        Utils.clearTimeChangesReceiver(this, mTimeChangesReceiver);
        mContentResolver.unregisterContentObserver(mObserver);
    }

    @Override
    public void eventsChanged() {
        mController.sendAction(this, CalendarController.ControllerAction.EVENTS_CHANGED, null, null, -1, ViewType.CURRENT);
    }

    @Override
    public long getSupportedActionTypes() {
        return ControllerAction.EDIT_EVENT | CalendarController.ControllerAction.DELETE_EVENT;
    }

    @Override
    public void handleAction(ActionInfo actionInfo) {
        long endTime = (actionInfo.endTime == null) ? -1 : actionInfo.endTime.toMillis(false);
        if (actionInfo.actionType == CalendarController.ControllerAction.EDIT_EVENT) {
            editEvent(actionInfo);
        } else if (actionInfo.actionType == CalendarController.ControllerAction.DELETE_EVENT) {
            deleteEvent(actionInfo.eventId, actionInfo.startTime.toMillis(false), endTime);
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mQuery = query;
        mController.sendAction(this, CalendarController.ControllerAction.SEARCH, null, null, -1, ViewType.CURRENT, 0, query,
                getComponentName());
        return false;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        Utils.returnToCalendarHome(this);
        return false;
    }
}
