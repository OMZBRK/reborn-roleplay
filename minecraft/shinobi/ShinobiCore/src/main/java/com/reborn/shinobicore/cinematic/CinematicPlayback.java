package com.reborn.shinobicore.cinematic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The cinematic playback engine. Runs one {@link PlaybackSession} per player:
 * teleport → freeze + view-lock → title + chat → hold → advance → release.
 *
 * <p>A single repeating task (every tick) drives every session: it re-asserts
 * the locked viewpoint (the freeze, §6) and counts down the current anchor,
 * advancing on zero. Using one task — not a chain of {@code runTaskLater}s —
 * makes it trivial to stop cleanly on disconnect / character-switch / stop.
 */
public final class CinematicPlayback {

    private final ShinobiCore plugin;
    private final CinematicManager manager;
    private final Map<UUID, PlaybackSession> sessions = new HashMap<>();
    private BukkitTask ticker;

    public CinematicPlayback(ShinobiCore plugin, CinematicManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    /* -------------------------------------------------------------- queries */

    public boolean isPlaying(Player p) {
        return p != null && sessions.containsKey(p.getUniqueId());
    }

    /** True while this player is frozen in a cinematic (listener guard). */
    public boolean isFrozen(UUID id) {
        return sessions.containsKey(id);
    }

    /** The viewpoint this player is currently locked to, or null. */
    public Location lockLocation(UUID id) {
        PlaybackSession s = sessions.get(id);
        return s == null ? null : s.lockLocation;
    }

    /* ---------------------------------------------------------------- start */

    /**
     * Begin a cinematic for {@code p}. No-op if the player is already in a
     * session, offline, or the cinematic has no anchors.
     */
    public void play(Player p, Cinematic cine, boolean isIntro, ShinobiCharacter introChar) {
        if (p == null || !p.isOnline()) return;
        if (cine == null || cine.isEmpty()) return;
        UUID id = p.getUniqueId();
        if (sessions.containsKey(id)) return;

        PlaybackSession s = new PlaybackSession(id, cine, isIntro, introChar);
        s.returnLocation = p.getLocation().clone();
        s.returnWalkSpeed = p.getWalkSpeed();
        s.returnFlySpeed = p.getFlySpeed();
        s.returnInvulnerable = p.isInvulnerable();
        s.returnInvisible = p.isInvisible();

        // Enter cinematic state: immobilise + invulnerable + invisible (so the
        // player can't even see their own body); close any GUI.
        p.closeInventory();
        p.setInvulnerable(true);
        p.setInvisible(true);
        p.setWalkSpeed(0f);
        p.setFlySpeed(0f);

        sessions.put(id, s);
        // Leave a stand-in where the player stood, hide the real player from
        // everyone (even staff), and hide the stand-in from the player so they
        // focus on the shot — not a clone of themselves.
        spawnStandIn(p, s);
        if (plugin.vanish() != null) plugin.vanish().hideForCinematic(p);
        enterAnchor(p, s, 0);
        ensureTicker();
    }

    /* --------------------------------------------------------------- engine */

    private void ensureTicker() {
        if (ticker != null) return;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        if (sessions.isEmpty()) {
            if (ticker != null) { ticker.cancel(); ticker = null; }
            return;
        }
        boolean hardLock = plugin.getConfig().getBoolean("cinematic.lock-view", true);
        int titleDelay = Math.max(0, plugin.getConfig().getInt("cinematic.title-delay-ticks", 80));
        int textInterval = Math.max(1, plugin.getConfig().getInt("cinematic.text-interval-ticks", 40));
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            PlaybackSession s = sessions.get(id);
            if (s == null) continue;
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { sessions.remove(id); continue; }

            // The freeze: re-assert the viewpoint every tick (must run even
            // under lag so the lock never loosens). Hard-lock pins yaw/pitch
            // too; free-look keeps the player's rotation.
            holdView(p, s, hardLock);

            // Staged reveal within the anchor: the title appears after a beat
            // (title-delay-ticks), then the chat lines roll in one at a time
            // (text-interval-ticks apart). All driven off this one ticker.
            CinematicAnchor a = s.current;
            if (a != null) {
                if (!s.titleShown && s.elapsed >= titleDelay) {
                    showTitle(p, a, a.durationTicks() - titleDelay);
                    s.titleShown = true;
                }
                int dueTexts = (s.elapsed >= titleDelay)
                        ? Math.min(3, 1 + (s.elapsed - titleDelay) / textInterval)
                        : 0;
                while (s.textsShown < dueTexts) {
                    showText(p, a, s.textsShown);
                    s.textsShown++;
                }
            }

            // Per-anchor hold → advance.
            s.elapsed++;
            if (a == null || s.elapsed >= a.durationTicks()) {
                enterAnchor(p, s, s.index + 1);
            }
        }
    }

    private void holdView(Player p, PlaybackSession s, boolean hardLock) {
        Location lock = s.lockLocation;
        if (lock == null) return;
        Location cur = p.getLocation();
        boolean posOff = cur.getX() != lock.getX()
                || cur.getY() != lock.getY()
                || cur.getZ() != lock.getZ();
        if (hardLock) {
            boolean rotOff = cur.getYaw() != lock.getYaw() || cur.getPitch() != lock.getPitch();
            if (posOff || rotOff) p.teleport(lock);
        } else if (posOff) {
            Location keep = lock.clone();
            keep.setYaw(cur.getYaw());
            keep.setPitch(cur.getPitch());
            p.teleport(keep);
        }
    }

    /** Enter the anchor at {@code index}, skipping anchors whose world is
     *  missing/unloaded; finishes the sequence when none remain. */
    private void enterAnchor(Player p, PlaybackSession s, int index) {
        int i = index;
        while (i < s.cinematic.size()) {
            CinematicAnchor a = s.cinematic.anchor(i);
            Location loc = a == null ? null : a.toLocation();
            if (loc == null) {
                plugin.getLogger().warning("Cinematic '" + s.cinematic.name()
                        + "' anchor " + i + " has a missing/unloaded world — skipped.");
                i++;
                continue;
            }
            s.index = i;
            s.current = a;
            s.lockLocation = loc;
            s.elapsed = 0;
            s.titleShown = false;
            s.textsShown = 0;
            p.teleport(loc);
            return;   // title + texts are revealed progressively in tick()
        }
        finish(p, s);
    }

    /** Show the anchor's title, kept on screen for {@code stayTicks}. */
    private void showTitle(Player p, CinematicAnchor a, int stayTicks) {
        if (a.title() == null) return;
        long stayMs = Math.max(500L, stayTicks * 50L);
        Title.Times times = Title.Times.times(
                Duration.ofMillis(500), Duration.ofMillis(stayMs), Duration.ofMillis(500));
        Component main = Component.text(a.title(),
                a.titleColor() != null ? a.titleColor() : NamedTextColor.GOLD);
        p.showTitle(Title.title(main, Component.empty(), times));
    }

    /** Send chat line {@code idx} (0..2) if present. */
    private void showText(Player p, CinematicAnchor a, int idx) {
        String text;
        NamedTextColor color;
        NamedTextColor def;
        switch (idx) {
            case 0 -> { text = a.text1(); color = a.color1(); def = NamedTextColor.WHITE; }
            case 1 -> { text = a.text2(); color = a.color2(); def = NamedTextColor.GRAY; }
            case 2 -> { text = a.text3(); color = a.color3(); def = NamedTextColor.GRAY; }
            default -> { return; }
        }
        if (text == null) return;
        p.sendMessage(Component.text(text, color != null ? color : def));
        // a.sound() is reserved for future voice-over — intentionally ignored.
    }

    /* --------------------------------------------------------------- finish */

    private void finish(Player p, PlaybackSession s) {
        sessions.remove(s.playerId);
        release(p, s);
        if (p != null && p.isOnline() && s.returnLocation != null) {
            p.teleport(s.returnLocation);
        }
        // Mark intro seen ONLY on natural completion (interrupted intros replay).
        if (s.isIntro && s.introChar != null) {
            s.introChar.setIntroSeen(true);
            if (plugin.characters() != null) plugin.characters().save(s.introChar);
        }
    }

    /** Restore the player's movement/invulnerability and clear the title.
     *  Safe to call with an offline-but-cached player object. */
    private void release(Player p, PlaybackSession s) {
        // Remove the stand-in (a world entity) regardless of player state.
        if (s.standIn != null) {
            try { s.standIn.remove(); } catch (Throwable ignore) {}
            s.standIn = null;
        }
        if (p != null && plugin.vanish() != null) plugin.vanish().endCinematicHide(p);
        if (p == null) return;
        try {
            p.setWalkSpeed(s.returnWalkSpeed);
            p.setFlySpeed(s.returnFlySpeed);
            p.setInvulnerable(s.returnInvulnerable);
            p.setInvisible(s.returnInvisible);
            p.clearTitle();
        } catch (Throwable ignore) {
            // never let a release failure cascade
        }
    }

    /** Spawn the armour-stand stand-in at the player's spot (their head +
     *  name) and hide it from the player themselves. */
    private void spawnStandIn(Player p, PlaybackSession s) {
        Location at = s.returnLocation;
        if (at == null || at.getWorld() == null) return;
        try {
            ShinobiCharacter c = plugin.characters() == null ? null
                    : plugin.characters().getActive(p.getUniqueId());
            String label = c != null ? c.name() : p.getName();
            ArmorStand stand = at.getWorld().spawn(at, ArmorStand.class, as -> {
                as.setArms(true);
                as.setBasePlate(false);
                as.setGravity(false);
                as.setInvulnerable(true);
                as.setPersistent(false);
                as.setCustomNameVisible(true);
                as.customName(Component.text(label, NamedTextColor.GRAY));
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                if (head.getItemMeta() instanceof SkullMeta sm) {
                    sm.setOwningPlayer(p);
                    head.setItemMeta(sm);
                }
                if (as.getEquipment() != null) as.getEquipment().setHelmet(head);
            });
            s.standIn = stand;
            p.hideEntity(plugin, stand);   // "even to himself"
        } catch (Throwable ex) {
            plugin.getLogger().warning("Cinematic stand-in spawn failed: " + ex.getMessage());
        }
    }

    /* ---------------------------------------------------------------- stop */

    /** Abort a running cinematic. Does NOT mark introSeen (so it replays). */
    public void stop(Player p, boolean teleportBack) {
        if (p == null) return;
        PlaybackSession s = sessions.remove(p.getUniqueId());
        if (s == null) return;
        release(p, s);
        if (teleportBack && p.isOnline() && s.returnLocation != null) {
            p.teleport(s.returnLocation);
        }
    }

    /** Release every active session (server stop) — restores movement so no
     *  one is left frozen on next boot. Cancels the ticker. */
    public void stopAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            PlaybackSession s = sessions.remove(id);
            if (s == null) continue;
            release(Bukkit.getPlayer(id), s);
        }
        if (ticker != null) { ticker.cancel(); ticker = null; }
    }
}
