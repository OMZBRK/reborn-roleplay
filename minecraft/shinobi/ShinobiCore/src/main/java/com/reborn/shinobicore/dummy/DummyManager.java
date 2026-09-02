package com.reborn.shinobicore.dummy;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.ko.injury.Injury;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Registry + lifecycle for {@link Dummy} training NPCs.
 *
 * <p>Each dummy is backed by a Villager entity tagged with the
 * {@link #DUMMY_KEY} PDC marker carrying the dummy's {@link Dummy#id}.
 * The Villager is configured with no AI, no aging, and an iron-bar
 * inventory (cosmetic) so it stays put and looks distinct from
 * vanilla traders. Damage events on tagged Villagers are intercepted
 * by {@link DummyListener}, which:
 * <ul>
 *   <li>Cancels the actual entity damage (so the Villager doesn't
 *       die),</li>
 *   <li>Subtracts the would-have-been damage from the dummy's
 *       virtual {@link Dummy#currentHp},</li>
 *   <li>Auto-tags an {@link Injury} on a random body part, mirroring
 *       the real KO listener,</li>
 *   <li>Sets {@link Dummy#ko} when virtual HP hits 0, freezing the
 *       Villager visually.</li>
 * </ul>
 *
 * <p>Persistence: {@code dummies.yml} carries name, id, entity uuid,
 * location, stats, injury list, KO flag.
 */
public final class DummyManager {

    /** PDC key stamped on a dummy Villager — value is the dummy id. */
    public static final String DUMMY_KEY = "dummy_id";

    /** The Bukkit max-health we pin every Villager to. We never let
     *  the entity actually take damage; this just keeps the heart
     *  bar happy if any UI surfaces it. */
    private static final double VILLAGER_MAX_HP = 20.0;

    private final ShinobiCore plugin;
    private final File         file;

    /** name (lower-case) → Dummy. */
    private final Map<String, Dummy> byName = new LinkedHashMap<>();
    /** entityId → Dummy, for fast lookup from damage events. */
    private final Map<UUID, Dummy>   byEntity = new LinkedHashMap<>();

    public DummyManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "dummies.yml");
    }

    public NamespacedKey markerKey() {
        return new NamespacedKey(plugin, DUMMY_KEY);
    }

    /* ---------------------------------------------------------- queries */

    public Dummy byName(String name) {
        if (name == null) return null;
        return byName.get(name.toLowerCase());
    }

    public Dummy byEntity(UUID entityId) {
        return entityId == null ? null : byEntity.get(entityId);
    }

    public Collection<Dummy> all() { return byName.values(); }

    /* ---------------------------------------------------------- mutators */

    /** Spawn a new dummy at {@code loc}. Fails if a dummy with this
     *  name already exists. */
    public Dummy create(String name, Location loc) {
        if (byName.containsKey(name.toLowerCase())) return null;
        Dummy d = new Dummy(name, UUID.randomUUID());
        d.setLocation(loc.getWorld().getName(), loc.getX(), loc.getY(), loc.getZ());
        Villager v = spawnVillagerFor(d, loc);
        if (v == null) return null;
        d.setEntityId(v.getUniqueId());
        byName.put(name.toLowerCase(), d);
        byEntity.put(v.getUniqueId(), d);
        save();
        return d;
    }

    /** Despawn the entity and drop the dummy from the registry. */
    public boolean delete(String name) {
        Dummy d = byName.remove(name.toLowerCase());
        if (d == null) return false;
        if (d.entityId() != null) {
            byEntity.remove(d.entityId());
            var ent = Bukkit.getEntity(d.entityId());
            if (ent != null) ent.remove();
        }
        save();
        return true;
    }

    /** Restore HP + chakra on a dummy and lift the KO flag. Injuries
     *  are intentionally NOT cleared — only {@code /soigner} can do
     *  that. {@code /dummy heal} is for testing combat resilience and
     *  the KO loop, and a tester usually wants to keep the injury
     *  history for the next /soigner run. */
    public boolean heal(String name) {
        Dummy d = byName.get(name.toLowerCase());
        if (d == null) return false;
        d.setCurrentHp(d.maxHp());
        d.setCurrentChakra(d.maxChakra());
        d.setKo(false);
        refreshVisual(d);
        save();
        return true;
    }

    /** Re-apply visual state (name + KO status) to the live Villager.
     *  Used after damage / heal / edit to keep the floating name in
     *  sync with the virtual stats. */
    public void refreshVisual(Dummy d) {
        if (d.entityId() == null) return;
        var ent = Bukkit.getEntity(d.entityId());
        if (!(ent instanceof Villager v)) return;
        Component name = Component.text(d.name(),
                d.ko() ? NamedTextColor.DARK_RED : NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true);
        if (d.ko()) {
            name = name.append(Component.text(" [KO]", NamedTextColor.GRAY));
        } else {
            name = name.append(Component.text(
                    "  " + ((int) d.currentHp()) + "/" + ((int) d.maxHp()) + " ❤",
                    NamedTextColor.GRAY));
        }
        v.customName(name);
        v.setCustomNameVisible(true);
        // Defensive re-pin in case something else (chunk reload,
        // older spawn settings, an admin tinkering) flipped these.
        // Keep awareness off so the villager doesn't pathfind to a
        // workstation, but DON'T setSilent — the hurt sound is the
        // main attack-feedback channel for testers.
        v.setAware(false);
        v.setSilent(false);
        AttributeInstance kb = v.getAttribute(
                Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(1.0);
    }

    /* ---------------------------------------------------------- spawn */

    private Villager spawnVillagerFor(Dummy d, Location loc) {
        World w = loc.getWorld();
        if (w == null) return null;
        Villager v = w.spawn(loc, Villager.class, ent -> {
            // setAware(false) disables pathfinding + trade behaviour
            // but KEEPS the hurt animation and damage events firing
            // — that's the difference from setAI(false), which makes
            // the villager look dead-to-the-world (no red flash, no
            // hurt sound). Awareness off + pin location each tick is
            // the right combo for a training dummy.
            ent.setAware(false);
            ent.setAgeLock(true);
            ent.setRemoveWhenFarAway(false);
            ent.setPersistent(true);
            ent.setCollidable(true);
            // Knockback resistance maxed so attacks don't push the
            // dummy across the room mid-test.
            AttributeInstance kb = ent.getAttribute(
                    Attribute.KNOCKBACK_RESISTANCE);
            if (kb != null) kb.setBaseValue(1.0);
            ent.setInvulnerable(false);  // damage events MUST fire; if true they never do
            // Pin Bukkit max-health to a stable value — virtual HP is
            // separate, tracked on the {@link Dummy} object.
            AttributeInstance maxHp =
                    ent.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null) {
                maxHp.setBaseValue(VILLAGER_MAX_HP);
                ent.setHealth(VILLAGER_MAX_HP);
            }
            ent.getPersistentDataContainer().set(markerKey(),
                    PersistentDataType.STRING, d.id().toString());
        });
        return v;
    }

    /* ---------------------------------------------------------- persistence */

    public void load() {
        byName.clear();
        byEntity.clear();
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("dummies");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                Dummy d = new Dummy(s.getString("name", key),
                        UUID.fromString(s.getString("id", UUID.randomUUID().toString())));
                d.setLocation(s.getString("world", ""),
                        s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
                String eid = s.getString("entity");
                if (eid != null) d.setEntityId(UUID.fromString(eid));
                d.setMaxHp(s.getDouble("max-hp", 100.0));
                d.setCurrentHp(s.getDouble("current-hp", d.maxHp()));
                d.setMaxChakra(s.getDouble("max-chakra", 100.0));
                d.setCurrentChakra(s.getDouble("current-chakra", d.maxChakra()));
                d.setKo(s.getBoolean("ko", false));
                ConfigurationSection inj = s.getConfigurationSection("injuries");
                if (inj != null) {
                    for (String iKey : inj.getKeys(false)) {
                        ConfigurationSection iSec = inj.getConfigurationSection(iKey);
                        if (iSec != null) {
                            Injury parsed = Injury.readFrom(iSec);
                            if (parsed != null) d.addInjury(parsed);
                        }
                    }
                }
                byName.put(d.name().toLowerCase(), d);
                if (d.entityId() != null) byEntity.put(d.entityId(), d);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed dummy '" + key + "'.");
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Dummy d : byName.values()) {
            String key = "dummies." + d.id();
            cfg.set(key + ".name", d.name());
            cfg.set(key + ".id", d.id().toString());
            cfg.set(key + ".entity", d.entityId() != null ? d.entityId().toString() : null);
            cfg.set(key + ".world", d.worldName());
            cfg.set(key + ".x", d.x());
            cfg.set(key + ".y", d.y());
            cfg.set(key + ".z", d.z());
            cfg.set(key + ".max-hp", d.maxHp());
            cfg.set(key + ".current-hp", d.currentHp());
            cfg.set(key + ".max-chakra", d.maxChakra());
            cfg.set(key + ".current-chakra", d.currentChakra());
            cfg.set(key + ".ko", d.ko() ? true : null);
            cfg.set(key + ".injuries", null);
            for (Injury inj : d.injuries()) {
                String iKey = key + ".injuries." + inj.id();
                cfg.createSection(iKey);
                inj.writeTo(cfg.getConfigurationSection(iKey));
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save dummies.yml", ex);
        }
    }
}
