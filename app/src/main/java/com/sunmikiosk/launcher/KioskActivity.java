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
import android.app.AlertDialog;
import android.content.Intent;
import android.util.Log;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

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



    /** Delay (ms) before relaunching target app when KioskActivity gains focus.
     *  Set to 3s so the dark screen stays long enough for the 5-tap exit gesture.
     *  The first tap in the bottom-right corner cancels the relaunch and starts
     *  the exit gesture with a full EXIT_GESTURE_PAUSE_MS window. */
    private static final int RELAUNCH_DELAY_MS = 3000;

    /** Size of the exit gesture corner target in dp (matches overlay). */
    private static final int CORNER_TARGET_DP = 100;

    /** SharedPreferences key for the saved stock launcher package. */
    static final String PREVIOUS_LAUNCHER_KEY = "previous_launcher";

    /** Number of taps required to trigger the PIN dialog. */
    private static final int TAP_COUNT_REQUIRED = 5;

    /** Time window (ms) in which all taps must occur. */
    private static final int TAP_TIMEOUT_MS = 5000;

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
    private boolean kioskActive;
    private boolean isResumed = false;
    private String targetPackage;

    // Exit gesture tracking
    private int tapCount = 0;
    private long lastTapTime = 0;
    /** When true, auto-relaunch is paused to let the user complete the exit gesture. */
    private boolean exitGestureActive = false;
    /** When true, PIN dialog is visible — taps should NOT count as exit gesture. */
    private boolean pinDialogShowing = false;
    /** How long to pause relaunch when exit gesture starts (ms). */
    private static final int EXIT_GESTURE_PAUSE_MS = 5000;
    /** Runnable that resets the exit gesture after timeout. */
    private Runnable exitGestureTimeoutRunnable;

    /** Reusable runnable for delayed relaunch — stored so it can be cancelled. */
    private final Runnable relaunchRunnable = () -> {
        if (kioskActive && isResumed && !exitGestureActive && !pinDialogShowing) {
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
            finish();
            return;
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
            subtitle.setText("Tap bottom-right corner 5× to exit");
        } else {
            subtitle.setText("Kiosk mode disabled. Open Settings to re-enable.");
        }

        center.addView(title);
        center.addView(subtitle);
        root.addView(center);

        setContentView(root);

        // NOTE: Touch handling is done via dispatchTouchEvent() at the Activity level,
        // NOT via root.setOnTouchListener(). This ensures touches are received even when
        // another window (e.g. Digital Wellbeing) briefly steals focus.
        Log.d("KioskExit", "onCreate: layout set, root=" + root);

        handler = new Handler();

        // Start the overlay service so the exit gesture works ON TOP of the target app
        startOverlayService();

        // If launched with show_pin (from overlay), show PIN dialog immediately
        if (getIntent().getBooleanExtra("show_pin", false)) {
            getIntent().removeExtra("show_pin");
            showPinDialog();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // Handle show_pin from overlay when activity already exists (singleTask)
        if (intent.getBooleanExtra("show_pin", false)) {
            intent.removeExtra("show_pin");
            handler.removeCallbacks(relaunchRunnable);
            exitGestureActive = true;
            showPinDialog();
        }
    }

    /** Start the overlay service for exit gesture on top of target app. */
    private void startOverlayService() {
        if (android.provider.Settings.canDrawOverlays(this)) {
            Intent svc = new Intent(this, KioskOverlayService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
        } else {
            Log.w("KioskExit", "Overlay permission not granted — exit gesture only on dark screen");
        }
    }

    /** Stop the overlay service. */
    private void stopOverlayService() {
        stopService(new Intent(this, KioskOverlayService.class));
    }

    /**
     * Launch the configured target application.
     * Uses the package manager to resolve the launch intent.
     */
    private void launchTargetApp() {
        Log.d("KioskExit", "launchTargetApp: exitGestureActive=" + exitGestureActive);
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
     * Intercept ALL touch events at the Activity level.
     * This is more reliable than View.OnTouchListener because it fires even when
     * the window focus is contested by overlays or other activities.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (kioskActive && !pinDialogShowing) {
            handleTouch(event);
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Handle touch events for the exit gesture.
     * Requires {@link #TAP_COUNT_REQUIRED} taps in the BOTTOM-RIGHT CORNER
     * within {@link #TAP_TIMEOUT_MS} milliseconds to trigger the PIN dialog.
     */
    private void handleTouch(MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_DOWN) return;

        // Only count taps in the bottom-right corner
        float cornerPx = CORNER_TARGET_DP * getResources().getDisplayMetrics().density;
        float screenW = getResources().getDisplayMetrics().widthPixels;
        float screenH = getResources().getDisplayMetrics().heightPixels;
        if (event.getRawX() < screenW - cornerPx || event.getRawY() < screenH - cornerPx) {
            return; // Outside the corner — ignore
        }

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
            handler.removeCallbacks(relaunchRunnable);
            exitGestureTimeoutRunnable = () -> {
                exitGestureActive = false;
                tapCount = 0;
                if (kioskActive && isResumed) {
                    launchTargetApp();
                }
            };
            handler.postDelayed(exitGestureTimeoutRunnable, EXIT_GESTURE_PAUSE_MS);
        }

        if (tapCount >= TAP_COUNT_REQUIRED) {
            tapCount = 0;
            if (exitGestureTimeoutRunnable != null) {
                handler.removeCallbacks(exitGestureTimeoutRunnable);
            }
            showPinDialog();
        }
    }

    /**
     * Show a PIN dialog to exit kiosk mode.
     * The correct PIN is stored in SharedPreferences (default: {@value DEFAULT_PIN}).
     */
    private void showPinDialog() {
        pinDialogShowing = true;
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
                pinDialogShowing = false;
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
                pinDialogShowing = false;
                // Go to settings to reconfigure (different from Exit)
                kioskActive = false;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                        .edit().putBoolean(KIOSK_ACTIVE_KEY, false).apply();
                handler.removeCallbacks(relaunchRunnable);
                if (exitGestureTimeoutRunnable != null) {
                    handler.removeCallbacks(exitGestureTimeoutRunnable);
                }
                exitGestureActive = false;
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            pinDialogShowing = false;
            exitGestureActive = false;
            if (kioskActive) {
                launchTargetApp();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setOnCancelListener(d -> {
            pinDialogShowing = false;
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
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(KIOSK_ACTIVE_KEY, false).apply();

        // 2. Stop all monitoring, relaunch callbacks, and overlay service
        handler.removeCallbacks(relaunchRunnable);
        if (exitGestureTimeoutRunnable != null) {
            handler.removeCallbacks(exitGestureTimeoutRunnable);
        }
        exitGestureActive = false;
        stopOverlayService();

        // 3. Clear this app as the preferred Home launcher
        getPackageManager().clearPackagePreferredActivities(getPackageName());

        // 4. Auto-force the saved stock launcher back
        String previousLauncher = prefs.getString(PREVIOUS_LAUNCHER_KEY, "");
        boolean restored = false;
        if (!previousLauncher.isEmpty()) {
            restored = forceHomeLauncher(previousLauncher);
        }

        // 5. Show the Kiosk settings screen
        startActivity(new Intent(this, SettingsActivity.class));

        // 6. If auto-force failed, trigger launcher chooser as fallback
        if (!restored) {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
        }

        finish();
        Toast.makeText(this, "Kiosk mode disabled", Toast.LENGTH_SHORT).show();
    }

    /**
     * Try to force a launcher as the default Home app via shell command.
     * Works on rooted/Sunmi devices. Falls back gracefully on standard Android.
     * @return true if the shell command succeeded
     */
    static boolean forceHomeLauncher(String componentFlat) {
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"cmd", "package", "set-home-activity", componentFlat});
            int exit = p.waitFor();
            Log.d("KioskExit", "set-home-activity " + componentFlat + " exit=" + exit);
            return exit == 0;
        } catch (Exception e) {
            Log.w("KioskExit", "forceHomeLauncher failed: " + e.getMessage());
            return false;
        }
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
            if (exitGestureTimeoutRunnable != null) {
                handler.removeCallbacks(exitGestureTimeoutRunnable);
            }
            startActivity(new Intent(this, SettingsActivity.class));
            finish();
            return;
        } else if (!exitGestureActive && !pinDialogShowing) {
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
        if (hasFocus && kioskActive && !exitGestureActive && !pinDialogShowing) {
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
        if (handler != null) {
            handler.removeCallbacks(relaunchRunnable);
        }
    }
}
