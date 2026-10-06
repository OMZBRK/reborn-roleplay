package fr.reborn.hud.effets;

import fr.reborn.hud.byakugan.ByakuganClient;
import fr.reborn.hud.meteo.MeteoClient;
import fr.reborn.hud.mixin.GameRendererByakuganAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Choisit le filtre plein écran (post effect) du jeu : un seul peut être actif à la fois.
 * Priorité : KO &gt; Byakugan &gt; météo &gt; aucun. Ne retire jamais un filtre qui ne vient pas du mod (ex. spectateur creeper).
 * Appelé à chaque tick client (le jeu peut retirer le filtre : changement de caméra, F4…).
 */
public final class FiltresEcran {

    private FiltresEcran() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.player == null) return;
        Identifier voulu = fr.reborn.hud.ko.KoClient.actif() ? fr.reborn.hud.ko.KoClient.FILTRE
                : ByakuganClient.active() ? ByakuganClient.FILTRE
                : MeteoClient.actif() ? MeteoClient.FILTRE : null;
        Identifier courant = mc.gameRenderer.currentPostEffect();
        if (voulu != null) {
            if (!voulu.equals(courant)) ((GameRendererByakuganAccessor) mc.gameRenderer).reborn$setPostEffect(voulu);
        } else if (ByakuganClient.FILTRE.equals(courant) || MeteoClient.FILTRE.equals(courant)
                || fr.reborn.hud.ko.KoClient.FILTRE.equals(courant)) {
            mc.gameRenderer.clearPostEffect();
        }
    }
}
