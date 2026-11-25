# SYNK MOTION

Wear OS motion controller prototype for controlling a cursor on a paired device.

## Project status

**Milestone 0 — repository and build scaffold.** The first app iteration focuses on reading the watch gyroscope and accelerometer, displaying live sensor status, and providing a neutral calibration baseline.

A first Bluetooth Classic HID mouse implementation is now included. The watch registers a HID mouse peripheral and sends relative pointer reports from gyroscope motion when a host is connected. Actual HID peripheral availability varies by watch firmware and must be verified on the Galaxy Watch 8. Pinch-click is not implemented yet; the current build has no reliable pinch sensor input.

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


## Bluetooth HID implementation

The HID peripheral/report-descriptor approach is informed by the open-source WearMouse project by Google LLC contributors (Apache License 2.0): https://github.com/ginkage/wearmouse. SYNK MOTION's Kotlin implementation is independently written; the upstream license is retained in `THIRD_PARTY_LICENSES/Apache-2.0-WearMouse.txt`.

Pair from the computer's Bluetooth settings by selecting **SYNK MOTION** while the watch app is open. The app must remain in the foreground for this prototype. If the watch firmware does not expose Android's HID_DEVICE profile, pairing as a mouse will not be available on that device.
