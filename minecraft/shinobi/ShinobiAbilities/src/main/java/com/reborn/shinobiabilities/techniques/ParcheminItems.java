package com.reborn.shinobiabilities.techniques;

import com.reborn.shinobiabilities.parchemin.ScrollCatalog;
import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobicore.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Parchemin de technique : un vrai objet (il se vole, s'échange, se brûle, se pose) qui porte une technique.
 * Modèle 3D par rang ({@code reborn:parchemin_<rang>}, livré par le mod), identifiant unique de rouleau (slots du
 * rang S, héritage) et poids propre dans la sacoche ({@code reborn:rp_weight}).
 */
public final class ParcheminItems {

    /** PDC : la technique portée par le rouleau. */
    public static final String PDC_ABILITY = "parchemin_ability";
    /** PDC : identifiant unique du rouleau. */
    public static final String PDC_SCROLL = "parchemin_id";

    private static final NamespacedKey WEIGHT = new NamespacedKey("reborn", "rp_weight");

    private ParcheminItems() {}

    public static ItemStack create(Ability ability) {
        return create(ability, UUID.randomUUID().toString());
    }

    public static ItemStack create(Ability ability, String scrollId) {
        char r = ScrollCatalog.rank(ability);
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("reborn", "parchemin_" + Character.toLowerCase(r == 'E' ? 'D' : r)));
        meta.setMaxStackSize(1);
        meta.displayName(Texts.title("Parchemin — " + ability.name(), NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(line("Rang " + r + " · " + ScrollCatalog.branch(ability)
                + (ScrollCatalog.nature(ability).isEmpty() ? "" : " · " + ScrollCatalog.nature(ability)), rankColor(r)));
        lore.add(line(ScrollCatalog.seances(r) + " séance" + (ScrollCatalog.seances(r) > 1 ? "s" : "")
                + " pour l'apprendre", NamedTextColor.GRAY));
        meta.lore(lore);
        Keys.setString(meta, PDC_ABILITY, ability.id());
        Keys.setString(meta, PDC_SCROLL, scrollId);
        meta.getPersistentDataContainer().set(WEIGHT, PersistentDataType.DOUBLE, ScrollCatalog.weight(r));
        it.setItemMeta(meta);
        return it;
    }

    private static Component line(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private static TextColor rankColor(char r) {
        return switch (r) {
            case 'C' -> TextColor.color(0x4E9A62);
            case 'B' -> TextColor.color(0x3E6FB8);
            case 'A' -> TextColor.color(0x8A4FC0);
            case 'S' -> TextColor.color(0xC01E35);
            default -> TextColor.color(0x8C98A2);
        };
    }

    /** Ability id rolled into this scroll, or null. */
    public static String abilityIdOf(ItemStack item) {
        return Keys.getString(item, PDC_ABILITY);
    }

    /** Identifiant unique du rouleau, ou null (anciens rouleaux). */
    public static String scrollIdOf(ItemStack item) {
        return Keys.getString(item, PDC_SCROLL);
    }

    public static boolean isParchemin(ItemStack item) {
        return abilityIdOf(item) != null;
    }

    /** A random scroll over the whole registry (admin handout). */
    public static ItemStack createRandom(AbilityRegistry registry) {
        var all = new ArrayList<>(registry.all().values());
        if (all.isEmpty()) return null;
        Ability pick = all.get(ThreadLocalRandom.current().nextInt(all.size()));
        return create(pick);
    }
}
