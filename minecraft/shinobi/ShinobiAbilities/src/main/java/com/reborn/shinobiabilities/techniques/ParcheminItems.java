package com.reborn.shinobiabilities.techniques;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobiabilities.util.Keys;
import com.reborn.shinobicore.util.Texts;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Parchemin de Technique — a paper scroll holding one rolled ability id.
 * Placed on an Étagère d'Apprentissage and consumed on a successful
 * learn.
 */
public final class ParcheminItems {

    /** PDC: the ability id this scroll teaches. */
    public static final String PDC_ABILITY = "parchemin_ability";

    private ParcheminItems() {}

    public static ItemStack create(Ability ability) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Texts.title("Parchemin — " + ability.name(),
                NamedTextColor.GOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Texts.lore("Rang " + ability.rank().displayName(),
                ability.rank().color()));
        lore.add(Texts.lore(ability.category()));
        lore.add(Texts.spacer());
        lore.add(Texts.lore("Pose-le sur une Étagère d'Apprentissage",
                NamedTextColor.YELLOW));
        lore.add(Texts.lore("puis clique-le pour t'entraîner.",
                NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        Keys.setString(meta, PDC_ABILITY, ability.id());
        it.setItemMeta(meta);
        return it;
    }

    /** Ability id rolled into this scroll, or null. */
    public static String abilityIdOf(ItemStack item) {
        return Keys.getString(item, PDC_ABILITY);
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
