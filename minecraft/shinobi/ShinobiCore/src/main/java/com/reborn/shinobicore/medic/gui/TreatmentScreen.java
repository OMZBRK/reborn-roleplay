package com.reborn.shinobicore.medic.gui;

import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiTitles;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobicore.ko.injury.Injury;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * The "drop medicines + validate" screen for one specific injury.
 *
 * <h2>Layout (3-row chest)</h2>
 * <pre>
 * row 0 |  free drop area                  (slots 0..8)
 * row 1 |  free drop area                  (slots 9..17)
 * row 2 |  [Annuler]   ......   [Valider]  (slot 18 + 26)
 * </pre>
 *
 * <p>No recipe ghosts, no help button. The medic must remember (or
 * consult their copy of "Les Blessures Majeures") which medicines to
 * apply. Framework port of the legacy {@code TreatmentGui}: the drop
 * area + the medic's own inventory pass clicks and drags through
 * ({@link #onRawClick}/{@link #onRawDrag}); ESC-close refunds whatever
 * sits in the strip unless Annuler/Valider already consumed it
 * ({@link #onClose}).
 */
public final class TreatmentScreen extends CoreScreen {

    static final String S_INJURY_ID = "injuryId";
    static final String S_LABEL     = "label";
    /** Set once Annuler/Valider consumed the strip — ESC-close then
     *  skips the refund. */
    static final String S_CONSUMED  = "consumed";

    public static final int SLOT_CANCEL   = 18;
    public static final int SLOT_VALIDATE = 26;
    /** Inclusive range of free-drop slots — rows 0 and 1. */
    public static final int DROP_FROM = 0;
    public static final int DROP_TO   = 17;

    private static final String ACT_CANCEL   = "treat:cancel";
    private static final String ACT_VALIDATE = "treat:validate";

    public TreatmentScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    public void open(Player viewer, SoignerScreen.Target target,
                     UUID targetId, Injury injury) {
        router.screens().open(viewer, this, Map.of(
                SoignerScreen.S_TARGET, target,
                SoignerScreen.S_TARGET_ID, targetId,
                S_INJURY_ID, injury.id(),
                S_LABEL, injury.type().label()));
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framed("Soin — " + view.string(S_LABEL));
    }

    @Override
    public int rows(View view) {
        return 3;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        // Rows 0-1 stay empty: that's the free drop area. Only the
        // bottom nav strip is painted.
        inv.setItem(SLOT_CANCEL, Ui.action(
                GuiIcons.destructive(Material.BARRIER, "Annuler",
                        "&cFerme la fenêtre et te rend les médicaments."),
                ACT_CANCEL));
        inv.setItem(SLOT_VALIDATE, Ui.action(
                GuiIcons.primary(Material.LIME_WOOL, "Valider",
                        "&aLance le rituel d'application."),
                ACT_VALIDATE));
        for (int s = SLOT_CANCEL + 1; s < SLOT_VALIDATE; s++) {
            inv.setItem(s, GuiIcons.filler());
        }
    }

    /* ------------------------------------------------------------ clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        switch (action) {
            case ACT_CANCEL -> {
                ItemStack[] strip = consumeStrip(view);
                viewer.closeInventory();
                refundStrip(viewer, strip);
            }
            case ACT_VALIDATE -> {
                ItemStack[] strip = consumeStrip(view);
                viewer.closeInventory();
                core().medicApplier().run(viewer,
                        view.get(SoignerScreen.S_TARGET),
                        view.uuid(SoignerScreen.S_TARGET_ID),
                        view.uuid(S_INJURY_ID), strip);
            }
            default -> { }
        }
    }

    @Override
    public void onRawClick(Player viewer, View view, InventoryClickEvent event) {
        int slot = event.getRawSlot();
        int chestSize = event.getInventory().getSize();
        // Free movement in the medic's own inventory and the drop area;
        // everything else (bottom-row fillers) stays cancelled.
        if (slot >= chestSize || (slot >= DROP_FROM && slot <= DROP_TO)) {
            event.setCancelled(false);
        }
    }

    @Override
    public void onRawDrag(Player viewer, View view, InventoryDragEvent event) {
        int chestSize = event.getInventory().getSize();
        // Allow the drag only when every affected slot is free-movement
        // territory (drop area or the medic's own inventory).
        for (int raw : event.getRawSlots()) {
            if (raw < chestSize && (raw < DROP_FROM || raw > DROP_TO)) return;
        }
        event.setCancelled(false);
    }

    @Override
    public void onClose(Player viewer, View view) {
        // ESC-close refunds whatever is sitting in the drop area,
        // unless Annuler/Valider already consumed it.
        if (view.flag(S_CONSUMED)) return;
        refundStrip(viewer, consumeStrip(view));
    }

    /* ----------------------------------------------------------- helpers */

    /** Snapshot + wipe the drop strip, marking it consumed. */
    private static ItemStack[] consumeStrip(View view) {
        Inventory inv = view.getInventory();
        ItemStack[] out = new ItemStack[DROP_TO - DROP_FROM + 1];
        if (inv != null) {
            for (int i = 0; i < out.length; i++) {
                ItemStack s = inv.getItem(DROP_FROM + i);
                out[i] = (s == null || s.getType().isAir()) ? null : s.clone();
                inv.setItem(DROP_FROM + i, null);
            }
        }
        view.set(S_CONSUMED, true);
        return out;
    }

    private static void refundStrip(Player viewer, ItemStack[] strip) {
        for (ItemStack s : strip) {
            if (s == null) continue;
            var leftover = viewer.getInventory().addItem(s);
            for (ItemStack stuck : leftover.values()) {
                viewer.getWorld().dropItemNaturally(viewer.getLocation(), stuck);
            }
        }
    }
}
