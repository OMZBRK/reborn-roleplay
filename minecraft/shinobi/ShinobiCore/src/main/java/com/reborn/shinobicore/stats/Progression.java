package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Progression par l'expérience, à côté des grades (qui restent une décision RP) : l'XP fait monter le
 * <b>niveau</b> du personnage, et chaque niveau donne 1 <b>point de stats</b> à répartir ({@code /stats}).
 * <b>Invisible pour le joueur</b> : ni niveau, ni XP, ni annonce — seul le compteur de points du menu bouge.
 *
 * <p>Sources (section {@code progression} de la config) :
 * <ul>
 *   <li><b>présence RP</b> : chaque minute active (le joueur a bougé) avec un autre joueur à proximité ; un peu
 *       moins seul ;</li>
 *   <li><b>entraînement</b> : chaque minute où le joueur a frappé dans un terrain d'entraînement (plafond
 *       journalier) ;</li>
 *   <li><b>apprentissage</b> : séance de parchemin réussie, technique apprise (appelés par ShinobiAbilities) ;</li>
 *   <li><b>staff</b> : {@code /xp donner} pour les missions, examens et events.</li>
 * </ul>
 * Pas d'XP au combat hors zone : on ne récompense pas l'agression.
 */
public final class Progression implements Listener {

    /** Réglages : courbe, points, gains. */
    public record Config(int maxLevel, double base, double perLevel, int pointsPerLevel, double presence, double solo,
                         double training, double trainingDailyCap, double radius) {}

    private static Config cfg = new Config(40, 400, 120, 1, 3, 1, 4, 120, 20);

    private final ShinobiCore plugin;
    private final Map<UUID, Location> lastPos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastMove = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTrainingHit = new ConcurrentHashMap<>();
    private final Map<UUID, Double> trainingToday = new ConcurrentHashMap<>();
    private LocalDate day = LocalDate.now();
    private BukkitTask task;

    public Progression(ShinobiCore plugin) { this.plugin = plugin; }

    public static Config config() { return cfg; }

    public static void load(ConfigurationSection s) {
        if (s == null) return;
        Config d = cfg;
        cfg = new Config(s.getInt("niveau-max", d.maxLevel()), s.getDouble("xp-base", d.base()),
                s.getDouble("xp-par-niveau", d.perLevel()), s.getInt("points-par-niveau", d.pointsPerLevel()),
                s.getDouble("gains.presence-rp", d.presence()), s.getDouble("gains.seul", d.solo()),
                s.getDouble("gains.entrainement", d.training()), s.getDouble("gains.entrainement-max-jour", d.trainingDailyCap()),
                s.getDouble("gains.rayon-presence", d.radius()));
    }

    /** XP nécessaire pour passer du niveau {@code level} au suivant. */
    public static double need(int level) { return cfg.base() + cfg.perLevel() * (level - 1); }

    /** Points de stats apportés par le niveau. */
    public static int pointsFor(int level) { return Math.max(0, level - 1) * cfg.pointsPerLevel(); }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::minute, 20L * 60, 20L * 60);
    }

    public void stop() { if (task != null) task.cancel(); }

    /* ------------------------------------------------------------------ gains */

    /** Donne (ou retire, montant négatif) de l'XP au personnage actif. Renvoie le nouveau niveau, ou -1. */
    public int grant(Player p, double amount, String why) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null || c.dead()) return -1;
        int before = c.level();
        double xp = c.experience() + amount;
        int level = before;
        while (level < cfg.maxLevel() && xp >= need(level)) { xp -= need(level); level++; }
        while (xp < 0 && level > 1) { level--; xp += need(level); }
        if (level >= cfg.maxLevel()) xp = Math.min(xp, need(level));
        c.setExperience(Math.max(0, xp));
        c.setLevel(level);
        plugin.characterRepository().save(c);
        // Journal universel (14/10) : chaque changement de niveau ; chaque gain d'XP si journal.xp (bavard).
        if (level != before || plugin.getConfig().getBoolean("journal.xp", false)) {
            com.reborn.shinobicore.journal.Journal.entree(com.reborn.shinobicore.api.JournalService.Categorie.JOUEUR,
                    level != before ? "NIVEAU" : "XP").acteur(p)
                    .detail(String.format(java.util.Locale.FRANCE, "%+.1f XP (%s)", amount, why)
                            + (level != before ? " · niveau " + before + " → " + level : "")).ecrire();
        }
        return level;
    }

    /* ------------------------------------------------------------ chaque minute */

    private void minute() {
        if (!LocalDate.now().equals(day)) { day = LocalDate.now(); trainingToday.clear(); }
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Location at = p.getLocation();
            Location prev = lastPos.put(id, at);
            if (prev == null || !prev.getWorld().equals(at.getWorld()) || prev.distanceSquared(at) > 4
                    || Math.abs(prev.getYaw() - at.getYaw()) > 20) lastMove.put(id, now);
            boolean active = now - lastMove.getOrDefault(id, 0L) < 3 * 60_000L;
            if (!active || (plugin.ko() != null && plugin.ko().isKo(id))) continue;
            double gain = nearSomeone(p) ? cfg.presence() : cfg.solo();
            Long hit = lastTrainingHit.get(id);
            if (hit != null && now - hit < 60_000L) {
                double done = trainingToday.getOrDefault(id, 0.0);
                double add = Math.min(cfg.training(), Math.max(0, cfg.trainingDailyCap() - done));
                trainingToday.put(id, done + add);
                gain += add;
            }
            if (gain > 0) grant(p, gain, "activité");
        }
    }

    private boolean nearSomeone(Player p) {
        double r2 = cfg.radius() * cfg.radius();
        for (Player o : p.getWorld().getPlayers()) {
            if (o == p) continue;
            if (o.getLocation().distanceSquared(p.getLocation()) <= r2) return true;
        }
        return false;
    }

    /** Coup porté dans un terrain d'entraînement : la minute compte comme entraînement. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        var zones = plugin.trainingZones();
        if (zones == null || zones.zoneAt(p.getLocation()) == null) return;
        // Un duel ne rapporte pas d'XP de combat, même s'il déborde sur un terrain.
        if (plugin.duels() != null && plugin.duels().inDuel(p.getUniqueId())) return;
        lastTrainingHit.put(p.getUniqueId(), System.currentTimeMillis());
    }
}
