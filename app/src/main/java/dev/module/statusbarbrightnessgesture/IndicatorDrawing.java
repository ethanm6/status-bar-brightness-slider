/*
 * Brightness Slider — status bar brightness gesture (LSPosed module).
 * Copyright (C) 2026 ethanm6
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Based on StatusBarBrightnessGesture by mbatthew (MIT); see LICENSE-MIT.
 */
package dev.module.statusbarbrightnessgesture;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;

/**
 * Shared drawing for the point-up droplet indicator, used by BOTH the live
 * settings preview and the real on-screen indicator so the two always match.
 * Geometry is derived from the text size, so a larger text size (preview) yields
 * a proportionally larger — but identically shaped — droplet.
 */
final class IndicatorDrawing {

    private IndicatorDrawing() {}

    // Shape tuning (ratios of the bulb radius). Adjust these to reshape the drop.
    // Modeled as a big bulb circle + a small rounded tip circle joined by tangents
    // (a proper rounded droplet), matching Super Status Bar's shape.
    static final float DROPLET_HEIGHT_FACTOR = 2.4f;  // total height ≈ this × bulb radius (shorter cone)
    static final float DROPLET_TIP_FACTOR    = 0.30f; // tip-circle radius ≈ this × bulb radius
    static final float DROPLET_TEXT_MARGIN_DP = 9f;   // padding of text inside the bulb

    /** Bulb radius (px) that fits "100%" at the given text size. */
    static float bulbRadius(float density, float textSizePx, Paint textPaint) {
        textPaint.setTextSize(textSizePx);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        float textW = textPaint.measureText("100%");
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textH = fm.bottom - fm.top;
        float margin = DROPLET_TEXT_MARGIN_DP * density;
        return Math.max(textW / 2f + margin, textH / 2f + margin);
    }

    /** Content width/height (px, excluding shadow padding) for the droplet. */
    static int dropletWidth(float r) { return Math.round(2 * r); }
    static int dropletHeight(float r) { return Math.round(DROPLET_HEIGHT_FACTOR * r); }

    /**
     * Draw a point-up droplet into the region [pad, pad, w-pad, h-pad] with the
     * value text centred in the bulb. Caller supplies reusable Paints.
     */
    static void drawDroplet(Canvas canvas, float w, float h, float pad,
                             int fillColor, int alpha255, int textColor, float textSizePx,
                             boolean shadow, float density, String text,
                             Paint fill, Paint textPaint) {
        float left = pad, right = w - pad, top = pad, bottom = h - pad;
        float R  = (right - left) / 2f;    // bulb radius = half the content width
        float rt = R * DROPLET_TIP_FACTOR; // rounded tip radius
        float cx = left + R;
        float cyB = bottom - R;            // bulb centre (near bottom)
        float cyT = top + rt;              // tip circle centre (near top)
        float D = cyB - cyT;               // distance between the two centres

        Path path = new Path();
        if (D > R - rt) {
            // External tangents between the bulb (R) and the tip circle (rt):
            // cos(theta) = (R - rt)/D, theta measured from the vertical centre line.
            float theta = (float) Math.acos((R - rt) / D);
            float s = (float) Math.sin(theta), c = (float) Math.cos(theta);
            float bRx = cx + R * s, bRy = cyB - R * c;   // bulb right tangent point
            float bLx = cx - R * s, bLy = cyB - R * c;   // bulb left  tangent point
            float tRx = cx + rt * s, tRy = cyT - rt * c; // tip right tangent point
            float tLx = cx - rt * s, tLy = cyT - rt * c; // tip left  tangent point
            RectF bulbOval = new RectF(cx - R, cyB - R, cx + R, cyB + R);
            RectF tipOval  = new RectF(cx - rt, cyT - rt, cx + rt, cyT + rt);

            float bR = (float) Math.toDegrees(Math.atan2(bRy - cyB, bRx - cx));
            float bL = (float) Math.toDegrees(Math.atan2(bLy - cyB, bLx - cx));
            float tR = (float) Math.toDegrees(Math.atan2(tRy - cyT, tRx - cx));
            float tL = (float) Math.toDegrees(Math.atan2(tLy - cyT, tLx - cx));

            path.moveTo(bRx, bRy);
            path.lineTo(tRx, tRy);
            path.arcTo(tipOval,  tR, sweepThrough(tR, tL, -90f));  // over the top
            path.lineTo(bLx, bLy);
            path.arcTo(bulbOval, bL, sweepThrough(bL, bR, 90f));   // around the bottom
            path.close();
        } else {
            path.addCircle(cx, cyB, R, Path.Direction.CW);
        }

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(fillColor);
        fill.setAlpha(alpha255);
        if (shadow) {
            int shadowAlpha = Math.round(0x66 * alpha255 / 255f);
            fill.setShadowLayer(8 * density, 0, 3 * density, shadowAlpha << 24);
        } else {
            fill.clearShadowLayer();
        }
        canvas.drawPath(path, fill);

        textPaint.setColor(textColor);
        textPaint.setAlpha(alpha255);
        textPaint.setTextSize(textSizePx);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, cx,
                cyB - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
    }

    /** Signed sweep (deg) from `from` to `to` that passes through `via`. */
    private static float sweepThrough(float from, float to, float via) {
        float pos = ((to - from) % 360f + 360f) % 360f;   // clockwise 0..360
        float viaOff = ((via - from) % 360f + 360f) % 360f;
        return viaOff <= pos ? pos : pos - 360f;           // else go counter-clockwise
    }
}
