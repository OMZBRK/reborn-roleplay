package com.reborn.shinobiabilities.techniques;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobiabilities.gui.AbilityText;
import com.reborn.shinobiabilities.gui.AbilityScreen;
import com.reborn.shinobiabilities.gui.GuiRouter;
import com.reborn.shinobicore.gui.framework.Screen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobiabilities.jutsu.JutsuItems;
import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.character.gui.GuiTitles;
import com.reborn.shinobicore.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Étagère d'Apprentissage — a placed CHISELED_BOOKSHELF holding rolled
 * Parchemins like a tiny chest. Two sizes (3 / 9 slots). Clicking a
 * stored parchemin starts the learning minigame; the scroll is consumed
 * only on success.
 *
 * <p>Block lifecycle + persistence live here; the chest UI itself is a
 * framework {@link Screen} (see {@link ShelfScreen}) so it shares the
 * global click plumbing.
 */
public final class LearningShelfManager implements Listener {

    public static final String PDC_SHELF_SIZE = "shelf_size";

    /** Live shelf state. */
    public static final class Shelf {
        final Location location;
        final int size;             // 3 or 9
        final String[] slots = new String[9];

        Shelf(Location location, int size) {
            this.location = location;
            this.size = size;
        }
    }

    private final JavaPlugin plugin;
    private final AbilityRegistry registry;
    private final GuiRouter router;
    private final ShelfScreen screen;
    private final File file;
    private final Map<String, Shelf> shelves = new HashMap<>();
    private TechniquesService techniques; // wired post-construction (cycle)

    public LearningShelfManager(JavaPlugin plugin, AbilityRegistry registry,
                                GuiRouter router) {
        this.plugin = plugin;
        this.registry = registry;
        this.router = router;
        this.screen = new ShelfScreen(router);
        this.file = new File(plugin.getDataFolder(), "shelves.yml");
    }

    public void wire(TechniquesService techniques) {
        this.techniques = techniques;
    }

    /* ------------------------------------------------------------- items */

    public static ItemStack shelfItem(boolean large) {
        ItemStack it = new ItemStack(Material.CHISELED_BOOKSHELF);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Texts.title(large
                ? "Grande Étagère d'Apprentissage"
                : "Étagère d'Apprentissage", NamedTextColor.GOLD));
        meta.lore(List.of(
                Texts.lore(large ? "9 emplacements de parchemins"
                        : "3 emplacements de parchemins"),
                Texts.lore("Pose-la, range tes parchemins,", NamedTextColor.YELLOW),
                Texts.lore("clique un parchemin pour apprendre.", NamedTextColor.YELLOW)));
        Keys.setString(meta, PDC_SHELF_SIZE, large ? "9" : "3");
        it.setItemMeta(meta);
        return it;
    }

    /* ------------------------------------------------------------ storage */

    private static String key(Location l) {
        return l.getWorld().getName() + ";" + l.getBlockX()
                + ";" + l.getBlockY() + ";" + l.getBlockZ();
    }

    public void load() {
        shelves.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String k : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(k);
            if (s == null) continue;
            String[] parts = k.split(";");
            if (parts.length != 4) continue;
            var world = Bukkit.getWorld(parts[0]);
            if (world == null) continue;
            Location loc;
            try {
                loc = new Location(world, Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
            } catch (NumberFormatException ex) { continue; }
            Shelf shelf = new Shelf(loc, Math.max(3, Math.min(9, s.getInt("size", 3))));
            List<String> slots = s.getStringList("slots");
            for (int i = 0; i < 9 && i < slots.size(); i++) {
                String v = slots.get(i);
                shelf.slots[i] = (v == null || v.isBlank() || v.equals("-")) ? null : v;
            }
            shelves.put(k, shelf);
        }
        plugin.getLogger().info(shelves.size() + " étagère(s) d'apprentissage chargée(s).");
    }

    public void saveSync() {
        YamlConfiguration yml = new YamlConfiguration();
        for (var e : shelves.entrySet()) {
            Shelf shelf = e.getValue();
            yml.set(e.getKey() + ".size", shelf.size);
            List<String> slots = new ArrayList<>(9);
            for (int i = 0; i < 9; i++) {
                slots.add(shelf.slots[i] == null ? "-" : shelf.slots[i]);
            }
            yml.set(e.getKey() + ".slots", slots);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.writeString(file.toPath(), yml.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Écriture shelves.yml impossible: " + ex.getMessage());
        }
    }

    /** Remove {@code abilityId} from the shelf slot — the learn
     *  succeeded. Returns false when the scroll is no longer there. */
    public boolean consume(Location shelfLoc, int slot, String abilityId) {
        Shelf shelf = shelves.get(key(shelfLoc));
        if (shelf == null || slot < 0 || slot >= 9) return false;
        if (shelf.slots[slot] == null || !shelf.slots[slot].equals(abilityId)) return false;
        shelf.slots[slot] = null;
        saveSync();
        return true;
    }

    /* -------------------------------------------------------- place/break */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        String sizeRaw = Keys.getString(event.getItemInHand(), PDC_SHELF_SIZE);
        if (sizeRaw == null) return;
        int size = sizeRaw.equals("9") ? 9 : 3;
        Location loc = event.getBlockPlaced().getLocation();
        shelves.put(key(loc), new Shelf(loc, size));
        saveSync();
        event.getPlayer().sendMessage(Component.text(
                "Étagère d'Apprentissage posée (" + size + " emplacements).",
                NamedTextColor.GREEN));
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Shelf shelf = shelves.remove(key(b.getLocation()));
        if (shelf == null) return;
        saveSync();
        event.setDropItems(false);
        Location at = b.getLocation().add(0.5, 0.5, 0.5);
        b.getWorld().dropItemNaturally(at, shelfItem(shelf.size == 9));
        for (int i = 0; i < 9; i++) {
            Ability a = registry.byId(shelf.slots[i]);
            if (a != null) b.getWorld().dropItemNaturally(at, ParcheminItems.create(a));
        }
        event.getPlayer().sendMessage(Component.text(
                "Étagère d'Apprentissage retirée.", NamedTextColor.GRAY));
    }

    /* ------------------------------------------------------------ open GUI */

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Block b = event.getClickedBlock();
        if (b == null || b.getType() != Material.CHISELED_BOOKSHELF) return;
        Shelf shelf = shelves.get(key(b.getLocation()));
        if (shelf == null) return;
        // Vanilla chiseled bookshelves swallow books on click — always ours.
        event.setCancelled(true);
        Player p = event.getPlayer();
        if (JutsuItems.isJutsuItem(p.getInventory().getItemInMainHand())) return;
        router.screens().open(p, screen, Map.of("shelf", shelf));
        p.playSound(p.getLocation(), Sound.BLOCK_CHISELED_BOOKSHELF_PICKUP, 0.8f, 1.0f);
    }

    private static boolean usable(Shelf shelf, int slot) {
        return shelf.size == 9 || (slot >= 3 && slot <= 5);
    }

    /* -------------------------------------------------------------- screen */

    /**
     * The shelf chest UI. The only screen that moves real items, so it
     * leans on {@link #onRawClick} for cursor deposits and bottom-
     * inventory shift-clicks; stored scrolls are tagged buttons.
     */
    private final class ShelfScreen extends AbilityScreen {

        private ShelfScreen(GuiRouter router) {
            super(router);
        }

        private Shelf shelf(View view) {
            return view.get("shelf");
        }

        @Override
        public Component title(Player viewer, View view) {
            Shelf shelf = shelf(view);
            return GuiTitles.framed(shelf != null && shelf.size == 9
                    ? "Grande Étagère" : "Étagère d'Apprentissage",
                    NamedTextColor.DARK_GREEN);
        }

        @Override
        public int rows(View view) { return 1; }

        @Override
        public void render(Player viewer, View view, Inventory inv) {
            Shelf shelf = shelf(view);
            if (shelf == null) return;
            for (int i = 0; i < 9; i++) {
                if (!usable(shelf, i)) {
                    inv.setItem(i, GuiIcons.border());
                    continue;
                }
                Ability a = registry.byId(shelf.slots[i]);
                if (a != null) {
                    inv.setItem(i, Ui.glint(Ui.coloured(Material.PAPER,
                            "Parchemin — " + a.name(), NamedTextColor.GOLD,
                            "scroll", String.valueOf(i),
                            AbilityText.loreOf(a, "",
                                    "&aClique : apprendre",
                                    "&eShift-clic : reprendre")), true));
                } else {
                    // Untagged on purpose — empty slots take raw deposits.
                    ItemStack empty = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
                    ItemMeta meta = empty.getItemMeta();
                    meta.displayName(Texts.lore("Emplacement vide", NamedTextColor.DARK_GRAY));
                    meta.lore(List.of(Texts.flavour("Dépose un parchemin ici")));
                    empty.setItemMeta(meta);
                    inv.setItem(i, empty);
                }
            }
        }

        @Override
        public void onAction(Player viewer, View view, String action,
                             String value, InventoryClickEvent event) {
            if (!action.equals("scroll")) return;
            Shelf shelf = shelf(view);
            if (shelf == null) return;
            int slot;
            try { slot = Integer.parseInt(value); }
            catch (NumberFormatException ex) { return; }
            String storedId = shelf.slots[slot];
            if (storedId == null) return;

            if (event.isShiftClick()) {
                Ability a = registry.byId(storedId);
                shelf.slots[slot] = null;
                saveSync();
                refresh(viewer, view);
                if (a != null) {
                    var leftover = viewer.getInventory().addItem(ParcheminItems.create(a));
                    for (ItemStack stuck : leftover.values()) {
                        viewer.getWorld().dropItemNaturally(viewer.getLocation(), stuck);
                    }
                }
                viewer.playSound(viewer.getLocation(),
                        Sound.BLOCK_CHISELED_BOOKSHELF_PICKUP, 0.9f, 0.9f);
                return;
            }

            if (techniques != null) {
                viewer.closeInventory();
                techniques.attemptLearn(viewer, shelf.location, slot, storedId);
            }
        }

        @Override
        public void onRawClick(Player viewer, View view, InventoryClickEvent event) {
            Shelf shelf = shelf(view);
            if (shelf == null) return;

            Inventory top = view.getInventory();
            boolean inTop = event.getClickedInventory() != null
                    && event.getClickedInventory().equals(top);

            if (inTop) {
                // Deposit from cursor onto an empty usable slot.
                int slot = event.getSlot();
                if (!usable(shelf, slot) || shelf.slots[slot] != null) return;
                ItemStack cursor = event.getCursor();
                if (cursor == null || !ParcheminItems.isParchemin(cursor)) return;
                String abilityId = ParcheminItems.abilityIdOf(cursor);
                if (registry.byId(abilityId) == null) return;
                shelf.slots[slot] = abilityId;
                cursor.setAmount(cursor.getAmount() - 1);
                saveSync();
                refresh(viewer, view);
                viewer.playSound(viewer.getLocation(),
                        Sound.BLOCK_CHISELED_BOOKSHELF_INSERT, 0.9f, 1.0f);
                return;
            }

            // Bottom inventory: shift-click deposits to the first empty slot.
            if (!event.isShiftClick()) return;
            ItemStack item = event.getCurrentItem();
            if (item == null || !ParcheminItems.isParchemin(item)) return;
            String abilityId = ParcheminItems.abilityIdOf(item);
            if (registry.byId(abilityId) == null) return;
            for (int i = 0; i < 9; i++) {
                if (!usable(shelf, i) || shelf.slots[i] != null) continue;
                shelf.slots[i] = abilityId;
                item.setAmount(item.getAmount() - 1);
                saveSync();
                refresh(viewer, view);
                viewer.playSound(viewer.getLocation(),
                        Sound.BLOCK_CHISELED_BOOKSHELF_INSERT, 0.9f, 1.0f);
                return;
            }
            viewer.sendActionBar(Component.text("Étagère pleine.", NamedTextColor.RED));
            GuiSounds.error(viewer);
        }

        @Override
        public void onBack(Player viewer, View view) {
            viewer.closeInventory();
        }
    }
}
