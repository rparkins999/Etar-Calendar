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

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import ws.xsoh.etar.R;

/**
 * Creates a circular swatch of a specified color.  Adds a checkmark if marked as checked.
 */
@SuppressLint("ViewConstructor")
public class ColorPickerSwatch extends FrameLayout implements View.OnClickListener {
    private final int mColor;
    private final OnColorSelectedListener mOnColorSelectedListener;

    /**
     * Interface for a callback when a color square is selected.
     */
    public interface OnColorSelectedListener {

        /**
         * Called when a specific color square has been selected.
         */
        void onColorSelected(int color);
    }

    public ColorPickerSwatch(Context context, int color, boolean checked,
            OnColorSelectedListener listener) {
        super(context);
        mColor = color;
        mOnColorSelectedListener = listener;

        LayoutInflater.from(context).inflate(
            R.layout.color_picker_swatch, this);
        ImageView mSwatchImage = findViewById(R.id.color_picker_swatch);
        ImageView mCheckmarkImage = findViewById(R.id.color_picker_checkmark);
        Drawable[] colorDrawable = new Drawable[]
            {getContext().getResources().getDrawable(R.drawable.color_picker_swatch)};
        mSwatchImage.setImageDrawable(
            new ColorStateDrawable(colorDrawable, color));
        mCheckmarkImage.setVisibility(checked ? View.VISIBLE : View.GONE );
        setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (mOnColorSelectedListener != null) {
            mOnColorSelectedListener.onColorSelected(mColor);
        }
    }
}
