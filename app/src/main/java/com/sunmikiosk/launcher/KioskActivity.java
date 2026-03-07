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
import android.util.Log;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
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

    /** Delay (ms) before relaunching target app when KioskActivity gains focus.
     *  This gives the user a window to start the exit gesture. */
    private static final int RELAUNCH_DELAY_MS = 3500;

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

    /** SharedPreferences key for persisting kiosk enabled state. */
    static final String KIOSK_ACTIVE_KEY = "kiosk_active";

    private Handler handler;
    private Runnable monitorRunnable;
    private boolean kioskActive;
    private boolean isResumed = false;
    private String targetPackage;

    // Exit gesture tracking
    private int tapCount = 0;
    private long lastTapTime = 0;
    /** When true, auto-relaunch is paused to let the user complete the exit gesture. */
    private boolean exitGestureActive = false;
    /** How long to pause relaunch when exit gesture starts (ms). */
    private static final int EXIT_GESTURE_PAUSE_MS = 5000;
    /** Runnable that resets the exit gesture after timeout. */
    private Runnable exitGestureTimeoutRunnable;

    /** Reusable runnable for delayed relaunch — stored so it can be cancelled. */
    private final Runnable relaunchRunnable = () -> {
        if (kioskActive && isResumed && !exitGestureActive) {
            launchTargetApp();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Load configured target package
        targetPackage = prefs.getString(TARGET_KEY, DEFAULT_TARGET_PACKAGE);

        // Load persisted kiosk state (defaults to true)
        kioskActive = prefs.getBoolean(KIOSK_ACTIVE_KEY, true);
        boolean configured = prefs.getBoolean("configured", false);

        // If not configured or kiosk is disabled, go straight to SettingsActivity
        if (!configured || !kioskActive) {
            startActivity(new Intent(this, SettingsActivity.class));
            // Don't finish() — this activity stays as the Home fallback
            // but doesn't block the user from interacting with Settings
        }

        // Keep screen on (important for POS terminals)
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Build a minimal UI — this screen is only briefly visible during transitions
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF111111);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT);
        center.setLayoutParams(centerLp);

        TextView title = new TextView(this);
        title.setText("Kiosk App Lite");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setTextColor(0xFF888888);
        subtitle.setTextSize(13);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 16, 0, 0);

        if (kioskActive) {
            subtitle.setText("Launching " + targetPackage + "…");
        } else {
            subtitle.setText("Kiosk mode disabled. Open Settings to re-enable.");
        }

        center.addView(title);
        center.addView(subtitle);
        root.addView(center);

        // Subtle exit hint in bottom-right corner
        if (kioskActive) {
            TextView exitHint = new TextView(this);
            exitHint.setText("● ● ●");
            exitHint.setTextColor(0x33FFFFFF); // Very subtle
            exitHint.setTextSize(10);
            exitHint.setPadding(0, 0, 24, 24);
            FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            exitHint.setLayoutParams(hintLp);
            root.addView(exitHint);
        }

        setContentView(root);

        // NOTE: Touch handling is done via dispatchTouchEvent() at the Activity level,
        // NOT via root.setOnTouchListener(). This ensures touches are received even when
        // another window (e.g. Digital Wellbeing) briefly steals focus.
        Log.d("KioskExit", "onCreate: layout set, root=" + root);

        handler = new Handler();

        // Begin the monitoring loop only if kiosk is active
        if (kioskActive) {
            startMonitoring();
        }

        // NOTE: Target app is launched from onWindowFocusChanged(), not here.
        // This ensures the KioskActivity window is visible first, giving
        // the user a chance to perform the exit gesture (5-tap in corner).
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
                if (kioskActive && isResumed && !exitGestureActive) {
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
     * Intercept ALL touch events at the Activity level.
     * This is more reliable than View.OnTouchListener because it fires even when
     * the window focus is contested by overlays or other activities.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (kioskActive) {
            handleTouch(event);
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Handle touch events for the exit gesture.
     * Requires {@link #TAP_COUNT_REQUIRED} taps in the bottom-right corner
     * within {@link #TAP_TIMEOUT_MS} milliseconds to trigger the PIN dialog.
     */
    private void handleTouch(MotionEvent event) {
        Log.d("KioskExit", "handleTouch: action=" + event.getAction()
                + " at (" + event.getRawX() + "," + event.getRawY() + ")");
        if (event.getAction() != MotionEvent.ACTION_DOWN) return;

        float density = getResources().getDisplayMetrics().density;
        float cornerSize = 150 * density;
        // Use raw screen coordinates (reliable regardless of view hierarchy)
        float screenWidth = getResources().getDisplayMetrics().widthPixels;
        float screenHeight = getResources().getDisplayMetrics().heightPixels;

        Log.d("KioskExit", "Touch at (" + event.getRawX() + "," + event.getRawY()
                + ") screen=" + screenWidth + "x" + screenHeight
                + " cornerThreshold=(" + (screenWidth - cornerSize) + "," + (screenHeight - cornerSize) + ")");

        // Bottom-right corner detection (using raw screen coordinates)
        if (event.getRawX() > screenWidth - cornerSize && event.getRawY() > screenHeight - cornerSize) {
            long now = System.currentTimeMillis();
            if (now - lastTapTime > TAP_TIMEOUT_MS) {
                tapCount = 0;
            }
            tapCount++;
            lastTapTime = now;
            Log.d("KioskExit", "Corner tap #" + tapCount + "/" + TAP_COUNT_REQUIRED);

            // On first tap, pause auto-relaunch so user has time to complete the gesture
            if (tapCount == 1) {
                exitGestureActive = true;
                // Cancel any pending relaunch callbacks
                handler.removeCallbacks(relaunchRunnable);
                // Auto-reset after timeout if user doesn't complete the gesture
                exitGestureTimeoutRunnable = () -> {
                    exitGestureActive = false;
                    tapCount = 0;
                    // Resume kiosk — relaunch target app
                    if (kioskActive && isResumed) {
                        launchTargetApp();
                    }
                };
                handler.postDelayed(exitGestureTimeoutRunnable, EXIT_GESTURE_PAUSE_MS);
            }

            if (tapCount >= TAP_COUNT_REQUIRED) {
                tapCount = 0;
                // Cancel the timeout — PIN dialog will handle the state
                if (exitGestureTimeoutRunnable != null) {
                    handler.removeCallbacks(exitGestureTimeoutRunnable);
                }
                // Keep exitGestureActive = true while PIN dialog is visible
                showPinDialog();
            }
        }
        return;
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
                disableKiosk();
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNeutralButton("Settings", (dialog, which) -> {
            String enteredPin = input.getText().toString();
            String correctPin = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getString(PIN_KEY, DEFAULT_PIN);

            if (enteredPin.equals(correctPin)) {
                disableKiosk();
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            // Resume kiosk mode — relaunch target app
            exitGestureActive = false;
            if (kioskActive) {
                launchTargetApp();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(d -> {
            // Back button or tap outside dialog
            exitGestureActive = false;
            if (kioskActive) {
                launchTargetApp();
            }
        });
        dialog.show();
    }

    /**
     * Disable kiosk mode, restore the device's default launcher, and open Settings.
     * Called when user enters correct PIN via either "Exit" or "Settings" button.
     */
    private void disableKiosk() {
        // 1. Persist disabled state
        kioskActive = false;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit().putBoolean(KIOSK_ACTIVE_KEY, false).apply();

        // 2. Stop all monitoring and relaunch callbacks
        handler.removeCallbacks(monitorRunnable);
        handler.removeCallbacks(relaunchRunnable);
        if (exitGestureTimeoutRunnable != null) {
            handler.removeCallbacks(exitGestureTimeoutRunnable);
        }
        exitGestureActive = false;

        // 3. Clear this app as the preferred Home launcher so the stock one takes over
        getPackageManager().clearPackagePreferredActivities(getPackageName());

        // 4. Show the Kiosk settings screen
        startActivity(new Intent(this, SettingsActivity.class));

        Toast.makeText(this, "Kiosk mode disabled", Toast.LENGTH_SHORT).show();
    }
    @Override
    protected void onResume() {
        super.onResume();
        isResumed = true;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // Reload target package in case settings changed
        targetPackage = prefs.getString(TARGET_KEY, DEFAULT_TARGET_PACKAGE);

        // Reload kiosk state — it may have changed via SettingsActivity or PIN exit
        kioskActive = prefs.getBoolean(KIOSK_ACTIVE_KEY, true);

        Log.d("KioskExit", "onResume: kioskActive=" + kioskActive
                + " exitGestureActive=" + exitGestureActive
                + " targetPackage=" + targetPackage);

        // If kiosk is disabled, redirect to Settings and stop all callbacks
        if (!kioskActive) {
            handler.removeCallbacks(relaunchRunnable);
            handler.removeCallbacks(monitorRunnable);
            if (exitGestureTimeoutRunnable != null) {
                handler.removeCallbacks(exitGestureTimeoutRunnable);
            }
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (!exitGestureActive) {
            // Schedule relaunch from onResume as well — onWindowFocusChanged(true)
            // may never fire if another app steals focus (e.g. Digital Wellbeing)
            boolean configured = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getBoolean("configured", false);
            if (configured) {
                handler.removeCallbacks(relaunchRunnable);
                handler.postDelayed(relaunchRunnable, RELAUNCH_DELAY_MS);
                Log.d("KioskExit", "onResume: scheduled relaunch in " + RELAUNCH_DELAY_MS + "ms");
            }
        }
    }

    /**
     * Called when this window gains or loses focus.
     * This is the reliable signal that the activity is truly visible and interactive.
     * We delay relaunch from here so the user has a window to perform the exit gesture.
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d("KioskExit", "onWindowFocusChanged: hasFocus=" + hasFocus
                + " kioskActive=" + kioskActive
                + " exitGestureActive=" + exitGestureActive);
        if (hasFocus && kioskActive && !exitGestureActive) {
            boolean configured = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .getBoolean("configured", false);
            if (configured) {
                handler.removeCallbacks(relaunchRunnable);
                handler.postDelayed(relaunchRunnable, RELAUNCH_DELAY_MS);
                Log.d("KioskExit", "onWindowFocusChanged: scheduled relaunch in " + RELAUNCH_DELAY_MS + "ms");
            }
        }
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
