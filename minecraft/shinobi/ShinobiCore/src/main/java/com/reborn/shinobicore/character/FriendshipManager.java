package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-character friendship graph + transient {@code /amitier} request
 * state.
 *
 * <h2>Semantic model</h2>
 * Friendship is a <em>mutual</em> relationship between two
 * {@link ShinobiCharacter} instances (keyed by their character UUID,
 * not by the Mojang account). Adding a friendship writes an edge on
 * both sides atomically and persists both characters through the
 * {@link CharacterManager}. The edge lives on {@link ShinobiCharacter}
 * as a {@code Set<UUID>}; this manager owns the "always touch both
 * sides together" invariant.
 *
 * <h2>Why per-character instead of per-account</h2>
 * The server is a roleplay environment where each player can run
 * multiple alts. Switching characters is equivalent to being a
 * different person in-world — so friendships travel with the character
 * sheet, not the login. Player A's Kakashi can be friends with Player
 * B's Sasuke while B's other character (Itachi) remains a stranger to
 * A's Kakashi. The tab list filter reads the two <em>active</em>
 * characters and asks this manager.
 *
 * <h2>Request handshake (transient)</h2>
 * {@code /amitier} with a target in front of view stashes a
 * {@link Pending} request keyed by the TARGET's Mojang UUID (same
 * convention as {@link RencontrerManager}). The target sees an
 * accept/decline GUI; on accept the manager makes the bond mutual and
 * drops the pending entry. Only one outgoing request per target at a
 * time — a second request overwrites the first.
 */
public class FriendshipManager {

    private final ShinobiCore plugin;

    /** target owner-UUID → pending friendship request. */
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    public FriendshipManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    /* ----------------------------------------- mutual edit (the graph) */

    /** True iff both sides have each other in their friend set. The
     *  invariant is maintained elsewhere; we double-check here so a
     *  broken save file can't silently reveal one side. */
    public boolean areFriends(ShinobiCharacter a, ShinobiCharacter b) {
        if (a == null || b == null) return false;
        return a.isFriend(b.id()) && b.isFriend(a.id());
    }

    /** Add a friendship edge on both sides and persist both characters.
     *  If both owners are online and this flip matters for what they
     *  see, also refresh the visibility pair so tab / world entries
     *  update immediately. No-op if either side is null or they're
     *  already friends. */
    public boolean addMutual(ShinobiCharacter a, ShinobiCharacter b) {
        if (a == null || b == null) return false;
        if (a.id().equals(b.id())) return false;
        boolean changedA = !a.isFriend(b.id());
        boolean changedB = !b.isFriend(a.id());
        if (!changedA && !changedB) return false;
        a.addFriend(b.id());
        b.addFriend(a.id());
        plugin.characters().save(a);
        plugin.characters().save(b);
        refreshVisibilityFor(a, b);
        return true;
    }

    /** Remove a friendship edge on both sides, persist, refresh
     *  visibility. */
    public boolean removeMutual(ShinobiCharacter a, ShinobiCharacter b) {
        if (a == null || b == null) return false;
        boolean changedA = a.isFriend(b.id());
        boolean changedB = b.isFriend(a.id());
        if (!changedA && !changedB) return false;
        a.removeFriend(b.id());
        b.removeFriend(a.id());
        plugin.characters().save(a);
        plugin.characters().save(b);
        refreshVisibilityFor(a, b);
        return true;
    }

    /** If both owners are online AND each is currently playing the
     *  character whose friendship just flipped, push a visibility
     *  refresh on both sides. Swaps that happen while one side is on
     *  another character don't affect what each sees right now; the
     *  refresh will happen naturally the next time they switch to the
     *  affected character (handled by CharacterManager.setActive). */
    private void refreshVisibilityFor(ShinobiCharacter a, ShinobiCharacter b) {
        if (plugin.visibility() == null) return;
        Player pa = plugin.getServer().getPlayer(a.ownerId());
        Player pb = plugin.getServer().getPlayer(b.ownerId());
        if (pa == null || pb == null) return;
        ShinobiCharacter aActive = plugin.characters().getActive(a.ownerId());
        ShinobiCharacter bActive = plugin.characters().getActive(b.ownerId());
        if (aActive == null || bActive == null) return;
        // Only flip the pair if the affected characters are the ones
        // currently active on both sides — otherwise visibility is
        // unchanged for the in-world players.
        if (!aActive.id().equals(a.id())) return;
        if (!bActive.id().equals(b.id())) return;
        plugin.visibility().refreshPair(pa, pb);
    }

    /* ----------------------------------------- request handshake */

    /** Start (or replace) a pending friendship request from
     *  {@code requester} toward {@code target}. Identified by Mojang
     *  UUIDs because we need to address the right <em>player client</em>
     *  for the accept/decline GUI; the target's active character might
     *  switch between the request and the reply, so we freeze the
     *  originating character ids in the {@link Pending} record. */
    public void openRequest(UUID requesterOwner, UUID requesterCharacter,
                            UUID targetOwner, UUID targetCharacter) {
        pending.put(targetOwner, new Pending(
                requesterOwner, requesterCharacter, targetOwner, targetCharacter));
    }

    /** Peek at the pending request on {@code target}, if any. */
    public Pending peek(UUID targetOwner) {
        return pending.get(targetOwner);
    }

    /** Remove and return the pending request on {@code target}. */
    public Pending consume(UUID targetOwner) {
        return pending.remove(targetOwner);
    }

    /* ----------------------------------------- data types */

    /** A queued /amitier request — frozen character ids on both sides
     *  so a post-accept character switch can't bond the wrong char. */
    public record Pending(UUID requesterOwner, UUID requesterCharacter,
                          UUID targetOwner, UUID targetCharacter) {}
}
