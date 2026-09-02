package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Spawns a short-lived floating {@link TextDisplay} above a player's head
 * and teleports it each tick so it tracks their position until the timer
 * expires.
 *
 * <p>Used by {@code /me} so bystanders can see the roleplay narration
 * visually — above the acting player — without having to scan the chat
 * log. Pairs with the broadcast line; the bubble is the visual cue, the
 * chat line is the searchable record.
 *
 * <h2>Per-viewer groups</h2>
 * Because the server is an RP environment and characters default to
 * {@code ????} until introduced, the bubble text depends on <em>who is
 * looking</em>. {@link #showPerViewer} spawns one TextDisplay per
 * {@link ViewerGroup} and uses {@link Player#hideEntity/showEntity} to
 * restrict visibility — two strangers share a single ???? bubble, while
 * someone who knows the sender's real name sees a separate one with the
 * real label. The legacy {@link #show} entry is kept for any caller that
 * wants a single label for everyone (e.g. console narration tooling).
 *
 * <h2>Duration model</h2>
 * The bubble stays up for a floor of {@value #MIN_TICKS} ticks plus
 * {@value #TICKS_PER_CHAR} ticks per character of action text, capped at
 * {@value #MAX_TICKS} ticks so even very long narrations don't stick
 * around forever.
 *
 * <h2>Placement</h2>
 * The display spawns at the top of the player's bounding box plus a small
 * upward pad, so it sits above the vanilla nameplate regardless of the
 * character's {@code size} attribute.
 */
public final class CharacterEmote {

    private CharacterEmote() {}

    /** Minimum ticks a /me bubble stays up, regardless of length. */
    private static final long MIN_TICKS = 40L;        // 2.0 s
    /** Extra ticks added per character of narration. */
    private static final long TICKS_PER_CHAR = 3L;    // ~0.15 s per char
    /** Safety cap so pasted walls-of-text eventually clear. */
    private static final long MAX_TICKS = 240L;       // 12 s

    /** Vertical pad above the player's bounding box top. */
    private static final double Y_PAD = 0.35;

    /** One bubble + the viewers allowed to see it.
     *  The {@code viewers} list is mutable so callers can populate it
     *  incrementally while grouping by label. */
    public static final class ViewerGroup {
        public final Component text;
        public final List<Player> viewers = new ArrayList<>();
        public ViewerGroup(Component text) { this.text = text; }
    }

    /* --------------------------------------------------------- single-label */

    /**
     * Spawn a floating bubble above {@code target} showing {@code text}
     * for <em>everyone</em>. Retained for callers that don't need
     * per-viewer masking (console narration, legacy paths).
     */
    public static void show(ShinobiCore plugin, Player target,
                            Component text, int charCount) {
        if (target == null || !target.isOnline() || target.getWorld() == null) return;
        ViewerGroup everyone = new ViewerGroup(text);
        everyone.viewers.addAll(Bukkit.getOnlinePlayers());
        showPerViewer(plugin, target, List.of(everyone), charCount);
    }

    /* --------------------------------------------------------- per-viewer */

    /**
     * Spawn one {@link TextDisplay} per {@link ViewerGroup} and restrict
     * each display's visibility to that group's viewer list.
     *
     * <p>Safe to call from the main thread only — spawns + teleports
     * Bukkit entities.
     *
     * @param target     the player the bubble follows
     * @param groups     non-null, may be empty; each group gets its own entity
     * @param charCount  used to scale the bubble duration
     */
    public static void showPerViewer(ShinobiCore plugin, Player target,
                                     Collection<ViewerGroup> groups, int charCount) {
        if (target == null || !target.isOnline() || target.getWorld() == null) return;
        if (groups == null || groups.isEmpty()) return;

        final long duration = Math.min(MAX_TICKS,
                MIN_TICKS + TICKS_PER_CHAR * Math.max(0, charCount));
        final Location spawn = anchor(target);
        final List<TextDisplay> displays = new ArrayList<>(groups.size());

        for (ViewerGroup g : groups) {
            if (g == null || g.text == null) continue;
            TextDisplay display = target.getWorld().spawn(spawn, TextDisplay.class, td -> {
                td.text(g.text);
                td.setBillboard(Display.Billboard.CENTER);
                td.setSeeThrough(true);
                td.setDefaultBackground(true);
                td.setPersistent(false);
            });

            // Default-hide from every online player, then reveal to the
            // selected viewers. We intentionally hide from everyone first
            // (rather than only hiding the non-viewers) so a late-joining
            // player can't briefly see a bubble meant for someone else.
            for (Player other : Bukkit.getOnlinePlayers()) {
                try { other.hideEntity(plugin, display); } catch (Throwable ignore) {}
            }
            for (Player viewer : g.viewers) {
                if (viewer == null || !viewer.isOnline()) continue;
                try { viewer.showEntity(plugin, display); } catch (Throwable ignore) {}
            }
            displays.add(display);
        }

        // Shared tick follower — teleports every live bubble to the actor's
        // current head position. One task regardless of group count keeps
        // the overhead flat.
        new BukkitRunnable() {
            long ticks = 0L;
            @Override
            public void run() {
                boolean anyAlive = false;
                for (TextDisplay d : displays) {
                    if (d != null && !d.isDead()) { anyAlive = true; break; }
                }
                if (!target.isOnline() || !anyAlive || ticks >= duration) {
                    for (TextDisplay d : displays) {
                        if (d != null && !d.isDead()) d.remove();
                    }
                    cancel();
                    return;
                }
                // Under lag, skip the cosmetic teleport (bubble lags a frame)
                // but keep counting toward the lifespan + cleanup above.
                if (com.reborn.shinobicore.util.Tps.shouldDefer()) { ticks++; return; }
                Location loc = anchor(target);
                for (TextDisplay d : displays) {
                    if (d != null && !d.isDead()) d.teleport(loc);
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Compute the bubble anchor — above the bounding-box top, so the
     *  placement stays correct as the player crouches, sprints, or
     *  changes character scale. */
    private static Location anchor(Player p) {
        double topY = p.getBoundingBox().getMaxY();
        Location base = p.getLocation();
        return new Location(p.getWorld(),
                base.getX(), topY + Y_PAD, base.getZ(),
                base.getYaw(), base.getPitch());
    }
}
