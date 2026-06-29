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
        try {
            Settings.Secure.putInt(getContentResolver(),
                    Prefs.KEY_AUTO_BRIGHTNESS, enabling ? 1 : 0);
            Settings.Secure.putInt(getContentResolver(),
                    Prefs.KEY_GESTURE_ENABLED, enabling ? 0 : 1);
        } catch (SecurityException ignored) {}
        Prefs.sendAll(this);
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
