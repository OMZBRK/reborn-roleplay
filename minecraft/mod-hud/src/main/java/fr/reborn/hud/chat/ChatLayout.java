package fr.reborn.hud.chat;

/**
 * Géométrie partagée du chat Reborn pour aligner les 3 blocs façon Paladium :
 * le panneau des messages, la barre de saisie et le bouton emoji — tous à la
 * MÊME largeur. Évite que la barre fasse toute la page et que le texte déborde.
 */
public final class ChatLayout {

    /** Bord gauche du panneau (collé au bord écran). */
    public static final int LEFT = 0;
    /** x du texte / des têtes dans le panneau. */
    public static final int TEXT_X = 4;
    /** Gouttière réservée à la tête (8) + gap (2). */
    public static final int HEAD_GUTTER = 10;
    /** Côté du bouton emoji. */
    public static final int EMOJI_BTN = 13;

    private ChatLayout() {}

    /** Largeur de wrap des messages (≈ largeur chat vanilla, cappée). */
    public static int areaW(int screenW) {
        return Math.min(Math.max(160, screenW - 8), 320);
    }

    /** Largeur totale du panneau / barre (assez large pour tête + ligne pleine). */
    public static int boxW(int screenW) {
        return areaW(screenW) + HEAD_GUTTER + 8;
    }

    /** x du bouton emoji (dans la barre, à droite). */
    public static int emojiBtnX(int screenW) {
        return boxW(screenW) - EMOJI_BTN - 2;
    }

    /** Largeur utile du champ de saisie (s'arrête avant le bouton emoji). */
    public static int inputW(int screenW) {
        return emojiBtnX(screenW) - TEXT_X - 3;
    }
}
