package com.reborn.shinobicore.inventory;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Réglages de l'inventaire ({@code config.yml} : {@code inventaire.*} et {@code rarete.par-id}), avec des
 * <b>valeurs par défaut dans le code</b> : une clé absente du fichier du serveur garde la valeur d'ici (cf.
 * PLUGINS_REFERENCE §5.1). Relu tout seul après un {@code /sc reload} (la config change d'instance).
 */
public final class InventaireConfig {

    private static FileConfiguration source;
    private static InventaireConfig courant = new InventaireConfig(null);

    // ─── ramassage ───
    public final double distance;
    public final long cadenceMs;
    public final double poidsMaxRatio;
    // ─── surcharge (ratios du poids max) ───
    public final double seuilCharge, seuilSurcharge, seuilImmobile;
    public final double vitChargeDe, vitChargeA, vitSurchargeDe, vitSurchargeA, vitImmobile;
    public final double sautCharge, sautSurcharge, sautImmobile;
    public final boolean sprintCharge;
    // ─── objets au sol ───
    public final long disparitionMs, disparitionKoMs, balayageTicks;
    public final boolean parcheminsEternels;
    public final Set<String> eternelsIds;
    // ─── journaux ───
    public final boolean journalFichier;
    public final RpRarity journalGardeMin;
    // ─── arme (étui) ───
    public final Set<String> armeIds;
    public final boolean armeVanilla;
    // ─── cases vanilla 9-35 ───
    public final boolean balayerCasesCachees;
    // ─── teinture des cosmétiques (18/10) ───
    /** Ids Nexo teintables (le tissu du modèle porte une source de teinte minecraft:dye) ; tout id bandeau_* l'est aussi. */
    public final Set<String> teintureIds;
    // ─── rareté ───
    public final Map<String, RpRarity> rareteParId;

    private InventaireConfig(FileConfiguration c) {
        distance = d(c, "inventaire.ramassage.distance", 3.0);
        cadenceMs = Math.max(50L, l(c, "inventaire.ramassage.cadence-ms", 250L));
        poidsMaxRatio = d(c, "inventaire.ramassage.poids-max-pourcent", 130.0) / 100.0;

        seuilCharge = d(c, "inventaire.surcharge.charge.de", 100.0) / 100.0;
        seuilSurcharge = d(c, "inventaire.surcharge.surcharge.de", 115.0) / 100.0;
        seuilImmobile = d(c, "inventaire.surcharge.immobile.de", 130.0) / 100.0;
        vitChargeDe = d(c, "inventaire.surcharge.charge.vitesse-de", 0.90);
        vitChargeA = d(c, "inventaire.surcharge.charge.vitesse-a", 0.70);
        sautCharge = d(c, "inventaire.surcharge.charge.saut", 1.0);
        sprintCharge = c != null && c.getBoolean("inventaire.surcharge.charge.sprint", false);
        vitSurchargeDe = d(c, "inventaire.surcharge.surcharge.vitesse-de", 0.70);
        vitSurchargeA = d(c, "inventaire.surcharge.surcharge.vitesse-a", 0.35);
        sautSurcharge = d(c, "inventaire.surcharge.surcharge.saut", 0.0);
        vitImmobile = d(c, "inventaire.surcharge.immobile.vitesse", 0.0);
        sautImmobile = d(c, "inventaire.surcharge.immobile.saut", 0.0);

        disparitionMs = Math.round(d(c, "inventaire.sol.disparition-minutes", 10.0) * 60_000L);
        disparitionKoMs = Math.round(d(c, "inventaire.sol.disparition-ko-minutes", 15.0) * 60_000L);
        balayageTicks = Math.max(20L, l(c, "inventaire.sol.balayage-secondes", 10L) * 20L);
        parcheminsEternels = c == null || c.getBoolean("inventaire.sol.jamais-detruits.parchemins", true);
        eternelsIds = ids(c, "inventaire.sol.jamais-detruits.ids", List.of());

        journalFichier = c == null || c.getBoolean("inventaire.journal.fichier", true);
        RpRarity g = c == null ? null : RpRarity.parse(c.getString("inventaire.journal.poste-de-garde-des", "RARE"));
        journalGardeMin = g != null ? g : RpRarity.RARE;

        armeIds = ids(c, "inventaire.arme.ids", List.of("katana", "ninjato", "tanto", "kendo", "gunbai",
            "baton_enma", "madara_scythe", "bo", "nodachi", "wakizashi"));
        armeVanilla = c == null || c.getBoolean("inventaire.arme.vanilla", true);

        balayerCasesCachees = c == null || c.getBoolean("inventaire.cases-cachees.balayage", true);

        teintureIds = ids(c, "inventaire.teinture.ids", List.of("bandeau_ninja"));

        Map<String, RpRarity> t = new HashMap<>(defautsRarete());
        ConfigurationSection s = c == null ? null : c.getConfigurationSection("rarete.par-id");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                RpRarity r = RpRarity.parse(s.getString(k));
                if (r != null) t.put(k.toLowerCase(Locale.ROOT), r);
            }
        }
        rareteParId = Map.copyOf(t);
    }

    /** Objet teintable (bandeau…) : id listé dans {@code inventaire.teinture.ids}, ou tout id {@code bandeau_*}. */
    public boolean teintable(String id) {
        return id != null && (teintureIds.contains(id) || id.startsWith("bandeau_"));
    }

    /** Table par défaut (objets Nexo du pack de test) — complétée / remplacée par {@code rarete.par-id}. */
    private static Map<String, RpRarity> defautsRarete() {
        Map<String, RpRarity> m = new HashMap<>();
        m.put("kunai", RpRarity.COMMUN);
        m.put("kendo", RpRarity.COMMUN);
        m.put("baton_enma", RpRarity.EPIQUE);
        m.put("gunbai", RpRarity.LEGENDAIRE);
        m.put("madara_scythe", RpRarity.LEGENDAIRE);
        m.put("masque_anbu", RpRarity.RARE);
        m.put("masque_hannya", RpRarity.RARE);
        m.put("bandeau_konoha", RpRarity.PEU_COMMUN);
        m.put("akatsuki_hat", RpRarity.EPIQUE);
        m.put("sac_sacoche", RpRarity.COMMUN);
        m.put("sac_bandouliere", RpRarity.PEU_COMMUN);
        m.put("sac_dos", RpRarity.PEU_COMMUN);
        m.put("sac_lourd", RpRarity.RARE);
        return m;
    }

    /** Réglages courants (relus si la config du plugin a été rechargée). */
    public static InventaireConfig get(Plugin plugin) {
        FileConfiguration c = plugin.getConfig();
        if (c != source) {
            source = c;
            courant = new InventaireConfig(c);
        }
        return courant;
    }

    /** Derniers réglages lus (sans plugin sous la main) ; défauts si jamais lus. */
    public static InventaireConfig get() {
        return courant;
    }

    private static double d(FileConfiguration c, String k, double def) {
        return c == null ? def : c.getDouble(k, def);
    }

    private static long l(FileConfiguration c, String k, long def) {
        return c == null ? def : c.getLong(k, def);
    }

    private static Set<String> ids(FileConfiguration c, String k, List<String> def) {
        List<String> l = c == null || !c.isList(k) ? def : c.getStringList(k);
        Set<String> out = new HashSet<>();
        for (String s : l) if (s != null && !s.isBlank()) out.add(s.trim().toLowerCase(Locale.ROOT));
        return Set.copyOf(out);
    }
}
