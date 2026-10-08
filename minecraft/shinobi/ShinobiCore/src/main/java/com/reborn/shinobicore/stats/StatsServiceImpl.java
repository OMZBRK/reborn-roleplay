package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.Internal;
import com.reborn.shinobicore.api.StatsService;
import com.reborn.shinobicore.api.event.CharacterStatsChangedEvent;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * {@link StatsService} over the character's {@link CharacterStats} and the
 * {@link StatFormulas} levers. Every mutation goes through here so the pools
 * are recomputed, the live player's MAX_HEALTH follows, the event fires, the
 * character is saved and an open sheet is refreshed.
 */
@Internal
public final class StatsServiceImpl implements StatsService {

    private static volatile StatsServiceImpl instance;

    private final ShinobiCore plugin;
    private StatsChannel channel;

    public StatsServiceImpl(ShinobiCore plugin) {
        this.plugin = plugin;
        instance = this;
    }

    void bindChannel(StatsChannel channel) { this.channel = channel; }

    /** Refresh the owner's sheet if they are online and this is their active character. */
    public static void pushIfOnline(ShinobiCharacter c) {
        StatsServiceImpl s = instance;
        if (s == null || c == null) return;
        s.refreshPools(c);
        if (s.channel == null) return;
        Player p = Bukkit.getPlayer(c.ownerId());
        ShinobiCharacter active = p == null ? null : s.plugin.characters().getActive(p.getUniqueId());
        if (active != null && active.id().equals(c.id())) s.channel.push(p, false);
    }

    /* ---------------------------------------------------------- lookup */

    ShinobiCharacter find(UUID character) {
        if (character == null || plugin.characters() == null) return null;
        for (ShinobiCharacter c : plugin.characters().activeView().values()) {
            if (c != null && character.equals(c.id())) return c;
        }
        var r = plugin.characters().resolveOwnedById(character);
        return r == null ? null : r.character();
    }

    private CharacterStats statsOf(UUID character) {
        ShinobiCharacter c = find(character);
        return c == null ? new CharacterStats() : c.stats();
    }

    /* ------------------------------------------------------------ reads */

    @Override
    public int get(UUID character, Stat stat) { return statsOf(character).get(stat); }

    @Override
    public double effective(UUID character, Stat stat) { return StatFormulas.eff(statsOf(character), stat); }

    @Override
    public Map<Stat, Integer> all(UUID character) { return statsOf(character).snapshot(); }

    @Override
    public int unspentPoints(UUID character) {
        ShinobiCharacter c = find(character);
        return c == null ? 0 : Math.max(0, StatFormulas.unspent(c.rank(), c.stats()));
    }

    /* ----------------------------------------------------------- writes */

    @Override
    public AllocationResult allocate(UUID character, Map<Stat, Integer> deltas) {
        ShinobiCharacter c = find(character);
        if (c == null) return AllocationResult.failure("Personnage introuvable.", 0);
        CharacterStats st = c.stats();
        int unspent = StatFormulas.unspent(c.rank(), st);
        int total = 0;
        for (Map.Entry<Stat, Integer> e : deltas.entrySet()) {
            int d = e.getValue() == null ? 0 : e.getValue();
            if (d < 0) return AllocationResult.failure("Un point dépensé ne se reprend pas (respec).", unspent);
            if (st.get(e.getKey()) + d > MAX) {
                return AllocationResult.failure(e.getKey().displayName() + " dépasserait " + MAX + ".", unspent);
            }
            total += d;
        }
        if (total == 0) return AllocationResult.failure("Aucun point à dépenser.", unspent);
        if (total > unspent) {
            return AllocationResult.failure("Il te reste " + Math.max(0, unspent) + " point(s), "
                    + total + " demandé(s).", unspent);
        }
        Map<Stat, Integer> before = st.snapshot();
        for (Map.Entry<Stat, Integer> e : deltas.entrySet()) {
            st.set(e.getKey(), st.get(e.getKey()) + e.getValue());
        }
        afterChange(c, before);
        return AllocationResult.success(StatFormulas.unspent(c.rank(), st));
    }

    @Override
    public void respec(UUID character) {
        ShinobiCharacter c = find(character);
        if (c == null) return;
        Map<Stat, Integer> before = c.stats().snapshot();
        c.stats().reset();
        afterChange(c, before);
    }

    /** Staff override: write one stat directly (clamped), no point check. */
    public void setDirect(ShinobiCharacter c, Stat stat, int value) {
        Map<Stat, Integer> before = c.stats().snapshot();
        c.stats().set(stat, value);
        afterChange(c, before);
    }

    /** Après un changement de grade : bonus passifs et points recalculés, poussés au joueur. */
    public void onRankChanged(ShinobiCharacter c) {
        afterChange(c, c.stats().snapshot());
    }

    private void afterChange(ShinobiCharacter c, Map<Stat, Integer> before) {
        refreshPools(c);
        Bukkit.getPluginManager().callEvent(new CharacterStatsChangedEvent(
                c.ownerId(), c.id(), before, c.stats().snapshot()));
        plugin.characters().save(c);
        pushIfOnline(c);
    }

    /**
     * Recompute the maxima and mirror them on the live player. Current HP is
     * kept in absolute terms — raising Vigueur never heals (§3.1).
     */
    void refreshPools(ShinobiCharacter c) {
        c.recomputeStats();
        Player p = Bukkit.getPlayer(c.ownerId());
        if (p == null || !p.isOnline()) return;
        ShinobiCharacter active = plugin.characters().getActive(p.getUniqueId());
        if (active == null || !active.id().equals(c.id())) return;
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp == null) return;
        double keep = p.getHealth();
        hp.setBaseValue(Math.max(1.0, c.maxHp()));
        p.setHealth(Math.max(0.0, Math.min(keep, hp.getValue())));
    }

    /* ---------------------------------------------------------- derived */

    @Override
    public double maxHp(UUID character) { return StatFormulas.maxHp(statsOf(character)); }

    @Override
    public double maxChakra(UUID character) { return StatFormulas.maxChakra(statsOf(character)); }

    @Override
    public double maxStamina(UUID character) { return StatFormulas.maxStamina(statsOf(character)); }

    @Override
    public double chakraRegenPer10s(UUID character) { return StatFormulas.chakraRegenPer10s(statsOf(character)); }

    @Override
    public double critChance(UUID character) { return StatFormulas.critChance(statsOf(character)); }

    @Override
    public double techniqueCost(UUID character, int techniqueRank, CostKind kind) {
        return StatFormulas.techniqueCost(statsOf(character), techniqueRank, kind);
    }

    @Override
    public double baseTechniqueCost(int techniqueRank, CostKind kind) {
        return StatFormulas.baseCost(techniqueRank, kind);
    }

    @Override
    public double techniquePower(UUID character, double base, Map<Stat, Grade> scaling) {
        return base * StatFormulas.powerMultiplier(statsOf(character), scaling);
    }

    @Override
    public double natureMultiplier(ChakraAffinity attacker, int attackerRank,
                                   ChakraAffinity defender, int defenderRank) {
        return StatFormulas.natureMultiplier(attacker, attackerRank, defender, defenderRank);
    }

    @Override
    public double meleeDamage(UUID character, double base, boolean armed) {
        return base * StatFormulas.meleeMultiplier(statsOf(character), armed);
    }

    /** Parse helper for commands: {@code "nin=2,ctr=1"} → deltas. Null on error. */
    public static Map<Stat, Integer> parseDeltas(String raw) {
        Map<Stat, Integer> out = new EnumMap<>(Stat.class);
        for (String part : raw.split("[,;]")) {
            String[] kv = part.trim().split("[=:]");
            if (kv.length != 2) return null;
            Stat s = Stat.from(kv[0]);
            if (s == null) return null;
            try { out.merge(s, Integer.parseInt(kv[1].trim()), Integer::sum); }
            catch (NumberFormatException e) { return null; }
        }
        return out;
    }
}
