package com.reborn.shinobicore.douane;

/**
 * Aiguillage à la connexion (CANDIDATURE_ET_DOUANE.md §3.3 et §3.12) — règle pure, sans Bukkit.
 *
 * <p>Deux preuves possibles :
 * <ul>
 *   <li>la réponse de {@code GET /v1/game/acces/:mcUuid}, lue au pré-login ({@code play}, {@code douane} ou
 *       {@code aucune}) ;</li>
 *   <li>la marque {@code reborn.scope} posée par RebornGuardian après une attestation valide ({@code play} ou
 *       {@code douane}), jusqu'à 8 s après l'arrivée.</li>
 * </ul>
 *
 * <p>Règles :
 * <ol>
 *   <li>un membre du staff en jeu (op, grade {@code reborn.staff.*}) n'est jamais envoyé au hall : sélection ;</li>
 *   <li>un refus explicite de l'API ({@code aucune}) expulse ;</li>
 *   <li><b>refus par défaut</b> : sans aucune preuve, on attend ; au bout du délai, expulsion ;</li>
 *   <li>une preuve {@code douane} ne mène qu'au hall ; si les deux sources divergent, la plus restrictive gagne ;</li>
 *   <li>la sélection n'ouvre que sur une preuve positive de {@code play}.</li>
 * </ol>
 */
public final class Aiguillage {

    /** Portée lue d'une source. {@link #AUCUNE} n'existe que du côté de l'API (refus explicite). */
    public enum Portee {
        PLAY, DOUANE, AUCUNE;

        /** Valeur de l'API ou de la marque ; null pour une valeur inconnue (jamais l'équivalent de {@code play}). */
        public static Portee lire(String brut) {
            if (brut == null) return null;
            return switch (brut) {
                case "play" -> PLAY;
                case "douane" -> DOUANE;
                case "aucune" -> AUCUNE;
                default -> null;
            };
        }
    }

    /** Ce qu'on fait du joueur. */
    public enum Issue { SELECTION, DOUANE, EXPULSER, ATTENDRE }

    /** Décision et sa source (pour le journal). */
    public record Decision(Issue issue, String source) {}

    private Aiguillage() {}

    /**
     * @param staff     grade staff en jeu (op ou {@code reborn.staff.*})
     * @param api       portée renvoyée par l'API au pré-login, null sans réponse
     * @param guardian  marque {@code reborn.scope} de RebornGuardian, null si absente ; {@link Portee#AUCUNE} est
     *                  ignorée (Guardian ne la pose jamais)
     * @param ecouleMs  temps écoulé depuis l'arrivée
     * @param delaiMs   délai maximal sans preuve
     */
    public static Decision decider(boolean staff, Portee api, Portee guardian, long ecouleMs, long delaiMs) {
        if (staff) return new Decision(Issue.SELECTION, "staff");
        if (api == Portee.AUCUNE) return new Decision(Issue.EXPULSER, "api");
        Portee g = guardian == Portee.AUCUNE ? null : guardian;
        if (api == null && g == null) {
            return ecouleMs >= delaiMs ? new Decision(Issue.EXPULSER, "aucune preuve") : new Decision(Issue.ATTENDRE, "");
        }
        String source = api != null && g != null ? "api+guardian" : api != null ? "api" : "guardian";
        if (api == Portee.DOUANE || g == Portee.DOUANE) return new Decision(Issue.DOUANE, source);
        return new Decision(Issue.SELECTION, source);
    }

    /**
     * Contrôle tardif : le joueur a été aiguillé vers la sélection sur la seule réponse de l'API, et la marque de
     * Guardian arrive ensuite. Vrai si elle est plus restrictive (douane) : le joueur doit être expulsé pour
     * revérification.
     */
    public static boolean marqueTardiveRestrictive(Portee guardian) {
        return guardian == Portee.DOUANE;
    }
}
