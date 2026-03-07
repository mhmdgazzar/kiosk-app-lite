/*
 * Copyright (c) 2026 mhmdgazzar
 * Licensed under the MIT License. See LICENSE file for details.
 */

package com.sunmikiosk.launcher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SettingsActivity — Clean, monochromatic settings UI for Kiosk App Lite.
 *
 * <p>Allows users to configure the target app package and exit PIN
 * through a simple, dark-themed settings screen with Phosphor Icons.</p>
 *
 * <p>This activity is shown before kiosk mode activates on first launch,
 * or when accessed via the exit PIN dialog.</p>
 */
public class SettingsActivity extends Activity {

    // Color palette — monochromatic dark theme
    private static final int BG_COLOR = 0xFF0D0D0D;
    private static final int CARD_COLOR = 0xFF1A1A1A;
    private static final int BORDER_COLOR = 0xFF2A2A2A;
    private static final int TEXT_PRIMARY = 0xFFE8E8E8;
    private static final int TEXT_SECONDARY = 0xFF888888;
    private static final int ACCENT_COLOR = 0xFFCCCCCC;
    private static final int INPUT_BG = 0xFF111111;
    private static final int BTN_SECONDARY_BG = 0xFF222222;

    private EditText packageInput;
    private EditText pinInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(KioskActivity.PREFS_NAME, MODE_PRIVATE);
        String currentPackage = prefs.getString(KioskActivity.TARGET_KEY, "com.jtl.pos");
        String currentPin = prefs.getString(KioskActivity.PIN_KEY, KioskActivity.DEFAULT_PIN);

        // Root scroll view
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(BG_COLOR);
        scrollView.setFillViewport(true);

        // Main container
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(48));

        // Header — app name only, no icon
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER);
        header.setPadding(0, dp(16), 0, dp(32));

        TextView title = createText("Kiosk App Lite", 24, TEXT_PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        header.addView(title);

        TextView subtitle = createText("Configure your kiosk launcher", 13, TEXT_SECONDARY, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(6), 0, 0);
        header.addView(subtitle);

        root.addView(header);

        // — Target App Card —
        LinearLayout appCard = createCard();

        LinearLayout appHeader = createCardHeader(R.drawable.ic_terminal, "Target Application");
        appCard.addView(appHeader);

        TextView appDesc = createText("Enter package name or browse installed apps", 12, TEXT_SECONDARY, false);
        appDesc.setPadding(0, dp(4), 0, dp(12));
        appCard.addView(appDesc);

        packageInput = createInput(currentPackage, "com.example.app");
        appCard.addView(packageInput);

        // Browse Apps button
        appCard.addView(createSpacer(10));

        TextView browseBtn = new TextView(this);
        browseBtn.setText("Browse Installed Apps…");
        browseBtn.setTextSize(13);
        browseBtn.setTextColor(ACCENT_COLOR);
        browseBtn.setTypeface(null, Typeface.BOLD);
        browseBtn.setGravity(Gravity.CENTER);
        browseBtn.setPadding(dp(16), dp(11), dp(16), dp(11));

        GradientDrawable browseBg = new GradientDrawable();
        browseBg.setColor(BTN_SECONDARY_BG);
        browseBg.setCornerRadius(dp(8));
        browseBg.setStroke(1, BORDER_COLOR);
        browseBtn.setBackground(browseBg);
        browseBtn.setOnClickListener(v -> showAppPicker());

        appCard.addView(browseBtn);
        root.addView(appCard);

        // Spacer
        root.addView(createSpacer(16));

        // — Exit PIN Card —
        LinearLayout pinCard = createCard();

        LinearLayout pinHeader = createCardHeader(R.drawable.ic_key, "Exit PIN");
        pinCard.addView(pinHeader);

        TextView pinDesc = createText("PIN code required to exit kiosk mode (numeric only)", 12, TEXT_SECONDARY, false);
        pinDesc.setPadding(0, dp(4), 0, dp(12));
        pinCard.addView(pinDesc);

        pinInput = createInput(currentPin, "1234");
        pinCard.addView(pinInput);

        root.addView(pinCard);

        // Spacer
        root.addView(createSpacer(16));

        // — Info Card —
        LinearLayout infoCard = createCard();

        LinearLayout infoHeader = createCardHeader(R.drawable.ic_shield, "How It Works");
        infoCard.addView(infoHeader);

        String[] tips = {
                "Set as default launcher → locks to target app",
                "Home/Back buttons return to the target app",
                "Status bar stays accessible for WiFi/Bluetooth",
                "Tap 5× anywhere on screen → enter PIN to exit",
                "ADB always stays accessible — no lockout risk"
        };

        for (String tip : tips) {
            TextView tipView = createText("·  " + tip, 12, TEXT_SECONDARY, false);
            tipView.setPadding(0, dp(6), 0, 0);
            infoCard.addView(tipView);
        }

        root.addView(infoCard);

        // Spacer
        root.addView(createSpacer(32));

        // — Save & Activate Button —
        TextView saveBtn = new TextView(this);
        saveBtn.setText("Save & Activate Kiosk");
        saveBtn.setTextSize(15);
        saveBtn.setTextColor(BG_COLOR);
        saveBtn.setTypeface(null, Typeface.BOLD);
        saveBtn.setGravity(Gravity.CENTER);
        saveBtn.setPadding(dp(24), dp(14), dp(24), dp(14));

        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(ACCENT_COLOR);
        saveBg.setCornerRadius(dp(10));
        saveBtn.setBackground(saveBg);

        saveBtn.setOnClickListener(v -> saveAndActivate());
        root.addView(saveBtn);

        scrollView.addView(root);
        setContentView(scrollView);
    }

    /**
     * Show a dialog listing all user-installed (launchable) apps.
     * Each item shows the app icon, name, and package name.
     */
    private void showAppPicker() {
        PackageManager pm = getPackageManager();
        Intent launchIntent = new Intent(Intent.ACTION_MAIN, null);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ApplicationInfo> apps = new ArrayList<>();
        List<String> appNames = new ArrayList<>();
        List<String> appPackages = new ArrayList<>();

        // Collect all launchable apps
        for (android.content.pm.ResolveInfo ri : pm.queryIntentActivities(launchIntent, 0)) {
            String pkg = ri.activityInfo.packageName;
            // Skip ourselves
            if (pkg.equals(getPackageName())) continue;

            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(pkg, 0);
                String label = pm.getApplicationLabel(appInfo).toString();
                if (!appPackages.contains(pkg)) {
                    apps.add(appInfo);
                    appNames.add(label);
                    appPackages.add(pkg);
                }
            } catch (PackageManager.NameNotFoundException ignored) {}
        }

        // Sort alphabetically by name
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < appNames.size(); i++) indices.add(i);
        Collections.sort(indices, (a, b) ->
                appNames.get(a).compareToIgnoreCase(appNames.get(b)));

        // Build the dialog with a custom list
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select App");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(4), dp(8), dp(4), dp(8));

        AlertDialog[] dialogRef = new AlertDialog[1];

        for (int idx : indices) {
            String name = appNames.get(idx);
            String pkg = appPackages.get(idx);
            ApplicationInfo appInfo = apps.get(idx);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));

            // App icon
            ImageView icon = new ImageView(this);
            try {
                Drawable appIcon = pm.getApplicationIcon(appInfo);
                icon.setImageDrawable(appIcon);
            } catch (Exception e) {
                icon.setImageResource(android.R.drawable.sym_def_app_icon);
            }
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
            iconParams.setMarginEnd(dp(14));
            icon.setLayoutParams(iconParams);
            row.addView(icon);

            // Text column
            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            textCol.setLayoutParams(new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

            TextView nameView = createText(name, 14, TEXT_PRIMARY, false);
            textCol.addView(nameView);

            TextView pkgView = createText(pkg, 11, TEXT_SECONDARY, false);
            pkgView.setTypeface(Typeface.MONOSPACE);
            textCol.addView(pkgView);

            row.addView(textCol);

            // Click to select
            row.setOnClickListener(v -> {
                packageInput.setText(pkg);
                if (dialogRef[0] != null) dialogRef[0].dismiss();
            });

            // Divider
            list.addView(row);
            if (idx != indices.get(indices.size() - 1)) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(BORDER_COLOR);
                list.addView(divider);
            }
        }

        scrollView.addView(list);
        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        dialogRef[0] = builder.create();
        dialogRef[0].show();
    }

    private void saveAndActivate() {
        String pkg = packageInput.getText().toString().trim();
        String pin = pinInput.getText().toString().trim();

        if (pkg.isEmpty()) {
            Toast.makeText(this, "Package name cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pin.isEmpty()) {
            Toast.makeText(this, "PIN cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        if (getPackageManager().getLaunchIntentForPackage(pkg) == null) {
            Toast.makeText(this, "App not found: " + pkg, Toast.LENGTH_LONG).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences(KioskActivity.PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putString(KioskActivity.TARGET_KEY, pkg)
                .putString(KioskActivity.PIN_KEY, pin)
                .putBoolean("configured", true)
                .putBoolean(KioskActivity.KIOSK_ACTIVE_KEY, true)
                .apply();

        Toast.makeText(this, "Saved! Activating kiosk…", Toast.LENGTH_SHORT).show();

        // Clear any cached launcher preferences
        getPackageManager().clearPackagePreferredActivities(getPackageName());

        // Show an alert telling the user they MUST select Kiosk App Lite as Home
        new AlertDialog.Builder(this)
                .setTitle("Set Default Launcher")
                .setMessage("To activate kiosk mode, you must select \"Kiosk App Lite\" as your Home app on the next screen.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    // Open the system Home settings page
                    Intent homeSettings = new Intent(android.provider.Settings.ACTION_HOME_SETTINGS);
                    startActivity(homeSettings);
                })
                .setCancelable(false)
                .show();
    }

    // ── UI Helpers ──────────────────────────────────────────

    private LinearLayout createCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(CARD_COLOR);
        bg.setCornerRadius(dp(12));
        bg.setStroke(1, BORDER_COLOR);
        card.setBackground(bg);

        return card;
    }

    private LinearLayout createCardHeader(int iconRes, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.setMarginEnd(dp(10));
        icon.setLayoutParams(iconParams);
        row.addView(icon);

        TextView text = createText(label, 15, TEXT_PRIMARY, true);
        row.addView(text);

        return row;
    }

    private EditText createInput(String value, String hint) {
        EditText input = new EditText(this);
        input.setText(value);
        input.setHint(hint);
        input.setTextColor(TEXT_PRIMARY);
        input.setHintTextColor(0xFF555555);
        input.setTextSize(14);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setTypeface(Typeface.MONOSPACE);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(INPUT_BG);
        bg.setCornerRadius(dp(8));
        bg.setStroke(1, BORDER_COLOR);
        input.setBackground(bg);

        return input;
    }

    private TextView createText(String text, float sizeSp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        return tv;
    }

    private View createSpacer(int heightDp) {
        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(heightDp)));
        return spacer;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
