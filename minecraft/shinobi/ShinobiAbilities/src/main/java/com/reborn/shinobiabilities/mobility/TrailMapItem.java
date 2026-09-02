package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobicore.util.Texts;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * « Carte des Pistes » — a COMPASS that points to the nearest trail waypoint
 * (départ / étape / arrivée). Held, it shows a live direction + distance read
 * in the actionbar; right-clicking re-aims the physical needle.
 */
public final class TrailMapItem {

    private static final String MARKER = "trail_map";

    private TrailMapItem() {}

    public static ItemStack create() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.displayName(Texts.title("Carte des Pistes", NamedTextColor.AQUA));
            meta.lore(List.of(
                    Texts.lore("Indique la piste la plus proche"),
                    Texts.lore("(départ, étape ou arrivée)."),
                    Texts.lore("Clic droit : oriente l'aiguille.")));
            Keys.setString(meta, MARKER, "1");
            it.setItemMeta(meta);
        }
        return it;
    }

    public static boolean isTrailMap(ItemStack item) {
        return item != null && item.getType() == Material.COMPASS && Keys.has(item, MARKER);
    }

    /** Aim the compass needle at {@code loc} (untracked lodestone, so it holds
     *  the bearing even without a real lodestone block). */
    public static void pointTo(ItemStack item, Location loc) {
        if (item == null || loc == null) return;
        if (item.getItemMeta() instanceof CompassMeta cm) {
            cm.setLodestone(loc);
            cm.setLodestoneTracked(false);
            item.setItemMeta(cm);
        }
    }
}
