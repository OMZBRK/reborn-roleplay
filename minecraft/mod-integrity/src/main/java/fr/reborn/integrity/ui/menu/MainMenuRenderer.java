package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
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

    /** Position X gauche de la card OST. */
    public static int ostCardX(int screenW) {
        return 20;
    }

    /** Position Y top de la card OST. */
    public static int ostCardY(int screenH) {
        return screenH - OSTPlayerV2.CARD_H - 60;
    }

    /** Position Y du PressSpacePrompt (centré horizontalement). */
    public static int promptY(int screenH) {
        return Math.round(screenH * 0.55f);
    }

    /** Position X centrée du ServerInfoMini (à droite de la card OST). */
    public static int serverInfoCenterX(int screenW) {
        return screenW - 140;
    }

    /** Position Y du ServerInfoMini (alignée verticalement à la card OST). */
    public static int serverInfoY(int screenH) {
        return ostCardY(screenH) + OSTPlayerV2.CARD_H / 2 - 12;
    }

    /** Position X centrée des 3 icons (centre-bas, style Lunar Client). */
    public static int bottomIconsCenterX(int screenW) {
        return screenW / 2;
    }

    /** Position Y du groupe d'icons centrés. */
    public static int bottomIconsY(int screenH) {
        return screenH - 78;
    }

    /** Taille de chaque icon button du groupe centré. */
    public static final int BOTTOM_ICON_SIZE = 24;
    /** Espacement horizontal entre les icons centrés. */
    public static final int BOTTOM_ICON_SPACING = 8;

    private MainMenuRenderer() {}

    /**
     * Multiplicateur de taille responsive — réduit l'UI sur petites
     * fenêtres pour éviter le chevauchement des composants. Plage 0.65..1.0.
     *
     * <ul>
     *   <li>screenW ≤ 800  → 0.65 (très petit, debugging dev)</li>
     *   <li>800-1000        → 0.80</li>
     *   <li>1000-1200       → 0.90</li>
     *   <li>≥ 1200          → 1.00 (rendu cible — full HD+)</li>
     * </ul>
     */
    public static float responsiveScale(int screenW) {
        if (screenW <= 800) return 0.65f;
        if (screenW <= 1000) return 0.80f;
        if (screenW <= 1200) return 0.90f;
        return 1.0f;
    }

    /**
     * Rend l'ensemble des éléments passifs du main menu. Appelé depuis
     * le mixin à la fin du render, avant les widgets cliquables qui sont
     * rendus par super.render() (ils sont dans drawables).
     */
    public static void render(DrawContext ctx, int screenW, int screenH) {
        // 1. Sakura particles overlay (animation continue par-dessus le
        //    panorama vanilla).
        SakuraParticles.INSTANCE.render(ctx, screenW, screenH);

        // 2. Bandeau gradient HAUT — masque ciblé pour le logo MC vanilla
        //    (centré ~y=30-110). On garde la zone opaque réduite à 80px
        //    puis un fade long sur 100px pour mieux blend avec le panorama
        //    qui reste majoritairement visible.
        ctx.fill(0, 0, screenW, 80, Colors.BACKGROUND);
        ctx.fillGradient(0, 80, screenW, 180, Colors.BACKGROUND, 0x00050608);

        // 3. Bandeau gradient BAS pour masquer version Fabric + copyright
        //    Mojang. Symétrique du haut, fade plus court.
        ctx.fillGradient(0, screenH - 80, screenW, screenH - 40, 0x00050608, Colors.BACKGROUND);
        ctx.fill(0, screenH - 40, screenW, screenH, Colors.BACKGROUND);

        // 4. Logo central Reborn — vient PAR-DESSUS le bandeau haut.
        CentralLogo.render(ctx, screenW, screenH);

        // 5. ServerInfo à droite (centré sur serverInfoCenterX, alignée
        //    verticalement avec le centre de la card OST).
        ServerInfoMini.render(ctx, serverInfoCenterX(screenW), serverInfoY(screenH));

        // 6. Credits coin bas-gauche — sous la card OST.
        CreditsCorner.render(ctx, screenW, screenH);

        // 7. OST card background. Les 4 IconButton controls de la card sont
        //    re-rendus PAR-DESSUS ce background par le TitleScreenMixin —
        //    sans ça ils seraient masqués.
        OSTPlayerV2.renderBackground(ctx, ostCardX(screenW), ostCardY(screenH));

        // 8. Pill background derrière les 3 icons centrés en bas (Lunar-style).
        //    Les icons eux-mêmes sont rendus PAR-DESSUS via reborn$persistentIcons.
        int iconsTotalW = 3 * BOTTOM_ICON_SIZE + 2 * BOTTOM_ICON_SPACING;
        int pillPadding = 10;
        int pillX = bottomIconsCenterX(screenW) - iconsTotalW / 2 - pillPadding;
        int pillY = bottomIconsY(screenH) - 6;
        int pillW = iconsTotalW + 2 * pillPadding;
        int pillH = BOTTOM_ICON_SIZE + 12;
        DrawHelpers.roundedOutlinedRect(ctx, pillX, pillY, pillW, pillH,
            pillH / 2, Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);
    }

    /** Couleur de masque opaque pour le panneau credits — couvre la
     *  version Fabric + copyright Mojang vanilla. */
    @SuppressWarnings("unused")
    private static final int FOOTER_MASK = Colors.BACKDROP_85;
}
