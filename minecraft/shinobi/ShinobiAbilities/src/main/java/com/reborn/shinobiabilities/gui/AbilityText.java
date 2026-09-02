package com.reborn.shinobiabilities.gui;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.JutsuRank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared ability presentation — lore lines and chat details, used by
 * the catalogue, the known-techniques screen and the binding editor so
 * a technique always reads the same everywhere.
 *
 * <p>Lore lines use the {@code &}-prefix encoding consumed by
 * {@code GuiIcons} / {@link com.reborn.shinobicore.gui.framework.Ui}
 * (supported codes: a e c b 6 d 7 8).
 */
public final class AbilityText {

    private AbilityText() {}

    /** Rank → supported GuiIcons colour code. */
    public static String rankCode(JutsuRank rank) {
        return switch (rank) {
            case E -> "&8";
            case D -> "&7";
            case C -> "&a";
            case B -> "&b";
            case A -> "&6";
            case HIDEN -> "&d";
        };
    }

    /**
     * Standard lore block: rank, category, cost line (castables), then
     * any trailing lines the caller appends ("&eClique pour…").
     */
    public static String[] loreOf(Ability a, String... trailing) {
        List<String> out = new ArrayList<>();
        out.add(rankCode(a.rank()) + "Rang " + a.rank().displayName());
        out.add("&7" + a.category());
        if (a.isCastable()) {
            out.add("&b" + (int) a.jutsu().chakraCost() + " chakra · "
                    + (a.jutsu().cooldownMillis() / 1000.0) + "s");
        }
        if (!a.description().isBlank()) {
            out.add("&8" + a.description());
        }
        for (String t : trailing) out.add(t);
        return out.toArray(new String[0]);
    }

    /** The uniform chat detail print (catalogue + techniques screens). */
    public static void printDetails(Player p, Ability a) {
        p.sendMessage(Component.text("— " + a.name() + " —", NamedTextColor.GOLD));
        p.sendMessage(Component.text("Rang " + a.rank().displayName()
                + " · " + a.category(), a.rank().color()));
        if (!a.description().isBlank()) {
            p.sendMessage(Component.text(a.description(), NamedTextColor.GRAY));
        }
        if (a.isCastable()) {
            p.sendMessage(Component.text(
                    "Canal : " + a.jutsu().itemType().displayName()
                            + " · " + (int) a.jutsu().chakraCost() + " chakra · "
                            + (a.jutsu().cooldownMillis() / 1000.0) + "s de recharge",
                    NamedTextColor.DARK_AQUA));
        }
    }
}
