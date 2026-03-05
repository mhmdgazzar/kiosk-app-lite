/*
 * Copyright (c) 2026 mhmdgazzar
 * Licensed under the MIT License. See LICENSE file for details.
 */

package com.sunmikiosk.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * ConfigReceiver — Allows remote configuration of the kiosk via ADB broadcasts.
 *
 * <h3>Usage:</h3>
 * <pre>
 * # Set the target app:
 * adb shell am broadcast -a com.sunmikiosk.launcher.SET_TARGET \
 *   --es package "com.example.app" -n com.sunmikiosk.launcher/.ConfigReceiver
 *
 * # Set the exit PIN:
 * adb shell am broadcast -a com.sunmikiosk.launcher.SET_PIN \
 *   --es pin "5678" -n com.sunmikiosk.launcher/.ConfigReceiver
 * </pre>
 */
public class ConfigReceiver extends BroadcastReceiver {

    private static final String ACTION_SET_TARGET = "com.sunmikiosk.launcher.SET_TARGET";
    private static final String ACTION_SET_PIN = "com.sunmikiosk.launcher.SET_PIN";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        SharedPreferences prefs = context.getSharedPreferences(
                KioskActivity.PREFS_NAME, Context.MODE_PRIVATE);

        switch (intent.getAction()) {
            case ACTION_SET_TARGET:
                String pkg = intent.getStringExtra("package");
                if (pkg != null && !pkg.isEmpty()) {
                    prefs.edit().putString(KioskActivity.TARGET_KEY, pkg).apply();
                }
                break;

            case ACTION_SET_PIN:
                String pin = intent.getStringExtra("pin");
                if (pin != null && !pin.isEmpty()) {
                    prefs.edit().putString(KioskActivity.PIN_KEY, pin).apply();
                }
                break;
        }
    }
}
