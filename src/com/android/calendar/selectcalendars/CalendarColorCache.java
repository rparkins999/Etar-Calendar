/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Modifications from the original version Copyright (C) Richard Parkins 2022
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

package com.android.calendar.selectcalendars;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Colors;

import androidx.annotation.Nullable;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarApplication;

import java.util.HashSet;

/**
 * CalendarColorCache queries the provider and stores the account identifiers
 * (name and type) of the accounts which contain optional calendar colors,
 * and thus should allow for the user to choose calendar colors.
 */
public class CalendarColorCache implements AsyncQueryService.AsyncQueryDone
{

    private final HashSet<String> mCache = new HashSet<>();

    private static final String SEPARATOR = "::";

    private final OnCalendarColorsLoadedListener mListener;

    private final StringBuffer mStringBuffer = new StringBuffer();

    private static final String[] PROJECTION =
        new String[] {Colors.ACCOUNT_NAME, Colors.ACCOUNT_TYPE };

    /**
     * Interface which provides callback after provider query of calendar colors.
     */
    public interface OnCalendarColorsLoadedListener {

        /**
         * Callback after the set of accounts with additional calendar colors are loaded.
         */
        void onCalendarColorsLoaded();
    }

    public CalendarColorCache(Context context, OnCalendarColorsLoadedListener listener) {
        mListener = listener;
        CalendarApplication.getAsyncQueryService().startQuery(
            null, this, Colors.CONTENT_URI,
            PROJECTION, Colors.COLOR_TYPE + "=" + Colors.TYPE_CALENDAR,
            null, null);
    }

    /**
     * Inserts a specified account into the set.
     */
    private void insert(String accountName, String accountType) {
        mCache.add(generateKey(accountName, accountType));
    }

    /**
     * Does a set lookup to determine if a specified account has more optional calendar colors.
     */
    public boolean hasColors(String accountName, String accountType) {
        return mCache.contains(generateKey(accountName, accountType));
    }

    /**
     * Clears the cached set.
     */
    private void clear() {
        mCache.clear();
    }

    /**
     * Generates a single key based on account name and account type for map lookup/insertion.
     */
    private String generateKey(String accountName, String accountType) {
        mStringBuffer.setLength(0);
        return mStringBuffer.append(accountName).append(SEPARATOR).append(accountType).toString();
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
        if (cursor.moveToFirst()) {
            clear();
            do {
                insert(cursor.getString(0), cursor.getString(1));
            } while (cursor.moveToNext());
            mListener.onCalendarColorsLoaded();
        }
        cursor.close();
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
     * Called when an asynchronous {@link ContentProviderOperation} is
     * completed.
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
