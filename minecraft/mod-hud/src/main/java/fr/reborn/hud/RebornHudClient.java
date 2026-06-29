package fr.reborn.hud;

import fr.reborn.hud.config.HudConfig;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.widget.DynamicPlayerBackground;
import fr.reborn.hud.ui.style.IconTextures;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entree client du mod Reborn HUD.
 *
 * <p>Init :
 * <ol>
 *   <li>Charge {@link HudConfig} (positions des elements HUD vanilla,
 *       presets, prefs chat) depuis {@code config/reborn-hud.json}.</li>
 *   <li>Enregistre les textures du pack {@code reborn-hud} (icones de
 *       l'editeur, ressources UI).</li>
 *   <li>Enregistre les keybindings (touche H = editeur HUD par defaut).</li>
 *   <li>Schedule la creation du browser MCEF qui rendra le background
 *       dynamique (skin 3D du joueur) du main menu. Le browser est cree
 *       une fois CEF initialise (~1-2s apres le boot du client).</li>
 * </ol>
 *
 * <p>Le singleton static {@link #config()} est expose parce que les
 * Mixins ont besoin de lire les offsets au moment du render, depuis
 * du code qui ne peut pas passer par DI.
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
        fr.reborn.hud.chat.ChatBlockCommands.register();

        // Extrait les assets dynamic-player + schedule la creation du
        // browser MCEF pour le main menu background.
        DynamicPlayerBackground.init();

        LOGGER.info("Reborn HUD mod ready.");
    }

    public static HudConfig config() {
        if (CONFIG == null) {
            throw new IllegalStateException("HudConfig not initialized yet");
        }
        return CONFIG;
    }
}
