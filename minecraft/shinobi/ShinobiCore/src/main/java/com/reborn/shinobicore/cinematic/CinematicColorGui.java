package com.reborn.shinobicore.cinematic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.gui.GuiBuilder;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiTitles;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

/**
 * Colour sub-GUI for the anchor editor. A row of four target buttons
 * (title / text 1 / text 2 / text 3) selects which line is being coloured;
 * a 16-swatch palette applies a {@link NamedTextColor}. Carries the editing
 * player's id so {@link CinematicListener} resolves the live
 * {@link CinematicEditorSession}.
 */
public final class CinematicColorGui implements GuiBuilder.HolderInventoryBound {

    /** The 16 named colours, in palette order. */
    public static final NamedTextColor[] PALETTE = {
            NamedTextColor.BLACK, NamedTextColor.DARK_BLUE, NamedTextColor.DARK_GREEN,
            NamedTextColor.DARK_AQUA, NamedTextColor.DARK_RED, NamedTextColor.DARK_PURPLE,
            NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.DARK_GRAY,
            NamedTextColor.BLUE, NamedTextColor.GREEN, NamedTextColor.AQUA,
            NamedTextColor.RED, NamedTextColor.LIGHT_PURPLE, NamedTextColor.YELLOW,
            NamedTextColor.WHITE
    };

    private static final Material[] SWATCH = {
            Material.BLACK_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL,
            Material.CYAN_WOOL, Material.RED_WOOL, Material.PURPLE_WOOL,
            Material.ORANGE_WOOL, Material.LIGHT_GRAY_WOOL, Material.GRAY_WOOL,
            Material.LIGHT_BLUE_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL,
            Material.RED_WOOL, Material.MAGENTA_WOOL, Material.YELLOW_WOOL,
            Material.WHITE_WOOL
    };

    private static final String[] TARGET_LABELS = { "Titre", "Texte 1", "Texte 2", "Texte 3" };
    private static final int[] TARGET_SLOTS = { 1, 3, 5, 7 };
    private static final int PALETTE_START = 9;     // slots 9..24
    private static final int BACK_SLOT = 31;

    private final ShinobiCore plugin;
    private final UUID playerId;
    private Inventory inventory;

    public CinematicColorGui(ShinobiCore plugin, UUID playerId) {
        this.plugin = plugin;
        this.playerId = playerId;
    }

    @Override public Inventory getInventory() { return inventory; }
    @Override public void bindInventory(Inventory inv) { this.inventory = inv; }

    public UUID playerId() { return playerId; }

    /** Target index (0..3) at this slot, or -1. */
    public int targetAt(int slot) {
        for (int i = 0; i < TARGET_SLOTS.length; i++) if (TARGET_SLOTS[i] == slot) return i;
        return -1;
    }

    /** Palette colour index (0..15) at this slot, or -1. */
    public int colorAt(int slot) {
        int i = slot - PALETTE_START;
        return (i >= 0 && i < PALETTE.length) ? i : -1;
    }

    public boolean isBack(int slot) { return slot == BACK_SLOT; }

    /* ---------------------------------------------------------------- open */

    public void open(Player viewer, CinematicEditorSession session) {
        int target = session.colorTarget();
        CinematicAnchor a = session.workingAnchor();

        GuiBuilder b = GuiBuilder.of(this, 4)
                .title(GuiTitles.framed("Couleurs — " + TARGET_LABELS[target]))
                .border();

        for (int i = 0; i < TARGET_SLOTS.length; i++) {
            NamedTextColor cur = session.currentColor(i);
            String curName = cur != null ? cur.toString() : "défaut";
            boolean selected = i == target;
            b.item(TARGET_SLOTS[i], selected
                    ? GuiIcons.primary(Material.NAME_TAG, TARGET_LABELS[i], "&7Couleur : &f" + curName, "&aSélectionné")
                    : GuiIcons.info(Material.NAME_TAG, TARGET_LABELS[i], "&7Couleur : &f" + curName, "&eCliquer pour cibler"));
        }

        for (int i = 0; i < PALETTE.length; i++) {
            b.item(PALETTE_START + i, GuiIcons.coloured(SWATCH[i],
                    capitalise(PALETTE[i].toString()), PALETTE[i],
                    "&7Applique à : &f" + TARGET_LABELS[target]));
        }

        b.item(BACK_SLOT, GuiIcons.backButton());
        b.fillEmpty();
        b.open(viewer);
    }

    private static String capitalise(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
