/*
 * Copyright (c) 2026 mhmdgazzar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.sunmikiosk.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * KioskActivity — A minimal Android launcher that locks the device to a single app.
 *
 * <p>This activity registers as the device HOME launcher. When set as the default
 * launcher, it immediately launches the configured target app and relaunches it
 * whenever the user navigates away (e.g. by pressing Home or Back).</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Auto-launches the target app on boot and on every Home press</li>
 *   <li>Monitors and relaunches the target app if it exits</li>
 *   <li>Status bar remains accessible for WiFi/Bluetooth quick toggles</li>
 *   <li>Exit via multi-tap on screen corner + PIN dialog</li>
 *   <li>No Device Owner/Admin required — ADB always remains accessible</li>
 * </ul>
 *
 * <h3>Configuration via ADB:</h3>
 * <pre>
 * # Set the target app package name:
 * adb shell am broadcast -a com.sunmikiosk.launcher.SET_TARGET \
 *   --es package "com.example.myapp" -n com.sunmikiosk.launcher/.ConfigReceiver
 *
 * # Set exit PIN:
 * adb shell am broadcast -a com.sunmikiosk.launcher.SET_PIN \
 *   --es pin "5678" -n com.sunmikiosk.launcher/.ConfigReceiver
 * </pre>
 */
public class KioskActivity extends Activity {

    /** Default target app to launch. Override via SharedPreferences or ADB. */
    private static final String DEFAULT_TARGET_PACKAGE = "com.jtl.pos";

    /** How often (ms) to check if the kiosk home screen is visible and needs to relaunch. */
    private static final int MONITOR_INTERVAL_MS = 3000;

    /** Number of taps required in the exit corner to trigger the PIN dialog. */
    private static final int TAP_COUNT_REQUIRED = 5;

    /** Time window (ms) in which all taps must occur. */
    private static final int TAP_TIMEOUT_MS = 3000;

    /** SharedPreferences file name. */
    static final String PREFS_NAME = "kiosk_prefs";

    /** SharedPreferences key for the exit PIN. */
    static final String PIN_KEY = "exit_pin";

    /** SharedPreferences key for the target app package name. */
    static final String TARGET_KEY = "target_package";

    /** Default exit PIN. */
    static final String DEFAULT_PIN = "1234";

    private Handler handler;
    private Runnable monitorRunnable;
    private boolean kioskActive = true;
    private boolean firstLaunch = true;
    private boolean isResumed = false;
    private String targetPackage;

    // Exit gesture tracking
    private int tapCount = 0;
    private long lastTapTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // On first launch, show settings to let user configure target app
        boolean configured = prefs.getBoolean("configured", false);
        if (!configured) {
            startActivity(new Intent(this, SettingsActivity.class));
        }

        // Load configured target package
        targetPackage = prefs.getString(TARGET_KEY, DEFAULT_TARGET_PACKAGE);

        // Keep screen on (important for POS terminals)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Build a minimal UI — this screen is only briefly visible during transitions
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0xFF111111);

        TextView title = new TextView(this);
        title.setText("Kiosk App Lite");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText("Launching " + targetPackage + "…");
        subtitle.setTextColor(0xFF888888);
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 16, 0, 0);

        layout.addView(title);
        layout.addView(subtitle);
        setContentView(layout);

        // Touch listener for the exit gesture (multi-tap in corner)
        layout.setOnTouchListener(this::handleTouch);

        handler = new Handler();

        // Launch target app immediately (only if configured)
        if (configured) {
            launchTargetApp();
        }

        // Begin the monitoring loop
        startMonitoring();
    }

    /**
     * Launch the configured target application.
     * Uses the package manager to resolve the launch intent.
     */
    private void launchTargetApp() {
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
        } else {
            Toast.makeText(this,
                    "Target app not found: " + targetPackage,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Start a periodic check that relaunches the target app whenever the
     * kiosk home screen becomes visible (meaning the target app was closed).
     *
     * <p>The monitor intentionally only checks {@code isResumed} — it does NOT
     * aggressively poll the foreground app. This prevents the kiosk from
     * interfering with the target app's own dialogs, sub-activities, or
     * permission prompts.</p>
     */
    private void startMonitoring() {
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (kioskActive && isResumed) {
                    launchTargetApp();
                }
                if (kioskActive) {
                    handler.postDelayed(this, MONITOR_INTERVAL_MS);
                }
            }
        };
        handler.postDelayed(monitorRunnable, MONITOR_INTERVAL_MS);
    }

    /**
     * Handle touch events for the exit gesture.
     * Requires {@link #TAP_COUNT_REQUIRED} taps in the bottom-right corner
     * within {@link #TAP_TIMEOUT_MS} milliseconds to trigger the PIN dialog.
     */
    private boolean handleTouch(View v, MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return false;

        float density = getResources().getDisplayMetrics().density;
        float cornerSize = 150 * density;
        float screenWidth = v.getWidth();
        float screenHeight = v.getHeight();

        // Bottom-right corner detection
        if (event.getX() > screenWidth - cornerSize && event.getY() > screenHeight - cornerSize) {
            long now = System.currentTimeMillis();
            if (now - lastTapTime > TAP_TIMEOUT_MS) {
                tapCount = 0;
            }
            tapCount++;
            lastTapTime = now;

            if (tapCount >= TAP_COUNT_REQUIRED) {
                tapCount = 0;
                showPinDialog();
            }
        }
        return true;
    }

    /**
     * Show a PIN dialog to exit kiosk mode.
     * The correct PIN is stored in SharedPreferences (default: {@value DEFAULT_PIN}).
     */
    private void showPinDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Exit Kiosk Mode");
        builder.setMessage("Enter PIN to exit:");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        input.setPadding(padding, padding, padding, padding);
        builder.setView(input);

        builder.setPositiveButton("Exit", (dialog, which) -> {
            String enteredPin = input.getText().toString();
            String correctPin = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(PIN_KEY, DEFAULT_PIN);

            if (enteredPin.equals(correctPin)) {
                kioskActive = false;
                handler.removeCallbacks(monitorRunnable);
                Toast.makeText(this, "Kiosk mode disabled", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Settings", (dialog, which) -> {
            String enteredPin = input.getText().toString();
            String correctPin = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(PIN_KEY, DEFAULT_PIN);

            if (enteredPin.equals(correctPin)) {
                kioskActive = false;
                handler.removeCallbacks(monitorRunnable);
                startActivity(new Intent(this, SettingsActivity.class));
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;

        // Reload target package in case settings changed
        targetPackage = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(TARGET_KEY, DEFAULT_TARGET_PACKAGE);

        boolean configured = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean("configured", false);

        if (kioskActive && configured && !firstLaunch) {
            handler.postDelayed(this::launchTargetApp, 800);
        }
        firstLaunch = false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
    }

    @Override
    public void onBackPressed() {
        if (kioskActive) {
            launchTargetApp();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && monitorRunnable != null) {
            handler.removeCallbacks(monitorRunnable);
        }
    }
}
