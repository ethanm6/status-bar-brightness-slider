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

import android.content.Context;
import android.content.Intent;

public final class Prefs {

    public static final String KEY_GESTURE_ENABLED       = "sbbrightness_gesture_enabled";
    public static final String KEY_OVERLAY_ENABLED        = "sbbrightness_overlay_enabled";
    public static final String KEY_BLOCK_LONGPRESS_QS    = "sbbrightness_block_longpress_qs";
    public static final String KEY_FULLSCREEN_SWIPE      = "sbbrightness_fullscreen_swipe";
    public static final String KEY_HAPTIC_FEEDBACK       = "sbbrightness_haptic_feedback";
    public static final String KEY_SENSITIVITY           = "sbbrightness_sensitivity";
    public static final String KEY_EDGE_PADDING_DP       = "sbbrightness_edge_padding_dp";
    public static final String KEY_AUTO_BRIGHTNESS        = "sbbrightness_auto_brightness";
    public static final String KEY_SAVED_BRIGHTNESS       = "sbbrightness_saved_brightness";
    public static final String KEY_INDICATOR_Y_POSITION   = "sbbrightness_indicator_y_position";

    // Indicator appearance
    public static final String KEY_INDICATOR_SHAPE             = "sbbrightness_indicator_shape";
    public static final String KEY_INDICATOR_COLOR_MODE        = "sbbrightness_indicator_color_mode";
    public static final String KEY_INDICATOR_CUSTOM_COLOR      = "sbbrightness_indicator_custom_color";
    public static final String KEY_INDICATOR_ALPHA             = "sbbrightness_indicator_alpha";
    public static final String KEY_INDICATOR_TEXT_COLOR_MODE   = "sbbrightness_indicator_text_color_mode";
    public static final String KEY_INDICATOR_TEXT_CUSTOM_COLOR = "sbbrightness_indicator_text_custom_color";
    public static final String KEY_INDICATOR_SHADOW            = "sbbrightness_indicator_shadow";
    public static final String KEY_REVERSE_SLIDER               = "sbbrightness_reverse_slider";

    public static final String ACTION_PREFS_CHANGED =
            "dev.module.statusbarbrightnessgesture.PREFS_CHANGED";

    // Sent by the app to ask the hook (running in SystemUI, which holds
    // WRITE_SECURE_SETTINGS) to persist a single pref. The app itself never writes
    // Settings.Secure, so it needs no permission grant.
    public static final String ACTION_SET_PREF =
            "dev.module.statusbarbrightnessgesture.SET_PREF";
    public static final String EXTRA_KEY   = "key";
    public static final String EXTRA_VALUE = "value";

    public static final int DEFAULT_GESTURE_ENABLED      = 1;
    public static final int DEFAULT_OVERLAY_ENABLED       = 1;
    public static final int DEFAULT_BLOCK_LONGPRESS_QS   = 0;
    public static final int DEFAULT_FULLSCREEN_SWIPE     = 0;
    public static final int DEFAULT_HAPTIC_FEEDBACK      = 0;
    public static final int DEFAULT_SENSITIVITY          = 7;
    public static final int DEFAULT_EDGE_PADDING_DP      = 0;
    public static final int DEFAULT_AUTO_BRIGHTNESS      = 0;
    public static final int DEFAULT_INDICATOR_Y_POSITION = 9;
    public static final int INDICATOR_Y_POSITION_MAX     = 50;

    public static final int DEFAULT_INDICATOR_SHAPE             = 0;
    public static final int DEFAULT_INDICATOR_COLOR_MODE        = 0;
    public static final int DEFAULT_INDICATOR_CUSTOM_COLOR      = 0xFF6650A4;
    public static final int DEFAULT_INDICATOR_ALPHA             = 100;
    public static final int DEFAULT_INDICATOR_TEXT_COLOR_MODE   = 0;
    public static final int DEFAULT_INDICATOR_TEXT_CUSTOM_COLOR = 0xFFFFFFFF;
    public static final int DEFAULT_INDICATOR_SHADOW            = 0;
    public static final int DEFAULT_REVERSE_SLIDER               = 0;

    public static final int SENSITIVITY_MIN    = 1;
    public static final int SENSITIVITY_MAX    = 10;
    public static final int EDGE_PADDING_MAX_DP = 64;

    // Shape constants
    public static final int INDICATOR_SHAPE_PILL     = 0;
    public static final int INDICATOR_SHAPE_TEARDROP = 1;

    // Text color mode constants
    public static final int TEXT_COLOR_MODE_AUTO         = 0;
    public static final int TEXT_COLOR_MODE_WHITE        = 1;
    public static final int TEXT_COLOR_MODE_BLACK        = 2;
    public static final int TEXT_COLOR_MODE_CUSTOM       = 3;
    public static final int TEXT_COLOR_MODE_ACCENT_LIGHT = 4;
    public static final int TEXT_COLOR_MODE_ACCENT       = 5;
    public static final int TEXT_COLOR_MODE_ACCENT_DARK  = 6;
    public static final int TEXT_COLOR_MODE_TERTIARY     = 7;
    public static final int TEXT_COLOR_MODE_NEUTRAL      = 8;

    // Color mode constants
    public static final int COLOR_MODE_ACCENT    = 0;
    public static final int COLOR_MODE_SECONDARY = 1;
    public static final int COLOR_MODE_TERTIARY  = 2;
    public static final int COLOR_MODE_WHITE     = 3;
    public static final int COLOR_MODE_BLACK     = 4;
    public static final int COLOR_MODE_CUSTOM      = 5;
    public static final int COLOR_MODE_NEUTRAL      = 6;
    public static final int COLOR_MODE_NEUTRAL_VAR  = 7;
    public static final int COLOR_MODE_ACCENT_LIGHT = 8;
    public static final int COLOR_MODE_ACCENT_DARK  = 9;

    /**
     * Ask the hook to persist one pref value to Settings.Secure and re-apply.
     * The app has no WRITE_SECURE_SETTINGS grant; the hook (in SystemUI) writes it.
     */
    public static void setPref(Context ctx, String key, int value) {
        Intent i = new Intent(ACTION_SET_PREF);
        i.setPackage("com.android.systemui");
        i.putExtra(EXTRA_KEY, key);
        i.putExtra(EXTRA_VALUE, value);
        ctx.sendBroadcast(i);
    }

    /** Nudge SystemUI to reload all prefs from Settings.Secure and re-apply. */
    public static void sendAll(Context ctx) {
        Intent i = new Intent(ACTION_PREFS_CHANGED);
        i.setPackage("com.android.systemui");
        ctx.sendBroadcast(i);
    }

    private Prefs() {}
}
