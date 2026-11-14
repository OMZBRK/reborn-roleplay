package com.reborn.shinobicore.dojutsu;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

/**
 * Règles pures du Byakugan (lot 5, étape B ; docs/gameplay/DEPLACEMENTS.md §12) : qui y a droit, ce qu'il coûte, ce qui
 * le refuse. Aucune dépendance à Bukkit : testées par {@code ByakuganReglesTest}.
 */
public final class ByakuganRegles {

    /** Type d'œil du Byakugan dans la chirurgie des yeux ({@code Oeil.normType}). */
    public static final String OEIL = "byakugan";

    private ByakuganRegles() {}

    /**
     * Réglages lus dans {@code dojutsu.byakugan} (config.yml de ShinobiCore).
     *
     * @param actif          module allumé (sinon : aucun droit, la touche O ne fait rien)
     * @param portee         blocs : personnes vues, à travers les murs compris
     * @param pctParSeconde  part de la réserve de chakra dépensée par seconde (0,1 = 0,1 %)
     * @param yeux           il faut au moins un œil qui voit, du clan ou un Byakugan greffé (chirurgie des yeux)
     * @param reprendreSense {@code /sense dojutsu} (clans du Byakugan) et {@code /sense byakugan} passent par ce module
     * @param signeRayon     blocs : les joueurs proches voient les yeux pâles du porteur (0 = jamais)
     */
    public record Reglages(boolean actif, double portee, double pctParSeconde, boolean yeux, boolean reprendreSense,
                           double signeRayon) {

        public static final Reglages DEFAUT = new Reglages(true, 50.0, 0.1, true, true, 12.0);

        public Reglages {
            portee = Math.max(4.0, Math.min(128.0, portee));
            pctParSeconde = Math.max(0.0, Math.min(10.0, pctParSeconde));
            signeRayon = Math.max(0.0, Math.min(32.0, signeRayon));
        }
    }

    /** Minuscules, sans accents ni espaces autour : « Hyūga » → « hyuga », « Ōtsutsuki » → « otsutsuki ». */
    public static String plier(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim(), Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) b.append(c);
        }
        return b.toString().toLowerCase(Locale.ROOT);
    }

    /** Le clan du personnage est-il dans la liste {@code dojutsu.byakugan.clans} (accents et casse ignorés) ? */
    public static boolean clanAutorise(String clan, Collection<String> clans) {
        if (clan == null || clan.isBlank() || clans == null) return false;
        String c = plier(clan);
        for (String x : clans) if (c.equals(plier(x))) return true;
        return false;
    }

    /**
     * Les yeux laissent-ils voir le Byakugan ? Au moins un œil qui voit (pas aveugle) et qui est soit un Byakugan, soit
     * l'œil natif du clan (les Ōtsutsuki ont un œil natif « normal » dans la chirurgie des yeux, leur Byakugan vient du
     * clan). Un Hyūga dont les deux Byakugan ont été prélevés, ou qui a reçu des yeux ordinaires, ne l'a plus.
     */
    public static boolean yeuxPermettent(String typeGauche, boolean aveugleGauche, String typeDroit, boolean aveugleDroit,
                                         String natif) {
        return oeilPermet(typeGauche, aveugleGauche, natif) || oeilPermet(typeDroit, aveugleDroit, natif);
    }

    private static boolean oeilPermet(String type, boolean aveugle, String natif) {
        if (aveugle) return false;
        String t = plier(type);
        return OEIL.equals(t) || t.equals(plier(natif));
    }

    /** Chakra à retirer pour une période de {@code periodeMs} (réserve {@code max}, {@code pct} % par seconde). */
    public static double coutPeriode(double max, double pctParSeconde, long periodeMs) {
        if (max <= 0 || pctParSeconde <= 0 || periodeMs <= 0) return 0.0;
        return max * pctParSeconde / 100.0 * periodeMs / 1000.0;
    }

    /** Secondes de vision que paie une réserve à {@code partRestante} (0 à 1). Infini si gratuit. */
    public static double autonomieSecondes(double partRestante, double pctParSeconde) {
        if (pctParSeconde <= 0) return Double.POSITIVE_INFINITY;
        return Math.max(0.0, partRestante) * 100.0 / pctParSeconde;
    }

    /**
     * Ce que le serveur sait du joueur à l'instant où il relit le Byakugan (activation, puis toutes les 0,5 s, et à
     * chaque changement d'état : KO, mort, cinématique…).
     *
     * @param techniqueRefus raison de la matrice d'états pour {@code TECHNIQUE} quand elle vaut 0 (null : permise, même
     *                       limitée : entravé, sans mudra, le Byakugan reste possible)
     */
    public record Contexte(boolean mortRp, boolean aTerre, boolean inconscient, boolean spectateur, boolean cinematique,
                           boolean douane, boolean horsJeu, boolean ecranNoir, String techniqueRefus) {
    }

    /**
     * Hors jeu pour le Byakugan : sélection de personnage, ou, pour la vision du joueur, mode staff (aucune mécanique RP,
     * comme ShinobiCombat) ou aucun personnage actif. La vision de test du staff ({@code staff}) passe en mode staff.
     */
    public static boolean horsJeu(boolean selection, boolean staff, boolean modeStaff, boolean sansPerso) {
        return selection || (!staff && (modeStaff || sansPerso));
    }

    /**
     * Pourquoi le Byakugan est refusé (ou s'arrête), null s'il est permis. Ordre : ce qui touche le corps d'abord, puis le
     * cadre, puis la matrice d'états, puis l'écran noir du staff. Phrases courtes au tutoiement, comme la matrice.
     */
    public static String refus(Contexte c) {
        if (c.mortRp()) return "Ton personnage est mort.";
        if (c.aTerre()) return "Tu es à terre.";
        if (c.inconscient()) return "Tu es inconscient.";
        if (c.spectateur()) return "Impossible en spectateur.";
        if (c.cinematique()) return "Cinématique en cours.";
        if (c.douane()) return "Douane : aucune mécanique RP.";
        if (c.horsJeu()) return "Hors jeu.";
        if (c.techniqueRefus() != null && !c.techniqueRefus().isBlank()) return c.techniqueRefus();
        if (c.ecranNoir()) return "Écran noir.";
        return null;
    }

    /** Sous-commandes de {@code /sense} reprises par ce module (le reste de ShinobiSense ne change pas). */
    public enum Reprise { AUCUNE, DOJUTSU, STAFF }

    /**
     * Que faire d'une ligne de commande tapée ({@code /sense dojutsu}, {@code /sentir yeux}, {@code /shinobisense:sense
     * byakugan}…) ? {@code DOJUTSU} : le dōjutsu du joueur (repris seulement pour un clan du Byakugan, l'appelant décide) ;
     * {@code STAFF} : la vision de test du staff.
     */
    public static Reprise reprise(String ligne) {
        if (ligne == null) return Reprise.AUCUNE;
        String l = ligne.trim();
        if (l.startsWith("/")) l = l.substring(1);
        String[] m = l.toLowerCase(Locale.ROOT).split("\\s+");
        if (m.length < 2) return Reprise.AUCUNE;
        String cmd = m[0];
        int deuxPoints = cmd.indexOf(':');
        if (deuxPoints >= 0) {
            if (!cmd.substring(0, deuxPoints).equals("shinobisense")) return Reprise.AUCUNE;
            cmd = cmd.substring(deuxPoints + 1);
        }
        if (!cmd.equals("sense") && !cmd.equals("sentir") && !cmd.equals("perception")) return Reprise.AUCUNE;
        return switch (m[1]) {
            case "dojutsu", "doujutsu", "yeux" -> Reprise.DOJUTSU;
            case "byakugan" -> Reprise.STAFF;
            default -> Reprise.AUCUNE;
        };
    }
}
