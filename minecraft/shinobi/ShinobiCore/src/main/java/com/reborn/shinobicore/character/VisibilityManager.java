package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * Tab-list filtering driver (formerly did entity hiding too; that was
 * rolled back — friendships now gate <em>only</em> the tab list).
 *
 * <p>Entities stay visible in the world: if you walk past a stranger
 * you still see them, and {@code /rencontrer} / {@code /amitier}
 * ray-traces hit them normally. What friendships filter is the tab
 * panel — we globally suppress the native tab roster (via
 * {@link com.reborn.shinobicore.lore.TabListManager}'s
 * {@code setListed(false)}) and render a per-viewer friend list into
 * the tab footer instead. This class just owns the "push a footer
 * repaint" triggers.
 *
 * <h2>Triggers</h2>
 * <ul>
 *   <li>Character switch ({@code CharacterManager.setActive}) →
 *       {@link #refresh(Player)} repaints the switcher's footer, and
 *       everyone else's footer that might mention the switcher.</li>
 *   <li>Friendship edge flip ({@code FriendshipManager.addMutual} /
 *       {@code removeMutual}) → {@link #refreshPair(Player, Player)}
 *       repaints both sides.</li>
 *   <li>Player join — handled directly in {@code TabListManager.onJoin}.</li>
 * </ul>
 */
public class VisibilityManager implements Listener {

    private final ShinobiCore plugin;

    public VisibilityManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /** Repaint {@code viewer}'s tab footer plus every other online
     *  player's footer — character switches can change who sees whom
     *  as a friend, so we refresh the whole server rather than try to
     *  be surgical. */
    public void refresh(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;
        if (plugin.tabList() == null) return;
        plugin.tabList().refresh(viewer);
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other == viewer) continue;
            plugin.tabList().refresh(other);
        }
    }

    /** Repaint both sides of a pair — used when a single friendship
     *  edge flips. Cheaper than {@link #refresh} because we only touch
     *  the two players involved. */
    public void refreshPair(Player a, Player b) {
        if (plugin.tabList() == null) return;
        if (a != null && a.isOnline()) plugin.tabList().refresh(a);
        if (b != null && b.isOnline()) plugin.tabList().refresh(b);
    }
}
