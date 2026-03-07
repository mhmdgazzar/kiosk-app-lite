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
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * KioskOverlayService — A minimal foreground service that keeps the kiosk process alive.
 *
 * <p>The actual exit gesture overlay is handled by {@link KioskAccessibilityService}
 * which uses TYPE_ACCESSIBILITY_OVERLAY for reliable touch detection on Android 14+.</p>
 */
public class KioskOverlayService extends Service {

    private static final String TAG = "KioskExit";
    private static final String CHANNEL_ID = "kiosk_service";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildSilentNotification());
        Log.d(TAG, "KioskOverlayService started — foreground service active");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
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
