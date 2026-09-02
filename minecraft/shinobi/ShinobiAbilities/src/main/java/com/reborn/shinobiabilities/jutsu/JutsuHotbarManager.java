package com.reborn.shinobiabilities.jutsu;

import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The hotbar-swap jutsu picker.
 *
 * <p>Open: snapshot the player's REAL hotbar (slots 0-8), replace with
 * slot 0 = JutsuItem icon, slots 1-5 = bound jutsu icons, slots 6-8 =
 * filler panes, and select slot 0. Close: restore the snapshot and the
 * previously-held slot. The snapshot lives only in memory — every
 * force-close path (quit, KO, character switch, death scrub) MUST go
 * through {@link #close} so no player ever keeps picker icons.
 */
public final class JutsuHotbarManager {

    public static final int HOTBAR_SIZE = 9;

    /** Open-picker state for one player. */
    public static final class Session {
        private final JutsuItemType type;
        private final ItemStack[] snapshot;
        private final int previousHeldSlot;
        private final UUID characterId;

        private Session(JutsuItemType type, ItemStack[] snapshot,
                        int previousHeldSlot, UUID characterId) {
            this.type = type;
            this.snapshot = snapshot;
            this.previousHeldSlot = previousHeldSlot;
            this.characterId = characterId;
        }

        public JutsuItemType type() { return type; }
        public UUID characterId() { return characterId; }
        /** Defensive copy of the saved hotbar (death-scrub needs it). */
        public ItemStack[] snapshotCopy() {
            ItemStack[] out = new ItemStack[HOTBAR_SIZE];
            for (int i = 0; i < HOTBAR_SIZE; i++) {
                out[i] = snapshot[i] == null ? null : snapshot[i].clone();
            }
            return out;
        }
    }

    private final JavaPlugin plugin;
    private final AbilityRegistry registry;
    private final JutsuBindingStore bindings;
    private final IncantationManager incantation;
    private final Map<UUID, Session> open = new HashMap<>();

    public JutsuHotbarManager(JavaPlugin plugin, AbilityRegistry registry,
                              JutsuBindingStore bindings,
                              IncantationManager incantation) {
        this.plugin = plugin;
        this.registry = registry;
        this.bindings = bindings;
        this.incantation = incantation;
    }

    public boolean isOpen(Player p) {
        return p != null && open.containsKey(p.getUniqueId());
    }

    public Session session(Player p) {
        return p == null ? null : open.get(p.getUniqueId());
    }

    /** Selected binding index 0-4 when the held slot is 1-5, else -1. */
    public int selectedBindingIndex(Player p) {
        Session s = session(p);
        if (s == null) return -1;
        int held = p.getInventory().getHeldItemSlot();
        return (held >= 1 && held <= JutsuBindingStore.SLOTS) ? held - 1 : -1;
    }

    /**
     * Open the picker for {@code type}. The caller has already resolved
     * the active character (no character → no picker).
     */
    public void open(Player p, JutsuItemType type, UUID characterId) {
        if (isOpen(p)) return;
        PlayerInventory inv = p.getInventory();

        ItemStack[] snapshot = new ItemStack[HOTBAR_SIZE];
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            ItemStack s = inv.getItem(i);
            snapshot[i] = s == null ? null : s.clone();
        }
        Session session = new Session(type, snapshot, inv.getHeldItemSlot(), characterId);
        open.put(p.getUniqueId(), session);

        // Slot 0 — the channel icon (a fresh JutsuItem clone, marked as
        // picker icon so it can't be mistaken for a real one on scrub).
        ItemStack channel = JutsuItems.create(type);
        var meta = channel.getItemMeta();
        com.reborn.shinobiabilities.util.Keys.setString(meta, JutsuItems.PDC_PICKER_ICON, "");
        channel.setItemMeta(meta);
        inv.setItem(0, channel);

        // Slots 1-5 — bound jutsu icons.
        String[] bound = bindings.get(characterId, type);
        for (int i = 0; i < JutsuBindingStore.SLOTS; i++) {
            Ability a = registry.byId(bound[i]);
            if (a != null && a.isCastable() && a.jutsu().itemType() == type) {
                inv.setItem(i + 1, JutsuItems.pickerIcon(a, i + 1));
            } else {
                inv.setItem(i + 1, JutsuItems.emptySlotIcon(i + 1));
            }
        }
        // Slots 6-8 — filler.
        for (int i = 6; i < HOTBAR_SIZE; i++) inv.setItem(i, JutsuItems.fillerIcon());

        inv.setHeldItemSlot(0);
        p.playSound(p.getLocation(), Sound.UI_TOAST_IN, 0.8f, 1.3f);
        p.sendActionBar(Component.text(type.displayName() + " — choisis un sort (1-5), F pour fermer",
                NamedTextColor.AQUA));
    }

    /**
     * Close the picker and restore the real hotbar. {@code force} skips
     * the feedback sound (quit / KO / switch paths).
     */
    public void close(Player p, boolean force) {
        if (p == null) return;
        Session s = open.remove(p.getUniqueId());
        if (s == null) return;
        incantation.cancel(p);

        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < HOTBAR_SIZE; i++) inv.setItem(i, s.snapshot[i]);
        int held = s.previousHeldSlot;
        if (held >= 0 && held < HOTBAR_SIZE) inv.setHeldItemSlot(held);

        if (!force && p.isOnline()) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_OUT, 0.8f, 1.1f);
        }
    }

    /** Drop the session WITHOUT touching the inventory — used by the
     *  death scrub, which rewrites the drop list instead. */
    public Session removeSessionRaw(Player p) {
        if (p == null) return null;
        Session s = open.remove(p.getUniqueId());
        if (s != null) incantation.cancel(p);
        return s;
    }

    /** Force-close every open picker (plugin disable). */
    public void closeAll() {
        for (UUID id : open.keySet().toArray(new UUID[0])) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                close(p, true);
            } else {
                open.remove(id);
            }
        }
    }
}
