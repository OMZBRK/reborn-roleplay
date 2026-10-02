package com.reborn.shinobicore.map;

import com.reborn.shinobicore.map.PlaceRegistry.MapDef;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * La <b>carte</b> d'une région, objet RP : il faut la tenir en main pour ouvrir la
 * carte du monde (clic droit ou touche M). Une carte = une {@link MapDef}.
 *
 * <p>PAPER (aucun comportement vanilla au clic) avec le modèle visuel de la carte
 * remplie vanilla ; le marqueur PDC {@code map_id} est la seule source de vérité.
 */
public final class MapItem {

    private static final Material MATERIAL = Material.PAPER;

    private MapItem() {}

    private static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, "map_id");
    }

    public static ItemStack create(JavaPlugin plugin, MapDef def) {
        ItemStack item = new ItemStack(MATERIAL);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(Component.text("Carte — " + def.title(), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Un parchemin dessiné à la main.", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Tiens-la en main : clic droit ou touche M.", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.setItemModel(NamespacedKey.minecraft("filled_map"));
        meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(key(plugin), PersistentDataType.STRING, def.id());
        item.setItemMeta(meta);
        return item;
    }

    /** Id de carte porté par l'objet, ou null si ce n'est pas une carte. */
    public static String mapId(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != MATERIAL || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key(plugin), PersistentDataType.STRING);
    }

    /** Carte tenue en main principale, sinon en main secondaire ; null si aucune. */
    public static String held(JavaPlugin plugin, Player p) {
        String id = mapId(plugin, p.getInventory().getItemInMainHand());
        return id != null ? id : mapId(plugin, p.getInventory().getItemInOffHand());
    }

    /** Donne la carte (barre d'action / inventaire, sinon au sol). */
    public static void give(JavaPlugin plugin, Player p, MapDef def) {
        p.getInventory().addItem(create(plugin, def)).values()
                .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
    }
}
