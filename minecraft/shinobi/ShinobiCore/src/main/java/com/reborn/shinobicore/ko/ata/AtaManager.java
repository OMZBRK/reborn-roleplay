package com.reborn.shinobicore.ko.ata;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ATA — « blessé, en attente de soin » (lot KO-3, docs/PROPOSITION_KO_PAINRP.md).
 *
 * <p>Posée sur le <b>personnage</b> à chaque réveil d'un KO par les PV :
 * {@link Level#PLEINE} après un rapatriement à l'hôpital, {@link Level#ALLEGEE}
 * quand quelqu'un a soigné le corps sur place. Tant qu'elle dure, le jeu porte
 * lui-même le PainRP et le FearRP :
 * <ul>
 *   <li><b>Pain</b> — pas de sprint (faim maintenue à 6), pas de kit de mobilité,
 *       techniques rang D maximum ({@code KoService#isImpaired}), coups au corps à
 *       corps divisés par deux, démarche ralentie ;</li>
 *   <li><b>Fear</b> — impossible de frapper les protagonistes du KO, et hors
 *       terrain d'entraînement impossible d'ouvrir un combat (on peut seulement se
 *       défendre). La peur dure au moins {@code ko.ata.peur-minutes} après le KO,
 *       même si l'ATA est levée avant ;</li>
 *   <li><b>NLR</b> — laissé au joueur : simple rappel.</li>
 * </ul>
 *
 * <p>On la lève par un soin médical ({@link #lift}, appelé par le futur soin
 * maintenu — lot KO-4 — ou par le staff via {@code /ata lever}), ou par le
 * <b>repos RP</b> cumulé : méditer, ou s'asseoir / s'allonger dans une zone de
 * repos ({@code /zonerp creer repos <id>} : restaurant, onsen, auberge, hôpital).
 * Le repos n'avance que si le joueur est actif (anti-AFK).
 *
 * <p>Le repos rend aussi des PV à tout le monde, ATA ou non, mais seulement
 * jusqu'au plancher ({@code ko.plancher-pct}, 30 %) : au-delà, il faut un médic.
 */
public final class AtaManager implements Listener {

    public enum Level { ALLEGEE, PLEINE }

    /** Ce que le mod client affiche de l'ATA (voir {@code KoHudSync}). */
    public record View(Level level, float progress, int restMinutes, int requiredMinutes, boolean resting) { }

    /** ATA d'un personnage. */
    private static final class State {
        final UUID characterId;
        Level level;
        long startMillis;
        long restMillis;
        int koCount;
        final Set<UUID> attackers = new LinkedHashSet<>();

        State(UUID characterId, Level level, long startMillis) {
            this.characterId = characterId;
            this.level = level;
            this.startMillis = startMillis;
        }
    }

    /** Peur résiduelle : survit à la levée de l'ATA jusqu'à {@code until}. */
    private record Fear(long until, Set<UUID> attackers) { }

    private static final long SELF_DEFENSE_MILLIS = 30_000L;
    private static final double MELEE_FACTOR = 0.5;

    private final ShinobiCore plugin;
    private final File file;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();      // characterId →
    private final Map<UUID, Fear> fears = new ConcurrentHashMap<>();        // characterId →
    private final Map<UUID, Long> lastActive = new ConcurrentHashMap<>();   // playerId →
    /** victim → (attacker → millis) : qui a frappé qui, pour la légitime défense. */
    private final Map<UUID, Map<UUID, Long>> hitBy = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();      // playerId →
    /** Joueurs dont on a coupé la faim : à rendre quand l'ATA disparaît. */
    private final Set<UUID> restricted = ConcurrentHashMap.newKeySet();
    /** Personnages au repos à la dernière seconde (affichage « en cours »). */
    private final Set<UUID> restingNow = ConcurrentHashMap.newKeySet();
    private BukkitTask ticker;
    private int ticks;

    private long restFullMillis = 20 * 60_000L;
    private long restLightMillis = 10 * 60_000L;
    private long fearMillis = 20 * 60_000L;
    private long afkMillis = 120_000L;
    private double restHealPct = 0.01;

    public AtaManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "ata-state.yml");
    }

    /* ============================================================ cycle de vie */

    public void start() {
        reloadConfig();
        load();
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) p.hideBossBar(e.getValue());
        }
        bars.clear();
        save();
    }

    public void reloadConfig() {
        var cfg = plugin.getConfig();
        restFullMillis  = Math.max(1, cfg.getLong("ko.ata.repos-minutes-pleine", 20)) * 60_000L;
        restLightMillis = Math.max(1, cfg.getLong("ko.ata.repos-minutes-allegee", 10)) * 60_000L;
        fearMillis      = Math.max(0, cfg.getLong("ko.ata.peur-minutes", 20)) * 60_000L;
        afkMillis       = Math.max(10, cfg.getLong("ko.ata.anti-afk-secondes", 120)) * 1000L;
        restHealPct     = Math.max(0, cfg.getDouble("ko.ata.repos-pv-pct-par-10s", 0.01));
    }

    private void load() {
        states.clear();
        fears.clear();
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("ata");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    UUID cid = UUID.fromString(key);
                    Level lvl;
                    try { lvl = Level.valueOf(s.getString("level", "PLEINE")); }
                    catch (IllegalArgumentException ex) { lvl = Level.PLEINE; }
                    State st = new State(cid, lvl, s.getLong("start", System.currentTimeMillis()));
                    st.restMillis = s.getLong("rest", 0L);
                    st.koCount = s.getInt("ko-count", 1);
                    st.attackers.addAll(uuids(s.getStringList("attackers")));
                    states.put(cid, st);
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Ligne ATA invalide ignorée : " + key);
                }
            }
        }
        ConfigurationSection fr = cfg.getConfigurationSection("peur");
        if (fr != null) {
            for (String key : fr.getKeys(false)) {
                try {
                    fears.put(UUID.fromString(key), new Fear(fr.getLong(key + ".until"),
                            new LinkedHashSet<>(uuids(fr.getStringList(key + ".attackers")))));
                } catch (IllegalArgumentException ignored) { }
            }
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (State st : states.values()) {
            String k = "ata." + st.characterId;
            cfg.set(k + ".level", st.level.name());
            cfg.set(k + ".start", st.startMillis);
            cfg.set(k + ".rest", st.restMillis);
            cfg.set(k + ".ko-count", st.koCount);
            cfg.set(k + ".attackers", strings(st.attackers));
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Fear> e : fears.entrySet()) {
            if (e.getValue().until() < now) continue;
            cfg.set("peur." + e.getKey() + ".until", e.getValue().until());
            cfg.set("peur." + e.getKey() + ".attackers", strings(e.getValue().attackers()));
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Échec de la sauvegarde de ata-state.yml", ex);
        }
    }

    private static List<UUID> uuids(List<String> raw) {
        List<UUID> out = new ArrayList<>();
        for (String s : raw) {
            try { out.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }

    private static List<String> strings(Collection<UUID> ids) {
        List<String> out = new ArrayList<>();
        for (UUID id : ids) out.add(id.toString());
        return out;
    }

    /* ============================================================ API */

    /** Réveil d'un KO par les PV : pose (ou aggrave) l'ATA du personnage. */
    public void apply(UUID characterId, UUID playerId, Collection<UUID> attackers, Level level) {
        long now = System.currentTimeMillis();
        State st = states.get(characterId);
        if (st == null) {
            st = new State(characterId, level, now);
            st.koCount = 1;
            states.put(characterId, st);
        } else if (level == Level.PLEINE) {
            st.level = Level.PLEINE;
        }
        st.attackers.addAll(attackers);
        Fear old = fears.get(characterId);
        Set<UUID> fearSet = new LinkedHashSet<>(st.attackers);
        if (old != null) fearSet.addAll(old.attackers());
        fears.put(characterId, new Fear(now + fearMillis, fearSet));
        save();

        Player p = Bukkit.getPlayer(playerId);
        if (p != null) {
            lastActive.put(playerId, now);
            p.sendMessage(Component.text("— ATA : tu es gravement blessé —", NamedTextColor.RED, TextDecoration.BOLD));
            p.sendMessage(Component.text(
                    "• Pas de sprint ni de mobilité, techniques de rang D seulement, coups affaiblis.",
                    NamedTextColor.GRAY));
            p.sendMessage(Component.text(
                    "• La peur t'empêche d'attaquer ceux qui t'ont mis à terre, et d'ouvrir un combat.",
                    NamedTextColor.GRAY));
            p.sendMessage(Component.text(
                    "• Pour te rétablir : un médic, ou du repos (méditer, t'asseoir au restaurant, à l'onsen, "
                            + "à l'auberge ou à l'hôpital).", NamedTextColor.GRAY));
            p.sendMessage(Component.text(
                    "• NLR : ne reviens pas sur la scène — c'est à toi de le jouer.", NamedTextColor.DARK_GRAY));
        }
    }

    /** Nouveau KO pendant une ATA : elle repart à zéro, en pleine. */
    public void onKnockedDown(UUID characterId) {
        State st = states.get(characterId);
        if (st == null) return;
        st.koCount++;
        st.level = Level.PLEINE;
        st.restMillis = 0L;
        save();
    }

    /** Lève l'ATA (soin médical, staff, repos terminé). La peur résiduelle reste. */
    public boolean lift(UUID characterId, String why) {
        State st = states.remove(characterId);
        if (st == null) return false;
        save();
        Player p = ownerOnline(characterId);
        if (p != null) {
            releaseRestriction(p);
            BossBar bar = bars.remove(p.getUniqueId());
            if (bar != null) p.hideBossBar(bar);
            boolean modded = plugin.koHud() != null
                    && plugin.koHud().event(p, com.reborn.shinobicore.ko.KoHudSync.EV_RETABLI);
            if (!modded) p.showTitle(Title.title(
                    Component.text("Rétabli", NamedTextColor.GREEN, TextDecoration.BOLD),
                    Component.text(why, NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(800))));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.2f);
        }
        return true;
    }

    public boolean hasAta(UUID characterId) { return states.containsKey(characterId); }

    /** État affichable de l'ATA, ou null s'il n'y en a pas. */
    public View view(UUID characterId) {
        State st = states.get(characterId);
        if (st == null) return null;
        long req = required(st);
        return new View(st.level, (float) Math.min(1.0, st.restMillis / (double) req),
                (int) (st.restMillis / 60_000L), (int) (req / 60_000L), restingNow.contains(characterId));
    }

    /** Peur active (ATA en cours ou peur résiduelle) ? */
    public boolean isAfraid(UUID characterId) {
        return fearOf(characterId) != null;
    }

    /** ATA du personnage actif du joueur ? */
    public boolean isImpaired(UUID playerId) {
        ShinobiCharacter c = plugin.characters().getActive(playerId);
        return c != null && states.containsKey(c.id());
    }

    /** Résumé lisible (commande /ata). */
    public String describe(UUID characterId) {
        State st = states.get(characterId);
        Fear f = fears.get(characterId);
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder();
        if (st == null) sb.append("§aAucune ATA.");
        else sb.append("§cATA ").append(st.level == Level.PLEINE ? "pleine" : "allégée")
                .append(" §7— repos ").append(st.restMillis / 60_000L).append("/")
                .append(required(st) / 60_000L).append(" min, KO subis : ").append(st.koCount);
        if (f != null && f.until() > now) {
            sb.append(" §7· peur encore ").append((f.until() - now) / 60_000L + 1).append(" min");
        }
        return sb.toString();
    }

    private long required(State st) {
        return st.level == Level.PLEINE ? restFullMillis : restLightMillis;
    }

    private Player ownerOnline(UUID characterId) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c != null && c.id().equals(characterId)) return p;
        }
        return null;
    }

    /** Le personnage est-il sous l'effet de la peur ? */
    private Fear fearOf(UUID characterId) {
        State st = states.get(characterId);
        Fear f = fears.get(characterId);
        long now = System.currentTimeMillis();
        if (f != null && f.until() < now) { fears.remove(characterId); f = null; }
        if (st != null) {
            Set<UUID> atk = new HashSet<>(st.attackers);
            if (f != null) atk.addAll(f.attackers());
            return new Fear(Long.MAX_VALUE, atk);
        }
        return f;
    }

    /* ============================================================ ticker */

    private void tick() {
        long now = System.currentTimeMillis();
        boolean dirty = false;
        ticks++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID pid = p.getUniqueId();
            ShinobiCharacter c = plugin.characters().getActive(pid);
            if (c == null || plugin.ko().isKo(pid) || plugin.isStaffBuilding(pid)) {
                hideBar(p);
                continue;
            }
            State st = states.get(c.id());
            boolean resting = isResting(p, now);

            // Le repos rend des PV jusqu'au plancher (ATA ou non) : 1 % / 10 s.
            if (resting && restHealPct > 0 && ticks % 10 == 0) {
                double floor = p.getMaxHealth() * plugin.ko().floorPct();
                if (p.getHealth() < floor) {
                    double hp = Math.min(floor, p.getHealth() + p.getMaxHealth() * restHealPct);
                    p.setHealth(hp);
                    c.setCurrentHp(hp);
                }
            }

            if (st == null) {
                hideBar(p);
                if (restricted.contains(pid)) releaseRestriction(p);
                continue;
            }

            // PainRP : pas de sprint (faim ≤ 6), démarche ralentie.
            restricted.add(pid);
            p.removePotionEffect(PotionEffectType.SATURATION);
            if (p.getFoodLevel() != 6) p.setFoodLevel(6);
            p.setSaturation(0f);
            if (p.isSprinting()) p.setSprinting(false);
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false, false));

            if (resting) restingNow.add(c.id()); else restingNow.remove(c.id());
            if (resting) {
                st.restMillis += 1000L;
                dirty = true;
                if (st.restMillis >= required(st)) {
                    lift(c.id(), "Le repos a fait son œuvre.");
                    continue;
                }
            }
            if (plugin.koHud() != null && plugin.koHud().modded(p)) hideBar(p);   // le mod l'affiche
            else showBar(p, st, resting);
        }
        if (dirty && ticks % 15 == 0) save();
    }

    /** Méditer, ou être assis / allongé dans une zone de repos — et pas AFK. */
    private boolean isResting(Player p, long now) {
        Long active = lastActive.get(p.getUniqueId());
        if (active == null || now - active > afkMillis) return false;
        if (plugin.meditation() != null && plugin.meditation().isMeditating(p.getUniqueId())) return true;
        if (plugin.trainingZones().restZoneAt(p.getLocation()) == null) return false;
        return p.isInsideVehicle() || p.isSleeping()
                || (plugin.posture() != null && plugin.posture().isPosed(p));
    }

    private void showBar(Player p, State st, boolean resting) {
        float progress = (float) Math.min(1.0, st.restMillis / (double) required(st));
        Component title = Component.text("ATA " + (st.level == Level.PLEINE ? "— grièvement blessé" : "— blessé"),
                        NamedTextColor.RED)
                .append(Component.text(" · repos " + st.restMillis / 60_000L + "/" + required(st) / 60_000L
                        + " min" + (resting ? " (en cours)" : "") + " · ou soin médical", NamedTextColor.GRAY));
        BossBar bar = bars.get(p.getUniqueId());
        BossBar.Color color = st.level == Level.PLEINE ? BossBar.Color.RED : BossBar.Color.YELLOW;
        if (bar == null) {
            bar = BossBar.bossBar(title, progress, color, BossBar.Overlay.NOTCHED_10);
            bars.put(p.getUniqueId(), bar);
            p.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(progress);
            bar.color(color);
        }
    }

    private void hideBar(Player p) {
        BossBar bar = bars.remove(p.getUniqueId());
        if (bar != null) p.hideBossBar(bar);
    }

    /** Rend la faim / saturation coupées pendant l'ATA. */
    private void releaseRestriction(Player p) {
        if (!restricted.remove(p.getUniqueId())) return;
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20 * 90, 0, true, false, false));
    }

    /* ============================================================ événements */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent ev) {
        // Activité = regard qui bouge (un AFK ne touche pas la souris).
        if (ev.getFrom().getYaw() != ev.getTo().getYaw() || ev.getFrom().getPitch() != ev.getTo().getPitch()) {
            lastActive.put(ev.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChat(io.papermc.paper.event.player.AsyncChatEvent ev) {
        lastActive.put(ev.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent ev) {
        lastActive.put(ev.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /** Manger dans une zone de repos compte comme 2 minutes de repos (repas RP). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEat(PlayerItemConsumeEvent ev) {
        Player p = ev.getPlayer();
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return;
        State st = states.get(c.id());
        if (st == null || plugin.trainingZones().restZoneAt(p.getLocation()) == null) return;
        st.restMillis = Math.min(required(st), st.restMillis + 120_000L);
        lastActive.put(p.getUniqueId(), System.currentTimeMillis());
        p.sendActionBar(Component.text("Un bon repas… (+2 min de repos)", NamedTextColor.GOLD));
    }

    @EventHandler(ignoreCancelled = true)
    public void onSprint(PlayerToggleSprintEvent ev) {
        if (ev.isSprinting() && isImpaired(ev.getPlayer().getUniqueId())) {
            ev.setCancelled(true);
            ev.getPlayer().setSprinting(false);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        hideBar(ev.getPlayer());
        restricted.remove(ev.getPlayer().getUniqueId());
    }

    /** FearRP + coups affaiblis. Après les plugins de combat (HIGHEST). */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent ev) {
        if (!(ev.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(ev.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        long now = System.currentTimeMillis();
        if (plugin.trainingZones().isTraining(attacker)) return;
        ShinobiCharacter ac = plugin.characters().getActive(attacker.getUniqueId());
        if (ac == null) { recordHit(victim, attacker, now); return; }

        Fear fear = fearOf(ac.id());
        if (fear != null) {
            if (fear.attackers().contains(victim.getUniqueId())) {
                ev.setCancelled(true);
                attacker.sendActionBar(Component.text("La peur te paralyse face à " + victim.getName() + "…",
                        NamedTextColor.DARK_RED));
                return;
            }
            Map<UUID, Long> mine = hitBy.get(attacker.getUniqueId());
            Long provoked = mine != null ? mine.get(victim.getUniqueId()) : null;
            if (provoked == null || now - provoked > SELF_DEFENSE_MILLIS) {
                ev.setCancelled(true);
                attacker.sendActionBar(Component.text("Trop blessé pour provoquer un combat — tu ne peux que te défendre.",
                        NamedTextColor.GRAY));
                return;
            }
        }
        if (states.containsKey(ac.id()) && ev.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            ev.setDamage(ev.getDamage() * MELEE_FACTOR);
        }
        recordHit(victim, attacker, now);
    }

    /** Coup réellement porté : ouvre la légitime défense de la victime. */
    private void recordHit(Player victim, Player attacker, long now) {
        hitBy.computeIfAbsent(victim.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(attacker.getUniqueId(), now);
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
