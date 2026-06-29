package dev.module.statusbarbrightnessgesture;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

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

    public static final String ACTION_PREFS_CHANGED =
            "dev.module.statusbarbrightnessgesture.PREFS_CHANGED";

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

    /** Broadcast all prefs to SystemUI. Called from both settings screens. */
    public static void sendAll(Context ctx) {
        Intent i = new Intent(ACTION_PREFS_CHANGED);
        i.setPackage("com.android.systemui");
        android.content.ContentResolver cr = ctx.getContentResolver();
        i.putExtra(KEY_GESTURE_ENABLED,
                Settings.Secure.getInt(cr, KEY_GESTURE_ENABLED, DEFAULT_GESTURE_ENABLED) == 1);
        i.putExtra(KEY_OVERLAY_ENABLED,
                Settings.Secure.getInt(cr, KEY_OVERLAY_ENABLED, DEFAULT_OVERLAY_ENABLED) == 1);
        i.putExtra(KEY_BLOCK_LONGPRESS_QS,
                Settings.Secure.getInt(cr, KEY_BLOCK_LONGPRESS_QS, DEFAULT_BLOCK_LONGPRESS_QS) == 1);
        i.putExtra(KEY_FULLSCREEN_SWIPE,
                Settings.Secure.getInt(cr, KEY_FULLSCREEN_SWIPE, DEFAULT_FULLSCREEN_SWIPE) == 1);
        i.putExtra(KEY_HAPTIC_FEEDBACK,
                Settings.Secure.getInt(cr, KEY_HAPTIC_FEEDBACK, DEFAULT_HAPTIC_FEEDBACK) == 1);
        i.putExtra(KEY_SENSITIVITY,
                Settings.Secure.getInt(cr, KEY_SENSITIVITY, DEFAULT_SENSITIVITY));
        i.putExtra(KEY_EDGE_PADDING_DP,
                Settings.Secure.getInt(cr, KEY_EDGE_PADDING_DP, DEFAULT_EDGE_PADDING_DP));
        i.putExtra(KEY_INDICATOR_SHAPE,
                Settings.Secure.getInt(cr, KEY_INDICATOR_SHAPE, DEFAULT_INDICATOR_SHAPE));
        i.putExtra(KEY_INDICATOR_COLOR_MODE,
                Settings.Secure.getInt(cr, KEY_INDICATOR_COLOR_MODE, DEFAULT_INDICATOR_COLOR_MODE));
        i.putExtra(KEY_INDICATOR_CUSTOM_COLOR,
                Settings.Secure.getInt(cr, KEY_INDICATOR_CUSTOM_COLOR, DEFAULT_INDICATOR_CUSTOM_COLOR));
        i.putExtra(KEY_INDICATOR_ALPHA,
                Settings.Secure.getInt(cr, KEY_INDICATOR_ALPHA, DEFAULT_INDICATOR_ALPHA));
        i.putExtra(KEY_INDICATOR_TEXT_COLOR_MODE,
                Settings.Secure.getInt(cr, KEY_INDICATOR_TEXT_COLOR_MODE, DEFAULT_INDICATOR_TEXT_COLOR_MODE));
        i.putExtra(KEY_INDICATOR_TEXT_CUSTOM_COLOR,
                Settings.Secure.getInt(cr, KEY_INDICATOR_TEXT_CUSTOM_COLOR, DEFAULT_INDICATOR_TEXT_CUSTOM_COLOR));
        i.putExtra(KEY_AUTO_BRIGHTNESS,
                Settings.Secure.getInt(cr, KEY_AUTO_BRIGHTNESS, DEFAULT_AUTO_BRIGHTNESS) == 1);
        i.putExtra(KEY_INDICATOR_Y_POSITION,
                Settings.Secure.getInt(cr, KEY_INDICATOR_Y_POSITION, DEFAULT_INDICATOR_Y_POSITION));
        ctx.sendBroadcast(i);
    }

    private Prefs() {}
}
