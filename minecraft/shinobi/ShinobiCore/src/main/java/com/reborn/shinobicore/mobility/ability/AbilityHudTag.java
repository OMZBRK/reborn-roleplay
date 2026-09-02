package com.reborn.shinobicore.mobility.ability;

/**
 * How an ability is surfaced in the side-panel cooldown HUD.
 *
 * <p>The HUD has two sections — <b>Passifs</b> (listing currently-enabled
 * passives) and <b>Cooldowns</b> (listing abilities whose cooldown is
 * ticking). Each ability picks exactly one tag to declare how it wants to
 * show up:
 *
 * <ul>
 *   <li>{@link #PASSIVE} — appears in the Passifs section while
 *       {@link MobilityAbility#isActive isActive} returns {@code true}.
 *       When the passive is <em>off</em> but has a live cooldown (e.g.
 *       toggle just ended, recharging), it falls back to the Cooldowns
 *       section with its timer.</li>
 *   <li>{@link #COOLDOWN_SHOW} — non-passive (or "passive-but-show-the-CD")
 *       ability whose cooldown is always surfaced in the Cooldowns
 *       section while it's ticking. Hidden when the cooldown is at 0.</li>
 *   <li>{@link #COOLDOWN_HIDE} — the ability's state is tracked normally
 *       but the HUD never shows it. For abilities whose timer is
 *       uninteresting to the player (short cooldowns, implementation-
 *       detail gates like air-token refill).</li>
 * </ul>
 *
 * <p>Default for the {@link MobilityAbility} interface is
 * {@link #COOLDOWN_HIDE} — abilities opt IN to being visible.
 */
public enum AbilityHudTag {
    PASSIVE,
    COOLDOWN_SHOW,
    COOLDOWN_HIDE
}
