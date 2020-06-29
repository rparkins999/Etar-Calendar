/*
 * Copyright (C) 2020 Richard P. Parkins, M. A.
 *
 * Logging with some backtrace information
 * Thanks to stackoverflow.com for hints on how to do it
 *
 * This file may be used under the terms of the GNU General Public License
 * version 3.0 or later as published by the Free Software Foundation.
 *
 */

package com.android.calendar;

import android.util.Log;

import androidx.annotation.Nullable;

class Llog extends Object {
    private static String getMethodName(StackTraceElement trace) {
        String s = trace.getMethodName();
        if (s.equals("<init>")) {
            s = trace.getClassName();
            s = s.substring(s.lastIndexOf(".") + 1);
        }
        return s;
    }
    private static String caller(int n, String args) {
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        int length = elements.length;
        if (length < 5) { return ""; }
        StackTraceElement trace = elements[4];
        StringBuilder sb =
            new StringBuilder(getMethodName(trace))
                .append((args == null ? "()" : "(" + args + ")"))
                .append(" line " + trace.getLineNumber());
        for (int j = 5; j <= n + 4; ++j) {
            if (j >= elements.length) { break; }
            trace = Thread.currentThread().getStackTrace()[j];
            sb.append(" called from ")
              .append(getMethodName(trace))
              .append(" line ")
              .append(trace.getLineNumber());
        }
        return sb.toString();
    }
    // Log method name with (), line number, and a string
    static void d(String s) {
        Log.d(caller(0, null), s);
    }
    // Log method name with (args), line number, and a string
    // args should be stringified arguments to the method
    static void d(String s, String args) {
        Log.d(caller(0, args), s);
    }
    // Log method name with n extra backtrace frames, and a string
    // args should be stringified arguments to the method or null
    static void d(String s, @Nullable String args, int n) {
        Log.d(caller(n, args), s);
    }
}
