package com.reborn.shinobiabilities.gui;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobicore.gui.framework.Screen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.character.gui.GuiTitles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Staff scroll-delivery picker: a paginated, alphabetical grid of the
 * characters currently connected (each online player's active character).
 * Opened from the {@link CatalogueGui} via shift-click on a jutsu; clicking
 * a character hands that player the parchemin for the chosen ability.
 */
public final class CharacterPickerGui extends AbilityScreen {

    private static final String STATE_ABILITY = "ability";
    private static final int PAGE_SIZE = 45;

    private final AbilityRegistry registry;

    public CharacterPickerGui(GuiRouter router, AbilityRegistry registry) {
        super(router);
        this.registry = registry;
    }

    /** Open the picker for a chosen ability id. */
    public void open(Player staff, String abilityId) {
        Map<String, Object> state = new HashMap<>();
        state.put(STATE_ABILITY, abilityId);
        router.screens().open(staff, this, state);
    }

    /* ----------------------------------------------------------- connected */

    /** One connected character per online player, alphabetical by name. */
    private List<Entry> connected() {
        List<Entry> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter c = router.core().characters().getActive(p.getUniqueId());
            if (c != null) out.add(new Entry(p.getUniqueId(), c.name()));
        }
        out.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return out;
    }

    private record Entry(UUID playerId, String name) {}

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        Ability a = registry.byId(view.string(STATE_ABILITY));
        return GuiTitles.framed("Donner : " + (a != null ? a.name() : "Technique"));
    }

    @Override
    public int rows(View view) {
        return 6;
    }

    @Override
    public int pages(Player viewer, View view) {
        return Math.max(1, (connected().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        List<Entry> list = connected();
        int from = view.page() * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = from + i;
            if (idx >= list.size()) break;
            Entry e = list.get(idx);
            inv.setItem(i, Ui.coloured(Material.PLAYER_HEAD, e.name(),
                    NamedTextColor.AQUA, "pick", e.playerId().toString(),
                    "&7Clique pour remettre le parchemin"));
        }
        Ui.footer(inv, true, view.page(), pages(viewer, view),
                "&7" + list.size() + " connecté(s)");
        Ui.fillEmpty(inv);
    }

    /* -------------------------------------------------------------- clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!action.equals("pick")) return;
        if (!viewer.hasPermission("shinobiabilities.staff")) return;

        Ability a = registry.byId(view.string(STATE_ABILITY));
        if (a == null) { viewer.closeInventory(); return; }

        UUID playerId;
        try { playerId = UUID.fromString(value); }
        catch (IllegalArgumentException ignore) { return; }

        Player target = Bukkit.getPlayer(playerId);
        if (target == null || !target.isOnline()) {
            viewer.sendMessage(Component.text("Ce joueur n'est plus connecté.",
                    NamedTextColor.RED));
            refresh(viewer, view);
            return;
        }

        ItemStack scroll = ParcheminItems.create(a);
        var overflow = target.getInventory().addItem(scroll);
        overflow.values().forEach(it ->
                target.getWorld().dropItemNaturally(target.getLocation(), it));
        GuiSounds.accept(viewer);
        viewer.sendMessage(Component.text("Parchemin de « " + a.name()
                + " » remis à " + target.getName() + ".", NamedTextColor.GREEN));
        target.sendMessage(Component.text("Tu reçois un parchemin : « "
                + a.name() + " ».", NamedTextColor.GOLD));
    }

    @Override
    public void onBack(Player viewer, View view) {
        router.openCatalogueBrowse(viewer);
    }
}
