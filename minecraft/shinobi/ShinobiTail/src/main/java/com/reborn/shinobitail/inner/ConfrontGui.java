package com.reborn.shinobitail.inner;

import com.reborn.shinobitail.ShinobiTail;
import com.reborn.shinobitail.beast.BeastDefinition;
import com.reborn.shinobitail.beast.BeastStage;
import com.reborn.shinobitail.data.JinchurikiData;
import com.reborn.shinobitail.util.Chances;
import com.reborn.shinobitail.util.Fmt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The two-button Inner World choice: yield (REDSTONE_BLOCK, slot 11) or
 * resist (NETHER_STAR, slot 15), with a context paper in the middle.
 * Closing without choosing reopens the GUI until the decision timeout
 * picks the configured default.
 */
public final class ConfrontGui implements Listener {

    private static final int SLOT_YIELD = 11;
    private static final int SLOT_INFO = 13;
    private static final int SLOT_RESIST = 15;
    private static final int SLOT_UNION = 22;

    private final ShinobiTail plugin;

    public ConfrontGui(ShinobiTail plugin) {
        this.plugin = plugin;
    }

    /** Marker holder so click routing can't collide with other GUIs. */
    private static final class Holder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    public void open(Player player, InnerWorldSession session) {
        BeastDefinition beast = session.beast();
        JinchurikiData data = session.data();
        int stage = session.stageAtEntry();

        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27,
                Component.text("Monde Intérieur — " + beast.beastName(),
                        NamedTextColor.DARK_PURPLE));
        holder.inventory = inv;

        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE,
                Component.empty(), List.of());
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // --- yield -----------------------------------------------------
        boolean atCap = stage >= beast.tails();
        BeastStage next = beast.stage(Math.min(beast.tails(), stage + 1));
        List<Component> yieldLore = new ArrayList<>();
        yieldLore.add(line("Laisser le démon prendre davantage", NamedTextColor.GRAY));
        yieldLore.add(line("de contrôle sur ton corps.", NamedTextColor.GRAY));
        yieldLore.add(Component.empty());
        yieldLore.add(atCap
                ? line("Le démon est déjà à son apogée — la", NamedTextColor.DARK_RED)
                : line("Étape suivante : " + (stage + 1) + "/" + beast.tails()
                        + " — " + next.displayName(), NamedTextColor.RED));
        if (atCap) yieldLore.add(line("rage repartira de plus belle.", NamedTextColor.DARK_RED));
        boolean nextIsFinal = !atCap && (stage + 1) >= beast.tails()
                && plugin.getConfig().getBoolean("final-stage.point-of-no-return", true);
        if (nextIsFinal) {
            yieldLore.add(Component.empty());
            yieldLore.add(line("⚠ POINT DE NON-RETOUR : libération totale.", NamedTextColor.DARK_RED));
            yieldLore.add(line("L'hôte sera perdu à jamais.", NamedTextColor.DARK_RED));
        }
        inv.setItem(SLOT_YIELD, item(Material.REDSTONE_BLOCK,
                Component.text("CÉDER", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                yieldLore));

        // --- info ------------------------------------------------------
        List<Component> info = new ArrayList<>();
        info.add(line("Étape actuelle : " + stage + "/" + beast.tails(),
                NamedTextColor.GOLD));
        info.add(line("Rage : " + Fmt.pct(data.rage()), NamedTextColor.RED));
        info.add(line("Maîtrise de l'étape : " + Fmt.pct(data.mastery(stage)),
                NamedTextColor.AQUA));
        inv.setItem(SLOT_INFO, item(Material.PAPER,
                Component.text(beast.beastName(), NamedTextColor.LIGHT_PURPLE), info));

        // --- resist ----------------------------------------------------
        List<Component> resistLore = new ArrayList<>();
        resistLore.add(line("Affronter sa volonté et tenter de", NamedTextColor.GRAY));
        resistLore.add(line("mettre fin à la transformation.", NamedTextColor.GRAY));
        if (plugin.getConfig().getBoolean("display.show-resist-chance", true)) {
            double chance = Chances.resistChance(
                    plugin.getConfig().getConfigurationSection("inner-world.resist"),
                    beast, data, stage);
            resistLore.add(Component.empty());
            resistLore.add(line("Chance estimée : " + Fmt.pct(chance),
                    NamedTextColor.AQUA));
        }
        resistLore.add(Component.empty());
        resistLore.add(line("En cas d'échec, le démon gagne", NamedTextColor.DARK_GRAY));
        resistLore.add(line("l'étape suivante…", NamedTextColor.DARK_GRAY));
        inv.setItem(SLOT_RESIST, item(Material.NETHER_STAR,
                Component.text("RÉSISTER", NamedTextColor.AQUA, TextDecoration.BOLD),
                resistLore));

        // --- union: break the seal (perfect trust + cooperation) -------
        var cfg = plugin.getConfig();
        boolean unionReady = cfg.getBoolean("union.enabled", true)
                && !data.sealRemoved()
                && data.trust() >= cfg.getDouble("union.require-trust", 100)
                && data.cooperation() >= cfg.getDouble("union.require-cooperation", 100);
        if (unionReady) {
            List<Component> unionLore = new ArrayList<>();
            unionLore.add(line("Confiance et Coopération parfaites.",
                    NamedTextColor.YELLOW));
            unionLore.add(line("Brise le sceau : vous ne ferez plus qu'un.",
                    NamedTextColor.GRAY));
            unionLore.add(Component.empty());
            unionLore.add(line("Débloque le MODE UNION : pleine puissance,",
                    NamedTextColor.GOLD));
            unionLore.add(line("zéro rage, transformable à volonté",
                    NamedTextColor.GOLD));
            unionLore.add(line("(/tail union).", NamedTextColor.GOLD));
            inv.setItem(SLOT_UNION, item(Material.BEACON,
                    Component.text("BRISER LE SCEAU — UNION",
                            NamedTextColor.GOLD, TextDecoration.BOLD),
                    unionLore));
        }

        player.openInventory(inv);
    }

    /* ------------------------------------------------------------- events */

    @EventHandler
    public void onClick(InventoryClickEvent ev) {
        if (!(ev.getInventory().getHolder() instanceof Holder)) return;
        ev.setCancelled(true);
        if (!(ev.getWhoClicked() instanceof Player player)) return;
        InnerWorldSession session = plugin.innerWorld().session(player.getUniqueId());
        if (session == null || session.phase() != InnerWorldSession.Phase.CHOOSING) return;

        switch (ev.getRawSlot()) {
            case SLOT_YIELD -> plugin.innerWorld().resolve(player, true);
            case SLOT_RESIST -> plugin.innerWorld().resolve(player, false);
            case SLOT_UNION -> plugin.innerWorld().resolveUnion(player);
            default -> { }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent ev) {
        if (ev.getInventory().getHolder() instanceof Holder) ev.setCancelled(true);
    }

    /** No escape: closing without choosing reopens until the timeout. */
    @EventHandler
    public void onClose(InventoryCloseEvent ev) {
        if (!(ev.getInventory().getHolder() instanceof Holder)) return;
        if (!(ev.getPlayer() instanceof Player player)) return;
        InnerWorldSession session = plugin.innerWorld().session(player.getUniqueId());
        if (session == null || session.phase() != InnerWorldSession.Phase.CHOOSING) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            InnerWorldSession s = plugin.innerWorld().session(player.getUniqueId());
            if (player.isOnline() && s != null
                    && s.phase() == InnerWorldSession.Phase.CHOOSING) {
                open(player, s);
            }
        }, 10L);
    }

    /* ------------------------------------------------------------- helpers */

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (!lore.isEmpty()) meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}
