package dev.module.statusbarbrightnessgesture;

import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

public class SettingsActivity extends AppCompatActivity {

    private int colBackground;
    private int colSurfaceContainer;
    private int colOnSurface;
    private int colOnSurfaceVariant;
    private int colPrimary;
    private int colOutlineVariant;

    private MaterialSwitch mGestureSwitch;
    private MaterialSwitch mAutoSwitch;
    private ContentObserver mSwitchObserver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        resolveColours();

        float dp = getResources().getDisplayMetrics().density;

        // ── Root scroll ───────────────────────────────────────────────────────
        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setBackgroundColor(colBackground);
        setContentView(scroll);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ViewCompat.setOnApplyWindowInsetsListener(scroll, (v, insets) -> {
            int top    = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            root.setPadding(0, top, 0, bottom + (int)(24 * dp));
            return WindowInsetsCompat.CONSUMED;
        });

        // ── Hero ──────────────────────────────────────────────────────────────
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding((int)(24*dp), (int)(24*dp), (int)(24*dp), (int)(8*dp));

        TextView title = new TextView(this);
        title.setText("Brightness\nGesture");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40);
        title.setTextColor(colOnSurface);
        title.setLineSpacing(0, 1.05f);
        hero.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Swipe horizontally on the status bar to adjust brightness");
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setTextColor(colOnSurfaceVariant);
        subtitle.setPadding(0, (int)(10*dp), 0, 0);
        hero.addView(subtitle);

        root.addView(hero, matchWidth());

        // ── Content ───────────────────────────────────────────────────────────
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int)(16*dp), (int)(8*dp), (int)(16*dp), 0);

        // Primary toggle — standalone card
        buildCard(content, dp, card -> {
            mGestureSwitch = addSwitch(card, dp, "Enable gesture",
                    "Swipe left to dim, right to brighten",
                    Prefs.KEY_GESTURE_ENABLED, Prefs.DEFAULT_GESTURE_ENABLED,
                    enabled -> {
                        if (enabled) {
                            try { Settings.Secure.putInt(getContentResolver(),
                                    Prefs.KEY_AUTO_BRIGHTNESS, 0); }
                            catch (SecurityException ignored) {}
                            if (mAutoSwitch != null) mAutoSwitch.setChecked(false);
                        }
                        sendPrefs();
                    });
            addDivider(card, dp);
            mAutoSwitch = addSwitch(card, dp, "Auto brightness mode",
                    "Enable system auto-brightness and pause gesture control",
                    Prefs.KEY_AUTO_BRIGHTNESS, Prefs.DEFAULT_AUTO_BRIGHTNESS,
                    enabled -> {
                        try { Settings.Secure.putInt(getContentResolver(),
                                Prefs.KEY_GESTURE_ENABLED, enabled ? 0 : 1); }
                        catch (SecurityException ignored) {}
                        if (mGestureSwitch != null) mGestureSwitch.setChecked(!enabled);
                        sendPrefs();
                    });
        });

        // Display
        boolean overlayOn = Settings.Secure.getInt(getContentResolver(),
                Prefs.KEY_OVERLAY_ENABLED, Prefs.DEFAULT_OVERLAY_ENABLED) == 1;
        final View[] appearanceRow = {null};
        final View[] posSlider = {null};
        addSectionLabel(content, dp, "Display");
        buildCard(content, dp, card -> {
            addSwitch(card, dp, "Brightness indicator",
                    "Shows brightness % while swiping",
                    Prefs.KEY_OVERLAY_ENABLED, Prefs.DEFAULT_OVERLAY_ENABLED,
                    enabled -> {
                        setNavRowEnabled(appearanceRow[0], enabled);
                        setNavRowEnabled(posSlider[0], enabled);
                    });
            addDivider(card, dp);
            appearanceRow[0] = addNavRow(card, dp, "Indicator appearance", "Shape and color",
                    () -> startActivity(new Intent(this, IndicatorAppearanceActivity.class)));
            addDivider(card, dp);
            posSlider[0] = addSlider(card, dp, "Indicator vertical position",
                    "Distance from the top of the screen",
                    Prefs.KEY_INDICATOR_Y_POSITION, Prefs.DEFAULT_INDICATOR_Y_POSITION,
                    0, Prefs.INDICATOR_Y_POSITION_MAX, "%", 1);
            addDivider(card, dp);
            addSwitch(card, dp, "Vibrate on gesture start",
                    "Brief haptic pulse when the swipe is recognised",
                    Prefs.KEY_HAPTIC_FEEDBACK, Prefs.DEFAULT_HAPTIC_FEEDBACK);
        });
        setNavRowEnabled(appearanceRow[0], overlayOn);
        setNavRowEnabled(posSlider[0], overlayOn);

        // Gesture
        addSectionLabel(content, dp, "Gesture");
        buildCard(content, dp, card -> {
            addSlider(card, dp, "Activation sensitivity",
                    "Higher = shorter swipe needed; lower = less likely to fire when pulling the shade",
                    Prefs.KEY_SENSITIVITY, Prefs.DEFAULT_SENSITIVITY,
                    Prefs.SENSITIVITY_MIN, Prefs.SENSITIVITY_MAX, "", 1);
            addDivider(card, dp);
            addSlider(card, dp, "Rounded-corner padding",
                    "Insets the range so 0% and 100% are reachable without reaching the curved screen edges",
                    Prefs.KEY_EDGE_PADDING_DP, Prefs.DEFAULT_EDGE_PADDING_DP,
                    0, Prefs.EDGE_PADDING_MAX_DP, " dp", 0);
        });

        // Advanced
        addSectionLabel(content, dp, "Advanced");
        buildCard(content, dp, card -> {
            addSwitch(card, dp, "Block long-press Quick Settings",
                    "Prevents holding the status bar from opening Quick Settings",
                    Prefs.KEY_BLOCK_LONGPRESS_QS, Prefs.DEFAULT_BLOCK_LONGPRESS_QS);
            addDivider(card, dp);
            addSwitch(card, dp, "Fullscreen swipe",
                    "Adjust brightness at the top of the screen in fullscreen apps",
                    Prefs.KEY_FULLSCREEN_SWIPE, Prefs.DEFAULT_FULLSCREEN_SWIPE);
        });

        // Footer
        TextView footer = new TextView(this);
        footer.setText("Changes take effect immediately — no reboot needed");
        footer.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        footer.setTextColor(colOnSurfaceVariant);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, (int)(20*dp), 0, 0);
        content.addView(footer, matchWidth());

        root.addView(content, matchWidth());
    }

    // ── Card ──────────────────────────────────────────────────────────────────

    interface CardFill { void fill(LinearLayout inner); }

    private void buildCard(LinearLayout parent, float dp, CardFill fill) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(28 * dp);
        card.setCardElevation(0);
        card.setCardBackgroundColor(colSurfaceContainer);
        card.setStrokeWidth(0);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int)(4 * dp);

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        card.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        fill.fill(inner);
        parent.addView(card, lp);
    }

    // ── Section label ─────────────────────────────────────────────────────────

    private void addSectionLabel(LinearLayout parent, float dp, String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTextColor(colPrimary);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setPadding((int)(4*dp), (int)(24*dp), (int)(4*dp), (int)(8*dp));
        parent.addView(label, matchWidth());
    }

    // ── Switch row ────────────────────────────────────────────────────────────

    private MaterialSwitch addSwitch(LinearLayout parent, float dp,
                            String title, String desc,
                            String key, int defaultVal) {
        return addSwitch(parent, dp, title, desc, key, defaultVal, null);
    }

    private MaterialSwitch addSwitch(LinearLayout parent, float dp,
                            String title, String desc,
                            String key, int defaultVal,
                            @androidx.annotation.Nullable java.util.function.Consumer<Boolean> onChange) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int)(64 * dp));
        row.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));

        TypedValue ripple = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            row.setBackgroundResource(ripple.resourceId);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd((int)(12*dp));
        textCol.setLayoutParams(textLp);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTextColor(colOnSurface);
        textCol.addView(tv);

        if (desc != null && !desc.isEmpty()) {
            TextView dv = new TextView(this);
            dv.setText(desc);
            dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            dv.setTextColor(colOnSurfaceVariant);
            dv.setPadding(0, (int)(2*dp), 0, 0);
            textCol.addView(dv);
        }
        row.addView(textCol);

        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setClickable(false);
        sw.setFocusable(false);
        sw.setChecked(Settings.Secure.getInt(getContentResolver(), key, defaultVal) == 1);
        row.addView(sw, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> {
            boolean next = !sw.isChecked();
            sw.setChecked(next);
            try { Settings.Secure.putInt(getContentResolver(), key, next ? 1 : 0); }
            catch (SecurityException ignored) {}
            sendPrefs();
            if (onChange != null) onChange.accept(next);
        });

        parent.addView(row, matchWidth());
        return sw;
    }

    // ── Nav row enabled state ─────────────────────────────────────────────────

    private void setNavRowEnabled(View row, boolean enabled) {
        if (row == null) return;
        row.setEnabled(enabled);
        row.setClickable(enabled);
        row.setAlpha(enabled ? 1f : 0.38f);
        if (row instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) row;
            for (int i = 0; i < vg.getChildCount(); i++)
                setEnabledRecursive(vg.getChildAt(i), enabled);
        }
    }

    private void setEnabledRecursive(View v, boolean enabled) {
        v.setEnabled(enabled);
        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            for (int i = 0; i < vg.getChildCount(); i++)
                setEnabledRecursive(vg.getChildAt(i), enabled);
        }
    }

    // ── Slider row ────────────────────────────────────────────────────────────

    private View addSlider(LinearLayout parent, float dp,
                            String title, String desc,
                            String key, int defaultVal,
                            int min, int max, String unit, float stepSize) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding((int)(16*dp), (int)(16*dp), (int)(16*dp), (int)(12*dp));

        // Title + current value
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTextColor(colOnSurface);
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(tv);

        int current = Math.max(min, Math.min(max,
                Settings.Secure.getInt(getContentResolver(), key, defaultVal)));

        TextView valueLabel = new TextView(this);
        valueLabel.setText(current + unit);
        valueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        valueLabel.setTextColor(colPrimary);
        valueLabel.setTypeface(Typeface.DEFAULT_BOLD);
        titleRow.addView(valueLabel);
        col.addView(titleRow, matchWidth());

        // Description
        TextView dv = new TextView(this);
        dv.setText(desc);
        dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        dv.setTextColor(colOnSurfaceVariant);
        dv.setPadding(0, (int)(3*dp), 0, (int)(6*dp));
        col.addView(dv, matchWidth());

        // Slider
        Slider slider = new Slider(this);
        slider.setValueFrom(min);
        slider.setValueTo(max);
        slider.setValue(current);
        slider.setStepSize(stepSize);
        slider.setLabelFormatter(v -> (int) v + unit);
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (fromUser) valueLabel.setText((int) value + unit);
        });
        slider.addOnSliderTouchListener(new Slider.OnSliderTouchListener() {
            @Override public void onStartTrackingTouch(@NonNull Slider s) {}
            @Override public void onStopTrackingTouch(@NonNull Slider s) {
                try { Settings.Secure.putInt(getContentResolver(), key, (int) s.getValue()); }
                catch (SecurityException ignored) {}
                sendPrefs();
            }
        });
        col.addView(slider, matchWidth());
        parent.addView(col, matchWidth());
        return col;
    }

    // ── In-card divider ───────────────────────────────────────────────────────

    private void addDivider(LinearLayout parent, float dp) {
        View line = new View(this);
        line.setBackgroundColor(colOutlineVariant);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        lp.setMarginStart((int)(16 * dp));
        line.setLayoutParams(lp);
        parent.addView(line);
    }

    // ── Prefs broadcast ───────────────────────────────────────────────────────

    private void sendPrefs() { Prefs.sendAll(this); }

    private void syncSwitches() {
        if (mGestureSwitch != null)
            mGestureSwitch.setChecked(Settings.Secure.getInt(getContentResolver(),
                    Prefs.KEY_GESTURE_ENABLED, Prefs.DEFAULT_GESTURE_ENABLED) == 1);
        if (mAutoSwitch != null)
            mAutoSwitch.setChecked(Settings.Secure.getInt(getContentResolver(),
                    Prefs.KEY_AUTO_BRIGHTNESS, Prefs.DEFAULT_AUTO_BRIGHTNESS) == 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ("android.service.quicksettings.action.QS_TILE_PREFERENCES"
                .equals(getIntent() != null ? getIntent().getAction() : null))
            AutoBrightnessTileService.sLastPrefOpenMs = System.currentTimeMillis();
        syncSwitches();
        sendPrefs();
        Handler h = new Handler(Looper.getMainLooper());
        mSwitchObserver = new ContentObserver(h) {
            @Override public void onChange(boolean selfChange) { syncSwitches(); }
        };
        Uri gestureUri = Settings.Secure.getUriFor(Prefs.KEY_GESTURE_ENABLED);
        Uri autoUri    = Settings.Secure.getUriFor(Prefs.KEY_AUTO_BRIGHTNESS);
        getContentResolver().registerContentObserver(gestureUri, false, mSwitchObserver);
        getContentResolver().registerContentObserver(autoUri,    false, mSwitchObserver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mSwitchObserver != null) {
            getContentResolver().unregisterContentObserver(mSwitchObserver);
            mSwitchObserver = null;
        }
    }

    // ── Colours ───────────────────────────────────────────────────────────────

    private void resolveColours() {
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;

        colBackground       = resolveColor(android.R.attr.colorBackground,
                                night ? 0xFF141218 : 0xFFFEF7FF);
        colSurfaceContainer = resolveColor(com.google.android.material.R.attr.colorSurfaceContainerHigh,
                                night ? 0xFF2B2930 : 0xFFECE6F0);
        colOnSurface        = resolveColor(android.R.attr.textColorPrimary,
                                night ? 0xFFE6E1E5 : 0xFF1D1B20);
        colOnSurfaceVariant = resolveColor(com.google.android.material.R.attr.colorOnSurfaceVariant,
                                night ? 0xFFCAC4D0 : 0xFF49454F);
        colPrimary          = resolveColor(android.R.attr.colorPrimary,
                                night ? 0xFFD0BCFF : 0xFF6650A4);
        colOutlineVariant   = resolveColor(com.google.android.material.R.attr.colorOutlineVariant,
                                night ? 0xFF49454F : 0xFFCAC4D0);
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attr, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        return fallback;
    }

    // ── Nav row ───────────────────────────────────────────────────────────────

    private View addNavRow(LinearLayout parent, float dp,
                            String title, String desc, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight((int)(64 * dp));
        row.setPadding((int)(16*dp), (int)(14*dp), (int)(16*dp), (int)(14*dp));

        TypedValue ripple = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            row.setBackgroundResource(ripple.resourceId);
        }

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        textLp.setMarginEnd((int)(8*dp));
        textCol.setLayoutParams(textLp);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTextColor(colOnSurface);
        textCol.addView(tv);

        if (desc != null && !desc.isEmpty()) {
            TextView dv = new TextView(this);
            dv.setText(desc);
            dv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            dv.setTextColor(colOnSurfaceVariant);
            dv.setPadding(0, (int)(2*dp), 0, 0);
            textCol.addView(dv);
        }
        row.addView(textCol);

        TextView chevron = new TextView(this);
        chevron.setText("›");
        chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        chevron.setTextColor(colOnSurfaceVariant);
        row.addView(chevron, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        row.setOnClickListener(v -> onClick.run());
        parent.addView(row, matchWidth());
        return row;
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private LinearLayout.LayoutParams matchWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
