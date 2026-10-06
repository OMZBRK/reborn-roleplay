package com.reborn.shinobicore.ko.zone;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.chakra.ChakraPool;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
 * Terrains d'entraînement et PV persistants (lot KO-1, docs/PROPOSITION_KO_PAINRP.md).
 *
 * <p>Hors d'un terrain, les PV perdus restent perdus : la régénération naturelle
 * (saturation permanente, cf. {@code display.auto-saturation}) est annulée pour les
 * personnages actifs. Seuls un médic, le repos RP (lots suivants) ou un soin explicite
 * rendent des PV.
 *
 * <p>Dans un terrain, le combat est sans séquelles :
 * <ul>
 *   <li>à l'entrée, on mémorise les PV et le chakra du personnage ;</li>
 *   <li>tomber à 0 PV ou 0 chakra donne une « défaite » (pas de KO, pas de blessure) ;</li>
 *   <li>à la sortie (ou à la déconnexion, au changement de personnage, à l'arrêt du
 *       serveur), on remet exactement les valeurs d'entrée : on ne peut ni s'y blesser
 *       durablement ni s'y soigner gratuitement.</li>
 * </ul>
 *
 * <p>Limite connue : en cas de crash serveur pendant un entraînement, la sauvegarde
 * auto a enregistré les PV d'entrée (via {@link #hpToPersist}) mais le chakra courant.
 */
public final class TrainingZones implements Listener {

    /** Valeurs mémorisées à l'entrée, liées au personnage actif à ce moment-là. */
    private record Snapshot(UUID characterId, String zoneId,
                            double hp, double chakra, double chakraDebt) { }

    private final ShinobiCore plugin;
    private final File file;
    private final Map<String, TrainingZone> zones = new LinkedHashMap<>();
    private final Map<UUID, Snapshot> snapshots = new ConcurrentHashMap<>();
    private BukkitTask ticker;

    private boolean persistentHp = true;
    private double defeatPct = 0.30;

    public TrainingZones(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "training-zones.yml");
    }

    /* ============================================================ cycle de vie */

    public void start() {
        reloadConfig();
        load();
        if (ticker != null) ticker.cancel();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void stop() {
        if (ticker != null) { ticker.cancel(); ticker = null; }
        restoreAll();
    }

    public void reloadConfig() {
        persistentHp = plugin.getConfig().getBoolean("ko.pv-persistants", true);
        defeatPct = Math.max(0.05, Math.min(1.0,
                plugin.getConfig().getDouble("ko.defaite-pct", 0.30)));
    }

    private void load() {
        zones.clear();
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("zones");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            TrainingZone.Kind kind = TrainingZone.Kind.parse(s.getString("type", "entrainement"));
            if (kind == null) kind = TrainingZone.Kind.ENTRAINEMENT;
            zones.put(id, new TrainingZone(id, kind, s.getString("world", "world"),
                    s.getInt("min.x"), s.getInt("min.y"), s.getInt("min.z"),
                    s.getInt("max.x"), s.getInt("max.y"), s.getInt("max.z")));
        }
        plugin.getLogger().info("[Zones RP] " + zones.size() + " zone(s) chargée(s).");
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (TrainingZone z : zones.values()) {
            String k = "zones." + z.id();
            cfg.set(k + ".type", z.kind().label());
            cfg.set(k + ".world", z.world());
            cfg.set(k + ".min.x", z.minX()); cfg.set(k + ".min.y", z.minY()); cfg.set(k + ".min.z", z.minZ());
            cfg.set(k + ".max.x", z.maxX()); cfg.set(k + ".max.y", z.maxY()); cfg.set(k + ".max.z", z.maxZ());
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Échec de la sauvegarde de training-zones.yml", ex);
        }
    }

    /* ============================================================ registre */

    public Collection<TrainingZone> all() { return zones.values(); }

    public TrainingZone get(String id) { return zones.get(id); }

    public void put(TrainingZone zone) { zones.put(zone.id(), zone); save(); }

    public boolean remove(String id) {
        if (zones.remove(id) == null) return false;
        save();
        return true;
    }

    /** Première zone (tous types) qui contient {@code loc}. */
    public TrainingZone anyZoneAt(Location loc) {
        for (TrainingZone z : zones.values()) if (z.contains(loc)) return z;
        return null;
    }

    /** Terrain d'entraînement qui contient {@code loc}, ou null. */
    public TrainingZone zoneAt(Location loc) {
        return zoneAt(loc, TrainingZone.Kind.ENTRAINEMENT);
    }

    /** Zone de repos qui contient {@code loc}, ou null. */
    public TrainingZone restZoneAt(Location loc) {
        return zoneAt(loc, TrainingZone.Kind.REPOS);
    }

    private TrainingZone zoneAt(Location loc, TrainingZone.Kind kind) {
        for (TrainingZone z : zones.values()) if (z.kind() == kind && z.contains(loc)) return z;
        return null;
    }

    /** Le joueur est-il en train de s'entraîner (dans un terrain, instantané pris) ? */
    public boolean isTraining(Player p) {
        return p != null && snapshots.containsKey(p.getUniqueId());
    }

    /* ============================================================ ticker */

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Snapshot snap = snapshots.get(id);
            ShinobiCharacter c = plugin.characters().getActive(id);
            boolean eligible = c != null
                    && !plugin.isStaffBuilding(id)
                    && !plugin.ko().isKo(id);
            TrainingZone zone = eligible ? zoneAt(p.getLocation()) : null;

            if (snap != null) {
                if (c == null || !snap.characterId().equals(c.id())) {
                    // Personnage changé sans passer par release() : on lâche l'instantané.
                    snapshots.remove(id);
                    snap = null;
                } else if (plugin.ko().isKo(id)) {
                    snapshots.remove(id);
                    continue;
                } else if (zone == null) {
                    restore(p, c, snap);
                    snapshots.remove(id);
                    p.sendActionBar(Component.text(
                            "Vous quittez le terrain d'entraînement — état d'avant restauré.",
                            NamedTextColor.GRAY));
                    continue;
                }
            }
            if (snap == null && zone != null) {
                ChakraPool pool = c.chakra();
                snapshots.put(id, new Snapshot(c.id(), zone.id(),
                        p.getHealth(), pool.current(), pool.debt()));
                p.sendActionBar(Component.text(
                        "Terrain d'entraînement « " + zone.id() + " » — combat sans séquelles.",
                        NamedTextColor.GOLD));
            }
        }
    }

    private void restore(Player p, ShinobiCharacter c, Snapshot snap) {
        double hp = Math.max(0.5, Math.min(snap.hp(), p.getMaxHealth()));
        p.setHealth(hp);
        c.setCurrentHp(hp);
        ChakraPool pool = c.chakra();
        pool.setCurrent(snap.chakra());
        if (snap.chakraDebt() > 0.0) pool.overdraw(snap.chakraDebt());
    }

    /** Restaure l'instantané (s'il concerne {@code c}) avant que le personnage soit
     *  sauvegardé et quitté — appelé par le changement de personnage. */
    public void release(Player p, ShinobiCharacter c) {
        Snapshot snap = snapshots.get(p.getUniqueId());
        if (snap == null || c == null || !snap.characterId().equals(c.id())) return;
        restore(p, c, snap);
        snapshots.remove(p.getUniqueId());
    }

    /** PV à écrire sur le personnage lors d'une sauvegarde : les PV d'entrée pendant
     *  un entraînement, sinon les PV réels. */
    public double hpToPersist(Player p, ShinobiCharacter c) {
        Snapshot snap = snapshots.get(p.getUniqueId());
        if (snap != null && c != null && snap.characterId().equals(c.id())) return snap.hp();
        return p.getHealth();
    }

    /** Remet tout le monde à son état d'entrée (arrêt du serveur). */
    public void restoreAll() {
        for (Map.Entry<UUID, Snapshot> e : snapshots.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            ShinobiCharacter c = plugin.characters().getActive(e.getKey());
            if (c != null && c.id().equals(e.getValue().characterId())) restore(p, c, e.getValue());
        }
        snapshots.clear();
    }

    /* ============================================================ défaite */

    /** Défaite sur un terrain : pas de KO, pas de blessure, on repart à
     *  {@code ko.defaite-pct} de la ressource épuisée. */
    public void defeat(Player p, boolean chakraCause) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (chakraCause) {
            if (c != null) c.chakra().setCurrent(c.chakra().max() * defeatPct);
        } else {
            p.setHealth(Math.max(1.0, p.getMaxHealth() * defeatPct));
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 3, false, false, false));
        p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 30, 0, false, false, false));
        p.showTitle(Title.title(
                Component.text("Défaite", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text(chakraCause ? "Chakra épuisé — reprends ton souffle."
                        : "Relève-toi, l'entraînement continue.", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1500),
                        Duration.ofMillis(600))));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.7f);
    }

    /* ============================================================ événements */

    /** Déconnexion pendant un entraînement : on restaure AVANT la sauvegarde du
     *  personnage (CharacterLifecycleListener écoute en priorité normale). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent ev) {
        Player p = ev.getPlayer();
        release(p, plugin.characters().getActive(p.getUniqueId()));
    }

    /** PV persistants : pas de régénération naturelle hors terrain d'entraînement. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent ev) {
        if (!persistentHp) return;
        if (!(ev.getEntity() instanceof Player p)) return;
        if (ev.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) return;
        if (plugin.characters().getActive(p.getUniqueId()) == null) return;
        if (isTraining(p)) return;
        ev.setCancelled(true);
    }
}
