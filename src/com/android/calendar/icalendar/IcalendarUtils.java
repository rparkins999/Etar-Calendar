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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.system.ErrnoException;
import android.system.OsConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.TimeZone;

/**
 * Helper functions to help adhere to the iCalendar format.
 */
public class IcalendarUtils {

    private static final String INVITE_FILE_NAME = "invite";

    /**
     * Ensure the string conforms to the iCalendar encoding requirements
     * Escape line breaks, commas and semicolons
     * @param sequence a charsequence to encode
     * @return the encoded charsequence as a String
     */
    public static String cleanseString(CharSequence sequence) {
        if (sequence == null) return null;
        String input = sequence.toString();

        // Replace new lines with the literal '\n'
        input = input.replaceAll("\\r|\\n|\\r\\n", "\\\\n");
        // Escape semicolons and commas
        input = input.replace(";", "\\;");
        input = input.replace(",", "\\,");

        return input;
    }

    /**
     * Creates an empty temporary file in the given directory using the given
     * prefix and suffix as part of the file name. If {@code suffix} is null, {@code .tmp} is used.
     *
     * <p>Note that this method does <i>not</i> call {@link File#deleteOnExit},
     * but see the documentation for that method before you call it manually.
     *
     * @param prefix
     *            the prefix to the temp file name.
     * @param suffix
     *            the suffix to the temp file name.
     * @param directory
     *            the location to which the temp file is to be written, or
     *            {@code null} for the default location for temporary files,
     *            which is taken from the "java.io.tmpdir" system property. It
     *            may be necessary to set this property to an existing, writable
     *            directory for this method to work properly.
     * @return the temporary file.
     * @throws IllegalArgumentException
     *             if the length of {@code prefix} is less than 3.
     * @throws IOException
     *             if an error occurs when writing the file.
     */
    public static File createTempFile(String prefix, String suffix, File directory)
            throws IOException {
        // Force a prefix null check first
        if (prefix.length() < 3) {
            throw new IllegalArgumentException("prefix must be at least 3 characters");
        }
        if (suffix == null) {
            suffix = ".tmp";
        }
        File result = null;
        try {
            result = File.createTempFile(prefix, suffix, directory);
        } catch (IOException ioe) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (ioe.getCause() instanceof ErrnoException) {
                    if (((ErrnoException) ioe.getCause()).errno == OsConstants.ENAMETOOLONG) {
                        // This is a recoverable error the file name was too long,
                        // lets go for a smaller file name
                        result = File.createTempFile(INVITE_FILE_NAME, suffix, directory);
                    }
                }
            }
        }
        return result;
    }

    public static VCalendar readCalendarFromFile(Context context, Uri uri) {
        ArrayList<String> contents = getStringArrayFromFile(context, uri);
        if (contents == null || contents.isEmpty()) {
            return null;
        }
        VCalendar calendar = new VCalendar();
        calendar.populateFromString(contents);
        return calendar;
    }

    public static ArrayList<String> getStringArrayFromFile(Context context, Uri uri) {
        String scheme = uri.getScheme();
        InputStream inputStream = null;
        if(ContentResolver.SCHEME_CONTENT.equals(scheme)) {
            try {
                inputStream = context.getContentResolver().openInputStream(uri);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else if (ContentResolver.SCHEME_FILE.equals(scheme)) {
            File f = new File(uri.getPath());
            try {
                inputStream = new FileInputStream(f);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        if (inputStream == null) {
            return null;
        }

        ArrayList<String> result = new ArrayList<>();

        try {
            // read file, removing RFC5545 line folding
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String previous = null;
            String line;
            while ((line = reader.readLine()) != null) {
                if (previous == null) {
                    previous = line;
                } else if (line.startsWith(" ") || line.startsWith("\t")) {
                    // line starts with space or tab, fold
                    previous = previous + line.substring(1);
                } else {
                    result.add(previous);
                    previous = line;
                }
            }
            if (previous != null) {
                result.add(previous);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * Stringify VCalendar object and write to file
     * @param calendar a VCalendar object
     * @param file the file to write it to
     * @return success status of the file write operation
     */
    public static boolean writeCalendarToFile(VCalendar calendar, File file) {
        if (calendar == null || file == null) return false;
        String icsFormattedString = calendar.getICalFormattedString();
        FileOutputStream outStream;
        try {
            outStream = new FileOutputStream(file);
            outStream.write(icsFormattedString.getBytes());
            outStream.close();
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    /**
     * RFC 5545 limits line length to 75 characters including the newline:
     * A long content line can be "folded" by inserting a newline followed by
     * a space (as many times as required). This sequence must be removed
     * again by any receiving decoder.
     * Note that this must *not* be called on an already folded text,
     * since it will treat the inserted newlines and spaces as real.
     * @param input A StringBuilder containing lines which may be too long
     * @return A StringBuilder whose content satisfies RFC5545 requirements
     */
    public static StringBuilder enforceICalLineLength(StringBuilder input) {
        final int sPermittedLineLength = 75; // Line length mandated by iCalendar format

        StringBuilder output = new StringBuilder();
        int length = input.length();

        boolean justHadNewline = false;
        int currentLineLength = 0;
        for (int i = 0; i < length; i++) {
            char currentChar = input.charAt(i);
            if (currentChar == '\n') {
                // A real newline
                output.append(currentChar);
                currentLineLength = 0; // Reset char counter
                justHadNewline = true;
            } else if (   (currentLineLength >= sPermittedLineLength)
                       || (   justHadNewline
                           && ((currentChar == ' ') || (currentChar == '\t'))))
            {
                // We need to break the line and insert a newline and a space,
                // either because we've reached the permitted line length
                // or because we have the tricky case where a real newline
                // is followed by a space or tab.
                // Since we don't want the receiving decoder to ignore a real newline,
                // we need to insert another newline and space to separate
                // the following space or tab from it.
                output.append("\n ");
                output.append(currentChar);
                currentLineLength = 2; // Already has 2 chars: space and currentChar
                justHadNewline = false;
            } else {
                // A non-newline char that can be part of the current line
                output.append(currentChar);
                currentLineLength++;
                justHadNewline = false;
            }
        }

        return output;
    }

    /**
     * Returns an iCalendar formatted UTC date-time
     * ex: 20141120T120000Z for noon on Nov 20, 2014
     *
     * @param millis in epoch time
     * @param timeZone indicates the time zone of the input epoch time
     * @return String containing iCalendar formatted UTC date-time
     */
    public static String getICalFormattedDateTime(long millis, String timeZone) {
        if (millis < 0) return null;

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(millis);

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dateTime = simpleDateFormat.format(calendar.getTime());

        // iCal UTC date format: <yyyyMMdd>T<HHmmss>Z
        return dateTime.subSequence(0, 8) + "T" + dateTime.substring(8) + "Z";
    }

    /**
     * Converts the time in a local time zone to UTC time
     * @param millis epoch time in the local timezone
     * @param localTimeZone string id of the local time zone
     * @return UTC time in a long
     */
    public static long convertTimeToUtc(long millis, String localTimeZone) {
        if (millis < 0) return 0;

        // Remove the local time zone's UTC offset
        return millis - TimeZone.getTimeZone(localTimeZone).getRawOffset();
    }

    /**
     * Copy the contents of a file into another
     *
     * @param src input / src file
     * @param dst file to be copied into
     */
    public static boolean copyFile(File src, File dst) {
        boolean isSuccessful = false;
        InputStream in = null;
        OutputStream out = null;
        try {
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);

            byte[] buf = new byte[1024];

            try {
                for (int len; (len = in.read(buf)) > 0; ) {
                    out.write(buf, 0, len);
                }
                isSuccessful = true;
            } catch (IOException e) {
                // Ignore
            }

        } catch (FileNotFoundException fnf) {
            // Ignore
        } finally {

            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // Ignore
                }
            }

            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        return isSuccessful;
    }
}
