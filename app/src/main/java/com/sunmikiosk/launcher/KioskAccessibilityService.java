/*
 * Copyright (c) 2026 mhmdgazzar
 * Licensed under the MIT License. See LICENSE file for details.
 */

package com.sunmikiosk.launcher;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

/**
 * KioskAccessibilityService — Detects the 5-tap exit gesture on top of ANY app.
 *
 * <p>This service uses the AccessibilityService framework to place a transparent
 * touch target in the bottom-right corner of the screen. Unlike a regular overlay
 * with FLAG_NOT_FOCUSABLE, this approach reliably receives touch events on
 * Android 14+ because AccessibilityService-managed overlays have elevated
 * window priority.</p>
 *
 * <p>The service must be explicitly enabled by the user in
 * Settings → Accessibility → Kiosk App Lite.</p>
 */
public class KioskAccessibilityService extends AccessibilityService {

    private static final String TAG = "KioskExit";

    /** Size of the transparent touch target in dp. */
    private static final int TOUCH_TARGET_DP = 100;

    /** Number of taps required to trigger exit. */
    private static final int TAP_COUNT = 5;

    /** Time window for all taps (ms). */
    private static final int TAP_TIMEOUT_MS = 5000;

    private WindowManager windowManager;
    private View overlayView;
    private int tapCount = 0;
    private long lastTapTime = 0;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();

        // Configure to receive all touch exploration events
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        setServiceInfo(info);

        createOverlay();
        Log.d(TAG, "KioskAccessibilityService connected — overlay placed");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // We don't need to handle accessibility events — we use the overlay for taps
    }

    @Override
    public void onInterrupt() {
        // Required but unused
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeOverlay();
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        overlayView = new View(this);
        overlayView.setBackgroundColor(0x00000000); // Fully transparent

        DisplayMetrics dm = getResources().getDisplayMetrics();
        int sizePx = (int) (TOUCH_TARGET_DP * dm.density);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                sizePx, sizePx,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.END;

        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                handleTap();
            }
            return true; // Consume the touch
        });

        windowManager.addView(overlayView, params);
        Log.d(TAG, "Accessibility overlay created: " + sizePx + "px in bottom-right corner");
    }

    private void removeOverlay() {
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
        }
    }

    private void handleTap() {
        // Only count taps when kiosk mode is active
        SharedPreferences prefs = getSharedPreferences(KioskActivity.PREFS_NAME, MODE_PRIVATE);
        if (!prefs.getBoolean(KioskActivity.KIOSK_ACTIVE_KEY, false)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastTapTime > TAP_TIMEOUT_MS) {
            tapCount = 0;
        }
        tapCount++;
        lastTapTime = now;
        Log.d(TAG, "Accessibility tap #" + tapCount + "/" + TAP_COUNT);

        if (tapCount >= TAP_COUNT) {
            tapCount = 0;
            Log.d(TAG, "Accessibility: 5 taps detected — launching PIN dialog");
            Intent intent = new Intent(this, KioskActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("show_pin", true);
            startActivity(intent);
        }
    }
}
