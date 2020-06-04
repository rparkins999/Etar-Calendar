/*
 * Copyright (C) 2020 Richard P. Parkins, M. A.
 *
 * Thanks to stackoverflow.com for hints on how to do it
 *
 * This file may be used under the terms of the GNU General Public License
 * version 3.0 or later as published by the Free Software Foundation.
 *
 */

package com.android.calendar;

import android.util.Log;

class Llog extends Object {
    // Log method name with (), line number, and a string
    static void d(String s) {
        StackTraceElement trace = Thread.currentThread().getStackTrace()[3];
        String prefix = trace.getMethodName();
        if (prefix.equals("<init>")) {
            prefix = trace.getClassName();
            prefix = prefix.substring(prefix.lastIndexOf(".") + 1);
        }
        Log.d(prefix + "()", "Line " + trace.getLineNumber() + " " + s);
    }
    // Log method name with (args), line number, and a string
    // args should be stringified arguments to the method
    static void d(String s, String args) {
        StackTraceElement trace = Thread.currentThread().getStackTrace()[3];
        String prefix = trace.getMethodName();
        if (prefix.equals("<init>")) {
            prefix = trace.getClassName();
            prefix = prefix.substring(prefix.lastIndexOf(".") + 1);
        }
        Log.d(prefix + "(" + args + ")", "Line " + trace.getLineNumber() + " " + s);
    }
}
