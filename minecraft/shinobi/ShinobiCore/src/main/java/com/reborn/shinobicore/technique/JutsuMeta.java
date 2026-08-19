package com.reborn.shinobicore.technique;

import org.bukkit.Material;

import java.util.List;

/**
 * Castable-jutsu metadata — the optional {@code jutsu:} sub-block of an
 * ability. Abilities without one (and outside any JutsuItem category)
 * are learn-only entries (passives, tools, RP techniques).
 *
 * <h2>Effect routing</h2>
 * A cast can drive an internal effect, external commands (the
 * MagicSpells bridge), or both:
 * <ul>
 *   <li>{@code effectKey != null} → dispatched through
 *       {@code JutsuEffectRegistry};</li>
 *   <li>{@code commands} non-empty → each command is run on cast with
 *       placeholders substituted (%player%, %uuid%, %character%,
 *       %world%, %x%, %y%, %z%), as console unless
 *       {@code runAsPlayer}.</li>
 * </ul>
 * The registry guarantees at least one of the two is present.
 *
 * @param itemType       which JutsuItem binds/casts this jutsu
 * @param icon           picker / GUI icon material
 * @param method         trigger once the picker slot is selected
 * @param chakraCost     chakra consumed on a successful cast
 * @param cooldownMillis per-player cooldown
 * @param effectKey      key into {@code JutsuEffectRegistry}; nullable
 *                       when the jutsu is command-driven only
 * @param commands       commands dispatched on cast (immutable; the
 *                       {@code magicspell:} shorthand lands here too)
 * @param runAsPlayer    true → commands run as the caster, false →
 *                       console (default — forcecast needs console)
 */
public record JutsuMeta(JutsuItemType itemType,
                        Material icon,
                        ExecutionMethod method,
                        double chakraCost,
                        long cooldownMillis,
                        String effectKey,
                        List<String> commands,
                        boolean runAsPlayer) {

    public static final double DEFAULT_CHAKRA_COST = 50.0;
    public static final long DEFAULT_COOLDOWN_MILLIS = 5000L;

    public JutsuMeta {
        commands = commands == null ? List.of() : List.copyOf(commands);
    }

    /** True when this jutsu fires external commands (MagicSpells…). */
    public boolean hasCommands() { return !commands.isEmpty(); }
}
