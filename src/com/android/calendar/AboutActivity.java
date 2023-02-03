/*
 * Original version Copyright (C) 2020 Dominik Schürmann <dominik@schuermann.eu>
 * Integration of fragment into Activity Copyright (C) Richard Parkins 2022
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.android.calendar;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import ws.xsoh.etar.R;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        new DynamicTheme().onCreate(this);
        setContentView(R.layout.simple_frame_layout_material);
        Toolbar myToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        ((ViewGroup)(findViewById(R.id.body_frame))).addView(
            getLayoutInflater().inflate(R.layout.about, null));
        String buildTime = getString(R.string.build_time);
        try
        {
            PackageInfo pi = getPackageManager().getPackageInfo(
                getPackageName(), 0);
            ((TextView) findViewById(R.id.version)).setText(
                getString(R.string.standalone_app_label)
                .concat(" ")
                .concat(pi.versionName)
                .concat(getString(R.string.preferences_build_time))
                .concat(buildTime)
            );
        }
        catch (PackageManager.NameNotFoundException ignore) {}
        ((TextView) findViewById(R.id.committed)).setText(
            getString(R.string.build_git1) + "\n" +
            getString(R.string.build_git2) + "\n" +
            getString(R.string.build_git3));
        findViewById(R.id.authorsLayout).setOnClickListener(
            new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.app_authors_url))));
                }
            });
        findViewById(R.id.licenseLayout).setOnClickListener(
            new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.app_license_url))));
                }
            });
        findViewById(R.id.source).setOnClickListener(
            new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.app_source_url))));
                }
            });
        findViewById(R.id.changelog).setOnClickListener(
            new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.app_changelog))));
                }
            });
        findViewById(R.id.issues).setOnClickListener(
            new View.OnClickListener() {
                public void onClick(View v) {
                    startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.app_issues_url))));
                }
            });
        String year = buildTime.substring(buildTime.length() - 4);
        ((TextView) findViewById(R.id.copyright)).setText(
            getString(R.string.app_copyright, year, year, year));
    }

    /**
     * This method is called whenever the user chooses to navigate Up within your application's
     * activity hierarchy from the action bar.
     *
     * @return true if Up navigation completed successfully and this Activity was finished,
     * false otherwise.
     */
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
