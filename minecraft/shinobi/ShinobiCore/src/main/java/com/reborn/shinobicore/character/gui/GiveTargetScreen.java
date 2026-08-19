package com.reborn.shinobicore.character.gui;

import com.reborn.shinobicore.character.RencontrerManager;
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

import java.util.HashMap;
import java.util.Map;

/**
 * Target picker of the give-name flow — framework port of the legacy
 * {@code GiveTargetGui}: reveal to the ray-traced player in front, or
 * to everyone within 10 blocks. The title still shows what's staged
 * ("vrai nom" or the pending nickname); the reveal/notification
 * strings are now French, per the house rules.
 */
public final class GiveTargetScreen extends CoreScreen {

    static final String S_NICK = "nick"; // null = real-name reveal

    private static final int SLOT_FRONT  = 3;
    private static final int SLOT_AROUND = 5;
    private static final int SLOT_BACK   = 0;
    private static final int SLOT_CLOSE  = 8;

    public GiveTargetScreen(CoreGuiRouter router) {
        super(router);
    }

    public void open(Player viewer, String pendingNickname) {
        Map<String, Object> state = new HashMap<>();
        if (pendingNickname != null) state.put(S_NICK, pendingNickname);
        router.screens().open(viewer, this, state);
    }

    @Override
    public Component title(Player viewer, View view) {
        String nick = view.string(S_NICK);
        String subtitle = nick == null ? "vrai nom" : "\"" + nick + "\"";
        return GuiTitles.framedWithCharacter("Révéler comme " + subtitle,
                core().characters().getActive(viewer.getUniqueId()));
    }

    @Override
    public int rows(View view) {
        return 1;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        Ui.frame(inv);
        inv.setItem(SLOT_BACK, Ui.action(GuiIcons.backButton(), Ui.ACTION_BACK));
        inv.setItem(SLOT_FRONT, Ui.action(GuiIcons.info(Material.SPYGLASS,
                "Joueur en face de toi",
                "&eCible jusqu'à 10 blocs depuis ton œil.",
                "Seul le joueur visé est révélé."), "front"));
        inv.setItem(SLOT_AROUND, Ui.action(GuiIcons.coloured(Material.ENDER_EYE,
                "Personnes autour de toi", NamedTextColor.LIGHT_PURPLE,
                "&dRévèle à tous les joueurs dans un rayon de 10 blocs.",
                "Idéal pour une présentation de groupe."), "around"));
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

        RencontrerManager.GiveDraft draft =
                core().rencontrer().draftFor(viewer.getUniqueId());
        String pendingNick = draft.nickname;
        RencontrerRootScreen root = router.rencontrerRoot();

        switch (action) {
            case "front" -> {
                Player hit = root.rayTracePlayer(viewer, 10.0);
                if (hit == null) {
                    viewer.closeInventory();
                    RencontrerRootScreen.msg(viewer,
                            "Personne en face de toi (à moins de 10 blocs).",
                            NamedTextColor.RED);
                    GuiSounds.error(viewer);
                    core().rencontrer().clearDraft(viewer.getUniqueId());
                    return;
                }
                root.revealGive(viewer, giverChar, hit, pendingNick);
                viewer.closeInventory();
                GuiSounds.accept(viewer);
                core().rencontrer().clearDraft(viewer.getUniqueId());
            }
            case "around" -> {
                int count = root.revealAround(viewer, giverChar, pendingNick, 10.0);
                viewer.closeInventory();
                if (count == 0) {
                    RencontrerRootScreen.msg(viewer,
                            "Personne à moins de 10 blocs à qui te présenter.",
                            NamedTextColor.YELLOW);
                    GuiSounds.error(viewer);
                } else {
                    RencontrerRootScreen.msg(viewer,
                            "Identité révélée à " + count + " joueur(s).",
                            NamedTextColor.GREEN);
                    GuiSounds.accept(viewer);
                }
                core().rencontrer().clearDraft(viewer.getUniqueId());
            }
            case "abort" -> {
                viewer.closeInventory();
                core().rencontrer().clearDraft(viewer.getUniqueId());
            }
            default -> { }
        }
    }
}
