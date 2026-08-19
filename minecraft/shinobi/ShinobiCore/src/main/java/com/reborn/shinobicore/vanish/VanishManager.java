package com.reborn.shinobicore.vanish;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transient staff "vanish" — who can see whom, layered ON TOP of everything
 * else (inconnu players are still visible in the world; vanish is the only
 * thing that hides a body). State is memory-only and cleared on logout.
 *
 * <p>Three modes:
 * <ul>
 *   <li>{@link Mode#HIDE_ALL} — nobody sees the target, except online staff
 *       ({@code shinobicore.staff}).</li>
 *   <li>{@link Mode#HIDE_EXCEPT} — nobody sees the target except staff + a
 *       chosen set of viewers.</li>
 *   <li>{@link Mode#SHOW_EXCEPT} — everyone sees the target except a chosen
 *       set of viewers.</li>
 * </ul>
 *
 * <p>A separate {@code cinematicHidden} set hides a player fully (even from
 * staff) while a cinematic plays — the stand-in NPC represents them.
 */
public final class VanishManager {

    public enum Mode { HIDE_ALL, HIDE_EXCEPT, SHOW_EXCEPT }

    /** A vanish configuration: mode + the chosen viewer player-UUIDs. */
    public static final class State {
        private Mode mode;
        private final Set<UUID> exceptions = new HashSet<>();
        State(Mode mode) { this.mode = mode; }
        public Mode mode() { return mode; }
        public Set<UUID> exceptions() { return exceptions; }
    }

    private static final String STAFF_PERM = "shinobicore.staff";

    private final ShinobiCore plugin;
    private final Map<UUID, State> vanished = new ConcurrentHashMap<>();
    private final Set<UUID> cinematicHidden = ConcurrentHashMap.newKeySet();

    public VanishManager(ShinobiCore plugin) { this.plugin = plugin; }

    /* ------------------------------------------------------------- queries */

    public boolean isVanished(UUID id) { return id != null && vanished.containsKey(id); }

    public Mode modeOf(UUID id) {
        State s = vanished.get(id);
        return s == null ? null : s.mode();
    }

    /**
     * Whether {@code viewer} can currently see {@code target}, honouring the
     * target's vanish mode + any active cinematic hide. A player always sees
     * themselves.
     */
    public boolean canSee(Player viewer, Player target) {
        if (viewer == null || target == null) return true;
        UUID tid = target.getUniqueId();
        if (viewer.getUniqueId().equals(tid)) return true;
        if (cinematicHidden.contains(tid)) return false;
        State s = vanished.get(tid);
        if (s == null) return true;
        return switch (s.mode) {
            case HIDE_ALL -> viewer.hasPermission(STAFF_PERM);
            case HIDE_EXCEPT -> viewer.hasPermission(STAFF_PERM)
                    || s.exceptions.contains(viewer.getUniqueId());
            case SHOW_EXCEPT -> !s.exceptions.contains(viewer.getUniqueId());
        };
    }

    /* -------------------------------------------------------------- mutate */

    /** Enable (or replace) a vanish for {@code target}. */
    public void enable(Player target, Mode mode, Set<UUID> exceptions) {
        if (target == null || mode == null) return;
        State s = new State(mode);
        if (exceptions != null) s.exceptions.addAll(exceptions);
        vanished.put(target.getUniqueId(), s);
        apply(target);
        refreshAllTabs();
    }

    /** Turn vanish off for {@code target} (returns them to visible). */
    public void disable(Player target) {
        if (target == null) return;
        vanished.remove(target.getUniqueId());
        apply(target);
        refreshAllTabs();
    }

    /** Drop all state for a player (logout). */
    public void clear(UUID id) {
        if (id == null) return;
        vanished.remove(id);
        cinematicHidden.remove(id);
    }

    /* ------------------------------------------------------------ cinematic */

    /** Hide a player from EVERYONE (even staff) for the duration of a
     *  cinematic; the stand-in NPC stands in for them. */
    public void hideForCinematic(Player target) {
        if (target == null) return;
        cinematicHidden.add(target.getUniqueId());
        apply(target);
        refreshAllTabs();
    }

    /** End a cinematic hide and re-assert any standing vanish. */
    public void endCinematicHide(Player target) {
        if (target == null) return;
        cinematicHidden.remove(target.getUniqueId());
        apply(target);
        refreshAllTabs();
    }

    /* --------------------------------------------------------- application */

    /** Re-evaluate who can see {@code target} and hide/show accordingly. */
    public void apply(Player target) {
        if (target == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.getUniqueId().equals(target.getUniqueId())) continue;
            if (canSee(viewer, target)) viewer.showEntity(plugin, target);
            else viewer.hideEntity(plugin, target);
        }
    }

    /** A viewer joined / changed world: evaluate their view of every hidden
     *  target so vanished players don't "pop" into view. */
    public void applyForViewer(Player viewer) {
        if (viewer == null) return;
        for (Player target : Bukkit.getOnlinePlayers()) {
            UUID tid = target.getUniqueId();
            if (tid.equals(viewer.getUniqueId())) continue;
            if (!vanished.containsKey(tid) && !cinematicHidden.contains(tid)) continue;
            if (canSee(viewer, target)) viewer.showEntity(plugin, target);
            else viewer.hideEntity(plugin, target);
        }
    }

    private void refreshAllTabs() {
        if (plugin.tabList() == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) plugin.tabList().refresh(p);
    }
}
