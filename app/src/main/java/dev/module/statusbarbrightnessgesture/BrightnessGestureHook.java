package dev.module.statusbarbrightnessgesture;

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed module — status bar brightness gesture.
 *
 * Hooking: findHookMethod() reflects into XposedBridge at runtime to bypass
 * LSPosed's obfuscation of hookMethod().
 *
 * Prefs: broadcast approach.
 *   - SettingsActivity writes to SharedPreferences and sends a targeted
 *     broadcast to com.android.systemui with the new values as extras.
 *   - Hook registers a BroadcastReceiver inside SystemUI from onAttachedToWindow —
 *     guaranteed safe timing, no race condition.
 *   - SettingsActivity also re-sends on onResume() so values survive SystemUI restarts.
 *   - No permissions needed.
 */
@SuppressWarnings({"JavaReflectionMemberAccess", "ConstantConditions"})
public class BrightnessGestureHook implements IXposedHookLoadPackage {

    private static final String TAG = "BrightnessGestureHook";
    private static final String SYSTEMUI_PACKAGE = "com.android.systemui";

    private static final String PHONE_STATUS_BAR_VIEW =
            "com.android.systemui.statusbar.phone.PhoneStatusBarView";
    private static final String SHADE_WINDOW_CLASS =
            "com.android.systemui.shade.NotificationShadeWindowView";
    private static final String BRIGHTNESS_UTILS_CLASS =
            "com.android.settingslib.display.BrightnessUtils";

    private static final int GAMMA_SPACE_MAX = 65535;
    private static final float STATUS_BAR_Y_FRACTION = 0.06f;
    private static final float GAMMA = 2.2f;
    private static final long INDICATOR_DISMISS_DELAY_MS = 800;

    // ── Per-gesture state ─────────────────────────────────────────────────────

    private float mDownX;
    private float mDownY;
    private boolean mGestureActive = false;
    private boolean mTouchStartedInStatusBar = false;

    // ── Cached resources ──────────────────────────────────────────────────────

    private DisplayManager mDisplayManager;
    private WindowManager mWindowManager;
    private int mScreenWidth;
    private int mScreenHeight;
    private float mDensity = 1f;
    private float mBaseSlopPx = 48f;
    private float mGestureSlopPx = 48f;
    private volatile float mHorizontalRatio = 2.0f;
    private float mEdgePaddingPx = 0f;
    private float mBrightnessMin = -1f;
    private float mBrightnessMax = 1.0f;

    private Method mSetTemporaryBrightnessMethod;
    private Method mSetBrightnessMethod;
    private Method mGetBrightnessInfoMethod;
    private Method mConvertLinearToGammaMethod;

    private Field mBrightnessField;
    private Field mBrightnessMinField;
    private Field mBrightnessMaxField;

    private final java.util.concurrent.ExecutorService mBgExecutor =
            Executors.newSingleThreadExecutor();
    private Handler mMainHandler;

    // ── Indicator ─────────────────────────────────────────────────────────────

    private android.view.View mIndicatorView;   // root window view (pill or teardrop)
    private TextView mIndicatorTextView;        // inner text view for setText
    private WindowManager.LayoutParams mIndicatorParams;
    private int mIndicatorW;
    private int mIndicatorH;
    private boolean mIndicatorAttached = false;
    private ValueAnimator mSlideInAnimator;
    private ValueAnimator mHideAnimator;
    private boolean mSlideInAnimating = false;
    private final Runnable mDismissIndicator = this::hideIndicator;
    private Context mContext;

    // ── Prefs ─────────────────────────────────────────────────────────────────

    private boolean mReceiverRegistered = false;
    private volatile boolean mGestureEnabled    = true;
    private volatile boolean mOverlayEnabled     = true;
    private volatile boolean mBlockLongPressQS  = false;
    private volatile boolean mFullscreenSwipe   = false;
    private volatile boolean mHapticEnabled     = false;
    private volatile int mSensitivity       = Prefs.DEFAULT_SENSITIVITY;
    private volatile int mEdgePaddingDp     = Prefs.DEFAULT_EDGE_PADDING_DP;
    private volatile int mIndicatorColorMode    = Prefs.DEFAULT_INDICATOR_COLOR_MODE;
    private volatile int mIndicatorCustomColor  = Prefs.DEFAULT_INDICATOR_CUSTOM_COLOR;
    private volatile int mIndicatorAlpha        = Prefs.DEFAULT_INDICATOR_ALPHA;
    private volatile int mTextColorMode         = Prefs.DEFAULT_INDICATOR_TEXT_COLOR_MODE;
    private volatile int mTextCustomColor       = Prefs.DEFAULT_INDICATOR_TEXT_CUSTOM_COLOR;
    private volatile int mIndicatorYPosition    = Prefs.DEFAULT_INDICATOR_Y_POSITION;

    // ── Fullscreen touch overlay ──────────────────────────────────────────────

    private android.view.View mStatusBarView;
    private android.view.View mShadeWindowView;        // NotificationShadeWindowView reference
    private volatile boolean mSendingCancel = false;   // true while dispatching synthetic CANCEL to shade
    private android.view.View mFullscreenTouchView;   // insets probe only — always FLAG_NOT_TOUCHABLE
    private WindowManager.LayoutParams mFullscreenTouchParams;
    private boolean mFullscreenTouchAttached = false;
    private Object mInputMonitor;                      // android.view.InputMonitor (reflection)
    private android.view.InputEventReceiver mGestureReceiver;
    private Context mOverlayContext;                   // SystemUI context for InputManager access
    private Runnable mPilferRunnable;                  // delayed pilfer to cut long-press-QS timer
    private boolean mPilferPending = false;

    // ── Entry point ───────────────────────────────────────────────────────────

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEMUI_PACKAGE.equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + ": loading in SystemUI");

        Method hookMethodFn = findHookMethod();
        if (hookMethodFn == null) {
            XposedBridge.log(TAG + ": could not find hookMethod() — aborting");
            return;
        }

        hookTouchTarget(PHONE_STATUS_BAR_VIEW, "onTouchEvent",
                lpparam.classLoader, hookMethodFn, true);
        hookTouchTarget(SHADE_WINDOW_CLASS, "dispatchTouchEvent",
                lpparam.classLoader, hookMethodFn, false);
        hookAttachedToWindow(PHONE_STATUS_BAR_VIEW,
                lpparam.classLoader, hookMethodFn);
    }

    // ── Runtime reflection to find LSPosed's real hookMethod ──────────────────

    private Method findHookMethod() {
        for (Method m : XposedBridge.class.getDeclaredMethods()) {
            Class<?>[] params = m.getParameterTypes();
            if (params.length == 2
                    && java.lang.reflect.Member.class.isAssignableFrom(params[0])
                    && XC_MethodHook.class.isAssignableFrom(params[1])) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }

    // ── onAttachedToWindow — register receiver and init resources ─────────────

    private void hookAttachedToWindow(String className, ClassLoader classLoader,
                                      Method hookMethodFn) {
        try {
            Class<?> cls = Class.forName(className, false, classLoader);
            Method target = cls.getDeclaredMethod("onAttachedToWindow");
            hookMethodFn.invoke(null, target, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Context ctx = (Context) param.thisObject.getClass()
                                .getMethod("getContext").invoke(param.thisObject);
                        if (ctx == null) return;
                        if (mStatusBarView == null) {
                            mStatusBarView = (android.view.View) param.thisObject;
                        }
                        if (!mReceiverRegistered) registerPrefsReceiver(ctx);
                        if (mDisplayManager == null) initDisplayResources(ctx);
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": onAttachedToWindow init failed: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + ": hooked " + className + ".onAttachedToWindow");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to hook onAttachedToWindow: " + t);
        }
    }


    // ── Broadcast receiver for prefs ──────────────────────────────────────────

    private void registerPrefsReceiver(Context context) {
        if (mReceiverRegistered) return;
        mReceiverRegistered = true;

        // Read persisted state from Settings.Secure on boot — available immediately,
        // no app process needed, survives reboots.
        // Falls back to true (enabled) if the key doesn't exist yet.
        try {
            mGestureEnabled = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_GESTURE_ENABLED, Prefs.DEFAULT_GESTURE_ENABLED) == 1;
            mOverlayEnabled = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_OVERLAY_ENABLED, Prefs.DEFAULT_OVERLAY_ENABLED) == 1;
            mBlockLongPressQS = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_BLOCK_LONGPRESS_QS, Prefs.DEFAULT_BLOCK_LONGPRESS_QS) == 1;
            mFullscreenSwipe = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_FULLSCREEN_SWIPE, Prefs.DEFAULT_FULLSCREEN_SWIPE) == 1;
            mHapticEnabled = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_HAPTIC_FEEDBACK, Prefs.DEFAULT_HAPTIC_FEEDBACK) == 1;
            mSensitivity = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_SENSITIVITY, Prefs.DEFAULT_SENSITIVITY);
            mEdgePaddingDp = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_EDGE_PADDING_DP, Prefs.DEFAULT_EDGE_PADDING_DP);
            mIndicatorColorMode = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_INDICATOR_COLOR_MODE, Prefs.DEFAULT_INDICATOR_COLOR_MODE);
            mIndicatorCustomColor = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_INDICATOR_CUSTOM_COLOR, Prefs.DEFAULT_INDICATOR_CUSTOM_COLOR);
            mIndicatorAlpha = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_INDICATOR_ALPHA, Prefs.DEFAULT_INDICATOR_ALPHA);
            mTextColorMode = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_INDICATOR_TEXT_COLOR_MODE, Prefs.DEFAULT_INDICATOR_TEXT_COLOR_MODE);
            mTextCustomColor = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_INDICATOR_TEXT_CUSTOM_COLOR, Prefs.DEFAULT_INDICATOR_TEXT_CUSTOM_COLOR);
            mIndicatorYPosition = Settings.Secure.getInt(context.getContentResolver(),
                    Prefs.KEY_INDICATOR_Y_POSITION, Prefs.DEFAULT_INDICATOR_Y_POSITION);
            applyTuning();
            XposedBridge.log(TAG + ": boot state from Settings.Secure — gesture="
                    + mGestureEnabled + " overlay=" + mOverlayEnabled
                    + " blockLongPress=" + mBlockLongPressQS
                    + " fullscreenSwipe=" + mFullscreenSwipe
                    + " sensitivity=" + mSensitivity + " edgePaddingDp=" + mEdgePaddingDp);
        } catch (Throwable t) {
            mGestureEnabled   = true;
            mOverlayEnabled    = true;
            mBlockLongPressQS = false;
            mFullscreenSwipe  = false;
            mSensitivity   = Prefs.DEFAULT_SENSITIVITY;
            mEdgePaddingDp = Prefs.DEFAULT_EDGE_PADDING_DP;
            applyTuning();
            XposedBridge.log(TAG + ": Settings.Secure read failed, defaulting: " + t);
        }

        // Broadcast receiver for live updates when user changes a toggle
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (Intent.ACTION_CONFIGURATION_CHANGED.equals(intent.getAction())) {
                    if (isSystemColorMode(mIndicatorColorMode) || isSystemTextColorMode(mTextColorMode)) reinitIndicator();
                    return;
                }
                if (!Prefs.ACTION_PREFS_CHANGED.equals(intent.getAction())) return;
                boolean prevGesture = mGestureEnabled;
                mGestureEnabled   = intent.getBooleanExtra(Prefs.KEY_GESTURE_ENABLED, true);
                mOverlayEnabled    = intent.getBooleanExtra(Prefs.KEY_OVERLAY_ENABLED,  true);
                mBlockLongPressQS = intent.getBooleanExtra(Prefs.KEY_BLOCK_LONGPRESS_QS, false);
                mFullscreenSwipe  = intent.getBooleanExtra(Prefs.KEY_FULLSCREEN_SWIPE, false);
                mHapticEnabled    = intent.getBooleanExtra(Prefs.KEY_HAPTIC_FEEDBACK, false);
                mSensitivity = intent.getIntExtra(
                        Prefs.KEY_SENSITIVITY, Prefs.DEFAULT_SENSITIVITY);
                mEdgePaddingDp = intent.getIntExtra(
                        Prefs.KEY_EDGE_PADDING_DP, Prefs.DEFAULT_EDGE_PADDING_DP);

                int newColorMode     = intent.getIntExtra(Prefs.KEY_INDICATOR_COLOR_MODE, Prefs.DEFAULT_INDICATOR_COLOR_MODE);
                int newCustom        = intent.getIntExtra(Prefs.KEY_INDICATOR_CUSTOM_COLOR, Prefs.DEFAULT_INDICATOR_CUSTOM_COLOR);
                int newAlpha         = intent.getIntExtra(Prefs.KEY_INDICATOR_ALPHA, Prefs.DEFAULT_INDICATOR_ALPHA);
                int newTextColorMode = intent.getIntExtra(Prefs.KEY_INDICATOR_TEXT_COLOR_MODE, Prefs.DEFAULT_INDICATOR_TEXT_COLOR_MODE);
                int newTextCustom    = intent.getIntExtra(Prefs.KEY_INDICATOR_TEXT_CUSTOM_COLOR, Prefs.DEFAULT_INDICATOR_TEXT_CUSTOM_COLOR);
                boolean needsReinit = newColorMode != mIndicatorColorMode
                        || newCustom != mIndicatorCustomColor
                        || newTextColorMode != mTextColorMode
                        || newTextCustom != mTextCustomColor;
                mIndicatorColorMode   = newColorMode;
                mIndicatorCustomColor = newCustom;
                mIndicatorAlpha       = newAlpha;
                mTextColorMode        = newTextColorMode;
                mTextCustomColor      = newTextCustom;
                mIndicatorYPosition   = intent.getIntExtra(Prefs.KEY_INDICATOR_Y_POSITION,
                        Prefs.DEFAULT_INDICATOR_Y_POSITION);

                boolean autoBrightness = intent.getBooleanExtra(Prefs.KEY_AUTO_BRIGHTNESS, false);
                try {
                    android.content.ContentResolver cr = mContext.getContentResolver();
                    int currentMode = android.provider.Settings.System.getInt(cr,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    boolean alreadyAuto = currentMode ==
                            android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
                    if (autoBrightness) {
                        if (!alreadyAuto) {
                            int current = android.provider.Settings.System.getInt(cr,
                                    android.provider.Settings.System.SCREEN_BRIGHTNESS, 128);
                            android.provider.Settings.Secure.putInt(cr,
                                    Prefs.KEY_SAVED_BRIGHTNESS, current);
                            android.provider.Settings.System.putInt(cr,
                                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                        }
                    } else {
                        if (alreadyAuto) {
                            android.provider.Settings.System.putInt(cr,
                                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE,
                                    android.provider.Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                            int saved = android.provider.Settings.Secure.getInt(cr,
                                    Prefs.KEY_SAVED_BRIGHTNESS, -1);
                            if (saved >= 0) {
                                android.provider.Settings.System.putInt(cr,
                                        android.provider.Settings.System.SCREEN_BRIGHTNESS, saved);
                            }
                        }
                    }
                } catch (SecurityException ignored) {}

                applyTuning();
                updateFullscreenTouch();
                if (needsReinit) reinitIndicator();
                if (prevGesture && !mGestureEnabled && mIndicatorAttached) {
                    hideIndicator();
                }
            }
        };

        IntentFilter filter = new IntentFilter(Prefs.ACTION_PREFS_CHANGED);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        XposedBridge.log(TAG + ": prefs receiver registered");

        // Watch for system-level brightness mode changes (e.g. brightness panel toggle).
        // App process can't reliably observe Settings.System on this ROM, so we do it here.
        Uri modeUri = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE);
        ContentObserver modeObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) {
                try {
                    android.content.ContentResolver cr = context.getContentResolver();
                    int mode = Settings.System.getInt(cr,
                            Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    boolean auto = (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                    Settings.Secure.putInt(cr, Prefs.KEY_AUTO_BRIGHTNESS, auto ? 1 : 0);
                    if (auto) {
                        Settings.Secure.putInt(cr, Prefs.KEY_GESTURE_ENABLED, 0);
                        mGestureEnabled = false;
                    }
                } catch (SecurityException ignored) {}
            }
        };
        context.getContentResolver().registerContentObserver(modeUri, false, modeObserver);
    }

    // ── Touch hook setup ──────────────────────────────────────────────────────

    private void hookTouchTarget(String className, String methodName,
                                 ClassLoader classLoader, Method hookMethodFn,
                                 boolean isStatusBarView) {
        try {
            Class<?> cls = Class.forName(className, false, classLoader);
            Method target = cls.getDeclaredMethod(methodName, MotionEvent.class);
            hookMethodFn.invoke(null, target, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    // Capture the shade window view so we can send it a cancel later.
                    if (!isStatusBarView && mShadeWindowView == null) {
                        mShadeWindowView = (android.view.View) param.thisObject;
                    }
                    // When we're posting a synthetic ACTION_CANCEL to abort the shade's
                    // in-progress gesture, skip our own processing so the cancel reaches
                    // the shade's gesture detector unmodified.
                    if (!isStatusBarView && mSendingCancel) return;
                    MotionEvent ev = (MotionEvent) param.args[0];
                    if (ev == null) return;
                    if (mDisplayManager == null) {
                        try {
                            Context ctx = (Context) param.thisObject.getClass()
                                    .getMethod("getContext").invoke(param.thisObject);
                            if (ctx != null) initDisplayResources(ctx);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": display init failed: " + t);
                        }
                    }
                    if (!mGestureEnabled) return;
                    if (handleTouchEvent(ev, isStatusBarView)) {
                        param.setResult(true);
                    }
                }
            });
            XposedBridge.log(TAG + ": hooked " + className + "." + methodName);
        } catch (ClassNotFoundException e) {
            XposedBridge.log(TAG + ": class not found: " + className);
        } catch (NoSuchMethodException e) {
            XposedBridge.log(TAG + ": method not found: " + methodName);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook failed for " + className + ": " + t);
        }
    }

    // ── Display resource initialisation ──────────────────────────────────────

    private void initDisplayResources(Context context) {
        try {
            mContext = context;
            if (mMainHandler == null) mMainHandler = new Handler(Looper.getMainLooper());
            mDisplayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            mWindowManager  = (WindowManager)  context.getSystemService(Context.WINDOW_SERVICE);

            android.graphics.Rect bounds = mWindowManager.getCurrentWindowMetrics().getBounds();
            mScreenWidth  = bounds.width();
            mScreenHeight = bounds.height();

            mDisplayManager.registerDisplayListener(new DisplayManager.DisplayListener() {
                @Override
                public void onDisplayChanged(int displayId) {
                    if (displayId != Display.DEFAULT_DISPLAY || mWindowManager == null) return;
                    android.graphics.Rect b = mWindowManager.getCurrentWindowMetrics().getBounds();
                    mScreenWidth  = b.width();
                    mScreenHeight = b.height();
                    XposedBridge.log(TAG + ": display rotated — screen="
                            + mScreenWidth + "x" + mScreenHeight);
                }
                @Override public void onDisplayAdded(int displayId) {}
                @Override public void onDisplayRemoved(int displayId) {}
            }, mMainHandler);

            mDensity = context.getResources().getDisplayMetrics().density;
            mBaseSlopPx = Math.max(
                    ViewConfiguration.get(context).getScaledTouchSlop(), 12f * mDensity);
            applyTuning();

            mSetTemporaryBrightnessMethod = DisplayManager.class
                    .getDeclaredMethod("setTemporaryBrightness", int.class, float.class);
            mSetTemporaryBrightnessMethod.setAccessible(true);

            mSetBrightnessMethod = DisplayManager.class
                    .getDeclaredMethod("setBrightness", int.class, float.class);
            mSetBrightnessMethod.setAccessible(true);

            mGetBrightnessInfoMethod = Display.class.getDeclaredMethod("getBrightnessInfo");
            mGetBrightnessInfoMethod.setAccessible(true);

            try {
                Class<?> bu = Class.forName(
                        BRIGHTNESS_UTILS_CLASS, false, context.getClassLoader());
                mConvertLinearToGammaMethod = bu.getMethod(
                        "convertLinearToGammaFloat", float.class, float.class, float.class);
                XposedBridge.log(TAG + ": found BrightnessUtils.convertLinearToGammaFloat");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": BrightnessUtils not found, using fallback");
            }

            readBrightnessRange();
            initIndicator(context);
            initFullscreenTouchOverlay(context);

            XposedBridge.log(TAG + ": display init — screen=" + mScreenWidth
                    + "x" + mScreenHeight
                    + " range=[" + mBrightnessMin + ", " + mBrightnessMax + "]");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": initDisplayResources failed: " + t);
        }
    }

    private void readBrightnessRange() {
        try {
            Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) return;
            Object info = mGetBrightnessInfoMethod.invoke(display);
            if (info == null) return;
            Class<?> cls = info.getClass();
            if (mBrightnessMinField == null) {
                mBrightnessField    = cls.getField("brightness");
                mBrightnessMinField = cls.getField("brightnessMinimum");
                mBrightnessMaxField = cls.getField("brightnessMaximum");
            }
            mBrightnessMin = (float) mBrightnessMinField.get(info);
            mBrightnessMax = (float) mBrightnessMaxField.get(info);
        } catch (Throwable t) {
            mBrightnessMin = 0.0f;
            mBrightnessMax = 1.0f;
            XposedBridge.log(TAG + ": readBrightnessRange fallback: " + t);
        }
    }

    /**
     * Derive the live gesture-tuning values from the user prefs. Safe to call before
     * display init (uses cached base slop / density, both defaulted).
     *   - Lower sensitivity → larger required swipe distance and a stricter
     *     horizontal-dominance ratio, so a vertical shade-pull won't misfire.
     *   - Edge padding insets the usable slider range for rounded corners.
     */
    private void applyTuning() {
        int s = Math.max(Prefs.SENSITIVITY_MIN,
                Math.min(Prefs.SENSITIVITY_MAX, mSensitivity));
        mGestureSlopPx   = mBaseSlopPx + (10 - s) * 8f * mDensity;
        mHorizontalRatio = 1.5f + (10 - s) * 0.25f;

        int pad = Math.max(0, Math.min(Prefs.EDGE_PADDING_MAX_DP, mEdgePaddingDp));
        mEdgePaddingPx = pad * mDensity;
    }

    // ── Indicator ─────────────────────────────────────────────────────────────

    private void initIndicator(Context context) {
        try {
            int accent  = getIndicatorColor(context) | 0xFF000000;
            int textCol = resolveTextColor(accent);
            float d     = context.getResources().getDisplayMetrics().density;

            initIndicatorPill(context, accent, textCol, d);

            mIndicatorParams = new WindowManager.LayoutParams(
                    mIndicatorW, mIndicatorH,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            mIndicatorParams.gravity = Gravity.TOP | Gravity.START;
            mIndicatorParams.alpha = 0f;
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": initIndicator failed: " + t);
        }
    }

    private void initIndicatorPill(Context context, int accent, int textCol, float d) {
        TextView tv = new TextView(context);
        tv.setTextColor(textCol);
        tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable();
        bg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        bg.setCornerRadius(100f);
        bg.setColor(accent);
        tv.setBackground(bg);
        tv.setAlpha(1.0f);
        tv.setPadding((int)(14*d), (int)(6*d), (int)(14*d), (int)(6*d));

        tv.setLayoutParams(new android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
        tv.setText("100%");
        tv.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));

        mIndicatorView     = tv;
        mIndicatorTextView = tv;
        mIndicatorW = tv.getMeasuredWidth();
        mIndicatorH = tv.getMeasuredHeight();
    }

    private void reinitIndicator() {
        if (mContext == null || mMainHandler == null) return;
        mMainHandler.post(() -> {
            if (mSlideInAnimator != null) { mSlideInAnimator.cancel(); mSlideInAnimator = null; }
            mSlideInAnimating = false;
            if (mIndicatorAttached && mWindowManager != null) {
                try { mWindowManager.removeView(mIndicatorView); } catch (Throwable ignored) {}
                mIndicatorAttached = false;
            }
            mIndicatorView = null;
            mIndicatorTextView = null;
            mIndicatorParams   = null;
            if (mWindowManager != null) initIndicator(mContext);
        });
    }

    private int resolveTextColor(int bgColor) {
        switch (mTextColorMode) {
            case Prefs.TEXT_COLOR_MODE_WHITE:  return Color.WHITE;
            case Prefs.TEXT_COLOR_MODE_BLACK:  return Color.BLACK;
            case Prefs.TEXT_COLOR_MODE_CUSTOM: return mTextCustomColor;
            case Prefs.TEXT_COLOR_MODE_ACCENT_LIGHT: {
                int base = resolveAccentColour(mContext);
                float[] hsv = new float[3]; android.graphics.Color.colorToHSV(base, hsv);
                hsv[1] = Math.max(0f, hsv[1] - 0.55f); hsv[2] = Math.min(1f, hsv[2] + 0.30f);
                return android.graphics.Color.HSVToColor(hsv) | 0xFF000000;
            }
            case Prefs.TEXT_COLOR_MODE_ACCENT:
                return resolveAccentColour(mContext);
            case Prefs.TEXT_COLOR_MODE_ACCENT_DARK: {
                int base = resolveAccentColour(mContext);
                float[] hsv = new float[3]; android.graphics.Color.colorToHSV(base, hsv);
                hsv[2] = Math.max(0f, hsv[2] - 0.50f);
                return android.graphics.Color.HSVToColor(hsv) | 0xFF000000;
            }
            case Prefs.TEXT_COLOR_MODE_TERTIARY:
                try { return mContext.getColor(android.R.color.system_accent3_600); } catch (Throwable t) { break; }
            case Prefs.TEXT_COLOR_MODE_NEUTRAL:
                try { return mContext.getColor(android.R.color.system_neutral1_400); } catch (Throwable t) { break; }
            default: break;
        }
        return getContrastingTextColour(bgColor);
    }

    private static boolean isSystemTextColorMode(int mode) {
        return mode == Prefs.TEXT_COLOR_MODE_ACCENT_LIGHT
                || mode == Prefs.TEXT_COLOR_MODE_ACCENT
                || mode == Prefs.TEXT_COLOR_MODE_ACCENT_DARK
                || mode == Prefs.TEXT_COLOR_MODE_TERTIARY
                || mode == Prefs.TEXT_COLOR_MODE_NEUTRAL;
    }

    private static boolean isSystemColorMode(int mode) {
        return mode == Prefs.COLOR_MODE_ACCENT
                || mode == Prefs.COLOR_MODE_ACCENT_LIGHT
                || mode == Prefs.COLOR_MODE_ACCENT_DARK
                || mode == Prefs.COLOR_MODE_SECONDARY
                || mode == Prefs.COLOR_MODE_TERTIARY
                || mode == Prefs.COLOR_MODE_NEUTRAL
                || mode == Prefs.COLOR_MODE_NEUTRAL_VAR;
    }

    private int getIndicatorColor(Context context) {
        if (mIndicatorColorMode == Prefs.COLOR_MODE_ACCENT_LIGHT) {
            int base = resolveAccentColour(context);
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(base, hsv);
            hsv[1] = Math.max(0f, hsv[1] - 0.55f);
            hsv[2] = Math.min(1f, hsv[2] + 0.30f);
            return android.graphics.Color.HSVToColor(hsv) | 0xFF000000;
        } else if (mIndicatorColorMode == Prefs.COLOR_MODE_ACCENT_DARK) {
            int base = resolveAccentColour(context);
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(base, hsv);
            hsv[2] = Math.max(0f, hsv[2] - 0.50f);
            return android.graphics.Color.HSVToColor(hsv) | 0xFF000000;
        } else if (mIndicatorColorMode == Prefs.COLOR_MODE_SECONDARY) {
            try { return context.getColor(android.R.color.system_accent2_600); } catch (Throwable ignored) {}
        } else if (mIndicatorColorMode == Prefs.COLOR_MODE_TERTIARY) {
            try { return context.getColor(android.R.color.system_accent3_600); } catch (Throwable ignored) {}
        } else if (mIndicatorColorMode == Prefs.COLOR_MODE_NEUTRAL) {
            try { return context.getColor(android.R.color.system_neutral1_400); } catch (Throwable ignored) {}
        } else if (mIndicatorColorMode == Prefs.COLOR_MODE_NEUTRAL_VAR) {
            try { return context.getColor(android.R.color.system_neutral2_400); } catch (Throwable ignored) {}
        } else if (mIndicatorColorMode == Prefs.COLOR_MODE_WHITE) {
            return 0xFFFFFFFF;
        } else if (mIndicatorColorMode == Prefs.COLOR_MODE_BLACK) {
            return 0xFF1C1B1F;
        } else if (mIndicatorColorMode == Prefs.COLOR_MODE_CUSTOM) {
            return mIndicatorCustomColor;
        }
        return resolveAccentColour(context);
    }

    private int resolveAccentColour(Context context) {
        try { return context.getColor(android.R.color.system_accent1_600); }
        catch (Throwable t) { return 0xFF1E1E1E; }
    }

    // ── Inner drawables / views ───────────────────────────────────────────────

    private int getContrastingTextColour(int bg) {
        double r = Color.red(bg)/255.0, g = Color.green(bg)/255.0, b = Color.blue(bg)/255.0;
        r = r<=0.03928?r/12.92:Math.pow((r+0.055)/1.055, 2.4);
        g = g<=0.03928?g/12.92:Math.pow((g+0.055)/1.055, 2.4);
        b = b<=0.03928?b/12.92:Math.pow((b+0.055)/1.055, 2.4);
        return (0.2126*r + 0.7152*g + 0.0722*b) < 0.35 ? Color.WHITE : Color.BLACK;
    }

    private void showIndicator(float fingerX, float linearBrightness) {
        if (mIndicatorView == null || mIndicatorTextView == null
                || mWindowManager == null || mMainHandler == null
                || mIndicatorParams == null) return;
        if (!mOverlayEnabled) return;

        mMainHandler.removeCallbacks(mDismissIndicator);

        // Fraction is the same value used to compute brightness and the % label,
        // so the pill's position is always 1-to-1 with the brightness value.
        float usable = mScreenWidth - 2f * mEdgePaddingPx;
        float fraction = usable <= 0
                ? Math.max(0f, Math.min(1f, fingerX / mScreenWidth))
                : Math.max(0f, Math.min(1f, (fingerX - mEdgePaddingPx) / usable));
        int pct = Math.round(fraction * 100f);

        // Center the pill on the fraction point within the usable range.
        // The pill arrives at its endpoints exactly when brightness reaches 0 / 100%
        // — no secondary snap from a disconnected pixel clamp.
        float centerX = mEdgePaddingPx + fraction * usable;
        int xOffset = Math.max(0, Math.min(mScreenWidth - mIndicatorW,
                Math.round(centerX - mIndicatorW / 2f)));
        int yOffset = (int)(Math.max(mScreenWidth, mScreenHeight) * mIndicatorYPosition / 100f);

        mIndicatorParams.x = xOffset;

        try {
            mIndicatorTextView.setText(pct + "%");
            float targetAlpha = mIndicatorAlpha / 100f;
            if (!mIndicatorAttached) {
                if (mHideAnimator != null) { mHideAnimator.cancel(); mHideAnimator = null; }
                mIndicatorParams.y = -mIndicatorH;
                mWindowManager.addView(mIndicatorView, mIndicatorParams);
                mIndicatorAttached = true;
                startSlideIn(yOffset, targetAlpha);
            } else {
                if (mHideAnimator != null) { mHideAnimator.cancel(); mHideAnimator = null; }
                if (!mSlideInAnimating) {
                    mIndicatorParams.y = yOffset;
                    mIndicatorParams.alpha = targetAlpha;
                }
                mWindowManager.updateViewLayout(mIndicatorView, mIndicatorParams);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": showIndicator failed: " + t);
        }
    }

    private void startSlideIn(int targetY, float targetAlpha) {
        if (mSlideInAnimator != null) mSlideInAnimator.cancel();
        mSlideInAnimating = true;

        mIndicatorParams.alpha = targetAlpha;
        try { mWindowManager.updateViewLayout(mIndicatorView, mIndicatorParams); }
        catch (Throwable ignored) {}

        ValueAnimator anim = ValueAnimator.ofInt(-mIndicatorH, targetY);
        anim.setDuration(220);
        anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(va -> {
            mIndicatorParams.y = (int) va.getAnimatedValue();
            try { mWindowManager.updateViewLayout(mIndicatorView, mIndicatorParams); }
            catch (Throwable ignored) {}
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                mSlideInAnimating = false;
                mIndicatorParams.y = targetY;
                try { mWindowManager.updateViewLayout(mIndicatorView, mIndicatorParams); }
                catch (Throwable ignored) {}
            }
        });
        mSlideInAnimator = anim;
        anim.start();
    }

    private int linearToDisplayPct(float linear) {
        try {
            if (mConvertLinearToGammaMethod != null) {
                int gammaVal = (int) mConvertLinearToGammaMethod.invoke(
                        null, linear, mBrightnessMin, mBrightnessMax);
                return Math.max(0, Math.min(100,
                        Math.round((float) gammaVal / GAMMA_SPACE_MAX * 100f)));
            }
        } catch (Throwable ignored) {}
        float range = mBrightnessMax - mBrightnessMin;
        if (range <= 0) return 0;
        float n = Math.max(0f, Math.min(1f, (linear - mBrightnessMin) / range));
        return Math.max(0, Math.min(100,
                Math.round((float) Math.pow(n, 1.0 / GAMMA) * 100f)));
    }

    private void hideIndicator() {
        if (mIndicatorView == null || mWindowManager == null || mMainHandler == null) return;
        mMainHandler.removeCallbacks(mDismissIndicator);
        if (mSlideInAnimator != null) { mSlideInAnimator.cancel(); mSlideInAnimator = null; mSlideInAnimating = false; }
        if (mHideAnimator != null) { mHideAnimator.cancel(); mHideAnimator = null; }
        if (!mIndicatorAttached) return;

        float startAlpha = mIndicatorParams != null ? mIndicatorParams.alpha : 0f;
        ValueAnimator anim = ValueAnimator.ofFloat(startAlpha, 0f);
        anim.setDuration(200);
        anim.addUpdateListener(va -> {
            if (mIndicatorParams != null) {
                mIndicatorParams.alpha = (float) va.getAnimatedValue();
                try { mWindowManager.updateViewLayout(mIndicatorView, mIndicatorParams); }
                catch (Throwable ignored) {}
            }
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                mHideAnimator = null;
                if (mIndicatorAttached && mWindowManager != null) {
                    try { mWindowManager.removeView(mIndicatorView); }
                    catch (Throwable ignored) {}
                    mIndicatorAttached = false;
                }
            }
        });
        mHideAnimator = anim;
        anim.start();
    }

    // ── Touch routing ─────────────────────────────────────────────────────────

    private boolean handleTouchEvent(MotionEvent ev, boolean isStatusBarView) {
        if (mDisplayManager == null || mScreenWidth == 0) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:   return onDown(ev, isStatusBarView);
            case MotionEvent.ACTION_MOVE:   return onMove(ev);
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: return onUpOrCancel(ev);
            default: return false;
        }
    }

    private boolean onDown(MotionEvent ev, boolean isStatusBarView) {
        mGestureActive = false;
        mTouchStartedInStatusBar = false;
        boolean inRegion = isStatusBarView
                || (ev.getY() <= mScreenHeight * STATUS_BAR_Y_FRACTION);
        if (!inRegion) return false;
        mTouchStartedInStatusBar = true;
        mDownX = ev.getX();
        mDownY = ev.getY();
        // Consuming DOWN on the status bar view stops the GestureDetector inside
        // onTouchEvent from seeing it, which prevents the long-press-to-QS timer.
        // The shade-swipe gesture is unaffected (it tracks at the window level).
        if (isStatusBarView && mBlockLongPressQS) return true;
        return false;
    }

    private boolean onMove(MotionEvent ev) {
        if (!mTouchStartedInStatusBar) return false;
        float absDX = Math.abs(ev.getX() - mDownX);
        float absDY = Math.abs(ev.getY() - mDownY);
        if (!mGestureActive) {
            if (absDX <= mGestureSlopPx || absDX <= absDY * mHorizontalRatio) return false;
            mGestureActive = true;
            cancelShadeGesture(ev.getDownTime());
            if (mHapticEnabled && mStatusBarView != null) {
                mStatusBarView.performHapticFeedback(
                        android.view.HapticFeedbackConstants.GESTURE_START);
            }
        }
        float brightness = computeBrightness(ev.getX());
        setTemporaryBrightness(brightness);
        showIndicator(ev.getX(), brightness);
        return true;
    }

    private void cancelShadeGesture(long downTime) {
        if (mShadeWindowView == null || mMainHandler == null) return;
        mMainHandler.post(() -> {
            if (mShadeWindowView == null) return;
            mSendingCancel = true;
            MotionEvent cancel = MotionEvent.obtain(
                    downTime, android.os.SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_CANCEL, mDownX, mDownY, 0);
            try { mShadeWindowView.dispatchTouchEvent(cancel); }
            catch (Throwable ignored) {}
            cancel.recycle();
            mSendingCancel = false;
        });
    }

    private boolean onUpOrCancel(MotionEvent ev) {
        if (!mGestureActive) { mTouchStartedInStatusBar = false; return false; }
        boolean cancelled = ev.getActionMasked() == MotionEvent.ACTION_CANCEL;
        float finalBrightness = cancelled
                ? getCurrentBrightness()
                : computeBrightness(ev.getX());
        setTemporaryBrightness(finalBrightness);
        commitBrightness(finalBrightness);
        if (mMainHandler != null)
            mMainHandler.postDelayed(mDismissIndicator, INDICATOR_DISMISS_DELAY_MS);
        mGestureActive = false;
        mTouchStartedInStatusBar = false;
        return true;
    }

    // ── Brightness computation ────────────────────────────────────────────────

    private float computeBrightness(float fingerX) {
        if (mBrightnessMin < 0) readBrightnessRange();
        float usable = mScreenWidth - 2f * mEdgePaddingPx;
        float fraction = usable <= 0
                ? Math.max(0f, Math.min(1f, fingerX / mScreenWidth))
                : Math.max(0f, Math.min(1f, (fingerX - mEdgePaddingPx) / usable));
        float gammaCorrected = (float) Math.pow(fraction, GAMMA);
        return Math.max(mBrightnessMin,
                Math.min(mBrightnessMax,
                        mBrightnessMin + gammaCorrected * (mBrightnessMax - mBrightnessMin)));
    }

    // ── Hidden API calls ──────────────────────────────────────────────────────

    private void setTemporaryBrightness(float brightness) {
        if (mSetTemporaryBrightnessMethod == null) return;
        try { mSetTemporaryBrightnessMethod.invoke(
                mDisplayManager, Display.DEFAULT_DISPLAY, brightness); }
        catch (Throwable t) { XposedBridge.log(TAG + ": setTemporaryBrightness: " + t); }
    }

    private void commitBrightness(float brightness) {
        if (mSetBrightnessMethod == null) return;
        mBgExecutor.execute(() -> {
            try { mSetBrightnessMethod.invoke(
                    mDisplayManager, Display.DEFAULT_DISPLAY, brightness); }
            catch (Throwable t) { XposedBridge.log(TAG + ": setBrightness: " + t); }
        });
    }

    private float getCurrentBrightness() {
        try {
            if (mGetBrightnessInfoMethod == null || mBrightnessField == null) return 0.5f;
            Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
            if (display == null) return 0.5f;
            Object info = mGetBrightnessInfoMethod.invoke(display);
            if (info == null) return 0.5f;
            float b = (float) mBrightnessField.get(info);
            return Math.max(mBrightnessMin, Math.min(mBrightnessMax, b));
        } catch (Throwable t) { return 0.5f; }
    }

    // ── Fullscreen swipe overlay ──────────────────────────────────────────────

    private void initFullscreenTouchOverlay(Context context) {
        try {
            // A 1px-tall always-non-touchable overlay exists solely so that
            // getRootWindowInsets().isVisible(statusBars()) can be queried from an
            // app-level window — the SystemUI window always "owns" the bar and would
            // return false, but this overlay correctly reflects the global inset state.
            mOverlayContext = context;
            android.view.View probeView = new android.view.View(context);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    (int)(64 * mDensity),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSPARENT);
            params.gravity = Gravity.TOP | Gravity.START;
            mFullscreenTouchView   = probeView;
            mFullscreenTouchParams = params;
            updateFullscreenTouch();
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": initFullscreenTouchOverlay failed: " + t);
        }
    }

    // Returns true when an app has hidden the status bar (fullscreen mode).
    // Queried from our app-level overlay window so the insets reflect the real state,
    // not SystemUI's internal perspective (which always "owns" the bar).
    private boolean isStatusBarHidden() {
        if (!mFullscreenTouchAttached) return false;
        try {
            if (!mFullscreenTouchView.isAttachedToWindow()) return false;
            WindowInsets wi = mFullscreenTouchView.getRootWindowInsets();
            return wi != null && !wi.isVisible(WindowInsets.Type.statusBars());
        } catch (Throwable t) { return false; }
    }

    // Height of the top strip to monitor for the fullscreen brightness gesture.
    // Uses the live status-bar-view height when available; falls back to the fraction.
    private float getStripHeight() {
        if (mStatusBarView != null && mStatusBarView.getHeight() > 0)
            return mStatusBarView.getHeight() * 1.5f; // a bit taller for easier targeting
        return mScreenHeight * STATUS_BAR_Y_FRACTION;
    }

    private void initGestureMonitor() {
        if (mGestureReceiver != null) return;
        try {
            // Context.INPUT_SERVICE returns the real InputManager; no static getInstance().
            android.hardware.input.InputManager im =
                    (android.hardware.input.InputManager) mOverlayContext
                            .getSystemService(Context.INPUT_SERVICE);
            mInputMonitor = im.getClass()
                    .getMethod("monitorGestureInput", String.class, int.class)
                    .invoke(im, "BrightnessGesture", Display.DEFAULT_DISPLAY);

            android.view.InputChannel channel = (android.view.InputChannel)
                    mInputMonitor.getClass().getMethod("getInputChannel").invoke(mInputMonitor);

            final Object monitor = mInputMonitor;
            mGestureReceiver = new android.view.InputEventReceiver(
                    channel, mMainHandler.getLooper()) {
                @Override
                public void onInputEvent(android.view.InputEvent event) {
                    try {
                        if (event instanceof MotionEvent
                                && mGestureEnabled && mFullscreenSwipe
                                && isStatusBarHidden()) {
                            MotionEvent me = (MotionEvent) event;
                            int action = me.getActionMasked();
                            // Only start tracking gestures that begin in the top strip.
                            // For in-progress gestures, mTouchStartedInStatusBar stays true.
                            boolean inStrip = (action == MotionEvent.ACTION_DOWN)
                                    ? me.getY() <= getStripHeight()
                                    : mTouchStartedInStatusBar;
                            if (inStrip) {
                                if (action == MotionEvent.ACTION_DOWN) {
                                    // Post a delayed pilfer that fires before the ROM's
                                    // long-press-QS timer (~500 ms). If the user swipes
                                    // clearly downward before then we cancel it so QS can
                                    // still open; taps cancel it on UP so they pass through.
                                    mPilferPending = true;
                                    if (mPilferRunnable != null)
                                        mMainHandler.removeCallbacks(mPilferRunnable);
                                    mPilferRunnable = () -> {
                                        if (mPilferPending) {
                                            mPilferPending = false;
                                            try {
                                                monitor.getClass()
                                                        .getMethod("pilferPointers")
                                                        .invoke(monitor);
                                            } catch (Throwable pt) {
                                                XposedBridge.log(TAG + ": delayed pilfer failed: " + pt);
                                            }
                                        }
                                    };
                                    mMainHandler.postDelayed(mPilferRunnable, 200);
                                } else if (action == MotionEvent.ACTION_UP
                                        || action == MotionEvent.ACTION_CANCEL) {
                                    mPilferPending = false;
                                    if (mPilferRunnable != null) {
                                        mMainHandler.removeCallbacks(mPilferRunnable);
                                        mPilferRunnable = null;
                                    }
                                }
                                boolean wasActive = mGestureActive;
                                handleTouchEvent(me, true);
                                if (!wasActive && mGestureActive) {
                                    // Brightness threshold crossed — steal immediately.
                                    mPilferPending = false;
                                    if (mPilferRunnable != null) {
                                        mMainHandler.removeCallbacks(mPilferRunnable);
                                        mPilferRunnable = null;
                                    }
                                    try {
                                        monitor.getClass()
                                                .getMethod("pilferPointers")
                                                .invoke(monitor);
                                    } catch (Throwable pt) {
                                        XposedBridge.log(TAG + ": pilfer failed: " + pt);
                                    }
                                }
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": gestureMonitor: " + t);
                    }
                    // Never mark as consumed — the monitor is a pure observer; pilfering
                    // is the mechanism for stealing the gesture when we decide to take it.
                    finishInputEvent(event, false);
                }
            };
            XposedBridge.log(TAG + ": gesture monitor started");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": initGestureMonitor failed: " + t);
        }
    }

    private void destroyGestureMonitor() {
        if (mGestureReceiver != null) {
            try { mGestureReceiver.dispose(); } catch (Throwable ignored) {}
            mGestureReceiver = null;
        }
        if (mInputMonitor != null) {
            try { mInputMonitor.getClass().getMethod("close").invoke(mInputMonitor); }
            catch (Throwable ignored) {}
            mInputMonitor = null;
        }
    }

    private void updateFullscreenTouch() {
        if (mFullscreenTouchView == null || mWindowManager == null) return;
        if (mFullscreenSwipe && !mFullscreenTouchAttached) {
            try {
                mWindowManager.addView(mFullscreenTouchView, mFullscreenTouchParams);
                mFullscreenTouchAttached = true;
                if (mMainHandler != null) mMainHandler.post(this::initGestureMonitor);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": fullscreen overlay add failed: " + t);
            }
        } else if (!mFullscreenSwipe && mFullscreenTouchAttached) {
            try {
                destroyGestureMonitor();
                mWindowManager.removeView(mFullscreenTouchView);
                mFullscreenTouchAttached = false;
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": fullscreen overlay remove failed: " + t);
            }
        }
    }
}