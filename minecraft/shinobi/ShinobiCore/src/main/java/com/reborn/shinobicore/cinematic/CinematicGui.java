package com.reborn.shinobicore.cinematic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.gui.GuiBuilder;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiTitles;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Management GUI for one cinematic: the ordered anchor list plus the
 * Add / Test / Close controls. Each anchor is a single item — left-click
 * edits, shift-left deletes, right-click / shift-right reorder. The holder
 * carries the cinematic name + a slot→anchor-index map so
 * {@link CinematicListener} resolves clicks without slot guesswork.
 */
public final class CinematicGui implements GuiBuilder.HolderInventoryBound {

    private final ShinobiCore plugin;
    private final String cinematicName;

    private Inventory inventory;
    private final Map<Integer, Integer> slotToAnchor = new HashMap<>();
    private int addSlot = -1;
    private int testSlot = -1;
    private int closeSlot = -1;

    public CinematicGui(ShinobiCore plugin, String cinematicName) {
        this.plugin = plugin;
        this.cinematicName = cinematicName;
    }

    @Override public Inventory getInventory() { return inventory; }
    @Override public void bindInventory(Inventory inv) { this.inventory = inv; }

    public String cinematicName() { return cinematicName; }
    public Integer anchorAt(int slot) { return slotToAnchor.get(slot); }
    public boolean isAdd(int slot) { return slot == addSlot; }
    public boolean isTest(int slot) { return slot == testSlot; }
    public boolean isClose(int slot) { return slot == closeSlot; }

    /* ---------------------------------------------------------------- open */

    public void open(Player viewer) {
        Cinematic cine = plugin.cinematics().getOrCreate(cinematicName);
        int count = cine.size();
        int anchorRows = Math.min(5, Math.max(1, (count + 8) / 9));
        int rows = anchorRows + 1;

        slotToAnchor.clear();
        GuiBuilder b = GuiBuilder.of(this, rows)
                .title(GuiTitles.framed("Cinématique : " + cinematicName));

        int shown = Math.min(count, anchorRows * 9);
        for (int i = 0; i < shown; i++) {
            b.item(i, anchorIcon(cine.anchor(i), i));
            slotToAnchor.put(i, i);
        }

        int base = (rows - 1) * 9;
        addSlot = base + 2;
        testSlot = base + 4;
        closeSlot = base + 6;
        b.item(addSlot, GuiIcons.primary(Material.EMERALD, "Ajouter un plan",
                "&7Ajoute un nouveau plan caméra",
                "&7et ouvre l'éditeur."));
        b.item(testSlot, GuiIcons.accent(Material.ENDER_EYE, "Tester la cinématique",
                count == 0 ? "&8Aucun plan à jouer." : "&7Joue toute la séquence sur toi."));
        b.item(closeSlot, GuiIcons.closeButton());
        b.fillEmpty();
        b.open(viewer);
    }

    private ItemStack anchorIcon(CinematicAnchor a, int index) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Titre : " + (a.title() != null ? "&f" + a.title() : "&8(aucun)"));
        if (a.text1() != null) lore.add("&8• &7" + a.text1());
        if (a.text2() != null) lore.add("&8• &7" + a.text2());
        if (a.text3() != null) lore.add("&8• &7" + a.text3());
        lore.add("&7Durée : &f" + a.durationSeconds() + "s");
        lore.add(a.hasCamera() ? "&aCaméra définie" : "&cCaméra non définie");
        lore.add("");
        lore.add("&eClic gauche : éditer");
        lore.add("&eClic droit : déplacer ↓");
        lore.add("&eMaj + gauche : supprimer");
        lore.add("&eMaj + droit : déplacer ↑");
        return GuiIcons.info(Material.PAPER, "Plan " + (index + 1),
                lore.toArray(new String[0]));
    }
}
