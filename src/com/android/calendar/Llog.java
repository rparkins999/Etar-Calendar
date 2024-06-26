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
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;

public class Llog {
    private static String forceNonEmpty(String s) {
        if ((s == null) || s.isEmpty()) { return " "; } else { return s; }
    }
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
                .append(" line ").append(trace.getLineNumber());
        for (int j = 5; j <= n + 4; ++j) {
            if (j >= elements.length) { break; }
            trace = Thread.currentThread().getStackTrace()[j];
            String method = getMethodName(trace);
            if (method.startsWith("access$")) {
                ++n; continue;
            }
            sb.append(" called from ")
              .append(method)
              .append(" line ")
              .append(trace.getLineNumber());
        }
        return sb.toString();
    }
    // Log method name with (), line number, and a string
    public static void d(String s) {
        Log.d(caller(0, null), forceNonEmpty(s));
    }
    // Log method name with (args), line number, and a string
    // args should be stringified arguments to the method
    public static void d(String s, String args) {
        Log.d(caller(0, args), forceNonEmpty(s));
    }
    // Log method name with n extra backtrace frames, and a string
    // args should be stringified arguments to the method or null
    public static void d(String s, @Nullable String args, int n) {
        Log.d(caller(n, args), forceNonEmpty(s));
    }
    // dump a view hierarchy
    private static void dumpView(View v, String indent) {
        StringBuilder sb = new StringBuilder();
        sb.append(v.getClass().getSimpleName());
        if (v instanceof TextView && (((TextView) v).getText() != null)) {
            sb.append("->");
            sb.append(((TextView) v).getText());
        } else {
            CharSequence s = v.getContentDescription();
            if (s != null) {
                sb.append(" (").append(s).append(")");
            }
        }
        if (v.getVisibility() == View.VISIBLE) {
            Log.d(indent, sb.toString());
        } else {
            Log.d(indent, sb.append(" not visible").toString());
        }
        if (v instanceof ViewGroup) {
            int n = ((ViewGroup) v).getChildCount();
            for (int i = 0; i < n; ++i) {
                dumpView(((ViewGroup) v).getChildAt(i), indent + ">>");
            }
        }
    }
    public static void d(View v) {
        Log.d(caller(0, null), "Dumping View");
        dumpView(v, ">>");
    }
}
