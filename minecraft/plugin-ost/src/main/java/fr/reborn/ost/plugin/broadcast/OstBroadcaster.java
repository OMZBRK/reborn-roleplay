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
import java.util.UUID;

/**
 * Logique de broadcast OST. Sépare le filtrage des joueurs cibles de
 * l'envoi du packet réseau pour faciliter les tests + la réutilisation
 * depuis les commandes.
 *
 * <p>Depuis Phase 2 : maintient un {@link OstZoneRegistry} pour permettre
 * aux joueurs qui rejoignent une zone de broadcast déjà active de recevoir
 * la track au timestamp courant. {@link #tick()} scanne les joueurs vs
 * zones actives et envoie un PLAY aux nouvelles subscriptions.
 */
public final class OstBroadcaster {

    private final Plugin plugin;
    private final OstZoneRegistry registry;

    public OstBroadcaster(Plugin plugin) {
        this(plugin, new OstZoneRegistry());
    }

    /** Pour injecter un registry custom dans les tests. */
    OstBroadcaster(Plugin plugin, OstZoneRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public OstZoneRegistry registry() { return registry; }

    /**
     * Crée une zone broadcast active et envoie le packet PLAY (offset = 0)
     * aux joueurs déjà dans le rayon. Les joueurs qui arrivent plus tard
     * sont notifiés par {@link #tick()}.
     *
     * @return nombre de joueurs notifiés initialement
     */
    public int playAtPosition(Location target, float radius, String trackId, float volume) {
        if (target == null || target.getWorld() == null) return 0;
        OstZoneRegistry.ZoneRecord zone = registry.addZone(
            target.getWorld().getUID(), target.getX(), target.getY(), target.getZ(),
            radius, trackId, volume);

        byte[] packet = OstPacket.playAtPosition(
            target.getX(), target.getY(), target.getZ(), radius, trackId, volume, 0f);

        List<Player> recipients = playersInRange(target, radius);
        for (Player p : recipients) {
            p.sendPluginMessage(plugin, OstChannel.NAME, packet);
            registry.markSubscribed(p.getUniqueId(), zone.id());
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
     * {@code radius < 0}, stop pour TOUS les joueurs en ligne et purge
     * toutes les zones actives.
     *
     * <p>Note : on supprime aussi les zones correspondantes du registry —
     * sinon le tick suivant ré-enverrait un PLAY à n'importe quel joueur
     * encore dans le rayon. Le "stop" est définitif.
     */
    public int stop(Location target, float radius) {
        byte[] packet = OstPacket.stopBroadcast();
        List<Player> recipients;
        if (target == null || radius < 0) {
            recipients = new ArrayList<>(Bukkit.getOnlinePlayers());
            registry.clearAll();
        } else {
            recipients = playersInRange(target, radius);
            if (target.getWorld() != null) {
                registry.removeZonesNear(target.getWorld().getUID(), target.getX(),
                    target.getY(), target.getZ(), radius);
            }
        }
        for (Player p : recipients) {
            p.sendPluginMessage(plugin, OstChannel.NAME, packet);
        }
        return recipients.size();
    }

    /**
     * Scan O(zones × joueurs en ligne). Pour chaque (zone, joueur) :
     * <ul>
     *   <li>si même monde + distance ≤ radius + pas encore subscribed →
     *       envoie un PLAY avec {@code secOffset = elapsed since zone start}</li>
     *   <li>sinon : no-op</li>
     * </ul>
     * À appeler depuis {@link org.bukkit.scheduler.BukkitScheduler} toutes
     * les 20 ticks (1 s). À cette fréquence, un joueur qui sprint à 6 m/s
     * couvre 6 blocs entre deux ticks — encore largement dedans le rayon
     * typique (16-64).
     *
     * @return nombre de subscriptions ajoutées (pour métriques / logs)
     */
    public int tick() {
        int added = 0;
        long now = System.currentTimeMillis();
        for (OstZoneRegistry.ZoneRecord zone : registry.allZones()) {
            double r2 = (double) zone.radius() * (double) zone.radius();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (registry.isSubscribed(p.getUniqueId(), zone.id())) continue;
                Location loc = p.getLocation();
                UUID worldId = loc.getWorld() != null ? loc.getWorld().getUID() : null;
                double d2 = zone.distanceSquaredFrom(worldId, loc.getX(), loc.getY(), loc.getZ());
                if (d2 > r2) continue;

                float secOffset = (now - zone.startedAtMs()) / 1000f;
                byte[] packet = OstPacket.playAtPosition(
                    zone.x(), zone.y(), zone.z(), zone.radius(),
                    zone.trackId(), zone.volume(), secOffset);
                p.sendPluginMessage(plugin, OstChannel.NAME, packet);
                registry.markSubscribed(p.getUniqueId(), zone.id());
                added++;
            }
        }
        return added;
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
            if (p.getLocation().distanceSquared(target) <= rSquared) {
                out.add(p);
            }
        }
        return out;
    }
}
