package com.reborn.shinobicore.medic;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Registry for placed Medic Armoirs.
 *
 * <p>Each armoir is a placed iron-door block carrying our PDC marker
 * via {@link #armoirItem(ShinobiCore)} (handed out by
 * {@code /sc itemgive armoir}). When a player right-clicks the door, a 27-slot chest
 * inventory opens (default loadout in {@link #defaultLoadout}). The
 * encyclopedia book sits at the centre slot. Inventory contents are
 * <em>not</em> per-armoir-persisted — the auto-refill resets every
 * 15 minutes, so saving every change would be wasted I/O. Only the
 * placement (location + last-refill timestamp) is on disk.
 *
 * <p>The right-click handler in {@link MedicArmoirListener} cancels
 * the vanilla door behaviour and opens the inventory. The 15-minute
 * ticker is global — each armoir refills independently based on its
 * own {@link MedicArmoir#lastRefillMillis}.
 */
public final class MedicArmoirManager {

    public static final String PDC_KEY            = "medic_armoir";
    public static final long   REFILL_INTERVAL_MS = 15L * 60L * 1000L;
    /** 27-slot chest. */
    public static final int    INV_SIZE           = 27;
    /** Centre slot (row 1 col 4) — the encyclopedia. */
    public static final int    ENCYCLOPEDIA_SLOT  = 13;

    private final ShinobiCore plugin;
    private final File         file;

    /** id → record. We don't index by location here because writes
     *  happen on each refill / placement; per-block lookup goes
     *  through {@link #atBlock}. */
    private final Map<UUID, MedicArmoir> byId = new LinkedHashMap<>();

    /** Live open inventories so a refill can rewrite them in place
     *  even while a medic is browsing. */
    private final Map<UUID, Inventory> liveInventories = new HashMap<>();

    private BukkitTask ticker;

    public MedicArmoirManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "medic-armoirs.yml");
    }

    public NamespacedKey markerKey() {
        return new NamespacedKey(plugin, PDC_KEY);
    }

    /* --------------------------------------------------------- lifecycle */

    public void start() {
        load();
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick,
                20L * 30, 20L * 30); // every 30s, individual cooldowns gate
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        save();
    }

    /* ----------------------------------------------------------- queries */

    public MedicArmoir atBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        for (MedicArmoir a : byId.values()) {
            if (!loc.getWorld().getName().equals(a.worldName())) continue;
            if (a.x() == loc.getBlockX()
                    && a.y() == loc.getBlockY()
                    && a.z() == loc.getBlockZ()) return a;
        }
        return null;
    }

    /* --------------------------------------------------------- placement */

    /** Register a freshly-placed armoir at {@code loc}. Returns the
     *  new record (also persisted). */
    public MedicArmoir register(Location loc) {
        MedicArmoir a = new MedicArmoir(UUID.randomUUID());
        a.setLocation(loc);
        a.setLastRefillMillis(0L); // immediate first-fill on next open
        byId.put(a.id(), a);
        save();
        return a;
    }

    /** Drop an armoir from the registry — used on block break. */
    public void unregister(MedicArmoir a) {
        if (a == null) return;
        byId.remove(a.id());
        liveInventories.remove(a.id());
        save();
    }

    /* ------------------------------------------------------------- open */

    /** Open the armoir for {@code viewer}. If the 15-min cooldown has
     *  elapsed since the last refill, top the inventory back up to
     *  the default loadout BEFORE the player sees it. */
    public void open(Player viewer, MedicArmoir a) {
        long now = System.currentTimeMillis();
        Inventory inv = liveInventories.get(a.id());
        if (inv == null) {
            inv = buildFreshInventory();
            liveInventories.put(a.id(), inv);
            a.setLastRefillMillis(now);
            save();
        } else if (now - a.lastRefillMillis() >= REFILL_INTERVAL_MS) {
            // Refill in place — preserves the same Inventory instance
            // so other open viewers see the change.
            populateLoadout(inv);
            a.setLastRefillMillis(now);
            save();
        }
        viewer.openInventory(inv);
    }

    /* ----------------------------------------------------------- ticker */

    private void tick() {
        long now = System.currentTimeMillis();
        for (MedicArmoir a : byId.values()) {
            if (now - a.lastRefillMillis() < REFILL_INTERVAL_MS) continue;
            Inventory inv = liveInventories.get(a.id());
            if (inv == null) continue; // no open instance yet — refill on next open
            populateLoadout(inv);
            a.setLastRefillMillis(now);
        }
    }

    /* ----------------------------------------------------------- inventory */

    private Inventory buildFreshInventory() {
        Component title = Component.text("Armoire à Pharmacie",
                NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true);
        ArmoirHolder holder = new ArmoirHolder();
        Inventory inv = Bukkit.createInventory(holder, INV_SIZE, title);
        holder.bind(inv);
        populateLoadout(inv);
        return inv;
    }

    /** Populate a 27-slot armoir with the standard loadout: a couple
     *  of stacks of every medicine + the encyclopedia book in the
     *  centre. We place items in deterministic slots so the layout
     *  is predictable run-to-run. */
    private void populateLoadout(Inventory inv) {
        inv.clear();
        // Encyclopedia at centre.
        inv.setItem(ENCYCLOPEDIA_SLOT, Encyclopedia.build());
        // Medicines distributed around the centre. Order chosen so
        // common items (Bétadine / Compresse / Bande) sit on the row
        // edges where the eye lands first.
        int[] stocks = {0, 1, 2, 3, 5, 6, 7, 8,        // row 0
                        9, 10, 11, 12,    14, 15, 16, 17,  // row 1 (skip 13)
                        18, 19, 20, 21, 22, 23, 24, 25, 26}; // row 2
        Medicine[] order = {
                Medicine.ARNICA_GEL, Medicine.BIAFINE,
                Medicine.PLATRE,     Medicine.ANTALGIQUE,
                Medicine.AMOXICILLINE, Medicine.BETADINE,
                Medicine.COMPRESSE_STERILE, Medicine.BANDE
        };
        int slotIdx = 0;
        // Two stacks per medicine (8 medicines × 2 = 16 stacks),
        // enough for an active medic shift between refills.
        for (int rep = 0; rep < 2; rep++) {
            for (Medicine m : order) {
                if (slotIdx >= stocks.length) return;
                inv.setItem(stocks[slotIdx++],
                        MedicineItem.create(plugin, m, m.material().getMaxStackSize()));
            }
        }
    }

    /* --------------------------------------------------------- persistence */

    private void load() {
        byId.clear();
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("armoirs");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                MedicArmoir a = new MedicArmoir(UUID.fromString(key));
                String world = s.getString("world", "");
                if (Bukkit.getWorld(world) == null) continue;
                a.setLocation(new Location(Bukkit.getWorld(world),
                        s.getInt("x"), s.getInt("y"), s.getInt("z")));
                a.setLastRefillMillis(s.getLong("last-refill", 0L));
                byId.put(a.id(), a);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed armoir '" + key + "'.");
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (MedicArmoir a : byId.values()) {
            String key = "armoirs." + a.id();
            cfg.set(key + ".world",       a.worldName());
            cfg.set(key + ".x",           a.x());
            cfg.set(key + ".y",           a.y());
            cfg.set(key + ".z",           a.z());
            cfg.set(key + ".last-refill", a.lastRefillMillis());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to save medic-armoirs.yml", ex);
        }
    }

    /* --------------------------------------------------------- holder */

    /** Marker holder so {@link MedicArmoirListener#onClick} can route
     *  the click to lock the encyclopedia slot. */
    public static final class ArmoirHolder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
        public void bind(Inventory inv) { this.inv = inv; }
    }

    /** The fixed item-stack used for the iron door given to medics. */
    public static ItemStack armoirItem(ShinobiCore plugin) {
        ItemStack it = new ItemStack(org.bukkit.Material.IRON_DOOR);
        var meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Armoire à Pharmacie",
                    NamedTextColor.AQUA).decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(java.util.List.of(
                    Component.text("Pose-la et clique-droit pour",
                            NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text("ouvrir l'inventaire de soin.",
                            NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, PDC_KEY),
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            it.setItemMeta(meta);
        }
        return it;
    }

    public static boolean isArmoirItem(ShinobiCore plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer()
                .has(new NamespacedKey(plugin, PDC_KEY),
                        org.bukkit.persistence.PersistentDataType.BYTE);
    }
}
