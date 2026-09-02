package com.reborn.shinobicore.mobility.ability;

import org.bukkit.entity.Player;

/**
 * Common shape of a mobility ability. Each implementation is responsible for
 * its own chakra/cooldown/token checks; the listener just forwards triggers.
 *
 * <p>Abilities also publish two pieces of metadata the side-panel HUD uses
 * to render itself — {@link #hudTag()} (which section, if any) and
 * {@link #displayName()} (what to label the row). Both have sensible
 * defaults so existing abilities keep compiling while the new HUD picks
 * them up.
 */
public interface MobilityAbility {

    /** Stable id (also used in cooldown keys). */
    String id();

    /** Config-backed toggle. */
    boolean isEnabled();

    /**
     * Attempt to activate the ability for {@code player}. Returns {@code true}
     * if the ability fired; {@code false} if any precondition failed (chakra,
     * cooldown, tokens, not-applicable state, etc.).
     */
    boolean tryActivate(Player player);

    /**
     * HUD-visibility classification. Defaults to {@link AbilityHudTag#COOLDOWN_HIDE}
     * so abilities opt <em>in</em> to being surfaced rather than having to
     * opt out. See the {@link AbilityHudTag} javadoc for the section each
     * tag drives.
     */
    default AbilityHudTag hudTag() { return AbilityHudTag.COOLDOWN_HIDE; }

    /**
     * True if {@code p} currently has this ability toggled <em>on</em>
     * (passives only). Non-toggle abilities default to {@code false} —
     * they're "active" only in the instantaneous tryActivate sense, which
     * isn't what the HUD wants to render.
     */
    default boolean isActive(Player p) { return false; }

    /**
     * Human-readable label for HUD rows. Override in each ability so the
     * scoreboard doesn't print raw snake-case ids. Defaults to {@link #id()}
     * to keep the interface forgiving.
     */
    default String displayName() { return id(); }
}
