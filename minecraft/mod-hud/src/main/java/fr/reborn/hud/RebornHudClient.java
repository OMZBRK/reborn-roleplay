package fr.reborn.hud;

import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.ui.style.IconTextures;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrée client du mod Reborn HUD.
 *
 * <p>Singleton static {@link #config()} exposé parce que les Mixins ont
 * besoin de lire l'offset au moment du render, depuis du code qui ne
 * peut pas passer par DI.
 */
public final class RebornHudClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("reborn-hud");

    private static HudConfig CONFIG;

    @Override
    public void onInitializeClient() {
        CONFIG = HudConfig.load();
        LOGGER.info("config loaded");

        IconTextures.registerAll();
        HudKeybinds.registerClient();

        LOGGER.info("Reborn HUD mod ready.");
    }

    public static HudConfig config() {
        if (CONFIG == null) {
            throw new IllegalStateException("HudConfig not initialized yet");
        }
        return CONFIG;
    }
}
