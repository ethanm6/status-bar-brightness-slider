/*
 * Brightness Slider — status bar brightness gesture (LSPosed module).
 * Copyright (C) 2026 ethanm6
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Based on StatusBarBrightnessGesture by mbatthew (MIT); see LICENSE-MIT.
 */
package dev.module.statusbarbrightnessgesture;

import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

public class AutoBrightnessTileService extends TileService {

    static volatile long sLastPrefOpenMs = 0;

    @Override
    public void onStartListening() {
        super.onStartListening();
        syncTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (System.currentTimeMillis() - sLastPrefOpenMs < 500) return;
        Tile tile = getQsTile();
        boolean enabling = tile.getState() != Tile.STATE_ACTIVE;
        // The hook (in SystemUI) persists these — the app has no WRITE_SECURE_SETTINGS grant.
        Prefs.setPref(this, Prefs.KEY_AUTO_BRIGHTNESS, enabling ? 1 : 0);
        Prefs.setPref(this, Prefs.KEY_GESTURE_ENABLED, enabling ? 0 : 1);
        tile.setState(enabling ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }

    private void syncTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        int mode = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        tile.setState(mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.updateTile();
    }
}
