package fr.reborn.hud.menu.connect;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.resources.Identifier;

/**
 * Textures et géométrie de la scène de chargement « nuit de pleine lune » ({@code textures/gui/chargement/*}, générées
 * par {@code tools/gen_chargement_textures.py} : le contrat des tailles et positions est en tête du générateur).
 *
 * <p>La scène fait {@value #VW}×{@value #VH} unités ; 1 texel = 1 unité ; affichée à k pixels écran par unité, k entier
 * le plus grand pour lequel la zone utile {@value #SAFE_W}×{@value #SAFE_H} tient dans la fenêtre (4 en 1080p, 3 en
 * 1366×768, 5 en 1440p, 8 en 4K). Ciel et premier plan débordent de la scène pour remplir les autres formats.
 */
final class ChargementArt {

    private ChargementArt() {}

    static Identifier tex(String n) { return DaKit.tex("chargement", n); }

    static final Identifier CIEL = tex("ciel"), LUNE = tex("lune"), NUAGES_VOILE = tex("nuages_voile"),
        VILLAGE = tex("village"), NUAGES_BAS = tex("nuages_bas"), AVANT = tex("avant"), CORBEAU = tex("corbeau"),
        FEUILLE = tex("feuille");

    static final int VW = 480, VH = 270;
    static final int SAFE_W = 440, SAFE_H = 240;

    /* ---- couches (unités de la scène) ---- */
    static final int EXT_X = 112, EXT_Y = 30;
    static final int CIEL_W = 704, CIEL_H = 300;
    static final int LUNE_X = 136, LUNE_Y = 14, LUNE_S = 208;
    static final int NUAGES_W = 960;
    static final int VOILE_Y = 36, VOILE_H = 84;
    static final int BAS_Y = 198, BAS_H = 72;
    static final int VILLAGE_Y = 170, VILLAGE_H = 100;
    static final int AVANT_Y = 118, AVANT_H = 152;
    static final int CB_W = 12, CB_H = 7, CB_N = 4;
    static final int FE_W = 5, FE_H = 5, FE_N = 4;

    /* ---- vitesses de défilement (unités / s, vers la droite : le vent) ---- */
    static final float V_VOILE = 4.5f, V_BAS = 7.0f;

    /* ---- couleurs ---- */
    /** Aplat au-dessus du ciel et voile des fondus (nuit profonde). */
    static final int NUIT = 0xFF03050D;
    /** Aplat sous le premier plan. */
    static final int SOL = 0xFF020409;
    static final int TEXTE = 0xFFC9D3EA;
    static final int OMBRE = 0xFF03050C;

    /* ---- barre de chargement pixel art (unités de la scène) ---- */
    /** Haut de la barre ; hauteur totale (intérieur 5 rangées + rail) ; largeur = 40 % de la largeur visible. */
    static final int BARRE_Y = 248, BARRE_H = 7;
    /** Cadre fin sombre, ombre intérieure, rail vide. */
    static final int CADRE = 0xFF26335E, RAIL_HAUT = 0xFF060A18, RAIL = 0xFF0B1229;
    /** Remplissage bleu nuit → lune, du haut (reflet) vers le bas, et tête claire. */
    static final int[] BLEUS = {0xFFAEC5F0, 0xFF7898DA, 0xFF5878C4, 0xFF4566AF, 0xFF34519A};
    static final int TETE = 0xFFDCE6FA;
    static final int ETOILE = 0xFFDCE4F5;
}
