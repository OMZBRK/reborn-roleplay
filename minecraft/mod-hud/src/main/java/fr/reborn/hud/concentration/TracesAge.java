package fr.reborn.hud.concentration;

/**
 * L'âge d'une trace de pas tel qu'un pisteur l'estime (retour du 10/11) : jamais exact, de plus en plus flou à mesure que la
 * trace vieillit. Règle pure : l'âge en secondes donne un <b>palier</b> (des minutes rondes), le palier donne la phrase. Le
 * texte ne change qu'au passage d'un palier : rien ne bouge d'une seconde à l'autre.
 *
 * <table>
 *   <caption>Paliers (minutes entières écoulées)</caption>
 *   <tr><td>0</td><td>« Laissée à l'instant »</td></tr>
 *   <tr><td>1 à 4</td><td>« Laissée il y a quelques minutes »</td></tr>
 *   <tr><td>5 à 7 · 8 à 12 · 13 à 17 · 18 à 24</td><td>« Laissée il y a environ 5 / 10 / 15 / 20 min »</td></tr>
 *   <tr><td>25 à 34</td><td>« Laissée il y a une demi-heure »</td></tr>
 *   <tr><td>35 à 44 · 45 à 54</td><td>« Laissée il y a environ 40 / 50 min »</td></tr>
 *   <tr><td>55 à 74</td><td>« Laissée il y a environ une heure »</td></tr>
 *   <tr><td>75 à 119</td><td>« Laissée il y a environ 1 h 30 »</td></tr>
 *   <tr><td>120 et plus</td><td>« Laissée il y a plus de deux heures »</td></tr>
 * </table>
 */
public final class TracesAge {

    private TracesAge() {}

    /** Paliers, en minutes : la valeur rendue par {@link #palier}. */
    public static final int INSTANT = 0, QUELQUES = 1, DEMI_HEURE = 30, HEURE = 60, HEURE_ET_DEMIE = 90, PLUS_DEUX_HEURES = 120;

    /** Le palier d'une trace de {@code ageSecondes} : 0, 1, 5, 10, 15, 20, 30, 40, 50, 60, 90 ou 120. */
    public static int palier(long ageSecondes) {
        long m = Math.max(0L, ageSecondes) / 60L;
        if (m < 1) return INSTANT;
        if (m < 5) return QUELQUES;
        if (m < 8) return 5;
        if (m < 13) return 10;
        if (m < 18) return 15;
        if (m < 25) return 20;
        if (m < 35) return DEMI_HEURE;
        if (m < 45) return 40;
        if (m < 55) return 50;
        if (m < 75) return HEURE;
        if (m < 120) return HEURE_ET_DEMIE;
        return PLUS_DEUX_HEURES;
    }

    /** La seconde ligne du panneau pour un palier de {@link #palier} (constantes : rien n'est alloué). */
    public static String texte(int palier) {
        return switch (palier) {
            case INSTANT -> "Laissée à l'instant";
            case QUELQUES -> "Laissée il y a quelques minutes";
            case 5 -> "Laissée il y a environ 5 min";
            case 10 -> "Laissée il y a environ 10 min";
            case 15 -> "Laissée il y a environ 15 min";
            case 20 -> "Laissée il y a environ 20 min";
            case DEMI_HEURE -> "Laissée il y a une demi-heure";
            case 40 -> "Laissée il y a environ 40 min";
            case 50 -> "Laissée il y a environ 50 min";
            case HEURE -> "Laissée il y a environ une heure";
            case HEURE_ET_DEMIE -> "Laissée il y a environ 1 h 30";
            default -> "Laissée il y a plus de deux heures";
        };
    }
}
