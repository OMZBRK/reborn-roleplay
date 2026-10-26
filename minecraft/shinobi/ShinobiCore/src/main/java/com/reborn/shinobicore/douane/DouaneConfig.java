package com.reborn.shinobicore.douane;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture de la section {@code douane:} de {@code config.yml} (CANDIDATURE_ET_DOUANE.md §3.2). Instantané immuable,
 * relu par {@code /sc reload} (sauf {@code enabled} et {@code monde}, lus au démarrage).
 */
final class DouaneConfig {

    /** Un point de la config (monde de la douane). */
    record Point(double x, double y, double z, float yaw, float pitch) {
        Location en(World w) {
            return new Location(w, x, y, z, yaw, pitch);
        }
    }

    /** Un bureau : point du candidat, point de l'examinateur, zone autorisée du candidat. */
    record BureauConf(String id, Point candidat, Point examinateur, Zone zone) {}

    final boolean enabled;
    final String monde;
    final boolean creerMonde;
    final long heureFixe;
    final boolean provisoire;
    final Point hall;
    final Zone zoneHall;
    final List<BureauConf> bureaux;
    final List<String> questions;
    final long preuveMs, httpMs, repriseMs, reportMs, reportGraceMs, renvoiMs, abandonMs;
    final int expulsionTicks, bienvenueTicks;
    final String voixCouper, voixRendre;
    /** Douane active sans coupure de la voix au hall fermé : acceptée explicitement (§3.4 demande la voix coupée). */
    final boolean voixNonCoupeeAcceptee;
    final java.time.ZoneId fuseau;

    private DouaneConfig(FileConfiguration c) {
        enabled = c.getBoolean("douane.enabled", false);
        String m = c.getString("douane.monde", "douane");
        monde = m == null || m.isBlank() ? "douane" : m.strip();
        creerMonde = c.getBoolean("douane.creer-monde", true);
        heureFixe = Math.floorMod(c.getLong("douane.heure-fixe", 6000L), 24000L);
        provisoire = c.getBoolean("douane.hall-provisoire", false);
        hall = point(section(c, "douane.hall.point"), new Point(0.5, 65.0, 0.5, 0f, 0f));
        zoneHall = zone(section(c, "douane.hall"), hall, c.getDouble("douane.hall.rayon", 16.0));
        List<BureauConf> bs = new ArrayList<>();
        ConfigurationSection sb = section(c, "douane.bureaux");
        if (sb != null) {
            for (String id : sb.getKeys(false)) {
                ConfigurationSection b = section(sb, id);
                if (b == null) continue;
                Point pc = point(section(b, "candidat"), null);
                Point pe = point(section(b, "examinateur"), null);
                if (pc == null || pe == null) continue;
                Point milieu = new Point((pc.x() + pe.x()) / 2, Math.min(pc.y(), pe.y()), (pc.z() + pe.z()) / 2, 0f, 0f);
                bs.add(new BureauConf(id, pc, pe, zone(b, milieu, b.getDouble("rayon", 6.0))));
            }
        }
        bureaux = List.copyOf(bs);
        questions = List.copyOf(c.getStringList("douane.questions"));
        // 10 s au moins : l'attestation de Guardian peut arriver jusqu'à 8 s après l'arrivée (§3.3). En dessous, sa
        // marque tardive échapperait à la surveillance.
        preuveMs = secondes(c, "douane.delais.preuve-secondes", 10, 10, 30);
        httpMs = Math.max(1000L, Math.min(3000L, c.getLong("douane.delais.http-ms", 2500L)));
        repriseMs = secondes(c, "douane.delais.reprise-minutes", 10, 1, 60) * 60L;
        reportMs = secondes(c, "douane.delais.report-minutes", 20, 5, 120) * 60L;
        reportGraceMs = secondes(c, "douane.delais.report-grace-minutes", 5, 1, 30) * 60L;
        renvoiMs = secondes(c, "douane.delais.renvoi-secondes", 30, 10, 600);
        abandonMs = secondes(c, "douane.delais.abandon-heures", 48, 1, 168) * 3600L;
        expulsionTicks = (int) (secondes(c, "douane.delais.expulsion-secondes", 8, 3, 30) / 50L);
        bienvenueTicks = (int) (secondes(c, "douane.delais.bienvenue-secondes", 3, 1, 10) / 50L);
        voixCouper = texte(c, "douane.voix.couper");
        voixRendre = texte(c, "douane.voix.rendre");
        voixNonCoupeeAcceptee = c.getBoolean("douane.voix.non-coupee-acceptee", false);
        java.time.ZoneId z;
        try { z = java.time.ZoneId.of(c.getString("douane.fuseau", "Europe/Paris")); }
        catch (RuntimeException e) { z = java.time.ZoneId.of("Europe/Paris"); }
        fuseau = z;
    }

    static DouaneConfig lire(FileConfiguration c) {
        return new DouaneConfig(c);
    }

    /** Les deux commandes de la voix sont remplies (une coupure sans commande pour rendre laisserait muet). */
    boolean voixCoupable() {
        return !voixCouper.isEmpty() && !voixRendre.isEmpty();
    }

    /** La douane peut être active : voix du hall fermé coupable, ou absence de coupure acceptée explicitement. */
    boolean voixValide() {
        return voixCoupable() || voixNonCoupeeAcceptee;
    }

    BureauConf bureau(String id) {
        for (BureauConf b : bureaux) if (b.id().equalsIgnoreCase(id)) return b;
        return null;
    }

    /**
     * Section du fichier, sinon celle du jar (valeurs par défaut), sans rien créer. {@code getConfigurationSection}
     * créerait une section vide quand seule la valeur par défaut existe : sur un serveur dont le {@code config.yml}
     * date d'avant la douane, les bureaux du jar seraient alors perdus.
     */
    private static ConfigurationSection section(ConfigurationSection c, String chemin) {
        Object v = c == null ? null : c.get(chemin);
        return v instanceof ConfigurationSection s ? s : null;
    }

    /** Valeur bornée, convertie en millisecondes (l'unité de la clé est donnée par son nom). */
    private static long secondes(FileConfiguration c, String cle, long defaut, long min, long max) {
        long v = c.getLong(cle, defaut);
        if (v < min || v > max) v = defaut;
        return v * 1000L;
    }

    private static String texte(FileConfiguration c, String cle) {
        String s = c.getString(cle, "");
        return s == null ? "" : s.strip();
    }

    private static Point point(ConfigurationSection s, Point defaut) {
        if (s == null || !s.contains("x") || !s.contains("y") || !s.contains("z")) return defaut;
        return new Point(s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw", 0.0), (float) s.getDouble("pitch", 0.0));
    }

    /** {@code zone: {min: {x,y,z}, max: {x,y,z}}} si présente, sinon un cylindre de {@code rayon} autour du centre. */
    private static Zone zone(ConfigurationSection s, Point centre, double rayon) {
        if (s != null) {
            ConfigurationSection min = section(s, "zone.min");
            ConfigurationSection max = section(s, "zone.max");
            if (min != null && max != null) {
                return Zone.pave(min.getDouble("x"), min.getDouble("y"), min.getDouble("z"),
                        max.getDouble("x"), max.getDouble("y"), max.getDouble("z"));
            }
        }
        return Zone.cylindre(centre.x(), centre.y(), centre.z(), rayon);
    }
}
