package com.reborn.shinobicore.vanish;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiBuilder;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiTitles;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Multi-select picker of the characters currently connected — used to choose
 * the exception set for {@link VanishManager.Mode#HIDE_EXCEPT} ("nobody sees
 * me except these") or {@link VanishManager.Mode#SHOW_EXCEPT} ("everyone sees
 * me except these"). Clicking a head toggles it; the selection persists on
 * the holder across re-renders. "Confirmer" applies the vanish.
 */
public final class VanishPickerGui implements GuiBuilder.HolderInventoryBound {

    private final ShinobiCore plugin;
    private final UUID target;
    private final VanishManager.Mode mode;
    private final Set<UUID> selected = new HashSet<>();

    private Inventory inventory;
    private final Map<Integer, UUID> slotToPlayer = new HashMap<>();
    private int confirmSlot = -1;
    private int closeSlot = -1;

    public VanishPickerGui(ShinobiCore plugin, UUID target, VanishManager.Mode mode) {
        this.plugin = plugin;
        this.target = target;
        this.mode = mode;
    }

    @Override public Inventory getInventory() { return inventory; }
    @Override public void bindInventory(Inventory inv) { this.inventory = inv; }

    public UUID target() { return target; }
    public VanishManager.Mode mode() { return mode; }
    public Set<UUID> selected() { return selected; }
    public UUID playerAt(int slot) { return slotToPlayer.get(slot); }
    public boolean isConfirm(int s) { return s == confirmSlot; }
    public boolean isClose(int s) { return s == closeSlot; }

    public void toggle(UUID playerId) {
        if (playerId == null) return;
        if (!selected.add(playerId)) selected.remove(playerId);
    }

    public void open(Player viewer) {
        List<Player> connected = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getUniqueId().equals(target)) continue;
            if (plugin.characters().getActive(p.getUniqueId()) == null) continue;
            connected.add(p);
        }
        connected.sort((a, b) -> nameOf(a).compareToIgnoreCase(nameOf(b)));

        int gridRows = Math.min(5, Math.max(1, (connected.size() + 8) / 9));
        int rows = gridRows + 1;
        slotToPlayer.clear();

        String head = (mode == VanishManager.Mode.HIDE_EXCEPT)
                ? "Qui peut me voir ?" : "Qui ne me voit pas ?";
        GuiBuilder b = GuiBuilder.of(this, rows).title(GuiTitles.framed(head));

        int shown = Math.min(connected.size(), gridRows * 9);
        for (int i = 0; i < shown; i++) {
            Player p = connected.get(i);
            boolean sel = selected.contains(p.getUniqueId());
            b.item(i, GuiIcons.head(p, nameOf(p),
                    "&7" + clanOf(p),
                    sel ? "&a✔ Sélectionné" : "&7Cliquer pour sélectionner"));
            slotToPlayer.put(i, p.getUniqueId());
        }

        int base = (rows - 1) * 9;
        confirmSlot = base + 2;
        closeSlot = base + 6;
        b.item(confirmSlot, GuiIcons.primary(Material.LIME_DYE,
                "Confirmer (" + selected.size() + ")",
                "&7Applique le vanish avec",
                "&7cette sélection."));
        b.item(closeSlot, GuiIcons.backButton());
        b.fillEmpty();
        b.open(viewer);
    }

    private String nameOf(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        return c != null ? c.name() : p.getName();
    }

    private String clanOf(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        return c != null ? c.clan() : "—";
    }
}
