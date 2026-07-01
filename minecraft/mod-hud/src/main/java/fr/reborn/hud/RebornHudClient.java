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

        // Overlay du menu d'interaction live (rendu HUD, pas un écran).
        net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register(
            (ctx, tickCounter) -> fr.reborn.hud.interaction.InteractionMode.INSTANCE.render(ctx));

        // Bandes noires cinéma (immersion) — toggle touche K.
        fr.reborn.hud.immersion.CinemaBars.INSTANCE.registerClient();

        // Preview auto après capture d'écran.
        fr.reborn.hud.screenshot.CapturePreview.INSTANCE.registerClient();

        // Désactive le narrateur Minecraft (demande user) — une fois, quand les
        // options sont prêtes.
        final boolean[] narratorDone = {false};
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (narratorDone[0] || client.options == null) return;
            narratorDone[0] = true;
            if (client.options.getNarrator().getValue() != net.minecraft.client.option.NarratorMode.OFF) {
                client.options.getNarrator().setValue(net.minecraft.client.option.NarratorMode.OFF);
                client.options.write();
                LOGGER.info("narrateur Minecraft désactivé");
            }
        });

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
