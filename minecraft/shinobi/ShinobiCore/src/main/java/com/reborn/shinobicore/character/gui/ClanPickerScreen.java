package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.Clan;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Clan picker — framework port of the legacy {@code ClanPickerGui}:
 * canonical clans on the same symmetric layout, an "Autre..." chat
 * fall-through for staff-approved custom clans, and the close tile
 * returning to the editor (back semantics, as before). The custom-clan
 * prompt is now French, per the house rules.
 */
public final class ClanPickerScreen extends CoreScreen {

    private static final int[] CLAN_LAYOUT = {
            10, 11, 12, 13, 14, 15, 16, 17,   // row 1: cols 1-8
            19, 21, 23                        // row 2: cols 1, 3, 5
    };
    private static final int SLOT_OTHER = 24;
    private static final int SLOT_BACK  = 26;

    private static final String ACT_CLAN  = "clan";
    private static final String ACT_OTHER = "other";

    public ClanPickerScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    public void open(Player viewer, ShinobiCharacter c) {
        router.screens().open(viewer, this, Map.of(
                CharacterEditScreen.S_OWNER, c.ownerId(),
                CharacterEditScreen.S_CHAR_ID, c.id()));
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Choisir un Clan", character(view));
    }

    @Override
    public int rows(View view) {
        return 3;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        ShinobiCharacter c = character(view);
        if (c == null) return;
        for (int i = 0; i < 9; i++) inv.setItem(i, GuiIcons.border());

        Clan current = Clan.forName(c.clan());
        Clan[] clans = Clan.values();
        for (int i = 0; i < clans.length && i < CLAN_LAYOUT.length; i++) {
            inv.setItem(CLAN_LAYOUT[i], Ui.action(
                    clanIcon(clans[i], clans[i] == current),
                    ACT_CLAN, clans[i].name()));
        }
        inv.setItem(SLOT_OTHER, Ui.action(GuiIcons.secondary(
                Material.NAME_TAG,
                "Autre...",
                "Tape un nom de clan personnalisé dans le chat.",
                "Pour les clans mineurs validés par le staff",
                "qui ne sont pas dans la liste principale."), ACT_OTHER));
        // Close tile keeps its legacy back-to-editor semantics.
        inv.setItem(SLOT_BACK, Ui.action(GuiIcons.closeButton(), Ui.ACTION_BACK));
        Ui.fillEmpty(inv);
    }

    /* ------------------------------------------------------------ clicks */

    @Override
    public void onBack(Player viewer, View view) {
        ShinobiCharacter c = character(view);
        if (c != null) router.openCharacterEdit(viewer, c);
        else viewer.closeInventory();
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        ShinobiCharacter c = character(view);
        if (c == null) { viewer.closeInventory(); return; }
        var mgr = core().characters();

        if (ACT_OTHER.equals(action)) {
            // Chat fall-through for staff-typed minor clans (Kaguya,
            // Yuki, …); canonical typos are normalised to the enum's
            // display name so the save file stays consistent.
            router.characterEdit().askText(viewer, c,
                    "Tape le nom de clan personnalisé dans le chat (ou 'cancel' pour annuler) :",
                    input -> {
                        String v = input.trim();
                        if (v.isEmpty()) {
                            viewer.sendMessage(Component.text(
                                    "Le clan ne peut pas être vide.", NamedTextColor.RED));
                            return;
                        }
                        Clan canon = Clan.forName(v);
                        if (canon != null) v = canon.displayName();
                        c.setClan(v); mgr.save(c);
                        router.characterEdit().refreshNickname(c);
                        viewer.sendMessage(Component.text(
                                "Clan défini : " + v, NamedTextColor.GREEN));
                    });
            return;
        }
        if (ACT_CLAN.equals(action) && value != null) {
            Clan picked;
            try { picked = Clan.valueOf(value); }
            catch (IllegalArgumentException ex) { return; }
            c.setClan(picked.displayName());
            mgr.save(c);
            router.characterEdit().refreshNickname(c);
            viewer.sendMessage(Component.text(
                    "Clan défini : " + picked.displayName(), NamedTextColor.GREEN));
            router.openCharacterEdit(viewer, c);
        }
    }

    /* ----------------------------------------------------------- helpers */

    private ShinobiCharacter character(View view) {
        return core().characters().findById(
                view.uuid(CharacterEditScreen.S_OWNER),
                view.uuid(CharacterEditScreen.S_CHAR_ID)).orElse(null);
    }

    private static ItemStack clanIcon(Clan clan, boolean selected) {
        ItemStack it = new ItemStack(clan.icon());
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            m.displayName(Component.text(clan.displayName(), clan.colour())
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(clan.flavour(), NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            if (selected) {
                lore.add(Component.text("✔ Sélection actuelle", NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("Cliquer pour choisir", NamedTextColor.YELLOW)
                        .decoration(TextDecoration.ITALIC, false));
            }
            m.lore(lore);
            it.setItemMeta(m);
        }
        return it;
    }
}
