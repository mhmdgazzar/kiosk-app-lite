# Kiosk App Lite

A minimal, open-source Android kiosk launcher that locks a device to a single app. Built for POS terminals, kiosks, digital signage, and any scenario where you need to restrict a device to one application.

**No root. No Device Owner. No trial. No watermark. Just works.**

## Features

| Feature | Description |
|---|---|
| <img src="https://raw.githubusercontent.com/phosphor-icons/core/main/assets/regular/lock-simple.svg" width="18" /> **Single-App Lock** | Automatically launches and locks the device to your chosen app |
| <img src="https://raw.githubusercontent.com/phosphor-icons/core/main/assets/regular/arrow-counter-clockwise.svg" width="18" /> **Auto-Relaunch** | If the app closes or crashes, it relaunches within 3 seconds |
| <img src="https://raw.githubusercontent.com/phosphor-icons/core/main/assets/regular/rocket-launch.svg" width="18" /> **Boot Auto-Start** | Launches the target app immediately after device boot |
| <img src="https://raw.githubusercontent.com/phosphor-icons/core/main/assets/regular/wifi-high.svg" width="18" /> **Quick Settings Access** | Status bar remains accessible for WiFi/Bluetooth toggles |
| <img src="https://raw.githubusercontent.com/phosphor-icons/core/main/assets/regular/key.svg" width="18" /> **PIN-Protected Exit** | Exit kiosk mode via hidden gesture + PIN code |
| <img src="https://raw.githubusercontent.com/phosphor-icons/core/main/assets/regular/terminal-window.svg" width="18" /> **ADB Configuration** | Change target app and PIN remotely via ADB — no rebuild needed |
| <img src="https://raw.githubusercontent.com/phosphor-icons/core/main/assets/regular/shield-check.svg" width="18" /> **ADB-Safe** | Does NOT use Device Owner/Admin — ADB always remains accessible |
| <img src="https://raw.githubusercontent.com/phosphor-icons/core/main/assets/regular/package.svg" width="18" /> **Tiny Footprint** | ~1.6 MB APK, minimal resource usage |

## Compatibility

- **Android 6.0+** (API 23+)
- Tested on **Sunmi T2** POS terminal (Android 7.1.1)
- Works on any Android device — tablets, phones, POS terminals, digital signage

## Quick Start

### 1. Install via ADB

```bash
adb install -r app-debug.apk
```

### 2. Set as Default Launcher

Press the **Home** button on the device. Android will ask you to choose a launcher:
- Select **"Kiosk App Lite"**
- Tap **"Always"**

The target app will launch immediately.

### 3. Configure the Target App

By default, the kiosk launches `com.jtl.pos`. To change it:

```bash
adb shell am broadcast -a com.sunmikiosk.launcher.SET_TARGET \
  --es package "com.your.app.package" \
  -n com.sunmikiosk.launcher/.ConfigReceiver
```

Then reboot the device or press Home to apply.

### 4. Change the Exit PIN

Default PIN is `1234`. To change it:

```bash
adb shell am broadcast -a com.sunmikiosk.launcher.SET_PIN \
  --es pin "9999" \
  -n com.sunmikiosk.launcher/.ConfigReceiver
```

## Exiting Kiosk Mode

1. **Tap 5 times quickly** in the **bottom-right corner** of the screen
2. A PIN dialog will appear
3. Enter the PIN (default: `1234`)
4. Kiosk mode is disabled — you return to the normal Android home screen

## Build from Source

### Prerequisites
- Java JDK 11+ (or 17)
- Android SDK with platform 32

### Build

```bash
git clone https://github.com/mhmdgazzar/kiosk-app-lite.git
cd kiosk-app-lite
chmod +x gradlew
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

```
app/src/main/java/com/sunmikiosk/launcher/
├── KioskActivity.java      # Main launcher — launches & monitors the target app
├── BootReceiver.java        # Starts kiosk on device boot
└── ConfigReceiver.java      # ADB-configurable target app & PIN
```

### How It Works

1. `KioskActivity` registers as an Android **HOME launcher** via the manifest
2. On launch, it immediately starts the configured target app
3. When the user presses Home or Back, Android returns to `KioskActivity` (the default launcher), which immediately relaunches the target app
4. A background `Handler` checks every 3 seconds if the kiosk screen is visible — if so, it relaunches the target app
5. The status bar is left fully accessible so users can toggle WiFi, Bluetooth, etc.

### Why No Device Owner?

Many kiosk apps require `dpm set-device-owner`, which gives them system-level control. This is dangerous because:
- It can **disable USB debugging**, locking you out of ADB
- It **cannot be easily removed** — often requires a factory reset
- It grants **excessive permissions** for a simple kiosk use case

Kiosk App Lite takes a deliberately simpler approach: it's just a launcher. It doesn't need — and doesn't request — any special permissions beyond `RECEIVE_BOOT_COMPLETED`.

## Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.

## License

This project is licensed under the [MIT License](LICENSE).

---

*Built for [Sunmi T2](https://www.sunmi.com/) POS terminals, but works on any Android device.*
