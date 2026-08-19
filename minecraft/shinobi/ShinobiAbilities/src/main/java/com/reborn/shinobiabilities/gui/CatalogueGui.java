package com.reborn.shinobiabilities.gui;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobicore.gui.framework.Screen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiLayout;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.character.gui.GuiTitles;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Catalogue des techniques — branch picker, then a paginated ability
 * grid with learned markers.
 *
 * <ul>
 *   <li>{@link Mode#BROWSE} — players: « ✔ Connue » + glint, click
 *       prints the details.</li>
 *   <li>{@link Mode#ADMIN_MANAGE} — admins on a target character:
 *       click toggles learn/forget, persisted immediately.</li>
 * </ul>
 */
public final class CatalogueGui extends AbilityScreen {

    public enum Mode { BROWSE, ADMIN_MANAGE }

    /** Top-level branches of the first page. */
    private enum Branch {
        NINJUTSU("Ninjutsu", Material.FIRE_CHARGE, "ninjutsu"),
        BUKIJUTSU("Bukijutsu", Material.IRON_SWORD, "bukijutsu"),
        TAIJUTSU("Taijutsu", Material.LEATHER, "taijutsu"),
        KEKKEI("Kekkei Genkai", Material.AMETHYST_SHARD, "kekkei"),
        DOJUTSU("Dōjutsu", Material.ENDER_EYE, "kekkei/dojutsu"),
        SENJUTSU("Senjutsu", Material.LILY_PAD, "senjutsu"),
        AUTRES("Autres", Material.SPIDER_EYE, "autres");

        final String display;
        final Material icon;
        final String prefix;

        Branch(String display, Material icon, String prefix) {
            this.display = display;
            this.icon = icon;
            this.prefix = prefix;
        }
    }

    private static final String STATE_MODE = "mode";
    private static final String STATE_TARGET = "target";
    private static final String STATE_BRANCH = "branch";
    private static final int PAGE_SIZE = 36;

    private final AbilityRegistry registry;

    public CatalogueGui(GuiRouter router, AbilityRegistry registry) {
        super(router);
        this.registry = registry;
    }

    public void openBranches(Player p, Mode mode, UUID targetCharacterId) {
        Map<String, Object> state = new HashMap<>();
        state.put(STATE_MODE, mode);
        if (targetCharacterId != null) state.put(STATE_TARGET, targetCharacterId);
        router.screens().open(p, this, state);
    }

    /* -------------------------------------------------------------- state */

    private Mode mode(View v) {
        Object m = v.get(STATE_MODE);
        return m instanceof Mode mode ? mode : Mode.BROWSE;
    }

    private Branch branch(View v) {
        String raw = v.string(STATE_BRANCH);
        if (raw == null) return null;
        try { return Branch.valueOf(raw); }
        catch (IllegalArgumentException ignore) { return null; }
    }

    /** Whose « connue » markers we render: the admin target in manage
     *  mode, else the viewer's own active character. */
    private ShinobiCharacter context(Player viewer, View v) {
        if (mode(v) == Mode.ADMIN_MANAGE) {
            return router.characterById(v.uuid(STATE_TARGET));
        }
        return com.reborn.shinobicore.util.Players.active(router.core().characters(), viewer);
    }

    private List<Ability> abilitiesOf(Branch branch) {
        List<Ability> out = new ArrayList<>();
        for (Ability a : registry.byCategoryPrefix(branch.prefix)) {
            if (branch == Branch.KEKKEI
                    && a.category().startsWith("kekkei/dojutsu")) continue;
            out.add(a);
        }
        return out;
    }

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        Branch branch = branch(view);
        String base = branch != null ? branch.display
                : (mode(view) == Mode.ADMIN_MANAGE ? "Gérer les Techniques" : "Catalogue");
        ShinobiCharacter ctx = context(viewer, view);
        return ctx != null ? GuiTitles.framedWithCharacter(base, ctx)
                : GuiTitles.framed(base);
    }

    @Override
    public int rows(View view) {
        return branch(view) == null ? 4 : 6;
    }

    @Override
    public int pages(Player viewer, View view) {
        Branch branch = branch(view);
        if (branch == null) return 1;
        return Math.max(1, (abilitiesOf(branch).size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        Branch branch = branch(view);
        ShinobiCharacter ctx = context(viewer, view);

        if (branch == null) {
            Ui.frame(inv);
            Branch[] branches = Branch.values();
            int[] r1 = GuiLayout.quad(1);
            int[] r2 = GuiLayout.triple(2);
            for (int i = 0; i < branches.length; i++) {
                inv.setItem(i < 4 ? r1[i] : r2[i - 4], branchIcon(branches[i], ctx));
            }
            Ui.footer(inv, true, 0, 1);
            Ui.fillEmpty(inv);
            return;
        }

        List<Ability> list = abilitiesOf(branch);
        int from = view.page() * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = from + i;
            if (idx >= list.size()) break;
            inv.setItem(i, abilityIcon(list.get(idx), ctx, mode(view)));
        }
        Ui.footer(inv, true, view.page(), pages(viewer, view),
                "&7" + list.size() + " technique(s)");
        Ui.fillEmpty(inv);
    }

    private org.bukkit.inventory.ItemStack branchIcon(Branch branch, ShinobiCharacter ctx) {
        List<Ability> list = abilitiesOf(branch);
        int known = 0;
        if (ctx != null) {
            for (Ability a : list) if (ctx.knowsAbility(a.id())) known++;
        }
        return Ui.accent(branch.icon, branch.display, "branch", branch.name(),
                "&7" + list.size() + " technique(s)",
                ctx != null ? "&b" + known + " connue(s)" : "&7—",
                "&eClique pour parcourir");
    }

    private org.bukkit.inventory.ItemStack abilityIcon(Ability a, ShinobiCharacter ctx,
                                                       Mode mode) {
        Material mat = a.isCastable() ? a.jutsu().icon() : Material.PAPER;
        boolean known = ctx != null && ctx.knowsAbility(a.id());
        String[] lore = mode == Mode.ADMIN_MANAGE
                ? AbilityText.loreOf(a, "", known
                        ? "&c✔ Connue — clic pour retirer"
                        : "&a✘ Inconnue — clic pour apprendre")
                : AbilityText.loreOf(a, "", known
                        ? "&a✔ Connue"
                        : "&8Non apprise — cherche son parchemin !",
                        "&eClique pour les détails");
        return Ui.glint(Ui.coloured(mat, a.name(),
                known ? NamedTextColor.GREEN : NamedTextColor.AQUA,
                "ability", a.id(), lore), known);
    }

    /* -------------------------------------------------------------- clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (action.equals("branch")) {
            Branch branch;
            try { branch = Branch.valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignore) { return; }
            GuiSounds.navigate(viewer);
            // New view — the grid has a different chest size.
            Map<String, Object> state = new HashMap<>();
            state.put(STATE_MODE, mode(view));
            if (view.uuid(STATE_TARGET) != null) {
                state.put(STATE_TARGET, view.uuid(STATE_TARGET));
            }
            state.put(STATE_BRANCH, branch.name());
            router.screens().open(viewer, this, state);
            return;
        }

        if (!action.equals("ability")) return;
        Ability a = registry.byId(value);
        if (a == null) return;

        if (mode(view) == Mode.ADMIN_MANAGE) {
            if (!viewer.hasPermission("shinobiabilities.admin")) return;
            ShinobiCharacter target = router.characterById(view.uuid(STATE_TARGET));
            if (target == null) { viewer.closeInventory(); return; }
            boolean nowKnown;
            if (target.knowsAbility(a.id())) {
                target.forgetAbility(a.id());
                nowKnown = false;
            } else {
                target.learnAbility(a.id());
                nowKnown = true;
            }
            router.core().characters().save(target);
            if (nowKnown) GuiSounds.accept(viewer); else GuiSounds.destructive(viewer);
            refresh(viewer, view);
            return;
        }

        // Staff catalogue: a click mints the parchemin. Left-click hands it to
        // the staff member; shift-click opens the character picker to give it
        // to a connected character.
        if (!viewer.hasPermission("shinobiabilities.staff")) {
            GuiSounds.select(viewer);
            AbilityText.printDetails(viewer, a);
            return;
        }
        if (event.isShiftClick()) {
            GuiSounds.navigate(viewer);
            router.openCharacterPicker(viewer, a.id());
            return;
        }
        giveScroll(viewer, a);
    }

    /** Mint and hand the staff member the parchemin for {@code a}. */
    private void giveScroll(Player staff, Ability a) {
        org.bukkit.inventory.ItemStack scroll = ParcheminItems.create(a);
        var overflow = staff.getInventory().addItem(scroll);
        overflow.values().forEach(it ->
                staff.getWorld().dropItemNaturally(staff.getLocation(), it));
        GuiSounds.accept(staff);
        staff.sendMessage(Component.text(
                "Parchemin de « " + a.name() + " » ajouté à ton inventaire.",
                NamedTextColor.GREEN));
    }

    @Override
    public void onBack(Player viewer, View view) {
        if (branch(view) != null) {
            openBranches(viewer, mode(view), view.uuid(STATE_TARGET));
        } else if (mode(view) == Mode.ADMIN_MANAGE) {
            router.openAdminManage(viewer, view.uuid(STATE_TARGET));
        } else {
            router.openHub(viewer);
        }
    }
}
