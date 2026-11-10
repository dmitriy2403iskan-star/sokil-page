# MK15 Android Shell — project workspace

This branch is the safe application-layer workspace for the MK15 Android platform project.

## Current device baseline

Collected from the read-only MK15 analyzer report:

- Device: SIYI MK15 / Qualcomm MSM8953 family
- Android: 9 (API 28)
- Build fingerprint: `iWaylink/MC401/MC401_GWL:9/PKQ1.190626.001/191105.PR031.003:user/release-keys`
- Kernel: Linux 4.9.112-perf
- ABI: arm64-v8a, armeabi-v7a, armeabi
- Display: 1776 × 1080, 480 dpi, 60 Hz
- RAM: approximately 1.9 GB
- Internal storage: approximately 9.6 GB

These values are a starting snapshot, not proof that an AOSP build for this exact board is available.

## Project goal

Build a maintainable Android tablet-style software experience for the MK15, beginning with an installable application-layer shell and usability improvements. Keep the factory system and its recovery path intact while compatibility is unknown.

## Work areas

1. **Launcher shell** — home screen, app shortcuts, settings entry points, responsive layout for 1776 × 1080.
2. **Touch usability** — investigate browser/site touch-target problems using ordinary Android accessibility and WebView/browser behavior. No private controller services or control interfaces.
3. **App environment** — document Android 9/API 28 compatibility, APK installation constraints, storage and memory budgets.
4. **Build pipeline** — reproducible Gradle builds and manually published test APKs.
5. **Compatibility research** — identify board/vendor requirements before considering any operating-system image work.

## Explicit non-goals for this workspace

- No flashing, bootloader unlocking, partition writing, factory reset, or destructive recovery commands.
- No modifications to radio links, flight-control firmware, MCU protocols, private controller services, or kernel input drivers.
- No implementation that converts physical drone-control sticks into Android/USB gamepad axes.
- No claim of AOSP/ROM compatibility until a matching device tree, vendor blobs, kernel configuration, and recovery strategy are independently verified.

## Development approach

- Prefer small, reversible, installable APK iterations.
- Keep the stock firmware untouched.
- Record each test with APK version, device build, expected behavior, actual behavior, and screenshots/logs where appropriate.
- Do not require the tester to repeat diagnostics already completed unless new evidence justifies it.

## Initial milestone

Create a basic launcher-style shell prototype that can be installed as a regular APK, validate its layout and touch behavior on the MK15, then iterate based on test feedback. This milestone does not replace Android System UI or the factory launcher.
