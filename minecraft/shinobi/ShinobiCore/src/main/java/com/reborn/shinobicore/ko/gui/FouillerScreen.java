package com.reborn.shinobicore.ko.gui;

import com.reborn.shinobicore.character.AutoMe;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiTitles;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;
import java.util.UUID;

/**
 * Loot UI for a KO target — framework port of the legacy
 * {@code FouillerGui}, layout and semantics identical:
 *
 * <pre>
 * row 0    |  hotbar 0..8
 * rows 1-3 |  main inventory 9..35
 * row 4    |  helm chest legs boots offhand
 * row 5    |  close (53)
 * </pre>
 *
 * <p>The looter shuffles freely between their inventory and the body
 * ({@link #onRawClick} un-cancels); pulling an item out of the chest
 * portion emits the auto-/me. The write-back to the target's live
 * inventory happens on {@link #onClose} — which the framework also
 * fires on the character-switch / KO force-closes, so no loot state
 * is ever stranded.
 */
public final class FouillerScreen extends CoreScreen {

    static final String S_TARGET_ID = "targetId";
    static final String S_NAME      = "name";

    public static final int SLOT_CLOSE = 53;

    public static final int SLOT_HELM  = 36;
    public static final int SLOT_CHEST = 37;
    public static final int SLOT_LEGS  = 38;
    public static final int SLOT_BOOTS = 39;
    public static final int SLOT_OFFH  = 40;

    public FouillerScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    public void open(Player viewer, UUID targetPlayerId) {
        Player target = core().getServer().getPlayer(targetPlayerId);
        if (target == null) {
            viewer.sendMessage(Component.text(
                    "La cible n'est plus connectée.", NamedTextColor.RED));
            return;
        }
        router.screens().open(viewer, this, Map.of(
                S_TARGET_ID, targetPlayerId,
                S_NAME, target.getName()));
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framed("Fouiller — " + view.string(S_NAME));
    }

    @Override
    public int rows(View view) {
        return 6;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        // Seeded once at open from the target's live inventory; NEVER
        // re-rendered mid-session (a refresh would clobber the looter's
        // in-progress shuffling — same contract as the legacy GUI).
        Player target = core().getServer().getPlayer(view.uuid(S_TARGET_ID));
        if (target == null) return;
        PlayerInventory pInv = target.getInventory();

        // Main + hotbar.
        ItemStack[] storage = pInv.getStorageContents();
        for (int i = 0; i < 36 && i < storage.length; i++) {
            if (storage[i] != null && !storage[i].getType().isAir()) {
                inv.setItem(i, storage[i].clone());
            }
        }
        // Armor — getArmorContents(): [boots, legs, chest, helm].
        ItemStack[] armor = pInv.getArmorContents();
        if (armor.length > 0 && armor[0] != null && !armor[0].getType().isAir()) inv.setItem(SLOT_BOOTS, armor[0].clone());
        if (armor.length > 1 && armor[1] != null && !armor[1].getType().isAir()) inv.setItem(SLOT_LEGS,  armor[1].clone());
        if (armor.length > 2 && armor[2] != null && !armor[2].getType().isAir()) inv.setItem(SLOT_CHEST, armor[2].clone());
        if (armor.length > 3 && armor[3] != null && !armor[3].getType().isAir()) inv.setItem(SLOT_HELM,  armor[3].clone());
        // Offhand.
        ItemStack off = pInv.getItemInOffHand();
        if (off != null && !off.getType().isAir()) {
            inv.setItem(SLOT_OFFH, off.clone());
        }

        inv.setItem(SLOT_CLOSE, Ui.action(GuiIcons.closeButton(), Ui.ACTION_CLOSE));
        // No fillEmpty — row 5 stays free, exactly like the legacy GUI.
    }

    /* ------------------------------------------------------------ clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        // No tagged action tiles remain in this screen (the legacy "sac"
        // dive was removed with the old Backpack system); the close button
        // is handled by the framework via Ui.ACTION_CLOSE.
    }

    @Override
    public void onRawClick(Player viewer, View view, InventoryClickEvent event) {
        // Free shuffling everywhere that isn't a tagged tile. If the
        // click pulls an item OUT of the chest portion, emit an
        // auto-/me so onlookers see the looter grab something.
        event.setCancelled(false);
        int raw = event.getRawSlot();
        int chestSize = event.getInventory().getSize();
        if (raw >= chestSize) return;
        ItemStack picked = event.getCurrentItem();
        InventoryAction act = event.getAction();
        boolean isPickup = act == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || act == InventoryAction.PICKUP_ALL
                || act == InventoryAction.PICKUP_HALF
                || act == InventoryAction.PICKUP_ONE
                || act == InventoryAction.PICKUP_SOME
                || act == InventoryAction.HOTBAR_SWAP
                || act == InventoryAction.HOTBAR_MOVE_AND_READD;
        if (isPickup && picked != null && !picked.getType().isAir()) {
            Player tgt = core().getServer().getPlayer(view.uuid(S_TARGET_ID));
            String tgtName;
            if (tgt != null) {
                ShinobiCharacter tc = core().characters().getActive(tgt.getUniqueId());
                tgtName = tc != null ? tc.name() : tgt.getName();
            } else {
                tgtName = "la cible";
            }
            AutoMe.broadcast(core(), viewer,
                    "prend " + readableItemName(picked)
                            + " dans les affaires de " + tgtName + ".");
        }
    }

    @Override
    public void onClose(Player viewer, View view) {
        Player target = core().getServer().getPlayer(view.uuid(S_TARGET_ID));
        writeBackTo(view, target);
    }

    /* ----------------------------------------------------------- helpers */

    /** Push every mapped slot back to the target's live inventory —
     *  items the looter took are removed by the overwrite, items
     *  inserted are added. */
    private static void writeBackTo(View view, Player target) {
        Inventory inv = view.getInventory();
        if (target == null || !target.isOnline() || inv == null) return;
        PlayerInventory pInv = target.getInventory();

        ItemStack[] main = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            main[i] = (s != null && !s.getType().isAir()) ? s.clone() : null;
        }
        pInv.setStorageContents(main);

        ItemStack[] armor = new ItemStack[4];
        armor[0] = nullIfAir(inv.getItem(SLOT_BOOTS));
        armor[1] = nullIfAir(inv.getItem(SLOT_LEGS));
        armor[2] = nullIfAir(inv.getItem(SLOT_CHEST));
        armor[3] = nullIfAir(inv.getItem(SLOT_HELM));
        pInv.setArmorContents(armor);

        pInv.setItemInOffHand(nullIfAir(inv.getItem(SLOT_OFFH)));
    }

    private static ItemStack nullIfAir(ItemStack s) {
        return (s == null || s.getType().isAir()) ? null : s.clone();
    }

    /** Best-effort French label for an arbitrary item — custom display
     *  name when set, else the material name lower-cased. */
    private static String readableItemName(ItemStack item) {
        if (item == null) return "un objet";
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            Component dn = item.getItemMeta().displayName();
            if (dn != null) {
                String plain = PlainTextComponentSerializer.plainText()
                        .serialize(dn).trim();
                if (!plain.isEmpty()) return plain;
            }
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }
}
