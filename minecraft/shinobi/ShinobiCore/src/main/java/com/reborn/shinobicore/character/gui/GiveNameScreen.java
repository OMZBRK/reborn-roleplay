package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;

/**
 * Second screen of the give-name flow — real name vs nickname branch.
 * Framework port of the legacy {@code GiveNameGui}; the real-name tile
 * still shows the giver's own Name+Clan so they can confirm what they
 * are about to share.
 */
public final class GiveNameScreen extends CoreScreen {

    private static final int SLOT_REAL = 3;
    private static final int SLOT_NICK = 5;
    private static final int SLOT_BACK = 0;

    public GiveNameScreen(CoreGuiRouter router) {
        super(router);
    }

    public void open(Player viewer) {
        router.screens().open(viewer, this, Map.of());
    }

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Donner ton nom",
                core().characters().getActive(viewer.getUniqueId()));
    }

    @Override
    public int rows(View view) {
        return 1;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        ShinobiCharacter giverChar =
                core().characters().getActive(viewer.getUniqueId());
        String realName = giverChar == null
                ? viewer.getName()
                : CharacterDisplay.realNameString(giverChar);
        Ui.frame(inv);
        inv.setItem(SLOT_BACK, Ui.action(GuiIcons.backButton(), Ui.ACTION_BACK));
        inv.setItem(SLOT_REAL, Ui.action(GuiIcons.primary(Material.PLAYER_HEAD,
                "Vrai nom : " + realName,
                "&aL'autre verra ton vrai Nom + Clan",
                "en chat, /me, tab — partout."), "real"));
        inv.setItem(SLOT_NICK, Ui.action(GuiIcons.secondary(Material.NAME_TAG,
                "Donner un surnom",
                "&eElle te connaîtra uniquement sous ce surnom.",
                "Choisis-en un sauvegardé ou tape-en un nouveau."), "nick"));
        Ui.fillEmpty(inv);
    }

    @Override
    public void onBack(Player viewer, View view) {
        router.openRencontrerRoot(viewer);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        ShinobiCharacter giverChar =
                core().characters().getActive(viewer.getUniqueId());
        if (giverChar == null) { viewer.closeInventory(); return; }
        switch (action) {
            case "real" -> {
                // Real-name give: null nickname on the draft = real-name path.
                core().rencontrer().draftFor(viewer.getUniqueId()).nickname = null;
                router.openGiveTarget(viewer, null);
            }
            case "nick" -> router.openGiveNickPicker(viewer);
            default -> { }
        }
    }
}
