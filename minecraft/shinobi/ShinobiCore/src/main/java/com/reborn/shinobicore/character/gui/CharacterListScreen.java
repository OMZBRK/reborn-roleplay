package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Head-per-character picker — framework port of the legacy
 * {@code CharacterListGui}, identical tiles and auto-sizing (1-6 rows,
 * header strip from 2 rows up). Two use cases via {@link Action}:
 * picking your own character to play, or staff picking which of a
 * target's characters to edit. The roster is resolved live at render.
 */
public final class CharacterListScreen extends CoreScreen {

    public enum Action { SELECT, EDIT }

    static final String S_OWNER  = "owner";
    static final String S_ACTION = "action";

    private static final String ACT_PICK = "pick";

    public CharacterListScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    public void open(Player viewer, UUID targetOwner, Action action) {
        router.screens().open(viewer, this, Map.of(
                S_OWNER, targetOwner,
                S_ACTION, action));
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return Component.text(view.get(S_ACTION) == Action.SELECT
                ? "Sélectionner un Personnage" : "Édition — Personnages");
    }

    @Override
    public int rows(View view) {
        // Every head + a header strip; cap at 6 rows (legacy sizing).
        int needed = roster(view).size() + 9;
        return Math.max(1, Math.min(6, (needed + 8) / 9));
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        Action action = view.get(S_ACTION);
        int rows = inv.getSize() / 9;
        if (rows >= 2) {
            ItemStack border = GuiIcons.border();
            for (int i = 0; i < 9; i++) inv.setItem(i, border);
        }
        int headStart = rows >= 2 ? 9 : 0;
        int placed = 0;
        for (ShinobiCharacter c : roster(view)) {
            int slot = headStart + placed;
            if (slot >= inv.getSize()) break;
            // Dead characters keep their red "Mort" tag; SELECT mode
            // still lists them so the player understands why they
            // can't be picked (the click refuses with an explanation).
            String hint;
            if (c.dead()) {
                hint = action == Action.SELECT
                        ? "&cMort — sélection impossible"
                        : "&cMort — clique pour modifier (édition staff)";
            } else {
                hint = action == Action.SELECT
                        ? "&aClique pour jouer ce personnage"
                        : "&eClique pour modifier ce personnage";
            }
            ItemStack head = GuiIcons.head(
                    Bukkit.getOfflinePlayer(c.ownerId()),
                    (c.dead() ? "✖ " : "") + c.name(),
                    "&eClan : " + c.clan(),
                    "Niv " + c.level() + "  " + c.ninjaArt().displayName(),
                    "Affinité : " + c.affinity().displayName(),
                    "Éléments Chakra : " + joinAffinities(c),
                    "&aPV : " + format(c.currentHp()) + " / " + format(c.maxHp()),
                    "&bChakra : " + format(c.chakra().current()) + " / " + format(c.chakra().max()),
                    " ",
                    hint);
            inv.setItem(slot, Ui.action(head, ACT_PICK, c.id().toString()));
            placed++;
        }
        Ui.fillEmpty(inv);
    }

    /* ------------------------------------------------------------ clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!ACT_PICK.equals(action) || value == null) return;
        UUID charId;
        try { charId = UUID.fromString(value); }
        catch (IllegalArgumentException ex) { return; }
        UUID owner = view.uuid(S_OWNER);
        ShinobiCharacter c = core().characters()
                .findById(owner, charId).orElse(null);
        if (c == null) return;

        if (view.get(S_ACTION) == Action.SELECT) {
            if (c.dead()) {
                viewer.sendMessage(Component.text(
                        "Ce personnage est mort. Demande à un staff de le ressusciter via /character edit.",
                        NamedTextColor.RED));
                return;
            }
            viewer.closeInventory();
            core().characters().setActive(viewer, c);
            viewer.sendMessage(Component.text(
                    "Tu joues maintenant " + c.name() + ".", NamedTextColor.GREEN));
        } else {
            router.openCharacterEdit(viewer, c);
        }
    }

    /* ----------------------------------------------------------- helpers */

    private List<ShinobiCharacter> roster(View view) {
        return core().characters().getAll(view.uuid(S_OWNER));
    }

    private static String format(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format("%.1f", v);
    }

    private static String joinAffinities(ShinobiCharacter c) {
        if (c.chakraAffinities().isEmpty()) return "Aucun";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (ChakraAffinity a : c.chakraAffinities()) {
            if (!first) sb.append(", ");
            sb.append(a.displayName());
            first = false;
        }
        return sb.toString();
    }
}
