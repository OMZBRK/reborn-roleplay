package com.reborn.shinobiabilities.mobility.training;

import com.reborn.shinobiabilities.mobility.ToggleStore;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Players;
import com.reborn.shinobicore.util.Tps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Runs a training parkour: the runner is teleported to the start, and at every
 * anchor a reaction mini-game is played — a cursor sweeps an action-bar timing
 * bar and the player presses the anchor's key (sneak / space / left-click) when
 * the cursor is over the success window. A good hit launches a full parabolic
 * leap to the next anchor; a miss or a time-out launches a too-weak leap that
 * falls short, ending the run. Clearing the final anchor unlocks the parkour's
 * reward abilities.
 *
 * <p>While a run is active the player's normal mobility stands down — see the
 * {@code isRunning} hooks in {@code MobilityListener}.
 */
public final class ParkourRunner {

    private static final int BAR_CELLS = 11;
    private static final int ZONE_WIDTH = 3;

    private enum Phase { REACTION, LEAP }

    private static final class Run {
        Parkour pk;
        int fromIndex;        // anchor we're ON (reaction) / leaping FROM
        Phase phase;
        int reactionTick;     // ticks since this reaction began
        int zoneStart;        // first cell of the success window
        int phaseTick;        // tick within the current leap
        int leapTicks;        // total ticks for the current leap
        Vector start, end;    // leap endpoints
        boolean weak;         // failed reaction → undershoot leap → fall
        int ticks;            // total run ticks — safety cap
    }

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final ToggleStore unlocks;
    private final ParkourManager manager;
    private final Map<UUID, Run> runs = new HashMap<>();
    private BukkitTask task;

    public ParkourRunner(JavaPlugin plugin, CoreServices core,
                         ToggleStore unlocks, ParkourManager manager) {
        this.plugin = plugin;
        this.core = core;
        this.unlocks = unlocks;
        this.manager = manager;
    }

    public boolean isRunning(Player p) { return p != null && runs.containsKey(p.getUniqueId()); }

    public ParkourManager manager() { return manager; }

    /* ------------------------------------------------------------- start/stop */

    public boolean start(Player p, Parkour pk) {
        if (pk == null || !pk.runnable() || isRunning(p)) return false;
        World w = plugin.getServer().getWorld(pk.world());
        if (w == null) return false;
        Run r = new Run();
        r.pk = pk;
        r.fromIndex = 0;
        runs.put(p.getUniqueId(), r);

        Location startLoc = pk.anchors().get(0).standLocation(w);
        startLoc.setYaw(p.getLocation().getYaw());
        startLoc.setPitch(p.getLocation().getPitch());
        p.teleport(startLoc);
        p.setAllowFlight(false);   // so space = jump (PlayerJumpEvent), not flight toggle

        beginReaction(p, r);
        ensureTask();
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
        p.sendMessage(Component.text("Entraînement « " + pk.name()
                + " » — réagis au bon moment !", NamedTextColor.AQUA));
        return true;
    }

    public void stop(Player p, boolean feedback) {
        Run r = runs.remove(p.getUniqueId());
        if (r == null) return;
        p.setFallDistance(0f);
        if (feedback && p.isOnline()) {
            p.sendActionBar(Component.text("Entraînement abandonné.", NamedTextColor.GRAY));
        }
    }

    public void stopAll() {
        for (UUID id : runs.keySet().toArray(new UUID[0])) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) stop(p, false); else runs.remove(id);
        }
        if (task != null) { task.cancel(); task = null; }
    }

    /* -------------------------------------------------------------- reaction */

    private void beginReaction(Player p, Run r) {
        ParkourAnchor a = r.pk.anchors().get(r.fromIndex);
        r.phase = Phase.REACTION;
        r.reactionTick = 0;
        r.zoneStart = zoneStart(a.zone());
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0f);
        p.setAllowFlight(false);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.5f);
    }

    private int zoneStart(ParkourAnchor.Zone zone) {
        return switch (zone) {
            case LEFT -> 0;
            case MIDDLE -> (BAR_CELLS - ZONE_WIDTH) / 2;
            case RIGHT -> BAR_CELLS - ZONE_WIDTH;
            case RANDOM -> ThreadLocalRandom.current().nextInt(BAR_CELLS - ZONE_WIDTH + 1);
        };
    }

    private int cursorAt(Run r, ParkourAnchor a) {
        int period = Math.max(4, a.loopTicks());
        int within = r.reactionTick % period;
        return Math.min(BAR_CELLS - 1, within * BAR_CELLS / period);
    }

    /** A press of {@code key} while running. Returns true when consumed (the
     *  listener then cancels the vanilla effect). */
    public boolean onPress(Player p, ParkourAnchor.Key key) {
        Run r = runs.get(p.getUniqueId());
        if (r == null) return false;
        if (r.phase != Phase.REACTION) return true;     // mid-leap: swallow input
        ParkourAnchor a = r.pk.anchors().get(r.fromIndex);
        if (key != a.key()) return true;                 // wrong key: swallow, no effect
        int cursor = cursorAt(r, a);
        boolean hit = cursor >= r.zoneStart && cursor < r.zoneStart + ZONE_WIDTH;
        if (hit) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
            startLeap(p, r, false);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 0.8f);
            startLeap(p, r, true);
        }
        return true;
    }

    /* ------------------------------------------------------------------ leap */

    private void startLeap(Player p, Run r, boolean weak) {
        ParkourAnchor from = r.pk.anchors().get(r.fromIndex);
        ParkourAnchor to = r.pk.anchors().get(r.fromIndex + 1);
        r.start = from.toVector();
        r.end = to.toVector();
        r.weak = weak;
        r.phase = Phase.LEAP;
        r.phaseTick = 0;
        double horiz = Math.hypot(r.end.getX() - r.start.getX(), r.end.getZ() - r.start.getZ());
        double leapSpeed = Math.max(0.1,
                plugin.getConfig().getDouble("mobility.training.leap-speed", 0.7));
        r.leapTicks = Math.max(6, Math.min(80, (int) Math.ceil(horiz / leapSpeed)));
        p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 0.7f, weak ? 1.0f : 1.6f);
    }

    private void tickLeap(Player p, Run r) {
        double leapHeight = plugin.getConfig().getDouble("mobility.training.leap-height", 4.5);
        if (r.weak) {
            // Undershoot: drive a low arc only part-way, then release → the
            // runner falls short and drops off. Failure.
            double weakFrac = plugin.getConfig()
                    .getDouble("mobility.training.weak-fraction", 0.55);
            int weakTicks = Math.max(3, (int) (r.leapTicks * weakFrac));
            if (r.phaseTick < weakTicks) {
                double t = Math.min(1.0, (r.phaseTick + 1.0) / r.leapTicks);
                Vector arc = arcPoint(r.start, r.end, t, leapHeight * 0.6);
                p.setVelocity(arc.subtract(p.getLocation().toVector()));
                p.setFallDistance(0f);
                r.phaseTick++;
            } else {
                fail(p);          // let physics drop them
            }
            return;
        }
        // Strong: full parabola onto the next anchor.
        double t = Math.min(1.0, (r.phaseTick + 1.0) / r.leapTicks);
        Vector arc = arcPoint(r.start, r.end, t, leapHeight);
        p.setVelocity(arc.subtract(p.getLocation().toVector()));
        p.setFallDistance(0f);
        if (!Tps.shouldDefer()) {
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 2, 0.1, 0.05, 0.1, 0.01);
        }
        if (++r.phaseTick >= r.leapTicks) land(p, r);
    }

    private void land(Player p, Run r) {
        r.fromIndex++;
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0f);
        if (r.fromIndex >= r.pk.size() - 1) complete(p, r);   // reached the final anchor
        else beginReaction(p, r);
    }

    private void complete(Player p, Run r) {
        ShinobiCharacter c = Players.active(core.characters(), p);
        int granted = (c != null) ? unlocks.unlockAll(c.id(), r.pk.rewards()) : 0;
        runs.remove(p.getUniqueId());
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0f);
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        p.sendMessage(Component.text("Parcours « " + r.pk.name() + " » réussi ! "
                + (granted > 0 ? granted + " capacité(s) débloquée(s)."
                              : "(rien de nouveau à débloquer)"),
                NamedTextColor.GREEN));
    }

    private void fail(Player p) {
        runs.remove(p.getUniqueId());                 // leave velocity → they fall
        p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_HURT, 0.8f, 0.7f);
        p.sendActionBar(Component.text("Raté ! Recommence le parcours.", NamedTextColor.RED));
    }

    /* ---------------------------------------------------------------- ticker */

    private void ensureTask() {
        if (task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (runs.isEmpty()) { task.cancel(); task = null; return; }
            int maxTicks = plugin.getConfig().getInt("mobility.training.max-run-ticks", 2400);
            for (UUID id : runs.keySet().toArray(new UUID[0])) {
                Player p = plugin.getServer().getPlayer(id);
                if (p == null || !p.isOnline()) { runs.remove(id); continue; }
                Run r = runs.get(id);
                if (r == null) continue;
                if (++r.ticks > maxTicks) { stop(p, true); continue; }
                if (r.phase == Phase.REACTION) tickReaction(p, r);
                else tickLeap(p, r);
            }
        }, 1L, 1L);
    }

    private void tickReaction(Player p, Run r) {
        ParkourAnchor a = r.pk.anchors().get(r.fromIndex);
        // SPACE anchors: PlayerJumpEvent is the primary detector, but if a setup
        // doesn't fire it while the player is pinned, catch the jump by its
        // upward velocity burst instead so the SPACE key always works.
        if (a.key() == ParkourAnchor.Key.SPACE && p.getVelocity().getY() > 0.25) {
            onPress(p, ParkourAnchor.Key.SPACE);
            if (r.phase != Phase.REACTION) return;
        }
        // Pin only the HORIZONTAL axis; keep vertical velocity so a real jump can
        // occur. Forcing Y to 0 every tick left the player "frozen-grounded" and
        // Paper never fired PlayerJumpEvent — so the SPACE key did nothing.
        p.setVelocity(new Vector(0, p.getVelocity().getY(), 0));
        p.setFallDistance(0f);
        int period = Math.max(4, a.loopTicks());
        int loopCount = r.reactionTick / period;
        if (loopCount >= a.loops()) {           // ran out of sweeps
            p.sendActionBar(Component.text("Trop lent !", NamedTextColor.RED));
            startLeap(p, r, true);
            return;
        }
        renderBar(p, r, a, cursorAt(r, a), loopCount);
        r.reactionTick++;
    }

    private void renderBar(Player p, Run r, ParkourAnchor a, int cursor, int loopCount) {
        Component bar = Component.text("« ", NamedTextColor.DARK_GRAY);
        for (int i = 0; i < BAR_CELLS; i++) {
            boolean inZone = i >= r.zoneStart && i < r.zoneStart + ZONE_WIDTH;
            if (i == cursor) {
                bar = bar.append(Component.text("◆",
                        inZone ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            } else {
                bar = bar.append(Component.text("▮",
                        inZone ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY));
            }
        }
        bar = bar.append(Component.text(" »  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("[" + a.key().pretty() + "]", NamedTextColor.AQUA))
                .append(Component.text("  " + (loopCount + 1) + "/" + a.loops(),
                        NamedTextColor.GRAY));
        p.sendActionBar(bar);
    }

    private static Vector arcPoint(Vector start, Vector end, double t, double leapHeight) {
        double x = start.getX() + (end.getX() - start.getX()) * t;
        double z = start.getZ() + (end.getZ() - start.getZ()) * t;
        double y = start.getY() + (end.getY() - start.getY()) * t
                + leapHeight * 4.0 * t * (1.0 - t);
        return new Vector(x, y, z);
    }
}
