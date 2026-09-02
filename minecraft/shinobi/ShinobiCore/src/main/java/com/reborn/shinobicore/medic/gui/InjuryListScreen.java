package com.reborn.shinobicore.medic.gui;

import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiTitles;
import com.reborn.shinobicore.gui.CoreGuiRouter;
import com.reborn.shinobicore.gui.CoreScreen;
import com.reborn.shinobicore.gui.framework.Ui;
import com.reborn.shinobicore.gui.framework.View;
import com.reborn.shinobicore.ko.injury.BodyPart;
import com.reborn.shinobicore.ko.injury.Injury;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sub-menu opened when a body part has more than one injury — the
 * medic picks exactly which wound to treat next. Worst severity first;
 * wounds in their post-treatment cooldown are tinted gray and refuse
 * clicks. Framework port of the legacy {@code BodyPartInjuryListGui};
 * the injury is re-resolved live on click.
 */
public final class InjuryListScreen extends CoreScreen {

    static final String S_PART = "part";

    private static final String ACT_INJURY = "injury";

    public InjuryListScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    public void open(Player viewer, SoignerScreen.Target target,
                     UUID targetId, BodyPart part) {
        router.screens().open(viewer, this, Map.of(
                SoignerScreen.S_TARGET, target,
                SoignerScreen.S_TARGET_ID, targetId,
                S_PART, part.name()));
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framed(part(view).label() + " — "
                + sortedInjuries(view).size() + " blessures");
    }

    @Override
    public int rows(View view) {
        // Auto-size: 1 row up to 8 wounds, 2 rows up to 17, 3 for 18+
        // (cap at 26 displayed; more means hospital, not GUI).
        int needed = sortedInjuries(view).size() + 1; // +1 close button
        return needed <= 9 ? 1 : (needed <= 18 ? 2 : 3);
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        List<Injury> sorted = sortedInjuries(view);
        int closeSlot = inv.getSize() - 1;
        int placed = 0;
        for (Injury inj : sorted) {
            int slot = placed++;
            if (slot >= closeSlot) break;
            inv.setItem(slot, Ui.action(iconFor(inj), ACT_INJURY, inj.id().toString()));
        }
        inv.setItem(closeSlot, Ui.action(GuiIcons.closeButton(), Ui.ACTION_CLOSE));
        Ui.fillEmpty(inv);
    }

    /* ------------------------------------------------------------ clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!ACT_INJURY.equals(action) || value == null) return;
        UUID injuryId;
        try { injuryId = UUID.fromString(value); }
        catch (IllegalArgumentException ex) { return; }

        SoignerScreen.Target target = view.get(SoignerScreen.S_TARGET);
        UUID targetId = view.uuid(SoignerScreen.S_TARGET_ID);
        Injury injury = null;
        for (Injury i : router.soigner().liveInjuriesOf(target, targetId)) {
            if (i.id().equals(injuryId)) { injury = i; break; }
        }
        if (injury == null) {
            viewer.sendMessage(Component.text(
                    "Cette blessure n'existe plus.", NamedTextColor.RED));
            return;
        }
        router.openTreatment(viewer, target, targetId, injury);
    }

    /* ----------------------------------------------------------- helpers */

    private BodyPart part(View view) {
        return BodyPart.valueOf(view.string(S_PART));
    }

    private List<Injury> sortedInjuries(View view) {
        SoignerScreen.Target target = view.get(SoignerScreen.S_TARGET);
        UUID targetId = view.uuid(SoignerScreen.S_TARGET_ID);
        BodyPart part = part(view);
        List<Injury> out = new ArrayList<>();
        for (Injury i : router.soigner().liveInjuriesOf(target, targetId)) {
            if (!i.hidden() && i.bodyPart() == part) out.add(i);
        }
        out.sort(Comparator.comparing(Injury::severity).reversed());
        return out;
    }

    private static ItemStack iconFor(Injury inj) {
        boolean healable = inj.isHealable();
        Material mat = healable
                ? MedicFmt.materialFor(inj.severity()) : Material.GRAY_DYE;
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;

        m.displayName(Component.text(
                inj.severity().label() + " — " + inj.type().label(),
                healable ? inj.severity().color() : NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Origine : " + inj.origin().label(),
                NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
        lore.add(Component.text("Reçu : "
                + fmt.format(new Date(inj.createdAtMillis())),
                NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        if (!healable) {
            lore.add(Component.text(
                    "En convalescence — " + MedicFmt.formatRemaining(
                            inj.cooldownRemainingMillis()),
                    NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, true));
        } else {
            lore.add(Component.text("Clique pour soigner.",
                    NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        }
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }
}
