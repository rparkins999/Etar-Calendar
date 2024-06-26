package com.android.calendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CalendarContract;
import android.text.TextUtils;
import android.widget.Toast;
import com.android.calendar.event.EditEventActivity;
import com.android.calendar.icalendar.Attendee;
import com.android.calendar.icalendar.IcalendarUtils;
import com.android.calendar.icalendar.VCalendar;
import com.android.calendar.icalendar.VEvent;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;

import ws.xsoh.etar.R;

public class ImportActivity extends Activity {

    LinkedList<CalendarEventModel> mEvents;
    public static final File EXPORT_SDCARD_DIRECTORY = new File(
        Environment.getExternalStorageDirectory(), "CalendarEvents");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!isValidIntent()) {
            Toast.makeText(this, R.string.cal_nothing_to_import, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            parseCalFile();
        }
    }

    private long getLocalTimeFromString(String iCalDate, String iCalDateParam) {
        // see https://tools.ietf.org/html/rfc5545#section-3.3.5

        // FORM #2: DATE WITH UTC TIME, e.g. 19980119T070000Z
        if (iCalDate.endsWith("Z")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
            format.setTimeZone(TimeZone.getTimeZone("UTC"));

            try {
                format.parse(iCalDate);
                format.setTimeZone(TimeZone.getDefault());
                return format.getCalendar().getTimeInMillis();
            } catch (ParseException e) { }
        }

        // FORM #3: DATE WITH LOCAL TIME AND TIME ZONE REFERENCE, e.g. TZID=America/New_York:19980119T020000
        else if (iCalDateParam != null && iCalDateParam.startsWith("TZID=")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
            String timeZone = iCalDateParam.substring(5).replace("\"", "");
            // This is a pretty hacky workaround to prevent exact parsing of VTimezones.
            // It assumes the TZID to be refered to with one of the names recognizable by Java.
            // (which are quite a lot, see e.g. http://tutorials.jenkov.com/java-date-time/java-util-timezone.html)
            if (Arrays.asList(TimeZone.getAvailableIDs()).contains(timeZone)) {
                format.setTimeZone(TimeZone.getTimeZone(timeZone));
            }
            else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    String convertedTimeZoneId = android.icu.util.TimeZone
                            .getIDForWindowsID(timeZone, "001");
                    if (convertedTimeZoneId != null && !convertedTimeZoneId.equals("")) {
                        format.setTimeZone(TimeZone.getTimeZone(convertedTimeZoneId));
                    }
                    else {
                        format.setTimeZone(TimeZone.getDefault());
                        Toast.makeText(
                                this,
                                getString(R.string.cal_import_error_time_zone_msg, timeZone),
                                Toast.LENGTH_SHORT).show();
                    }
                }
                else {
                    format.setTimeZone(TimeZone.getDefault());
                    Toast.makeText(
                            this,
                            getString(R.string.cal_import_error_time_zone_msg, timeZone),
                            Toast.LENGTH_SHORT).show();
                }
            }
            try {
                format.parse(iCalDate);
                return format.getCalendar().getTimeInMillis();
            } catch (ParseException e) {  }
        }

        // ONLY DATE, e.g. 20190415
        else if (iCalDateParam != null && iCalDateParam.equals("VALUE=DATE")) {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
            format.setTimeZone(TimeZone.getDefault());

            try {
                format.parse(iCalDate);
                return format.getCalendar().getTimeInMillis();
            } catch (ParseException e) {
            }
        }

        // FORM #1: DATE WITH LOCAL TIME, e.g. 19980118T230000
        else {
            SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
            format.setTimeZone(TimeZone.getDefault());

            try {
                format.parse(iCalDate);
                return format.getCalendar().getTimeInMillis();
            } catch (ParseException e) {
            }
        }

        Toast.makeText(this, getString(R.string.cal_import_error_date_msg, iCalDate), Toast.LENGTH_SHORT).show();

        return System.currentTimeMillis();
    }

    private void showErrorToast() {
        Toast.makeText(
            this, R.string.cal_import_error_msg, Toast.LENGTH_SHORT).show();
        finish();
    }

    //FIXME should really do this in background task
    private void parseCalFile() {
        Uri uri = getIntent().getData();
        VCalendar calendar = IcalendarUtils.readCalendarFromFile(this, uri);

        if (calendar == null) {
            showErrorToast();
        }

        if (CalendarApplication.mEvents.size() == 0) {
            showErrorToast();
        }

        if (CalendarApplication.mEvents.size() > 1) {
            // For the time being we don't handle more than one VEVENT
            showErrorToast();
        }

        Intent intent = new Intent(this, EditEventActivity.class);

        // FIXME move this to VEVENT decoding
        /* Check if some special property which say it is a "All-Day" event.
        String microsoft_all_day_event =
            firstEvent.getProperty("X-MICROSOFT-CDO-ALLDAYEVENT");
         */

        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // This can't really happen
        } finally {
            finish();
        }
    }

    private boolean isTimeStartOfDay(String dtStart, String dtStartParam) {
        // convert to epoch milli seconds
        long timeStamp = getLocalTimeFromString(dtStart, dtStartParam);
        Date date = new Date(timeStamp);

        DateFormat dateFormat = new SimpleDateFormat("HH:mm");
        String dateStr = dateFormat.format(date);
        if (dateStr.equals("00:00")) {
            return true;
        }
        return false;
    }

    private boolean isValidIntent() {
        Intent intent = getIntent();
        if (intent == null) {
            return false;
        }
        Uri fileUri = intent.getData();
        if (fileUri == null) {
            return false;
        }
        String scheme = fileUri.getScheme();
        return ContentResolver.SCHEME_CONTENT.equals(scheme)
                || ContentResolver.SCHEME_FILE.equals(scheme);
    }

    private static class ListFilesTask extends AsyncTask<Void, Void, String[]> {
        private final Context mContext;

        public ListFilesTask(Context context) {
            mContext = context;
        }

        @Override
        protected String[] doInBackground(Void... params) {
            if (!hasThingsToImport()) {
                return null;
            }
            File folder = EXPORT_SDCARD_DIRECTORY;
            String[] result = null;
            if (folder.exists()) {
                result = folder.list();
            }
            return result;
        }

        @Override
        protected void onPostExecute(final String[] files) {
            if (files == null || files.length == 0) {
                Toast.makeText(mContext, R.string.cal_nothing_to_import,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            AlertDialog.Builder builder =
                new AlertDialog.Builder(mContext);
            builder.setTitle(R.string.cal_pick_ics)
                    .setItems(files, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Intent i = new Intent(mContext, ImportActivity.class);
                            File f = new File(EXPORT_SDCARD_DIRECTORY,
                                    files[which]);
                            i.setData(Uri.fromFile(f));
                            mContext.startActivity(i);
                        }
                    });
            builder.show();
        }
    }

    public static void pickImportFile(Context context) {
        new ListFilesTask(context).execute();
    }

    public static boolean hasThingsToImport() {
        File[] files = EXPORT_SDCARD_DIRECTORY.listFiles();
        return files != null && files.length > 0;
    }
}
