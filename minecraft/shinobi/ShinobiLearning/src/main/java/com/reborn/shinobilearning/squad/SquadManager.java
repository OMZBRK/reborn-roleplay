package com.reborn.shinobilearning.squad;

import com.reborn.shinobilearning.ShinobiLearning;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Genin squads — a ranking sensei plus up to a few students. In-memory for the
 * MVP (transient across restarts; persistence comes later). No squad chat by
 * design — comms are voice + the Corbeau/Missive messenger.
 */
public final class SquadManager {

    private final ShinobiLearning plugin;
    private final Map<UUID, Squad> squads = new ConcurrentHashMap<>();      // squadId -> squad
    private final Map<UUID, UUID> memberIndex = new ConcurrentHashMap<>();  // charId  -> squadId

    public SquadManager(ShinobiLearning plugin) { this.plugin = plugin; }

    /** A squad: a named team led by a sensei, keyed by character UUIDs. */
    public static final class Squad {
        private final UUID id;
        private final String name;
        private final UUID leaderCharId;
        private final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();

        Squad(UUID id, String name, UUID leaderCharId, String leaderName) {
            this.id = id;
            this.name = name;
            this.leaderCharId = leaderCharId;
            this.members.put(leaderCharId, leaderName);
        }

        public UUID id() { return id; }
        public String name() { return name; }
        public UUID leaderCharId() { return leaderCharId; }
        public Map<UUID, String> members() { return members; }
        public int size() { return members.size(); }
    }

    public int maxMembers() {
        return Math.max(2, plugin.getConfig().getInt("squad.max-members", 4));
    }

    public Squad squadOf(UUID charId) {
        UUID sid = memberIndex.get(charId);
        return sid == null ? null : squads.get(sid);
    }

    /** Create a squad led by the given character. Null if the leader is already
     *  in a squad. */
    public Squad create(UUID leaderCharId, String leaderName, String squadName) {
        if (memberIndex.containsKey(leaderCharId)) return null;
        UUID id = UUID.randomUUID();
        Squad s = new Squad(id, squadName, leaderCharId, leaderName);
        squads.put(id, s);
        memberIndex.put(leaderCharId, id);
        return s;
    }

    /** Add a member. Returns false when the squad is full or the character is
     *  already in a squad. */
    public boolean addMember(Squad squad, UUID charId, String name) {
        if (squad == null || memberIndex.containsKey(charId)) return false;
        if (squad.size() >= maxMembers()) return false;
        squad.members.put(charId, name);
        memberIndex.put(charId, squad.id());
        return true;
    }

    /** Remove a member (or disband if the leader leaves). Returns the squad the
     *  character was in, or null. */
    public Squad removeMember(UUID charId) {
        UUID sid = memberIndex.remove(charId);
        if (sid == null) return null;
        Squad s = squads.get(sid);
        if (s == null) return null;
        if (s.leaderCharId().equals(charId)) {
            disband(s);
        } else {
            s.members.remove(charId);
        }
        return s;
    }

    public void disband(Squad squad) {
        if (squad == null) return;
        for (UUID m : squad.members().keySet()) memberIndex.remove(m);
        squads.remove(squad.id());
    }
}
