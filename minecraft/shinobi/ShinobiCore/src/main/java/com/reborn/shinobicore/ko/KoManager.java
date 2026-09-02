package com.reborn.shinobicore.ko;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The central KO registry.
 *
 * <p>Owns the {@link KoState} table, the persistence file
 * ({@code ko-state.yml}), and the ticker that:
 * <ul>
 *   <li>Pins KO players to their lock location (so they can't crawl).</li>
 *   <li>Refreshes blindness + slowness so it never visibly blinks.</li>
 *   <li>Auto-wakes a player after the 5-minute floor with HP=20 and
 *       chakra=50, but ONLY if they haven't already been heal-revived
 *       by another path (a healer pushing their HP above the heal
 *       threshold, see {@link #checkHealRevive}).</li>
 *   <li>Emits a periodic French actionbar showing time-remaining.</li>
 * </ul>
 *
 * <p>Entry into KO is initiated externally — by {@code KoListener} when
 * an HP cap fires, or by the chakra-zero check in {@link #tick}. Exit
 * goes through {@link #revive} (any path) which is responsible for
 * restoring HP/chakra and clearing the row.
 */
public final class KoManager implements com.reborn.shinobicore.api.KoService {

    /** Minimum 5-minute KO floor before auto-wake can fire when the
     *  KO was triggered by HP loss (took a killing blow). */
    public static final long MIN_KO_MILLIS_HP     = 5L * 60L * 1000L;
    /** Shorter 3-minute floor (2 minutes + 60 seconds) when the KO
     *  was triggered by chakra exhaustion. Fainting from chakra fatigue
     *  is less catastrophic than near-death from a sword blow, so the
     *  body recovers faster. */
    public static final long MIN_KO_MILLIS_CHAKRA = (2L * 60L + 60L) * 1000L;

    /** HP restored on a HP-cause auto-wake. Chakra is left untouched. */
    public static final double AUTO_WAKE_HP        = 20.0;
    /** Chakra restored on a chakra-cause auto-wake. HP is left untouched. */
    public static final double AUTO_WAKE_CHAKRA    = 50.0;

    /** Heal-revive trigger: HP must reach max(maxHealth * 0.01, 100). */
    public static final double HEAL_REVIVE_PCT     = 0.01;
    public static final double HEAL_REVIVE_FLOOR   = 100.0;

    private final ShinobiCore plugin;
    private final File         file;

    /** playerId → KoState. Concurrent because the auto-save ticker
     *  runs on async + main; reads can happen from chat / GUI clicks
     *  on either side. */
    private final Map<UUID, KoState> active = new ConcurrentHashMap<>();

    /** playerId → wall-clock millis at which a freshly-revived player
     *  becomes eligible for the chakra-zero KO scan again. Without
     *  this, an auto-wake at chakra=50 could be re-KO'd the very next
     *  tick if anything (mobility consumption, an Iryō technique
     *  bug, race) drops chakra back below threshold before the player
     *  can react. 10-second floor matches the title-card visibility
     *  window so the wake actually feels like waking up. */
    private final Map<UUID, Long> reviveGraceUntil = new ConcurrentHashMap<>();
    public static final long REVIVE_GRACE_MILLIS = 10_000L;

    private BukkitTask ticker;

    public KoManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "ko-state.yml");
    }

    /* ============================================================ lifecycle */

    public void start() {
        load();
        if (ticker != null) ticker.cancel();
        // 1-second resolution is plenty for blindness refresh, action
        // bar text, and pin-to-lock-location enforcement.
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        save();
    }

    /* ----------------------------------------------------------- persistence */

    private void load() {
        active.clear();
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("ko");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID pid = UUID.fromString(key);
                UUID cid = UUID.fromString(s.getString("character", ""));
                KoState.Cause cause;
                try { cause = KoState.Cause.valueOf(s.getString("cause", "HP")); }
                catch (IllegalArgumentException ex) { cause = KoState.Cause.HP; }
                long start = s.getLong("start", System.currentTimeMillis());
                long blind = s.getLong("blind-until", start + minKoMillisFor(cause));
                Location loc = KoState.parseLoc(
                        s.getString("loc.world", ""),
                        s.getDouble("loc.x"), s.getDouble("loc.y"), s.getDouble("loc.z"),
                        (float) s.getDouble("loc.yaw"), (float) s.getDouble("loc.pitch"));
                if (loc == null) continue;
                active.put(pid, new KoState(pid, cid, cause, start, blind, loc));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed KO row '" + key + "'.");
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (KoState st : active.values()) {
            String key = "ko." + st.playerId();
            cfg.set(key + ".character",   st.characterId().toString());
            cfg.set(key + ".cause",       st.cause().name());
            cfg.set(key + ".start",       st.startMillis());
            cfg.set(key + ".blind-until", st.blindUntil());
            Location l = st.lockLocation();
            cfg.set(key + ".loc.world", l.getWorld().getName());
            cfg.set(key + ".loc.x",     l.getX());
            cfg.set(key + ".loc.y",     l.getY());
            cfg.set(key + ".loc.z",     l.getZ());
            cfg.set(key + ".loc.yaw",   l.getYaw());
            cfg.set(key + ".loc.pitch", l.getPitch());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save ko-state.yml", ex);
        }
    }

    /** Cooldown floor in millis for the cause that produced this KO. */
    public static long minKoMillisFor(KoState.Cause cause) {
        return cause == KoState.Cause.CHAKRA
                ? MIN_KO_MILLIS_CHAKRA : MIN_KO_MILLIS_HP;
    }

    /* --------------------------------------------------------------- queries */

    public boolean isKo(UUID playerId)         { return active.containsKey(playerId); }
    public KoState getKo(UUID playerId)        { return active.get(playerId); }
    public Collection<KoState> all()           { return active.values(); }

    /* --------------------------------------------------------------- enter */

    /** Put a player into the KO state.
     *
     *  @param player  the now-unconscious player
     *  @param charId  the character they were incarnating at the moment
     *                 of the KO; used so injuries land on the right
     *                 character even if the player swaps later
     *  @param cause   what dropped them — drives the cooldown floor
     *                 and which stat is restored on wake
     *  @return the freshly-created state, or the existing one if the
     *          player was already KO (no double-entry).
     */
    public KoState enterKo(Player player, UUID charId, KoState.Cause cause) {
        if (player == null) return null;
        KoState existing = active.get(player.getUniqueId());
        if (existing != null) return existing;

        // Notify addon plugins (ShinobiAbilities) before the KO effects
        // land, so the jutsu picker can restore the hotbar and any
        // in-flight incantation is cancelled. See KoEnterEvent javadoc.
        plugin.getServer().getPluginManager().callEvent(
                new com.reborn.shinobicore.event.KoEnterEvent(player, charId, cause));

        // Worsen every pre-existing injury one tier. The KO that put
        // the character down also stresses the healing tissues — old
        // bruises bloom into hematomas, faible plaies tear back open,
        // etc. URGENT can't go higher so it stays. We also reset the
        // heal cooldown to 0 since the injury is fresh again at its
        // new severity. New injuries from THIS hit are added in
        // KoListener.triggerKo AFTER enterKo, so they keep their
        // natural starting severity.
        ShinobiCharacter c = plugin.characters().getActive(player.getUniqueId());
        if (c != null) {
            boolean changed = false;
            for (com.reborn.shinobicore.ko.injury.Injury inj : c.injuries()) {
                if (inj.hidden()) continue;
                com.reborn.shinobicore.ko.injury.Severity worse =
                        inj.severity().upgrade();
                if (worse != inj.severity()) {
                    inj.setSeverity(worse);
                    inj.setNextHealableMillis(0L);
                    changed = true;
                }
            }
            // After upgrading, run the merge pass — what used to be
            // 5 Faible Hématome on a part is now 5 Moyen, which can
            // collapse into 1 Important and possibly trigger a hidden
            // Os cassé.
            if (com.reborn.shinobicore.ko.injury.InjuryMerger
                    .mergeAll(c.injuries())) changed = true;
            if (changed) plugin.characterRepository().save(c);
        }

        long now = System.currentTimeMillis();
        Location lock = player.getLocation().clone();
        KoState st = new KoState(player.getUniqueId(), charId, cause, now,
                now + minKoMillisFor(cause), lock);
        active.put(player.getUniqueId(), st);

        // Visual + status cues. Blindness covers the 5-min floor; the
        // ticker refreshes it before it runs out. Slowness 6 paralyses
        // input; gravity stays so the body falls if pushed mid-air.
        applyKoEffects(player);

        // Title card so the moment lands.
        player.showTitle(Title.title(
                Component.text("KO", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("Vous êtes inconscient.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(800))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.5f);

        save();
        return st;
    }

    /* --------------------------------------------------------------- exit */

    /** Wake a KO player. Routed by:
     *  <ul>
     *    <li>{@link #checkHealRevive} when HP crosses the heal floor;</li>
     *    <li>the 5-minute auto-wake branch in {@link #tick};</li>
     *    <li>the Iryō Réveil technique (future);</li>
     *    <li>the staff /character edit revive command.</li>
     *  </ul>
     *
     *  @param hp     HP to set on wake, clamped to character's max
     *  @param chakra chakra to set on wake, clamped to pool's max
     */
    /** Wake a KO player. Asymmetric by cause:
     *  <ul>
     *    <li>{@code HP}     — sets HP to {@code hp}, leaves chakra
     *        exactly where it was at KO entry.</li>
     *    <li>{@code CHAKRA} — sets chakra to {@code chakra}, leaves
     *        HP exactly where it was. (HP can be zero / very low if
     *        you also took damage during KO; that's fine, you crawl
     *        out hurt but conscious.)</li>
     *  </ul>
     *  Either way, the chakra-zero scan is silenced for
     *  {@link #REVIVE_GRACE_MILLIS} so the wake doesn't immediately
     *  re-KO the player on the next tick.
     */
    public void revive(UUID playerId, KoState.Cause cause,
                       double hp, double chakra) {
        KoState st = active.remove(playerId);
        if (st == null) return;
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) {
            // Clear effects.
            p.removePotionEffect(PotionEffectType.BLINDNESS);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            ShinobiCharacter c = plugin.characters().getActive(playerId);
            switch (cause) {
                case HP -> {
                    if (c != null) {
                        c.setCurrentHp(Math.min(hp, c.maxHp()));
                    }
                    p.setHealth(Math.min(hp, p.getMaxHealth()));
                    // Chakra: deliberately untouched.
                }
                case CHAKRA -> {
                    if (c != null) {
                        c.chakra().setCurrent(Math.min(chakra, c.chakra().max()));
                    }
                    // HP: deliberately untouched. The player's Bukkit
                    // attribute already reflects whatever HP they had
                    // when they fainted; no need to write it.
                }
            }
            // Drop the carry mount if any.
            if (p.getVehicle() != null) p.leaveVehicle();
            // Suppress the chakra-zero re-KO scan for a few seconds so
            // mobility consumption / async health attribute jitter
            // can't punt us straight back into KO before the player
            // even sees the wake-up title card.
            reviveGraceUntil.put(playerId,
                    System.currentTimeMillis() + REVIVE_GRACE_MILLIS);
            p.showTitle(Title.title(
                    Component.text("Réveil", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text("Vous reprenez conscience.", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300),
                            Duration.ofSeconds(2),
                            Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, 1.4f);
        }
        save();
    }

    /** Backwards-compat shim — picks the cause from the live KO row
     *  if any, otherwise defaults to HP. */
    public void revive(UUID playerId, double hp, double chakra) {
        KoState st = active.get(playerId);
        KoState.Cause c = st != null ? st.cause() : KoState.Cause.HP;
        revive(playerId, c, hp, chakra);
    }

    /** Check whether a heal pushed this player past the wake threshold.
     *  Called by {@code KoListener.onRegain}. Threshold is
     *  {@code max(maxHealth * 0.01, 100)} per the design brief — a
     *  healer must raise the body above that line.
     *
     *  <p>Heal-revive only restores HP (the stat the healer just bumped);
     *  chakra is preserved. This applies to both HP-cause and
     *  chakra-cause KOs — receiving a heal-revive on a chakra-cause
     *  KO simply means a medic stitched you up while you were
     *  fainted from chakra fatigue, which is fine and lore-consistent. */
    public void checkHealRevive(Player p) {
        if (p == null) return;
        if (!isKo(p.getUniqueId())) return;
        double threshold = Math.max(p.getMaxHealth() * HEAL_REVIVE_PCT,
                HEAL_REVIVE_FLOOR);
        if (p.getHealth() >= threshold) {
            revive(p.getUniqueId(), KoState.Cause.HP,
                    p.getHealth(), 0 /* chakra ignored on HP cause */);
        }
    }

    /* --------------------------------------------------------------- ticker */

    private void tick() {
        long now = System.currentTimeMillis();

        // Chakra-zero check: any online player whose active character
        // has hit 0 chakra goes down immediately. We skip players
        // already KO, players with no active character, and players
        // still inside their post-revive grace window.
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (active.containsKey(p.getUniqueId())) continue;
            Long graceEnd = reviveGraceUntil.get(p.getUniqueId());
            if (graceEnd != null) {
                if (now < graceEnd) continue;
                reviveGraceUntil.remove(p.getUniqueId());
            }
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c == null) continue;
            if (c.chakra().current() <= 0.0001 && c.chakra().max() > 0.0) {
                enterKo(p, c.id(), KoState.Cause.CHAKRA);
            }
        }

        for (KoState st : active.values()) {
            Player p = Bukkit.getPlayer(st.playerId());
            if (p == null) continue;

            // Pin to lock location unless being carried (in which case
            // PorterManager keeps the body riding the carrier).
            if (!st.isBeingCarried() && p.getVehicle() == null) {
                Location cur = p.getLocation();
                Location lock = st.lockLocation();
                if (cur.getWorld().equals(lock.getWorld())
                        && cur.distanceSquared(lock) > 0.05) {
                    p.teleport(lock);
                }
            }

            // Refresh effects so they never blink between ticks.
            applyKoEffects(p);

            // Auto-wake — duration depends on the cause of the KO.
            // HP-cause: 5 min, restore HP=20. Chakra-cause: 3 min,
            // restore chakra=50. The OTHER stat is left untouched.
            long floorMillis = minKoMillisFor(st.cause());
            if (now >= st.startMillis() + floorMillis) {
                revive(st.playerId(), st.cause(),
                        AUTO_WAKE_HP, AUTO_WAKE_CHAKRA);
                continue; // Don't paint the actionbar after a revive.
            }

            // Actionbar countdown.
            long remaining = (st.startMillis() + floorMillis - now) / 1000L;
            long mins = remaining / 60;
            long secs = remaining % 60;
            String label = st.cause() == KoState.Cause.CHAKRA
                    ? "Épuisement chakra" : "Inconscient";
            p.sendActionBar(Component.text(
                    String.format("%s — %d:%02d restantes", label, mins, secs),
                    NamedTextColor.DARK_GRAY));
        }
    }

    /** Re-apply KO effects on the player. Runs on every tick + on
     *  every fresh entry / after a respawn (since respawn clears
     *  potion effects). */
    public void applyKoEffects(Player p) {
        if (p == null || !p.isOnline()) return;
        // 5-minute blindness, refreshed each second.
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS,
                40, 0, true, false, false));
        // Slowness VII — paralyses input.
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                40, 6, true, false, false));
        // Survival mode so they can't fly out of the hold.
        if (p.getGameMode() == GameMode.CREATIVE
                || p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    /** Force-clear KO state (used by /character edit and on shutdown if
     *  a staff member chooses to wipe a stale row). */
    public void forceClear(UUID playerId) {
        active.remove(playerId);
        save();
    }

    /* --------------------------------------------------------------- access */

    /** Snapshot map (immutable, for logs / debug). */
    public Map<UUID, KoState> snapshot() {
        return new LinkedHashMap<>(active);
    }
}
