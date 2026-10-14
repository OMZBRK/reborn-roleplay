package com.reborn.shinobicore.nourriture;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Réglages du bloc {@code nourriture} de {@code config.yml} (docs/gameplay/NOURRITURE.md §11). Relu par
 * {@code /sc reload} via {@link NourritureModule#reload()}. Les valeurs par défaut du code sont celles de la v3 validée
 * le 14/10.
 */
public final class NourritureConfig {

    /** Un effet posé à la dernière bouchée : type de modificateur, intensité, durée. */
    public record Effet(String type, double intensite, long dureeMs) {}

    /** Un plat (ou la ration, ou la pilule) : nom, satiété, durée du repas, icône, plafond, reste, effets. */
    public record Plat(String id, String nom, double satiete, double dureeS, String icone, double plafond, String reste,
                       List<Effet> effets) {}

    /** Une offre de l'échoppe (onglet Vivres). */
    public record Vivre(String id, String nom, long prix, int quantite, boolean cuisinierSeulement, String recette) {}

    public boolean actif;
    // satiété
    public double depart, pertePs, techniqueParDix, vitesseSprint, affame;
    public double fAssis, fMarche, fTrot, fSprint, fNage, fCombat;
    public long combatMs, afkMs;
    // régénération
    public double seuilRegen, regenPvPs, plafondAta;
    public long pauseCoupMs;
    // faim
    public double faimEndurance;
    // cuisine
    public double prepS, distanceCuisine;
    public int platsParPanier;
    public long chaudMs;
    public boolean posteDeSonEntreprise;
    // repas
    public double assisMult, effetsFroid;
    public long pauseRepasMs;
    // tendre
    public double distanceTendre;
    public int delaiTendre;
    // pilule
    public double piluleSatiete, piluleIntensite;
    public long piluleRechargeMs, piluleRefusCoupMs, piluleDureeMs;
    // catalogues
    public final Map<String, Plat> plats = new LinkedHashMap<>();
    public Plat ration;
    public final Map<String, Vivre> vivres = new LinkedHashMap<>();

    public static NourritureConfig lire(FileConfiguration c) {
        NourritureConfig n = new NourritureConfig();
        String r = "nourriture.";
        n.actif = c.getBoolean(r + "actif", true);
        n.depart = c.getDouble(r + "satiete.depart", 80);
        n.pertePs = c.getDouble(r + "satiete.perte-par-seconde", 0.0125);
        n.techniqueParDix = c.getDouble(r + "satiete.technique-par-10-chakra", 0.2);
        n.vitesseSprint = c.getDouble(r + "satiete.vitesse-sprint", 5.8);
        n.affame = c.getDouble(r + "satiete.affame", 20);
        n.fAssis = c.getDouble(r + "satiete.activite.assis", 0.5);
        n.fMarche = c.getDouble(r + "satiete.activite.marche", 1.0);
        n.fTrot = c.getDouble(r + "satiete.activite.trot", 1.5);
        n.fSprint = c.getDouble(r + "satiete.activite.sprint", 4.0);
        n.fNage = c.getDouble(r + "satiete.activite.nage", 2.0);
        n.fCombat = c.getDouble(r + "satiete.activite.combat", 3.0);
        n.combatMs = (long) (c.getDouble(r + "satiete.combat-secondes", 10) * 1000);
        n.afkMs = (long) (c.getDouble(r + "satiete.afk-secondes", 300) * 1000);
        n.seuilRegen = c.getDouble(r + "regeneration.seuil", 90);
        n.regenPvPs = c.getDouble(r + "regeneration.pv-pourcent-par-seconde", 0.25);
        n.pauseCoupMs = (long) (c.getDouble(r + "regeneration.pause-apres-coup-secondes", 30) * 1000);
        n.plafondAta = c.getDouble(r + "regeneration.plafond-ata", 30);
        n.faimEndurance = c.getDouble(r + "faim.endurance-recuperation", 0.5);
        n.prepS = c.getDouble(r + "cuisine.preparation-secondes", 15);
        n.platsParPanier = Math.max(1, c.getInt(r + "cuisine.plats-par-panier", 2));
        n.distanceCuisine = c.getDouble(r + "cuisine.distance-max", 4);
        n.chaudMs = (long) (c.getDouble(r + "cuisine.plat-chaud-minutes", 10) * 60_000);
        n.posteDeSonEntreprise = c.getBoolean(r + "cuisine.poste-de-son-entreprise", true);
        n.assisMult = c.getDouble(r + "repas.assis-multiplicateur", 2);
        n.pauseRepasMs = (long) (c.getDouble(r + "repas.pause-apres-coup-secondes", 30) * 1000);
        n.effetsFroid = c.getDouble(r + "repas.effets-froid", 0.5);
        n.distanceTendre = c.getDouble(r + "tendre.distance", 3);
        n.delaiTendre = Math.max(3, c.getInt(r + "tendre.delai-secondes", 10));
        n.piluleSatiete = c.getDouble(r + "pilule.satiete", 20);
        n.piluleIntensite = c.getDouble(r + "pilule.endurance", 0.4);
        n.piluleDureeMs = (long) (c.getDouble(r + "pilule.duree-secondes", 180) * 1000);
        n.piluleRechargeMs = (long) (c.getDouble(r + "pilule.recharge-minutes", 10) * 60_000);
        n.piluleRefusCoupMs = (long) (c.getDouble(r + "pilule.refus-apres-coup-secondes", 30) * 1000);

        ConfigurationSection ps = c.getConfigurationSection(r + "plats");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(id);
                if (s != null) n.plats.put(id.toLowerCase(Locale.ROOT), plat(id.toLowerCase(Locale.ROOT), s, 100));
            }
        }
        ConfigurationSection rs = c.getConfigurationSection(r + "ration");
        n.ration = rs != null ? plat("ration_seche", rs, 60)
                : new Plat("ration_seche", "Ration sèche", 25, 20, "ration", 60, null, List.of());

        ConfigurationSection vs = c.getConfigurationSection(r + "vivres");
        if (vs != null) {
            for (String id : vs.getKeys(false)) {
                ConfigurationSection s = vs.getConfigurationSection(id);
                if (s == null) continue;
                String recette = s.getString("recette");
                String nom = s.getString("nom", recette != null && n.plats.containsKey(recette)
                        ? "Ingrédients : " + n.plats.get(recette).nom().toLowerCase(Locale.FRANCE) : id);
                n.vivres.put(id.toLowerCase(Locale.ROOT), new Vivre(id.toLowerCase(Locale.ROOT), nom,
                        Math.max(0, s.getLong("prix", 30)), Math.max(1, s.getInt("quantite", 1)),
                        s.getBoolean("cuisinier-seulement", recette != null), recette));
            }
        }
        return n;
    }

    private static Plat plat(String id, ConfigurationSection s, double plafondDef) {
        List<Effet> eff = new ArrayList<>();
        for (Map<?, ?> m : s.getMapList("effets")) {
            Object t = m.get("type");
            if (t == null) continue;
            eff.add(new Effet(String.valueOf(t).toLowerCase(Locale.ROOT), num(m.get("intensite"), 1),
                    (long) (num(m.get("duree-secondes"), 60) * 1000)));
        }
        return new Plat(id, s.getString("nom", id), s.getDouble("satiete", 20), Math.max(1, s.getDouble("duree-secondes", 30)),
                s.getString("icone", "bol"), s.getDouble("plafond", plafondDef), s.getString("reste", null), eff);
    }

    private static double num(Object o, double def) {
        if (o instanceof Number x) return x.doubleValue();
        try { return o == null ? def : Double.parseDouble(String.valueOf(o)); } catch (NumberFormatException e) { return def; }
    }
}
