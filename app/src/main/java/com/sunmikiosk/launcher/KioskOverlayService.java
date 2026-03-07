/*
 * Copyright (c) 2026 mhmdgazzar
 * Licensed under the MIT License. See LICENSE file for details.
 */

package com.sunmikiosk.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * KioskOverlayService — A foreground service that places a transparent touch target
 * in the bottom-right corner of the screen. This overlay sits on top of ALL apps
 * (including the kiosk target app) and detects the 5-tap exit gesture.
 *
 * <p>Requires {@code SYSTEM_ALERT_WINDOW} permission.</p>
 *
 * <p>On Android 14+ with {@code FLAG_NOT_FOCUSABLE}, the overlay does not block
 * touches — taps pass through to the app below. The overlay still detects them
 * (best-effort). On Sunmi hardware (API 28-30) this works reliably.</p>
 */
public class KioskOverlayService extends Service {

    private static final String TAG = "KioskExit";
    private static final String CHANNEL_ID = "kiosk_service";
    private static final int NOTIFICATION_ID = 1;

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
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildSilentNotification());
        createOverlay();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (overlayView != null && windowManager != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (Exception ignored) {}
        }
    }

    private void createOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Transparent touch target
        overlayView = new View(this);
        overlayView.setBackgroundColor(0x00000000);

        int sizePx = (int) (TOUCH_TARGET_DP * getResources().getDisplayMetrics().density);

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                sizePx, sizePx,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.BOTTOM | Gravity.END;

        overlayView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                handleTap();
            }
            return true;
        });

        windowManager.addView(overlayView, params);
        Log.d(TAG, "Overlay created: " + sizePx + "px in bottom-right corner");
    }

    private void handleTap() {
        long now = System.currentTimeMillis();
        if (now - lastTapTime > TAP_TIMEOUT_MS) {
            tapCount = 0;
        }
        tapCount++;
        lastTapTime = now;
        Log.d(TAG, "Overlay tap #" + tapCount + "/" + TAP_COUNT);

        if (tapCount >= TAP_COUNT) {
            tapCount = 0;
            Log.d(TAG, "Overlay: 5 taps detected — launching PIN dialog");
            Intent intent = new Intent(this, KioskActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("show_pin", true);
            startActivity(intent);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Kiosk Service", NotificationManager.IMPORTANCE_MIN);
            channel.setDescription("Background service for kiosk mode");
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    /** Minimal silent notification — required for foreground service. */
    private Notification buildSilentNotification() {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("Kiosk Mode")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setOngoing(true)
                .build();
    }
}
