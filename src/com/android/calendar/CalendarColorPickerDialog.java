/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Modifications from the original version Copyright (C) Richard Parkins 2024
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

import android.app.Activity;
import android.app.Dialog;
import android.content.ContentProviderResult;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.calendar.colorpicker.ColorPickerDialog;
import com.android.calendar.colorpicker.HsvColorComparator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import ws.xsoh.etar.R;

public class CalendarColorPickerDialog extends ColorPickerDialog
    implements AsyncQueryService.AsyncQueryDone
{

    static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR
    };
    // This looks a bit messy, but it makes the compiler do the work
    // and avoids the maintenance burden of keeping track of the indices by hand.
    private static final List<String> calendarsProjection =
        Arrays.asList(CALENDARS_PROJECTION);
    static final int CALENDARS_INDEX_ACCOUNT_NAME =
        calendarsProjection.indexOf(Calendars.ACCOUNT_NAME);
    static final int CALENDARS_INDEX_ACCOUNT_TYPE =
        calendarsProjection.indexOf(Calendars.ACCOUNT_TYPE);
    static final int CALENDARS_INDEX_CALENDAR_COLOR =
        calendarsProjection.indexOf(Calendars.CALENDAR_COLOR);

    static final String[] COLORS_PROJECTION = new String[] {
            Colors._ID,
            Colors.ACCOUNT_NAME,
            Colors.ACCOUNT_TYPE,
            Colors.COLOR,
            Colors.COLOR_KEY,
    };
    // This looks a bit messy, but it makes the compiler do the work
    // and avoids the maintenance burden of keeping track of the indices by hand.
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

    private static final int NUM_COLUMNS = 4;
    private static final String KEY_CALENDAR_ID = "calendar_id";
    private static final String KEY_COLOR_KEYS = "color_keys";
    private static final int TOKEN_QUERY_CALENDARS = 1 << 1;
    private static final int TOKEN_QUERY_CALENDAR_COLORS = 1 << 2;
    private static final int TOKEN_QUERY_EVENT_COLORS = 1 << 3;
    private static final int[] mDefaultColors = {
            0, 0xFF000000,
            1, 0xFF000055,
            2, 0xFF0000AA,
            3, 0xFF0000FF,
            4, 0xFF005500,
            5, 0xFF005555,
            6, 0xFF0055AA,
            7, 0xFF0055FF,
            8, 0xFF00AA00,
            9, 0xFF00AA55,
            10, 0xFF00AAAA,
            11, 0xFF00AAFF,
            12, 0xFF00FF00,
            13, 0xFF00FF55,
            14, 0xFF00FFAA,
            15, 0xFF00FFFF,
            16, 0xFF550000,
            17, 0xFF550055,
            18, 0xFF5500AA,
            19, 0xFF5500FF,
            20, 0xFF555500,
            21, 0xFF555555,
            22, 0xFF5555AA,
            23, 0xFF5555FF,
            24, 0xFF55AA00,
            25, 0xFF55AA55,
            26, 0xFF55AAAA,
            27, 0xFF55AAFF,
            28, 0xFF55FF00,
            29, 0xFF55FF55,
            30, 0xFF55FFAA,
            31, 0xFF55FFFF,
            32, 0xFFAA0000,
            33, 0xFFAA0055,
            34, 0xFFAA00AA,
            35, 0xFFAA00FF,
            36, 0xFFAA5500,
            37, 0xFFAA5555,
            38, 0xFFAA55AA,
            39, 0xFFAA55FF,
            40, 0xFFAAAA00,
            41, 0xFFAAAA55,
            42, 0xFFAAAAAA,
            43, 0xFFAAAAFF,
            44, 0xFFAAFF00,
            45, 0xFFAAFF55,
            46, 0xFFAAFFAA,
            47, 0xFFAAFFFF,
            48, 0xFFFF0000,
            49, 0xFFFF0055,
            50, 0xFFFF00AA,
            51, 0xFFFF00FF,
            52, 0xFFFF5500,
            53, 0xFFFF5555,
            54, 0xFFFF55AA,
            55, 0xFFFF55FF,
            56, 0xFFFFAA00,
            57, 0xFFFFAA55,
            58, 0xFFFFAAAA,
            59, 0xFFFFAAFF,
            60, 0xFFFFFF00,
            61, 0xFFFFFF55,
            62, 0xFFFFFFAA,
            63, 0xFFFFFFFF
    };

    // We keep a map of colour keys, but we don't seem to use it
    private HashMap<Integer,String> mColorKeyMap;
    private long mCalendarId;
    private AsyncQueryService mService;
    private String mAccountName;
    private String mAccountType;
    private ArrayList<Integer> mColorsArray;

    public CalendarColorPickerDialog(
        @NonNull Context context)
    {
        super(context);
        setOwnerActivity((Activity) context);
    }

    public static CalendarColorPickerDialog newInstance(
        @NonNull Context context, long calendarId,
        boolean isTablet)
    {
        CalendarColorPickerDialog ret = new CalendarColorPickerDialog(context);
        ret.mTitleResId = R.string.calendar_color_picker_dialog_title;
        ret.mColumns = NUM_COLUMNS;
        ret.mSize = isTablet ? SIZE_LARGE : SIZE_SMALL;
        ret.setCalendarId(calendarId);
        return ret;
    }

    @NonNull
    @Override
    public Bundle onSaveInstanceState() {
        Bundle b = super.onSaveInstanceState();
        b.putLong(KEY_CALENDAR_ID, mCalendarId);
        b.putSerializable(KEY_COLOR_KEYS, mColorKeyMap);
        return b;
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCalendarId = savedInstanceState.getLong(KEY_CALENDAR_ID);
            mColorKeyMap = (HashMap<Integer, String>)
                    savedInstanceState.getSerializable(KEY_COLOR_KEYS);
            startQuery();
        }
    }

    public void setCalendarId(long calendarId) {
        if (calendarId != mCalendarId) {
            mCalendarId = calendarId;
            startQuery();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        return dialog;
    }

    private void startQuery() {
        showProgressBarView();
        if (mService == null) {
            mService = CalendarApplication.getAsyncQueryService();
        }
        mService.startQuery(
                TOKEN_QUERY_CALENDARS, this,
                ContentUris.withAppendedId(Calendars.CONTENT_URI, mCalendarId),
                CALENDARS_PROJECTION, null, null,
                null);
    }

    public void colorSelected(int color) {
        if (color == mSelectedColor || mService == null) {
            return;
        }

        ContentValues values = new ContentValues();
        values.put(Calendars.CALENDAR_COLOR, color);
        mService.startUpdate(
                null, null /* no callback wanted */,
                ContentUris.withAppendedId(Calendars.CONTENT_URI, mCalendarId),
                values, null, null);
    }


    /**
     * Called when an asynchronous query is completed.
     *
     * @param cookie the cookie object that's passed in from
     *               AsyncQueryService.startQuery().
     * @param cursor The cursor holding the results from the query,
     *               may be empty if nothing matched or null if it failed.
     */
    @Override
    public void onQueryDone(@Nullable Object cookie, Cursor cursor) {
        if (cursor == null) {
            return;
        }

        if (cookie == null) {
            return;
        }

        // If the Activity is finishing, then close the cursor.
        // Otherwise, use the new cursor in the adapter.
        final Activity activity = getOwnerActivity();
        if (activity == null || activity.isFinishing()) {
            cursor.close();
            return;
        }

        String[] args;
        switch ((int)cookie) {
            case TOKEN_QUERY_CALENDARS:
                if (!cursor.moveToFirst()) {
                    cursor.close();
                    dismiss();
                    break;
                }
                mSelectedColor = Utils.getDisplayColorFromColor(
                    cursor.getInt(CALENDARS_INDEX_CALENDAR_COLOR));
                mAccountName = cursor.getString(CALENDARS_INDEX_ACCOUNT_NAME);
                mAccountType = cursor.getString(CALENDARS_INDEX_ACCOUNT_TYPE);
                cursor.close();
                mService.startQuery(TOKEN_QUERY_CALENDAR_COLORS, this,
                        Colors.CONTENT_URI, COLORS_PROJECTION,
                        Colors.COLOR_TYPE + "=" + Colors.TYPE_CALENDAR,
                        null, null);
                break;
            case TOKEN_QUERY_CALENDAR_COLORS:
                // We initialise these here because we seem to get to
                // this entry point twice even though the call to
                // startQuery above is only executed once.
                mColorKeyMap = new HashMap<>();
                mColorsArray = new ArrayList<>();
                if (cursor.moveToFirst()) {
                    do {
                        String accountName =
                                cursor.getString(COLORS_INDEX_ACCOUNT_NAME);
                        String accountType =
                                cursor.getString(COLORS_INDEX_ACCOUNT_TYPE);
                        if (   (accountName.equals(mAccountName))
                            && (accountType.equals(mAccountType)))
                        {
                            String colorKey =
                                    cursor.getString(COLORS_INDEX_COLOR_KEY);
                            int displayColor =
                                    cursor.getInt(COLORS_INDEX_COLOR);
                            mColorKeyMap.put(displayColor, colorKey);
                            mColorsArray.add(displayColor);
                        }
                    } while (cursor.moveToNext());
                }
                if (mColorsArray.isEmpty()) {
                    // If the calendar has no calendar colours
                    // try its event colours instead
                    cursor.close();
                    mService.startQuery(TOKEN_QUERY_CALENDAR_COLORS, this,
                            Colors.CONTENT_URI, COLORS_PROJECTION,
                            Colors.COLOR_TYPE + "=" + Colors.TYPE_EVENT,
                            null, null);
                    break;
                }
                //FALLTHRU
            case TOKEN_QUERY_EVENT_COLORS:
                if (mColorsArray.isEmpty()) {
                    if (cursor.moveToFirst()) {
                        do {
                            String accountName =
                                    cursor.getString(COLORS_INDEX_ACCOUNT_NAME);
                            String accountType =
                                    cursor.getString(COLORS_INDEX_ACCOUNT_TYPE);
                            if (   (accountName.equals(mAccountName))
                                    && (accountType.equals(mAccountType)))
                            {
                                String colorKey =
                                        cursor.getString(COLORS_INDEX_COLOR_KEY);
                                int displayColor =
                                        cursor.getInt(COLORS_INDEX_COLOR);
                                mColorKeyMap.put(displayColor, colorKey);
                                mColorsArray.add(displayColor);
                            }
                        } while (cursor.moveToNext());
                    } else {
                        // If the calendar has no event colours either
                        // use our own built-in colour map
                        for (int i = 0; i < 128;) {
                            String colorKey = String.valueOf(mDefaultColors[i++]);
                            int displayColor = mDefaultColors[i++];
                            mColorKeyMap.put(displayColor, colorKey);
                            mColorsArray.add(displayColor);
                        }
                    }
                }
                cursor.close();
                Integer[] colorsToSort = mColorsArray.toArray(new Integer[0]);
                Arrays.sort(colorsToSort, new HsvColorComparator());
                mColors = new int[colorsToSort.length];
                for (int i = 0; i < mColors.length; i++) {
                    mColors[i] = colorsToSort[i];
                }
                showPaletteView();
                cursor.close();
                break;
        }
    }

    /**
     * Called when an asynchronous insert is completed.
     *
     * @param cookie the cookie object that's passed in from
     *               AsyncQueryService.startInsert().
     * @param uri    the URL of the newly created row,
     *               null indicates failure.
     */
    @Override
    public void onInsertDone(@Nullable Object cookie, Uri uri) {
        // never called
    }

    /**
     * Called when an asynchronous update is completed.
     *
     * @param cookie the cookie object that's passed in from
     *               AsyncQueryService.startUpdate().
     * @param result the number of rows updated
     *               zero indicates failure.
     */
    @Override
    public void onUpdateDone(@Nullable Object cookie, int result) {
        // never called
    }

    /**
     * Called when an asynchronous delete is completed.
     *
     * @param cookie the cookie object that's passed in from
     *               AsyncQueryService.startDelete().
     * @param result the number of rows deleted: zero indicates failure.
     */
    @Override
    public void onDeleteDone(@Nullable Object cookie, int result) {
        // never called
    }

    /**
     * Called when an asynchronous ContentProviderOperation is completed.
     *
     * @param cookie  the cookie object that's passed in from
     *                AsyncQueryService.startBatch().
     * @param results an array of results from the operations:
     *                the type of each result depends on the operation.
     */
    @Override
    public void onBatchDone(
        @Nullable Object cookie, ContentProviderResult[] results)
    {
        // never called
    }
}
