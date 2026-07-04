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

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.slider.Slider;

public class IndicatorAppearanceActivity extends AppCompatActivity {

    private int colBackground;
    private int colSurfaceContainer;
    private int colOnSurface;
    private int colOnSurfaceVariant;
    private int colPrimary;
    private int colSecondary;
    private int mMainColor;
    private float dp;

    private int mColorMode;
    private int mCustomColor;
    private int mAlpha;
    private int mTextColorMode;
    private int mTextCustomColor;
    private boolean mShadow;
    private int mShape;

    private IndicatorPreviewView mPreviewView;
    private MaterialCardView mPreviewCard;
    private LinearLayout mPreviewWrapper;
    private View mPreviewPlaceholder;
    private ColorSwatch[] mMaterialYouSwatches;   // 5 entries: modes 0,1,2,6,7
    private ColorSwatch[] mSwatches;              // 3 entries: modes 3,4,5 (White/Dark/Custom)
    private EditText mHexInput;
    private TextView mAlphaValueLabel;
    private ColorSwatch[] mTextMySwatches;         // 5 entries: Light, Main, Dark, Tonal, Neutral
    private ColorSwatch[] mTextSwatches;          // 4 entries: Auto, White, Black, Custom
    private EditText mTextHexInput;
    private TextView mTextColorSubtitle;
    private NestedScrollView mScrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        resolveColours();
        dp = getResources().getDisplayMetrics().density;
        // "Main" = the app slider's inactive tick colour (the right-side dots). Read it
        // exactly and report it so the indicator matches (the hook resolves M3 differently).
        // "Main" = the app slider's inactive track colour = colorSecondaryContainer.
        mMainColor = resolveAttr(com.google.android.material.R.attr.colorSecondaryContainer, 0xFF920026);
        boolean nightNow = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        Prefs.setPref(this, nightNow ? Prefs.KEY_MAIN_DARK : Prefs.KEY_MAIN_LIGHT, mMainColor);

        mColorMode   = Settings.Secure.getInt(getContentResolver(),
                Prefs.KEY_INDICATOR_COLOR_MODE,   Prefs.DEFAULT_INDICATOR_COLOR_MODE);
        mCustomColor = Settings.Secure.getInt(getContentResolver(),
                Prefs.KEY_INDICATOR_CUSTOM_COLOR, Prefs.DEFAULT_INDICATOR_CUSTOM_COLOR);
        mAlpha           = Settings.Secure.getInt(getContentResolver(),
                Prefs.KEY_INDICATOR_ALPHA,              Prefs.DEFAULT_INDICATOR_ALPHA);
        mTextColorMode   = Settings.Secure.getInt(getContentResolver(),
                Prefs.KEY_INDICATOR_TEXT_COLOR_MODE,   Prefs.DEFAULT_INDICATOR_TEXT_COLOR_MODE);
        mTextCustomColor = Settings.Secure.getInt(getContentResolver(),
                Prefs.KEY_INDICATOR_TEXT_CUSTOM_COLOR, Prefs.DEFAULT_INDICATOR_TEXT_CUSTOM_COLOR);
        mShadow          = Settings.Secure.getInt(getContentResolver(),
                Prefs.KEY_INDICATOR_SHADOW,            Prefs.DEFAULT_INDICATOR_SHADOW) == 1;
        mShape           = Settings.Secure.getInt(getContentResolver(),
                Prefs.KEY_INDICATOR_SHAPE,             Prefs.DEFAULT_INDICATOR_SHAPE);

        // ── Root ──────────────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(colBackground);
        setContentView(root);

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            int top    = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            v.setPadding(0, top, 0, bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        // ── Toolbar ───────────────────────────────────────────────────────────
        MaterialToolbar toolbar = new MaterialToolbar(this);
        toolbar.setTitle("");
        toolbar.setBackgroundColor(colBackground);
        toolbar.setTitleTextColor(colOnSurface);
        int navBtnSize = (int)(40 * dp);
        int navIconPad = (int)(10 * dp);
        GradientDrawable navCircle = new GradientDrawable();
        navCircle.setShape(GradientDrawable.OVAL);
        navCircle.setColor(colSurfaceContainer);
        navCircle.setSize(navBtnSize, navBtnSize);
        android.graphics.drawable.Drawable navArrow =
                getDrawable(androidx.appcompat.R.drawable.abc_ic_ab_back_material).mutate();
        navArrow.setTint(colOnSurface);
        LayerDrawable navIcon = new LayerDrawable(
                new android.graphics.drawable.Drawable[]{navCircle, navArrow});
        navIcon.setLayerInset(1, navIconPad, navIconPad, navIconPad, navIconPad);
        toolbar.setNavigationIcon(navIcon);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        // Set the collapsed title once and drive its visibility purely through alpha.
        // The page OPENS collapsed (small toolbar title, like the A16 settings pages);
        // the large title only appears when the user scrolls up to reveal it, crossfading
        // with this one via the scroll listener below.
        toolbar.setTitle("Indicator appearance");
        final TextView collapsedTitle = styleToolbarTitle(toolbar);
        if (collapsedTitle != null) collapsedTitle.setAlpha(1f);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── Content ───────────────────────────────────────────────────────────
        // Frame holds both the scroll and the floating preview overlay
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mScrollView = new NestedScrollView(this);
        NestedScrollView scroll = mScrollView;
        // Overscroll stretch stays ON for the card content. The title and preview are
        // floated out of the scroll (into the frame overlay) so the stretch never touches
        // them — the card list still stretches, the header stays rigid.
        frame.addView(scroll, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int)(16*dp), (int)(8*dp), (int)(16*dp), 0);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Android-16-style large collapsing settings title: 32sp, medium weight (500).
        // It floats in the overlay (like the preview) so the top-edge overscroll stretch
        // never stretches it; a placeholder reserves its space in the scroll.
        TextView pageTitle = new TextView(this);
        pageTitle.setText("Indicator appearance");
        pageTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 32);
        pageTitle.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        pageTitle.setTextColor(colOnSurface);
        pageTitle.setBackgroundColor(colBackground);
        // 16dp content inset + 4dp original inset = 20dp left; 16dp right.
        pageTitle.setPadding((int)(20*dp), (int)(8*dp), (int)(16*dp), (int)(20*dp));
        pageTitle.setAlpha(0f);  // page opens collapsed; revealed by scrolling up
        frame.addView(pageTitle, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Measure the title now so the placeholder gets its real height on the FIRST layout
        // pass — otherwise the preview (positioned from the placeholder) starts on top of
        // the title and covers it.
        int widthPx = getResources().getDisplayMetrics().widthPixels;
        pageTitle.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        final View titlePlaceholder = new View(this);
        content.addView(titlePlaceholder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, pageTitle.getMeasuredHeight()));

        buildPreview(content, frame);

        scroll.setOnScrollChangeListener(
            (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldX, oldY) -> {
                int bottom = titlePlaceholder.getBottom();
                if (bottom > 0) {
                    // A16-style crossfade (CollapsingToolbarLayout fade mode): the
                    // large title fades out over the first half of the collapse as
                    // it slides under the toolbar, the collapsed title fades in
                    // over the second half — and the reverse scrolling back down.
                    float p = Math.max(0f, Math.min(1f, scrollY / (float) bottom));
                    pageTitle.setAlpha(1f - Math.min(1f, p / 0.5f));
                    if (collapsedTitle != null)
                        collapsedTitle.setAlpha(Math.max(0f, (p - 0.5f) / 0.5f));
                }
                // Title scrolls up freely (unclamped) and slides under the toolbar.
                pageTitle.setTranslationY(titlePlaceholder.getTop() - scrollY);
                if (mPreviewWrapper != null && mPreviewPlaceholder != null) {
                    float y = Math.max(0f, mPreviewPlaceholder.getTop() - scrollY);
                    mPreviewWrapper.setTranslationY(y);
                }
            });
        scroll.post(() -> {
            // Open collapsed: start scrolled just past the large title so the page
            // begins with the small toolbar title; the large one only appears if the
            // user scrolls up. (A restored scroll position is left alone.)
            if (scroll.getScrollY() == 0) scroll.scrollTo(0, titlePlaceholder.getBottom());
            pageTitle.setTranslationY(titlePlaceholder.getTop() - scroll.getScrollY());
            if (mPreviewWrapper != null && mPreviewPlaceholder != null) {
                mPreviewWrapper.setTranslationY(
                        Math.max(0f, mPreviewPlaceholder.getTop() - scroll.getScrollY()));
            }
            // Apply the scroll-derived alphas for the initial position (the scrollTo
            // above fires the listener, but a restored position may not).
            int bottom = titlePlaceholder.getBottom();
            float p = bottom > 0
                    ? Math.max(0f, Math.min(1f, scroll.getScrollY() / (float) bottom))
                    : 0f;
            pageTitle.setAlpha(1f - Math.min(1f, p / 0.5f));
            if (collapsedTitle != null)
                collapsedTitle.setAlpha(Math.max(0f, (p - 0.5f) / 0.5f));
        });

        addSectionLabel(content, "Shape", 0);
        buildShapeSection(content);
        addSectionLabel(content, "Color");
        buildColorSection(content);
        buildTextColorSection(content);
        addSectionLabel(content, "Opacity");
        buildOpacitySection(content);
        addSectionLabel(content, "Effects");
        buildShadowSection(content);

        refreshColorSwatches();
        refreshTextColorSwatches();
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    private void buildPreview(LinearLayout contentParent, android.widget.FrameLayout frameParent) {
        // Invisible placeholder in scroll content — holds the space so items below don't jump up
        int wrapperH = (int)((148 + 24) * dp);  // card height + bottom padding
        mPreviewPlaceholder = new View(this);
        contentParent.addView(mPreviewPlaceholder, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, wrapperH));

        // Wrapper lives in the frame overlay — outside the scroll, immune to overscroll
        mPreviewWrapper = new LinearLayout(this);
        mPreviewWrapper.setOrientation(LinearLayout.VERTICAL);
        mPreviewWrapper.setBackgroundColor(colBackground);
        mPreviewWrapper.setPadding((int)(16*dp), 0, (int)(16*dp), (int)(24 * dp));
        mPreviewWrapper.setElevation(6 * dp);
        mPreviewWrapper.setTranslationY(-10000);  // hidden until scroll.post() positions it

        mPreviewCard = new MaterialCardView(this);
        mPreviewCard.setRadius(28 * dp);
        mPreviewCard.setCardElevation(0);
        mPreviewCard.setCardBackgroundColor(colSurfaceContainer);
        mPreviewCard.setStrokeWidth(0);

        mPreviewView = new IndicatorPreviewView();
        mPreviewCard.addView(mPreviewView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int)(148 * dp)));
        mPreviewWrapper.addView(mPreviewCard, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        frameParent.addView(mPreviewWrapper, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void invalidatePreviews() {
        mPreviewView.invalidate();
    }

    /**
     * Match the collapsed toolbar title's weight to the large title (medium 500)
     * and return its TextView so scrolling can drive its fade.
     */
    private TextView styleToolbarTitle(MaterialToolbar tb) {
        for (int i = 0; i < tb.getChildCount(); i++) {
            View c = tb.getChildAt(i);
            if (c instanceof TextView) {
                ((TextView) c).setTypeface(android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.NORMAL));
                return (TextView) c;
            }
        }
        return null;
    }

    // ── Color section ─────────────────────────────────────────────────────────

    private void buildColorSection(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(28 * dp);
        card.setCardElevation(0);
        card.setCardBackgroundColor(colSurfaceContainer);
        card.setStrokeWidth(0);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = (int)(8 * dp);

        LinearLayout cardInner = new LinearLayout(this);
        cardInner.setOrientation(LinearLayout.VERTICAL);

        // Content (always visible)
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int)(16*dp), (int)(12*dp), (int)(16*dp), (int)(12*dp));

        // Row 1: Material You swatches
        int[] myModes  = {Prefs.COLOR_MODE_ACCENT_LIGHT, Prefs.COLOR_MODE_ACCENT,
                          Prefs.COLOR_MODE_ACCENT_DARK, Prefs.COLOR_MODE_TERTIARY,
                          Prefs.COLOR_MODE_NEUTRAL};
        String[] myLabels = {"Primary", "Main", "Secondary", "Tonal", "Neutral"};
        mMaterialYouSwatches = new ColorSwatch[5];
        LinearLayout myRow = new LinearLayout(this);
        myRow.setOrientation(LinearLayout.HORIZONTAL);
        myRow.setPadding(0, (int)(4*dp), 0, (int)(10*dp));
        for (int i = 0; i < 5; i++) {
            final int mode = myModes[i];
            mMaterialYouSwatches[i] = new ColorSwatch(resolveColorForMode(mode), myLabels[i]);
            mMaterialYouSwatches[i].setOnClickListener(v -> selectColorMode(mode));
            myRow.addView(mMaterialYouSwatches[i], new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        content.addView(myRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Row 2: Static swatches
        int[] stdModes   = {Prefs.COLOR_MODE_WHITE, Prefs.COLOR_MODE_BLACK, Prefs.COLOR_MODE_CUSTOM};
        String[] stdLabels = {"White", "Dark", "Custom"};
        int[] stdColors  = {0xFFFFFFFF, 0xFF2D2D2D, mCustomColor};
        mSwatches = new ColorSwatch[3];
        LinearLayout stdRow = new LinearLayout(this);
        stdRow.setOrientation(LinearLayout.HORIZONTAL);
        stdRow.setPadding(0, (int)(2*dp), 0, (int)(4*dp));
        for (int i = 0; i < 3; i++) {
            final int mode = stdModes[i];
            mSwatches[i] = new ColorSwatch(stdColors[i], stdLabels[i]);
            mSwatches[i].setOnClickListener(v -> selectColorMode(mode));
            stdRow.addView(mSwatches[i], new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        content.addView(stdRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Hex input row
        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        hexRow.setPadding(0, (int)(4*dp), 0, 0);
        TextView hexLabel = new TextView(this);
        hexLabel.setText("Custom color");
        hexLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hexLabel.setTextColor(colOnSurface);
        hexRow.addView(hexLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        mHexInput = new EditText(this);
        mHexInput.setHint("#RRGGBB");
        mHexInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mHexInput.setTextColor(colOnSurface);
        mHexInput.setHintTextColor(colOnSurfaceVariant);
        mHexInput.setInputType(InputType.TYPE_CLASS_TEXT);
        mHexInput.setSingleLine(true);
        mHexInput.setEms(8);
        mHexInput.setText(String.format("#%06X", mCustomColor & 0xFFFFFF));
        mHexInput.setBackground(null);
        hexRow.addView(mHexInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView setBtn = new TextView(this);
        setBtn.setText("Set");
        setBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        setBtn.setTextColor(colPrimary);
        setBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        setBtn.setPadding((int)(12*dp), (int)(4*dp), 0, (int)(4*dp));
        setBtn.setOnClickListener(v -> applyCustomHex());
        hexRow.addView(setBtn);
        content.addView(hexRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cardInner.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(cardInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(card, cardLp);
    }

    private void selectColorMode(int mode) {
        mColorMode = mode;
        Prefs.setPref(this, Prefs.KEY_INDICATOR_COLOR_MODE, mode);
        refreshColorSwatches();
        refreshTextColorSwatches();
        invalidatePreviews();
    }

    private void applyCustomHex() {
        String text = mHexInput.getText().toString().trim();
        if (!text.startsWith("#")) text = "#" + text;
        try {
            int color = Color.parseColor(text) | 0xFF000000;
            mCustomColor = color;
            mColorMode = Prefs.COLOR_MODE_CUSTOM;
            Prefs.setPref(this, Prefs.KEY_INDICATOR_CUSTOM_COLOR, color);
            Prefs.setPref(this, Prefs.KEY_INDICATOR_COLOR_MODE, Prefs.COLOR_MODE_CUSTOM);
            mSwatches[2].setColor(color);
            refreshColorSwatches();
            invalidatePreviews();
        } catch (IllegalArgumentException e) {
            mHexInput.setError("Invalid color");
        }
    }

    private void refreshColorSwatches() {
        int[] myModes  = {Prefs.COLOR_MODE_ACCENT_LIGHT, Prefs.COLOR_MODE_ACCENT,
                          Prefs.COLOR_MODE_ACCENT_DARK, Prefs.COLOR_MODE_TERTIARY,
                          Prefs.COLOR_MODE_NEUTRAL};
        for (int i = 0; i < mMaterialYouSwatches.length; i++) {
            mMaterialYouSwatches[i].setSelected(mColorMode == myModes[i]);
        }
        int[] stdModes = {Prefs.COLOR_MODE_WHITE, Prefs.COLOR_MODE_BLACK, Prefs.COLOR_MODE_CUSTOM};
        for (int i = 0; i < mSwatches.length; i++) {
            mSwatches[i].setSelected(mColorMode == stdModes[i]);
        }
    }

    // ── Opacity section ───────────────────────────────────────────────────────

    private void buildOpacitySection(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(28 * dp);
        card.setCardElevation(0);
        card.setCardBackgroundColor(colSurfaceContainer);
        card.setStrokeWidth(0);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = 0;

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding((int)(16*dp), (int)(12*dp), (int)(4*dp), (int)(12*dp));

        mAlphaValueLabel = new TextView(this);
        mAlphaValueLabel.setText(mAlpha + "%");
        mAlphaValueLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        mAlphaValueLabel.setTextColor(colOnSurfaceVariant);
        mAlphaValueLabel.setPadding(0, 0, (int)(12*dp), 0);
        inner.addView(mAlphaValueLabel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Slider slider = new Slider(this);
        slider.setValueFrom(10f);
        slider.setValueTo(100f);
        slider.setStepSize(1f);
        slider.setValue(Math.max(10f, Math.min(100f, mAlpha)));
        slider.setTrackActiveTintList(android.content.res.ColorStateList.valueOf(colPrimary));
        slider.addOnChangeListener((s, value, fromUser) -> {
            if (!fromUser) return;
            mAlpha = (int) value;
            mAlphaValueLabel.setText(mAlpha + "%");
            Prefs.setPref(this, Prefs.KEY_INDICATOR_ALPHA, mAlpha);
            invalidatePreviews();
        });
        inner.addView(slider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(inner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(card, cardLp);
    }

    // ── Shape section ─────────────────────────────────────────────────────────

    private ShapeTile[] mShapeTiles;

    private void buildShapeSection(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(28 * dp);
        card.setCardElevation(0);
        card.setCardBackgroundColor(colSurfaceContainer);
        card.setStrokeWidth(0);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = (int)(8 * dp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding((int)(12*dp), (int)(12*dp), (int)(12*dp), (int)(12*dp));

        String[] names = {"Pill", "Droplet", "Circle", "Star"};
        int[] shapes = {Prefs.INDICATOR_SHAPE_PILL, Prefs.INDICATOR_SHAPE_DROPLET,
                Prefs.INDICATOR_SHAPE_CIRCLE, Prefs.INDICATOR_SHAPE_STAR};
        mShapeTiles = new ShapeTile[shapes.length];
        for (int i = 0; i < shapes.length; i++) {
            final int shape = shapes[i];
            ShapeTile tile = new ShapeTile(names[i], shape);
            tile.setSelectedState(mShape == shape, false);
            tile.setOnClickListener(v -> selectShape(shape));
            mShapeTiles[i] = tile;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, (int)(92 * dp), 1f);
            if (i > 0) lp.leftMargin = (int)(8 * dp);
            row.addView(tile, lp);
        }

        card.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(card, cardLp);
    }

    private void selectShape(int shape) {
        if (mShape == shape) return;
        mShape = shape;
        Prefs.setPref(this, Prefs.KEY_INDICATOR_SHAPE, shape);
        for (ShapeTile tile : mShapeTiles) {
            tile.setSelectedState(tile.mShapeId == shape, true);
        }
        invalidatePreviews();
    }

    /**
     * One tile of the shape selector: a rounded surface that draws a miniature of
     * the actual indicator shape (via the shared IndicatorDrawing paths) with its
     * name underneath. Selecting a tile morphs it — the corner radius grows, the
     * fill sweeps to secondary-container, and the glyph springs slightly — matching
     * the expressive segmented pickers on Android 16 settings pages.
     */
    private class ShapeTile extends View {
        final int mShapeId;
        private final String mLabel;
        private float mFrac;            // 0 = unselected, 1 = selected
        private android.animation.ValueAnimator mAnim;

        private final Paint mBgPaint    = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mGlyphText  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mLabelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF mRect = new RectF();

        private final int colTileBg = androidx.core.graphics.ColorUtils.blendARGB(
                colSurfaceContainer, colOnSurface, 0.06f);
        private final int colTileSel = resolveAttr(
                com.google.android.material.R.attr.colorSecondaryContainer,
                androidx.core.graphics.ColorUtils.blendARGB(colSurfaceContainer, colPrimary, 0.25f));
        private final int colOnTileSel = resolveAttr(
                com.google.android.material.R.attr.colorOnSecondaryContainer, colOnSurface);

        ShapeTile(String label, int shapeId) {
            super(IndicatorAppearanceActivity.this);
            mLabel = label;
            mShapeId = shapeId;
            setClickable(true);
            setContentDescription(label + " indicator shape");
            mLabelPaint.setTextAlign(Paint.Align.CENTER);
            mLabelPaint.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
            mLabelPaint.setTypeface(android.graphics.Typeface.create(
                    "sans-serif-medium", android.graphics.Typeface.NORMAL));
        }

        void setSelectedState(boolean sel, boolean animate) {
            float target = sel ? 1f : 0f;
            if (mAnim != null) mAnim.cancel();
            if (!animate) {
                mFrac = target;
                invalidate();
                return;
            }
            mAnim = android.animation.ValueAnimator.ofFloat(mFrac, target);
            mAnim.setDuration(sel ? 340 : 240);
            mAnim.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
            mAnim.addUpdateListener(a -> {
                mFrac = (float) a.getAnimatedValue();
                invalidate();
            });
            mAnim.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            float f = mFrac;

            // Surface: 18dp rounded rect that swells toward a squircle and sweeps
            // to the secondary-container colour as it becomes selected.
            float radius = (18 * dp) + (h / 2f - 18 * dp) * f * 0.5f;
            mBgPaint.setColor(androidx.core.graphics.ColorUtils.blendARGB(
                    colTileBg, colTileSel, f));
            mRect.set(0, 0, w, h);
            canvas.drawRoundRect(mRect, radius, radius, mBgPaint);

            int glyphCol = androidx.core.graphics.ColorUtils.blendARGB(
                    colOnSurfaceVariant, colOnTileSel, f);
            int labelCol = androidx.core.graphics.ColorUtils.blendARGB(
                    colOnSurfaceVariant, colOnTileSel, f);

            float labelBase = h - 13 * dp;
            float glyphCy = (labelBase - mLabelPaint.getTextSize()) / 2f + 3 * dp;
            float cx = w / 2f;

            // Expressive spring: the glyph pops slightly mid-transition.
            float pop = 1f + 0.12f * (float) Math.sin(Math.PI * f);
            canvas.save();
            canvas.scale(pop, pop, cx, glyphCy);
            drawGlyph(canvas, cx, glyphCy, glyphCol);
            canvas.restore();

            mLabelPaint.setColor(labelCol);
            canvas.drawText(mLabel, cx, labelBase, mLabelPaint);
        }

        private void drawGlyph(Canvas canvas, float cx, float cy, int col) {
            if (mShapeId == Prefs.INDICATOR_SHAPE_PILL) {
                float pw = 34 * dp, ph = 18 * dp;
                mGlyphPaint.setColor(col);
                mGlyphPaint.setStyle(Paint.Style.FILL);
                mGlyphPaint.clearShadowLayer();
                mRect.set(cx - pw / 2f, cy - ph / 2f, cx + pw / 2f, cy + ph / 2f);
                canvas.drawRoundRect(mRect, ph / 2f, ph / 2f, mGlyphPaint);
            } else if (mShapeId == Prefs.INDICATOR_SHAPE_DROPLET) {
                float gh = 30 * dp, gw = gh / IndicatorDrawing.DROPLET_HEIGHT_FACTOR * 2f;
                canvas.save();
                canvas.translate(cx - gw / 2f, cy - gh / 2f);
                IndicatorDrawing.drawDroplet(canvas, gw, gh, 0, col, 255,
                        col, 1f, false, dp, "", mGlyphPaint, mGlyphText);
                canvas.restore();
            } else if (mShapeId == Prefs.INDICATOR_SHAPE_CIRCLE) {
                float gd = 24 * dp;
                canvas.save();
                canvas.translate(cx - gd / 2f, cy - gd / 2f);
                IndicatorDrawing.drawCircle(canvas, gd, gd, 0, col, 255,
                        col, 1f, false, dp, "", mGlyphPaint, mGlyphText);
                canvas.restore();
            } else {
                float gw = 28 * dp, gh = gw * IndicatorDrawing.STAR_HEIGHT_FACTOR;
                canvas.save();
                canvas.translate(cx - gw / 2f, cy - gh / 2f);
                IndicatorDrawing.drawStar(canvas, gw, gh, 0, col, 255,
                        col, 1f, false, dp, "", mGlyphPaint, mGlyphText);
                canvas.restore();
            }
        }
    }

    // ── Shadow section ────────────────────────────────────────────────────────

    private void buildShadowSection(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(28 * dp);
        card.setCardElevation(0);
        card.setCardBackgroundColor(colSurfaceContainer);
        card.setStrokeWidth(0);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = (int)(24 * dp);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding((int)(20*dp), (int)(12*dp), (int)(20*dp), (int)(12*dp));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("Drop shadow");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        title.setTextColor(colOnSurface);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        texts.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView desc = new TextView(this);
        desc.setText("Cast a soft shadow behind the indicator");
        desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        desc.setTextColor(colOnSurfaceVariant);
        texts.addView(desc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(texts, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        MaterialSwitch sw = new MaterialSwitch(this);
        sw.setChecked(mShadow);
        sw.setOnCheckedChangeListener((v, checked) -> {
            mShadow = checked;
            Prefs.setPref(this, Prefs.KEY_INDICATOR_SHADOW, checked ? 1 : 0);
            invalidatePreviews();
        });
        row.addView(sw, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(row, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(card, cardLp);
    }

    // ── Text color section ────────────────────────────────────────────────────

    private int resolveTextColorForMode(int bgColor) {
        switch (mTextColorMode) {
            case Prefs.TEXT_COLOR_MODE_WHITE:        return Color.WHITE;
            case Prefs.TEXT_COLOR_MODE_BLACK:        return Color.BLACK;
            case Prefs.TEXT_COLOR_MODE_CUSTOM:       return mTextCustomColor;
            case Prefs.TEXT_COLOR_MODE_ACCENT_LIGHT: return resolveColorForMode(Prefs.COLOR_MODE_ACCENT_LIGHT);
            case Prefs.TEXT_COLOR_MODE_ACCENT:       return resolveColorForMode(Prefs.COLOR_MODE_ACCENT);
            case Prefs.TEXT_COLOR_MODE_ACCENT_DARK:  return resolveColorForMode(Prefs.COLOR_MODE_ACCENT_DARK);
            case Prefs.TEXT_COLOR_MODE_TERTIARY:     return resolveColorForMode(Prefs.COLOR_MODE_TERTIARY);
            case Prefs.TEXT_COLOR_MODE_NEUTRAL:      return resolveColorForMode(Prefs.COLOR_MODE_NEUTRAL);
            default:                                 return contrastColor(bgColor);
        }
    }

    private void buildTextColorSection(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(28 * dp);
        card.setCardElevation(0);
        card.setCardBackgroundColor(colSurfaceContainer);
        card.setStrokeWidth(0);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        LinearLayout cardInner = new LinearLayout(this);
        cardInner.setOrientation(LinearLayout.VERTICAL);

        // Header row (always visible)
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding((int)(20*dp), (int)(16*dp), (int)(20*dp), (int)(16*dp));
        header.setClickable(true);
        header.setFocusable(true);
        android.util.TypedValue ripple = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        header.setBackgroundResource(ripple.resourceId);

        LinearLayout headerTexts = new LinearLayout(this);
        headerTexts.setOrientation(LinearLayout.VERTICAL);

        TextView titleTv = new TextView(this);
        titleTv.setText("Text color");
        titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        titleTv.setTextColor(colOnSurface);
        titleTv.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        headerTexts.addView(titleTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mTextColorSubtitle = new TextView(this);
        mTextColorSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mTextColorSubtitle.setTextColor(colOnSurfaceVariant);
        headerTexts.addView(mTextColorSubtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        header.addView(headerTexts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Chevron — custom drawn "V" shape, larger and clearer
        final View chevron = new View(this) {
            final android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            {
                p.setColor(colOnSurfaceVariant);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                p.setStrokeWidth(2.5f * dp);
            }
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                float w = getWidth(), h = getHeight();
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(w * 0.15f, h * 0.3f);
                path.lineTo(w * 0.5f,  h * 0.72f);
                path.lineTo(w * 0.85f, h * 0.3f);
                canvas.drawPath(path, p);
            }
        };
        chevron.setWillNotDraw(false);
        int chevronSize = (int)(28 * dp);
        header.addView(chevron, new LinearLayout.LayoutParams(chevronSize, chevronSize));

        // Expandable content (hidden by default)
        LinearLayout expandContent = new LinearLayout(this);
        expandContent.setOrientation(LinearLayout.VERTICAL);
        expandContent.setVisibility(View.GONE);
        expandContent.setPadding((int)(16*dp), 0, (int)(16*dp), (int)(12*dp));

        // Row 1: Material You text color swatches
        int[] myTextModes  = {Prefs.TEXT_COLOR_MODE_ACCENT_LIGHT, Prefs.TEXT_COLOR_MODE_ACCENT,
                              Prefs.TEXT_COLOR_MODE_ACCENT_DARK,  Prefs.TEXT_COLOR_MODE_TERTIARY,
                              Prefs.TEXT_COLOR_MODE_NEUTRAL};
        int[] myBgModes    = {Prefs.COLOR_MODE_ACCENT_LIGHT, Prefs.COLOR_MODE_ACCENT,
                              Prefs.COLOR_MODE_ACCENT_DARK,  Prefs.COLOR_MODE_TERTIARY,
                              Prefs.COLOR_MODE_NEUTRAL};
        String[] myLabels  = {"Primary", "Main", "Secondary", "Tonal", "Neutral"};
        mTextMySwatches = new ColorSwatch[5];
        for (int i = 0; i < 5; i++) {
            mTextMySwatches[i] = new ColorSwatch(resolveColorForMode(myBgModes[i]), myLabels[i]);
            final int mode = myTextModes[i];
            mTextMySwatches[i].setOnClickListener(v -> selectTextColorMode(mode));
        }
        LinearLayout mySwatchRow = new LinearLayout(this);
        mySwatchRow.setOrientation(LinearLayout.HORIZONTAL);
        mySwatchRow.setPadding(0, (int)(4*dp), 0, (int)(10*dp));
        for (ColorSwatch s : mTextMySwatches)
            mySwatchRow.addView(s, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        expandContent.addView(mySwatchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Row 2: Static swatches (Auto, White, Black, Custom)
        int autoColor = contrastColor(resolveColorForMode(mColorMode));
        mTextSwatches = new ColorSwatch[]{
            new ColorSwatch(autoColor,        "Auto"),
            new ColorSwatch(0xFFFFFFFF,       "White"),
            new ColorSwatch(0xFF2D2D2D,       "Black"),
            new ColorSwatch(mTextCustomColor, "Custom"),
        };
        mTextSwatches[0].setYinYang(true);
        int[] stdTextModes = {Prefs.TEXT_COLOR_MODE_AUTO, Prefs.TEXT_COLOR_MODE_WHITE,
                              Prefs.TEXT_COLOR_MODE_BLACK, Prefs.TEXT_COLOR_MODE_CUSTOM};
        LinearLayout swatchRow = new LinearLayout(this);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setPadding(0, (int)(2*dp), 0, (int)(4*dp));
        for (int i = 0; i < mTextSwatches.length; i++) {
            final int mode = stdTextModes[i];
            mTextSwatches[i].setOnClickListener(v -> selectTextColorMode(mode));
            swatchRow.addView(mTextSwatches[i], new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        expandContent.addView(swatchRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Row 3: Custom hex input
        LinearLayout hexRow = new LinearLayout(this);
        hexRow.setOrientation(LinearLayout.HORIZONTAL);
        hexRow.setGravity(Gravity.CENTER_VERTICAL);
        hexRow.setPadding(0, (int)(4*dp), 0, 0);

        TextView hexLbl = new TextView(this);
        hexLbl.setText("Custom color");
        hexLbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hexLbl.setTextColor(colOnSurface);
        hexRow.addView(hexLbl, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        mTextHexInput = new EditText(this);
        mTextHexInput.setHint("#RRGGBB");
        mTextHexInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        mTextHexInput.setTextColor(colOnSurface);
        mTextHexInput.setHintTextColor(colOnSurfaceVariant);
        mTextHexInput.setInputType(InputType.TYPE_CLASS_TEXT);
        mTextHexInput.setSingleLine(true);
        mTextHexInput.setEms(8);
        mTextHexInput.setText(String.format("#%06X", mTextCustomColor & 0xFFFFFF));
        mTextHexInput.setBackground(null);
        hexRow.addView(mTextHexInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView setBtn = new TextView(this);
        setBtn.setText("Set");
        setBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        setBtn.setTextColor(colPrimary);
        setBtn.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        setBtn.setPadding((int)(12*dp), (int)(4*dp), 0, (int)(4*dp));
        setBtn.setOnClickListener(v -> applyTextCustomHex());
        hexRow.addView(setBtn);
        expandContent.addView(hexRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Toggle expand on header click
        final android.animation.ValueAnimator[] runningAnim = {null};
        header.setOnClickListener(v -> {
            if (runningAnim[0] != null) runningAnim[0].cancel();
            boolean expanding = expandContent.getVisibility() != View.VISIBLE;

            chevron.animate()
                    .rotation(expanding ? 180f : 0f)
                    .setDuration(expanding ? 280 : 220)
                    .setInterpolator(expanding
                            ? new android.view.animation.DecelerateInterpolator()
                            : new android.view.animation.AccelerateInterpolator())
                    .start();

            if (expanding) {
                int cardWidth = card.getWidth();
                expandContent.measure(
                        View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int targetH = expandContent.getMeasuredHeight();
                int belowPad = (int)(24 * dp); // matches "Opacity" label topPadding
                expandContent.getLayoutParams().height = 0;
                expandContent.setVisibility(View.VISIBLE);
                android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(0, targetH);
                anim.setDuration(280);
                anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
                anim.addUpdateListener(a -> {
                    expandContent.getLayoutParams().height = (int) a.getAnimatedValue();
                    expandContent.requestLayout();
                    expandContent.post(() -> {
                        android.graphics.Rect r = new android.graphics.Rect(
                                0, 0, cardWidth, expandContent.getHeight() + belowPad);
                        expandContent.requestRectangleOnScreen(r, true);
                    });
                });
                anim.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        expandContent.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        expandContent.requestLayout();
                        runningAnim[0] = null;
                    }
                });
                runningAnim[0] = anim;
                anim.start();
            } else {
                int startH = expandContent.getHeight();
                android.animation.ValueAnimator anim = android.animation.ValueAnimator.ofInt(startH, 0);
                anim.setDuration(220);
                anim.setInterpolator(new android.view.animation.AccelerateInterpolator());
                anim.addUpdateListener(a -> {
                    expandContent.getLayoutParams().height = (int) a.getAnimatedValue();
                    expandContent.requestLayout();
                });
                anim.addListener(new android.animation.AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(android.animation.Animator animation) {
                        expandContent.setVisibility(View.GONE);
                        expandContent.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                        runningAnim[0] = null;
                    }
                });
                runningAnim[0] = anim;
                anim.start();
            }
        });

        cardInner.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        cardInner.addView(expandContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(cardInner, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        parent.addView(card, cardLp);
    }

    private void selectTextColorMode(int mode) {
        mTextColorMode = mode;
        Prefs.setPref(this, Prefs.KEY_INDICATOR_TEXT_COLOR_MODE, mode);
        refreshTextColorSwatches();
        invalidatePreviews();
    }

    private void applyTextCustomHex() {
        String text = mTextHexInput.getText().toString().trim();
        if (!text.startsWith("#")) text = "#" + text;
        try {
            int color = Color.parseColor(text) | 0xFF000000;
            mTextCustomColor = color;
            mTextColorMode = Prefs.TEXT_COLOR_MODE_CUSTOM;
            Prefs.setPref(this, Prefs.KEY_INDICATOR_TEXT_CUSTOM_COLOR, color);
            Prefs.setPref(this, Prefs.KEY_INDICATOR_TEXT_COLOR_MODE, Prefs.TEXT_COLOR_MODE_CUSTOM);
            mTextSwatches[3].setColor(color);
            refreshTextColorSwatches();
            invalidatePreviews();
        } catch (IllegalArgumentException e) {
            mTextHexInput.setError("Invalid color");
        }
    }

    private void refreshTextColorSwatches() {
        if (mTextMySwatches == null || mTextSwatches == null) return;
        int[] myModes = {Prefs.TEXT_COLOR_MODE_ACCENT_LIGHT, Prefs.TEXT_COLOR_MODE_ACCENT,
                         Prefs.TEXT_COLOR_MODE_ACCENT_DARK, Prefs.TEXT_COLOR_MODE_TERTIARY,
                         Prefs.TEXT_COLOR_MODE_NEUTRAL};
        int[] bgModes  = {Prefs.COLOR_MODE_ACCENT_LIGHT, Prefs.COLOR_MODE_ACCENT,
                          Prefs.COLOR_MODE_ACCENT_DARK, Prefs.COLOR_MODE_TERTIARY, Prefs.COLOR_MODE_NEUTRAL};
        for (int i = 0; i < mTextMySwatches.length; i++) {
            mTextMySwatches[i].setColor(resolveColorForMode(bgModes[i]));
            mTextMySwatches[i].setSelected(mTextColorMode == myModes[i]);
        }
        int[] stdModes = {Prefs.TEXT_COLOR_MODE_AUTO, Prefs.TEXT_COLOR_MODE_WHITE,
                          Prefs.TEXT_COLOR_MODE_BLACK, Prefs.TEXT_COLOR_MODE_CUSTOM};
        mTextSwatches[0].setColor(contrastColor(resolveColorForMode(mColorMode)));
        for (int i = 0; i < mTextSwatches.length; i++) {
            mTextSwatches[i].setSelected(mTextColorMode == stdModes[i]);
        }
        if (mTextColorSubtitle != null) {
            String[] allNames = {"Auto", "White", "Black", "Custom",
                                 "Primary", "Main", "Secondary", "Tonal", "Neutral"};
            int idx = (mTextColorMode >= 0 && mTextColorMode < allNames.length) ? mTextColorMode : 0;
            mTextColorSubtitle.setText(allNames[idx]);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────


    private int resolveColorForMode(int mode) {
        if (mode == Prefs.COLOR_MODE_ACCENT) {               // "Main" — slider inactive tick colour
            return mMainColor | 0xFF000000;
        } else if (mode == Prefs.COLOR_MODE_ACCENT_LIGHT) {  // "Primary" — matches the indicator exactly
            boolean night = (getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            return getColor(night ? android.R.color.system_neutral1_800
                    : android.R.color.system_neutral1_100) | 0xFF000000;
        } else if (mode == Prefs.COLOR_MODE_ACCENT_DARK) {  // "Secondary" — app secondary role
            return colSecondary | 0xFF000000;
        } else if (mode == Prefs.COLOR_MODE_SECONDARY) {
            try { return getColor(android.R.color.system_accent2_600); } catch (Throwable ignored) {}
        } else if (mode == Prefs.COLOR_MODE_TERTIARY) {
            try { return getColor(android.R.color.system_accent3_600); } catch (Throwable ignored) {}
        } else if (mode == Prefs.COLOR_MODE_NEUTRAL) {
            try { return getColor(android.R.color.system_neutral1_400); } catch (Throwable ignored) {}
        } else if (mode == Prefs.COLOR_MODE_NEUTRAL_VAR) {
            try { return getColor(android.R.color.system_neutral2_400); } catch (Throwable ignored) {}
        } else if (mode == Prefs.COLOR_MODE_WHITE) {
            return 0xFFFFFFFF;
        } else if (mode == Prefs.COLOR_MODE_BLACK) {
            return 0xFF2D2D2D;
        } else if (mode == Prefs.COLOR_MODE_CUSTOM) {
            return mCustomColor;
        }
        try { return getColor(android.R.color.system_accent1_600); } catch (Throwable ignored) {}
        return colPrimary;
    }

    private int contrastColor(int bg) {
        double r = Color.red(bg)/255.0, g = Color.green(bg)/255.0, b = Color.blue(bg)/255.0;
        r = r<=0.03928?r/12.92:Math.pow((r+0.055)/1.055,2.4);
        g = g<=0.03928?g/12.92:Math.pow((g+0.055)/1.055,2.4);
        b = b<=0.03928?b/12.92:Math.pow((b+0.055)/1.055,2.4);
        return (0.2126*r + 0.7152*g + 0.0722*b) < 0.35 ? Color.WHITE : Color.BLACK;
    }

    private void addSectionLabel(LinearLayout parent, String text) {
        addSectionLabel(parent, text, 24);
    }

    private void addSectionLabel(LinearLayout parent, String text, int topDp) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTextColor(colPrimary);
        label.setTypeface(android.graphics.Typeface.create(
                "sans-serif-medium", android.graphics.Typeface.NORMAL));
        label.setPadding((int)(4*dp), (int)(topDp*dp), (int)(4*dp), (int)(8*dp));
        parent.addView(label, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Prefs.sendAll(this);
    }

    // ── Inner views ───────────────────────────────────────────────────────────

    /** Full-size live preview of the indicator. */
    private class IndicatorPreviewView extends View {
        private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        IndicatorPreviewView() {
            super(IndicatorAppearanceActivity.this);
            // Software layer so Paint.setShadowLayer renders for shapes.
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            int bgColor = resolveColorForMode(mColorMode);
            int textCol = resolveTextColorForMode(bgColor);
            float w = getWidth(), h = getHeight(), cx = w / 2f;
            int alpha255 = Math.round(mAlpha / 100f * 255);

            if (mShape == Prefs.INDICATOR_SHAPE_DROPLET) {
                // Shared drawer → identical to the on-screen indicator, scaled up by text size.
                float textSizePx = 17 * dp;
                float r  = IndicatorDrawing.bulbRadius(dp, textSizePx, mTextPaint);
                float tw = IndicatorDrawing.dropletWidth(r);
                float th = IndicatorDrawing.dropletHeight(r);
                canvas.save();
                canvas.translate(cx - tw / 2f, h / 2f - th / 2f);
                IndicatorDrawing.drawDroplet(canvas, tw, th, 0,
                        bgColor, alpha255, textCol, textSizePx,
                        mShadow, dp, "75%", mFillPaint, mTextPaint);
                canvas.restore();
                return;
            }

            if (mShape == Prefs.INDICATOR_SHAPE_STAR) {
                // Shared drawer → identical to the on-screen indicator, scaled up by text size.
                float textSizePx = 17 * dp;
                float sw = IndicatorDrawing.starContentWidth(dp, textSizePx, mTextPaint);
                float tw = IndicatorDrawing.starWidth(sw);
                float th = IndicatorDrawing.starHeight(sw);
                canvas.save();
                canvas.translate(cx - tw / 2f, h / 2f - th / 2f);
                IndicatorDrawing.drawStar(canvas, tw, th, 0,
                        bgColor, alpha255, textCol, textSizePx,
                        mShadow, dp, "75%", mFillPaint, mTextPaint);
                canvas.restore();
                return;
            }

            if (mShape == Prefs.INDICATOR_SHAPE_CIRCLE) {
                // Shared drawer → identical to the on-screen indicator, scaled up by text size.
                float textSizePx = 17 * dp;
                float r  = IndicatorDrawing.bulbRadius(dp, textSizePx, mTextPaint);
                float tw = IndicatorDrawing.dropletWidth(r);
                canvas.save();
                canvas.translate(cx - tw / 2f, h / 2f - tw / 2f);
                IndicatorDrawing.drawCircle(canvas, tw, tw, 0,
                        bgColor, alpha255, textCol, textSizePx,
                        mShadow, dp, "75%", mFillPaint, mTextPaint);
                canvas.restore();
                return;
            }

            // Mirror the real pill's construction exactly, scaled up by text size.
            // Real pill: 13sp bold text, 14dp horizontal / 6dp vertical padding,
            // fully rounded corners. Preview draws at 17dp text, so paddings scale
            // by the same factor — identical proportions at a larger size.
            final float scale = 17f / 13f;
            mTextPaint.setTextSize(17 * dp);
            mTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = mTextPaint.getFontMetrics();
            float textH = fm.bottom - fm.top;
            // The real pill is sized once from measuring "100%" and keeps that
            // width for all values — so the preview sizes from "100%" too, even
            // though it displays "75%".
            float textW = mTextPaint.measureText("100%");
            float pw = textW  + 2 * 14 * dp * scale;
            float ph = textH + 2 * 6 * dp * scale;

            mFillPaint.setColor(bgColor);
            mFillPaint.setStyle(Paint.Style.FILL);
            mFillPaint.setAlpha(Math.round(mAlpha / 100f * 255));
            if (mShadow) {
                // The real indicator dims via window alpha, which fades the shadow
                // together with the pill — mirror that by scaling the shadow alpha.
                int shadowAlpha = Math.round(0x66 * mAlpha / 100f);
                mFillPaint.setShadowLayer(8 * dp, 0, 3 * dp, shadowAlpha << 24);
            } else {
                mFillPaint.clearShadowLayer();
            }

            canvas.drawRoundRect(
                    new RectF(cx - pw/2, h/2 - ph/2, cx + pw/2, h/2 + ph/2),
                    ph/2, ph/2, mFillPaint);

            mTextPaint.setColor(textCol);
            mTextPaint.setAlpha(Math.round(mAlpha / 100f * 255));
            canvas.drawText("75%", cx,
                    h/2 - (mTextPaint.descent() + mTextPaint.ascent()) / 2f, mTextPaint);
        }
    }

    /** Circular color swatch with selection ring. */
    private class ColorSwatch extends LinearLayout {
        private int mColor;
        private boolean mIsSelected;
        private boolean mIsYinYang;
        private final Paint mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final View  mCircleView;
        private final TextView mLabel;
        private final FrameLayout mWrapper;

        ColorSwatch(int color, String label) {
            super(IndicatorAppearanceActivity.this);
            mColor = color;
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER);

            mRingPaint.setStyle(Paint.Style.STROKE);
            mRingPaint.setStrokeWidth(2.5f * dp);
            mRingPaint.setColor(colPrimary);

            int circleSize = (int)(40 * dp);
            int wrapSize   = (int)(48 * dp);

            FrameLayout wrapper = new FrameLayout(getContext()) {
                @Override protected void onDraw(@NonNull Canvas canvas) {
                    super.onDraw(canvas);
                    if (mIsSelected) {
                        float c = getWidth() / 2f;
                        canvas.drawCircle(c, c, c - dp, mRingPaint);
                    }
                }
            };
            wrapper.setWillNotDraw(false);
            mWrapper = wrapper;

            FrameLayout.LayoutParams circLp = new FrameLayout.LayoutParams(circleSize, circleSize);
            circLp.gravity = Gravity.CENTER;

            mCircleView = new View(getContext()) {
                @Override protected void onDraw(@NonNull Canvas canvas) {
                    float r  = Math.min(getWidth(), getHeight()) / 2f - dp;
                    float cx = getWidth() / 2f, cy = getHeight() / 2f;
                    if (mIsYinYang) {
                        Paint wp = new Paint(Paint.ANTI_ALIAS_FLAG);
                        wp.setStyle(Paint.Style.FILL);
                        wp.setColor(0xFFFFFFFF);
                        Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
                        bp.setStyle(Paint.Style.FILL);
                        bp.setColor(0xFF2D2D2D);
                        float sr = r / 2f;
                        android.graphics.RectF outer = new android.graphics.RectF(cx-r, cy-r, cx+r, cy+r);
                        canvas.drawArc(outer, 270, 180, true, bp);  // right half = black
                        canvas.drawArc(outer,  90, 180, true, wp);  // left half = white
                        // right half of upper small circle = white (S-curve bites right into black at top)
                        canvas.drawArc(new android.graphics.RectF(cx-sr, cy-2*sr, cx+sr, cy), 270, 180, true, wp);
                        // left half of lower small circle = black (S-curve bites left into white at bottom)
                        canvas.drawArc(new android.graphics.RectF(cx-sr, cy, cx+sr, cy+2*sr), 90, 180, true, bp);
                    } else {
                        mFillPaint.setColor(mColor);
                        mFillPaint.setStyle(Paint.Style.FILL);
                        canvas.drawCircle(cx, cy, r, mFillPaint);
                    }
                }
            };
            mCircleView.setWillNotDraw(false);
            wrapper.addView(mCircleView, circLp);
            addView(wrapper, new LayoutParams(wrapSize, wrapSize));

            mLabel = new TextView(getContext());
            mLabel.setText(label);
            mLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            mLabel.setTextColor(colOnSurfaceVariant);
            mLabel.setGravity(Gravity.CENTER);
            mLabel.setSingleLine(true);
            mLabel.setPadding(0, (int)(2*dp), 0, 0);
            addView(mLabel, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

        }

        @Override
        public void setSelected(boolean sel) {
            mIsSelected = sel;
            mLabel.setTextColor(sel ? colPrimary : colOnSurfaceVariant);
            mWrapper.invalidate();
        }

        void setColor(int color) {
            mColor = color;
            mCircleView.invalidate();
        }

        void setYinYang(boolean yy) {
            mIsYinYang = yy;
            mCircleView.invalidate();
        }
    }

    // ── Theme colors ──────────────────────────────────────────────────────────

    private void resolveColours() {
        boolean night = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        colBackground       = resolveAttr(android.R.attr.colorBackground,
                                night ? 0xFF141218 : 0xFFFEF7FF);
        colSurfaceContainer = resolveAttr(com.google.android.material.R.attr.colorSurfaceContainerHigh,
                                night ? 0xFF2B2930 : 0xFFECE6F0);
        colOnSurface        = resolveAttr(android.R.attr.textColorPrimary,
                                night ? 0xFFE6E1E5 : 0xFF1D1B20);
        // System secondary text color, so descriptions follow the system text
        // palette; falls back to M3 onSurfaceVariant if the attr is missing.
        colOnSurfaceVariant = resolveAttr(android.R.attr.textColorSecondary,
                                resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant,
                                        night ? 0xFFCAC4D0 : 0xFF49454F));
        colPrimary          = resolveAttr(android.R.attr.colorPrimary,
                                night ? 0xFFD0BCFF : 0xFF6650A4);
        colSecondary        = resolveAttr(com.google.android.material.R.attr.colorSecondary,
                                night ? 0xFFCCC2DC : 0xFF625B71);
    }

    private int resolveAttr(int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (!getTheme().resolveAttribute(attr, tv, true)) return fallback;
        if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        // Text-color attrs (textColorPrimary/Secondary) resolve to ColorStateList
        // resources, not raw color ints — without this branch they silently fell
        // back to the hardcoded defaults instead of the system text palette.
        if (tv.resourceId != 0) {
            try {
                android.content.res.ColorStateList csl = getColorStateList(tv.resourceId);
                if (csl != null) return csl.getDefaultColor();
            } catch (Throwable ignored) {}
        }
        return fallback;
    }
}
