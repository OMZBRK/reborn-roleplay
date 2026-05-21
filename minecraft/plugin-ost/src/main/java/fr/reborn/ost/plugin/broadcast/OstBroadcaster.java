package fr.reborn.ost.plugin.broadcast;

import fr.reborn.ost.plugin.network.OstChannel;
import fr.reborn.ost.plugin.network.OstPacket;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Logique de broadcast OST. Sépare le filtrage des joueurs cibles de
 * l'envoi du packet réseau pour faciliter les tests + la réutilisation
 * depuis les commandes.
 */
public final class OstBroadcaster {

    private final Plugin plugin;

    public OstBroadcaster(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Joue un son positionnel pour tous les joueurs dans {@code radius}
     * blocks autour de {@code target} et dans le même monde.
     *
     * @return nombre de joueurs notifiés
     */
    public int playAtPosition(Location target, float radius, String trackId, float volume) {
        if (target == null || target.getWorld() == null) return 0;
        List<Player> recipients = playersInRange(target, radius);
        byte[] packet = OstPacket.playAtPosition(
            target.getX(), target.getY(), target.getZ(), radius, trackId, volume);
        for (Player p : recipients) {
            p.sendPluginMessage(plugin, OstChannel.NAME, packet);
        }
        return recipients.size();
    }

    /** Broadcast global sans distance — tous les joueurs en ligne. */
    public int playGlobal(String trackId, float volume) {
        byte[] packet = OstPacket.playGlobal(trackId, volume);
        int count = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPluginMessage(plugin, OstChannel.NAME, packet);
            count++;
        }
        return count;
    }

    /**
     * Stoppe le son broadcast actuel pour les joueurs dans le radius
     * autour de {@code target}. Si {@code target == null} ou
     * {@code radius < 0}, stop pour TOUS les joueurs en ligne.
     */
    public int stop(Location target, float radius) {
        byte[] packet = OstPacket.stopBroadcast();
        List<Player> recipients;
        if (target == null || radius < 0) {
            recipients = new ArrayList<>(Bukkit.getOnlinePlayers());
        } else {
            recipients = playersInRange(target, radius);
        }
        for (Player p : recipients) {
            p.sendPluginMessage(plugin, OstChannel.NAME, packet);
        }
        return recipients.size();
    }

    /**
     * @return joueurs en ligne dans le même monde que {@code target},
     *         à distance ≤ {@code radius} blocks (distance euclidienne).
     */
    private static List<Player> playersInRange(Location target, float radius) {
        World world = target.getWorld();
        double rSquared = (double) radius * (double) radius;
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(world)) continue;
            // distanceSquared évite un sqrt par joueur.
            if (p.getLocation().distanceSquared(target) <= rSquared) {
                out.add(p);
            }
        }
        return out;
    }
}
