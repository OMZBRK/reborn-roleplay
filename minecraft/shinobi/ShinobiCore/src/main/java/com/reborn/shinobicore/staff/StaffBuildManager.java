package com.reborn.shinobicore.staff;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.gui.GuiSounds;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creative build-mode toggle for staff builders ({@code /staff build}).
 *
 * <h2>Why it needs care</h2>
 * ShinobiCore captures each character's ~41-slot inventory on switch,
 * auto-save, quit and KO ({@code ShinobiCharacter#captureInventoryFrom}).
 * If a builder dropped into creative and one of those captures fired,
 * the creative junk would overwrite the character's real RP inventory.
 *
 * <h2>Guards</h2>
 * <ol>
 *   <li>On enable we snapshot the player's real survival inventory and
 *       flip to CREATIVE; the uuid joins {@link #building}.</li>
 *   <li>The <b>auto-save</b> capture site early-returns for building
 *       players (they never get snapshotted mid-build) — the only
 *       capture site not preceded by a force-exit.</li>
 *   <li><b>Quit / KO / character-switch</b> each force-exit build
 *       <em>before</em> the capture runs (this listener sits at
 *       {@link EventPriority#LOWEST}), restoring the real inventory so
 *       the capture that follows records the survival inventory, not
 *       creative junk.</li>
 *   <li>On disable we flip back to SURVIVAL, clear, and restore the
 *       snapshot.</li>
 * </ol>
 */
public final class StaffBuildManager implements Listener {

    private final ShinobiCore plugin;
    private final Set<UUID> building = ConcurrentHashMap.newKeySet();
    private final Map<UUID, ItemStack[]> snapshots = new HashMap<>();

    public StaffBuildManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public boolean isBuilding(UUID id) {
        return id != null && building.contains(id);
    }

    public void toggle(Player p) {
        if (isBuilding(p.getUniqueId())) disable(p, true);
        else enable(p);
    }

    public void enable(Player p) {
        UUID id = p.getUniqueId();
        if (isBuilding(id)) return;
        if (plugin.ko() != null && plugin.ko().isKo(id)) {
            p.sendMessage(Component.text(
                    "Impossible d'entrer en construction en etat KO.", NamedTextColor.RED));
            GuiSounds.error(p);
            return;
        }
        snapshots.put(id, cloneContents(p.getInventory().getContents()));
        building.add(id);
        p.setGameMode(GameMode.CREATIVE);
        p.sendMessage(Component.text(
                "Mode construction ACTIVE — creatif. Ton inventaire RP est sauvegarde.",
                NamedTextColor.GREEN));
        p.sendMessage(Component.text(
                "Relance /staff build pour revenir a ton personnage.", NamedTextColor.GRAY));
        GuiSounds.accept(p);
    }

    public void disable(Player p, boolean notify) {
        if (restore(p) && notify) {
            p.sendMessage(Component.text(
                    "Mode construction desactive — inventaire RP restaure.",
                    NamedTextColor.YELLOW));
            GuiSounds.navigate(p);
        }
    }

    /** Flip back to survival and restore the snapshot. Returns true when
     *  the player was actually building. Shared by the toggle and every
     *  force-exit path. */
    private boolean restore(Player p) {
        UUID id = p.getUniqueId();
        if (!building.remove(id)) return false;
        p.setGameMode(GameMode.SURVIVAL);
        ItemStack[] snap = snapshots.remove(id);
        p.getInventory().clear();
        if (snap != null) p.getInventory().setContents(snap);
        return true;
    }

    /* -------------------------------------------------- force-exit hooks */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        // Runs before CharacterLifecycleListener (NORMAL) captures the
        // inventory: restore the real survival inventory first.
        restore(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onKo(com.reborn.shinobicore.event.KoEnterEvent e) {
        if (restore(e.player())) {
            e.player().sendMessage(Component.text(
                    "Sortie forcee du mode construction (KO).", NamedTextColor.RED));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwitch(com.reborn.shinobicore.api.event.CharacterSwitchEvent e) {
        // Fired at the very start of setActive, before teardownPrevious
        // captures — restoring here means the capture sees real items.
        restore(e.player());
    }

    /** Restore every builder on shutdown (before the roster flush). */
    public void restoreAll() {
        for (UUID id : Set.copyOf(building)) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) restore(p);
            else { building.remove(id); snapshots.remove(id); }
        }
    }

    private static ItemStack[] cloneContents(ItemStack[] src) {
        ItemStack[] out = new ItemStack[src.length];
        for (int i = 0; i < src.length; i++) {
            out[i] = src[i] == null ? null : src[i].clone();
        }
        return out;
    }
}
