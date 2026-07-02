# Brightness Gesture

An LSPosed module that adds a horizontal swipe gesture on the status bar to control screen brightness. Built with a modern Material You interface that adapts to your wallpaper colors.

## Features

- Swipe left/right on the status bar to adjust brightness
- Brightness indicator pill with customizable color (including Material You), opacity, text color, and vertical position
- Auto-brightness toggle via in-app switch or Quick Settings tile
- Saves and restores manual brightness when toggling auto-brightness
- Configurable sensitivity and edge padding
- Optional fullscreen swipe support and haptic feedback

## Requirements

- Android 14+
- LSPosed

## Download

[![Get it on Obtainium](https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/ethanm6/status-bar-brightness-slider)

## Setup

1. Install the APK
2. Enable the module in LSPosed with scope set to **System UI**
3. Reboot
4. Grant the permission via ADB (one-time):
   ```
   adb shell pm grant dev.module.statusbarbrightnessgesture android.permission.WRITE_SECURE_SETTINGS
   ```

## Based on

[StatusBarBrightnessGesture](https://github.com/mbatthew/StatusBarBrightnessGesture) by mbatthew — licensed under MIT.
