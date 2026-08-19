package com.reborn.shinobicore.technique;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Loads {@code abilities.yml}, indexes by id and by category, and routes
 * castable jutsu to their JutsuItemType.
 *
 * <h2>Auto-derived JutsuMeta</h2>
 * When an entry has no {@code jutsu:} sub-block but its category falls
 * under a JutsuItemType's accepted prefixes, a default meta is derived:
 * LEFT_CLICK, {@value JutsuMeta#DEFAULT_CHAKRA_COST} chakra,
 * {@value JutsuMeta#DEFAULT_COOLDOWN_MILLIS} ms cooldown, generic
 * per-leaf effect, the item type's material as icon.
 *
 * <h2>F_PRESS rewrite</h2>
 * {@code method: F_PRESS} is deprecated (F is reserved for the picker).
 * It is rewritten to LEFT_CLICK at load time with a console warning.
 *
 * <p>Engine-owned since the catalog relocation: this is the
 * {@link com.reborn.shinobicore.api.TechniqueRegistry} implementation.
 * The DATA stays content-owned — the world pack (ShinobiAbilities for
 * Naruto) ships {@code abilities.yml} in its own data folder and calls
 * {@link #load(File)} at its boot step 1, so the live server's data
 * path is unchanged. Concrete names here keep their Naruto vocabulary
 * until the dedicated engine-rename migration (BOUNDARY.md §6).
 */
@com.reborn.shinobicore.api.Internal
public final class AbilityRegistry implements com.reborn.shinobicore.api.TechniqueRegistry {

    private final JavaPlugin plugin;
    private final Logger log;
    private final Map<String, Ability> byId = new LinkedHashMap<>();

    public AbilityRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /** Replace the registry contents from {@code file}. Returns the
     *  number of abilities loaded. */
    public int load(File file) {
        byId.clear();
        if (file == null || !file.exists()) {
            log.warning("abilities.yml introuvable — aucun jutsu chargé.");
            return 0;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        List<Map<?, ?>> raw = yml.getMapList("abilities");
        if (raw.isEmpty()) {
            // Also accept the section-form (abilities: { id: {...} }).
            ConfigurationSection sec = yml.getConfigurationSection("abilities");
            if (sec != null) {
                for (String key : sec.getKeys(false)) {
                    ConfigurationSection a = sec.getConfigurationSection(key);
                    if (a != null) parseOne(key, sectionToMap(a));
                }
                log.info(byId.size() + " techniques chargées depuis abilities.yml (forme section).");
                return byId.size();
            }
        }
        for (Map<?, ?> m : raw) {
            Object id = m.get("id");
            parseOne(id == null ? null : id.toString(), m);
        }
        log.info(byId.size() + " techniques chargées depuis abilities.yml.");
        return byId.size();
    }

    private static Map<?, ?> sectionToMap(ConfigurationSection s) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : s.getKeys(false)) {
            Object v = s.get(k);
            out.put(k, v instanceof ConfigurationSection cs ? sectionToMap(cs) : v);
        }
        return out;
    }

    private void parseOne(String id, Map<?, ?> m) {
        if (id == null || id.isBlank()) {
            log.warning("Entrée d'ability sans id — ignorée.");
            return;
        }
        id = id.trim().toLowerCase(Locale.ROOT);
        if (byId.containsKey(id)) {
            log.warning("Ability en double: " + id + " — la première gagne.");
            return;
        }
        String name = str(m, "name", id);
        String category = str(m, "category", "autres/divers").toLowerCase(Locale.ROOT);
        JutsuRank rank = JutsuRank.from(str(m, "rank", "E"));
        ExecutionType execution = ExecutionType.from(str(m, "execution", "JUTSU"));
        MinigameType minigame = MinigameType.from(str(m, "minigame", "NONE"));
        Difficulty difficulty = Difficulty.from(str(m, "difficulty", "MEDIUM"));

        List<Mudra> mudras = new ArrayList<>();
        Object rawMudras = m.get("mudras");
        if (rawMudras instanceof List<?> list) {
            for (Object o : list) {
                Mudra mu = Mudra.from(String.valueOf(o));
                if (mu == null) {
                    log.warning("Mudra inconnu '" + o + "' sur " + id + " — ignoré.");
                } else {
                    mudras.add(mu);
                }
            }
        }

        // Free-form incantation steps for non-mudra arts. Accepted keys:
        // steps / movements / mouvements / gestures (first match wins).
        List<String> steps = new ArrayList<>();
        for (String key : new String[]{"steps", "movements", "mouvements", "gestures"}) {
            Object raw = m.get(key);
            if (raw instanceof List<?> list) {
                for (Object o : list) {
                    String s = String.valueOf(o).trim();
                    if (!s.isEmpty()) steps.add(s);
                }
                break;
            }
        }
        if (!mudras.isEmpty() && !steps.isEmpty()) {
            log.warning("Ability " + id + ": mudras ET steps déclarés — "
                    + "les mudras priment, steps ignorés.");
        }

        String description = str(m, "description", "");

        JutsuMeta meta = null;
        Object rawJutsu = m.get("jutsu");
        if (rawJutsu instanceof Map<?, ?> j) {
            JutsuItemType itemType = JutsuItemType.from(str(j, "item-type", null));
            if (itemType == null) itemType = JutsuItemType.forCategory(category);
            if (itemType == null) {
                log.warning("Jutsu " + id + ": item-type illisible et catégorie '"
                        + category + "' non routable — bloc jutsu ignoré.");
            } else {
                Material icon = matchMaterial(str(j, "icon", null), itemType.material());
                ExecutionMethod method = ExecutionMethod.from(str(j, "method", "LEFT_CLICK"));
                if (method == ExecutionMethod.F_PRESS) {
                    log.warning("Jutsu " + id + ": method F_PRESS est déprécié (F = picker)"
                            + " — réécrit en LEFT_CLICK.");
                    method = ExecutionMethod.LEFT_CLICK;
                }
                double cost = num(j, "chakra-cost", JutsuMeta.DEFAULT_CHAKRA_COST);
                long cd = (long) num(j, "cooldown-millis", JutsuMeta.DEFAULT_COOLDOWN_MILLIS);

                // ----- effect routing: internal effect / MagicSpells / raw commands.
                List<String> commands = new ArrayList<>();
                Object rawCmds = j.get("commands");
                if (rawCmds instanceof List<?> list) {
                    for (Object o : list) {
                        String c = String.valueOf(o).trim();
                        if (!c.isEmpty()) commands.add(c);
                    }
                }
                String magicspell = str(j, "magicspell", null);
                if (magicspell != null && !magicspell.isBlank()) {
                    commands.add(magicSpellTemplate()
                            .replace("%spell%", magicspell.trim()));
                }
                boolean runAsPlayer = "PLAYER".equalsIgnoreCase(
                        str(j, "run-as", "CONSOLE"));

                // Optional readability field — validated, never required.
                String type = str(j, "type", null);
                if (type != null) {
                    String t = type.trim().toUpperCase(Locale.ROOT);
                    if (t.equals("MAGICSPELL") && magicspell == null) {
                        log.warning("Jutsu " + id + ": type MAGICSPELL sans clé "
                                + "'magicspell:' — rien à lancer.");
                    } else if (t.equals("COMMAND") && commands.isEmpty()) {
                        log.warning("Jutsu " + id + ": type COMMAND sans 'commands:'.");
                    }
                }

                // Explicit effect wins; otherwise commands stand alone;
                // with neither, fall back to the per-leaf generic.
                String effect = str(j, "effect", null);
                if (effect == null && commands.isEmpty()) {
                    effect = "generic_" + leaf(category);
                }
                meta = new JutsuMeta(itemType, icon, method, cost, cd,
                        effect, commands, runAsPlayer);
            }
        } else {
            // Auto-derive when the category is routable to a JutsuItem.
            JutsuItemType itemType = JutsuItemType.forCategory(category);
            if (itemType != null && execution == ExecutionType.JUTSU) {
                meta = new JutsuMeta(itemType, itemType.material(),
                        ExecutionMethod.LEFT_CLICK,
                        JutsuMeta.DEFAULT_CHAKRA_COST,
                        JutsuMeta.DEFAULT_COOLDOWN_MILLIS,
                        "generic_" + leaf(category), List.of(), false);
            }
        }

        byId.put(id, new Ability(id, name, category, rank, execution,
                minigame, difficulty, mudras, steps, description, meta));
    }

    /** Console command generated by the {@code magicspell:} shorthand —
     *  configurable so any MagicSpells alias setup fits. */
    private String magicSpellTemplate() {
        return plugin.getConfig().getString("jutsu.magicspells.command-template",
                "cast forcecast %player% %spell%");
    }

    private static String leaf(String category) {
        int i = category.lastIndexOf('/');
        return i < 0 ? category : category.substring(i + 1);
    }

    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static double num(Map<?, ?> m, String key, double def) {
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        if (v != null) {
            try { return Double.parseDouble(String.valueOf(v)); }
            catch (NumberFormatException ignore) { }
        }
        return def;
    }

    private Material matchMaterial(String name, Material def) {
        if (name == null || name.isBlank()) return def;
        Material mat = Material.matchMaterial(name.trim());
        if (mat == null) {
            log.warning("Icône inconnue '" + name + "' — fallback " + def + ".");
            return def;
        }
        return mat;
    }

    /* ----------------------------------------------------------- queries */

    public Ability byId(String id) {
        return id == null ? null : byId.get(id.trim().toLowerCase(Locale.ROOT));
    }

    /** Unmodifiable view, in file order. */
    public Map<String, Ability> all() { return Collections.unmodifiableMap(byId); }

    /** Castable jutsu whose category routes to {@code type}, file order. */
    public List<Ability> castableFor(JutsuItemType type) {
        List<Ability> out = new ArrayList<>();
        for (Ability a : byId.values()) {
            if (a.isCastable() && a.jutsu().itemType() == type) out.add(a);
        }
        return out;
    }

    /** All abilities whose category starts with {@code prefix}. */
    public List<Ability> byCategoryPrefix(String prefix) {
        String p = prefix == null ? "" : prefix.trim().toLowerCase(Locale.ROOT);
        List<Ability> out = new ArrayList<>();
        for (Ability a : byId.values()) {
            if (a.category().startsWith(p)) out.add(a);
        }
        return out;
    }
}
