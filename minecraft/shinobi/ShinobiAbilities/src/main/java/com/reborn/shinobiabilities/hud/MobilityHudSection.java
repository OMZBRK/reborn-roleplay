package com.reborn.shinobiabilities.hud;

import com.reborn.shinobiabilities.mobility.Climb;
import com.reborn.shinobiabilities.mobility.DoubleJump;
import com.reborn.shinobiabilities.mobility.FloorShockwave;
import com.reborn.shinobiabilities.mobility.MobilityModule;
import com.reborn.shinobiabilities.mobility.ShinobiDash;
import com.reborn.shinobiabilities.util.CooldownTracker;
import com.reborn.shinobicore.mobility.hud.CooldownHud;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * « Mobilité » HUD section — active passives (Course, Escalade charges)
 * plus the interesting ticking cooldowns (Dash, Onde de Choc). The
 * short DJ/WJ timers stay hidden (COOLDOWN_HIDE spirit) — they'd just
 * flicker.
 */
public final class MobilityHudSection implements CooldownHud.HudSection {

    private final MobilityModule mobility;
    private final CooldownTracker cooldowns;

    public MobilityHudSection(MobilityModule mobility, CooldownTracker cooldowns) {
        this.mobility = mobility;
        this.cooldowns = cooldowns;
    }

    @Override
    public String label() { return "Mobilité"; }

    @Override
    public NamedTextColor color() { return NamedTextColor.GOLD; }

    @Override
    public List<CooldownHud.HudRow> rows(Player viewer) {
        List<CooldownHud.HudRow> rows = new ArrayList<>();

        if (mobility.narutoRun().isActive(viewer)) {
            rows.add(new CooldownHud.HudRow("Course", "ON", NamedTextColor.GREEN));
        }
        Climb climb = mobility.climb();
        int charges = climb.charges(viewer);
        if (climb.enabled() && charges < climb.maxChargesPublic()) {
            rows.add(new CooldownHud.HudRow("Escalade",
                    charges + "/" + climb.maxChargesPublic(),
                    charges == 0 ? NamedTextColor.RED : NamedTextColor.YELLOW));
        }
        addCooldown(rows, viewer, ShinobiDash.COOLDOWN_ID, "Dash");
        addCooldown(rows, viewer, FloorShockwave.COOLDOWN_ID, "Onde de Choc");
        // DJ row only when the long-ish lockout matters (token spent).
        if (!mobility.airTokens().hasToken(viewer)
                && cooldowns.remainingMillis(viewer.getUniqueId(), DoubleJump.COOLDOWN_ID) == 0
                && !viewer.isOnGround()) {
            rows.add(new CooldownHud.HudRow("Saut", "épuisé", NamedTextColor.DARK_GRAY));
        }
        return rows;
    }

    private void addCooldown(List<CooldownHud.HudRow> rows, Player viewer,
                             String id, String label) {
        long rem = cooldowns.remainingMillis(viewer.getUniqueId(), id);
        if (rem <= 0) return;
        rows.add(new CooldownHud.HudRow(label,
                String.format("%.1fs", rem / 1000.0),
                rem > 5000 ? NamedTextColor.RED : NamedTextColor.YELLOW));
    }
}
