package com.reborn.shinobicore.douane;

import java.util.Locale;

/**
 * Petites règles de la douane, pures et testables (CANDIDATURE_ET_DOUANE.md §3.6 à §3.10). Les textes vus du joueur
 * sont ici pour rester identiques partout (commande, HUD du mod, repli sans mod).
 */
public final class ReglesDouane {

    /** Résultats acceptés par {@code POST /v1/game/douane/decision}. */
    public static final String APPROUVE = "APPROVED", A_REVOIR = "NEEDS_REVISION", REFUSE = "REJECTED";
    /** Bornes du contrat : motif visible du joueur, notes privées. */
    public static final int MOTIF_MAX = 2000, NOTES_MAX = 4000;

    public static final String LIGNE_FERME = "DOUANE · Ouverture dès l'arrivée d'un examinateur";
    public static final String LIGNE_BUREAU = "DOUANE · Entretien en cours";
    public static final String TEXTE_VERIFICATION = "Vérification de l'accès…";
    public static final String TEXTE_APPEL = "Un examinateur t'appelle";
    public static final String TEXTE_BIENVENUE = "Bienvenue sur Reborn";
    public static final String TEXTE_NEGATIF =
            "La douane n'est pas validée aujourd'hui. Le commentaire de l'examinateur est dans ton launcher.";
    /** Décision en attente d'envoi (API muette, §3.9) : le candidat quitte la douane, le résultat suit au launcher. */
    public static final String TEXTE_RESULTAT_DIFFERE = "La douane est terminée. Résultat sous peu dans ton launcher.";
    /** Valeur {@code resultat} du message {@code resultat} pour une décision en attente d'envoi. */
    public static final String RESULTAT_DIFFERE = "PENDING";
    public static final String EXPULSION_SANS_PREUVE = "Accès non vérifié. Réessaie dans un instant.";
    public static final String EXPULSION_DOUANE_FERMEE = "La douane est fermée pour le moment. Réessaie plus tard.";
    public static final String EXPULSION_REVERIFIER = "Accès à revérifier. Reconnecte-toi dans un instant.";
    public static final String EXPULSION_DOUANE_INACTIVE = "La douane n'est pas ouverte sur ce serveur.";
    public static final String EXPULSION_REPORT =
            "Séance reportée : aucun examinateur n'est disponible. Réserve une nouvelle séance dans le launcher.";
    public static final String EXPULSION_REDEMARRAGE = "La douane redémarre. Reconnecte-toi dans un instant.";
    public static final String PREFIXE_RETRAIT = "Tu as été retiré de la douane. Motif : ";

    private ReglesDouane() {}

    /** « 1ᵉʳ », « 2ᵉ », « 3ᵉ »… */
    public static String rang(int n) {
        return n <= 1 ? "1ᵉʳ" : n + "ᵉ";
    }

    /** Ligne du HUD d'un candidat au hall. */
    public static String ligneAttente(boolean ouvert, int rang) {
        if (!ouvert) return LIGNE_FERME;
        return rang > 0 ? "DOUANE · En attente · " + rang(rang) : "DOUANE · En attente";
    }

    /** Mot de la commande → résultat du contrat ; null si inconnu. */
    public static String resultat(String mot) {
        if (mot == null) return null;
        return switch (mot.toLowerCase(Locale.ROOT)) {
            case "accepter", "accepte", "approved" -> APPROUVE;
            case "revoir", "arevoir", "needs_revision" -> A_REVOIR;
            case "refuser", "refuse", "rejected" -> REFUSE;
            default -> null;
        };
    }

    /** Libellé français d'un résultat. */
    public static String libelle(String resultat) {
        if (APPROUVE.equals(resultat)) return "Accepté";
        if (A_REVOIR.equals(resultat)) return "À revoir";
        if (REFUSE.equals(resultat)) return "Refusé";
        return String.valueOf(resultat);
    }

    /**
     * Contrôle local d'une décision, avant l'envoi : renvoie le message d'erreur, ou null si elle peut partir. L'API
     * revérifie tout (400 sinon).
     */
    public static String verifierDecision(String resultat, String motif, String notes) {
        if (!APPROUVE.equals(resultat) && !A_REVOIR.equals(resultat) && !REFUSE.equals(resultat)) {
            return "Décision inconnue : accepter, revoir ou refuser.";
        }
        String m = motif == null ? "" : motif.strip();
        if (!APPROUVE.equals(resultat) && m.isEmpty()) {
            return "Le motif est obligatoire pour « " + libelle(resultat) + " » (il est visible du candidat).";
        }
        if (m.length() > MOTIF_MAX) return "Motif trop long (" + MOTIF_MAX + " caractères au plus).";
        if (notes != null && notes.strip().length() > NOTES_MAX) {
            return "Notes privées trop longues (" + NOTES_MAX + " caractères au plus).";
        }
        return null;
    }

    /**
     * File d'envoi locale : on ne renvoie que sur une coupure réseau (délai dépassé compris), un 429 ou un 5xx. Un
     * autre 4xx est définitif.
     */
    public static boolean aRenvoyer(int code, boolean reseau) {
        return reseau || code == 0 || code == 429 || code >= 500;
    }

    /** Durée d'un refus local quand la place (et donc la fin de sa fenêtre d'entrée) est inconnue. */
    public static final long REFUS_SANS_PLACE_MS = 2L * 3600_000L;

    /**
     * Fin d'un refus local de la douane (retrait, séance reportée) : la fin de la fenêtre d'entrée de la place, et au
     * moins le délai de reprise (retour après une coupure) ; place inconnue : {@link #REFUS_SANS_PLACE_MS}.
     */
    public static long finRefus(long entreeFermeAt, long maintenant, long repriseMs) {
        return entreeFermeAt > 0 ? Math.max(entreeFermeAt, maintenant + repriseMs) : maintenant + REFUS_SANS_PLACE_MS;
    }

    /**
     * Le refus local vaut-il pour cette arrivée ? Oui pour la même place, ou quand l'une des deux est inconnue (refus par
     * défaut) ; non pour une autre réservation : la séance suivante reste possible.
     */
    public static boolean refusApplicable(String placeRefusee, String placeActuelle) {
        if (placeRefusee == null || placeRefusee.isBlank() || placeActuelle == null || placeActuelle.isBlank()) return true;
        return placeRefusee.equals(placeActuelle);
    }

    /** Reconnexion après une coupure : la place est gardée pendant {@code repriseMs}. */
    public static boolean reprisePossible(long departAt, long maintenant, long repriseMs) {
        return departAt > 0 && maintenant - departAt <= repriseMs;
    }

    /**
     * Séance reportée : l'heure de convocation + {@code reportMs} est passée ET le hall est fermé <b>sans
     * interruption</b> depuis au moins {@code graceMs}. Une fermeture brève (examinateur seul qui sort du mode, plante
     * ou se déconnecte) ne coûte donc pas la séance à tout le hall. {@code fermeDepuis} 0 = hall ouvert.
     */
    public static boolean seanceReportee(long convocationAt, long fermeDepuis, long maintenant, long reportMs,
                                         long graceMs) {
        return convocationAt > 0 && fermeDepuis > 0 && maintenant - fermeDepuis >= graceMs
                && maintenant >= convocationAt + reportMs;
    }
}
