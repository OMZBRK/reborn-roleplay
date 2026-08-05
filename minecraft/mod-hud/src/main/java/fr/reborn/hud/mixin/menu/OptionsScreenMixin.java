package fr.reborn.hud.mixin.menu;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Filtre l'OptionsScreen vanilla pour cacher les categories qui n'ont
 * pas leur place sur un serveur RP avec resource pack force et skin
 * server-managed. Liste configurable cote {@link #HIDDEN_KEYS} —
 * commenter les lignes pour re-afficher.
 *
 * <p>Approche identique a TitleScreenMixin : on filtre par translation
 * key des boutons. Si un mod (Sodium, Mod Menu...) ajoute son propre
 * bouton avec une autre key, on ne le touche pas. Donc Sodium Options
 * restera accessible meme apres ce filtre.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends Screen {

    private static final Logger REBORN_LOGGER =
        LoggerFactory.getLogger("reborn-hud/options-mixin");

    /**
     * Translation keys des boutons a masquer. On cible :
     * - skin parts (le serveur gere les skins en RP)
     * - resource packs (le serveur force le sien)
     * - online / realms (pas pertinent pour notre serveur unique)
     */
    private static final String[] HIDDEN_KEYS = {
        "options.skinCustomisation",
        "options.resourcepack",
        "options.online",
    };

    protected OptionsScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void reborn$hideUnwantedOptions(CallbackInfo ci) {
        List<GuiEventListener> toRemove = new ArrayList<>();
        for (GuiEventListener child : this.children()) {
            if (child instanceof Button btn) {
                String label = btn.getMessage().getString();
                for (String key : HIDDEN_KEYS) {
                    if (label.equalsIgnoreCase(Component.translatable(key).getString())) {
                        toRemove.add(child);
                        break;
                    }
                }
            }
        }
        for (GuiEventListener e : toRemove) {
            this.removeWidget(e);
        }
        if (!toRemove.isEmpty()) {
            REBORN_LOGGER.info(
                "options screen : {} categories masquees (RP server)",
                toRemove.size()
            );
        }
    }
}
