# Brightness Slider

An LSPosed module that adds a horizontal swipe gesture on the status bar to control screen brightness. Built with a modern Material You interface that adapts to your wallpaper colors.

## Features

- Swipe left/right on the status bar to adjust brightness
- Brightness indicator in your choice of shape (pill, droplet, circle, star) with customizable color (including Material You), opacity, text color, shadow, and vertical position
- Auto-brightness toggle via in-app switch or Quick Settings tile, with an option to keep the gesture active while auto-brightness is on
- Saves and restores manual brightness when toggling auto-brightness
- Adjustable brightness curve — matches the system slider by default, or skew it for finer control at the dim end
- Configurable sensitivity and edge padding
- Optional fullscreen swipe support and haptic feedback — brightness swipes don't trigger the back gesture or reveal the status bar

## Requirements

- Android 13+
- LSPosed

## Download

[Download the latest APK](https://github.com/ethanm6/status-bar-brightness-slider/releases/latest/download/brightness-slider-release.apk), or add the app to Obtainium for automatic updates:

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/ethanm6/status-bar-brightness-slider"><img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png" alt="Get it on Obtainium" height="72"></a>

## Setup

1. Install the APK
2. Enable the module in LSPosed — the recommended scope is pre-selected on fresh installs. Besides **System UI** it includes the system framework, which appears as **System Framework**, **Android System**, or both depending on your manager; keep every framework entry checked
3. Reboot

The framework scope keeps the status bar hidden during fullscreen brightness swipes; everything else works with the System UI scope alone.

No ADB commands or manual permission grants are needed.

## License

Licensed under the **GNU General Public License v3.0 or later** (GPL-3.0-or-later) — see [LICENSE](LICENSE).

## Credits

- Based on [StatusBarBrightnessGesture](https://github.com/mbatthew/StatusBarBrightnessGesture) by mbatthew, originally licensed under the MIT License — see [LICENSE-MIT](LICENSE-MIT).
- Launcher and Quick Settings tile icons adapted from [Lawnicons](https://github.com/LawnchairLauncher/lawnicons) by the Lawnchair team, licensed under the Apache License 2.0 — see [LICENSE-APACHE](LICENSE-APACHE).
