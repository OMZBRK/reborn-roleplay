package com.reborn.shinobiabilities.jutsu;

import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobicore.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Factories + recognition for JutsuItems and picker icons.
 *
 * <p>JutsuItems are universal keys — NO owner stamping. The PDC carries
 * only the item-type marker; bindings live on the character.
 */
public final class JutsuItems {

    /** PDC: marks an item as a JutsuItem; value = JutsuItemType name. */
    public static final String PDC_ITEM_TYPE = "jutsu_item_type";
    /** PDC: marks picker-bar icons so they can never leak/dupe. */
    public static final String PDC_PICKER_ICON = "jutsu_picker_icon";

    private JutsuItems() {}

    /** Build the universal JutsuItem for {@code type}. */
    public static ItemStack create(JutsuItemType type) {
        ItemStack it = new ItemStack(type.material());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Texts.title(type.displayName(), NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Texts.lore("Canal de jutsu — " + type.displayName()));
        lore.add(Texts.spacer());
        lore.add(Texts.lore("F : ouvrir le sélecteur", NamedTextColor.YELLOW));
        lore.add(Texts.lore("Accroupi + F : éditer les sorts", NamedTextColor.YELLOW));
        lore.add(Texts.lore("Clic gauche : lancer le sort 1", NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        Keys.setString(meta, PDC_ITEM_TYPE, type.name());
        it.setItemMeta(meta);
        return it;
    }

    /** JutsuItemType of {@code item}, or null when it isn't one. */
    public static JutsuItemType typeOf(ItemStack item) {
        return JutsuItemType.from(Keys.getString(item, PDC_ITEM_TYPE));
    }

    /** True when the player holds a JutsuItem in the given stack. */
    public static boolean isJutsuItem(ItemStack item) {
        return typeOf(item) != null;
    }

    /** Either hand of {@code p} holding any JutsuItem? Used by the
     *  EnderSignal guard. */
    public static boolean holdsType(Player p, JutsuItemType type) {
        return typeOf(p.getInventory().getItemInMainHand()) == type
                || typeOf(p.getInventory().getItemInOffHand()) == type;
    }

    /* ------------------------------------------------------ picker icons */

    /** Icon rendered in picker slot 1-5 for a bound jutsu. */
    public static ItemStack pickerIcon(Ability ability, int slotNumber) {
        ItemStack it = new ItemStack(ability.jutsu().icon());
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Texts.title(ability.name(), NamedTextColor.AQUA));
        List<Component> lore = new ArrayList<>();
        lore.add(Texts.lore("Rang " + ability.rank().displayName(), ability.rank().color()));
        lore.add(Texts.lore(ability.category()));
        lore.add(Texts.lore((int) ability.jutsu().chakraCost() + " chakra · "
                + (ability.jutsu().cooldownMillis() / 1000.0) + "s", NamedTextColor.DARK_AQUA));
        lore.add(Texts.spacer());
        lore.add(Texts.lore(methodHint(ability), NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        Keys.setString(meta, PDC_PICKER_ICON, ability.id());
        it.setItemMeta(meta);
        return it;
    }

    /** Empty picker slot filler. */
    public static ItemStack emptySlotIcon(int slotNumber) {
        ItemStack it = new ItemStack(org.bukkit.Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Texts.lore("Slot " + slotNumber + " — vide", NamedTextColor.DARK_GRAY));
        meta.lore(List.of(Texts.flavour("Accroupi + F pour assigner")));
        Keys.setString(meta, PDC_PICKER_ICON, "");
        it.setItemMeta(meta);
        return it;
    }

    /** Trailing filler for picker slots 6-8. */
    public static ItemStack fillerIcon() {
        ItemStack it = new ItemStack(org.bukkit.Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.empty());
        Keys.setString(meta, PDC_PICKER_ICON, "");
        it.setItemMeta(meta);
        return it;
    }

    /** True for any item minted by the picker (icon or filler). */
    public static boolean isPickerIcon(ItemStack item) {
        return Keys.has(item, PDC_PICKER_ICON);
    }

    private static String methodHint(Ability a) {
        return switch (a.jutsu().method()) {
            case LEFT_CLICK, MUDRA -> "Clic gauche pour lancer";
            case RIGHT_CLICK -> "Clic droit pour lancer";
            case HOLD_SNEAK -> "Maintiens Accroupi ≥ 1s puis relâche";
            case CLICK_SEQUENCE -> "3 clics rapides pour lancer";
            case F_PRESS -> "Clic gauche pour lancer"; // rewritten at load
        };
    }
}
