package com.reborn.shinobicore.backpack;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Spawns + persists the entities that represent a backpack lying on
 * the ground.
 *
 * <h2>Two-entity setup</h2>
 * <ul>
 *   <li>An {@link ItemDisplay} renders the actual backpack item with
 *       a small downward translation + a slight tilt — that's the
 *       visible "bag on the floor" the player sees.</li>
 *   <li>An {@link Interaction} sits at the same location with a small
 *       hitbox so right-click and sneak+right-click events can fire.
 *       (ItemDisplay alone has no hitbox.)</li>
 * </ul>
 *
 * <p>Both entities carry the backpack id in their PDC under
 * {@link #backpackIdKey} so we can resolve clicks back to the
 * {@link Backpack} record without relying on world-coordinate lookups.
 *
 * <h2>Persistence</h2>
 * {@code placed-backpacks.yml} keys each entry by the backpack id with
 * world+x/y/z + the two entity UUIDs. On startup we re-index the
 * loaded entities so right-click handling works immediately. If the
 * world hasn't been loaded yet (lazy world load), the row is kept
 * pending and re-tried on the world load event.
 */
public class BackpackEntityManager {

    private static final String DISPLAY_TAG_KEY      = "backpack_display";
    private static final String INTERACTION_TAG_KEY  = "backpack_interaction";
    private static final String BACKPACK_ID_KEY      = "backpack_id";

    private final ShinobiCore plugin;
    private final File file;
    /** backpackId → placement record. */
    private final Map<UUID, PlacedBackpack> placed = new LinkedHashMap<>();

    public BackpackEntityManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "placed-backpacks.yml");
    }

    /* -------------------------------------------------- PDC namespace keys */

    private NamespacedKey displayKeyCache;
    private NamespacedKey interactionKeyCache;
    private NamespacedKey backpackIdKeyCache;

    public NamespacedKey displayTagKey() {
        if (displayKeyCache == null) displayKeyCache = new NamespacedKey(plugin, DISPLAY_TAG_KEY);
        return displayKeyCache;
    }
    public NamespacedKey interactionTagKey() {
        if (interactionKeyCache == null) interactionKeyCache = new NamespacedKey(plugin, INTERACTION_TAG_KEY);
        return interactionKeyCache;
    }
    public NamespacedKey backpackIdKey() {
        if (backpackIdKeyCache == null) backpackIdKeyCache = new NamespacedKey(plugin, BACKPACK_ID_KEY);
        return backpackIdKeyCache;
    }

    /* ---------------------------------------------------------- lifecycle */

    public void load() {
        placed.clear();
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("placed");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID id = UUID.fromString(key);
                BackpackSize size = BackpackSize.parse(s.getString("size", "SMALL"));
                if (size == null) size = BackpackSize.SMALL;
                String worldName = s.getString("world", "");
                World w = Bukkit.getWorld(worldName);
                if (w == null) continue;
                Location loc = new Location(w,
                        s.getDouble("x"), s.getDouble("y"), s.getDouble("z"));
                UUID dispId = UUID.fromString(s.getString("display"));
                UUID interId = UUID.fromString(s.getString("interaction"));
                placed.put(id, new PlacedBackpack(id, size, loc, dispId, interId));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed placed-backpack '" + key + "'.");
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (PlacedBackpack p : placed.values()) {
            String key = "placed." + p.backpackId();
            cfg.set(key + ".size", p.size().name());
            cfg.set(key + ".world", p.location().getWorld().getName());
            cfg.set(key + ".x", p.location().getX());
            cfg.set(key + ".y", p.location().getY());
            cfg.set(key + ".z", p.location().getZ());
            cfg.set(key + ".display",     p.displayEntityId().toString());
            cfg.set(key + ".interaction", p.interactionEntityId().toString());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save placed-backpacks.yml", ex);
        }
    }

    /* ------------------------------------------------------------ place */

    /** Spawn the visual + hitbox entities for {@code backpack} at
     *  {@code location} and register the placement. The backpack's
     *  physical {@link ItemStack} representation is passed as
     *  {@code visual} — that's what the ItemDisplay renders, so it
     *  carries the correct Material (Sac vs Sac Large) without us
     *  having to rebuild it. */
    public PlacedBackpack place(Backpack backpack, Location location, ItemStack visual) {
        // ItemDisplay — rendered visual.
        ItemDisplay disp = location.getWorld().spawn(
                location.clone().add(0.5, 0.05, 0.5),
                ItemDisplay.class,
                ent -> {
                    ent.setItemStack(visual);
                    ent.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
                    // Lay it slightly tilted forward so it reads as
                    // "bag dropped on the ground" rather than a
                    // floating cube.
                    Transformation tf = ent.getTransformation();
                    // Build the left rotation as a Quaternionf so both
                    // rotations passed to the Transformation constructor
                    // are the same JOML type (the (Vector3f,
                    // Quaternionf, Vector3f, Quaternionf) overload).
                    Quaternionf tilt = new Quaternionf().rotateAxis(
                            (float) Math.toRadians(35f), 1f, 0f, 0f);
                    tf = new Transformation(
                            new Vector3f(0f, -0.35f, 0f),
                            tilt,
                            new Vector3f(0.6f, 0.6f, 0.6f),
                            tf.getRightRotation());
                    ent.setTransformation(tf);
                    ent.getPersistentDataContainer().set(displayTagKey(),
                            PersistentDataType.BYTE, (byte) 1);
                    ent.getPersistentDataContainer().set(backpackIdKey(),
                            PersistentDataType.STRING, backpack.id().toString());
                });

        // Interaction — invisible click-hitbox. Sized to fit the
        // visual so right-click feels accurate from any angle.
        Interaction inter = location.getWorld().spawn(
                location.clone().add(0.5, 0.0, 0.5),
                Interaction.class,
                ent -> {
                    ent.setInteractionWidth(0.7f);
                    ent.setInteractionHeight(0.5f);
                    ent.setResponsive(true);
                    ent.getPersistentDataContainer().set(interactionTagKey(),
                            PersistentDataType.BYTE, (byte) 1);
                    ent.getPersistentDataContainer().set(backpackIdKey(),
                            PersistentDataType.STRING, backpack.id().toString());
                });

        PlacedBackpack record = new PlacedBackpack(
                backpack.id(), backpack.size(), location.clone(),
                disp.getUniqueId(), inter.getUniqueId());
        placed.put(backpack.id(), record);
        save();
        return record;
    }

    /* ------------------------------------------------------------ pickup */

    /** Remove the placement entities + record. Called after the
     *  player has successfully picked up the backpack item. Returns
     *  the previously-recorded placement, or {@code null} when no
     *  placement was registered for this id. */
    public PlacedBackpack remove(UUID backpackId) {
        PlacedBackpack p = placed.remove(backpackId);
        if (p == null) return null;
        Entity disp = Bukkit.getEntity(p.displayEntityId());
        if (disp != null) disp.remove();
        Entity inter = Bukkit.getEntity(p.interactionEntityId());
        if (inter != null) inter.remove();
        save();
        return p;
    }

    /* ------------------------------------------------------------ lookup */

    public PlacedBackpack get(UUID backpackId) { return placed.get(backpackId); }
    public int count()                          { return placed.size(); }
}
