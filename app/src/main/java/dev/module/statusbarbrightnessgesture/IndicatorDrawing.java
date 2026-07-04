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

    /**
     * Draw a plain circle into the region [pad, pad, w-pad, h-pad] with the value
     * text centred. Same bulb sizing as the droplet, so "100%" always fits.
     */
    static void drawCircle(Canvas canvas, float w, float h, float pad,
                            int fillColor, int alpha255, int textColor, float textSizePx,
                            boolean shadow, float density, String text,
                            Paint fill, Paint textPaint) {
        float left = pad, right = w - pad, top = pad, bottom = h - pad;
        float R  = (right - left) / 2f;
        float cx = left + R;
        float cy = top + (bottom - top) / 2f;

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(fillColor);
        fill.setAlpha(alpha255);
        if (shadow) {
            int shadowAlpha = Math.round(0x66 * alpha255 / 255f);
            fill.setShadowLayer(8 * density, 0, 3 * density, shadowAlpha << 24);
        } else {
            fill.clearShadowLayer();
        }
        canvas.drawCircle(cx, cy, R, fill);

        textPaint.setColor(textColor);
        textPaint.setAlpha(alpha255);
        textPaint.setTextSize(textSizePx);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, cx,
                cy - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
    }

    // Rounded five-point star, traced verbatim from the PT (Brazil) 2021 logo SVG
    // and normalized to a unit width. Point up, bezier-rounded tips and valleys.
    static final float STAR_HEIGHT_FACTOR  = 0.956435f; // height = this × width
    static final float STAR_CENTER_Y       = 0.52573f;  // circumcentre y = this × width
    static final float STAR_TEXT_MARGIN_DP = 3f;        // clearance of the text corners to the star edges

    /**
     * Content width (px, excluding shadow padding) whose star fits "100%".
     * Sized so the text-rect corners clear the star's arm edges (the binding
     * ones are the lower-arm edges near the bottom valleys), which allows a
     * much smaller star than fitting the whole inscribed circle.
     */
    static float starContentWidth(float density, float textSizePx, Paint textPaint) {
        textPaint.setTextSize(textSizePx);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        float wHalf = textPaint.measureText("100%") / 2f + STAR_TEXT_MARGIN_DP * density;
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float hHalf = (fm.bottom - fm.top) / 2f;
        // Edge-line normals of the unit-width star (text centred on the circumcentre):
        // bottom corners vs. valley→bottom-tip edges, top corners vs. tip→valley edges.
        float s1 = (0.95106f * wHalf - 0.30902f * hHalf) / 0.20008f;
        float s2 = (0.58778f * wHalf - 0.80900f * hHalf) / 0.19688f;
        return Math.max(s1, s2);
    }

    static int starWidth(float s)  { return Math.round(s); }
    static int starHeight(float s) { return Math.round(s * STAR_HEIGHT_FACTOR); }

    /**
     * Draw the rounded star into the region [pad, pad, w-pad, h-pad] with the
     * value text centred on the star's circumcentre.
     */
    static void drawStar(Canvas canvas, float w, float h, float pad,
                          int fillColor, int alpha255, int textColor, float textSizePx,
                          boolean shadow, float density, String text,
                          Paint fill, Paint textPaint) {
        float s = w - 2 * pad;   // content width; height is s × STAR_HEIGHT_FACTOR
        float ox = pad, oy = pad;

        Path p = new Path();
        p.moveTo(ox + 0.55179f*s, oy + 0.03763f*s);
        p.lineTo(ox + 0.63249f*s, oy + 0.28601f*s);
        p.cubicTo(ox + 0.63978f*s, oy + 0.30845f*s,
                  ox + 0.66069f*s, oy + 0.32364f*s,
                  ox + 0.68428f*s, oy + 0.32364f*s);
        p.lineTo(ox + 0.94545f*s, oy + 0.32364f*s);
        p.cubicTo(ox + 0.99819f*s, oy + 0.32364f*s,
                  ox + 1.02012f*s, oy + 0.39113f*s,
                  ox + 0.97745f*s, oy + 0.42214f*s);
        p.lineTo(ox + 0.76616f*s, oy + 0.57565f*s);
        p.cubicTo(ox + 0.74708f*s, oy + 0.58952f*s,
                  ox + 0.73909f*s, oy + 0.61409f*s,
                  ox + 0.74638f*s, oy + 0.63652f*s);
        p.lineTo(ox + 0.82709f*s, oy + 0.88491f*s);
        p.cubicTo(ox + 0.84338f*s, oy + 0.93508f*s,
                  ox + 0.78597f*s, oy + 0.97679f*s,
                  ox + 0.74330f*s, oy + 0.94579f*s);
        p.lineTo(ox + 0.53201f*s, oy + 0.79228f*s);
        p.cubicTo(ox + 0.51292f*s, oy + 0.77841f*s,
                  ox + 0.48708f*s, oy + 0.77841f*s,
                  ox + 0.46799f*s, oy + 0.79228f*s);
        p.lineTo(ox + 0.25670f*s, oy + 0.94579f*s);
        p.cubicTo(ox + 0.21403f*s, oy + 0.97679f*s,
                  ox + 0.15661f*s, oy + 0.93508f*s,
                  ox + 0.17291f*s, oy + 0.88491f*s);
        p.lineTo(ox + 0.25362f*s, oy + 0.63652f*s);
        p.cubicTo(ox + 0.26091f*s, oy + 0.61409f*s,
                  ox + 0.25292f*s, oy + 0.58952f*s,
                  ox + 0.23384f*s, oy + 0.57565f*s);
        p.lineTo(ox + 0.02255f*s, oy + 0.42214f*s);
        p.cubicTo(ox + -0.02012f*s, oy + 0.39113f*s,
                  ox + 0.00181f*s, oy + 0.32364f*s,
                  ox + 0.05455f*s, oy + 0.32364f*s);
        p.lineTo(ox + 0.31572f*s, oy + 0.32364f*s);
        p.cubicTo(ox + 0.33931f*s, oy + 0.32364f*s,
                  ox + 0.36022f*s, oy + 0.30845f*s,
                  ox + 0.36751f*s, oy + 0.28601f*s);
        p.lineTo(ox + 0.44821f*s, oy + 0.03763f*s);
        p.cubicTo(ox + 0.46451f*s, oy + -0.01254f*s,
                  ox + 0.53549f*s, oy + -0.01254f*s,
                  ox + 0.55179f*s, oy + 0.03763f*s);
        p.close();

        fill.setStyle(Paint.Style.FILL);
        fill.setColor(fillColor);
        fill.setAlpha(alpha255);
        if (shadow) {
            int shadowAlpha = Math.round(0x66 * alpha255 / 255f);
            fill.setShadowLayer(8 * density, 0, 3 * density, shadowAlpha << 24);
        } else {
            fill.clearShadowLayer();
        }
        canvas.drawPath(p, fill);

        textPaint.setColor(textColor);
        textPaint.setAlpha(alpha255);
        textPaint.setTextSize(textSizePx);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(text, ox + 0.5f * s,
                oy + STAR_CENTER_Y * s
                        - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint);
    }

    /** Signed sweep (deg) from `from` to `to` that passes through `via`. */
    private static float sweepThrough(float from, float to, float via) {
        float pos = ((to - from) % 360f + 360f) % 360f;   // clockwise 0..360
        float viaOff = ((via - from) % 360f + 360f) % 360f;
        return viaOff <= pos ? pos : pos - 360f;           // else go counter-clockwise
    }
}
