package com.reborn.shinobiabilities.gui;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobicore.gui.framework.Screen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobiabilities.mobility.MobilityModule;
import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiLayout;
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
 * Administration — two shapes on one screen: the character list
 * (player heads, paginated) and, once a head is clicked, the
 * per-character management page (techniques, liaisons, tout
 * apprendre / tout oublier avec double-clic, reset cooldowns).
 */
public final class AdminGui extends AbilityScreen {

    private static final String STATE_TARGET = "target"; // null = list shape
    private static final int PAGE_SIZE = 45;
    private static final long CONFIRM_WINDOW_MS = 5000L;

    private record PendingWipe(UUID characterId, long at) {}

    private final AbilityRegistry registry;
    private final CooldownTracker cooldowns;
    private final MobilityModule mobility;
    /** viewer → pending « tout oublier » confirmation. */
    private final Map<UUID, PendingWipe> pendingWipes = new HashMap<>();

    public AdminGui(GuiRouter router, AbilityRegistry registry,
                    CooldownTracker cooldowns, MobilityModule mobility) {
        super(router);
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.mobility = mobility;
    }

    public void openCharacterList(Player p, int page) {
        View view = router.screens().open(p, this, Map.of());
        if (page > 0) {
            view.setPage(page);
            router.screens().refresh(p, view);
        }
    }

    public void openManage(Player p, UUID characterId) {
        if (router.characterById(characterId) == null) {
            GuiSounds.error(p);
            openCharacterList(p, 0);
            return;
        }
        router.screens().open(p, this, Map.of(STATE_TARGET, characterId));
    }

    /* -------------------------------------------------------------- helpers */

    private record Row(UUID owner, ShinobiCharacter c) {}

    private List<Row> allCharacters() {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<UUID, List<ShinobiCharacter>> e
                : router.core().characters().rosterView().entrySet()) {
            for (ShinobiCharacter c : e.getValue()) rows.add(new Row(e.getKey(), c));
        }
        rows.sort((a, b) -> a.c().name().compareToIgnoreCase(b.c().name()));
        return rows;
    }

    private int countKnown(ShinobiCharacter c) {
        int known = 0;
        for (Ability a : registry.all().values()) {
            if (c.knowsAbility(a.id())) known++;
        }
        return known;
    }

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        UUID target = view.uuid(STATE_TARGET);
        if (target == null) {
            return GuiTitles.framed("Administration", NamedTextColor.RED);
        }
        return GuiTitles.framedWithCharacter("Gérer", router.characterById(target));
    }

    @Override
    public int rows(View view) {
        return view.uuid(STATE_TARGET) == null ? 6 : 5;
    }

    @Override
    public int pages(Player viewer, View view) {
        if (view.uuid(STATE_TARGET) != null) return 1;
        return Math.max(1, (allCharacters().size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        UUID target = view.uuid(STATE_TARGET);
        if (target == null) renderList(viewer, view, inv);
        else renderManage(view, inv, target);
    }

    private void renderList(Player viewer, View view, Inventory inv) {
        List<Row> rows = allCharacters();
        int from = view.page() * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE; i++) {
            int idx = from + i;
            if (idx >= rows.size()) break;
            Row row = rows.get(idx);
            var owner = Bukkit.getOfflinePlayer(row.owner());
            ItemStack head = GuiIcons.head(owner, row.c().name(),
                    "&7Joueur : " + (owner.getName() == null ? "?" : owner.getName()),
                    "&7Niveau " + row.c().level() + " — " + row.c().rank().displayName(),
                    "&b" + countKnown(row.c()) + " technique(s) connue(s)",
                    row.c().dead() ? "&c☠ Mort"
                            : (Bukkit.getPlayer(row.owner()) != null
                                    ? "&a● Joueur en ligne" : "&7● Joueur hors-ligne"),
                    "",
                    "&eClique pour gérer");
            inv.setItem(i, Ui.action(head, "manage", row.c().id().toString()));
        }
        Ui.footer(inv, true, view.page(), pages(viewer, view),
                "&7" + rows.size() + " personnage(s) chargés");
        Ui.fillEmpty(inv);
    }

    private void renderManage(View view, Inventory inv, UUID targetId) {
        ShinobiCharacter c = router.characterById(targetId);
        if (c == null) return;
        UUID owner = router.ownerOf(targetId);
        boolean online = owner != null && Bukkit.getPlayer(owner) != null;

        Ui.frame(inv);
        int[] r1 = GuiLayout.triple(1);
        inv.setItem(r1[0], Ui.primary(Material.CHISELED_BOOKSHELF,
                "Gérer les techniques", "techniques", null,
                "&b" + countKnown(c) + " / " + registry.all().size() + " connue(s)",
                "&7Apprendre / retirer au clic,",
                "&7voie par voie.",
                "&eClique pour ouvrir"));
        inv.setItem(r1[1], Ui.accent(Material.NAME_TAG, "Liaisons",
                "bindings", null,
                "&7Éditer les 5 slots de chaque",
                "&7canal de ce personnage.",
                "&eClique pour ouvrir"));
        inv.setItem(r1[2], Ui.secondary(Material.EXPERIENCE_BOTTLE,
                "Tout apprendre", "learnall", null,
                "&7Ajoute TOUTES les techniques du",
                "&7registre à ce personnage.",
                "&eClique pour appliquer"));

        int[] r2 = GuiLayout.triple(2);
        inv.setItem(r2[0], Ui.destructive(Material.LAVA_BUCKET, "Tout oublier",
                "forgetall", null,
                "&7Retire TOUTES les techniques.",
                "&cDouble-clic pour confirmer (5s).",
                "&eClique pour lancer"));
        inv.setItem(r2[1], Ui.secondary(Material.CLOCK, "Reset cooldowns",
                "cooldowns", null,
                online ? "&7Vide les recharges + jetons d'air."
                        : "&cJoueur hors-ligne — indisponible.",
                "&eClique pour appliquer"));
        inv.setItem(r2[2], GuiIcons.info(Material.BOOK, "Infos",
                "&7Niveau " + c.level() + " — " + c.rank().displayName(),
                "&7Chakra max : " + (int) c.chakra().max(),
                "&7Mobilité débloquée : "
                        + mobility.toggles().unlockedOf(c.id()).size() + "/6 capacité(s)",
                c.dead() ? "&c☠ Personnage mort" : "&a● Vivant"));

        Ui.footer(inv, true, 0, 1);
        Ui.fillEmpty(inv);
    }

    /* -------------------------------------------------------------- clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!viewer.hasPermission("shinobiabilities.admin")) return;

        if (action.equals("manage")) {
            try {
                GuiSounds.navigate(viewer);
                openManage(viewer, UUID.fromString(value));
            } catch (IllegalArgumentException ignore) { }
            return;
        }

        UUID targetId = view.uuid(STATE_TARGET);
        ShinobiCharacter target = router.characterById(targetId);
        if (target == null) {
            GuiSounds.error(viewer);
            openCharacterList(viewer, 0);
            return;
        }

        switch (action) {
            case "techniques" -> {
                GuiSounds.navigate(viewer);
                router.openCatalogueManage(viewer, target);
            }
            case "bindings" -> {
                GuiSounds.navigate(viewer);
                router.openChannelPicker(viewer, target);
            }
            case "learnall" -> {
                int added = 0;
                for (Ability a : registry.all().values()) {
                    if (target.learnAbility(a.id())) added++;
                }
                if (added > 0) router.core().characters().save(target);
                GuiSounds.accept(viewer);
                viewer.sendMessage(Component.text(
                        added + " technique(s) apprises pour " + target.name() + ".",
                        NamedTextColor.GREEN));
                refresh(viewer, view);
            }
            case "forgetall" -> handleForgetAll(viewer, view, target);
            case "cooldowns" -> {
                UUID owner = router.ownerOf(targetId);
                Player onlineOwner = owner == null ? null : Bukkit.getPlayer(owner);
                if (onlineOwner == null) {
                    GuiSounds.error(viewer);
                    return;
                }
                cooldowns.clearAll(onlineOwner.getUniqueId());
                mobility.airTokens().refill(onlineOwner);
                GuiSounds.accept(viewer);
                viewer.sendMessage(Component.text(
                        "Cooldowns et jetons remis à zéro pour "
                                + onlineOwner.getName() + ".", NamedTextColor.GREEN));
            }
            default -> { }
        }
    }

    private void handleForgetAll(Player viewer, View view, ShinobiCharacter target) {
        PendingWipe pending = pendingWipes.get(viewer.getUniqueId());
        long now = System.currentTimeMillis();
        if (pending == null || !pending.characterId().equals(target.id())
                || now - pending.at() > CONFIRM_WINDOW_MS) {
            pendingWipes.put(viewer.getUniqueId(), new PendingWipe(target.id(), now));
            GuiSounds.decline(viewer);
            viewer.sendMessage(Component.text(
                    "Re-clique « Tout oublier » dans les 5s pour confirmer.",
                    NamedTextColor.RED));
            return;
        }
        pendingWipes.remove(viewer.getUniqueId());
        int removed = 0;
        for (Ability a : registry.all().values()) {
            if (target.forgetAbility(a.id())) removed++;
        }
        if (removed > 0) router.core().characters().save(target);
        GuiSounds.destructive(viewer);
        viewer.sendMessage(Component.text(
                removed + " technique(s) retirées à " + target.name() + ".",
                NamedTextColor.RED));
        refresh(viewer, view);
    }

    @Override
    public void onBack(Player viewer, View view) {
        if (view.uuid(STATE_TARGET) != null) {
            openCharacterList(viewer, 0);   // manage → list
        } else {
            router.openHub(viewer);          // list → hub
        }
    }
}
