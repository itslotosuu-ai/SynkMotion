# SYNK MOTION

Wear OS motion controller prototype for controlling a cursor on a paired device.

## Project status

**Milestone 0 — repository and build scaffold.** The first app iteration focuses on reading the watch gyroscope and accelerometer, displaying live sensor status, and providing a neutral calibration baseline.

Bluetooth HID output and companion clients are intentionally not claimed as implemented yet. Support for Bluetooth HID Device on the target Galaxy Watch firmware must be verified before choosing the final transport.

## Planned components

- `watch/` — Wear OS app: motion sensing, calibration, gesture state, connection UI.
- `android-companion/` — future Android companion and accessibility-based pointer integration (user-enabled).
- `windows-companion/` — future Windows companion/configuration client.
- `core/` — shared motion filtering and gesture logic when the modules are introduced.

## Build

Requires JDK 17 and Android SDK Platform 35.

```bash
gradle :watch:assembleDebug
```

The debug APK is produced at `watch/build/outputs/apk/debug/watch-debug.apk`.

## Safety and privacy

Sensor data is processed locally by the watch app. No account, analytics, or network service is required by this prototype.
