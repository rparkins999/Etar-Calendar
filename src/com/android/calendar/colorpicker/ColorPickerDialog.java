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

package com.android.calendar.colorpicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;

import ws.xsoh.etar.R;

import com.android.calendar.colorpicker.ColorPickerSwatch.OnColorSelectedListener;

/**
 * A dialog which takes in as input an array of colors and creates a palette allowing the user to
 * select a specific color swatch, which invokes a listener.
 */
public class ColorPickerDialog extends Dialog implements OnColorSelectedListener {

    public static final int SIZE_LARGE = 1;
    public static final int SIZE_SMALL = 2;

    protected AlertDialog mAlertDialog;

    protected static final String KEY_TITLE_ID = "title_id";
    protected static final String KEY_COLORS = "colors";
    protected static final String KEY_SELECTED_COLOR = "selected_color";
    protected static final String KEY_COLUMNS = "columns";
    protected static final String KEY_SIZE = "size";

    protected int mTitleResId = R.string.color_picker_default_title;
    protected int[] mColors = null;
    protected int mSelectedColor;
    protected int mColumns;
    protected int mSize;

    private ColorPickerPalette mPalette;
    private ProgressBar mProgress;

    protected OnColorSelectedListener mListener;

    @Override
    public void onColorSelected(int color) {
        if (mListener != null) {
            mListener.onColorSelected(color);
        }

        if (getOwnerActivity() instanceof OnColorSelectedListener) {
            final OnColorSelectedListener listener =
                (OnColorSelectedListener) getOwnerActivity();
            listener.onColorSelected(color);
        }

        if (color != mSelectedColor) {
            mSelectedColor = color;
            // Redraw palette to show checkmark on newly selected color before dismissing.
            mPalette.drawPalette(mColors, mSelectedColor);
        }

        dismiss();
    }

    /**
     * Creates a dialog window that uses the default dialog theme.
     * <p>
     * The supplied {@code context} is used to obtain the window manager and
     * base theme used to present the dialog.
     *
     * @param context the context in which the dialog should run
     */
    public ColorPickerDialog(@NonNull Context context) {
        super(context);
    }

    public static ColorPickerDialog newInstance(
        @NonNull Context context, int titleResId, int[] colors,
        int selectedColor, int columns, int size) {
        ColorPickerDialog ret = new ColorPickerDialog(context);
        ret.mTitleResId = titleResId;
        ret.setColors(colors, selectedColor);
        ret.mColumns = columns;
        ret.mSize = size;
        return ret;
    }

    public void initialize(int titleResId, int[] colors, int selectedColor, int columns, int size) {
        mTitleResId = titleResId;
        mColumns = columns;
        mSize = size;
        setColors(colors, selectedColor);
    }

    public void setOnColorSelectedListener(
        OnColorSelectedListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mTitleResId = savedInstanceState.getInt(KEY_TITLE_ID);
            mColors = savedInstanceState.getIntArray(KEY_COLORS);
            mSelectedColor = savedInstanceState.getInt(
                KEY_SELECTED_COLOR);
            mColumns = savedInstanceState.getInt(KEY_COLUMNS);
            mSize = savedInstanceState.getInt(KEY_SIZE);
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM);
        setContentView(R.layout.color_picker_dialog);
        mProgress = findViewById(android.R.id.progress);
        mPalette = findViewById(R.id.color_picker);
        mPalette.init(mSize, mColumns, this);
        if (mProgress != null && mPalette != null && mColors != null) {
            mProgress.setVisibility(View.GONE);
            refreshPalette();
            mPalette.setVisibility(View.VISIBLE);
        }
    }

    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getOwnerActivity();

        View view = LayoutInflater.from(getOwnerActivity()).inflate(R.layout.color_picker_dialog, null);

        if (mColors != null) {
            showPaletteView();
        }

        mAlertDialog = new AlertDialog.Builder(activity)
            .setTitle(mTitleResId)
            .setView(view)
            .create();

        return mAlertDialog;
    }

    public void showPaletteView() {
        if (mProgress != null && mPalette != null) {
            mProgress.setVisibility(View.GONE);
            refreshPalette();
            mPalette.setVisibility(View.VISIBLE);
        }
    }

    public void showProgressBarView() {
        if (mProgress != null && mPalette != null) {
            mProgress.setVisibility(View.VISIBLE);
            mPalette.setVisibility(View.GONE);
        }
    }

    public void setColors(int[] colors, int selectedColor) {
        if (mColors != colors || mSelectedColor != selectedColor) {
            mColors = colors;
            mSelectedColor = selectedColor;
            refreshPalette();
        }
    }

    public void setColors(int[] colors) {
        if (mColors != colors) {
            mColors = colors;
            refreshPalette();
        }
    }

    public void setSelectedColor(int color) {
        if (mSelectedColor != color) {
            mSelectedColor = color;
            refreshPalette();
        }
    }

    private void refreshPalette() {
        if (mPalette != null && mColors != null) {
            mPalette.drawPalette(mColors, mSelectedColor);
        }
    }

    public int[] getColors() {
        return mColors;
    }

    public int getSelectedColor() {
        return mSelectedColor;
    }

    @Override
    public Bundle onSaveInstanceState() {
        Bundle outState = super.onSaveInstanceState();
        outState.putInt(KEY_TITLE_ID, mTitleResId);
        outState.putIntArray(KEY_COLORS, mColors);
        outState.putInt(KEY_SELECTED_COLOR, mSelectedColor);
        outState.putInt(KEY_COLUMNS, mColumns);
        outState.putInt(KEY_SIZE, mSize);
        return outState;
    }
}
