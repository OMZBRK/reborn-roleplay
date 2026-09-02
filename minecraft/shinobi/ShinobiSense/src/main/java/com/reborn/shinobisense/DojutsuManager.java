package com.reborn.shinobisense;

import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.util.Tps;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dōjutsu (Byakugan / Sharingan …) — the eyes of perception. While active a
 * holder's chakra sensing is sharpened (longer range, sees through suppression,
 * names everyone) but it <b>drains chakra each second</b> and is <b>visible</b>
 * to nearby players — so the strongest sensors can't spy covertly. Clan-gated.
 *
 * <p>The client-rendered eye overlay waits for the mod; the broadcast to nearby
 * players is the server-side stand-in for "the eyes light up".
 */
public final class DojutsuManager {

    private final ShinobiSense plugin;
    private BukkitTask task;
    private final Set<UUID> active = ConcurrentHashMap.newKeySet();

    public DojutsuManager(ShinobiSense plugin) { this.plugin = plugin; }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        active.clear();
    }

    public boolean isActive(UUID id) { return active.contains(id); }

    /** Toggle the dōjutsu. Returns the new state (true = now active). */
    public boolean toggle(Player p) {
        UUID id = p.getUniqueId();
        if (active.remove(id)) {
            p.sendActionBar(Component.text("Tu désactives ton dōjutsu.", NamedTextColor.GRAY));
            return false;
        }
        ShinobiCharacter c = plugin.characters() != null
                ? plugin.characters().getActive(id) : null;
        if (c == null) {
            p.sendActionBar(Component.text("Aucun personnage actif.", NamedTextColor.RED));
            return false;
        }
        if (!clanAllowed(c) && !p.hasPermission("shinobisense.dojutsu.any")) {
            p.sendActionBar(Component.text("Ton clan ne possède pas de dōjutsu.", NamedTextColor.RED));
            return false;
        }
        active.add(id);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_AMBIENT, 0.8f, 1.6f);
        announce(p, c);
        return true;
    }

    private boolean clanAllowed(ShinobiCharacter c) {
        List<String> clans = plugin.getConfig().getStringList("dojutsu.clans");
        if (clans.isEmpty()) return true;     // unset = allow any (config it to gate)
        String clan = c.clan();
        for (String cl : clans) if (cl.equalsIgnoreCase(clan)) return true;
        return false;
    }

    /** Visible activation — the eyes light up for everyone close by. */
    private void announce(Player p, ShinobiCharacter c) {
        double radius = plugin.getConfig().getDouble("dojutsu.visible-radius", 12.0);
        Location at = p.getLocation();
        Component msg = Component.text("Les yeux de " + c.name() + " s'illuminent.",
                NamedTextColor.LIGHT_PURPLE);
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getWorld().equals(at.getWorld())
                    && o.getLocation().distanceSquared(at) <= radius * radius) {
                o.sendMessage(msg);
            }
        }
    }

    private void tick() {
        if (active.isEmpty() || Tps.shouldDefer()) return;
        double drain = plugin.getConfig().getDouble("dojutsu.chakra-per-second", 8.0);
        for (UUID id : Set.copyOf(active)) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !p.isOnline()) { active.remove(id); continue; }
            ShinobiCharacter c = plugin.characters() != null
                    ? plugin.characters().getActive(id) : null;
            if (c == null) { active.remove(id); continue; }
            if (drain > 0 && !c.chakra().consume(drain)) {
                active.remove(id);
                p.sendActionBar(Component.text("Ton chakra faiblit — le dōjutsu s'éteint.",
                        NamedTextColor.AQUA));
            }
        }
    }
}
