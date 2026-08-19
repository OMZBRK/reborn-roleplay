package com.reborn.shinobiabilities.gui;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobicore.gui.framework.Screen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.character.gui.GuiTitles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mes Techniques — the character's learned abilities, grouped by
 * category (registry order), paginated. Click prints the details.
 */
public final class KnownAbilitiesGui extends AbilityScreen {

    private static final String STATE_TARGET = "target";
    private static final int PAGE_SIZE = 45;

    private final AbilityRegistry registry;

    public KnownAbilitiesGui(AbilityRegistry registry, GuiRouter router) {
        super(router);
        this.registry = registry;
    }

    public void open(Player p, ShinobiCharacter c) {
        router.screens().open(p, this, Map.of(STATE_TARGET, c.id()));
    }

    private ShinobiCharacter target(View view) {
        return router.characterById(view.uuid(STATE_TARGET));
    }

    private List<Ability> knownOf(ShinobiCharacter c) {
        List<Ability> out = new ArrayList<>();
        if (c == null) return out;
        for (Ability a : registry.all().values()) {
            if (c.knowsAbility(a.id())) out.add(a);
        }
        return out;
    }

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Techniques", target(view));
    }

    @Override
    public int rows(View view) { return 6; }

    @Override
    public int pages(Player viewer, View view) {
        return Math.max(1, (knownOf(target(view)).size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        ShinobiCharacter c = target(view);
        List<Ability> known = knownOf(c);

        if (known.isEmpty()) {
            inv.setItem(22, GuiIcons.info(Material.LECTERN,
                    "Aucune technique connue",
                    "&7Trouve des parchemins et entraîne-toi",
                    "&7sur une Étagère d'Apprentissage,",
                    "&7ou explore le &6Catalogue&7 du /menu."));
        } else {
            int from = view.page() * PAGE_SIZE;
            for (int i = 0; i < PAGE_SIZE; i++) {
                int idx = from + i;
                if (idx >= known.size()) break;
                Ability a = known.get(idx);
                Material mat = a.isCastable() ? a.jutsu().icon() : Material.PAPER;
                inv.setItem(i, Ui.coloured(mat, a.name(), NamedTextColor.AQUA,
                        "detail", a.id(),
                        AbilityText.loreOf(a, "", "&eClique pour les détails")));
            }
        }
        Ui.footer(inv, true, view.page(), pages(viewer, view),
                "&6" + known.size() + " technique(s)");
        Ui.fillEmpty(inv);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!action.equals("detail")) return;
        Ability a = registry.byId(value);
        if (a == null) return;
        GuiSounds.select(viewer);
        AbilityText.printDetails(viewer, a);
    }
}
