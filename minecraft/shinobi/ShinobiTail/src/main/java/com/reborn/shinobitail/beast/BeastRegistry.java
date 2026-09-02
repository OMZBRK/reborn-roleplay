package com.reborn.shinobitail.beast;

import com.reborn.shinobitail.ShinobiTail;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads {@code beasts.yml} (personalities + beasts, with the stage power
 * curve from config.yml) and owns the per-beast Inner World locations
 * persisted in {@code data/innerworlds.yml}.
 *
 * <p>Stage resolution: every stage 1..tails gets a {@link BeastStage}.
 * Power defaults to {@code stage * (refTails / tails) ^ exponent}; any
 * key present under {@code beasts.<id>.stages.<n>} overrides the curve
 * value for that stage only. Aura range follows the explicit
 * {@code aura-range.by-stage} list (extrapolated past its end).
 */
public final class BeastRegistry {

    private static final double[] DEFAULT_AURA =
            {100, 280, 460, 640, 820, 1000, 1333, 1666, 2000};

    private final ShinobiTail plugin;
    private final Map<String, Personality> personalities = new LinkedHashMap<>();
    private final Map<String, BeastDefinition> beasts = new LinkedHashMap<>();
    private final Map<String, Location> innerWorlds = new LinkedHashMap<>();

    public BeastRegistry(ShinobiTail plugin) {
        this.plugin = plugin;
    }

    /* ------------------------------------------------------------ loading */

    public void load() {
        personalities.clear();
        beasts.clear();

        File file = new File(plugin.getDataFolder(), "beasts.yml");
        if (!file.exists()) plugin.saveResource("beasts.yml", false);
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);

        ConfigurationSection pSec = yml.getConfigurationSection("personalities");
        if (pSec != null) {
            for (String id : pSec.getKeys(false)) {
                personalities.put(id.toLowerCase(Locale.ROOT),
                        Personality.load(id, pSec.getConfigurationSection(id)));
            }
        }

        ConfigurationSection bSec = yml.getConfigurationSection("beasts");
        if (bSec != null) {
            for (String id : bSec.getKeys(false)) {
                ConfigurationSection sec = bSec.getConfigurationSection(id);
                if (sec == null) continue;
                try {
                    BeastDefinition def = loadBeast(id.toLowerCase(Locale.ROOT), sec);
                    beasts.put(def.id(), def);
                } catch (Exception ex) {
                    plugin.getLogger().warning("beasts.yml — could not load beast '"
                            + id + "': " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + beasts.size() + " tailed beast(s), "
                + personalities.size() + " personality archetype(s).");

        loadInnerWorlds();
    }

    private BeastDefinition loadBeast(String id, ConfigurationSection sec) {
        String name = sec.getString("name", id);
        int tails = Math.max(1, sec.getInt("tails", 1));
        Personality pers = personalities.getOrDefault(
                sec.getString("personality", "neutral").toLowerCase(Locale.ROOT),
                Personality.NEUTRAL);
        Color color = parseColor(sec.getString("color", "#FF3B1F"));
        double beastRageMult = sec.getDouble("rage-multiplier", 1.0);

        ConfigurationSection curve = plugin.getConfig()
                .getConfigurationSection("stage-power");
        double refTails = curve != null ? curve.getDouble("reference-tails", 9) : 9;
        double exponent = curve != null ? curve.getDouble("exponent", 0.732) : 0.732;
        double entryExp = curve != null
                ? curve.getDouble("entry-difficulty-exponent", 0.5) : 0.5;
        ConfigurationSection perPower = curve != null
                ? curve.getConfigurationSection("effects-per-power") : null;

        double entryDivisor = Math.max(1.0,
                Math.pow(refTails / (double) tails, entryExp));
        entryDivisor = sec.getDouble("entry-difficulty", entryDivisor);

        ConfigurationSection stageOverrides = sec.getConfigurationSection("stages");
        Map<Integer, BeastStage> stages = new LinkedHashMap<>();
        for (int s = 1; s <= tails; s++) {
            ConfigurationSection ov = stageOverrides != null
                    ? stageOverrides.getConfigurationSection(String.valueOf(s)) : null;
            double power = s * Math.pow(refTails / (double) tails, exponent);
            if (ov != null) power = ov.getDouble("power", power);

            String stageName = ov != null
                    ? ov.getString("name", "Queue " + s) : "Queue " + s;
            double dmg   = pick(ov, "damage-multiplier",
                    1.0 + power * get(perPower, "damage-multiplier", 0.10));
            double spd   = pick(ov, "bonus-speed",
                    power * get(perPower, "bonus-speed-percent", 3.0));
            double res   = Math.min(80.0, pick(ov, "resistance",
                    power * get(perPower, "resistance-percent", 4.0)));
            double kb    = Math.min(1.0, pick(ov, "knockback-resistance",
                    power * get(perPower, "knockback-resistance", 0.06)));
            double scale = pick(ov, "scale",
                    1.0 + power * get(perPower, "extra-scale", 0.02));
            double hps   = pick(ov, "health-regen",
                    power * get(perPower, "health-regen-percent", 0.35));
            double ih    = pick(ov, "instant-heal",
                    power * get(perPower, "instant-heal-percent", 3.0));
            double cps   = pick(ov, "chakra-regen",
                    power * get(perPower, "chakra-regen-percent", 0.8));
            double ic    = pick(ov, "instant-chakra",
                    power * get(perPower, "instant-chakra-percent", 4.0));
            double aura  = pick(ov, "aura-range", auraRangeFor(s));
            List<String> fx = ov != null
                    ? ov.getStringList("effects") : Collections.emptyList();

            stages.put(s, new BeastStage(s, stageName, power, dmg, spd, res,
                    kb, scale, hps, ih, cps, ic, aura, fx));
        }

        return new BeastDefinition(id, name, tails, pers, color, beastRageMult,
                entryDivisor, sec.getStringList("whispers"),
                sec.getStringList("confront-lines"), stages);
    }

    /** Aura range for a stage: config list first, default curve as
     *  fallback, linear extrapolation past either list's end. */
    private double auraRangeFor(int stage) {
        List<Double> cfg = plugin.getConfig().getDoubleList("aura-range.by-stage");
        if (cfg != null && !cfg.isEmpty()) {
            if (stage <= cfg.size()) return cfg.get(stage - 1);
            double last = cfg.get(cfg.size() - 1);
            double prev = cfg.size() > 1 ? cfg.get(cfg.size() - 2) : last / 2;
            return last + (last - prev) * (stage - cfg.size());
        }
        if (stage <= DEFAULT_AURA.length) return DEFAULT_AURA[stage - 1];
        return DEFAULT_AURA[DEFAULT_AURA.length - 1]
                + 334.0 * (stage - DEFAULT_AURA.length);
    }

    private static double pick(ConfigurationSection ov, String key, double def) {
        return ov != null && ov.contains(key) ? ov.getDouble(key) : def;
    }

    private static double get(ConfigurationSection sec, String key, double def) {
        return sec != null ? sec.getDouble(key, def) : def;
    }

    private static Color parseColor(String hex) {
        try {
            String h = hex.replace("#", "").trim();
            return Color.fromRGB(Integer.parseInt(h, 16));
        } catch (Exception ex) {
            return Color.fromRGB(0xFF3B1F);
        }
    }

    /* ------------------------------------------------------ inner worlds */

    private File innerWorldsFile() {
        return new File(plugin.getDataFolder(), "data/innerworlds.yml");
    }

    private void loadInnerWorlds() {
        innerWorlds.clear();
        File f = innerWorldsFile();
        if (!f.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(f);
        for (String id : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(id);
            if (s == null) continue;
            World w = Bukkit.getWorld(s.getString("world", ""));
            if (w == null) {
                plugin.getLogger().warning("innerworlds.yml — world not loaded for '"
                        + id + "', the location will resolve on next reload.");
                continue;
            }
            innerWorlds.put(id.toLowerCase(Locale.ROOT), new Location(w,
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
        }
    }

    public void setInnerWorld(String beastId, Location loc) {
        innerWorlds.put(beastId.toLowerCase(Locale.ROOT), loc.clone());
        File f = innerWorldsFile();
        f.getParentFile().mkdirs();
        YamlConfiguration yml = f.exists()
                ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        String p = beastId.toLowerCase(Locale.ROOT);
        yml.set(p + ".world", loc.getWorld().getName());
        yml.set(p + ".x", loc.getX());
        yml.set(p + ".y", loc.getY());
        yml.set(p + ".z", loc.getZ());
        yml.set(p + ".yaw", loc.getYaw());
        yml.set(p + ".pitch", loc.getPitch());
        try {
            yml.save(f);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save innerworlds.yml: " + ex.getMessage());
        }
    }

    /** Inner World location for this beast, or {@code null} if unset. */
    public Location innerWorld(String beastId) {
        Location l = innerWorlds.get(beastId.toLowerCase(Locale.ROOT));
        return l != null ? l.clone() : null;
    }

    /* ---------------------------------------------------------- accessors */

    public BeastDefinition byId(String id) {
        return id == null ? null : beasts.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, BeastDefinition> all() {
        return Collections.unmodifiableMap(beasts);
    }
}
