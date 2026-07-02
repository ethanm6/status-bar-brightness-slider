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

import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
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
    // Exponent applied to finger position when mapping to the brightness float. 1.0 = linear.
    private static final float BRIGHTNESS_CURVE = 2.2f;
    // Privileged SystemUI window type for the indicator. TYPE_VOLUME_OVERLAY (2020)
    // layers ABOVE the status bar (like the volume panel) so the indicator slides over
    // it during the recoil/retract animations, and it's exempt from the 0.8-alpha clamp
    // applied to ordinary app overlays. showIndicator() falls back to
    // TYPE_APPLICATION_OVERLAY if the ROM rejects it.
    private static final int TYPE_INDICATOR_WINDOW = 2020;
    private static final long INDICATOR_DISMISS_DELAY_MS = 0;

    // ── Per-gesture state ─────────────────────────────────────────────────────

    private float mDownX;
    private float mDownY;
    private float mLastTargetBrightness = -1f;
    private boolean mGestureActive = false;
    private boolean mTouchStartedInStatusBar = false;
    // downTime of the touch stream currently owned by the real PhoneStatusBarView window.
    // In peek mode (transient status bar in fullscreen) the SBV receives the touches while
    // isStatusBarHidden() is still true, so the gesture monitor would double-process the
    // same stream and its pilferPointers() would CANCEL the SBV stream, killing the
    // gesture. The monitor skips any stream whose downTime matches this.
    private volatile long mSbvStreamDownTime = -1;

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

    private android.view.View mIndicatorView;   // root window view (pill or droplet)
    private TextView mIndicatorTextView;        // inner text view for setText (pill only)
    private DropletView mDropletView;         // custom view for the droplet shape
    private int mIndicatorShadowPad = 0;        // wrapper padding when shadow is enabled
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
    private volatile boolean mIndicatorShadow   = Prefs.DEFAULT_INDICATOR_SHADOW == 1;
    private volatile boolean mReverseSlider     = Prefs.DEFAULT_REVERSE_SLIDER == 1;
    private volatile int mIndicatorShape        = Prefs.DEFAULT_INDICATOR_SHAPE;
    private volatile int mMainLight             = Prefs.DEFAULT_MAIN_LIGHT;
    private volatile int mMainDark              = Prefs.DEFAULT_MAIN_DARK;

    // ── Fullscreen touch overlay ──────────────────────────────────────────────

    private android.view.View mStatusBarView;
    private android.view.View mShadeWindowView;        // NotificationShadeWindowView reference
    private volatile boolean mSendingCancel = false;   // true while dispatching synthetic CANCEL to shade
    // true from DOWN until 200ms after UP (or until gesture confirmed vertical);
    // used by the shade-expansion hook to suppress QS during brightness gestures.
    private volatile boolean mSuppressShadeExpand = false;
    private final Runnable mClearSuppressShadeExpand = () -> mSuppressShadeExpand = false;
    private android.view.View mFullscreenTouchView;   // insets probe only — always FLAG_NOT_TOUCHABLE
    private WindowManager.LayoutParams mFullscreenTouchParams;
    private boolean mFullscreenTouchAttached = false;
    // Updated asynchronously by an insets listener so isStatusBarHidden() never has to make
    // a synchronous getRootWindowInsets() call on the touch-dispatch path (that call added
    // enough latency to lose the race against the ROM's own edge-swipe gesture monitor).
    private volatile boolean mCachedSbHidden = false;
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
        suppressShadeExpansion(lpparam.classLoader, hookMethodFn);
        hookFlingToHeight(lpparam.classLoader, hookMethodFn);
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
        loadPrefsFromSecure(context);

        // Broadcast receiver for live updates when the user changes a setting.
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (Intent.ACTION_CONFIGURATION_CHANGED.equals(action)) {
                    if (isSystemColorMode(mIndicatorColorMode) || isSystemTextColorMode(mTextColorMode)) reinitIndicator();
                    return;
                }

                // The app has no WRITE_SECURE_SETTINGS grant, so it asks us to persist
                // each change; we hold the permission (running in SystemUI).
                String changedKey = null;
                if (Prefs.ACTION_SET_PREF.equals(action)) {
                    changedKey = intent.getStringExtra(Prefs.EXTRA_KEY);
                    int value = intent.getIntExtra(Prefs.EXTRA_VALUE, 0);
                    if (changedKey != null) {
                        try {
                            Settings.Secure.putInt(mContext.getContentResolver(), changedKey, value);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + ": setPref write failed: " + t);
                        }
                    }
                } else if (!Prefs.ACTION_PREFS_CHANGED.equals(action)) {
                    return;
                }

                // Reload everything from Secure (the source of truth we just updated) and apply.
                boolean prevGesture = mGestureEnabled;
                int prevColorMode = mIndicatorColorMode, prevCustom = mIndicatorCustomColor;
                int prevTextMode = mTextColorMode, prevTextCustom = mTextCustomColor;
                boolean prevShadow = mIndicatorShadow;
                int prevShape = mIndicatorShape;
                int prevMainLight = mMainLight, prevMainDark = mMainDark;

                loadPrefsFromSecure(mContext);
                applyAutoBrightness();

                boolean needsReinit = prevColorMode != mIndicatorColorMode
                        || prevCustom != mIndicatorCustomColor
                        || prevTextMode != mTextColorMode
                        || prevTextCustom != mTextCustomColor
                        || prevShadow != mIndicatorShadow
                        || prevShape != mIndicatorShape
                        || prevMainLight != mMainLight
                        || prevMainDark != mMainDark;

                updateFullscreenTouch();
                if (needsReinit) reinitIndicator();
                if (prevGesture && !mGestureEnabled && mIndicatorAttached) {
                    hideIndicator();
                }
            }
        };

        IntentFilter filter = new IntentFilter(Prefs.ACTION_PREFS_CHANGED);
        filter.addAction(Prefs.ACTION_SET_PREF);
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

    /** Read every pref from Settings.Secure into the in-memory fields, then re-tune. */
    private void loadPrefsFromSecure(Context context) {
        try {
            android.content.ContentResolver cr = context.getContentResolver();
            mGestureEnabled = Settings.Secure.getInt(cr,
                    Prefs.KEY_GESTURE_ENABLED, Prefs.DEFAULT_GESTURE_ENABLED) == 1;
            mOverlayEnabled = Settings.Secure.getInt(cr,
                    Prefs.KEY_OVERLAY_ENABLED, Prefs.DEFAULT_OVERLAY_ENABLED) == 1;
            mBlockLongPressQS = Settings.Secure.getInt(cr,
                    Prefs.KEY_BLOCK_LONGPRESS_QS, Prefs.DEFAULT_BLOCK_LONGPRESS_QS) == 1;
            mFullscreenSwipe = Settings.Secure.getInt(cr,
                    Prefs.KEY_FULLSCREEN_SWIPE, Prefs.DEFAULT_FULLSCREEN_SWIPE) == 1;
            mHapticEnabled = Settings.Secure.getInt(cr,
                    Prefs.KEY_HAPTIC_FEEDBACK, Prefs.DEFAULT_HAPTIC_FEEDBACK) == 1;
            mSensitivity = Settings.Secure.getInt(cr,
                    Prefs.KEY_SENSITIVITY, Prefs.DEFAULT_SENSITIVITY);
            mEdgePaddingDp = Settings.Secure.getInt(cr,
                    Prefs.KEY_EDGE_PADDING_DP, Prefs.DEFAULT_EDGE_PADDING_DP);
            mIndicatorColorMode = Settings.Secure.getInt(cr,
                    Prefs.KEY_INDICATOR_COLOR_MODE, Prefs.DEFAULT_INDICATOR_COLOR_MODE);
            mIndicatorCustomColor = Settings.Secure.getInt(cr,
                    Prefs.KEY_INDICATOR_CUSTOM_COLOR, Prefs.DEFAULT_INDICATOR_CUSTOM_COLOR);
            mIndicatorAlpha = Settings.Secure.getInt(cr,
                    Prefs.KEY_INDICATOR_ALPHA, Prefs.DEFAULT_INDICATOR_ALPHA);
            mTextColorMode = Settings.Secure.getInt(cr,
                    Prefs.KEY_INDICATOR_TEXT_COLOR_MODE, Prefs.DEFAULT_INDICATOR_TEXT_COLOR_MODE);
            mTextCustomColor = Settings.Secure.getInt(cr,
                    Prefs.KEY_INDICATOR_TEXT_CUSTOM_COLOR, Prefs.DEFAULT_INDICATOR_TEXT_CUSTOM_COLOR);
            mIndicatorYPosition = Settings.Secure.getInt(cr,
                    Prefs.KEY_INDICATOR_Y_POSITION, Prefs.DEFAULT_INDICATOR_Y_POSITION);
            mIndicatorShadow = Settings.Secure.getInt(cr,
                    Prefs.KEY_INDICATOR_SHADOW, Prefs.DEFAULT_INDICATOR_SHADOW) == 1;
            mReverseSlider = Settings.Secure.getInt(cr,
                    Prefs.KEY_REVERSE_SLIDER, Prefs.DEFAULT_REVERSE_SLIDER) == 1;
            mIndicatorShape = Settings.Secure.getInt(cr,
                    Prefs.KEY_INDICATOR_SHAPE, Prefs.DEFAULT_INDICATOR_SHAPE);
            mMainLight = Settings.Secure.getInt(cr, Prefs.KEY_MAIN_LIGHT, Prefs.DEFAULT_MAIN_LIGHT);
            mMainDark = Settings.Secure.getInt(cr, Prefs.KEY_MAIN_DARK, Prefs.DEFAULT_MAIN_DARK);
            applyTuning();
        } catch (Throwable t) {
            mGestureEnabled = true;
            mOverlayEnabled = true;
            mBlockLongPressQS = false;
            mFullscreenSwipe = false;
            mSensitivity = Prefs.DEFAULT_SENSITIVITY;
            mEdgePaddingDp = Prefs.DEFAULT_EDGE_PADDING_DP;
            applyTuning();
            XposedBridge.log(TAG + ": Settings.Secure read failed, defaulting: " + t);
        }
    }

    /** Apply the persisted KEY_AUTO_BRIGHTNESS value to Settings.System (needs SystemUI perms). */
    private void applyAutoBrightness() {
        try {
            android.content.ContentResolver cr = mContext.getContentResolver();
            boolean autoBrightness = Settings.Secure.getInt(cr,
                    Prefs.KEY_AUTO_BRIGHTNESS, Prefs.DEFAULT_AUTO_BRIGHTNESS) == 1;
            int currentMode = Settings.System.getInt(cr,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            boolean alreadyAuto = currentMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            if (autoBrightness) {
                if (!alreadyAuto) {
                    int current = Settings.System.getInt(cr,
                            Settings.System.SCREEN_BRIGHTNESS, 128);
                    Settings.Secure.putInt(cr, Prefs.KEY_SAVED_BRIGHTNESS, current);
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC);
                }
            } else {
                if (alreadyAuto) {
                    Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE,
                            Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    int saved = Settings.Secure.getInt(cr, Prefs.KEY_SAVED_BRIGHTNESS, -1);
                    if (saved >= 0) {
                        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, saved);
                    }
                }
            }
        } catch (SecurityException ignored) {}
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
                    // Mark this stream as owned by the real status bar window so the
                    // gesture monitor leaves it alone (peek mode double-processing guard).
                    if (isStatusBarView
                            && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        mSbvStreamDownTime = ev.getDownTime();
                    }
                    // Capture before handleTouchEvent mutates it — reflects whether a real
                    // brightness drag was already underway going into this event.
                    boolean wasGestureActive = mGestureActive;
                    boolean handled = handleTouchEvent(ev, isStatusBarView);
                    // While suppress is armed AND an actual drag is in progress, also consume
                    // the shade window's top-strip events so the ROM's own re-expansion timer
                    // can't reopen the shade mid-drag. Plain taps (no drag ever starts) are
                    // left alone so they still reach the app underneath.
                    boolean inTopStrip = ev.getY() <= mScreenHeight * STATUS_BAR_Y_FRACTION;
                    boolean consume = handled || (mSuppressShadeExpand
                            && (isStatusBarView || (inTopStrip && wasGestureActive)));
                    if (consume) {
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

            mIndicatorTextView = null;
            mDropletView = null;
            if (mIndicatorShape == Prefs.INDICATOR_SHAPE_DROPLET) {
                initIndicatorDroplet(context, accent, textCol, d);
            } else {
                initIndicatorPill(context, accent, textCol, d);
            }

            // See TYPE_INDICATOR_WINDOW: a privileged SystemUI type that layers above the
            // status bar and is exempt from the untrusted-touch 0.8-alpha clamp that
            // afflicts ordinary FLAG_NOT_TOUCHABLE app overlays. showIndicator() falls
            // back to TYPE_APPLICATION_OVERLAY if the ROM rejects it.
            mIndicatorParams = new WindowManager.LayoutParams(
                    mIndicatorW, mIndicatorH,
                    TYPE_INDICATOR_WINDOW,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            // NO_LIMITS: don't clip the window to the content area, so it
                            // can slide up over the status bar and off the top edge instead
                            // of vanishing at the status-bar boundary.
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
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

        mIndicatorTextView = tv;
        mIndicatorShadowPad = 0;
        if (mIndicatorShadow) {
            // Shadow drawn with Paint.setShadowLayer using the SAME parameters as
            // the settings preview (blur 8dp, dy 3dp, 0x66000000) so they match —
            // elevation shadows render much lighter. The wrapper is padded and
            // non-clipping so the blur has room; software layer is required for
            // setShadowLayer on shapes.
            final int pad = (int)(16 * d);
            final float blur = 8 * d, dy = 3 * d;
            final int pillColor = accent;
            android.widget.FrameLayout wrap = new android.widget.FrameLayout(context) {
                private final android.graphics.Paint mShadowPaint =
                        new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
                @Override
                protected void onDraw(android.graphics.Canvas canvas) {
                    super.onDraw(canvas);
                    float l = pad, t = pad;
                    float r = getWidth() - pad, b = getHeight() - pad;
                    float rad = (b - t) / 2f;
                    // Same fill color as the pill so anti-aliased edges blend;
                    // the TextView's own background draws on top of this rect.
                    mShadowPaint.setColor(pillColor);
                    mShadowPaint.setShadowLayer(blur, 0, dy, 0x66000000);
                    canvas.drawRoundRect(l, t, r, b, rad, rad, mShadowPaint);
                }
            };
            wrap.setWillNotDraw(false);
            wrap.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);
            wrap.setClipChildren(false);
            wrap.setClipToPadding(false);
            wrap.setPadding(pad, pad, pad, pad);
            // Fixed child size (measured at "100%") so the pill width doesn't
            // shrink as the text gets shorter — same behavior as the no-shadow
            // window, which is sized once from this measurement.
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(
                            tv.getMeasuredWidth(), tv.getMeasuredHeight(),
                            Gravity.CENTER);
            wrap.addView(tv, lp);
            mIndicatorShadowPad = pad;
            mIndicatorView = wrap;
            mIndicatorW = tv.getMeasuredWidth()  + 2 * pad;
            mIndicatorH = tv.getMeasuredHeight() + 2 * pad;
        } else {
            mIndicatorView = tv;
            mIndicatorW = tv.getMeasuredWidth();
            mIndicatorH = tv.getMeasuredHeight();
        }
    }

    private void initIndicatorDroplet(Context context, int accent, int textCol, float d) {
        final float textSizePx = 13f * context.getResources()
                .getDisplayMetrics().scaledDensity;
        Paint measure = new Paint(Paint.ANTI_ALIAS_FLAG);
        float r = IndicatorDrawing.bulbRadius(d, textSizePx, measure);
        // Shadow needs blur room around the drop; the window is sized to include it.
        int pad = mIndicatorShadow ? (int)(16 * d) : 0;
        mIndicatorShadowPad = pad;
        mIndicatorW = IndicatorDrawing.dropletWidth(r) + 2 * pad;
        mIndicatorH = IndicatorDrawing.dropletHeight(r) + 2 * pad;

        DropletView view = new DropletView(context, accent, textCol, textSizePx,
                mIndicatorShadow, d, pad);
        if (mIndicatorShadow) {
            view.setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null);
        }
        mDropletView = view;
        mIndicatorView = view;
    }

    /** Custom view that canvas-draws the point-up droplet (shared with the preview). */
    private static final class DropletView extends android.view.View {
        private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint mText = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int mFillColor, mTextColor;
        private final float mTextSizePx, mDensity, mPad;
        private final boolean mShadow;
        private String mValue = "100%";

        DropletView(Context c, int fill, int text, float textSizePx,
                     boolean shadow, float density, float pad) {
            super(c);
            mFillColor = fill; mTextColor = text; mTextSizePx = textSizePx;
            mShadow = shadow; mDensity = density; mPad = pad;
        }

        void setValue(String v) { mValue = v; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            IndicatorDrawing.drawDroplet(canvas, getWidth(), getHeight(), mPad,
                    mFillColor, 255, mTextColor, mTextSizePx,
                    mShadow, mDensity, mValue, mFill, mText);
        }
    }

    private void setIndicatorValue(String s) {
        if (mIndicatorTextView != null) mIndicatorTextView.setText(s);
        else if (mDropletView != null) mDropletView.setValue(s);
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
            mDropletView = null;
            mIndicatorParams   = null;
            if (mWindowManager != null) initIndicator(mContext);
        });
    }

    private int resolveTextColor(int bgColor) {
        switch (mTextColorMode) {
            case Prefs.TEXT_COLOR_MODE_WHITE:  return Color.WHITE;
            case Prefs.TEXT_COLOR_MODE_BLACK:  return Color.BLACK;
            case Prefs.TEXT_COLOR_MODE_CUSTOM: return mTextCustomColor;
            case Prefs.TEXT_COLOR_MODE_ACCENT_LIGHT:   // "Primary"
                try { return mContext.getColor(isNightMode(mContext)
                        ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_100); }
                catch (Throwable t) { return resolveAccentColour(mContext); }
            case Prefs.TEXT_COLOR_MODE_ACCENT:   // "Main" — reported slider inactive tick colour
                return (isNightMode(mContext) ? mMainDark : mMainLight) | 0xFF000000;
            case Prefs.TEXT_COLOR_MODE_ACCENT_DARK:    // "Secondary"
                try { return mContext.getColor(isNightMode(mContext)
                        ? android.R.color.system_accent2_200 : android.R.color.system_accent2_600); }
                catch (Throwable t) { return resolveAccentColour(mContext); }
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

    private int resolveAccentColour(Context context) {
        try { return context.getColor(android.R.color.system_accent1_600); }
        catch (Throwable t) { return 0xFF1E1E1E; }
    }

    private boolean isNightMode(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int getIndicatorColor(Context context) {
        if (mIndicatorColorMode == Prefs.COLOR_MODE_ACCENT_LIGHT) {   // "Primary"
            try { return context.getColor(isNightMode(context)
                    ? android.R.color.system_neutral1_800 : android.R.color.system_neutral1_100); }
            catch (Throwable ignored) { return resolveAccentColour(context); }
        } else if (mIndicatorColorMode == Prefs.COLOR_MODE_ACCENT_DARK) {  // "Secondary" — app secondary role
            try { return context.getColor(isNightMode(context)
                    ? android.R.color.system_accent2_200 : android.R.color.system_accent2_600); }
            catch (Throwable ignored) { return resolveAccentColour(context); }
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
        // "Main" (and default) — the reported slider inactive tick colour, per theme.
        return (isNightMode(context) ? mMainDark : mMainLight) | 0xFF000000;
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
        if (mIndicatorView == null
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
        // The pill tracks the finger (position fraction), but the % label shows the
        // brightness value — which is the mirrored fraction when the slider is reversed.
        int pct = Math.round((mReverseSlider ? 1f - fraction : fraction) * 100f);

        // Center the pill on the fraction point within the usable range.
        // The pill arrives at its endpoints exactly when brightness reaches 0 / 100%
        // — no secondary snap from a disconnected pixel clamp.
        // Clamp so the PILL (not the shadow-padded window) stops at the screen edges.
        float centerX = mEdgePaddingPx + fraction * usable;
        int xOffset = Math.max(-mIndicatorShadowPad,
                Math.min(mScreenWidth - mIndicatorW + mIndicatorShadowPad,
                        Math.round(centerX - mIndicatorW / 2f)));
        // Subtract the shadow wrapper padding so the pill's visual position is the
        // same whether or not the shadow (and its padded window) is enabled.
        int yOffset = (int)(Math.max(mScreenWidth, mScreenHeight) * mIndicatorYPosition / 100f)
                - mIndicatorShadowPad;

        mIndicatorParams.x = xOffset;

        try {
            setIndicatorValue(pct + "%");
            float targetAlpha = mIndicatorAlpha / 100f;
            if (!mIndicatorAttached) {
                if (mHideAnimator != null) { mHideAnimator.cancel(); mHideAnimator = null; }
                mIndicatorParams.y = -mIndicatorH;
                try {
                    mWindowManager.addView(mIndicatorView, mIndicatorParams);
                } catch (Throwable addFail) {
                    if (mIndicatorParams.type != WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                        // ROM rejected the privileged type — fall back to a standard
                        // overlay (which the system will clamp to 0.8 alpha, but works).
                        XposedBridge.log(TAG + ": privileged indicator type rejected, "
                                + "falling back to TYPE_APPLICATION_OVERLAY: " + addFail);
                        mIndicatorParams.type =
                                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
                        mWindowManager.addView(mIndicatorView, mIndicatorParams);
                    } else {
                        throw addFail;
                    }
                }
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

    /** Damped-spring interpolator: overshoots then oscillates to a settle (springy bounce). */
    private static final class SpringInterpolator implements android.animation.TimeInterpolator {
        private final double freq, decay;
        SpringInterpolator(double oscillations, double decay) {
            this.freq = 2 * Math.PI * oscillations;
            this.decay = decay;
        }
        @Override public float getInterpolation(float t) {
            return (float) (1 - Math.exp(-decay * t) * Math.cos(freq * t));
        }
    }

    private void startSlideIn(int targetY, float targetAlpha) {
        if (mSlideInAnimator != null) mSlideInAnimator.cancel();
        mSlideInAnimating = true;

        mIndicatorParams.alpha = targetAlpha;
        try { mWindowManager.updateViewLayout(mIndicatorView, mIndicatorParams); }
        catch (Throwable ignored) {}

        ValueAnimator anim = ValueAnimator.ofInt(-mIndicatorH, targetY);
        anim.setDuration(280);
        // Overshoot → drops in past its resting spot and recoils back (a touch pronounced).
        anim.setInterpolator(new android.view.animation.OvershootInterpolator(1.6f));
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

    // Suppress ShadeControllerImpl.instantExpandShade while a brightness gesture is in
    // progress, so a horizontal swipe on the status bar can't also fling the shade open.
    // (The bottom-edge fullscreen swipe-up path is handled separately by collapsing the
    // shade after the ROM opens it — see cancelShadeGesture/collapseShadeIfOpen.)
    private void suppressShadeExpansion(ClassLoader cl, Method hookMethodFn) {
        try {
            Class<?> c = Class.forName(
                    "com.android.systemui.shade.ShadeControllerImpl", false, cl);
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (m.getName().equalsIgnoreCase("instantExpandShade")
                        && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    hookMethodFn.invoke(null, m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            if (mSuppressShadeExpand) param.setResult(null);
                        }
                    });
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": suppressShadeExpansion failed: " + t);
        }
    }

    // Root cause of the fullscreen shade flash (confirmed via stack trace): in fullscreen,
    // this ROM routes top-strip touches directly to NotificationShadeWindowView, so the
    // panel's TouchHandler tracks our brightness swipe. When our pilfer/synthetic cancel
    // ends that stream with ACTION_CANCEL, endMotionEvent force-expands the panel
    // (AOSP treats cancel-while-tracking as "expand"), flinging the shade open.
    // Fix: while a brightness gesture has suppress armed, redirect any panel fling to a
    // collapse (expand=false, target=0). The method still runs normally — no state
    // corruption — it just animates to closed instead of open.
    private void hookFlingToHeight(ClassLoader cl, Method hookMethodFn) {
        try {
            Class<?> c = Class.forName(
                    "com.android.systemui.shade.NotificationPanelViewController", false, cl);
            for (java.lang.reflect.Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("flingToHeight")) continue;
                final Class<?>[] p = m.getParameterTypes();
                m.setAccessible(true);
                hookMethodFn.invoke(null, m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!mSuppressShadeExpand) return;
                        // AOSP: flingToHeight(float vel, boolean expand, float target,
                        //                     float collapseSpeedUpFactor, boolean falsing)
                        for (int i = 0; i < p.length; i++) {
                            if (p[i] == boolean.class) { param.args[i] = false; break; }
                        }
                        if (p.length >= 3 && p[2] == float.class) param.args[2] = 0f;
                    }
                });
                XposedBridge.log(TAG + ": hooked flingToHeight "
                        + java.util.Arrays.toString(p));
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hookFlingToHeight failed: " + t);
        }
    }

    private void hideIndicator() {
        if (mIndicatorView == null || mWindowManager == null || mMainHandler == null) return;
        mMainHandler.removeCallbacks(mDismissIndicator);
        if (mSlideInAnimator != null) { mSlideInAnimator.cancel(); mSlideInAnimator = null; mSlideInAnimating = false; }
        if (mHideAnimator != null) { mHideAnimator.cancel(); mHideAnimator = null; }
        if (!mIndicatorAttached) return;

        final int startY = mIndicatorParams != null ? mIndicatorParams.y : 0;
        // Travel fully above the screen top so it clearly slides up past the status bar
        // before it's removed (extra clearance beyond just -mIndicatorH).
        final int endY = -mIndicatorH - Math.round(48 * mDensity);
        // Swift pull-away: accelerate upward out of view. No fade — it just slides out.
        ValueAnimator anim = ValueAnimator.ofInt(startY, endY);
        anim.setDuration(170);
        anim.setInterpolator(new android.view.animation.AccelerateInterpolator(1.5f));
        anim.addUpdateListener(va -> {
            if (mIndicatorParams != null) {
                mIndicatorParams.y = (int) va.getAnimatedValue();
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
        mLastTargetBrightness = -1f;
        // Cancel any delayed clear from the previous gesture, then reset the flag fresh.
        if (mMainHandler != null) mMainHandler.removeCallbacks(mClearSuppressShadeExpand);
        mSuppressShadeExpand = false;
        boolean inRegion = isStatusBarView
                || (ev.getY() <= mScreenHeight * STATUS_BAR_Y_FRACTION);
        if (!inRegion) return false;
        mTouchStartedInStatusBar = true;
        mDownX = ev.getX();
        mDownY = ev.getY();
        if (isStatusBarView) {
            boolean sbHidden = isStatusBarHidden();
            boolean blockForFullscreen = mFullscreenSwipe && sbHidden;
            if (blockForFullscreen) mSuppressShadeExpand = true;
            // Consuming DOWN skips the original onTouchEvent, which is what feeds the ROM's
            // long-press-to-QS GestureDetector — so we only do it when the user has enabled
            // long-press blocking, or when we need to suppress shade expansion for a
            // fullscreen/peek swipe. Otherwise let the original run so long-press and QS
            // pull-down behave natively; the status bar returns true for DOWN, so we still
            // receive MOVE/UP and start the brightness gesture on horizontal movement.
            return mBlockLongPressQS || blockForFullscreen;
        }
        return false;
    }

    private boolean onMove(MotionEvent ev) {
        if (!mTouchStartedInStatusBar) return false;
        float absDX = Math.abs(ev.getX() - mDownX);
        float absDY = Math.abs(ev.getY() - mDownY);
        if (!mGestureActive) {
            // Clearly vertical → this is an intentional QS swipe; let the shade open.
            if (mSuppressShadeExpand && absDY > mGestureSlopPx && absDY > absDX) {
                mSuppressShadeExpand = false;
            }
            if (absDX <= mGestureSlopPx || absDX <= absDY * mHorizontalRatio) {
                return false;
            }
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
        if (mMainHandler == null) return;
        // Send ACTION_CANCEL to abort any in-progress drag gesture in the shade window.
        if (mShadeWindowView != null) {
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
        // The ROM's swipe-up monitor calls makeExpandedVisible ~100ms after DOWN.
        // Schedule a collapse so it fires after the shade has opened, regardless of
        // whether the brightness threshold was crossed before or after that timer.
        long elapsed = android.os.SystemClock.uptimeMillis() - downTime;
        long delay = Math.max(10, 160 - elapsed);
        mMainHandler.postDelayed(this::collapseShadeIfOpen, delay);
    }

    private void collapseShadeIfOpen() {
        try {
            Context ctx = mOverlayContext != null ? mOverlayContext : mContext;
            if (ctx == null) return;
            Object sbm = ctx.getSystemService(Context.STATUS_BAR_SERVICE);
            if (sbm == null) return;
            sbm.getClass().getMethod("collapsePanels").invoke(sbm);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": collapseShade failed: " + t);
        }
    }

    private boolean onUpOrCancel(MotionEvent ev) {
        // Delay the clear so the swipe-up monitor's ~100ms deferred expansion still sees
        // the flag set, even if the finger lifted before the expansion fired.
        if (mMainHandler != null) {
            mMainHandler.removeCallbacks(mClearSuppressShadeExpand);
            mMainHandler.postDelayed(mClearSuppressShadeExpand, 200);
        } else {
            mSuppressShadeExpand = false;
        }
        if (!mGestureActive) { mTouchStartedInStatusBar = false; return false; }
        boolean cancelled = ev.getActionMasked() == MotionEvent.ACTION_CANCEL;
        float finalBrightness = cancelled && mLastTargetBrightness >= 0
                ? mLastTargetBrightness
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
        // Reversed slider: 100% on the left, 0% on the right.
        if (mReverseSlider) fraction = 1f - fraction;
        // Finger position raised to BRIGHTNESS_CURVE before mapping to the brightness float.
        // Settled on 2.2 (the original author's value) after trying linear and 1.3.
        float curved = (float) Math.pow(fraction, BRIGHTNESS_CURVE);
        return Math.max(mBrightnessMin,
                Math.min(mBrightnessMax,
                        mBrightnessMin + curved * (mBrightnessMax - mBrightnessMin)));
    }

    // ── Hidden API calls ──────────────────────────────────────────────────────

    private void setTemporaryBrightness(float brightness) {
        if (mSetTemporaryBrightnessMethod == null) return;
        try { mSetTemporaryBrightnessMethod.invoke(
                mDisplayManager, Display.DEFAULT_DISPLAY, brightness); }
        catch (Throwable t) { XposedBridge.log(TAG + ": setTemporaryBrightness: " + t); }
        mLastTargetBrightness = brightness;
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
            // 1px tall on purpose: window insets are global to the window, so a minimal
            // probe reports the same status-bar visibility as a full-size one. A tall
            // overlay would sit over the app's top toolbar and — even though it's
            // FLAG_NOT_TOUCHABLE — cause Android to flag touches underneath as OBSCURED,
            // which security-conscious views (toolbar buttons) discard. Keeping it 1px
            // means it only covers the very top edge, never the app's tappable content.
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    1,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSPARENT);
            params.gravity = Gravity.TOP | Gravity.START;
            probeView.setOnApplyWindowInsetsListener((v, insets) -> {
                mCachedSbHidden = !insets.isVisible(WindowInsets.Type.statusBars());
                return insets;
            });
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
        return mFullscreenTouchAttached && mCachedSbHidden;
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
                        // The gesture monitor is ONLY needed when the status bar is genuinely
                        // hidden (true fullscreen). In normal apps the status bar is visible and
                        // the SBV hook already owns that region — processing here too would
                        // double-handle shared gesture state and interfere with taps reaching
                        // the app below. So stay completely dormant unless the bar is hidden.
                        if (event instanceof MotionEvent && mGestureEnabled
                                && mFullscreenSwipe && isStatusBarHidden()
                                // Peek mode: the transient status bar window owns this
                                // stream — the SBV hook handles it; pilfering here would
                                // CANCEL that stream and kill the gesture.
                                && ((MotionEvent) event).getDownTime() != mSbvStreamDownTime) {
                            MotionEvent me = (MotionEvent) event;
                            int action = me.getActionMasked();
                            boolean inStrip = (action == MotionEvent.ACTION_DOWN)
                                    ? me.getY() <= getStripHeight()
                                    : mTouchStartedInStatusBar;
                            if (inStrip) {
                                boolean wasActive = mGestureActive;
                                handleTouchEvent(me, true);

                                if (!wasActive && mGestureActive) {
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