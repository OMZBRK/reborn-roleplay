package com.reborn.shinobicore.ko;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.ata.AtaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * The central KO registry.
 *
 * <p>Owns the {@link KoState} table, the persistence file
 * ({@code ko-state.yml}), and the ticker. Since lot KO-2
 * (docs/PROPOSITION_KO_PAINRP.md) a KO has two phases:
 * <ol>
 *   <li><b>À terre</b> ({@link KoState.Phase#DOWNED}, HP cause only) — the
 *       body crawls (forced swimming pose, no jump), whispers, can call for
 *       help ({@code /aide}). Any new hit, or the timer
 *       ({@code ko.a-terre-secondes}), knocks the player out.</li>
 *   <li><b>Inconscient</b> ({@link KoState.Phase#UNCONSCIOUS}) — the body
 *       lies pinned, still sees (darkness, not blindness), whispers and uses
 *       {@code /me}. After {@code ko.hopital-propose-apres} seconds the player
 *       may choose {@code /hopital}; at the end of the timer an HP-cause KO is
 *       repatriated to the village hospital at the HP floor. A chakra-cause KO
 *       still wakes on the spot.</li>
 * </ol>
 *
 * <p>Every HP-cause wake hands the character to the {@link AtaManager}
 * (PainRP / FearRP state, lot KO-3): {@code PLEINE} after the hospital,
 * {@code ALLEGEE} when someone healed the body on site.
 *
 * <p>Note: {@link #isKo} stays true in both phases, so every existing gate
 * (jutsu, mobility, combat, inventory) keeps blocking a downed player.
 */
public final class KoManager implements com.reborn.shinobicore.api.KoService {

    /** Minimum 5-minute KO floor before auto-wake can fire when the
     *  KO was triggered by HP loss (took a killing blow). Overridden by
     *  {@code ko.inconscient-secondes}. */
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

    /** Window during which a hit counts as "part of the fight" that
     *  dropped the victim (FearRP protagonists). */
    private static final long ATTACKER_WINDOW_MILLIS = 60_000L;

    private final ShinobiCore plugin;
    private final File         file;
    private final NamespacedKey noJumpKey;

    /** playerId → KoState. Concurrent because the auto-save ticker
     *  runs on async + main; reads can happen from chat / GUI clicks
     *  on either side. */
    private final Map<UUID, KoState> active = new ConcurrentHashMap<>();

    /** victim → (attacker → last hit millis). Feeds {@link KoState#attackers()}. */
    private final Map<UUID, Map<UUID, Long>> recentHits = new ConcurrentHashMap<>();

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

    /* Tunables (ko.* in config.yml). */
    private boolean downedEnabled = true;
    private long    downedMillis = 45_000L;
    private long    unconsciousHpMillis = MIN_KO_MILLIS_HP;
    private long    hospitalOfferMillis = 120_000L;
    private double  floorPct = 0.30;

    public KoManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file   = new File(plugin.getDataFolder(), "ko-state.yml");
        this.noJumpKey = new NamespacedKey(plugin, "ko_no_jump");
    }

    /* ============================================================ lifecycle */

    public void start() {
        reloadConfig();
        load();
        if (ticker != null) ticker.cancel();
        // 1-second resolution is plenty for effects refresh, action
        // bar text, and pin-to-lock-location enforcement.
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        save();
    }

    public void reloadConfig() {
        var cfg = plugin.getConfig();
        downedEnabled       = cfg.getBoolean("ko.a-terre", true);
        downedMillis        = Math.max(5, cfg.getLong("ko.a-terre-secondes", 45)) * 1000L;
        unconsciousHpMillis = Math.max(10, cfg.getLong("ko.inconscient-secondes", 300)) * 1000L;
        hospitalOfferMillis = Math.max(0, cfg.getLong("ko.hopital-propose-apres", 120)) * 1000L;
        floorPct            = Math.max(0.01, Math.min(1.0, cfg.getDouble("ko.plancher-pct", 0.30)));
    }

    /** Fraction of max HP that rest can restore and the hospital wakes you at. */
    public double floorPct() { return floorPct; }

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
                KoState st = new KoState(pid, cid, cause, start, blind, loc);
                KoState.Phase phase;
                try { phase = KoState.Phase.valueOf(s.getString("phase", "UNCONSCIOUS")); }
                catch (IllegalArgumentException ex) { phase = KoState.Phase.UNCONSCIOUS; }
                st.setPhase(phase, s.getLong("phase-start", start));
                st.setHospitalOffered(s.getBoolean("hospital-offered", false));
                for (String a : s.getStringList("attackers")) {
                    try { st.attackers().add(UUID.fromString(a)); } catch (IllegalArgumentException ignored) { }
                }
                active.put(pid, st);
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
            cfg.set(key + ".phase",       st.phase().name());
            cfg.set(key + ".phase-start", st.phaseStartMillis());
            cfg.set(key + ".hospital-offered", st.hospitalOffered());
            List<String> atk = new ArrayList<>();
            for (UUID a : st.attackers()) atk.add(a.toString());
            cfg.set(key + ".attackers", atk);
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

    /** Duration of the unconscious phase for this KO. */
    private long unconsciousMillisFor(KoState.Cause cause) {
        return cause == KoState.Cause.CHAKRA ? MIN_KO_MILLIS_CHAKRA : unconsciousHpMillis;
    }

    /* --------------------------------------------------------------- queries */

    public boolean isKo(UUID playerId)         { return active.containsKey(playerId); }
    public KoState getKo(UUID playerId)        { return active.get(playerId); }
    public Collection<KoState> all()           { return active.values(); }

    public boolean isDowned(UUID playerId) {
        KoState st = active.get(playerId);
        return st != null && st.isDowned();
    }

    /** ATA (PainRP / FearRP) — see {@link AtaManager}. */
    @Override
    public boolean isImpaired(UUID playerId) {
        return plugin.ata() != null && plugin.ata().isImpaired(playerId);
    }

    /** Remember who hit whom, so a KO knows its protagonists. */
    public void recordHit(UUID victim, UUID attacker) {
        if (victim == null || attacker == null || victim.equals(attacker)) return;
        recentHits.computeIfAbsent(victim, k -> new ConcurrentHashMap<>())
                .put(attacker, System.currentTimeMillis());
    }

    /* --------------------------------------------------------------- enter */

    /** Put a player into the KO state.
     *
     *  @param player  the now-unconscious player
     *  @param charId  the character they were incarnating at the moment
     *                 of the KO; used so injuries land on the right
     *                 character even if the player swaps later
     *  @param cause   what dropped them — HP starts "à terre", chakra
     *                 faints straight away
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
        Map<UUID, Long> hits = recentHits.remove(player.getUniqueId());
        if (hits != null) {
            hits.forEach((attacker, t) -> {
                if (now - t <= ATTACKER_WINDOW_MILLIS) st.attackers().add(attacker);
            });
        }
        boolean downed = cause == KoState.Cause.HP && downedEnabled;
        st.setPhase(downed ? KoState.Phase.DOWNED : KoState.Phase.UNCONSCIOUS, now);
        active.put(player.getUniqueId(), st);

        // A KO during an ATA aggravates it (KO-3).
        if (plugin.ata() != null && charId != null) plugin.ata().onKnockedDown(charId);

        applyKoEffects(player);

        if (downed && !modEvent(player, KoHudSync.EV_A_TERRE)) {
            player.showTitle(Title.title(
                    Component.text("À terre", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                    Component.text("Tu peux encore ramper… /aide pour appeler", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(200),
                            Duration.ofSeconds(2),
                            Duration.ofMillis(800))));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.6f);
        } else if (!downed) {
            showUnconsciousTitle(player);
        }

        save();
        return st;
    }

    /** À terre → inconscient (timer écoulé, ou nouveau coup reçu). */
    public void knockOut(UUID playerId) {
        KoState st = active.get(playerId);
        if (st == null || !st.isDowned()) return;
        st.setPhase(KoState.Phase.UNCONSCIOUS, System.currentTimeMillis());
        // The body stops where it lost consciousness.
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) {
            st.setLockLocation(p.getLocation().clone());
            applyKoEffects(p);
            showUnconsciousTitle(p);
        }
        save();
    }

    private void showUnconsciousTitle(Player player) {
        if (modEvent(player, KoHudSync.EV_KO)) return;
        player.showTitle(Title.title(
                Component.text("KO", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("Vous perdez connaissance.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300),
                        Duration.ofSeconds(2),
                        Duration.ofMillis(800))));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 0.5f);
    }

    /* --------------------------------------------------------------- exit */

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
     *
     *  <p>Called by heal-revive, techniques (bijuu cloak…) and staff:
     *  an HP-cause wake here means someone treated the body on site, so
     *  the character leaves with a light ATA.
     */
    public void revive(UUID playerId, KoState.Cause cause,
                       double hp, double chakra) {
        wake(playerId, cause, hp, chakra, AtaManager.Level.ALLEGEE,
                KoHudSync.EV_REVEIL, "Réveil", "Vous reprenez conscience.");
    }

    /** Backwards-compat shim — picks the cause from the live KO row
     *  if any, otherwise defaults to HP. */
    public void revive(UUID playerId, double hp, double chakra) {
        KoState st = active.get(playerId);
        KoState.Cause c = st != null ? st.cause() : KoState.Cause.HP;
        revive(playerId, c, hp, chakra);
    }

    private void wake(UUID playerId, KoState.Cause cause, double hp, double chakra,
                      AtaManager.Level ataLevel, byte event, String title, String subtitle) {
        KoState st = active.remove(playerId);
        if (st == null) return;
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) {
            clearKoEffects(p);
            ShinobiCharacter c = plugin.characters().getActive(playerId);
            switch (cause) {
                case HP -> {
                    if (c != null) {
                        c.setCurrentHp(Math.min(hp, c.maxHp()));
                    }
                    p.setHealth(Math.max(0.5, Math.min(hp, p.getMaxHealth())));
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
            if (!modEvent(p, event)) p.showTitle(Title.title(
                    Component.text(title, NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(subtitle, NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300),
                            Duration.ofSeconds(3),
                            Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 0.8f, 0.8f);
        }
        // PainRP / FearRP after a blow — not after chakra fatigue.
        if (cause == KoState.Cause.HP && ataLevel != null && plugin.ata() != null
                && st.characterId() != null) {
            plugin.ata().apply(st.characterId(), playerId, st.attackers(), ataLevel);
        }
        save();
    }

    /** Fin du KO sans soin (ou {@code /hopital}) : réveil à l'hôpital du
     *  village, PV au plancher, ATA pleine. */
    public boolean hospitalize(UUID playerId) {
        KoState st = active.get(playerId);
        Player p = Bukkit.getPlayer(playerId);
        if (st == null || p == null) return false;
        ShinobiCharacter c = plugin.characters().getActive(playerId);
        String village = c != null ? c.village() : "";
        Location dest = plugin.hospitals() != null ? plugin.hospitals().locationFor(village) : null;
        if (dest == null) dest = p.getWorld().getSpawnLocation();
        plugin.porter().releaseAllInvolving(playerId);
        double hp = Math.max(1.0, p.getMaxHealth() * floorPct);
        wake(playerId, KoState.Cause.HP, hp, 0, AtaManager.Level.PLEINE,
                KoHudSync.EV_HOPITAL, "Hôpital", "On t'a ramené à l'hôpital. Tu es encore très faible.");
        p.teleport(dest);
        return true;
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
                if (plugin.trainingZones().isTraining(p)) {
                    plugin.trainingZones().defeat(p, true);
                    continue;
                }
                enterKo(p, c.id(), KoState.Cause.CHAKRA);
            }
        }

        for (KoState st : active.values()) {
            Player p = Bukkit.getPlayer(st.playerId());
            if (p == null) continue;

            if (st.isDowned()) {
                long left = st.phaseStartMillis() + downedMillis - now;
                if (left <= 0) {
                    knockOut(st.playerId());
                    continue;
                }
                applyKoEffects(p);
                if (!modded(p)) p.sendActionBar(Component.text(
                        String.format("À terre — %d s · tu peux ramper · /aide pour appeler",
                                left / 1000L + 1), NamedTextColor.RED));
                continue;
            }

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

            long elapsed = now - st.phaseStartMillis();
            long total = unconsciousMillisFor(st.cause());

            // Offer the way out once (HP cause only).
            if (st.cause() == KoState.Cause.HP && !st.hospitalOffered()
                    && elapsed >= hospitalOfferMillis) {
                st.setHospitalOffered(true);
                p.sendMessage(Component.text("Personne ne vient ? ", NamedTextColor.GRAY)
                        .append(Component.text("[Se laisser emmener à l'hôpital]",
                                        NamedTextColor.GOLD, TextDecoration.BOLD)
                                .clickEvent(ClickEvent.runCommand("/hopital"))
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        "Tu te réveilleras à l'hôpital, très affaibli (ATA).",
                                        NamedTextColor.GRAY)))));
            }

            if (elapsed >= total) {
                if (st.cause() == KoState.Cause.HP) {
                    hospitalize(st.playerId());
                } else {
                    wake(st.playerId(), st.cause(), AUTO_WAKE_HP, AUTO_WAKE_CHAKRA, null,
                            KoHudSync.EV_REVEIL, "Réveil", "Vous reprenez conscience.");
                }
                continue; // Don't paint the actionbar after a wake.
            }

            // Actionbar countdown.
            long remaining = (total - elapsed) / 1000L;
            String label = st.cause() == KoState.Cause.CHAKRA
                    ? "Épuisement chakra" : "Inconscient";
            String hint = st.cause() == KoState.Cause.HP && st.hospitalOffered()
                    ? " · /hopital pour être emmené" : "";
            if (!modded(p)) p.sendActionBar(Component.text(
                    String.format("%s — %d:%02d · tu peux chuchoter et /me%s",
                            label, remaining / 60, remaining % 60, hint),
                    NamedTextColor.DARK_GRAY));
        }
    }

    /** Durée de la phase en cours (à terre ou inconscient), pour l'affichage. */
    public long phaseMillis(KoState st) {
        return st.isDowned() ? downedMillis : unconsciousMillisFor(st.cause());
    }

    private boolean modded(Player p) {
        return plugin.koHud() != null && plugin.koHud().modded(p);
    }

    /** Titre stylisé côté mod ; false = client sans mod, garder le titre vanilla. */
    private boolean modEvent(Player p, byte kind) {
        return plugin.koHud() != null && plugin.koHud().event(p, kind);
    }

    /** Re-apply KO effects on the player. Runs on every tick + on
     *  every fresh entry / after a respawn (since respawn clears
     *  potion effects). */
    public void applyKoEffects(Player p) {
        if (p == null || !p.isOnline()) return;
        KoState st = active.get(p.getUniqueId());
        boolean downed = st != null && st.isDowned();
        // Darkness instead of blindness: the body still sees, the vision pulses.
        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                60, 0, true, false, false));
        // À terre : lent mais mobile. Inconscient : paralysé.
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                40, downed ? 1 : 6, true, false, false));
        // Crawl on the ground / lie unconscious.
        if (!p.isInsideVehicle()) {
            Pose want = downed ? Pose.SWIMMING : Pose.SLEEPING;
            if (p.getPose() != want) p.setPose(want, true);
        }
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null && jump.getModifier(noJumpKey) == null) {
            jump.addTransientModifier(new AttributeModifier(noJumpKey, -1.0,
                    AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.ANY));
        }
        // Survival mode so they can't fly out of the hold.
        if (p.getGameMode() == GameMode.CREATIVE
                || p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
        }
    }

    private void clearKoEffects(Player p) {
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.setPose(Pose.STANDING, false);
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(noJumpKey);
    }

    /** Force-clear KO state (used by /character edit and on shutdown if
     *  a staff member chooses to wipe a stale row). */
    public void forceClear(UUID playerId) {
        active.remove(playerId);
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) clearKoEffects(p);
        save();
    }

    /* --------------------------------------------------------------- access */

    /** Snapshot map (immutable, for logs / debug). */
    public Map<UUID, KoState> snapshot() {
        return new LinkedHashMap<>(active);
    }
}
