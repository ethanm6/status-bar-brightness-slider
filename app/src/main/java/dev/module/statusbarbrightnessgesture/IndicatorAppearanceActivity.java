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
import com.google.android.material.slider.Slider;

public class IndicatorAppearanceActivity extends AppCompatActivity {

    private int colBackground;
    private int colSurfaceContainer;
    private int colOnSurface;
    private int colOnSurfaceVariant;
    private int colPrimary;
    private float dp;

    private int mColorMode;
    private int mCustomColor;
    private int mAlpha;
    private int mTextColorMode;
    private int mTextCustomColor;

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
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ── Content ───────────────────────────────────────────────────────────
        // Frame holds both the scroll and the floating preview overlay
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        root.addView(frame, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        mScrollView = new NestedScrollView(this);
        NestedScrollView scroll = mScrollView;
        // overscroll re-enabled — preview is outside the scroll so it won't be affected
        frame.addView(scroll, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding((int)(16*dp), (int)(8*dp), (int)(16*dp), 0);
        scroll.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView pageTitle = new TextView(this);
        pageTitle.setText("Indicator appearance");
        pageTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 28);
        pageTitle.setTextColor(colOnSurface);
        pageTitle.setPadding((int)(4*dp), (int)(8*dp), 0, (int)(20*dp));
        content.addView(pageTitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buildPreview(content, frame);

        scroll.setOnScrollChangeListener(
            (NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldX, oldY) -> {
                int bottom = pageTitle.getBottom();
                if (bottom > 0) {
                    toolbar.setTitle(scrollY >= bottom ? "Indicator appearance" : "");
                }
                if (mPreviewWrapper != null && mPreviewPlaceholder != null) {
                    float y = Math.max(0f, mPreviewPlaceholder.getTop() - scrollY);
                    mPreviewWrapper.setTranslationY(y);
                }
            });
        scroll.post(() -> {
            if (mPreviewWrapper != null && mPreviewPlaceholder != null) {
                mPreviewWrapper.setTranslationY(
                        Math.max(0f, mPreviewPlaceholder.getTop() - scroll.getScrollY()));
            }
        });

        addSectionLabel(content, "Color", 0);
        buildColorSection(content);
        buildTextColorSection(content);
        addSectionLabel(content, "Opacity");
        buildOpacitySection(content);

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
        mPreviewCard.setRadius(24 * dp);
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

    // ── Color section ─────────────────────────────────────────────────────────

    private void buildColorSection(LinearLayout parent) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(24 * dp);
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
        String[] myLabels = {"Light", "Main", "Dark", "Tonal", "Neutral"};
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
        try { Settings.Secure.putInt(getContentResolver(), Prefs.KEY_INDICATOR_COLOR_MODE, mode); }
        catch (SecurityException ignored) {}
        Prefs.sendAll(this);
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
            Settings.Secure.putInt(getContentResolver(), Prefs.KEY_INDICATOR_CUSTOM_COLOR, color);
            Settings.Secure.putInt(getContentResolver(), Prefs.KEY_INDICATOR_COLOR_MODE, Prefs.COLOR_MODE_CUSTOM);
            Prefs.sendAll(this);
            mSwatches[2].setColor(color);
            refreshColorSwatches();
            invalidatePreviews();
        } catch (IllegalArgumentException e) {
            mHexInput.setError("Invalid color");
        } catch (SecurityException ignored) {}
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
        card.setRadius(24 * dp);
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
            try { Settings.Secure.putInt(getContentResolver(), Prefs.KEY_INDICATOR_ALPHA, mAlpha); }
            catch (SecurityException ignored) {}
            Prefs.sendAll(this);
            invalidatePreviews();
        });
        inner.addView(slider, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        card.addView(inner, new ViewGroup.LayoutParams(
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
        card.setRadius(24 * dp);
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
        headerTexts.addView(titleTv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mTextColorSubtitle = new TextView(this);
        mTextColorSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
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
        String[] myLabels  = {"Light", "Main", "Dark", "Tonal", "Neutral"};
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
        try { Settings.Secure.putInt(getContentResolver(), Prefs.KEY_INDICATOR_TEXT_COLOR_MODE, mode); }
        catch (SecurityException ignored) {}
        Prefs.sendAll(this);
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
            Settings.Secure.putInt(getContentResolver(), Prefs.KEY_INDICATOR_TEXT_CUSTOM_COLOR, color);
            Settings.Secure.putInt(getContentResolver(), Prefs.KEY_INDICATOR_TEXT_COLOR_MODE, Prefs.TEXT_COLOR_MODE_CUSTOM);
            Prefs.sendAll(this);
            mTextSwatches[3].setColor(color);
            refreshTextColorSwatches();
            invalidatePreviews();
        } catch (IllegalArgumentException e) {
            mTextHexInput.setError("Invalid color");
        } catch (SecurityException ignored) {}
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
                                 "Light", "Main", "Dark", "Tonal", "Neutral"};
            int idx = (mTextColorMode >= 0 && mTextColorMode < allNames.length) ? mTextColorMode : 0;
            mTextColorSubtitle.setText(allNames[idx]);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int resolveAccentBase() {
        try { return getColor(android.R.color.system_accent1_600); } catch (Throwable ignored) {}
        return colPrimary;
    }

    private int resolveColorForMode(int mode) {
        if (mode == Prefs.COLOR_MODE_ACCENT_LIGHT) {
            float[] hsv = new float[3];
            Color.colorToHSV(resolveAccentBase(), hsv);
            hsv[1] = Math.max(0f, hsv[1] - 0.55f);
            hsv[2] = Math.min(1f, hsv[2] + 0.30f);
            return Color.HSVToColor(hsv) | 0xFF000000;
        } else if (mode == Prefs.COLOR_MODE_ACCENT_DARK) {
            float[] hsv = new float[3];
            Color.colorToHSV(resolveAccentBase(), hsv);
            hsv[2] = Math.max(0f, hsv[2] - 0.50f);
            return Color.HSVToColor(hsv) | 0xFF000000;
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
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        label.setTextColor(colPrimary);
        label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
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

        IndicatorPreviewView() { super(IndicatorAppearanceActivity.this); }

        @Override
        protected void onDraw(@NonNull Canvas canvas) {
            int bgColor = resolveColorForMode(mColorMode);
            int textCol = resolveTextColorForMode(bgColor);
            float w = getWidth(), h = getHeight(), cx = w / 2f;

            mFillPaint.setColor(bgColor);
            mFillPaint.setStyle(Paint.Style.FILL);
            mFillPaint.setAlpha(Math.round(mAlpha / 100f * 255));

            float ph = 36 * dp;
            float pw = Math.min(w * 0.42f, 84 * dp);
            canvas.drawRoundRect(
                    new RectF(cx - pw/2, h/2 - ph/2, cx + pw/2, h/2 + ph/2),
                    ph/2, ph/2, mFillPaint);

            mTextPaint.setColor(textCol);
            mTextPaint.setAlpha(Math.round(mAlpha / 100f * 255));
            mTextPaint.setTextSize(17 * dp);
            mTextPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            mTextPaint.setTextAlign(Paint.Align.CENTER);
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
        colOnSurfaceVariant = resolveAttr(com.google.android.material.R.attr.colorOnSurfaceVariant,
                                night ? 0xFFCAC4D0 : 0xFF49454F);
        colPrimary          = resolveAttr(android.R.attr.colorPrimary,
                                night ? 0xFFD0BCFF : 0xFF6650A4);
    }

    private int resolveAttr(int attr, int fallback) {
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(attr, tv, true)
                && tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return tv.data;
        }
        return fallback;
    }
}
