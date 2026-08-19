package com.reborn.shinobiabilities.gui;

import com.reborn.shinobicore.gui.framework.Screen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobicore.technique.JutsuItemType;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiLayout;
import com.reborn.shinobicore.character.gui.GuiSounds;
import com.reborn.shinobicore.character.gui.GuiTitles;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;

/**
 * Channel picker — choose which JutsuItem's five slots to edit. Serves
 * both the player path (hub → « Mes Liaisons ») and the admin path
 * (admin panel → « Liaisons » of any character).
 */
public final class ChannelPickerGui extends AbilityScreen {

    private static final String STATE_TARGET = "target";

    public ChannelPickerGui(GuiRouter router) {
        super(router);
    }

    public void open(Player p, ShinobiCharacter target) {
        router.screens().open(p, this, Map.of(STATE_TARGET, target.id()));
    }

    /* ------------------------------------------------------------- screen */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framedWithCharacter("Liaisons",
                router.characterById(view.uuid(STATE_TARGET)));
    }

    @Override
    public int rows(View view) { return 4; }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        Ui.frame(inv);
        JutsuItemType[] types = JutsuItemType.values();
        int[] r1 = GuiLayout.quad(1);
        int[] r2 = GuiLayout.triple(2);
        for (int i = 0; i < types.length; i++) {
            int slot = i < 4 ? r1[i] : r2[i - 4];
            inv.setItem(slot, Ui.accent(types[i].material(), types[i].displayName(),
                    "channel", types[i].name(),
                    "&75 slots de jutsu liables.",
                    "&eClique pour éditer ce canal"));
        }
        Ui.footer(inv, true, 0, 1);
        Ui.fillEmpty(inv);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!action.equals("channel")) return;
        JutsuItemType type = JutsuItemType.from(value);
        if (type == null) return;
        ShinobiCharacter target = router.characterById(view.uuid(STATE_TARGET));
        if (target == null) { viewer.closeInventory(); return; }
        GuiSounds.navigate(viewer);
        router.openBindingEditor(viewer, type, target);
    }

    @Override
    public void onBack(Player viewer, View view) {
        UUID target = view.uuid(STATE_TARGET);
        ShinobiCharacter own = com.reborn.shinobicore.util.Players
                .active(router.core().characters(), viewer);
        if (own != null && !own.id().equals(target)) {
            router.openAdminManage(viewer, target);   // admin path
        } else {
            router.openHub(viewer);
        }
    }
}
