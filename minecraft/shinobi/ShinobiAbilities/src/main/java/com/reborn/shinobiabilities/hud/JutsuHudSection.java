package com.reborn.shinobiabilities.hud;

import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobicore.mobility.hud.CooldownHud;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * « Jutsu » HUD section — the viewer's ticking jutsu cooldowns. Hidden
 * (no header) when nothing is recharging. Capped to 5 rows so the
 * sidebar never overflows its 16-line budget.
 */
public final class JutsuHudSection implements CooldownHud.HudSection {

    private static final int MAX_ROWS = 5;

    private final AbilityRegistry registry;
    private final CooldownTracker cooldowns;

    public JutsuHudSection(AbilityRegistry registry, CooldownTracker cooldowns) {
        this.registry = registry;
        this.cooldowns = cooldowns;
    }

    @Override
    public String label() { return "Jutsu"; }

    @Override
    public NamedTextColor color() { return NamedTextColor.AQUA; }

    @Override
    public List<CooldownHud.HudRow> rows(Player viewer) {
        List<CooldownTracker.Entry> active = cooldowns.active(viewer.getUniqueId());
        if (active.isEmpty()) return List.of();

        List<CooldownHud.HudRow> rows = new ArrayList<>();
        active.sort((a, b) -> Long.compare(a.remainingMillis(), b.remainingMillis()));
        for (CooldownTracker.Entry e : active) {
            Ability a = registry.byId(e.abilityId());
            if (a == null) continue;          // mobility ids live in the other section
            rows.add(new CooldownHud.HudRow(
                    shorten(a.name()),
                    String.format("%.1fs", e.remainingMillis() / 1000.0),
                    e.remainingMillis() > 3000
                            ? NamedTextColor.RED : NamedTextColor.YELLOW));
            if (rows.size() >= MAX_ROWS) break;
        }
        return rows;
    }

    /** Scoreboard lines are capped at 40 chars including prefixes —
     *  keep names tight. */
    private static String shorten(String name) {
        return name.length() <= 18 ? name : name.substring(0, 17) + "…";
    }
}
