package com.reborn.shinobicore.api;

import com.reborn.shinobicore.gui.framework.ScreenManager;
import org.bukkit.entity.Player;

/**
 * Minimal navigation contract the engine screen framework needs from a
 * plugin's GUI router: a way home and the shared screen lifecycle.
 * Plugins implement this on their concrete router (which typically
 * carries many more navigation methods for its own screens).
 */
@Stable
public interface ScreenRouter {

    /** Open the plugin's hub / landing screen for {@code p}. */
    void openHub(Player p);

    /** The shared screen framework this router's screens ride on. */
    ScreenManager screens();
}
