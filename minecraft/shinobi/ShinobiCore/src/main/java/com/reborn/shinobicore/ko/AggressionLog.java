package com.reborn.shinobicore.ko;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Journal des agressions hors terrain d'entraînement (lot KO-1).
 *
 * <p>Pour chaque paire de joueurs, seul le <b>premier coup</b> d'un échange est noté
 * (fenêtre {@code ko.journal-agressions.fenetre-minutes}, 5 min par défaut) : la ligne
 * dit donc qui a ouvert le combat. Le fichier {@code aggression-log.txt} sert à repérer
 * les joueurs qui enchaînent les combats « d'arène » ; il sera exposé au panel staff.
 */
public final class AggressionLog implements Listener {

    private final ShinobiCore plugin;
    private final File file;
    /** "uuidA|uuidB" (ordre stable) → dernier coup vu entre ces deux joueurs. */
    private final Map<String, Long> lastExchange = new ConcurrentHashMap<>();

    public AggressionLog(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "aggression-log.txt");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent ev) {
        if (!plugin.getConfig().getBoolean("ko.journal-agressions.enabled", true)) return;
        if (!(ev.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(ev.getDamager());
        if (attacker == null || attacker.equals(victim)) return;
        if (plugin.trainingZones().isTraining(victim) || plugin.trainingZones().isTraining(attacker)) return;

        ShinobiCharacter vc = plugin.characters().getActive(victim.getUniqueId());
        ShinobiCharacter ac = plugin.characters().getActive(attacker.getUniqueId());
        if (vc == null || ac == null) return;

        long window = Math.max(1, plugin.getConfig().getLong("ko.journal-agressions.fenetre-minutes", 5))
                * 60_000L;
        String a = attacker.getUniqueId().toString(), b = victim.getUniqueId().toString();
        String key = a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
        long now = System.currentTimeMillis();
        Long last = lastExchange.put(key, now);
        if (last != null && now - last < window) return;   // échange déjà en cours

        Location l = victim.getLocation();
        String line = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(now))
                + " | " + attacker.getName() + " (" + ac.name() + ")"
                + " -> " + victim.getName() + " (" + vc.name() + ")"
                + " | " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
        append(line);
    }

    private static Player resolveAttacker(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private void append(String line) {
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            try (BufferedWriter w = new BufferedWriter(new FileWriter(file, true))) {
                w.write(line);
                w.newLine();
            }
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Échec d'écriture de aggression-log.txt", ex);
        }
    }
}
