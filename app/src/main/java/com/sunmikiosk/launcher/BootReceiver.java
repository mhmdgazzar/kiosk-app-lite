/*
 * Copyright (c) 2026 mhmdgazzar
 * Licensed under the MIT License. See LICENSE file for details.
 */

package com.sunmikiosk.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * BootReceiver — Starts the kiosk launcher when the device finishes booting.
 *
 * <p>Listens for {@link Intent#ACTION_BOOT_COMPLETED} and launches
 * {@link KioskActivity} to immediately enter kiosk mode.</p>
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent launchIntent = new Intent(context, KioskActivity.class);
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launchIntent);
        }
    }
}
