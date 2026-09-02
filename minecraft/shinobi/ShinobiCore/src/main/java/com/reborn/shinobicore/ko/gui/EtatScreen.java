package com.reborn.shinobicore.ko.gui;

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
import com.reborn.shinobicore.ko.injury.InjuryType;
import com.reborn.shinobicore.ko.injury.Severity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
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
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only body silhouette of a character's current injuries —
 * framework port of the legacy {@code EtatGui}, layouts and wording
 * identical.
 *
 * <p>Two render modes: {@link Mode#DETAILED} (full medical view for
 * {@code /osculter} + the KO action menu — exact type, origin, tier,
 * timestamp) and {@link Mode#COARSE} ({@code /etat} first-person view —
 * the character only <em>feels</em> their body: three sensations, a
 * binary légère/forte severity, no origin, no timestamp).
 *
 * <p>Intentionally read-only — every non-close click stays cancelled
 * by the framework default.
 */
public final class EtatScreen extends CoreScreen {

    /** Detail level for the silhouette render. */
    public enum Mode { DETAILED, COARSE }

    private enum Kind { PLAYER, DUMMY }

    static final String S_KIND      = "kind";
    static final String S_TARGET_ID = "targetId";
    static final String S_MODE      = "mode";
    static final String S_NAME      = "name";

    private static final int SLOT_CLOSE = 53;

    public EtatScreen(CoreGuiRouter router) {
        super(router);
    }

    /* ------------------------------------------------------------- open */

    /** Detailed silhouette — {@code /osculter} + the KO action menu. */
    public void open(Player viewer, UUID targetPlayerId, ShinobiCharacter target) {
        open(viewer, targetPlayerId, target, Mode.DETAILED);
    }

    public void open(Player viewer, UUID targetPlayerId,
                     ShinobiCharacter target, Mode mode) {
        String prefix = mode == Mode.COARSE ? "État" : "Examen";
        router.screens().open(viewer, this, Map.of(
                S_KIND, Kind.PLAYER,
                S_TARGET_ID, targetPlayerId,
                S_MODE, mode,
                S_NAME, prefix + " — "
                        + (target != null ? target.name() : "Inconnu")));
    }

    /** First-person coarse view of the viewer's own active character. */
    public void openSelf(Player viewer, ShinobiCharacter own) {
        open(viewer, viewer.getUniqueId(), own, Mode.COARSE);
    }

    /** Silhouette straight off a dummy's injury list — always DETAILED
     *  (inspecting a dummy is implicitly staff doing test work). */
    public void openForDummy(Player viewer, Dummy dummy) {
        router.screens().open(viewer, this, Map.of(
                S_KIND, Kind.DUMMY,
                S_TARGET_ID, dummy.id(),
                S_MODE, Mode.DETAILED,
                S_NAME, "Examen — " + (dummy != null ? dummy.name() : "Dummy")));
    }

    /* -------------------------------------------------------- rendering */

    @Override
    public Component title(Player viewer, View view) {
        return GuiTitles.framed(view.string(S_NAME));
    }

    @Override
    public int rows(View view) {
        return 6;
    }

    @Override
    public void render(Player viewer, View view, Inventory inv) {
        Mode mode = view.get(S_MODE);
        Map<BodyPart, List<Injury>> grouped = new EnumMap<>(BodyPart.class);
        boolean skipHidden = view.get(S_KIND) == Kind.PLAYER;
        for (Injury inj : liveInjuries(view)) {
            if (skipHidden && inj.hidden()) continue;
            grouped.computeIfAbsent(inj.bodyPart(), k -> new ArrayList<>()).add(inj);
        }
        for (BodyPart part : BodyPart.values()) {
            List<Injury> here = grouped.getOrDefault(part, List.of());
            inv.setItem(part.slot(), iconFor(part, here, mode));
        }
        inv.setItem(SLOT_CLOSE, Ui.action(GuiIcons.closeButton(), Ui.ACTION_CLOSE));
        Ui.fillEmpty(inv);
    }

    @Override
    public void onAction(Player viewer, View view, String action,
                         String value, InventoryClickEvent event) {
        // Read-only screen — only the reserved close action exists.
    }

    /* ----------------------------------------------------------- helpers */

    private List<Injury> liveInjuries(View view) {
        UUID targetId = view.uuid(S_TARGET_ID);
        if (view.get(S_KIND) == Kind.PLAYER) {
            ShinobiCharacter c = core().characters().getActive(targetId);
            return c == null ? List.of() : c.injuries();
        }
        for (Dummy d : core().dummies().all()) {
            if (d.id().equals(targetId)) return d.injuries();
        }
        return List.of();
    }

    private static ItemStack iconFor(BodyPart part, List<Injury> injuries, Mode mode) {
        if (injuries.isEmpty()) {
            return GuiIcons.nav(Material.STRUCTURE_VOID, part.label(),
                    mode == Mode.COARSE ? "&aAucune douleur." : "&aAucune blessure.");
        }
        // Sort worst-first so the headline injury sets the icon and
        // the rest follow in the lore.
        injuries.sort(Comparator.comparing(Injury::severity).reversed());
        Injury worst = injuries.get(0);
        Material mat = materialFor(worst.severity());

        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        if (m != null) {
            TextColor headColour = mode == Mode.COARSE
                    ? coarseColour(worst.severity())
                    : worst.severity().color();
            m.displayName(Component.text(part.label(), headColour)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm");
            for (Injury inj : injuries) {
                if (mode == Mode.COARSE) {
                    // "<sensation> <légère|forte>" — no origin, no
                    // timestamp: the player only feels their body.
                    String line = coarseSensation(inj.type())
                            + " " + coarseSeverityLabel(inj.severity());
                    lore.add(Component.text(line, coarseColour(inj.severity()))
                            .decoration(TextDecoration.ITALIC, false));
                } else {
                    lore.add(Component.text(
                            inj.severity().label() + " — " + inj.type().label(),
                            inj.severity().color())
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text(
                            "  Origine : " + inj.origin().label(),
                            NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.text(
                            "  Reçu : " + fmt.format(new Date(inj.createdAtMillis())),
                            NamedTextColor.DARK_GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    lore.add(Component.empty());
                }
            }
            // Detailed mode left a trailing blank between entries; drop it.
            if (mode == Mode.DETAILED && !lore.isEmpty()) {
                lore.remove(lore.size() - 1);
            }
            m.lore(lore);
            it.setItemMeta(m);
        }
        return it;
    }

    private static Material materialFor(Severity s) {
        return switch (s) {
            case URGENT    -> Material.RED_DYE;
            case IMPORTANT -> Material.GOLD_NUGGET;
            case MOYEN     -> Material.YELLOW_DYE;
            case FAIBLE    -> Material.GREEN_DYE;
        };
    }

    /* -------------------------------------------- coarse-mode mapping */

    private static String coarseSensation(InjuryType t) {
        return switch (t) {
            case BRULURE  -> "Brûlure";
            case HEMATOME -> "Hématome";
            default       -> "Douleur";
        };
    }

    /** {@code FAIBLE / MOYEN} read as "légère"; {@code IMPORTANT /
     *  URGENT} as "forte". */
    private static String coarseSeverityLabel(Severity s) {
        return switch (s) {
            case FAIBLE, MOYEN     -> "légère";
            case IMPORTANT, URGENT -> "forte";
        };
    }

    private static TextColor coarseColour(Severity s) {
        return switch (s) {
            case FAIBLE, MOYEN     -> NamedTextColor.YELLOW;
            case IMPORTANT, URGENT -> NamedTextColor.RED;
        };
    }
}
