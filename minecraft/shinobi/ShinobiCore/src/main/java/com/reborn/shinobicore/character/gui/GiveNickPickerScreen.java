package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;

/**
 * Nickname branch of the give-name flow — framework port of the legacy
 * {@code GiveNickPickerGui}: up to seven saved nicknames as tiles,
 * "taper un nouveau" via the chat prompt (anvil in B3), back to the
 * name branch, close abandons the draft. The type-new prompt is now
 * French, per the house rules.
 */
public final class GiveNickPickerScreen extends CoreScreen {

    private static final int SLOT_BACK  = 18;
    private static final int SLOT_TYPE  = 22;
    private static final int SLOT_CLOSE = 26;
    private static final int MAX_VISIBLE_NICKS = 7;

    public GiveNickPickerScreen(CoreGuiRouter router) {
        super(router);
    }

    public void open(Player viewer) {
        router.screens().open(viewer, this, Map.of());
    }

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Choisir un surnom",
                core().characters().getActive(viewer.getUniqueId()));
    }

    @Override
    public int rows(View view) {
        return 3;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        ShinobiCharacter giverChar =
                core().characters().getActive(viewer.getUniqueId());
        Ui.frame(inv);
        List<String> saved = giverChar == null ? List.of() : giverChar.savedNicknames();
        int max = Math.min(saved.size(), MAX_VISIBLE_NICKS);
        for (int i = 0; i < max; i++) {
            String nick = saved.get(i);
            inv.setItem(10 + i, Ui.action(GuiIcons.secondary(Material.PAPER, nick,
                    "&eCliquer pour donner ce surnom",
                    "à ta prochaine cible."), "pick", nick));
        }
        inv.setItem(SLOT_BACK, Ui.action(GuiIcons.backButton(), Ui.ACTION_BACK));
        inv.setItem(SLOT_TYPE, Ui.action(GuiIcons.primary(Material.WRITABLE_BOOK,
                "Taper un nouveau surnom",
                "&aFerme le menu et te demande",
                "de taper le surnom dans le chat."), "type"));
        inv.setItem(SLOT_CLOSE, Ui.action(GuiIcons.closeButton(), "abort"));
        Ui.fillEmpty(inv);
    }

    @Override
    public void onBack(Player viewer, View view) {
        router.openGiveName(viewer);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        ShinobiCharacter giverChar =
                core().characters().getActive(viewer.getUniqueId());
        if (giverChar == null) { viewer.closeInventory(); return; }
        switch (action) {
            case "abort" -> {
                viewer.closeInventory();
                core().rencontrer().clearDraft(viewer.getUniqueId());
            }
            case "type" -> core().chatInputs().prompt(viewer,
                    "Tape le surnom que tu veux donner (ou 'cancel').",
                    raw -> {
                        String nick = raw.trim();
                        if (nick.isEmpty()) {
                            RencontrerRootScreen.msg(viewer,
                                    "Le surnom ne peut pas être vide.", NamedTextColor.RED);
                            router.openGiveNickPicker(viewer);
                            return;
                        }
                        core().rencontrer().draftFor(viewer.getUniqueId()).nickname = nick;
                        router.openGiveTarget(viewer, nick);
                    },
                    () -> router.openGiveNickPicker(viewer));
            case "pick" -> {
                if (value == null) return;
                // Saved-pool pick: stash and jump forward. No save-prompt
                // fires for already-saved nicknames.
                core().rencontrer().draftFor(viewer.getUniqueId()).nickname = value;
                router.openGiveTarget(viewer, value);
            }
            default -> { }
        }
    }
}
