package com.reborn.shinobicore.monnaie;

import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.inventory.RpRarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Réglages de la monnaie et de la banque (docs/gameplay/MONNAIE_ET_BANQUE.md, décisions du 15/10). <b>Tous les
 * défauts sont dans le code</b> : les sections {@code monnaie} et {@code banque} de {@code config.yml} sont
 * facultatives et ne surchargent que ce qu'elles contiennent.
 */
public final class MonnaieConfig {

    private static volatile MonnaieConfig instance = new MonnaieConfig();

    public static MonnaieConfig get() { return instance; }

    /** Un palier du coffre de compte : coût unique, cases, plafond des dépôts (≤ 0 : sans plafond). */
    public record Palier(int numero, String nom, long cout, int cases, long plafond) {}

    // ── bourse ──
    public long seuilGarnie = 100, seuilLourde = 5000;
    public long solMs = 30 * 60_000L;
    public long alerteRamasse = 5000;

    // ── vol sur un corps (décision 3 du 15/10 : 30 % au plus de la bourse) ──
    public int volPourcent = 30;
    public boolean volSansPlafondMort = true;
    public int fouilleSecondes = 3;
    public long volAlerte = 1000;
    public long corpsMs = 15 * 60_000L;
    public double fouilleDistance = 3.5;

    // ── guichet ──
    public double porteeGuichetier = 6.0;
    public double porteeGuichet = 5.0;
    public int fraisAlliePourcent = 5;
    public Map<String, Set<String>> allies = new HashMap<>();
    public Set<String> villagesRefuses = new HashSet<>(Set.of("deserteur", "exile", "nukenin"));

    // ── virement ──
    public int virementPourcent = 1;
    public long virementMin = 5;
    public int virementAutreVillagePourcent = 5;
    public long virementPlafondJour = 20_000;
    public long virementAlerte = 10_000;

    // ── échange garanti ──
    public int echangePourcent = 2;
    public long echangeMin = 10;
    public double echangeDistance = 4.0;
    public long echangeAlerte = 10_000;

    // ── paliers (décision 7 : 2 / 6 / 12 / 18 cases, 0 / 2 000 / 8 000 / 25 000) ──
    public List<Palier> paliers = new ArrayList<>(List.of(
            new Palier(1, "Livret", 0, 2, 5_000),
            new Palier(2, "Compte", 2_000, 6, 20_000),
            new Palier(3, "Compte scellé", 8_000, 12, 60_000),
            new Palier(4, "Chambre forte", 25_000, 18, 0)));

    // ── objets refusés au coffre (décision 8 : armes OUI, sauf armes RP légendaires / mythiques) ──
    public boolean refuserParchemins = true;
    public boolean refuserUniques = true;
    public boolean refuserVoles = true;
    public Set<String> refusIds = new HashSet<>(Set.of("parchemin_explosif", "kibakufuda", "fumigene", "bombe_fumigene"));
    public Set<String> armesRefuseesIds = new HashSet<>(Set.of("gunbai", "kubikiribocho", "samehada", "kiba", "nuibari",
            "shibuki", "hiramekarei", "kabutowari"));
    public RpRarity armesRefuseesRarete = RpRarity.LEGENDAIRE;
    public String tagLegendaire = "legendaire";

    // ── solde hebdomadaire ──
    public DayOfWeek soldeJour = DayOfWeek.SUNDAY;
    public int soldeHeure = 18;
    public String fuseau = "Europe/Paris";
    public int presenceHeures = 3;
    public Map<Rank, Long> bareme = new EnumMap<>(Rank.class);
    public long fonctionBanque = 300;
    public Map<String, Long> tresorInitial = new HashMap<>();

    // ── mort RP (décision 4) ──
    public int gelMortJours = 7;

    private MonnaieConfig() {
        bareme.put(Rank.ACADEMICIEN, 0L);
        bareme.put(Rank.GENIN, 200L);
        bareme.put(Rank.GENIN_CONFIRME, 300L);
        bareme.put(Rank.CHUNIN, 500L);
        bareme.put(Rank.KONIN, 700L);
        bareme.put(Rank.TOKUBETSU_JONIN, 900L);
        bareme.put(Rank.JONIN, 1200L);
        bareme.put(Rank.COMMANDANT_JONIN, 1600L);
        bareme.put(Rank.KAGE, 2000L);
    }

    public Palier palier(int n) {
        for (Palier p : paliers) if (p.numero() == n) return p;
        return paliers.get(0);
    }

    public int palierMax() { return paliers.get(paliers.size() - 1).numero(); }

    /** Relit la config (défauts du code pour tout ce qui manque). */
    public static void charger(Plugin plugin) {
        MonnaieConfig k = new MonnaieConfig();
        FileConfiguration c = plugin.getConfig();
        ConfigurationSection m = c.getConfigurationSection("monnaie");
        if (m != null) {
            k.seuilGarnie = m.getLong("etats.garnie", k.seuilGarnie);
            k.seuilLourde = m.getLong("etats.lourde", k.seuilLourde);
            k.solMs = m.getLong("sol-minutes", 30) * 60_000L;
            k.alerteRamasse = m.getLong("alerte-ramasse", k.alerteRamasse);
            k.volPourcent = Math.max(0, Math.min(100, m.getInt("vol.pourcent", k.volPourcent)));
            k.volSansPlafondMort = m.getBoolean("vol.sans-plafond-mort-rp", k.volSansPlafondMort);
            k.fouilleSecondes = Math.max(0, m.getInt("vol.fouille-secondes", k.fouilleSecondes));
            k.volAlerte = m.getLong("vol.alerte", k.volAlerte);
            k.corpsMs = m.getLong("vol.corps-minutes", 15) * 60_000L;
        }
        ConfigurationSection b = c.getConfigurationSection("banque");
        if (b != null) {
            k.porteeGuichetier = b.getDouble("guichet.portee-guichetier", k.porteeGuichetier);
            k.porteeGuichet = b.getDouble("guichet.portee", k.porteeGuichet);
            k.fraisAlliePourcent = b.getInt("guichet.frais-allie-pourcent", k.fraisAlliePourcent);
            ConfigurationSection al = b.getConfigurationSection("allies");
            if (al != null) {
                for (String v : al.getKeys(false)) {
                    Set<String> s = new HashSet<>();
                    for (String x : al.getStringList(v)) s.add(village(x));
                    k.allies.put(village(v), s);
                }
            }
            if (b.isList("villages-refuses")) {
                k.villagesRefuses = new HashSet<>();
                for (String x : b.getStringList("villages-refuses")) k.villagesRefuses.add(village(x));
            }
            k.virementPourcent = b.getInt("virement.frais-pourcent", k.virementPourcent);
            k.virementMin = b.getLong("virement.frais-min", k.virementMin);
            k.virementAutreVillagePourcent = b.getInt("virement.frais-autre-village-pourcent", k.virementAutreVillagePourcent);
            k.virementPlafondJour = b.getLong("virement.plafond-jour", k.virementPlafondJour);
            k.virementAlerte = b.getLong("virement.alerte", k.virementAlerte);
            k.echangePourcent = b.getInt("echange.frais-pourcent", k.echangePourcent);
            k.echangeMin = b.getLong("echange.frais-min", k.echangeMin);
            k.echangeDistance = b.getDouble("echange.distance", k.echangeDistance);
            k.echangeAlerte = b.getLong("echange.alerte", k.echangeAlerte);
            if (b.isList("paliers")) {
                List<Palier> ps = new ArrayList<>();
                int i = 1;
                for (Map<?, ?> e : b.getMapList("paliers")) {
                    ps.add(new Palier(i++, String.valueOf(e.containsKey("nom") ? e.get("nom") : "Palier " + i),
                            num(e.get("cout")), (int) num(e.get("cases")), num(e.get("plafond"))));
                }
                if (!ps.isEmpty()) k.paliers = ps;
            }
            ConfigurationSection r = b.getConfigurationSection("refuses");
            if (r != null) {
                k.refuserParchemins = r.getBoolean("parchemins", k.refuserParchemins);
                k.refuserUniques = r.getBoolean("uniques", k.refuserUniques);
                k.refuserVoles = r.getBoolean("voles", k.refuserVoles);
                if (r.isList("ids")) k.refusIds = lower(r.getStringList("ids"));
                if (r.isList("armes-ids")) k.armesRefuseesIds = lower(r.getStringList("armes-ids"));
                RpRarity rr = RpRarity.parse(r.getString("armes-rarete-min"));
                if (rr != null) k.armesRefuseesRarete = rr;
                k.tagLegendaire = r.getString("tag", k.tagLegendaire);
            }
            ConfigurationSection s = b.getConfigurationSection("solde");
            if (s != null) {
                try { k.soldeJour = DayOfWeek.valueOf(s.getString("jour", "SUNDAY").toUpperCase(Locale.ROOT)); }
                catch (IllegalArgumentException ignored) { }
                k.soldeHeure = s.getInt("heure", k.soldeHeure);
                k.fuseau = s.getString("fuseau", k.fuseau);
                k.presenceHeures = s.getInt("presence-heures", k.presenceHeures);
                k.fonctionBanque = s.getLong("fonction-banque", k.fonctionBanque);
                ConfigurationSection bar = s.getConfigurationSection("bareme");
                if (bar != null) for (String g : bar.getKeys(false)) k.bareme.put(Rank.from(g), bar.getLong(g));
            }
            ConfigurationSection t = b.getConfigurationSection("tresor-initial");
            if (t != null) for (String v : t.getKeys(false)) k.tresorInitial.put(village(v), t.getLong(v));
            k.gelMortJours = b.getInt("mort-rp.gel-jours", k.gelMortJours);
        }
        instance = k;
    }

    private static long num(Object o) {
        if (o instanceof Number n) return n.longValue();
        try { return o == null ? 0 : Long.parseLong(String.valueOf(o).trim()); } catch (NumberFormatException e) { return 0; }
    }

    private static Set<String> lower(List<String> l) {
        Set<String> s = new HashSet<>();
        for (String x : l) if (x != null && !x.isBlank()) s.add(x.trim().toLowerCase(Locale.ROOT));
        return s;
    }

    /** Clé de village : minuscules, sans accents ni espaces (« Konoha » → {@code konoha}). */
    public static String village(String v) {
        if (v == null) return "";
        return java.text.Normalizer.normalize(v.trim(), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
