/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.ex.chips;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.FontMetricsInt;
import android.graphics.Rect;
import android.text.TextUtils;
import android.text.style.ReplacementSpan;

/**
 * RecipientChip defines a span that contains information relevant to a
 * particular recipient.
 */
/* package */class InvisibleRecipientChip extends ReplacementSpan implements RecipientChip {
    private final CharSequence mDisplay;

    private final CharSequence mValue;

    private final long mContactId;

    private final long mDataId;

    private RecipientEntry mEntry;

    private boolean mSelected = false;

    private CharSequence mOriginalText;

    /** <code>true</code> to display the original text, <code>false</code> to display nothing */
    private boolean mDisplayOriginalText = false;

    public InvisibleRecipientChip(RecipientEntry entry, int offset) {
        super();
        mDisplay = entry.getDisplayName();
        mValue = entry.getDestination().trim();
        mContactId = entry.getContactId();
        mDataId = entry.getDataId();
        mEntry = entry;
    }

    /**
     * Set the selected state of the chip.
     * @param selected
     */
    public void setSelected(boolean selected) {
        mSelected = selected;
    }

    /**
     * Return true if the chip is selected.
     */
    public boolean isSelected() {
        return mSelected;
    }

    /**
     * Get the text displayed in the chip.
     */
    public CharSequence getDisplay() {
        return mDisplay;
    }

    /**
     * Get the text value this chip represents.
     */
    public CharSequence getValue() {
        return mValue;
    }

    /**
     * Get the id of the contact associated with this chip.
     */
    public long getContactId() {
        return mContactId;
    }

    /**
     * Get the id of the data associated with this chip.
     */
    public long getDataId() {
        return mDataId;
    }

    /**
     * Get associated RecipientEntry.
     */
    public RecipientEntry getEntry() {
        return mEntry;
    }

    public void setOriginalText(String text) {
        if (!TextUtils.isEmpty(text)) {
            text = text.trim();
        }
        mOriginalText = text;
    }

    public CharSequence getOriginalText() {
        return !TextUtils.isEmpty(mOriginalText) ? mOriginalText : mEntry.getDestination();
    }

    public void setDisplayOriginalText(final boolean displayOriginalText) {
        mDisplayOriginalText = displayOriginalText;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y,
            int bottom, Paint paint) {
        if (mDisplayOriginalText) {
            canvas.drawText(text, start, end, x, y, paint);
        } else {
            // Do nothing.
        }
    }

    @Override
    public int getSize(
            Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (mDisplayOriginalText) {
            return (int) paint.measureText(text, start, end);
        } else {
            return 0;
        }
    }

    @Override
    public Rect getBounds() {
        return new Rect(0, 0, 0, 0);
    }

    @Override
    public void draw(Canvas canvas) {
        // do nothing.
    }
}
