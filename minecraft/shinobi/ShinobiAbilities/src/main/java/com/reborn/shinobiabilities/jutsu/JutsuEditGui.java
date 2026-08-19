package com.reborn.shinobiabilities.jutsu;

import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobiabilities.gui.AbilityText;
import com.reborn.shinobiabilities.gui.AbilityScreen;
import com.reborn.shinobiabilities.gui.GuiRouter;
import com.reborn.shinobicore.gui.framework.Screen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiLayout;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.character.gui.GuiTitles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Binding editor — Sneak + F on a JutsuItem, or hub → Liaisons →
 * canal. Framework screen:
 *
 * <pre>
 * row 0 : frame, help book centre
 * row 1 : the five binding slots (click = select, shift-click = clear)
 * rows 2-4 : the channel's ability catalogue (27 per page)
 * row 5 : shared footer (back · pagination · close)
 * </pre>
 *
 * Serves the admin path too (editing any character's bindings); the
 * back button returns to that character's channel picker.
 */
public final class JutsuEditGui extends AbilityScreen {

    private static final String STATE_TYPE = "type";
    private static final String STATE_TARGET = "target";
    private static final String STATE_SELECTED = "selected";

    private static final int[] BINDING_SLOTS = GuiLayout.five(1);
    private static final int PAGE_AREA_START = 18;
    private static final int PAGE_SIZE = 27;
    private static final int SLOT_HELP = 4;

    private final JavaPlugin plugin;
    private final AbilityRegistry registry;
    private final JutsuBindingStore bindings;

    public JutsuEditGui(JavaPlugin plugin, AbilityRegistry registry,
                        JutsuBindingStore bindings, GuiRouter router) {
        super(router);
        this.plugin = plugin;
        this.registry = registry;
        this.bindings = bindings;
    }

    public void open(Player p, JutsuItemType type, ShinobiCharacter character) {
        Map<String, Object> state = new HashMap<>();
        state.put(STATE_TYPE, type.name());
        state.put(STATE_TARGET, character.id());
        state.put(STATE_SELECTED, 0);
        router.screens().open(p, this, state);
    }

    /** Close the editor if {@code p} has it open (switch / KO hooks in
     *  JutsuListener — the ScreenManager also force-closes on its own). */
    public void closeFor(Player p) {
        router.screens().closeIfScreen(p, this);
    }

    /* -------------------------------------------------------------- state */

    private JutsuItemType type(View view) {
        return JutsuItemType.from(view.string(STATE_TYPE));
    }

    private List<Ability> eligibleFor(Player p, View view) {
        boolean requireLearned = plugin.getConfig()
                .getBoolean("jutsu.require-learned", false)
                && !p.hasPermission("shinobiabilities.admin");
        ShinobiCharacter c = router.characterById(view.uuid(STATE_TARGET));
        List<Ability> out = new ArrayList<>();
        JutsuItemType type = type(view);
        if (type == null) return out;
        for (Ability a : registry.castableFor(type)) {
            if (requireLearned && (c == null || !c.knowsAbility(a.id()))) continue;
            out.add(a);
        }
        return out;
    }

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        JutsuItemType type = type(view);
        return GuiTitles.framedWithCharacter(
                type == null ? "Sorts" : type.displayName(),
                router.characterById(view.uuid(STATE_TARGET)));
    }

    @Override
    public int rows(View view) { return 6; }

    @Override
    public int pages(Player viewer, View view) {
        return Math.max(1, (eligibleFor(viewer, view).size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        JutsuItemType type = type(view);
        UUID target = view.uuid(STATE_TARGET);
        if (type == null || target == null) return;
        int selected = view.integer(STATE_SELECTED, 0);

        Ui.frame(inv);
        inv.setItem(SLOT_HELP, GuiIcons.info(Material.BOOK, "Aide",
                "&71. Clique un slot (en haut) pour le sélectionner.",
                "&72. Clique un sort (en bas) pour le lier.",
                "&73. Shift-clic sur un slot pour le vider.",
                "&8Les sorts liés apparaissent dans le sélecteur (F)."));

        String[] bound = bindings.get(target, type);
        for (int i = 0; i < JutsuBindingStore.SLOTS; i++) {
            inv.setItem(BINDING_SLOTS[i],
                    bindingIcon(i, registry.byId(bound[i]), i == selected));
        }

        List<Ability> eligible = eligibleFor(viewer, view);
        int from = view.page() * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = from + i;
            if (idx >= eligible.size()) break;
            Ability a = eligible.get(idx);
            inv.setItem(PAGE_AREA_START + i, Ui.coloured(a.jutsu().icon(),
                    a.name(), NamedTextColor.AQUA, "bind", a.id(),
                    AbilityText.loreOf(a, "",
                            "&eClique pour lier au slot sélectionné")));
        }

        Ui.footer(inv, true, view.page(), pages(viewer, view),
                "&7" + eligible.size() + " sort(s) éligibles");
        Ui.fillEmpty(inv);
    }

    private ItemStack bindingIcon(int slot, Ability bound, boolean selected) {
        ItemStack it;
        if (bound != null && bound.isCastable()) {
            it = Ui.coloured(bound.jutsu().icon(),
                    "Slot " + (slot + 1) + " — " + bound.name(),
                    selected ? NamedTextColor.GOLD : NamedTextColor.AQUA,
                    "slot", String.valueOf(slot),
                    AbilityText.rankCode(bound.rank()) + "Rang "
                            + bound.rank().displayName(),
                    "",
                    selected ? "&6▶ Slot sélectionné" : "&eClique pour sélectionner",
                    "&cShift-clic pour vider");
        } else {
            it = Ui.coloured(selected
                            ? Material.YELLOW_STAINED_GLASS_PANE
                            : Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "Slot " + (slot + 1) + " — vide",
                    selected ? NamedTextColor.GOLD : NamedTextColor.GRAY,
                    "slot", String.valueOf(slot),
                    selected ? "&6▶ Slot sélectionné — choisis un sort en bas"
                            : "&eClique pour sélectionner");
        }
        return Ui.glint(it, selected);
    }

    /* -------------------------------------------------------------- clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        JutsuItemType type = type(view);
        UUID target = view.uuid(STATE_TARGET);
        if (type == null || target == null) return;

        if (action.equals("slot")) {
            int slot;
            try { slot = Integer.parseInt(value); }
            catch (NumberFormatException ex) { return; }
            if (event.isShiftClick()) {
                bindings.set(target, type, slot, null);
                GuiSounds.destructive(viewer);
            } else {
                view.set(STATE_SELECTED, slot);
                GuiSounds.select(viewer);
            }
            refresh(viewer, view);
            return;
        }

        if (action.equals("bind")) {
            Ability a = registry.byId(value);
            if (a == null || !a.isCastable() || a.jutsu().itemType() != type) return;
            bindings.set(target, type, view.integer(STATE_SELECTED, 0), a.id());
            GuiSounds.accept(viewer);
            refresh(viewer, view);
        }
    }

    @Override
    public void onBack(Player viewer, View view) {
        ShinobiCharacter target = router.characterById(view.uuid(STATE_TARGET));
        if (target != null) {
            router.openChannelPicker(viewer, target);
        } else {
            viewer.closeInventory();
        }
    }
}
