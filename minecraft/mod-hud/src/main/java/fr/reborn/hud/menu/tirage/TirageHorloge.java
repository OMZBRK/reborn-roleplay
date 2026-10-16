package fr.reborn.hud.menu.tirage;

/**
 * Horloge du test de la Feuille (écran, caméra). En jeu : l'heure système. Au banc ({@link TirageDebug}) : une horloge
 * imposée qui avance de 50 ms par tick client capturé — le personnage (animation, particules) avance lui aussi d'un tick
 * client par capture : l'écran, la caméra et le monde restent synchrones et le film est régulier (20 i/s) quel que soit
 * le temps d'écriture des captures.
 */
public final class TirageHorloge {

    private static long impose = -1;
    /** Images rendues (banc : une capture par image nouvelle, jamais deux fois la même). */
    static int images;

    private TirageHorloge() {}

    public static long now() { return impose >= 0 ? impose : System.currentTimeMillis(); }

    static void imposer(long ms) { impose = ms; }

    static void avancer(long ms) { if (impose >= 0) impose += ms; }

    static void liberer() { impose = -1; }
}
