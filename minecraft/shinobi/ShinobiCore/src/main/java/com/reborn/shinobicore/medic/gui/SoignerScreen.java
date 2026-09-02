package com.reborn.shinobicore.medic.gui;

import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.character.gui.GuiIcons;
import com.reborn.shinobicore.character.gui.GuiTitles;
import com.reborn.shinobicore.dummy.Dummy;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Body silhouette opened by {@code /soigner} — same 16-slot layout as
 * the KO état view, but every body part with at least one injury is
 * clickable: single wound routes straight to the treatment screen,
 * several open the per-part list. Framework port of the legacy
 * {@code SoignerGui}; injuries are re-fetched live at click time so a
 * click never acts on a stale snapshot.
 */
public final class SoignerScreen extends CoreScreen {

    /** Who is being healed — a real character or a training dummy. */
    public enum Target { PLAYER, DUMMY }

    static final String S_TARGET    = "target";
    static final String S_TARGET_ID = "targetId";
    static final String S_NAME      = "name";

    private static final int SLOT_CLOSE = 53;
    private static final String ACT_PART = "part";

    public SoignerScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    public void openForPlayer(Player viewer, UUID targetPlayerId,
                              ShinobiCharacter target) {
        router.screens().open(viewer, this, Map.of(
                S_TARGET, Target.PLAYER,
                S_TARGET_ID, targetPlayerId,
                S_NAME, target != null ? target.name() : "Inconnu"));
    }

    public void openForDummy(Player viewer, Dummy dummy) {
        router.screens().open(viewer, this, Map.of(
                S_TARGET, Target.DUMMY,
                S_TARGET_ID, dummy.id(),
                S_NAME, dummy.name()));
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framed("Soigner — " + view.string(S_NAME));
    }

    @Override
    public int rows(View view) {
        return 6;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        Map<BodyPart, List<Injury>> grouped = new EnumMap<>(BodyPart.class);
        for (Injury inj : liveInjuries(view)) {
            if (inj.hidden()) continue;
            grouped.computeIfAbsent(inj.bodyPart(), k -> new ArrayList<>()).add(inj);
        }
        for (BodyPart part : BodyPart.values()) {
            List<Injury> here = grouped.getOrDefault(part, List.of());
            ItemStack icon = iconFor(part, here);
            if (!here.isEmpty()) icon = Ui.action(icon, ACT_PART, part.name());
            inv.setItem(part.slot(), icon);
        }
        inv.setItem(SLOT_CLOSE, Ui.action(GuiIcons.closeButton(), Ui.ACTION_CLOSE));
        Ui.fillEmpty(inv);
    }

    /* ------------------------------------------------------------ clicks */

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        if (!ACT_PART.equals(action) || value == null) return;
        BodyPart part;
        try { part = BodyPart.valueOf(value); }
        catch (IllegalArgumentException ex) { return; }

        // Live re-fetch — never act on the render-time snapshot.
        List<Injury> onPart = new ArrayList<>();
        for (Injury i : liveInjuries(view)) {
            if (!i.hidden() && i.bodyPart() == part) onPart.add(i);
        }
        if (onPart.isEmpty()) return;

        Target target = view.get(S_TARGET);
        UUID targetId = view.uuid(S_TARGET_ID);
        if (onPart.size() == 1) {
            router.openTreatment(viewer, target, targetId, onPart.get(0));
        } else {
            router.openInjuryList(viewer, target, targetId, part);
        }
    }

    /* ----------------------------------------------------------- helpers */

    private List<Injury> liveInjuries(View view) {
        return liveInjuriesOf(view.get(S_TARGET), view.uuid(S_TARGET_ID));
    }

    /** Live injuries of the target (character active on the player, or
     *  the dummy's list). Empty when the target vanished. Shared with
     *  the injury-list and treatment screens via the router. */
    public List<Injury> liveInjuriesOf(Target target, UUID targetId) {
        if (target == Target.PLAYER) {
            ShinobiCharacter c = core().characters().getActive(targetId);
            return c == null ? List.of() : c.injuries();
        }
        for (Dummy d : core().dummies().all()) {
            if (d.id().equals(targetId)) return d.injuries();
        }
        return List.of();
    }

    private static ItemStack iconFor(BodyPart part, List<Injury> injuries) {
        if (injuries.isEmpty()) {
            return GuiIcons.nav(Material.STRUCTURE_VOID, part.label(),
                    "&aRien à soigner ici.");
        }
        injuries.sort(Comparator.comparing(Injury::severity).reversed());
        Injury worst = injuries.get(0);

        boolean healable = worst.isHealable();
        // When the wound is in its post-treatment recovery window,
        // swap the icon for a grey marker so the silhouette reads
        // "treated, resting" rather than "treat me again".
        Material mat = healable
                ? MedicFmt.materialFor(worst.severity())
                : Material.GRAY_DYE;

        // Stack amount = injury count, capped at 64. The number badge
        // on the icon makes "Pied droit (×6)" visible at a glance
        // without taking up lore real estate.
        ItemStack it = new ItemStack(mat, Math.min(64, injuries.size()));
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            String title = injuries.size() > 1
                    ? part.label() + "  (×" + injuries.size() + ")"
                    : part.label();
            m.displayName(Component.text(title,
                    healable ? worst.severity().color() : NamedTextColor.GRAY)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            // Severity histogram so the medic sees what they're up
            // against without opening the sub-list.
            int urgent = 0, important = 0, moyen = 0, faible = 0;
            for (Injury inj : injuries) switch (inj.severity()) {
                case URGENT    -> urgent++;
                case IMPORTANT -> important++;
                case MOYEN     -> moyen++;
                case FAIBLE    -> faible++;
            }
            if (injuries.size() == 1) {
                lore.add(Component.text(
                        worst.severity().label() + " — " + worst.type().label(),
                        worst.severity().color())
                        .decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("Origine : " + worst.origin().label(),
                        NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                StringBuilder breakdown = new StringBuilder();
                if (urgent    > 0) breakdown.append("Urgent ×").append(urgent).append("  ");
                if (important > 0) breakdown.append("Important ×").append(important).append("  ");
                if (moyen     > 0) breakdown.append("Moyen ×").append(moyen).append("  ");
                if (faible    > 0) breakdown.append("Faible ×").append(faible);
                lore.add(Component.text(breakdown.toString().trim(),
                        NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false));
            }
            lore.add(Component.empty());
            if (!healable) {
                lore.add(Component.text(
                        "En convalescence — " + MedicFmt.formatRemaining(
                                worst.cooldownRemainingMillis()),
                        NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, true));
            } else if (injuries.size() == 1) {
                lore.add(Component.text("Clique pour soigner.",
                        NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text(
                        "Clique pour voir les " + injuries.size() + " blessures.",
                        NamedTextColor.GREEN)
                        .decoration(TextDecoration.ITALIC, false));
            }
            m.lore(lore);
            it.setItemMeta(m);
        }
        return it;
    }
}
