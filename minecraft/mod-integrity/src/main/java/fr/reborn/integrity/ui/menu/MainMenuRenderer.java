package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.SakuraParticles;
import net.minecraft.client.gui.DrawContext;

/**
 * Orchestrateur du rendu visuel du main menu Reborn — appelle dans
 * l'ordre tous les composants passifs (logo, server info, credits,
 * fond OST). Les composants cliquables (PressSpacePrompt, IconButton
 * du quit / settings / globe / discord / OST controls) sont des
 * {@code ClickableWidget} ajoutés au screen via {@code addDrawableChild}
 * dans le mixin — ils se rendent automatiquement.
 *
 * <p>L'appelant doit avoir poussé une matrice avec Z élevé AVANT
 * d'appeler cette méthode si nécessaire (pour passer au-dessus des
 * vanilla draws masqués).
 *
 * @see fr.reborn.integrity.mixin.TitleScreenMixin
 */
public final class MainMenuRenderer {

    /** Position X gauche de la card OST. Public car le mixin en a besoin
     *  pour placer les IconButton enfants au bon endroit. */
    public static int ostCardX(int screenW) {
        return Math.round(screenW * 0.06f);
    }

    /** Position Y top de la card OST. */
    public static int ostCardY(int screenH) {
        return screenH - OSTPlayerV2.CARD_H - 60;
    }

    /** Position Y du PressSpacePrompt (centré horizontalement). */
    public static int promptY(int screenH) {
        return Math.round(screenH * 0.55f);
    }

    /** Position Y du ServerInfoMini (footer central, sous la card OST). */
    public static int serverInfoY(int screenH) {
        return ostCardY(screenH) + 22;
    }

    /** X right des BottomRightIcons (3 icons). */
    public static int bottomRightX(int screenW) {
        return screenW - 20;
    }

    /** Y top des BottomRightIcons. */
    public static int bottomRightY(int screenH) {
        return ostCardY(screenH) + 24;
    }

    private MainMenuRenderer() {}

    /**
     * Rend l'ensemble des éléments passifs du main menu. Appelé depuis
     * le mixin à la fin du render, avant les widgets cliquables qui sont
     * rendus par super.render() (ils sont dans drawables).
     */
    public static void render(DrawContext ctx, int screenW, int screenH) {
        // Pas de fill global sombre — le panorama vanilla reste pleinement
        // visible derrière. L'UI Reborn (logo, credits, OST card) dispose
        // de fonds opaques propres là où le contraste est nécessaire.

        // 1. Sakura particles overlay (animation continue par-dessus le
        //    panorama).
        SakuraParticles.INSTANCE.render(ctx, screenW, screenH);

        // 2. Logo central + sous-titre — couvre visuellement le logo MC
        //    vanilla qui est dessous.
        CentralLogo.render(ctx, screenW, screenH);

        // 3. ServerInfo footer centre.
        ServerInfoMini.render(ctx, screenW / 2, serverInfoY(screenH));

        // 4. Credits coin bas-gauche — couvre la version Fabric + copyright
        //    Mojang vanilla.
        CreditsCorner.render(ctx, screenW, screenH);

        // 5. OST card background. Les 4 IconButton controls de la card sont
        //    re-rendus PAR-DESSUS ce background par le TitleScreenMixin —
        //    sans ça ils seraient masqués.
        OSTPlayerV2.renderBackground(ctx, ostCardX(screenW), ostCardY(screenH));
    }

    /** Couleur de masque opaque pour le panneau credits — couvre la
     *  version Fabric + copyright Mojang vanilla. */
    @SuppressWarnings("unused")
    private static final int FOOTER_MASK = Colors.BACKDROP_85;
}
