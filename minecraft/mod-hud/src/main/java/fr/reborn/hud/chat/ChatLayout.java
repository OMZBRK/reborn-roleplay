package fr.reborn.hud.chat;

/**
 * Géométrie partagée du chat Reborn pour aligner les blocs façon Paladium :
 * le panneau des messages et la barre de saisie ont la MÊME largeur et la même
 * marge gauche ; le bouton emoji est une boîte SÉPARÉE, juste après la barre
 * (petit espace), sur la même ligne ; le picker s'ouvre à DROITE (pas sur le chat).
 */
public final class ChatLayout {

    /** Marge gauche (un peu d'air, pas collé au bord écran). */
    public static final int LEFT = 6;
    /** x du texte / des têtes dans le panneau (relatif au bord écran). */
    public static final int TEXT_X = LEFT + 4;
    /** Gouttière réservée à la tête (8) + gap (2). */
    public static final int HEAD_GUTTER = 10;
    /** Côté du bouton emoji. */
    public static final int EMOJI_BTN = 13;
    /** Espace entre la fin de la barre de saisie et le bouton emoji. */
    public static final int EMOJI_GAP = 4;

    private ChatLayout() {}

    /** Largeur de wrap des messages (≈ largeur chat vanilla, cappée). */
    public static int areaW(int screenW) {
        return Math.min(Math.max(160, screenW - 8), 320);
    }

    /** Largeur du panneau / barre (assez large pour tête + ligne pleine). */
    public static int boxW(int screenW) {
        return areaW(screenW) + HEAD_GUTTER + 8;
    }

    /** x droit de la barre (fin de la barre de saisie). */
    public static int barRight(int screenW) {
        return LEFT + boxW(screenW);
    }

    /** x du bouton emoji : APRÈS la barre, avec un petit espace. */
    public static int emojiBtnX(int screenW) {
        return barRight(screenW) + EMOJI_GAP;
    }

    /** Largeur utile du champ de saisie (toute la barre, le bouton est dehors). */
    public static int inputW(int screenW) {
        return boxW(screenW) - (TEXT_X - LEFT) - 4;
    }
}
