/*
 * Copyright (c) 2026 mhmdgazzar
 * Licensed under the MIT License. See LICENSE file for details.
 */

package com.sunmikiosk.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * KioskOverlayService — A foreground service that shows a persistent notification
 * with an "Exit Kiosk" action. Tapping the notification action opens the PIN dialog
 * in KioskActivity.
 *
 * <p>This replaces the unreliable touch overlay approach. The notification
 * is always accessible via the status bar pull-down.</p>
 */
public class KioskOverlayService extends Service {

    private static final String TAG = "KioskExit";
    private static final String CHANNEL_ID = "kiosk_overlay";
    private static final int NOTIFICATION_ID = 1;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        Log.d(TAG, "KioskOverlayService started — notification with Exit action shown");
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
                    CHANNEL_ID, "Kiosk Mode", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Kiosk mode is active");
            channel.setShowBadge(false);
            getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        // Tapping the notification body opens KioskActivity with show_pin
        Intent pinIntent = new Intent(this, KioskActivity.class);
        pinIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        pinIntent.putExtra("show_pin", true);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, pinIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder
                .setContentTitle("Kiosk Mode Active")
                .setContentText("Tap to exit kiosk mode")
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}
