package com.reborn.shinobicore.communication;

import com.reborn.shinobicore.communication.CommData.TypeVoliere;
import com.reborn.shinobicore.communication.CommData.Voliere;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Règles pures de la cloche et des volières (sans Bukkit) : réglages, trajets autorisés, délais d'acheminement, coûts,
 * anti-spam de la cloche (CLOCHE_ET_MISSIVES.md §2.5, §3.1, §3.3). Tout part d'ici côté serveur.
 */
public final class CommRegles {

    private CommRegles() {}

    /** Réglages ({@code communication.*} de la config de ShinobiCore). */
    public record Reglages(int rayonDefaut, int delaiCloche, int delaiPersonne, int dangerParHeure, int texteCloche,
                           int dureeNormale, int dureeImportante, int dureeDanger,
                           long coutVillage, long coutAutre, int delaiBase, int blocsParMinute, int maxPays,
                           int maxAutre, int gardeJours, int texteMissive, int adresseMax, int destMax, int dureeAlerte) {

        public static Reglages defaut() {
            return new Reglages(250, 120, 600, 3, 200, 10, 12, 15, 10, 40, 60, 250, 480, 1200, 7, 600, 40, 10, 8);
        }

        public static Reglages lire(ConfigurationSection c) {
            Reglages d = defaut();
            if (c == null) return d;
            return new Reglages(
                    Math.max(16, c.getInt("cloche.rayon-defaut", d.rayonDefaut)),
                    Math.max(0, c.getInt("cloche.delai-cloche-secondes", d.delaiCloche)),
                    Math.max(0, c.getInt("cloche.delai-personne-secondes", d.delaiPersonne)),
                    Math.max(0, c.getInt("cloche.danger-par-heure", d.dangerParHeure)),
                    Math.max(20, Math.min(400, c.getInt("cloche.texte-max", d.texteCloche))),
                    Math.max(3, c.getInt("cloche.duree.normale", d.dureeNormale)),
                    Math.max(3, c.getInt("cloche.duree.importante", d.dureeImportante)),
                    Math.max(3, c.getInt("cloche.duree.danger", d.dureeDanger)),
                    Math.max(0, c.getLong("voliere.cout-village", d.coutVillage)),
                    Math.max(0, c.getLong("voliere.cout-autre-village", d.coutAutre)),
                    Math.max(0, c.getInt("voliere.delai-base-secondes", d.delaiBase)),
                    Math.max(1, c.getInt("voliere.blocs-par-minute", d.blocsParMinute)),
                    Math.max(0, c.getInt("voliere.delai-max-pays-secondes", d.maxPays)),
                    Math.max(0, c.getInt("voliere.delai-max-autre-village-secondes", d.maxAutre)),
                    Math.max(1, c.getInt("voliere.garde-jours", d.gardeJours)),
                    Math.max(40, Math.min(2000, c.getInt("voliere.texte-max", d.texteMissive))),
                    Math.max(10, Math.min(80, c.getInt("voliere.adresse-max", d.adresseMax))),
                    Math.max(1, Math.min(30, c.getInt("voliere.destinataires-max", d.destMax))),
                    Math.max(3, c.getInt("voliere.duree-alerte", d.dureeAlerte)));
        }

        public int duree(String type) {
            return switch (type) { case "importante" -> dureeImportante; case "danger" -> dureeDanger; default -> dureeNormale; };
        }
    }

    public static final String[] TYPES = {"normale", "importante", "danger"};

    public static String type(String s) {
        for (String t : TYPES) if (t.equalsIgnoreCase(s)) return t;
        return null;
    }

    /* ================================================================ trajets */

    /**
     * Le trajet {@code a → b} est-il permis ? {@code null} = oui, sinon la raison. Dans le pays (même village) : toute
     * volière ; hors du pays : seulement de tour à tour ; la volière du palais n'est jointe que depuis son pays (le seuil
     * « Écrire à la volière du palais » est vérifié à part).
     */
    public static String route(Voliere a, Voliere b) {
        if (a == null || b == null) return "Volière introuvable.";
        if (a.faction.equals(b.faction)) return null;
        if (b.type == TypeVoliere.PALAIS) return "La volière du palais ne reçoit que de son village.";
        if (a.type != TypeVoliere.TOUR || b.type != TypeVoliere.TOUR) return "Hors du pays, un corbeau ne part que de tour à tour.";
        return null;
    }

    /** Délai d'acheminement en secondes ({@code distance} &lt; 0 : autre monde = délai maximal). */
    public static int delai(Voliere a, Voliere b, double distance, Reglages k) {
        if (a == null || b == null) return k.maxAutre();
        if (a.id.equals(b.id) || b.type == TypeVoliere.PALAIS && a.faction.equals(b.faction)) return k.delaiBase();
        boolean pays = a.faction.equals(b.faction);
        int max = pays ? k.maxPays() : k.maxAutre();
        if (distance < 0) return Math.max(k.delaiBase(), max);
        int tranches = (int) Math.floor(distance / k.blocsParMinute());
        int s = pays ? k.delaiBase() + 60 * tranches : 60 * Math.max(1, (int) Math.ceil(distance / k.blocsParMinute()));
        return Math.max(k.delaiBase(), Math.min(Math.max(k.delaiBase(), max), s));
    }

    /** Coût d'une copie (ryo). */
    public static long cout(Voliere a, Voliere b, Reglages k) {
        return a != null && b != null && !a.faction.equals(b.faction) ? k.coutAutre() : k.coutVillage();
    }

    /* ================================================================ anti-spam de la cloche */

    /**
     * Peut-on sonner ? {@code null} = oui, sinon la raison. {@code derniereCloche} : dernière annonce de cette cloche ;
     * {@code dernierePersonne} : dernière annonce de ce personnage (toutes cloches) ; {@code dangersHeure} : annonces
     * « danger » du village dans l'heure.
     */
    public static String refusCloche(long now, long derniereCloche, long dernierePersonne, int dangersHeure, String type,
                                     long taireJusqua, Reglages k) {
        return refusCloche(now, derniereCloche, dernierePersonne, dangersHeure, type, taireJusqua, false, k);
    }

    /**
     * Idem ; {@code sansDelai} (seuil « Sonner sans délai », Kage, animateur) lève tout l'anti-spam : seule une cloche
     * tue par le staff refuse encore.
     */
    public static String refusCloche(long now, long derniereCloche, long dernierePersonne, int dangersHeure, String type,
                                     long taireJusqua, boolean sansDelai, Reglages k) {
        if (taireJusqua > now) return "La cloche a été tue par le staff (encore " + minutes(taireJusqua - now) + ").";
        if (sansDelai) return null;
        if (derniereCloche > 0 && now - derniereCloche < k.delaiCloche() * 1000L)
            return "La cloche vient de sonner : encore " + minutes(k.delaiCloche() * 1000L - (now - derniereCloche)) + ".";
        if (dernierePersonne > 0 && now - dernierePersonne < k.delaiPersonne() * 1000L)
            return "Tu as sonné il y a peu : encore " + minutes(k.delaiPersonne() * 1000L - (now - dernierePersonne)) + ".";
        if ("danger".equals(type) && dangersHeure >= k.dangerParHeure())
            return "Déjà " + dangersHeure + " alertes « danger » dans l'heure pour ce village.";
        return null;
    }

    /** « 1 min 20 s », « 8 min », « 40 s ». */
    public static String minutes(long ms) {
        long s = Math.max(1, (ms + 999) / 1000);
        if (s < 60) return s + " s";
        long m = s / 60, r = s % 60;
        return r == 0 || m >= 5 ? m + " min" : m + " min " + r + " s";
    }
}
